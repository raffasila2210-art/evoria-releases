package com.example.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.local.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MusicPlayerState(
  val currentTrack: TrackEntity? = null,
  val queue: List<TrackEntity> = emptyList(),
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val isYouTubeMode: Boolean = false,
  val activeYouTubeVideoId: String? = null,
  val errorMessage: String? = null,
)

sealed interface YouTubePlayerCommand {
  data class Load(val videoId: String, val startSeconds: Float = 0f) : YouTubePlayerCommand

  data object Play : YouTubePlayerCommand

  data object Pause : YouTubePlayerCommand

  data class SeekTo(val positionMs: Long) : YouTubePlayerCommand
}

class EvoriaPlayerManager(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var progressJob: Job? = null

  private val player: ExoPlayer by lazy {
    ExoPlayer.Builder(appContext).build().apply {
      addListener(
        object : Player.Listener {
          override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_state.value.isYouTubeMode) return
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) startProgressPolling() else stopProgressPolling()
          }

          override fun onPlaybackStateChanged(playbackState: Int) {
            if (_state.value.isYouTubeMode) return
            val dur = duration.coerceAtLeast(0L)
            _state.value = _state.value.copy(durationMs = dur)
            if (playbackState == Player.STATE_ENDED) {
              next()
            }
          }

          override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            if (_state.value.isYouTubeMode) return
            _state.value =
              _state.value.copy(
                isPlaying = false,
                errorMessage = "Tidak dapat memutar berkas audio: ${error.localizedMessage ?: "format tidak didukung"}",
              )
          }
        }
      )
    }
  }

  private val _state = MutableStateFlow(MusicPlayerState())
  val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

  private val _youtubeCommands = MutableSharedFlow<YouTubePlayerCommand>(extraBufferCapacity = 16)
  val youtubeCommands: SharedFlow<YouTubePlayerCommand> = _youtubeCommands.asSharedFlow()

  fun setQueueAndPlay(tracks: List<TrackEntity>, startTrackId: String? = null, resumePosMs: Long = 0L) {
    if (tracks.isEmpty()) return
    val target = tracks.firstOrNull { it.id == startTrackId } ?: tracks.first()

    // 1. Local offline full audio track takes priority if explicitly linked
    if (target.mediaUri.isNotBlank() && !target.mediaUri.startsWith("http")) {
      _state.value =
        _state.value.copy(
          queue = tracks,
          currentTrack = target,
          isYouTubeMode = false,
          activeYouTubeVideoId = null,
          errorMessage = null,
        )
      runCatching {
          player.setMediaItem(MediaItem.fromUri(Uri.parse(target.mediaUri)))
          player.prepare()
          if (resumePosMs > 0L) player.seekTo(resumePosMs)
          player.play()
        }
        .onFailure {
          _state.value = _state.value.copy(isPlaying = false, errorMessage = "Berkas audio lokal tidak dapat diputar.")
        }
      return
    }

    // 2. Full YouTube video track resolved via YouTube Data API v3
    val ytId = target.youtubeVideoId?.takeIf { it.isNotBlank() }
    if (ytId != null) {
      stopProgressPolling()
      runCatching { player.pause() }
      _state.value =
        _state.value.copy(
          queue = tracks,
          currentTrack = target,
          isPlaying = true,
          positionMs = resumePosMs,
          durationMs = target.durationMs.coerceAtLeast(0L),
          isYouTubeMode = true,
          activeYouTubeVideoId = ytId,
          errorMessage = null,
        )
      _youtubeCommands.tryEmit(YouTubePlayerCommand.Load(ytId, resumePosMs / 1000f))
      return
    }

    // 3. No local file and no YouTube match yet
    stopProgressPolling()
    runCatching { player.pause() }
    _state.value =
      _state.value.copy(
        queue = tracks,
        currentTrack = target,
        isPlaying = false,
        positionMs = 0L,
        durationMs = target.durationMs.coerceAtLeast(0L),
        isYouTubeMode = false,
        activeYouTubeVideoId = null,
        errorMessage =
          if (target.sourceType == "spotify") {
            "Lagu YouTube belum dicocokkan untuk trek ini. Ketuk 'Cocokkan YouTube' atau tautkan MP3 lokal."
          } else {
            "Berkas audio belum ditautkan untuk lagu ini."
          },
      )
  }

  fun togglePlayPause() {
    val cur = _state.value
    val track = cur.currentTrack ?: return
    if (cur.isYouTubeMode && !cur.activeYouTubeVideoId.isNullOrBlank()) {
      val nextPlaying = !cur.isPlaying
      _state.value = cur.copy(isPlaying = nextPlaying)
      _youtubeCommands.tryEmit(if (nextPlaying) YouTubePlayerCommand.Play else YouTubePlayerCommand.Pause)
      return
    }
    if (track.mediaUri.isBlank() || track.mediaUri.startsWith("http")) {
      setQueueAndPlay(cur.queue.ifEmpty { listOf(track) }, track.id, 0L)
      return
    }
    if (player.mediaItemCount == 0) {
      setQueueAndPlay(cur.queue.ifEmpty { listOf(track) }, track.id, cur.positionMs)
      return
    }
    if (player.isPlaying) {
      player.pause()
    } else {
      player.play()
    }
  }

  fun seekTo(positionMs: Long) {
    val clamped = positionMs.coerceAtLeast(0L)
    if (_state.value.isYouTubeMode) {
      _state.value = _state.value.copy(positionMs = clamped)
      _youtubeCommands.tryEmit(YouTubePlayerCommand.SeekTo(clamped))
      return
    }
    player.seekTo(clamped)
    _state.value = _state.value.copy(positionMs = clamped)
  }

  fun onYouTubePlaybackUpdate(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
    if (!_state.value.isYouTubeMode) return
    val resolvedDur = if (durationMs > 0L) durationMs else _state.value.durationMs
    _state.value =
      _state.value.copy(
        isPlaying = isPlaying,
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = resolvedDur,
      )
  }

  fun onYouTubeVideoEnded() {
    if (_state.value.isYouTubeMode) {
      next()
    }
  }

  fun onYouTubePlayerError(message: String) {
    if (_state.value.isYouTubeMode) {
      _state.value = _state.value.copy(isPlaying = false, errorMessage = message)
    }
  }

  fun pauseYouTubeIfLeavingPlayer() {
    if (_state.value.isYouTubeMode && _state.value.isPlaying) {
      _state.value = _state.value.copy(isPlaying = false)
    }
  }

  fun next() {
    val q = _state.value.queue
    val cur = _state.value.currentTrack ?: return
    if (q.isEmpty()) return
    val idx = q.indexOfFirst { it.id == cur.id }
    val nextTrack = q[(idx + 1).mod(q.size)]
    setQueueAndPlay(q, nextTrack.id)
  }

  fun previous() {
    val q = _state.value.queue
    val cur = _state.value.currentTrack ?: return
    if (q.isEmpty()) return
    val idx = q.indexOfFirst { it.id == cur.id }
    val prevTrack = q[(idx - 1).mod(q.size)]
    setQueueAndPlay(q, prevTrack.id)
  }

  private fun startProgressPolling() {
    progressJob?.cancel()
    progressJob =
      scope.launch {
        while (isActive) {
          _state.value =
            _state.value.copy(
              positionMs = player.currentPosition.coerceAtLeast(0L),
              durationMs = player.duration.coerceAtLeast(_state.value.currentTrack?.durationMs ?: 0L),
            )
          delay(500L)
        }
      }
  }

  private fun stopProgressPolling() {
    progressJob?.cancel()
    progressJob = null
  }

  fun refreshTrackInQueue(updated: TrackEntity) {
    val updatedQueue = _state.value.queue.map { if (it.id == updated.id) updated else it }
    if (_state.value.currentTrack?.id == updated.id) {
      setQueueAndPlay(updatedQueue.ifEmpty { listOf(updated) }, updated.id, 0L)
    } else if (updatedQueue.isNotEmpty()) {
      _state.value = _state.value.copy(queue = updatedQueue)
    }
  }

  fun clearPlaylistIfActive(playlistId: String) {
    val cur = _state.value
    val remainingQueue = cur.queue.filterNot { it.playlistId == playlistId }
    if (cur.currentTrack?.playlistId == playlistId) {
      stopProgressPolling()
      runCatching {
        player.stop()
        player.clearMediaItems()
      }
      _state.value = MusicPlayerState(queue = remainingQueue)
    } else if (remainingQueue.size != cur.queue.size) {
      _state.value = cur.copy(queue = remainingQueue)
    }
  }

  fun release() {
    stopProgressPolling()
    runCatching { player.release() }
  }
}
