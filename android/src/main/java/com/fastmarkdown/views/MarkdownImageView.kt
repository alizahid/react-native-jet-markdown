package com.fastmarkdown.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.fastmarkdown.render.Block

/**
 * One markdown image, loaded through Glide: animated GIF playback,
 * downsampling, memory + disk caches, in-flight request sharing, and
 * cancellation when the view is rebound. Background shows while loading
 * (and stays for broken URLs); rounded corners clip both.
 */
@SuppressLint("AppCompatCustomView")
class MarkdownImageView(context: Context) : ImageView(context) {
  private var block: Block.Image? = null
  private val clipPath = Path()

  var host: MarkdownHost? = null

  init {
    scaleType = ScaleType.FIT_CENTER
    setOnClickListener {
      block?.let { image ->
        val density = resources.displayMetrics.density
        val location = IntArray(2)
        getLocationOnScreen(location)
        host?.onImagePress(
          image.url,
          location[0] / density,
          location[1] / density,
          width / density,
          height / density,
        )
      }
    }
  }

  fun bind(image: Block.Image) {
    block = image
    // Application context: requests outlive transient view detaches during
    // list recycling and are replaced (cancelling the old one) on rebind.
    Glide.with(context.applicationContext)
      .load(image.url.ifEmpty { null })
      .listener(object : RequestListener<Drawable> {
        override fun onLoadFailed(
          e: GlideException?,
          model: Any?,
          target: Target<Drawable>,
          isFirstResource: Boolean,
        ): Boolean = false

        override fun onResourceReady(
          resource: Drawable,
          model: Any,
          target: Target<Drawable>?,
          dataSource: DataSource,
          isFirstResource: Boolean,
        ): Boolean {
          if (block?.url == image.url) {
            // Image pixels map 1:1 to dp (web semantics); reported for
            // cache hits too so a fresh view still resizes un-presized
            // images.
            host?.onImageIntrinsicSize(
              image.url,
              resource.intrinsicWidth.toFloat(),
              resource.intrinsicHeight.toFloat(),
            )
          }
          return false
        }
      })
      .into(this)
    invalidate()
  }

  /**
   * Discard hook (views are recreated wholesale on rebind): stops the
   * in-flight request from delivering into a recycled host and frees GIF
   * buffers early. Transient detaches during scrolling must NOT clear —
   * this is only called when the parent drops the view for good.
   */
  fun unbind() {
    host = null
    block = null
    Glide.with(context.applicationContext).clear(this)
  }

  override fun onDraw(canvas: Canvas) {
    val image = block ?: return
    if (image.borderRadiusPx > 0) {
      clipPath.reset()
      clipPath.addRoundRect(
        RectF(0f, 0f, width.toFloat(), height.toFloat()),
        image.borderRadiusPx,
        image.borderRadiusPx,
        Path.Direction.CW,
      )
      canvas.clipPath(clipPath)
    }
    image.backgroundColor?.let { canvas.drawColor(it) }
    super.onDraw(canvas)
  }
}
