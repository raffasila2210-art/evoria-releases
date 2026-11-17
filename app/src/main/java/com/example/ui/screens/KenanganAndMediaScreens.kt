package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.LocalAlbumEntity
import com.example.data.local.LocalMemoryEntity
import com.example.data.local.UploadJobEntity
import com.example.ui.components.EvoriaCard
import com.example.ui.components.EvoriaFormTextField
import com.example.ui.components.EvoriaPillSearchBar
import com.example.ui.components.EvoriaSanctuaryBackdrop
import com.example.ui.components.EvoriaSymmetricDialogShell
import com.example.ui.components.EvoriaSymmetricPrivacyRow
import com.example.ui.components.EvoriaTopBar
import com.example.ui.components.VisibilityBadge
import com.example.ui.components.formatIndoDate
import com.example.ui.components.formatIndoDateTime
import com.example.ui.components.rememberThumbnailRequest
import com.example.ui.theme.LocalEvoriaTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun filterAndSortMemories(
  memories: List<LocalMemoryEntity>,
  filterMode: String,
  sortNewestFirst: Boolean,
  albumFilterId: String? = null,
  searchQuery: String = "",
): List<LocalMemoryEntity> {
  val mode = filterMode.trim().lowercase()
  val normalizedQuery = searchQuery.trim().lowercase()
  val base =
    memories.filter { mem ->
      val albumOk = albumFilterId == null || mem.albumId == albumFilterId
      if (!albumOk) return@filter false
      val queryOk =
        normalizedQuery.isEmpty() ||
          mem.title.contains(normalizedQuery, ignoreCase = true) ||
          mem.caption.contains(normalizedQuery, ignoreCase = true) ||
          mem.story.contains(normalizedQuery, ignoreCase = true) ||
          mem.locationName.contains(normalizedQuery, ignoreCase = true) ||
          mem.tagsCsv.contains(normalizedQuery, ignoreCase = true)
      if (!queryOk) return@filter false
      val vis = mem.visibility.trim().lowercase()
      val type = mem.mediaType.trim().lowercase()
      when (mode) {
        "private" -> vis == "private"
        "shared" -> vis == "shared"
        "photo" -> type == "photo" || (!mem.mediaUri.isNullOrBlank() && type != "video" && type != "story")
        "video" -> type == "video"
        "story" -> type == "story" || mem.mediaUri.isNullOrBlank()
        "favorite" -> mem.isFavorite
        else -> true
      }
    }
  return if (sortNewestFirst) base.sortedByDescending { it.memoryDateMillis }
  else base.sortedBy { it.memoryDateMillis }
}

fun formatGallerySectionHeader(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
  val targetCal = Calendar.getInstance().apply { timeInMillis = millis }
  val todayCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
  val yesterdayCal = Calendar.getInstance().apply {
    timeInMillis = nowMillis
    add(Calendar.DAY_OF_YEAR, -1)
  }
  return when {
    targetCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
      targetCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) -> "Hari ini"
    targetCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
      targetCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR) -> "Kemarin"
    else -> formatIndoDate(millis)
  }
}

private data class GalleryDaySection(
  val dayKey: String,
  val locationSummary: String,
  val items: List<LocalMemoryEntity>,
)

private fun groupMemoriesByGalleryDay(
  memories: List<LocalMemoryEntity>,
  nowMillis: Long = System.currentTimeMillis(),
): List<GalleryDaySection> {
  if (memories.isEmpty()) return emptyList()
  val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
  val todayYear = cal.get(Calendar.YEAR)
  val todayDay = cal.get(Calendar.DAY_OF_YEAR)
  cal.add(Calendar.DAY_OF_YEAR, -1)
  val yesterdayYear = cal.get(Calendar.YEAR)
  val yesterdayDay = cal.get(Calendar.DAY_OF_YEAR)
  val formatter = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

  return memories
    .groupBy { mem ->
      cal.timeInMillis = mem.memoryDateMillis
      val y = cal.get(Calendar.YEAR)
      val d = cal.get(Calendar.DAY_OF_YEAR)
      when {
        y == todayYear && d == todayDay -> "Hari ini"
        y == yesterdayYear && d == yesterdayDay -> "Kemarin"
        else -> formatter.format(Date(mem.memoryDateMillis))
      }
    }
    .map { (dayLabel, dayItems) ->
      val locations =
        dayItems
          .mapNotNull { it.locationName.trim().takeIf(String::isNotEmpty) }
          .distinct()
          .take(2)
          .joinToString(", ")
      GalleryDaySection(dayKey = dayLabel, locationSummary = locations, items = dayItems)
    }
}

