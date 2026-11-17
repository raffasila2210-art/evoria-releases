package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class SearchOutcome(
  val matchingMemoryIds: List<String>,
  val usedCloudAi: Boolean,
  val explanation: String,
  val isFallbackNotice: String? = null,
  val detectedTags: List<String> = emptyList(),
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
  ): SearchOutcome
}

class EvoriaSearchEngine(
  context: Context? = null,
) : JelajahSearchRepository {

  private val appContext = context?.applicationContext

  private val searchSchema =
    Schema.obj(
      properties =
        mapOf(
          "matchedIds" to
            Schema.array(
              items = Schema.string(),
              description = "ID kenangan yang paling cocok secara visual maupun kontekstual dengan kueri pengguna.",
            ),
          "detectedTags" to
            Schema.array(
              items = Schema.string(),
              description = "Maksimal 4 kata kunci visual atau objek yang terdeteksi dari foto/kenangan yang cocok.",
            ),
          "reason" to
            Schema.string(
              description = "Penjelasan singkat dalam Bahasa Indonesia yang hangat (maks 1 kalimat).",
            ),
        ),
      optionalProperties = listOf("detectedTags", "reason"),
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
      val localMatches = withContext(Dispatchers.Default) { localMetadataSearch(trimmed, candidates) }
      val notice =
        if (!allowCloudAi) {
          "Mode Pencarian Lokal Aktif — Pencarian visual AI Gemini dapat diaktifkan pada kartu privasi di atas."
        } else null
      return SearchOutcome(
        matchingMemoryIds = localMatches,
        usedCloudAi = false,
        explanation =
          if (localMatches.isEmpty()) "Tidak ditemukan kecocokan pada judul, caption, lokasi, atau tag lokal."
          else "Ditemukan ${localMatches.size} kenangan yang cocok dari pencarian pintar lokal.",
        isFallbackNotice = notice,
      )
    }

    // Build metadata summary for up to 60 candidates
    val recentCandidates = candidates.sortedByDescending { it.memoryDateMillis }.take(60)
    val indexSummary =
      recentCandidates.joinToString("\n") { m ->
        "- id=${m.id} | judul=${m.title} | caption=${m.caption} | lokasi=${m.locationName} | tag=${m.tagsCsv} | tipe=${m.mediaType} | favorit=${m.isFavorite}"
      }

    // Attach lightweight downsampled thumbnails (max 384px edge, up to 10 photos) off the main thread
    val thumbnailPairs: List<Pair<String, Bitmap>> =
      if (appContext != null) {
        withContext(Dispatchers.IO) {
          recentCandidates
            .filter { !it.mediaUri.isNullOrBlank() && it.mediaType.lowercase() != "video" }
            .take(MAX_MULTIMODAL_IMAGES)
            .mapNotNull { mem ->
              val bmp = decodeSampledThumbnail(mem.mediaUri)
              if (bmp != null) mem.id to bmp else null
            }
        }
      } else {
        emptyList()
      }

    val promptText =
      """
      Kamu adalah mesin pencari foto visual & semantik seperti Google Photos untuk aplikasi kenangan EVORIA (bukan chatbot).
      Tugasmu menganalisis isi visual gambar yang terlampir DAN metadata setiap item, lalu memilih daftar `id` kenangan yang paling cocok dengan maksud kueri pengguna dalam Bahasa Indonesia.
      Contoh: jika pengguna mencari "kapal ferry di laut", "pantai", "senyum di dekat air", "langit senja", atau "kopi", cocokkan objek visual di foto walaupun tidak tertulis di judul atau caption!
      Jangan mengarang id yang tidak ada di daftar. Jika tidak ada yang relevan, kembalikan array kosong.
      
      Kueri pengguna: "$trimmed"
      
      Daftar metadata kenangan (${recentCandidates.size} item):
      $indexSummary
      """.trimIndent()

    val requestContent =
      content {
        text(promptText)
        thumbnailPairs.forEach { (id, bitmap) ->
          text("Pratinjau foto untuk id=$id:")
          image(bitmap)
        }
      }

    return try {
      val response = withTimeout(60_000L) { model.generateContent(requestContent) }
      val raw = response.text ?: throw IllegalStateException("Respons AI kosong.")
      val json = JSONObject(raw)
      val validIds = candidates.map { it.id }.toSet()

      val idsJson = json.optJSONArray("matchedIds")
      val matched = buildList {
        if (idsJson != null) {
          for (i in 0 until idsJson.length()) {
            val id = idsJson.optString(i)
            if (id in validIds && id !in this) add(id)
          }
        }
      }

      val tagsJson = json.optJSONArray("detectedTags")
      val tags = buildList {
        if (tagsJson != null) {
          for (i in 0 until tagsJson.length()) {
            val t = tagsJson.optString(i).trim()
            if (t.isNotEmpty() && t !in this) add(t)
          }
        }
      }.take(4)

      val reason =
        json.optString("reason").takeIf { it.isNotBlank() }
          ?: if (thumbnailPairs.isNotEmpty()) {
            "Dicocokkan secara visual dari ${thumbnailPairs.size} foto & metadata oleh Gemini AI."
          } else {
            "Dicocokkan menggunakan Gemini AI berdasarkan konteks dan deskripsi kenangan."
          }

      SearchOutcome(
        matchingMemoryIds = matched,
        usedCloudAi = true,
        explanation = reason,
        detectedTags = tags,
      )
    } catch (e: TimeoutCancellationException) {
      Log.w(TAG, "AI search timeout, falling back to local search", e)
      val local = withContext(Dispatchers.Default) { localMetadataSearch(trimmed, candidates) }
      SearchOutcome(
        matchingMemoryIds = local,
        usedCloudAi = false,
        explanation = "Koneksi AI habis waktu. Menampilkan hasil pencarian pintar lokal.",
        isFallbackNotice = "Timeout AI — beralih otomatis ke pencarian lokal.",
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "AI search failed, using local metadata fallback", e)
      val local = withContext(Dispatchers.Default) { localMetadataSearch(trimmed, candidates) }
      SearchOutcome(
        matchingMemoryIds = local,
        usedCloudAi = false,
        explanation = "Pencarian AI Cloud sedang tidak tersedia (${e.localizedMessage ?: "gangguan jaringan"}). Menampilkan hasil pencarian lokal.",
        isFallbackNotice = "Fallback Pencarian Lokal (metadata/semantik/waktu) aktif.",
      )
    } finally {
      thumbnailPairs.forEach { (_, bmp) ->
        if (!bmp.isRecycled) bmp.recycle()
      }
    }
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

  fun localMetadataSearch(query: String, candidates: List<LocalMemoryEntity>): List<String> {
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
        val haystack =
          "${mem.title} ${mem.caption} ${mem.story} ${mem.locationName} ${mem.tagsCsv} ${mem.mediaType} ${mem.visibility}"
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

        // If user entered specific topic keywords, require at least one content/synonym hit
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

      val maxSide = maxOf(decoded.width, decoded.height)
      if (maxSide <= maxEdgePx) {
        decoded
      } else {
        val ratio = maxEdgePx.toFloat() / maxSide.toFloat()
        val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
          if (it !== decoded) decoded.recycle()
        }
      }
    }.getOrNull()
  }

  private fun openStreamForUri(ctx: Context, uri: Uri) =
    when (uri.scheme?.lowercase()) {
      "file", null -> uri.path?.let { File(it) }?.takeIf { it.exists() }?.inputStream()
      else -> ctx.contentResolver.openInputStream(uri)
    }

  private companion object {
    const val TAG = "EvoriaSearchEngine"
    const val MAX_MULTIMODAL_IMAGES = 10
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
        "pantai" to listOf("laut", "ombak", "pasir", "kapal", "ferry", "danau", "pulau", "samudra", "dermaga", "pesisir"),
        "laut" to listOf("pantai", "ombak", "kapal", "ferry", "danau", "air", "pulau", "dermaga"),
        "sunset" to listOf("senja", "sore", "jingga", "matahari", "langit"),
        "senja" to listOf("sunset", "sore", "jingga", "matahari", "maghrib"),
        "kopi" to listOf("kafe", "coffee", "latte", "espresso", "teh", "nongkrong", "kuliner", "sarapan"),
        "kafe" to listOf("kopi", "coffee", "restoran", "kuliner", "makan"),
        "makan" to listOf("kuliner", "kafe", "kopi", "malam", "jajan", "masak"),
        "kuliner" to listOf("makan", "kopi", "kafe", "jajan", "roti", "kue"),
        "malam" to listOf("bintang", "bulan", "gelap", "lampu", "kota", "tenang"),
        "alam" to listOf("gunung", "bukit", "hutan", "pohon", "danau", "pantai", "hijau", "taman"),
        "gunung" to listOf("bukit", "alam", "pendakian", "kabut", "puncak"),
        "hujan" to listOf("gerimis", "mendung", "payung", "dingin", "teras"),
        "berdua" to listOf("kita", "pasangan", "selfie", "swafoto", "shared", "bersama", "kencan", "date"),
        "selfie" to listOf("swafoto", "wajah", "momen", "berdua", "senyum"),
        "liburan" to listOf("jalan", "trip", "wisata", "pantai", "kapal", "danau", "gunung"),
      )
  }
}
