// Golden tests for the shared markdown core.
// Build & run: cpp/tests/run_tests.sh

#include <algorithm>
#include <cstdio>
#include <string>
#include <utility>
#include <vector>

#include "../core/AstJson.h"
#include "../core/AstToMarkdown.h"
#include "../core/EditorRuns.h"
#include "../core/EditorText.h"
#include "../core/Parser.h"

namespace {

int g_failures = 0;
int g_total = 0;

void expectAst(const char* name, const std::string& markdown, const std::string& expected) {
  g_total++;
  auto doc = fastmarkdown::parseMarkdown(markdown);
  const std::string actual = fastmarkdown::astToJson(doc->root);
  if (actual != expected) {
    g_failures++;
    std::printf("FAIL %s\n  input:    %s\n  expected: %s\n  actual:   %s\n",
                name, markdown.c_str(), expected.c_str(), actual.c_str());
  }
}

void expectContains(const char* name, const std::string& markdown, const std::string& fragment) {
  g_total++;
  auto doc = fastmarkdown::parseMarkdown(markdown);
  const std::string actual = fastmarkdown::astToJson(doc->root);
  if (actual.find(fragment) == std::string::npos) {
    g_failures++;
    std::printf("FAIL %s\n  input:    %s\n  missing:  %s\n  actual:   %s\n",
                name, markdown.c_str(), fragment.c_str(), actual.c_str());
  }
}

void expectNotContains(const char* name, const std::string& markdown, const std::string& fragment) {
  g_total++;
  auto doc = fastmarkdown::parseMarkdown(markdown);
  const std::string actual = fastmarkdown::astToJson(doc->root);
  if (actual.find(fragment) != std::string::npos) {
    g_failures++;
    std::printf("FAIL %s\n  input:    %s\n  found unexpected: %s\n  actual:   %s\n",
                name, markdown.c_str(), fragment.c_str(), actual.c_str());
  }
}

// The round-trip law: parsing the serialized output must reproduce the
// original AST exactly, even when the spelling normalizes.
void expectRoundTrip(const char* name, const std::string& markdown) {
  g_total++;
  auto doc = fastmarkdown::parseMarkdown(markdown);
  const std::string original = fastmarkdown::astToJson(doc->root);
  const std::string serialized = fastmarkdown::astToMarkdown(doc->root);
  auto reparsed = fastmarkdown::parseMarkdown(serialized);
  const std::string actual = fastmarkdown::astToJson(reparsed->root);
  if (actual != original) {
    g_failures++;
    std::printf(
        "FAIL roundtrip %s\n  input:      %s\n  serialized: %s\n  expected:   %s\n  actual:     %s\n",
        name, markdown.c_str(), serialized.c_str(), original.c_str(), actual.c_str());
  }
}

void expectString(
    const char* name, const std::string& actual, const std::string& expected) {
  g_total++;
  if (actual != expected) {
    g_failures++;
    std::printf("FAIL %s\n  expected: %s\n  actual:   %s\n",
                name, expected.c_str(), actual.c_str());
  }
}

// Canonical form: one maximal run per mark bit, so equivalent mark coverage
// compares equal regardless of how runs were sliced or combined.
std::string dumpStyled(const fastmarkdown::StyledText& styled) {
  std::string out = "\"" + styled.text + "\"";
  for (uint32_t bit = 1; bit <= fastmarkdown::MarkSubscript; bit <<= 1) {
    std::vector<std::pair<uint32_t, uint32_t>> intervals;
    for (const auto& run : styled.runs) {
      if ((run.flags & bit) == 0 || run.start >= run.end) {
        continue;
      }
      intervals.push_back({run.start, run.end});
    }
    std::sort(intervals.begin(), intervals.end());
    for (size_t i = 0; i < intervals.size(); i++) {
      auto [start, end] = intervals[i];
      while (i + 1 < intervals.size() && intervals[i + 1].first <= end) {
        end = std::max(end, intervals[++i].second);
      }
      out += " " + std::to_string(start) + ":" + std::to_string(end) + ":" +
          std::to_string(bit);
    }
  }
  return out;
}

std::string dumpEditor(const fastmarkdown::EditorDocument& document) {
  std::string out = dumpStyled({document.text, document.runs});
  out += " |";
  for (const auto& line : document.lines) {
    out += " " + std::to_string(static_cast<int>(line.type));
    if (line.level != 0) {
      out += "." + std::to_string(line.level);
    }
  }
  for (const auto& link : document.links) {
    out += " [" + std::to_string(link.start) + ":" + std::to_string(link.end) +
        " " + link.url + "]";
  }
  return out;
}

// The editor round-trip law with blocks: text + runs + line blocks must
// survive serialization to markdown and re-extraction unchanged.
void expectEditorRoundTrip(
    const char* name,
    const std::string& text,
    const std::vector<fastmarkdown::StyledRun>& runs,
    const std::vector<fastmarkdown::EditorLine>& lines) {
  g_total++;
  const std::string markdown = fastmarkdown::markdownFromEditor(text, runs, lines);
  const auto extracted = fastmarkdown::editorFromMarkdown(markdown);
  const std::string expected = dumpEditor({text, runs, lines, {}});
  const std::string actual = dumpEditor(extracted);
  if (actual != expected) {
    g_failures++;
    std::printf(
        "FAIL editor %s\n  serialized: %s\n  expected:   %s\n  actual:     %s\n",
        name, markdown.c_str(), expected.c_str(), actual.c_str());
  }
}

// The editor round-trip law: text + mark runs must survive serialization to
// markdown and re-extraction unchanged.
void expectStyledRoundTrip(
    const char* name,
    const std::string& text,
    const std::vector<fastmarkdown::StyledRun>& runs) {
  g_total++;
  const std::string markdown = fastmarkdown::markdownFromStyledText(text, runs);
  const auto extracted = fastmarkdown::styledTextFromMarkdown(markdown);
  const std::string expected = dumpStyled({text, runs});
  const std::string actual = dumpStyled(extracted);
  if (actual != expected) {
    g_failures++;
    std::printf(
        "FAIL styled %s\n  serialized: %s\n  expected:   %s\n  actual:     %s\n",
        name, markdown.c_str(), expected.c_str(), actual.c_str());
  }
}

} // namespace

