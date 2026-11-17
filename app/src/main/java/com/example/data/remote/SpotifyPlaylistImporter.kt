package com.example.data.remote

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class SpotifyImportedTrack(
  val title: String,
  val artist: String,
  val durationMs: Long,
  val externalUrl: String,
  val coverUrl: String? = null,
)

data class SpotifyPlaylistData(
  val playlistId: String,
  val title: String,
  val subtitle: String,
  val coverUrl: String?,
  val externalUrl: String,
  val tracks: List<SpotifyImportedTrack>,
)

object SpotifyPlaylistImporter {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private val playlistUrlRegex =
    Regex("""(?:open\.spotify\.com/(?:intl-[a-z-]+/)?playlist/|spotify:playlist:)([A-Za-z0-9]{10,32})""")
  private val rawIdRegex = Regex("""^[A-Za-z0-9]{15,32}$""")
  private val nextDataRegex =
    Regex("""<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
  private val ogTitleRegex =
    Regex("""<meta[^>]*property=["']og:title["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
  private val ogDescRegex =
    Regex("""<meta[^>]*property=["']og:description["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
  private val ogImageRegex =
    Regex("""<meta[^>]*property=["']og:image["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
  private val musicSongRegex =
    Regex("""<meta[^>]*name=["']music:song["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

  fun parseSpotifyPlaylistId(rawInput: String): String? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return null
    playlistUrlRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
    if (rawIdRegex.matches(trimmed)) return trimmed
    return null
  }

  fun toNativeSpotifyUri(externalUrl: String): String? {
    val trackMatch = Regex("""open\.spotify\.com/(?:intl-[a-z-]+/)?track/([A-Za-z0-9]{10,32})""").find(externalUrl)
    if (trackMatch != null) return "spotify:track:${trackMatch.groupValues[1]}"
    val playlistMatch = playlistUrlRegex.find(externalUrl)
    if (playlistMatch != null) return "spotify:playlist:${playlistMatch.groupValues[1]}"
    return null
  }

  suspend fun importFromInput(rawInput: String): Result<SpotifyPlaylistData> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleaned = rawInput.trim()
        require(cleaned.isNotEmpty()) {
          "Masukkan tautan atau URI playlist Spotify terlebih dahulu."
        }

        val resolvedInput =
          if (cleaned.contains("spotify.link", ignoreCase = true) ||
            cleaned.contains("spoti.fi", ignoreCase = true)
          ) {
            resolveRedirectUrl(cleaned) ?: cleaned
          } else {
            cleaned
          }

        val playlistId =
          parseSpotifyPlaylistId(resolvedInput)
            ?: throw IllegalArgumentException(
              "Tautan playlist Spotify tidak dikenali. Contoh: https://open.spotify.com/playlist/..."
            )

        val canonicalUrl = "https://open.spotify.com/playlist/$playlistId"
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"

        val embedResponse = fetchUrl(embedUrl)
        if (embedResponse.code == 404 || embedResponse.code == 401 || embedResponse.code == 403) {
          throw IllegalStateException(
            "Playlist Spotify tidak ditemukan atau masih Privat. Pastikan playlist berstatus Publik lalu coba lagi."
          )
        }

        val embedParsed =
          if (embedResponse.body != null) {
            parseEmbedHtml(embedResponse.body, playlistId, canonicalUrl)
          } else {
            null
          }

        if (embedParsed != null && embedParsed.tracks.isNotEmpty()) {
          val enriched = enrichTracksWithPosters(embedParsed.tracks, embedParsed.coverUrl)
          return@runCatching embedParsed.copy(tracks = enriched)
        }

        // Fallback: oEmbed metadata + OpenGraph playlist page
        val oembedJson = fetchUrl("https://open.spotify.com/oembed?url=${URLEncoder.encode(canonicalUrl, "UTF-8")}").body
        val oembedObj = oembedJson?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val pageHtml = fetchUrl(canonicalUrl).body.orEmpty()

        val title =
          oembedObj?.get("title")?.jsonPrimitive?.contentOrNull
            ?: ogTitleRegex.find(pageHtml)?.groupValues?.getOrNull(1)?.decodeHtmlEntities()
            ?: embedParsed?.title
            ?: throw IllegalStateException(
              "Gagal membaca playlist dari Spotify. Pastikan koneksi internet aktif dan playlist bersifat Publik."
            )

        val cover =
          oembedObj?.get("thumbnail_url")?.jsonPrimitive?.contentOrNull
            ?: ogImageRegex.find(pageHtml)?.groupValues?.getOrNull(1)?.decodeHtmlEntities()
            ?: embedParsed?.coverUrl

        val subtitle =
          ogDescRegex.find(pageHtml)?.groupValues?.getOrNull(1)?.decodeHtmlEntities()
            ?: embedParsed?.subtitle
            ?: "Diimpor dari Spotify"

        val songUrls = musicSongRegex.findAll(pageHtml).mapNotNull { it.groupValues.getOrNull(1) }.distinct().toList()
        val fallbackTracks =
          if (embedParsed != null && embedParsed.tracks.isNotEmpty()) {
            enrichTracksWithPosters(embedParsed.tracks, cover)
          } else {
            songUrls.mapIndexed { idx, trackUrl ->
              val trackId = trackUrl.substringAfterLast("/").substringBefore("?")
              val trackMeta = fetchTrackOembedMeta(trackUrl)
              SpotifyImportedTrack(
                title = trackMeta?.first ?: "Lagu Spotify #${idx + 1}",
                artist = trackMeta?.second ?: "Spotify",
                durationMs = 0L,
                externalUrl = if (trackId.isNotBlank()) "https://open.spotify.com/track/$trackId" else trackUrl,
                coverUrl = trackMeta?.third ?: cover,
              )
            }
          }

        if (fallbackTracks.isEmpty()) {
          throw IllegalStateException(
            "Playlist \"$title\" ditemukan, namun daftar lagunya kosong atau tidak dapat diakses publik."
          )
        }

        SpotifyPlaylistData(
          playlistId = playlistId,
          title = title,
          subtitle = subtitle,
          coverUrl = cover,
          externalUrl = canonicalUrl,
          tracks = fallbackTracks,
        )
      }
    }

  internal fun parseEmbedHtml(
    html: String,
    playlistId: String,
    canonicalUrl: String = "https://open.spotify.com/playlist/$playlistId",
  ): SpotifyPlaylistData? {
    val scriptContent = nextDataRegex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: return null
    return parseEmbedNextDataJson(scriptContent, playlistId, canonicalUrl)
  }

  internal fun parseEmbedNextDataJson(
    jsonText: String,
    playlistId: String,
    canonicalUrl: String = "https://open.spotify.com/playlist/$playlistId",
  ): SpotifyPlaylistData? {
    val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return null
    val entity = findEntityWithTracks(root) ?: return null

    val title =
      entity.string("name")
        ?: entity.string("title")
        ?: "Playlist Spotify"

    val ownerName =
      entity["ownerV2"]?.asObjectOrNull()?.get("data")?.asObjectOrNull()?.string("name")
        ?: entity.string("subtitle")
        ?: "Playlist Spotify"

    val coverUrl = extractCoverUrl(entity)
    val trackArray = entity["trackList"]?.asArrayOrNull() ?: return null

    val parsedTracks =
      trackArray.mapNotNull { elem ->
        val obj = elem.asObjectOrNull() ?: return@mapNotNull null
        val trackTitle = obj.string("title") ?: obj.string("name") ?: return@mapNotNull null
        val artist = obj.string("subtitle") ?: extractArtists(obj) ?: "Artis Spotify"
        val durationMs = obj["duration"]?.jsonPrimitive?.longOrNull ?: 0L
        val uriOrUid = obj.string("uri") ?: obj.string("uid").orEmpty()
        val trackId = uriOrUid.substringAfterLast(":")
        val trackExternalUrl =
          if (trackId.isNotBlank() && !trackId.contains("/")) {
            "https://open.spotify.com/track/$trackId"
          } else {
            canonicalUrl
          }
        val trackCoverUrl = extractTrackCoverUrl(obj) ?: coverUrl

        SpotifyImportedTrack(
          title = trackTitle,
          artist = artist,
          durationMs = durationMs.coerceAtLeast(0L),
          externalUrl = trackExternalUrl,
          coverUrl = trackCoverUrl,
        )
      }

    return SpotifyPlaylistData(
      playlistId = playlistId,
      title = title,
      subtitle = ownerName,
      coverUrl = coverUrl,
      externalUrl = canonicalUrl,
      tracks = parsedTracks,
    )
  }

  private fun findEntityWithTracks(element: JsonElement): JsonObject? {
    when (element) {
      is JsonObject -> {
        if (element.containsKey("trackList") && element["trackList"] is JsonArray) {
          return element
        }
        for (value in element.values) {
          val found = findEntityWithTracks(value)
          if (found != null) return found
        }
      }
      is JsonArray -> {
        for (item in element) {
          val found = findEntityWithTracks(item)
          if (found != null) return found
        }
      }
      else -> Unit
    }
    return null
  }

  private fun extractCoverUrl(entity: JsonObject): String? {
    val coverSources =
      entity["coverArt"]?.asObjectOrNull()?.get("sources")?.asArrayOrNull()
        ?: entity["visualIdentity"]?.asObjectOrNull()?.get("image")?.asArrayOrNull()
        ?: entity["images"]?.asArrayOrNull()
    return coverSources
      ?.mapNotNull { it.asObjectOrNull()?.string("url") }
      ?.firstOrNull { it.startsWith("http") }
      ?: entity.string("coverUrl")
      ?: entity.string("image")
  }

  private fun extractTrackCoverUrl(trackObj: JsonObject): String? {
    val sources =
      trackObj["coverArt"]?.asObjectOrNull()?.get("sources")?.asArrayOrNull()
        ?: trackObj["visualIdentity"]?.asObjectOrNull()?.get("image")?.asArrayOrNull()
        ?: trackObj["albumOfTrack"]?.asObjectOrNull()?.get("coverArt")?.asObjectOrNull()?.get("sources")?.asArrayOrNull()
        ?: trackObj["album"]?.asObjectOrNull()?.get("images")?.asArrayOrNull()
    return sources
      ?.mapNotNull { it.asObjectOrNull()?.string("url") }
      ?.firstOrNull { it.startsWith("http") }
      ?: trackObj.string("coverUrl")
      ?: trackObj.string("thumbnailUrl")
      ?: trackObj.string("image")
  }

  private suspend fun enrichTracksWithPosters(
    tracks: List<SpotifyImportedTrack>,
    playlistCoverUrl: String?,
  ): List<SpotifyImportedTrack> = coroutineScope {
    val result = tracks.toMutableList()
    // Enrich tracks missing distinct per-track cover art via public Spotify oEmbed endpoint
    val indicesToFetch =
      tracks.indices.filter { idx ->
        val t = tracks[idx]
        t.externalUrl.contains("/track/") &&
          (t.coverUrl.isNullOrBlank() || t.coverUrl == playlistCoverUrl)
      }.take(40)

    indicesToFetch.chunked(6).forEach { chunk ->
      chunk
        .map { idx ->
          async(Dispatchers.IO) {
            val meta = fetchTrackOembedMeta(tracks[idx].externalUrl)
            idx to meta?.third
          }
        }
        .awaitAll()
        .forEach { (idx, thumbUrl) ->
          if (!thumbUrl.isNullOrBlank()) {
            result[idx] = result[idx].copy(coverUrl = thumbUrl)
          }
        }
    }
    result
  }

  private fun extractArtists(trackObj: JsonObject): String? {
    val artists = trackObj["artists"]?.asArrayOrNull() ?: return null
    val names = artists.mapNotNull { it.asObjectOrNull()?.string("name") }
    return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
  }

  private fun fetchTrackOembedMeta(trackUrl: String): Triple<String, String, String?>? {
    val res =
      fetchUrl("https://open.spotify.com/oembed?url=${URLEncoder.encode(trackUrl, "UTF-8")}").body
        ?: return null
    val obj = runCatching { json.parseToJsonElement(res).jsonObject }.getOrNull() ?: return null
    val rawTitle = obj.string("title") ?: return null
    val thumb = obj.string("thumbnail_url")?.takeIf { it.startsWith("http") }
    val parts = rawTitle.split(" - ", limit = 2)
    return if (parts.size == 2) {
      Triple(parts[0].trim(), parts[1].trim(), thumb)
    } else {
      Triple(rawTitle, "Spotify", thumb)
    }
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
          setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
          )
          setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
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

  private fun resolveRedirectUrl(urlString: String): String? {
    return runCatching {
      var current = urlString
      repeat(5) {
        val conn =
          (URL(current).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty(
              "User-Agent",
              "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36",
            )
          }
        try {
          val code = conn.responseCode
          if (code in 300..399) {
            val location = conn.getHeaderField("Location") ?: return@runCatching current
            current = if (location.startsWith("http")) location else URL(URL(current), location).toString()
          } else {
            return@runCatching current
          }
        } finally {
          conn.disconnect()
        }
      }
      current
    }.getOrNull()
  }

  private fun JsonObject.string(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.takeIf { it.isNotBlank() }

  private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

  private fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray

  private fun String.decodeHtmlEntities(): String =
    replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
}
