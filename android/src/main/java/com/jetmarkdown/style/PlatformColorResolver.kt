package com.jetmarkdown.style

import android.content.Context
import android.util.TypedValue
import java.lang.ref.WeakReference
import org.json.JSONObject

/**
 * Resolves React Native platform-color descriptors ({"resource_paths":
 * ["@color/x", "?attr/y", ...]}) against the current theme. The context is
 * registered by the view managers (a ThemedReactContext, so ?attr lookups
 * see the activity theme) and held weakly.
 */
object PlatformColorResolver {
  @Volatile private var contextRef: WeakReference<Context>? = null

  fun install(context: Context) {
    contextRef = WeakReference(context)
  }

  /** The registered themed context (font/color resolution off-view). */
  fun current(): Context? = contextRef?.get()

  /** Night-mode bit of the registered context; part of style cache keys. */
  fun appearanceKey(): Int {
    val context = contextRef?.get() ?: return 0
    return context.resources.configuration.uiMode and
      android.content.res.Configuration.UI_MODE_NIGHT_MASK
  }

  fun resolve(json: JSONObject): Int? {
    val paths = json.optJSONArray("resource_paths") ?: return null
    val context = contextRef?.get() ?: return null
    for (index in 0 until paths.length()) {
      val resolved = resolvePath(context, paths.optString(index, ""))
      if (resolved != null) {
        return resolved
      }
    }
    return null
  }

  private fun resolvePath(context: Context, path: String): Int? {
    if (path.isEmpty()) {
      return null
    }
    if (path.startsWith("?")) {
      val name = path.removePrefix("?").removePrefix("attr/")
      val attrId = context.resources.getIdentifier(name, "attr", context.packageName)
      if (attrId == 0) {
        return null
      }
      val out = TypedValue()
      if (!context.theme.resolveAttribute(attrId, out, true)) {
        return null
      }
      if (out.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
        return out.data
      }
      return runCatching {
        context.resources.getColor(out.resourceId, context.theme)
      }.getOrNull()
    }
    if (path.startsWith("@")) {
      // "@color/name" or "@android:color/name".
      val spec = path.removePrefix("@")
      val pkg = if (spec.contains(":")) spec.substringBefore(":") else context.packageName
      val name = spec.substringAfter(":").removePrefix("color/")
      val id = context.resources.getIdentifier(name, "color", pkg)
      if (id == 0) {
        return null
      }
      return runCatching { context.resources.getColor(id, context.theme) }.getOrNull()
    }
    return null
  }
}
