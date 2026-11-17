package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.local.LocalMemoryEntity
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

  fun resolveShareMimeType(memories: List<LocalMemoryEntity>): String {
    val mediaMemories = memories.filter { !it.mediaUri.isNullOrBlank() }
    if (mediaMemories.isEmpty()) return "text/plain"
    val hasVideo = mediaMemories.any { it.mediaType.equals("video", ignoreCase = true) }
    val hasPhoto = mediaMemories.any { !it.mediaType.equals("video", ignoreCase = true) }
    return when {
      hasVideo && hasPhoto -> "*/*"
      hasVideo -> "video/*"
      else -> "image/*"
    }
  }

  fun resolveShareContentUri(context: Context, uriString: String?): Uri? {
    if (uriString.isNullOrBlank()) return null
    val parsed = Uri.parse(uriString)
    return when (parsed.scheme) {
      "content" -> parsed
      "file" -> {
        val path = parsed.path ?: return null
        val file = File(path)
        if (!file.exists()) return null
        runCatching {
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
      }
      else -> null
    }
  }

  fun buildMemoriesShareSummary(memories: List<LocalMemoryEntity>): String {
    if (memories.isEmpty()) return ""
    return memories.take(8).joinToString("\n\n") { mem ->
      buildString {
        append(mem.title.ifBlank { "Kenangan EVORIA" })
        if (mem.locationName.isNotBlank()) append(" (${mem.locationName})")
        val desc = mem.caption.ifBlank { mem.story }
        if (desc.isNotBlank()) {
          append("\n")
          append(desc)
        }
      }
    }
  }

  fun buildShareMemoriesChooserIntent(
    context: Context,
    memories: List<LocalMemoryEntity>,
  ): Intent? {
    if (memories.isEmpty()) return null
    val shareUris =
      ArrayList(
        memories.mapNotNull { mem -> resolveShareContentUri(context, mem.mediaUri) }
      )
    val summaryText = buildMemoriesShareSummary(memories)
    val mimeType = resolveShareMimeType(memories)

    val baseIntent =
      when {
        shareUris.size == 1 -> {
          Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUris.first())
            if (summaryText.isNotBlank()) {
              putExtra(Intent.EXTRA_TEXT, summaryText)
            }
            clipData =
              runCatching { ClipData.newUri(context.contentResolver, "Evoria Media", shareUris.first()) }
                .getOrElse { ClipData.newRawUri("Evoria Media", shareUris.first()) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        }
        shareUris.size > 1 -> {
          Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
            if (summaryText.isNotBlank()) {
              putExtra(Intent.EXTRA_TEXT, summaryText)
            }
            val clip =
              runCatching { ClipData.newUri(context.contentResolver, "Evoria Media", shareUris.first()) }
                .getOrElse { ClipData.newRawUri("Evoria Media", shareUris.first()) }
            for (i in 1 until shareUris.size) {
              clip.addItem(ClipData.Item(shareUris[i]))
            }
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        }
        else -> {
          Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summaryText.ifBlank { "Kenangan dari EVORIA" })
          }
        }
      }

    return Intent.createChooser(
      baseIntent,
      if (shareUris.size > 1) "Bagikan ${shareUris.size} media ke..." else "Bagikan kenangan via...",
    ).apply {
      if (context !is Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }

  fun shareMemoriesViaAllApps(
    context: Context,
    memories: List<LocalMemoryEntity>,
  ): Boolean {
    val chooser = buildShareMemoriesChooserIntent(context, memories) ?: return false
    return runCatching {
      context.startActivity(chooser)
      true
    }.getOrDefault(false)
  }

  data class MediaDetailedInfo(
    val fileName: String,
    val year: String,
    val monthName: String,
    val dayOfMonth: String,
    val dayOfWeek: String,
    val timeFormatted: String,
    val fullDateFormatted: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val resolutionLabel: String,
    val durationLabel: String?,
    val mediaTypeLabel: String,
    val storageLocationLabel: String,
  )

  fun inspectMediaDetails(
    context: Context,
    memory: LocalMemoryEntity,
    nowZone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
  ): MediaDetailedInfo {
    val instant = java.time.Instant.ofEpochMilli(memory.memoryDateMillis)
    val zdt = instant.atZone(nowZone)
    val indoLocale = java.util.Locale("id", "ID")
    val dayOfWeek = zdt.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", indoLocale))
      .replaceFirstChar { it.uppercase() }
    val monthName = zdt.format(java.time.format.DateTimeFormatter.ofPattern("MMMM", indoLocale))
      .replaceFirstChar { it.uppercase() }
    val year = zdt.year.toString()
    val dayOfMonth = zdt.dayOfMonth.toString()
    val timeFormatted = zdt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss", indoLocale))
    val fullDateFormatted = "$dayOfWeek, $dayOfMonth $monthName $year"

    val uriStr = memory.mediaUri
    var sizeBytes = 0L
    var fileName = memory.title.ifBlank { "Kenangan EVORIA" }
    var storagePath = "Penyimpanan internal EVORIA (Lokal)"
    var resolution = "Tidak tersedia"
    var durationLabel: String? = null

    if (!uriStr.isNullOrBlank()) {
      val parsed = Uri.parse(uriStr)
      if (parsed.scheme == "file" && !parsed.path.isNullOrBlank()) {
        val file = File(parsed.path!!)
        storagePath = file.absolutePath
        fileName = file.name
        if (file.exists()) {
          sizeBytes = file.length()
        }
      } else {
        storagePath = uriStr
        fileName = queryDisplayName(context, parsed).ifBlank { fileName }
        runCatching {
          context.contentResolver.query(parsed, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
              val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
              if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
            }
          }
        }
      }

      if (memory.mediaType.equals("video", ignoreCase = true)) {
        runCatching {
          val retriever = android.media.MediaMetadataRetriever()
          try {
            if (parsed.scheme == "file" && !parsed.path.isNullOrBlank()) {
              retriever.setDataSource(parsed.path)
            } else {
              retriever.setDataSource(context, parsed)
            }
            val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (w != null && h != null && w > 0 && h > 0) {
              resolution = "$w × $h px"
            }
            if (dur != null && dur > 0L) {
              val totalSec = dur / 1000L
              val min = totalSec / 60L
              val sec = totalSec % 60L
              durationLabel = String.format(java.util.Locale.US, "%d:%02d", min, sec)
            }
          } finally {
            retriever.release()
          }
        }
      } else if (memory.mediaType.equals("photo", ignoreCase = true) || !memory.mediaUri.isNullOrBlank()) {
        runCatching {
          val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
          if (parsed.scheme == "file" && !parsed.path.isNullOrBlank()) {
            android.graphics.BitmapFactory.decodeFile(parsed.path, opts)
          } else {
            context.contentResolver.openInputStream(parsed)?.use {
              android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
          }
          if (opts.outWidth > 0 && opts.outHeight > 0) {
            resolution = "${opts.outWidth} × ${opts.outHeight} px"
          }
        }
      }
    }

    val typeLabel =
      when (memory.mediaType.lowercase()) {
        "video" -> "Video"
        "story" -> "Cerita Tertulis"
        else -> "Foto"
      }

    return MediaDetailedInfo(
      fileName = fileName,
      year = year,
      monthName = monthName,
      dayOfMonth = dayOfMonth,
      dayOfWeek = dayOfWeek,
      timeFormatted = timeFormatted,
      fullDateFormatted = fullDateFormatted,
      sizeBytes = sizeBytes,
      sizeFormatted = if (sizeBytes > 0L) formatBytes(sizeBytes) else "Belum terukur",
      resolutionLabel = resolution,
      durationLabel = durationLabel,
      mediaTypeLabel = typeLabel,
      storageLocationLabel = storagePath,
    )
  }
}
