package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.example.ml.EvoriaVisionRuntime
import com.example.notifications.EvoriaNotificationHelper
import com.example.ui.EvoriaViewModel
import com.example.ui.components.CloudBloomOverlay
import com.example.ui.components.EvoriaMiniPlayerBar
import com.example.ui.components.EvoriaSanctuaryBackdrop
import com.example.ui.components.LiquidGlassNavbar
import com.example.ui.screens.AboutEvoriaScreen
import com.example.ui.screens.AddOrEditMemoryScreen
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AppearanceSettingsScreen
import com.example.ui.screens.BerandaScreen
import com.example.ui.screens.FirstProfileSetupDialogContent
import com.example.ui.screens.FullMusicPlayerScreen
import com.example.ui.screens.FullscreenPhotoViewer
import com.example.ui.screens.FullscreenVideoViewer
import com.example.ui.screens.JelajahScreen
import com.example.ui.screens.KenanganDetailScreen
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
import com.example.ui.screens.YouTubeFullTrackEmbedPlayer
import com.example.ui.theme.EvoriaTheme
import com.example.util.FrameRateController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private sealed interface EvoriaRoute {
  data object MainTabs : EvoriaRoute
  data class AlbumDetail(val albumId: String) : EvoriaRoute
  data class KenanganDetail(val collectionId: String) : EvoriaRoute
  data class MemoryDetail(val memoryId: String) : EvoriaRoute
  data class AddOrEditMemory(val memoryId: String?) : EvoriaRoute
  data class FullscreenPhoto(val uri: String, val title: String, val memoryId: String? = null) : EvoriaRoute
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
  data object Appearance : EvoriaRoute
  data object About : EvoriaRoute
}

class MainActivity : FragmentActivity() {

  private var pendingQuickShareUris by mutableStateOf<List<Uri>>(emptyList())
  private var pendingInviteCode by mutableStateOf<String?>(null)
  private var pendingNotifRoute by mutableStateOf<String?>(null)

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
        customAccentHex = userPrefs.customAccentHex,
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
                    val modelManager = EvoriaVisionRuntime.modelManager(appCtx)
                    val embeddingEngine = EvoriaVisionRuntime.embeddingEngine(appCtx)
                    val searchEngine =
                      EvoriaSearchEngine(
                        context = appCtx,
                        embeddingEngine = embeddingEngine,
                        modelManager = modelManager,
                        sanctuaryRepo = localRepo,
                        ownerUidProvider = { user.uid },
                      )
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
                      appContext = appCtx,
                      modelManager = modelManager,
                      embeddingEngine = embeddingEngine,
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
            pendingNotifRoute = pendingNotifRoute,
            onConsumeNotifRoute = { pendingNotifRoute = null },
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
    val notifRoute = intent.getStringExtra(EvoriaNotificationHelper.EXTRA_NOTIF_ROUTE)
    if (!notifRoute.isNullOrBlank()) {
      pendingNotifRoute = notifRoute
    }
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
  pendingNotifRoute: String?,
  onConsumeNotifRoute: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val userPrefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val activeTabIndex by viewModel.activeTabIndex.collectAsStateWithLifecycle()
  val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
  val isSecurityResolved by viewModel.isSecurityResolved.collectAsStateWithLifecycle()
  val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
  val isProfileResolved by viewModel.isProfileResolved.collectAsStateWithLifecycle()
  val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
  val activeCouple by viewModel.activeCouple.collectAsStateWithLifecycle()
  val playerState by viewModel.playerState.collectAsStateWithLifecycle()
  val bloomMessage by viewModel.bloomMessage.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

  var currentRoute by remember { mutableStateOf<EvoriaRoute>(EvoriaRoute.MainTabs) }
  var returnRouteFromFullPlayer by remember { mutableStateOf<EvoriaRoute>(EvoriaRoute.MainTabs) }
  var quickOpenApplied by rememberSaveable(uid) { mutableStateOf(false) }
  var favoritesOnlyKenangan by remember { mutableStateOf(false) }
  var profileSetupDismissed by rememberSaveable(uid) { mutableStateOf(false) }
  var deepLinkCodeForScreen by remember { mutableStateOf<String?>(null) }
  var youtubeSlotBounds by remember { mutableStateOf<Rect?>(null) }

  val notifPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

  LaunchedEffect(uid) {
    if (Build.VERSION.SDK_INT >= 33) {
      val hasPerm =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!hasPerm) {
        val permPrefs = context.getSharedPreferences("evoria_perm_prompts", android.content.Context.MODE_PRIVATE)
        if (!permPrefs.getBoolean("notif_prompted", false)) {
          permPrefs.edit().putBoolean("notif_prompted", true).apply()
          notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      }
    }
  }

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

