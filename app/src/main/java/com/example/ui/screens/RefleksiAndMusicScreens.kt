package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.JournalEntity
import com.example.data.local.LetterEntity
import com.example.data.local.LocalAlbumEntity
import com.example.data.local.MoodEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.ReflectionAnswerEntity
import com.example.data.local.TimeCapsuleEntity
import com.example.data.local.TrackEntity
import com.example.data.remote.SharedReflectionDto
import com.example.data.remote.SpotifyPlaylistImporter
import com.example.data.remote.YouTubeDataApiClient
import com.example.data.remote.YouTubeTrackMatch
import com.example.media.MusicPlayerState
import com.example.media.YouTubePlayerCommand
import com.example.ui.AiReflectionUiState
import com.example.ui.SpotifyImportUiState
import com.example.ui.YouTubeDirectSearchUiState
import com.example.ui.components.EvoriaActionChip
import com.example.ui.components.EvoriaCard
import com.example.ui.components.EvoriaFormTextField
import com.example.ui.components.EvoriaPillSearchBar
import com.example.ui.components.EvoriaSanctuaryBackdrop
import com.example.ui.components.EvoriaSymmetricDialogShell
import com.example.ui.components.EvoriaSymmetricPrivacyRow
import com.example.ui.components.EvoriaTopBar
import com.example.ui.components.VisibilityBadge
import com.example.ui.components.formatDurationMmSs
import com.example.ui.components.formatIndoDate
import com.example.ui.components.formatIndoDateTime
import com.example.ui.components.rememberThumbnailRequest
import com.example.ui.theme.LocalEvoriaTokens
import java.net.URLEncoder
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

private data class MoodOption(
  val key: String,
  val label: String,
)

private val EvoriaMoodOptions =
  listOf(
    MoodOption("tenang", "Tenang"),
    MoodOption("hangat", "Hangat"),
    MoodOption("rindu", "Rindu"),
    MoodOption("syukur", "Syukur"),
    MoodOption("lelah", "Lelah"),
    MoodOption("bahagia", "Bahagia"),
  )

private data class DailyReflectionPrompt(
  val id: String,
  val text: String,
)

private val DailyPrompts =
  listOf(
    DailyReflectionPrompt("p1", "Momen kecil apa hari ini yang membuatmu teringat padanya?"),
    DailyReflectionPrompt("p2", "Hal sederhana apa yang ingin kamu syukuri tentang perjalanan kalian?"),
    DailyReflectionPrompt("p3", "Jika hari ini bisa diabadikan dalam satu kalimat untuk tahun depan, apa isinya?"),
    DailyReflectionPrompt("p4", "Tempat mana yang ingin kalian kunjungi lagi bersama?"),
  )

@Composable
private fun MoodVectorEmblem(
  moodKey: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier.size(28.dp)) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = size.minDimension * 0.36f
    val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)

    when (moodKey) {
      "tenang" -> {
        // Calm ripples & soft crescent arc
        drawArc(
          color = color,
          startAngle = 200f,
          sweepAngle = 140f,
          useCenter = false,
          topLeft = Offset(cx - r, cy - r * 0.7f),
          size = androidx.compose.ui.geometry.Size(r * 2f, r * 1.4f),
          style = stroke,
        )
        drawLine(color, Offset(cx - r * 0.8f, cy + r * 0.45f), Offset(cx + r * 0.8f, cy + r * 0.45f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - r * 0.5f, cy + r * 0.85f), Offset(cx + r * 0.5f, cy + r * 0.85f), strokeWidth = stroke.width, cap = StrokeCap.Round)
      }
      "hangat" -> {
        // Warm sun & horizon
        drawCircle(color = color, radius = r * 0.55f, center = Offset(cx, cy - r * 0.1f), style = stroke)
        for (i in 0 until 6) {
          val rad = (i * 60.0) * PI / 180.0
          val sx = cx + cos(rad).toFloat() * (r * 0.75f)
          val sy = (cy - r * 0.1f) + sin(rad).toFloat() * (r * 0.75f)
          val ex = cx + cos(rad).toFloat() * (r * 1.05f)
          val ey = (cy - r * 0.1f) + sin(rad).toFloat() * (r * 1.05f)
          drawLine(color, Offset(sx, sy), Offset(ex, ey), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
      }
      "rindu" -> {
        // Crescent & reflective star dot
        drawCircle(color = color, radius = r * 0.82f, center = Offset(cx, cy), style = stroke)
        drawCircle(color = color, radius = r * 0.22f, center = Offset(cx + r * 0.25f, cy - r * 0.2f))
      }
      "syukur" -> {
        // Botanical leaf
        val leaf =
          Path().apply {
            moveTo(cx - r * 0.75f, cy + r * 0.75f)
            quadraticTo(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.85f, cy - r * 0.8f)
            quadraticTo(cx + r * 0.8f, cy + r * 0.8f, cx - r * 0.75f, cy + r * 0.75f)
          }
        drawPath(leaf, color = color, style = stroke)
        drawLine(color, Offset(cx - r * 0.75f, cy + r * 0.75f), Offset(cx + r * 0.3f, cy - r * 0.3f), strokeWidth = stroke.width * 0.8f, cap = StrokeCap.Round)
      }
      "lelah" -> {
        // Soft cloud curve
        val cloud =
          Path().apply {
            moveTo(cx - r * 0.75f, cy + r * 0.35f)
            quadraticTo(cx - r * 0.9f, cy - r * 0.2f, cx - r * 0.3f, cy - r * 0.3f)
            quadraticTo(cx, cy - r * 0.85f, cx + r * 0.45f, cy - r * 0.25f)
            quadraticTo(cx + r * 0.95f, cy - r * 0.15f, cx + r * 0.75f, cy + r * 0.35f)
            close()
          }
        drawPath(cloud, color = color, style = stroke)
      }
      else -> {
        // Bahagia: four-point bloom spark
        val spark =
          Path().apply {
            moveTo(cx, cy - r)
            quadraticTo(cx, cy, cx + r, cy)
            quadraticTo(cx, cy, cx, cy + r)
            quadraticTo(cx, cy, cx - r, cy)
            quadraticTo(cx, cy, cx, cy - r)
          }
        drawPath(spark, color = color, style = stroke)
      }
    }
  }
}

