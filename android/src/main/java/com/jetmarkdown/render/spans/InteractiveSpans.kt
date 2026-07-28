package com.jetmarkdown.render.spans

/** Data-only marker spans; hit-testing and drawing live in BlockTextView. */
class LinkSpan(val url: String)

class SpoilerSpan(
  val id: Int,
  /** Run font, for measuring glyph ink bounds at draw time. */
  val typeface: android.graphics.Typeface?,
  val textSizePx: Float,
)
