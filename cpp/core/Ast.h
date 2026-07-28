#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace jetmarkdown {

enum class NodeType : uint8_t {
  Document = 0,
  Heading = 1,
  Paragraph = 2,
  BlockQuote = 3,
  CodeBlock = 4,
  List = 5,
  ListItem = 6,
  Table = 7,
  TableRow = 8,
  TableCell = 9,
  Image = 10,
  ThematicBreak = 11,
  Text = 12,
  SoftBreak = 13,
  HardBreak = 14,
  Bold = 15,
  Italic = 16,
  Strikethrough = 17,
  Link = 18,
  InlineCode = 19,
  Spoiler = 20,
  Superscript = 21,
  Subscript = 22,
};

enum class CellAlign : uint8_t {
  Default = 0,
  Left = 1,
  Center = 2,
  Right = 3,
};

// Node field usage by type:
//   Text:       text = content
//   Image:      text = alt text, url = source
//   CodeBlock:  text = code content, url = info string (language)
//   InlineCode: text = content
//   Link:       url = destination, children = label
//   Heading:    level = 1..6
//   List:       ordered, startIndex
//   TableRow:   level = 1 when header row
//   TableCell:  level = CellAlign
struct Node {
  NodeType type = NodeType::Document;
  std::string text;
  std::string url;
  uint8_t level = 0;
  bool ordered = false;
  // Text nodes born from character escapes/entities. The inline-extension
  // scanner must not read delimiters out of them (the author explicitly
  // de-fanged the characters), so they never merge with neighbours.
  bool verbatim = false;
  int32_t startIndex = 1;
  std::vector<Node*> children;
};

// Owns every node of one parsed document; nodes are freed together.
class AstArena {
 public:
  Node* alloc(NodeType type) {
    nodes_.push_back(std::make_unique<Node>());
    nodes_.back()->type = type;
    return nodes_.back().get();
  }

 private:
  std::vector<std::unique_ptr<Node>> nodes_;
};

struct MarkdownDocument {
  AstArena arena;
  Node* root = nullptr;
};

} // namespace jetmarkdown