@Composable
fun GalleryMediaTile(
  memory: LocalMemoryEntity,
  isSelected: Boolean = false,
  multiSelectMode: Boolean = false,
  onClick: () -> Unit,
  onToggleFavorite: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalEvoriaTokens.current
  val isVideo = memory.mediaType.equals("video", ignoreCase = true)

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(RoundedCornerShape(2.dp))
        .background(
          if (memory.mediaUri != null) tokens.secondarySurface
          else tokens.accentPeach.copy(alpha = if (tokens.isDark) 0.16f else 0.24f)
        )
        .then(
          if (isSelected) Modifier.border(3.dp, tokens.primaryTerracotta, RoundedCornerShape(2.dp))
          else Modifier
        )
        .clickable(onClick = onClick)
        .testTag("memory_card_${memory.id}"),
  ) {
    if (memory.mediaUri != null) {
      val thumbRequest = rememberThumbnailRequest(memory.mediaUri, sizePx = 360)
      AsyncImage(
        model = thumbRequest,
        contentDescription = memory.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Box(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = memory.story.ifBlank { memory.caption.ifBlank { memory.title } },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
          color = tokens.inkText,
          maxLines = 4,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
      }
    }

    if (isSelected) {
      Box(modifier = Modifier.fillMaxSize().background(tokens.primaryTerracotta.copy(alpha = 0.24f)))
    }

    if (multiSelectMode) {
      Box(
        modifier =
          Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(if (isSelected) tokens.primaryTerracotta else Color.Black.copy(alpha = 0.42f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        if (isSelected) {
          Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Terpilih",
            tint = Color.White,
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }

    if (isVideo) {
      Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.46f),
        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Video",
            tint = Color.White,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = "Video",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
          )
        }
      }
    }

    if (memory.isFavorite) {
      Box(
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .padding(5.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .then(
              if (onToggleFavorite != null) Modifier.clickable(onClick = onToggleFavorite)
              else Modifier
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = "Kenangan favorit",
          tint = Color(0xFFFF6B6B),
          modifier = Modifier.size(13.dp),
        )
      }
    }
  }
}

@Composable
fun KenanganScreen(
  albums: List<LocalAlbumEntity>,
  memories: List<LocalMemoryEntity>,
  favoritesOnlyDefault: Boolean = false,
  onConsumeFavoritesDefault: () -> Unit = {},
  onOpenAlbum: (String) -> Unit,
  onOpenMemory: (String) -> Unit,
  onOpenAddMemory: () -> Unit,
  onImportGalleryPhotos: (List<Uri>) -> Unit = {},
  onSaveAlbum: (existingId: String?, title: String, desc: String, visibility: String, soundtrack: String?, coverUri: Uri?, existingCover: String?) -> Unit,
  onToggleFavorite: (LocalMemoryEntity) -> Unit,
  onDeleteMemories: (List<LocalMemoryEntity>) -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  var isGrid by rememberSaveable { mutableStateOf(true) }
  var sortNewestFirst by rememberSaveable { mutableStateOf(true) }
  var filterMode by rememberSaveable {
    mutableStateOf(if (favoritesOnlyDefault) "favorite" else "all")
  }
  var selectedAlbumFilterId by rememberSaveable { mutableStateOf<String?>(null) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var multiSelectMode by remember { mutableStateOf(false) }
  var selectedIds by remember { mutableStateOf(setOf<String>()) }
  var showAlbumDialog by remember { mutableStateOf(false) }
  var fabMenuExpanded by remember { mutableStateOf(false) }

  LaunchedEffect(favoritesOnlyDefault) {
    if (favoritesOnlyDefault) {
      filterMode = "favorite"
      onConsumeFavoritesDefault()
    }
  }

  LaunchedEffect(filterMode, selectedAlbumFilterId, searchQuery) {
    if (multiSelectMode) {
      selectedIds = emptySet()
    }
  }

  BackHandler(enabled = fabMenuExpanded) {
    fabMenuExpanded = false
  }

  val galleryDirectPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
      if (uris.isNotEmpty()) {
        onImportGalleryPhotos(uris)
      }
    }

  val filteredMemories = remember(memories, filterMode, sortNewestFirst, selectedAlbumFilterId, searchQuery) {
    filterAndSortMemories(
      memories = memories,
      filterMode = filterMode,
      sortNewestFirst = sortNewestFirst,
      albumFilterId = selectedAlbumFilterId,
      searchQuery = searchQuery,
    )
  }

  val activeAlbumTitle =
    selectedAlbumFilterId?.let { id -> albums.firstOrNull { it.id == id }?.title }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 96.dp)) {
      // Header + actions
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Kenangan", style = MaterialTheme.typography.displayMedium, color = tokens.inkText)
          val countText =
            if (filterMode == "all" && selectedAlbumFilterId == null) {
              "${memories.size} momen • ${albums.size} album"
            } else {
              "Menampilkan ${filteredMemories.size} dari ${memories.size} momen"
            }
          Text(
            text = "$countText • ${if (sortNewestFirst) "Terbaru" else "Terlama"}",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.mutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row {
          IconButton(
            onClick = { sortNewestFirst = !sortNewestFirst },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("kenangan_sort_toggle"),
          ) {
            Icon(
              Icons.Filled.Sort,
              contentDescription = if (sortNewestFirst) "Urutkan terlama" else "Urutkan terbaru",
              tint = if (sortNewestFirst) tokens.inkText else tokens.primaryTerracotta,
            )
          }
          IconButton(
            onClick = { isGrid = !isGrid },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_grid_timeline"),
          ) {
            Icon(
              imageVector = if (isGrid) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
              contentDescription = if (isGrid) "Tampilan Linimasa" else "Tampilan Grid",
              tint = tokens.inkText,
            )
          }
        }
      }

      EvoriaPillSearchBar(
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        onSearch = {},
        placeholder = "Cari kenangan...",
        inputTestTag = "kenangan_search_input",
        submitTestTag = "kenangan_search_submit",
        modifier = Modifier.padding(horizontal = 20.dp),
      )
      Spacer(modifier = Modifier.height(6.dp))

      if (multiSelectMode) {
        Surface(
          color = tokens.accentPeach.copy(alpha = 0.25f),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
          shape = RoundedCornerShape(16.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("${selectedIds.size} dipilih", style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
            Row {
              TextButton(
                onClick = {
                  val targets = memories.filter { it.id in selectedIds }
                  onDeleteMemories(targets)
                  selectedIds = emptySet()
                  multiSelectMode = false
                }
              ) {
                Text("Hapus", color = tokens.roseError)
              }
              TextButton(
                onClick = {
                  selectedIds = emptySet()
                  multiSelectMode = false
                }
              ) {
                Text("Selesai", color = tokens.primaryTerracotta)
              }
            }
          }
        }
      }

      // Albums Carousel
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Album Kita", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
        TextButton(
          onClick = { showAlbumDialog = true },
          modifier = Modifier.testTag("create_album_button"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Album Baru", color = tokens.primaryTerracotta)
        }
      }

      LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(albums, key = { it.id }, contentType = { "album_card" }) { album ->
          val count = remember(memories, album.id) { memories.count { it.albumId == album.id } }
          Surface(
            modifier = Modifier.width(152.dp).clickable { onOpenAlbum(album.id) },
            shape = RoundedCornerShape(18.dp),
            color = tokens.cardSurface,
            border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.6f)),
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Box(
                modifier = Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).background(tokens.accentPeach.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
              ) {
                if (album.coverUri != null) {
                  val coverRequest = rememberThumbnailRequest(album.coverUri, sizePx = 320)
                  AsyncImage(
                    model = coverRequest,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                  )
                } else {
                  Icon(Icons.Filled.Collections, contentDescription = null, tint = tokens.primaryTerracotta)
                }
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text(album.title, style = MaterialTheme.typography.titleSmall, color = tokens.inkText, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text("$count item • ${if (album.visibility == "shared") "Bersama" else "Pribadi"}", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Filter Chips
      LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val filters = listOf(
          "all" to "Semua",
          "private" to "Pribadi",
          "shared" to "Bersama",
          "favorite" to "Favorit",
          "photo" to "Foto",
          "video" to "Video",
          "story" to "Cerita",
        )
        items(filters) { (key, label) ->
          FilterChip(
            selected = filterMode == key,
            onClick = { filterMode = key },
            label = { Text(label) },
            modifier = Modifier.testTag("kenangan_filter_$key"),
          )
        }
        if (albums.isNotEmpty()) {
          items(albums, key = { "filter_alb_${it.id}" }) { alb ->
            val albSelected = selectedAlbumFilterId == alb.id
            FilterChip(
              selected = albSelected,
              onClick = {
                selectedAlbumFilterId = if (albSelected) null else alb.id
              },
              leadingIcon = {
                Icon(Icons.Filled.Collections, contentDescription = null, modifier = Modifier.size(14.dp))
              },
              label = { Text(alb.title, maxLines = 1) },
            )
          }
        }
        item {
          FilterChip(
            selected = multiSelectMode,
            onClick = {
              multiSelectMode = !multiSelectMode
              if (!multiSelectMode) selectedIds = emptySet()
            },
            label = { Text("Pilih Banyak") },
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      if (filteredMemories.isEmpty()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          EvoriaCard {
            val emptyArtReq = rememberThumbnailRequest(R.drawable.img_empty_memories_1794808523116, sizePx = 640)
            AsyncImage(
              model = emptyArtReq,
              contentDescription = "Tidak ada kenangan",
              modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)),
              contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text =
                if (activeAlbumTitle != null) "Belum ada kenangan di album \"$activeAlbumTitle\""
                else "Belum ada kenangan di filter ini",
              style = MaterialTheme.typography.titleMedium,
              color = tokens.inkText,
            )
            Text(
              "Tambahkan foto, video, atau cerita baru dengan tombol + di kanan bawah.",
              style = MaterialTheme.typography.bodySmall,
              color = tokens.mutedText,
            )
            if (filterMode != "all" || selectedAlbumFilterId != null) {
              Spacer(modifier = Modifier.height(10.dp))
              OutlinedButton(
                onClick = {
                  filterMode = "all"
                  selectedAlbumFilterId = null
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("kenangan_reset_filter"),
              ) {
                Text("Tampilkan Semua Kenangan")
              }
            }
          }
        }
      } else if (isGrid) {
        val groupedSections =
          remember(filteredMemories) {
            groupMemoriesByGalleryDay(filteredMemories)
          }

        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 104.dp),
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          groupedSections.forEach { section ->
            item(
              key = "hdr_${section.dayKey}",
              span = { GridItemSpan(maxLineSpan) },
              contentType = "gallery_header",
            ) {
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(
                  text = section.dayKey,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = tokens.inkText,
                )
                if (section.locationSummary.isNotBlank()) {
                  Text(
                    text = section.locationSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
            items(
              items = section.items,
              key = { it.id },
              contentType = { "gallery_tile" },
            ) { mem ->
              val isSelected = mem.id in selectedIds
              GalleryMediaTile(
                memory = mem,
                isSelected = isSelected,
                multiSelectMode = multiSelectMode,
                onClick = {
                  if (multiSelectMode) {
                    selectedIds = if (isSelected) selectedIds - mem.id else selectedIds + mem.id
                  } else {
                    onOpenMemory(mem.id)
                  }
                },
                onToggleFavorite = { onToggleFavorite(mem) },
              )
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 36.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(
            items = filteredMemories,
            key = { it.id },
            contentType = { "timeline_card" },
          ) { mem ->
            EvoriaCard(onClick = { onOpenMemory(mem.id) }) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(formatIndoDate(mem.memoryDateMillis), style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
                VisibilityBadge(mem.visibility)
              }
              Spacer(modifier = Modifier.height(6.dp))
              if (mem.mediaUri != null) {
                val timelineRequest = rememberThumbnailRequest(mem.mediaUri, sizePx = 640)
                AsyncImage(
                  model = timelineRequest,
                  contentDescription = mem.title,
                  modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)),
                  contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.height(8.dp))
              }
              Text(mem.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
              if (mem.caption.isNotBlank()) {
                Text(mem.caption, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText, maxLines = 2)
              }
            }
          }
        }
      }
    }

    // Scrim backdrop when Speed-Dial FAB Menu is open
    AnimatedVisibility(
      visible = fabMenuExpanded,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (tokens.isDark) 0.56f else 0.36f))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) { fabMenuExpanded = false }
      )
    }

    // Expandable 3-function + FAB menu
    Column(
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 104.dp),
    ) {
      AnimatedVisibility(
        visible = fabMenuExpanded,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
      ) {
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          KenanganSpeedDialOption(
            label = "Buat Album Baru",
            icon = Icons.Filled.Collections,
            testTag = "fab_action_create_album",
            onClick = {
              fabMenuExpanded = false
              showAlbumDialog = true
            },
          )
          KenanganSpeedDialOption(
            label = "Impor Foto dari Galeri",
            icon = Icons.Filled.PhotoLibrary,
            testTag = "fab_action_import_gallery",
            onClick = {
              fabMenuExpanded = false
              galleryDirectPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
          )
          KenanganSpeedDialOption(
            label = "Buat Kenangan",
            icon = Icons.Filled.Create,
            isPrimary = true,
            testTag = "fab_action_create_memory",
            onClick = {
              fabMenuExpanded = false
              onOpenAddMemory()
            },
          )
        }
      }

      val iconRotation by animateFloatAsState(
        targetValue = if (fabMenuExpanded) 45f else 0f,
        label = "kenangan_fab_rotate",
      )
      FloatingActionButton(
        onClick = { fabMenuExpanded = !fabMenuExpanded },
        containerColor = tokens.primaryTerracotta,
        contentColor = Color.White,
        shape = CircleShape,
        modifier = Modifier.testTag("kenangan_fab_add"),
      ) {
        Icon(
          Icons.Filled.Add,
          contentDescription = if (fabMenuExpanded) "Tutup menu tambah" else "Menu tambah kenangan",
          modifier = Modifier.rotate(iconRotation),
        )
      }
    }
  }

  if (showAlbumDialog) {
    AlbumEditorDialog(
      existing = null,
      onDismiss = { showAlbumDialog = false },
      onSave = { title, desc, vis, soundtrack, coverUri ->
        onSaveAlbum(null, title, desc, vis, soundtrack, coverUri, null)
        showAlbumDialog = false
      },
    )
  }
}