@Composable
fun RefleksiScreen(
  journals: List<JournalEntity>,
  letters: List<LetterEntity>,
  moods: List<MoodEntity>,
  reflectionAnswers: List<ReflectionAnswerEntity>,
  capsules: List<TimeCapsuleEntity>,
  onSaveJournal: (existingId: String?, title: String, body: String, visibility: String, moodTag: String, isDraft: Boolean, attachmentUri: Uri?, existingAttachment: String?) -> Unit,
  onDeleteJournal: (String) -> Unit,
  onSaveLetter: (existingId: String?, title: String, body: String, recipientName: String, isSealed: Boolean, isDraft: Boolean, isShared: Boolean) -> Unit,
  onDeleteLetter: (String) -> Unit,
  onRecordMood: (moodKey: String, note: String) -> Unit,
  onSaveReflectionAnswer: (promptId: String, promptText: String, answer: String) -> Unit,
  onCreateTimeCapsule: (title: String, body: String, unlockAtMillis: Long, visibility: String, attachmentUri: Uri?) -> Unit,
  onReadEnforcedCapsule: suspend (String) -> TimeCapsuleEntity?,
  onOpenMusic: () -> Unit,
  sharedReflections: List<SharedReflectionDto> = emptyList(),
  currentUid: String = "",
  aiConsentGranted: Boolean = false,
  aiReflectionState: AiReflectionUiState = AiReflectionUiState.Idle,
  onGenerateAiReflection: () -> Unit = {},
) {
  val tokens = LocalEvoriaTokens.current
  val scope = rememberCoroutineScope()
  var selectedSection by remember { mutableStateOf("jurnal") }

  val partnerSharedReflections =
    remember(sharedReflections, currentUid) {
      sharedReflections.filter { it.authorUid != currentUid }
    }
  val partnerJournals = remember(partnerSharedReflections) { partnerSharedReflections.filter { it.kind == "journal" } }
  val partnerLetters = remember(partnerSharedReflections) { partnerSharedReflections.filter { it.kind == "letter" } }
  val partnerCapsules = remember(partnerSharedReflections) { partnerSharedReflections.filter { it.kind == "capsule" } }

  var editingJournal by remember { mutableStateOf<JournalEntity?>(null) }
  var showJournalDialog by remember { mutableStateOf(false) }

  var editingLetter by remember { mutableStateOf<LetterEntity?>(null) }
  var showLetterDialog by remember { mutableStateOf(false) }

  var showCapsuleDialog by remember { mutableStateOf(false) }
  var inspectedCapsule by remember { mutableStateOf<TimeCapsuleEntity?>(null) }
  var inspectedPartnerReflection by remember { mutableStateOf<SharedReflectionDto?>(null) }

  // Mood input state
  var selectedMoodKey by remember { mutableStateOf("hangat") }
  var moodNote by remember { mutableStateOf("") }

  // Reflection prompt state
  var promptIndex by remember { mutableStateOf(0) }
  var customAiPrompt by remember { mutableStateOf<String?>(null) }
  val currentPrompt = DailyPrompts[promptIndex % DailyPrompts.size]
  val displayedPromptText = customAiPrompt ?: currentPrompt.text
  val displayedPromptId = if (customAiPrompt != null) "ai_prompt_${customAiPrompt.hashCode()}" else currentPrompt.id
  var promptAnswer by remember { mutableStateOf("") }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("refleksi_lazy_column"),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Refleksi", style = MaterialTheme.typography.displayMedium, color = tokens.inkText)
          Text(
            "Jurnal, surat tak terkirim, suasana hati & kapsul waktu.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.mutedText,
          )
        }
        OutlinedButton(
          onClick = onOpenMusic,
          shape = RoundedCornerShape(50),
          modifier = Modifier.testTag("refleksi_open_music_button"),
        ) {
          Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.primaryTerracotta)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Musik", color = tokens.inkText)
        }
      }
    }

    // Section tabs
    item {
      LazyRow(
        modifier = Modifier.testTag("refleksi_tabs_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        item {
          EvoriaActionChip(
            label = "Jurnal (${journals.size})",
            icon = Icons.Filled.MenuBook,
            selected = selectedSection == "jurnal",
            onClick = { selectedSection = "jurnal" },
            modifier = Modifier.testTag("refleksi_tab_jurnal"),
          )
        }
        item {
          EvoriaActionChip(
            label = "Surat (${letters.size})",
            icon = Icons.Filled.MailOutline,
            selected = selectedSection == "surat",
            onClick = { selectedSection = "surat" },
            modifier = Modifier.testTag("refleksi_tab_surat"),
          )
        }
        item {
          EvoriaActionChip(
            label = "Suasana Hati",
            icon = Icons.Filled.Mood,
            selected = selectedSection == "mood",
            onClick = { selectedSection = "mood" },
            modifier = Modifier.testTag("refleksi_tab_mood"),
          )
        }
        item {
          EvoriaActionChip(
            label = "Pertanyaan",
            icon = Icons.Filled.AutoAwesome,
            selected = selectedSection == "pertanyaan",
            onClick = { selectedSection = "pertanyaan" },
            modifier = Modifier.testTag("refleksi_tab_pertanyaan"),
          )
        }
        item {
          EvoriaActionChip(
            label = "Kapsul Waktu (${capsules.size})",
            icon = Icons.Filled.HourglassBottom,
            selected = selectedSection == "kapsul",
            onClick = { selectedSection = "kapsul" },
            modifier = Modifier.testTag("refleksi_tab_kapsul"),
          )
        }
      }
    }

    when (selectedSection) {
      "jurnal" -> {
        item {
          Button(
            onClick = {
              editingJournal = null
              showJournalDialog = true
            },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("new_journal_button"),
          ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tulis Jurnal Baru")
          }
        }
        if (journals.isEmpty()) {
          item {
            EvoriaCard {
              Text("Belum ada catatan jurnal", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "Setiap tulisan bersifat pribadi kecuali kamu memilih untuk membagikannya ke pasangan.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
              )
            }
          }
        } else {
          items(journals, key = { it.id }) { j ->
            EvoriaCard(onClick = {
              editingJournal = j
              showJournalDialog = true
            }) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(j.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                  Text(
                    "${formatIndoDate(j.updatedAtMillis)} • ${j.moodTag.replaceFirstChar { it.uppercase() }}" +
                      if (j.isDraft) " • Draf" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.mutedText,
                  )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  VisibilityBadge(j.visibility)
                  IconButton(onClick = { onDeleteJournal(j.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus jurnal", tint = tokens.roseError, modifier = Modifier.size(18.dp))
                  }
                }
              }
              if (j.attachmentUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val attachReq = rememberThumbnailRequest(j.attachmentUri, sizePx = 560)
                AsyncImage(
                  model = attachReq,
                  contentDescription = j.title,
                  modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)),
                  contentScale = ContentScale.Crop,
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(j.body, style = MaterialTheme.typography.bodyMedium, color = tokens.inkText, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
          }
        }
        if (partnerJournals.isNotEmpty()) {
          item {
            Text(
              text = "Jurnal Bersama dari Pasangan (${partnerJournals.size})",
              style = MaterialTheme.typography.titleMedium,
              color = tokens.primaryTerracotta,
            )
          }
          items(partnerJournals, key = { "pj_${it.id}" }) { pj ->
            EvoriaCard(elevated = true) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(pj.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                  Text("Ditulis oleh ${pj.authorName}", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
                }
                VisibilityBadge("shared")
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(pj.body, style = MaterialTheme.typography.bodyMedium, color = tokens.inkText)
            }
          }
        }
      }

      "surat" -> {
        item {
          Button(
            onClick = {
              editingLetter = null
              showLetterDialog = true
            },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("new_letter_button"),
          ) {
            Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tulis Surat Tak Terkirim")
          }
        }
        if (letters.isEmpty()) {
          item {
            EvoriaCard {
              Text("Ruang Surat Tak Terkirim", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "Simpan kata-kata jujur yang belum siap diucapkan. Kamu dapat menyimpannya sebagai draf pribadi atau membagikannya saat waktunya tepat.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
              )
            }
          }
        } else {
          items(letters, key = { it.id }) { ltr ->
            EvoriaCard(onClick = {
              editingLetter = ltr
              showLetterDialog = true
            }) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(ltr.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                  Text(
                    "Untuk ${ltr.recipientName} • ${if (ltr.isDraft) "Draf" else if (ltr.isSealed) "Tersegel" else "Terbuka"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.mutedText,
                  )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  VisibilityBadge(if (ltr.isShared) "shared" else "private")
                  IconButton(onClick = { onDeleteLetter(ltr.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus surat", tint = tokens.roseError, modifier = Modifier.size(18.dp))
                  }
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = if (ltr.isSealed && !ltr.isDraft) "Ketuk untuk membuka segel surat ini..." else ltr.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ltr.isSealed && !ltr.isDraft) tokens.primaryTerracotta else tokens.inkText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        if (partnerLetters.isNotEmpty()) {
          item {
            Text(
              text = "Surat dari Pasangan (${partnerLetters.size})",
              style = MaterialTheme.typography.titleMedium,
              color = tokens.primaryTerracotta,
            )
          }
          items(partnerLetters, key = { "pl_${it.id}" }) { pl ->
            EvoriaCard(
              elevated = true,
              onClick = { inspectedPartnerReflection = pl },
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(pl.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                  Text("Dari ${pl.authorName}", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
                }
                VisibilityBadge("shared")
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = if (pl.isSealed) "Surat bersegel — ketuk untuk membacanya" else pl.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (pl.isSealed) tokens.primaryTerracotta else tokens.inkText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      "mood" -> {
        item {
          EvoriaCard(elevated = true) {
            Text("Bagaimana Suasana Hatimu Hari Ini?", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              EvoriaMoodOptions.take(3).forEach { opt ->
                val active = selectedMoodKey == opt.key
                MoodPillItem(opt, active, onClick = { selectedMoodKey = opt.key }, modifier = Modifier.weight(1f))
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              EvoriaMoodOptions.drop(3).forEach { opt ->
                val active = selectedMoodKey == opt.key
                MoodPillItem(opt, active, onClick = { selectedMoodKey = opt.key }, modifier = Modifier.weight(1f))
              }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
              value = moodNote,
              onValueChange = { moodNote = it },
              label = { Text("Catatan Singkat (Opsional)") },
              maxLines = 2,
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = {
                onRecordMood(selectedMoodKey, moodNote)
                moodNote = ""
              },
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_mood_button"),
            ) {
              Text("Catat Suasana Hati")
            }
          }
        }
        item {
          Text("Riwayat Suasana Hati", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
        }
        if (moods.isEmpty()) {
          item {
            EvoriaCard {
              Text("Belum ada riwayat check-in suasana hati.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
            }
          }
        } else {
          items(moods, key = { it.id }) { m ->
            EvoriaCard {
              Row(verticalAlignment = Alignment.CenterVertically) {
                MoodVectorEmblem(m.moodKey, tokens.primaryTerracotta)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    m.moodKey.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.inkText,
                  )
                  if (m.note.isNotBlank()) {
                    Text(m.note, style = MaterialTheme.typography.bodySmall, color = tokens.inkText)
                  }
                  Text(formatIndoDateTime(m.recordedAtMillis), style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
                }
              }
            }
          }
        }
      }

      "pertanyaan" -> {
        item {
          EvoriaCard(elevated = true) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Pertanyaan Refleksi Harian", style = MaterialTheme.typography.labelLarge, color = tokens.primaryTerracotta)
              TextButton(onClick = {
                customAiPrompt = null
                promptIndex = (promptIndex + 1) % DailyPrompts.size
              }) {
                Text("Ganti Pertanyaan", color = tokens.mutedText)
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(displayedPromptText, style = MaterialTheme.typography.headlineSmall, color = tokens.inkText)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
              value = promptAnswer,
              onValueChange = { promptAnswer = it },
              label = { Text("Jawabanmu...") },
              minLines = 3,
              modifier = Modifier.fillMaxWidth().testTag("reflection_answer_input"),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = {
                onSaveReflectionAnswer(displayedPromptId, displayedPromptText, promptAnswer)
                promptAnswer = ""
              },
              enabled = promptAnswer.isNotBlank(),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_reflection_answer_button"),
            ) {
              Text("Simpan Jawaban")
            }
          }
        }
        item {
          EvoriaCard(modifier = Modifier.testTag("refleksi_ai_insight_card")) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
                Text("Wawasan & Pertanyaan Gemini AI", style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
              }
              TextButton(
                onClick = onGenerateAiReflection,
                enabled = aiReflectionState !is AiReflectionUiState.Loading,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("refleksi_ai_generate_button"),
              ) {
                if (aiReflectionState is AiReflectionUiState.Loading) {
                  CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = tokens.primaryTerracotta)
                } else {
                  Text("Minta AI", color = tokens.primaryTerracotta)
                }
              }
            }
            when (aiReflectionState) {
              AiReflectionUiState.Idle -> {
                Text(
                  text =
                    if (aiConsentGranted) "Ketuk 'Minta AI' untuk mendapatkan pertanyaan refleksi yang disesuaikan dengan suasana hatimu."
                    else "Aktifkan izin Gemini AI di tab Jelajah untuk membuka wawasan refleksi emosional.",
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.mutedText,
                )
              }
              AiReflectionUiState.Loading -> {
                Text(
                  text = "Gemini AI sedang membaca catatan suasana hatimu...",
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.mutedText,
                )
              }
              is AiReflectionUiState.Success -> {
                Text(
                  text = aiReflectionState.insight.warmInsight,
                  style = MaterialTheme.typography.bodyMedium,
                  color = tokens.inkText,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Saran pertanyaan: ${aiReflectionState.insight.promptQuestion}",
                  style = MaterialTheme.typography.labelMedium,
                  color = tokens.primaryTerracotta,
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                  onClick = { customAiPrompt = aiReflectionState.insight.promptQuestion },
                  shape = RoundedCornerShape(50),
                ) {
                  Text("Jawab Pertanyaan Ini")
                }
              }
              is AiReflectionUiState.Error -> {
                Text(
                  text = aiReflectionState.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.roseError,
                )
              }
            }
          }
        }
        items(reflectionAnswers, key = { it.id }) { ans ->
          EvoriaCard {
            Text(ans.promptText, style = MaterialTheme.typography.labelMedium, color = tokens.primaryTerracotta)
            Spacer(modifier = Modifier.height(4.dp))
            Text(ans.answerText, style = MaterialTheme.typography.bodyMedium, color = tokens.inkText)
            Spacer(modifier = Modifier.height(4.dp))
            Text(formatIndoDate(ans.answeredAtMillis), style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
          }
        }
      }

      "kapsul" -> {
        item {
          Button(
            onClick = { showCapsuleDialog = true },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("new_capsule_button"),
          ) {
            Icon(Icons.Filled.HourglassBottom, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Segel Kapsul Waktu Baru")
          }
        }
        if (capsules.isEmpty()) {
          item {
            EvoriaCard {
              val sealReq = rememberThumbnailRequest(R.drawable.img_time_capsule_seal_1794808539025, sizePx = 640)
              AsyncImage(
                model = sealReq,
                contentDescription = "Segel kapsul waktu",
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text("Belum Ada Kapsul Waktu", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
              Text(
                "Tulis pesan atau lampirkan foto untuk dibuka pada tanggal spesial di masa depan. Isi kapsul terkunci hingga waktu buka tercapai.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
              )
            }
          }
        } else {
          items(capsules, key = { it.id }) { cap ->
            val unlocked = cap.isUnlocked()
            EvoriaCard(
              onClick = {
                scope.launch {
                  inspectedCapsule = onReadEnforcedCapsule(cap.id) ?: cap
                }
              },
              elevated = unlocked,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(
                    imageVector = if (unlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (unlocked) tokens.sageSuccess else tokens.primaryTerracotta,
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(cap.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                    Text(
                      if (unlocked) "Terbuka sejak ${formatIndoDate(cap.unlockAtMillis)}"
                      else "Terkunci hingga ${formatIndoDate(cap.unlockAtMillis)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = tokens.mutedText,
                    )
                  }
                }
                VisibilityBadge(cap.visibility)
              }
            }
          }
        }
        if (partnerCapsules.isNotEmpty()) {
          item {
            Text(
              text = "Kapsul Waktu dari Pasangan (${partnerCapsules.size})",
              style = MaterialTheme.typography.titleMedium,
              color = tokens.primaryTerracotta,
            )
          }
          items(partnerCapsules, key = { "pc_${it.id}" }) { pc ->
            val unlockMs = pc.unlockAtMillis ?: Long.MAX_VALUE
            val unlocked = System.currentTimeMillis() >= unlockMs
            EvoriaCard(
              elevated = unlocked,
              onClick = { inspectedPartnerReflection = pc },
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(
                    imageVector = if (unlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (unlocked) tokens.sageSuccess else tokens.primaryTerracotta,
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(pc.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                    Text(
                      text =
                        if (unlocked) "Dari ${pc.authorName} • Terbuka sejak ${formatIndoDate(unlockMs)}"
                        else "Dari ${pc.authorName} • Terkunci hingga ${formatIndoDate(unlockMs)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = tokens.mutedText,
                    )
                  }
                }
                VisibilityBadge("shared")
              }
            }
          }
        }
      }
    }
  }

  if (showJournalDialog) {
    JournalEditorDialog(
      existing = editingJournal,
      onDismiss = { showJournalDialog = false },
      onSave = { title, body, visibility, moodTag, isDraft, attachmentUri ->
        onSaveJournal(
          editingJournal?.id,
          title,
          body,
          visibility,
          moodTag,
          isDraft,
          attachmentUri,
          editingJournal?.attachmentUri,
        )
        showJournalDialog = false
      },
    )
  }

  if (showLetterDialog) {
    LetterEditorDialog(
      existing = editingLetter,
      onDismiss = { showLetterDialog = false },
      onSave = { title, body, recipient, isSealed, isDraft, isShared ->
        onSaveLetter(editingLetter?.id, title, body, recipient, isSealed, isDraft, isShared)
        showLetterDialog = false
      },
    )
  }

  if (showCapsuleDialog) {
    CreateCapsuleDialog(
      onDismiss = { showCapsuleDialog = false },
      onCreate = { title, body, unlockAtMillis, visibility, attachmentUri ->
        onCreateTimeCapsule(title, body, unlockAtMillis, visibility, attachmentUri)
        showCapsuleDialog = false
      },
    )
  }

  inspectedCapsule?.let { cap ->
    val unlocked = cap.isUnlocked()
    AlertDialog(
      onDismissRequest = { inspectedCapsule = null },
      title = { Text(cap.title) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (!unlocked) {
            val sealReq = rememberThumbnailRequest(R.drawable.img_time_capsule_seal_1794808539025, sizePx = 560)
            AsyncImage(
              model = sealReq,
              contentDescription = "Kapsul masih tersegel",
              modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)),
              contentScale = ContentScale.Crop,
            )
            Text(
              "Kapsul waktu ini masih tersegel rapat hingga ${formatIndoDate(cap.unlockAtMillis)}. Demi menjaga kejutan, isinya belum dapat dibuka sekarang.",
              style = MaterialTheme.typography.bodyMedium,
              color = tokens.mutedText,
            )
          } else {
            if (cap.attachmentUri != null) {
              val capAttachReq = rememberThumbnailRequest(cap.attachmentUri, sizePx = 560)
              AsyncImage(
                model = capAttachReq,
                contentDescription = cap.title,
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
              )
            }
            Text(cap.body.ifBlank { "(Pesan kosong)" }, style = MaterialTheme.typography.bodyLarge, color = tokens.inkText)
            Text("Dibuat ${formatIndoDate(cap.createdAtMillis)}", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { inspectedCapsule = null }) { Text("Tutup") }
      },
    )
  }

  inspectedPartnerReflection?.let { pr ->
    val isCapsule = pr.kind == "capsule"
    val unlockMs = pr.unlockAtMillis ?: Long.MAX_VALUE
    val unlocked = !isCapsule || System.currentTimeMillis() >= unlockMs
    AlertDialog(
      onDismissRequest = { inspectedPartnerReflection = null },
      title = { Text(pr.title) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Dari ${pr.authorName}", style = MaterialTheme.typography.labelSmall, color = tokens.primaryTerracotta)
          if (!unlocked) {
            Text(
              "Kapsul waktu dari pasangan masih tersegel rapat hingga ${formatIndoDate(unlockMs)}.",
              style = MaterialTheme.typography.bodyMedium,
              color = tokens.mutedText,
            )
          } else {
            Text(pr.body, style = MaterialTheme.typography.bodyLarge, color = tokens.inkText)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { inspectedPartnerReflection = null }) { Text("Tutup") }
      },
    )
  }
}

@Composable
private fun MoodPillItem(
  option: MoodOption,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalEvoriaTokens.current
  Surface(
    modifier = modifier.padding(horizontal = 4.dp).clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    color = if (selected) tokens.accentPeach.copy(alpha = 0.36f) else tokens.secondarySurface,
    border = BorderStroke(1.dp, if (selected) tokens.primaryTerracotta else tokens.outlineSoft.copy(alpha = 0.5f)),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      MoodVectorEmblem(
        moodKey = option.key,
        color = if (selected) tokens.primaryTerracotta else tokens.mutedText,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = option.label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) tokens.primaryTerracotta else tokens.inkText,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun JournalEditorDialog(
  existing: JournalEntity?,
  onDismiss: () -> Unit,
  onSave: (title: String, body: String, visibility: String, moodTag: String, isDraft: Boolean, attachmentUri: Uri?) -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  var title by remember { mutableStateOf(existing?.title.orEmpty()) }
  var body by remember { mutableStateOf(existing?.body.orEmpty()) }
  var visibility by remember { mutableStateOf(existing?.visibility ?: "private") }
  var moodTag by remember { mutableStateOf(existing?.moodTag ?: "tenang") }
  var pickedUri by remember { mutableStateOf<Uri?>(null) }

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) pickedUri = uri
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 20.dp)
          .heightIn(max = 760.dp),
      shape = RoundedCornerShape(28.dp),
      color = tokens.background,
      tonalElevation = 6.dp,
      shadowElevation = 14.dp,
      border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.7f)),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Layered gradient editorial header
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .background(
                Brush.horizontalGradient(
                  listOf(
                    tokens.primaryTerracotta.copy(alpha = if (tokens.isDark) 0.28f else 0.16f),
                    tokens.accentPeach.copy(alpha = if (tokens.isDark) 0.18f else 0.30f),
                  )
                )
              )
              .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = tokens.primaryTerracotta,
                shadowElevation = 4.dp,
              ) {
                Icon(
                  Icons.Filled.MenuBook,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.padding(10.dp).size(20.dp),
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = if (existing == null) "Tulis Jurnal Baru" else "Ubah Jurnal",
                  style = MaterialTheme.typography.titleLarge,
                  color = tokens.inkText,
                )
                Text(
                  text = "${body.length} karakter • Simpan draf atau terbitkan",
                  style = MaterialTheme.typography.labelSmall,
                  color = tokens.mutedText,
                )
              }
            }
            IconButton(onClick = onDismiss) {
              Icon(Icons.Filled.Close, contentDescription = "Tutup form jurnal", tint = tokens.inkText)
            }
          }
        }

        // Scrollable layered stationery cards
        Column(
          modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // 1. Stationery Title & Body Card
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = tokens.elevatedCard,
            tonalElevation = 4.dp,
            shadowElevation = 5.dp,
            border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.75f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Judul & Isi Catatan", style = MaterialTheme.typography.labelLarge, color = tokens.primaryTerracotta)
              EvoriaFormTextField(
                value = title,
                onValueChange = { title = it },
                label = "Judul Jurnal",
                placeholder = "Misal: Senja hangat setelah hujan...",
                singleLine = true,
                modifier = Modifier.testTag("journal_title_input"),
              )
              EvoriaFormTextField(
                value = body,
                onValueChange = { body = it },
                label = "Tuangkan ceritamu...",
                singleLine = false,
                minLines = 5,
                maxLines = 9,
                modifier = Modifier.testTag("journal_body_input"),
              )
            }
          }

          // 2. Tactile Mood Picker Card
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = tokens.elevatedCard,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.75f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Nuansa Hati Saat Ini", style = MaterialTheme.typography.labelLarge, color = tokens.primaryTerracotta)
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                EvoriaMoodOptions.take(3).forEach { m ->
                  MoodPillItem(m, moodTag == m.key, { moodTag = m.key }, modifier = Modifier.weight(1f))
                }
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                EvoriaMoodOptions.drop(3).forEach { m ->
                  MoodPillItem(m, moodTag == m.key, { moodTag = m.key }, modifier = Modifier.weight(1f))
                }
              }
            }
          }

          // 3. Privacy & Media Attachment Card
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = tokens.elevatedCard,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.75f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Privasi & Lampiran", style = MaterialTheme.typography.labelLarge, color = tokens.primaryTerracotta)
              EvoriaSymmetricPrivacyRow(
                visibility = visibility,
                onVisibilityChange = { visibility = it },
              )
              val previewModel: Any? = pickedUri ?: existing?.attachmentUri
              if (previewModel != null) {
                val journalPreviewReq = rememberThumbnailRequest(previewModel, sizePx = 560)
                AsyncImage(
                  model = journalPreviewReq,
                  contentDescription = "Lampiran foto jurnal",
                  modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(14.dp)),
                  contentScale = ContentScale.Crop,
                )
              }
              OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, tokens.primaryTerracotta.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = if (previewModel != null) "Ganti Lampiran Foto" else "Lampirkan Foto Momen (Opsional)",
                  color = tokens.primaryTerracotta,
                )
              }
            }
          }
        }

        // Elevated Action Dock
        Surface(
          color = tokens.cardSurface,
          tonalElevation = 6.dp,
          shadowElevation = 8.dp,
          border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.6f)),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(
              onClick = { onSave(title, body, visibility, moodTag, true, pickedUri) },
              shape = RoundedCornerShape(50),
              modifier = Modifier.weight(1f).height(48.dp),
            ) {
              Text("Simpan Draf", maxLines = 1)
            }
            Button(
              onClick = { onSave(title, body, visibility, moodTag, false, pickedUri) },
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
              elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
              modifier = Modifier.weight(1.2f).height(48.dp).testTag("journal_save_button"),
            ) {
              Text("Simpan Jurnal", color = Color.White, maxLines = 1)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LetterEditorDialog(
  existing: LetterEntity?,
  onDismiss: () -> Unit,
  onSave: (title: String, body: String, recipientName: String, isSealed: Boolean, isDraft: Boolean, isShared: Boolean) -> Unit,
) {
  var title by remember { mutableStateOf(existing?.title.orEmpty()) }
  var recipient by remember { mutableStateOf(existing?.recipientName ?: "Kamu") }
  var body by remember { mutableStateOf(existing?.body.orEmpty()) }
  var isSealed by remember { mutableStateOf(existing?.isSealed ?: true) }
  var isShared by remember { mutableStateOf(existing?.isShared ?: false) }

  EvoriaSymmetricDialogShell(
    title = if (existing == null) "Surat Tak Terkirim" else "Baca / Ubah Surat",
    subtitle = "Simpan sebagai draf pribadi atau segel untuk nanti",
    icon = Icons.Filled.MailOutline,
    onDismiss = onDismiss,
    secondaryActionLabel = "Simpan Draf",
    onSecondaryAction = { onSave(title, body, recipient, isSealed, true, isShared) },
    confirmLabel = "Simpan Surat",
    onConfirm = { onSave(title, body, recipient, isSealed, false, isShared) },
  ) {
    EvoriaFormTextField(
      value = title,
      onValueChange = { title = it },
      label = "Judul Surat",
      placeholder = "Misal: Untuk kamu yang selalu sabar",
      singleLine = true,
    )
    EvoriaFormTextField(
      value = recipient,
      onValueChange = { recipient = it },
      label = "Ditujukan untuk",
      singleLine = true,
    )
    EvoriaFormTextField(
      value = body,
      onValueChange = { body = it },
      label = "Isi Surat",
      singleLine = false,
      minLines = 4,
      maxLines = 8,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text("Segel Surat", style = MaterialTheme.typography.bodyMedium)
      Switch(checked = isSealed, onCheckedChange = { isSealed = it })
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text("Bagikan ke Pasangan", style = MaterialTheme.typography.bodyMedium)
      Switch(checked = isShared, onCheckedChange = { isShared = it })
    }
  }
}

@Composable
private fun CreateCapsuleDialog(
  onDismiss: () -> Unit,
  onCreate: (title: String, body: String, unlockAtMillis: Long, visibility: String, attachmentUri: Uri?) -> Unit,
) {
  val context = LocalContext.current
  val tokens = LocalEvoriaTokens.current
  var title by remember { mutableStateOf("") }
  var body by remember { mutableStateOf("") }
  var visibility by remember { mutableStateOf("private") }
  var attachmentUri by remember { mutableStateOf<Uri?>(null) }
  var unlockAtMillis by remember {
    mutableLongStateOf(System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L)
  }

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) attachmentUri = uri
    }

  EvoriaSymmetricDialogShell(
    title = "Buat Kapsul Waktu",
    subtitle = "Terkunci aman hingga tanggal buka tiba",
    icon = Icons.Filled.HourglassBottom,
    onDismiss = onDismiss,
    confirmLabel = "Segel Sekarang",
    onConfirm = { onCreate(title, body, unlockAtMillis, visibility, attachmentUri) },
  ) {
    EvoriaFormTextField(
      value = title,
      onValueChange = { title = it },
      label = "Judul Kapsul",
      placeholder = "Misal: Untuk perayaan tahun depan",
      singleLine = true,
    )
    EvoriaFormTextField(
      value = body,
      onValueChange = { body = it },
      label = "Pesan untuk Masa Depan",
      singleLine = false,
      minLines = 3,
      maxLines = 6,
    )
    EvoriaSymmetricPrivacyRow(
      visibility = visibility,
      onVisibilityChange = { visibility = it },
    )
    Text("Tanggal Buka: ${formatIndoDate(unlockAtMillis)}", style = MaterialTheme.typography.labelLarge, color = tokens.primaryTerracotta)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        FilterChip(
          selected = false,
          onClick = { unlockAtMillis = System.currentTimeMillis() + 7L * 24 * 3600 * 1000L },
          label = { Text("+7 Hari") },
        )
      }
      item {
        FilterChip(
          selected = false,
          onClick = { unlockAtMillis = System.currentTimeMillis() + 30L * 24 * 3600 * 1000L },
          label = { Text("+1 Bulan") },
        )
      }
      item {
        FilterChip(
          selected = false,
          onClick = { unlockAtMillis = System.currentTimeMillis() + 365L * 24 * 3600 * 1000L },
          label = { Text("+1 Tahun") },
        )
      }
    }
    OutlinedButton(
      onClick = {
        val cal = Calendar.getInstance().apply { timeInMillis = unlockAtMillis }
        DatePickerDialog(
          context,
          { _, y, m, d ->
            val picked = Calendar.getInstance().apply {
              set(y, m, d, 9, 0, 0)
              set(Calendar.MILLISECOND, 0)
            }
            unlockAtMillis = picked.timeInMillis.coerceAtLeast(System.currentTimeMillis() + 86_400_000L)
          },
          cal.get(Calendar.YEAR),
          cal.get(Calendar.MONTH),
          cal.get(Calendar.DAY_OF_MONTH),
        ).apply {
          datePicker.minDate = System.currentTimeMillis() + 86_400_000L
        }.show()
      },
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
      Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text("Pilih Tanggal Sendiri")
    }
    OutlinedButton(
      onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
      Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text(if (attachmentUri != null) "Foto Terlampir" else "Lampirkan Foto (Opsional)")
    }
  }
}

