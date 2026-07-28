#include "EditorText.h"

#include "EditorRuns.h"

namespace jetmarkdown {

std::string markdownFromPlainText(const std::string& text) {
  return markdownFromStyledText(text, {});
}

std::string plainTextFromMarkdown(const std::string& markdown) {
  return styledTextFromMarkdown(markdown).text;
}

} // namespace jetmarkdown
