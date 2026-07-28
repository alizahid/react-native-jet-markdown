package com.jetmarkdown.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan

/** Inline mark bits; mirrors jetmarkdown::EditorMark in cpp/core/EditorRuns.h. */
object EditorMarks {
  const val BOLD = 1
  const val ITALIC = 1 shl 1
  const val STRIKETHROUGH = 1 shl 2
  const val INLINE_CODE = 1 shl 3
  const val SPOILER = 1 shl 4
  const val SUPERSCRIPT = 1 shl 5
  const val SUBSCRIPT = 1 shl 6

  val ALL = intArrayOf(
    BOLD, ITALIC, STRIKETHROUGH, INLINE_CODE, SPOILER, SUPERSCRIPT, SUBSCRIPT,
  )
}

/** Per-line block types; mirrors jetmarkdown::EditorBlockType. */
object EditorBlocks {
  const val HEADING = 1
  const val QUOTE = 2
  const val CODE = 3
  const val BULLET = 4
  const val ORDERED = 5

  fun pack(type: Int, level: Int): Int = (type shl 8) or level

  fun type(packed: Int): Int = packed shr 8

  fun level(packed: Int): Int = packed and 0xFF

}

/** Marker for every derived visual span; removed wholesale on rebuild. */
interface EditorDerivedSpan

/**
 * Data-only source of truth for one inline mark over a range. Visual styling
 * is carried by derived spans rebuilt from these after every change.
 */
class EditorMarkSpan(val mark: Int)

/**
 * Data-only linked range. Mentions carry an app-scheme URL and `atomic`
 * (the token edits as one unit).
 */
class LinkDataSpan(val url: String, val atomic: Boolean)

/** Derived link appearance. */
class LinkDisplaySpan(private val color: Int) :
  android.text.style.CharacterStyle(),
  EditorDerivedSpan {
  override fun updateDrawState(paint: TextPaint) {
    paint.color = color
    paint.isUnderlineText = true
  }
}

/** Derived visual styling for the combined mark flags of a range. */
class EditorDisplaySpan(private val flags: Int) : MetricAffectingSpan(), EditorDerivedSpan {
  override fun updateMeasureState(paint: TextPaint) {
    apply(paint)
  }

  override fun updateDrawState(paint: TextPaint) {
    apply(paint)
    if (flags and EditorMarks.STRIKETHROUGH != 0) {
      paint.isStrikeThruText = true
    }
    if (flags and EditorMarks.INLINE_CODE != 0) {
      paint.bgColor = 0x26808080
    }
    if (flags and EditorMarks.SPOILER != 0) {
      paint.bgColor = 0x40595959
    }
  }

  private fun apply(paint: TextPaint) {
    var style = paint.typeface?.style ?: Typeface.NORMAL
    if (flags and EditorMarks.BOLD != 0) {
      style = style or Typeface.BOLD
    }
    if (flags and EditorMarks.ITALIC != 0) {
      style = style or Typeface.ITALIC
    }
    val base =
      if (flags and EditorMarks.INLINE_CODE != 0) Typeface.MONOSPACE else paint.typeface
    paint.typeface = Typeface.create(base, style)

    // Sup/sub match the viewer's 0.7 scaling.
    if (flags and (EditorMarks.SUPERSCRIPT or EditorMarks.SUBSCRIPT) != 0) {
      val size = paint.textSize
      paint.textSize = size * 0.7f
      if (flags and EditorMarks.SUPERSCRIPT != 0) {
        paint.baselineShift -= (size * 0.33f).toInt()
      } else {
        paint.baselineShift += (size * 0.15f).toInt()
      }
    }
  }
}

/** Heading lines scale up and embolden. */
class HeadingDisplaySpan(level: Int) : MetricAffectingSpan(), EditorDerivedSpan {
  private val scale = when (level) {
    1 -> 2f
    2 -> 1.5f
    3 -> 1.25f
    4 -> 1.125f
    5 -> 1f
    else -> 0.875f
  }

  override fun updateMeasureState(paint: TextPaint) = apply(paint)

  override fun updateDrawState(paint: TextPaint) = apply(paint)

  private fun apply(paint: TextPaint) {
    paint.textSize = paint.textSize * scale
    paint.isFakeBoldText = true
  }
}

/**
 * Code lines: monospace glyphs. The full-width background stripe is drawn
 * by the view (spans cannot cover empty lines).
 */
class CodeLineDisplaySpan : MetricAffectingSpan(), EditorDerivedSpan {
  override fun updateMeasureState(paint: TextPaint) {
    paint.typeface = Typeface.MONOSPACE
  }

  override fun updateDrawState(paint: TextPaint) {
    paint.typeface = Typeface.MONOSPACE
  }
}

/** Quote lines: leading margin with a vertical bar. */
class QuoteDisplaySpan(private val density: Float) : LeadingMarginSpan, EditorDerivedSpan {
  override fun getLeadingMargin(first: Boolean): Int = (16 * density).toInt()

  override fun drawLeadingMargin(
    canvas: Canvas,
    paint: Paint,
    x: Int,
    dir: Int,
    top: Int,
    baseline: Int,
    bottom: Int,
    text: CharSequence,
    start: Int,
    end: Int,
    first: Boolean,
    layout: Layout,
  ) {
    val previous = paint.color
    val width = 3 * density
    paint.color = (paint.color and 0x00FFFFFF) or -0x67000000
    val barLeft = x + dir * 4 * density
    canvas.drawRect(barLeft, top.toFloat(), barLeft + dir * width, bottom.toFloat(), paint)
    paint.color = previous
  }
}

/** Line-height span the display refresh can remove wholesale. */
class EditorLineHeightSpan(heightPx: Int) :
  com.jetmarkdown.render.spans.MarkdownLineHeightSpan(heightPx),
  EditorDerivedSpan

/**
 * No-op run splitter for a heading's newline: keeps it out of the
 * heading's font run so the trailing empty line's caret doesn't inherit
 * heading metrics.
 */
class NewlineResetSpan : MetricAffectingSpan(), EditorDerivedSpan {
  override fun updateMeasureState(paint: TextPaint) = Unit

  override fun updateDrawState(paint: TextPaint) = Unit
}

/** List lines: leading margin drawing the bullet or the item number. */
class ListMarkerDisplaySpan(
  private val marker: String,
  private val density: Float,
) : LeadingMarginSpan, EditorDerivedSpan {
  override fun getLeadingMargin(first: Boolean): Int = (28 * density).toInt()

  override fun drawLeadingMargin(
    canvas: Canvas,
    paint: Paint,
    x: Int,
    dir: Int,
    top: Int,
    baseline: Int,
    bottom: Int,
    text: CharSequence,
    start: Int,
    end: Int,
    first: Boolean,
    layout: Layout,
  ) {
    if (!first) {
      return
    }
    val width = paint.measureText(marker)
    val position = x + dir * ((24 * density) - width - (6 * density))
    canvas.drawText(marker, position, baseline.toFloat(), paint)
  }
}
