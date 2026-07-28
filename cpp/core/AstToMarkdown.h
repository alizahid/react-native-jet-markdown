#pragma once

#include <string>

#include "Ast.h"

namespace jetmarkdown {

// Serializes an AST back to markdown text such that re-parsing the output
// yields an identical AST (the round-trip law, covered by golden tests).
// Inline text is escaped so literal characters survive: parse(serialize(x))
// == x even when the original spelling differs (e.g. Reddit ^word
// superscripts normalize to ^word^).
std::string astToMarkdown(const Node* root);

} // namespace jetmarkdown
