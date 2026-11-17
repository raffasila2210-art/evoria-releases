package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.configureAppCheck
import com.example.auth.signOut
import com.example.data.ai.EvoriaSearchEngine
import com.example.data.local.EvoriaDatabase
import com.example.data.local.EvoriaSanctuaryRepository
import com.example.data.local.LocalMemoryEntity
import com.example.data.local.SecurityPreferences
import com.example.data.local.ThemeMode
import com.example.data.remote.EvoriaCloudRepository
import com.example.media.EvoriaPlayerManager
import com.example.ui.EvoriaViewModel
import com.example.ui.components.CloudBloomOverlay
import com.example.ui.components.EvoriaMiniPlayerBar
import com.example.ui.components.EvoriaSanctuaryBackdrop
import com.example.ui.components.LiquidGlassNavbar
import com.example.ui.screens.AboutEvoriaScreen
import com.example.ui.screens.AddOrEditMemoryScreen
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.BerandaScreen
import com.example.ui.screens.FirstProfileSetupDialogContent
import com.example.ui.screens.FullMusicPlayerScreen
import com.example.ui.screens.FullscreenPhotoViewer
import com.example.ui.screens.FullscreenVideoViewer
import com.example.ui.screens.JelajahScreen
import com.example.ui.screens.KenanganScreen
import com.example.ui.screens.LiquidGlassShowcaseScreen
import com.example.ui.screens.MemoryDetailScreen
import com.example.ui.screens.MusicAndSoundtrackScreen
import com.example.ui.screens.NotificationCenterScreen
import com.example.ui.screens.OnboardingAndSignInScreen
import com.example.ui.screens.PartnerConnectionScreen
import com.example.ui.screens.ProfilScreen
import com.example.ui.screens.QuickShareScreen
import com.example.ui.screens.RefleksiScreen
import com.example.ui.screens.SecurityAndAppLockScreen
import com.example.ui.screens.ShareInboxScreen
import com.example.ui.screens.StorageAndBackupScreen
import com.example.ui.screens.UploadQueueScreen
import com.example.ui.theme.EvoriaTheme
import com.example.util.FrameRateController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private sealed interface EvoriaRoute {
  data object MainTabs : EvoriaRoute
  data class AlbumDetail(val albumId: String) : EvoriaRoute
  data class MemoryDetail(val memoryId: String) : EvoriaRoute
  data class AddOrEditMemory(val memoryId: String?) : EvoriaRoute
  data class FullscreenPhoto(val uri: String, val title: String) : EvoriaRoute
  data class FullscreenVideo(val uri: String) : EvoriaRoute
  data object UploadQueue : EvoriaRoute
  data object ConnectPartner : EvoriaRoute
  data object ShareInbox : EvoriaRoute
  data object Notifications : EvoriaRoute
  data object Music : EvoriaRoute
  data object FullMusicPlayer : EvoriaRoute
  data object Security : EvoriaRoute
  data object StorageBackup : EvoriaRoute
  data object LiquidGlassShowcase : EvoriaRoute
  data object About : EvoriaRoute
}

class MainActivity : FragmentActivity() {

  private var pendingQuickShareUris by mutableStateOf<List<Uri>>(emptyList())
  private var pendingInviteCode by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // App Check must initialize before other Firebase calls
    configureAppCheck(this, intent)
    handleIncomingIntent(intent)

    val prefs = SecurityPreferences(applicationContext)

