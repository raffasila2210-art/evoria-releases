package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.JournalEntity
import com.example.data.local.LocalMemoryEntity
import com.example.data.local.UploadJobEntity
import com.example.data.remote.CoupleConnectionDto
import com.example.data.remote.SharedMemoryDto
import com.example.data.remote.UserProfileDto
import com.example.ui.components.EvoriaActionChip
import com.example.ui.components.EvoriaCard
import com.example.ui.components.VisibilityBadge
import com.example.ui.components.formatIndoDate
import com.example.ui.components.rememberThumbnailRequest
import com.example.ui.theme.LocalEvoriaTokens
import java.util.Calendar

@Composable
fun BerandaScreen(
  profile: UserProfileDto?,
  fallbackName: String,
  couple: CoupleConnectionDto?,
  localMemories: List<LocalMemoryEntity>,
  sharedMemories: List<SharedMemoryDto>,
  journals: List<JournalEntity>,
  uploadJobs: List<UploadJobEntity>,
  unreadNotifCount: Int,
  lastViewedMemoryId: String?,
  onOpenAddMemory: () -> Unit,
  onOpenConnectPartner: () -> Unit,
  onOpenMemoryDetail: (String) -> Unit,
  onOpenFavorites: () -> Unit,
  onOpenNotifications: () -> Unit,
  onOpenUploadQueue: () -> Unit,
  onOpenMusic: () -> Unit,
  onOpenRefleksi: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalEvoriaTokens.current
  val greeting = remember {
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
      in 4..10 -> "Selamat Pagi"
      in 11..14 -> "Selamat Siang"
      in 15..18 -> "Selamat Sore"
      else -> "Selamat Malam"
    }
  }
  val userName = profile?.displayName?.ifBlank { fallbackName } ?: fallbackName
  val partnerName = profile?.partnerDisplayName ?: couple?.let {
    if (it.memberOneName == userName) it.memberTwoName else it.memberOneName
  }

  val onThisDayMemories = remember(localMemories) {
    val today = Calendar.getInstance()
    val m = today.get(Calendar.MONTH)
    val d = today.get(Calendar.DAY_OF_MONTH)
    localMemories.filter {
      val cal = Calendar.getInstance().apply { timeInMillis = it.memoryDateMillis }
      cal.get(Calendar.MONTH) == m && cal.get(Calendar.DAY_OF_MONTH) == d
    }
  }
  val favorites = remember(localMemories) { localMemories.filter { it.isFavorite }.take(5) }
  val continueMemory = remember(localMemories, lastViewedMemoryId) {
    localMemories.firstOrNull { it.id == lastViewedMemoryId } ?: localMemories.firstOrNull()
  }
  val pendingJobs = remember(uploadJobs) {
    uploadJobs.count { it.status == "queued" || it.status == "processing" || it.status == "failed" }
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    // Header greeting & notification bell
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!profile?.avatarUrl.isNullOrBlank()) {
          val avatarReq = rememberThumbnailRequest(profile?.avatarUrl, sizePx = 140)
          AsyncImage(
            model = avatarReq,
            contentDescription = userName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape),
          )
          Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "$greeting, $userName",
            style = MaterialTheme.typography.displayMedium,
            color = tokens.inkText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "A place you've been before.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.mutedText,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (pendingJobs > 0) {
            IconButton(
              onClick = onOpenUploadQueue,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("beranda_queue_button"),
            ) {
              BadgedBox(badge = { Badge { Text("$pendingJobs") } }) {
                Icon(Icons.Filled.CloudUpload, contentDescription = "Antrean unggahan", tint = tokens.primaryTerracotta)
              }
            }
          }
          IconButton(
            onClick = onOpenNotifications,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("beranda_notifications_button"),
          ) {
            BadgedBox(badge = { if (unreadNotifCount > 0) Badge { Text("$unreadNotifCount") } }) {
              Icon(Icons.Filled.Notifications, contentDescription = "Notifikasi", tint = tokens.inkText)
            }
          }
        }
      }
    }

    // Partner Sanctuary Card
    item {
      EvoriaCard(elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(tokens.accentPeach.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (couple != null) Icons.Filled.People else Icons.Filled.Link,
              contentDescription = null,
              tint = tokens.primaryTerracotta,
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            if (couple != null) {
              Text(
                text = "Terhubung dengan ${partnerName ?: "Pasangan"}",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.inkText,
              )
              Text(
                text = couple.anniversaryLabel ?: "Ruang berdua aktif",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
              )
            } else {
              Text(
                text = "Belum Terhubung dengan Pasangan",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.inkText,
              )
              Text(
                text = "Buat kode 6 karakter atau masukkan undangan pasanganmu.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
              )
            }
          }
        }
        if (couple == null) {
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = onOpenConnectPartner,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
            modifier = Modifier.fillMaxWidth().testTag("beranda_connect_partner_button"),
          ) {
            Text("Hubungkan Pasangan")
          }
        }
      }
    }

    // Quick Action Row
    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
          EvoriaActionChip(
            label = "Tambah Kenangan",
            icon = Icons.Filled.AddPhotoAlternate,
            selected = true,
            onClick = onOpenAddMemory,
            modifier = Modifier.testTag("quick_add_memory_chip"),
          )
        }
        item {
          EvoriaActionChip(
            label = "Favorit (${favorites.size})",
            icon = Icons.Filled.Favorite,
            onClick = onOpenFavorites,
          )
        }
        item {
          EvoriaActionChip(
            label = "Musik Kita",
            icon = Icons.Filled.MusicNote,
            onClick = onOpenMusic,
          )
        }
        item {
          EvoriaActionChip(
            label = "Tulis Refleksi",
            icon = Icons.Filled.AutoAwesome,
            onClick = onOpenRefleksi,
          )
        }
      }
    }

    // Continue Viewing or Empty Welcome Story
    item {
      if (continueMemory != null) {
        Text("Lanjutkan Melihat", style = MaterialTheme.typography.headlineSmall, color = tokens.inkText)
        Spacer(modifier = Modifier.height(10.dp))
        EvoriaCard(onClick = { onOpenMemoryDetail(continueMemory.id) }, elevated = true) {
          if (continueMemory.mediaUri != null) {
            val heroThumb = rememberThumbnailRequest(continueMemory.mediaUri, sizePx = 720)
            AsyncImage(
              model = heroThumb,
              contentDescription = continueMemory.title,
              modifier = Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(16.dp)),
              contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(12.dp))
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = continueMemory.title,
              style = MaterialTheme.typography.titleLarge,
              color = tokens.inkText,
              modifier = Modifier.weight(1f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            VisibilityBadge(continueMemory.visibility)
          }
          if (continueMemory.caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = continueMemory.caption,
              style = MaterialTheme.typography.bodyMedium,
              color = tokens.mutedText,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = formatIndoDate(continueMemory.memoryDateMillis),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.mutedText,
          )
        }
      } else {
        EvoriaCard(elevated = true) {
          val emptyArtReq = rememberThumbnailRequest(R.drawable.img_empty_memories_1794808523116, sizePx = 640)
          AsyncImage(
            model = emptyArtReq,
            contentDescription = "Album kenangan kosong",
            modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
          )
          Spacer(modifier = Modifier.height(14.dp))
          Text("Belum Ada Kenangan Tersimpan", style = MaterialTheme.typography.titleLarge, color = tokens.inkText)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            "Mulai simpan foto, video, atau cerita hangat pertamamu. Kamu juga bisa langsung berbagi dari galeri Android lewat menu Bagikan > EVORIA.",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.mutedText,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = onOpenAddMemory,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
            modifier = Modifier.testTag("empty_add_memory_button"),
          ) {
            Text("Simpan Kenangan Pertama")
          }
        }
      }
    }

    // Pada Hari Ini
    if (onThisDayMemories.isNotEmpty()) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.History, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Pada Hari Ini", style = MaterialTheme.typography.headlineSmall, color = tokens.inkText)
        }
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(onThisDayMemories.take(6), key = { it.id }) { mem ->
            Surface(
              modifier = Modifier.width(190.dp).clickable { onOpenMemoryDetail(mem.id) },
              shape = RoundedCornerShape(18.dp),
              color = tokens.cardSurface,
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                if (mem.mediaUri != null) {
                  val cardThumb = rememberThumbnailRequest(mem.mediaUri, sizePx = 380)
                  AsyncImage(
                    model = cardThumb,
                    contentDescription = mem.title,
                    modifier = Modifier.fillMaxWidth().height(105.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                }
                Text(mem.title, style = MaterialTheme.typography.titleSmall, color = tokens.inkText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatIndoDate(mem.memoryDateMillis), style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
              }
            }
          }
        }
      }
    }

    // Shared Memories Preview
    if (sharedMemories.isNotEmpty()) {
      item {
        Text("Kenangan Bersama Terbaru", style = MaterialTheme.typography.headlineSmall, color = tokens.inkText)
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          sharedMemories.take(3).forEach { shared ->
            EvoriaCard {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(shared.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkText, modifier = Modifier.weight(1f))
                VisibilityBadge("shared")
              }
              if (!shared.caption.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(shared.caption, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText, maxLines = 2)
              }
            }
          }
        }
      }
    }

    // Recent Reflections Preview
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Refleksi Terakhir", style = MaterialTheme.typography.headlineSmall, color = tokens.inkText)
        TextButton(onClick = onOpenRefleksi) { Text("Buka Refleksi", color = tokens.primaryTerracotta) }
      }
      if (journals.isEmpty()) {
        EvoriaCard(onClick = onOpenRefleksi) {
          Text("Belum ada catatan jurnal", style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
          Text("Tuangkan perasaanmu hari ini atau tinggalkan pesan di Kapsul Waktu.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
        }
      } else {
        val latest = journals.first()
        EvoriaCard(onClick = onOpenRefleksi) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(latest.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
            VisibilityBadge(latest.visibility)
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(latest.body, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
