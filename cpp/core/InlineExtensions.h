#pragma once

#include "Ast.h"

namespace jetmarkdown {

// Post-parse scan of inline runs adding the syntax md4c does not know:
//   >!spoiler!<  and  ||spoiler||          -> Spoiler
//   ~~strikethrough~~                      -> Strikethrough
//   ~subscript~                            -> Subscript (same run, no spaces)
//   ^sup^  ^word  ^(multi word)            -> Superscript
// Delimiters may span styled runs (e.g. "||bold **x**||") for spoilers and
// strikethrough. Unclosed delimiters stay literal text. Link contents are
// left untouched.
void applyInlineExtensions(MarkdownDocument& doc);

} // namespace jetmarkdown
