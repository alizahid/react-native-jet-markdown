#pragma once

#include <string>

namespace jetmarkdown {

// E1 editor bridge: the editor holds plain text until inline marks (E2) and
// block formatting (E3) attach structure.

// Lines become paragraphs (blank lines separate them) and text is escaped,
// so literal markdown characters the user typed survive as literals.
std::string markdownFromPlainText(const std::string& text);

// Flattens parsed markdown to the editor's plain-text representation:
// blocks become lines; inline formatting reduces to its text content.
std::string plainTextFromMarkdown(const std::string& markdown);

} // namespace jetmarkdown