// --- MUSIC & SOUNDTRACK SCREENS (Secondary Feature, Not a 6th Tab) ---

private val SpotifyObsidianBg = Color(0xFF121212)
private val SpotifyCardBg = Color(0xFF1C1C1E)
private val SpotifyHeroTop = Color(0xFF213B30)
private val SpotifyEmerald = Color(0xFF1DB954)
private val SpotifyTextPrimary = Color(0xFFFFFFFF)
private val SpotifyTextMuted = Color(0xFFB3B3B3)
private val SpotifyOutline = Color(0xFF2E2E32)

private fun isLocalFullAudioTrack(track: TrackEntity): Boolean =
  track.mediaUri.isNotBlank() && !track.mediaUri.startsWith("http")

private fun isYouTubeMatchedTrack(track: TrackEntity): Boolean =
  !track.youtubeVideoId.isNullOrBlank()

private fun openExternalUri(context: Context, url: String) {
  runCatching {
    context.startActivity(
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    )
  }
}

private fun openTrackOnYouTube(context: Context, track: TrackEntity) {
  val url =
    if (!track.youtubeVideoId.isNullOrBlank()) {
      "https://www.youtube.com/watch?v=${track.youtubeVideoId}"
    } else {
      val q = URLEncoder.encode("${track.title} ${track.artist} official audio", "UTF-8")
      "https://www.youtube.com/results?search_query=$q"
    }
  openExternalUri(context, url)
}

