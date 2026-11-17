package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.data.local.AppUserPrefs
import com.example.data.local.AutoLockTimeout
import com.example.data.local.DEVELOPER_ADMIN_EMAIL
import com.example.data.local.EvoriaAccentPreset
import com.example.data.local.LocalNotificationEntity
import com.example.data.local.LockMethod
import com.example.data.local.NavbarGlassIntensity
import com.example.data.local.NavbarThemeStyle
import com.example.data.local.PinVerifyResult
import com.example.data.local.SecuritySettings
import com.example.data.local.SyncCheckpointEntity
import com.example.data.local.ThemeMode
import com.example.data.local.canEditDeveloperSocials
import com.example.data.remote.CoupleConnectionDto
import com.example.data.remote.GeneratedInvitation
import com.example.data.remote.InvitationDto
import com.example.data.remote.NotificationItemDto
import com.example.data.remote.SharedContentRequestDto
import com.example.data.remote.UserProfileDto
import com.example.security.BiometricHelper
import com.example.ui.components.EvoriaCard
import com.example.ui.components.EvoriaFormTextField
import com.example.ui.components.EvoriaMainTabs
import com.example.ui.components.EvoriaSanctuaryBackdrop
import com.example.ui.components.EvoriaSymmetricDialogShell
import com.example.ui.components.EvoriaTopBar
import com.example.ui.components.LiquidGlassInteractionState
import com.example.ui.components.LiquidGlassNavbar
import com.example.ui.components.LiquidGlassTelemetry
import com.example.ui.components.formatIndoDateTime
import com.example.ui.components.rememberThumbnailRequest
import com.example.ui.theme.EvoriaTheme
import com.example.ui.theme.LocalEvoriaTokens
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilScreen(
  currentUid: String,
  profile: UserProfileDto?,
  fallbackName: String,
  couple: CoupleConnectionDto?,
  albumCount: Int,
  memoryCount: Int,
  journalCount: Int,
  capsuleCount: Int,
  pendingShareRequestsCount: Int,
  unreadNotificationCount: Int,
  localStorageLabel: String,
  userPrefs: AppUserPrefs,
  securitySettings: SecuritySettings,
  onSetThemeMode: (ThemeMode) -> Unit,
  onSetAccentPreset: (EvoriaAccentPreset) -> Unit = {},
  onSetNavbarThemeStyle: (NavbarThemeStyle) -> Unit = {},
  onSetNavbarGlassIntensity: (NavbarGlassIntensity) -> Unit = {},
  onSetReducedMotion: (Boolean) -> Unit,
  onSetLock60Fps: (Boolean) -> Unit = {},
  onSaveProfile: (displayName: String, partnerNickname: String, avatarUri: Uri?) -> Unit = { _, _, _ -> },
  onDeleteAllLocalData: () -> Unit = {},
  onOpenConnectPartner: () -> Unit,
  onOpenShareInbox: () -> Unit,
  onOpenNotifications: () -> Unit,
  onOpenMusic: () -> Unit,
  onOpenUploadQueue: () -> Unit,
  onOpenSecurity: () -> Unit,
  onOpenBackupStorage: () -> Unit,
  onOpenLiquidGlassShowcase: () -> Unit,
  onOpenAbout: () -> Unit = {},
  onSignOut: () -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  val displayName = profile?.displayName?.ifBlank { fallbackName } ?: fallbackName
  val partnerName =
    profile?.partnerDisplayName
      ?: couple?.let { if (it.memberOneUid == currentUid) it.memberTwoName else it.memberOneName }

  val avatarPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        onSaveProfile(displayName, profile?.partnerNickname.orEmpty(), uri)
      }
    }

  var showEditProfileDialog by remember { mutableStateOf(false) }
  var confirmWipeAllData by remember { mutableStateOf(false) }
  var miniPreviewTab by remember { mutableIntStateOf(4) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("profil_lazy_column"),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Text("Profil & Pengaturan", style = MaterialTheme.typography.displayMedium, color = tokens.inkText)
      Text("Kelola ruang berdua, privasi, keamanan & tampilan.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
    }

    // Identity + Partner Card with Avatar Upload & Rename
    item {
      EvoriaCard(elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier =
              Modifier
                .size(68.dp)
                .clickable {
                  avatarPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                }
                .testTag("profil_avatar_picker"),
          ) {
            Box(
              modifier =
                Modifier
                  .fillMaxSize()
                  .clip(CircleShape)
                  .background(tokens.accentPeach.copy(alpha = 0.35f))
                  .border(1.5.dp, tokens.primaryTerracotta.copy(alpha = 0.45f), CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              if (!profile?.avatarUrl.isNullOrBlank()) {
                val avatarReq = rememberThumbnailRequest(profile?.avatarUrl, sizePx = 180)
                AsyncImage(
                  model = avatarReq,
                  contentDescription = "Foto profil $displayName",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
                )
              } else {
                Text(
                  text = displayName.take(1).uppercase(),
                  style = MaterialTheme.typography.headlineMedium,
                  color = tokens.primaryTerracotta,
                )
              }
            }
            Surface(
              color = tokens.primaryTerracotta,
              shape = CircleShape,
              border = BorderStroke(1.5.dp, tokens.cardSurface),
              modifier = Modifier.size(24.dp).align(Alignment.BottomEnd),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Filled.CameraAlt,
                  contentDescription = "Unggah foto profil",
                  tint = Color.White,
                  modifier = Modifier.size(13.dp),
                )
              }
            }
          }

          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              displayName,
              style = MaterialTheme.typography.titleLarge,
              color = tokens.inkText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (!profile?.partnerNickname.isNullOrBlank()) {
              Text(
                "Sapaan untuk pasangan: ${profile?.partnerNickname}",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.mutedText,
              )
            }
            Text(
              text = if (couple != null) "Berdua bersama ${partnerName ?: "Pasangan"}" else "Mode Pribadi • Belum terhubung",
              style = MaterialTheme.typography.bodySmall,
              color = if (couple != null) tokens.primaryTerracotta else tokens.mutedText,
            )
          }
          IconButton(
            onClick = { showEditProfileDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("profil_edit_button"),
          ) {
            Icon(Icons.Filled.Edit, contentDescription = "Ubah nama & foto profil", tint = tokens.primaryTerracotta)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = {
              avatarPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f).height(40.dp).testTag("profil_upload_photo_button"),
          ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Ganti Foto", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          OutlinedButton(
            onClick = { showEditProfileDialog = true },
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f).height(40.dp).testTag("profil_rename_button"),
          ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Ganti Nama", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          ProfileStatCol("Album", "$albumCount")
          ProfileStatCol("Kenangan", "$memoryCount")
          ProfileStatCol("Jurnal", "$journalCount")
          ProfileStatCol("Kapsul", "$capsuleCount")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("Penyimpanan media lokal: $localStorageLabel", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
      }
    }

    // Expanded Theme EVORIA & Liquid Glass Navbar Card
    item {
      EvoriaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Palette, contentDescription = null, tint = tokens.primaryTerracotta)
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text("Tema EVORIA & Liquid Navbar", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
            Text("Sesuaikan atmosfer warna, palet aksen & optik bilah navigasi.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
          }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text("Mode Kanvas", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
        Spacer(modifier = Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          ThemeMode.entries.forEach { mode ->
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { onSetThemeMode(mode) }
                  .padding(vertical = 8.dp, horizontal = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(mode.labelId, style = MaterialTheme.typography.bodyMedium, color = tokens.inkText)
              if (userPrefs.themeMode == mode) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Dipilih", tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
              }
            }
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = tokens.outlineSoft)

        Text("Aksen Warna EVORIA", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          EvoriaAccentPreset.entries.forEach { preset ->
            val dotColor = Color(if (tokens.isDark) preset.darkPrimaryHex else preset.lightPrimaryHex)
            FilterChip(
              selected = userPrefs.accentPreset == preset,
              onClick = { onSetAccentPreset(preset) },
              label = { Text(preset.labelId) },
              leadingIcon = {
                Box(
                  modifier =
                    Modifier
                      .size(12.dp)
                      .clip(CircleShape)
                      .background(dotColor)
                      .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                )
              },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = dotColor.copy(alpha = 0.22f),
                  selectedLabelColor = tokens.inkText,
                ),
              modifier = Modifier.testTag("tema_accent_${preset.name.lowercase()}"),
            )
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = tokens.outlineSoft)

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Tune, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Gaya Tema Navbar", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          NavbarThemeStyle.entries.forEach { style ->
            val selected = userPrefs.navbarThemeStyle == style
            Surface(
              shape = RoundedCornerShape(14.dp),
              color =
                if (selected) tokens.primaryTerracotta.copy(alpha = 0.14f)
                else tokens.secondarySurface.copy(alpha = 0.45f),
              border =
                BorderStroke(
                  1.dp,
                  if (selected) tokens.primaryTerracotta.copy(alpha = 0.55f) else tokens.outlineSoft,
                ),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { onSetNavbarThemeStyle(style) }
                  .testTag("navbar_theme_${style.name.lowercase()}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(style.labelId, style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
                  Text(style.descId, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
                }
                if (selected) {
                  Icon(Icons.Filled.CheckCircle, contentDescription = "Dipilih", tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Intensitas Kilau & Dispersi Kaca Navbar", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          NavbarGlassIntensity.entries.forEach { intensity ->
            FilterChip(
              selected = userPrefs.navbarGlassIntensity == intensity,
              onClick = { onSetNavbarGlassIntensity(intensity) },
              label = { Text(intensity.labelId, maxLines = 1, overflow = TextOverflow.Ellipsis) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = tokens.primaryTerracotta.copy(alpha = 0.22f),
                  selectedLabelColor = tokens.inkText,
                ),
              modifier = Modifier.weight(1f).testTag("navbar_intensity_${intensity.name.lowercase()}"),
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Pratinjau Langsung Navbar:", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
        Spacer(modifier = Modifier.height(6.dp))
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(
                Brush.linearGradient(
                  if (tokens.isDark) listOf(Color(0xFF28201E), Color(0xFF171316))
                  else listOf(tokens.secondarySurface, tokens.accentPeach.copy(alpha = 0.45f))
                )
              )
              .padding(vertical = 8.dp),
        ) {
          LiquidGlassNavbar(
            selectedTabIndex = miniPreviewTab,
            onTabSelected = { miniPreviewTab = it },
            reducedMotionOverride = userPrefs.reducedMotionOverride,
            navbarThemeStyle = userPrefs.navbarThemeStyle,
            navbarGlassIntensity = userPrefs.navbarGlassIntensity,
            applyNavigationBarInsets = false,
            modifier = Modifier.testTag("profil_navbar_live_preview"),
          )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Kurangi Gerakan (Reduced Motion)", style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
            Text("Tenangkan transisi kapsul & efek bloom.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
          }
          Switch(checked = userPrefs.reducedMotionOverride, onCheckedChange = onSetReducedMotion)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Kunci 60 FPS (Performa Mulus)", style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
            Text("Kunci refresh rate 60 Hz agar guliran & animasi tetap stabil.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
          }
          Switch(
            checked = userPrefs.lock60Fps,
            onCheckedChange = onSetLock60Fps,
            modifier = Modifier.testTag("profil_lock_60fps_switch"),
          )
        }
      }
    }

    // Menu Actions
    item {
      EvoriaCard {
        ProfileMenuItem(
          icon = Icons.Filled.People,
          title = "Hubungkan Pasangan",
          subtitle = if (couple != null) "Terkoneksi dengan ${partnerName ?: "Pasangan"}" else "Buat kode 6 karakter / pindai undangan",
          onClick = onOpenConnectPartner,
          testTag = "profil_connect_partner",
        )
        ProfileMenuItem(
          icon = Icons.Filled.Inbox,
          title = "Kotak Masuk EVORIA Share",
          subtitle = "Permintaan berbagi foto/album dengan pasangan",
          badgeCount = pendingShareRequestsCount,
          onClick = onOpenShareInbox,
        )
        ProfileMenuItem(
          icon = Icons.Filled.Notifications,
          title = "Pusat Notifikasi & Preferensi",
          subtitle = "Aktivitas undangan, share, kapsul & keamanan",
          badgeCount = unreadNotificationCount,
          onClick = onOpenNotifications,
        )
        ProfileMenuItem(
          icon = Icons.Filled.MusicNote,
          title = "Musik & Soundtrack Kita",
          subtitle = "Playlist lagu & impor Spotify bergaya gelap",
          onClick = onOpenMusic,
        )
        ProfileMenuItem(
          icon = Icons.Filled.CloudUpload,
          title = "Antrean Unggahan",
          subtitle = "Status pemrosesan & pemulihan otomatis",
          onClick = onOpenUploadQueue,
        )
        ProfileMenuItem(
          icon = Icons.Filled.Security,
          title = "Keamanan & Kunci Aplikasi",
          subtitle = if (securitySettings.appLockEnabled) "Aktif (${securitySettings.preferredMethod.labelId})" else "PIN 6-digit & Biometrik belum aktif",
          onClick = onOpenSecurity,
          testTag = "profil_security_button",
        )
        ProfileMenuItem(
          icon = Icons.Filled.Backup,
          title = "Cadangan, Ekspor & Data Lokal",
          subtitle = "Cadangkan metadata ke Firestore atau ekspor JSON",
          onClick = onOpenBackupStorage,
        )
        ProfileMenuItem(
          icon = Icons.Filled.TouchApp,
          title = "Showcase Liquid Glass Navbar",
          subtitle =
            if (userPrefs.quickOpenShowcaseOnLaunch) {
              "Buka Cepat Aktif • Uji gestur, optik & responsivitas"
            } else {
              "Uji tap, tekan-tahan, geser elastis & dispersi kromatik"
            },
          onClick = onOpenLiquidGlassShowcase,
          testTag = "profil_liquid_glass_showcase",
        )
        ProfileMenuItem(
          icon = Icons.Filled.Info,
          title = "Tentang EVORIA",
          subtitle = "Developer RAFFA AULIA MUSYAWAL • Versi ${BuildConfig.VERSION_NAME}",
          onClick = onOpenAbout,
          testTag = "profil_about_button",
        )
      }
    }

    // Delete All Data + Sign Out Actions
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = { confirmWipeAllData = true },
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, tokens.roseError.copy(alpha = 0.75f)),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("profil_delete_all_data_button"),
        ) {
          Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = tokens.roseError, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Hapus Seluruh Data Perangkat", color = tokens.roseError)
        }

        OutlinedButton(
          onClick = onSignOut,
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, tokens.outlineSoft),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = tokens.inkText, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Keluar dari Akun Google", color = tokens.inkText)
        }
      }
    }
  }

  if (showEditProfileDialog) {
    EditProfileDialog(
      initialName = displayName,
      initialPartnerNickname = profile?.partnerNickname.orEmpty(),
      currentAvatarUrl = profile?.avatarUrl,
      onDismiss = { showEditProfileDialog = false },
      onSave = { newName, newNickname, newAvatarUri ->
        onSaveProfile(newName, newNickname, newAvatarUri)
        showEditProfileDialog = false
      },
    )
  }

  if (confirmWipeAllData) {
    AlertDialog(
      onDismissRequest = { confirmWipeAllData = false },
      title = { Text("Hapus Seluruh Data?") },
      text = {
        Text(
          "Semua kenangan, album, jurnal, surat, suasana hati, kapsul waktu, dan playlist yang tersimpan di perangkat ini akan dihapus permanen."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            confirmWipeAllData = false
            onDeleteAllLocalData()
          },
          colors = ButtonDefaults.buttonColors(containerColor = tokens.roseError),
          modifier = Modifier.testTag("profil_confirm_delete_all_button"),
        ) {
          Text("Ya, Hapus Semua")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmWipeAllData = false }) { Text("Batal") }
      },
    )
  }
}