    setContent {
      val userPrefs by prefs.userPrefsFlow.collectAsStateWithLifecycle(initialValue = com.example.data.local.AppUserPrefs())

      DisposableEffect(userPrefs.lock60Fps) {
        val cancelScheduled =
          FrameRateController.scheduleFrameRateLock(this@MainActivity, userPrefs.lock60Fps)
        val unregisterObserver =
          FrameRateController.registerDisplayRefreshObserver(this@MainActivity) {
            userPrefs.lock60Fps
          }
        onDispose {
          cancelScheduled()
          unregisterObserver()
        }
      }

      EvoriaTheme(
        themeMode = userPrefs.themeMode,
        accentPreset = userPrefs.accentPreset,
      ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }
        val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

        LaunchedEffect(Unit) {
          if (Firebase.auth.currentUser == null) {
            kotlinx.coroutines.delay(200)
            attemptAutoSignIn(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {},
              onUnauthenticated = {},
              scope = scope,
            )
          }
        }

        val user = currentUser
        if (user == null) {
          OnboardingAndSignInScreen(
            onboardingAlreadyCompleted = userPrefs.onboardingCompleted,
            onCompleteOnboarding = {
              scope.launchNoThrow { prefs.setOnboardingCompleted(true) }
            },
            onAuthSuccess = {},
          )
        } else {
          val vm: EvoriaViewModel =
            viewModel(
              key = "evoria_vm_${user.uid}",
              factory =
                viewModelFactory {
                  initializer {
                    val appCtx = applicationContext
                    val dao = EvoriaDatabase.getInstance(appCtx).evoriaDao()
                    val localRepo = EvoriaSanctuaryRepository(appCtx, dao)
                    val cloudRepo = EvoriaCloudRepository(appCtx)
                    val searchEngine = EvoriaSearchEngine(appCtx)
                    val playerManager = EvoriaPlayerManager(appCtx)
                    EvoriaViewModel(
                      currentUserId = user.uid,
                      initialDisplayName = user.displayName ?: "Sahabat EVORIA",
                      localRepo = localRepo,
                      cloudRepo = cloudRepo,
                      prefs = prefs,
                      searchEngine = searchEngine,
                      playerManager = playerManager,
                      currentUserEmail = user.email,
                    )
                  }
                },
            )

          EvoriaAuthenticatedShell(
            uid = user.uid,
            currentUserEmail = user.email,
            fallbackName = user.displayName ?: "Sahabat EVORIA",
            viewModel = vm,
            pendingQuickShareUris = pendingQuickShareUris,
            onClearQuickShare = { pendingQuickShareUris = emptyList() },
            pendingInviteCode = pendingInviteCode,
            onConsumeInviteCode = { pendingInviteCode = null },
            onSignOut = {
              signOut(credentialManager = credentialManager, scope = scope)
            },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return
    when (intent.action) {
      Intent.ACTION_SEND -> {
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (uri != null) {
          pendingQuickShareUris = listOf(uri)
        }
      }
      Intent.ACTION_SEND_MULTIPLE -> {
        val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (!uris.isNullOrEmpty()) {
          pendingQuickShareUris = uris
        }
      }
      Intent.ACTION_VIEW -> {
        val data = intent.data
        if (data?.scheme == "evoria" && data.host == "invite") {
          val code = data.getQueryParameter("code")?.trim()?.uppercase()
          if (!code.isNullOrBlank()) {
            pendingInviteCode = code
          }
        }
      }
    }
  }
}

private fun kotlinx.coroutines.CoroutineScope.launchNoThrow(block: suspend () -> Unit) {
  launch(kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }) { block() }
}

@Composable
private fun EvoriaAuthenticatedShell(
  uid: String,
  currentUserEmail: String?,
  fallbackName: String,
  viewModel: EvoriaViewModel,
  pendingQuickShareUris: List<Uri>,
  onClearQuickShare: () -> Unit,
  pendingInviteCode: String?,
  onConsumeInviteCode: () -> Unit,
  onSignOut: () -> Unit,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val userPrefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val activeTabIndex by viewModel.activeTabIndex.collectAsStateWithLifecycle()
  val spotifyImportState by viewModel.spotifyImportState.collectAsStateWithLifecycle()
  val youtubeMatchingPlaylistId by viewModel.youtubeMatchingPlaylistId.collectAsStateWithLifecycle()
  val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
  val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
  val isProfileResolved by viewModel.isProfileResolved.collectAsStateWithLifecycle()
  val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
  val activeCouple by viewModel.activeCouple.collectAsStateWithLifecycle()
  val myInvitations by viewModel.myInvitations.collectAsStateWithLifecycle()
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val sharedMemories by viewModel.sharedMemories.collectAsStateWithLifecycle()
  val shareRequests by viewModel.shareRequests.collectAsStateWithLifecycle()
  val uploadJobs by viewModel.uploadJobs.collectAsStateWithLifecycle()
  val journals by viewModel.journals.collectAsStateWithLifecycle()
  val letters by viewModel.letters.collectAsStateWithLifecycle()
  val moods by viewModel.moods.collectAsStateWithLifecycle()
  val reflectionAnswers by viewModel.reflectionAnswers.collectAsStateWithLifecycle()
  val capsules by viewModel.timeCapsules.collectAsStateWithLifecycle()
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  val localNotifications by viewModel.localNotifications.collectAsStateWithLifecycle()
  val remoteNotifications by viewModel.remoteNotifications.collectAsStateWithLifecycle()
  val syncCheckpoint by viewModel.syncCheckpoint.collectAsStateWithLifecycle()
  val localStorageLabel by viewModel.localStorageLabel.collectAsStateWithLifecycle()

  val bloomMessage by viewModel.bloomMessage.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
  val generatedInvitation by viewModel.generatedInvitation.collectAsStateWithLifecycle()
  val lookedUpInvitation by viewModel.lookedUpInvitation.collectAsStateWithLifecycle()
  val inviteLoading by viewModel.inviteLoading.collectAsStateWithLifecycle()
  val inviteError by viewModel.inviteActionError.collectAsStateWithLifecycle()
  val searchState by viewModel.searchState.collectAsStateWithLifecycle()

  var currentRoute by remember { mutableStateOf<EvoriaRoute>(EvoriaRoute.MainTabs) }
  var quickOpenApplied by rememberSaveable(uid) { mutableStateOf(false) }
  var favoritesOnlyKenangan by remember { mutableStateOf(false) }
  var profileSetupDismissed by rememberSaveable(uid) { mutableStateOf(false) }
  var deepLinkCodeForScreen by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(userPrefs.quickOpenShowcaseOnLaunch) {
    if (userPrefs.quickOpenShowcaseOnLaunch && !quickOpenApplied) {
      quickOpenApplied = true
      currentRoute = EvoriaRoute.LiquidGlassShowcase
    }
  }

  val snackbarHostState = remember { SnackbarHostState() }

  // Observe lifecycle for App Lock enforcement
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
        Lifecycle.Event.ON_START -> viewModel.evaluateAutoLockOnResume()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Also check lock at initial cold launch when appLock is enabled
  LaunchedEffect(securitySettings.appLockEnabled, securitySettings.hasPinConfigured) {
    if (securitySettings.appLockEnabled && securitySettings.hasPinConfigured && securitySettings.lastBackgroundMillis == 0L) {
      viewModel.evaluateAutoLockOnResume()
    }
  }

  LaunchedEffect(statusBanner) {
    val msg = statusBanner
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.dismissStatusBanner()
    }
  }