private fun openSpotifyPreferNative(context: Context, externalUrl: String): Boolean {
  val nativeUri = SpotifyPlaylistImporter.toNativeSpotifyUri(externalUrl)
  if (nativeUri != null) {
    val launchedNative =
      runCatching {
          context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(nativeUri)).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
          )
        }
        .isSuccess
    if (launchedNative) return true
  }
  return runCatching {
      context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      )
    }
    .isSuccess
}

@Composable
fun MusicAndSoundtrackScreen(
  playlists: List<PlaylistEntity>,
  tracks: List<TrackEntity>,
  albums: List<LocalAlbumEntity>,
  playerState: MusicPlayerState,
  spotifyImportState: SpotifyImportUiState = SpotifyImportUiState.Idle,
  matchingYouTubePlaylistId: String? = null,
  isYouTubeApiConfigured: Boolean = YouTubeDataApiClient.isApiKeyConfigured(),
  youtubeDirectSearchState: YouTubeDirectSearchUiState = YouTubeDirectSearchUiState.Idle,
  hasConnectedPartner: Boolean = false,
  onSearchYouTubeDirect: (String) -> Unit = {},
  onClearYouTubeDirectSearch: () -> Unit = {},
  onPlayYouTubeMatchDirectly: (match: YouTubeTrackMatch, preferredPlaylistId: String?, onReadyToOpenPlayer: () -> Unit) -> Unit = { _, _, _ -> },
  onCreatePlaylist: (title: String, subtitle: String, linkedAlbumId: String?) -> Unit,
  onDeletePlaylist: (String) -> Unit = {},
  onImportSpotifyPlaylist: (rawUrl: String, linkedAlbumId: String?, onImported: (String) -> Unit) -> Unit = { _, _, _ -> },
  onResetSpotifyImport: () -> Unit = {},
  onMatchPlaylistWithYouTube: (playlistId: String) -> Unit = {},
  onMatchTrackWithYouTube: (TrackEntity) -> Unit = {},
  onAddAudioTrack: (playlistId: String, title: String, artist: String, uri: Uri) -> Unit,
  onAttachLocalAudioToTrack: (TrackEntity, Uri) -> Unit = { _, _ -> },
  onBatchAttachLocalAudio: (playlistId: String, List<Uri>) -> Unit = { _, _ -> },
  onMoveTrackOrder: (playlistTracks: List<TrackEntity>, fromIndex: Int, toIndex: Int) -> Unit,
  onDeleteTrack: (String) -> Unit,
  onPlayTrack: (playlistTracks: List<TrackEntity>, trackId: String) -> Unit,
  onSendPlaylistToPartner: (PlaylistEntity, List<TrackEntity>) -> Unit = { _, _ -> },
  onSendTrackToPartner: (TrackEntity) -> Unit = {},
  onOpenFullPlayer: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val tokens = LocalEvoriaTokens.current
  val context = LocalContext.current
  var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
  var youtubeSearchQuery by rememberSaveable { mutableStateOf("") }
  var showNewPlaylistDialog by remember { mutableStateOf(false) }
  var showSpotifyImportDialog by remember { mutableStateOf(false) }
  var playlistPendingDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

  var pendingPlaylistForAudio by remember { mutableStateOf<String?>(null) }
  var pendingPlaylistForBatchAudio by remember { mutableStateOf<String?>(null) }
  var trackTitleDraft by remember { mutableStateOf("") }
  var trackArtistDraft by remember { mutableStateOf("") }
  var showTrackMetaDialog by remember { mutableStateOf<Uri?>(null) }
  var pendingAttachTrack by remember { mutableStateOf<TrackEntity?>(null) }

  val audioPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        trackTitleDraft = ""
        trackArtistDraft = ""
        showTrackMetaDialog = uri
      }
    }

  val attachAudioPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      val target = pendingAttachTrack
      pendingAttachTrack = null
      if (uri != null && target != null) {
        onAttachLocalAudioToTrack(target, uri)
      }
    }

  val batchAudioPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      val plId = pendingPlaylistForBatchAudio
      pendingPlaylistForBatchAudio = null
      if (uris.isNotEmpty() && plId != null) {
        onBatchAttachLocalAudio(plId, uris)
      }
    }

  val activePlaylist = playlists.firstOrNull { it.id == selectedPlaylistId } ?: playlists.firstOrNull()
  val playlistTracks =
    remember(tracks, activePlaylist) {
      if (activePlaylist == null) emptyList()
      else tracks.filter { it.playlistId == activePlaylist.id }.sortedBy { it.sortOrder }
    }
  val displayedTracks =
    remember(playlistTracks, youtubeSearchQuery, youtubeDirectSearchState) {
      val q = youtubeSearchQuery.trim()
      if (q.isEmpty() || youtubeDirectSearchState !is YouTubeDirectSearchUiState.Idle) playlistTracks
      else playlistTracks.filter { it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
    }
  val isMatchingCurrentPlaylist =
    activePlaylist != null && matchingYouTubePlaylistId == activePlaylist.id

  val autoMatchedPlaylistSessions = remember { mutableStateOf<Set<String>>(emptySet()) }
  LaunchedEffect(activePlaylist?.id, playlistTracks.size) {
    val pl = activePlaylist ?: return@LaunchedEffect
    val hasUnmatched =
      pl.sourceType == "spotify" &&
        playlistTracks.any { !isYouTubeMatchedTrack(it) && !isLocalFullAudioTrack(it) }
    if (hasUnmatched && matchingYouTubePlaylistId == null && pl.id !in autoMatchedPlaylistSessions.value) {
      autoMatchedPlaylistSessions.value = autoMatchedPlaylistSessions.value + pl.id
      onMatchPlaylistWithYouTube(pl.id)
    }
  }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(SpotifyHeroTop, SpotifyObsidianBg, SpotifyObsidianBg)
          )
        )
        .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Spotify-style dark top navigation bar
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Kembali",
            tint = SpotifyTextPrimary,
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Musik & Soundtrack",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = SpotifyTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "Metadata Spotify + YouTube Full Audio",
            style = MaterialTheme.typography.labelSmall,
            color = SpotifyTextMuted,
          )
        }
        Surface(
          shape = RoundedCornerShape(50),
          color = SpotifyEmerald.copy(alpha = 0.18f),
          border = BorderStroke(1.dp, SpotifyEmerald.copy(alpha = 0.6f)),
          modifier =
            Modifier
              .clickable {
                onResetSpotifyImport()
                showSpotifyImportDialog = true
              }
              .testTag("open_spotify_import_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = SpotifyEmerald, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text("Impor", style = MaterialTheme.typography.labelMedium, color = SpotifyEmerald)
          }
        }
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(onClick = { showNewPlaylistDialog = true }) {
          Icon(Icons.Filled.Add, contentDescription = "Playlist Baru", tint = SpotifyTextPrimary)
        }
      }

      // Unified YouTube Search & Playlist Filter bar (works with or without YOUTUBE_API_KEY)
      EvoriaPillSearchBar(
        query = youtubeSearchQuery,
        onQueryChange = { q ->
          youtubeSearchQuery = q
          if (q.isBlank()) onClearYouTubeDirectSearch()
        },
        onSearch = { onSearchYouTubeDirect(youtubeSearchQuery) },
        placeholder = "Cari lagu di YouTube atau filter playlist...",
        inputTestTag = "music_youtube_search_input",
        submitTestTag = "music_youtube_search_submit",
        darkSpotifyStyle = true,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
      )

      if (youtubeDirectSearchState !is YouTubeDirectSearchUiState.Idle) {
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = SpotifyCardBg,
          border = BorderStroke(1.dp, SpotifyEmerald.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Hasil Pencarian YouTube",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = SpotifyEmerald,
              )
              IconButton(
                onClick = {
                  youtubeSearchQuery = ""
                  onClearYouTubeDirectSearch()
                },
                modifier = Modifier.size(32.dp).testTag("music_youtube_search_close"),
              ) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup hasil YouTube", tint = SpotifyTextMuted, modifier = Modifier.size(18.dp))
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            when (val ytState = youtubeDirectSearchState) {
              YouTubeDirectSearchUiState.Loading -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.padding(vertical = 8.dp),
                ) {
                  CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SpotifyEmerald)
                  Text("Mencari lagu di YouTube...", style = MaterialTheme.typography.bodySmall, color = SpotifyTextMuted)
                }
              }
              is YouTubeDirectSearchUiState.Error -> {
                Text(
                  text = ytState.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.roseError,
                  modifier = Modifier.padding(vertical = 4.dp),
                )
              }
              is YouTubeDirectSearchUiState.Success -> {
                if (ytState.results.isEmpty()) {
                  Text(
                    text = "Tidak ditemukan lagu untuk \"${ytState.query}\". Coba kata kunci lain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyTextMuted,
                    modifier = Modifier.padding(vertical = 6.dp),
                  )
                } else {
                  LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 228.dp).testTag("music_youtube_results_list"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    items(ytState.results, key = { it.videoId }) { match ->
                      Row(
                        modifier =
                          Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF242429))
                            .clickable {
                              onPlayYouTubeMatchDirectly(match, activePlaylist?.id, onOpenFullPlayer)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("yt_direct_play_${match.videoId}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                      ) {
                        val thumbReq = rememberThumbnailRequest(YouTubeDataApiClient.thumbnailUrl(match.videoId), sizePx = 128)
                        AsyncImage(
                          model = thumbReq,
                          contentDescription = match.title,
                          contentScale = ContentScale.Crop,
                          modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(SpotifyHeroTop),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = match.title,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = SpotifyTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                          )
                          val durLabel = if (match.durationMs > 0L) " • ${formatDurationMmSs(match.durationMs)}" else ""
                          Text(
                            text = "${match.channelTitle}$durLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                          )
                        }
                        Surface(
                          shape = CircleShape,
                          color = SpotifyEmerald,
                          modifier = Modifier.size(34.dp),
                        ) {
                          Box(contentAlignment = Alignment.Center) {
                            Icon(
                              imageVector = Icons.Filled.PlayArrow,
                              contentDescription = "Putar ${match.title}",
                              tint = Color.Black,
                              modifier = Modifier.size(20.dp),
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }
              YouTubeDirectSearchUiState.Idle -> Unit
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      LazyColumn(
        modifier = Modifier.weight(1f).testTag("music_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Horizontal playlist selector chips
        if (playlists.isNotEmpty()) {
          item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(playlists, key = { it.id }) { pl ->
                val selected = activePlaylist?.id == pl.id
                FilterChip(
                  selected = selected,
                  onClick = { selectedPlaylistId = pl.id },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      containerColor = SpotifyCardBg,
                      labelColor = SpotifyTextMuted,
                      selectedContainerColor = SpotifyEmerald,
                      selectedLabelColor = Color.Black,
                      selectedLeadingIconColor = Color.Black,
                    ),
                  border =
                    FilterChipDefaults.filterChipBorder(
                      enabled = true,
                      selected = selected,
                      borderColor = SpotifyOutline,
                      selectedBorderColor = SpotifyEmerald,
                    ),
                  leadingIcon =
                    if (pl.sourceType == "spotify") {
                      { Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    } else null,
                  label = { Text(pl.title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                )
              }
            }
          }
        }

        // Now playing dock inside Music hub
        if (playerState.currentTrack != null) {
          item {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = Color(0xFF282828),
              border = BorderStroke(1.dp, SpotifyEmerald.copy(alpha = 0.45f)),
              modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFullPlayer),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Box(
                  modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(SpotifyHeroTop),
                  contentAlignment = Alignment.Center,
                ) {
                  val cover = playerState.currentTrack.coverUrl ?: activePlaylist?.coverUrl
                  if (!cover.isNullOrBlank()) {
                    val coverReq = rememberThumbnailRequest(cover, sizePx = 140)
                    AsyncImage(model = coverReq, contentDescription = playerState.currentTrack.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                  } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SpotifyEmerald)
                  }
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = if (playerState.isYouTubeMode) "Putar Penuh YouTube" else "Sedang Diputar",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyEmerald,
                  )
                  Text(
                    text = playerState.currentTrack.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = SpotifyTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                      if (playerState.isPlaying && playerState.currentTrack.title.length > 24) Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                      else Modifier,
                  )
                  Text(playerState.currentTrack.artist, style = MaterialTheme.typography.bodySmall, color = SpotifyTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(shape = CircleShape, color = SpotifyEmerald, modifier = Modifier.size(36.dp)) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                      contentDescription = "Buka Pemutar",
                      tint = Color.Black,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              }
            }
          }
        }

        if (activePlaylist == null) {
          item {
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = SpotifyCardBg,
              border = BorderStroke(1.dp, SpotifyOutline),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Belum Ada Playlist", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = SpotifyTextPrimary)
                Text(
                  "Impor playlist Spotify publik (hanya metadata lagu & poster), lalu EVORIA otomatis mencari versi lagu berdurasi penuh di YouTube Data API.",
                  style = MaterialTheme.typography.bodySmall,
                  color = SpotifyTextMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Button(
                    onClick = {
                      onResetSpotifyImport()
                      showSpotifyImportDialog = true
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyEmerald, contentColor = Color.Black),
                    modifier = Modifier.testTag("import_spotify_card_button"),
                  ) {
                    Text("Impor Spotify", fontWeight = FontWeight.Bold)
                  }
                  OutlinedButton(
                    onClick = { showNewPlaylistDialog = true },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, SpotifyOutline),
                  ) {
                    Text("Playlist Lokal", color = SpotifyTextPrimary)
                  }
                }
              }
            }
          }
        } else {
          // Spotify-style Playlist Hero Header
          item {
            val ytMatchedCount = playlistTracks.count { isYouTubeMatchedTrack(it) || isLocalFullAudioTrack(it) }
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = SpotifyCardBg,
              border = BorderStroke(1.dp, SpotifyOutline),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(18.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier =
                      Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                          Brush.linearGradient(listOf(SpotifyEmerald.copy(alpha = 0.65f), SpotifyHeroTop))
                        ),
                    contentAlignment = Alignment.Center,
                  ) {
                    val plCover = activePlaylist.coverUrl ?: playlistTracks.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
                    if (!plCover.isNullOrBlank()) {
                      val plCoverReq = rememberThumbnailRequest(plCover, sizePx = 320)
                      AsyncImage(
                        model = plCoverReq,
                        contentDescription = activePlaylist.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                      )
                    } else {
                      Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = SpotifyTextPrimary, modifier = Modifier.size(36.dp))
                    }
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Surface(
                        shape = RoundedCornerShape(50),
                        color = if (activePlaylist.sourceType == "spotify") SpotifyEmerald.copy(alpha = 0.2f) else SpotifyOutline,
                      ) {
                        Text(
                          text =
                            if (activePlaylist.sourceType == "spotify") "SPOTIFY + YT • $ytMatchedCount/${playlistTracks.size}"
                            else "PLAYLIST LOKAL • ${playlistTracks.size} LAGU",
                          style = MaterialTheme.typography.labelSmall,
                          color = if (activePlaylist.sourceType == "spotify") SpotifyEmerald else SpotifyTextMuted,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                      }
                      IconButton(
                        onClick = { playlistPendingDelete = activePlaylist },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_playlist_button"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Delete,
                          contentDescription = "Hapus playlist ${activePlaylist.title}",
                          tint = tokens.roseError.copy(alpha = 0.85f),
                          modifier = Modifier.size(19.dp),
                        )
                      }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = activePlaylist.title,
                      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                      color = SpotifyTextPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    if (activePlaylist.subtitle.isNotBlank()) {
                      Text(
                        text = activePlaylist.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyTextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }

                if (isMatchingCurrentPlaylist) {
                  Spacer(modifier = Modifier.height(10.dp))
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = SpotifyEmerald)
                    Text(
                      text = "Mencari & memuat audio penuh YouTube...",
                      style = MaterialTheme.typography.labelSmall,
                      color = SpotifyEmerald,
                    )
                  }
                } else if (activePlaylist.sourceType == "spotify" && !isYouTubeApiConfigured && playlistTracks.any { !isYouTubeMatchedTrack(it) && !isLocalFullAudioTrack(it) }) {
                  Spacer(modifier = Modifier.height(10.dp))
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF28231D),
                    border = BorderStroke(1.dp, SpotifyEmerald.copy(alpha = 0.35f)),
                  ) {
                    Text(
                      text = "Ketuk lagu mana saja atau tekan 'Cari YouTube' untuk mencari dan memutar audio YouTube langsung di aplikasi.",
                      style = MaterialTheme.typography.labelSmall,
                      color = tokens.accentPeach,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                  ) {
                    if (activePlaylist.sourceType == "spotify") {
                      OutlinedButton(
                        enabled = !isMatchingCurrentPlaylist,
                        onClick = { onMatchPlaylistWithYouTube(activePlaylist.id) },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, SpotifyEmerald.copy(alpha = 0.7f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("match_youtube_playlist_button"),
                      ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = SpotifyEmerald, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Cari YouTube", color = SpotifyEmerald, maxLines = 1)
                      }
                      OutlinedButton(
                        onClick = {
                          pendingPlaylistForBatchAudio = activePlaylist.id
                          batchAudioPicker.launch(arrayOf("audio/*"))
                        },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, SpotifyTextMuted.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                      ) {
                        Icon(Icons.Filled.AudioFile, contentDescription = null, tint = SpotifyTextPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("MP3", color = SpotifyTextPrimary, maxLines = 1)
                      }
                    } else {
                      OutlinedButton(
                        onClick = {
                          pendingPlaylistForAudio = activePlaylist.id
                          audioPicker.launch(arrayOf("audio/*"))
                        },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, SpotifyTextMuted.copy(alpha = 0.5f)),
                      ) {
                        Icon(Icons.Filled.AudioFile, contentDescription = null, tint = SpotifyTextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tambah Audio", color = SpotifyTextPrimary)
                      }
                    }
                  }

                  val playableTracks =
                    playlistTracks.filter { isLocalFullAudioTrack(it) || isYouTubeMatchedTrack(it) }
                  val firstTarget = playableTracks.firstOrNull() ?: playlistTracks.firstOrNull()
                  if (firstTarget != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                      shape = CircleShape,
                      color = SpotifyEmerald,
                      shadowElevation = 6.dp,
                      modifier =
                        Modifier
                          .size(48.dp)
                          .clickable {
                            if (isLocalFullAudioTrack(firstTarget) || isYouTubeMatchedTrack(firstTarget) || activePlaylist.sourceType != "spotify") {
                              onPlayTrack(playlistTracks, firstTarget.id)
                              onOpenFullPlayer()
                            } else {
                              onMatchTrackWithYouTube(firstTarget)
                            }
                          }
                          .testTag("spotify_hero_play_button"),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector =
                            if (playerState.isPlaying && playlistTracks.any { it.id == playerState.currentTrack?.id }) Icons.Filled.Pause
                            else Icons.Filled.PlayArrow,
                          contentDescription = "Putar Playlist",
                          tint = Color.Black,
                          modifier = Modifier.size(28.dp),
                        )
                      }
                    }
                  }
                }
                if (playlistTracks.isNotEmpty()) {
                  Spacer(modifier = Modifier.height(10.dp))
                  OutlinedButton(
                    onClick = { onSendPlaylistToPartner(activePlaylist, playlistTracks) },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, SpotifyEmerald.copy(alpha = if (hasConnectedPartner) 0.65f else 0.35f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("send_playlist_partner_button"),
                  ) {
                    Icon(Icons.Filled.Send, contentDescription = null, tint = SpotifyEmerald, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = if (hasConnectedPartner) "Kirim Playlist ke Pasangan" else "Kirim Playlist (Hubungkan Pasangan)",
                      color = SpotifyTextPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
          }

          if (playlistTracks.isEmpty()) {
            item {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = SpotifyCardBg,
                border = BorderStroke(1.dp, SpotifyOutline),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "Playlist masih kosong. Tambah berkas audio (MP3/M4A/WAV) dari perangkat atau impor metadata playlist Spotify.",
                  style = MaterialTheme.typography.bodySmall,
                  color = SpotifyTextMuted,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          } else if (displayedTracks.isEmpty()) {
            item {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = SpotifyCardBg,
                border = BorderStroke(1.dp, SpotifyOutline),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "Tidak ada lagu yang cocok dengan \"$youtubeSearchQuery\".",
                  style = MaterialTheme.typography.bodySmall,
                  color = SpotifyTextMuted,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          } else {
            itemsIndexed(
              items = displayedTracks,
              key = { _, t -> t.id },
              contentType = { _, _ -> "track_row" },
            ) { index, track ->
              val isPlayingThis = playerState.currentTrack?.id == track.id
              val hasFullLocalAudio = isLocalFullAudioTrack(track)
              val hasYouTubeMatch = isYouTubeMatchedTrack(track)
              val isPlayableInApp = hasFullLocalAudio || hasYouTubeMatch || track.sourceType != "spotify"
              val posterUrl = track.coverUrl ?: activePlaylist.coverUrl

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isPlayingThis) Color(0xFF242429) else SpotifyCardBg,
                border =
                  if (isPlayingThis) BorderStroke(1.dp, SpotifyEmerald.copy(alpha = 0.55f))
                  else BorderStroke(1.dp, SpotifyOutline.copy(alpha = 0.6f)),
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      if (isPlayableInApp) {
                        onPlayTrack(playlistTracks, track.id)
                        onOpenFullPlayer()
                      } else {
                        onMatchTrackWithYouTube(track)
                      }
                    }
                    .testTag("track_row_${track.id}"),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  // Track Poster Artwork
                  Box(
                    modifier =
                      Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyHeroTop),
                    contentAlignment = Alignment.Center,
                  ) {
                    if (!posterUrl.isNullOrBlank()) {
                      val posterReq = rememberThumbnailRequest(posterUrl, sizePx = 140)
                      AsyncImage(
                        model = posterReq,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                      )
                    } else {
                      Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SpotifyEmerald, modifier = Modifier.size(22.dp))
                    }
                    if (isPlayingThis) {
                      Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                      ) {
                        Icon(
                          imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                          contentDescription = null,
                          tint = SpotifyEmerald,
                          modifier = Modifier.size(22.dp),
                        )
                      }
                    }
                  }

                  // Animate marquee only on the actively playing track row so long playlists never tick dozens of animations
                  Column(modifier = Modifier.weight(1f)) {
                    val useMarquee = isPlayingThis && playerState.isPlaying && track.title.length > 22
                    Text(
                      text = track.title,
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                      color = if (isPlayingThis) SpotifyEmerald else SpotifyTextPrimary,
                      maxLines = 1,
                      softWrap = !useMarquee,
                      overflow = TextOverflow.Ellipsis,
                      modifier =
                        Modifier
                          .fillMaxWidth()
                          .then(if (useMarquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                    )
                    val durText = if (track.durationMs > 0L) " • ${formatDurationMmSs(track.durationMs)}" else ""
                    Text(
                      text = "${track.artist}$durText",
                      style = MaterialTheme.typography.bodySmall,
                      color = SpotifyTextMuted,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    if (track.sourceType == "spotify") {
                      val badgeLabel =
                        when {
                          hasFullLocalAudio -> "Audio penuh lokal • Siap putar"
                          hasYouTubeMatch -> "YouTube Audio • Ketuk putar"
                          else -> "Spotify • Ketuk untuk cari & putar"
                        }
                      Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasFullLocalAudio || hasYouTubeMatch) SpotifyEmerald else SpotifyTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }

                  if (track.sourceType == "spotify" && !hasFullLocalAudio && !hasYouTubeMatch) {
                    IconButton(
                      onClick = { onMatchTrackWithYouTube(track) },
                      modifier =
                        Modifier
                          .minimumInteractiveComponentSize()
                          .testTag("youtube_match_track_btn_${track.id}"),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Cari & putar ${track.title} di YouTube",
                        tint = SpotifyEmerald,
                      )
                    }
                  } else if (hasYouTubeMatch || hasFullLocalAudio) {
                    IconButton(
                      onClick = {
                        onPlayTrack(playlistTracks, track.id)
                        onOpenFullPlayer()
                      },
                      modifier =
                        Modifier
                          .minimumInteractiveComponentSize()
                          .testTag("play_full_track_btn_${track.id}"),
                    ) {
                      Icon(
                        imageVector =
                          if (isPlayingThis && playerState.isPlaying) Icons.Filled.Pause
                          else Icons.Filled.PlayArrow,
                        contentDescription = "Putar penuh ${track.title}",
                        tint = SpotifyEmerald,
                      )
                    }
                  }

                  var trackMenuExpanded by remember(track.id) { mutableStateOf(false) }
                  Box {
                    IconButton(onClick = { trackMenuExpanded = true }) {
                      Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Opsi lagu ${track.title}",
                        tint = SpotifyTextMuted,
                      )
                    }
                    DropdownMenu(
                      expanded = trackMenuExpanded,
                      onDismissRequest = { trackMenuExpanded = false },
                    ) {
                      if (isPlayableInApp) {
                        DropdownMenuItem(
                          leadingIcon = {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = SpotifyEmerald)
                          },
                          text = { Text("Putar Lagu Penuh") },
                          onClick = {
                            trackMenuExpanded = false
                            onPlayTrack(playlistTracks, track.id)
                            onOpenFullPlayer()
                          },
                        )
                      }
                      if (track.sourceType == "spotify") {
                        DropdownMenuItem(
                          leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = SpotifyEmerald)
                          },
                          text = {
                            Text(if (hasYouTubeMatch) "Cari Ulang di YouTube API" else "Cari Lagu Penuh di YouTube API")
                          },
                          onClick = {
                            trackMenuExpanded = false
                            onMatchTrackWithYouTube(track)
                          },
                        )
                      }
                      DropdownMenuItem(
                        leadingIcon = {
                          Icon(Icons.Filled.Send, contentDescription = null, tint = SpotifyEmerald)
                        },
                        text = { Text("Kirim Lagu ke Pasangan") },
                        onClick = {
                          trackMenuExpanded = false
                          onSendTrackToPartner(track)
                        },
                      )
                      DropdownMenuItem(
                        leadingIcon = {
                          Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        },
                        text = { Text("Buka di YouTube") },
                        onClick = {
                          trackMenuExpanded = false
                          openTrackOnYouTube(context, track)
                        },
                      )
                      if (!track.externalUrl.isNullOrBlank()) {
                        DropdownMenuItem(
                          leadingIcon = {
                            Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                          },
                          text = { Text("Buka di Spotify") },
                          onClick = {
                            trackMenuExpanded = false
                            openSpotifyPreferNative(context, track.externalUrl)
                          },
                        )
                      }
                      if (track.sourceType == "spotify") {
                        DropdownMenuItem(
                          leadingIcon = {
                            Icon(Icons.Filled.AudioFile, contentDescription = null)
                          },
                          text = { Text("Tautkan MP3 Lokal") },
                          onClick = {
                            trackMenuExpanded = false
                            pendingAttachTrack = track
                            attachAudioPicker.launch(arrayOf("audio/*"))
                          },
                        )
                      }
                      if (index > 0) {
                        DropdownMenuItem(
                          leadingIcon = {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = null)
                          },
                          text = { Text("Naikkan Urutan") },
                          onClick = {
                            trackMenuExpanded = false
                            onMoveTrackOrder(playlistTracks, index, index - 1)
                          },
                        )
                      }
                      if (index < playlistTracks.lastIndex) {
                        DropdownMenuItem(
                          leadingIcon = {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = null)
                          },
                          text = { Text("Turunkan Urutan") },
                          onClick = {
                            trackMenuExpanded = false
                            onMoveTrackOrder(playlistTracks, index, index + 1)
                          },
                        )
                      }
                      DropdownMenuItem(
                        leadingIcon = {
                          Icon(Icons.Filled.Delete, contentDescription = null, tint = tokens.roseError)
                        },
                        text = { Text("Hapus Trek", color = tokens.roseError) },
                        onClick = {
                          trackMenuExpanded = false
                          onDeleteTrack(track.id)
                        },
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

  if (showSpotifyImportDialog) {
    var spotifyUrlDraft by remember { mutableStateOf("") }
    var spAlbumId by remember { mutableStateOf<String?>(null) }
    val isImporting = spotifyImportState is SpotifyImportUiState.Loading
    val importError = (spotifyImportState as? SpotifyImportUiState.Error)?.message

    EvoriaSymmetricDialogShell(
      title = "Impor Playlist Spotify",
      subtitle = "Metadata Spotify dicocokkan ke lagu penuh YouTube API",
      icon = Icons.Filled.CloudDownload,
      onDismiss = {
        onResetSpotifyImport()
        showSpotifyImportDialog = false
      },
      confirmLabel = "Impor Sekarang",
      confirmEnabled = !isImporting && spotifyUrlDraft.isNotBlank(),
      confirmTestTag = "spotify_import_confirm",
      dismissEnabled = !isImporting,
      accentColor = SpotifyEmerald,
      confirmContentColor = Color.Black,
      onConfirm = {
        onImportSpotifyPlaylist(spotifyUrlDraft, spAlbumId) { importedPlaylistId ->
          selectedPlaylistId = importedPlaylistId
          showSpotifyImportDialog = false
        }
      },
    ) {
      Text(
        "Tempel tautan playlist Spotify publik. Aplikasi mengambil metadata & poster lagu dari Spotify lalu mencari lagu berdurasi penuh di YouTube Data API.",
        style = MaterialTheme.typography.bodySmall,
        color = tokens.mutedText,
      )
      EvoriaFormTextField(
        value = spotifyUrlDraft,
        onValueChange = { spotifyUrlDraft = it },
        label = "Tautan / URI Playlist Spotify",
        placeholder = "https://open.spotify.com/playlist/...",
        leadingIcon = Icons.Filled.LibraryMusic,
        singleLine = true,
        enabled = !isImporting,
        trailingIcon = {
          IconButton(
            enabled = !isImporting,
            onClick = {
              val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
              val clipText = cb?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
              if (clipText.isNotBlank()) spotifyUrlDraft = clipText.trim()
            },
          ) {
            Icon(Icons.Filled.ContentPaste, contentDescription = "Tempel tautan")
          }
        },
        modifier = Modifier.testTag("spotify_url_input"),
      )
      if (albums.isNotEmpty()) {
        Text("Kaitkan dengan album (opsional):", style = MaterialTheme.typography.labelMedium, color = tokens.inkText)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item { FilterChip(selected = spAlbumId == null, onClick = { spAlbumId = null }, label = { Text("Umum") }) }
          items(albums, key = { it.id }) { alb ->
            FilterChip(selected = spAlbumId == alb.id, onClick = { spAlbumId = alb.id }, label = { Text(alb.title) })
          }
        }
      }
      if (isImporting) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SpotifyEmerald)
          Text("Mengambil metadata playlist dari Spotify...", style = MaterialTheme.typography.bodySmall, color = tokens.inkText)
        }
      }
      if (!importError.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = tokens.roseError.copy(alpha = 0.12f),
        ) {
          Text(
            text = importError,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.roseError,
            modifier = Modifier.padding(12.dp),
          )
        }
      }
    }
  }

  if (showNewPlaylistDialog) {
    var plTitle by remember { mutableStateOf("") }
    var plSubtitle by remember { mutableStateOf("") }
    var plAlbumId by remember { mutableStateOf<String?>(null) }

    EvoriaSymmetricDialogShell(
      title = "Playlist Baru",
      subtitle = "Buat daftar putar soundtrack kenangan kalian",
      icon = Icons.Filled.LibraryMusic,
      onDismiss = { showNewPlaylistDialog = false },
      confirmLabel = "Buat Playlist",
      accentColor = SpotifyEmerald,
      confirmContentColor = Color.Black,
      onConfirm = {
        onCreatePlaylist(plTitle, plSubtitle, plAlbumId)
        showNewPlaylistDialog = false
      },
    ) {
      EvoriaFormTextField(
        value = plTitle,
        onValueChange = { plTitle = it },
        label = "Nama Playlist",
        placeholder = "Misal: Senja di Perjalanan",
        singleLine = true,
      )
      EvoriaFormTextField(
        value = plSubtitle,
        onValueChange = { plSubtitle = it },
        label = "Keterangan / Suasana",
        singleLine = true,
      )
      if (albums.isNotEmpty()) {
        Text("Kaitkan dengan album (opsional):", style = MaterialTheme.typography.labelMedium, color = tokens.inkText)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item { FilterChip(selected = plAlbumId == null, onClick = { plAlbumId = null }, label = { Text("Umum") }) }
          items(albums, key = { it.id }) { alb ->
            FilterChip(selected = plAlbumId == alb.id, onClick = { plAlbumId = alb.id }, label = { Text(alb.title) })
          }
        }
      }
    }
  }

  showTrackMetaDialog?.let { uri ->
    EvoriaSymmetricDialogShell(
      title = "Detail Trek Audio",
      subtitle = "Lengkapi judul & artis sebelum disimpan ke playlist",
      icon = Icons.Filled.MusicNote,
      onDismiss = { showTrackMetaDialog = null },
      confirmLabel = "Tambahkan",
      accentColor = SpotifyEmerald,
      confirmContentColor = Color.Black,
      onConfirm = {
        val targetPl = pendingPlaylistForAudio ?: activePlaylist?.id
        if (targetPl != null) {
          onAddAudioTrack(targetPl, trackTitleDraft, trackArtistDraft, uri)
        }
        showTrackMetaDialog = null
      },
    ) {
      EvoriaFormTextField(
        value = trackTitleDraft,
        onValueChange = { trackTitleDraft = it },
        label = "Judul Lagu",
        singleLine = true,
      )
      EvoriaFormTextField(
        value = trackArtistDraft,
        onValueChange = { trackArtistDraft = it },
        label = "Artis / Catatan",
        singleLine = true,
      )
    }
  }

  playlistPendingDelete?.let { target ->
    val targetTracksCount = tracks.count { it.playlistId == target.id }
    AlertDialog(
      onDismissRequest = { playlistPendingDelete = null },
      title = { Text("Hapus Playlist?") },
      text = {
        Text(
          "Playlist \"${target.title}\" beserta $targetTracksCount lagu di dalamnya akan dihapus dari perangkat ini."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (selectedPlaylistId == target.id) {
              selectedPlaylistId = null
            }
            onDeletePlaylist(target.id)
            playlistPendingDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = tokens.roseError, contentColor = Color.White),
          modifier = Modifier.testTag("confirm_delete_playlist_button"),
        ) {
          Text("Hapus Playlist")
        }
      },
      dismissButton = {
        TextButton(onClick = { playlistPendingDelete = null }) {
          Text("Batal")
        }
      },
    )
  }
}

