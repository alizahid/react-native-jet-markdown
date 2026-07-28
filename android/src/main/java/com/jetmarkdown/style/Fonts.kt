package com.jetmarkdown.style

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import com.facebook.react.common.assets.ReactFontManager

/**
 * Typeface resolution that understands React Native custom fonts.
 * Typeface.create() only knows system families; fonts registered through
 * RN/Expo (assets/fonts, expo-font) resolve via ReactFontManager — the same
 * path RN's own <Text> uses.
 */
object Fonts {
  fun resolve(context: Context?, family: String?, weight: Int, italic: Boolean): Typeface {
    val style = when {
      weight >= 600 && italic -> Typeface.BOLD_ITALIC
      weight >= 600 -> Typeface.BOLD
      italic -> Typeface.ITALIC
      else -> Typeface.NORMAL
    }
    val base = when {
      family == null -> Typeface.DEFAULT
      context != null ->
        ReactFontManager.getInstance().getTypeface(family, style, context.assets)
      else -> Typeface.create(family, style)
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Typeface.create(base, weight, italic)
    } else {
      Typeface.create(base, style)
    }
  }
}