  LaunchedEffect(pendingInviteCode) {
    if (!pendingInviteCode.isNullOrBlank()) {
      deepLinkCodeForScreen = pendingInviteCode
      onConsumeInviteCode()
      currentRoute = EvoriaRoute.ConnectPartner
    }
  }

  val unreadNotifs =
    localNotifications.count { !it.isRead } + remoteNotifications.count { !it.isRead }
  val pendingSharesCount =
    shareRequests.count { it.status == "pending" && it.recipientUid == uid }

  // 1. Security App Lock Overlay (blocks everything including Quick Share & navbar when locked)
  if (isLocked) {
    AppLockScreen(
      securitySettings = securitySettings,
      onVerifyPin = viewModel::verifyPin,
      onUnlockSuccess = viewModel::unlockSucceeded,
    )
    return
  }

  // 2. Android Quick Share Flow
  if (pendingQuickShareUris.isNotEmpty()) {
    QuickShareScreen(
      sharedUris = pendingQuickShareUris,
      albums = albums,
      hasConnectedPartner = activeCouple != null,
      onCreateInlineAlbum = { title, vis ->
        viewModel.createOrUpdateAlbum(
          title = title,
          description = "",
          visibility = vis,
          soundtrackTitle = null,
        )
      },
      onConfirmSave = { uris, title, caption, albumId, visibility ->
        viewModel.saveNewMemoriesFromUris(
          uris = uris,
          titlePrefix = title,
          caption = caption,
          story = "",
          locationName = "",
          albumId = albumId,
          visibility = visibility,
          tagsCsv = "",
          onDone = onClearQuickShare,
        )
      },
      onCancel = onClearQuickShare,
    )
    return
  }