internal fun sanitizeYouTubeVideoId(raw: String): String =
  raw.replace(Regex("[^A-Za-z0-9_-]"), "")

internal fun buildAppEmbedOrigin(packageName: String): String {
  val sanitized = packageName.trim().lowercase().replace(Regex("[^a-z0-9.-]"), "")
  return "https://${sanitized.ifBlank { "com.aistudio.evoria" }}"
}

internal fun isYouTubeEmbedFallbackErrorCode(code: Int): Boolean =
  code in setOf(0, 2, 5, 101, 150, 152, 153)

internal fun resolveYouTubeInAppFallbackUrl(videoId: String): String =
  "https://m.youtube.com/watch?v=${sanitizeYouTubeVideoId(videoId)}&autoplay=1"

internal fun buildYouTubeMobileWatchBridgeJs(): String =
  """
  (function() {
    if (window.__evoriaWatchInit) return;
    window.__evoriaWatchInit = true;
    try {
      Object.defineProperty(document, 'hidden', { configurable: true, get: function() { return false; } });
      Object.defineProperty(document, 'visibilityState', { configurable: true, get: function() { return 'visible'; } });
    } catch (e) {}
    var style = document.createElement('style');
    style.textContent = 'body, html { background: #0D0D0F !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; } ytm-mobile-topbar-renderer, header, #header-bar, ytm-item-section-renderer, ytm-comment-section-header-renderer, ytm-engagement-panel-section-list-renderer, .ytm-autonav-bar, ytm-upsell-dialog-renderer, tp-yt-paper-dialog, .open-in-app-banner, ytm-app-upsell-dialog-renderer, ytm-promoted-sparkles-web-renderer { display: none !important; } #player-container-id, .player-container, #movie_player, video { width: 100vw !important; height: 100vh !important; max-height: 100vh !important; position: fixed !important; top: 0 !important; left: 0 !important; z-index: 9999 !important; background: #0D0D0F !important; object-fit: contain !important; }';
    (document.head || document.documentElement).appendChild(style);

    function findVideo() { return document.querySelector('video'); }
    function ensurePlay() {
      var v = findVideo();
      if (!v) return false;
      try { v.muted = false; v.volume = 1.0; } catch (e) {}
      if (v.paused && !v.ended) {
        try {
          var p = v.play();
          if (p && p.catch) { p.catch(function(){}); }
        } catch (e) {}
      }
      var unmuteBtn = document.querySelector('.ytp-unmute, button[aria-label*="Unmute"], button[aria-label*="Suara"]');
      if (unmuteBtn) { try { unmuteBtn.click(); } catch (e) {} }
      var playBtn = document.querySelector('.ytp-large-play-button, button[aria-label="Play"], button[aria-label="Putar"]');
      if (v.paused && playBtn) { try { playBtn.click(); } catch (e) {} }
      return true;
    }

    window.playCmd = function() {
      var v = findVideo();
      if (v) { try { v.muted = false; v.volume = 1.0; v.play(); } catch (e) {} }
    };
    window.pauseCmd = function() {
      var v = findVideo();
      if (v) { try { v.pause(); } catch (e) {} }
    };
    window.seekCmd = function(sec) {
      var v = findVideo();
      if (v && isFinite(sec)) { try { v.currentTime = sec; } catch (e) {} }
    };
    window.loadFullVideo = function(vid, startSec) {
      var t = Math.max(0, Math.round(startSec || 0));
      window.location.href = 'https://m.youtube.com/watch?v=' + vid + '&autoplay=1' + (t > 0 ? '&t=' + t + 's' : '');
    };

    var boundVideo = null;
    var attempts = 0;
    setInterval(function() {
      var v = findVideo();
      if (v && v !== boundVideo) {
        boundVideo = v;
        v.addEventListener('ended', function() {
          if (window.EvoriaYtBridge) window.EvoriaYtBridge.onEnded();
        });
        ensurePlay();
      } else if (v && attempts < 14) {
        attempts++;
        if (v.paused) ensurePlay();
      }
      if (v && window.EvoriaYtBridge) {
        var dur = (v.duration && isFinite(v.duration)) ? v.duration : 0;
        var pos = (v.currentTime && isFinite(v.currentTime)) ? v.currentTime : 0;
        var playing = !v.paused && !v.ended && v.readyState >= 2;
        window.EvoriaYtBridge.onProgress(playing, Math.round(pos * 1000), Math.round(dur * 1000));
      }
    }, 450);
  })();
  """.trimIndent()

