#include "AstSerializer.h"

namespace jetmarkdown {

namespace {

void writeU32(std::vector<uint8_t>& out, uint32_t value) {
  out.push_back(static_cast<uint8_t>(value & 0xFF));
  out.push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
  out.push_back(static_cast<uint8_t>((value >> 16) & 0xFF));
  out.push_back(static_cast<uint8_t>((value >> 24) & 0xFF));
}

void writeNode(std::vector<uint8_t>& out, const Node* node) {
  out.push_back(static_cast<uint8_t>(node->type));
  out.push_back(node->level);
  out.push_back(node->ordered ? 1 : 0);
  writeU32(out, static_cast<uint32_t>(node->startIndex));
  writeU32(out, static_cast<uint32_t>(node->text.size()));
  out.insert(out.end(), node->text.begin(), node->text.end());
  writeU32(out, static_cast<uint32_t>(node->url.size()));
  out.insert(out.end(), node->url.begin(), node->url.end());
  writeU32(out, static_cast<uint32_t>(node->children.size()));
  for (const Node* child : node->children) {
    writeNode(out, child);
  }
}

} // namespace

std::vector<uint8_t> serializeAst(const Node* root) {
  std::vector<uint8_t> out;
  if (root != nullptr) {
    out.reserve(1024);
    writeNode(out, root);
  }
  return out;
}

} // namespace jetmarkdown
