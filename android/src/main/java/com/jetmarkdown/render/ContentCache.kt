package com.jetmarkdown.render

import android.content.res.Resources
import android.util.LruCache
import com.jetmarkdown.JetMarkdownNative
import com.jetmarkdown.style.StyleConfig

/**
 * Shared parse/render cache. The Fabric measurer populates it on the layout
 * thread; the mounted view reads the same entry on the main thread, so a
 * mount never re-parses.
 */
object ContentCache {
  private data class Key(
    val markdown: String,
    val stylesJson: String,
    val fontScale: Float,
    // Rendered spans hold theme-resolved platform colors; a dark-mode flip
    // must not serve light-resolved content.
    val appearance: Int,
  )

  private val cache = object : LruCache<Key, RenderedContent>(64) {}

  fun get(markdown: String, stylesJson: String, fontScale: Float): RenderedContent {
    val key = Key(
      markdown,
      stylesJson,
      fontScale,
      com.jetmarkdown.style.PlatformColorResolver.appearanceKey(),
    )
    synchronized(cache) {
      cache.get(key)?.let { return it }
    }

    val density = Resources.getSystem().displayMetrics.density
    val styles = StyleConfig.from(stylesJson)
    val root = JetMarkdownNative.parseMarkdown(markdown)
    val blocks = SpannableRenderer.render(root, styles, density, fontScale)
    val content = RenderedContent(
      blocks = blocks,
      gapPx = styles.gap * density,
      topPaddingPx = styles.paddingTop * density,
      bottomPaddingPx = styles.paddingBottom * density,
      density = density,
    )

    synchronized(cache) {
      cache.put(key, content)
    }
    return content
  }
}