internal fun buildYouTubeIFrameHtml(
  initialVideoId: String,
  appOrigin: String = "https://com.aistudio.evoria",
): String {
  val safeId = sanitizeYouTubeVideoId(initialVideoId)
  val safeOrigin = appOrigin.replace("'", "").replace("\"", "")
  return """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0" />
      <meta name="referrer" content="strict-origin-when-cross-origin" />
      <style>
        html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #0D0D0F; overflow: hidden; }
        #player { width: 100%; height: 100%; }
      </style>
    </head>
    <body>
      <div id="player"></div>
      <script>
        try {
          Object.defineProperty(document, 'hidden', { configurable: true, get: function() { return false; } });
          Object.defineProperty(document, 'visibilityState', { configurable: true, get: function() { return 'visible'; } });
        } catch (e) {}
      </script>
      <script src="https://www.youtube.com/iframe_api"></script>
      <script>
        var ytPlayer = null;
        var isReady = false;
        var currentLoadedId = "$safeId";
        var pendingVideoId = "$safeId";
        var pendingStartSec = 0;
        var pendingCmd = null;
        var pollTimer = null;

        setTimeout(function() {
          if (!isReady && !ytPlayer && window.EvoriaYtBridge) {
            window.EvoriaYtBridge.onFallbackNeeded(0);
          }
        }, 4200);

        function onYouTubeIframeAPIReady() {
          ytPlayer = new YT.Player('player', {
            height: '100%',
            width: '100%',
            videoId: pendingVideoId,
            playerVars: {
              autoplay: 1,
              playsinline: 1,
              controls: 1,
              rel: 0,
              modestbranding: 1,
              enablejsapi: 1,
              origin: '$safeOrigin',
              widget_referrer: '$safeOrigin'
            },
            events: {
              onReady: function(ev) {
                isReady = true;
                try {
                  if (ev.target.unMute) ev.target.unMute();
                  if (ev.target.setVolume) ev.target.setVolume(100);
                } catch (e) {}
                if (pendingVideoId && pendingVideoId !== currentLoadedId) {
                  currentLoadedId = pendingVideoId;
                  ev.target.loadVideoById({ videoId: pendingVideoId, startSeconds: pendingStartSec || 0 });
                } else {
                  ev.target.playVideo();
                }
                if (pendingCmd === 'pause' && ev.target.pauseVideo) {
                  ev.target.pauseVideo();
                }
                pendingCmd = null;
                startPolling();
              },
              onStateChange: function(ev) {
                if (ev.data === YT.PlayerState.ENDED) {
                  if (window.EvoriaYtBridge) window.EvoriaYtBridge.onEnded();
                } else {
                  reportNow();
                }
              },
              onError: function(ev) {
                var code = (ev && typeof ev.data === 'number') ? ev.data : 0;
                if (window.EvoriaYtBridge) {
                  window.EvoriaYtBridge.onFallbackNeeded(code);
                }
              }
            }
          });
        }

        function startPolling() {
          if (pollTimer) clearInterval(pollTimer);
          pollTimer = setInterval(reportNow, 500);
        }

        function reportNow() {
          if (!ytPlayer || !isReady || !window.EvoriaYtBridge) return;
          var state = ytPlayer.getPlayerState ? ytPlayer.getPlayerState() : -1;
          var isPlaying = (state === 1 || state === 3);
          var pos = ytPlayer.getCurrentTime ? ytPlayer.getCurrentTime() : 0;
          var dur = ytPlayer.getDuration ? ytPlayer.getDuration() : 0;
          window.EvoriaYtBridge.onProgress(isPlaying, Math.round(pos * 1000), Math.round(dur * 1000));
        }

        function loadFullVideo(vid, startSec) {
          pendingVideoId = vid;
          pendingStartSec = startSec || 0;
          if (ytPlayer && isReady && ytPlayer.loadVideoById) {
            if (vid === currentLoadedId && (startSec || 0) === 0) {
              try {
                if (ytPlayer.unMute) ytPlayer.unMute();
                ytPlayer.playVideo();
              } catch (e) {}
              return;
            }
            currentLoadedId = vid;
            ytPlayer.loadVideoById({ videoId: vid, startSeconds: pendingStartSec });
          }
        }
        function playCmd() {
          pendingCmd = 'play';
          if (ytPlayer && isReady && ytPlayer.playVideo) {
            try { if (ytPlayer.unMute) ytPlayer.unMute(); } catch (e) {}
            ytPlayer.playVideo();
          }
        }
        function pauseCmd() {
          pendingCmd = 'pause';
          if (ytPlayer && isReady && ytPlayer.pauseVideo) ytPlayer.pauseVideo();
        }
        function seekCmd(sec) { if (ytPlayer && isReady && ytPlayer.seekTo) ytPlayer.seekTo(sec, true); }
      </script>
    </body>
    </html>
  """.trimIndent()
}