  LaunchedEffect(currentRoute) {
    if (currentRoute != EvoriaRoute.FullMusicPlayer) {
      youtubeSlotBounds = null
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

  LaunchedEffect(pendingNotifRoute) {
    val target = pendingNotifRoute ?: return@LaunchedEffect
    onConsumeNotifRoute()
    currentRoute =
      when (target) {
        "share_inbox" -> EvoriaRoute.ShareInbox
        "partner" -> EvoriaRoute.ConnectPartner
        "full_music_player" -> {
          returnRouteFromFullPlayer = EvoriaRoute.MainTabs
          EvoriaRoute.FullMusicPlayer
        }
        else -> EvoriaRoute.Notifications
      }
  }

  // 0. Wait for Security DataStore resolution on cold launch so locked apps never bypass PIN/biometric gate
  if (!isSecurityResolved) {
    EvoriaSanctuaryBackdrop {
      Box(
        modifier = Modifier.fillMaxSize().testTag("security_gate_loading"),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    }
    return
  }

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
    QuickShareRouteHost(
      viewModel = viewModel,
      sharedUris = pendingQuickShareUris,
      hasConnectedPartner = activeCouple != null,
      onClearQuickShare = onClearQuickShare,
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
                  BerandaTabHost(
                    viewModel = viewModel,
                    userProfile = userProfile,
                    fallbackName = fallbackName,
                    activeCouple = activeCouple,
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
                  KenanganTabHost(
                    viewModel = viewModel,
                    favoritesOnlyKenangan = favoritesOnlyKenangan,
                    onConsumeFavoritesDefault = { favoritesOnlyKenangan = false },
                    onOpenAlbum = { id -> currentRoute = EvoriaRoute.AlbumDetail(id) },
                    onOpenKenangan = { id -> currentRoute = EvoriaRoute.KenanganDetail(id) },
                    onOpenMemory = { id ->
                      viewModel.setLastViewedMemory(id)
                      currentRoute = EvoriaRoute.MemoryDetail(id)
                    },
                    onOpenAddMemory = { currentRoute = EvoriaRoute.AddOrEditMemory(null) },
                  )

                2 ->
                  JelajahTabHost(
                    viewModel = viewModel,
                    searchHistory = userPrefs.searchHistory,
                    aiConsentGranted = userPrefs.aiSearchConsent,
                    onOpenMemory = { id ->
                      viewModel.setLastViewedMemory(id)
                      currentRoute = EvoriaRoute.MemoryDetail(id)
                    },
                  )

                3 ->
                  RefleksiTabHost(
                    viewModel = viewModel,
                    currentUid = uid,
                    aiConsentGranted = userPrefs.aiSearchConsent,
                    onOpenMusic = { currentRoute = EvoriaRoute.Music },
                  )

                else ->
                  ProfilTabHost(
                    viewModel = viewModel,
                    currentUid = uid,
                    userProfile = userProfile,
                    fallbackName = fallbackName,
                    activeCouple = activeCouple,
                    userPrefs = userPrefs,
                    securitySettings = securitySettings,
                    onOpenConnectPartner = { currentRoute = EvoriaRoute.ConnectPartner },
                    onOpenShareInbox = { currentRoute = EvoriaRoute.ShareInbox },
                    onOpenNotifications = { currentRoute = EvoriaRoute.Notifications },
                    onOpenMusic = { currentRoute = EvoriaRoute.Music },
                    onOpenUploadQueue = { currentRoute = EvoriaRoute.UploadQueue },
                    onOpenSecurity = { currentRoute = EvoriaRoute.Security },
                    onOpenBackupStorage = { currentRoute = EvoriaRoute.StorageBackup },
                    onOpenLiquidGlassShowcase = { currentRoute = EvoriaRoute.LiquidGlassShowcase },
                    onOpenAppearanceSettings = { currentRoute = EvoriaRoute.Appearance },
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
                  onOpenFullPlayer = {
                    returnRouteFromFullPlayer = EvoriaRoute.MainTabs
                    currentRoute = EvoriaRoute.FullMusicPlayer
                  },
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
        AlbumDetailRouteHost(
          viewModel = viewModel,
          albumId = route.albumId,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
          onOpenMemory = { id ->
            viewModel.setLastViewedMemory(id)
            currentRoute = EvoriaRoute.MemoryDetail(id)
          },
        )
      }

      is EvoriaRoute.KenanganDetail -> {
        KenanganDetailRouteHost(
          viewModel = viewModel,
          collectionId = route.collectionId,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
          onOpenAlbum = { id -> currentRoute = EvoriaRoute.AlbumDetail(id) },
          onOpenMemory = { id ->
            viewModel.setLastViewedMemory(id)
            currentRoute = EvoriaRoute.MemoryDetail(id)
          },
        )
      }

      is EvoriaRoute.MemoryDetail -> {
        MemoryDetailRouteHost(
          viewModel = viewModel,
          memoryId = route.memoryId,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
          onEdit = { currentRoute = EvoriaRoute.AddOrEditMemory(route.memoryId) },
          onOpenFullscreenPhoto = { uri, title, memId ->
            currentRoute = EvoriaRoute.FullscreenPhoto(uri, title, memId)
          },
          onOpenFullscreenVideo = { uri ->
            currentRoute = EvoriaRoute.FullscreenVideo(uri)
          },
        )
      }

      is EvoriaRoute.AddOrEditMemory -> {
        AddOrEditMemoryRouteHost(
          viewModel = viewModel,
          memoryId = route.memoryId,
          aiConsentGranted = userPrefs.aiSearchConsent,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      is EvoriaRoute.FullscreenPhoto -> {
        FullscreenPhotoRouteHost(
          viewModel = viewModel,
          mediaUri = route.uri,
          title = route.title,
          memoryId = route.memoryId,
          onEdit = { id -> currentRoute = EvoriaRoute.AddOrEditMemory(id) },
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
        UploadQueueRouteHost(
          viewModel = viewModel,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.ConnectPartner -> {
        ConnectPartnerRouteHost(
          viewModel = viewModel,
          currentUid = uid,
          activeCouple = activeCouple,
          initialCodeFromDeepLink = deepLinkCodeForScreen,
          onClearDeepLink = { deepLinkCodeForScreen = null },
          onBack = {
            deepLinkCodeForScreen = null
            currentRoute = EvoriaRoute.MainTabs
          },
        )
      }

      EvoriaRoute.ShareInbox -> {
        ShareInboxRouteHost(
          viewModel = viewModel,
          currentUid = uid,
          onOpenFullPlayer = {
            returnRouteFromFullPlayer = EvoriaRoute.Music
            currentRoute = EvoriaRoute.FullMusicPlayer
          },
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.Notifications -> {
        NotificationsRouteHost(
          viewModel = viewModel,
          userPrefs = userPrefs,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.Music -> {
        EvoriaMusicRouteContent(
          viewModel = viewModel,
          hasConnectedPartner = activeCouple != null,
          onOpenFullPlayer = {
            returnRouteFromFullPlayer = EvoriaRoute.Music
            currentRoute = EvoriaRoute.FullMusicPlayer
          },
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.FullMusicPlayer -> {
        EvoriaFullMusicPlayerRouteContent(
          viewModel = viewModel,
          onYouTubeSlotBounds = { youtubeSlotBounds = it },
          onBack = { currentRoute = returnRouteFromFullPlayer },
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
        StorageBackupRouteHost(
          viewModel = viewModel,
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

      EvoriaRoute.Appearance -> {
        AppearanceSettingsScreen(
          userPrefs = userPrefs,
          onSetThemeMode = viewModel::setThemeMode,
          onSetAccentPreset = viewModel::setAccentPreset,
          onSetCustomAccentHex = viewModel::setCustomAccentHex,
          onSetNavbarThemeStyle = viewModel::setNavbarThemeStyle,
          onSetNavbarGlassIntensity = viewModel::setNavbarGlassIntensity,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }

      EvoriaRoute.About -> {
        AboutEvoriaScreen(
          userPrefs = userPrefs,
          currentUserEmail = currentUserEmail,
          onSaveDeveloperSocials = viewModel::updateDeveloperSocials,
          onSaveDeveloperAboutInfo = viewModel::updateDeveloperAboutProfile,
          onPickDeveloperBackground = viewModel::saveDeveloperBackground,
          onResetDeveloperBackground = viewModel::clearDeveloperBackground,
          onPickDeveloperAvatar = viewModel::saveDeveloperAvatar,
          onResetDeveloperAvatar = viewModel::clearDeveloperAvatar,
          onBack = { currentRoute = EvoriaRoute.MainTabs },
        )
      }
    }

    // Persistent YouTube Audio/Video WebView kept alive across all screens so music continues playing in background
    val activeYtId = playerState.activeYouTubeVideoId
    if (playerState.isYouTubeMode && !activeYtId.isNullOrBlank()) {
      val density = LocalDensity.current
      val bounds = youtubeSlotBounds
      val showInFullPlayer = currentRoute == EvoriaRoute.FullMusicPlayer && bounds != null
      val overlayModifier =
        if (showInFullPlayer && bounds != null) {
          with(density) {
            Modifier
              .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
              .size(bounds.width.toDp(), bounds.height.toDp())
              .clip(RoundedCornerShape(18.dp))
          }
        } else {
          Modifier.align(Alignment.BottomStart).size(1.dp).alpha(0.01f)
        }
      Box(modifier = overlayModifier) {
        YouTubeFullTrackEmbedPlayer(
          videoId = activeYtId,
          commands = viewModel.playerManager.youtubeCommands,
          onPlaybackUpdate = viewModel.playerManager::onYouTubePlaybackUpdate,
          onEnded = viewModel.playerManager::onYouTubeVideoEnded,
          onError = viewModel.playerManager::onYouTubePlayerError,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }

    // Gentle full-screen warm Cloud Bloom ONLY after successful memory, album, or kenangan creation
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
private fun QuickShareRouteHost(
  viewModel: EvoriaViewModel,
  sharedUris: List<Uri>,
  hasConnectedPartner: Boolean,
  onClearQuickShare: () -> Unit,
) {
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  QuickShareScreen(
    sharedUris = sharedUris,
    albums = albums,
    hasConnectedPartner = hasConnectedPartner,
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
}

@Composable
private fun BerandaTabHost(
  viewModel: EvoriaViewModel,
  userProfile: com.example.data.remote.UserProfileDto?,
  fallbackName: String,
  activeCouple: com.example.data.remote.CoupleConnectionDto?,
  lastViewedMemoryId: String?,
  onOpenAddMemory: () -> Unit,
  onOpenConnectPartner: () -> Unit,
  onOpenMemoryDetail: (String) -> Unit,
  onOpenFavorites: () -> Unit,
  onOpenNotifications: () -> Unit,
  onOpenUploadQueue: () -> Unit,
  onOpenMusic: () -> Unit,
  onOpenRefleksi: () -> Unit,
) {
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val sharedMemories by viewModel.sharedMemories.collectAsStateWithLifecycle()
  val journals by viewModel.journals.collectAsStateWithLifecycle()
  val uploadJobs by viewModel.uploadJobs.collectAsStateWithLifecycle()
  val localNotifications by viewModel.localNotifications.collectAsStateWithLifecycle()
  val remoteNotifications by viewModel.remoteNotifications.collectAsStateWithLifecycle()
  val unreadNotifs =
    remember(localNotifications, remoteNotifications) {
      localNotifications.count { !it.isRead } + remoteNotifications.count { !it.isRead }
    }
  BerandaScreen(
    profile = userProfile,
    fallbackName = fallbackName,
    couple = activeCouple,
    localMemories = memories,
    sharedMemories = sharedMemories,
    journals = journals,
    uploadJobs = uploadJobs,
    unreadNotifCount = unreadNotifs,
    lastViewedMemoryId = lastViewedMemoryId,
    onOpenAddMemory = onOpenAddMemory,
    onOpenConnectPartner = onOpenConnectPartner,
    onOpenMemoryDetail = onOpenMemoryDetail,
    onOpenFavorites = onOpenFavorites,
    onOpenNotifications = onOpenNotifications,
    onOpenUploadQueue = onOpenUploadQueue,
    onOpenMusic = onOpenMusic,
    onOpenRefleksi = onOpenRefleksi,
  )
}

@Composable
private fun KenanganTabHost(
  viewModel: EvoriaViewModel,
  favoritesOnlyKenangan: Boolean,
  onConsumeFavoritesDefault: () -> Unit,
  onOpenAlbum: (String) -> Unit,
  onOpenKenangan: (String) -> Unit,
  onOpenMemory: (String) -> Unit,
  onOpenAddMemory: () -> Unit,
) {
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val kenanganCollections by viewModel.kenanganCollections.collectAsStateWithLifecycle()
  KenanganScreen(
    albums = albums,
    memories = memories,
    kenanganCollections = kenanganCollections,
    favoritesOnlyDefault = favoritesOnlyKenangan,
    onConsumeFavoritesDefault = onConsumeFavoritesDefault,
    onOpenAlbum = onOpenAlbum,
    onOpenKenangan = onOpenKenangan,
    onCreateKenangan = viewModel::createKenanganCollection,
    onDeleteKenangan = viewModel::deleteKenanganCollection,
    onOpenMemory = onOpenMemory,
    onOpenAddMemory = onOpenAddMemory,
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
}

@Composable
private fun KenanganDetailRouteHost(
  viewModel: EvoriaViewModel,
  collectionId: String,
  onBack: () -> Unit,
  onOpenAlbum: (String) -> Unit,
  onOpenMemory: (String) -> Unit,
) {
  val collections by viewModel.kenanganCollections.collectAsStateWithLifecycle()
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val collection = remember(collections, collectionId) { collections.firstOrNull { it.id == collectionId } }
  if (collection == null) {
    LaunchedEffect(Unit) { onBack() }
  } else {
    KenanganDetailScreen(
      collection = collection,
      albums = albums,
      memories = memories,
      onBack = onBack,
      onOpenAlbum = onOpenAlbum,
      onOpenMemory = onOpenMemory,
      onDeleteKenangan = {
        viewModel.deleteKenanganCollection(collection.id)
        onBack()
      },
    )
  }
}

@Composable
private fun JelajahTabHost(
  viewModel: EvoriaViewModel,
  searchHistory: List<String>,
  aiConsentGranted: Boolean,
  onOpenMemory: (String) -> Unit,
) {
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  val searchState by viewModel.searchState.collectAsStateWithLifecycle()
  val modelState by viewModel.modelState.collectAsStateWithLifecycle()
  val indexingStats by viewModel.indexingStats.collectAsStateWithLifecycle()
  val isIndexingPhotos by viewModel.isIndexingPhotos.collectAsStateWithLifecycle()
  JelajahScreen(
    memories = memories,
    albums = albums,
    searchHistory = searchHistory,
    aiConsentGranted = aiConsentGranted,
    searchState = searchState,
    onToggleAiConsent = viewModel::setAiSearchConsent,
    onRunSearch = viewModel::runSearch,
    onClearHistory = viewModel::clearSearchHistory,
    onOpenMemory = onOpenMemory,
    modelState = modelState,
    indexingStats = indexingStats,
    isIndexingPhotos = isIndexingPhotos,
    onDownloadModel = viewModel::downloadVisionModel,
    onCancelModelDownload = viewModel::cancelVisionModelDownload,
    onDeleteModel = viewModel::deleteVisionModel,
    onTriggerIndexing = viewModel::triggerPhotoIndexing,
    onClearSearchState = viewModel::clearSearchState,
  )
}

@Composable
private fun RefleksiTabHost(
  viewModel: EvoriaViewModel,
  currentUid: String,
  aiConsentGranted: Boolean,
  onOpenMusic: () -> Unit,
) {
  val journals by viewModel.journals.collectAsStateWithLifecycle()
  val letters by viewModel.letters.collectAsStateWithLifecycle()
  val moods by viewModel.moods.collectAsStateWithLifecycle()
  val reflectionAnswers by viewModel.reflectionAnswers.collectAsStateWithLifecycle()
  val capsules by viewModel.timeCapsules.collectAsStateWithLifecycle()
  val sharedReflections by viewModel.sharedReflections.collectAsStateWithLifecycle()
  val aiReflectionState by viewModel.aiReflectionState.collectAsStateWithLifecycle()
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
    onOpenMusic = onOpenMusic,
    sharedReflections = sharedReflections,
    currentUid = currentUid,
    aiConsentGranted = aiConsentGranted,
    aiReflectionState = aiReflectionState,
    onGenerateAiReflection = viewModel::generateAiReflectionInsight,
  )
}

@Composable
private fun ProfilTabHost(
  viewModel: EvoriaViewModel,
  currentUid: String,
  userProfile: com.example.data.remote.UserProfileDto?,
  fallbackName: String,
  activeCouple: com.example.data.remote.CoupleConnectionDto?,
  userPrefs: com.example.data.local.AppUserPrefs,
  securitySettings: com.example.data.local.SecuritySettings,
  onOpenConnectPartner: () -> Unit,
  onOpenShareInbox: () -> Unit,
  onOpenNotifications: () -> Unit,
  onOpenMusic: () -> Unit,
  onOpenUploadQueue: () -> Unit,
  onOpenSecurity: () -> Unit,
  onOpenBackupStorage: () -> Unit,
  onOpenLiquidGlassShowcase: () -> Unit,
  onOpenAppearanceSettings: () -> Unit,
  onOpenAbout: () -> Unit,
  onSignOut: () -> Unit,
) {
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val journals by viewModel.journals.collectAsStateWithLifecycle()
  val capsules by viewModel.timeCapsules.collectAsStateWithLifecycle()
  val shareRequests by viewModel.shareRequests.collectAsStateWithLifecycle()
  val sharedMusic by viewModel.sharedMusic.collectAsStateWithLifecycle()
  val localNotifications by viewModel.localNotifications.collectAsStateWithLifecycle()
  val remoteNotifications by viewModel.remoteNotifications.collectAsStateWithLifecycle()
  val localStorageLabel by viewModel.localStorageLabel.collectAsStateWithLifecycle()
  val unreadNotifs =
    remember(localNotifications, remoteNotifications) {
      localNotifications.count { !it.isRead } + remoteNotifications.count { !it.isRead }
    }
  val pendingSharesCount =
    remember(shareRequests, sharedMusic, currentUid) {
      shareRequests.count { it.status == "pending" && it.recipientUid == currentUid } +
        sharedMusic.count { it.status == "pending" && it.recipientUid == currentUid }
    }
  ProfilScreen(
    currentUid = currentUid,
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
    onOpenConnectPartner = onOpenConnectPartner,
    onOpenShareInbox = onOpenShareInbox,
    onOpenNotifications = onOpenNotifications,
    onOpenMusic = onOpenMusic,
    onOpenUploadQueue = onOpenUploadQueue,
    onOpenSecurity = onOpenSecurity,
    onOpenBackupStorage = onOpenBackupStorage,
    onOpenLiquidGlassShowcase = onOpenLiquidGlassShowcase,
    onOpenAppearanceSettings = onOpenAppearanceSettings,
    onOpenAbout = onOpenAbout,
    onPickProfileBanner = viewModel::saveProfileBanner,
    onResetProfileBanner = viewModel::clearProfileBanner,
    onSignOut = onSignOut,
  )
}

@Composable
private fun AlbumDetailRouteHost(
  viewModel: EvoriaViewModel,
  albumId: String,
  onBack: () -> Unit,
  onOpenMemory: (String) -> Unit,
) {
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val album = remember(albums, albumId) { albums.firstOrNull { it.id == albumId } }
  val albumMemories = remember(memories, albumId) { memories.filter { it.albumId == albumId } }
  if (album == null) {
    LaunchedEffect(Unit) { onBack() }
  } else {
    AlbumDetailScreen(
      album = album,
      memories = albumMemories,
      onBack = onBack,
      onOpenMemory = onOpenMemory,
      onEditAlbum = { t, d, v, s, c ->
        viewModel.createOrUpdateAlbum(album.id, t, d, v, s, c, album.coverUri)
      },
      onDeleteAlbum = {
        viewModel.deleteAlbum(album.id)
        onBack()
      },
    )
  }
}

@Composable
private fun MemoryDetailRouteHost(
  viewModel: EvoriaViewModel,
  memoryId: String,
  onBack: () -> Unit,
  onEdit: () -> Unit,
  onOpenFullscreenPhoto: (String, String, String) -> Unit,
  onOpenFullscreenVideo: (String) -> Unit,
) {
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  val mem = remember(memories, memoryId) { memories.firstOrNull { it.id == memoryId } }
  val albumTitle = remember(albums, mem?.albumId) { albums.firstOrNull { it.id == mem?.albumId }?.title }
  if (mem == null) {
    LaunchedEffect(Unit) { onBack() }
  } else {
    MemoryDetailScreen(
      memory = mem,
      albumTitle = albumTitle,
      onBack = onBack,
      onToggleFavorite = { viewModel.toggleMemoryFavorite(mem) },
      onEdit = onEdit,
      onDelete = {
        viewModel.deleteMemory(mem)
        onBack()
      },
      onSendEvoriaShare = { viewModel.sendShareRequestToPartner(mem) },
      onOpenFullscreenPhoto = {
        mem.mediaUri?.let { uri -> onOpenFullscreenPhoto(uri, mem.title, mem.id) }
      },
      onOpenFullscreenVideo = {
        mem.mediaUri?.let { uri -> onOpenFullscreenVideo(uri) }
      },
    )
  }
}

@Composable
private fun FullscreenPhotoRouteHost(
  viewModel: EvoriaViewModel,
  mediaUri: String,
  title: String,
  memoryId: String?,
  onEdit: (String) -> Unit,
  onBack: () -> Unit,
) {
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  val mem = remember(memories, memoryId) { memoryId?.let { id -> memories.firstOrNull { it.id == id } } }
  val albumTitle = remember(albums, mem?.albumId) { albums.firstOrNull { it.id == mem?.albumId }?.title }
  FullscreenPhotoViewer(
    mediaUri = mediaUri,
    title = title,
    memory = mem,
    albumTitle = albumTitle,
    onToggleFavorite = mem?.let { { viewModel.toggleMemoryFavorite(it) } },
    onEdit = mem?.let { { onEdit(it.id) } },
    onDelete = mem?.let { { viewModel.deleteMemory(it) } },
    onBack = onBack,
  )
}

@Composable
private fun AddOrEditMemoryRouteHost(
  viewModel: EvoriaViewModel,
  memoryId: String?,
  aiConsentGranted: Boolean,
  onBack: () -> Unit,
) {
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  val aiMemoryDraftState by viewModel.aiMemoryDraftState.collectAsStateWithLifecycle()
  val existing: LocalMemoryEntity? =
    remember(memories, memoryId) { memoryId?.let { id -> memories.firstOrNull { it.id == id } } }
  AddOrEditMemoryScreen(
    existing = existing,
    albums = albums,
    aiDraftState = aiMemoryDraftState,
    aiConsentGranted = aiConsentGranted,
    onGenerateAiDraft = viewModel::generateAiMemoryStory,
    onClearAiDraft = viewModel::clearAiMemoryDraftState,
    onBack = {
      viewModel.clearAiMemoryDraftState()
      onBack()
    },
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
        onDone = {
          viewModel.clearAiMemoryDraftState()
          onBack()
        },
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
      viewModel.clearAiMemoryDraftState()
      onBack()
    },
  )
}

@Composable
private fun UploadQueueRouteHost(
  viewModel: EvoriaViewModel,
  onBack: () -> Unit,
) {
  val uploadJobs by viewModel.uploadJobs.collectAsStateWithLifecycle()
  val memories by viewModel.localMemories.collectAsStateWithLifecycle()
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  UploadQueueScreen(
    jobs = uploadJobs,
    memories = memories,
    albums = albums,
    onRetry = viewModel::retryUploadJob,
    onDelete = viewModel::deleteUploadJob,
    onBack = onBack,
  )
}

@Composable
private fun ConnectPartnerRouteHost(
  viewModel: EvoriaViewModel,
  currentUid: String,
  activeCouple: com.example.data.remote.CoupleConnectionDto?,
  initialCodeFromDeepLink: String?,
  onClearDeepLink: () -> Unit,
  onBack: () -> Unit,
) {
  val generatedInvitation by viewModel.generatedInvitation.collectAsStateWithLifecycle()
  val myInvitations by viewModel.myInvitations.collectAsStateWithLifecycle()
  val lookedUpInvitation by viewModel.lookedUpInvitation.collectAsStateWithLifecycle()
  val inviteLoading by viewModel.inviteLoading.collectAsStateWithLifecycle()
  val inviteError by viewModel.inviteActionError.collectAsStateWithLifecycle()
  PartnerConnectionScreen(
    currentUid = currentUid,
    couple = activeCouple,
    generatedInvitation = generatedInvitation,
    myInvitations = myInvitations,
    lookedUpInvitation = lookedUpInvitation,
    inviteLoading = inviteLoading,
    inviteError = inviteError,
    initialCodeFromDeepLink = initialCodeFromDeepLink,
    onGenerateCode = viewModel::generatePartnerCode,
    onCancelInvitation = viewModel::cancelInvitation,
    onLookupCode = viewModel::lookupInvitationCode,
    onAcceptInvitation = viewModel::acceptLookedUpInvitation,
    onDeclineInvitation = viewModel::declineLookedUpInvitation,
    onClearLookup = {
      onClearDeepLink()
      viewModel.clearLookedUpInvitation()
    },
    onDisconnectCouple = viewModel::disconnectCouple,
    onBack = onBack,
  )
}

@Composable
private fun ShareInboxRouteHost(
  viewModel: EvoriaViewModel,
  currentUid: String,
  onOpenFullPlayer: () -> Unit,
  onBack: () -> Unit,
) {
  val shareRequests by viewModel.shareRequests.collectAsStateWithLifecycle()
  val sharedMusic by viewModel.sharedMusic.collectAsStateWithLifecycle()
  ShareInboxScreen(
    currentUid = currentUid,
    requests = shareRequests,
    sharedMusic = sharedMusic,
    onAccept = { viewModel.respondShareRequest(it, accept = true) },
    onDecline = { viewModel.respondShareRequest(it, accept = false) },
    onRevoke = viewModel::revokeShareRequest,
    onAcceptSharedMusic = { item, playNow ->
      viewModel.respondSharedMusic(item, accept = true, playImmediately = playNow)
      if (playNow) onOpenFullPlayer()
    },
    onDeclineSharedMusic = { item ->
      viewModel.respondSharedMusic(item, accept = false)
    },
    onBack = onBack,
  )
}

@Composable
private fun NotificationsRouteHost(
  viewModel: EvoriaViewModel,
  userPrefs: com.example.data.local.AppUserPrefs,
  onBack: () -> Unit,
) {
  val localNotifications by viewModel.localNotifications.collectAsStateWithLifecycle()
  val remoteNotifications by viewModel.remoteNotifications.collectAsStateWithLifecycle()
  NotificationCenterScreen(
    localNotifications = localNotifications,
    remoteNotifications = remoteNotifications,
    userPrefs = userPrefs,
    onUpdatePrefs = viewModel::updateNotificationPrefs,
    onMarkAllRead = viewModel::markAllNotificationsRead,
    onBack = onBack,
  )
}

@Composable
private fun StorageBackupRouteHost(
  viewModel: EvoriaViewModel,
  onBack: () -> Unit,
) {
  val localStorageLabel by viewModel.localStorageLabel.collectAsStateWithLifecycle()
  val syncCheckpoint by viewModel.syncCheckpoint.collectAsStateWithLifecycle()
  StorageAndBackupScreen(
    localStorageLabel = localStorageLabel,
    checkpoint = syncCheckpoint,
    onBackupToCloud = { viewModel.backupMetadataToCloud() },
    onRestoreFromCloud = viewModel::restoreLatestCloudBackup,
    onGenerateExportJson = viewModel::generateExportJson,
    onDeleteAllLocalData = viewModel::clearAllLocalData,
    onBack = onBack,
  )
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
      onNext = viewModel::playNextTrack,
      onOpenFullPlayer = onOpenFullPlayer,
    )
    Spacer(modifier = Modifier.height(6.dp))
  }
}

@Composable
private fun EvoriaMusicRouteContent(
  viewModel: EvoriaViewModel,
  hasConnectedPartner: Boolean,
  onOpenFullPlayer: () -> Unit,
  onBack: () -> Unit,
) {
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  val albums by viewModel.localAlbums.collectAsStateWithLifecycle()
  val spotifyImportState by viewModel.spotifyImportState.collectAsStateWithLifecycle()
  val matchingYouTubePlaylistId by viewModel.youtubeMatchingPlaylistId.collectAsStateWithLifecycle()
  val playerState by viewModel.playerState.collectAsStateWithLifecycle()
  val youtubeDirectSearchState by viewModel.youtubeDirectSearchState.collectAsStateWithLifecycle()
  MusicAndSoundtrackScreen(
    playlists = playlists,
    tracks = tracks,
    albums = albums,
    playerState = playerState,
    spotifyImportState = spotifyImportState,
    matchingYouTubePlaylistId = matchingYouTubePlaylistId,
    youtubeDirectSearchState = youtubeDirectSearchState,
    hasConnectedPartner = hasConnectedPartner,
    onSearchYouTubeDirect = viewModel::searchYouTubeDirect,
    onClearYouTubeDirectSearch = viewModel::clearYouTubeDirectSearch,
    onPlayYouTubeMatchDirectly = viewModel::playYouTubeMatchDirectly,
    onCreatePlaylist = viewModel::createPlaylist,
    onDeletePlaylist = viewModel::deletePlaylist,
    onImportSpotifyPlaylist = viewModel::importSpotifyPlaylist,
    onResetSpotifyImport = viewModel::resetSpotifyImportState,
    onMatchPlaylistWithYouTube = viewModel::matchPlaylistTracksWithYouTube,
    onMatchTrackWithYouTube = { track ->
      viewModel.matchSingleTrackWithYouTube(
        track = track,
        playImmediately = true,
        onReadyToOpenPlayer = onOpenFullPlayer,
      )
    },
    onAddAudioTrack = { plId, title, artist, uri ->
      viewModel.addLocalAudioTrack(plId, title, artist, uri, null)
    },
    onAttachLocalAudioToTrack = viewModel::attachLocalAudioToTrack,
    onBatchAttachLocalAudio = viewModel::batchAttachLocalAudioToPlaylist,
    onMoveTrackOrder = viewModel::moveTrackOrder,
    onDeleteTrack = viewModel::deleteTrack,
    onPlayTrack = viewModel::playPlaylistTrack,
    onSendPlaylistToPartner = viewModel::sendPlaylistToPartner,
    onSendTrackToPartner = viewModel::sendTrackToPartner,
    onOpenFullPlayer = onOpenFullPlayer,
    onBack = onBack,
  )
}

@Composable
private fun EvoriaFullMusicPlayerRouteContent(
  viewModel: EvoriaViewModel,
  onYouTubeSlotBounds: (Rect?) -> Unit,
  onBack: () -> Unit,
) {
  val playerState by viewModel.playerState.collectAsStateWithLifecycle()
  FullMusicPlayerScreen(
    playerState = playerState,
    youtubeCommands = viewModel.playerManager.youtubeCommands,
    onYouTubePlaybackUpdate = viewModel.playerManager::onYouTubePlaybackUpdate,
    onYouTubeVideoEnded = viewModel.playerManager::onYouTubeVideoEnded,
    onYouTubePlayerError = viewModel.playerManager::onYouTubePlayerError,
    hostPersistentYouTubeOverlay = true,
    onYouTubeSlotBounds = onYouTubeSlotBounds,
    onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
    onSeekTo = { viewModel.playerManager.seekTo(it) },
    onNext = viewModel::playNextTrack,
    onPrevious = viewModel::playPreviousTrack,
    onBack = onBack,
  )
}