  // 3. First-time Firestore Profile Setup (only after profile snapshot resolves as missing)
  if (isProfileResolved && userProfile == null && !profileSetupDismissed && currentRoute == EvoriaRoute.MainTabs) {
    FirstProfileSetupDialogContent(
      initialName = fallbackName,
      onSaveProfile = { name, nickname ->
        viewModel.saveProfileSetup(name, nickname, null)
        profileSetupDismissed = true
      },
      onOpenConnectPartner = {
        profileSetupDismissed = true
        currentRoute = EvoriaRoute.ConnectPartner
      },
      onSkip = {
        profileSetupDismissed = true
      },
    )
    return
  }

  // 4. Route Switcher
  Box(modifier = Modifier.fillMaxSize()) {
    when (val route = currentRoute) {
      EvoriaRoute.MainTabs -> {
        Scaffold(
          contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
          EvoriaSanctuaryBackdrop(useWarmTreePhoto = (activeTabIndex == 0)) {
            Box(
              modifier =
                Modifier
                  .fillMaxSize()
                  .padding(innerPadding)
            ) {
              when (activeTabIndex) {
                0 ->
                  BerandaScreen(
                    profile = userProfile,
                    fallbackName = fallbackName,
                    couple = activeCouple,
                    localMemories = memories,
                    sharedMemories = sharedMemories,
                    journals = journals,
                    uploadJobs = uploadJobs,
                    unreadNotifCount = unreadNotifs,
                    lastViewedMemoryId = userPrefs.lastViewedMemoryId,
                    onOpenAddMemory = { currentRoute = EvoriaRoute.AddOrEditMemory(null) },
                    onOpenConnectPartner = { currentRoute = EvoriaRoute.ConnectPartner },
                    onOpenMemoryDetail = { id ->
                      viewModel.setLastViewedMemory(id)
                      currentRoute = EvoriaRoute.MemoryDetail(id)
                    },
                    onOpenFavorites = {
                      favoritesOnlyKenangan = true
                      viewModel.setActiveTab(1)
                    },
                    onOpenNotifications = { currentRoute = EvoriaRoute.Notifications },
                    onOpenUploadQueue = { currentRoute = EvoriaRoute.UploadQueue },
                    onOpenMusic = { currentRoute = EvoriaRoute.Music },
                    onOpenRefleksi = { viewModel.setActiveTab(3) },
                  )

                1 ->
                  KenanganScreen(
                    albums = albums,
                    memories = memories,
                    favoritesOnlyDefault = favoritesOnlyKenangan,
                    onConsumeFavoritesDefault = { favoritesOnlyKenangan = false },
                    onOpenAlbum = { id -> currentRoute = EvoriaRoute.AlbumDetail(id) },
                    onOpenMemory = { id ->
                      viewModel.setLastViewedMemory(id)
                      currentRoute = EvoriaRoute.MemoryDetail(id)
                    },
                    onOpenAddMemory = { currentRoute = EvoriaRoute.AddOrEditMemory(null) },
                    onImportGalleryPhotos = { uris ->
                      viewModel.saveNewMemoriesFromUris(
                        uris = uris,
                        titlePrefix = "Galeri Kita",
                        caption = "",
                        story = "",
                        locationName = "",
                        albumId = null,
                        visibility = "private",
                        tagsCsv = "galeri",
                      )
                    },
                    onSaveAlbum = { existingId, title, desc, vis, soundtrack, coverUri, existingCover ->
                      viewModel.createOrUpdateAlbum(existingId, title, desc, vis, soundtrack, coverUri, existingCover)
                    },
                    onToggleFavorite = viewModel::toggleMemoryFavorite,
                    onDeleteMemories = { list -> list.forEach { viewModel.deleteMemory(it) } },
                  )

                2 ->
                  JelajahScreen(
                    memories = memories,
                    albums = albums,
                    searchHistory = userPrefs.searchHistory,
                    aiConsentGranted = userPrefs.aiSearchConsent,
                    searchState = searchState,
                    onToggleAiConsent = viewModel::setAiSearchConsent,
                    onRunSearch = viewModel::runSearch,
                    onClearHistory = viewModel::clearSearchHistory,
                    onOpenMemory = { id ->
                      viewModel.setLastViewedMemory(id)
                      currentRoute = EvoriaRoute.MemoryDetail(id)
                    },
                  )

                3 ->
                  RefleksiScreen(
                    journals = journals,
                    letters = letters,
                    moods = moods,
                    reflectionAnswers = reflectionAnswers,
                    capsules = capsules,
                    onSaveJournal = viewModel::saveJournal,
                    onDeleteJournal = viewModel::deleteJournal,
                    onSaveLetter = viewModel::saveLetter,
                    onDeleteLetter = viewModel::deleteLetter,
                    onRecordMood = viewModel::recordMood,
                    onSaveReflectionAnswer = viewModel::saveReflectionAnswer,
                    onCreateTimeCapsule = viewModel::createTimeCapsule,
                    onReadEnforcedCapsule = viewModel::readEnforcedCapsule,
                    onOpenMusic = { currentRoute = EvoriaRoute.Music },
                  )

                else ->
                  ProfilScreen(
                    currentUid = uid,
                    profile = userProfile,
                    fallbackName = fallbackName,
                    couple = activeCouple,
                    albumCount = albums.size,
                    memoryCount = memories.size,
                    journalCount = journals.size,
                    capsuleCount = capsules.size,
                    pendingShareRequestsCount = pendingSharesCount,
                    unreadNotificationCount = unreadNotifs,
                    localStorageLabel = localStorageLabel,
                    userPrefs = userPrefs,
                    securitySettings = securitySettings,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSetAccentPreset = viewModel::setAccentPreset,
                    onSetNavbarThemeStyle = viewModel::setNavbarThemeStyle,
                    onSetNavbarGlassIntensity = viewModel::setNavbarGlassIntensity,
                    onSetReducedMotion = viewModel::setReducedMotion,
                    onSetLock60Fps = viewModel::setLock60Fps,
                    onSaveProfile = viewModel::saveProfileSetup,
                    onDeleteAllLocalData = viewModel::clearAllLocalData,
                    onOpenConnectPartner = { currentRoute = EvoriaRoute.ConnectPartner },
                    onOpenShareInbox = { currentRoute = EvoriaRoute.ShareInbox },
                    onOpenNotifications = { currentRoute = EvoriaRoute.Notifications },
                    onOpenMusic = { currentRoute = EvoriaRoute.Music },
                    onOpenUploadQueue = { currentRoute = EvoriaRoute.UploadQueue },
                    onOpenSecurity = { currentRoute = EvoriaRoute.Security },
                    onOpenBackupStorage = { currentRoute = EvoriaRoute.StorageBackup },
                    onOpenLiquidGlassShowcase = { currentRoute = EvoriaRoute.LiquidGlassShowcase },
                    onOpenAbout = { currentRoute = EvoriaRoute.About },
                    onSignOut = onSignOut,
                  )
              }

              // Bottom Persistent Mini-Player (isolated so 500ms playback ticks never recompose tabs/navbar) + Signature Liquid Glass Navbar
              Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                EvoriaFloatingMiniPlayerOverlay(
                  viewModel = viewModel,
                  onOpenFullPlayer = { currentRoute = EvoriaRoute.FullMusicPlayer },
                )
                LiquidGlassNavbar(
                  selectedTabIndex = activeTabIndex,
                  onTabSelected = { idx ->
                    if (idx != 1) favoritesOnlyKenangan = false
                    viewModel.setActiveTab(idx)
                  },
                  reducedMotionOverride = userPrefs.reducedMotionOverride,
                  navbarThemeStyle = userPrefs.navbarThemeStyle,
                  navbarGlassIntensity = userPrefs.navbarGlassIntensity,
                  applyNavigationBarInsets = false,
                )
              }
            }
          }
        }
      }

      is EvoriaRoute.AlbumDetail -> {
        val album = albums.firstOrNull { it.id == route.albumId }
        if (album == null) {
          LaunchedEffect(Unit) { currentRoute = EvoriaRoute.MainTabs }
        } else {
          AlbumDetailScreen(
            album = album,
            memories = memories.filter { it.albumId == album.id },
            onBack = { currentRoute = EvoriaRoute.MainTabs },
            onOpenMemory = { id ->
              viewModel.setLastViewedMemory(id)
              currentRoute = EvoriaRoute.MemoryDetail(id)
            },
            onEditAlbum = { t, d, v, s, c ->
              viewModel.createOrUpdateAlbum(album.id, t, d, v, s, c, album.coverUri)
            },
            onDeleteAlbum = {
              viewModel.deleteAlbum(album.id)
              currentRoute = EvoriaRoute.MainTabs
            },
          )
        }
      }

      is EvoriaRoute.MemoryDetail -> {
        val mem = memories.firstOrNull { it.id == route.memoryId }
        if (mem == null) {
          LaunchedEffect(Unit) { currentRoute = EvoriaRoute.MainTabs }
        } else {
          MemoryDetailScreen(
            memory = mem,
            onBack = { currentRoute = EvoriaRoute.MainTabs },
            onToggleFavorite = { viewModel.toggleMemoryFavorite(mem) },
            onEdit = { currentRoute = EvoriaRoute.AddOrEditMemory(mem.id) },
            onDelete = {
              viewModel.deleteMemory(mem)
              currentRoute = EvoriaRoute.MainTabs
            },
            onSendEvoriaShare = { viewModel.sendShareRequestToPartner(mem) },
            onOpenFullscreenPhoto = {
              mem.mediaUri?.let { uri ->
                currentRoute = EvoriaRoute.FullscreenPhoto(uri, mem.title)
              }
            },
            onOpenFullscreenVideo = {
              mem.mediaUri?.let { uri ->
                currentRoute = EvoriaRoute.FullscreenVideo(uri)
              }
            },
          )
        }
      }

      is EvoriaRoute.AddOrEditMemory -> {
        val existing: LocalMemoryEntity? =
          route.memoryId?.let { id -> memories.firstOrNull { it.id == id } }
        AddOrEditMemoryScreen(
          existing = existing,
          albums = albums,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
          onSaveMediaMemories = { uris, title, caption, story, location, albumId, visibility, tagsCsv ->
            viewModel.saveNewMemoriesFromUris(
              uris = uris,
              titlePrefix = title,
              caption = caption,
              story = story,
              locationName = location,
              albumId = albumId,
              visibility = visibility,
              tagsCsv = tagsCsv,
              onDone = { currentRoute = EvoriaRoute.MainTabs },
            )
          },
          onSaveStoryOnly = { ex, title, caption, story, location, albumId, visibility, tagsCsv ->
            viewModel.saveStoryOnlyMemory(
              existing = ex,
              title = title,
              caption = caption,
              story = story,
              locationName = location,
              albumId = albumId,
              visibility = visibility,
              tagsCsv = tagsCsv,
            )
            currentRoute = EvoriaRoute.MainTabs
          },
        )
      }

      is EvoriaRoute.FullscreenPhoto -> {
        FullscreenPhotoViewer(
          mediaUri = route.uri,
          title = route.title,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      is EvoriaRoute.FullscreenVideo -> {
        FullscreenVideoViewer(
          mediaUri = route.uri,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.UploadQueue -> {
        UploadQueueScreen(
          jobs = uploadJobs,
          onRetry = viewModel::retryUploadJob,
          onDelete = viewModel::deleteUploadJob,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.ConnectPartner -> {
        PartnerConnectionScreen(
          currentUid = uid,
          couple = activeCouple,
          generatedInvitation = generatedInvitation,
          myInvitations = myInvitations,
          lookedUpInvitation = lookedUpInvitation,
          inviteLoading = inviteLoading,
          inviteError = inviteError,
          initialCodeFromDeepLink = deepLinkCodeForScreen,
          onGenerateCode = viewModel::generatePartnerCode,
          onCancelInvitation = viewModel::cancelInvitation,
          onLookupCode = viewModel::lookupInvitationCode,
          onAcceptInvitation = viewModel::acceptLookedUpInvitation,
          onDeclineInvitation = viewModel::declineLookedUpInvitation,
          onClearLookup = {
            deepLinkCodeForScreen = null
            viewModel.clearLookedUpInvitation()
          },
          onDisconnectCouple = viewModel::disconnectCouple,
          onBack = {
            deepLinkCodeForScreen = null
            currentRoute = EvoriaRoute.MainTabs
          },
        )
      }

      EvoriaRoute.ShareInbox -> {
        ShareInboxScreen(
          currentUid = uid,
          requests = shareRequests,
          onAccept = { viewModel.respondShareRequest(it, accept = true) },
          onDecline = { viewModel.respondShareRequest(it, accept = false) },
          onRevoke = viewModel::revokeShareRequest,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.Notifications -> {
        NotificationCenterScreen(
          localNotifications = localNotifications,
          remoteNotifications = remoteNotifications,
          userPrefs = userPrefs,
          onUpdatePrefs = viewModel::updateNotificationPrefs,
          onMarkAllRead = viewModel::markAllNotificationsRead,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.Music -> {
        EvoriaMusicRouteContent(
          viewModel = viewModel,
          playlists = playlists,
          tracks = tracks,
          albums = albums,
          spotifyImportState = spotifyImportState,
          matchingYouTubePlaylistId = youtubeMatchingPlaylistId,
          onOpenFullPlayer = { currentRoute = EvoriaRoute.FullMusicPlayer },
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.FullMusicPlayer -> {
        EvoriaFullMusicPlayerRouteContent(
          viewModel = viewModel,
          onBack = { currentRoute = EvoriaRoute.Music },
        )
      }

      EvoriaRoute.Security -> {
        SecurityAndAppLockScreen(
          securitySettings = securitySettings,
          onConfigurePin = viewModel::configurePin,
          onVerifyExistingPin = viewModel::verifyPin,
          onDisableLock = viewModel::disableAppLock,
          onSelectMethod = viewModel::setPreferredLockMethod,
          onSelectAutoLock = viewModel::setAutoLockTimeout,
          onLockNow = {
            viewModel.lockNow()
            currentRoute = EvoriaRoute.MainTabs
          },
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.StorageBackup -> {
        StorageAndBackupScreen(
          localStorageLabel = localStorageLabel,
          checkpoint = syncCheckpoint,
          onBackupToCloud = { viewModel.backupMetadataToCloud() },
          onRestoreFromCloud = viewModel::restoreLatestCloudBackup,
          onGenerateExportJson = viewModel::generateExportJson,
          onDeleteAllLocalData = viewModel::clearAllLocalData,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.LiquidGlassShowcase -> {
        LiquidGlassShowcaseScreen(
          quickOpenOnLaunch = userPrefs.quickOpenShowcaseOnLaunch,
          onToggleQuickOpenOnLaunch = { enabled ->
            quickOpenApplied = true
            viewModel.setQuickOpenShowcase(enabled)
          },
          initialNavbarStyle = userPrefs.navbarThemeStyle,
          initialNavbarIntensity = userPrefs.navbarGlassIntensity,
          accentPreset = userPrefs.accentPreset,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.About -> {
        AboutEvoriaScreen(
          userPrefs = userPrefs,
          currentUserEmail = currentUserEmail,
          onSaveDeveloperSocials = viewModel::updateDeveloperSocials,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }
    }

    // Gentle full-screen warm Cloud Bloom ONLY after successful memory or album save
    CloudBloomOverlay(
      visible = bloomMessage != null,
      message = bloomMessage.orEmpty(),
      reducedMotion = userPrefs.reducedMotionOverride,
      onFinished = viewModel::dismissBloom,
    )

    SnackbarHost(
      hostState = snackbarHostState,
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(bottom = 88.dp, start = 16.dp, end = 16.dp),
    )
  }
}

@Composable
private fun EvoriaFloatingMiniPlayerOverlay(
  viewModel: EvoriaViewModel,
  onOpenFullPlayer: () -> Unit,
) {
  val playerState by viewModel.playerState.collectAsStateWithLifecycle()
  if (playerState.currentTrack != null) {
    EvoriaMiniPlayerBar(
      playerState = playerState,
      onTogglePlay = { viewModel.playerManager.togglePlayPause() },
      onNext = { viewModel.playerManager.next() },
      onOpenFullPlayer = onOpenFullPlayer,
    )
    Spacer(modifier = Modifier.height(6.dp))
  }
}

@Composable
private fun EvoriaMusicRouteContent(
  viewModel: EvoriaViewModel,
  playlists: List<com.example.data.local.PlaylistEntity>,
  tracks: List<com.example.data.local.TrackEntity>,
  albums: List<com.example.data.local.LocalAlbumEntity>,
  spotifyImportState: com.example.ui.SpotifyImportUiState,
  matchingYouTubePlaylistId: String?,
  onOpenFullPlayer: () -> Unit,
  onBack: () -> Unit,
) {
  val playerState by viewModel.playerState.collectAsStateWithLifecycle()
  MusicAndSoundtrackScreen(
    playlists = playlists,
    tracks = tracks,
    albums = albums,
    playerState = playerState,
    spotifyImportState = spotifyImportState,
    matchingYouTubePlaylistId = matchingYouTubePlaylistId,
    onCreatePlaylist = viewModel::createPlaylist,
    onDeletePlaylist = viewModel::deletePlaylist,
    onImportSpotifyPlaylist = viewModel::importSpotifyPlaylist,
    onResetSpotifyImport = viewModel::resetSpotifyImportState,
    onMatchPlaylistWithYouTube = viewModel::matchPlaylistTracksWithYouTube,
    onMatchTrackWithYouTube = viewModel::matchSingleTrackWithYouTube,
    onAddAudioTrack = { plId, title, artist, uri ->
      viewModel.addLocalAudioTrack(plId, title, artist, uri, null)
    },
    onAttachLocalAudioToTrack = viewModel::attachLocalAudioToTrack,
    onBatchAttachLocalAudio = viewModel::batchAttachLocalAudioToPlaylist,
    onMoveTrackOrder = viewModel::moveTrackOrder,
    onDeleteTrack = viewModel::deleteTrack,
    onPlayTrack = viewModel::playPlaylistTrack,
    onOpenFullPlayer = onOpenFullPlayer,
    onBack = onBack,
  )
}

@Composable
private fun EvoriaFullMusicPlayerRouteContent(
  viewModel: EvoriaViewModel,
  onBack: () -> Unit,
) {
  val playerState by viewModel.playerState.collectAsStateWithLifecycle()
  FullMusicPlayerScreen(
    playerState = playerState,
    youtubeCommands = viewModel.playerManager.youtubeCommands,
    onYouTubePlaybackUpdate = viewModel.playerManager::onYouTubePlaybackUpdate,
    onYouTubeVideoEnded = viewModel.playerManager::onYouTubeVideoEnded,
    onYouTubePlayerError = viewModel.playerManager::onYouTubePlayerError,
    onLeavingYouTubePlayer = viewModel.playerManager::pauseYouTubeIfLeavingPlayer,
    onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
    onSeekTo = { viewModel.playerManager.seekTo(it) },
    onNext = { viewModel.playerManager.next() },
    onPrevious = { viewModel.playerManager.previous() },
    onBack = onBack,
  )
}
