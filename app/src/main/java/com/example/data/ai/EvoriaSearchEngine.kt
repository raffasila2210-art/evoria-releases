package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.data.local.LocalMemoryEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class SearchOutcome(
  val matchingMemoryIds: List<String>,
  val usedCloudAi: Boolean,
  val explanation: String,
  val isFallbackNotice: String? = null,
  val detectedTags: List<String> = emptyList(),
  val discoveredTagsByMemoryId: Map<String, List<String>> = emptyMap(),
)

data class AiMemoryStoryDraft(
  val title: String,
  val caption: String,
  val story: String,
  val tagsCsv: String,
)

data class AiReflectionInsight(
  val promptQuestion: String,
  val warmInsight: String,
)

interface JelajahSearchRepository {
  suspend fun searchMemories(
    query: String,
    candidates: List<LocalMemoryEntity>,
    allowCloudAi: Boolean,
    albumTitlesById: Map<String, String> = emptyMap(),
  ): SearchOutcome
}

class EvoriaSearchEngine(
  context: Context? = null,
) : JelajahSearchRepository {

  private val appContext = context?.applicationContext

  private val searchItemSchema =
    Schema.obj(
      properties =
        mapOf(
          "id" to Schema.string(description = "ID kenangan yang cocok."),
          "tags" to
            Schema.array(
              items = Schema.string(),
              description = "2 sampai 4 label objek/suasana visual singkat dalam huruf kecil (mis: pantai, sunset, kopi).",
            ),
        ),
      optionalProperties = listOf("tags"),
    )

  private val searchSchema =
    Schema.obj(
      properties =
        mapOf(
          "matchedIds" to
            Schema.array(
              items = Schema.string(),
              description = "ID kenangan yang paling cocok secara visual maupun kontekstual dengan kueri pengguna.",
            ),
          "matchedItems" to
            Schema.array(
              items = searchItemSchema,
              description = "Rincian ID yang cocok beserta tag visual terdeteksi per foto.",
            ),
          "detectedTags" to
            Schema.array(
              items = Schema.string(),
              description = "Maksimal 5 kata kunci visual atau objek yang terdeteksi dari foto/kenangan yang cocok.",
            ),
          "reason" to
            Schema.string(
              description = "Penjelasan singkat dalam Bahasa Indonesia yang hangat (maks 1 kalimat).",
            ),
        ),
      optionalProperties = listOf("matchedItems", "detectedTags", "reason"),
    )

  private val model by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = searchSchema
        },
    )
  }

  private val storyDraftSchema =
    Schema.obj(
      properties =
        mapOf(
          "title" to Schema.string(description = "Judul kenangan singkat dan puitis dalam Bahasa Indonesia (maks 8 kata)."),
          "caption" to Schema.string(description = "Caption hangat 1 kalimat untuk kenangan tersebut."),
          "story" to Schema.string(description = "Narasi cerita 2-3 kalimat yang hangat dan personal dalam Bahasa Indonesia."),
          "tags" to
            Schema.array(
              items = Schema.string(),
              description = "3 sampai 5 kata kunci pencarian relevan dalam huruf kecil.",
            ),
        ),
      optionalProperties = listOf("tags"),
    )

  private val storyDraftModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = storyDraftSchema
        },
    )
  }

  private val reflectionSchema =
    Schema.obj(
      properties =
        mapOf(
          "promptQuestion" to
            Schema.string(
              description = "Satu pertanyaan refleksi baru yang mendalam dan hangat untuk pasangan dalam Bahasa Indonesia.",
            ),
          "warmInsight" to
            Schema.string(
              description = "Satu paragraf singkat (2 kalimat) apresiasi atau wawasan emosional yang menenangkan berdasarkan catatan terbaru.",
            ),
        ),
    )

  private val reflectionModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = reflectionSchema
        },
    )
  }

  override suspend fun searchMemories(
    query: String,
    candidates: List<LocalMemoryEntity>,
    allowCloudAi: Boolean,
    albumTitlesById: Map<String, String>,
  ): SearchOutcome {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      return SearchOutcome(
        matchingMemoryIds = candidates.map { it.id },
        usedCloudAi = false,
        explanation = "Menampilkan seluruh ${candidates.size} kenangan sesuai filter.",
      )
    }

    if (!allowCloudAi || candidates.isEmpty()) {
      val localMatches =
        withContext(Dispatchers.Default) {
          localMetadataSearch(trimmed, candidates, albumTitlesById)
        }
      val notice =
        if (!allowCloudAi) {
          "Mode Pencarian Lokal Aktif — Pencarian visual AI Gemini dapat diaktifkan pada kartu privasi di atas."
        } else null
      return SearchOutcome(
        matchingMemoryIds = localMatches,
        usedCloudAi = false,
        explanation =
          if (localMatches.isEmpty()) "Tidak ditemukan kecocokan pada judul, caption, lokasi, album, atau tag lokal."
          else "Ditemukan ${localMatches.size} kenangan yang cocok dari pencarian pintar lokal.",
        isFallbackNotice = notice,
      )
    }

    val validIds = candidates.map { it.id }.toSet()
    val sortedCandidates = candidates.sortedByDescending { it.memoryDateMillis }
    val localBaselineMatches =
      withContext(Dispatchers.Default) {
        localMetadataSearch(trimmed, sortedCandidates, albumTitlesById)
      }

    // Prioritize visual analysis on all photo candidates (up to 36 across 3 batches, preferring untagged/galeri-imported first)
    val photoPool =
      sortedCandidates
        .filter { !it.mediaUri.isNullOrBlank() && it.mediaType.lowercase() != "video" }
        .sortedWith(
          compareBy<LocalMemoryEntity> { m ->
            val tags = m.tagsCsv.trim().lowercase()
            if (tags.isBlank() || tags == "galeri") 0 else 1
          }.thenByDescending { it.memoryDateMillis }
        )
        .take(MAX_TOTAL_VISUAL_PHOTOS)

    val textOnlyCandidates =
      sortedCandidates.filter { m -> photoPool.none { it.id == m.id } }.take(40)

    val batches = photoPool.chunked(BATCH_IMAGE_COUNT).take(MAX_BATCHES)
    val bitmapsToRecycle = mutableListOf<Bitmap>()

    return try {
      val batchResults =
        if (batches.isEmpty()) {
          listOf(
            runSingleGeminiBatch(
              query = trimmed,
              batchCandidates = sortedCandidates.take(60),
              albumTitlesById = albumTitlesById,
              bitmapsToRecycle = bitmapsToRecycle,
              includeImages = false,
            )
          )
        } else {
          coroutineScope {
            batches.mapIndexed { idx, photoChunk ->
              val extraText = if (idx == 0) textOnlyCandidates else emptyList()
              async {
                runSingleGeminiBatch(
                  query = trimmed,
                  batchCandidates = photoChunk + extraText,
                  albumTitlesById = albumTitlesById,
                  bitmapsToRecycle = bitmapsToRecycle,
                  includeImages = true,
                )
              }
            }.awaitAll()
          }
        }

      val orderedMatched = linkedSetOf<String>()
      val discoveredTagsById = mutableMapOf<String, MutableList<String>>()
      val combinedDetectedTags = linkedSetOf<String>()
      var firstReason: String? = null

      batchResults.forEach { res ->
        res.matchedIds.forEach { id ->
          if (id in validIds) orderedMatched.add(id)
        }
        res.discoveredTagsByMemoryId.forEach { (id, tags) ->
          if (id in validIds) {
            val list = discoveredTagsById.getOrPut(id) { mutableListOf() }
            tags.forEach { t -> if (t !in list) list.add(t) }
          }
        }
        res.detectedTags.forEach { combinedDetectedTags.add(it) }
        if (firstReason.isNullOrBlank() && !res.reason.isNullOrBlank()) {
          firstReason = res.reason
        }
      }

      // Append any high-confidence local semantic/album matches beyond visual batches
      localBaselineMatches.forEach { orderedMatched.add(it) }

      val resolvedList = orderedMatched.toList()
      val resolvedReason =
        firstReason
          ?: if (resolvedList.isEmpty()) {
            "Gemini AI sudah memeriksa ${photoPool.size} foto dan metadata tanpa kecocokan langsung untuk \"$trimmed\"."
          } else {
            "Dicocokkan oleh Gemini Vision dari ${photoPool.size} foto, album, dan metadata kenangan."
          }

      SearchOutcome(
        matchingMemoryIds = resolvedList,
        usedCloudAi = true,
        explanation = resolvedReason,
        detectedTags = combinedDetectedTags.take(6),
        discoveredTagsByMemoryId = discoveredTagsById.mapValues { it.value.take(6) },
      )
    } catch (e: TimeoutCancellationException) {
      Log.w(TAG, "AI search timeout, falling back to local search", e)
      SearchOutcome(
        matchingMemoryIds = localBaselineMatches,
        usedCloudAi = false,
        explanation = "Koneksi AI habis waktu. Menampilkan hasil pencarian pintar lokal.",
        isFallbackNotice = "Timeout AI — beralih otomatis ke pencarian lokal.",
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "AI search failed, using local metadata fallback", e)
      SearchOutcome(
        matchingMemoryIds = localBaselineMatches,
        usedCloudAi = false,
        explanation =
          "Pencarian AI Cloud sedang tidak tersedia (${e.localizedMessage ?: "gangguan jaringan"}). Menampilkan hasil pencarian lokal.",
        isFallbackNotice = "Fallback Pencarian Lokal (metadata/semantik/waktu) aktif.",
      )
    } finally {
      synchronized(bitmapsToRecycle) {
        bitmapsToRecycle.forEach { bmp ->
          if (!bmp.isRecycled) bmp.recycle()
        }
        bitmapsToRecycle.clear()
      }
    }
  }

  private data class BatchAiResult(
    val matchedIds: List<String>,
    val detectedTags: List<String>,
    val discoveredTagsByMemoryId: Map<String, List<String>>,
    val reason: String?,
  )

  private suspend fun runSingleGeminiBatch(
    query: String,
    batchCandidates: List<LocalMemoryEntity>,
    albumTitlesById: Map<String, String>,
    bitmapsToRecycle: MutableList<Bitmap>,
    includeImages: Boolean,
  ): BatchAiResult {
    val indexSummary =
      batchCandidates.joinToString("\n") { m ->
        val albumLabel = m.albumId?.let { albumTitlesById[it] }.orEmpty().ifBlank { "Tanpa Album" }
        "- id=${m.id} | judul=${m.title} | album=$albumLabel | caption=${m.caption} | lokasi=${m.locationName} | tag=${m.tagsCsv} | tipe=${m.mediaType} | favorit=${m.isFavorite}"
      }

    val thumbnailPairs: List<Pair<String, Bitmap>> =
      if (includeImages && appContext != null) {
        withContext(Dispatchers.IO) {
          batchCandidates
            .filter { !it.mediaUri.isNullOrBlank() && it.mediaType.lowercase() != "video" }
            .take(BATCH_IMAGE_COUNT)
            .mapNotNull { mem ->
              val bmp = decodeSampledThumbnail(mem.mediaUri)
              if (bmp != null) {
                synchronized(bitmapsToRecycle) { bitmapsToRecycle.add(bmp) }
                mem.id to bmp
              } else {
                null
              }
            }
        }
      } else {
        emptyList()
      }

    val promptText =
      """
      Kamu adalah mesin pencari foto visual & semantik cerdas seperti Google Photos untuk aplikasi kenangan EVORIA (bukan chatbot).
      Tugasmu menganalisis isi visual setiap gambar yang terlampir secara menyeluruh (objek, orang, pose, pakaian, warna, tempat, suasana, cahaya, teks di foto) DAN metadata/album di bawah, lalu memilih `matchedIds` yang relevan dengan maksud kueri pengguna (dalam Bahasa Indonesia maupun Inggris).
      CATATAN PENTING: Banyak foto diimpor dengan judul generik seperti "Galeri Kita" tanpa caption. Untuk foto seperti itu, ANDALKAN isi visual foto sepenuhnya! Jangan menolak foto hanya karena judulnya generik.
      Untuk setiap item yang cocok, isi juga `matchedItems` berisi `{ "id": "...", "tags": ["objek1", "suasana2"] }` dalam huruf kecil agar foto tersebut lebih mudah dicari ke depannya.
      Jangan mengarang id yang tidak ada di daftar. Jika tidak ada yang relevan di batch ini, kembalikan array kosong.

      Kueri pengguna: "$query"

      Daftar kenangan batch ini (${batchCandidates.size} item):
      $indexSummary
      """.trimIndent()

    val requestContent =
      content {
        text(promptText)
        thumbnailPairs.forEach { (id, bitmap) ->
          text("Foto id=$id:")
          image(bitmap)
        }
      }

    val response = withTimeout(60_000L) { model.generateContent(requestContent) }
    val raw = response.text ?: throw IllegalStateException("Respons AI kosong.")
    val json = JSONObject(raw)

    val matched = linkedSetOf<String>()
    val idsJson = json.optJSONArray("matchedIds")
    if (idsJson != null) {
      for (i in 0 until idsJson.length()) {
        val id = idsJson.optString(i).trim()
        if (id.isNotEmpty()) matched.add(id)
      }
    }

    val perMemoryTags = mutableMapOf<String, List<String>>()
    val itemsJson = json.optJSONArray("matchedItems")
    if (itemsJson != null) {
      for (i in 0 until itemsJson.length()) {
        val obj = itemsJson.optJSONObject(i) ?: continue
        val id = obj.optString("id").trim()
        if (id.isNotEmpty()) {
          matched.add(id)
          val arr = obj.optJSONArray("tags")
          val itemTags = buildList {
            if (arr != null) {
              for (j in 0 until arr.length()) {
                val t = arr.optString(j).trim().lowercase()
                if (t.isNotEmpty() && t !in this) add(t)
              }
            }
          }
          if (itemTags.isNotEmpty()) perMemoryTags[id] = itemTags
        }
      }
    }

    val tagsJson = json.optJSONArray("detectedTags")
    val detected = buildList {
      if (tagsJson != null) {
        for (i in 0 until tagsJson.length()) {
          val t = tagsJson.optString(i).trim().lowercase()
          if (t.isNotEmpty() && t !in this) add(t)
        }
      }
    }

    return BatchAiResult(
      matchedIds = matched.toList(),
      detectedTags = detected,
      discoveredTagsByMemoryId = perMemoryTags,
      reason = json.optString("reason").trim().takeIf { it.isNotBlank() },
    )
  }

  suspend fun generateMemoryStorySuggestion(
    photoUri: String?,
    seedTitle: String,
    location: String,
  ): Result<AiMemoryStoryDraft> {
    val bmp: Bitmap? =
      if (!photoUri.isNullOrBlank()) {
        withContext(Dispatchers.IO) { decodeSampledThumbnail(photoUri, maxEdgePx = 512) }
      } else {
        null
      }

    val contextHints =
      buildList {
        if (seedTitle.isNotBlank()) add("Ide judul awal: \"${seedTitle.trim()}\"")
        if (location.isNotBlank()) add("Lokasi: \"${location.trim()}\"")
      }.joinToString(". ")

    val promptText =
      """
      Kamu membantu pengguna aplikasi album kenangan privat EVORIA merangkai judul, caption, cerita hangat, dan tag pencarian dalam Bahasa Indonesia.
      Nada tulisan harus natural, tulus, puitis secukupnya (tidak berlebihan/klise).
      ${if (contextHints.isNotBlank()) "Petunjuk pengguna: $contextHints." else "Buat berdasarkan suasana visual foto atau momen berdua yang hangat."}
      """.trimIndent()

    val requestContent =
      content {
        text(promptText)
        if (bmp != null) {
          image(bmp)
        }
      }

    return try {
      val response = withTimeout(60_000L) { storyDraftModel.generateContent(requestContent) }
      val raw = response.text ?: throw IllegalStateException("Respons Gemini AI kosong.")
      val json = JSONObject(raw)
      val title = json.optString("title").trim().ifBlank { seedTitle.ifBlank { "Momen Berdua" } }
      val caption = json.optString("caption").trim()
      val story = json.optString("story").trim()
      val tagsArr = json.optJSONArray("tags")
      val tags =
        buildList {
          if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
              val tag = tagsArr.optString(i).trim().lowercase()
              if (tag.isNotBlank() && tag !in this) add(tag)
            }
          }
        }.take(5)
      if (caption.isBlank() && story.isBlank()) {
        Result.failure(IllegalStateException("Gemini AI tidak mengembalikan draf cerita yang valid."))
      } else {
        Result.success(
          AiMemoryStoryDraft(
            title = title,
            caption = caption,
            story = story,
            tagsCsv = tags.joinToString(", "),
          )
        )
      }
    } catch (e: TimeoutCancellationException) {
      Log.w(TAG, "AI story suggestion timed out", e)
      Result.failure(IllegalStateException("Permintaan Gemini AI habis waktu. Coba lagi saat koneksi stabil."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "AI story suggestion failed", e)
      Result.failure(
        IllegalStateException(
          "Gagal membuat draf cerita AI: ${e.localizedMessage ?: "gangguan layanan Firebase AI Logic."}"
        )
      )
    } finally {
      if (bmp != null && !bmp.isRecycled) bmp.recycle()
    }
  }

  suspend fun generateDailyReflectionInsight(
    recentMoods: List<String>,
    recentJournalSnippets: List<String>,
  ): Result<AiReflectionInsight> {
    val moodSummary = recentMoods.take(6).joinToString(", ").ifBlank { "belum ada catatan mood" }
    val snippetSummary =
      recentJournalSnippets
        .take(4)
        .joinToString(" | ") { it.replace("\n", " ").take(120) }
        .ifBlank { "belum ada potongan jurnal terbaru" }

    val promptText =
      """
      Kamu adalah pendamping refleksi privat pada aplikasi EVORIA.
      Berdasarkan suasana hati terakhir ($moodSummary) dan tema jurnal terbaru ($snippetSummary), buat:
      1. `promptQuestion`: satu pertanyaan refleksi personal yang hangat untuk dijawab hari ini (Bahasa Indonesia).
      2. `warmInsight`: dua kalimat apresiasi/wawasan emosional yang menenangkan dan suportif (Bahasa Indonesia).
      Jangan menyebut data sensitif secara harfiah jika kosong.
      """.trimIndent()

    return try {
      val response = withTimeout(60_000L) { reflectionModel.generateContent(promptText) }
      val raw = response.text ?: throw IllegalStateException("Respons Gemini AI kosong.")
      val json = JSONObject(raw)
      val question = json.optString("promptQuestion").trim()
      val insight = json.optString("warmInsight").trim()
      if (question.isBlank() || insight.isBlank()) {
        Result.failure(IllegalStateException("Gemini AI tidak mengembalikan refleksi yang valid."))
      } else {
        Result.success(AiReflectionInsight(promptQuestion = question, warmInsight = insight))
      }
    } catch (e: TimeoutCancellationException) {
      Log.w(TAG, "AI reflection insight timed out", e)
      Result.failure(IllegalStateException("Permintaan Gemini AI habis waktu. Coba lagi sebentar."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "AI reflection insight failed", e)
      Result.failure(
        IllegalStateException(
          "Gagal memuat refleksi AI: ${e.localizedMessage ?: "gangguan layanan Firebase AI Logic."}"
        )
      )
    }
  }

  fun localMetadataSearch(
    query: String,
    candidates: List<LocalMemoryEntity>,
    albumTitlesById: Map<String, String> = emptyMap(),
  ): List<String> {
    val loweredQuery = query.lowercase().trim()
    val allTokens =
      loweredQuery
        .split(Regex("[\\s,.;:!?]+"))
        .map { it.trim() }
        .filter { it.length >= 2 && it !in STOP_WORDS }

    val wantsFavorite = "favorit" in loweredQuery || "kesukaan" in loweredQuery
    val wantsVideo = "video" in loweredQuery || "rekaman" in loweredQuery || "klip" in loweredQuery
    val wantsPhoto = "foto" in loweredQuery || "gambar" in loweredQuery || "potret" in loweredQuery
    val wantsToday = "hari ini" in loweredQuery || "tadi" in loweredQuery
    val wantsThisWeek = "minggu ini" in loweredQuery || "pekan ini" in loweredQuery
    val wantsThisMonth = "bulan ini" in loweredQuery

    val contentTokens = allTokens.filter { it !in QUALIFIER_WORDS }

    if (contentTokens.isEmpty() && !wantsFavorite && !wantsVideo && !wantsPhoto && !wantsToday && !wantsThisWeek && !wantsThisMonth) {
      return candidates.map { it.id }
    }

    val now = System.currentTimeMillis()
    val startOfToday = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    return candidates
      .mapNotNull { mem ->
        val albumTitle = mem.albumId?.let { albumTitlesById[it] }.orEmpty()
        val haystack =
          "${mem.title} ${mem.caption} ${mem.story} ${mem.locationName} ${mem.tagsCsv} $albumTitle ${mem.mediaType} ${mem.visibility}"
            .lowercase()

        var contentScore = 0
        for (token in contentTokens) {
          if (haystack.contains(token)) {
            contentScore += 3
          } else {
            val synonymHits = SEMANTIC_SYNONYMS[token].orEmpty().count { syn -> haystack.contains(syn) }
            if (synonymHits > 0) {
              contentScore += synonymHits * 2
            }
          }
        }

        if (contentTokens.isNotEmpty() && contentScore == 0) {
          return@mapNotNull null
        }

        var intentScore = 0
        val isVideo = mem.mediaType.equals("video", ignoreCase = true)
        val isPhoto = mem.mediaType.equals("photo", ignoreCase = true) || (!mem.mediaUri.isNullOrBlank() && !isVideo)
        if (wantsFavorite && mem.isFavorite) intentScore += 2
        if (wantsVideo && isVideo) intentScore += 2
        if (wantsPhoto && isPhoto) intentScore += 1
        if (wantsToday && mem.memoryDateMillis >= startOfToday) intentScore += 2
        if (wantsThisWeek && mem.memoryDateMillis >= now - 7L * 86_400_000L) intentScore += 2
        if (wantsThisMonth && mem.memoryDateMillis >= now - 30L * 86_400_000L) intentScore += 2

        val totalScore = contentScore + intentScore
        if (contentTokens.isEmpty() && intentScore == 0) null
        else if (totalScore > 0) mem.id to totalScore else null
      }
      .sortedByDescending { it.second }
      .map { it.first }
  }

  private fun decodeSampledThumbnail(uriString: String?, maxEdgePx: Int = 384): Bitmap? {
    val ctx = appContext ?: return null
    val raw = uriString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
      val uri = Uri.parse(raw)
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      openStreamForUri(ctx, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
      val width = bounds.outWidth
      val height = bounds.outHeight
      if (width <= 0 || height <= 0) return@runCatching null

      var sampleSize = 1
      while (width / sampleSize > maxEdgePx * 2 || height / sampleSize > maxEdgePx * 2) {
        sampleSize *= 2
      }
      val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      val decoded = openStreamForUri(ctx, uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        ?: return@runCatching null

      val exifRotation = readExifRotationDegrees(ctx, uri)
      val oriented =
        if (exifRotation != 0f) {
          val matrix = Matrix().apply { postRotate(exifRotation) }
          Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
          }
        } else {
          decoded
        }

      val maxSide = maxOf(oriented.width, oriented.height)
      if (maxSide <= maxEdgePx) {
        oriented
      } else {
        val ratio = maxEdgePx.toFloat() / maxSide.toFloat()
        val targetW = (oriented.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (oriented.height * ratio).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(oriented, targetW, targetH, true).also {
          if (it !== oriented) oriented.recycle()
        }
      }
    }.getOrNull()
  }

  private fun readExifRotationDegrees(ctx: Context, uri: Uri): Float =
    runCatching {
      val orientation =
        openStreamForUri(ctx, uri)?.use { stream ->
          ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
          )
        } ?: ExifInterface.ORIENTATION_NORMAL
      when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
      }
    }.getOrDefault(0f)

  private fun openStreamForUri(ctx: Context, uri: Uri) =
    when (uri.scheme?.lowercase()) {
      "file", null -> uri.path?.let { File(it) }?.takeIf { it.exists() }?.inputStream()
      else -> ctx.contentResolver.openInputStream(uri)
    }

  private companion object {
    const val TAG = "EvoriaSearchEngine"
    const val BATCH_IMAGE_COUNT = 12
    const val MAX_BATCHES = 3
    const val MAX_TOTAL_VISUAL_PHOTOS = BATCH_IMAGE_COUNT * MAX_BATCHES

    val STOP_WORDS =
      setOf(
        "di", "ke", "dari", "yang", "dan", "waktu", "saat", "kita", "aku", "kamu",
        "dengan", "untuk", "pada", "ini", "itu", "nya", "ada",
      )

    val QUALIFIER_WORDS =
      setOf(
        "foto", "gambar", "potret", "video", "rekaman", "klip",
        "favorit", "kesukaan", "hari", "minggu", "pekan", "bulan", "tadi",
      )

    val SEMANTIC_SYNONYMS: Map<String, List<String>> =
      mapOf(
        "pantai" to listOf("laut", "ombak", "pasir", "kapal", "ferry", "danau", "pulau", "samudra", "dermaga", "pesisir", "beach"),
        "laut" to listOf("pantai", "ombak", "kapal", "ferry", "danau", "air", "pulau", "dermaga", "sea", "ocean"),
        "sunset" to listOf("senja", "sore", "jingga", "matahari", "langit"),
        "senja" to listOf("sunset", "sore", "jingga", "matahari", "maghrib"),
        "kopi" to listOf("kafe", "coffee", "latte", "espresso", "teh", "nongkrong", "kuliner", "sarapan"),
        "kafe" to listOf("kopi", "coffee", "restoran", "kuliner", "makan", "cafe"),
        "makan" to listOf("kuliner", "kafe", "kopi", "malam", "jajan", "masak", "food", "makanan"),
        "makanan" to listOf("makan", "kuliner", "kue", "roti", "kafe", "restoran", "jajan", "food"),
        "kuliner" to listOf("makan", "kopi", "kafe", "jajan", "roti", "kue", "food"),
        "malam" to listOf("bintang", "bulan", "gelap", "lampu", "kota", "tenang", "night"),
        "alam" to listOf("gunung", "bukit", "hutan", "pohon", "danau", "pantai", "hijau", "taman", "nature"),
        "gunung" to listOf("bukit", "alam", "pendakian", "kabut", "puncak", "mountain"),
        "hujan" to listOf("gerimis", "mendung", "payung", "dingin", "teras", "rain"),
        "berdua" to listOf("kita", "pasangan", "selfie", "swafoto", "shared", "bersama", "kencan", "date", "couple"),
        "selfie" to listOf("swafoto", "wajah", "momen", "berdua", "senyum", "portrait"),
        "liburan" to listOf("jalan", "trip", "wisata", "pantai", "kapal", "danau", "gunung", "holiday"),
        "kucing" to listOf("cat", "hewan", "peliharaan", "lucu", "bulu"),
        "bunga" to listOf("flower", "buket", "mawar", "taman", "hadiah"),
        "ulang" to listOf("ultah", "birthday", "kue", "lilin", "perayaan"),
        "ultah" to listOf("ulang tahun", "birthday", "kue", "lilin", "hadiah"),
        "keluarga" to listOf("rumah", "orang tua", "family", "lebaran", "kumpul"),
        "teman" to listOf("sahabat", "nongkrong", "friend", "bareng", "grup"),
        "motor" to listOf("jalan", "helm", "riding", "touring", "kendaraan"),
        "mobil" to listOf("jalan", "trip", "setir", "kendaraan", "car"),
      )
  }
}
