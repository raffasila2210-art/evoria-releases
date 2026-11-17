package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiMemoryStoryDraft
import com.example.data.ai.AiReflectionInsight
import com.example.data.ai.EvoriaSearchEngine
import com.example.data.ai.SearchOutcome
import com.example.data.local.AppUserPrefs
import com.example.data.local.AutoLockTimeout
import com.example.data.local.DEVELOPER_ADMIN_EMAIL
import com.example.data.local.EvoriaAccentPreset
import com.example.data.local.EvoriaSanctuaryRepository
import com.example.data.local.JournalEntity
import com.example.data.local.KenanganCollectionEntity
import com.example.data.local.LetterEntity
import com.example.data.local.LocalAlbumEntity
import com.example.data.local.LocalMemoryEntity
import com.example.data.local.LocalNotificationEntity
import com.example.data.local.LockMethod
import com.example.data.local.MoodEntity
import com.example.data.local.NavbarGlassIntensity
import com.example.data.local.NavbarThemeStyle
import com.example.data.local.PinVerifyResult
import com.example.data.local.PlaylistEntity
import com.example.data.local.ReflectionAnswerEntity
import com.example.data.local.SecurityPreferences
import com.example.data.local.SecuritySettings
import com.example.data.local.SyncCheckpointEntity
import com.example.data.local.ThemeMode
import com.example.data.local.TimeCapsuleEntity
import com.example.data.local.TrackEntity
import com.example.data.local.UploadJobEntity
import com.example.data.local.canEditDeveloperSocials
import com.example.data.remote.CoupleConnectionDto
import com.example.data.remote.EvoriaCloudRepository
import com.example.data.remote.GeneratedInvitation
import com.example.data.remote.InvitationDto
import com.example.data.remote.NotificationItemDto
import com.example.data.remote.SharedAlbumDto
import com.example.data.remote.SharedContentRequestDto
import com.example.data.remote.SharedMemoryDto
import com.example.data.remote.SharedMusicDto
import com.example.data.remote.SharedMusicTrackItem
import com.example.data.remote.SharedReflectionDto
import com.example.data.remote.SpotifyPlaylistImporter
import com.example.data.remote.UserProfileDto
import com.example.data.remote.YouTubeDataApiClient
import com.example.data.remote.YouTubeSearchResult
import com.example.data.remote.YouTubeTrackMatch
import com.example.data.remote.unwrapFirestoreException
import com.example.media.EvoriaPlayerManager
import com.example.media.MusicPlayerState
import com.example.notifications.EvoriaNotificationHelper
import com.example.util.MediaStorageHelper
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SearchUiState {
  data object Idle : SearchUiState
  data object Loading : SearchUiState
  data class Success(val outcome: SearchOutcome) : SearchUiState
  data class Error(val message: String) : SearchUiState
}

sealed interface SpotifyImportUiState {
  data object Idle : SpotifyImportUiState
  data object Loading : SpotifyImportUiState
  data class Error(val message: String) : SpotifyImportUiState
}

sealed interface YouTubeDirectSearchUiState {
  data object Idle : YouTubeDirectSearchUiState
  data object Loading : YouTubeDirectSearchUiState
  data class Success(val query: String, val results: List<YouTubeTrackMatch>) : YouTubeDirectSearchUiState
  data class Error(val message: String) : YouTubeDirectSearchUiState
}

sealed interface AiMemoryDraftUiState {
  data object Idle : AiMemoryDraftUiState
  data object Loading : AiMemoryDraftUiState
  data class Success(val draft: AiMemoryStoryDraft) : AiMemoryDraftUiState
  data class Error(val message: String) : AiMemoryDraftUiState
}

sealed interface AiReflectionUiState {
  data object Idle : AiReflectionUiState
  data object Loading : AiReflectionUiState
  data class Success(val insight: AiReflectionInsight) : AiReflectionUiState
  data class Error(val message: String) : AiReflectionUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class EvoriaViewModel(
  private val currentUserId: String,
  private val initialDisplayName: String,
  private val localRepo: EvoriaSanctuaryRepository,
  private val cloudRepo: EvoriaCloudRepository,
  private val prefs: SecurityPreferences,
  private val searchEngine: EvoriaSearchEngine,
  val playerManager: EvoriaPlayerManager,
  private val currentUserEmail: String? = null,
  private val appContext: Context? = null,
) : ViewModel() {

  private val _isSecurityResolved = MutableStateFlow(false)
  val isSecurityResolved: StateFlow<Boolean> = _isSecurityResolved.asStateFlow()

  private var hasUnlockedCurrentLaunch: Boolean = false
  private var suppressNextBackgroundLock: Boolean = false
  private val musicShareJson = Json { ignoreUnknownKeys = true }

  val userPrefs: StateFlow<AppUserPrefs> =
    prefs.userPrefsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppUserPrefs())

  private val _optimisticActiveTab = MutableStateFlow<Int?>(null)
  val activeTabIndex: StateFlow<Int> =
    combine(prefs.userPrefsFlow, _optimisticActiveTab) { p, optimistic ->
        (optimistic ?: p.activeTabIndex).coerceIn(0, 4)
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

  val securitySettings: StateFlow<SecuritySettings> =
    prefs.securitySettingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, SecuritySettings())

  private val _isProfileResolved = MutableStateFlow(false)
  val isProfileResolved: StateFlow<Boolean> = _isProfileResolved.asStateFlow()

