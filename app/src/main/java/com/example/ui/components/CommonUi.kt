package com.example.ui.components

import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.media.MusicPlayerState
import com.example.ui.theme.LocalEvoriaTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val idLocale = Locale("id", "ID")
private val indoDateFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd MMM yyyy", idLocale)
private val indoDateTimeFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", idLocale)

fun formatIndoDate(millis: Long): String =
  Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(indoDateFormatter)

fun formatIndoDateTime(millis: Long): String =
  Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(indoDateTimeFormatter)

fun formatDurationMmSs(ms: Long): String {
  val totalSec = (ms / 1000L).coerceAtLeast(0L)
  val min = totalSec / 60L
  val sec = totalSec % 60L
  return "%d:%02d".format(min, sec)
}

/**
 * Builds a size-bounded, hardware-bitmap Coil ImageRequest cached in remember()
 * so Lazy grids and lists never decode multi-megapixel photos on the fly during 60 FPS flings.
 */
@Composable
fun rememberThumbnailRequest(model: Any?, sizePx: Int = 360): Any? {
  val context = LocalContext.current
  return remember(model, sizePx, context) {
    val valid =
      when (model) {
        null -> false
        is String -> model.isNotBlank()
        else -> true
      }
    if (!valid) null
    else {
      ImageRequest.Builder(context)
        .data(model)
        .size(sizePx)
        .precision(Precision.INEXACT)
        .allowHardware(true)
        .crossfade(false)
        .build()
    }
  }
}

@Composable
fun EvoriaCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  elevated: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  val containerColor = if (elevated) tokens.elevatedCard else tokens.cardSurface
  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    shape = RoundedCornerShape(tokens.cardRadius),
    color = containerColor,
    tonalElevation = if (elevated) 2.dp else 0.dp,
    shadowElevation = if (elevated) 2.dp else 0.dp,
    border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(18.dp), content = content)
  }
}

@Composable
fun EvoriaTopBar(
  title: String,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  trailingAction: (@Composable () -> Unit)? = null,
) {
  val tokens = LocalEvoriaTokens.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (onBack != null) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Kembali",
          tint = tokens.inkText,
        )
      }
      Spacer(modifier = Modifier.width(6.dp))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = tokens.inkText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = tokens.mutedText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (trailingAction != null) {
      trailingAction()
    }
  }
}

@Composable
fun VisibilityBadge(visibility: String) {
  val tokens = LocalEvoriaTokens.current
  val isShared = visibility == "shared"
  val bg = if (isShared) tokens.accentPeach.copy(alpha = 0.28f) else tokens.outlineSoft.copy(alpha = 0.35f)
  val fg = if (isShared) tokens.primaryTerracotta else tokens.mutedText
  Surface(
    shape = RoundedCornerShape(50),
    color = bg,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = if (isShared) Icons.Filled.People else Icons.Filled.Lock,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(13.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = if (isShared) "Bersama" else "Pribadi",
        style = MaterialTheme.typography.labelSmall,
        color = fg,
      )
    }
  }
}

