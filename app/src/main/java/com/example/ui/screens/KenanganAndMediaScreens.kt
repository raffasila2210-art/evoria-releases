package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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
import com.example.data.local.KenanganCollectionEntity
import com.example.data.local.LocalAlbumEntity
import com.example.data.local.LocalMemoryEntity
import com.example.data.local.UploadJobEntity
import com.example.ui.AiMemoryDraftUiState
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
import com.example.util.MediaStorageHelper
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
  val zone = ZoneId.systemDefault()
  val targetDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
  val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
  return when (targetDate) {
    today -> "Hari ini"
    today.minusDays(1) -> "Kemarin"
    else -> formatIndoDate(millis)
  }
}

fun computeRangeSelection(
  orderedIds: List<String>,
  anchorId: String,
  hoveredId: String,
  baseSelection: Set<String> = emptySet(),
): Set<String> {
  val startIdx = orderedIds.indexOf(anchorId)
  val endIdx = orderedIds.indexOf(hoveredId)
  if (startIdx < 0 || endIdx < 0) return baseSelection + anchorId
  val lo = minOf(startIdx, endIdx)
  val hi = maxOf(startIdx, endIdx)
  return baseSelection + orderedIds.subList(lo, hi + 1)
}

private fun findVisibleMemoryIdAtPosition(
  gridState: LazyGridState,
  localPosition: Offset,
  allowGapTolerance: Boolean = false,
): String? {
  val x = localPosition.x
  val y = localPosition.y
  val tiles =
    gridState.layoutInfo.visibleItemsInfo.filter { item ->
      val key = item.key as? String ?: return@filter false
      !key.startsWith("hdr_") && !key.startsWith("album_hdr_")
    }
  for (item in tiles) {
    val left = item.offset.x.toFloat()
    val top = item.offset.y.toFloat()
    val right = left + item.size.width.toFloat()
    val bottom = top + item.size.height.toFloat()
    if (x in left..right && y in top..bottom) {
      return item.key as? String
    }
  }
  if (!allowGapTolerance || tiles.isEmpty()) return null

  val gapPad = 12f
  for (item in tiles) {
    val left = item.offset.x.toFloat() - gapPad
    val top = item.offset.y.toFloat() - gapPad
    val right = item.offset.x.toFloat() + item.size.width.toFloat() + gapPad
    val bottom = item.offset.y.toFloat() + item.size.height.toFloat() + gapPad
    if (x in left..right && y in top..bottom) {
      return item.key as? String
    }
  }

  // Fallback for dragging past the right side of a partial last row: snap to nearest tile in that row
  val sameRow =
    tiles.filter { item ->
      val top = item.offset.y.toFloat() - gapPad
      val bottom = item.offset.y.toFloat() + item.size.height.toFloat() + gapPad
      y in top..bottom
    }
  if (sameRow.isNotEmpty()) {
    return sameRow.minByOrNull { item ->
      val left = item.offset.x.toFloat()
      val right = left + item.size.width.toFloat()
      when {
        x < left -> left - x
        x > right -> x - right
        else -> 0f
      }
    }?.key as? String
  }
  return null
}

