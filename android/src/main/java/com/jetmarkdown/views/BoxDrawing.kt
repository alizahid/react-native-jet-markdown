package com.jetmarkdown.views

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.jetmarkdown.style.LayoutStyleSpec

/** Shared background + per-side border painting for block containers. */
object BoxDrawing {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  fun draw(canvas: Canvas, style: LayoutStyleSpec, width: Float, height: Float) {
    if (style.backgroundColor != null) {
      paint.style = Paint.Style.FILL
      paint.color = style.backgroundColor
      canvas.drawRoundRect(
        RectF(0f, 0f, width, height),
        style.borderRadius,
        style.borderRadius,
        paint,
      )
    }
    paint.style = Paint.Style.FILL
    if (style.borderLeftWidth > 0 && style.borderLeftColor != null) {
      paint.color = style.borderLeftColor
      if (style.borderRadius > 0) {
        canvas.drawRoundRect(
          RectF(0f, 0f, style.borderLeftWidth, height),
          style.borderRadius / 2,
          style.borderRadius / 2,
          paint,
        )
      } else {
        canvas.drawRect(0f, 0f, style.borderLeftWidth, height, paint)
      }
    }
    if (style.borderRightWidth > 0 && style.borderRightColor != null) {
      paint.color = style.borderRightColor
      canvas.drawRect(width - style.borderRightWidth, 0f, width, height, paint)
    }
    if (style.borderTopWidth > 0 && style.borderTopColor != null) {
      paint.color = style.borderTopColor
      canvas.drawRect(0f, 0f, width, style.borderTopWidth, paint)
    }
    if (style.borderBottomWidth > 0 && style.borderBottomColor != null) {
      paint.color = style.borderBottomColor
      canvas.drawRect(0f, height - style.borderBottomWidth, width, height, paint)
    }
  }
}
