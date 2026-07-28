#pragma once

#include <string>

#include "Ast.h"

namespace jetmarkdown {

// Compact JSON rendering of the AST for golden tests and debugging.
std::string astToJson(const Node* node);

} // namespace jetmarkdown
