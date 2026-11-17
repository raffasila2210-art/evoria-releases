package com.example.data.remote

import com.example.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class YouTubeTrackMatch(
  val videoId: String,
  val title: String,
  val channelTitle: String,
  val durationMs: Long,
  val embeddable: Boolean,
)

sealed class YouTubeSearchResult {
  data class Found(val match: YouTubeTrackMatch) : YouTubeSearchResult()

  data class NotFound(val query: String) : YouTubeSearchResult()

  data object MissingApiKey : YouTubeSearchResult()

  data class ApiError(val statusCode: Int, val message: String) : YouTubeSearchResult()
}

internal data class YouTubeCandidate(
  val videoId: String,
  val title: String,
  val channelTitle: String,
)

internal data class YouTubeVideoDetail(
  val durationMs: Long,
  val embeddable: Boolean,
)

object YouTubeDataApiClient {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private const val DEFAULT_PLACEHOLDER = "YOUTUBE_API_KEY_DEFAULT_VALUE"
  private val isoDurationRegex =
    Regex("""^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$""", RegexOption.IGNORE_CASE)

  fun safeConfiguredApiKey(): String =
    runCatching { BuildConfig.YOUTUBE_API_KEY }.getOrDefault("").trim()

  fun isApiKeyConfigured(apiKey: String = safeConfiguredApiKey()): Boolean =
    apiKey.isNotBlank() && apiKey != DEFAULT_PLACEHOLDER