@Composable
private fun Modifier.photoGridDragSelection(
  gridState: LazyGridState,
  orderedIds: List<String>,
  multiSelectMode: Boolean,
  onStartDragSelection: (anchorId: String) -> Unit,
  onUpdateDragSelection: (hoveredId: String) -> Unit,
  onEndDragSelection: () -> Unit,
): Modifier {
  val latestOrderedIds by rememberUpdatedState(orderedIds)
  val latestMultiSelect by rememberUpdatedState(multiSelectMode)
  val latestOnStart by rememberUpdatedState(onStartDragSelection)
  val latestOnUpdate by rememberUpdatedState(onUpdateDragSelection)
  val latestOnEnd by rememberUpdatedState(onEndDragSelection)
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val edgeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
  val maxScrollSpeedPxPerFrame = with(LocalDensity.current) { 18.dp.toPx() }

  return this.pointerInput(gridState) {
    var autoScrollJob: Job? = null
    try {
      awaitEachGesture {
        autoScrollJob?.cancel()
        autoScrollJob = null

        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (latestOrderedIds.isEmpty()) return@awaitEachGesture
        val pointerId = down.id
        val startPos = down.position
        val touchSlop = viewConfiguration.touchSlop
        val holdTimeoutMs = viewConfiguration.longPressTimeoutMillis.coerceIn(260L, 400L)

        var latestPos = startPos
        val waitResult =
          withTimeoutOrNull(holdTimeoutMs) {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              val change = event.changes.firstOrNull { it.id == pointerId } ?: return@withTimeoutOrNull "cancel"
              if (!change.pressed) {
                return@withTimeoutOrNull "cancel"
              }
              latestPos = change.position
              val delta = latestPos - startPos
              if (delta.getDistance() > touchSlop) {
                if (latestMultiSelect && abs(delta.x) > abs(delta.y) * 1.35f) {
                  change.consume()
                  return@withTimeoutOrNull "immediate_horizontal"
                }
                return@withTimeoutOrNull "cancel"
              }
            }
            @Suppress("UNREACHABLE_CODE")
            "cancel"
          }

        if (waitResult != null && waitResult != "immediate_horizontal") {
          return@awaitEachGesture
        }

        val anchorPos = if (waitResult == "immediate_horizontal") startPos else latestPos
        val anchorId =
          findVisibleMemoryIdAtPosition(gridState, anchorPos, allowGapTolerance = true)
            ?: return@awaitEachGesture

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        latestOnStart(anchorId)
        if (latestPos != anchorPos) {
          findVisibleMemoryIdAtPosition(gridState, latestPos, allowGapTolerance = true)?.let { hovered ->
            latestOnUpdate(hovered)
          }
        }

        var currentDragPos = latestPos
        var isDragging = true
        autoScrollJob =
          scope.launch {
            while (isDragging) {
              val viewportH = gridState.layoutInfo.viewportSize.height.toFloat()
              val py = currentDragPos.y
              val verticalDeltaFromAnchor = py - anchorPos.y
              val scrollDelta =
                when {
                  viewportH <= edgeThresholdPx * 2.1f -> 0f
                  py < edgeThresholdPx && verticalDeltaFromAnchor < -touchSlop && gridState.canScrollBackward -> {
                    val ratio = ((edgeThresholdPx - py) / edgeThresholdPx).coerceIn(0.15f, 1.4f)
                    -maxScrollSpeedPxPerFrame * ratio
                  }
                  py > viewportH - edgeThresholdPx && verticalDeltaFromAnchor > touchSlop && gridState.canScrollForward -> {
                    val ratio = ((py - (viewportH - edgeThresholdPx)) / edgeThresholdPx).coerceIn(0.15f, 1.4f)
                    maxScrollSpeedPxPerFrame * ratio
                  }
                  else -> 0f
                }
              if (scrollDelta != 0f) {
                gridState.scrollBy(scrollDelta)
                findVisibleMemoryIdAtPosition(gridState, currentDragPos, allowGapTolerance = true)?.let { hovered ->
                  latestOnUpdate(hovered)
                }
              }
              delay(16L)
            }
          }

        try {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null || !change.pressed) {
              change?.consume()
              break
            }
            change.consume()
            currentDragPos = change.position
            findVisibleMemoryIdAtPosition(gridState, currentDragPos, allowGapTolerance = true)?.let { hovered ->
              latestOnUpdate(hovered)
            }
          }
        } finally {
          isDragging = false
          autoScrollJob?.cancel()
          autoScrollJob = null
          latestOnEnd()
        }
      }
    } finally {
      autoScrollJob?.cancel()
    }
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
  val zone = ZoneId.systemDefault()
  val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
  val yesterday = today.minusDays(1)

  return memories
    .groupBy { mem ->
      val d = Instant.ofEpochMilli(mem.memoryDateMillis).atZone(zone).toLocalDate()
      when (d) {
        today -> "Hari ini"
        yesterday -> "Kemarin"
        else -> formatIndoDate(mem.memoryDateMillis)
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

enum class KenanganSubPage(val labelId: String) {
  KENANGAN("Kenangan"),
  ALBUM("Album"),
  GALERI("Galeri"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryMediaTile(
  memory: LocalMemoryEntity,
  isSelected: Boolean = false,
  multiSelectMode: Boolean = false,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
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
        .then(
          if (onLongClick != null) {
            Modifier.combinedClickable(
              onClick = onClick,
              onLongClick = onLongClick,
            )
          } else {
            Modifier.clickable(onClick = onClick)
          }
        )
        .testTag("memory_card_${memory.id}"),
  ) {
    if (memory.mediaUri != null) {
      val thumbRequest = rememberThumbnailRequest(memory.mediaUri, sizePx = 240)
      AsyncImage(
        model = thumbRequest,
        contentDescription = memory.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Box(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = memory.story.ifBlank { memory.caption.ifBlank { memory.title } },
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
          color = tokens.inkText,
          maxLines = 4,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
      }
    }

    if (isSelected) {
      Box(modifier = Modifier.fillMaxSize().background(tokens.primaryTerracotta.copy(alpha = 0.28f)))
    }

    if (multiSelectMode) {
      Box(
        modifier =
          Modifier
            .align(Alignment.TopStart)
            .padding(5.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(if (isSelected) tokens.primaryTerracotta else Color.Black.copy(alpha = 0.45f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        if (isSelected) {
          Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Terpilih",
            tint = Color.White,
            modifier = Modifier.size(13.dp),
          )
        }
      }
    }

    if (isVideo) {
      Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.52f),
        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Video",
            tint = Color.White,
            modifier = Modifier.size(11.dp),
          )
          Text(
            text = "Video",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
          )
        }
      }
    }

    if (memory.isFavorite && !multiSelectMode) {
      Box(
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp)
            .size(20.dp)
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
          modifier = Modifier.size(12.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun KenanganScreen(
  albums: List<LocalAlbumEntity>,
  memories: List<LocalMemoryEntity>,
  kenanganCollections: List<KenanganCollectionEntity> = emptyList(),
  favoritesOnlyDefault: Boolean = false,
  onConsumeFavoritesDefault: () -> Unit = {},
  onOpenAlbum: (String) -> Unit,
  onOpenKenangan: (String) -> Unit = {},
  onCreateKenangan: (title: String, description: String, albumIds: List<String>, visibility: String) -> Unit = { _, _, _, _ -> },
  onDeleteKenangan: (String) -> Unit = {},
  onOpenMemory: (String) -> Unit,
  onOpenAddMemory: () -> Unit,
  onImportGalleryPhotos: (List<Uri>) -> Unit = {},
  onSaveAlbum: (existingId: String?, title: String, desc: String, visibility: String, soundtrack: String?, coverUri: Uri?, existingCover: String?) -> Unit,
  onToggleFavorite: (LocalMemoryEntity) -> Unit,
  onDeleteMemories: (List<LocalMemoryEntity>) -> Unit,
) {
  val context = LocalContext.current
  val tokens = LocalEvoriaTokens.current
  var activeSubPage by rememberSaveable {
    mutableStateOf(if (favoritesOnlyDefault) KenanganSubPage.GALERI else KenanganSubPage.KENANGAN)
  }
  var sortNewestFirst by rememberSaveable { mutableStateOf(true) }
  var filterMode by rememberSaveable {
    mutableStateOf(if (favoritesOnlyDefault) "favorite" else "all")
  }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var multiSelectMode by remember { mutableStateOf(false) }
  var selectedIds by remember { mutableStateOf(setOf<String>()) }
  var confirmBatchDelete by remember { mutableStateOf(false) }
  var showAlbumDialog by remember { mutableStateOf(false) }
  var showCreateKenanganDialog by remember { mutableStateOf(false) }
  var kenanganPendingDelete by remember { mutableStateOf<KenanganCollectionEntity?>(null) }
  var fabMenuExpanded by remember { mutableStateOf(false) }

  LaunchedEffect(favoritesOnlyDefault) {
    if (favoritesOnlyDefault) {
      activeSubPage = KenanganSubPage.GALERI
      filterMode = "favorite"
      onConsumeFavoritesDefault()
    }
  }

  LaunchedEffect(filterMode, searchQuery, activeSubPage) {
    if (multiSelectMode) {
      selectedIds = emptySet()
    }
  }

  BackHandler(enabled = fabMenuExpanded || multiSelectMode) {
    when {
      fabMenuExpanded -> fabMenuExpanded = false
      multiSelectMode -> {
        multiSelectMode = false
        selectedIds = emptySet()
      }
    }
  }

  val galleryDirectPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
      if (uris.isNotEmpty()) {
        onImportGalleryPhotos(uris)
      }
    }

  val filteredKenanganCollections =
    remember(kenanganCollections, searchQuery, filterMode, sortNewestFirst, albums) {
      val q = searchQuery.trim()
      val albumMap = albums.associateBy { it.id }
      val base =
        kenanganCollections.filter { col ->
          val visOk =
            when (filterMode) {
              "private" -> col.visibility.equals("private", ignoreCase = true)
              "shared" -> col.visibility.equals("shared", ignoreCase = true)
              else -> true
            }
          if (!visOk) return@filter false
          if (q.isEmpty()) return@filter true
          val albumTitles = col.albumIdList().mapNotNull { albumMap[it]?.title }.joinToString(" ")
          col.title.contains(q, ignoreCase = true) ||
            col.description.contains(q, ignoreCase = true) ||
            albumTitles.contains(q, ignoreCase = true)
        }
      if (sortNewestFirst) base.sortedByDescending { it.updatedAtMillis } else base.sortedBy { it.updatedAtMillis }
    }

  val galleryMemories = remember(memories, filterMode, sortNewestFirst, searchQuery) {
    val effectiveFilter =
      when (filterMode) {
        "favorite", "photo", "video", "private", "shared" -> filterMode
        else -> "all"
      }
    val mediaOnly = memories.filter { !it.mediaUri.isNullOrBlank() || it.mediaType == "story" }
    filterAndSortMemories(
      memories = mediaOnly,
      filterMode = effectiveFilter,
      sortNewestFirst = sortNewestFirst,
      albumFilterId = null,
      searchQuery = searchQuery,
    )
  }

  val filteredAlbums = remember(albums, searchQuery, sortNewestFirst) {
    val q = searchQuery.trim()
    val base =
      if (q.isEmpty()) albums
      else albums.filter {
        it.title.contains(q, ignoreCase = true) ||
          it.description.contains(q, ignoreCase = true) ||
          it.soundtrackTitle.orEmpty().contains(q, ignoreCase = true)
      }
    if (sortNewestFirst) base.sortedByDescending { it.updatedAtMillis } else base.sortedBy { it.updatedAtMillis }
  }

  val albumCounts = remember(memories) {
    memories.mapNotNull { it.albumId }.groupingBy { it }.eachCount()
  }
  val albumsById = remember(albums) { albums.associateBy { it.id } }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 96.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = activeSubPage.labelId,
            style = MaterialTheme.typography.displayMedium,
            color = tokens.inkText,
          )
          val countText =
            when (activeSubPage) {
              KenanganSubPage.KENANGAN -> "${filteredKenanganCollections.size} gabungan album"
              KenanganSubPage.ALBUM -> "${albums.size} album tersimpan"
              KenanganSubPage.GALERI -> "${galleryMemories.size} foto & video"
            }
          Text(
            text = "$countText • ${if (sortNewestFirst) "Terbaru" else "Terlama"}",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.mutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (activeSubPage == KenanganSubPage.GALERI) {
            IconButton(
              onClick = {
                multiSelectMode = !multiSelectMode
                if (!multiSelectMode) selectedIds = emptySet()
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("kenangan_multiselect_toggle"),
            ) {
              Icon(
                imageVector = Icons.Filled.Checklist,
                contentDescription = if (multiSelectMode) "Keluar pilih banyak" else "Pilih banyak foto",
                tint = if (multiSelectMode) tokens.primaryTerracotta else tokens.inkText,
              )
            }
          }
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
        }
      }

      Surface(
        shape = RoundedCornerShape(50),
        color = tokens.secondarySurface.copy(alpha = if (tokens.isDark) 0.72f else 0.85f),
        border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          KenanganSubPage.entries.forEach { page ->
            val selected = activeSubPage == page
            Surface(
              shape = RoundedCornerShape(50),
              color = if (selected) tokens.primaryTerracotta else Color.Transparent,
              modifier =
                Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(50))
                  .clickable {
                    activeSubPage = page
                    if (page != KenanganSubPage.GALERI) {
                      multiSelectMode = false
                      selectedIds = emptySet()
                    }
                  }
                  .testTag("kenangan_subtab_${page.name.lowercase()}"),
            ) {
              Text(
                text = page.labelId,
                style = MaterialTheme.typography.labelLarge.copy(
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) Color.White else tokens.mutedText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      if (multiSelectMode && activeSubPage == KenanganSubPage.GALERI) {
        Surface(
          color = tokens.elevatedCard,
          border = BorderStroke(1.dp, tokens.primaryTerracotta.copy(alpha = 0.45f)),
          shape = RoundedCornerShape(50),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "${selectedIds.size} dipilih",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = tokens.inkText,
              maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              TextButton(
                onClick = {
                  val allIds = galleryMemories.map { it.id }.toSet()
                  selectedIds = if (selectedIds.size == allIds.size && allIds.isNotEmpty()) emptySet() else allIds
                },
                modifier = Modifier.testTag("kenangan_multiselect_select_all"),
              ) {
                Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  if (selectedIds.size == galleryMemories.size && galleryMemories.isNotEmpty()) "Batal"
                  else "Semua",
                )
              }
              TextButton(
                onClick = {
                  selectedIds = emptySet()
                  multiSelectMode = false
                },
                modifier = Modifier.testTag("kenangan_multiselect_close"),
              ) {
                Text("Selesai", color = tokens.mutedText)
              }
            }
          }
        }
      } else {
        EvoriaPillSearchBar(
          query = searchQuery,
          onQueryChange = { searchQuery = it },
          onSearch = {},
          placeholder =
            when (activeSubPage) {
              KenanganSubPage.KENANGAN -> "Cari kompilasi kenangan atau album..."
              KenanganSubPage.ALBUM -> "Cari album..."
              KenanganSubPage.GALERI -> "Cari foto, video, atau lokasi..."
            },
          inputTestTag = "kenangan_search_input",
          submitTestTag = "kenangan_search_submit",
          modifier = Modifier.padding(horizontal = 20.dp),
        )
      }
      Spacer(modifier = Modifier.height(6.dp))

      AnimatedContent(
        targetState = activeSubPage,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
        modifier = Modifier.weight(1f),
        label = "kenangan_subpage_transition",
      ) { subPage ->
        when (subPage) {
          KenanganSubPage.KENANGAN -> {
            Column(modifier = Modifier.fillMaxSize()) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Kompilasi Kenangan", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                  Text(
                    "Gabungkan beberapa album jadi satu kenangan utuh",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.mutedText,
                  )
                }
                OutlinedButton(
                  onClick = { showCreateKenanganDialog = true },
                  shape = RoundedCornerShape(50),
                  border = BorderStroke(1.dp, tokens.primaryTerracotta.copy(alpha = 0.65f)),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                  modifier = Modifier.testTag("create_kenangan_collection_button"),
                ) {
                  Icon(Icons.Filled.AutoStories, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(5.dp))
                  Text("Buat Kenangan", color = tokens.primaryTerracotta, maxLines = 1)
                }
              }

              LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                val filters = listOf("all" to "Semua", "private" to "Pribadi", "shared" to "Bersama")
                items(filters) { (key, label) ->
                  FilterChip(
                    selected = filterMode == key,
                    onClick = { filterMode = key },
                    label = { Text(label) },
                    modifier = Modifier.testTag("kenangan_filter_$key"),
                  )
                }
              }

              if (filteredKenanganCollections.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                  EvoriaCard {
                    val emptyArtReq = rememberThumbnailRequest(R.drawable.img_empty_memories_1794808523116, sizePx = 540)
                    AsyncImage(
                      model = emptyArtReq,
                      contentDescription = "Belum ada kompilasi kenangan",
                      modifier = Modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(14.dp)),
                      contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                      text = "Kenangan Adalah Gabungan Album",
                      style = MaterialTheme.typography.titleMedium,
                      color = tokens.inkText,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text =
                        if (albums.isEmpty()) {
                          "Buat album di tab Album terlebih dahulu, lalu gabungkan beberapa album menjadi satu Kenangan spesial."
                        } else {
                          "Pilih dua atau lebih album yang sudah ada untuk digabungkan ke dalam satu halaman Kenangan."
                        },
                      style = MaterialTheme.typography.bodySmall,
                      color = tokens.mutedText,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                      onClick = {
                        if (albums.isEmpty()) {
                          activeSubPage = KenanganSubPage.ALBUM
                          showAlbumDialog = true
                        } else {
                          showCreateKenanganDialog = true
                        }
                      },
                      shape = RoundedCornerShape(50),
                      colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
                      modifier = Modifier.fillMaxWidth().testTag("kenangan_empty_cta_button"),
                    ) {
                      Text(if (albums.isEmpty()) "Buat Album Pertama" else "Gabungkan Album Sekarang")
                    }
                  }
                }
              } else {
                LazyColumn(
                  modifier = Modifier.weight(1f).testTag("kenangan_list"),
                  contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 36.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  items(
                    items = filteredKenanganCollections,
                    key = { it.id },
                    contentType = { "kenangan_collection_card" },
                  ) { col ->
                    val mergedIds = col.albumIdList()
                    val mergedAlbums = mergedIds.mapNotNull { albumsById[it] }
                    val mergedMemories = memories.filter { it.albumId in mergedIds }
                    val heroCover =
                      col.coverUri
                        ?: mergedAlbums.firstNotNullOfOrNull { it.coverUri }
                        ?: mergedMemories.firstOrNull { !it.mediaUri.isNullOrBlank() }?.mediaUri

                    Surface(
                      shape = RoundedCornerShape(tokens.cardRadius),
                      color = tokens.elevatedCard,
                      border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.55f)),
                      modifier =
                        Modifier
                          .fillMaxWidth()
                          .clip(RoundedCornerShape(tokens.cardRadius))
                          .clickable { onOpenKenangan(col.id) }
                          .testTag("kenangan_collection_${col.id}"),
                    ) {
                      Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically,
                        ) {
                          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.AutoStories, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(16.dp))
                            Text(
                              text = "${mergedAlbums.size} Album • ${mergedMemories.size} Item",
                              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                              color = tokens.primaryTerracotta,
                            )
                          }
                          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            VisibilityBadge(col.visibility)
                            IconButton(
                              onClick = { kenanganPendingDelete = col },
                              modifier = Modifier.size(30.dp).testTag("delete_kenangan_${col.id}"),
                            ) {
                              Icon(Icons.Filled.Delete, contentDescription = "Hapus ${col.title}", tint = tokens.roseError, modifier = Modifier.size(18.dp))
                            }
                          }
                        }

                        if (!heroCover.isNullOrBlank()) {
                          Spacer(modifier = Modifier.height(10.dp))
                          val coverReq = rememberThumbnailRequest(heroCover, sizePx = 540)
                          AsyncImage(
                            model = coverReq,
                            contentDescription = col.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(14.dp)),
                          )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                          text = col.title,
                          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                          color = tokens.inkText,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        if (col.description.isNotBlank()) {
                          Spacer(modifier = Modifier.height(3.dp))
                          Text(
                            text = col.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.mutedText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                          )
                        }
                        if (mergedAlbums.isNotEmpty()) {
                          Spacer(modifier = Modifier.height(8.dp))
                          FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                          ) {
                            mergedAlbums.forEach { alb ->
                              Surface(
                                shape = RoundedCornerShape(50),
                                color = tokens.secondarySurface,
                                border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.6f)),
                              ) {
                                Text(
                                  text = alb.title,
                                  style = MaterialTheme.typography.labelSmall,
                                  color = tokens.inkText,
                                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          KenanganSubPage.ALBUM -> {
            Column(modifier = Modifier.fillMaxSize()) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Koleksi Album", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                OutlinedButton(
                  onClick = { showAlbumDialog = true },
                  shape = RoundedCornerShape(50),
                  border = BorderStroke(1.dp, tokens.primaryTerracotta.copy(alpha = 0.6f)),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                  modifier = Modifier.testTag("create_album_button"),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Album Baru", color = tokens.primaryTerracotta)
                }
              }
              Spacer(modifier = Modifier.height(8.dp))

              if (filteredAlbums.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                  EvoriaCard {
                    Icon(Icons.Filled.Collections, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(34.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Belum ada album tersimpan", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                    Text(
                      "Kelompokkan foto perjalanan, hari spesial, atau momen berdua ke dalam album tersendiri.",
                      style = MaterialTheme.typography.bodySmall,
                      color = tokens.mutedText,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                      onClick = { showAlbumDialog = true },
                      shape = RoundedCornerShape(50),
                      colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
                    ) {
                      Text("Buat Album Pertama")
                    }
                  }
                }
              } else {
                LazyVerticalGrid(
                  columns = GridCells.Fixed(2),
                  modifier = Modifier.weight(1f).testTag("album_page_grid"),
                  contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 36.dp),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  items(
                    items = filteredAlbums,
                    key = { it.id },
                    contentType = { "album_grid_card" },
                  ) { album ->
                    val count = albumCounts[album.id] ?: 0
                    Surface(
                      shape = RoundedCornerShape(20.dp),
                      color = tokens.elevatedCard,
                      border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.55f)),
                      modifier =
                        Modifier
                          .fillMaxWidth()
                          .clip(RoundedCornerShape(20.dp))
                          .clickable { onOpenAlbum(album.id) }
                          .testTag("album_card_${album.id}"),
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                          modifier =
                            Modifier
                              .fillMaxWidth()
                              .aspectRatio(1.15f)
                              .clip(RoundedCornerShape(14.dp))
                              .background(tokens.accentPeach.copy(alpha = 0.24f)),
                          contentAlignment = Alignment.Center,
                        ) {
                          if (album.coverUri != null) {
                            val coverReq = rememberThumbnailRequest(album.coverUri, sizePx = 320)
                            AsyncImage(
                              model = coverReq,
                              contentDescription = album.title,
                              contentScale = ContentScale.Crop,
                              modifier = Modifier.fillMaxSize(),
                            )
                          } else {
                            Icon(Icons.Filled.Collections, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(34.dp))
                          }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(album.title, style = MaterialTheme.typography.titleSmall, color = tokens.inkText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                          text = "$count item • ${if (album.visibility == "shared") "Bersama" else "Pribadi"}",
                          style = MaterialTheme.typography.labelSmall,
                          color = tokens.mutedText,
                          maxLines = 1,
                        )
                        if (!album.soundtrackTitle.isNullOrBlank()) {
                          Spacer(modifier = Modifier.height(4.dp))
                          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(12.dp))
                            Text(album.soundtrackTitle, style = MaterialTheme.typography.labelSmall, color = tokens.primaryTerracotta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          KenanganSubPage.GALERI -> {
            Column(modifier = Modifier.fillMaxSize()) {
              if (multiSelectMode) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Button(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                      val targets = memories.filter { it.id in selectedIds }
                      MediaStorageHelper.shareMemoriesViaAllApps(context, targets)
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
                    modifier = Modifier.weight(1f).height(40.dp).testTag("kenangan_multiselect_share"),
                  ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan", maxLines = 1)
                  }
                  OutlinedButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = { confirmBatchDelete = true },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, tokens.roseError.copy(alpha = 0.7f)),
                    modifier = Modifier.weight(1f).height(40.dp).testTag("kenangan_multiselect_delete"),
                  ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = tokens.roseError, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus", color = tokens.roseError, maxLines = 1)
                  }
                }
              } else {
                LazyRow(
                  contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  val galFilters = listOf(
                    "all" to "Semua",
                    "photo" to "Foto",
                    "video" to "Video",
                    "favorite" to "Favorit",
                    "shared" to "Bersama",
                  )
                  items(galFilters) { (key, label) ->
                    FilterChip(
                      selected = filterMode == key,
                      onClick = { filterMode = key },
                      label = { Text(label) },
                    )
                  }
                }
              }

              if (galleryMemories.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                  EvoriaCard {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(34.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Galeri masih kosong", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                    Text(
                      "Impor foto atau video dari perangkat melalui tombol + di kanan bawah.",
                      style = MaterialTheme.typography.bodySmall,
                      color = tokens.mutedText,
                    )
                  }
                }
              } else {
                val groupedSections = remember(galleryMemories) { groupMemoriesByGalleryDay(galleryMemories) }
                val flatGalleryIds = remember(groupedSections) { groupedSections.flatMap { sec -> sec.items.map { it.id } } }
                val galeriGridState = rememberLazyGridState()
                var dragAnchorId by remember { mutableStateOf<String?>(null) }
                var dragBaseSelection by remember { mutableStateOf<Set<String>>(emptySet()) }

                LazyVerticalGrid(
                  state = galeriGridState,
                  columns = GridCells.Fixed(4),
                  modifier =
                    Modifier
                      .weight(1f)
                      .photoGridDragSelection(
                        gridState = galeriGridState,
                        orderedIds = flatGalleryIds,
                        multiSelectMode = multiSelectMode,
                        onStartDragSelection = { anchorId ->
                          multiSelectMode = true
                          dragAnchorId = anchorId
                          dragBaseSelection = selectedIds
                          selectedIds = dragBaseSelection + anchorId
                        },
                        onUpdateDragSelection = { hoveredId ->
                          val anchor = dragAnchorId ?: return@photoGridDragSelection
                          selectedIds = computeRangeSelection(flatGalleryIds, anchor, hoveredId, dragBaseSelection)
                        },
                        onEndDragSelection = {
                          dragAnchorId = null
                        },
                      )
                      .testTag("galeri_grid"),
                  contentPadding = PaddingValues(top = 2.dp, bottom = 36.dp),
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
                        onLongClick = null,
                        onToggleFavorite = { onToggleFavorite(mem) },
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = fabMenuExpanded,
      enter = fadeIn(tween(180)),
      exit = fadeOut(tween(150)),
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

    if (!(multiSelectMode && activeSubPage == KenanganSubPage.GALERI)) {
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 104.dp),
      ) {
        AnimatedVisibility(
          visible = fabMenuExpanded,
          enter =
            fadeIn(tween(180)) +
              scaleIn(
                initialScale = 0.72f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
              ),
          exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.78f),
        ) {
          Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            KenanganSpeedDialOption(
              label = "Gabung Album Jadi Kenangan",
              icon = Icons.Filled.AutoStories,
              testTag = "fab_action_create_kenangan",
              onClick = {
                fabMenuExpanded = false
                showCreateKenanganDialog = true
              },
            )
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
              label = "Impor Foto & Video",
              icon = Icons.Filled.PhotoLibrary,
              testTag = "fab_action_import_gallery",
              onClick = {
                fabMenuExpanded = false
                galleryDirectPicker.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
              },
            )
            KenanganSpeedDialOption(
              label = "Tambah Foto / Cerita",
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

        val wheelRotation by animateFloatAsState(
          targetValue = if (fabMenuExpanded) 315f else 0f,
          animationSpec = spring(dampingRatio = 0.62f, stiffness = 240f),
          label = "kenangan_fab_rotate",
        )
        val wheelScale by animateFloatAsState(
          targetValue = if (fabMenuExpanded) 1.06f else 1f,
          animationSpec = spring(dampingRatio = 0.68f, stiffness = 280f),
          label = "kenangan_fab_scale",
        )
        FloatingActionButton(
          onClick = { fabMenuExpanded = !fabMenuExpanded },
          containerColor = tokens.primaryTerracotta,
          contentColor = Color.White,
          shape = CircleShape,
          modifier =
            Modifier
              .graphicsLayer {
                scaleX = wheelScale
                scaleY = wheelScale
              }
              .testTag("kenangan_fab_add"),
        ) {
          Box(
            modifier =
              Modifier
                .size(48.dp)
                .graphicsLayer { rotationZ = wheelRotation },
            contentAlignment = Alignment.Center,
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val r = size.minDimension * 0.41f
              val inner = size.minDimension * 0.25f
              val c = Offset(size.width / 2f, size.height / 2f)
              val spokeColor = Color.White.copy(alpha = 0.26f)
              for (i in 0 until 8) {
                val rad = Math.toRadians(i * 45.0)
                val sx = c.x + (inner * cos(rad)).toFloat()
                val sy = c.y + (inner * sin(rad)).toFloat()
                val ex = c.x + (r * cos(rad)).toFloat()
                val ey = c.y + (r * sin(rad)).toFloat()
                drawLine(
                  color = spokeColor,
                  start = Offset(sx, sy),
                  end = Offset(ex, ey),
                  strokeWidth = 1.6.dp.toPx(),
                  cap = StrokeCap.Round,
                )
              }
            }
            Icon(
              Icons.Filled.Add,
              contentDescription = if (fabMenuExpanded) "Tutup menu tambah" else "Menu tambah kenangan",
            )
          }
        }
      }
    }

  }

  if (confirmBatchDelete && selectedIds.isNotEmpty()) {
    val deleteTargets = memories.filter { it.id in selectedIds }
    AlertDialog(
      onDismissRequest = { confirmBatchDelete = false },
      title = { Text("Hapus ${deleteTargets.size} Item Terpilih?") },
      text = {
        Text("Foto, video, atau cerita yang dipilih akan dihapus permanen dari perangkat ini.")
      },
      confirmButton = {
        Button(
          onClick = {
            confirmBatchDelete = false
            onDeleteMemories(deleteTargets)
            selectedIds = emptySet()
            multiSelectMode = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = tokens.roseError),
          modifier = Modifier.testTag("kenangan_confirm_batch_delete"),
        ) {
          Text("Hapus")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmBatchDelete = false }) { Text("Batal") }
      },
    )
  }

  kenanganPendingDelete?.let { col ->
    AlertDialog(
      onDismissRequest = { kenanganPendingDelete = null },
      title = { Text("Hapus Kompilasi Kenangan?") },
      text = { Text("Kompilasi \"${col.title}\" akan dihapus. Album dan foto asli di dalamnya tetap aman.") },
      confirmButton = {
        Button(
          onClick = {
            onDeleteKenangan(col.id)
            kenanganPendingDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = tokens.roseError),
        ) { Text("Hapus") }
      },
      dismissButton = {
        TextButton(onClick = { kenanganPendingDelete = null }) { Text("Batal") }
      },
    )
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

  if (showCreateKenanganDialog) {
    CreateKenanganDialog(
      albums = albums,
      albumCounts = albumCounts,
      onDismiss = { showCreateKenanganDialog = false },
      onCreateAlbumFirst = {
        showCreateKenanganDialog = false
        showAlbumDialog = true
      },
      onSave = { title, desc, selectedAlbumIds, visibility ->
        onCreateKenangan(title, desc, selectedAlbumIds, visibility)
        showCreateKenanganDialog = false
      },
    )
  }
}

