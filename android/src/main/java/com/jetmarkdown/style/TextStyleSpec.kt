package com.jetmarkdown.style

import org.json.JSONArray
import org.json.JSONObject

/** One element's text style from stylesJson; null fields inherit. */
class TextStyleSpec(
  val fontSize: Float?,
  val fontWeight: Int?,
  val fontFamily: String?,
  val lineHeight: Float?,
  val color: Int?,
  val fontVariant: List<String>?,
  val textDecorationColor: Int?,
  val textDecorationLine: String?,
  val textDecorationStyle: String?,
  val backgroundColor: Int?,
) {
  companion object {
    fun from(json: JSONObject?): TextStyleSpec? {
      if (json == null) {
        return null
      }
      return TextStyleSpec(
        fontSize = json.optNumber("fontSize"),
        fontWeight = when (val weight = json.optString("fontWeight", "")) {
          "" -> null
          "bold" -> 700
          "normal" -> 400
          else -> weight.toIntOrNull()?.takeIf { it in 100..900 }
        },
        fontFamily = json.optString("fontFamily", "").ifEmpty { null },
        lineHeight = json.optNumber("lineHeight"),
        color = json.optColor("color"),
        fontVariant = (json.optJSONArray("fontVariant"))?.toStringList(),
        textDecorationColor = json.optColor("textDecorationColor"),
        textDecorationLine = json.optString("textDecorationLine", "").ifEmpty { null },
        textDecorationStyle = json.optString("textDecorationStyle", "").ifEmpty { null },
        backgroundColor = json.optColor("backgroundColor"),
      )
    }

    private fun JSONObject.optNumber(key: String): Float? {
      val value = optDouble(key)
      return if (value.isNaN()) null else value.toFloat()
    }

    internal fun JSONObject.optColor(key: String): Int? {
      if (!has(key)) {
        return null
      }
      // Platform colors arrive as {"resource_paths": [...]} descriptors.
      optJSONObject(key)?.let { return PlatformColorResolver.resolve(it) }
      val value = optLong(key, Long.MIN_VALUE)
      return if (value == Long.MIN_VALUE) null else value.toInt()
    }

    private fun JSONArray.toStringList(): List<String> =
      (0 until length()).mapNotNull { optString(it, null) }
  }
}
