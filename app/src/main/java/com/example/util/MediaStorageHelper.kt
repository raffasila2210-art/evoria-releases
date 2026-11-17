package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PersistedMedia(
  val localFileUri: String,
  val mediaType: String, // "photo" | "video"
  val displayName: String,
  val sizeBytes: Long,
)

object MediaStorageHelper {
  private const val MEDIA_DIR = "evoria_media"

  fun queryDisplayName(context: Context, uri: Uri): String {
    var displayName = uri.lastPathSegment?.substringAfterLast("/") ?: "Audio"
    runCatching {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIdx >= 0) {
            displayName = cursor.getString(nameIdx) ?: displayName
          }
        }
      }
    }
    return displayName
  }

  fun persistUriToAppStorage(context: Context, uri: Uri): Result<PersistedMedia> = runCatching {
    val resolver = context.contentResolver
    val displayName = queryDisplayName(context, uri).ifBlank { "Kenangan" }
    var sizeBytes = 0L
    runCatching {
      resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
        }
      }
    }

    val mime = resolver.getType(uri).orEmpty().lowercase()
    val nameOrPathLower = "$displayName ${uri.lastPathSegment.orEmpty()}".lowercase()
    val isVideoExt =
      listOf(".mp4", ".mov", ".mkv", ".webm", ".3gp", ".avi").any { nameOrPathLower.contains(it) }
    val isAudioExt =
      listOf(".mp3", ".m4a", ".wav", ".ogg", ".aac", ".flac").any { nameOrPathLower.contains(it) }
    val mediaType =
      when {
        mime.startsWith("video/") || isVideoExt -> "video"
        mime.startsWith("audio/") || isAudioExt -> "audio"
        else -> "photo"
      }
    val ext =
      when {
        mime.contains("png") || nameOrPathLower.endsWith(".png") -> "png"
        mime.contains("webp") || nameOrPathLower.endsWith(".webp") -> "webp"
        mime.contains("mpeg") || mime.contains("mp3") || nameOrPathLower.endsWith(".mp3") -> "mp3"
        mime.contains("wav") || nameOrPathLower.endsWith(".wav") -> "wav"
        mime.contains("ogg") || nameOrPathLower.endsWith(".ogg") -> "ogg"
        mime.contains("aac") || nameOrPathLower.endsWith(".aac") -> "aac"
        mime.contains("flac") || nameOrPathLower.endsWith(".flac") -> "flac"
        mime.contains("mp4") || mime.contains("m4a") -> if (mediaType == "audio") "m4a" else "mp4"
        mime.contains("3gpp") -> "3gp"
        mediaType == "video" -> "mp4"
        mediaType == "audio" -> "mp3"
        else -> "jpg"
      }

    val dir = File(context.filesDir, MEDIA_DIR).apply { if (!exists()) mkdirs() }
    val outFile = File(dir, "evo_${UUID.randomUUID().toString().take(12)}.$ext")

    resolver.openInputStream(uri)?.use { input ->
      FileOutputStream(outFile).use { output -> input.copyTo(output) }
    } ?: throw IllegalStateException("Tidak dapat membaca file media dari URI.")

    if (sizeBytes <= 0L) sizeBytes = outFile.length()
    PersistedMedia(
      localFileUri = Uri.fromFile(outFile).toString(),
      mediaType = mediaType,
      displayName = displayName,
      sizeBytes = sizeBytes,
    )
  }

  fun deletePersistedFile(uriString: String?) {
    if (uriString.isNullOrBlank()) return
    runCatching {
      val uri = Uri.parse(uriString)
      if (uri.scheme == "file") {
        uri.path?.let { File(it).delete() }
      }
    }
  }

  fun calculateStorageBytes(context: Context): Long {
    val dir = File(context.filesDir, MEDIA_DIR)
    if (!dir.exists()) return 0L
    return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
  }

  fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
  }
}
