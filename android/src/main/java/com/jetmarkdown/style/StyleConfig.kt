package com.jetmarkdown.style

import com.jetmarkdown.style.TextStyleSpec.Companion.optColor
import java.util.regex.Pattern
import org.json.JSONObject

class MentionVariant(val pattern: Pattern, val style: TextStyleSpec?)

/**
 * Parsed stylesJson with defaults, cached per JSON string.
 */
class StyleConfig private constructor(private val root: JSONObject) {
  private val main: JSONObject? = root.optJSONObject("main")
  private val textStyles = HashMap<String, TextStyleSpec?>()

  // Container style-prop gap wins; the styles prop (defaultStyles.gap)
  // supplies the themed value; unstyled floor is 0.
  val gap: Float = main.optFloatOr("gap", root.optFloatOr("gap", 0f))
  val paddingLeft: Float = main.optFloatOr("paddingLeft", 0f)
  val paddingRight: Float = main.optFloatOr("paddingRight", 0f)
  val paddingTop: Float = main.optFloatOr("paddingTop", 0f)
  val paddingBottom: Float = main.optFloatOr("paddingBottom", 0f)
  val backgroundColor: Int? = main?.optColor("backgroundColor")

  /** Ordered longest-pattern-first; a link whose URL matches is a mention. */
  val mentionVariants: List<MentionVariant> = buildList {
    val variants = root.optJSONObject("mention")?.optJSONArray("variants") ?: return@buildList
    for (i in 0 until variants.length()) {
      val pair = variants.optJSONArray(i) ?: continue
      val patternString = pair.optString(0, "")
      if (patternString.isEmpty()) {
        continue
      }
      val pattern = runCatching { Pattern.compile(patternString) }.getOrNull() ?: continue
      add(MentionVariant(pattern, TextStyleSpec.from(pair.optJSONObject(1))))
    }
  }

  /** Raw JSON section for an element key (layout parsing). */
  fun rawSection(key: String): JSONObject? = root.optJSONObject(key)

  /** User style for an element key; null when not provided. */
  fun textStyleFor(key: String): TextStyleSpec? {
    synchronized(textStyles) {
      if (textStyles.containsKey(key)) {
        return textStyles[key]
      }
      val style = TextStyleSpec.from(root.optJSONObject(key))
      textStyles[key] = style
      return style
    }
  }


  companion object {
    private val cache = HashMap<String, StyleConfig>()

    fun from(stylesJson: String): StyleConfig {
      // Platform colors resolve against the current theme; a dark-mode flip
      // must not serve configs holding light-resolved ints.
      val cacheKey = "${PlatformColorResolver.appearanceKey()}\u001f$stylesJson"
      synchronized(cache) {
        cache[cacheKey]?.let { return it }
        val config = StyleConfig(
          runCatching { JSONObject(stylesJson.ifEmpty { "{}" }) }.getOrElse { JSONObject() }
        )
        if (cache.size > 16) {
          cache.clear()
        }
        cache[cacheKey] = config
        return config
      }
    }

    private fun JSONObject?.optFloatOr(key: String, fallback: Float): Float {
      val value = this?.optDouble(key) ?: return fallback
      return if (value.isNaN()) fallback else value.toFloat()
    }
  }
}