@Composable
private fun KenanganSpeedDialOption(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isPrimary: Boolean = false,
  testTag: String,
  onClick: () -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.clickable(onClick = onClick).testTag(testTag),
  ) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = if (isPrimary) tokens.primaryTerracotta else tokens.elevatedCard,
      shadowElevation = 6.dp,
      tonalElevation = 4.dp,
      border =
        BorderStroke(
          1.dp,
          if (isPrimary) tokens.primaryTerracotta else tokens.outlineSoft.copy(alpha = 0.7f),
        ),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (isPrimary) Color.White else tokens.inkText,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      )
    }
    SmallFloatingActionButton(
      onClick = onClick,
      containerColor = if (isPrimary) tokens.primaryTerracotta else tokens.elevatedCard,
      contentColor = if (isPrimary) Color.White else tokens.primaryTerracotta,
      shape = CircleShape,
    ) {
      Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
  }
}

@Composable
fun AlbumEditorDialog(
  existing: LocalAlbumEntity?,
  onDismiss: () -> Unit,
  onSave: (title: String, desc: String, visibility: String, soundtrack: String?, coverUri: Uri?) -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  var title by remember { mutableStateOf(existing?.title.orEmpty()) }
  var desc by remember { mutableStateOf(existing?.description.orEmpty()) }
  var visibility by remember { mutableStateOf(existing?.visibility ?: "private") }
  var soundtrack by remember { mutableStateOf(existing?.soundtrackTitle.orEmpty()) }
  var coverUri by remember { mutableStateOf<Uri?>(null) }

  val coverPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) coverUri = uri
    }

  EvoriaSymmetricDialogShell(
    title = if (existing == null) "Buat Album Baru" else "Ubah Album",
    subtitle = "Kelompokkan foto dan momen berdua dengan rapi",
    icon = Icons.Filled.Collections,
    onDismiss = onDismiss,
    confirmLabel = "Simpan",
    onConfirm = { onSave(title, desc, visibility, soundtrack.ifBlank { null }, coverUri) },
  ) {
    EvoriaFormTextField(
      value = title,
      onValueChange = { title = it },
      label = "Judul Album",
      placeholder = "Misal: Perjalanan Ke Danau",
      singleLine = true,
    )
    EvoriaFormTextField(
      value = desc,
      onValueChange = { desc = it },
      label = "Deskripsi Singkat",
      placeholder = "Cerita singkat mengenai album ini...",
      singleLine = false,
      minLines = 2,
      maxLines = 3,
    )
    EvoriaFormTextField(
      value = soundtrack,
      onValueChange = { soundtrack = it },
      label = "Lagu Terkait (Opsional)",
      leadingIcon = Icons.Filled.MusicNote,
      singleLine = true,
    )
    Text("Privasi Album", style = MaterialTheme.typography.labelLarge, color = tokens.primaryTerracotta)
    EvoriaSymmetricPrivacyRow(
      visibility = visibility,
      onVisibilityChange = { visibility = it },
    )
    OutlinedButton(
      onClick = {
        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, tokens.primaryTerracotta.copy(alpha = 0.5f)),
      modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
      Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = if (coverUri != null) "Sampul Album Dipilih" else "Pilih Sampul Album",
        color = tokens.primaryTerracotta,
      )
    }
  }
}

