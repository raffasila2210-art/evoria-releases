package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.EvoriaSearchEngine
import com.example.data.ai.SearchOutcome
import com.example.data.local.AppUserPrefs
import com.example.data.local.AutoLockTimeout
import com.example.data.local.DEVELOPER_ADMIN_EMAIL
import com.example.data.local.EvoriaAccentPreset
import com.example.data.local.EvoriaSanctuaryRepository
import com.example.data.local.JournalEntity
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
import com.example.data.remote.SpotifyPlaylistImporter
import com.example.data.remote.UserProfileDto
import com.example.data.remote.YouTubeDataApiClient
import com.example.data.remote.YouTubeSearchResult
import com.example.data.remote.unwrapFirestoreException
import com.example.media.EvoriaPlayerManager
import com.example.media.MusicPlayerState
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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

@OptIn(ExperimentalCoroutinesApi::class)
class EvoriaViewModel(
  private val currentUserId: String,
  private val initialDisplayName: String,
  private val localRepo: EvoriaSanctuaryRepository,
  private val cloudRepo: EvoriaCloudRepository,
  private val prefs: SecurityPreferences,
  private val searchEngine: EvoriaSearchEngine,
  val playerManager: EvoriaPlayerManager,
  private val currentUserEmail: String? = null,
) : ViewModel() {

  val userPrefs: StateFlow<AppUserPrefs> =
    prefs.userPrefsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppUserPrefs())

  private val _optimisticActiveTab = MutableStateFlow<Int?>(null)
  val activeTabIndex: StateFlow<Int> =
    combine(prefs.userPrefsFlow, _optimisticActiveTab) { p, optimistic ->
        (optimistic ?: p.activeTabIndex).coerceIn(0, 4)
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

  val securitySettings: StateFlow<SecuritySettings> =
    prefs.securitySettingsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SecuritySettings())

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

  val remoteNotifications: StateFlow<List<NotificationItemDto>> =
    activeCouple
      .flatMapLatest { couple ->
        if (couple == null) flowOf(emptyList())
        else cloudRepo.observeNotifications(couple.id).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val localAlbums: StateFlow<List<LocalAlbumEntity>> =
    localRepo.observeAlbums(currentUserId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

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

  // Transient UI states
  private val _isLocked = MutableStateFlow(false)
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

  private val _localStorageLabel = MutableStateFlow("Menghitung...")
  val localStorageLabel: StateFlow<String> = _localStorageLabel.asStateFlow()

  init {
    refreshLocalStorageLabel()
    // Recompute local storage label off the main thread when media-bearing tables change
    viewModelScope.launch {
      combine(localMemories, localAlbums, tracks) { m, a, t -> Triple(m.size, a.size, t.size) }
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

  fun lockNow() {
    if (securitySettings.value.appLockEnabled) {
      _isLocked.value = true
    }
  }

  fun unlockSucceeded() {
    _isLocked.value = false
  }

  fun evaluateAutoLockOnResume() {
    val sec = securitySettings.value
    if (!sec.appLockEnabled || !sec.hasPinConfigured) return
    if (sec.lastBackgroundMillis <= 0L) {
      _isLocked.value = true
      return
    }
    val elapsedSec = (System.currentTimeMillis() - sec.lastBackgroundMillis) / 1000L
    if (elapsedSec >= sec.autoLockTimeout.seconds) {
      _isLocked.value = true
    }
  }

  fun onAppBackgrounded() {
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
        _bloomMessage.value = "Album \"${album.title}\" kini menjadi bagian dari ruang kalian."
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
      uris.forEachIndexed { idx, uri ->
        val persisted = localRepo.persistMediaUri(uri).getOrNull() ?: return@forEachIndexed
        val memId = "mem_${UUID.randomUUID().toString().take(12)}"
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
        savedCount++
      }
      if (savedCount > 0) {
        _bloomMessage.value = "$savedCount kenangan baru disimpan dengan hangat."
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
    val partnerUid =
      if (couple.memberOneUid == currentUserId) couple.memberTwoUid else couple.memberOneUid
    val myName = userProfile.value?.displayName ?: initialDisplayName
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
      val outcome =
        searchEngine.searchMemories(
          query = query,
          candidates = filtered,
          allowCloudAi = userPrefs.value.aiSearchConsent,
        )
      _searchState.value = SearchUiState.Success(outcome)
    }
  }

  fun clearSearchHistory() {
    viewModelScope.launch { prefs.clearSearchHistory() }
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
      val savedAttachment =
        if (attachmentUri != null) localRepo.persistMediaUri(attachmentUri).getOrNull()?.localFileUri
        else existingAttachment
      localRepo.saveJournal(
        JournalEntity(
          id = existingId ?: "jrn_${UUID.randomUUID().toString().take(12)}",
          ownerUid = currentUserId,
          title = title.trim().ifBlank { "Catatan Hari Ini" },
          body = body.trim(),
          attachmentUri = savedAttachment,
          visibility = visibility,
          moodTag = moodTag,
          isDraft = isDraft,
          createdAtMillis = now,
          updatedAtMillis = now,
        )
      )
      _statusBanner.value = if (isDraft) "Draf jurnal disimpan." else "Jurnal tersimpan."
    }
  }

  fun deleteJournal(id: String) {
    viewModelScope.launch { localRepo.deleteJournal(id) }
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
      localRepo.saveLetter(
        LetterEntity(
          id = existingId ?: "ltr_${UUID.randomUUID().toString().take(12)}",
          ownerUid = currentUserId,
          title = title.trim().ifBlank { "Surat untukmu" },
          body = body.trim(),
          recipientName = recipientName.trim().ifBlank { "Kamu" },
          isSealed = isSealed,
          isDraft = isDraft,
          isShared = isShared,
          createdAtMillis = now,
          updatedAtMillis = now,
        )
      )
      _statusBanner.value = if (isDraft) "Draf surat disimpan." else "Surat tersimpan dengan aman."
    }
  }

  fun deleteLetter(id: String) {
    viewModelScope.launch { localRepo.deleteLetter(id) }
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

      if (!YouTubeDataApiClient.isApiKeyConfigured()) {
        _statusBanner.value =
          "Metadata ${savedTracks.size} lagu diimpor. Atur YOUTUBE_API_KEY di panel Secrets AI Studio untuk mencocokkan lagu penuh dari YouTube."
      } else {
        matchTracksWithYouTubeInternal(playlistId, savedTracks)
      }
    }
  }

  fun matchPlaylistTracksWithYouTube(playlistId: String) {
    val playlistTracks = tracks.value.filter { it.playlistId == playlistId }.sortedBy { it.sortOrder }
    if (playlistTracks.isEmpty()) return
    viewModelScope.launch {
      if (!YouTubeDataApiClient.isApiKeyConfigured()) {
        _statusBanner.value =
          "YOUTUBE_API_KEY belum diisi di panel Secrets AI Studio. Tambahkan API key agar pencarian lagu penuh YouTube aktif."
        return@launch
      }
      matchTracksWithYouTubeInternal(playlistId, playlistTracks)
    }
  }

  fun matchSingleTrackWithYouTube(track: TrackEntity) {
    viewModelScope.launch {
      if (!YouTubeDataApiClient.isApiKeyConfigured()) {
        _statusBanner.value =
          "YOUTUBE_API_KEY belum diisi di panel Secrets AI Studio."
        return@launch
      }
      _youtubeMatchingPlaylistId.value = track.playlistId
      val result =
        YouTubeDataApiClient.searchFullTrack(
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
              durationMs = if (track.durationMs > 0L) track.durationMs else result.match.durationMs,
            )
          localRepo.saveTrack(updated)
          playerManager.refreshTrackInQueue(updated)
          _statusBanner.value = "Lagu penuh YouTube ditemukan: \"${result.match.title}\"."
        }
        is YouTubeSearchResult.ApiError -> {
          _statusBanner.value = "YouTube API error (${result.statusCode}): ${result.message}"
        }
        is YouTubeSearchResult.NotFound -> {
          _statusBanner.value = "Tidak menemukan hasil yang cocok untuk \"${track.title}\" di YouTube."
        }
        YouTubeSearchResult.MissingApiKey -> {
          _statusBanner.value = "YOUTUBE_API_KEY belum dikonfigurasi."
        }
      }
    }
  }

  private suspend fun matchTracksWithYouTubeInternal(
    playlistId: String,
    tracksToProcess: List<TrackEntity>,
  ) {
    val unmatched = tracksToProcess.filter { it.youtubeVideoId.isNullOrBlank() }
    if (unmatched.isEmpty()) {
      _statusBanner.value = "Semua lagu di playlist sudah terhubung dengan YouTube."
      return
    }
    _youtubeMatchingPlaylistId.value = playlistId
    var matchedCount = 0
    var fatalError: String? = null
    try {
      coroutineScope {
        for (chunk in unmatched.take(50).chunked(3)) {
          if (fatalError != null) break
          val outcomes =
            chunk
              .map { track ->
                async {
                  track to
                    YouTubeDataApiClient.searchFullTrack(
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
                    durationMs = if (track.durationMs > 0L) track.durationMs else res.match.durationMs,
                  )
                localRepo.saveTrack(updated)
                playerManager.refreshTrackInQueue(updated)
                matchedCount++
              }
              is YouTubeSearchResult.ApiError -> {
                if (fatalError == null) {
                  fatalError = "YouTube API (${res.statusCode}): ${res.message}"
                }
              }
              is YouTubeSearchResult.MissingApiKey -> {
                fatalError = "YOUTUBE_API_KEY belum diisi di panel Secrets AI Studio."
              }
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
        fatalError != null && matchedCount == 0 -> fatalError
        fatalError != null -> "Dicocokkan $matchedCount lagu YouTube sebelum terhenti: $fatalError"
        matchedCount > 0 ->
          "Diimpor & dicocokkan $matchedCount dari ${unmatched.size} lagu penuh via YouTube Data API."
        else ->
          "Metadata diimpor, namun belum ada lagu yang cocok otomatis di YouTube Data API."
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
    playerManager.setQueueAndPlay(playlistTracks, trackId)
    viewModelScope.launch { prefs.savePlaybackState(trackId, 0L) }
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
      localRepo.clearAllUserLocalData(currentUserId)
      refreshLocalStorageLabel()
      _statusBanner.value = "Semua data lokal di perangkat ini telah dihapus."
    }
  }

  fun markAllNotificationsRead() {
    viewModelScope.launch { localRepo.markAllNotificationsRead() }
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
