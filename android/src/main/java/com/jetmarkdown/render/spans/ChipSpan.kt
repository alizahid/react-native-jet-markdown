package com.jetmarkdown.render.spans

/**
 * Data-only marker for a drawn run background ("chip"): BlockTextView fills
 * a rounded rect behind the run, sized from the run's real font metrics so
 * ascenders and descenders are always covered (TextPaint.bgColor and
 * NSBackgroundColor both misalign under custom line heights).
 */
class ChipSpan(
  val color: Int,
  val radiusPx: Float,
  val padLeftPx: Float,
  val padRightPx: Float,
  /** Run font, for measuring glyph ink bounds at draw time. */
  val typeface: android.graphics.Typeface?,
  val textSizePx: Float,
)