@Composable
private fun EditProfileDialog(
  initialName: String,
  initialPartnerNickname: String,
  currentAvatarUrl: String?,
  onDismiss: () -> Unit,
  onSave: (displayName: String, partnerNickname: String, avatarUri: Uri?) -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  var nameInput by remember(initialName) { mutableStateOf(initialName) }
  var nicknameInput by remember(initialPartnerNickname) { mutableStateOf(initialPartnerNickname) }
  var pickedAvatarUri by remember { mutableStateOf<Uri?>(null) }

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) pickedAvatarUri = uri
    }

  EvoriaSymmetricDialogShell(
    title = "Edit Profil EVORIA",
    subtitle = "Foto profil dan nama panggilan kalian",
    icon = Icons.Filled.Edit,
    onDismiss = onDismiss,
    confirmLabel = "Simpan",
    confirmEnabled = nameInput.trim().isNotEmpty(),
    confirmTestTag = "edit_profile_save_button",
    onConfirm = { onSave(nameInput.trim(), nicknameInput.trim(), pickedAvatarUri) },
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      val previewModel: Any? = pickedAvatarUri ?: currentAvatarUrl
      Box(
        modifier =
          Modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(tokens.accentPeach.copy(alpha = 0.35f))
            .border(1.5.dp, tokens.primaryTerracotta.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        if (previewModel != null && previewModel.toString().isNotBlank()) {
          AsyncImage(
            model = previewModel,
            contentDescription = "Pratinjau foto profil",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Text(
            nameInput.ifBlank { "E" }.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = tokens.primaryTerracotta,
          )
        }
      }
      OutlinedButton(
        onClick = {
          picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, tokens.primaryTerracotta.copy(alpha = 0.5f)),
        modifier = Modifier.weight(1f).height(48.dp),
      ) {
        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (pickedAvatarUri != null) "Foto Terpilih" else "Ganti Foto Profil", color = tokens.primaryTerracotta)
      }
    }
    EvoriaFormTextField(
      value = nameInput,
      onValueChange = { nameInput = it.take(80) },
      label = "Nama Tampilan",
      singleLine = true,
      modifier = Modifier.testTag("edit_profile_name_input"),
    )
    EvoriaFormTextField(
      value = nicknameInput,
      onValueChange = { nicknameInput = it.take(60) },
      label = "Nama Panggilan Pasangan (opsional)",
      singleLine = true,
    )
  }
}

@Composable
private fun ProfileStatCol(label: String, value: String) {
  val tokens = LocalEvoriaTokens.current
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleLarge, color = tokens.primaryTerracotta)
    Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
  }
}

