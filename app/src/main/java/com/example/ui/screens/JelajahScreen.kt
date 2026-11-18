package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalAlbumEntity
import com.example.data.local.LocalMemoryEntity
import com.example.ml.IndexingStats
import com.example.ml.ModelState
import com.example.ui.SearchUiState
import com.example.ui.components.EvoriaCard
import com.example.ui.components.EvoriaPillSearchBar
import com.example.ui.components.EvoriaVisionStatusCard
import com.example.ui.theme.LocalEvoriaTokens
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class QuickExploreCategory(
  val label: String,
  val query: String,
  val icon: ImageVector,
)

@Composable
fun JelajahScreen(
  memories: List<LocalMemoryEntity>,
  albums: List<LocalAlbumEntity>,
  searchHistory: List<String>,
  aiConsentGranted: Boolean = false,
  searchState: SearchUiState,
  onToggleAiConsent: (Boolean) -> Unit = {},
  onRunSearch: (query: String, visibility: String, mediaType: String, albumId: String?) -> Unit,
  onClearHistory: () -> Unit,
  onOpenMemory: (String) -> Unit,
  modelState: ModelState = ModelState.NotDownloaded,
  indexingStats: IndexingStats = IndexingStats(),
  isIndexingPhotos: Boolean = false,
  onDownloadModel: () -> Unit = {},
  onCancelModelDownload: () -> Unit = {},
  onDeleteModel: () -> Unit = {},
  onTriggerIndexing: () -> Unit = {},
  onClearSearchState: () -> Unit = {},
) {
  val tokens = LocalEvoriaTokens.current
  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  val keyboardController = LocalSoftwareKeyboardController.current
  var query by remember { mutableStateOf("") }
  var visFilter by remember { mutableStateOf("all") }
  var typeFilter by remember { mutableStateOf("all") }
  var albumFilter by remember { mutableStateOf<String?>(null) }
  var pendingScrollToResults by remember { mutableStateOf(false) }
  var searchTriggerCount by remember { mutableIntStateOf(0) }
  val resultsStartIndex = if (searchHistory.isNotEmpty()) 7 else 6

  // Debounced live semantic search as user types, plus clean reset when cleared
  LaunchedEffect(query, visFilter, typeFilter, albumFilter) {
    if (query.isBlank() && visFilter == "all" && typeFilter == "all" && albumFilter == null) {
      onClearSearchState()
    } else if (query.isNotBlank()) {
      delay(280L)
      onRunSearch(query, visFilter, typeFilter, albumFilter)
    } else {
      onRunSearch("", visFilter, typeFilter, albumFilter)
    }
  }

  // Automatically scroll to the search results section when user clicks the search button
  LaunchedEffect(searchTriggerCount, searchState, resultsStartIndex) {
    if (!pendingScrollToResults) return@LaunchedEffect
    listState.animateScrollToItem(resultsStartIndex)
    if (searchState is SearchUiState.Success || searchState is SearchUiState.Error) {
      pendingScrollToResults = false
    }
  }

  val categories = remember {
    listOf(
      QuickExploreCategory("Pantai & Senja", "pantai saat matahari terbenam", Icons.Filled.WbTwilight),
      QuickExploreCategory("Alam & Gunung", "pemandangan gunung dan alam", Icons.Filled.Landscape),
      QuickExploreCategory("Makanan & Kopi", "makanan kopi kafe", Icons.Filled.Coffee),
      QuickExploreCategory("Malam", "malam bintang lampu", Icons.Filled.NightsStay),
      QuickExploreCategory("Video", "video", Icons.Filled.Videocam),
      QuickExploreCategory("Favorit", "favorit", Icons.Filled.Favorite),
      QuickExploreCategory("Hari Ini", "hari ini", Icons.Filled.Today),
    )
  }

  val suggestions = remember {
    listOf(
      "kucing tidur",
      "pantai saat matahari terbenam",
      "mobil merah",
      "pemandangan gunung",
      "orang naik sepeda",
      "langit biru",
    )
  }

  val recentPhotos = remember(memories) { memories.sortedByDescending { it.memoryDateMillis }.take(9) }
  val recentRows = remember(recentPhotos) { recentPhotos.chunked(3) }
  val successOutcome = (searchState as? SearchUiState.Success)?.outcome
  val matchedMemories = remember(successOutcome?.matchingMemoryIds, memories) {
    if (successOutcome == null) emptyList()
    else {
      val byId = memories.associateBy { it.id }
      successOutcome.matchingMemoryIds.mapNotNull(byId::get)
    }
  }
  val matchedRows = remember(matchedMemories) { matchedMemories.chunked(3) }
  val scoresByMemoryId = successOutcome?.scoresByMemoryId.orEmpty()

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).testTag("jelajah_lazy_column"),
      contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Jelajah Foto AI", style = MaterialTheme.typography.displayMedium, color = tokens.inkText)
            Surface(
              shape = RoundedCornerShape(50),
              color = tokens.primaryTerracotta.copy(alpha = 0.14f),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(13.dp))
                Text(
                  text = if (modelState is ModelState.Ready) "Vision Offline" else "Lokal",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = tokens.primaryTerracotta,
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            "Cari objek, suasana, atau warna di dalam foto dengan bahasa natural secara privat di perangkat.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.mutedText,
          )
        }
      }

      // EVORIA Theme-Matched Pill Search Bar
      item {
        Column(
          modifier = Modifier.padding(horizontal = 20.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          EvoriaPillSearchBar(
            query = query,
            onQueryChange = { query = it },
            onSearch = {
              keyboardController?.hide()
              pendingScrollToResults = true
              searchTriggerCount++
              onRunSearch(query, visFilter, typeFilter, albumFilter)
              coroutineScope.launch { listState.animateScrollToItem(resultsStartIndex) }
            },
            placeholder = "Coba \"kucing tidur\", \"pantai sunset\", \"mobil merah\"...",
            inputTestTag = "jelajah_search_input",
            submitTestTag = "jelajah_search_submit",
          )
          Text(
            text =
              if (modelState is ModelState.Ready) {
                "EVRIA Vision Search Lite aktif (${indexingStats.indexedCount}/${indexingStats.totalPhotoCount} foto terindeks offline)"
              } else {
                "Mode kata kunci lokal aktif — unduh Model AI di bawah untuk mengenali isi visual foto"
              },
            style = MaterialTheme.typography.labelSmall,
            color = tokens.mutedText,
            modifier = Modifier.padding(horizontal = 8.dp),
          )
        }
      }

      // On-Device Vision Model & Indexing Status Card (100% Privacy-First, Zero Gemini)
      item {
        EvoriaVisionStatusCard(
          modelState = modelState,
          indexingStats = indexingStats,
          isIndexingPhotos = isIndexingPhotos,
          onDownloadModel = onDownloadModel,
          onCancelDownload = onCancelModelDownload,
          onDeleteModel = onDeleteModel,
          onTriggerIndexing = onTriggerIndexing,
          modifier = Modifier.padding(horizontal = 20.dp),
        )
      }

      // Quick Visual Categories
      item {
        Column {
          Text(
            "Jelajah Cepat",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.inkText,
            modifier = Modifier.padding(horizontal = 20.dp),
          )
          Spacer(modifier = Modifier.height(8.dp))
          LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(categories) { cat ->
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = tokens.cardSurface,
                border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.7f)),
                modifier = Modifier.clickable {
                  query = cat.query
                  onRunSearch(cat.query, visFilter, typeFilter, albumFilter)
                },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(cat.icon, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(16.dp))
                  Text(cat.label, style = MaterialTheme.typography.labelMedium, color = tokens.inkText)
                }
              }
            }
          }
        }
      }

      // Filters
      item {
        LazyRow(
          contentPadding = PaddingValues(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf("all" to "Semua Privasi", "private" to "Pribadi", "shared" to "Bersama").forEach { (k, label) ->
            item { FilterChip(selected = visFilter == k, onClick = { visFilter = k }, label = { Text(label) }) }
          }
          listOf("all" to "Semua Media", "photo" to "Foto", "video" to "Video").forEach { (k, label) ->
            item { FilterChip(selected = typeFilter == k, onClick = { typeFilter = k }, label = { Text(label) }) }
          }
          items(albums, key = { it.id }) { alb ->
            FilterChip(
              selected = albumFilter == alb.id,
              onClick = { albumFilter = if (albumFilter == alb.id) null else alb.id },
              label = { Text(alb.title) },
            )
          }
        }
      }

      // Natural language visual suggestions
      item {
        LazyRow(
          contentPadding = PaddingValues(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(suggestions) { sug ->
            Surface(
              shape = RoundedCornerShape(50),
              color = tokens.secondarySurface,
              modifier = Modifier.clickable {
                query = sug
                onRunSearch(sug, visFilter, typeFilter, albumFilter)
              },
            ) {
              Text(
                sug,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.inkText,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              )
            }
          }
        }
      }

      // History
      if (searchHistory.isNotEmpty()) {
        item {
          Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Riwayat Pencarian", style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
              TextButton(onClick = onClearHistory) { Text("Bersihkan", color = tokens.mutedText) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(searchHistory) { h ->
                Surface(
                  shape = RoundedCornerShape(50),
                  color = tokens.secondarySurface,
                  modifier = Modifier.clickable {
                    query = h
                    onRunSearch(h, visFilter, typeFilter, albumFilter)
                  },
                ) {
                  Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = tokens.mutedText, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(h, style = MaterialTheme.typography.labelMedium, color = tokens.inkText)
                  }
                }
              }
            }
          }
        }
      }

      // Results (Google Photos style borderless gallery grid)
      when (searchState) {
        SearchUiState.Idle -> {
          item(contentType = "status_card") {
            EvoriaCard(modifier = Modifier.padding(horizontal = 20.dp).testTag("jelajah_results_section")) {
              Text("Siap Menjelajah ${memories.size} Momen", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "Ketik apa yang terlihat di foto (misalnya kucing tidur, pantai sunset, mobil merah), lokasi, atau ketuk saran di atas.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
              )
            }
          }
          if (recentPhotos.isNotEmpty()) {
            item(contentType = "section_title") {
              Text(
                "Sorotan Galeri Terbaru",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.inkText,
                modifier = Modifier.padding(horizontal = 20.dp),
              )
            }
            items(
              items = recentRows,
              key = { row -> row.joinToString("_") { it.id } },
              contentType = { "gallery_row" },
            ) { rowItems ->
              GalleryGridRow(rowItems = rowItems, onOpenMemory = onOpenMemory)
            }
          }
        }
        SearchUiState.Loading -> {
          item(contentType = "loading") {
            Box(
              modifier = Modifier.fillMaxWidth().padding(28.dp).testTag("jelajah_results_section"),
              contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = tokens.primaryTerracotta)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text =
                    if (modelState is ModelState.Ready) "Mencocokkan makna visual foto di perangkat..."
                    else "Mencari di galeri lokal...",
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.mutedText,
                )
              }
            }
          }
        }
        is SearchUiState.Error -> {
          item(contentType = "error") {
            EvoriaCard(modifier = Modifier.padding(horizontal = 20.dp).testTag("jelajah_results_section")) {
              Text("Pencarian Terkendala", style = MaterialTheme.typography.titleMedium, color = tokens.roseError)
              Text(searchState.message, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
            }
          }
        }
        is SearchUiState.Success -> {
          val outcome = searchState.outcome
          item(contentType = "status_card") {
            EvoriaCard(modifier = Modifier.padding(horizontal = 20.dp).testTag("jelajah_results_section")) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text =
                    if (outcome.usedOnDeviceVision) "Hasil EVRIA Vision Search Lite"
                    else "Hasil Pencarian Pintar Lokal",
                  style = MaterialTheme.typography.titleSmall,
                  color = tokens.primaryTerracotta,
                )
                Text(
                  text = "${matchedMemories.size} hasil",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = tokens.inkText,
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(outcome.explanation, style = MaterialTheme.typography.bodySmall, color = tokens.inkText)
              if (outcome.translatedPrompts.isNotEmpty() && outcome.usedOnDeviceVision) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  items(outcome.translatedPrompts.take(3)) { prompt ->
                    Surface(
                      shape = RoundedCornerShape(50),
                      color = tokens.accentPeach.copy(alpha = 0.25f),
                    ) {
                      Text(
                        text = prompt,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.primaryTerracotta,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      )
                    }
                  }
                }
              }
              if (!outcome.isFallbackNotice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(outcome.isFallbackNotice, style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
              }
            }
          }
          if (matchedMemories.isEmpty()) {
            item(contentType = "empty_card") {
              EvoriaCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Tidak ada foto atau kenangan yang cocok", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                Text(
                  text =
                    if (modelState is ModelState.Ready && indexingStats.pendingCount > 0) {
                      "Masih ada ${indexingStats.pendingCount} foto menunggu diindeks. Ketuk \"Indeks Foto\" atau coba deskripsi lebih umum."
                    } else {
                      "Coba deskripsi objek yang lebih sederhana (misal: kucing, pantai, mobil, makanan) atau ubah filter."
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.mutedText,
                )
              }
            }
          } else {
            items(
              items = matchedRows,
              key = { row -> row.joinToString("_") { it.id } },
              contentType = { "gallery_row" },
            ) { rowItems ->
              GalleryGridRow(
                rowItems = rowItems,
                scoresByMemoryId = scoresByMemoryId,
                onOpenMemory = onOpenMemory,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GalleryGridRow(
  rowItems: List<LocalMemoryEntity>,
  scoresByMemoryId: Map<String, Float> = emptyMap(),
  onOpenMemory: (String) -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    rowItems.forEach { mem ->
      val score = scoresByMemoryId[mem.id]
      Box(modifier = Modifier.weight(1f)) {
        GalleryMediaTile(
          memory = mem,
          onClick = { onOpenMemory(mem.id) },
          modifier = Modifier.fillMaxWidth(),
        )
        if (score != null && score > 0f) {
          val pct = ((score - 0.14f) / 0.22f * 100f).coerceIn(50f, 99f).roundToInt()
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = tokens.cardSurface.copy(alpha = 0.88f),
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
          ) {
            Text(
              text = "$pct%",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = tokens.primaryTerracotta,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
          }
        }
      }
    }
    repeat(3 - rowItems.size) {
      Spacer(modifier = Modifier.weight(1f))
    }
  }
}
