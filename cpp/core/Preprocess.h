#pragma once

#include <string>

namespace jetmarkdown {

// Escapes a line-leading ">" when it opens a Reddit spoiler (">!") so md4c
// does not consume it as a blockquote marker. Runs before md4c; the inline
// extension scanner then recognizes ">!...!<" as a spoiler in plain text.
// Skips fenced code blocks and lines indented 4+ spaces (potential indented
// code). All other spoiler handling happens post-parse.
std::string preprocessMarkdown(const std::string& input);

} // namespace jetmarkdown