int main() {
  using namespace std::string_literals;

  // --- Basics ---
  expectAst(
      "paragraph",
      "hello world",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"text","text":"hello world"}]}]})");

  expectAst(
      "heading",
      "## Title",
      R"({"type":"document","children":[{"type":"heading","level":2,"children":[{"type":"text","text":"Title"}]}]})");

  expectAst(
      "bold italic nesting",
      "**bold _italic_**",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"bold","children":[{"type":"text","text":"bold "},{"type":"italic","children":[{"type":"text","text":"italic"}]}]}]}]})");

  expectAst(
      "link",
      "[label](https://example.com)",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"link","url":"https://example.com","children":[{"type":"text","text":"label"}]}]}]})");

  expectContains(
      "mention link keeps scheme",
      "hey [@ali](users://ali)!",
      R"({"type":"link","url":"users://ali","children":[{"type":"text","text":"@ali"}]})");

  expectContains(
      "autolink",
      "see https://example.com now",
      R"("type":"link","url":"https://example.com")");

  expectAst(
      "image",
      "![alt text](https://example.com/x.png)",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"image","text":"alt text","url":"https://example.com/x.png"}]}]})");

  expectAst(
      "inline code",
      "run `npm i` now",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"text","text":"run "},{"type":"inlineCode","text":"npm i"},{"type":"text","text":" now"}]}]})");

  expectAst(
      "code block with lang",
      "```js\nlet x = 1;\n```",
      R"({"type":"document","children":[{"type":"codeBlock","text":"let x = 1;\n","url":"js"}]})");

  expectContains("block quote", "> quoted", R"("type":"blockQuote")");

  expectContains(
      "ordered list start",
      "3. three\n4. four",
      R"("type":"list","ordered":true,"start":3)");

  expectContains("thematic break", "a\n\n---\n\nb", R"("type":"thematicBreak")");

  expectContains("hard break", "line one  \nline two", R"("type":"hardBreak")");

  // --- Tables ---
  expectAst(
      "table structure",
      "| a | b |\n|---|--:|\n| 1 | 2 |",
      R"({"type":"document","children":[{"type":"table","children":[{"type":"tableRow","level":1,"children":[{"type":"tableCell","children":[{"type":"text","text":"a"}]},{"type":"tableCell","level":3,"children":[{"type":"text","text":"b"}]}]},{"type":"tableRow","children":[{"type":"tableCell","children":[{"type":"text","text":"1"}]},{"type":"tableCell","level":3,"children":[{"type":"text","text":"2"}]}]}]}]})");

  // --- Spoilers ---
  expectAst(
      "discord spoiler",
      "a ||secret|| b",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"text","text":"a "},{"type":"spoiler","children":[{"type":"text","text":"secret"}]},{"type":"text","text":" b"}]}]})");

  expectAst(
      "reddit spoiler mid-line",
      "a >!secret!< b",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"text","text":"a "},{"type":"spoiler","children":[{"type":"text","text":"secret"}]},{"type":"text","text":" b"}]}]})");

  expectAst(
      "reddit spoiler at line start (not blockquote)",
      ">!secret!< after",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"spoiler","children":[{"type":"text","text":"secret"}]},{"type":"text","text":" after"}]}]})");

  expectContains(
      "spoiler inside blockquote",
      "> before >!secret!< after",
      R"({"type":"spoiler","children":[{"type":"text","text":"secret"}]})");

  expectAst(
      "spoiler spanning styled runs",
      "||bold **x** end||",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"spoiler","children":[{"type":"text","text":"bold "},{"type":"bold","children":[{"type":"text","text":"x"}]},{"type":"text","text":" end"}]}]}]})");

  expectNotContains(
      "spoiler not inside fenced code",
      "```\n>!not a spoiler!<\n||also not||\n```",
      R"("type":"spoiler")");

  expectNotContains(
      "spoiler not inside inline code",
      "`||nope||`",
      R"("type":"spoiler")");

  expectNotContains("empty spoiler literal", "||||", R"("type":"spoiler")");

  expectNotContains(
      "unclosed spoiler literal",
      "a ||secret forever",
      R"("type":"spoiler")");

  // --- Strikethrough vs subscript ---
  expectAst(
      "double tilde strikethrough",
      "~~gone~~",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"strikethrough","children":[{"type":"text","text":"gone"}]}]}]})");

  expectAst(
      "single tilde subscript",
      "H~2~O",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"text","text":"H"},{"type":"subscript","children":[{"type":"text","text":"2"}]},{"type":"text","text":"O"}]}]})");

  expectNotContains(
      "subscript rejects spaces",
      "~not a sub~",
      R"("type":"subscript")");

  expectContains(
      "strikethrough spanning styled runs",
      "~~a **b** c~~",
      R"({"type":"strikethrough","children":[{"type":"text","text":"a "},{"type":"bold","children":[{"type":"text","text":"b"}]},{"type":"text","text":" c"}]})");

  // --- Superscript ---
  expectAst(
      "pandoc superscript",
      "x^2^ y",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"text","text":"x"},{"type":"superscript","children":[{"type":"text","text":"2"}]},{"type":"text","text":" y"}]}]})");

  expectAst(
      "reddit bare superscript",
      "x^2 y",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"text","text":"x"},{"type":"superscript","children":[{"type":"text","text":"2"}]},{"type":"text","text":" y"}]}]})");

  expectAst(
      "reddit paren superscript",
      "note^(multi word here) end",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"text","text":"note"},{"type":"superscript","children":[{"type":"text","text":"multi word here"}]},{"type":"text","text":" end"}]}]})");

  expectAst(
      "bare superscript to end of line",
      "wow^amazing",
      R"({"type":"document","children":[{"type":"paragraph","children":[{"type":"text","text":"wow"},{"type":"superscript","children":[{"type":"text","text":"amazing"}]}]}]})");

  expectNotContains(
      "lone caret literal",
      "a ^ b",
      R"("type":"superscript")");

  // --- Interactions ---
  expectContains(
      "sub inside spoiler",
      "||H~2~O||",
      R"({"type":"spoiler","children":[{"type":"text","text":"H"},{"type":"subscript","children":[{"type":"text","text":"2"}]},{"type":"text","text":"O"}]})");

  expectNotContains(
      "extensions skip link labels",
      "[a ||b|| c](https://example.com)",
      R"("type":"spoiler")");

  expectContains(
      "entity translation",
      "a &amp; b &#65;",
      R"({"type":"text","text":"a & b A"})");

  expectContains(
      "nested list structure",
      "- a\n  - b",
      R"("type":"list")");

  // --- Serializer round trips (AstToMarkdown) ---
  expectRoundTrip("rt paragraph", "hello world");
  expectRoundTrip("rt two paragraphs", "one\n\ntwo");
  expectRoundTrip("rt soft break", "line one\nline two");
  expectRoundTrip("rt hard break", "line one\\\nline two");
  expectRoundTrip("rt headings", "# H1\n\n## H2\n\n###### H6");
  expectRoundTrip("rt bold italic", "**bold** and _italic_ and **bold _nested_**");
  expectRoundTrip("rt strikethrough", "~~gone~~ stays");
  expectRoundTrip("rt spoiler", "both ||secret|| kinds >!hidden!< here");
  expectRoundTrip("rt sup sub", "x^2^ and H~2~O and reddit ^word too");
  expectRoundTrip("rt sup multiword", "reddit ^(multi word) form");
  expectRoundTrip("rt inline code", "run `npm install` now");
  expectRoundTrip("rt inline code with backticks", "a ``code `with` ticks`` b");
  expectRoundTrip("rt link", "see [the docs](https://example.com) now");
  expectRoundTrip("rt link with parens", "see [x](https://en.wikipedia.org/wiki/Bracket_(disambiguation))");
  expectRoundTrip("rt mention", "ping [@ali](users://ali) in [#general](channels://general)");
  expectRoundTrip("rt autolink", "visit https://example.com today");
  {
    // A link whose label equals its URL serializes as the bare URL (the
    // permissive-autolink form), not the noisy [url](url) form.
    auto doc = fastmarkdown::parseMarkdown("visit https://example.com today");
    expectString("autolink serializes bare",
                 fastmarkdown::astToMarkdown(doc->root),
                 "visit https://example.com today\n");
    auto labeled = fastmarkdown::parseMarkdown("[docs](https://example.com)");
    expectString("labeled link keeps brackets",
                 fastmarkdown::astToMarkdown(labeled->root),
                 "[docs](https://example.com)\n");
  }
  expectRoundTrip("rt image", "![alt text](https://example.com/img.png)");
  expectRoundTrip("rt quote", "> quoted **bold**\n>\n> second paragraph");
  expectRoundTrip("rt nested quote content", "> outer\n>\n> - a\n> - b");
  expectRoundTrip("rt code block", "```ts\nconst x = 1;\nconst y = \"two\";\n```");
  expectRoundTrip("rt code block with backticks", "````\na ``` fence inside\n````");
  expectRoundTrip("rt unordered list", "- one\n- two\n- three");
  expectRoundTrip("rt ordered list", "1. first\n2. second");
  expectRoundTrip("rt ordered list start", "5. five\n6. six");
  expectRoundTrip("rt nested list", "- a\n  - a1\n  - a2\n- b");
  expectRoundTrip("rt list with paragraphs", "- first para\n\n  second para\n- next item");
  expectRoundTrip("rt table", "| A | B |\n|---|---|\n| 1 | 2 |");
  expectRoundTrip("rt table alignment", "| L | C | R |\n|:--|:-:|--:|\n| a | b | c |");
  expectRoundTrip("rt thematic break", "one\n\n---\n\ntwo");
  expectRoundTrip("rt literal specials", "not *bold* stays\\* literal \\_x\\_ and \\`tick\\`");
  expectRoundTrip("rt literal hash", "\\# not a heading");
  expectRoundTrip("rt literal list", "1\\. not a list");
  expectRoundTrip("rt literal pipe", "a \\| b");
  expectRoundTrip("rt exclamation", "wow! and ![not image");
  expectRoundTrip("rt mixed document",
      "# Title\n\nIntro with **bold**, `code`, and [a link](https://x.dev).\n\n"
      "> A quote\n\n- item one\n- item two\n\n```js\nconsole.log(1);\n```\n\n"
      "| K | V |\n|---|---|\n| a | 1 |\n\n---\n\nThe ||end|| ^fin^");

  // --- Escaped extension delimiters must stay literal through round trips
  // (md4c strips backslashes before the inline-extension scanner runs; the
  // preprocess entity rewrite + verbatim text nodes keep them de-fanged).
  expectStyledRoundTrip("escape literal spoiler", "a ||spoiler|| b", {});
  expectStyledRoundTrip("escape literal caret", "a^b c", {});
  expectStyledRoundTrip("escape literal tilde pair", "a ~sub~ b", {});
  expectStyledRoundTrip("escape literal strikethrough", "x ~~strike~~ y", {});
  expectStyledRoundTrip("escape literal reddit spoiler", ">!hidden!< tail", {});
  expectStyledRoundTrip("escape literal entity text", "use &#124; here", {});
  expectStyledRoundTrip("escape literal angle autolink", "see <http://example.com> now", {});
  expectStyledRoundTrip("escape leading spaces", "    indented text", {});
  expectRoundTrip("escaped pipe still literal in backticks", "keep `a \\| b` code");

  // --- Emphasis adjacency: stars must survive intraword adjacency and the
  // 4+ star-run fallback must use flanking-legal underscores.
  expectStyledRoundTrip("adjacent bold italic intraword", "XyZ",
      {{0, 1, fastmarkdown::MarkBold}, {1, 2, fastmarkdown::MarkItalic}});
  expectStyledRoundTrip("adjacent italic bold intraword", "XyZ",
      {{0, 1, fastmarkdown::MarkItalic}, {1, 2, fastmarkdown::MarkBold}});

  // --- Superscript content that fits neither caret form.
  expectStyledRoundTrip("sup paren without space", "ab",
      {{0, 2, fastmarkdown::MarkSuperscript}});

  // --- Hostile nesting must parse without blowing the stack (depth cap).
  {
    std::string hostile;
    for (int i = 0; i < 20000; i++) {
      hostile += '>';
    }
    hostile += " x";
    auto doc = fastmarkdown::parseMarkdown(hostile);
    g_total++;
    if (doc->root == nullptr) {
      g_failures++;
      std::printf("FAIL deep nesting parse\n");
    } else {
      // Serialization must also complete (bounded recursion).
      const std::string out = fastmarkdown::astToMarkdown(doc->root);
      if (out.empty()) {
        g_failures++;
        std::printf("FAIL deep nesting serialize\n");
      }
    }
  }

  // --- Nested image inside alt text keeps the outer alt intact.
  expectContains(
      "nested image alt",
      "![a ![b](u2) c](u1)",
      R"("text":"a b c")");

  // --- Surrogate-range character references become U+FFFD, not invalid
  // UTF-8.
  expectContains(
      "surrogate entity replaced",
      "bad &#xD800; ref",
      "\xEF\xBF\xBD");

  // --- Editor plain-text bridge (E1) ---
  expectString(
      "editor markdown from text",
      fastmarkdown::markdownFromPlainText("hello\nworld"),
      "hello\n\nworld\n");
  expectString(
      "editor text escapes literals",
      fastmarkdown::markdownFromPlainText("**not bold** #tag"),
      "\\*\\*not bold\\*\\* #tag\n");
  expectString(
      "editor text escapes line starts",
      fastmarkdown::markdownFromPlainText("# not a heading\n1. not a list"),
      "\\# not a heading\n\n1\\. not a list\n");
  expectString(
      "editor text stable round trip",
      fastmarkdown::plainTextFromMarkdown(
          fastmarkdown::markdownFromPlainText("line one\nline two")),
      "line one\nline two");
  expectString(
      "editor setValue flattens structure",
      fastmarkdown::plainTextFromMarkdown("# Title\n\nbody with **bold**\n\n- a\n- b"),
      "Title\nbody with bold\na\nb");

  // --- Editor styled runs (E2) ---
  using fastmarkdown::MarkBold;
  using fastmarkdown::MarkInlineCode;
  using fastmarkdown::MarkItalic;
  using fastmarkdown::MarkSpoiler;
  using fastmarkdown::MarkStrikethrough;
  using fastmarkdown::MarkSubscript;
  using fastmarkdown::MarkSuperscript;

  expectString(
      "styled bold run",
      fastmarkdown::markdownFromStyledText("hello world", {{0, 5, MarkBold}}),
      "**hello** world\n");
  expectString(
      "styled code ignores nested marks",
      fastmarkdown::markdownFromStyledText(
          "x code y", {{2, 6, MarkInlineCode | MarkBold}}),
      "x **`code`** y\n");
  expectString(
      "styled run clipped at newline",
      fastmarkdown::markdownFromStyledText("ab\ncd", {{0, 5, MarkBold}}),
      "**ab**\n\n**cd**\n");
  expectString(
      "styled marks escape content",
      fastmarkdown::markdownFromStyledText("a*b", {{0, 3, MarkBold}}),
      "**a\\*b**\n");

  expectStyledRoundTrip("styled rt plain", "just text", {});
  expectStyledRoundTrip("styled rt bold", "hello world", {{0, 5, MarkBold}});
  expectStyledRoundTrip(
      "styled rt every mark",
      "abcdefg",
      {{0, 1, MarkBold},
       {1, 2, MarkItalic},
       {2, 3, MarkStrikethrough},
       {3, 4, MarkInlineCode},
       {4, 5, MarkSpoiler},
       {5, 6, MarkSuperscript},
       {6, 7, MarkSubscript}});
  expectStyledRoundTrip(
      "styled rt overlap", "abc", {{0, 2, MarkBold}, {1, 3, MarkItalic}});
  expectStyledRoundTrip(
      "styled rt overlap glued tail",
      "bcd",
      {{0, 1, MarkBold}, {0, 2, MarkItalic}});
  expectStyledRoundTrip(
      "styled rt partial bold in code",
      "x bc y",
      {{2, 4, MarkInlineCode}, {3, 4, MarkBold}});
  expectStyledRoundTrip(
      "styled rt nested combo",
      "bold and italic",
      {{0, 15, MarkBold}, {9, 15, MarkItalic}});
  expectStyledRoundTrip(
      "styled rt multiline",
      "first line\nsecond line",
      {{0, 5, MarkBold}, {11, 17, MarkStrikethrough}});
  expectStyledRoundTrip(
      "styled rt emoji offsets", "\xF0\x9F\x98\x80 xy", {{3, 5, MarkBold}});
  expectStyledRoundTrip(
      "styled rt literal specials", "keep **this** literal", {{5, 13, MarkSpoiler}});
  expectStyledRoundTrip(
      "styled rt adjacent same mark merge", "abc", {{0, 2, MarkBold}, {2, 3, MarkBold}});

  expectString(
      "styled extraction",
      dumpStyled(fastmarkdown::styledTextFromMarkdown("a **b** `c`")),
      "\"a b c\" 2:3:1 4:5:8");

  expectString(
      "styled run trims edge spaces",
      fastmarkdown::markdownFromStyledText("hi bold", {{2, 7, MarkBold}}),
      "hi **bold**\n");
  expectString(
      "styled whitespace-only run drops",
      fastmarkdown::markdownFromStyledText("a b", {{1, 2, MarkBold}}),
      "a b\n");
  // The pad space survives the code-span round trip (CommonMark strips one
  // leading/trailing space pair), so the content keeps its edge spaces.
  expectStyledRoundTrip(
      "styled code run keeps spaces", "x y z", {{1, 4, MarkInlineCode}});

  // --- Editor line blocks (E3) ---
  using fastmarkdown::EditorBlockType;
  using fastmarkdown::EditorLine;
  const EditorLine P = {EditorBlockType::Paragraph, 0};
  const EditorLine Q = {EditorBlockType::Quote, 0};
  const EditorLine C = {EditorBlockType::Code, 0};
  const EditorLine UL = {EditorBlockType::Bullet, 0};
  const EditorLine OL = {EditorBlockType::Ordered, 0};

  expectString(
      "editor heading line",
      fastmarkdown::markdownFromEditor(
          "Title\nbody", {}, {{EditorBlockType::Heading, 2}, P}),
      "## Title\n\nbody\n");
  expectString(
      "editor quote lines merge",
      fastmarkdown::markdownFromEditor("a\nb", {}, {Q, Q}),
      "> a\n>\n> b\n");
  expectString(
      "editor code lines merge raw",
      fastmarkdown::markdownFromEditor(
          "x = 1\ny *= 2", {{0, 5, MarkBold}}, {C, C}),
      "```\nx = 1\ny *= 2\n```\n");
  expectString(
      "editor bullet list",
      fastmarkdown::markdownFromEditor("a\nb", {}, {UL, UL}),
      "- a\n- b\n");
  expectString(
      "editor ordered list",
      fastmarkdown::markdownFromEditor("a\nb", {}, {OL, OL}),
      "1. a\n2. b\n");
  expectString(
      "editor mixed blocks",
      fastmarkdown::markdownFromEditor(
          "Title\nintro\nitem", {{0, 5, MarkBold}},
          {{EditorBlockType::Heading, 1}, P, UL}),
      "# **Title**\n\nintro\n\n- item\n");

  expectEditorRoundTrip("editor rt paragraphs", "one\ntwo", {}, {P, P});
  expectEditorRoundTrip(
      "editor rt heading levels",
      "h1\nh6",
      {},
      {{EditorBlockType::Heading, 1}, {EditorBlockType::Heading, 6}});
  expectEditorRoundTrip("editor rt quote", "quoted\nlines", {}, {Q, Q});
  expectEditorRoundTrip(
      "editor rt code block", "const x = 1;\nreturn x;", {}, {C, C});
  expectEditorRoundTrip("editor rt bullets", "a\nb\nc", {}, {UL, UL, UL});
  expectEditorRoundTrip("editor rt ordered", "a\nb", {}, {OL, OL});
  expectEditorRoundTrip(
      "editor rt marked list items",
      "bold item\nplain",
      {{0, 4, MarkBold}},
      {UL, UL});
  expectEditorRoundTrip(
      "editor rt block transitions",
      "head\npara\nquote\ncode\nitem one\nitem two\ntail",
      {},
      {{EditorBlockType::Heading, 3}, P, Q, C, UL, UL, P});

  expectString(
      "editor extraction with blocks",
      dumpEditor(fastmarkdown::editorFromMarkdown(
          "# Title\n\n- a\n- **b**\n\n> q\n\n```\nx\n```")),
      "\"Title\na\nb\nq\nx\" 8:9:1 | 1.1 4 4 2 3");

  // --- Editor links + mentions (E4) ---
  expectString(
      "editor link run serializes",
      fastmarkdown::markdownFromEditor(
          "visit the docs now", {}, {}, {{6, 14, "https://x.dev"}}),
      "visit [the docs](https://x.dev) now\n");
  expectString(
      "editor mention link",
      fastmarkdown::markdownFromEditor(
          "ping @ali ok", {}, {}, {{5, 9, "users://ali"}}),
      "ping [@ali](users://ali) ok\n");
  expectString(
      "editor marked link label",
      fastmarkdown::markdownFromEditor(
          "see docs here",
          {{4, 8, fastmarkdown::MarkBold}},
          {},
          {{4, 8, "https://x.dev"}}),
      "see [**docs**](https://x.dev) here\n");
  expectString(
      "editor link extraction",
      dumpEditor(fastmarkdown::editorFromMarkdown(
          "visit [the docs](https://x.dev) and [@ali](users://ali)")),
      "\"visit the docs and @ali\" | 0 [6:14 https://x.dev] [19:23 users://ali]");
  expectString(
      "editor autolink extraction",
      dumpEditor(fastmarkdown::editorFromMarkdown("see https://x.dev now")),
      "\"see https://x.dev now\" | 0 [4:17 https://x.dev]");
  expectString(
      "editor link in list item",
      fastmarkdown::markdownFromEditor(
          "docs\nother",
          {},
          {{fastmarkdown::EditorBlockType::Bullet, 0},
           {fastmarkdown::EditorBlockType::Bullet, 0}},
          {{0, 4, "https://x.dev"}}),
      "- [docs](https://x.dev)\n- other\n");

  std::printf("%d/%d passed\n", g_total - g_failures, g_total);
  return g_failures == 0 ? 0 : 1;
}
