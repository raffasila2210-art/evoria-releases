package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.data.local.EvoriaSanctuaryRepository
import com.example.data.local.LocalMemoryEntity
import com.example.ml.EvoriaVisionRuntime
import com.example.ml.IndonesianQueryTranslator
import com.example.ml.ModelManager
import com.example.ml.VisionEmbeddingEngine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
  val usedOnDeviceVision: Boolean = false,
  val scoresByMemoryId: Map<String, Float> = emptyMap(),
  val translatedPrompts: List<String> = emptyList(),
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

/**
 * EVORIA Search & Reflection Coordinator.
 * - Jelajah semantic photo search runs 100% on-device via EVRIA VISION SEARCH LITE
 *   (ONNX Runtime + CLIP ViT-B/32 quantized embeddings + cosine similarity + metadata fallback).
 *   Zero Gemini API or cloud image calls are ever made for photo search or ranking.
 * - Reflection & memory story suggestions use Gemini 2.5 Flash (when reachable) with instant
 *   local Indonesian fallback so Refleksi never fails with timeout errors.
 */
class EvoriaSearchEngine(
  context: Context? = null,
  private val embeddingEngine: VisionEmbeddingEngine? = null,
  private val modelManager: ModelManager? = null,
  private val sanctuaryRepo: EvoriaSanctuaryRepository? = null,
  private val ownerUidProvider: (() -> String)? = null,
) : JelajahSearchRepository {

  private val appContext = context?.applicationContext
  private val vectorSearchEngine by lazy { LocalVectorSearchEngine(embeddingEngine) }

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
      modelName = TEXT_AI_MODEL,
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
      modelName = TEXT_AI_MODEL,
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

    if (candidates.isEmpty()) {
      return SearchOutcome(
        matchingMemoryIds = emptyList(),
        usedCloudAi = false,
        explanation = "Belum ada kenangan pada filter yang dipilih.",
      )
    }

    val sortedCandidates = candidates.sortedByDescending { it.memoryDateMillis }
    val localBaselineMatches =
      withContext(Dispatchers.Default) {
        localMetadataSearch(trimmed, sortedCandidates, albumTitlesById)
      }

    val activeEngine = embeddingEngine
    val ownerUid = ownerUidProvider?.invoke()?.trim().orEmpty()

    if (activeEngine != null && activeEngine.isReady() && sanctuaryRepo != null && ownerUid.isNotEmpty()) {
      val indexedEmbeddings =
        withContext(Dispatchers.IO) {
          sanctuaryRepo.getIndexedEmbeddingsForModel(ownerUid, activeEngine.modelVersion)
        }

      if (indexedEmbeddings.isNotEmpty()) {
        val embeddingsById = indexedEmbeddings.associateBy { it.memoryId }
        val vectorResult =
          vectorSearchEngine.search(
            query = trimmed,
            candidates = sortedCandidates,
            embeddingsByMemoryId = embeddingsById,
            albumTitlesById = albumTitlesById,
            metadataBaselineIds = localBaselineMatches,
          )

        if (vectorResult.usedVisualModel) {
          val matchedIds = vectorResult.matches.map { it.memoryId }
          val visualScores =
            vectorResult.matches
              .filter { it.visualScore > 0f }
              .associate { it.memoryId to it.visualScore }
          val unindexedCount =
            sortedCandidates.count { mem ->
              EvoriaVisionRuntime.isIndexablePhoto(mem) && mem.id !in embeddingsById
            }
          val notice =
            if (unindexedCount > 0) {
              "$unindexedCount foto baru belum terindeks; hasil akan makin lengkap setelah pengindeksan selesai."
            } else {
              null
            }
          val explanation =
            if (matchedIds.isEmpty()) {
              "EVRIA Vision Search memeriksa ${vectorResult.indexedPhotoCount} foto secara on-device tanpa kecocokan langsung untuk \"$trimmed\"."
            } else {
              "Ditemukan ${matchedIds.size} kenangan dari pencarian visual on-device (${vectorResult.indexedPhotoCount} foto terindeks, 100% privat)."
            }
          return SearchOutcome(
            matchingMemoryIds = matchedIds,
            usedCloudAi = false,
            explanation = explanation,
            isFallbackNotice = notice,
            detectedTags = IndonesianQueryTranslator.buildVisualPrompts(trimmed).take(2),
            usedOnDeviceVision = true,
            scoresByMemoryId = visualScores,
            translatedPrompts = vectorResult.translatedPrompts,
          )
        }
      }

      val hasIndexablePhotos = sortedCandidates.any(EvoriaVisionRuntime::isIndexablePhoto)
      if (hasIndexablePhotos) {
        return SearchOutcome(
          matchingMemoryIds = localBaselineMatches,
          usedCloudAi = false,
          explanation =
            if (localBaselineMatches.isEmpty()) {
              "Foto belum selesai diindeks AI lokal dan tidak ada kecocokan kata kunci untuk \"$trimmed\"."
            } else {
              "Ditemukan ${localBaselineMatches.size} kenangan dari kata kunci lokal sambil menunggu indeks visual selesai."
            },
          isFallbackNotice = "Model AI siap — sedang mengindeks foto di latar belakang atau ketuk \"Indeks Sekarang\".",
        )
      }
    }

    val fallbackNotice =
      if (modelManager?.isReady() == true || activeEngine?.isReady() == true) {
        "Mode pencarian teks & sinonim lokal aktif."
      } else {
        "Mode Pencarian Kata Kunci Lokal — Unduh Model AI On-Device di kartu atas untuk pencarian visual isi foto."
      }

    return SearchOutcome(
      matchingMemoryIds = localBaselineMatches,
      usedCloudAi = false,
      explanation =
        if (localBaselineMatches.isEmpty()) {
          "Tidak ditemukan kecocokan pada judul, caption, lokasi, album, atau tag lokal."
        } else {
          "Ditemukan ${localBaselineMatches.size} kenangan yang cocok dari pencarian pintar lokal."
        },
      isFallbackNotice = fallbackNotice,
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
      val response = withTimeout(TEXT_AI_TIMEOUT_MS) { storyDraftModel.generateContent(requestContent) }
      val raw = response.text ?: throw IllegalStateException("Respons AI kosong.")
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
        Result.success(buildLocalMemoryStoryDraft(seedTitle, location))
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
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Cloud AI story draft unavailable, returning local Indonesian draft fallback", e)
      Result.success(buildLocalMemoryStoryDraft(seedTitle, location))
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
      val response = withTimeout(TEXT_AI_TIMEOUT_MS) { reflectionModel.generateContent(promptText) }
      val raw = response.text ?: throw IllegalStateException("Respons AI kosong.")
      val json = JSONObject(raw)
      val question = json.optString("promptQuestion").trim()
      val insight = json.optString("warmInsight").trim()
      if (question.isBlank() || insight.isBlank()) {
        Result.success(buildLocalReflectionInsight(recentMoods, recentJournalSnippets))
      } else {
        Result.success(AiReflectionInsight(promptQuestion = question, warmInsight = insight))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Cloud AI reflection unavailable, returning local Indonesian insight fallback", e)
      Result.success(buildLocalReflectionInsight(recentMoods, recentJournalSnippets))
    }
  }

  internal fun buildLocalReflectionInsight(
    recentMoods: List<String>,
    recentJournalSnippets: List<String>,
  ): AiReflectionInsight {
    val joinedMoods = recentMoods.joinToString(" ").lowercase()
    val hasJournal = recentJournalSnippets.any { it.isNotBlank() }

    val (insightByMood, questionByMood) =
      when {
        listOf("sedih", "lelah", "cemas", "penat", "berat", "galau").any { it in joinedMoods } ->
          "Tidak apa-apa memberi ruang bagi perasaan yang terasa berat akhir-akhir ini. Langkah kecil merawat dirimu dan mencatatnya di sini sudah menjadi bukti ketangguhan yang berharga." to
            "Hal kecil apa hari ini yang bisa membuat bahumu terasa sedikit lebih ringan?"
        listOf("rindu", "kangen", "hangat", "sayang", "cinta").any { it in joinedMoods } ->
          "Rasa rindu dan kehangatan yang kamu simpan menunjukkan betapa berartinya ikatan yang kalian rawat. Mengabadikannya dalam kata menjaga momen kecil tetap hidup meski waktu berjalan." to
            "Momen sederhana apa bersama orang tersayang yang paling ingin kamu syukuri dan ulangi kembali?"
        listOf("bahagia", "senang", "ceria", "syukur", "tenang", "damai").any { it in joinedMoods } ->
          "Ketenteraman dan rasa syukur yang hadir belakangan ini patut dirayakan dengan lembut. Menyadari kebahagiaan-kebahagiaan kecil membantu hati tetap hangat bahkan di hari yang sibuk." to
            "Kejadian tak terduga apa yang paling membuatmu tersenyum tenang dalam beberapa hari terakhir?"
        else ->
          "Setiap hari menyimpan cerita yang layak didengar dengan jujur tanpa terburu-buru. Meluangkan beberapa menit untuk menengok isi hati membantu menjaga ritme hidup tetap selaras." to
            "Jika hari ini dirangkum menjadi satu kenangan berharga, bagian mana yang paling ingin kamu ingat?"
      }

    val fullInsight =
      if (hasJournal) {
        "$insightByMood Catatan jurnal terbarumu memperlihatkan perhatian yang tulus pada hal-hal yang bermakna."
      } else {
        insightByMood
      }

    return AiReflectionInsight(
      promptQuestion = questionByMood,
      warmInsight = fullInsight,
    )
  }

  internal fun buildLocalMemoryStoryDraft(
    seedTitle: String,
    location: String,
  ): AiMemoryStoryDraft {
    val cleanTitle = seedTitle.trim()
    val cleanLocation = location.trim()
    val finalTitle =
      when {
        cleanTitle.isNotBlank() -> cleanTitle
        cleanLocation.isNotBlank() -> "Momen Hangat di $cleanLocation"
        else -> "Jejak Momen Berdua"
      }
    val locationClause = if (cleanLocation.isNotBlank()) " di $cleanLocation" else ""
    val caption = "Menyimpan potongan waktu yang tenang$locationClause agar selalu mudah dikenang."
    val story =
      "Ada momen sederhana$locationClause yang terasa sayang jika dibiarkan lewat begitu saja. " +
        "Dari tawa kecil hingga suasana di sekitar kita saat itu, semuanya tertinggal rapi di ruang kenangan EVORIA."
    val tags =
      buildList {
        add("kenangan")
        add("momen")
        if (cleanLocation.isNotBlank()) add(cleanLocation.lowercase().split(" ").first())
        add("berdua")
      }.distinct().take(4).joinToString(", ")

    return AiMemoryStoryDraft(
      title = finalTitle,
      caption = caption,
      story = story,
      tagsCsv = tags,
    )
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
    const val TEXT_AI_MODEL = "gemini-2.5-flash"
    const val TEXT_AI_TIMEOUT_MS = 12_000L

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
        "anjing" to listOf("dog", "hewan", "peliharaan", "taman", "lucu"),
        "bunga" to listOf("flower", "buket", "mawar", "taman", "hadiah"),
        "ulang" to listOf("ultah", "birthday", "kue", "lilin", "perayaan"),
        "ultah" to listOf("ulang tahun", "birthday", "kue", "lilin", "hadiah"),
        "keluarga" to listOf("rumah", "orang tua", "family", "lebaran", "kumpul"),
        "teman" to listOf("sahabat", "nongkrong", "friend", "bareng", "grup"),
        "motor" to listOf("jalan", "helm", "riding", "touring", "kendaraan"),
        "mobil" to listOf("jalan", "trip", "setir", "kendaraan", "car"),
        "sepeda" to listOf("gowes", "bersepeda", "bicycle", "taman"),
        "langit" to listOf("sky", "awan", "biru", "senja", "pagi"),
      )
  }
}