@Composable
fun EvoriaActionChip(
  label: String,
  icon: ImageVector,
  selected: Boolean = false,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalEvoriaTokens.current
  Surface(
    modifier = modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    shape = RoundedCornerShape(50),
    color = if (selected) tokens.primaryTerracotta else tokens.cardSurface,
    border = BorderStroke(1.dp, if (selected) tokens.primaryTerracotta else tokens.outlineSoft),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.onPrimary else tokens.inkText,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else tokens.inkText,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun EvoriaMiniPlayerBar(
  playerState: MusicPlayerState,
  onTogglePlay: () -> Unit,
  onNext: () -> Unit,
  onOpenFullPlayer: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val track = playerState.currentTrack ?: return
  val tokens = LocalEvoriaTokens.current
  Surface(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onOpenFullPlayer),
    shape = RoundedCornerShape(20.dp),
    color = tokens.elevatedCard,
    tonalElevation = 4.dp,
    shadowElevation = 6.dp,
    border = BorderStroke(1.dp, tokens.outlineSoft),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(tokens.accentPeach.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
      ) {
        if (!track.coverUrl.isNullOrBlank()) {
          AsyncImage(
            model = rememberThumbnailRequest(track.coverUrl, sizePx = 140),
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = tokens.primaryTerracotta,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        val runMarquee = playerState.isPlaying && track.title.length > 20
        Text(
          text = track.title,
          style = MaterialTheme.typography.titleSmall,
          color = tokens.inkText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = if (runMarquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
        )
        Text(
          text = track.artist,
          style = MaterialTheme.typography.bodySmall,
          color = tokens.mutedText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onTogglePlay, modifier = Modifier.minimumInteractiveComponentSize()) {
        Icon(
          imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (playerState.isPlaying) "Jeda musik" else "Putar musik",
          tint = Color(0xFF1DB954),
        )
      }
      IconButton(onClick = onNext, modifier = Modifier.minimumInteractiveComponentSize()) {
        Icon(
          imageVector = Icons.Filled.SkipNext,
          contentDescription = "Trek berikutnya",
          tint = tokens.inkText,
        )
      }
    }
  }
}

@Composable
fun EvoriaPillSearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onSearch: () -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "Search...",
  inputTestTag: String? = null,
  submitTestTag: String? = null,
  darkSpotifyStyle: Boolean = false,
) {
  val tokens = LocalEvoriaTokens.current
  val pillGradientBrush =
    remember(darkSpotifyStyle, tokens.isDark, tokens.elevatedCard, tokens.cardSurface, tokens.secondarySurface) {
      val colors =
        when {
          darkSpotifyStyle -> listOf(Color(0xFF1F1F24), Color(0xFF25252B), Color(0xFF2B2B32))
          tokens.isDark -> listOf(tokens.elevatedCard, tokens.cardSurface, tokens.secondarySurface)
          else -> listOf(tokens.elevatedCard, tokens.secondarySurface, tokens.cardSurface)
        }
      Brush.horizontalGradient(colors)
    }
  val rimBorderColor =
    when {
      darkSpotifyStyle -> Color(0xFF1DB954).copy(alpha = 0.42f)
      tokens.isDark -> tokens.outlineSoft.copy(alpha = 0.85f)
      else -> tokens.primaryTerracotta.copy(alpha = 0.28f)
    }
  val inputInk = if (darkSpotifyStyle) Color.White else tokens.inkText
  val placeholderInk = if (darkSpotifyStyle) Color(0xFFB3B3B3) else tokens.mutedText
  val cursorColor = if (darkSpotifyStyle) Color(0xFF1DB954) else tokens.primaryTerracotta
  val actionButtonColor = if (darkSpotifyStyle) Color(0xFF1DB954) else tokens.primaryTerracotta
  val actionIconColor = if (darkSpotifyStyle) Color.Black else MaterialTheme.colorScheme.onPrimary

  Surface(
    modifier = modifier.fillMaxWidth().height(54.dp),
    shape = RoundedCornerShape(50),
    color = Color.Transparent,
    tonalElevation = 0.dp,
    shadowElevation = if (tokens.isDark || darkSpotifyStyle) 4.dp else 5.dp,
    border = BorderStroke(1.dp, rimBorderColor),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxSize()
          .background(pillGradientBrush)
          .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterStart,
      ) {
        if (query.isEmpty()) {
          Text(
            text = placeholder,
            style =
              MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
              ),
            color = placeholderInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle =
            MaterialTheme.typography.bodyLarge.copy(
              color = inputInk,
              fontWeight = FontWeight.Medium,
            ),
          cursorBrush = SolidColor(cursorColor),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSearch() }),
          modifier =
            Modifier
              .fillMaxWidth()
              .then(if (inputTestTag != null) Modifier.testTag(inputTestTag) else Modifier),
        )
      }

      if (query.isNotEmpty()) {
        IconButton(
          onClick = { onQueryChange("") },
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Hapus kata kunci",
            tint = placeholderInk,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(4.dp))

      Surface(
        shape = CircleShape,
        color = actionButtonColor,
        shadowElevation = 4.dp,
        modifier =
          Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onSearch)
            .semantics {
              role = Role.Button
              contentDescription = "Cari sekarang"
            }
            .then(if (submitTestTag != null) Modifier.testTag(submitTestTag) else Modifier),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = actionIconColor,
            modifier = Modifier.size(21.dp),
          )
        }
      }
    }
  }
}

