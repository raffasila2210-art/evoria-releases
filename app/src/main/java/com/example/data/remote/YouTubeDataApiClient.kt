package com.example.data.remote

import com.example.BuildConfig
import java.io.OutputStreamWriter
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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
  private const val DESKTOP_CHROME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
  private const val YT_CONSENT_COOKIE = "CONSENT=YES+cb.20210328-17-p0.en+FX+678; SOCS=CAI"
  private val isoDurationRegex =
    Regex("""^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$""", RegexOption.IGNORE_CASE)
  private val quotedJsonVideoIdRegex =
    Regex(""""videoId"\s*:\s*"([A-Za-z0-9_-]{11})"""")

  fun safeConfiguredApiKey(): String =
    runCatching { BuildConfig.YOUTUBE_API_KEY }.getOrDefault("").trim()

  fun isApiKeyConfigured(apiKey: String = safeConfiguredApiKey()): Boolean =
    apiKey.isNotBlank() && apiKey != DEFAULT_PLACEHOLDER

  fun thumbnailUrl(videoId: String): String =
    "https://i.ytimg.com/vi/${videoId.trim()}/hqdefault.jpg"

  internal fun cleanTrackTitle(title: String): String =
    title
      .replace('\u00A0', ' ')
      .replace(Regex("""^\s*(?:E|EXPLICIT)\s*[•|·\-:]\s*""", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""\([^)]*(feat|ft|with|remaster|version|edit|mix|live|deluxe|bonus|from)[^)]*\)""", RegexOption.IGNORE_CASE), " ")
      .replace(Regex("""\[[^\]]*]"""), " ")
      .replace(Regex("""\s+-\s+(?:(?:19|20)\d{2}\s+)?(?:remaster(?:ed)?|radio edit|single version|album version|explicit|clean).*$""", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""\s+"""), " ")
      .trim()
      .ifBlank { title.replace('\u00A0', ' ').trim() }

  internal fun cleanPrimaryArtist(artist: String): String =
    artist
      .replace('\u00A0', ' ')
      .split(",", "•", "&", "feat.", "ft.", "/", "×")
      .firstOrNull()
      ?.trim()
      .orEmpty()
      .ifBlank { artist.replace('\u00A0', ' ').trim() }

  internal fun buildSearchQuery(title: String, artist: String): String {
    val cleanTitle = cleanTrackTitle(title)
    val cleanArtist = cleanPrimaryArtist(artist)
    return "$cleanTitle $cleanArtist official audio".trim()
  }

  internal fun buildCandidateQueries(title: String, artist: String): List<String> {
    val cleanTitle = cleanTrackTitle(title)
    val cleanArtist = cleanPrimaryArtist(artist)
    return listOfNotNull(
      buildSearchQuery(title, artist).takeIf { it.isNotBlank() },
      listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ").trim().takeIf { it.isNotBlank() },
      cleanTitle.takeIf { it.isNotBlank() },
    ).distinct()
  }

  internal fun parseIso8601DurationMs(iso: String): Long {
    val match = isoDurationRegex.matchEntire(iso.trim()) ?: return 0L
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: 0L
    val seconds = match.groupValues[3].toLongOrNull() ?: 0L
    return (hours * 3600L + minutes * 60L + seconds) * 1000L
  }

  internal fun parseMmSsDurationMs(label: String): Long {
    val parts = label.trim().split(":", ".").mapNotNull { it.trim().toLongOrNull() }
    return when (parts.size) {
      2 -> (parts[0] * 60L + parts[1]) * 1000L
      3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
      else -> 0L
    }
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

  /**
   * Searches YouTube for the best playable track using YouTube Data API v3 first when configured,
   * automatically falling back to multi-query public YouTube InnerTube/web search so Spotify-imported
   * tracks reliably resolve and play inside EVORIA.
   */
  suspend fun searchBestPlayableTrack(
    title: String,
    artist: String,
    targetDurationMs: Long = 0L,
    apiKey: String = safeConfiguredApiKey(),
  ): YouTubeSearchResult =
    withContext(Dispatchers.IO) {
      if (isApiKeyConfigured(apiKey)) {
        val apiResult = searchFullTrack(title, artist, targetDurationMs, apiKey)
        if (apiResult is YouTubeSearchResult.Found) {
          return@withContext apiResult
        }
      }

      val candidateQueries = buildCandidateQueries(title, artist)
      if (candidateQueries.isEmpty()) {
        return@withContext YouTubeSearchResult.NotFound(title)
      }

      var fallbackMatches: List<YouTubeTrackMatch> = emptyList()
      var lastFailure: Throwable? = null
      for (query in candidateQueries) {
        val res = searchTracksPublicFallback(query, maxResults = 8)
        if (res.isSuccess) {
          val list = res.getOrDefault(emptyList())
          if (list.isNotEmpty()) {
            fallbackMatches = list
            break
          }
        } else {
          lastFailure = res.exceptionOrNull()
        }
      }

      if (fallbackMatches.isEmpty()) {
        val errMsg = lastFailure?.localizedMessage
        return@withContext if (!errMsg.isNullOrBlank()) {
          YouTubeSearchResult.ApiError(0, errMsg)
        } else {
          YouTubeSearchResult.NotFound(candidateQueries.first())
        }
      }

      val candidates = fallbackMatches.map { YouTubeCandidate(it.videoId, it.title, it.channelTitle) }
      val details = fallbackMatches.associate {
        it.videoId to YouTubeVideoDetail(durationMs = it.durationMs, embeddable = it.embeddable)
      }
      val best =
        selectBestCandidate(
          candidates = candidates,
          details = details,
          targetTitle = cleanTrackTitle(title),
          targetArtist = cleanPrimaryArtist(artist),
          targetDurationMs = targetDurationMs,
        ) ?: fallbackMatches.first()
      YouTubeSearchResult.Found(best)
    }

  /**
   * Direct song search on YouTube returning a list of playable candidates.
   * Uses YouTube Data API v3 if configured, with transparent public InnerTube & web fallback.
   */
  suspend fun searchTracks(
    query: String,
    maxResults: Int = 10,
    apiKey: String = safeConfiguredApiKey(),
  ): Result<List<YouTubeTrackMatch>> =
    withContext(Dispatchers.IO) {
      val cleanQuery = query.trim()
      if (cleanQuery.isBlank()) return@withContext Result.success(emptyList())

      if (isApiKeyConfigured(apiKey)) {
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val searchUrl =
          "https://www.googleapis.com/youtube/v3/search" +
            "?part=snippet&type=video&videoEmbeddable=true&maxResults=$maxResults&q=$encodedQuery&key=$encodedKey"
        val searchRes = fetchUrl(searchUrl)
        if (searchRes.code in 200..299 && !searchRes.body.isNullOrBlank()) {
          val candidates = parseSearchCandidates(searchRes.body)
          if (candidates.isNotEmpty()) {
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
            val list =
              candidates
                .map { c ->
                  val d = detailsMap[c.videoId]
                  YouTubeTrackMatch(
                    videoId = c.videoId,
                    title = c.title,
                    channelTitle = c.channelTitle,
                    durationMs = d?.durationMs ?: 0L,
                    embeddable = d?.embeddable ?: true,
                  )
                }
                .sortedByDescending { it.embeddable }
            return@withContext Result.success(list)
          }
        }
      }

      searchTracksPublicFallback(cleanQuery, maxResults)
    }

  suspend fun searchTracksPublicFallback(
    query: String,
    maxResults: Int = 10,
  ): Result<List<YouTubeTrackMatch>> =
    withContext(Dispatchers.IO) {
      val trimmedQuery = query.trim()
      if (trimmedQuery.isBlank()) return@withContext Result.success(emptyList())

      // Strategy 1: Public YouTube Web InnerTube JSON Search API
      val webBody =
        buildJsonObject {
          putJsonObject("context") {
            putJsonObject("client") {
              put("clientName", "WEB")
              put("clientVersion", "2.20250101.00.00")
              put("hl", "id")
              put("gl", "ID")
            }
          }
          put("query", trimmedQuery)
        }.toString()

      val webRes =
        postJson(
          urlString = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false",
          jsonPayload = webBody,
          userAgent = DESKTOP_CHROME_USER_AGENT,
          origin = "https://www.youtube.com",
          clientNameHeader = "1",
          clientVersionHeader = "2.20250101.00.00",
        )
      if (webRes.code in 200..299 && !webRes.body.isNullOrBlank()) {
        val parsed = parseInnerTubeJson(webRes.body, maxResults)
        if (parsed.isNotEmpty()) {
          return@withContext Result.success(parsed)
        }
      }

      // Strategy 2: YouTube Music InnerTube JSON Search API (WEB_REMIX)
      val ytMusicBody =
        buildJsonObject {
          putJsonObject("context") {
            putJsonObject("client") {
              put("clientName", "WEB_REMIX")
              put("clientVersion", "1.20241120.01.00")
              put("hl", "id")
              put("gl", "ID")
            }
          }
          put("query", trimmedQuery)
        }.toString()

      val musicRes =
        postJson(
          urlString = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false",
          jsonPayload = ytMusicBody,
          userAgent = DESKTOP_CHROME_USER_AGENT,
          origin = "https://music.youtube.com",
          clientNameHeader = "67",
          clientVersionHeader = "1.20241120.01.00",
        )
      if (musicRes.code in 200..299 && !musicRes.body.isNullOrBlank()) {
        val parsed = parseInnerTubeJson(musicRes.body, maxResults)
        if (parsed.isNotEmpty()) {
          return@withContext Result.success(parsed)
        }
      }

      // Strategy 3: YouTube ANDROID InnerTube Search API
      val androidBody =
        buildJsonObject {
          putJsonObject("context") {
            putJsonObject("client") {
              put("clientName", "ANDROID")
              put("clientVersion", "19.29.37")
              put("androidSdkVersion", 34)
              put("hl", "id")
              put("gl", "ID")
            }
          }
          put("query", trimmedQuery)
        }.toString()

      val androidRes =
        postJson(
          urlString = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false",
          jsonPayload = androidBody,
          userAgent = "com.google.android.youtube/19.29.37 (Linux; U; Android 14; id_ID) gzip",
          origin = "https://www.youtube.com",
          clientNameHeader = "3",
          clientVersionHeader = "19.29.37",
        )
      if (androidRes.code in 200..299 && !androidRes.body.isNullOrBlank()) {
        val parsed = parseInnerTubeJson(androidRes.body, maxResults)
        if (parsed.isNotEmpty()) {
          return@withContext Result.success(parsed)
        }
      }

      // Strategy 4 & 5: Desktop & Mobile YouTube HTML search pages with Consent cookies + JS unescape
      val encodedQuery = URLEncoder.encode(trimmedQuery, "UTF-8")
      val htmlEndpoints =
        listOf(
          "https://www.youtube.com/results?search_query=$encodedQuery&hl=id" to DESKTOP_CHROME_USER_AGENT,
          "https://m.youtube.com/results?search_query=$encodedQuery&hl=id" to
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        )
      var lastHttpCode = -1
      for ((url, ua) in htmlEndpoints) {
        val res =
          fetchUrl(
            urlString = url,
            acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            userAgent = ua,
            cookieHeader = YT_CONSENT_COOKIE,
          )
        if (res.code in 200..299 && !res.body.isNullOrBlank()) {
          val parsed = parsePublicSearchHtml(res.body, maxResults)
          if (parsed.isNotEmpty()) {
            return@withContext Result.success(parsed)
          }
        } else {
          lastHttpCode = res.code
        }
      }

      if (lastHttpCode <= 0 && webRes.code <= 0 && musicRes.code <= 0 && androidRes.code <= 0) {
        Result.failure(IllegalStateException("Koneksi jaringan tidak tersedia saat mencari di YouTube."))
      } else {
        Result.success(emptyList())
      }
    }

  internal fun parseInnerTubeJson(jsonText: String, maxResults: Int = 10): List<YouTubeTrackMatch> {
    val root = runCatching { json.parseToJsonElement(jsonText) }.getOrNull() ?: return emptyList()
    val collected = mutableListOf<YouTubeTrackMatch>()
    val seenIds = HashSet<String>()
    collectVideoRenderers(root, collected, seenIds, maxResults)
    return collected
  }

  internal fun parsePublicSearchHtml(html: String, maxResults: Int = 10): List<YouTubeTrackMatch> {
    val jsonBlob = extractYtInitialDataJson(html)
    if (jsonBlob != null) {
      val parsed = parseInnerTubeJson(jsonBlob, maxResults)
      if (parsed.isNotEmpty()) return parsed
    }

    // Regex fallback if YouTube alters ytInitialData container or uses newer renderer wrappers
    val seenIds = HashSet<String>()
    val fallback = mutableListOf<YouTubeTrackMatch>()
    for (match in quotedJsonVideoIdRegex.findAll(html)) {
      val vidId = match.groupValues.getOrNull(1) ?: continue
      if (!seenIds.add(vidId)) continue
      val windowEnd = (match.range.last + 1800).coerceAtMost(html.length)
      val slice = html.substring(match.range.first, windowEnd)
      val titleMatch =
        Regex(""""(?:title|headline)"\s*:\s*\{\s*"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"((?:\\.|[^"\\])+)"""")
          .find(slice)
          ?: Regex(""""title"\s*:\s*\{\s*"simpleText"\s*:\s*"((?:\\.|[^"\\])+)"""").find(slice)
          ?: Regex(""""simpleText"\s*:\s*"((?:\\.|[^"\\]){3,140})"""").find(slice)
      val title =
        titleMatch?.groupValues?.getOrNull(1)
          ?.replace("\\\"", "\"")
          ?.decodeHtmlEntities()
          ?: continue
      fallback.add(
        YouTubeTrackMatch(
          videoId = vidId,
          title = title,
          channelTitle = "YouTube",
          durationMs = 0L,
          embeddable = true,
        )
      )
      if (fallback.size >= maxResults) break
    }
    return fallback
  }

  private fun extractYtInitialDataJson(html: String): String? {
    extractBalancedJsonAfter(html, "ytInitialData")?.let { return it }
    // Mobile/escaped pages emit: var ytInitialData = '\x7b\x22...';
    val escapedRegex =
      Regex("""ytInitialData\s*=\s*(['"])((?:\\.|(?!\1).)*)\1""", RegexOption.DOT_MATCHES_ALL)
    val match = escapedRegex.find(html) ?: return null
    val rawLiteral = match.groupValues.getOrNull(2) ?: return null
    val unescaped = unescapeJsStringLiteral(rawLiteral)
    return if (unescaped.trimStart().startsWith("{")) unescaped else null
  }

  private fun unescapeJsStringLiteral(raw: String): String {
    val sb = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
      val ch = raw[i]
      if (ch == '\\' && i + 1 < raw.length) {
        when (val next = raw[i + 1]) {
          'x' -> {
            val code = if (i + 3 < raw.length) raw.substring(i + 2, i + 4).toIntOrNull(16) else null
            if (code != null) {
              sb.append(code.toChar())
              i += 4
            } else {
              sb.append(next)
              i += 2
            }
            continue
          }
          'u' -> {
            val code = if (i + 5 < raw.length) raw.substring(i + 2, i + 6).toIntOrNull(16) else null
            if (code != null) {
              sb.append(code.toChar())
              i += 6
            } else {
              sb.append(next)
              i += 2
            }
            continue
          }
          'n' -> { sb.append('\n'); i += 2; continue }
          'r' -> { sb.append('\r'); i += 2; continue }
          't' -> { sb.append('\t'); i += 2; continue }
          '\\' -> { sb.append('\\'); i += 2; continue }
          '\'' -> { sb.append('\''); i += 2; continue }
          '"' -> { sb.append('"'); i += 2; continue }
          else -> { sb.append(next); i += 2; continue }
        }
      }
      sb.append(ch)
      i++
    }
    return sb.toString()
  }

  private fun extractBalancedJsonAfter(html: String, marker: String): String? {
    val markerIdx = html.indexOf(marker)
    if (markerIdx < 0) return null
    // Peek ahead up to 16 chars for '{' so we don't accidentally jump past a quoted string literal
    val searchLimit = (markerIdx + marker.length + 16).coerceAtMost(html.length)
    val braceStart = html.indexOf('{', startIndex = markerIdx)
    if (braceStart < 0 || braceStart > searchLimit) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in braceStart until html.length) {
      val ch = html[i]
      if (escaped) {
        escaped = false
        continue
      }
      if (inString) {
        if (ch == '\\') escaped = true
        else if (ch == '"') inString = false
        continue
      }
      when (ch) {
        '"' -> inString = true
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) {
            return html.substring(braceStart, i + 1)
          }
        }
      }
    }
    return null
  }

  private fun collectVideoRenderers(
    element: JsonElement,
    out: MutableList<YouTubeTrackMatch>,
    seenIds: MutableSet<String>,
    maxResults: Int,
  ) {
    if (out.size >= maxResults) return
    when (element) {
      is JsonObject -> {
        val renderer =
          (element["videoRenderer"] as? JsonObject)
            ?: (element["videoWithContextRenderer"] as? JsonObject)
            ?: (element["compactVideoRenderer"] as? JsonObject)
            ?: (element["gridVideoRenderer"] as? JsonObject)
        if (renderer != null) {
          val vidId = renderer.string("videoId")
          if (!vidId.isNullOrBlank() && seenIds.add(vidId)) {
            val title =
              extractYtRunsOrSimpleText(renderer["title"])
                ?: extractYtRunsOrSimpleText(renderer["headline"])
                ?: "YouTube Audio"
            val channel =
              extractYtRunsOrSimpleText(renderer["ownerText"])
                ?: extractYtRunsOrSimpleText(renderer["longBylineText"])
                ?: extractYtRunsOrSimpleText(renderer["shortBylineText"])
                ?: "YouTube"
            val durationText =
              extractYtRunsOrSimpleText(renderer["lengthText"])
                ?: extractDurationFromOverlays(renderer["thumbnailOverlays"])
                ?: ""
            val durationMs = parseMmSsDurationMs(durationText)
            val isExplicitLive =
              durationText.contains("LIVE", ignoreCase = true) ||
                hasLiveBadge(renderer)
            val isVeryShortClip = durationMs in 1L..29_999L
            if (!isExplicitLive && !isVeryShortClip) {
              out.add(
                YouTubeTrackMatch(
                  videoId = vidId,
                  title = title.decodeHtmlEntities(),
                  channelTitle = channel.decodeHtmlEntities(),
                  durationMs = durationMs,
                  embeddable = true,
                )
              )
            }
          }
        } else {
          // Support YouTube Music items (musicResponsiveListItemRenderer) and YouTube Lockup cards
          val musicItem = element["musicResponsiveListItemRenderer"] as? JsonObject
          if (musicItem != null) {
            extractFromMusicResponsiveItem(musicItem)?.let { match ->
              if (seenIds.add(match.videoId)) out.add(match)
            }
          }
          val lockup = element["lockupViewModel"] as? JsonObject
          if (lockup != null) {
            extractFromLockupViewModel(lockup)?.let { match ->
              if (seenIds.add(match.videoId)) out.add(match)
            }
          }
        }
        for (value in element.values) {
          if (out.size >= maxResults) break
          collectVideoRenderers(value, out, seenIds, maxResults)
        }
      }
      is JsonArray -> {
        for (child in element) {
          if (out.size >= maxResults) break
          collectVideoRenderers(child, out, seenIds, maxResults)
        }
      }
      else -> Unit
    }
  }

  private fun extractFromMusicResponsiveItem(item: JsonObject): YouTubeTrackMatch? {
    val overlayVid =
      (((item["overlay"] as? JsonObject)
        ?.get("musicItemThumbnailOverlayRenderer") as? JsonObject)
        ?.get("content") as? JsonObject)
        ?.get("musicPlayButtonRenderer")
        ?.let { it as? JsonObject }
        ?.get("playNavigationEndpoint")
        ?.let { it as? JsonObject }
        ?.get("watchEndpoint")
        ?.let { it as? JsonObject }
        ?.string("videoId")
    val playlistItemVid = (item["playlistItemData"] as? JsonObject)?.string("videoId")
    val flexCols = item["flexColumns"] as? JsonArray
    val col0 =
      (flexCols?.getOrNull(0) as? JsonObject)
        ?.get("musicResponsiveListItemFlexColumnRenderer") as? JsonObject
    val col1 =
      (flexCols?.getOrNull(1) as? JsonObject)
        ?.get("musicResponsiveListItemFlexColumnRenderer") as? JsonObject
    val titleVid =
      (((col0?.get("text") as? JsonObject)
        ?.get("runs") as? JsonArray)
        ?.firstOrNull() as? JsonObject)
        ?.get("navigationEndpoint")
        ?.let { it as? JsonObject }
        ?.get("watchEndpoint")
        ?.let { it as? JsonObject }
        ?.string("videoId")
    val vidId = overlayVid ?: playlistItemVid ?: titleVid ?: return null
    val title = extractYtRunsOrSimpleText(col0?.get("text")) ?: return null
    val subtitleRaw = extractYtRunsOrSimpleText(col1?.get("text")).orEmpty()
    val subtitleParts = subtitleRaw.split("•").map { it.trim() }.filter { it.isNotBlank() }
    val durationMs =
      subtitleParts.lastOrNull { it.contains(":") || it.contains(".") }
        ?.let(::parseMmSsDurationMs)
        ?: 0L
    val channel =
      subtitleParts
        .firstOrNull { !it.equals("Song", true) && !it.equals("Video", true) && !it.equals("Lagu", true) && !it.contains(":") }
        ?: "YouTube Music"
    return YouTubeTrackMatch(
      videoId = vidId,
      title = title.decodeHtmlEntities(),
      channelTitle = channel.decodeHtmlEntities(),
      durationMs = durationMs,
      embeddable = true,
    )
  }

  private fun extractFromLockupViewModel(lockup: JsonObject): YouTubeTrackMatch? {
    val contentType = lockup.string("contentType").orEmpty()
    if (contentType.isNotEmpty() && !contentType.contains("VIDEO", ignoreCase = true)) return null
    val vidId = lockup.string("contentId")?.takeIf { it.length == 11 } ?: return null
    val meta =
      ((lockup["metadata"] as? JsonObject)
        ?.get("lockupMetadataViewModel") as? JsonObject)
        ?: return null
    val title = (meta["title"] as? JsonObject)?.string("content") ?: return null
    return YouTubeTrackMatch(
      videoId = vidId,
      title = title.decodeHtmlEntities(),
      channelTitle = "YouTube",
      durationMs = 0L,
      embeddable = true,
    )
  }

  private fun extractDurationFromOverlays(overlaysElem: JsonElement?): String? {
    val arr = overlaysElem as? JsonArray ?: return null
    for (item in arr) {
      val status = (item as? JsonObject)?.get("thumbnailOverlayTimeStatusRenderer") as? JsonObject ?: continue
      val text = extractYtRunsOrSimpleText(status["text"])
      if (!text.isNullOrBlank()) return text
    }
    return null
  }

  private fun hasLiveBadge(renderer: JsonObject): Boolean {
    val overlays = renderer["thumbnailOverlays"] as? JsonArray
    if (overlays != null) {
      for (item in overlays) {
        val status = (item as? JsonObject)?.get("thumbnailOverlayTimeStatusRenderer") as? JsonObject
        if (status?.string("style").equals("LIVE", ignoreCase = true)) return true
      }
    }
    val badges = renderer["badges"] as? JsonArray ?: return false
    return badges.any { badge ->
      val meta = (badge as? JsonObject)?.get("metadataBadgeRenderer") as? JsonObject
      meta?.string("style")?.contains("LIVE", ignoreCase = true) == true
    }
  }

  private fun extractYtRunsOrSimpleText(elem: JsonElement?): String? {
    val obj = elem as? JsonObject ?: return null
    obj.string("simpleText")?.let { return it }
    val runs = obj["runs"] as? JsonArray ?: return null
    val joined = runs.mapNotNull { (it as? JsonObject)?.string("text") }.joinToString("")
    return joined.takeIf { it.isNotBlank() }
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
        score += (candidates.size - idx) * 2.0

        if (embeddable) score += 25.0 else score -= 20.0

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

        if (titleWords.isNotEmpty()) {
          val matched = titleWords.count { it in vidWords }.toDouble() / titleWords.size.toDouble()
          score += matched * 30.0
        }
        if (artistWords.isNotEmpty()) {
          val matched = artistWords.count { it in vidWords }.toDouble() / artistWords.size.toDouble()
          score += matched * 18.0
        }

        if (chanLower.endsWith("- topic")) score += 18.0
        if (vidLower.contains("official audio") || vidLower.contains("audio")) score += 10.0
        if (vidLower.contains("official video") || vidLower.contains("official music video")) score += 5.0

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

  private fun fetchUrl(
    urlString: String,
    acceptHeader: String = "application/json",
    userAgent: String? = null,
    cookieHeader: String? = null,
  ): HttpResponse {
    return runCatching {
      val conn =
        (URL(urlString).openConnection() as HttpURLConnection).apply {
          instanceFollowRedirects = true
          connectTimeout = 10_000
          readTimeout = 12_000
          requestMethod = "GET"
          setRequestProperty("Accept", acceptHeader)
          setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
          if (!userAgent.isNullOrBlank()) {
            setRequestProperty("User-Agent", userAgent)
          }
          if (!cookieHeader.isNullOrBlank()) {
            setRequestProperty("Cookie", cookieHeader)
          }
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

  private fun postJson(
    urlString: String,
    jsonPayload: String,
    userAgent: String,
    origin: String = "https://www.youtube.com",
    clientNameHeader: String = "1",
    clientVersionHeader: String = "2.20250101.00.00",
  ): HttpResponse {
    return runCatching {
      val conn =
        (URL(urlString).openConnection() as HttpURLConnection).apply {
          instanceFollowRedirects = true
          connectTimeout = 6_000
          readTimeout = 8_000
          requestMethod = "POST"
          doOutput = true
          setRequestProperty("Content-Type", "application/json; charset=UTF-8")
          setRequestProperty("Accept", "application/json")
          setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
          setRequestProperty("User-Agent", userAgent)
          setRequestProperty("Origin", origin)
          setRequestProperty("Referer", "$origin/")
          setRequestProperty("X-YouTube-Client-Name", clientNameHeader)
          setRequestProperty("X-YouTube-Client-Version", clientVersionHeader)
          setRequestProperty("Cookie", YT_CONSENT_COOKIE)
        }
      try {
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
          writer.write(jsonPayload)
        }
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
      .replace("\\u0026", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
}
