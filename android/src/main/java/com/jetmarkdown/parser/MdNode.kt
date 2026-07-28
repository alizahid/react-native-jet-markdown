package com.jetmarkdown.parser

enum class MdNodeType(val id: Int) {
  DOCUMENT(0),
  HEADING(1),
  PARAGRAPH(2),
  BLOCK_QUOTE(3),
  CODE_BLOCK(4),
  LIST(5),
  LIST_ITEM(6),
  TABLE(7),
  TABLE_ROW(8),
  TABLE_CELL(9),
  IMAGE(10),
  THEMATIC_BREAK(11),
  TEXT(12),
  SOFT_BREAK(13),
  HARD_BREAK(14),
  BOLD(15),
  ITALIC(16),
  STRIKETHROUGH(17),
  LINK(18),
  INLINE_CODE(19),
  SPOILER(20),
  SUPERSCRIPT(21),
  SUBSCRIPT(22);

  companion object {
    private val byId = entries.associateBy { it.id }

    fun from(id: Int): MdNodeType = byId[id] ?: TEXT
  }
}

class MdNode(
  val type: MdNodeType,
  val text: String,
  val url: String,
  val level: Int,
  val ordered: Boolean,
  val startIndex: Int,
  val children: List<MdNode>,
)