@Composable
fun AlbumDetailScreen(
  album: LocalAlbumEntity,
  memories: List<LocalMemoryEntity>,
  onBack: () -> Unit,
  onOpenMemory: (String) -> Unit,
  onEditAlbum: (title: String, desc: String, visibility: String, soundtrack: String?, coverUri: Uri?) -> Unit,
  onDeleteAlbum: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val tokens = LocalEvoriaTokens.current
  var showEdit by remember { mutableStateOf(false) }

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(
        title = album.title,
        subtitle = "${memories.size} kenangan • ${if (album.visibility == "shared") "Bersama" else "Pribadi"}",
        onBack = onBack,
        trailingAction = {
          Row {
            IconButton(onClick = { showEdit = true }) {
              Icon(Icons.Filled.Edit, contentDescription = "Ubah album", tint = tokens.inkText)
            }
            IconButton(onClick = onDeleteAlbum) {
              Icon(Icons.Filled.Delete, contentDescription = "Hapus album", tint = tokens.roseError)
            }
          }
        },
      )
      val sortedAlbumMemories = remember(memories) { memories.sortedByDescending { it.memoryDateMillis } }
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(top = 4.dp, bottom = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        if (album.description.isNotBlank() || !album.soundtrackTitle.isNullOrBlank()) {
          item(
            span = { GridItemSpan(maxLineSpan) },
            contentType = "album_header",
          ) {
            EvoriaCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
              if (album.description.isNotBlank()) {
                Text(album.description, style = MaterialTheme.typography.bodyMedium, color = tokens.inkText)
              }
              if (!album.soundtrackTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Filled.MusicNote, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Soundtrack: ${album.soundtrackTitle}", style = MaterialTheme.typography.labelMedium, color = tokens.primaryTerracotta)
                }
              }
            }
          }
        }
        items(
          items = sortedAlbumMemories,
          key = { it.id },
          contentType = { "gallery_tile" },
        ) { mem ->
          GalleryMediaTile(
            memory = mem,
            onClick = { onOpenMemory(mem.id) },
          )
        }
      }
    }
  }

  if (showEdit) {
    AlbumEditorDialog(
      existing = album,
      onDismiss = { showEdit = false },
      onSave = { t, d, v, s, c ->
        onEditAlbum(t, d, v, s, c)
        showEdit = false
      },
    )
  }
}

