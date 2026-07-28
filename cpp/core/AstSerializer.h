#pragma once

#include <cstdint>
#include <vector>

#include "Ast.h"

namespace jetmarkdown {

// Flat little-endian pre-order encoding, decoded by AstDecoder.kt on Android.
// Per node:
//   u8  type
//   u8  level
//   u8  flags        (bit 0: ordered)
//   i32 startIndex
//   u32 textLen, textLen bytes (UTF-8)
//   u32 urlLen,  urlLen bytes (UTF-8)
//   u32 childCount, then children recursively
std::vector<uint8_t> serializeAst(const Node* root);

} // namespace jetmarkdown
