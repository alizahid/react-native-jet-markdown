package com.jetmarkdown.measure

import android.content.res.Resources
import com.jetmarkdown.render.ContentCache
import com.jetmarkdown.style.StyleConfig
import org.json.JSONObject

/**
 * Called from the C++ shadow node (via JNI) on the Fabric layout thread.
 * Receives dp, works in px, returns dp.
 */
object MarkdownMeasurer {
  @JvmName("measure")
  fun measure(
    markdown: ByteArray,
    stylesJson: ByteArray,
    imagesJson: ByteArray,
    maxWidth: Float,
    fontScale: Float,
  ): Float {
    val markdownString = String(markdown, Charsets.UTF_8)
    val stylesString = String(stylesJson, Charsets.UTF_8)
    val imagesString = String(imagesJson, Charsets.UTF_8)

    val density = Resources.getSystem().displayMetrics.density
    val styles = StyleConfig.from(stylesString)
    val content = ContentCache.get(markdownString, stylesString, fontScale)

    val horizontalPadding = styles.paddingLeft + styles.paddingRight
    val contentWidthPx = ((maxWidth - horizontalPadding) * density).toInt()
    if (contentWidthPx <= 0) {
      return 0f
    }

    val layout = content.layoutFor(contentWidthPx, parseImageSizes(imagesString))
    return layout.totalHeightPx / density
  }

  /** {"url":[w,h],...} in dp. */
  fun parseImageSizes(json: String): Map<String, FloatArray> {
    if (json.isEmpty() || json == "{}") {
      return emptyMap()
    }
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
    val sizes = HashMap<String, FloatArray>()
    for (key in root.keys()) {
      val pair = root.optJSONArray(key) ?: continue
      if (pair.length() == 2) {
        sizes[key] = floatArrayOf(pair.optDouble(0).toFloat(), pair.optDouble(1).toFloat())
      }
    }
    return sizes
  }
}