private class EvoriaBackgroundWebView(context: Context) : WebView(context) {
  override fun onWindowVisibilityChanged(visibility: Int) {
    // Keep audio & JS timers running even when offscreen or app moves to background
    super.onWindowVisibilityChanged(android.view.View.VISIBLE)
  }
}

@Composable
fun YouTubeFullTrackEmbedPlayer(
  videoId: String,
  commands: Flow<YouTubePlayerCommand>,
  onPlaybackUpdate: (isPlaying: Boolean, positionMs: Long, durationMs: Long) -> Unit,
  onEnded: () -> Unit,
  onError: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val appOrigin = remember(context) { buildAppEmbedOrigin(context.packageName) }
  val latestUpdate by rememberUpdatedState(onPlaybackUpdate)
  val latestEnded by rememberUpdatedState(onEnded)
  val latestError by rememberUpdatedState(onError)
  val mainHandler = remember { Handler(Looper.getMainLooper()) }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var loadedInitialId by remember { mutableStateOf<String?>(null) }
  var mobileWatchFallbackActive by remember { mutableStateOf(false) }

  DisposableEffect(Unit) {
    onDispose {
      webViewRef?.apply {
        stopLoading()
        loadUrl("about:blank")
        onPause()
        destroy()
      }
      webViewRef = null
    }
  }

  LaunchedEffect(videoId, webViewRef) {
    val wv = webViewRef ?: return@LaunchedEffect
    val safeVid = sanitizeYouTubeVideoId(videoId)
    if (safeVid.isBlank()) return@LaunchedEffect
    if (loadedInitialId == null) {
      mobileWatchFallbackActive = false
      loadedInitialId = safeVid
      wv.loadDataWithBaseURL(
        appOrigin,
        buildYouTubeIFrameHtml(safeVid, appOrigin),
        "text/html",
        "utf-8",
        null,
      )
    } else if (loadedInitialId != safeVid) {
      loadedInitialId = safeVid
      if (mobileWatchFallbackActive) {
        wv.loadUrl(
          resolveYouTubeInAppFallbackUrl(safeVid),
          mapOf("Referer" to "https://m.youtube.com/"),
        )
      } else {
        wv.evaluateJavascript("if (typeof loadFullVideo === 'function') loadFullVideo('$safeVid', 0);", null)
      }
    }
  }

  LaunchedEffect(commands, webViewRef) {
    commands.collect { cmd ->
      val wv = webViewRef ?: return@collect
      when (cmd) {
        is YouTubePlayerCommand.Load -> {
          val safeVid = sanitizeYouTubeVideoId(cmd.videoId)
          if (safeVid.isNotBlank()) {
            loadedInitialId = safeVid
            if (mobileWatchFallbackActive) {
              wv.loadUrl(
                resolveYouTubeInAppFallbackUrl(safeVid),
                mapOf("Referer" to "https://m.youtube.com/"),
              )
            } else {
              wv.evaluateJavascript(
                "if (typeof loadFullVideo === 'function') loadFullVideo('$safeVid', ${cmd.startSeconds});",
                null,
              )
            }
          }
        }
        YouTubePlayerCommand.Play -> wv.evaluateJavascript("if (typeof playCmd === 'function') playCmd();", null)
        YouTubePlayerCommand.Pause -> wv.evaluateJavascript("if (typeof pauseCmd === 'function') pauseCmd();", null)
        is YouTubePlayerCommand.SeekTo -> {
          val sec = cmd.positionMs / 1000f
          wv.evaluateJavascript("if (typeof seekCmd === 'function') seekCmd($sec);", null)
        }
      }
    }
  }

  AndroidView(
    factory = { ctx ->
      EvoriaBackgroundWebView(ctx).apply {
        val webView = this
        val rawUa = settings.userAgentString.orEmpty()
        settings.userAgentString =
          rawUa.replace("; wv)", ")").replace("Version/4.0 ", "").ifBlank {
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
          }
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().apply {
          setAcceptCookie(true)
          setAcceptThirdPartyCookies(webView, true)
        }
        webViewClient =
          object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
              val url = request?.url?.toString() ?: return false
              if (
                url.startsWith("https://www.youtube.com") ||
                url.startsWith("https://m.youtube.com") ||
                url.startsWith("https://www.youtube-nocookie.com") ||
                url.startsWith("https://music.youtube.com") ||
                url.startsWith("https://youtu.be")
              ) {
                return false
              }
              return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
              super.onPageFinished(view, url)
              if (url != null && url.startsWith("https://m.youtube.com/watch")) {
                view?.evaluateJavascript(buildYouTubeMobileWatchBridgeJs(), null)
              }
            }

            override fun onReceivedError(
              view: WebView?,
              request: WebResourceRequest?,
              error: WebResourceError?,
            ) {
              super.onReceivedError(view, request, error)
              if (request?.isForMainFrame == true) {
                mainHandler.post {
                  val safeId = sanitizeYouTubeVideoId(loadedInitialId ?: videoId)
                  if (!mobileWatchFallbackActive && safeId.isNotBlank()) {
                    mobileWatchFallbackActive = true
                    loadUrl(
                      resolveYouTubeInAppFallbackUrl(safeId),
                      mapOf("Referer" to "https://m.youtube.com/"),
                    )
                  } else {
                    latestError("Gagal memuat audio lagu di dalam aplikasi. Periksa koneksi internet lalu coba putar ulang.")
                  }
                }
              }
            }
          }
        webChromeClient = WebChromeClient()
        addJavascriptInterface(
          object {
            @JavascriptInterface
            fun onProgress(isPlaying: Boolean, posMs: Long, durMs: Long) {
              mainHandler.post { latestUpdate(isPlaying, posMs, durMs) }
            }

            @JavascriptInterface
            fun onEnded() {
              mainHandler.post { latestEnded() }
            }

            @JavascriptInterface
            fun onFallbackNeeded(code: Int) {
              mainHandler.post {
                val safeId = sanitizeYouTubeVideoId(loadedInitialId ?: videoId)
                if (!mobileWatchFallbackActive && safeId.isNotBlank()) {
                  mobileWatchFallbackActive = true
                  loadUrl(
                    resolveYouTubeInAppFallbackUrl(safeId),
                    mapOf("Referer" to "https://m.youtube.com/"),
                  )
                } else if (isYouTubeEmbedFallbackErrorCode(code) && code != 0) {
                  latestError("Koneksi pemutar lagu sedang bermasalah (kode $code). Ketuk putar untuk mencoba ulang.")
                }
              }
            }

            @JavascriptInterface
            fun onError(code: Int) {
              mainHandler.post {
                val safeId = sanitizeYouTubeVideoId(loadedInitialId ?: videoId)
                if (!mobileWatchFallbackActive && safeId.isNotBlank()) {
                  mobileWatchFallbackActive = true
                  loadUrl(
                    resolveYouTubeInAppFallbackUrl(safeId),
                    mapOf("Referer" to "https://m.youtube.com/"),
                  )
                } else {
                  latestError("Pemutar audio internal mengalami kendala (kode $code). Ketuk putar untuk mencoba lagi.")
                }
              }
            }
          },
          "EvoriaYtBridge",
        )
        webViewRef = this
      }
    },
    modifier = modifier,
  )
}

