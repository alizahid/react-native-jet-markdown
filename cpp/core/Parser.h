#pragma once

#include <memory>
#include <string>

#include "Ast.h"

namespace jetmarkdown {

// Parses markdown into an AST: preprocess (spoiler escaping) -> md4c ->
// inline extension scan (spoilers, strikethrough, superscript, subscript).
// Never returns nullptr; on parser failure the document contains a single
// paragraph with the raw input as plain text.
std::unique_ptr<MarkdownDocument> parseMarkdown(const std::string& markdown);

} // namespace jetmarkdown
