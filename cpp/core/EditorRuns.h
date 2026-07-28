#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace jetmarkdown {

// Inline mark bits carried by editor styled runs. Mirrored by the native
// editors (JMDEditorMarks attribute on iOS, EditorMarkSpan on Android).
enum EditorMark : uint32_t {
  MarkBold = 1u << 0,
  MarkItalic = 1u << 1,
  MarkStrikethrough = 1u << 2,
  MarkInlineCode = 1u << 3,
  MarkSpoiler = 1u << 4,
  MarkSuperscript = 1u << 5,
  MarkSubscript = 1u << 6,
};

// Per-line block types. Mirrored by the native editors (JMDEditorBlock
// attribute on iOS, EditorBlockSpan on Android).
enum class EditorBlockType : uint8_t {
  Paragraph = 0,
  Heading = 1, // level = 1..6
  Quote = 2,
  Code = 3,
  Bullet = 4,
  Ordered = 5,
};

// A contiguous marked range over the editor's text. Offsets are UTF-16 code
// units (NSRange / Spannable indices), converted internally.
struct StyledRun {
  uint32_t start = 0;
  uint32_t end = 0;
  uint32_t flags = 0;
};

struct EditorLine {
  EditorBlockType type = EditorBlockType::Paragraph;
  uint8_t level = 0;
};

// A linked range (mentions are links whose URL carries an app scheme, e.g.
// users://ali). Links never overlap; offsets are UTF-16 code units.
struct LinkRun {
  uint32_t start = 0;
  uint32_t end = 0;
  std::string url;
};

struct StyledText {
  std::string text;
  std::vector<StyledRun> runs;
};

struct EditorDocument {
  std::string text;
  std::vector<StyledRun> runs;
  // One entry per text line (newline-separated); missing entries mean
  // Paragraph.
  std::vector<EditorLine> lines;
  std::vector<LinkRun> links;
  // Running UTF-16 length of `text`, maintained by every append during
  // extraction so run offsets never require an O(N) rescan (which made
  // opening large documents quadratic).
  uint32_t textUtf16Length = 0;
};

// Serializes the editor's content to markdown. Every newline in `text` is a
// line break; `lines` assigns each line its block type, and consecutive
// same-type lines merge into one block (quote, code fence, list).
// Overlapping mark runs are nested by longest extent; inline-code content is
// emitted verbatim; run edges are trimmed past whitespace so emphasis
// delimiters always re-parse. Code lines ignore inline marks.
std::string markdownFromEditor(
    const std::string& text,
    const std::vector<StyledRun>& runs,
    const std::vector<EditorLine>& lines,
    const std::vector<LinkRun>& links = {});

// Parses markdown and flattens it to editor text + mark runs + line blocks
// (the inverse of markdownFromEditor for the editor's subset; unsupported
// structure flattens to paragraph lines).
EditorDocument editorFromMarkdown(const std::string& markdown);

// Marks-only conveniences (all lines are paragraphs).
std::string markdownFromStyledText(
    const std::string& text,
    const std::vector<StyledRun>& runs);
StyledText styledTextFromMarkdown(const std::string& markdown);

} // namespace jetmarkdown
