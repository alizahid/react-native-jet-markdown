#include "EditorRuns.h"

#include <algorithm>
#include <set>

#include "Ast.h"
#include "AstToMarkdown.h"
#include "Parser.h"

namespace jetmarkdown {

namespace {

// Outer-to-inner nesting order when marks overlap. Inline code is innermost
// because markdown cannot format inside a code span; the serializer emits
// code content verbatim.
constexpr uint32_t kNestingOrder[] = {
    MarkSpoiler,
    MarkBold,
    MarkItalic,
    MarkStrikethrough,
    MarkSuperscript,
    MarkSubscript,
    MarkInlineCode,
};

NodeType nodeTypeForMark(uint32_t mark) {
  switch (mark) {
    case MarkBold:
      return NodeType::Bold;
    case MarkItalic:
      return NodeType::Italic;
    case MarkStrikethrough:
      return NodeType::Strikethrough;
    case MarkSpoiler:
      return NodeType::Spoiler;
    case MarkSuperscript:
      return NodeType::Superscript;
    case MarkSubscript:
      return NodeType::Subscript;
    default:
      return NodeType::InlineCode;
  }
}

uint32_t markForNodeType(NodeType type) {
  switch (type) {
    case NodeType::Bold:
      return MarkBold;
    case NodeType::Italic:
      return MarkItalic;
    case NodeType::Strikethrough:
      return MarkStrikethrough;
    case NodeType::InlineCode:
      return MarkInlineCode;
    case NodeType::Spoiler:
      return MarkSpoiler;
    case NodeType::Superscript:
      return MarkSuperscript;
    case NodeType::Subscript:
      return MarkSubscript;
    default:
      return 0;
  }
}

// Maps every UTF-16 code-unit offset to a byte offset in the UTF-8 text.
// Index i holds the byte position where UTF-16 unit i starts; the final
// entry is text.size().
std::vector<size_t> utf16ToByteTable(const std::string& text) {
  std::vector<size_t> table;
  table.reserve(text.size() + 1);
  size_t i = 0;
  while (i < text.size()) {
    const auto byte = static_cast<unsigned char>(text[i]);
    size_t charLen = 1;
    size_t utf16Len = 1;
    if (byte >= 0xF0) {
      charLen = 4;
      utf16Len = 2; // surrogate pair
    } else if (byte >= 0xE0) {
      charLen = 3;
    } else if (byte >= 0xC0) {
      charLen = 2;
    }
    for (size_t u = 0; u < utf16Len; u++) {
      table.push_back(i);
    }
    i += charLen;
  }
  table.push_back(text.size());
  return table;
}

size_t utf16Length(const char* data, size_t size) {
  size_t length = 0;
  size_t i = 0;
  while (i < size) {
    const auto byte = static_cast<unsigned char>(data[i]);
    if (byte >= 0xF0) {
      length += 2;
      i += 4;
    } else if (byte >= 0xE0) {
      length += 1;
      i += 3;
    } else if (byte >= 0xC0) {
      length += 1;
      i += 2;
    } else {
      length += 1;
      i += 1;
    }
  }
  return length;
}

struct Segment {
  std::string text;
  uint32_t flags = 0;
  // Index into the links vector, or -1 when the piece is not linked.
  int link = -1;
};

// Builds inline nodes for a line's segments. The mark whose run extends
// over the most upcoming segments wraps them (ties broken by
// kNestingOrder), which keeps overlapping runs nested instead of producing
// adjacent close/open delimiter runs that merge when re-parsed. Inline code
// never wraps other marks (markdown cannot format inside a code span), so
// it only groups exact-equal flag segments and stays innermost.
void buildInlineNodes(
    MarkdownDocument& document,
    Node* parent,
    const std::vector<Segment>& segments,
    size_t begin,
    size_t end,
    uint32_t handled) {
  const auto extentOf = [&](size_t from, uint32_t mark) {
    size_t j = from;
    while (j < end && (segments[j].flags & ~handled & mark) != 0) {
      j++;
    }
    return j - from;
  };

  size_t i = begin;
  while (i < end) {
    const uint32_t flags = segments[i].flags & ~handled;
    if (flags == 0) {
      Node* text = document.arena.alloc(NodeType::Text);
      text->text = segments[i].text;
      parent->children.push_back(text);
      i++;
      continue;
    }
    uint32_t mark = 0;
    size_t extent = 0;
    for (const uint32_t candidate : kNestingOrder) {
      if (candidate == MarkInlineCode || (flags & candidate) == 0) {
        continue;
      }
      const size_t candidateExtent = extentOf(i, candidate);
      if (candidateExtent > extent) {
        mark = candidate;
        extent = candidateExtent;
      }
    }
    if (mark == 0) {
      Node* code = document.arena.alloc(NodeType::InlineCode);
      size_t j = i;
      while (j < end && (segments[j].flags & ~handled) == flags) {
        code->text += segments[j].text;
        j++;
      }
      parent->children.push_back(code);
      i = j;
    } else {
      Node* wrapper = document.arena.alloc(nodeTypeForMark(mark));
      parent->children.push_back(wrapper);
      buildInlineNodes(document, wrapper, segments, i, i + extent, handled | mark);
      i += extent;
    }
  }
}

struct EditorLineContent {
  std::vector<Segment> segments;
  EditorLine block;

