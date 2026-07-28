package com.jetmarkdown

import com.jetmarkdown.measure.MarkdownMeasurer
import com.jetmarkdown.parser.AstDecoder
import com.jetmarkdown.parser.MdNode

/**
 * JNI bridge into the shared C++ core. The natives are linked into the app's
 * `libappmodules.so` (loaded by React Native at startup), so no explicit
 * `System.loadLibrary` is required.
 */
object JetMarkdownNative {
  @Volatile private var installed = false

  fun ensureInstalled() {
    if (!installed) {
      synchronized(this) {
        if (!installed) {
          // libappmodules.so may not be loaded yet at package-construction
          // time; callers retry from later lifecycle points.
          installed = runCatching { installMeasurer(MarkdownMeasurer) }.isSuccess
        }
      }
    }
  }

  fun parseMarkdown(markdown: String): MdNode {
    return AstDecoder.decode(parse(markdown.toByteArray(Charsets.UTF_8)))
  }


  /** Decoded editor content: text, inline-mark runs, line blocks, links. */
  class EditorContent(
    val text: String,
    /** Flat `[start, end, flags]` triples in UTF-16 offsets. */
    val runs: IntArray,
    /** Flat `[type, level]` pairs, one per text line. */
    val lineBlocks: IntArray,
    /** Flat `[start, end]` pairs, aligned with [linkUrls]. */
    val linkRanges: IntArray,
    val linkUrls: List<String>,
  )

  /**
   * Editor: markdown from text + inline-mark runs + per-line blocks +
   * links. Runs are flat `[start, end, flags]` triples in UTF-16 offsets
   * (Spannable indices); lineBlocks are `[type, level]` pairs per line;
   * links are `[start, end]` pairs with URLs joined by '\n'.
   */
  fun markdownFromEditor(
    text: String,
    runs: IntArray,
    lineBlocks: IntArray,
    linkRanges: IntArray = IntArray(0),
    linkUrls: List<String> = emptyList(),
  ): String {
    return markdownFromEditorContent(
      text.toByteArray(Charsets.UTF_8),
      runs,
      lineBlocks,
      linkRanges,
      linkUrls.joinToString("\n").toByteArray(Charsets.UTF_8),
    ).toString(Charsets.UTF_8)
  }

  /**
   * Editor: markdown parsed into editor content. The native payload is
   * `[int32 runCount][triples][int32 lineCount][pairs][int32 linkCount]
   * [(start, end, urlLen) triples][url bytes][utf8 text]`, little-endian.
   */
  fun editorFromMarkdown(markdown: String): EditorContent {
    val bytes = editorFromMarkdownContent(markdown.toByteArray(Charsets.UTF_8))
    val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val runs = IntArray(buffer.int * 3)
    for (i in runs.indices) {
      runs[i] = buffer.int
    }
    val lineBlocks = IntArray(buffer.int * 2)
    for (i in lineBlocks.indices) {
      lineBlocks[i] = buffer.int
    }
    val linkCount = buffer.int
    val linkRanges = IntArray(linkCount * 2)
    val urlLengths = IntArray(linkCount)
    for (i in 0 until linkCount) {
      linkRanges[i * 2] = buffer.int
      linkRanges[i * 2 + 1] = buffer.int
      urlLengths[i] = buffer.int
    }
    val linkUrls = ArrayList<String>(linkCount)
    for (i in 0 until linkCount) {
      val urlBytes = ByteArray(urlLengths[i])
      buffer.get(urlBytes)
      linkUrls.add(urlBytes.toString(Charsets.UTF_8))
    }
    val text = ByteArray(buffer.remaining())
    buffer.get(text)
    return EditorContent(text.toString(Charsets.UTF_8), runs, lineBlocks, linkRanges, linkUrls)
  }

  @JvmStatic private external fun parse(markdown: ByteArray): ByteArray

  @JvmStatic private external fun markdownFromEditorContent(
    text: ByteArray,
    runs: IntArray,
    lineBlocks: IntArray,
    linkRanges: IntArray,
    linkUrls: ByteArray,
  ): ByteArray

  @JvmStatic private external fun editorFromMarkdownContent(markdown: ByteArray): ByteArray

  @JvmStatic private external fun installMeasurer(measurer: MarkdownMeasurer)
}
