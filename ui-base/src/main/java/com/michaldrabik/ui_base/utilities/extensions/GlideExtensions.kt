package com.michaldrabik.ui_base.utilities.extensions

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.security.MessageDigest

inline fun RequestBuilder<Drawable>.withFailListener(crossinline action: () -> Unit) =
  addListener(object : RequestListener<Drawable?> {
    override fun onLoadFailed(
      e: GlideException?,
      model: Any?,
      target: Target<Drawable?>,
      isFirstResource: Boolean,
    ): Boolean {
      action()
      return false
    }

    override fun onResourceReady(
      resource: Drawable?,
      model: Any,
      target: Target<Drawable?>?,
      dataSource: DataSource,
      isFirstResource: Boolean,
    ): Boolean = false
  })

inline fun RequestBuilder<Drawable>.withSuccessListener(crossinline action: () -> Unit) =
  addListener(object : RequestListener<Drawable?> {
    override fun onLoadFailed(
      e: GlideException?,
      model: Any?,
      target: Target<Drawable?>,
      isFirstResource: Boolean,
    ): Boolean = false

    override fun onResourceReady(
      resource: Drawable?,
      model: Any,
      target: Target<Drawable?>?,
      dataSource: DataSource,
      isFirstResource: Boolean,
    ): Boolean {
      action()
      return false
    }
  })

fun Bitmap.addOutline(strokeWidthPx: Float): Bitmap {
  val alphaMask = extractAlpha()

  val padding = (strokeWidthPx * 1.5f).toInt()
  val newWidth = width + (padding * 2)
  val newHeight = height + (padding * 2)

  val output = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(output)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
  }

  // Draw the silhouette in a full circle to create a solid, thick outline
  val radius = strokeWidthPx.toInt()
  for (x in -radius..radius) {
    for (y in -radius..radius) {
      if (x * x + y * y <= radius * radius) {
        canvas.drawBitmap(alphaMask, padding + x.toFloat(), padding + y.toFloat(), paint)
      }
    }
  }

  // Draw the original colored logo on top
  canvas.drawBitmap(this, padding.toFloat(), padding.toFloat(), null)

  alphaMask.recycle()
  return output
}

class OutlineTransformation(private val strokeWidth: Float) : BitmapTransformation() {
  override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
    return toTransform.addOutline(strokeWidth)
  }

  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update("OutlineTransformation_$strokeWidth".toByteArray())
  }
}