  bool empty() const {
    return segments.empty();
  }

  std::string rawText() const {
    std::string out;
    for (const Segment& segment : segments) {
      out += segment.text;
    }
    return out;
  }
};

// Builds a line's inline nodes: consecutive same-link segments wrap in a
// Link node (links are outermost — markdown formats inside the label).
void buildLineNodes(
    MarkdownDocument& document,
    Node* parent,
    const std::vector<Segment>& segments,
    const std::vector<LinkRun>& links) {
  size_t i = 0;
  while (i < segments.size()) {
    const int link = segments[i].link;
    size_t j = i;
    while (j < segments.size() && segments[j].link == link) {
      j++;
    }
    if (link >= 0 && static_cast<size_t>(link) < links.size()) {
      Node* node = document.arena.alloc(NodeType::Link);
      node->url = links[static_cast<size_t>(link)].url;
      parent->children.push_back(node);
      buildInlineNodes(document, node, segments, i, j, 0);
    } else {
      buildInlineNodes(document, parent, segments, i, j, 0);
    }
    i = j;
  }
}

// ---------------------------------------------------------------------------
// Markdown extraction (parse -> editor document)
// ---------------------------------------------------------------------------

void appendMarkedText(EditorDocument& out, const std::string& text, uint32_t flags) {
  if (text.empty()) {
    return;
  }
  const uint32_t start = out.textUtf16Length;
  out.text += text;
  out.textUtf16Length +=
      static_cast<uint32_t>(utf16Length(text.data(), text.size()));
  if (flags != 0) {
    const uint32_t end = out.textUtf16Length;
    if (!out.runs.empty() && out.runs.back().flags == flags &&
        out.runs.back().end == start) {
      out.runs.back().end = end;
    } else {
      out.runs.push_back({start, end, flags});
    }
  }
}

struct EditorCollector {
  EditorDocument& out;
  bool atBlockStart = true;
  EditorLine currentLine;

  // Completes the current text line: every '\n' appended to out.text gets a
  // matching entry in out.lines.
  void endLine() {
    out.lines.push_back(currentLine);
    out.text += '\n';
    out.textUtf16Length += 1;
  }

  void beginBlock(const EditorLine& line) {
    if (!atBlockStart && !out.text.empty()) {
      endLine();
    }
    currentLine = line;
    atBlockStart = true;
  }

  void collectChildren(const Node* node, uint32_t activeFlags, EditorLine context) {
    for (const Node* child : node->children) {
      collect(child, activeFlags, context);
    }
  }

