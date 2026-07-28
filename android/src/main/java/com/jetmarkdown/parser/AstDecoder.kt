package com.jetmarkdown.parser

/**
 * Decodes the flat binary AST produced by cpp/core/AstSerializer.cpp.
 * Layout per node (little endian):
 *   u8 type, u8 level, u8 flags (bit 0 ordered), i32 startIndex,
 *   u32 textLen + bytes, u32 urlLen + bytes, u32 childCount, children...
 */
object AstDecoder {
  fun decode(bytes: ByteArray): MdNode {
    if (bytes.isEmpty()) {
      return MdNode(MdNodeType.DOCUMENT, "", "", 0, false, 1, emptyList())
    }
    return Reader(bytes).readNode()
  }

  private class Reader(private val bytes: ByteArray) {
    private var pos = 0

    fun readNode(): MdNode {
      val type = MdNodeType.from(readU8())
      val level = readU8()
      val flags = readU8()
      val startIndex = readI32()
      val text = readString()
      val url = readString()
      val childCount = readI32()
      val children = if (childCount > 0) {
        ArrayList<MdNode>(childCount).also { list ->
          repeat(childCount) { list.add(readNode()) }
        }
      } else {
        emptyList()
      }
      return MdNode(type, text, url, level, flags and 1 == 1, startIndex, children)
    }

    private fun readU8(): Int = bytes[pos++].toInt() and 0xFF

    private fun readI32(): Int {
      val value = (bytes[pos].toInt() and 0xFF) or
        ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
        ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
        ((bytes[pos + 3].toInt() and 0xFF) shl 24)
      pos += 4
      return value
    }

    private fun readString(): String {
      val length = readI32()
      if (length == 0) {
        return ""
      }
      val value = String(bytes, pos, length, Charsets.UTF_8)
      pos += length
      return value
    }
  }
}