@Composable
fun FullMusicPlayerScreen(
  playerState: MusicPlayerState,
  youtubeCommands: Flow<YouTubePlayerCommand> = emptyFlow(),
  onYouTubePlaybackUpdate: (isPlaying: Boolean, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
  onYouTubeVideoEnded: () -> Unit = {},
  onYouTubePlayerError: (String) -> Unit = {},
  hostPersistentYouTubeOverlay: Boolean = false,
  onYouTubeSlotBounds: (Rect?) -> Unit = {},
  onLeavingYouTubePlayer: () -> Unit = {},
  onTogglePlayPause: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  DisposableEffect(Unit) {
    onDispose {
      onYouTubeSlotBounds(null)
      onLeavingYouTubePlayer()
    }
  }
  val tokens = LocalEvoriaTokens.current
  val track = playerState.currentTrack
  val isYouTubePlayingMode = playerState.isYouTubeMode && !playerState.activeYouTubeVideoId.isNullOrBlank()

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(listOf(Color(0xFF2D2321), SpotifyObsidianBg)))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 14.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = SpotifyTextPrimary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = if (isYouTubePlayingMode) "PEMUTAR AUDIO EVORIA" else "MEMUTAR AUDIO PENUH",
            style = MaterialTheme.typography.labelSmall,
            color = if (isYouTubePlayingMode) SpotifyEmerald else SpotifyTextMuted,
          )
          Text(
            text = if (track?.sourceType == "spotify") "Metadata Spotify + YouTube" else "Koleksi Lokal",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = SpotifyTextPrimary,
          )
        }
        Spacer(modifier = Modifier.size(48.dp))
      }

      Spacer(modifier = Modifier.weight(0.5f))

      // Visible YouTube Full Player Slot OR Square Album Poster Artwork
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = SpotifyCardBg,
        shadowElevation = 18.dp,
        modifier =
          if (isYouTubePlayingMode) Modifier.fillMaxWidth().height(230.dp)
          else Modifier.size(268.dp),
      ) {
        if (isYouTubePlayingMode) {
          if (hostPersistentYouTubeOverlay) {
            Box(
              modifier =
                Modifier
                  .fillMaxSize()
                  .onGloballyPositioned { coords -> onYouTubeSlotBounds(coords.boundsInRoot()) },
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(
                color = SpotifyEmerald,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
              )
            }
          } else {
            YouTubeFullTrackEmbedPlayer(
              videoId = playerState.activeYouTubeVideoId!!,
              commands = youtubeCommands,
              onPlaybackUpdate = onYouTubePlaybackUpdate,
              onEnded = onYouTubeVideoEnded,
              onError = onYouTubePlayerError,
              modifier = Modifier.fillMaxSize(),
            )
          }
        } else {
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(SpotifyHeroTop, SpotifyCardBg))),
            contentAlignment = Alignment.Center,
          ) {
            if (!track?.coverUrl.isNullOrBlank()) {
              val fullCoverReq = rememberThumbnailRequest(track?.coverUrl, sizePx = 640)
              AsyncImage(
                model = fullCoverReq,
                contentDescription = track?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SpotifyEmerald, modifier = Modifier.size(88.dp))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Marquee Full Title only while actively playing to avoid idle display ticks
      val playerTitle = track?.title ?: "Tidak ada lagu dipilih"
      val useTitleMarquee = playerState.isPlaying && playerTitle.length > 22
      Text(
        text = playerTitle,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = SpotifyTextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = !useTitleMarquee,
        overflow = TextOverflow.Ellipsis,
        modifier =
          Modifier
            .fillMaxWidth()
            .then(if (useTitleMarquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = track?.artist ?: "Pilih trek dari playlist",
        style = MaterialTheme.typography.bodyMedium,
        color = SpotifyTextMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      if (!playerState.errorMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(playerState.errorMessage, style = MaterialTheme.typography.bodySmall, color = tokens.roseError, textAlign = TextAlign.Center)
      }

      Spacer(modifier = Modifier.height(18.dp))
      val dur = playerState.durationMs.coerceAtLeast(1L)
      val sliderVal = (playerState.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
      Slider(
        value = sliderVal,
        onValueChange = { ratio -> onSeekTo((ratio * dur).toLong()) },
        colors =
          SliderDefaults.colors(
            thumbColor = SpotifyTextPrimary,
            activeTrackColor = SpotifyTextPrimary,
            inactiveTrackColor = SpotifyTextMuted.copy(alpha = 0.3f),
          ),
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDurationMmSs(playerState.positionMs), style = MaterialTheme.typography.labelSmall, color = SpotifyTextMuted)
        Text(formatDurationMmSs(playerState.durationMs), style = MaterialTheme.typography.labelSmall, color = SpotifyTextMuted)
      }

      Spacer(modifier = Modifier.height(16.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onPrevious, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(Icons.Filled.SkipPrevious, contentDescription = "Sebelumnya", tint = SpotifyTextPrimary, modifier = Modifier.size(38.dp))
        }
        Surface(
          shape = CircleShape,
          color = SpotifyTextPrimary,
          modifier = Modifier.size(68.dp).clickable(onClick = onTogglePlayPause),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              contentDescription = if (playerState.isPlaying) "Jeda" else "Putar",
              tint = Color.Black,
              modifier = Modifier.size(36.dp),
            )
          }
        }
        IconButton(onClick = onNext, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(Icons.Filled.SkipNext, contentDescription = "Berikutnya", tint = SpotifyTextPrimary, modifier = Modifier.size(38.dp))
        }
      }
      Spacer(modifier = Modifier.weight(1f))
    }
  }
}