  void collect(const Node* node, uint32_t activeFlags, EditorLine context) {
    const uint32_t mark = markForNodeType(node->type);
    if (mark != 0 && node->type != NodeType::InlineCode) {
      collectChildren(node, activeFlags | mark, context);
      return;
    }
    switch (node->type) {
      case NodeType::Text:
        appendMarkedText(out, node->text, activeFlags);
        break;
      case NodeType::SoftBreak:
      case NodeType::HardBreak:
        endLine();
        currentLine = context;
        break;
      case NodeType::InlineCode:
        appendMarkedText(out, node->text, activeFlags | MarkInlineCode);
        break;
      case NodeType::Link: {
        const uint32_t start = out.textUtf16Length;
        collectChildren(node, activeFlags, context);
        const uint32_t end = out.textUtf16Length;
        if (end > start) {
          out.links.push_back({start, end, node->url});
        }
        break;
      }
      case NodeType::Image:
        appendMarkedText(out, node->text, activeFlags);
        break;
      case NodeType::CodeBlock: {
        beginBlock({EditorBlockType::Code, 0});
        std::string content = node->text;
        if (!content.empty() && content.back() == '\n') {
          content.pop_back();
        }
        size_t lineStart = 0;
        while (true) {
          const size_t newline = content.find('\n', lineStart);
          const std::string piece = content.substr(
              lineStart, newline == std::string::npos ? std::string::npos
                                                      : newline - lineStart);
          out.text += piece;
          out.textUtf16Length +=
              static_cast<uint32_t>(utf16Length(piece.data(), piece.size()));
          if (newline == std::string::npos) {
            break;
          }
          endLine();
          currentLine = {EditorBlockType::Code, 0};
          lineStart = newline + 1;
        }
        atBlockStart = false;
        break;
      }
      case NodeType::Heading:
        beginBlock({EditorBlockType::Heading, node->level});
        collectChildren(node, activeFlags, {EditorBlockType::Heading, node->level});
        atBlockStart = false;
        break;
      case NodeType::Paragraph:
      case NodeType::TableRow:
        beginBlock(context);
        collectChildren(node, activeFlags, context);
        atBlockStart = false;
        break;
      case NodeType::BlockQuote:
        // Innermost block wins: lists inside a quote become list lines.
        collectChildren(node, activeFlags, {EditorBlockType::Quote, 0});
        break;
      case NodeType::List: {
        const EditorLine itemLine = {
            node->ordered ? EditorBlockType::Ordered : EditorBlockType::Bullet, 0};
        for (const Node* item : node->children) {
          // Tight items hold inline children directly; force a fresh line
          // for each item, then collect content in item context.
          beginBlock(itemLine);
          collectChildren(item, activeFlags, itemLine);
          atBlockStart = false;
        }
        break;
      }
      case NodeType::TableCell:
        if (!atBlockStart && !out.text.empty() && out.text.back() != '\n') {
          out.text += ' ';
          out.textUtf16Length += 1;
        }
        collectChildren(node, activeFlags, context);
        break;
      case NodeType::ThematicBreak:
        break;
      default:
        collectChildren(node, activeFlags, context);
        break;
    }
  }

