package com.example.ml

import kotlin.math.sqrt

/**
 * Vector normalization, similarity, and compact IEEE-754 Float16 serialization utilities
 * for EVORIA Vision Search Lite.
 */
object EmbeddingMath {

  fun l2Normalize(vector: FloatArray): FloatArray {
    if (vector.isEmpty()) return FloatArray(0)
    var sumSq = 0.0
    for (v in vector) {
      if (v.isFinite()) {
        sumSq += v.toDouble() * v.toDouble()
      }
    }
    if (sumSq <= 1e-12) return FloatArray(vector.size)
    val invNorm = (1.0 / sqrt(sumSq)).toFloat()
    return FloatArray(vector.size) { i ->
      val v = vector[i]
      if (v.isFinite()) v * invNorm else 0f
    }
  }

  /**
   * Computes cosine similarity between two embeddings.
   * Returns 0f when vectors are empty, mismatched, or have zero norm.
   */
  fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.isEmpty() || a.size != b.size) return 0f
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
      val va = a[i]
      val vb = b[i]
      if (!va.isFinite() || !vb.isFinite()) continue
      dot += va.toDouble() * vb.toDouble()
      normA += va.toDouble() * va.toDouble()
      normB += vb.toDouble() * vb.toDouble()
    }
    val denom = sqrt(normA) * sqrt(normB)
    if (denom <= 1e-12) return 0f
    return (dot / denom).toFloat().coerceIn(-1f, 1f)
  }

  /**
   * Encodes a FloatArray into compact little-endian IEEE-754 Float16 bytes (2 bytes per float).
   * A 512-dim CLIP vector occupies exactly 1,024 bytes.
   */
  fun toFloat16ByteArray(vector: FloatArray): ByteArray {
    val out = ByteArray(vector.size * 2)
    for (i in vector.indices) {
      val bits = float32ToFloat16Bits(vector[i]).toInt() and 0xFFFF
      out[i * 2] = (bits and 0xFF).toByte()
      out[i * 2 + 1] = ((bits ushr 8) and 0xFF).toByte()
    }
    return out
  }

  fun fromFloat16ByteArray(bytes: ByteArray): FloatArray {
    if (bytes.isEmpty() || bytes.size % 2 != 0) return FloatArray(0)
    val count = bytes.size / 2
    return FloatArray(count) { i ->
      val lo = bytes[i * 2].toInt() and 0xFF
      val hi = bytes[i * 2 + 1].toInt() and 0xFF
      val bits = ((hi shl 8) or lo).toShort()
      float16BitsToFloat32(bits)
    }
  }

  fun float32ToFloat16Bits(value: Float): Short {
    val fbits = java.lang.Float.floatToIntBits(value)
    val sign = (fbits ushr 16) and 0x8000
    var valBits = (fbits and 0x7FFFFFFF) + 0x1000
    if (valBits >= 0x47800000) {
      if ((fbits and 0x7FFFFFFF) >= 0x47800000) {
        if (valBits < 0x7F800000) {
          return (sign or 0x7C00).toShort()
        }
        return (sign or 0x7C00 or ((fbits and 0x007FFFFF) ushr 13)).toShort()
      }
      return (sign or 0x7BFF).toShort()
    }
    if (valBits >= 0x38800000) {
      return (sign or ((valBits - 0x38000000) ushr 13)).toShort()
    }
    if (valBits < 0x33000000) {
      return sign.toShort()
    }
    valBits = (fbits and 0x7FFFFFFF) ushr 23
    return (sign or (((fbits and 0x007FFFFF or 0x00800000) + (0x800000 ushr (valBits - 102))) ushr (126 - valBits))).toShort()
  }

  fun float16BitsToFloat32(bits: Short): Float {
    val h = bits.toInt() and 0xFFFF
    var mant = h and 0x03FF
    var exp = h and 0x7C00
    if (exp == 0x7C00) {
      exp = 0x3FC00
    } else if (exp != 0) {
      exp += 0x1C000
    } else if (mant != 0) {
      exp = 0x1C400
      do {
        mant = mant shl 1
        exp -= 0x400
      } while ((mant and 0x400) == 0)
      mant = mant and 0x3FF
    }
    return java.lang.Float.intBitsToFloat(((h and 0x8000) shl 16) or ((exp or mant) shl 13))
  }
}
