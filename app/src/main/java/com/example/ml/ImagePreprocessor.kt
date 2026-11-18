package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/**
 * Memory-safe image decoding, EXIF orientation normalization, center-crop,
 * and CLIP ViT-B/32 NCHW tensor normalization ([1, 3, 224, 224]).
 */
object ImagePreprocessor {

  const val TARGET_INPUT_SIZE = 224

  // Exact normalization parameters from Xenova/clip-vit-base-patch32 preprocessor_config.json
  private val CLIP_MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
  private val CLIP_STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

  /**
   * Decodes and preprocesses a local photo URI into an NCHW FloatArray [3 * 224 * 224].
   * Never decodes full-resolution camera bitmaps into heap memory.
   */
  fun preprocessUriToClipNchw(
    context: Context,
    uriString: String?,
    targetSize: Int = TARGET_INPUT_SIZE,
  ): FloatArray? {
    val raw = uriString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val uri = Uri.parse(raw)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val minEdge = minOf(bounds.outWidth, bounds.outHeight)
    while (minEdge / (sampleSize * 2) >= targetSize) {
      sampleSize *= 2
    }

    val decodeOpts =
      BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    val decoded = openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
      ?: return null

    var oriented: Bitmap? = null
    var cropped: Bitmap? = null
    var scaled: Bitmap? = null
    return try {
      val orientation = readExifOrientation(context, uri)
      oriented = applyExifTransform(decoded, orientation)
      cropped = centerCropSquare(oriented)
      scaled =
        if (cropped.width == targetSize && cropped.height == targetSize) {
          if (cropped.config == Bitmap.Config.ARGB_8888) cropped
          else cropped.copy(Bitmap.Config.ARGB_8888, false)
        } else {
          Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        }
      bitmapToClipNchwFloatArray(scaled, targetSize)
    } finally {
      if (scaled != null && scaled !== cropped && !scaled.isRecycled) scaled.recycle()
      if (cropped != null && cropped !== oriented && !cropped.isRecycled) cropped.recycle()
      if (oriented != null && oriented !== decoded && !oriented.isRecycled) oriented.recycle()
      if (!decoded.isRecycled) decoded.recycle()
    }
  }

  /**
   * Pure pixel-array converter from ARGB_8888 bitmap into CLIP-normalized NCHW FloatArray.
   */
  fun bitmapToClipNchwFloatArray(bitmap: Bitmap, size: Int = TARGET_INPUT_SIZE): FloatArray {
    val safeBitmap =
      if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
      else bitmap.copy(Bitmap.Config.ARGB_8888, false)

    val totalPixels = size * size
    val pixels = IntArray(totalPixels)
    safeBitmap.getPixels(pixels, 0, size, 0, 0, size, size)
    if (safeBitmap !== bitmap && !safeBitmap.isRecycled) {
      safeBitmap.recycle()
    }

    val out = FloatArray(3 * totalPixels)
    val inv255 = 1f / 255f
    val meanR = CLIP_MEAN[0]
    val meanG = CLIP_MEAN[1]
    val meanB = CLIP_MEAN[2]
    val stdR = CLIP_STD[0]
    val stdG = CLIP_STD[1]
    val stdB = CLIP_STD[2]

    for (i in 0 until totalPixels) {
      val argb = pixels[i]
      val r = ((argb ushr 16) and 0xFF) * inv255
      val g = ((argb ushr 8) and 0xFF) * inv255
      val b = (argb and 0xFF) * inv255
      out[i] = (r - meanR) / stdR
      out[totalPixels + i] = (g - meanG) / stdG
      out[2 * totalPixels + i] = (b - meanB) / stdB
    }
    return out
  }

  private fun centerCropSquare(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    if (w == h) return src
    val edge = minOf(w, h)
    val left = (w - edge) / 2
    val top = (h - edge) / 2
    return Bitmap.createBitmap(src, left, top, edge, edge)
  }

  private fun applyExifTransform(src: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.preScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(270f)
        matrix.preScale(-1f, 1f)
      }
      else -> return src
    }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
  }

  private fun readExifOrientation(context: Context, uri: Uri): Int =
    runCatching {
      openStream(context, uri)?.use { stream ->
        ExifInterface(stream).getAttributeInt(
          ExifInterface.TAG_ORIENTATION,
          ExifInterface.ORIENTATION_NORMAL,
        )
      } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

  private fun openStream(context: Context, uri: Uri) =
    when (uri.scheme?.lowercase()) {
      "file", null -> uri.path?.let { File(it) }?.takeIf { it.exists() }?.inputStream()
      else -> context.contentResolver.openInputStream(uri)
    }
}