@Composable
fun evoriaFormTextFieldColors(
  isDarkSpotify: Boolean = false,
): TextFieldColors {
  val tokens = LocalEvoriaTokens.current
  val accent = if (isDarkSpotify) Color(0xFF1DB954) else tokens.primaryTerracotta
  val container =
    if (isDarkSpotify) Color(0xFF1E1E24)
    else tokens.secondarySurface.copy(alpha = if (tokens.isDark) 0.72f else 0.55f)
  val borderUnfocused =
    if (isDarkSpotify) Color(0xFF34343B)
    else tokens.outlineSoft.copy(alpha = 0.85f)
  val textInk = if (isDarkSpotify) Color.White else tokens.inkText
  val hintInk = if (isDarkSpotify) Color(0xFFB3B3B3) else tokens.mutedText

  return OutlinedTextFieldDefaults.colors(
    focusedTextColor = textInk,
    unfocusedTextColor = textInk,
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    focusedBorderColor = accent,
    unfocusedBorderColor = borderUnfocused,
    focusedLabelColor = accent,
    unfocusedLabelColor = hintInk,
    cursorColor = accent,
    focusedPlaceholderColor = hintInk,
    unfocusedPlaceholderColor = hintInk,
    focusedLeadingIconColor = accent,
    unfocusedLeadingIconColor = hintInk,
    focusedTrailingIconColor = accent,
    unfocusedTrailingIconColor = hintInk,
  )
}

@Composable
fun EvoriaFormTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  leadingIcon: ImageVector? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  singleLine: Boolean = true,
  minLines: Int = 1,
  maxLines: Int = if (singleLine) 1 else 6,
  enabled: Boolean = true,
  isDarkSpotify: Boolean = false,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    placeholder = placeholder?.let { { Text(it) } },
    leadingIcon =
      leadingIcon?.let { icon ->
        { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(19.dp)) }
      },
    trailingIcon = trailingIcon,
    singleLine = singleLine,
    minLines = minLines,
    maxLines = maxLines,
    enabled = enabled,
    shape = RoundedCornerShape(16.dp),
    colors = evoriaFormTextFieldColors(isDarkSpotify = isDarkSpotify),
    modifier = modifier.fillMaxWidth(),
  )
}

@Composable
fun EvoriaSymmetricPrivacyRow(
  visibility: String,
  onVisibilityChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  privateTestTag: String? = null,
  sharedTestTag: String? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    EvoriaActionChip(
      label = "Pribadi",
      icon = Icons.Filled.Lock,
      selected = visibility == "private",
      onClick = { onVisibilityChange("private") },
      modifier =
        Modifier
          .weight(1f)
          .then(if (privateTestTag != null) Modifier.testTag(privateTestTag) else Modifier),
    )
    EvoriaActionChip(
      label = "Bersama",
      icon = Icons.Filled.People,
      selected = visibility == "shared",
      onClick = { onVisibilityChange("shared") },
      modifier =
        Modifier
          .weight(1f)
          .then(if (sharedTestTag != null) Modifier.testTag(sharedTestTag) else Modifier),
    )
  }
}

