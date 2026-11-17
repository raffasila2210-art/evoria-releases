package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppUserPrefs

object EvoriaNotificationHelper {
  const val CHANNEL_INVITES = "evoria_invites"
  const val CHANNEL_SHARES = "evoria_shares"
  const val CHANNEL_CAPSULES = "evoria_capsules"
  const val CHANNEL_SECURITY = "evoria_security"
  const val CHANNEL_MUSIC = "evoria_music_playback"

  const val EXTRA_NOTIF_ROUTE = "evoria_notif_route"

  fun ensureChannels(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    val channels =
      listOf(
        NotificationChannel(
          CHANNEL_INVITES,
          "Undangan & Pasangan",
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "Notifikasi saat pasangan mengirim atau menerima undangan koneksi."
        },
        NotificationChannel(
          CHANNEL_SHARES,
          "Kiriman Kenangan & Musik",
          NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
          description = "Notifikasi saat pasangan membagikan foto, playlist, atau lagu."
        },
        NotificationChannel(
          CHANNEL_CAPSULES,
          "Kapsul Waktu & Pengingat",
          NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
          description = "Pemberitahuan kapsul waktu yang siap dibuka dan pengingat hangat."
        },
        NotificationChannel(
          CHANNEL_SECURITY,
          "Keamanan & Sesi Akun",
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "Peringatan keamanan kunci aplikasi, PIN, dan sesi masuk."
        },
        NotificationChannel(
          CHANNEL_MUSIC,
          "Pemutar Musik Latar Belakang",
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = "Kontrol pemutaran musik latar belakang di EVORIA."
          setShowBadge(false)
        },
      )
    channels.forEach { nm.createNotificationChannel(it) }
  }

  fun hasPostPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
  }

  fun channelForKind(kind: String): String {
    val k = kind.lowercase()
    return when {
      k.contains("invite") || k.contains("partner") -> CHANNEL_INVITES
      k.contains("capsule") -> CHANNEL_CAPSULES
      k.contains("security") || k.contains("lock") || k.contains("pin") -> CHANNEL_SECURITY
      k.contains("music") || k.contains("playlist") -> CHANNEL_MUSIC
      else -> CHANNEL_SHARES
    }
  }

  fun isCategoryEnabled(kind: String, prefs: AppUserPrefs): Boolean {
    val k = kind.lowercase()
    return when {
      k.contains("invite") || k.contains("partner") -> prefs.notificationInvites
      k.contains("capsule") -> prefs.notificationCapsules
      k.contains("security") || k.contains("lock") || k.contains("pin") -> prefs.notificationSecurity
      else -> prefs.notificationShares
    }
  }

  fun showNotificationIfEnabled(
    context: Context,
    notificationId: String,
    kind: String,
    title: String,
    body: String,
    prefs: AppUserPrefs,
    targetRoute: String = "notifications",
  ): Boolean {
    if (!isCategoryEnabled(kind, prefs)) return false
    if (!hasPostPermission(context)) return false
    ensureChannels(context)

    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_NOTIF_ROUTE, targetRoute)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        notificationId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val channel = channelForKind(kind)
    val builder =
      NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_evoria_brand_logo)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setPriority(
          if (channel == CHANNEL_INVITES || channel == CHANNEL_SECURITY) {
            NotificationCompat.PRIORITY_HIGH
          } else {
            NotificationCompat.PRIORITY_DEFAULT
          }
        )
        .setContentIntent(pendingIntent)

    return runCatching {
        NotificationManagerCompat.from(context).notify(notificationId.hashCode(), builder.build())
        true
      }
      .getOrDefault(false)
  }
}
