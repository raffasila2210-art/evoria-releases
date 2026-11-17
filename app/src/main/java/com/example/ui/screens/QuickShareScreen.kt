package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.LocalAlbumEntity
import com.example.ui.components.EvoriaCard
import com.example.ui.components.EvoriaFormTextField
import com.example.ui.components.EvoriaSanctuaryBackdrop
import com.example.ui.components.EvoriaSymmetricPrivacyRow
import com.example.ui.components.EvoriaTopBar
import com.example.ui.theme.LocalEvoriaTokens

@Composable
fun QuickShareScreen(
  sharedUris: List<Uri>,
  albums: List<LocalAlbumEntity>,
  hasConnectedPartner: Boolean,
  onCreateInlineAlbum: (title: String, visibility: String) -> Unit,
  onConfirmSave: (
    uris: List<Uri>,
    title: String,
    caption: String,
    albumId: String?,
    visibility: String,
  ) -> Unit,
  onCancel: () -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  var title by remember { mutableStateOf("") }
  var caption by remember { mutableStateOf("") }
  var visibility by remember { mutableStateOf("private") }
  var selectedAlbumId by remember { mutableStateOf<String?>(null) }
  var showInlineNewAlbum by remember { mutableStateOf(false) }
  var newAlbumName by remember { mutableStateOf("") }

  EvoriaSanctuaryBackdrop {
    Column(
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      EvoriaTopBar(
        title = "Quick Share ke EVORIA",
        subtitle = "${sharedUris.size} media siap disimpan",
        onBack = onCancel,
      )

      Column(
        modifier =
          Modifier
            .weight(1f)
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        LazyRow(
          contentPadding = PaddingValues(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(sharedUris) { uri ->
            AsyncImage(
              model = uri,
              contentDescription = "Pratinjau media yang dibagikan",
              modifier = Modifier.size(136.dp).clip(RoundedCornerShape(14.dp)),
              contentScale = ContentScale.Crop,
            )
          }
        }

        EvoriaCard {
          Text("Privasi Kiriman", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
          Spacer(modifier = Modifier.height(10.dp))
          EvoriaSymmetricPrivacyRow(
            visibility = visibility,
            onVisibilityChange = { visibility = it },
            privateTestTag = "quick_share_private",
            sharedTestTag = "quick_share_shared",
          )
          if (visibility == "shared" && !hasConnectedPartner) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              "Catatan: Pasangan belum terhubung. Media tetap disimpan aman di perangkat dan akan masuk antrean sinkronisasi.",
              style = MaterialTheme.typography.bodySmall,
              color = tokens.mutedText,
            )
          }
        }

        EvoriaCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Pilih Album", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
            OutlinedButton(
              onClick = { showInlineNewAlbum = !showInlineNewAlbum },
              shape = RoundedCornerShape(50),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Album Baru")
            }
          }

          if (showInlineNewAlbum) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              EvoriaFormTextField(
                value = newAlbumName,
                onValueChange = { newAlbumName = it },
                label = "Nama Album Baru",
                leadingIcon = Icons.Filled.Collections,
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              Button(
                onClick = {
                  if (newAlbumName.isNotBlank()) {
                    onCreateInlineAlbum(newAlbumName, visibility)
                    newAlbumName = ""
                    showInlineNewAlbum = false
                  }
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
                modifier = Modifier.height(48.dp),
              ) {
                Text("Buat")
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              FilterChip(
                selected = selectedAlbumId == null,
                onClick = { selectedAlbumId = null },
                label = { Text("Tanpa Album") },
              )
            }
            items(albums, key = { it.id }) { album ->
              FilterChip(
                selected = selectedAlbumId == album.id,
                onClick = { selectedAlbumId = album.id },
                label = { Text(album.title) },
              )
            }
          }
        }

        EvoriaCard {
          Text("Catatan Media", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
          Spacer(modifier = Modifier.height(10.dp))
          EvoriaFormTextField(
            value = title,
            onValueChange = { title = it },
            label = "Judul Singkat (Opsional)",
            singleLine = true,
          )
          Spacer(modifier = Modifier.height(12.dp))
          EvoriaFormTextField(
            value = caption,
            onValueChange = { caption = it },
            label = "Caption / Cerita Singkat (Opsional)",
            singleLine = false,
            minLines = 2,
            maxLines = 4,
          )
        }
      }

      Box(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).padding(horizontal = 20.dp, vertical = 16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f).height(52.dp),
          ) {
            Text("Batal", color = tokens.inkText)
          }
          Button(
            onClick = { onConfirmSave(sharedUris, title, caption, selectedAlbumId, visibility) },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
            modifier = Modifier.weight(1.25f).height(52.dp).testTag("quick_share_save_button"),
          ) {
            Text("Simpan ke EVORIA", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }
}