  void finish() {
    // Line entries exist per newline; the final (unterminated) line gets
    // its entry here so lines.size() == line count.
    out.lines.push_back(currentLine);
  }
};

} // namespace

std::string markdownFromEditor(
    const std::string& text,
    const std::vector<StyledRun>& runs,
    const std::vector<EditorLine>& lines,
    const std::vector<LinkRun>& links) {
  const std::vector<size_t> toByte = utf16ToByteTable(text);
  const auto utf16Size = static_cast<uint32_t>(toByte.size() - 1);

  // Emphasis delimiters cannot open before or close after whitespace
  // (flanking rules), so runs are trimmed inward past edge spaces — the
  // spaces fall outside as plain text. Code-only runs keep their spaces:
  // the code-span serializer pads them instead.
  const auto isEdgeSpace = [&](uint32_t offset) {
    const char c = text[toByte[offset]];
    return c == ' ' || c == '\t';
  };
  std::vector<StyledRun> trimmed;
  trimmed.reserve(runs.size());
  for (StyledRun run : runs) {
    run.start = std::min(run.start, utf16Size);
    run.end = std::min(run.end, utf16Size);
    if ((run.flags & ~MarkInlineCode) != 0) {
      while (run.start < run.end && isEdgeSpace(run.start)) {
        run.start++;
      }
      while (run.end > run.start && isEdgeSpace(run.end - 1)) {
        run.end--;
      }
    }
    if (run.start < run.end) {
      trimmed.push_back(run);
    }
  }

  // Split at every run/link edge and newline so each piece has constant
  // flags and link membership.
  std::set<uint32_t> cuts = {0, utf16Size};
  for (const StyledRun& run : trimmed) {
    cuts.insert(run.start);
    cuts.insert(run.end);
  }
  for (const LinkRun& link : links) {
    cuts.insert(std::min(link.start, utf16Size));
    cuts.insert(std::min(link.end, utf16Size));
  }
  for (uint32_t u = 0; u < utf16Size; u++) {
    if (text[toByte[u]] == '\n') {
      cuts.insert(u);
      cuts.insert(u + 1);
    }
  }

  // Collect lines (keeping empties so indices align with `lines`).
  std::vector<EditorLineContent> collected;
  collected.push_back({});
  size_t lineIndex = 0;
  const auto blockForLine = [&](size_t index) -> EditorLine {
    return index < lines.size() ? lines[index] : EditorLine{};
  };
  collected.back().block = blockForLine(0);

  // Sweep instead of rescanning every run per piece (which made heavily
  // styled documents O(pieces × runs)). Run edges are all cuts, so a run
  // covers piece [prev, next) exactly when start <= prev < end.
  std::vector<std::pair<uint32_t, uint32_t>> runStarts;
  std::vector<std::pair<uint32_t, uint32_t>> runEnds;
  runStarts.reserve(trimmed.size());
  runEnds.reserve(trimmed.size());
  for (const StyledRun& run : trimmed) {
    runStarts.push_back({run.start, run.flags});
    runEnds.push_back({run.end, run.flags});
  }
  std::sort(runStarts.begin(), runStarts.end());
  std::sort(runEnds.begin(), runEnds.end());
  std::vector<size_t> linkOrder(links.size());
  for (size_t l = 0; l < links.size(); l++) {
    linkOrder[l] = l;
  }
  std::sort(linkOrder.begin(), linkOrder.end(), [&](size_t a, size_t b) {
    return links[a].start < links[b].start;
  });

  int bitCounts[32] = {0};
  const auto adjustFlags = [&](uint32_t flags, int delta) {
    for (int bit = 0; bit < 32; bit++) {
      if (flags & (1u << bit)) {
        bitCounts[bit] += delta;
      }
    }
  };
  const auto activeFlags = [&]() {
    uint32_t flags = 0;
    for (int bit = 0; bit < 32; bit++) {
      if (bitCounts[bit] > 0) {
        flags |= 1u << bit;
      }
    }
    return flags;
  };

  size_t startIdx = 0;
  size_t endIdx = 0;
  size_t linkPtr = 0;

  auto it = cuts.begin();
  uint32_t prev = *it;
  for (++it; it != cuts.end(); ++it) {
    const uint32_t next = *it;
    while (endIdx < runEnds.size() && runEnds[endIdx].first <= prev) {
      adjustFlags(runEnds[endIdx].second, -1);
      endIdx++;
    }
    while (startIdx < runStarts.size() && runStarts[startIdx].first <= prev) {
      adjustFlags(runStarts[startIdx].second, 1);
      startIdx++;
    }
    while (linkPtr < linkOrder.size() &&
           links[linkOrder[linkPtr]].end <= prev) {
      linkPtr++;
    }

    const std::string piece =
        text.substr(toByte[prev], toByte[next] - toByte[prev]);
    if (piece == "\n") {
      lineIndex++;
      collected.push_back({});
      collected.back().block = blockForLine(lineIndex);
    } else if (!piece.empty()) {
      int linkIndex = -1;
      if (linkPtr < linkOrder.size() &&
          links[linkOrder[linkPtr]].start <= prev &&
          next <= links[linkOrder[linkPtr]].end) {
        linkIndex = static_cast<int>(linkOrder[linkPtr]);
      }
      collected.back().segments.push_back({piece, activeFlags(), linkIndex});
    }
    prev = next;
  }

  // Group consecutive same-block lines into markdown block nodes.
  MarkdownDocument document;
  Node* root = document.arena.alloc(NodeType::Document);
  document.root = root;

  size_t i = 0;
  while (i < collected.size()) {
    const EditorLineContent& line = collected[i];
    switch (line.block.type) {
      case EditorBlockType::Heading: {
        if (!line.empty()) {
          Node* heading = document.arena.alloc(NodeType::Heading);
          heading->level = std::clamp<uint8_t>(line.block.level, 1, 6);
          root->children.push_back(heading);
          buildLineNodes(document, heading, line.segments, links);
        }
        i++;
        break;
      }
      case EditorBlockType::Quote: {
        Node* quote = document.arena.alloc(NodeType::BlockQuote);
        bool any = false;
        while (i < collected.size() &&
               collected[i].block.type == EditorBlockType::Quote) {
          if (!collected[i].empty()) {
            Node* paragraph = document.arena.alloc(NodeType::Paragraph);
            quote->children.push_back(paragraph);
            buildLineNodes(document, paragraph, collected[i].segments, links);
            any = true;
          }
          i++;
        }
        if (any) {
          root->children.push_back(quote);
        }
        break;
      }
      case EditorBlockType::Code: {
        // Raw text; inline marks cannot exist inside a code fence. Empty
        // interior lines stay; the fence itself provides the boundaries.
        Node* code = document.arena.alloc(NodeType::CodeBlock);
        std::string content;
        bool any = false;
        while (i < collected.size() &&
               collected[i].block.type == EditorBlockType::Code) {
          if (!content.empty() || any) {
            content += '\n';
          }
          content += collected[i].rawText();
          any = true;
          i++;
        }
        if (any && !content.empty()) {
          code->text = content + "\n";
          root->children.push_back(code);
        }
        break;
      }
      case EditorBlockType::Bullet:
      case EditorBlockType::Ordered: {
        const EditorBlockType type = line.block.type;
        Node* list = document.arena.alloc(NodeType::List);
        list->ordered = type == EditorBlockType::Ordered;
        list->startIndex = 1;
        bool any = false;
        while (i < collected.size() && collected[i].block.type == type) {
          if (!collected[i].empty()) {
            Node* item = document.arena.alloc(NodeType::ListItem);
            list->children.push_back(item);
            buildLineNodes(document, item, collected[i].segments, links);
            any = true;
          }
          i++;
        }
        if (any) {
          root->children.push_back(list);
        }
        break;
      }
      case EditorBlockType::Paragraph:
      default: {
        if (!line.empty()) {
          Node* paragraph = document.arena.alloc(NodeType::Paragraph);
          root->children.push_back(paragraph);
          buildLineNodes(document, paragraph, line.segments, links);
        }
        i++;
        break;
      }
    }
  }

  return astToMarkdown(root);
}

EditorDocument editorFromMarkdown(const std::string& markdown) {
  auto document = parseMarkdown(markdown);
  EditorDocument out;
  EditorCollector collector{out, true, EditorLine{}};
  if (document->root != nullptr) {
    collector.collectChildren(document->root, 0, EditorLine{});
  }
  collector.finish();
  return out;
}

std::string markdownFromStyledText(
    const std::string& text,
    const std::vector<StyledRun>& runs) {
  return markdownFromEditor(text, runs, {});
}

StyledText styledTextFromMarkdown(const std::string& markdown) {
  EditorDocument document = editorFromMarkdown(markdown);
  return {std::move(document.text), std::move(document.runs)};
}

} // namespace jetmarkdown