@Composable
fun MemoryDetailScreen(
  memory: LocalMemoryEntity,
  onBack: () -> Unit,
  onToggleFavorite: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onSendEvoriaShare: () -> Unit,
  onOpenFullscreenPhoto: () -> Unit,
  onOpenFullscreenVideo: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val tokens = LocalEvoriaTokens.current
  var confirmDelete by remember { mutableStateOf(false) }

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(
        title = memory.title,
        subtitle = formatIndoDate(memory.memoryDateMillis),
        onBack = onBack,
        trailingAction = {
          Row {
            IconButton(onClick = onToggleFavorite) {
              Icon(
                imageVector = if (memory.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorit",
                tint = if (memory.isFavorite) tokens.primaryTerracotta else tokens.inkText,
              )
            }
            IconButton(onClick = onEdit) {
              Icon(Icons.Filled.Edit, contentDescription = "Ubah kenangan", tint = tokens.inkText)
            }
            IconButton(onClick = { confirmDelete = true }) {
              Icon(Icons.Filled.Delete, contentDescription = "Hapus kenangan", tint = tokens.roseError)
            }
          }
        },
      )

      Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        if (memory.mediaUri != null) {
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable {
                  if (memory.mediaType == "video") onOpenFullscreenVideo() else onOpenFullscreenPhoto()
                }
          ) {
            AsyncImage(
              model = memory.mediaUri,
              contentDescription = memory.title,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
            if (memory.mediaType == "video") {
              Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "Putar video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(56.dp),
              )
            }
          }
        }

        EvoriaCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            VisibilityBadge(memory.visibility)
            Text("Status: ${memory.syncState}", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
          }
          if (memory.caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(memory.caption, style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
          }
          if (memory.story.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(memory.story, style = MaterialTheme.typography.bodyLarge, color = tokens.inkText)
          }
          if (memory.locationName.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.LocationOn, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(memory.locationName, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
            }
          }
          if (memory.tagsCsv.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tag: ${memory.tagsCsv}", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
          }
        }

        Button(
          onClick = onSendEvoriaShare,
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
          modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
          Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Kirim via EVORIA Share ke Pasangan")
        }
      }
    }
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Hapus Kenangan?") },
      text = { Text("Kenangan \"${memory.title}\" akan dihapus permanen dari perangkat ini.") },
      confirmButton = {
        Button(
          onClick = {
            confirmDelete = false
            onDelete()
          },
          colors = ButtonDefaults.buttonColors(containerColor = tokens.roseError),
        ) { Text("Hapus") }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) { Text("Batal") }
      },
    )
  }
}