  val userProfile: StateFlow<UserProfileDto?> =
    cloudRepo.observeUserProfile(currentUserId)
      .onEach { _isProfileResolved.value = true }
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Profile stream error", fsErr ?: error)
        _isProfileResolved.value = true
        emit(null)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val activeCouple: StateFlow<CoupleConnectionDto?> =
    cloudRepo.observeActiveCouple(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Couple stream error", fsErr ?: error)
        emit(null)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val myInvitations: StateFlow<List<InvitationDto>> =
    cloudRepo.observeMyInvitations(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val sharedAlbums: StateFlow<List<SharedAlbumDto>> =
    activeCouple
      .flatMapLatest { couple ->
        if (couple == null) flowOf(emptyList())
        else cloudRepo.observeSharedAlbums(couple.id).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val sharedMemories: StateFlow<List<SharedMemoryDto>> =
    activeCouple
      .flatMapLatest { couple ->
        if (couple == null) flowOf(emptyList())
        else cloudRepo.observeSharedMemories(couple.id).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val shareRequests: StateFlow<List<SharedContentRequestDto>> =
    activeCouple
      .flatMapLatest { couple ->
        if (couple == null) flowOf(emptyList())
        else cloudRepo.observeShareRequests(couple.id).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val sharedMusic: StateFlow<List<SharedMusicDto>> =
    activeCouple
      .flatMapLatest { couple ->
        if (couple == null) flowOf(emptyList())
        else cloudRepo.observeSharedMusic(couple.id).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val remoteNotifications: StateFlow<List<NotificationItemDto>> =
    activeCouple
      .flatMapLatest { couple ->
        if (couple == null) flowOf(emptyList())
        else cloudRepo.observeNotifications(couple.id).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val sharedReflections: StateFlow<List<SharedReflectionDto>> =
    activeCouple
      .flatMapLatest { couple ->
        if (couple == null) flowOf(emptyList())
        else cloudRepo.observeSharedReflections(couple.id).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val localAlbums: StateFlow<List<LocalAlbumEntity>> =
    localRepo.observeAlbums(currentUserId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val kenanganCollections: StateFlow<List<KenanganCollectionEntity>> =
    localRepo.observeKenanganCollections(currentUserId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val localMemories: StateFlow<List<LocalMemoryEntity>> =
    localRepo.observeMemories(currentUserId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val uploadJobs: StateFlow<List<UploadJobEntity>> =
    localRepo.observeUploadJobs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val journals: StateFlow<List<JournalEntity>> =
    localRepo.observeJournals(currentUserId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val letters: StateFlow<List<LetterEntity>> =
    localRepo.observeLetters(currentUserId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val moods: StateFlow<List<MoodEntity>> =
    localRepo.observeMoods(currentUserId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val reflectionAnswers: StateFlow<List<ReflectionAnswerEntity>> =
    localRepo.observeReflectionAnswers(currentUserId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val timeCapsules: StateFlow<List<TimeCapsuleEntity>> =
    localRepo.observeTimeCapsules(currentUserId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val playlists: StateFlow<List<PlaylistEntity>> =
    localRepo.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val tracks: StateFlow<List<TrackEntity>> =
    localRepo.observeTracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val localNotifications: StateFlow<List<LocalNotificationEntity>> =
    localRepo.observeLocalNotifications().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val syncCheckpoint: StateFlow<SyncCheckpointEntity?> =
    localRepo.observeSyncCheckpoint().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val playerState: StateFlow<MusicPlayerState> = playerManager.state

  private val initialSyncLock = prefs.readInitialLockSnapshotSync()

  // Transient UI states
  private val _isLocked = MutableStateFlow(initialSyncLock.first && initialSyncLock.second)
  val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

  private val _bloomMessage = MutableStateFlow<String?>(null)
  val bloomMessage: StateFlow<String?> = _bloomMessage.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _generatedInvitation = MutableStateFlow<GeneratedInvitation?>(null)
  val generatedInvitation: StateFlow<GeneratedInvitation?> = _generatedInvitation.asStateFlow()

  private val _lookedUpInvitation = MutableStateFlow<InvitationDto?>(null)
  val lookedUpInvitation: StateFlow<InvitationDto?> = _lookedUpInvitation.asStateFlow()

  private val _inviteActionError = MutableStateFlow<String?>(null)
  val inviteActionError: StateFlow<String?> = _inviteActionError.asStateFlow()

  private val _inviteLoading = MutableStateFlow(false)
  val inviteLoading: StateFlow<Boolean> = _inviteLoading.asStateFlow()

  private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
  val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

  private val _spotifyImportState = MutableStateFlow<SpotifyImportUiState>(SpotifyImportUiState.Idle)
  val spotifyImportState: StateFlow<SpotifyImportUiState> = _spotifyImportState.asStateFlow()

  private val _youtubeMatchingPlaylistId = MutableStateFlow<String?>(null)
  val youtubeMatchingPlaylistId: StateFlow<String?> = _youtubeMatchingPlaylistId.asStateFlow()

  private val _youtubeDirectSearchState = MutableStateFlow<YouTubeDirectSearchUiState>(YouTubeDirectSearchUiState.Idle)
  val youtubeDirectSearchState: StateFlow<YouTubeDirectSearchUiState> = _youtubeDirectSearchState.asStateFlow()

  private val _aiMemoryDraftState = MutableStateFlow<AiMemoryDraftUiState>(AiMemoryDraftUiState.Idle)
  val aiMemoryDraftState: StateFlow<AiMemoryDraftUiState> = _aiMemoryDraftState.asStateFlow()

  private val _aiReflectionState = MutableStateFlow<AiReflectionUiState>(AiReflectionUiState.Idle)
  val aiReflectionState: StateFlow<AiReflectionUiState> = _aiReflectionState.asStateFlow()

  private val _localStorageLabel = MutableStateFlow("Menghitung...")
  val localStorageLabel: StateFlow<String> = _localStorageLabel.asStateFlow()

  init {
    refreshLocalStorageLabel()
    // Enforce cold-start app lock immediately once DataStore security preferences resolve
    viewModelScope.launch {
      prefs.securitySettingsFlow.collect { sec ->
        _isSecurityResolved.value = true
        if (sec.appLockEnabled && sec.hasPinConfigured && !hasUnlockedCurrentLaunch) {
          _isLocked.value = true
        } else if (!sec.appLockEnabled || !sec.hasPinConfigured) {
          _isLocked.value = false
        }
      }
    }

    // Dispatch system push notifications when new unread remote partner alerts arrive
    viewModelScope.launch {
      val seenNotifPrefs = appContext?.getSharedPreferences("evoria_pushed_notifs", Context.MODE_PRIVATE)
      remoteNotifications.collect { items ->
        val ctx = appContext ?: return@collect
        val currentPrefs = userPrefs.value
        items
          .filter { !it.isRead && it.recipientUid == currentUserId }
          .forEach { n ->
            val key = "pushed_${n.id}"
            val alreadyShown = seenNotifPrefs?.getBoolean(key, false) == true
            if (!alreadyShown) {
              val route =
                when {
                  n.kind.contains("share") -> "share_inbox"
                  n.kind.contains("invite") || n.kind.contains("partner") -> "partner"
                  else -> "notifications"
                }
              val shown =
                EvoriaNotificationHelper.showNotificationIfEnabled(
                  context = ctx,
                  notificationId = n.id,
                  kind = n.kind,
                  title = n.title,
                  body = n.body,
                  prefs = currentPrefs,
                  targetRoute = route,
                )
              if (shown) {
                seenNotifPrefs?.edit()?.putBoolean(key, true)?.apply()
              }
            }
          }
      }
    }

    // Recompute local storage label off the main thread only when counts meaningfully change
    viewModelScope.launch {
      combine(localMemories, localAlbums, tracks) { m, a, t -> Triple(m.size, a.size, t.size) }
        .distinctUntilChanged()
        .debounce(350L)
        .collect {
          refreshLocalStorageLabel()
        }
    }

    // Sync own profile summary whenever couple connection becomes active on this account
    viewModelScope.launch {
      combine(userProfile, activeCouple) { profile, couple -> profile to couple }.collect { (profile, couple) ->
        if (profile != null && couple != null && profile.coupleId != couple.id) {
          val isOne = couple.memberOneUid == currentUserId
          val partnerUid = if (isOne) couple.memberTwoUid else couple.memberOneUid
          val partnerName = if (isOne) couple.memberTwoName else couple.memberOneName
          cloudRepo.syncOwnPartnerSummary(couple.id, partnerUid, partnerName)
        } else if (profile != null && couple == null && profile.coupleId != null) {
          cloudRepo.syncOwnPartnerSummary(null, null, null)
        }
      }
    }
  }

  fun refreshLocalStorageLabel() {
    viewModelScope.launch {
      val label = withContext(Dispatchers.IO) { localRepo.getLocalMediaSizeFormatted() }
      _localStorageLabel.value = label
    }
  }

  fun dismissStatusBanner() {
    _statusBanner.value = null
  }

  fun dismissBloom() {
    _bloomMessage.value = null
  }

  fun suppressNextAutoLockOnce() {
    suppressNextBackgroundLock = true
  }

  fun lockNow() {
    if (securitySettings.value.appLockEnabled && securitySettings.value.hasPinConfigured) {
      hasUnlockedCurrentLaunch = false
      _isLocked.value = true
    }
  }

  fun unlockSucceeded() {
    hasUnlockedCurrentLaunch = true
    suppressNextBackgroundLock = false
    _isLocked.value = false
  }

  fun evaluateAutoLockOnResume() {
    val sec = securitySettings.value
    if (!sec.appLockEnabled || !sec.hasPinConfigured) return
    if (suppressNextBackgroundLock) {
      suppressNextBackgroundLock = false
      return
    }
    if (!hasUnlockedCurrentLaunch) {
      _isLocked.value = true
      return
    }
    if (sec.lastBackgroundMillis <= 0L) {
      hasUnlockedCurrentLaunch = false
      _isLocked.value = true
      return
    }
    val elapsedSec = (System.currentTimeMillis() - sec.lastBackgroundMillis) / 1000L
    if (sec.autoLockTimeout == AutoLockTimeout.IMMEDIATE || elapsedSec >= sec.autoLockTimeout.seconds) {
      hasUnlockedCurrentLaunch = false
      _isLocked.value = true
    }
  }

  fun onAppBackgrounded() {
    if (suppressNextBackgroundLock) return
    prefs.recordBackgroundTimestampSync()
    val sec = securitySettings.value
    if (sec.appLockEnabled && sec.hasPinConfigured && sec.autoLockTimeout == AutoLockTimeout.IMMEDIATE) {
      hasUnlockedCurrentLaunch = false
      _isLocked.value = true
    }
    viewModelScope.launch { prefs.recordBackgroundTimestamp() }
  }

  fun setActiveTab(index: Int) {
    val clamped = index.coerceIn(0, 4)
    _optimisticActiveTab.value = clamped
    viewModelScope.launch { prefs.setActiveTab(clamped) }
  }

  fun setLastViewedMemory(id: String?) {
    viewModelScope.launch { prefs.setLastViewedMemoryId(id) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { prefs.setThemeMode(mode) }
  }

  fun setAccentPreset(preset: EvoriaAccentPreset) {
    viewModelScope.launch { prefs.setAccentPreset(preset) }
  }

  fun setCustomAccentHex(colorHex: Long?) {
    viewModelScope.launch { prefs.setCustomAccentHex(colorHex) }
  }

  fun setNavbarThemeStyle(style: NavbarThemeStyle) {
    viewModelScope.launch { prefs.setNavbarThemeStyle(style) }
  }

  fun setNavbarGlassIntensity(intensity: NavbarGlassIntensity) {
    viewModelScope.launch { prefs.setNavbarGlassIntensity(intensity) }
  }

  fun setReducedMotion(enabled: Boolean) {
    viewModelScope.launch { prefs.setReducedMotionOverride(enabled) }
  }

  fun setLock60Fps(enabled: Boolean) {
    viewModelScope.launch { prefs.setLock60Fps(enabled) }
  }

  fun setOnboardingCompleted(completed: Boolean) {
    viewModelScope.launch { prefs.setOnboardingCompleted(completed) }
  }

  fun setQuickOpenShowcase(enabled: Boolean) {
    viewModelScope.launch { prefs.setQuickOpenShowcase(enabled) }
  }

  fun updateDeveloperSocials(
    instagram: String,
    tiktok: String,
    github: String,
    email: String,
    website: String,
  ) {
    if (!canEditDeveloperSocials(currentUserEmail)) {
      _statusBanner.value =
        "Akses ditolak: hanya akun Google $DEVELOPER_ADMIN_EMAIL yang dapat mengatur sosial media developer."
      return
    }
    viewModelScope.launch {
      prefs.updateDeveloperSocials(
        instagram = instagram,
        tiktok = tiktok,
        github = github,
        email = email,
        website = website,
      )
      _statusBanner.value = "Tautan sosial media developer tersimpan."
    }
  }

  fun setAiSearchConsent(consent: Boolean) {
    viewModelScope.launch {
      prefs.setAiSearchConsent(consent)
      userProfile.value?.let { prof ->
        cloudRepo.upsertUserProfile(
          displayName = prof.displayName,
          partnerNickname = prof.partnerNickname,
          avatarUrl = prof.avatarUrl,
          aiSearchConsent = consent,
        )
      }
    }
  }

  fun updateNotificationPrefs(invites: Boolean, shares: Boolean, capsules: Boolean, security: Boolean) {
    viewModelScope.launch { prefs.updateNotificationPrefs(invites, shares, capsules, security) }
  }

  fun saveProfileSetup(displayName: String, partnerNickname: String, avatarUri: Uri?) {
    viewModelScope.launch {
      val persistedAvatar =
        if (avatarUri != null) localRepo.persistMediaUri(avatarUri).getOrNull()?.localFileUri
        else userProfile.value?.avatarUrl
      val res =
        cloudRepo.upsertUserProfile(
          displayName = displayName.ifBlank { initialDisplayName },
          partnerNickname = partnerNickname,
          avatarUrl = persistedAvatar,
          aiSearchConsent = userPrefs.value.aiSearchConsent,
        )
      _statusBanner.value =
        if (res.isSuccess) "Profil tersimpan." else "Gagal menyimpan profil: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun saveProfileBanner(uri: Uri?) {
    if (uri == null) return
    viewModelScope.launch {
      val oldUri = userPrefs.value.profileBannerUri
      val persisted = localRepo.persistMediaUri(uri).getOrNull()
      if (persisted == null || (persisted.mediaType != "photo" && persisted.mediaType != "video")) {
        _statusBanner.value = "Pilih file foto atau video yang valid untuk latar profil."
        return@launch
      }
      prefs.setProfileBanner(persisted.localFileUri, persisted.mediaType)
      if (!oldUri.isNullOrBlank() && oldUri != persisted.localFileUri) {
        withContext(Dispatchers.IO) { MediaStorageHelper.deletePersistedFile(oldUri) }
      }
      _statusBanner.value =
        if (persisted.mediaType == "video") "Latar video profil diperbarui." else "Latar foto profil diperbarui."
    }
  }

  fun clearProfileBanner() {
    viewModelScope.launch {
      val oldUri = userPrefs.value.profileBannerUri
      prefs.clearProfileBanner()
      if (!oldUri.isNullOrBlank()) {
        withContext(Dispatchers.IO) { MediaStorageHelper.deletePersistedFile(oldUri) }
      }
      _statusBanner.value = "Latar header profil dikembalikan ke gradien EVORIA."
    }
  }

  private fun partnerUidOf(couple: CoupleConnectionDto): String =
    if (couple.memberOneUid == currentUserId) couple.memberTwoUid else couple.memberOneUid

  private fun currentDisplayName(): String =
    userProfile.value?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName

  // --- MEMORIES & ALBUMS ---

  fun createOrUpdateAlbum(
    existingId: String? = null,
    title: String,
    description: String,
    visibility: String,
    soundtrackTitle: String?,
    coverUri: Uri? = null,
    existingCoverUri: String? = null,
  ) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val albumId = existingId ?: "alb_${UUID.randomUUID().toString().take(12)}"
      val savedCover =
        if (coverUri != null) localRepo.persistMediaUri(coverUri).getOrNull()?.localFileUri
        else existingCoverUri
      val album =
        LocalAlbumEntity(
          id = albumId,
          ownerUid = currentUserId,
          title = title.trim().ifBlank { "Album Tanpa Judul" },
          description = description.trim(),
          coverUri = savedCover,
          visibility = visibility,
          soundtrackTitle = soundtrackTitle?.trim()?.takeIf { it.isNotEmpty() },
          createdAtMillis = now,
          updatedAtMillis = now,
        )
      localRepo.saveAlbum(album)

      val couple = activeCouple.value
      if (visibility == "shared" && couple != null) {
        cloudRepo.publishSharedAlbum(
          coupleId = couple.id,
          memberUids = couple.memberUids,
          albumId = albumId,
          title = album.title,
          description = album.description,
          soundtrackTitle = album.soundtrackTitle,
          itemCount = localMemories.value.count { it.albumId == albumId }.toLong(),
        )
      }
      if (existingId == null) {
        _bloomMessage.value = "Kamu berhasil membuat album"
      } else {
        _statusBanner.value = "Album diperbarui."
      }
    }
  }

  fun deleteAlbum(albumId: String) {
    viewModelScope.launch {
      localRepo.deleteAlbum(albumId)
      _statusBanner.value = "Album dihapus dari perangkat."
    }
  }

  fun createKenanganCollection(
    title: String,
    description: String,
    albumIds: List<String>,
    visibility: String = "private",
  ) {
    val distinctAlbumIds = albumIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (distinctAlbumIds.isEmpty()) {
      _statusBanner.value = "Pilih minimal satu album untuk digabungkan ke kenangan."
      return
    }
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val selectedAlbums = localAlbums.value.filter { it.id in distinctAlbumIds }
      val firstCover =
        selectedAlbums.firstNotNullOfOrNull { it.coverUri }
          ?: localMemories.value.firstOrNull { it.albumId in distinctAlbumIds && !it.mediaUri.isNullOrBlank() }?.mediaUri
      val collection =
        KenanganCollectionEntity(
          id = "kng_${UUID.randomUUID().toString().take(12)}",
          ownerUid = currentUserId,
          title = title.trim().ifBlank {
            selectedAlbums.take(2).joinToString(" & ") { it.title }.ifBlank { "Kompilasi Kenangan" }
          },
          description = description.trim(),
          albumIdsCsv = distinctAlbumIds.joinToString(","),
          coverUri = firstCover,
          visibility = visibility,
          createdAtMillis = now,
          updatedAtMillis = now,
        )
      localRepo.saveKenanganCollection(collection)
      _bloomMessage.value = "Kamu berhasil membuat kenangan"
    }
  }

  fun deleteKenanganCollection(collectionId: String) {
    viewModelScope.launch {
      localRepo.deleteKenanganCollection(collectionId)
      _statusBanner.value = "Kompilasi kenangan dihapus."
    }
  }

  fun saveNewMemoriesFromUris(
    uris: List<Uri>,
    titlePrefix: String,
    caption: String,
    story: String,
    locationName: String,
    albumId: String?,
    visibility: String,
    tagsCsv: String,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      var savedCount = 0
      var photoCount = 0
      var videoCount = 0
      val couple = activeCouple.value
      uris.forEachIndexed { idx, uri ->
        val persisted = localRepo.persistMediaUri(uri).getOrNull() ?: return@forEachIndexed
        val memId = "mem_${UUID.randomUUID().toString().take(12)}"
        if (persisted.mediaType == "video") videoCount++ else photoCount++
        val resolvedTitle =
          if (uris.size == 1) titlePrefix.trim().ifBlank { persisted.displayName }
          else "${titlePrefix.trim().ifBlank { "Kenangan" }} #${idx + 1}"
        val entity =
          LocalMemoryEntity(
            id = memId,
            ownerUid = currentUserId,
            albumId = albumId,
            title = resolvedTitle,
            caption = caption.trim(),
            story = story.trim(),
            locationName = locationName.trim(),
            mediaUri = persisted.localFileUri,
            thumbnailUri = persisted.localFileUri,
            mediaType = persisted.mediaType,
            visibility = visibility,
            isFavorite = false,
            tagsCsv = tagsCsv.trim(),
            memoryDateMillis = now,
            createdAtMillis = now,
            updatedAtMillis = now,
            syncState = if (visibility == "shared") "pending" else "local_only",
          )
        localRepo.saveMemoryAndEnqueue(entity, enqueueUploadJob = true)
        if (visibility == "shared" && couple != null) {
          cloudRepo.publishSharedMemory(
            coupleId = couple.id,
            memberUids = couple.memberUids,
            memoryId = entity.id,
            albumId = entity.albumId,
            title = entity.title,
            caption = entity.caption,
            story = entity.story,
            locationName = entity.locationName,
            mediaType = entity.mediaType,
            memoryDateMillis = entity.memoryDateMillis,
            isFavorite = entity.isFavorite,
            tags = entity.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
          )
        }
        savedCount++
      }
      if (savedCount > 0) {
        if (visibility == "shared" && couple != null) {
          cloudRepo.sendPartnerNotification(
            coupleId = couple.id,
            memberUids = couple.memberUids,
            recipientUid = partnerUidOf(couple),
            kind = "memory",
            title = "Kenangan Baru Dibagikan",
            body = "${currentDisplayName()} menambahkan $savedCount kenangan ke ruang bersama.",
          )
        }
        _bloomMessage.value =
          when {
            photoCount > 0 && videoCount > 0 -> "Kamu berhasil masukin foto & video"
            videoCount > 0 -> "Kamu berhasil masukin video"
            else -> "Kamu berhasil masukin foto"
          }
      } else {
        _statusBanner.value = "Tidak ada media yang dapat dibaca dari perangkat."
      }
      onDone()
    }
  }

  fun saveStoryOnlyMemory(
    existing: LocalMemoryEntity? = null,
    title: String,
    caption: String,
    story: String,
    locationName: String,
    albumId: String?,
    visibility: String,
    tagsCsv: String,
  ) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val entity =
        existing?.copy(
          title = title.trim().ifBlank { "Cerita Kita" },
          caption = caption.trim(),
          story = story.trim(),
          locationName = locationName.trim(),
          albumId = albumId,
          visibility = visibility,
          tagsCsv = tagsCsv.trim(),
          updatedAtMillis = now,
          syncState = if (visibility == "shared") "pending" else "local_only",
        )
          ?: LocalMemoryEntity(
            id = "mem_${UUID.randomUUID().toString().take(12)}",
            ownerUid = currentUserId,
            albumId = albumId,
            title = title.trim().ifBlank { "Cerita Kita" },
            caption = caption.trim(),
            story = story.trim(),
            locationName = locationName.trim(),
            mediaType = "story",
            visibility = visibility,
            tagsCsv = tagsCsv.trim(),
            memoryDateMillis = now,
            createdAtMillis = now,
            updatedAtMillis = now,
            syncState = if (visibility == "shared") "pending" else "local_only",
          )
      localRepo.saveMemoryAndEnqueue(entity, enqueueUploadJob = false)

      val couple = activeCouple.value
      if (visibility == "shared" && couple != null) {
        val pubRes =
          cloudRepo.publishSharedMemory(
            coupleId = couple.id,
            memberUids = couple.memberUids,
            memoryId = entity.id,
            albumId = entity.albumId,
            title = entity.title,
            caption = entity.caption,
            story = entity.story,
            locationName = entity.locationName,
            mediaType = entity.mediaType,
            memoryDateMillis = entity.memoryDateMillis,
            isFavorite = entity.isFavorite,
            tags = entity.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
          )
        if (pubRes.isSuccess && existing == null) {
          cloudRepo.sendPartnerNotification(
            coupleId = couple.id,
            memberUids = couple.memberUids,
            recipientUid = partnerUidOf(couple),
            kind = "memory",
            title = "Cerita Kenangan Baru",
            body = "${currentDisplayName()} membagikan \"${entity.title}\".",
          )
        }
      }
      if (existing == null) {
        _bloomMessage.value = "Cerita \"${entity.title}\" tersimpan."
      } else {
        _statusBanner.value = "Kenangan diperbarui."
      }
    }
  }

  fun toggleMemoryFavorite(memory: LocalMemoryEntity) {
    viewModelScope.launch { localRepo.toggleFavorite(memory.id, !memory.isFavorite) }
  }

  fun deleteMemory(memory: LocalMemoryEntity) {
    viewModelScope.launch {
      localRepo.deleteMemory(memory)
      _statusBanner.value = "Kenangan dihapus."
    }
  }

  fun sendShareRequestToPartner(memory: LocalMemoryEntity) {
    val couple = activeCouple.value
    if (couple == null) {
      _statusBanner.value = "Hubungkan pasangan terlebih dahulu untuk mengirim EVORIA Share."
      return
    }
    val partnerUid = partnerUidOf(couple)
    val myName = currentDisplayName()
    viewModelScope.launch {
      val res =
        cloudRepo.createShareRequest(
          coupleId = couple.id,
          memberUids = couple.memberUids,
          senderName = myName,
          recipientUid = partnerUid,
          memoryId = memory.id,
          albumId = memory.albumId,
          title = memory.title,
          caption = memory.caption.ifBlank { memory.story.take(120) },
        )
      if (res.isSuccess) {
        cloudRepo.sendPartnerNotification(
          coupleId = couple.id,
          memberUids = couple.memberUids,
          recipientUid = partnerUid,
          kind = "share",
          title = "Kiriman EVORIA Share Baru",
          body = "$myName mengirimkan \"${memory.title}\" ke kotak masukmu.",
        )
      }
      _statusBanner.value =
        if (res.isSuccess) "Permintaan EVORIA Share dikirim ke pasangan."
        else "Gagal mengirim share: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun respondShareRequest(request: SharedContentRequestDto, accept: Boolean) {
    val couple = activeCouple.value ?: return
    viewModelScope.launch {
      val status = if (accept) "accepted" else "declined"
      val res = cloudRepo.respondToShareRequest(couple.id, request.id, status)
      if (res.isSuccess && accept) {
        localRepo.addLocalNotification(
          kind = "share",
          title = "EVORIA Share diterima",
          body = "\"${request.title}\" ditambahkan ke ruang bersama.",
        )
        cloudRepo.sendPartnerNotification(
          coupleId = couple.id,
          memberUids = couple.memberUids,
          recipientUid = request.senderUid,
          kind = "share",
          title = "EVORIA Share Diterima",
          body = "${currentDisplayName()} menerima \"${request.title}\".",
        )
      }
      _statusBanner.value =
        if (res.isSuccess) {
          if (accept) "Kiriman pasangan diterima." else "Kiriman ditolak."
        } else {
          "Gagal memproses permintaan: ${res.exceptionOrNull()?.localizedMessage}"
        }
    }
  }

  fun revokeShareRequest(request: SharedContentRequestDto) {
    val couple = activeCouple.value ?: return
    viewModelScope.launch {
      val res = cloudRepo.respondToShareRequest(couple.id, request.id, "revoked")
      _statusBanner.value =
        if (res.isSuccess) "Permintaan share dibatalkan."
        else "Gagal membatalkan: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun retryUploadJob(job: UploadJobEntity) {
    viewModelScope.launch { localRepo.retryUploadJob(job) }
  }

  fun deleteUploadJob(jobId: String) {
    viewModelScope.launch { localRepo.deleteUploadJob(jobId) }
  }

  // --- JELAJAH AI PHOTO SEARCH ---

  fun runSearch(
    query: String,
    visibilityFilter: String = "all",
    mediaTypeFilter: String = "all",
    albumFilterId: String? = null,
  ) {
    viewModelScope.launch {
      _searchState.value = SearchUiState.Loading
      if (query.isNotBlank()) prefs.addSearchHistory(query)
      val filtered =
        localMemories.value.filter { mem ->
          val visOk = visibilityFilter == "all" || mem.visibility.equals(visibilityFilter, ignoreCase = true)
          val type = mem.mediaType.trim().lowercase()
          val typeOk =
            when (mediaTypeFilter) {
              "all" -> true
              "photo" -> type == "photo" || (!mem.mediaUri.isNullOrBlank() && type != "video" && type != "story")
              "video" -> type == "video"
              "story" -> type == "story" || mem.mediaUri.isNullOrBlank()
              "favorite" -> mem.isFavorite
              else -> type == mediaTypeFilter
            }
          val albOk = albumFilterId == null || mem.albumId == albumFilterId
          visOk && typeOk && albOk
        }
      val albumTitlesById = localAlbums.value.associate { it.id to it.title }
      val outcome =
        searchEngine.searchMemories(
          query = query,
          candidates = filtered,
          allowCloudAi = userPrefs.value.aiSearchConsent,
          albumTitlesById = albumTitlesById,
        )
      if (outcome.discoveredTagsByMemoryId.isNotEmpty()) {
        outcome.discoveredTagsByMemoryId.forEach { (memId, newTags) ->
          val existingMem = localMemories.value.firstOrNull { it.id == memId }
          if (existingMem != null) {
            val currentTags =
              existingMem.tagsCsv.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableSet()
            val added = newTags.map { it.trim().lowercase() }.filter { it.isNotEmpty() && currentTags.add(it) }
            if (added.isNotEmpty()) {
              localRepo.saveMemoryAndEnqueue(
                existingMem.copy(tagsCsv = currentTags.take(12).joinToString(", ")),
                enqueueUploadJob = false,
              )
            }
          }
        }
      }
      _searchState.value = SearchUiState.Success(outcome)
    }
  }

  fun clearSearchHistory() {
    viewModelScope.launch { prefs.clearSearchHistory() }
  }

  fun generateAiMemoryStory(photoUri: Uri?, seedTitle: String, location: String) {
    if (!userPrefs.value.aiSearchConsent) {
      _aiMemoryDraftState.value =
        AiMemoryDraftUiState.Error(
          "Aktifkan persetujuan Gemini AI di tab Jelajah terlebih dahulu untuk merangkai cerita otomatis."
        )
      return
    }
    viewModelScope.launch {
      _aiMemoryDraftState.value = AiMemoryDraftUiState.Loading
      val res =
        searchEngine.generateMemoryStorySuggestion(
          photoUri = photoUri?.toString(),
          seedTitle = seedTitle,
          location = location,
        )
      _aiMemoryDraftState.value =
        res.fold(
          onSuccess = { AiMemoryDraftUiState.Success(it) },
          onFailure = {
            AiMemoryDraftUiState.Error(it.localizedMessage ?: "Gagal meminta draf cerita dari Gemini AI.")
          },
        )
    }
  }

  fun clearAiMemoryDraftState() {
    _aiMemoryDraftState.value = AiMemoryDraftUiState.Idle
  }

  fun generateAiReflectionInsight() {
    if (!userPrefs.value.aiSearchConsent) {
      _aiReflectionState.value =
        AiReflectionUiState.Error(
          "Aktifkan persetujuan Gemini AI di tab Jelajah agar Gemini dapat memberi pertanyaan & wawasan hangat."
        )
      return
    }
    viewModelScope.launch {
      _aiReflectionState.value = AiReflectionUiState.Loading
      val recentMoods =
        moods.value.take(6).map { m ->
          if (m.note.isNotBlank()) "${m.moodKey} (${m.note})" else m.moodKey
        }
      val recentSnippets =
        journals.value
          .filter { !it.isDraft }
          .take(4)
          .map { "${it.title}: ${it.body.take(120)}" }
      val res = searchEngine.generateDailyReflectionInsight(recentMoods, recentSnippets)
      _aiReflectionState.value =
        res.fold(
          onSuccess = { AiReflectionUiState.Success(it) },
          onFailure = {
            AiReflectionUiState.Error(it.localizedMessage ?: "Gagal memuat refleksi Gemini AI.")
          },
        )
    }
  }

  fun clearAiReflectionState() {
    _aiReflectionState.value = AiReflectionUiState.Idle
  }

  // --- REFLEKSI ---

  fun saveJournal(
    existingId: String? = null,
    title: String,
    body: String,
    visibility: String,
    moodTag: String,
    isDraft: Boolean,
    attachmentUri: Uri? = null,
    existingAttachment: String? = null,
  ) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val journalId = existingId ?: "jrn_${UUID.randomUUID().toString().take(12)}"
      val cleanTitle = title.trim().ifBlank { "Catatan Hari Ini" }
      val cleanBody = body.trim()
      val savedAttachment =
        if (attachmentUri != null) localRepo.persistMediaUri(attachmentUri).getOrNull()?.localFileUri
        else existingAttachment
      localRepo.saveJournal(
        JournalEntity(
          id = journalId,
          ownerUid = currentUserId,
          title = cleanTitle,
          body = cleanBody,
          attachmentUri = savedAttachment,
          visibility = visibility,
          moodTag = moodTag,
          isDraft = isDraft,
          createdAtMillis = now,
          updatedAtMillis = now,
        )
      )
      val couple = activeCouple.value
      if (couple != null) {
        if (!isDraft && visibility == "shared") {
          val pubRes =
            cloudRepo.publishSharedReflection(
              coupleId = couple.id,
              memberUids = couple.memberUids,
              reflectionId = journalId,
              authorName = currentDisplayName(),
              kind = "journal",
              title = cleanTitle,
              body = cleanBody,
              unlockAtMillis = null,
              isSealed = false,
            )
          if (pubRes.isSuccess && existingId == null) {
            cloudRepo.sendPartnerNotification(
              coupleId = couple.id,
              memberUids = couple.memberUids,
              recipientUid = partnerUidOf(couple),
              kind = "reflection",
              title = "Jurnal Bersama Baru",
              body = "${currentDisplayName()} menulis jurnal \"$cleanTitle\".",
            )
          }
        } else if (existingId != null) {
          cloudRepo.deleteSharedReflection(couple.id, journalId)
        }
      }
      _statusBanner.value = if (isDraft) "Draf jurnal disimpan." else "Jurnal tersimpan."
    }
  }

  fun deleteJournal(id: String) {
    viewModelScope.launch {
      localRepo.deleteJournal(id)
      activeCouple.value?.let { couple -> cloudRepo.deleteSharedReflection(couple.id, id) }
    }
  }

  fun saveLetter(
    existingId: String? = null,
    title: String,
    body: String,
    recipientName: String,
    isSealed: Boolean,
    isDraft: Boolean,
    isShared: Boolean,
  ) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val letterId = existingId ?: "ltr_${UUID.randomUUID().toString().take(12)}"
      val cleanTitle = title.trim().ifBlank { "Surat untukmu" }
      val cleanBody = body.trim()
      localRepo.saveLetter(
        LetterEntity(
          id = letterId,
          ownerUid = currentUserId,
          title = cleanTitle,
          body = cleanBody,
          recipientName = recipientName.trim().ifBlank { "Kamu" },
          isSealed = isSealed,
          isDraft = isDraft,
          isShared = isShared,
          createdAtMillis = now,
          updatedAtMillis = now,
        )
      )
      val couple = activeCouple.value
      if (couple != null) {
        if (!isDraft && isShared) {
          val pubRes =
            cloudRepo.publishSharedReflection(
              coupleId = couple.id,
              memberUids = couple.memberUids,
              reflectionId = letterId,
              authorName = currentDisplayName(),
              kind = "letter",
              title = cleanTitle,
              body = cleanBody,
              unlockAtMillis = null,
              isSealed = isSealed,
            )
          if (pubRes.isSuccess && existingId == null) {
            cloudRepo.sendPartnerNotification(
              coupleId = couple.id,
              memberUids = couple.memberUids,
              recipientUid = partnerUidOf(couple),
              kind = "reflection",
              title = "Surat Baru Untukmu",
              body = "${currentDisplayName()} mengirimkan surat \"$cleanTitle\".",
            )
          }
        } else if (existingId != null) {
          cloudRepo.deleteSharedReflection(couple.id, letterId)
        }
      }
      _statusBanner.value = if (isDraft) "Draf surat disimpan." else "Surat tersimpan dengan aman."
    }
  }

  fun deleteLetter(id: String) {
    viewModelScope.launch {
      localRepo.deleteLetter(id)
      activeCouple.value?.let { couple -> cloudRepo.deleteSharedReflection(couple.id, id) }
    }
  }

  fun recordMood(moodKey: String, note: String) {
    viewModelScope.launch {
      localRepo.recordMood(
        MoodEntity(
          id = "md_${UUID.randomUUID().toString().take(10)}",
          ownerUid = currentUserId,
          moodKey = moodKey,
          note = note.trim(),
          recordedAtMillis = System.currentTimeMillis(),
        )
      )
      _statusBanner.value = "Suasana hati hari ini dicatat."
    }
  }

  fun saveReflectionAnswer(promptId: String, promptText: String, answerText: String) {
    if (answerText.isBlank()) return
    viewModelScope.launch {
      localRepo.saveReflectionAnswer(
        ReflectionAnswerEntity(
          id = "ans_${UUID.randomUUID().toString().take(10)}",
          ownerUid = currentUserId,
          promptId = promptId,
          promptText = promptText,
          answerText = answerText.trim(),
          answeredAtMillis = System.currentTimeMillis(),
        )
      )
      _statusBanner.value = "Jawaban refleksi tersimpan."
    }
  }

  fun createTimeCapsule(
    title: String,
    body: String,
    unlockAtMillis: Long,
    visibility: String,
    attachmentUri: Uri?,
  ) {
    viewModelScope.launch {
      val savedUri =
        if (attachmentUri != null) localRepo.persistMediaUri(attachmentUri).getOrNull()?.localFileUri
        else null
      val capsule =
        TimeCapsuleEntity(
          id = "cap_${UUID.randomUUID().toString().take(12)}",
          ownerUid = currentUserId,
          title = title.trim().ifBlank { "Kapsul Waktu Kita" },
          body = body.trim(),
          attachmentUri = savedUri,
          unlockAtMillis = unlockAtMillis,
          visibility = visibility,
          isSealed = true,
          createdAtMillis = System.currentTimeMillis(),
        )
      localRepo.saveTimeCapsule(capsule)
      val couple = activeCouple.value
      if (visibility == "shared" && couple != null) {
        val pubRes =
          cloudRepo.publishSharedReflection(
            coupleId = couple.id,
            memberUids = couple.memberUids,
            reflectionId = capsule.id,
            authorName = currentDisplayName(),
            kind = "capsule",
            title = capsule.title,
            body = capsule.body,
            unlockAtMillis = capsule.unlockAtMillis,
            isSealed = true,
          )
        if (pubRes.isSuccess) {
          cloudRepo.sendPartnerNotification(
            coupleId = couple.id,
            memberUids = couple.memberUids,
            recipientUid = partnerUidOf(couple),
            kind = "capsule",
            title = "Kapsul Waktu Disegel",
            body = "${currentDisplayName()} menyegel kapsul \"${capsule.title}\" untuk dibuka bersama.",
          )
        }
      }
      _statusBanner.value = "Kapsul waktu disegel hingga tanggal buka."
    }
  }

  suspend fun readEnforcedCapsule(id: String): TimeCapsuleEntity? =
    localRepo.readTimeCapsuleEnforced(id)

  // --- MUSIC & SOUNDTRACK ---

  fun createPlaylist(title: String, subtitle: String, linkedAlbumId: String?) {
    viewModelScope.launch {
      localRepo.savePlaylist(
        PlaylistEntity(
          id = "pl_${UUID.randomUUID().toString().take(10)}",
          title = title.trim().ifBlank { "Daftar Putar Kita" },
          subtitle = subtitle.trim(),
          linkedAlbumId = linkedAlbumId,
          createdAtMillis = System.currentTimeMillis(),
        )
      )
    }
  }

  fun addLocalAudioTrack(playlistId: String, title: String, artist: String, audioUri: Uri, linkedMemoryId: String?) {
    viewModelScope.launch {
      val persisted = localRepo.persistMediaUri(audioUri)
      if (persisted.isSuccess) {
        val order = tracks.value.count { it.playlistId == playlistId }
        localRepo.saveTrack(
          TrackEntity(
            id = "trk_${UUID.randomUUID().toString().take(10)}",
            playlistId = playlistId,
            title = title.trim().ifBlank { persisted.getOrThrow().displayName },
            artist = artist.trim().ifBlank { "Audio Lokal" },
            mediaUri = persisted.getOrThrow().localFileUri,
            sortOrder = order,
            linkedMemoryId = linkedMemoryId,
          )
        )
        _statusBanner.value = "Trek audio lokal ditambahkan."
      } else {
        _statusBanner.value = "Gagal memuat berkas audio."
      }
    }
  }

  fun resetSpotifyImportState() {
    _spotifyImportState.value = SpotifyImportUiState.Idle
  }

  fun importSpotifyPlaylist(
    rawInput: String,
    linkedAlbumId: String?,
    onImported: (String) -> Unit,
  ) {
    viewModelScope.launch {
      _spotifyImportState.value = SpotifyImportUiState.Loading
      val res = SpotifyPlaylistImporter.importFromInput(rawInput)
      if (res.isFailure) {
        _spotifyImportState.value =
          SpotifyImportUiState.Error(
            res.exceptionOrNull()?.localizedMessage
              ?: "Gagal mengimpor metadata playlist dari Spotify."
          )
        return@launch
      }
      val data = res.getOrThrow()
      val playlistId = "pl_sp_${UUID.randomUUID().toString().take(8)}"
      localRepo.savePlaylist(
        PlaylistEntity(
          id = playlistId,
          title = data.title,
          subtitle = data.subtitle,
          linkedAlbumId = linkedAlbumId,
          createdAtMillis = System.currentTimeMillis(),
          sourceType = "spotify",
          externalUrl = data.externalUrl,
          coverUrl = data.coverUrl,
        )
      )
      val savedTracks =
        data.tracks.mapIndexed { idx, spTrack ->
          TrackEntity(
            id = "trk_sp_${UUID.randomUUID().toString().take(10)}",
            playlistId = playlistId,
            title = spTrack.title,
            artist = spTrack.artist,
            mediaUri = "",
            durationMs = spTrack.durationMs,
            sortOrder = idx,
            linkedMemoryId = null,
            sourceType = "spotify",
            externalUrl = spTrack.externalUrl,
            coverUrl = spTrack.coverUrl ?: data.coverUrl,
            youtubeVideoId = null,
          ).also { localRepo.saveTrack(it) }
        }
      _spotifyImportState.value = SpotifyImportUiState.Idle
      onImported(playlistId)

      if (savedTracks.isNotEmpty()) {
        matchTracksWithYouTubeInternal(playlistId, savedTracks)
      }
    }
  }

  fun searchYouTubeDirect(query: String) {
    val clean = query.trim()
    if (clean.isEmpty()) {
      _youtubeDirectSearchState.value = YouTubeDirectSearchUiState.Idle
      return
    }
    viewModelScope.launch {
      _youtubeDirectSearchState.value = YouTubeDirectSearchUiState.Loading
      val res = YouTubeDataApiClient.searchTracks(clean, maxResults = 12)
      res
        .onSuccess { list ->
          _youtubeDirectSearchState.value = YouTubeDirectSearchUiState.Success(clean, list)
        }
        .onFailure { err ->
          _youtubeDirectSearchState.value =
            YouTubeDirectSearchUiState.Error(
              err.localizedMessage ?: "Gagal mencari lagu di YouTube. Periksa koneksi internet."
            )
        }
    }
  }

  fun clearYouTubeDirectSearch() {
    _youtubeDirectSearchState.value = YouTubeDirectSearchUiState.Idle
  }

  fun playYouTubeMatchDirectly(
    match: YouTubeTrackMatch,
    preferredPlaylistId: String? = null,
    onReadyToOpenPlayer: () -> Unit = {},
  ) {
    viewModelScope.launch {
      val existingTrack = tracks.value.firstOrNull { it.youtubeVideoId == match.videoId }
      if (existingTrack != null) {
        val queue =
          tracks.value
            .filter { it.playlistId == existingTrack.playlistId }
            .sortedBy { it.sortOrder }
            .ifEmpty { listOf(existingTrack) }
        playerManager.setQueueAndPlay(queue, existingTrack.id)
        prefs.savePlaybackState(existingTrack.id, 0L)
        onReadyToOpenPlayer()
        return@launch
      }

      val targetPlaylistId =
        preferredPlaylistId?.takeIf { id -> playlists.value.any { it.id == id } }
          ?: playlists.value.firstOrNull { it.id == "pl_yt_direct" }?.id
          ?: run {
            val createdId = "pl_yt_direct"
            localRepo.savePlaylist(
              PlaylistEntity(
                id = createdId,
                title = "Lagu YouTube",
                subtitle = "Putar langsung dari pencarian YouTube",
                linkedAlbumId = null,
                createdAtMillis = System.currentTimeMillis(),
                sourceType = "youtube",
              )
            )
            createdId
          }

      val currentQueue = tracks.value.filter { it.playlistId == targetPlaylistId }.sortedBy { it.sortOrder }
      val newTrack =
        TrackEntity(
          id = "trk_yt_${UUID.randomUUID().toString().take(10)}",
          playlistId = targetPlaylistId,
          title = match.title,
          artist = match.channelTitle.ifBlank { "YouTube Audio" },
          mediaUri = "",
          durationMs = match.durationMs,
          sortOrder = currentQueue.size,
          linkedMemoryId = null,
          sourceType = "youtube",
          externalUrl = "https://www.youtube.com/watch?v=${match.videoId}",
          coverUrl = YouTubeDataApiClient.thumbnailUrl(match.videoId),
          youtubeVideoId = match.videoId,
        )
      localRepo.saveTrack(newTrack)
      val updatedQueue = currentQueue + newTrack
      playerManager.setQueueAndPlay(updatedQueue, newTrack.id)
      prefs.savePlaybackState(newTrack.id, 0L)
      onReadyToOpenPlayer()
    }
  }

  fun matchPlaylistTracksWithYouTube(playlistId: String) {
    val playlistTracks = tracks.value.filter { it.playlistId == playlistId }.sortedBy { it.sortOrder }
    if (playlistTracks.isEmpty()) return
    viewModelScope.launch {
      matchTracksWithYouTubeInternal(playlistId, playlistTracks)
    }
  }

  fun matchSingleTrackWithYouTube(
    track: TrackEntity,
    playImmediately: Boolean = true,
    onReadyToOpenPlayer: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _youtubeMatchingPlaylistId.value = track.playlistId
      val result =
        YouTubeDataApiClient.searchBestPlayableTrack(
          title = track.title,
          artist = track.artist,
          targetDurationMs = track.durationMs,
        )
      _youtubeMatchingPlaylistId.value = null
      when (result) {
        is YouTubeSearchResult.Found -> {
          val updated =
            track.copy(
              youtubeVideoId = result.match.videoId,
              coverUrl = track.coverUrl ?: YouTubeDataApiClient.thumbnailUrl(result.match.videoId),
              durationMs = if (track.durationMs > 0L) track.durationMs else result.match.durationMs,
            )
          localRepo.saveTrack(updated)
          if (playImmediately) {
            val queue =
              tracks.value
                .filter { it.playlistId == updated.playlistId }
                .sortedBy { it.sortOrder }
                .map { if (it.id == updated.id) updated else it }
                .ifEmpty { listOf(updated) }
            playerManager.setQueueAndPlay(queue, updated.id)
            prefs.savePlaybackState(updated.id, 0L)
            onReadyToOpenPlayer()
          } else {
            playerManager.refreshTrackInQueue(updated)
            _statusBanner.value = "Lagu penuh YouTube ditemukan: \"${result.match.title}\"."
          }
        }
        is YouTubeSearchResult.ApiError -> {
          _statusBanner.value = "YouTube error (${result.statusCode}): ${result.message}"
        }
        is YouTubeSearchResult.NotFound -> {
          _statusBanner.value = "Tidak menemukan hasil yang cocok untuk \"${track.title}\" di YouTube."
        }
        YouTubeSearchResult.MissingApiKey -> {
          _statusBanner.value = "Tidak dapat mencari lagu di YouTube."
        }
      }
    }
  }

  private suspend fun matchTracksWithYouTubeInternal(
    playlistId: String,
    tracksToProcess: List<TrackEntity>,
  ) {
    if (_youtubeMatchingPlaylistId.value == playlistId) return
    val unmatched = tracksToProcess.filter { it.youtubeVideoId.isNullOrBlank() }
    if (unmatched.isEmpty()) {
      _statusBanner.value = "Semua lagu di playlist sudah terhubung dengan YouTube."
      return
    }
    _youtubeMatchingPlaylistId.value = playlistId
    var matchedCount = 0
    var lastError: String? = null
    try {
      coroutineScope {
        for (chunk in unmatched.take(50).chunked(4)) {
          val outcomes =
            chunk
              .map { track ->
                async {
                  track to
                    YouTubeDataApiClient.searchBestPlayableTrack(
                      title = track.title,
                      artist = track.artist,
                      targetDurationMs = track.durationMs,
                    )
                }
              }
              .awaitAll()
          for ((track, res) in outcomes) {
            when (res) {
              is YouTubeSearchResult.Found -> {
                val updated =
                  track.copy(
                    youtubeVideoId = res.match.videoId,
                    coverUrl = track.coverUrl ?: YouTubeDataApiClient.thumbnailUrl(res.match.videoId),
                    durationMs = if (track.durationMs > 0L) track.durationMs else res.match.durationMs,
                  )
                localRepo.saveTrack(updated)
                val playerSnapshot = playerManager.state.value
                if (playerSnapshot.currentTrack?.id == updated.id || playerSnapshot.queue.any { it.id == updated.id }) {
                  playerManager.refreshTrackInQueue(updated)
                }
                matchedCount++
              }
              is YouTubeSearchResult.ApiError -> {
                if (lastError == null) {
                  lastError = "YouTube (${res.statusCode}): ${res.message}"
                }
              }
              is YouTubeSearchResult.MissingApiKey -> Unit
              is YouTubeSearchResult.NotFound -> Unit
            }
          }
        }
      }
    } finally {
      _youtubeMatchingPlaylistId.value = null
    }
    _statusBanner.value =
      when {
        matchedCount > 0 ->
          "Dicocokkan $matchedCount dari ${unmatched.size} lagu dari YouTube."
        lastError != null -> lastError
        else ->
          "Belum menemukan video YouTube yang cocok untuk lagu di playlist ini."
      }
  }

  fun attachLocalAudioToTrack(track: TrackEntity, audioUri: Uri) {
    viewModelScope.launch {
      val persisted = localRepo.persistMediaUri(audioUri)
      if (persisted.isSuccess) {
        val updated = track.copy(mediaUri = persisted.getOrThrow().localFileUri)
        localRepo.saveTrack(updated)
        playerManager.refreshTrackInQueue(updated)
        _statusBanner.value = "Audio lokal penuh ditautkan ke \"${track.title}\"."
      } else {
        _statusBanner.value = "Gagal membaca berkas audio lokal."
      }
    }
  }

  fun batchAttachLocalAudioToPlaylist(playlistId: String, audioUris: List<Uri>) {
    if (audioUris.isEmpty()) return
    viewModelScope.launch {
      val playlistTracks = tracks.value.filter { it.playlistId == playlistId }.sortedBy { it.sortOrder }
      val candidates = audioUris.map { uri -> localRepo.resolveDisplayName(uri) to uri }
      val matchedPairs = matchLocalAudioToTracks(playlistTracks, candidates)
      var linkedCount = 0
      matchedPairs.forEach { (track, uri) ->
        val persisted = localRepo.persistMediaUri(uri)
        if (persisted.isSuccess) {
          val updated = track.copy(mediaUri = persisted.getOrThrow().localFileUri)
          localRepo.saveTrack(updated)
          playerManager.refreshTrackInQueue(updated)
          linkedCount++
        }
      }
      _statusBanner.value =
        if (linkedCount > 0) {
          "Berhasil menautkan $linkedCount lagu audio penuh ke playlist."
        } else {
          "Nama berkas audio tidak cocok otomatis dengan judul lagu. Gunakan menu ⋮ pada lagu untuk tautkan manual."
        }
    }
  }

  fun deletePlaylist(playlistId: String) {
    viewModelScope.launch {
      playerManager.clearPlaylistIfActive(playlistId)
      localRepo.deletePlaylist(playlistId)
      _statusBanner.value = "Playlist dihapus."
    }
  }

  fun deleteTrack(trackId: String) {
    viewModelScope.launch { localRepo.deleteTrack(trackId) }
  }

  fun moveTrackOrder(playlistTracks: List<TrackEntity>, fromIndex: Int, toIndex: Int) {
    if (fromIndex !in playlistTracks.indices || toIndex !in playlistTracks.indices || fromIndex == toIndex) return
    val mutable = playlistTracks.toMutableList()
    val moved = mutable.removeAt(fromIndex)
    mutable.add(toIndex, moved)
    viewModelScope.launch {
      mutable.forEachIndexed { idx, track ->
        if (track.sortOrder != idx) {
          localRepo.saveTrack(track.copy(sortOrder = idx))
        }
      }
    }
  }

  fun playPlaylistTrack(playlistTracks: List<TrackEntity>, trackId: String) {
    val target = playlistTracks.firstOrNull { it.id == trackId } ?: playlistTracks.firstOrNull()
    val hasLocalFile = target != null && target.mediaUri.isNotBlank() && !target.mediaUri.startsWith("http")
    val hasYouTube = target != null && !target.youtubeVideoId.isNullOrBlank()
    if (target != null && target.sourceType == "spotify" && !hasLocalFile && !hasYouTube) {
      matchSingleTrackWithYouTube(target, playImmediately = true)
      return
    }
    playerManager.setQueueAndPlay(playlistTracks, trackId)
    viewModelScope.launch { prefs.savePlaybackState(trackId, 0L) }
  }

  fun playNextTrack() {
    val curState = playerState.value
    val q = curState.queue
    val cur = curState.currentTrack ?: return
    if (q.isEmpty()) return
    val idx = q.indexOfFirst { it.id == cur.id }
    val nextTrack = q[(idx + 1).mod(q.size)]
    val latestNext = tracks.value.firstOrNull { it.id == nextTrack.id } ?: nextTrack
    val updatedQueue = q.map { candidate -> tracks.value.firstOrNull { it.id == candidate.id } ?: candidate }
    playPlaylistTrack(updatedQueue, latestNext.id)
  }

  fun playPreviousTrack() {
    val curState = playerState.value
    val q = curState.queue
    val cur = curState.currentTrack ?: return
    if (q.isEmpty()) return
    val idx = q.indexOfFirst { it.id == cur.id }
    val prevTrack = q[(idx - 1).mod(q.size)]
    val latestPrev = tracks.value.firstOrNull { it.id == prevTrack.id } ?: prevTrack
    val updatedQueue = q.map { candidate -> tracks.value.firstOrNull { it.id == candidate.id } ?: candidate }
    playPlaylistTrack(updatedQueue, latestPrev.id)
  }

  fun sendTrackToPartner(track: TrackEntity) {
    val couple = activeCouple.value
    if (couple == null) {
      _statusBanner.value = "Hubungkan pasangan terlebih dahulu untuk mengirim lagu."
      return
    }
    val items =
      listOf(
        SharedMusicTrackItem(
          title = track.title,
          artist = track.artist,
          durationMs = track.durationMs,
          sourceType = track.sourceType,
          externalUrl = track.externalUrl,
          coverUrl = track.coverUrl,
          youtubeVideoId = track.youtubeVideoId,
        )
      )
    val partnerUid = partnerUidOf(couple)
    val myName = currentDisplayName()
    viewModelScope.launch {
      val res =
        cloudRepo.sendSharedMusic(
          coupleId = couple.id,
          memberUids = couple.memberUids,
          senderName = myName,
          recipientUid = partnerUid,
          kind = "track",
          title = track.title,
          subtitle = track.artist.ifBlank { "Lagu pilihan" },
          externalUrl = track.externalUrl,
          coverUrl = track.coverUrl,
          tracksJson = musicShareJson.encodeToString(items),
        )
      if (res.isSuccess) {
        cloudRepo.sendPartnerNotification(
          coupleId = couple.id,
          memberUids = couple.memberUids,
          recipientUid = partnerUid,
          kind = "share",
          title = "Lagu Dikirim Untukmu",
          body = "$myName mengirimkan lagu \"${track.title}\".",
        )
      }
      _statusBanner.value =
        if (res.isSuccess) "Lagu \"${track.title}\" dikirim ke pasangan."
        else "Gagal mengirim lagu: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun sendPlaylistToPartner(playlist: PlaylistEntity, playlistTracks: List<TrackEntity>) {
    val couple = activeCouple.value
    if (couple == null) {
      _statusBanner.value = "Hubungkan pasangan terlebih dahulu untuk mengirim playlist."
      return
    }
    if (playlistTracks.isEmpty()) {
      _statusBanner.value = "Tambahkan minimal satu lagu ke playlist sebelum mengirim."
      return
    }
    val items =
      playlistTracks.take(40).map { t ->
        SharedMusicTrackItem(
          title = t.title,
          artist = t.artist,
          durationMs = t.durationMs,
          sourceType = t.sourceType,
          externalUrl = t.externalUrl,
          coverUrl = t.coverUrl,
          youtubeVideoId = t.youtubeVideoId,
        )
      }
    val partnerUid = partnerUidOf(couple)
    val myName = currentDisplayName()
    viewModelScope.launch {
      val res =
        cloudRepo.sendSharedMusic(
          coupleId = couple.id,
          memberUids = couple.memberUids,
          senderName = myName,
          recipientUid = partnerUid,
          kind = "playlist",
          title = playlist.title,
          subtitle = playlist.subtitle.ifBlank { "${items.size} lagu dari $myName" },
          externalUrl = playlist.externalUrl,
          coverUrl = playlist.coverUrl,
          tracksJson = musicShareJson.encodeToString(items),
        )
      if (res.isSuccess) {
        cloudRepo.sendPartnerNotification(
          coupleId = couple.id,
          memberUids = couple.memberUids,
          recipientUid = partnerUid,
          kind = "share",
          title = "Playlist Baru Untukmu",
          body = "$myName membagikan playlist \"${playlist.title}\" (${items.size} lagu).",
        )
      }
      _statusBanner.value =
        if (res.isSuccess) "Playlist \"${playlist.title}\" dikirim ke pasangan."
        else "Gagal mengirim playlist: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun respondSharedMusic(
    share: SharedMusicDto,
    accept: Boolean,
    playImmediately: Boolean = false,
  ) {
    val couple = activeCouple.value ?: return
    viewModelScope.launch {
      val newStatus = if (accept) "accepted" else "declined"
      val res = cloudRepo.respondToSharedMusic(couple.id, share.id, newStatus)
      if (res.isSuccess && accept) {
        val parsedTracks =
          runCatching { musicShareJson.decodeFromString<List<SharedMusicTrackItem>>(share.tracksJson) }
            .getOrDefault(emptyList())
        if (parsedTracks.isNotEmpty()) {
          val now = System.currentTimeMillis()
          val playlistId = "pl_shared_${share.id}"
          val playlist =
            PlaylistEntity(
              id = playlistId,
              title =
                if (share.kind == "track") "Kiriman ${share.senderName}: ${share.title}"
                else share.title,
              subtitle = "Dari ${share.senderName} • ${parsedTracks.size} lagu",
              createdAtMillis = now,
              sourceType = parsedTracks.first().sourceType,
              externalUrl = share.externalUrl,
              coverUrl = share.coverUrl,
            )
          localRepo.savePlaylist(playlist)
          val savedTracks =
            parsedTracks.mapIndexed { idx, item ->
              val trk =
                TrackEntity(
                  id = "trk_${share.id}_$idx",
                  playlistId = playlistId,
                  title = item.title,
                  artist = item.artist,
                  mediaUri = "",
                  durationMs = item.durationMs,
                  sortOrder = idx,
                  sourceType = item.sourceType,
                  externalUrl = item.externalUrl,
                  coverUrl = item.coverUrl,
                  youtubeVideoId = item.youtubeVideoId,
                )
              localRepo.saveTrack(trk)
              trk
            }
          if (playImmediately && savedTracks.isNotEmpty()) {
            playPlaylistTrack(savedTracks, savedTracks.first().id)
          }
        }
        cloudRepo.sendPartnerNotification(
          coupleId = couple.id,
          memberUids = couple.memberUids,
          recipientUid = share.senderUid,
          kind = "share",
          title = "Kiriman Musik Diterima",
          body = "${currentDisplayName()} menyimpan \"${share.title}\" ke koleksi musiknya.",
        )
      }
      _statusBanner.value =
        when {
          res.isFailure -> "Gagal memproses kiriman musik: ${res.exceptionOrNull()?.localizedMessage}"
          accept -> "Musik dari pasangan disimpan ke daftar playlist."
          else -> "Kiriman musik ditolak."
        }
    }
  }

  // --- COUPLE CONNECTION & INVITATION CODES ---

  fun generatePartnerCode() {
    viewModelScope.launch {
      _inviteLoading.value = true
      _inviteActionError.value = null
      val myName = userProfile.value?.displayName ?: initialDisplayName
      val res = cloudRepo.createInvitation(myName)
      _inviteLoading.value = false
      if (res.isSuccess) {
        _generatedInvitation.value = res.getOrThrow()
      } else {
        _inviteActionError.value = res.exceptionOrNull()?.localizedMessage ?: "Gagal membuat kode."
      }
    }
  }

  fun cancelInvitation(codeHash: String) {
    viewModelScope.launch {
      _inviteLoading.value = true
      val res = cloudRepo.cancelInvitation(codeHash)
      _inviteLoading.value = false
      if (res.isSuccess) {
        if (_generatedInvitation.value?.codeHash == codeHash) _generatedInvitation.value = null
        _statusBanner.value = "Kode undangan dibatalkan."
      } else {
        _inviteActionError.value = res.exceptionOrNull()?.localizedMessage
      }
    }
  }

  fun lookupInvitationCode(rawCode: String) {
    viewModelScope.launch {
      _inviteLoading.value = true
      _inviteActionError.value = null
      _lookedUpInvitation.value = null
      val res = cloudRepo.lookupInvitation(rawCode)
      _inviteLoading.value = false
      if (res.isSuccess) {
        _lookedUpInvitation.value = res.getOrThrow()
      } else {
        _inviteActionError.value = res.exceptionOrNull()?.localizedMessage ?: "Kode tidak valid."
      }
    }
  }

  fun acceptLookedUpInvitation(invite: InvitationDto) {
    viewModelScope.launch {
      _inviteLoading.value = true
      _inviteActionError.value = null
      val myName = userProfile.value?.displayName ?: initialDisplayName
      val res = cloudRepo.acceptInvitation(invite.codeHash, myName)
      _inviteLoading.value = false
      if (res.isSuccess) {
        _lookedUpInvitation.value = null
        localRepo.addLocalNotification(
          kind = "invite",
          title = "Terhubung dengan pasangan",
          body = "Kalian kini terhubung di ruang EVORIA.",
        )
        _statusBanner.value = "Selamat! Akun kalian kini terhubung."
      } else {
        _inviteActionError.value = res.exceptionOrNull()?.localizedMessage ?: "Gagal menerima undangan."
      }
    }
  }

  fun declineLookedUpInvitation(invite: InvitationDto) {
    viewModelScope.launch {
      _inviteLoading.value = true
      val myName = userProfile.value?.displayName ?: initialDisplayName
      cloudRepo.declineInvitation(invite.codeHash, myName)
      _inviteLoading.value = false
      _lookedUpInvitation.value = null
      _statusBanner.value = "Undangan ditolak."
    }
  }

  fun clearLookedUpInvitation() {
    _lookedUpInvitation.value = null
    _inviteActionError.value = null
  }

  fun disconnectCouple() {
    val couple = activeCouple.value ?: return
    viewModelScope.launch {
      val res = cloudRepo.disconnectCouple(couple.id)
      _statusBanner.value =
        if (res.isSuccess) "Koneksi pasangan diputuskan."
        else "Gagal memutus koneksi: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  // --- APP LOCK & SECURITY ---

  fun configurePin(pin: String) {
    viewModelScope.launch {
      prefs.configurePin(pin)
      localRepo.addLocalNotification(
        kind = "security",
        title = "PIN EVORIA aktif",
        body = "Kunci aplikasi 6-digit berhasil disimpan aman dengan salt & PBKDF2.",
      )
      _statusBanner.value = "PIN 6-digit berhasil dikonfigurasi."
    }
  }

  fun disableAppLock() {
    viewModelScope.launch {
      prefs.clearPinAndDisableLock()
      _isLocked.value = false
      _statusBanner.value = "Kunci aplikasi dinonaktifkan."
    }
  }

  fun setPreferredLockMethod(method: LockMethod) {
    viewModelScope.launch { prefs.setPreferredLockMethod(method) }
  }

  fun setAutoLockTimeout(timeout: AutoLockTimeout) {
    viewModelScope.launch { prefs.setAutoLockTimeout(timeout) }
  }

  suspend fun verifyPin(pin: String): PinVerifyResult = prefs.verifyPin(pin)

  // --- BACKUP, RESTORE, EXPORT, DELETE ---

  fun backupMetadataToCloud(deviceLabel: String = "Perangkat Android") {
    viewModelScope.launch {
      val payloadJson = localRepo.exportBackupJson(currentUserId)
      val res =
        cloudRepo.savePrivateBackup(
          deviceLabel = deviceLabel,
          albumCount = localAlbums.value.size.toLong(),
          memoryCount = localMemories.value.size.toLong(),
          reflectionCount = (journals.value.size + letters.value.size).toLong(),
          payloadJson = payloadJson,
        )
      if (res.isSuccess) {
        val now = System.currentTimeMillis()
        localRepo.updateSyncCheckpoint(
          syncMillis = now,
          backupMillis = now,
          message = "Cadangan metadata privat berhasil disimpan ke Firestore.",
        )
        _statusBanner.value = "Cadangan metadata berhasil disimpan."
      } else {
        _statusBanner.value = "Gagal mencadangkan: ${res.exceptionOrNull()?.localizedMessage}"
      }
    }
  }

  fun restoreLatestCloudBackup() {
    viewModelScope.launch {
      val latest = cloudRepo.getLatestPrivateBackup()
      if (latest.isFailure) {
        _statusBanner.value = "Gagal mengambil cadangan: ${latest.exceptionOrNull()?.localizedMessage}"
        return@launch
      }
      val dto = latest.getOrNull()
      if (dto == null) {
        _statusBanner.value = "Belum ada cadangan metadata di cloud."
        return@launch
      }
      val restored = localRepo.restoreBackupJson(currentUserId, dto.payloadJson)
      _statusBanner.value =
        if (restored.isSuccess) "Berhasil memulihkan ${restored.getOrThrow()} item dari cadangan."
        else "Format cadangan tidak dapat dipulihkan."
    }
  }

  suspend fun generateExportJson(): String = localRepo.exportBackupJson(currentUserId)

  fun clearAllLocalData() {
    viewModelScope.launch {
      val oldBanner = userPrefs.value.profileBannerUri
      prefs.clearProfileBanner()
      if (!oldBanner.isNullOrBlank()) {
        withContext(Dispatchers.IO) { MediaStorageHelper.deletePersistedFile(oldBanner) }
      }
      localRepo.clearAllUserLocalData(currentUserId)
      refreshLocalStorageLabel()
      _statusBanner.value = "Semua data lokal di perangkat ini telah dihapus."
    }
  }

  fun markAllNotificationsRead() {
    viewModelScope.launch {
      localRepo.markAllNotificationsRead()
      val couple = activeCouple.value ?: return@launch
      val unreadRemoteIds =
        remoteNotifications.value
          .filter { !it.isRead && it.recipientUid == currentUserId }
          .map { it.id }
      if (unreadRemoteIds.isNotEmpty()) {
        cloudRepo.markRemoteNotificationsRead(couple.id, unreadRemoteIds)
      }
    }
  }

  fun getLocalStorageLabel(): String = _localStorageLabel.value

  private companion object {
    const val TAG = "EvoriaViewModel"
  }
}

internal fun matchLocalAudioToTracks(
  tracks: List<TrackEntity>,
  audioCandidates: List<Pair<String, Uri>>,
): List<Pair<TrackEntity, Uri>> {
  fun normalizeWords(raw: String): Set<String> {
    val clean =
      raw
        .lowercase()
        .replace(Regex("""\.(mp3|m4a|wav|flac|ogg|aac)$"""), "")
        .replace(Regex("""\([^)]*\)"""), " ")
        .replace(Regex("""\[[^\]]*\]"""), " ")
        .replace(Regex("""[^a-z0-9\s]"""), " ")
    return clean.split(Regex("""\s+""")).filter { it.length >= 2 }.toSet()
  }

  val unmatchedTracks = tracks.toMutableList()
  val matches = mutableListOf<Pair<TrackEntity, Uri>>()

  audioCandidates.forEach { (filename, uri) ->
    val fileWords = normalizeWords(filename)
    if (fileWords.isEmpty()) return@forEach
    val fileLower = filename.lowercase()

    val bestTrack =
      unmatchedTracks
        .filter { it.mediaUri.isBlank() || it.mediaUri.startsWith("http") }
        .mapNotNull { trk ->
          val titleWords = normalizeWords(trk.title)
          if (titleWords.isEmpty()) return@mapNotNull null
          val overlap = titleWords.count { it in fileWords }
          val ratio = overlap.toFloat() / titleWords.size.toFloat()
          val directSubstring =
            fileLower.contains(trk.title.lowercase().take(12).trim()) && trk.title.length >= 4
          if (ratio >= 0.6f || directSubstring) {
            trk to (ratio + if (directSubstring) 0.5f else 0f)
          } else {
            null
          }
        }
        .maxByOrNull { it.second }
        ?.first

    if (bestTrack != null) {
      unmatchedTracks.remove(bestTrack)
      matches.add(bestTrack to uri)
    }
  }
  return matches
}
