package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ml.IndexingStats
import com.example.ml.ModelState
import com.example.ui.theme.LocalEvoriaTokens

@Composable
fun EvoriaVisionStatusCard(
  modelState: ModelState,
  indexingStats: IndexingStats,
  isIndexingPhotos: Boolean,
  onDownloadModel: () -> Unit,
  onCancelDownload: () -> Unit,
  onDeleteModel: () -> Unit,
  onTriggerIndexing: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalEvoriaTokens.current
  var showDeleteConfirm by remember { mutableStateOf(false) }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Hapus Model AI Lokal?") },
      text = {
        Text(
          "Model EVRIA Vision Search Lite (~154 MB) akan dihapus untuk mengosongkan penyimpanan. Pencarian kata kunci tetap berfungsi dan model bisa diunduh kembali kapan saja."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteConfirm = false
            onDeleteModel()
          }
        ) {
          Text("Hapus", color = tokens.roseError)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) {
          Text("Batal", color = tokens.mutedText)
        }
      },
    )
  }

  EvoriaCard(modifier = modifier.testTag("vision_status_card")) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = Icons.Filled.AutoAwesome,
          contentDescription = "EVRIA Vision Search Lite",
          tint = tokens.primaryTerracotta,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = "EVRIA Vision Search Lite",
          style = MaterialTheme.typography.titleSmall,
          color = tokens.inkText,
        )
      }
      Surface(
        shape = RoundedCornerShape(50),
        color = tokens.sageSuccess.copy(alpha = 0.14f),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = tokens.sageSuccess,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "100% On-Device",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tokens.sageSuccess,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    when (modelState) {
      ModelState.NotDownloaded -> {
        Text(
          text = "Unduh model visual privat (~154 MB, sekali unduh) agar EVORIA dapat mencari isi foto secara offline tanpa mengirim foto ke cloud.",
          style = MaterialTheme.typography.bodySmall,
          color = tokens.mutedText,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = onDownloadModel,
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vision_download_button"),
        ) {
          Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Unduh Model AI (~154 MB)")
        }
      }

      is ModelState.Downloading -> {
        val downloadedMb = modelState.downloadedBytes / (1024f * 1024f)
        val totalMb = (modelState.totalBytes / (1024f * 1024f)).coerceAtLeast(1f)
        Text(
          text = "Mengunduh ${modelState.fileLabel} (%.1f / %.0f MB)...".format(downloadedMb, totalMb),
          style = MaterialTheme.typography.bodySmall,
          color = tokens.inkText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
          progress = { modelState.progress },
          color = tokens.primaryTerracotta,
          trackColor = tokens.secondarySurface,
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = onCancelDownload,
          shape = RoundedCornerShape(50),
          modifier = Modifier.heightIn(min = 48.dp).testTag("vision_cancel_download_button"),
        ) {
          Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Batalkan Unduhan", color = tokens.mutedText)
        }
      }

      is ModelState.Verifying -> {
        Text(
          text = "Memverifikasi checksum SHA-256 ${modelState.fileLabel}...",
          style = MaterialTheme.typography.bodySmall,
          color = tokens.inkText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
          color = tokens.primaryTerracotta,
          trackColor = tokens.secondarySurface,
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        )
      }

      is ModelState.Ready -> {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = tokens.sageSuccess,
            modifier = Modifier.size(16.dp),
          )
          val indexSummary =
            when {
              indexingStats.totalPhotoCount == 0 -> "Model siap. Tambahkan foto untuk mulai indeks visual."
              isIndexingPhotos -> "Mengindeks foto lokal: ${indexingStats.indexedCount}/${indexingStats.totalPhotoCount} selesai..."
              indexingStats.pendingCount > 0 ->
                "${indexingStats.indexedCount}/${indexingStats.totalPhotoCount} foto terindeks (${indexingStats.pendingCount} menunggu)"
              else -> "Semua ${indexingStats.indexedCount} foto terindeks & siap dicari offline."
            }
          Text(
            text = indexSummary,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.inkText,
            modifier = Modifier.weight(1f),
          )
        }

        if (isIndexingPhotos && indexingStats.totalPhotoCount > 0) {
          Spacer(modifier = Modifier.height(8.dp))
          LinearProgressIndicator(
            progress = { indexingStats.progressFraction },
            color = tokens.sageSuccess,
            trackColor = tokens.secondarySurface,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
          )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (indexingStats.pendingCount > 0 || indexingStats.failedCount > 0 || !isIndexingPhotos) {
            OutlinedButton(
              onClick = onTriggerIndexing,
              enabled = !isIndexingPhotos && indexingStats.totalPhotoCount > 0,
              shape = RoundedCornerShape(50),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("vision_index_now_button"),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (isIndexingPhotos) "Mengindeks..." else "Indeks Foto")
            }
          }
          TextButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.heightIn(min = 48.dp).testTag("vision_delete_model_button"),
          ) {
            Icon(
              Icons.Filled.DeleteOutline,
              contentDescription = "Hapus model AI lokal",
              tint = tokens.mutedText,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Hapus Model", color = tokens.mutedText)
          }
        }
      }

      is ModelState.UnsupportedDevice -> {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = tokens.mutedText, modifier = Modifier.size(18.dp))
          Text(
            text = "${modelState.reason} Pencarian kata kunci & sinonim lokal tetap aktif.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.mutedText,
          )
        }
      }

      is ModelState.Error -> {
        Text(
          text = modelState.message,
          style = MaterialTheme.typography.bodySmall,
          color = tokens.roseError,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = onDownloadModel,
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vision_retry_download_button"),
        ) {
          Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Coba Unduh Lagi")
        }
      }
    }
  }
}
