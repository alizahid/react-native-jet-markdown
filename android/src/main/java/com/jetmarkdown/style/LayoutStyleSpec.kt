package com.jetmarkdown.style

import com.jetmarkdown.style.TextStyleSpec.Companion.optColor
import org.json.JSONObject

/** One element's box style from stylesJson (dp values). */
class LayoutStyleSpec(
  val backgroundColor: Int?,
  val paddingLeft: Float,
  val paddingRight: Float,
  val paddingTop: Float,
  val paddingBottom: Float,
  val borderRadius: Float,
  val borderLeftColor: Int?,
  val borderLeftWidth: Float,
  val borderRightColor: Int?,
  val borderRightWidth: Float,
  val borderTopColor: Int?,
  val borderTopWidth: Float,
  val borderBottomColor: Int?,
  val borderBottomWidth: Float,
) {
  /** Converts dp values to px; colors unchanged. */
  fun scaled(density: Float): LayoutStyleSpec = LayoutStyleSpec(
    backgroundColor = backgroundColor,
    paddingLeft = paddingLeft * density,
    paddingRight = paddingRight * density,
    paddingTop = paddingTop * density,
    paddingBottom = paddingBottom * density,
    borderRadius = borderRadius * density,
    borderLeftColor = borderLeftColor,
    borderLeftWidth = borderLeftWidth * density,
    borderRightColor = borderRightColor,
    borderRightWidth = borderRightWidth * density,
    borderTopColor = borderTopColor,
    borderTopWidth = borderTopWidth * density,
    borderBottomColor = borderBottomColor,
    borderBottomWidth = borderBottomWidth * density,
  )

  companion object {
    val EMPTY = LayoutStyleSpec(null, 0f, 0f, 0f, 0f, 0f, null, 0f, null, 0f, null, 0f, null, 0f)

    fun from(json: JSONObject?, defaults: LayoutStyleSpec = EMPTY): LayoutStyleSpec {
      if (json == null) {
        return defaults
      }
      return LayoutStyleSpec(
        backgroundColor = json.optColor("backgroundColor") ?: defaults.backgroundColor,
        paddingLeft = json.optDp("paddingLeft", defaults.paddingLeft),
        paddingRight = json.optDp("paddingRight", defaults.paddingRight),
        paddingTop = json.optDp("paddingTop", defaults.paddingTop),
        paddingBottom = json.optDp("paddingBottom", defaults.paddingBottom),
        borderRadius = json.optDp("borderRadius", defaults.borderRadius),
        borderLeftColor = json.optColor("borderLeftColor") ?: defaults.borderLeftColor,
        borderLeftWidth = json.optDp("borderLeftWidth", defaults.borderLeftWidth),
        borderRightColor = json.optColor("borderRightColor") ?: defaults.borderRightColor,
        borderRightWidth = json.optDp("borderRightWidth", defaults.borderRightWidth),
        borderTopColor = json.optColor("borderTopColor") ?: defaults.borderTopColor,
        borderTopWidth = json.optDp("borderTopWidth", defaults.borderTopWidth),
        borderBottomColor = json.optColor("borderBottomColor") ?: defaults.borderBottomColor,
        borderBottomWidth = json.optDp("borderBottomWidth", defaults.borderBottomWidth),
      )
    }

    private fun JSONObject.optDp(key: String, fallback: Float): Float {
      val value = optDouble(key)
      return if (value.isNaN()) fallback else value.toFloat()
    }
  }
}