@Composable
private fun ProfileMenuItem(
  icon: ImageVector,
  title: String,
  subtitle: String,
  badgeCount: Int = 0,
  testTag: String? = null,
  onClick: () -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .padding(vertical = 12.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(22.dp))
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
    }
    if (badgeCount > 0) {
      Badge(containerColor = tokens.primaryTerracotta, contentColor = Color.White) {
        Text("$badgeCount")
      }
    }
  }
}

// --- COUPLE CONNECTION FLOWS ---

@Composable
fun PartnerConnectionScreen(
  currentUid: String,
  couple: CoupleConnectionDto?,
  generatedInvitation: GeneratedInvitation?,
  myInvitations: List<InvitationDto>,
  lookedUpInvitation: InvitationDto?,
  inviteLoading: Boolean,
  inviteError: String?,
  initialCodeFromDeepLink: String?,
  onGenerateCode: () -> Unit,
  onCancelInvitation: (String) -> Unit,
  onLookupCode: (String) -> Unit,
  onAcceptInvitation: (InvitationDto) -> Unit,
  onDeclineInvitation: (InvitationDto) -> Unit,
  onClearLookup: () -> Unit,
  onDisconnectCouple: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val tokens = LocalEvoriaTokens.current
  var codeInput by remember(initialCodeFromDeepLink) { mutableStateOf(initialCodeFromDeepLink.orEmpty()) }
  var confirmDisconnect by remember { mutableStateOf(false) }

  LaunchedEffect(initialCodeFromDeepLink) {
    if (!initialCodeFromDeepLink.isNullOrBlank() && couple == null) {
      onLookupCode(initialCodeFromDeepLink)
    }
  }

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(
        title = "Hubungkan Pasangan",
        subtitle = "Tautan privat 1-lawan-1",
        onBack = onBack,
      )

      Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (couple != null) {
          val isOne = couple.memberOneUid == currentUid
          val partnerName = if (isOne) couple.memberTwoName else couple.memberOneName
          EvoriaCard(elevated = true) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tokens.sageSuccess, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Ruang Berdua Aktif", style = MaterialTheme.typography.headlineMedium, color = tokens.inkText)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Terhubung dengan $partnerName", style = MaterialTheme.typography.titleMedium, color = tokens.primaryTerracotta)
            Text(couple.anniversaryLabel ?: "Setiap kenangan bersama kini dapat dibagikan melalui EVORIA Share.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
              onClick = { confirmDisconnect = true },
              border = BorderStroke(1.dp, tokens.roseError),
              shape = RoundedCornerShape(50),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Putuskan Koneksi Pasangan", color = tokens.roseError)
            }
          }
        } else {
          // Option A: Generate Invitation Code & QR
          EvoriaCard(elevated = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.QrCode2, contentDescription = null, tint = tokens.primaryTerracotta)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Buat Kode Undangan", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              "Kode 6 karakter berlaku 24 jam, sekali pakai, dan disimpan dalam bentuk hash SHA-256.",
              style = MaterialTheme.typography.bodySmall,
              color = tokens.mutedText,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (generatedInvitation != null) {
              val qrBitmap = remember(generatedInvitation.rawCode) {
                QrCodeGenerator.generateQrBitmap("evoria://invite?code=${generatedInvitation.rawCode}", 420)
              }
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Surface(
                  shape = RoundedCornerShape(18.dp),
                  color = Color.White,
                  shadowElevation = 2.dp,
                ) {
                  if (qrBitmap != null) {
                    Image(
                      bitmap = qrBitmap.asImageBitmap(),
                      contentDescription = "QR Undangan EVORIA",
                      modifier = Modifier.size(168.dp).padding(10.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = generatedInvitation.rawCode,
                  style = MaterialTheme.typography.displayMedium,
                  color = tokens.primaryTerracotta,
                  letterSpacing = MaterialTheme.typography.displayMedium.letterSpacing,
                )
                Text(
                  text = "Berlaku hingga ${formatIndoDateTime(generatedInvitation.expiresAtMillis)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = tokens.mutedText,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  OutlinedButton(
                    onClick = {
                      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      cm.setPrimaryClip(ClipData.newPlainText("Kode EVORIA", generatedInvitation.rawCode))
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salin")
                  }
                  Button(
                    onClick = {
                      val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                          Intent.EXTRA_TEXT,
                          "Ayo terhubung denganku di EVORIA. Masukkan kode undangan ${generatedInvitation.rawCode} atau buka tautan evoria://invite?code=${generatedInvitation.rawCode}",
                        )
                      }
                      context.startActivity(Intent.createChooser(shareIntent, "Bagikan Kode EVORIA"))
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan")
                  }
                }
              }
            } else {
              Button(
                onClick = onGenerateCode,
                enabled = !inviteLoading,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_invite_code_button"),
              ) {
                if (inviteLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Buat Kode Undangan 6 Karakter")
              }
            }
          }

          // Option B: Enter Partner Code + Preview Accept/Decline
          EvoriaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.Link, contentDescription = null, tint = tokens.primaryTerracotta)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Masukkan Kode dari Pasangan", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
            }
            Spacer(modifier = Modifier.height(10.dp))
            EvoriaFormTextField(
              value = codeInput,
              onValueChange = { codeInput = it.uppercase().take(6) },
              label = "Kode 6 Karakter",
              placeholder = "MISAL: A1B2C3",
              leadingIcon = Icons.Filled.Key,
              singleLine = true,
              modifier = Modifier.testTag("enter_invite_code_input"),
            )
            if (!inviteError.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(inviteError, style = MaterialTheme.typography.bodySmall, color = tokens.roseError)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = { onLookupCode(codeInput) },
              enabled = codeInput.length == 6 && !inviteLoading,
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("lookup_invite_code_button"),
            ) {
              Text("Periksa Kode Undangan")
            }

            if (lookedUpInvitation != null) {
              Spacer(modifier = Modifier.height(14.dp))
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = tokens.accentPeach.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, tokens.primaryTerracotta.copy(alpha = 0.4f)),
              ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("Undangan Ditemukan", style = MaterialTheme.typography.labelLarge, color = tokens.primaryTerracotta)
                  Text("Dari: ${lookedUpInvitation.inviterName}", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                  Text("Berlaku hingga ${formatIndoDateTime(lookedUpInvitation.expiresAtMillis)}", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                      onClick = {
                        onDeclineInvitation(lookedUpInvitation)
                        onClearLookup()
                      },
                      shape = RoundedCornerShape(50),
                      modifier = Modifier.weight(1f),
                    ) {
                      Text("Tolak", color = tokens.roseError)
                    }
                    Button(
                      onClick = { onAcceptInvitation(lookedUpInvitation) },
                      shape = RoundedCornerShape(50),
                      colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
                      modifier = Modifier.weight(1f).testTag("accept_invite_button"),
                    ) {
                      Text("Terima")
                    }
                  }
                }
              }
            }
          }

          // Active / History Invitations
          if (myInvitations.isNotEmpty()) {
            EvoriaCard {
              Text("Riwayat Kode Saya", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
              Spacer(modifier = Modifier.height(8.dp))
              myInvitations.forEach { inv ->
                Row(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text("Status: ${inv.status.uppercase()}", style = MaterialTheme.typography.labelMedium, color = tokens.inkText)
                    Text("Berlaku s/d ${formatIndoDateTime(inv.expiresAtMillis)}", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
                  }
                  if (inv.status == "pending") {
                    TextButton(onClick = { onCancelInvitation(inv.codeHash) }) {
                      Text("Batalkan", color = tokens.roseError)
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

  if (confirmDisconnect) {
    AlertDialog(
      onDismissRequest = { confirmDisconnect = false },
      title = { Text("Putuskan Koneksi?") },
      text = { Text("Koneksi ruang bersama akan diputuskan. Data lokal pribadimu tetap aman tersimpan di perangkat ini.") },
      confirmButton = {
        Button(
          onClick = {
            confirmDisconnect = false
            onDisconnectCouple()
          },
          colors = ButtonDefaults.buttonColors(containerColor = tokens.roseError),
        ) { Text("Putuskan") }
      },
      dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Batal") } },
    )
  }
}

@Composable
fun ShareInboxScreen(
  currentUid: String,
  requests: List<SharedContentRequestDto>,
  onAccept: (SharedContentRequestDto) -> Unit,
  onDecline: (SharedContentRequestDto) -> Unit,
  onRevoke: (SharedContentRequestDto) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val tokens = LocalEvoriaTokens.current

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(title = "Kotak Masuk EVORIA Share", subtitle = "Kiriman & persetujuan berdua", onBack = onBack)
      if (requests.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          EvoriaCard {
            Text("Belum Ada Permintaan EVORIA Share", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
            Text("Saat kamu atau pasangan mengirim kenangan via EVORIA Share, persetujuan akan muncul di sini.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
          }
        }
      } else {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          items(requests, key = { it.id }) { req ->
            val isIncoming = req.recipientUid == currentUid
            EvoriaCard {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(req.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkText, modifier = Modifier.weight(1f))
                Text(req.status.uppercase(), style = MaterialTheme.typography.labelSmall, color = tokens.primaryTerracotta)
              }
              Text(
                if (isIncoming) "Dari ${req.senderName}" else "Dikirim ke pasangan",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.mutedText,
              )
              if (!req.caption.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(req.caption, style = MaterialTheme.typography.bodySmall, color = tokens.inkText)
              }
              if (req.status == "pending") {
                Spacer(modifier = Modifier.height(10.dp))
                if (isIncoming) {
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onDecline(req) }, modifier = Modifier.weight(1f)) { Text("Tolak", color = tokens.roseError) }
                    Button(
                      onClick = { onAccept(req) },
                      colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
                      modifier = Modifier.weight(1f),
                    ) { Text("Terima") }
                  }
                } else {
                  OutlinedButton(onClick = { onRevoke(req) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Batalkan Kiriman", color = tokens.mutedText)
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

@Composable
fun NotificationCenterScreen(
  localNotifications: List<LocalNotificationEntity>,
  remoteNotifications: List<NotificationItemDto>,
  userPrefs: AppUserPrefs,
  onUpdatePrefs: (invites: Boolean, shares: Boolean, capsules: Boolean, security: Boolean) -> Unit,
  onMarkAllRead: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val tokens = LocalEvoriaTokens.current
  var hasNotifPermission by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    )
  }
  val notifPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotifPermission = granted
    }

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(
        title = "Pusat Notifikasi",
        onBack = onBack,
        trailingAction = {
          TextButton(onClick = onMarkAllRead) { Text("Tandai Dibaca", color = tokens.primaryTerracotta) }
        },
      )
      LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
          item {
            EvoriaCard(elevated = true) {
              Text("Izin Notifikasi Perangkat", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
              Spacer(modifier = Modifier.height(4.dp))
              Text("Aktifkan izin notifikasi agar pengingat kapsul waktu & aktivitas pasangan dapat tampil.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
              Spacer(modifier = Modifier.height(10.dp))
              Button(
                onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
              ) {
                Text("Izinkan Notifikasi")
              }
            }
          }
        }
        item {
          EvoriaCard {
            Text("Kategori Notifikasi", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
            Spacer(modifier = Modifier.height(8.dp))
            NotifToggleRow("Undangan Pasangan", userPrefs.notificationInvites) {
              onUpdatePrefs(it, userPrefs.notificationShares, userPrefs.notificationCapsules, userPrefs.notificationSecurity)
            }
            NotifToggleRow("EVORIA Share", userPrefs.notificationShares) {
              onUpdatePrefs(userPrefs.notificationInvites, it, userPrefs.notificationCapsules, userPrefs.notificationSecurity)
            }
            NotifToggleRow("Kapsul Waktu", userPrefs.notificationCapsules) {
              onUpdatePrefs(userPrefs.notificationInvites, userPrefs.notificationShares, it, userPrefs.notificationSecurity)
            }
            NotifToggleRow("Keamanan & PIN", userPrefs.notificationSecurity) {
              onUpdatePrefs(userPrefs.notificationInvites, userPrefs.notificationShares, userPrefs.notificationCapsules, it)
            }
          }
        }
        if (remoteNotifications.isNotEmpty()) {
          item { Text("Aktivitas Bersama", style = MaterialTheme.typography.titleSmall, color = tokens.inkText) }
          items(remoteNotifications, key = { it.id }) { n ->
            EvoriaCard {
              Text(n.title, style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
              Text(n.body, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
            }
          }
        }
        item { Text("Riwayat Lokal", style = MaterialTheme.typography.titleSmall, color = tokens.inkText) }
        if (localNotifications.isEmpty()) {
          item {
            EvoriaCard { Text("Belum ada notifikasi lokal.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText) }
          }
        } else {
          items(localNotifications, key = { it.id }) { n ->
            EvoriaCard {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(n.title, style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
                if (!n.isRead) {
                  Text("BARU", style = MaterialTheme.typography.labelSmall, color = tokens.primaryTerracotta)
                }
              }
              Text(n.body, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
              Text(formatIndoDateTime(n.createdAtMillis), style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NotifToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalEvoriaTokens.current.inkText)
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

// --- SECURITY, PIN, BIOMETRICS & APP LOCK OVERLAY ---

@Composable
fun SecurityAndAppLockScreen(
  securitySettings: SecuritySettings,
  onConfigurePin: (String) -> Unit,
  onVerifyExistingPin: suspend (String) -> PinVerifyResult,
  onDisableLock: () -> Unit,
  onSelectMethod: (LockMethod) -> Unit,
  onSelectAutoLock: (AutoLockTimeout) -> Unit,
  onLockNow: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val tokens = LocalEvoriaTokens.current
  val scope = rememberCoroutineScope()
  val capabilities = remember(context) { BiometricHelper.inspectCapabilities(context) }

  var showPinSetupDialog by remember { mutableStateOf(false) }
  var pinActionMode by remember { mutableStateOf("setup") } // "setup" | "disable"
  var currentPinInput by remember { mutableStateOf("") }
  var newPinInput by remember { mutableStateOf("") }
  var confirmPinInput by remember { mutableStateOf("") }
  var pinError by remember { mutableStateOf<String?>(null) }

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(title = "Keamanan & Kunci Aplikasi", subtitle = "PIN 6-digit + Biometrik Perangkat", onBack = onBack)

      Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        EvoriaCard(elevated = true) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Kunci Aplikasi EVORIA", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
              Text(
                if (securitySettings.appLockEnabled) "Aktif • Melindungi semua kenangan & jurnal."
                else "Nonaktif • Aktifkan dengan PIN 6-digit.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
              )
            }
            Switch(
              checked = securitySettings.appLockEnabled,
              onCheckedChange = { enable ->
                pinError = null
                currentPinInput = ""
                newPinInput = ""
                confirmPinInput = ""
                if (enable) {
                  pinActionMode = "setup"
                  showPinSetupDialog = true
                } else {
                  pinActionMode = "disable"
                  showPinSetupDialog = true
                }
              },
              modifier = Modifier.testTag("app_lock_toggle"),
            )
          }

          Spacer(modifier = Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
              onClick = {
                pinError = null
                currentPinInput = ""
                newPinInput = ""
                confirmPinInput = ""
                pinActionMode = "setup"
                showPinSetupDialog = true
              },
              shape = RoundedCornerShape(50),
              modifier = Modifier.weight(1f).testTag("configure_pin_button"),
            ) {
              Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (securitySettings.hasPinConfigured) "Ubah PIN" else "Buat PIN")
            }
            if (securitySettings.appLockEnabled) {
              Button(
                onClick = onLockNow,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
                modifier = Modifier.weight(1f).testTag("lock_now_button"),
              ) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Kunci Sekarang")
              }
            }
          }
        }

        // Biometric Capability & Preferred Method
        EvoriaCard {
          Text("Metode Buka Kunci Utama", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
          Spacer(modifier = Modifier.height(4.dp))
          Text(capabilities.statusDescription, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
          Spacer(modifier = Modifier.height(10.dp))

          LockMethodRow(
            icon = Icons.Filled.Key,
            title = LockMethod.PIN.labelId,
            subtitle = "Selalu tersedia sebagai pengaman utama",
            selected = securitySettings.preferredMethod == LockMethod.PIN,
            enabled = true,
            onClick = { onSelectMethod(LockMethod.PIN) },
          )
          LockMethodRow(
            icon = Icons.Filled.Fingerprint,
            title = LockMethod.FINGERPRINT.labelId,
            subtitle = if (capabilities.hasFingerprintHardware && capabilities.canUseBiometric) "Tersedia via Android BiometricPrompt" else "Perangkat/pendaftaran sidik jari belum siap",
            selected = securitySettings.preferredMethod == LockMethod.FINGERPRINT,
            enabled = capabilities.canUseBiometric,
            onClick = { onSelectMethod(LockMethod.FINGERPRINT) },
          )
          LockMethodRow(
            icon = Icons.Filled.Face,
            title = LockMethod.FACE.labelId,
            subtitle = if (capabilities.hasFaceHardware && capabilities.canUseBiometric) "Didukung lewat BiometricPrompt" else "Sensor Face Unlock tidak tersedia atau belum terdaftar",
            selected = securitySettings.preferredMethod == LockMethod.FACE,
            enabled = capabilities.canUseBiometric,
            onClick = { onSelectMethod(LockMethod.FACE) },
          )
        }

        // Auto-lock timeout
        EvoriaCard {
          Text("Kunci Otomatis Saat Latar Belakang", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
          Spacer(modifier = Modifier.height(8.dp))
          AutoLockTimeout.entries.forEach { t ->
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelectAutoLock(t) }.padding(vertical = 8.dp, horizontal = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(t.labelId, style = MaterialTheme.typography.bodyMedium, color = tokens.inkText)
              if (securitySettings.autoLockTimeout == t) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }
    }
  }

  if (showPinSetupDialog) {
    EvoriaSymmetricDialogShell(
      title =
        when {
          pinActionMode == "disable" -> "Nonaktifkan Kunci Aplikasi"
          securitySettings.hasPinConfigured -> "Ubah PIN 6-Digit"
          else -> "Buat PIN 6-Digit EVORIA"
        },
      subtitle = "Perlindungan PIN 6 angka terenkripsi di perangkat",
      icon = Icons.Filled.Lock,
      onDismiss = { showPinSetupDialog = false },
      confirmLabel = "Konfirmasi",
      onConfirm = {
        scope.launch {
          if (securitySettings.hasPinConfigured) {
            when (val check = onVerifyExistingPin(currentPinInput)) {
              is PinVerifyResult.Invalid -> {
                pinError = "PIN saat ini salah (sisa ${check.remainingBeforeCooldown} percobaan)."
                return@launch
              }
              is PinVerifyResult.Cooldown -> {
                pinError = "Terlalu banyak percobaan. Tunggu ${check.remainingSeconds} detik."
                return@launch
              }
              PinVerifyResult.Success -> Unit
            }
          }
          if (pinActionMode == "disable") {
            onDisableLock()
            showPinSetupDialog = false
          } else {
            if (newPinInput.length != 6) {
              pinError = "PIN harus terdiri dari tepat 6 angka."
            } else if (newPinInput != confirmPinInput) {
              pinError = "Konfirmasi PIN tidak cocok."
            } else {
              onConfigurePin(newPinInput)
              showPinSetupDialog = false
            }
          }
        }
      },
    ) {
      if (securitySettings.hasPinConfigured) {
        EvoriaFormTextField(
          value = currentPinInput,
          onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) currentPinInput = it },
          label = "PIN Saat Ini (6 angka)",
          leadingIcon = Icons.Filled.Key,
          singleLine = true,
        )
      }
      if (pinActionMode == "setup") {
        EvoriaFormTextField(
          value = newPinInput,
          onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPinInput = it },
          label = "PIN Baru (6 angka)",
          leadingIcon = Icons.Filled.Lock,
          singleLine = true,
        )
        EvoriaFormTextField(
          value = confirmPinInput,
          onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPinInput = it },
          label = "Ulangi PIN Baru",
          leadingIcon = Icons.Filled.Lock,
          singleLine = true,
        )
      }
      if (!pinError.isNullOrBlank()) {
        Text(pinError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = tokens.roseError)
      }
    }
  }
}

@Composable
private fun LockMethodRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(vertical = 8.dp, horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = if (enabled) tokens.primaryTerracotta else tokens.mutedText.copy(alpha = 0.45f))
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) tokens.inkText else tokens.mutedText)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
    }
    if (selected) {
      Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(18.dp))
    }
  }
}

@Composable
fun AppLockScreen(
  securitySettings: SecuritySettings,
  onVerifyPin: suspend (String) -> PinVerifyResult,
  onUnlockSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val tokens = LocalEvoriaTokens.current
  val scope = rememberCoroutineScope()
  val capabilities = remember(context) { BiometricHelper.inspectCapabilities(context) }

  var enteredPin by remember { mutableStateOf("") }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var cooldownSeconds by remember(securitySettings.cooldownUntilMillis) {
    mutableLongStateOf(((securitySettings.cooldownUntilMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L))
  }

  LaunchedEffect(cooldownSeconds) {
    if (cooldownSeconds > 0L) {
      delay(1000L)
      cooldownSeconds = (cooldownSeconds - 1L).coerceAtLeast(0L)
    }
  }

  fun launchBiometricIfAvailable() {
    val activity = context as? FragmentActivity ?: return
    if (!capabilities.canUseBiometric) return
    BiometricHelper.authenticate(
      activity = activity,
      onSuccess = onUnlockSuccess,
      onFallbackPin = {},
      onError = { msg -> errorMsg = msg },
    )
  }

  LaunchedEffect(securitySettings.preferredMethod) {
    if (securitySettings.preferredMethod != LockMethod.PIN && capabilities.canUseBiometric) {
      launchBiometricIfAvailable()
    }
  }

  EvoriaSanctuaryBackdrop {
    Column(
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(20.dp))
        val lockEmblemReq = rememberThumbnailRequest(R.drawable.evoria_app_icon_1794808521071, sizePx = 192)
        AsyncImage(
          model = lockEmblemReq,
          contentDescription = "EVORIA Terkunci",
          modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)),
          contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text("EVORIA Terkunci", style = MaterialTheme.typography.headlineLarge, color = tokens.inkText)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Masukkan PIN 6 angka untuk membuka ruang kenanganmu", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText, textAlign = TextAlign.Center)
      }

      // 6-Dot PIN indicator
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          repeat(6) { idx ->
            val filled = idx < enteredPin.length
            Box(
              modifier =
                Modifier
                  .size(16.dp)
                  .clip(CircleShape)
                  .background(if (filled) tokens.primaryTerracotta else Color.Transparent)
                  .border(1.5.dp, if (filled) tokens.primaryTerracotta else tokens.outlineSoft, CircleShape)
            )
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (cooldownSeconds > 0L) {
          Text(
            text = "Terlalu banyak percobaan salah. Coba lagi dalam $cooldownSeconds detik.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.roseError,
            textAlign = TextAlign.Center,
          )
        } else if (!errorMsg.isNullOrBlank()) {
          Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = tokens.roseError, textAlign = TextAlign.Center)
        }
      }

      // Numeric Keypad
      Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val rows = listOf(
          listOf("1", "2", "3"),
          listOf("4", "5", "6"),
          listOf("7", "8", "9"),
        )
        rows.forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            row.forEach { digit ->
              PinKeyButton(label = digit, enabled = cooldownSeconds == 0L) {
                if (enteredPin.length < 6) {
                  val next = enteredPin + digit
                  enteredPin = next
                  errorMsg = null
                  if (next.length == 6) {
                    scope.launch {
                      when (val res = onVerifyPin(next)) {
                        PinVerifyResult.Success -> onUnlockSuccess()
                        is PinVerifyResult.Invalid -> {
                          enteredPin = ""
                          errorMsg = "PIN salah (sisa ${res.remainingBeforeCooldown} percobaan sebelum jeda)."
                        }
                        is PinVerifyResult.Cooldown -> {
                          enteredPin = ""
                          cooldownSeconds = res.remainingSeconds
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
          if (capabilities.canUseBiometric) {
            IconButton(
              onClick = { launchBiometricIfAvailable() },
              modifier = Modifier.size(66.dp),
            ) {
              Icon(
                imageVector = if (securitySettings.preferredMethod == LockMethod.FACE) Icons.Filled.Face else Icons.Filled.Fingerprint,
                contentDescription = "Buka dengan biometrik",
                tint = tokens.primaryTerracotta,
                modifier = Modifier.size(30.dp),
              )
            }
          } else {
            Spacer(modifier = Modifier.size(66.dp))
          }
          PinKeyButton(label = "0", enabled = cooldownSeconds == 0L) {
            if (enteredPin.length < 6) {
              val next = enteredPin + "0"
              enteredPin = next
              if (next.length == 6) {
                scope.launch {
                  when (val res = onVerifyPin(next)) {
                    PinVerifyResult.Success -> onUnlockSuccess()
                    is PinVerifyResult.Invalid -> {
                      enteredPin = ""
                      errorMsg = "PIN salah (sisa ${res.remainingBeforeCooldown} percobaan)."
                    }
                    is PinVerifyResult.Cooldown -> {
                      enteredPin = ""
                      cooldownSeconds = res.remainingSeconds
                    }
                  }
                }
              }
            }
          }
          IconButton(
            onClick = { if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1) },
            modifier = Modifier.size(66.dp),
          ) {
            Icon(Icons.Filled.Backspace, contentDescription = "Hapus angka", tint = tokens.inkText)
          }
        }
      }
      Spacer(modifier = Modifier.height(10.dp))
    }
  }
}

@Composable
private fun PinKeyButton(label: String, enabled: Boolean, onClick: () -> Unit) {
  val tokens = LocalEvoriaTokens.current
  Surface(
    modifier = Modifier.size(66.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
    shape = CircleShape,
    color = tokens.cardSurface,
    border = BorderStroke(1.dp, tokens.outlineSoft),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(label, style = MaterialTheme.typography.headlineMedium, color = tokens.inkText)
    }
  }
}

// --- STORAGE, BACKUP, RESTORE, EXPORT, DELETE ---

@Composable
fun StorageAndBackupScreen(
  localStorageLabel: String,
  checkpoint: SyncCheckpointEntity?,
  onBackupToCloud: () -> Unit,
  onRestoreFromCloud: () -> Unit,
  onGenerateExportJson: suspend () -> String,
  onDeleteAllLocalData: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val tokens = LocalEvoriaTokens.current
  val scope = rememberCoroutineScope()
  var confirmWipe by remember { mutableStateOf(false) }

  EvoriaSanctuaryBackdrop {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      EvoriaTopBar(title = "Cadangan & Penyimpanan", subtitle = "Firestore Spark & Data Lokal", onBack = onBack)
      Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        EvoriaCard(elevated = true) {
          Text("Penyimpanan Perangkat", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
          Spacer(modifier = Modifier.height(6.dp))
          Text("Ukuran media lokal EVORIA: $localStorageLabel", style = MaterialTheme.typography.bodyMedium, color = tokens.primaryTerracotta)
          if (checkpoint != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(checkpoint.statusMessage, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
            if (checkpoint.lastBackupMillis > 0L) {
              Text("Cadangan terakhir: ${formatIndoDateTime(checkpoint.lastBackupMillis)}", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
            }
          }
        }

        EvoriaCard {
          Text("Cadangan & Pemulihan", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            "Menyimpan metadata album, cerita, jurnal, surat & kapsul ke dokumen privat Firestore milikmu (tanpa biaya penagihan cloud).",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.mutedText,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = onBackupToCloud,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
            modifier = Modifier.fillMaxWidth().height(48.dp),
          ) {
            Text("Cadangkan Metadata ke Firestore")
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = onRestoreFromCloud,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(48.dp),
          ) {
            Text("Pulihkan dari Cadangan Cloud", color = tokens.inkText)
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = {
              scope.launch {
                val payload = onGenerateExportJson()
                val intent = Intent(Intent.ACTION_SEND).apply {
                  type = "application/json"
                  putExtra(Intent.EXTRA_SUBJECT, "Cadangan Metadata EVORIA")
                  putExtra(Intent.EXTRA_TEXT, payload)
                }
                context.startActivity(Intent.createChooser(intent, "Ekspor Cadangan JSON EVORIA"))
              }
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(48.dp),
          ) {
            Text("Ekspor JSON via Sharesheet", color = tokens.inkText)
          }
        }

        EvoriaCard {
          Text("Zona Penghapusan Lokal", style = MaterialTheme.typography.titleMedium, color = tokens.roseError)
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            "Menghapus seluruh salinan foto, video, jurnal, surat, dan kapsul lokal di perangkat ini.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.mutedText,
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = { confirmWipe = true },
            border = BorderStroke(1.dp, tokens.roseError),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = tokens.roseError, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Hapus Semua Data Lokal", color = tokens.roseError)
          }
        }
      }
    }
  }

  if (confirmWipe) {
    AlertDialog(
      onDismissRequest = { confirmWipe = false },
      title = { Text("Hapus Semua Data Lokal?") },
      text = { Text("Tindakan ini tidak dapat dibatalkan untuk data lokal yang belum dicadangkan.") },
      confirmButton = {
        Button(
          onClick = {
            confirmWipe = false
            onDeleteAllLocalData()
          },
          colors = ButtonDefaults.buttonColors(containerColor = tokens.roseError),
        ) { Text("Hapus Permanen") }
      },
      dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Batal") } },
    )
  }
}

// --- SCREEN #68: LIQUID GLASS NAVBAR INTERACTION SHOWCASE ---

private enum class ShowcaseBackdropMode(val label: String) {
  EDITORIAL("Kartu Editorial"),
  SUNSET("Pendar Senja"),
  CONTRAST("Kanvas Kontras"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiquidGlassShowcaseScreen(
  quickOpenOnLaunch: Boolean,
  onToggleQuickOpenOnLaunch: (Boolean) -> Unit,
  initialNavbarStyle: NavbarThemeStyle = NavbarThemeStyle.FROST_AURORA,
  initialNavbarIntensity: NavbarGlassIntensity = NavbarGlassIntensity.BALANCED,
  accentPreset: EvoriaAccentPreset = EvoriaAccentPreset.TERRACOTTA,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val hostIsDark = LocalEvoriaTokens.current.isDark
  var previewDark by remember { mutableStateOf(hostIsDark) }
  var previewReducedMotion by remember { mutableStateOf(false) }
  var previewNavbarStyle by remember(initialNavbarStyle) { mutableStateOf(initialNavbarStyle) }
  var previewNavbarIntensity by remember(initialNavbarIntensity) { mutableStateOf(initialNavbarIntensity) }
  var tabletViewportPreview by remember { mutableStateOf(false) }
  var backdropMode by remember { mutableStateOf(ShowcaseBackdropMode.EDITORIAL) }
  var demoTabIndex by remember { mutableIntStateOf(0) }
  var telemetry by remember { mutableStateOf(LiquidGlassTelemetry()) }

  EvoriaTheme(
    themeMode = if (previewDark) ThemeMode.DARK else ThemeMode.LIGHT,
    accentPreset = accentPreset,
  ) {
    val tokens = LocalEvoriaTokens.current
    EvoriaSanctuaryBackdrop {
      Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        EvoriaTopBar(
          title = "Showcase Liquid Glass",
          subtitle =
            if (previewDark) "Midnight Longing • Optik & Gestur"
            else "Warm Solitude • Optik & Gestur",
          onBack = onBack,
        )

        BoxWithConstraints(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentAlignment = Alignment.TopCenter,
        ) {
          val maxAvailable = maxWidth
          val viewportCap =
            if (tabletViewportPreview) 620.dp.coerceAtMost(maxAvailable)
            else 396.dp.coerceAtMost(maxAvailable)

          Box(
            modifier =
              Modifier
                .widthIn(max = viewportCap)
                .fillMaxSize()
                .then(
                  if (tabletViewportPreview || maxAvailable > viewportCap + 24.dp) {
                    Modifier
                      .padding(horizontal = 6.dp)
                      .border(
                        width = 1.dp,
                        color = tokens.outlineSoft.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                      )
                      .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                  } else {
                    Modifier
                  }
                ),
          ) {
            // Scrollable showcase stage extending beneath the floating LiquidGlassNavbar
            Column(
              modifier =
                Modifier
                  .fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 128.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              // 1. Live State & Optical Telemetry HUD
              EvoriaCard(elevated = true) {
                val badgeBg by animateColorAsState(
                  targetValue =
                    when (telemetry.interactionState) {
                      LiquidGlassInteractionState.IDLE -> tokens.secondarySurface
                      LiquidGlassInteractionState.PRESSED -> tokens.accentPeach.copy(alpha = 0.35f)
                      LiquidGlassInteractionState.DRAGGED -> tokens.primaryTerracotta.copy(alpha = 0.22f)
                    },
                  label = "showcaseStateBadgeBg",
                )
                val badgeText =
                  when (telemetry.interactionState) {
                    LiquidGlassInteractionState.IDLE -> tokens.mutedText
                    LiquidGlassInteractionState.PRESSED -> tokens.primaryTerracotta
                    LiquidGlassInteractionState.DRAGGED -> tokens.primaryTerracotta
                  }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    "Telemetri Optik Langsung",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.inkText,
                  )
                  Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, tokens.primaryTerracotta.copy(alpha = 0.35f)),
                    modifier = Modifier.testTag("showcase_state_badge"),
                  ) {
                    Text(
                      text = telemetry.interactionState.labelId,
                      style = MaterialTheme.typography.labelSmall,
                      color = badgeText,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  ShowcaseMetricTile(
                    label = "Regangan",
                    value = "${"%.2f".format(telemetry.stretchFactor)}x",
                  )
                  ShowcaseMetricTile(
                    label = "Dispersi Tepi",
                    value = "${"%.2f".format(telemetry.chromaticDispersionDp)} dp",
                  )
                  ShowcaseMetricTile(
                    label = "Kecepatan",
                    value = "${telemetry.velocityPxPerSec.toInt()} px/s",
                  )
                  ShowcaseMetricTile(
                    label = "Posisi",
                    value = "${(telemetry.indicatorCenterRatio * 100).toInt()}%",
                  )
                }
              }

              // 2. Simulated Active EVORIA Tab Preview
              val activeTab = EvoriaMainTabs[demoTabIndex]
              EvoriaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier =
                      Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.accentPeach.copy(alpha = if (tokens.isDark) 0.22f else 0.32f)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = activeTab.selectedIcon,
                      contentDescription = activeTab.label,
                      tint = tokens.primaryTerracotta,
                    )
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      "Pratinjau Tab: ${activeTab.label}",
                      style = MaterialTheme.typography.titleMedium,
                      color = tokens.inkText,
                    )
                    Text(
                      when (demoTabIndex) {
                        0 -> "Beranda — Linimasa hangat & sapaan berdua."
                        1 -> "Kenangan — Album & kisah visual berdua."
                        2 -> "Jelajah — Pencarian foto & kenangan berbasis deskripsi."
                        3 -> "Refleksi — Jurnal harian, surat & kapsul waktu."
                        else -> "Profil — Ruang pribadi, privasi & tema EVORIA."
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = tokens.mutedText,
                    )
                  }
                }
              }

              // 3. Theme, Viewport Responsiveness & Launch Quick-Open Controls
              EvoriaCard {
                Text("Konfigurasi Pratinjau", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                Spacer(modifier = Modifier.height(10.dp))

                Text("Tema Visual", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  FilterChip(
                    selected = !previewDark,
                    onClick = { previewDark = false },
                    label = { Text("Warm Solitude") },
                    leadingIcon = {
                      Icon(Icons.Filled.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tokens.accentPeach.copy(alpha = 0.35f),
                        selectedLabelColor = tokens.inkText,
                      ),
                    modifier = Modifier.weight(1f).testTag("showcase_theme_light"),
                  )
                  FilterChip(
                    selected = previewDark,
                    onClick = { previewDark = true },
                    label = { Text("Midnight Longing") },
                    leadingIcon = {
                      Icon(Icons.Filled.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tokens.primaryTerracotta.copy(alpha = 0.32f),
                        selectedLabelColor = tokens.inkText,
                      ),
                    modifier = Modifier.weight(1f).testTag("showcase_theme_dark"),
                  )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Gaya & Intensitas Kaca Navbar", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  NavbarThemeStyle.entries.forEach { style ->
                    FilterChip(
                      selected = previewNavbarStyle == style,
                      onClick = { previewNavbarStyle = style },
                      label = { Text(style.labelId) },
                      colors =
                        FilterChipDefaults.filterChipColors(
                          selectedContainerColor = tokens.accentPeach.copy(alpha = 0.35f),
                          selectedLabelColor = tokens.inkText,
                        ),
                    )
                  }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  NavbarGlassIntensity.entries.forEach { intensity ->
                    FilterChip(
                      selected = previewNavbarIntensity == intensity,
                      onClick = { previewNavbarIntensity = intensity },
                      label = { Text(intensity.labelId) },
                      colors =
                        FilterChipDefaults.filterChipColors(
                          selectedContainerColor = tokens.accentPeach.copy(alpha = 0.35f),
                          selectedLabelColor = tokens.inkText,
                        ),
                      modifier = Modifier.weight(1f),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Responsivitas Lebar", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  FilterChip(
                    selected = !tabletViewportPreview,
                    onClick = { tabletViewportPreview = false },
                    label = { Text("Ponsel (~396dp)") },
                    leadingIcon = {
                      Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tokens.accentPeach.copy(alpha = 0.35f),
                        selectedLabelColor = tokens.inkText,
                      ),
                    modifier = Modifier.weight(1f).testTag("showcase_viewport_phone"),
                  )
                  FilterChip(
                    selected = tabletViewportPreview,
                    onClick = { tabletViewportPreview = true },
                    label = { Text("Tablet (~620dp)") },
                    leadingIcon = {
                      Icon(Icons.Filled.TabletAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tokens.accentPeach.copy(alpha = 0.35f),
                        selectedLabelColor = tokens.inkText,
                      ),
                    modifier = Modifier.weight(1f).testTag("showcase_viewport_tablet"),
                  )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Latar Belakang di Bawah Navbar", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  ShowcaseBackdropMode.entries.forEach { mode ->
                    FilterChip(
                      selected = backdropMode == mode,
                      onClick = { backdropMode = mode },
                      label = { Text(mode.label) },
                      leadingIcon = {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                      },
                      colors =
                        FilterChipDefaults.filterChipColors(
                          selectedContainerColor = tokens.accentPeach.copy(alpha = 0.35f),
                          selectedLabelColor = tokens.inkText,
                        ),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("Kurangi Gerakan (Reduced Motion)", style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
                    Text("Nonaktifkan regangan & dispersi kromatik demi kenyamanan.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
                  }
                  Switch(
                    checked = previewReducedMotion,
                    onCheckedChange = { previewReducedMotion = it },
                    modifier = Modifier.testTag("showcase_reduced_motion_switch"),
                  )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("Buka Showcase Saat Luncur", style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
                    Text("Masuk ke Showcase lebih dulu saat membuka EVORIA (tetap mudah diakses lewat Profil).", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
                  }
                  Switch(
                    checked = quickOpenOnLaunch,
                    onCheckedChange = onToggleQuickOpenOnLaunch,
                    modifier = Modifier.testTag("showcase_quick_open_switch"),
                  )
                }
              }

              // 4. Gesture Choreography Guide
              EvoriaCard {
                Text("Koreografi Gestur & Optik", style = MaterialTheme.typography.titleMedium, color = tokens.inkText)
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Tenang (Idle): Kapsul kaca memanjang tenang dengan pendar lembut di bawahnya.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
                Text("• Tekan & Tahan: Kapsul menggembung melampaui bingkai, pantulan card navbar menciut ke dalam, tepi melengkung berkilau chromatic halus.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
                Text("• Geser (Drag Jelly): Kapsul melentur bak jeli searah gerak dengan tepi kromatik pelangi melengkung & haptic di tiap tab.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
                Text("• Lepas (Release): Pegas elastis mengembalikan kapsul ke bentuk normal dan mengunci tab aktif pilihan.", style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
              }

              // 5. Scrollable Backdrop Cards designed to pass right behind the LiquidGlassNavbar
              ShowcaseBackdropSampleBand(mode = backdropMode)
            }

            // Floating Liquid Glass Navbar docked at the bottom of the responsive stage
            LiquidGlassNavbar(
              selectedTabIndex = demoTabIndex,
              onTabSelected = { demoTabIndex = it },
              reducedMotionOverride = previewReducedMotion,
              navbarThemeStyle = previewNavbarStyle,
              navbarGlassIntensity = previewNavbarIntensity,
              applyNavigationBarInsets = false,
              onTelemetryChanged = { telemetry = it },
              modifier = Modifier.align(Alignment.BottomCenter).testTag("showcase_liquid_glass_navbar"),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ShowcaseMetricTile(label: String, value: String) {
  val tokens = LocalEvoriaTokens.current
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleSmall, color = tokens.primaryTerracotta, fontWeight = FontWeight.SemiBold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
  }
}

@Composable
private fun ShowcaseBackdropSampleBand(mode: ShowcaseBackdropMode) {
  val tokens = LocalEvoriaTokens.current
  val gradientColors =
    when (mode) {
      ShowcaseBackdropMode.EDITORIAL ->
        if (tokens.isDark) listOf(Color(0xFF3E322F), Color(0xFF2B2220))
        else listOf(Color(0xFFF3E7D8), Color(0xFFE7D6C2))
      ShowcaseBackdropMode.SUNSET ->
        if (tokens.isDark) listOf(Color(0xFF6B3E2E), Color(0xFF3D2832), Color(0xFF211C24))
        else listOf(Color(0xFFF2B694), Color(0xFFD9876B), Color(0xFFEBD0B7))
      ShowcaseBackdropMode.CONTRAST ->
        if (tokens.isDark) listOf(Color(0xFF1F2B30), Color(0xFF4F342A), Color(0xFF2D2523))
        else listOf(Color(0xFF352823), Color(0xFFA46B55), Color(0xFFE9B99E))
    }

  Surface(
    shape = RoundedCornerShape(22.dp),
    border = BorderStroke(1.dp, tokens.outlineSoft),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(210.dp)
          .background(Brush.linearGradient(gradientColors)),
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
          color = Color.White.copy(alpha = 0.22f),
          radius = size.width * 0.24f,
          center = Offset(size.width * 0.78f, size.height * 0.28f),
        )
        drawCircle(
          color = tokens.accentPeach.copy(alpha = 0.28f),
          radius = size.width * 0.30f,
          center = Offset(size.width * 0.24f, size.height * 0.76f),
        )
      }
      Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Gulir ke bawah untuk menguji keburaman optik di belakang bilah navigasi",
          style = MaterialTheme.typography.titleSmall,
          color = if (mode == ShowcaseBackdropMode.EDITORIAL && !tokens.isDark) tokens.inkText else Color.White,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          listOf("Senja Danau", "Bangku Kosong", "Langkah Berdua").forEach { tag ->
            Surface(
              color = Color.Black.copy(alpha = 0.24f),
              shape = RoundedCornerShape(50),
            ) {
              Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
          }
        }
      }
    }
  }
}

fun normalizeSocialUrl(platform: String, rawInput: String): String {
  val trimmed = rawInput.trim()
  if (trimmed.isEmpty()) return ""
  val lower = trimmed.lowercase()
  if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) {
    return trimmed
  }
  val cleanHandle = trimmed.removePrefix("@").trim()
  if (cleanHandle.isEmpty()) return ""
  return when (platform.lowercase()) {
    "instagram" -> "https://instagram.com/$cleanHandle"
    "tiktok" -> "https://www.tiktok.com/@$cleanHandle"
    "github" -> "https://github.com/$cleanHandle"
    "email" -> "mailto:$trimmed"
    else -> "https://$cleanHandle"
  }
}

@Composable
fun AboutEvoriaScreen(
  userPrefs: AppUserPrefs,
  currentUserEmail: String? = null,
  onSaveDeveloperSocials: (instagram: String, tiktok: String, github: String, email: String, website: String) -> Unit = { _, _, _, _, _ -> },
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val tokens = LocalEvoriaTokens.current
  val context = LocalContext.current
  val isDeveloperAdmin = remember(currentUserEmail) { canEditDeveloperSocials(currentUserEmail) }
  var showEditSocialsDialog by remember { mutableStateOf(false) }
  var actionFeedback by remember { mutableStateOf<String?>(null) }

  fun openExternalOrPrompt(platform: String, rawValue: String) {
    val targetUrl = normalizeSocialUrl(platform, rawValue)
    if (targetUrl.isEmpty()) {
      if (isDeveloperAdmin) {
        showEditSocialsDialog = true
      } else {
        actionFeedback = "Hanya akun Google $DEVELOPER_ADMIN_EMAIL yang dapat mengatur tautan ini."
      }
      return
    }
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { context.startActivity(intent) }
      .onFailure {
        actionFeedback = "Tidak dapat membuka tautan ($targetUrl)."
      }
  }

  fun copyToClipboard(label: String, value: String) {
    if (value.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, value))
    actionFeedback = "$label disalin ke papan klip."
  }

  EvoriaSanctuaryBackdrop {
    Column(
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      EvoriaTopBar(
        title = "Tentang EVORIA",
        subtitle = "Versi aplikasi, developer & sosial media",
        onBack = onBack,
      )

      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).testTag("about_lazy_column"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // App Identity & Version Card
        item {
          EvoriaCard(elevated = true) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = tokens.primaryTerracotta,
                shadowElevation = 4.dp,
                modifier = Modifier.size(62.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = "E",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                  )
                }
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "EVORIA",
                  style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                  color = tokens.inkText,
                )
                Text(
                  text = "Ruang kenangan berdua yang hangat & privat",
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.mutedText,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                  shape = RoundedCornerShape(50),
                  color = tokens.accentPeach.copy(alpha = 0.28f),
                ) {
                  Text(
                    text = "Versi ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.primaryTerracotta,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("about_version_text"),
                  )
                }
              }
            }
          }
        }

        // Developer Card
        item {
          EvoriaCard(elevated = true) {
            Text(
              text = "DEVELOPER",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = tokens.primaryTerracotta,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "RAFFA AULIA MUSYAWAL",
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
              color = tokens.inkText,
              modifier = Modifier.testTag("about_developer_name"),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Kreator & Pengembang Aplikasi EVORIA",
              style = MaterialTheme.typography.titleSmall,
              color = tokens.mutedText,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Dibangun dengan Jetpack Compose & Material Design 3 untuk menyimpan foto, album, jurnal refleksi, kapsul waktu, dan soundtrack penuh bermakna.",
              style = MaterialTheme.typography.bodySmall,
              color = tokens.mutedText,
            )
          }
        }

        // Developer Social Media Accounts (Editable ONLY by musyawalraffa@gmail.com)
        item {
          EvoriaCard {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Akun Sosial Media",
                  style = MaterialTheme.typography.titleMedium,
                  color = tokens.inkText,
                )
                Text(
                  text =
                    if (isDeveloperAdmin) "Terverifikasi sebagai developer ($DEVELOPER_ADMIN_EMAIL)"
                    else "Terhubung langsung ke profil developer",
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.mutedText,
                )
              }
              if (isDeveloperAdmin) {
                TextButton(
                  onClick = { showEditSocialsDialog = true },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("about_edit_socials_button"),
                ) {
                  Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.primaryTerracotta)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Atur", color = tokens.primaryTerracotta)
                }
              } else {
                Surface(
                  shape = RoundedCornerShape(50),
                  color = tokens.secondarySurface,
                  modifier = Modifier.testTag("about_socials_locked_badge"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = tokens.mutedText, modifier = Modifier.size(13.dp))
                    Text(
                      text = "Hanya Developer",
                      style = MaterialTheme.typography.labelSmall,
                      color = tokens.mutedText,
                    )
                  }
                }
              }
            }

            if (!actionFeedback.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = actionFeedback.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.primaryTerracotta,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            AboutSocialMediaRow(
              icon = Icons.Filled.CameraAlt,
              platformLabel = "Instagram",
              value = userPrefs.devInstagram,
              isEditable = isDeveloperAdmin,
              testTag = "about_social_instagram",
              onOpen = { openExternalOrPrompt("instagram", userPrefs.devInstagram) },
              onCopy = { copyToClipboard("Instagram", userPrefs.devInstagram) },
            )
            HorizontalDivider(color = tokens.outlineSoft.copy(alpha = 0.5f))
            AboutSocialMediaRow(
              icon = Icons.Filled.MusicNote,
              platformLabel = "TikTok",
              value = userPrefs.devTiktok,
              isEditable = isDeveloperAdmin,
              testTag = "about_social_tiktok",
              onOpen = { openExternalOrPrompt("tiktok", userPrefs.devTiktok) },
              onCopy = { copyToClipboard("TikTok", userPrefs.devTiktok) },
            )
            HorizontalDivider(color = tokens.outlineSoft.copy(alpha = 0.5f))
            AboutSocialMediaRow(
              icon = Icons.Filled.Code,
              platformLabel = "GitHub",
              value = userPrefs.devGithub,
              isEditable = isDeveloperAdmin,
              testTag = "about_social_github",
              onOpen = { openExternalOrPrompt("github", userPrefs.devGithub) },
              onCopy = { copyToClipboard("GitHub", userPrefs.devGithub) },
            )
            HorizontalDivider(color = tokens.outlineSoft.copy(alpha = 0.5f))
            AboutSocialMediaRow(
              icon = Icons.Filled.Email,
              platformLabel = "Email",
              value = userPrefs.devEmail,
              isEditable = isDeveloperAdmin,
              testTag = "about_social_email",
              onOpen = { openExternalOrPrompt("email", userPrefs.devEmail) },
              onCopy = { copyToClipboard("Email", userPrefs.devEmail) },
            )
            HorizontalDivider(color = tokens.outlineSoft.copy(alpha = 0.5f))
            AboutSocialMediaRow(
              icon = Icons.Filled.Language,
              platformLabel = "Website / Portofolio",
              value = userPrefs.devWebsite,
              isEditable = isDeveloperAdmin,
              testTag = "about_social_website",
              onOpen = { openExternalOrPrompt("website", userPrefs.devWebsite) },
              onCopy = { copyToClipboard("Website", userPrefs.devWebsite) },
            )
          }
        }
      }
    }
  }

  if (isDeveloperAdmin && showEditSocialsDialog) {
    AboutEditSocialsDialog(
      initialInstagram = userPrefs.devInstagram,
      initialTiktok = userPrefs.devTiktok,
      initialGithub = userPrefs.devGithub,
      initialEmail = userPrefs.devEmail,
      initialWebsite = userPrefs.devWebsite,
      onDismiss = { showEditSocialsDialog = false },
      onSave = { ig, tt, gh, mail, web ->
        onSaveDeveloperSocials(ig, tt, gh, mail, web)
        showEditSocialsDialog = false
      },
    )
  }
}

@Composable
private fun AboutSocialMediaRow(
  icon: ImageVector,
  platformLabel: String,
  value: String,
  isEditable: Boolean,
  testTag: String,
  onOpen: () -> Unit,
  onCopy: () -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  val hasValue = value.isNotBlank()
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clickable(onClick = onOpen)
        .padding(vertical = 10.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      shape = CircleShape,
      color = if (hasValue) tokens.accentPeach.copy(alpha = 0.28f) else tokens.secondarySurface,
      modifier = Modifier.size(38.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (hasValue) tokens.primaryTerracotta else tokens.mutedText,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(platformLabel, style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
      Text(
        text =
          when {
            hasValue -> value
            isEditable -> "Ketuk untuk tambahkan akun $platformLabel"
            else -> "Belum ditambahkan oleh developer"
          },
        style = MaterialTheme.typography.bodySmall,
        color = if (hasValue) tokens.primaryTerracotta else tokens.mutedText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (hasValue) {
      IconButton(
        onClick = onCopy,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Filled.ContentCopy,
          contentDescription = "Salin $platformLabel",
          tint = tokens.mutedText,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Icon(
      imageVector =
        when {
          hasValue -> Icons.AutoMirrored.Filled.OpenInNew
          isEditable -> Icons.Filled.Edit
          else -> Icons.Filled.Lock
        },
      contentDescription =
        when {
          hasValue -> "Buka $platformLabel"
          isEditable -> "Atur $platformLabel"
          else -> "Terkunci"
        },
      tint = tokens.mutedText,
      modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
private fun AboutEditSocialsDialog(
  initialInstagram: String,
  initialTiktok: String,
  initialGithub: String,
  initialEmail: String,
  initialWebsite: String,
  onDismiss: () -> Unit,
  onSave: (instagram: String, tiktok: String, github: String, email: String, website: String) -> Unit,
) {
  var ig by remember(initialInstagram) { mutableStateOf(initialInstagram) }
  var tt by remember(initialTiktok) { mutableStateOf(initialTiktok) }
  var gh by remember(initialGithub) { mutableStateOf(initialGithub) }
  var mail by remember(initialEmail) { mutableStateOf(initialEmail) }
  var web by remember(initialWebsite) { mutableStateOf(initialWebsite) }

  EvoriaSymmetricDialogShell(
    title = "Atur Sosial Media Developer",
    subtitle = "Khusus akun $DEVELOPER_ADMIN_EMAIL • RAFFA AULIA MUSYAWAL",
    icon = Icons.Filled.Share,
    onDismiss = onDismiss,
    confirmLabel = "Simpan",
    confirmTestTag = "about_social_save_button",
    onConfirm = { onSave(ig, tt, gh, mail, web) },
  ) {
    EvoriaFormTextField(
      value = ig,
      onValueChange = { ig = it },
      label = "Instagram (@username / URL)",
      leadingIcon = Icons.Filled.CameraAlt,
      singleLine = true,
      modifier = Modifier.testTag("about_social_input_instagram"),
    )
    EvoriaFormTextField(
      value = tt,
      onValueChange = { tt = it },
      label = "TikTok (@username / URL)",
      leadingIcon = Icons.Filled.MusicNote,
      singleLine = true,
      modifier = Modifier.testTag("about_social_input_tiktok"),
    )
    EvoriaFormTextField(
      value = gh,
      onValueChange = { gh = it },
      label = "GitHub (username / URL)",
      leadingIcon = Icons.Filled.Code,
      singleLine = true,
      modifier = Modifier.testTag("about_social_input_github"),
    )
    EvoriaFormTextField(
      value = mail,
      onValueChange = { mail = it },
      label = "Email Developer",
      leadingIcon = Icons.Filled.Email,
      singleLine = true,
      modifier = Modifier.testTag("about_social_input_email"),
    )
    EvoriaFormTextField(
      value = web,
      onValueChange = { web = it },
      label = "Website / Portofolio",
      leadingIcon = Icons.Filled.Language,
      singleLine = true,
      modifier = Modifier.testTag("about_social_input_website"),
    )
  }
}