@Composable
fun AddOrEditMemoryScreen(
  existing: LocalMemoryEntity?,
  albums: List<LocalAlbumEntity>,
  onBack: () -> Unit,
  onSaveMediaMemories: (uris: List<Uri>, title: String, caption: String, story: String, location: String, albumId: String?, visibility: String, tagsCsv: String) -> Unit,
  onSaveStoryOnly: (existing: LocalMemoryEntity?, title: String, caption: String, story: String, location: String, albumId: String?, visibility: String, tagsCsv: String) -> Unit,
) {
  BackHandler(onBack = onBack)
  val tokens = LocalEvoriaTokens.current
  var title by remember { mutableStateOf(existing?.title.orEmpty()) }
  var caption by remember { mutableStateOf(existing?.caption.orEmpty()) }
  var story by remember { mutableStateOf(existing?.story.orEmpty()) }
  var location by remember { mutableStateOf(existing?.locationName.orEmpty()) }
  var tagsCsv by remember { mutableStateOf(existing?.tagsCsv.orEmpty()) }
  var visibility by remember { mutableStateOf(existing?.visibility ?: "private") }
  var selectedAlbumId by remember { mutableStateOf(existing?.albumId) }
  var pickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

  val mediaPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
      if (uris.isNotEmpty()) pickedUris = uris
    }

  EvoriaSanctuaryBackdrop {
    Column(
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      EvoriaTopBar(
        title = if (existing == null) "Simpan Kenangan Baru" else "Ubah Kenangan",
        subtitle = "Foto, video, atau cerita hangat berdua",
        onBack = onBack,
      )
      Column(
        modifier =
          Modifier
            .weight(1f)
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        if (existing == null) {
          EvoriaCard(elevated = true) {
            Text("Pilih Foto atau Video", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Atau biarkan kosong jika ingin menyimpan cerita tertulis saja.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
              onClick = {
                mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
              },
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, tokens.primaryTerracotta.copy(alpha = 0.55f)),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pick_media_button"),
            ) {
              Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (pickedUris.isEmpty()) "Pilih Media dari Perangkat" else "${pickedUris.size} Media Dipilih",
                color = tokens.primaryTerracotta,
              )
            }
            if (pickedUris.isNotEmpty()) {
              Spacer(modifier = Modifier.height(10.dp))
              LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(pickedUris) { uri ->
                  AsyncImage(
                    model = uri,
                    contentDescription = "Media terpilih",
                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                  )
                }
              }
            }
          }
        }

        EvoriaCard(elevated = true) {
          Text("Detail & Cerita Momen", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
          Spacer(modifier = Modifier.height(10.dp))
          EvoriaFormTextField(
            value = title,
            onValueChange = { title = it },
            label = "Judul Kenangan",
            placeholder = "Misal: Senja di dermaga berdua",
            singleLine = true,
          )
          Spacer(modifier = Modifier.height(12.dp))
          EvoriaFormTextField(
            value = caption,
            onValueChange = { caption = it },
            label = "Caption Singkat",
            singleLine = false,
            minLines = 1,
            maxLines = 2,
          )
          Spacer(modifier = Modifier.height(12.dp))
          EvoriaFormTextField(
            value = story,
            onValueChange = { story = it },
            label = "Cerita Lengkap",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
          )
          Spacer(modifier = Modifier.height(12.dp))
          EvoriaFormTextField(
            value = location,
            onValueChange = { location = it },
            label = "Lokasi (Opsional)",
            leadingIcon = Icons.Filled.LocationOn,
            singleLine = true,
          )
          Spacer(modifier = Modifier.height(12.dp))
          EvoriaFormTextField(
            value = tagsCsv,
            onValueChange = { tagsCsv = it },
            label = "Tag Pencarian (mis: pantai, senja, kopi)",
            singleLine = true,
          )
        }

        EvoriaCard {
          Text("Privasi & Album", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
          Spacer(modifier = Modifier.height(10.dp))
          EvoriaSymmetricPrivacyRow(
            visibility = visibility,
            onVisibilityChange = { visibility = it },
          )
          if (albums.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              item {
                FilterChip(selected = selectedAlbumId == null, onClick = { selectedAlbumId = null }, label = { Text("Tanpa Album") })
              }
              items(albums, key = { it.id }) { alb ->
                FilterChip(selected = selectedAlbumId == alb.id, onClick = { selectedAlbumId = alb.id }, label = { Text(alb.title) })
              }
            }
          }
        }
      }

      Box(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).padding(horizontal = 20.dp, vertical = 16.dp),
      ) {
        Button(
          onClick = {
            if (existing == null && pickedUris.isNotEmpty()) {
              onSaveMediaMemories(pickedUris, title, caption, story, location, selectedAlbumId, visibility, tagsCsv)
            } else {
              onSaveStoryOnly(existing, title, caption, story, location, selectedAlbumId, visibility, tagsCsv)
            }
          },
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_memory_button"),
        ) {
          Text("Simpan Kenangan", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

@Composable
fun FullscreenPhotoViewer(mediaUri: String, title: String, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Color.Black)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(1f, 4f)
            offset = if (scale > 1f) offset + pan else Offset.Zero
          }
        }
  ) {
    AsyncImage(
      model = mediaUri,
      contentDescription = title,
      modifier =
        Modifier
          .fillMaxSize()
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
          },
      contentScale = ContentScale.Fit,
    )
    IconButton(
      onClick = onBack,
      modifier = Modifier.align(Alignment.TopStart).padding(12.dp).minimumInteractiveComponentSize(),
    ) {
      Icon(Icons.Filled.Close, contentDescription = "Tutup penampil foto", tint = Color.White)
    }
  }
}