@Composable
fun EvoriaSymmetricDialogShell(
  title: String,
  subtitle: String? = null,
  icon: ImageVector,
  onDismiss: () -> Unit,
  confirmLabel: String,
  onConfirm: () -> Unit,
  confirmEnabled: Boolean = true,
  confirmTestTag: String? = null,
  dismissLabel: String = "Batal",
  dismissEnabled: Boolean = true,
  secondaryActionLabel: String? = null,
  onSecondaryAction: (() -> Unit)? = null,
  accentColor: Color? = null,
  confirmContentColor: Color = Color.White,
  content: @Composable ColumnScope.() -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  val primary = accentColor ?: tokens.primaryTerracotta

  Dialog(
    onDismissRequest = { if (dismissEnabled) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 18.dp, vertical = 20.dp)
          .widthIn(max = 520.dp)
          .heightIn(max = 760.dp),
      shape = RoundedCornerShape(28.dp),
      color = tokens.background,
      tonalElevation = 6.dp,
      shadowElevation = 14.dp,
      border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.72f)),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Editorial symmetric header
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .background(
                Brush.horizontalGradient(
                  listOf(
                    primary.copy(alpha = if (tokens.isDark) 0.24f else 0.14f),
                    tokens.accentPeach.copy(alpha = if (tokens.isDark) 0.14f else 0.24f),
                  )
                )
              )
              .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = primary,
              shadowElevation = 4.dp,
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = confirmContentColor,
                modifier = Modifier.padding(10.dp).size(20.dp),
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = tokens.inkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (!subtitle.isNullOrBlank()) {
                Text(
                  text = subtitle,
                  style = MaterialTheme.typography.labelSmall,
                  color = tokens.mutedText,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            IconButton(
              onClick = { if (dismissEnabled) onDismiss() },
              enabled = dismissEnabled,
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Tutup dialog", tint = tokens.inkText)
            }
          }
        }

        // Balanced scrollable form content
        Column(
          modifier =
            Modifier
              .weight(1f, fill = false)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 20.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          content = content,
        )

        // Symmetric pill action footer
        Surface(
          color = tokens.cardSurface,
          tonalElevation = 4.dp,
          border = BorderStroke(1.dp, tokens.outlineSoft.copy(alpha = 0.55f)),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (!secondaryActionLabel.isNullOrBlank() && onSecondaryAction != null) {
              OutlinedButton(
                onClick = onSecondaryAction,
                enabled = dismissEnabled,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                Text(secondaryActionLabel, maxLines = 1, color = tokens.inkText)
              }
            } else {
              OutlinedButton(
                onClick = onDismiss,
                enabled = dismissEnabled,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                Text(dismissLabel, maxLines = 1, color = tokens.inkText)
              }
            }
            Button(
              onClick = onConfirm,
              enabled = confirmEnabled,
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = confirmContentColor),
              modifier =
                Modifier
                  .weight(1.2f)
                  .height(48.dp)
                  .then(if (confirmTestTag != null) Modifier.testTag(confirmTestTag) else Modifier),
            ) {
              Text(confirmLabel, maxLines = 1, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
  }
}

/**
 * Muted, looping ExoPlayer backdrop surface with lifecycle-safe pause/resume/release
 * and Robolectric/preview fallback safety.
 */
@Composable
fun EvoriaLoopingVideoSurface(
  videoUri: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val isInspection = LocalInspectionMode.current
  val isRobolectric = remember { Build.FINGERPRINT.equals("robolectric", ignoreCase = true) }

  if (isInspection || isRobolectric || videoUri.isBlank()) {
    Box(
      modifier =
        modifier.background(
          Brush.linearGradient(
            listOf(Color(0xFF281D36), Color(0xFF3E264D), Color(0xFF1F1629))
          )
        )
    )
    return
  }

  val exoPlayer =
    remember(videoUri) {
      runCatching {
        val loadControl =
          DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 4000, 400, 800)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
          .setLoadControl(loadControl)
          .build()
          .apply {
            trackSelectionParameters =
              trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            prepare()
            playWhenReady = true
          }
      }.getOrNull()
    }

  if (exoPlayer == null) {
    Box(modifier = modifier.background(Color(0xFF251B30)))
    return
  }

  DisposableEffect(lifecycleOwner, exoPlayer) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
          exoPlayer.playWhenReady = true
        }
        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
          exoPlayer.playWhenReady = false
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      exoPlayer.release()
    }
  }

  AndroidView(
    factory = { ctx ->
      PlayerView(ctx).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        player = exoPlayer
      }
    },
    update = { view ->
      if (view.player !== exoPlayer) {
        view.player = exoPlayer
      }
    },
    modifier = modifier,
  )
}
