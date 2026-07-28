package com.jetmarkdown.render.spans

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * One span per text run carrying the fully-resolved attributes, applied to
 * both measurement and drawing so StaticLayout heights stay exact.
 */
class RunSpan(
  private val typeface: Typeface,
  private val textSizePx: Float,
  private val color: Int,
  private val baselineShiftPx: Int,
  private val fontFeatureSettings: String?,
  private val underline: Boolean,
  private val strikethrough: Boolean,
) : MetricAffectingSpan() {

  override fun updateMeasureState(paint: TextPaint) {
    applyMetrics(paint)
  }

  override fun updateDrawState(paint: TextPaint) {
    applyMetrics(paint)
    paint.color = color
    paint.isUnderlineText = underline
    paint.isStrikeThruText = strikethrough
  }

  private fun applyMetrics(paint: TextPaint) {
    paint.typeface = typeface
    paint.textSize = textSizePx
    paint.baselineShift += baselineShiftPx
    if (fontFeatureSettings != null) {
      paint.fontFeatureSettings = fontFeatureSettings
    }
  }
}