@Composable
fun FullscreenVideoViewer(mediaUri: String, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val exoPlayer = remember(mediaUri) {
    ExoPlayer.Builder(context).build().apply {
      setMediaItem(MediaItem.fromUri(Uri.parse(mediaUri)))
      prepare()
      playWhenReady = true
    }
  }
  DisposableEffect(exoPlayer) {
    onDispose { exoPlayer.release() }
  }

  Box(modifier = Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
    AndroidView(
      factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
      modifier = Modifier.fillMaxSize(),
    )
    IconButton(
      onClick = onBack,
      modifier = Modifier.align(Alignment.TopStart).padding(12.dp).minimumInteractiveComponentSize(),
    ) {
      Icon(Icons.Filled.Close, contentDescription = "Tutup pemutar video", tint = Color.White)
    }
  }
}

@Composable
fun UploadQueueScreen(
  jobs: List<UploadJobEntity>,
  onRetry: (UploadJobEntity) -> Unit,
  onDelete: (String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val tokens = LocalEvoriaTokens.current

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(title = "Antrean Unggahan", subtitle = "Pemulihan otomatis & backoff", onBack = onBack)
      if (jobs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          EvoriaCard {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tokens.sageSuccess, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Semua antrean unggahan bersih", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(jobs, key = { it.id }) { job ->
            EvoriaCard {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(job.caption.ifBlank { "Media ${job.targetVisibility}" }, style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
                  Text("Status: ${job.status} • Percobaan: ${job.attemptCount}", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
                  Text(formatIndoDateTime(job.updatedAtMillis), style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
                }
                Row {
                  if (job.status == "failed" || job.status == "queued") {
                    IconButton(onClick = { onRetry(job) }) {
                      Icon(Icons.Filled.Refresh, contentDescription = "Coba ulang", tint = tokens.primaryTerracotta)
                    }
                  }
                  IconButton(onClick = { onDelete(job.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus antrean", tint = tokens.roseError)
                  }
                }
              }
              if (!job.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(job.lastError, style = MaterialTheme.typography.bodySmall, color = tokens.roseError)
              }
            }
          }
        }
      }
    }
  }
}
