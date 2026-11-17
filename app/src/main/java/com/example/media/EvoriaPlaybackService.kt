package com.example.media

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.notifications.EvoriaNotificationHelper

object EvoriaPlaybackControlBus {
  const val ACTION_PLAY_PAUSE = "com.example.evoria.ACTION_PLAY_PAUSE"
  const val ACTION_NEXT = "com.example.evoria.ACTION_NEXT"
  const val ACTION_PREV = "com.example.evoria.ACTION_PREV"
  const val ACTION_STOP = "com.example.evoria.ACTION_STOP"

  @Volatile var onAction: ((String) -> Unit)? = null
}

class EvoriaPlaybackService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action
    when (action) {
      EvoriaPlaybackControlBus.ACTION_PLAY_PAUSE,
      EvoriaPlaybackControlBus.ACTION_NEXT,
      EvoriaPlaybackControlBus.ACTION_PREV -> {
        EvoriaPlaybackControlBus.onAction?.invoke(action)
        return START_NOT_STICKY
      }
      EvoriaPlaybackControlBus.ACTION_STOP -> {
        EvoriaPlaybackControlBus.onAction?.invoke(action)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
      }
    }

    val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Musik EVORIA" }
    val artist = intent?.getStringExtra(EXTRA_ARTIST).orEmpty().ifBlank { "Soundtrack Kenangan" }
    val isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, false) ?: false

    EvoriaNotificationHelper.ensureChannels(this)
    val notification = buildNotification(title, artist, isPlaying)

    if (isPlaying) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } else {
      stopForeground(STOP_FOREGROUND_DETACH)
      if (EvoriaNotificationHelper.hasPostPermission(this)) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
      }
    }

    return START_NOT_STICKY
  }

  private fun buildNotification(title: String, artist: String, isPlaying: Boolean) =
    NotificationCompat.Builder(this, EvoriaNotificationHelper.CHANNEL_MUSIC)
      .setSmallIcon(R.drawable.ic_evoria_brand_logo)
      .setContentTitle(title)
      .setContentText(artist)
      .setSubText(if (isPlaying) "Sedang Diputar di Latar Belakang" else "Dijeda")
      .setOngoing(isPlaying)
      .setOnlyAlertOnce(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setContentIntent(contentPendingIntent())
      .addAction(
        android.R.drawable.ic_media_previous,
        "Sebelumnya",
        serviceActionIntent(EvoriaPlaybackControlBus.ACTION_PREV, 101),
      )
      .addAction(
        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        if (isPlaying) "Jeda" else "Putar",
        serviceActionIntent(EvoriaPlaybackControlBus.ACTION_PLAY_PAUSE, 102),
      )
      .addAction(
        android.R.drawable.ic_media_next,
        "Berikutnya",
        serviceActionIntent(EvoriaPlaybackControlBus.ACTION_NEXT, 103),
      )
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        "Tutup",
        serviceActionIntent(EvoriaPlaybackControlBus.ACTION_STOP, 104),
      )
      .build()

  private fun contentPendingIntent(): PendingIntent {
    val openIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EvoriaNotificationHelper.EXTRA_NOTIF_ROUTE, "full_music_player")
      }
    return PendingIntent.getActivity(
      this,
      100,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun serviceActionIntent(action: String, reqCode: Int): PendingIntent {
    val intent = Intent(this, EvoriaPlaybackService::class.java).apply { this.action = action }
    return PendingIntent.getService(
      this,
      reqCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  companion object {
    const val NOTIFICATION_ID = 4401
    private const val EXTRA_TITLE = "extra_title"
    private const val EXTRA_ARTIST = "extra_artist"
    private const val EXTRA_IS_PLAYING = "extra_is_playing"

    fun syncPlayback(context: Context, state: MusicPlayerState) {
      val track = state.currentTrack
      if (track == null) {
        stopPlayback(context)
        return
      }
      val intent =
        Intent(context, EvoriaPlaybackService::class.java).apply {
          putExtra(EXTRA_TITLE, track.title)
          putExtra(EXTRA_ARTIST, track.artist)
          putExtra(EXTRA_IS_PLAYING, state.isPlaying)
        }
      runCatching {
        if (state.isPlaying) {
          ContextCompat.startForegroundService(context, intent)
        } else {
          context.startService(intent)
        }
      }
    }

    fun stopPlayback(context: Context) {
      runCatching {
        context.stopService(Intent(context, EvoriaPlaybackService::class.java))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
      }
    }
  }
}