  internal fun buildSearchQuery(title: String, artist: String): String {
    val cleanTitle =
      title
        .replace(Regex("""\([^)]*(feat|ft|with|remaster|version|edit)[^)]*\)""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\[[^\]]*]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { title.trim() }
    val cleanArtist =
      artist
        .split(",", "•", "&")
        .firstOrNull()
        ?.trim()
        .orEmpty()
        .ifBlank { artist.trim() }
    return "$cleanTitle $cleanArtist official audio".trim()
  }

  internal fun parseIso8601DurationMs(iso: String): Long {
    val match = isoDurationRegex.matchEntire(iso.trim()) ?: return 0L
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: 0L
    val seconds = match.groupValues[3].toLongOrNull() ?: 0L
    return (hours * 3600L + minutes * 60L + seconds) * 1000L
  }

  suspend fun searchFullTrack(
    title: String,
    artist: String,
    targetDurationMs: Long = 0L,
    apiKey: String = safeConfiguredApiKey(),
  ): YouTubeSearchResult =
    withContext(Dispatchers.IO) {
      if (!isApiKeyConfigured(apiKey)) {
        return@withContext YouTubeSearchResult.MissingApiKey
      }

      val query = buildSearchQuery(title, artist)
      val encodedQuery = URLEncoder.encode(query, "UTF-8")
      val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
      val searchUrl =
        "https://www.googleapis.com/youtube/v3/search" +
          "?part=snippet&type=video&videoCategoryId=10&videoEmbeddable=true&maxResults=6&q=$encodedQuery&key=$encodedKey"

      var searchRes = fetchUrl(searchUrl)
      if (searchRes.code == 400 || searchRes.code == 403) {
        return@withContext YouTubeSearchResult.ApiError(
          searchRes.code,
          extractApiErrorMessage(searchRes.body) ?: "YouTube API key ditolak atau kuota habis.",
        )
      }

      var candidates =
        searchRes.body?.let { parseSearchCandidates(it) }.orEmpty()

      // Fallback broader search if music-category filter yielded 0 embeddable results
      if (candidates.isEmpty()) {
        val broadUrl =
          "https://www.googleapis.com/youtube/v3/search" +
            "?part=snippet&type=video&maxResults=6&q=$encodedQuery&key=$encodedKey"
        searchRes = fetchUrl(broadUrl)
        if (searchRes.code == 400 || searchRes.code == 403) {
          return@withContext YouTubeSearchResult.ApiError(
            searchRes.code,
            extractApiErrorMessage(searchRes.body) ?: "YouTube API key ditolak atau kuota habis.",
          )
        }
        candidates = searchRes.body?.let { parseSearchCandidates(it) }.orEmpty()
      }

      if (candidates.isEmpty()) {
        return@withContext YouTubeSearchResult.NotFound(query)
      }

      val idsParam = candidates.joinToString(",") { it.videoId }
      val detailsUrl =
        "https://www.googleapis.com/youtube/v3/videos" +
          "?part=contentDetails,status&id=${URLEncoder.encode(idsParam, "UTF-8")}&key=$encodedKey"
      val detailsRes = fetchUrl(detailsUrl)
      val detailsMap =
        if (detailsRes.code in 200..299 && detailsRes.body != null) {
          parseVideoDetails(detailsRes.body)
        } else {
          emptyMap()
        }

      val best =
        selectBestCandidate(
          candidates = candidates,
          details = detailsMap,
          targetTitle = title,
          targetArtist = artist,
          targetDurationMs = targetDurationMs,
        )
      if (best != null) {
        YouTubeSearchResult.Found(best)
      } else {
        YouTubeSearchResult.NotFound(query)
      }
    }

  internal fun parseSearchCandidates(jsonText: String): List<YouTubeCandidate> {
    val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return emptyList()
    val items = root["items"] as? JsonArray ?: return emptyList()
    return items.mapNotNull { elem ->
      val obj = elem as? JsonObject ?: return@mapNotNull null
      val videoId = obj["id"]?.let { it as? JsonObject }?.string("videoId") ?: return@mapNotNull null
      val snippet = obj["snippet"] as? JsonObject
      val vidTitle = snippet?.string("title")?.decodeHtmlEntities() ?: "YouTube Audio"
      val channelTitle = snippet?.string("channelTitle")?.decodeHtmlEntities() ?: ""
      YouTubeCandidate(
        videoId = videoId,
        title = vidTitle,
        channelTitle = channelTitle,
      )
    }
  }

  internal fun parseVideoDetails(jsonText: String): Map<String, YouTubeVideoDetail> {
    val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return emptyMap()
    val items = root["items"] as? JsonArray ?: return emptyMap()
    val map = LinkedHashMap<String, YouTubeVideoDetail>()
    for (elem in items) {
      val obj = elem as? JsonObject ?: continue
      val id = obj.string("id") ?: continue
      val durationIso = (obj["contentDetails"] as? JsonObject)?.string("duration").orEmpty()
      val embeddable =
        (obj["status"] as? JsonObject)
          ?.get("embeddable")
          ?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
          ?: true
      map[id] =
        YouTubeVideoDetail(
          durationMs = parseIso8601DurationMs(durationIso),
          embeddable = embeddable,
        )
    }
    return map
  }

  internal fun selectBestCandidate(
    candidates: List<YouTubeCandidate>,
    details: Map<String, YouTubeVideoDetail>,
    targetTitle: String,
    targetArtist: String,
    targetDurationMs: Long,
  ): YouTubeTrackMatch? {
    if (candidates.isEmpty()) return null

    fun words(raw: String): Set<String> =
      raw
        .lowercase()
        .replace(Regex("""[^a-z0-9\s]"""), " ")
        .split(Regex("""\s+"""))
        .filter { it.length >= 2 }
        .toSet()

    val titleWords = words(targetTitle)
    val artistWords = words(targetArtist)
    val targetTitleLower = targetTitle.lowercase()

    val scored =
      candidates.mapIndexed { idx, candidate ->
        val detail = details[candidate.videoId]
        val durationMs = detail?.durationMs ?: 0L
        val embeddable = detail?.embeddable ?: true
        val vidLower = candidate.title.lowercase()
        val chanLower = candidate.channelTitle.lowercase()
        val vidWords = words("${candidate.title} ${candidate.channelTitle}")

        var score = 0.0
        // Preserve slight preference for top API relevance order
        score += (candidates.size - idx) * 2.0

        // Prefer embeddable videos so in-app player works cleanly
        if (embeddable) score += 25.0 else score -= 20.0

        // Reject clips < 45s or previews when Spotify track is full length (>= 60s)
        if (durationMs in 1L..44_999L && targetDurationMs >= 60_000L) {
          score -= 100.0
        } else if (durationMs > 0L && targetDurationMs > 0L) {
          val diffSec = abs(durationMs - targetDurationMs) / 1000.0
          when {
            diffSec <= 6.0 -> score += 32.0
            diffSec <= 20.0 -> score += 20.0
            diffSec <= 45.0 -> score += 8.0
            diffSec > 120.0 -> score -= 35.0
          }
        }

        // Metadata token overlap
        if (titleWords.isNotEmpty()) {
          val matched = titleWords.count { it in vidWords }.toDouble() / titleWords.size.toDouble()
          score += matched * 30.0
        }
        if (artistWords.isNotEmpty()) {
          val matched = artistWords.count { it in vidWords }.toDouble() / artistWords.size.toDouble()
          score += matched * 18.0
        }

        // Prefer official studio audio / YouTube Music Topic channels
        if (chanLower.endsWith("- topic")) score += 18.0
        if (vidLower.contains("official audio") || vidLower.contains("audio")) score += 10.0
        if (vidLower.contains("official video") || vidLower.contains("official music video")) score += 5.0

        // Penalize non-original / preview variants unless explicitly in original Spotify title
        listOf("cover", "karaoke", "instrumental", "reaction", "shorts", "teaser", "preview", "sped up", "slowed")
          .forEach { noise ->
            if (vidLower.contains(noise) && !targetTitleLower.contains(noise)) {
              score -= 22.0
            }
          }

        val resolvedDuration = if (durationMs > 0L) durationMs else targetDurationMs
        YouTubeTrackMatch(
          videoId = candidate.videoId,
          title = candidate.title,
          channelTitle = candidate.channelTitle,
          durationMs = resolvedDuration,
          embeddable = embeddable,
        ) to score
      }

    return scored.maxByOrNull { it.second }?.first
  }

  private fun extractApiErrorMessage(body: String?): String? {
    if (body.isNullOrBlank()) return null
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val err = root["error"] as? JsonObject ?: return null
    return err.string("message")
  }

  private data class HttpResponse(val code: Int, val body: String?)

  private fun fetchUrl(urlString: String): HttpResponse {
    return runCatching {
      val conn =
        (URL(urlString).openConnection() as HttpURLConnection).apply {
          instanceFollowRedirects = true
          connectTimeout = 10_000
          readTimeout = 12_000
          requestMethod = "GET"
          setRequestProperty("Accept", "application/json")
        }
      try {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }
        HttpResponse(code, text)
      } finally {
        conn.disconnect()
      }
    }.getOrElse { HttpResponse(-1, null) }
  }

  private fun JsonObject.string(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.takeIf { it.isNotBlank() }

  private fun String.decodeHtmlEntities(): String =
    replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
}
