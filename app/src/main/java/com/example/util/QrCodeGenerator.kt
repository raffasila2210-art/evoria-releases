package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {
  fun generateQrBitmap(
    content: String,
    sizePx: Int = 512,
    foregroundColor: Int = Color.parseColor("#352823"),
    backgroundColor: Int = Color.parseColor("#F5F0E8"),
  ): Bitmap? {
    return runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
          for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) foregroundColor else backgroundColor)
          }
        }
        bitmap
      }
      .getOrNull()
  }

  fun extractInviteCode(rawQrText: String?): String? {
    val clean = rawQrText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (clean.contains("evoria://", ignoreCase = true) || clean.contains("code=", ignoreCase = true)) {
      val uri = runCatching { android.net.Uri.parse(clean) }.getOrNull()
      val paramCode = uri?.getQueryParameter("code")?.trim()?.uppercase()
      if (!paramCode.isNullOrBlank() && paramCode.all { it.isLetterOrDigit() } && paramCode.length in 4..16) {
        return paramCode
      }
      val regexMatch = Regex("code=([A-Za-z0-9]{4,16})").find(clean)?.groupValues?.getOrNull(1)
      if (!regexMatch.isNullOrBlank()) return regexMatch.uppercase()
    }
    val upper = clean.uppercase()
    return if (upper.length in 4..16 && upper.all { it.isLetterOrDigit() }) upper else null
  }
}