@Composable
private fun CreateKenanganDialog(
  albums: List<LocalAlbumEntity>,
  albumCounts: Map<String, Int>,
  onDismiss: () -> Unit,
  onCreateAlbumFirst: () -> Unit,
  onSave: (title: String, desc: String, albumIds: List<String>, visibility: String) -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var visibility by remember { mutableStateOf("private") }
  var chosenAlbumIds by remember { mutableStateOf<Set<String>>(emptySet()) }

  val requiresTwoAlbums = albums.size >= 2
  val hasEnoughAlbumsSelected = if (requiresTwoAlbums) chosenAlbumIds.size >= 2 else false

  EvoriaSymmetricDialogShell(
    title = "Buat Kenangan dari Album",
    subtitle = "Gabungkan minimal 2 album pilihan menjadi satu cerita kenangan",
    icon = Icons.Filled.AutoStories,
    onDismiss = onDismiss,
    confirmLabel = if (albums.size < 2) "Buat Album Baru" else "Gabungkan",
    confirmEnabled = albums.size < 2 || hasEnoughAlbumsSelected,
    confirmTestTag = "confirm_create_kenangan_button",
    onConfirm = {
      if (albums.size < 2) {
        onCreateAlbumFirst()
      } else {
        onSave(title, description, chosenAlbumIds.toList(), visibility)
      }
    },
  ) {
    if (albums.size < 2) {
      Text(
        if (albums.isEmpty()) {
          "Kenangan hanya dapat dibuat dari gabungan beberapa album. Buat minimal 2 album terlebih dahulu."
        } else {
          "Kamu baru memiliki 1 album (\"${albums.first().title}\"). Buat 1 album lagi agar bisa digabungkan menjadi Kenangan."
        },
        style = MaterialTheme.typography.bodySmall,
        color = tokens.mutedText,
      )
    } else {
      EvoriaFormTextField(
        value = title,
        onValueChange = { title = it },
        label = "Judul Kenangan",
        placeholder = "Misal: Tahun Pertama Kita",
        singleLine = true,
        modifier = Modifier.testTag("create_kenangan_title_input"),
      )
      EvoriaFormTextField(
        value = description,
        onValueChange = { description = it },
        label = "Catatan Singkat (Opsional)",
        singleLine = false,
        minLines = 2,
        maxLines = 3,
      )
      Text("Privasi Kompilasi", style = MaterialTheme.typography.labelLarge, color = tokens.primaryTerracotta)
      EvoriaSymmetricPrivacyRow(visibility = visibility, onVisibilityChange = { visibility = it })
      Text(
        "Pilih Minimal 2 Album untuk Digabung (${chosenAlbumIds.size} terpilih):",
        style = MaterialTheme.typography.labelLarge,
        color = tokens.primaryTerracotta,
      )
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        albums.forEach { alb ->
          val checked = alb.id in chosenAlbumIds
          val itemCount = albumCounts[alb.id] ?: 0
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (checked) tokens.primaryTerracotta.copy(alpha = 0.14f) else tokens.secondarySurface.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, if (checked) tokens.primaryTerracotta else tokens.outlineSoft.copy(alpha = 0.6f)),
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                  chosenAlbumIds = if (checked) chosenAlbumIds - alb.id else chosenAlbumIds + alb.id
                }
                .testTag("kenangan_album_check_${alb.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Checkbox(
                checked = checked,
                onCheckedChange = {
                  chosenAlbumIds = if (checked) chosenAlbumIds - alb.id else chosenAlbumIds + alb.id
                },
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(alb.title, style = MaterialTheme.typography.titleSmall, color = tokens.inkText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$itemCount item", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KenanganDetailScreen(
  collection: KenanganCollectionEntity,
  albums: List<LocalAlbumEntity>,
  memories: List<LocalMemoryEntity>,
  onBack: () -> Unit,
  onOpenAlbum: (String) -> Unit,
  onOpenMemory: (String) -> Unit,
  onDeleteKenangan: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val tokens = LocalEvoriaTokens.current
  val includedIds = remember(collection.albumIdsCsv) { collection.albumIdList().toSet() }
  val includedAlbums = remember(albums, includedIds) { albums.filter { it.id in includedIds } }
  val mergedMemories =
    remember(memories, includedIds) {
      memories.filter { it.albumId in includedIds }.sortedByDescending { it.memoryDateMillis }
    }

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(
        title = collection.title,
        subtitle = "${includedAlbums.size} album • ${mergedMemories.size} kenangan",
        onBack = onBack,
        trailingAction = {
          IconButton(onClick = onDeleteKenangan) {
            Icon(Icons.Filled.Delete, contentDescription = "Hapus kompilasi", tint = tokens.roseError)
          }
        },
      )

      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.weight(1f).testTag("kenangan_detail_grid"),
        contentPadding = PaddingValues(top = 4.dp, bottom = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "kenangan_detail_header") {
          EvoriaCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (collection.description.isNotBlank()) {
              Text(collection.description, style = MaterialTheme.typography.bodyMedium, color = tokens.inkText)
              Spacer(modifier = Modifier.height(8.dp))
            }
            Text("Album Tergabung:", style = MaterialTheme.typography.labelMedium, color = tokens.primaryTerracotta)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              includedAlbums.forEach { alb ->
                FilterChip(
                  selected = true,
                  onClick = { onOpenAlbum(alb.id) },
                  leadingIcon = { Icon(Icons.Filled.Collections, contentDescription = null, modifier = Modifier.size(14.dp)) },
                  label = { Text(alb.title) },
                )
              }
            }
          }
        }
        items(
          items = mergedMemories,
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
  val tokens = LocalEvoriaTokens.current
  val context = LocalContext.current
  var showEdit by remember { mutableStateOf(false) }
  var multiSelectMode by remember { mutableStateOf(false) }
  var selectedIds by remember { mutableStateOf(setOf<String>()) }
  var dragAnchorId by remember { mutableStateOf<String?>(null) }
  var dragBaseSelection by remember { mutableStateOf<Set<String>>(emptySet()) }

  BackHandler {
    if (multiSelectMode) {
      multiSelectMode = false
      selectedIds = emptySet()
    } else {
      onBack()
    }
  }

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(
        title = album.title,
        subtitle = "${memories.size} item • ${if (album.visibility == "shared") "Bersama" else "Pribadi"}",
        onBack = {
          if (multiSelectMode) {
            multiSelectMode = false
            selectedIds = emptySet()
          } else {
            onBack()
          }
        },
        trailingAction = {
          Row {
            IconButton(onClick = {
              multiSelectMode = !multiSelectMode
              if (!multiSelectMode) selectedIds = emptySet()
            }) {
              Icon(
                Icons.Filled.Checklist,
                contentDescription = "Pilih banyak",
                tint = if (multiSelectMode) tokens.primaryTerracotta else tokens.inkText,
              )
            }
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
      val flatAlbumIds = remember(sortedAlbumMemories) { sortedAlbumMemories.map { it.id } }
      val albumGridState = rememberLazyGridState()
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        LazyVerticalGrid(
          state = albumGridState,
          columns = GridCells.Adaptive(minSize = 104.dp),
          modifier =
            Modifier
              .fillMaxSize()
              .photoGridDragSelection(
                gridState = albumGridState,
                orderedIds = flatAlbumIds,
                multiSelectMode = multiSelectMode,
                onStartDragSelection = { anchorId ->
                  multiSelectMode = true
                  dragAnchorId = anchorId
                  dragBaseSelection = selectedIds
                  selectedIds = dragBaseSelection + anchorId
                },
                onUpdateDragSelection = { hoveredId ->
                  val anchor = dragAnchorId ?: return@photoGridDragSelection
                  selectedIds = computeRangeSelection(flatAlbumIds, anchor, hoveredId, dragBaseSelection)
                },
                onEndDragSelection = { dragAnchorId = null },
              )
              .testTag("album_detail_grid"),
          contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          if (album.description.isNotBlank() || !album.soundtrackTitle.isNullOrBlank()) {
            item(
              key = "album_hdr_info",
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
            val selected = mem.id in selectedIds
            GalleryMediaTile(
              memory = mem,
              isSelected = selected,
              multiSelectMode = multiSelectMode,
              onClick = {
                if (multiSelectMode) {
                  selectedIds = if (selected) selectedIds - mem.id else selectedIds + mem.id
                } else {
                  onOpenMemory(mem.id)
                }
              },
              onLongClick = null,
            )
          }
        }

        androidx.compose.animation.AnimatedVisibility(
          visible = multiSelectMode,
          enter = fadeIn(tween(180)),
          exit = fadeOut(tween(140)),
          modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
          Surface(
            color = tokens.elevatedCard,
            border = BorderStroke(1.dp, tokens.primaryTerracotta.copy(alpha = 0.45f)),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("${selectedIds.size} dipilih", style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                  enabled = selectedIds.isNotEmpty(),
                  onClick = {
                    MediaStorageHelper.shareMemoriesViaAllApps(context, memories.filter { it.id in selectedIds })
                  },
                ) { Text("Bagikan", color = tokens.primaryTerracotta) }
                TextButton(onClick = {
                  selectedIds = emptySet()
                  multiSelectMode = false
                }) { Text("Selesai") }
              }
            }
          }
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
fun GooglePhotosActionBottomBar(
  isFavorite: Boolean,
  onToggleFavorite: () -> Unit,
  onEdit: () -> Unit,
  onShowRincian: () -> Unit,
  onShare: () -> Unit,
  onDelete: () -> Unit,
  darkOverlayMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalEvoriaTokens.current
  val iconTint = if (darkOverlayMode || tokens.isDark) Color.White else tokens.inkText
  val pillSurface = if (darkOverlayMode || tokens.isDark) Color(0xFF333136) else tokens.secondarySurface
  val rincianCircle = if (darkOverlayMode || tokens.isDark) Color(0xFF423F46) else tokens.primaryTerracotta.copy(alpha = 0.16f)

  Column(
    modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Floating "Rincian" tooltip pill directly above the circular (i) button
    Surface(
      shape = RoundedCornerShape(50),
      color = pillSurface,
      shadowElevation = 4.dp,
      modifier = Modifier.clickable(onClick = onShowRincian),
    ) {
      Text(
        text = "Rincian",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        color = iconTint,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onToggleFavorite, modifier = Modifier.minimumInteractiveComponentSize().testTag("memory_bar_favorite")) {
        Icon(
          imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
          contentDescription = "Favorit",
          tint = if (isFavorite) Color(0xFFFF6B6B) else iconTint,
        )
      }
      IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize().testTag("memory_bar_edit")) {
        Icon(Icons.Filled.Edit, contentDescription = "Ubah", tint = iconTint)
      }
      Surface(
        shape = CircleShape,
        color = rincianCircle,
        modifier =
          Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onShowRincian)
            .testTag("memory_rincian_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = "Rincian foto & video",
            tint = iconTint,
            modifier = Modifier.size(24.dp),
          )
        }
      }
      IconButton(onClick = onShare, modifier = Modifier.minimumInteractiveComponentSize().testTag("memory_bar_share")) {
        Icon(Icons.Filled.Share, contentDescription = "Bagikan", tint = iconTint)
      }
      IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize().testTag("memory_bar_delete")) {
        Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = iconTint)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryRincianBottomSheet(
  memory: LocalMemoryEntity,
  albumTitle: String?,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val tokens = LocalEvoriaTokens.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val detailedInfo by produceState<MediaStorageHelper.MediaDetailedInfo?>(
    initialValue = null,
    key1 = memory.id,
    key2 = memory.mediaUri,
    key3 = memory.memoryDateMillis,
  ) {
    value = withContext(Dispatchers.IO) { MediaStorageHelper.inspectMediaDetails(context, memory) }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = tokens.elevatedCard,
    modifier = Modifier.testTag("memory_rincian_sheet"),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 22.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Rincian", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = tokens.inkText)
      val info = detailedInfo
      if (info == null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = tokens.primaryTerracotta)
          Text("Membaca rincian berkas...", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
        }
      } else {
        RincianDetailRow(
          icon = Icons.Filled.CalendarToday,
          title = info.fullDateFormatted,
          subtitle = "Hari: ${info.dayOfWeek} • Tanggal: ${info.dayOfMonth} • Bulan: ${info.monthName} • Tahun: ${info.year}",
        )
        RincianDetailRow(
          icon = Icons.Filled.Schedule,
          title = "Waktu: ${info.timeFormatted}",
          subtitle = "Dicatat pada zona waktu perangkat",
        )
        RincianDetailRow(
          icon = Icons.Filled.PhotoSizeSelectLarge,
          title = "${info.mediaTypeLabel} • ${info.fileName}",
          subtitle =
            buildString {
              append("Ukuran: ${info.sizeFormatted}")
              if (info.resolutionLabel != "Tidak tersedia") append(" • ${info.resolutionLabel}")
              if (!info.durationLabel.isNullOrBlank()) append(" • Durasi: ${info.durationLabel}")
            },
        )
        RincianDetailRow(
          icon = Icons.Filled.Folder,
          title = "Lokasi Penyimpanan",
          subtitle = info.storageLocationLabel,
        )
        if (!albumTitle.isNullOrBlank()) {
          RincianDetailRow(
            icon = Icons.Filled.Collections,
            title = "Album: $albumTitle",
            subtitle = if (memory.visibility == "shared") "Dibagikan ke pasangan" else "Penyimpanan pribadi",
          )
        }
        if (memory.locationName.isNotBlank()) {
          RincianDetailRow(
            icon = Icons.Filled.LocationOn,
            title = "Lokasi Momen",
            subtitle = memory.locationName,
          )
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun RincianDetailRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
) {
  val tokens = LocalEvoriaTokens.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Surface(
      shape = CircleShape,
      color = tokens.primaryTerracotta.copy(alpha = 0.14f),
      modifier = Modifier.size(36.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
      }
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = tokens.inkText)
      Spacer(modifier = Modifier.height(2.dp))
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
    }
  }
}

@Composable
fun MemoryDetailScreen(
  memory: LocalMemoryEntity,
  albumTitle: String? = null,
  onBack: () -> Unit,
  onToggleFavorite: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onSendEvoriaShare: () -> Unit,
  onOpenFullscreenPhoto: () -> Unit,
  onOpenFullscreenVideo: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val tokens = LocalEvoriaTokens.current
  var confirmDelete by remember { mutableStateOf(false) }
  var showRincianSheet by remember { mutableStateOf(false) }

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(
        title = memory.title,
        subtitle = formatIndoDate(memory.memoryDateMillis),
        onBack = onBack,
        trailingAction = {
          IconButton(onClick = { showRincianSheet = true }) {
            Icon(Icons.Filled.Info, contentDescription = "Rincian", tint = tokens.inkText)
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
            val detailHeroReq = rememberThumbnailRequest(memory.mediaUri, sizePx = 960)
            AsyncImage(
              model = detailHeroReq,
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

      GooglePhotosActionBottomBar(
        isFavorite = memory.isFavorite,
        onToggleFavorite = onToggleFavorite,
        onEdit = onEdit,
        onShowRincian = { showRincianSheet = true },
        onShare = { MediaStorageHelper.shareMemoriesViaAllApps(context, listOf(memory)) },
        onDelete = { confirmDelete = true },
      )
    }
  }

  if (showRincianSheet) {
    MemoryRincianBottomSheet(
      memory = memory,
      albumTitle = albumTitle,
      onDismiss = { showRincianSheet = false },
    )
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
  aiDraftState: AiMemoryDraftUiState = AiMemoryDraftUiState.Idle,
  aiConsentGranted: Boolean = false,
  onGenerateAiDraft: (photoUri: Uri?, seedTitle: String, location: String) -> Unit = { _, _, _ -> },
  onClearAiDraft: () -> Unit = {},
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

  LaunchedEffect(aiDraftState) {
    if (aiDraftState is AiMemoryDraftUiState.Success) {
      val draft = aiDraftState.draft
      if (title.isBlank()) title = draft.title
      if (caption.isBlank()) caption = draft.caption
      if (story.isBlank()) story = draft.story
      if (tagsCsv.isBlank()) tagsCsv = draft.tagsCsv
    }
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
                items(pickedUris, key = { it.toString() }) { uri ->
                  val thumbReq = rememberThumbnailRequest(uri, sizePx = 180)
                  AsyncImage(
                    model = thumbReq,
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
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Detail & Cerita Momen", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
            TextButton(
              onClick = {
                val targetUri = pickedUris.firstOrNull() ?: existing?.mediaUri?.let { Uri.parse(it) }
                onGenerateAiDraft(targetUri, title, location)
              },
              enabled = aiDraftState !is AiMemoryDraftUiState.Loading,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("memory_ai_draft_button"),
            ) {
              if (aiDraftState is AiMemoryDraftUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = tokens.primaryTerracotta)
              } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.primaryTerracotta)
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (aiDraftState is AiMemoryDraftUiState.Loading) "Merangkai..." else "Rangkai AI",
                color = tokens.primaryTerracotta,
              )
            }
          }
          if (!aiConsentGranted) {
            Text(
              text = "Aktifkan izin Gemini AI di tab Jelajah bila ingin membuat judul & cerita otomatis dari foto.",
              style = MaterialTheme.typography.labelSmall,
              color = tokens.mutedText,
            )
          } else if (aiDraftState is AiMemoryDraftUiState.Success) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Draf Gemini AI siap diterapkan ke kolom kosong.",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.sageSuccess,
              )
              TextButton(onClick = {
                val draft = aiDraftState.draft
                title = draft.title
                caption = draft.caption
                story = draft.story
                tagsCsv = draft.tagsCsv
                onClearAiDraft()
              }) {
                Text("Timpa Semua", color = tokens.primaryTerracotta)
              }
            }
          } else if (aiDraftState is AiMemoryDraftUiState.Error) {
            Text(
              text = aiDraftState.message,
              style = MaterialTheme.typography.labelSmall,
              color = tokens.roseError,
            )
          }
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
fun FullscreenPhotoViewer(
  mediaUri: String,
  title: String,
  memory: LocalMemoryEntity? = null,
  albumTitle: String? = null,
  onToggleFavorite: (() -> Unit)? = null,
  onEdit: (() -> Unit)? = null,
  onDelete: (() -> Unit)? = null,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val density = LocalDensity.current
  val tokens = LocalEvoriaTokens.current
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var dismissDragY by remember { mutableFloatStateOf(0f) }
  var showRincianSheet by remember { mutableStateOf(false) }
  var showMenu by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }

  val dismissThresholdPx = with(density) { 140.dp.toPx() }
  val animatedDismissY by animateFloatAsState(
    targetValue = dismissDragY,
    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
    label = "viewer_dismiss_y",
  )
  val bgAlpha = (1f - (abs(animatedDismissY) / (dismissThresholdPx * 2.2f))).coerceIn(0.35f, 1f)

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = bgAlpha))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .pointerInput(Unit) {
          detectTapGestures(
            onDoubleTap = {
              if (scale > 1.05f) {
                scale = 1f
                offset = Offset.Zero
              } else {
                scale = 2.2f
              }
            }
          )
        }
        .pointerInput(scale) {
          if (scale <= 1.02f) {
            detectVerticalDragGestures(
              onVerticalDrag = { _, dragAmount ->
                dismissDragY = (dismissDragY + dragAmount).coerceAtLeast(-dismissThresholdPx * 1.4f)
              },
              onDragEnd = {
                if (abs(dismissDragY) >= dismissThresholdPx) {
                  onBack()
                } else {
                  dismissDragY = 0f
                }
              },
              onDragCancel = { dismissDragY = 0f },
            )
          } else {
            detectTransformGestures { _, pan, zoom, _ ->
              scale = (scale * zoom).coerceIn(1f, 4f)
              offset = if (scale > 1f) offset + pan else Offset.Zero
            }
          }
        }
  ) {
    val fullscreenReq = rememberThumbnailRequest(mediaUri, sizePx = 2048)
    AsyncImage(
      model = fullscreenReq,
      contentDescription = title,
      modifier =
        Modifier
          .fillMaxSize()
          .graphicsLayer {
            val dragScale = (1f - (abs(animatedDismissY) / (dismissThresholdPx * 4f))).coerceIn(0.84f, 1f)
            scaleX = scale * dragScale
            scaleY = scale * dragScale
            translationX = offset.x
            translationY = if (scale > 1.02f) offset.y else animatedDismissY
          },
      contentScale = ContentScale.Fit,
    )
    Row(
      modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("fullscreen_photo_back"),
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tutup penampil foto", tint = Color.White)
      }
      if (memory != null) {
        Box {
          IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("fullscreen_photo_more"),
          ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Opsi foto", tint = Color.White)
          }
          DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
          ) {
            DropdownMenuItem(
              leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
              text = { Text("Rincian") },
              onClick = {
                showMenu = false
                showRincianSheet = true
              },
            )
            DropdownMenuItem(
              leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
              text = { Text("Bagikan via All") },
              onClick = {
                showMenu = false
                MediaStorageHelper.shareMemoriesViaAllApps(context, listOf(memory))
              },
            )
            DropdownMenuItem(
              leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
              text = { Text("Ubah Detail") },
              onClick = {
                showMenu = false
                onEdit?.invoke()
              },
            )
          }
        }
      }
    }

    if (memory != null) {
      GooglePhotosActionBottomBar(
        isFavorite = memory.isFavorite,
        onToggleFavorite = { onToggleFavorite?.invoke() },
        onEdit = { onEdit?.invoke() },
        onShowRincian = { showRincianSheet = true },
        onShare = { MediaStorageHelper.shareMemoriesViaAllApps(context, listOf(memory)) },
        onDelete = { confirmDelete = true },
        darkOverlayMode = true,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }

  if (showRincianSheet && memory != null) {
    MemoryRincianBottomSheet(
      memory = memory,
      albumTitle = albumTitle,
      onDismiss = { showRincianSheet = false },
    )
  }

  if (confirmDelete && memory != null) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Hapus Foto Ini?") },
      text = { Text("\"${memory.title}\" akan dihapus dari ruang kenangan perangkat ini.") },
      confirmButton = {
        Button(
          onClick = {
            confirmDelete = false
            onDelete?.invoke()
            onBack()
          },
          colors = ButtonDefaults.buttonColors(containerColor = tokens.roseError),
        ) {
          Text("Hapus")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) { Text("Batal") }
      },
    )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadQueueScreen(
  jobs: List<UploadJobEntity>,
  memories: List<LocalMemoryEntity> = emptyList(),
  albums: List<LocalAlbumEntity> = emptyList(),
  onRetry: (UploadJobEntity) -> Unit,
  onDelete: (String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val tokens = LocalEvoriaTokens.current
  val memoriesById = remember(memories) { memories.associateBy { it.id } }
  val albumsById = remember(albums) { albums.associateBy { it.id } }

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(title = "Antrean Unggahan", subtitle = "Detail antrean, ukuran & pemulihan otomatis", onBack = onBack)
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
            val mem = memoriesById[job.memoryId]
            val targetAlbum = (job.targetAlbumId ?: mem?.albumId)?.let { albumsById[it] }
            val mediaType = mem?.mediaType?.lowercase() ?: "photo"
            val fileSizeLabel by produceState(initialValue = "...", key1 = job.mediaUri) {
              value =
                withContext(Dispatchers.IO) {
                  val path = runCatching { Uri.parse(job.mediaUri).path }.getOrNull()
                  val bytes = if (!path.isNullOrBlank()) File(path).length() else 0L
                  if (bytes > 0L) MediaStorageHelper.formatBytes(bytes) else "Ukuran lokal"
                }
            }
            val statusLabel =
              when (job.status.lowercase()) {
                "completed", "synced" -> "Selesai"
                "processing", "uploading" -> "Memproses"
                "failed" -> "Gagal"
                else -> "Antre"
              }
            val statusColor =
              when (job.status.lowercase()) {
                "completed", "synced" -> tokens.sageSuccess
                "failed" -> tokens.roseError
                else -> tokens.primaryTerracotta
              }

            EvoriaCard(modifier = Modifier.testTag("upload_job_card_${job.id}")) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier
                      .size(58.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(tokens.secondarySurface),
                  contentAlignment = Alignment.Center,
                ) {
                  if (job.mediaUri.isNotBlank()) {
                    val req = rememberThumbnailRequest(job.mediaUri, sizePx = 180)
                    AsyncImage(
                      model = req,
                      contentDescription = mem?.title ?: job.caption,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )
                  } else {
                    Icon(
                      imageVector = if (mediaType == "video") Icons.Filled.Videocam else Icons.Filled.Photo,
                      contentDescription = null,
                      tint = tokens.primaryTerracotta,
                    )
                  }
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = mem?.title?.ifBlank { job.caption } ?: job.caption.ifBlank { "Media ${job.targetVisibility}" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.inkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    UploadDetailPill(label = statusLabel, color = statusColor)
                    UploadDetailPill(
                      label = when (mediaType) {
                        "video" -> "Video"
                        "story" -> "Cerita"
                        else -> "Foto"
                      },
                      color = tokens.primaryTerracotta,
                    )
                    UploadDetailPill(label = fileSizeLabel, color = tokens.mutedText)
                  }
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

              Spacer(modifier = Modifier.height(8.dp))
              HorizontalDivider(color = tokens.outlineSoft.copy(alpha = 0.45f))
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Album: ${targetAlbum?.title ?: "Tanpa Album"} • Privasi: ${if (job.targetVisibility == "shared") "Bersama" else "Pribadi"} • Percobaan: ${job.attemptCount}",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
              )
              Text(
                text = "Dibuat: ${formatIndoDateTime(job.createdAtMillis)} • Diperbarui: ${formatIndoDateTime(job.updatedAtMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.mutedText,
              )
              if (!job.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Kendala: ${job.lastError}", style = MaterialTheme.typography.bodySmall, color = tokens.roseError)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun UploadDetailPill(label: String, color: Color) {
  Surface(
    shape = RoundedCornerShape(50),
    color = color.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
      color = color,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
    )
  }
}
