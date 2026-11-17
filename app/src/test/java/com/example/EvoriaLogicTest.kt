package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.example.data.ai.EvoriaSearchEngine
import com.example.data.local.AppUserPrefs
import com.example.data.local.AutoLockTimeout
import com.example.data.local.EvoriaAccentPreset
import com.example.data.local.EvoriaDatabase
import com.example.data.local.EvoriaSanctuaryRepository
import com.example.data.local.KenanganCollectionEntity
import com.example.data.local.LocalAlbumEntity
import com.example.data.local.LocalMemoryEntity
import com.example.data.local.LockMethod
import com.example.data.local.NavbarGlassIntensity
import com.example.data.local.NavbarThemeStyle
import com.example.data.local.PinVerifyResult
import com.example.data.local.PlaylistEntity
import com.example.data.local.SecurityPreferences
import com.example.data.local.SecuritySettings
import com.example.data.local.ThemeMode
import com.example.data.local.TimeCapsuleEntity
import com.example.data.local.TrackEntity
import com.example.data.local.UploadJobEntity
import com.example.data.local.canEditDeveloperSocials
import com.example.data.remote.SharedMusicDto
import com.example.data.remote.SharedReflectionDto
import com.example.data.remote.SpotifyPlaylistImporter
import com.example.data.remote.YouTubeDataApiClient
import com.example.data.remote.YouTubeTrackMatch
import com.example.media.EvoriaPlayerManager
import com.example.media.MusicPlayerState
import com.example.notifications.EvoriaNotificationHelper
import com.example.ui.AiMemoryDraftUiState
import com.example.ui.AiReflectionUiState
import com.example.ui.SearchUiState
import com.example.ui.YouTubeDirectSearchUiState
import com.example.ui.components.LiquidGlassInteractionState
import com.example.ui.components.LiquidGlassNavbar
import com.example.ui.components.LiquidGlassTelemetry
import com.example.ui.components.computeLiquidGlassKinematics
import com.example.ui.components.computeNavbarWaistPinchPx
import com.example.ui.components.computeProjectedSnapTabIndex
import com.example.ui.components.computeSnapTabIndex
import com.example.ui.components.computeTouchTargetLeftPx
import com.example.ui.components.formatIndoDate
import com.example.ui.matchLocalAudioToTracks
import com.example.ui.screens.AboutEvoriaScreen
import com.example.ui.screens.AddOrEditMemoryScreen
import com.example.ui.screens.AppearanceSettingsScreen
import com.example.ui.screens.JelajahScreen
import com.example.ui.screens.KenanganScreen
import com.example.ui.screens.MusicAndSoundtrackScreen
import com.example.ui.screens.ProfilScreen
import com.example.ui.screens.RefleksiScreen
import com.example.ui.screens.ShareInboxScreen
import com.example.ui.screens.UploadQueueScreen
import com.example.ui.screens.buildAppEmbedOrigin
import com.example.ui.screens.buildYouTubeIFrameHtml
import com.example.ui.screens.buildYouTubeMobileWatchBridgeJs
import com.example.ui.screens.computeRangeSelection
import com.example.ui.screens.filterAndSortMemories
import com.example.ui.screens.formatGallerySectionHeader
import com.example.ui.screens.isYouTubeEmbedFallbackErrorCode
import com.example.ui.screens.normalizeSocialUrl
import com.example.ui.screens.resolveYouTubeInAppFallbackUrl
import com.example.ui.theme.EvoriaTheme
import com.example.util.DisplayModeCandidate
import com.example.util.MediaStorageHelper
import com.example.util.QrCodeGenerator
import com.example.util.selectLocked60HzDisplayModeId
import com.example.util.shouldMutateWindowAttributesFor60Fps
import java.io.File
import java.time.ZoneId
import org.junit.Rule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EvoriaLogicTest {

  @get:Rule val composeRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: EvoriaDatabase
  private lateinit var repo: EvoriaSanctuaryRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, EvoriaDatabase::class.java).allowMainThreadQueries().build()
    repo = EvoriaSanctuaryRepository(context, db.evoriaDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun pinSecurity_pbkdf2VerifyAndCooldownThrottling_worksCorrectly() = runBlocking {
    val prefs = SecurityPreferences(context)
    prefs.clearPinAndDisableLock()

    prefs.configurePin("240899")
    val settings = prefs.securitySettingsFlow.first()
    assertTrue(settings.appLockEnabled)
    assertTrue(settings.hasPinConfigured)

    // Wrong attempts 1..4 decrement remainingBeforeCooldown
    for (attempt in 1..4) {
      val res = prefs.verifyPin("111111", nowMillis = 10_000L)
      assertTrue("Expected Invalid on attempt $attempt, got $res", res is PinVerifyResult.Invalid)
      assertEquals(5 - attempt, (res as PinVerifyResult.Invalid).remainingBeforeCooldown)
    }

    // 5th wrong attempt triggers 30s cooldown
    val fifth = prefs.verifyPin("111111", nowMillis = 10_000L)
    assertTrue(fifth is PinVerifyResult.Cooldown)
    assertEquals(30L, (fifth as PinVerifyResult.Cooldown).remainingSeconds)

    // Even valid PIN is blocked while inside cooldown window
    val blocked = prefs.verifyPin("240899", nowMillis = 20_000L)
    assertTrue(blocked is PinVerifyResult.Cooldown)

    // After cooldown expires, valid PIN succeeds
    val unlocked = prefs.verifyPin("240899", nowMillis = 45_000L)
    assertEquals(PinVerifyResult.Success, unlocked)

    prefs.clearPinAndDisableLock()
  }

  @Test
  fun timeCapsule_readEnforced_blocksBodyBeforeUnlockDateAndAllowsAfter() = runBlocking {
    val unlockAt = 2_000_000L
    val capsule =
      TimeCapsuleEntity(
        id = "cap_test_1",
        ownerUid = "uid_1",
        title = "Untuk Tahun Depan",
        body = "Pesan rahasia penuh kasih sayang.",
        attachmentUri = "file:///tmp/photo.jpg",
        unlockAtMillis = unlockAt,
        visibility = "private",
        isSealed = true,
        createdAtMillis = 1_000_000L,
      )
    repo.saveTimeCapsule(capsule)

    val beforeUnlock = repo.readTimeCapsuleEnforced("cap_test_1", nowMillis = 1_500_000L)
    assertNotNull(beforeUnlock)
    assertEquals("", beforeUnlock?.body)
    assertNull(beforeUnlock?.attachmentUri)

    val afterUnlock = repo.readTimeCapsuleEnforced("cap_test_1", nowMillis = 2_500_000L)
    assertNotNull(afterUnlock)
    assertEquals("Pesan rahasia penuh kasih sayang.", afterUnlock?.body)
    assertEquals("file:///tmp/photo.jpg", afterUnlock?.attachmentUri)
  }

  @Test
  fun searchEngine_localMetadataFallback_findsRelevantMemoriesWhenConsentFalse() = runBlocking {
    val engine = EvoriaSearchEngine()
    val candidates =
      listOf(
        LocalMemoryEntity(
          id = "m1",
          ownerUid = "u1",
          title = "Senja di Pantai Parangtritis",
          caption = "Angin sore berdua",
          story = "Kita duduk menikmati sunset yang hangat.",
          locationName = "Yogyakarta",
          tagsCsv = "pantai, sunset, senja",
          memoryDateMillis = 1000L,
          createdAtMillis = 1000L,
          updatedAtMillis = 1000L,
        ),
        LocalMemoryEntity(
          id = "m2",
          ownerUid = "u1",
          title = "Kopi Pagi di Teras",
          caption = "Hujan rintik",
          story = "Secangkir kopi tubruk.",
          locationName = "Bandung",
          tagsCsv = "kopi, hujan",
          memoryDateMillis = 2000L,
          createdAtMillis = 2000L,
          updatedAtMillis = 2000L,
        ),
      )

    val outcome = engine.searchMemories("foto kita waktu sunset di pantai", candidates, allowCloudAi = false)
    assertFalse(outcome.usedCloudAi)
    assertEquals(listOf("m1"), outcome.matchingMemoryIds)
    assertNotNull(outcome.isFallbackNotice)
  }

  @Test
  fun liquidGlass_computeSnapTabIndex_mapsPositionsAccurately() {
    val slot = 100f
    assertEquals(0, computeSnapTabIndex(-40f, slot, 5))
    assertEquals(0, computeSnapTabIndex(10f, slot, 5))
    assertEquals(2, computeSnapTabIndex(190f, slot, 5))
    assertEquals(4, computeSnapTabIndex(395f, slot, 5))
    assertEquals(4, computeSnapTabIndex(900f, slot, 5))

    // Momentum-aware projection carries quick flicks into the adjacent tab
    assertEquals(1, computeProjectedSnapTabIndex(30f, velocityPxPerSec = 900f, slotWidthPx = 100f, tabCount = 5))
    assertEquals(0, computeProjectedSnapTabIndex(30f, velocityPxPerSec = 0f, slotWidthPx = 100f, tabCount = 5))
    assertEquals(3, computeProjectedSnapTabIndex(370f, velocityPxPerSec = -900f, slotWidthPx = 100f, tabCount = 5))

    // Rubber-band edge resistance yields bounded soft overscroll
    val leftOver = computeTouchTargetLeftPx(-80f, slotWidthPx = 100f, tabCount = 5)
    assertTrue(leftOver < 0f && leftOver >= -14f)
    val rightOver = computeTouchTargetLeftPx(480f, slotWidthPx = 100f, tabCount = 5)
    assertTrue(rightOver > 400f && rightOver <= 414f)
  }

  @Test
  fun liquidGlass_kinematics_respectsIdlePressedDraggedAndReducedMotion() {
    val idle = computeLiquidGlassKinematics(0f, LiquidGlassInteractionState.IDLE, reducedMotion = false)
    assertEquals(1f, idle.stretchFactor, 0.001f)
    assertEquals(0f, idle.chromaticDispersionDp, 0.001f)

    val pressed = computeLiquidGlassKinematics(0f, LiquidGlassInteractionState.PRESSED, reducedMotion = false)
    assertEquals(1.03f, pressed.stretchFactor, 0.001f)
    assertEquals(0.75f, pressed.chromaticDispersionDp, 0.001f)

    val slowDrag = computeLiquidGlassKinematics(100f, LiquidGlassInteractionState.DRAGGED, reducedMotion = false)
    val fastDrag = computeLiquidGlassKinematics(2500f, LiquidGlassInteractionState.DRAGGED, reducedMotion = false)
    assertTrue(fastDrag.stretchFactor > slowDrag.stretchFactor)
    assertTrue(slowDrag.chromaticDispersionDp in 1.1f..3.25f)
    assertTrue(fastDrag.chromaticDispersionDp in 1.1f..3.25f)
    assertTrue(fastDrag.chromaticDispersionDp > slowDrag.chromaticDispersionDp)
    assertTrue(fastDrag.directionalBiasRatio > 0f)

    val reduced = computeLiquidGlassKinematics(2500f, LiquidGlassInteractionState.DRAGGED, reducedMotion = true)
    assertEquals(1f, reduced.stretchFactor, 0.001f)
    assertEquals(0f, reduced.chromaticDispersionDp, 0.001f)
    assertEquals(0f, reduced.directionalBiasRatio, 0.001f)
  }

  @Test
  fun quickOpenShowcase_persistsInSecurityPreferences() = runBlocking {
    val prefs = SecurityPreferences(context)
    prefs.setQuickOpenShowcase(true)
    assertTrue(prefs.userPrefsFlow.first().quickOpenShowcaseOnLaunch)
    prefs.setQuickOpenShowcase(false)
    assertFalse(prefs.userPrefsFlow.first().quickOpenShowcaseOnLaunch)
  }

  @Test
  fun spotifyImporter_parsesLinksUrisAndEmbedJsonAccurately() = runBlocking {
    assertEquals(
      "37i9dQZF1DXcBWIGoYBM5M",
      SpotifyPlaylistImporter.parseSpotifyPlaylistId(
        "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc123"
      ),
    )
    assertEquals(
      "37i9dQZF1DXcBWIGoYBM5M",
      SpotifyPlaylistImporter.parseSpotifyPlaylistId(
        "https://open.spotify.com/intl-id/playlist/37i9dQZF1DXcBWIGoYBM5M"
      ),
    )
    assertEquals(
      "37i9dQZF1DXcBWIGoYBM5M",
      SpotifyPlaylistImporter.parseSpotifyPlaylistId("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"),
    )
    assertNull(SpotifyPlaylistImporter.parseSpotifyPlaylistId("https://example.com/not-spotify"))

    val sampleHtml =
      """
      <html><head></head><body>
      <script id="__NEXT_DATA__" type="application/json">
      {
        "props": {
          "pageProps": {
            "state": {
              "data": {
                "entity": {
                  "name": "Senja Berdua",
                  "subtitle": "Oleh Raffa",
                  "coverArt": { "sources": [{ "url": "https://i.scdn.co/image/cover123" }] },
                  "trackList": [
                    {
                      "title": "Lagu Pertama",
                      "subtitle": "Hindia",
                      "duration": 215000,
                      "uri": "spotify:track:4uLU6hMCjMI75M1A2tKUQC",
                      "audioPreview": { "url": "https://p.scdn.co/mp3-preview/sample1" }
                    },
                    {
                      "title": "Lagu Kedua",
                      "subtitle": "Tulus",
                      "duration": 198000,
                      "uri": "spotify:track:1234567890abcdef",
                      "audioPreview": null
                    }
                  ]
                }
              }
            }
          }
        }
      }
      </script>
      </body></html>
      """.trimIndent()

    val parsed = SpotifyPlaylistImporter.parseEmbedHtml(sampleHtml, "37i9dQZF1DXcBWIGoYBM5M")
    assertNotNull(parsed)
    assertEquals("Senja Berdua", parsed!!.title)
    assertEquals("Oleh Raffa", parsed.subtitle)
    assertEquals("https://i.scdn.co/image/cover123", parsed.coverUrl)
    assertEquals(2, parsed.tracks.size)
    assertEquals("Lagu Pertama", parsed.tracks[0].title)
    assertEquals("Hindia", parsed.tracks[0].artist)
    assertEquals(215_000L, parsed.tracks[0].durationMs)
    assertEquals("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", parsed.tracks[0].externalUrl)

    // Verify Room persists Spotify metadata & YouTube videoId cleanly
    val pl =
      PlaylistEntity(
        id = "pl_sp_test",
        title = parsed.title,
        subtitle = parsed.subtitle,
        createdAtMillis = 1000L,
        sourceType = "spotify",
        externalUrl = parsed.externalUrl,
        coverUrl = parsed.coverUrl,
      )
    repo.savePlaylist(pl)
    repo.saveTrack(
      TrackEntity(
        id = "trk_sp_test_1",
        playlistId = "pl_sp_test",
        title = parsed.tracks[0].title,
        artist = parsed.tracks[0].artist,
        mediaUri = "",
        durationMs = parsed.tracks[0].durationMs,
        sortOrder = 0,
        sourceType = "spotify",
        externalUrl = parsed.tracks[0].externalUrl,
        youtubeVideoId = "dQw4w9WgXcQ",
      )
    )
    assertEquals("spotify", repo.observePlaylists().first().first().sourceType)
    assertEquals("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", repo.observeTracks().first().first().externalUrl)
    assertEquals("dQw4w9WgXcQ", repo.observeTracks().first().first().youtubeVideoId)
  }

  @Test
  fun liquidGlassNavbar_switchesToAndFromProfilWithoutStaleState() {
    var selectedTab by mutableIntStateOf(0)
    composeRule.setContent {
      EvoriaTheme {
        LiquidGlassNavbar(
          selectedTabIndex = selectedTab,
          onTabSelected = { selectedTab = it },
          applyNavigationBarInsets = false,
        )
      }
    }

    composeRule.onNodeWithTag("nav_tab_profil").performClick()
    composeRule.waitForIdle()
    assertEquals(4, selectedTab)

    composeRule.onNodeWithTag("nav_tab_beranda").performClick()
    composeRule.waitForIdle()
    assertEquals(0, selectedTab)

    composeRule.onNodeWithTag("nav_tab_kenangan").performClick()
    composeRule.waitForIdle()
    assertEquals(1, selectedTab)

    composeRule.onNodeWithTag("nav_tab_profil").performClick()
    composeRule.waitForIdle()
    assertEquals(4, selectedTab)

    composeRule.onNodeWithTag("nav_tab_refleksi").performClick()
    composeRule.waitForIdle()
    assertEquals(3, selectedTab)
  }

  @Test
  fun liquidGlassNavbar_decouplesPageIndexDuringDragAndCommitsOnRelease() {
    var selectedTab by mutableIntStateOf(0)
    var committedCount = 0
    composeRule.setContent {
      EvoriaTheme {
        LiquidGlassNavbar(
          selectedTabIndex = selectedTab,
          onTabSelected = {
            selectedTab = it
            committedCount++
          },
          applyNavigationBarInsets = false,
        )
      }
    }

    composeRule.onNodeWithTag("nav_tab_beranda").performTouchInput {
      down(center)
      // Drag across multiple tabs without lifting finger
      moveBy(Offset(260f, 0f))
    }
    composeRule.waitForIdle()
    // Page index MUST remain untouched while dragging
    assertEquals(0, selectedTab)
    assertEquals(0, committedCount)

    composeRule.onNodeWithTag("nav_tab_profil").performTouchInput {
      up()
    }
    composeRule.waitForIdle()
    // After release, page commits once to the target tab
    assertTrue(selectedTab in 1..4)
    assertEquals(1, committedCount)
  }

  @Test
  fun themeAndNavbarCustomization_persistsInSecurityPreferences() = runBlocking {
    val prefs = SecurityPreferences(context)
    prefs.setThemeMode(ThemeMode.DARK)
    prefs.setAccentPreset(EvoriaAccentPreset.ROSEWOOD)
    prefs.setNavbarThemeStyle(NavbarThemeStyle.VELVET_NOIR)
    prefs.setNavbarGlassIntensity(NavbarGlassIntensity.VIVID)

    val loaded = prefs.userPrefsFlow.first()
    assertEquals(ThemeMode.DARK, loaded.themeMode)
    assertEquals(EvoriaAccentPreset.ROSEWOOD, loaded.accentPreset)
    assertEquals(NavbarThemeStyle.VELVET_NOIR, loaded.navbarThemeStyle)
    assertEquals(NavbarGlassIntensity.VIVID, loaded.navbarGlassIntensity)
  }

  @Test
  fun kenanganFilterAndSort_handlesAllModesAndAlbumFilter() {
    val memories =
      listOf(
        LocalMemoryEntity(
          id = "m_photo",
          ownerUid = "u1",
          albumId = "alb_1",
          title = "Foto Pantai",
          mediaType = "photo",
          mediaUri = "file:///tmp/p.jpg",
          visibility = "private",
          isFavorite = true,
          memoryDateMillis = 100L,
          createdAtMillis = 100L,
          updatedAtMillis = 100L,
        ),
        LocalMemoryEntity(
          id = "m_video",
          ownerUid = "u1",
          albumId = "alb_1",
          title = "Video Senja",
          mediaType = "video",
          mediaUri = "file:///tmp/v.mp4",
          visibility = "shared",
          isFavorite = false,
          memoryDateMillis = 300L,
          createdAtMillis = 300L,
          updatedAtMillis = 300L,
        ),
        LocalMemoryEntity(
          id = "m_story",
          ownerUid = "u1",
          albumId = "alb_2",
          title = "Kisah Malam",
          mediaType = "story",
          mediaUri = null,
          visibility = "SHARED",
          isFavorite = true,
          memoryDateMillis = 200L,
          createdAtMillis = 200L,
          updatedAtMillis = 200L,
        ),
      )

    assertEquals(listOf("m_video", "m_story", "m_photo"), filterAndSortMemories(memories, "all", true).map { it.id })
    assertEquals(listOf("m_photo", "m_story", "m_video"), filterAndSortMemories(memories, "all", false).map { it.id })
    assertEquals(listOf("m_photo"), filterAndSortMemories(memories, "private", true).map { it.id })
    assertEquals(listOf("m_video", "m_story"), filterAndSortMemories(memories, "shared", true).map { it.id })
    assertEquals(listOf("m_story", "m_photo"), filterAndSortMemories(memories, "favorite", true).map { it.id })
    assertEquals(listOf("m_photo"), filterAndSortMemories(memories, "photo", true).map { it.id })
    assertEquals(listOf("m_video"), filterAndSortMemories(memories, "video", true).map { it.id })
    assertEquals(listOf("m_story"), filterAndSortMemories(memories, "story", true).map { it.id })
    assertEquals(listOf("m_video", "m_photo"), filterAndSortMemories(memories, "all", true, albumFilterId = "alb_1").map { it.id })
  }

  @Test
  fun spotifyImporter_parsesTrackCoverUrlAndNativeUri() = runBlocking {
    assertEquals(
      "spotify:track:4uLU6hMCjMI75M1A2tKUQC",
      SpotifyPlaylistImporter.toNativeSpotifyUri("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=1"),
    )
    assertEquals(
      "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
      SpotifyPlaylistImporter.toNativeSpotifyUri("https://open.spotify.com/intl-id/playlist/37i9dQZF1DXcBWIGoYBM5M"),
    )

    val htmlWithTrackArt =
      """
      <html><body>
      <script id="__NEXT_DATA__" type="application/json">
      {
        "props": {
          "pageProps": {
            "state": {
              "data": {
                "entity": {
                  "name": "Malam Berdua",
                  "subtitle": "Oleh Kita",
                  "coverArt": { "sources": [{ "url": "https://i.scdn.co/image/playlistart" }] },
                  "trackList": [
                    {
                      "title": "Judul Sangat Panjang Untuk Diuji Pada Teks Berjalan",
                      "subtitle": "Afgan",
                      "duration": 225000,
                      "uri": "spotify:track:7ouMYWpwJ422jRcDASZB7P",
                      "coverArt": { "sources": [{ "url": "https://i.scdn.co/image/trackcover999" }] },
                      "audioPreview": { "url": "https://p.scdn.co/mp3-preview/sample999" }
                    }
                  ]
                }
              }
            }
          }
        }
      }
      </script>
      </body></html>
      """.trimIndent()

    val parsed = SpotifyPlaylistImporter.parseEmbedHtml(htmlWithTrackArt, "37i9dQZF1DXcBWIGoYBM5M")
    assertNotNull(parsed)
    assertEquals("https://i.scdn.co/image/trackcover999", parsed!!.tracks.first().coverUrl)

    repo.saveTrack(
      TrackEntity(
        id = "trk_cover_test",
        playlistId = "pl_1",
        title = parsed.tracks.first().title,
        artist = parsed.tracks.first().artist,
        mediaUri = "",
        coverUrl = parsed.tracks.first().coverUrl,
        durationMs = parsed.tracks.first().durationMs,
        sortOrder = 0,
        sourceType = "spotify",
        externalUrl = parsed.tracks.first().externalUrl,
      )
    )
    assertEquals("https://i.scdn.co/image/trackcover999", repo.observeTracks().first().first().coverUrl)
  }

  @Test
  fun kenanganScreen_speedDialFabExposesThreeMainActions() {
    var openedAddMemory = false
    composeRule.setContent {
      EvoriaTheme {
        KenanganScreen(
          albums = listOf(LocalAlbumEntity("alb_1", "u1", "Liburan", "", null, "private", null, 1L, 1L)),
          memories = emptyList(),
          onOpenAlbum = {},
          onOpenMemory = {},
          onOpenAddMemory = { openedAddMemory = true },
          onImportGalleryPhotos = {},
          onSaveAlbum = { _, _, _, _, _, _, _ -> },
          onToggleFavorite = {},
          onDeleteMemories = {},
        )
      }
    }

    composeRule.onNodeWithTag("kenangan_fab_add").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("fab_action_create_memory").assertExists()
    composeRule.onNodeWithTag("fab_action_import_gallery").assertExists()
    composeRule.onNodeWithTag("fab_action_create_album").assertExists()

    composeRule.onNodeWithTag("fab_action_create_memory").performClick()
    composeRule.waitForIdle()
    assertTrue(openedAddMemory)
  }

  @Test
  fun profilScreen_deleteAllDataRequiresConfirmationDialog() {
    var deletedAll = false
    composeRule.setContent {
      EvoriaTheme {
        ProfilScreen(
          currentUid = "u1",
          profile = null,
          fallbackName = "Raffa",
          couple = null,
          albumCount = 0,
          memoryCount = 0,
          journalCount = 0,
          capsuleCount = 0,
          pendingShareRequestsCount = 0,
          unreadNotificationCount = 0,
          localStorageLabel = "0 KB",
          userPrefs = com.example.data.local.AppUserPrefs(),
          securitySettings = SecuritySettings(),
          onSetThemeMode = {},
          onSetReducedMotion = {},
          onDeleteAllLocalData = { deletedAll = true },
          onOpenConnectPartner = {},
          onOpenShareInbox = {},
          onOpenNotifications = {},
          onOpenMusic = {},
          onOpenUploadQueue = {},
          onOpenSecurity = {},
          onOpenBackupStorage = {},
          onOpenLiquidGlassShowcase = {},
          onSignOut = {},
        )
      }
    }

    // Scroll LazyColumn to bottom action item before clicking
    composeRule.onNodeWithTag("profil_lazy_column").performScrollToIndex(4)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("profil_delete_all_data_button").assertExists().performClick()
    composeRule.waitForIdle()
    assertFalse(deletedAll)

    composeRule.onNodeWithTag("profil_confirm_delete_all_button").assertExists().performClick()
    composeRule.waitForIdle()
    assertTrue(deletedAll)
  }

  @Test
  fun deletePlaylist_removesPlaylistAndTracksWithoutAffectingOtherPlaylists() = runBlocking {
    val pl1 = PlaylistEntity("pl_del_1", "Untuk Dihapus", "Lama", null, 100L)
    val pl2 = PlaylistEntity("pl_keep_2", "Tetap Disimpan", "Favorit", null, 200L)
    repo.savePlaylist(pl1)
    repo.savePlaylist(pl2)
    repo.saveTrack(TrackEntity("t_del_a", "pl_del_1", "Lagu A", "Artis", "", sortOrder = 0))
    repo.saveTrack(TrackEntity("t_del_b", "pl_del_1", "Lagu B", "Artis", "", sortOrder = 1))
    repo.saveTrack(TrackEntity("t_keep_c", "pl_keep_2", "Lagu C", "Artis", "", sortOrder = 0))

    repo.deletePlaylist("pl_del_1")

    val remainingPlaylists = repo.observePlaylists().first()
    val remainingTracks = repo.observeTracks().first()
    assertEquals(listOf("pl_keep_2"), remainingPlaylists.map { it.id })
    assertEquals(listOf("t_keep_c"), remainingTracks.map { it.id })
  }

  @Test
  fun musicScreen_deletePlaylistRequiresConfirmationAndInvokesCallback() {
    var deletedPlaylistId: String? = null
    val pl = PlaylistEntity("pl_ui_1", "Senja Spotify", "Kita", null, 100L, sourceType = "spotify")
    val trk = TrackEntity("trk_ui_1", "pl_ui_1", "Lagu Pertama", "Hindia", "", sortOrder = 0, sourceType = "spotify")

    composeRule.setContent {
      EvoriaTheme {
        MusicAndSoundtrackScreen(
          playlists = listOf(pl),
          tracks = listOf(trk),
          albums = emptyList(),
          playerState = MusicPlayerState(),
          onCreatePlaylist = { _, _, _ -> },
          onDeletePlaylist = { deletedPlaylistId = it },
          onAddAudioTrack = { _, _, _, _ -> },
          onMoveTrackOrder = { _, _, _ -> },
          onDeleteTrack = {},
          onPlayTrack = { _, _ -> },
          onOpenFullPlayer = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("delete_playlist_button").assertExists().performClick()
    composeRule.waitForIdle()
    assertNull(deletedPlaylistId)

    composeRule.onNodeWithTag("confirm_delete_playlist_button").assertExists().performClick()
    composeRule.waitForIdle()
    assertEquals("pl_ui_1", deletedPlaylistId)
  }

  @Test
  fun matchLocalAudioToTracks_pairsSpotifyTracksAndSkipsAlreadyLinkedLocalFiles() {
    val tracks =
      listOf(
        TrackEntity(
          id = "t1",
          playlistId = "pl_sp",
          title = "Terima Kasih Cinta",
          artist = "Afgan",
          mediaUri = "https://p.scdn.co/mp3-preview/111",
          sortOrder = 0,
          sourceType = "spotify",
        ),
        TrackEntity(
          id = "t2",
          playlistId = "pl_sp",
          title = "Evaluasi",
          artist = "Hindia",
          mediaUri = "",
          sortOrder = 1,
          sourceType = "spotify",
        ),
        TrackEntity(
          id = "t3",
          playlistId = "pl_sp",
          title = "Monokrom",
          artist = "Tulus",
          mediaUri = "file:///data/user/0/com.example/files/media/already_linked.mp3",
          sortOrder = 2,
          sourceType = "spotify",
        ),
      )

    val uri1 = Uri.parse("content://com.android.providers/audio/1")
    val uri2 = Uri.parse("content://com.android.providers/audio/2")
    val uri3 = Uri.parse("content://com.android.providers/audio/3")

    val matches =
      matchLocalAudioToTracks(
        tracks = tracks,
        audioCandidates =
          listOf(
            "Afgan - Terima Kasih Cinta (Official Audio).mp3" to uri1,
            "hindia_evaluasi.m4a" to uri2,
            "Tulus - Monokrom.flac" to uri3,
          ),
      )

    assertEquals(2, matches.size)
    assertEquals("t1" to uri1, matches[0].first.id to matches[0].second)
    assertEquals("t2" to uri2, matches[1].first.id to matches[1].second)
  }

  @Test
  fun youtubeDataApiClient_parsesDurationRankAndPrefersFullOfficialAudio() {
    assertEquals(216_000L, YouTubeDataApiClient.parseIso8601DurationMs("PT3M36S"))
    assertEquals(30_000L, YouTubeDataApiClient.parseIso8601DurationMs("PT30S"))
    assertEquals(3_725_000L, YouTubeDataApiClient.parseIso8601DurationMs("PT1H2M5S"))
    assertEquals(
      "Evaluasi Hindia official audio",
      YouTubeDataApiClient.buildSearchQuery("Evaluasi (feat. Someone)", "Hindia, .Feast"),
    )
    assertFalse(YouTubeDataApiClient.isApiKeyConfigured("YOUTUBE_API_KEY_DEFAULT_VALUE"))
    assertTrue(YouTubeDataApiClient.isApiKeyConfigured("AIzaSyFakeConfiguredKeyForUnitTest"))

    val searchJson =
      """
      {
        "items": [
          {
            "id": { "videoId": "short_preview_30s" },
            "snippet": {
              "title": "Evaluasi Preview 30s Teaser",
              "channelTitle": "Random Clips"
            }
          },
          {
            "id": { "videoId": "full_official_audio" },
            "snippet": {
              "title": "Hindia - Evaluasi (Official Audio)",
              "channelTitle": "Hindia - Topic"
            }
          },
          {
            "id": { "videoId": "acoustic_cover" },
            "snippet": {
              "title": "Evaluasi Hindia Karaoke Cover",
              "channelTitle": "Karaoke ID"
            }
          }
        ]
      }
      """.trimIndent()

    val detailsJson =
      """
      {
        "items": [
          { "id": "short_preview_30s", "contentDetails": { "duration": "PT30S" }, "status": { "embeddable": true } },
          { "id": "full_official_audio", "contentDetails": { "duration": "PT3M26S" }, "status": { "embeddable": true } },
          { "id": "acoustic_cover", "contentDetails": { "duration": "PT3M30S" }, "status": { "embeddable": true } }
        ]
      }
      """.trimIndent()

    val candidates = YouTubeDataApiClient.parseSearchCandidates(searchJson)
    val details = YouTubeDataApiClient.parseVideoDetails(detailsJson)
    val selected =
      YouTubeDataApiClient.selectBestCandidate(
        candidates = candidates,
        details = details,
        targetTitle = "Evaluasi",
        targetArtist = "Hindia",
        targetDurationMs = 205_000L,
      )
    assertNotNull(selected)
    assertEquals("full_official_audio", selected!!.videoId)
    assertEquals(206_000L, selected.durationMs)
    assertTrue(selected.embeddable)
  }

  @Test
  fun playerManager_switchesToYouTubeFullModeForMatchedSpotifyTracks() {
    val manager = EvoriaPlayerManager(context)
    val ytTrack =
      TrackEntity(
        id = "trk_yt_1",
        playlistId = "pl_sp",
        title = "Evaluasi",
        artist = "Hindia",
        mediaUri = "",
        durationMs = 206_000L,
        sourceType = "spotify",
        youtubeVideoId = "full_official_audio",
      )
    manager.setQueueAndPlay(listOf(ytTrack), "trk_yt_1")
    val state = manager.state.value
    assertTrue(state.isYouTubeMode)
    assertEquals("full_official_audio", state.activeYouTubeVideoId)
    assertTrue(state.isPlaying)
    assertNull(state.errorMessage)
    manager.release()
  }

  @Test
  fun musicScreen_youtubeMatchedTrackPlaysInFullPlayerAndUnmatchedTriggersSearch() {
    val playedIds = mutableListOf<String>()
    val matchedCalls = mutableListOf<String>()
    var openedFullPlayer = false
    val pl = PlaylistEntity("pl_sp_yt", "Senja Spotify", "Kita", null, 100L, sourceType = "spotify")
    val matchedTrack =
      TrackEntity(
        id = "trk_yt_ok",
        playlistId = "pl_sp_yt",
        title = "Evaluasi",
        artist = "Hindia",
        mediaUri = "",
        durationMs = 206_000L,
        sortOrder = 0,
        sourceType = "spotify",
        youtubeVideoId = "ytVid123456",
      )
    val unmatchedTrack =
      TrackEntity(
        id = "trk_yt_wait",
        playlistId = "pl_sp_yt",
        title = "Monokrom",
        artist = "Tulus",
        mediaUri = "",
        durationMs = 214_000L,
        sortOrder = 1,
        sourceType = "spotify",
        youtubeVideoId = null,
      )

    composeRule.setContent {
      EvoriaTheme {
        MusicAndSoundtrackScreen(
          playlists = listOf(pl),
          tracks = listOf(matchedTrack, unmatchedTrack),
          albums = emptyList(),
          playerState = MusicPlayerState(),
          isYouTubeApiConfigured = true,
          onCreatePlaylist = { _, _, _ -> },
          onMatchTrackWithYouTube = { matchedCalls.add(it.id) },
          onAddAudioTrack = { _, _, _, _ -> },
          onMoveTrackOrder = { _, _, _ -> },
          onDeleteTrack = {},
          onPlayTrack = { _, trackId -> playedIds.add(trackId) },
          onOpenFullPlayer = { openedFullPlayer = true },
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("track_row_trk_yt_ok").assertExists().performClick()
    composeRule.waitForIdle()
    assertEquals(listOf("trk_yt_ok"), playedIds)
    assertTrue(openedFullPlayer)

    composeRule.onNodeWithTag("music_lazy_column").performScrollToIndex(3)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("youtube_match_track_btn_trk_yt_wait").assertExists().performClick()
    composeRule.waitForIdle()
    assertEquals(listOf("trk_yt_wait"), matchedCalls)
  }

  @Test
  fun jelajahScreen_pillSearchBarSubmitsQueryAndFilters() {
    var submittedQuery: String? = null
    composeRule.setContent {
      EvoriaTheme {
        JelajahScreen(
          memories = emptyList(),
          albums = emptyList(),
          searchHistory = emptyList(),
          aiConsentGranted = false,
          searchState = SearchUiState.Idle,
          onToggleAiConsent = {},
          onRunSearch = { q, _, _, _ -> submittedQuery = q },
          onClearHistory = {},
          onOpenMemory = {},
        )
      }
    }

    composeRule.onNodeWithTag("jelajah_search_input").performTextInput("sunset pantai")
    composeRule.onNodeWithTag("jelajah_search_submit").performClick()
    composeRule.waitForIdle()
    assertEquals("sunset pantai", submittedQuery)
  }

  @Test
  fun filterAndSortMemories_filtersBySearchQueryCaseInsensitively() {
    val memories =
      listOf(
        LocalMemoryEntity(
          id = "m1",
          ownerUid = "u1",
          title = "Senja di Parangtritis",
          caption = "Angin laut hangat",
          mediaType = "photo",
          visibility = "shared",
          memoryDateMillis = 100L,
          createdAtMillis = 100L,
          updatedAtMillis = 100L,
        ),
        LocalMemoryEntity(
          id = "m2",
          ownerUid = "u1",
          title = "Kopi Pagi Bandung",
          caption = "Gerimis tenang",
          mediaType = "photo",
          visibility = "private",
          memoryDateMillis = 200L,
          createdAtMillis = 200L,
          updatedAtMillis = 200L,
        ),
      )

    assertEquals(listOf("m1"), filterAndSortMemories(memories, "all", true, searchQuery = "parangtritis").map { it.id })
    assertEquals(listOf("m2"), filterAndSortMemories(memories, "all", true, searchQuery = "GERIMIS").map { it.id })
  }

  @Test
  fun normalizeSocialUrl_convertsHandlesAndKeepsFullUrls() {
    assertEquals("https://instagram.com/raffa.dev", normalizeSocialUrl("instagram", "@raffa.dev"))
    assertEquals("https://www.tiktok.com/@raffa_id", normalizeSocialUrl("tiktok", "raffa_id"))
    assertEquals("https://github.com/raffamusyawal", normalizeSocialUrl("github", "raffamusyawal"))
    assertEquals("mailto:raffa@example.com", normalizeSocialUrl("email", "raffa@example.com"))
    assertEquals("https://custom.site/me", normalizeSocialUrl("website", "https://custom.site/me"))
    assertEquals("", normalizeSocialUrl("instagram", "   "))
  }

  @Test
  fun aboutEvoriaScreen_showsDeveloperNameVersionAndAllowsSocialLinkUpdate() {
    var savedInstagram: String? = null
    composeRule.setContent {
      EvoriaTheme {
        AboutEvoriaScreen(
          userPrefs = AppUserPrefs(devInstagram = "@raffamusyawal"),
          currentUserEmail = "musyawalraffa@gmail.com",
          onSaveDeveloperSocials = { ig, _, _, _, _ -> savedInstagram = ig },
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("about_developer_name").assertTextContains("RAFFA AULIA MUSYAWAL")
    composeRule.onNodeWithTag("about_version_text").assertTextContains("Versi ${BuildConfig.VERSION_NAME}", substring = true)

    composeRule.onNodeWithTag("about_lazy_column").performScrollToIndex(2)
    composeRule.waitForIdle()
    composeRule.onNodeWithText("@raffamusyawal").assertExists()

    composeRule.onNodeWithTag("about_edit_socials_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("about_social_input_instagram").performTextReplacement("@raffamusyawal_official")
    composeRule.onNodeWithTag("about_social_save_button").performClick()
    composeRule.waitForIdle()
    assertEquals("@raffamusyawal_official", savedInstagram)
  }

  @Test
  fun aboutEvoriaScreen_blocksSocialEditForNonDeveloperAccounts() {
    assertTrue(canEditDeveloperSocials("musyawalraffa@gmail.com"))
    assertTrue(canEditDeveloperSocials("  MUSYAWALRAFFA@gmail.com "))
    assertFalse(canEditDeveloperSocials("other@gmail.com"))
    assertFalse(canEditDeveloperSocials("raffasila2210@gmail.com"))
    assertFalse(canEditDeveloperSocials(null))

    composeRule.setContent {
      EvoriaTheme {
        AboutEvoriaScreen(
          userPrefs = AppUserPrefs(devInstagram = "@raffamusyawal"),
          currentUserEmail = "other@gmail.com",
          onSaveDeveloperSocials = { _, _, _, _, _ -> },
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("about_lazy_column").performScrollToIndex(2)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("about_edit_socials_button").assertDoesNotExist()
    composeRule.onNodeWithTag("about_socials_locked_badge").assertExists()
  }

  @Test
  fun searchEngine_localSemanticSynonymsAndTimeIntent_matchWithoutExactWords() {
    val engine = EvoriaSearchEngine()
    val candidates =
      listOf(
        LocalMemoryEntity(
          id = "m_ferry",
          ownerUid = "u1",
          title = "Senja & Laut",
          caption = "Naik kapal ferry menyeberang",
          story = "Angin sepoi-sepoi di dek atas.",
          locationName = "Danau Toba",
          tagsCsv = "kapal, laut",
          mediaType = "photo",
          memoryDateMillis = 1000L,
          createdAtMillis = 1000L,
          updatedAtMillis = 1000L,
        ),
        LocalMemoryEntity(
          id = "m_coffee",
          ownerUid = "u1",
          title = "Espresso Sore",
          caption = "Nongkrong hangat",
          locationName = "Kafe Senja",
          tagsCsv = "kafe",
          mediaType = "photo",
          memoryDateMillis = 2000L,
          createdAtMillis = 2000L,
          updatedAtMillis = 2000L,
        ),
      )

    assertEquals(listOf("m_ferry"), engine.localMetadataSearch("pantai", candidates))
    assertEquals(listOf("m_coffee"), engine.localMetadataSearch("kopi", candidates))
  }

  @Test
  fun gallerySectionHeaderAndGridTiles_renderBorderlessGalleryWithoutCards() {
    val now = 1_700_000_000_000L
    val yesterday = now - 86_400_000L
    val older = now - 5L * 86_400_000L

    assertEquals("Hari ini", formatGallerySectionHeader(now, nowMillis = now))
    assertEquals("Kemarin", formatGallerySectionHeader(yesterday, nowMillis = now))
    assertEquals(formatIndoDate(older), formatGallerySectionHeader(older, nowMillis = now))

    val memories =
      listOf(
        LocalMemoryEntity(
          id = "m_gal_1",
          ownerUid = "u1",
          title = "Foto Dermaga",
          locationName = "Sei Rampah",
          mediaType = "photo",
          mediaUri = "file:///tmp/1.jpg",
          memoryDateMillis = now,
          createdAtMillis = now,
          updatedAtMillis = now,
        ),
        LocalMemoryEntity(
          id = "m_gal_2",
          ownerUid = "u1",
          title = "Video Ombak",
          locationName = "Sei Rampah",
          mediaType = "video",
          mediaUri = "file:///tmp/2.mp4",
          memoryDateMillis = now - 1000L,
          createdAtMillis = now - 1000L,
          updatedAtMillis = now - 1000L,
        ),
      )

    composeRule.setContent {
      EvoriaTheme {
        KenanganScreen(
          albums = emptyList(),
          memories = memories,
          onOpenAlbum = {},
          onOpenMemory = {},
          onOpenAddMemory = {},
          onImportGalleryPhotos = {},
          onSaveAlbum = { _, _, _, _, _, _, _ -> },
          onToggleFavorite = {},
          onDeleteMemories = {},
        )
      }
    }

    composeRule.onNodeWithTag("kenangan_subtab_galeri").assertExists().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("galeri_grid").assertExists()
    composeRule.onNodeWithTag("memory_card_m_gal_1").assertExists()
    composeRule.onNodeWithTag("memory_card_m_gal_2").assertExists()
  }

  @Test
  fun profilScreen_aboutMenuInvokesCallback() {
    var openedAbout = false
    composeRule.setContent {
      EvoriaTheme {
        ProfilScreen(
          currentUid = "u1",
          profile = null,
          fallbackName = "Raffa",
          couple = null,
          albumCount = 0,
          memoryCount = 0,
          journalCount = 0,
          capsuleCount = 0,
          pendingShareRequestsCount = 0,
          unreadNotificationCount = 0,
          localStorageLabel = "0 KB",
          userPrefs = AppUserPrefs(),
          securitySettings = SecuritySettings(),
          onSetThemeMode = {},
          onSetReducedMotion = {},
          onOpenConnectPartner = {},
          onOpenShareInbox = {},
          onOpenNotifications = {},
          onOpenMusic = {},
          onOpenUploadQueue = {},
          onOpenSecurity = {},
          onOpenBackupStorage = {},
          onOpenLiquidGlassShowcase = {},
          onOpenAbout = { openedAbout = true },
          onSignOut = {},
        )
      }
    }

    composeRule.onNodeWithTag("profil_lazy_column").performScrollToIndex(4)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("profil_about_button").assertExists().performScrollTo().performClick()
    composeRule.waitForIdle()
    assertTrue(openedAbout)
  }

  @Test
  fun frameRateController_selectsExact60HzModeAtMatchingResolution() {
    val modes =
      listOf(
        DisplayModeCandidate(modeId = 1, width = 1080, height = 2400, refreshRate = 120.0f),
        DisplayModeCandidate(modeId = 2, width = 1080, height = 2400, refreshRate = 90.0f),
        DisplayModeCandidate(modeId = 3, width = 1080, height = 2400, refreshRate = 60.0f),
        DisplayModeCandidate(modeId = 4, width = 1440, height = 3200, refreshRate = 60.0f),
      )
    assertEquals(3, selectLocked60HzDisplayModeId(modes, activeWidth = 1080, activeHeight = 2400))
    assertEquals(4, selectLocked60HzDisplayModeId(modes, activeWidth = 1440, activeHeight = 3200))

    val onlyHighHz = listOf(DisplayModeCandidate(modeId = 10, width = 1080, height = 2400, refreshRate = 120.0f))
    assertNull(selectLocked60HzDisplayModeId(onlyHighHz, activeWidth = 1080, activeHeight = 2400))
    assertNull(selectLocked60HzDisplayModeId(emptyList(), activeWidth = 1080, activeHeight = 2400))

    // Fixed-60Hz displays (and LTPO displays already resting in the 60Hz band) must not trigger
    // redundant WindowManager relayout / SurfaceSyncGroup transactions when no override is active
    assertFalse(
      shouldMutateWindowAttributesFor60Fps(
        activeRefreshRate = 60.0f,
        hasHighRefreshMode = false,
        currentPreferredRefreshRate = 0f,
        currentPreferredModeId = 0,
        lockTo60Fps = true,
      )
    )
    assertFalse(
      shouldMutateWindowAttributesFor60Fps(
        activeRefreshRate = 60.0f,
        hasHighRefreshMode = true,
        currentPreferredRefreshRate = 0f,
        currentPreferredModeId = 0,
        lockTo60Fps = true,
      )
    )
    // High-refresh-rate displays mutate once until 60f is applied, then avoid re-triggering
    assertTrue(
      shouldMutateWindowAttributesFor60Fps(
        activeRefreshRate = 120.0f,
        hasHighRefreshMode = true,
        currentPreferredRefreshRate = 0f,
        currentPreferredModeId = 0,
        lockTo60Fps = true,
      )
    )
    assertFalse(
      shouldMutateWindowAttributesFor60Fps(
        activeRefreshRate = 120.0f,
        hasHighRefreshMode = true,
        currentPreferredRefreshRate = 60.0f,
        currentPreferredModeId = 3,
        lockTo60Fps = true,
      )
    )
  }

  @Test
  fun lock60FpsPreference_defaultsFalseForAdaptiveSmoothnessAndPersistsToggles() = runBlocking {
    val prefs = SecurityPreferences(context)
    assertFalse(AppUserPrefs().lock60Fps)
    prefs.setLock60Fps(true)
    assertTrue(prefs.userPrefsFlow.first().lock60Fps)
    prefs.setLock60Fps(false)
    assertFalse(prefs.userPrefsFlow.first().lock60Fps)
  }

  @Test
  fun profilScreen_lock60FpsToggleInvokesCallback() {
    var toggledValue: Boolean? = null
    composeRule.setContent {
      EvoriaTheme {
        ProfilScreen(
          currentUid = "u1",
          profile = null,
          fallbackName = "Raffa",
          couple = null,
          albumCount = 0,
          memoryCount = 0,
          journalCount = 0,
          capsuleCount = 0,
          pendingShareRequestsCount = 0,
          unreadNotificationCount = 0,
          localStorageLabel = "0 KB",
          userPrefs = AppUserPrefs(lock60Fps = true),
          securitySettings = SecuritySettings(),
          onSetThemeMode = {},
          onSetReducedMotion = {},
          onSetLock60Fps = { toggledValue = it },
          onOpenConnectPartner = {},
          onOpenShareInbox = {},
          onOpenNotifications = {},
          onOpenMusic = {},
          onOpenUploadQueue = {},
          onOpenSecurity = {},
          onOpenBackupStorage = {},
          onOpenLiquidGlassShowcase = {},
          onSignOut = {},
        )
      }
    }

    composeRule.onNodeWithTag("profil_lazy_column").performScrollToIndex(2)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("profil_lock_60fps_switch").assertExists().performScrollTo().performClick()
    composeRule.waitForIdle()
    assertEquals(false, toggledValue)
  }

  @Test
  fun liquidGlass_bulgeJellyPinchAndReleaseReturnToNormal_workAccurately() {
    val idle = computeLiquidGlassKinematics(0f, LiquidGlassInteractionState.IDLE, reducedMotion = false)
    val pressed = computeLiquidGlassKinematics(0f, LiquidGlassInteractionState.PRESSED, reducedMotion = false)
    val fastDrag = computeLiquidGlassKinematics(1800f, LiquidGlassInteractionState.DRAGGED, reducedMotion = false)

    // Press & Drag trigger vertical capsule bulge, card pinch factor, and stronger under-glow
    assertEquals(0f, idle.bulgeFactor, 0.001f)
    assertEquals(0f, idle.waistPinchFactor, 0.001f)
    assertTrue(pressed.bulgeFactor >= 1f)
    assertTrue(pressed.verticalJellyScale > 1.2f)
    assertEquals(1f, pressed.waistPinchFactor, 0.001f)
    assertTrue(pressed.underGlowAlpha > idle.underGlowAlpha)

    // Volume-conserving jelly drag stretches X while slightly compressing Y relative to static hold
    assertTrue(fastDrag.stretchFactor > pressed.stretchFactor)
    assertTrue(fastDrag.verticalJellyScale < pressed.verticalJellyScale)
    assertTrue(fastDrag.verticalJellyScale > 1.1f)
    assertTrue(fastDrag.underGlowAlpha >= pressed.underGlowAlpha)

    // Navbar card waist pinch peaks near the capsule center and smoothly tapers away at pill ends
    val centerPinch =
      computeNavbarWaistPinchPx(
        xPx = 180f,
        capsuleCenterX = 180f,
        capsuleHalfWidthPx = 40f,
        maxPinchPx = 16f,
        pillLeftPx = 0f,
        pillRightPx = 360f,
        pillRadiusPx = 34f,
      )
    val farEdgePinch =
      computeNavbarWaistPinchPx(
        xPx = 310f,
        capsuleCenterX = 180f,
        capsuleHalfWidthPx = 40f,
        maxPinchPx = 16f,
        pillLeftPx = 0f,
        pillRightPx = 360f,
        pillRadiusPx = 34f,
      )
    val idlePinch =
      computeNavbarWaistPinchPx(
        xPx = 180f,
        capsuleCenterX = 180f,
        capsuleHalfWidthPx = 40f,
        maxPinchPx = 0f,
        pillLeftPx = 0f,
        pillRightPx = 360f,
        pillRadiusPx = 34f,
      )
    assertTrue(centerPinch > 12f)
    assertTrue(centerPinch > farEdgePinch * 3f)
    assertEquals(0f, idlePinch, 0.001f)
  }

  @Test
  fun liquidGlassNavbar_holdAndDragInflatePinchThenResetToNormalAndActivateTabOnRelease() {
    var selectedTab by mutableIntStateOf(0)
    var latestTelemetry = LiquidGlassTelemetry()
    composeRule.setContent {
      EvoriaTheme {
        LiquidGlassNavbar(
          selectedTabIndex = selectedTab,
          onTabSelected = { selectedTab = it },
          applyNavigationBarInsets = false,
          onTelemetryChanged = { latestTelemetry = it },
        )
      }
    }
    composeRule.waitForIdle()
    assertEquals(LiquidGlassInteractionState.IDLE, latestTelemetry.interactionState)
    assertEquals(0f, latestTelemetry.bulgeFactor, 0.01f)

    // Start dragging from Beranda toward right tabs
    composeRule.onNodeWithTag("nav_tab_beranda").performTouchInput {
      down(center)
      moveBy(Offset(210f, 0f))
    }
    composeRule.mainClock.advanceTimeBy(180L)
    composeRule.waitForIdle()

    assertEquals(LiquidGlassInteractionState.DRAGGED, latestTelemetry.interactionState)
    assertTrue(latestTelemetry.bulgeFactor > 0.5f)
    assertTrue(latestTelemetry.waistPinchFactor > 0.5f)
    assertTrue(latestTelemetry.chromaticDispersionDp > 0.5f)

    // Release finger: target tab becomes active and visual state springs back to normal IDLE
    composeRule.onNodeWithTag("nav_tab_profil").performTouchInput { up() }
    composeRule.mainClock.advanceTimeBy(800L)
    composeRule.waitForIdle()

    assertEquals(LiquidGlassInteractionState.IDLE, latestTelemetry.interactionState)
    assertTrue(selectedTab in 1..4)
    assertEquals(0f, latestTelemetry.bulgeFactor, 0.02f)
    assertEquals(0f, latestTelemetry.waistPinchFactor, 0.02f)
    assertEquals(0f, latestTelemetry.chromaticDispersionDp, 0.05f)
  }

  @Test
  fun mediaStorageHelper_buildsShareViaAllAppsChooserForSingleMultipleAndStory() {
    val photo1 =
      LocalMemoryEntity(
        id = "m_s_1",
        ownerUid = "u1",
        title = "Senja Pantai",
        caption = "Langit oranye indah",
        locationName = "Medan",
        mediaType = "photo",
        mediaUri = "content://media/external/images/1",
        memoryDateMillis = 1000L,
        createdAtMillis = 1000L,
        updatedAtMillis = 1000L,
      )
    val video1 =
      LocalMemoryEntity(
        id = "m_s_2",
        ownerUid = "u1",
        title = "Ombak Sore",
        mediaType = "video",
        mediaUri = "content://media/external/video/2",
        memoryDateMillis = 2000L,
        createdAtMillis = 2000L,
        updatedAtMillis = 2000L,
      )
    val story1 =
      LocalMemoryEntity(
        id = "m_s_3",
        ownerUid = "u1",
        title = "Catatan Kecil",
        story = "Hari yang hangat bersama.",
        mediaType = "story",
        mediaUri = null,
        memoryDateMillis = 3000L,
        createdAtMillis = 3000L,
        updatedAtMillis = 3000L,
      )

    assertEquals("image/*", MediaStorageHelper.resolveShareMimeType(listOf(photo1)))
    assertEquals("video/*", MediaStorageHelper.resolveShareMimeType(listOf(video1)))
    assertEquals("*/*", MediaStorageHelper.resolveShareMimeType(listOf(photo1, video1)))
    assertEquals("text/plain", MediaStorageHelper.resolveShareMimeType(listOf(story1)))

    val singleChooser = MediaStorageHelper.buildShareMemoriesChooserIntent(context, listOf(photo1))
    assertNotNull(singleChooser)
    assertEquals(Intent.ACTION_CHOOSER, singleChooser!!.action)
    @Suppress("DEPRECATION")
    val singleTarget = singleChooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
    assertNotNull(singleTarget)
    assertEquals(Intent.ACTION_SEND, singleTarget!!.action)
    assertEquals("image/*", singleTarget.type)
    assertTrue((singleTarget.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

    val multiChooser = MediaStorageHelper.buildShareMemoriesChooserIntent(context, listOf(photo1, video1))
    assertNotNull(multiChooser)
    @Suppress("DEPRECATION")
    val multiTarget = multiChooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
    assertNotNull(multiTarget)
    assertEquals(Intent.ACTION_SEND_MULTIPLE, multiTarget!!.action)
    assertEquals("*/*", multiTarget.type)
    assertTrue((multiTarget.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

    val storyChooser = MediaStorageHelper.buildShareMemoriesChooserIntent(context, listOf(story1))
    assertNotNull(storyChooser)
    @Suppress("DEPRECATION")
    val storyTarget = storyChooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
    assertNotNull(storyTarget)
    assertEquals(Intent.ACTION_SEND, storyTarget!!.action)
    assertEquals("text/plain", storyTarget.type)
    assertTrue(storyTarget.getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains("Catatan Kecil"))
  }

  @Test
  fun youtubeDataApiClient_parsesPublicSearchHtmlAndMmSsDurations() {
    assertEquals(225_000L, YouTubeDataApiClient.parseMmSsDurationMs("3:45"))
    assertEquals(3_723_000L, YouTubeDataApiClient.parseMmSsDurationMs("1:02:03"))
    assertEquals(0L, YouTubeDataApiClient.parseMmSsDurationMs("invalid"))

    val fakeHtml =
      """
      <html><head><script>var ytInitialData = {
        "contents": {
          "twoColumnSearchResultsRenderer": {
            "primaryContents": {
              "sectionListRenderer": {
                "contents": [
                  {
                    "itemSectionRenderer": {
                      "contents": [
                        {
                          "videoRenderer": {
                            "videoId": "shortVid123",
                            "title": { "runs": [{ "text": "Short Preview #shorts" }] },
                            "ownerText": { "runs": [{ "text": "Clips" }] },
                            "lengthText": { "simpleText": "0:18" }
                          }
                        },
                        {
                          "videoRenderer": {
                            "videoId": "dQw4w9WgXcQ",
                            "title": { "runs": [{ "text": "Lagu Kenangan (Official Audio)" }] },
                            "ownerText": { "runs": [{ "text": "Tulus - Topic" }] },
                            "lengthText": { "simpleText": "4:12" }
                          }
                        }
                      ]
                    }
                  }
                ]
              }
            }
          }
        }
      };</script></head></html>
      """.trimIndent()

    val parsed = YouTubeDataApiClient.parsePublicSearchHtml(fakeHtml, maxResults = 10)
    assertEquals(1, parsed.size)
    assertEquals("dQw4w9WgXcQ", parsed.first().videoId)
    assertEquals("Lagu Kenangan (Official Audio)", parsed.first().title)
    assertEquals("Tulus - Topic", parsed.first().channelTitle)
    assertEquals(252_000L, parsed.first().durationMs)
  }

  @Test
  fun kenanganScreen_separatesKenanganAlbumGaleriAndSupportsMultiSelectDelete() {
    val now = 1_700_000_000_000L
    val album1 =
      LocalAlbumEntity(
        id = "alb_10",
        ownerUid = "u1",
        title = "Liburan Bali",
        description = "Pantai selatan",
        createdAtMillis = now,
        updatedAtMillis = now,
      )
    val album2 =
      LocalAlbumEntity(
        id = "alb_11",
        ownerUid = "u1",
        title = "Pulau Nusa Penida",
        description = "Trip lanjutan",
        createdAtMillis = now - 2000L,
        updatedAtMillis = now - 2000L,
      )
    val collection =
      KenanganCollectionEntity(
        id = "kng_10",
        ownerUid = "u1",
        title = "Kompilasi Bali",
        description = "Dua album pantai bersama",
        albumIdsCsv = "alb_10,alb_11",
        createdAtMillis = now,
        updatedAtMillis = now,
      )
    val mem1 =
      LocalMemoryEntity(
        id = "m_k_1",
        ownerUid = "u1",
        albumId = "alb_10",
        title = "Matahari Terbit",
        locationName = "Sanur",
        mediaType = "photo",
        mediaUri = "file:///tmp/sanur.jpg",
        memoryDateMillis = now,
        createdAtMillis = now,
        updatedAtMillis = now,
      )
    val mem2 =
      LocalMemoryEntity(
        id = "m_k_2",
        ownerUid = "u1",
        albumId = "alb_10",
        title = "Cerita Senja",
        story = "Kami berjalan di tepi pantai.",
        mediaType = "story",
        mediaUri = null,
        memoryDateMillis = now - 5000L,
        createdAtMillis = now - 5000L,
        updatedAtMillis = now - 5000L,
      )
    var deletedBatch: List<LocalMemoryEntity> = emptyList()

    composeRule.setContent {
      EvoriaTheme {
        KenanganScreen(
          albums = listOf(album1, album2),
          memories = listOf(mem1, mem2),
          kenanganCollections = listOf(collection),
          onOpenAlbum = {},
          onOpenMemory = {},
          onOpenAddMemory = {},
          onImportGalleryPhotos = {},
          onSaveAlbum = { _, _, _, _, _, _, _ -> },
          onToggleFavorite = {},
          onDeleteMemories = { deletedBatch = it },
        )
      }
    }

    // Default Kenangan sub-page shows multi-album compilation cards (NOT individual photos)
    composeRule.onNodeWithTag("kenangan_collection_kng_10").assertExists()

    // Switch to Album sub-page -> shows dedicated album grid & create album CTA
    composeRule.onNodeWithTag("kenangan_subtab_album").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("create_album_button").assertExists()
    composeRule.onNodeWithTag("album_card_alb_10").assertExists()

    // Switch to Galeri sub-page -> shows 4-col photo/video/story grid & multi-select delete
    composeRule.onNodeWithTag("kenangan_subtab_galeri").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("galeri_grid").assertExists()
    composeRule.onNodeWithTag("memory_card_m_k_1").assertExists()

    composeRule.onNodeWithTag("kenangan_multiselect_toggle").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("kenangan_multiselect_select_all").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("kenangan_multiselect_delete").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("kenangan_confirm_batch_delete").performClick()
    composeRule.waitForIdle()

    assertEquals(setOf("m_k_1", "m_k_2"), deletedBatch.map { it.id }.toSet())
  }

  @Test
  fun musicScreen_directYouTubeSearchDisplaysResultsAndPlaysDirectly() {
    var searchedQuery = ""
    var playedDirectly: YouTubeTrackMatch? = null
    var openedFullPlayer = false
    val sampleMatch =
      YouTubeTrackMatch(
        videoId = "yt_abc_999",
        title = "Hati-Hati di Jalan",
        channelTitle = "Tulus",
        durationMs = 242_000L,
        embeddable = true,
      )

    composeRule.setContent {
      EvoriaTheme {
        MusicAndSoundtrackScreen(
          playlists = listOf(PlaylistEntity("pl_1", "Soundtrack", "Kita", null, 100L)),
          tracks = emptyList(),
          albums = emptyList(),
          playerState = MusicPlayerState(),
          youtubeDirectSearchState =
            YouTubeDirectSearchUiState.Success(
              query = "Hati Hati di Jalan",
              results = listOf(sampleMatch),
            ),
          onSearchYouTubeDirect = { searchedQuery = it },
          onPlayYouTubeMatchDirectly = { match, _, onReady ->
            playedDirectly = match
            onReady()
          },
          onCreatePlaylist = { _, _, _ -> },
          onAddAudioTrack = { _, _, _, _ -> },
          onMoveTrackOrder = { _, _, _ -> },
          onDeleteTrack = {},
          onPlayTrack = { _, _ -> },
          onOpenFullPlayer = { openedFullPlayer = true },
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("music_youtube_search_input").performTextReplacement("Tulus Hati Hati")
    composeRule.onNodeWithTag("music_youtube_search_submit").performClick()
    composeRule.waitForIdle()
    assertEquals("Tulus Hati Hati", searchedQuery)

    composeRule.onNodeWithTag("music_youtube_results_list").assertExists()
    composeRule.onNodeWithTag("yt_direct_play_yt_abc_999").performClick()
    composeRule.waitForIdle()

    assertEquals(sampleMatch, playedDirectly)
    assertTrue(openedFullPlayer)
  }

  @Test
  fun youtubeDataApiClient_parsesInnerTubeMobileAndEscapedHtmlAndBuildsCleanQueries() {
    val queries =
      YouTubeDataApiClient.buildCandidateQueries(
        title = "Pura-Pura Lupa (Remastered 2024) (feat. Guest)",
        artist = "Mahen, Guest Artist",
      )
    assertEquals(
      listOf(
        "Pura-Pura Lupa Mahen official audio",
        "Pura-Pura Lupa Mahen",
        "Pura-Pura Lupa",
      ),
      queries,
    )

    val innerTubeJson =
      """
      {
        "contents": {
          "sectionListRenderer": {
            "contents": [
              {
                "videoWithContextRenderer": {
                  "videoId": "vidMahen001",
                  "headline": { "runs": [{ "text": "Mahen - Pura-Pura Lupa (Official Lyric Video)" }] },
                  "shortBylineText": { "runs": [{ "text": "Indo Semar Records" }] },
                  "lengthText": { "runs": [{ "text": "4:08" }] }
                }
              },
              {
                "compactVideoRenderer": {
                  "videoId": "vidCompact2",
                  "title": { "simpleText": "Pura-Pura Lupa Acoustic" },
                  "longBylineText": { "runs": [{ "text": "Mahen - Topic" }] },
                  "lengthText": { "simpleText": "3:55" }
                }
              },
              {
                "videoWithContextRenderer": {
                  "videoId": "shortClip15",
                  "headline": { "runs": [{ "text": "Teaser" }] },
                  "shortBylineText": { "runs": [{ "text": "Clips" }] },
                  "lengthText": { "simpleText": "0:15" }
                }
              }
            ]
          }
        }
      }
      """.trimIndent()

    val innerParsed = YouTubeDataApiClient.parseInnerTubeJson(innerTubeJson, maxResults = 10)
    assertEquals(2, innerParsed.size)
    assertEquals("vidMahen001", innerParsed[0].videoId)
    assertEquals(248_000L, innerParsed[0].durationMs)
    assertEquals("vidCompact2", innerParsed[1].videoId)
    assertEquals(235_000L, innerParsed[1].durationMs)

    // JS hex-escaped JSON literal used in mobile YouTube HTML responses
    val rawInner = """{"videoRenderer":{"videoId":"hexVid12345","title":{"runs":[{"text":"Pura-Pura Lupa"}]},"ownerText":{"runs":[{"text":"Mahen"}]},"lengthText":{"simpleText":"4:05"}}}"""
    val hexEscaped = rawInner.replace("{", "\\x7b").replace("}", "\\x7d").replace("\"", "\\x22")
    val escapedHtml = """<html><script>var ytInitialData = '$hexEscaped';</script></html>"""

    val htmlParsed = YouTubeDataApiClient.parsePublicSearchHtml(escapedHtml, maxResults = 5)
    assertEquals(1, htmlParsed.size)
    assertEquals("hexVid12345", htmlParsed.first().videoId)
    assertEquals("Pura-Pura Lupa", htmlParsed.first().title)
    assertEquals(245_000L, htmlParsed.first().durationMs)
  }

  @Test
  fun profileBannerPreference_persistsVideoAndPhotoAndClearsCleanly() = runBlocking {
    val prefs = SecurityPreferences(context)
    prefs.clearProfileBanner()
    assertNull(prefs.userPrefsFlow.first().profileBannerUri)
    assertEquals("", prefs.userPrefsFlow.first().profileBannerType)

    prefs.setProfileBanner("file:///data/user/0/com.example/files/media/banner_1.mp4", "video")
    val loaded = prefs.userPrefsFlow.first()
    assertEquals("file:///data/user/0/com.example/files/media/banner_1.mp4", loaded.profileBannerUri)
    assertEquals("video", loaded.profileBannerType)

    prefs.clearProfileBanner()
    val cleared = prefs.userPrefsFlow.first()
    assertNull(cleared.profileBannerUri)
    assertEquals("", cleared.profileBannerType)
  }

  @Test
  fun profilScreen_rendersBannerPickerAndResetButtonWhenCustomBannerIsSet() {
    var resetCalled = false
    composeRule.setContent {
      EvoriaTheme {
        ProfilScreen(
          currentUid = "u1",
          profile = null,
          fallbackName = "Raffa",
          couple = null,
          albumCount = 1,
          memoryCount = 2,
          journalCount = 1,
          capsuleCount = 0,
          pendingShareRequestsCount = 0,
          unreadNotificationCount = 0,
          localStorageLabel = "12 KB",
          userPrefs =
            AppUserPrefs(
              profileBannerUri = "file:///tmp/custom_banner.mp4",
              profileBannerType = "video",
            ),
          securitySettings = SecuritySettings(),
          onSetThemeMode = {},
          onSetReducedMotion = {},
          onOpenConnectPartner = {},
          onOpenShareInbox = {},
          onOpenNotifications = {},
          onOpenMusic = {},
          onOpenUploadQueue = {},
          onOpenSecurity = {},
          onOpenBackupStorage = {},
          onOpenLiquidGlassShowcase = {},
          onResetProfileBanner = { resetCalled = true },
          onSignOut = {},
        )
      }
    }

    composeRule.onNodeWithTag("profil_banner_picker_button").assertExists()
    composeRule.onNodeWithTag("profil_banner_reset_button").assertExists().performClick()
    composeRule.waitForIdle()
    assertTrue(resetCalled)
  }

  @Test
  fun refleksiScreen_enforcesPartnerCapsuleLockAndTriggersAiInsightCallback() {
    var requestedAi = false
    val futureUnlock = System.currentTimeMillis() + 5L * 86_400_000L
    val partnerCapsule =
      SharedReflectionDto(
        id = "ref_cap_partner",
        authorUid = "partner_uid",
        authorName = "Nadia",
        memberUids = listOf("my_uid", "partner_uid"),
        kind = "capsule",
        title = "Kapsul Masa Depan Nadia",
        body = "Rahasia yang tidak boleh terlihat sebelum tanggal buka.",
        unlockAtMillis = futureUnlock,
        isSealed = true,
      )

    composeRule.setContent {
      EvoriaTheme {
        RefleksiScreen(
          journals = emptyList(),
          letters = emptyList(),
          moods = emptyList(),
          reflectionAnswers = emptyList(),
          capsules = emptyList(),
          onSaveJournal = { _, _, _, _, _, _, _, _ -> },
          onDeleteJournal = {},
          onSaveLetter = { _, _, _, _, _, _, _ -> },
          onDeleteLetter = {},
          onRecordMood = { _, _ -> },
          onSaveReflectionAnswer = { _, _, _ -> },
          onCreateTimeCapsule = { _, _, _, _, _ -> },
          onReadEnforcedCapsule = { null },
          onOpenMusic = {},
          sharedReflections = listOf(partnerCapsule),
          currentUid = "my_uid",
          aiConsentGranted = true,
          aiReflectionState = AiReflectionUiState.Idle,
          onGenerateAiReflection = { requestedAi = true },
        )
      }
    }

    // Verify sealed partner capsule never leaks body while locked
    composeRule.onNodeWithTag("refleksi_tab_kapsul").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("refleksi_lazy_column").performScrollToNode(hasText("Kapsul Masa Depan Nadia"))
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Kapsul Masa Depan Nadia").assertExists().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("masih tersegel rapat", substring = true).assertExists()
    composeRule.onNodeWithText("Rahasia yang tidak boleh terlihat sebelum tanggal buka.").assertDoesNotExist()
    composeRule.onNodeWithText("Tutup").performClick()
    composeRule.waitForIdle()

    // Scroll back to tabs row, switch to Pertanyaan tab & trigger Gemini AI reflection button
    composeRule.onNodeWithTag("refleksi_lazy_column").performScrollToIndex(0)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("refleksi_tab_pertanyaan").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("refleksi_lazy_column").performScrollToNode(hasTestTag("refleksi_ai_insight_card"))
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("refleksi_ai_generate_button").assertExists().performClick()
    composeRule.waitForIdle()
    assertTrue(requestedAi)
  }

  @Test
  fun addOrEditMemoryScreen_invokesAiDraftCallback() {
    var draftTriggered = false
    composeRule.setContent {
      EvoriaTheme {
        AddOrEditMemoryScreen(
          existing = null,
          albums = emptyList(),
          aiDraftState = AiMemoryDraftUiState.Idle,
          aiConsentGranted = true,
          onGenerateAiDraft = { _, _, _ -> draftTriggered = true },
          onClearAiDraft = {},
          onBack = {},
          onSaveMediaMemories = { _, _, _, _, _, _, _, _ -> },
          onSaveStoryOnly = { _, _, _, _, _, _, _, _ -> },
        )
      }
    }

    composeRule.onNodeWithTag("memory_ai_draft_button").assertExists().performScrollTo().performClick()
    composeRule.waitForIdle()
    assertTrue(draftTriggered)
  }

  @Test
  fun youtubeDataApiClient_parsesYouTubeMusicLockupIndonesianDurationAndNbspArtists() {
    assertEquals(244_000L, YouTubeDataApiClient.parseMmSsDurationMs("4.04"))
    assertEquals("KAROL G", YouTubeDataApiClient.cleanPrimaryArtist("KAROL G,\u00A0Judeline,\u00A0rusowsky"))

    val ytMusicJson =
      """
      {
        "contents": {
          "musicResponsiveListItemRenderer": {
            "overlay": {
              "musicItemThumbnailOverlayRenderer": {
                "content": {
                  "musicPlayButtonRenderer": {
                    "playNavigationEndpoint": {
                      "watchEndpoint": { "videoId": "9II3OGZETo4" }
                    }
                  }
                }
              }
            },
            "flexColumns": [
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": { "runs": [{ "text": "Hati-Hati di Jalan" }] }
                }
              },
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": { "runs": [{ "text": "Tulus • Lagu • 4.02" }] }
                }
              }
            ]
          }
        },
        "secondary": {
          "lockupViewModel": {
            "contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
            "contentId": "abcDEF12345",
            "metadata": {
              "lockupMetadataViewModel": {
                "title": { "content": "Hati-Hati di Jalan (Official Audio)" }
              }
            }
          }
        }
      }
      """.trimIndent()

    val parsed = YouTubeDataApiClient.parseInnerTubeJson(ytMusicJson, maxResults = 10)
    assertEquals(2, parsed.size)
    assertEquals("9II3OGZETo4", parsed[0].videoId)
    assertEquals("Hati-Hati di Jalan", parsed[0].title)
    assertEquals("Tulus", parsed[0].channelTitle)
    assertEquals(242_000L, parsed[0].durationMs)
    assertEquals("abcDEF12345", parsed[1].videoId)
    assertEquals("Hati-Hati di Jalan (Official Audio)", parsed[1].title)
  }

  @Test
  fun musicScreen_autoMatchesUnmatchedSpotifyPlaylistAndHeroPlayTriggersFirstTrack() {
    val autoMatchedPlaylistIds = mutableListOf<String>()
    val matchedTrackIds = mutableListOf<String>()
    val pl = PlaylistEntity("pl_sp_auto", "Favorit Berdua", "Dari Spotify", null, 100L, sourceType = "spotify")
    val trk1 =
      TrackEntity(
        id = "trk_unmatched_1",
        playlistId = "pl_sp_auto",
        title = "Sial",
        artist = "Mahalini",
        mediaUri = "",
        durationMs = 240_000L,
        sortOrder = 0,
        sourceType = "spotify",
        youtubeVideoId = null,
      )

    composeRule.setContent {
      EvoriaTheme {
        MusicAndSoundtrackScreen(
          playlists = listOf(pl),
          tracks = listOf(trk1),
          albums = emptyList(),
          playerState = MusicPlayerState(),
          onCreatePlaylist = { _, _, _ -> },
          onMatchPlaylistWithYouTube = { autoMatchedPlaylistIds.add(it) },
          onMatchTrackWithYouTube = { matchedTrackIds.add(it.id) },
          onAddAudioTrack = { _, _, _, _ -> },
          onMoveTrackOrder = { _, _, _ -> },
          onDeleteTrack = {},
          onPlayTrack = { _, _ -> },
          onOpenFullPlayer = {},
          onBack = {},
        )
      }
    }

    composeRule.waitForIdle()
    assertEquals(listOf("pl_sp_auto"), autoMatchedPlaylistIds)

    composeRule.onNodeWithTag("spotify_hero_play_button").assertExists().performClick()
    composeRule.waitForIdle()
    assertEquals(listOf("trk_unmatched_1"), matchedTrackIds)
  }

  @Test
  fun youtubeInAppPlayback_handlesError152WithAppOriginAndMobileWatchBridge() {
    assertTrue(isYouTubeEmbedFallbackErrorCode(152))
    assertTrue(isYouTubeEmbedFallbackErrorCode(101))
    assertTrue(isYouTubeEmbedFallbackErrorCode(150))
    assertTrue(isYouTubeEmbedFallbackErrorCode(153))
    assertTrue(isYouTubeEmbedFallbackErrorCode(5))
    assertTrue(isYouTubeEmbedFallbackErrorCode(2))
    assertTrue(isYouTubeEmbedFallbackErrorCode(0))
    assertFalse(isYouTubeEmbedFallbackErrorCode(404))

    val appOrigin = buildAppEmbedOrigin("com.aistudio.evoria.vqmtxr")
    assertEquals("https://com.aistudio.evoria.vqmtxr", appOrigin)

    val iframeHtml = buildYouTubeIFrameHtml("dQw4w9WgXcQ", appOrigin)
    assertTrue(iframeHtml.contains("origin: 'https://com.aistudio.evoria.vqmtxr'"))
    assertTrue(iframeHtml.contains("widget_referrer: 'https://com.aistudio.evoria.vqmtxr'"))
    assertTrue(iframeHtml.contains("strict-origin-when-cross-origin"))
    assertTrue(iframeHtml.contains("EvoriaYtBridge.onFallbackNeeded"))

    val fallbackUrl = resolveYouTubeInAppFallbackUrl("dQw4w9WgXcQ?bad")
    assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQbad&autoplay=1", fallbackUrl)

    val watchBridgeJs = buildYouTubeMobileWatchBridgeJs()
    assertTrue(watchBridgeJs.contains("window.playCmd"))
    assertTrue(watchBridgeJs.contains("window.pauseCmd"))
    assertTrue(watchBridgeJs.contains("window.seekCmd"))
    assertTrue(watchBridgeJs.contains("window.loadFullVideo"))
    assertTrue(watchBridgeJs.contains("window.EvoriaYtBridge.onProgress"))
  }

  @Test
  fun playerManager_youtubeErrorClearsOncePlaybackResumes() {
    val manager = EvoriaPlayerManager(context)
    val track =
      TrackEntity(
        id = "trk_yt_152",
        playlistId = "pl_1",
        title = "Pura Pura Lupa",
        artist = "Mahen",
        mediaUri = "",
        durationMs = 240_000L,
        sortOrder = 0,
        sourceType = "youtube",
        youtubeVideoId = "9II3OGZETo4",
      )

    manager.setQueueAndPlay(listOf(track), track.id)
    manager.onYouTubePlayerError("Kendala embed (152)")
    assertEquals("Kendala embed (152)", manager.state.value.errorMessage)
    assertFalse(manager.state.value.isPlaying)

    manager.onYouTubePlaybackUpdate(isPlaying = true, positionMs = 1500L, durationMs = 240_000L)
    assertNull(manager.state.value.errorMessage)
    assertTrue(manager.state.value.isPlaying)
    assertEquals(1500L, manager.state.value.positionMs)

    manager.release()
  }

  @Test
  fun kenanganCollection_roomCrudAndMultiAlbumGrouping_worksAccurately() = runBlocking {
    val now = 1_725_000_000_000L
    val col =
      KenanganCollectionEntity(
        id = "kng_room_1",
        ownerUid = "u1",
        title = "Musim Panas",
        description = "Gabungan tiga album",
        albumIdsCsv = "alb_a, alb_b , alb_a, alb_c",
        visibility = "shared",
        createdAtMillis = now,
        updatedAtMillis = now,
      )
    assertEquals(listOf("alb_a", "alb_b", "alb_c"), col.albumIdList())

    repo.saveKenanganCollection(col)
    val observed = repo.observeKenanganCollections("u1").first()
    assertEquals(1, observed.size)
    assertEquals("Musim Panas", observed.first().title)

    repo.deleteKenanganCollection("kng_room_1")
    assertTrue(repo.observeKenanganCollections("u1").first().isEmpty())
  }

  @Test
  fun dragSelection_computeRangeSelection_handlesForwardBackwardAndBase() {
    val ids = listOf("a", "b", "c", "d", "e")
    assertEquals(setOf("b", "c", "d"), computeRangeSelection(ids, anchorId = "b", hoveredId = "d"))
    assertEquals(setOf("b", "c", "d"), computeRangeSelection(ids, anchorId = "d", hoveredId = "b"))
    assertEquals(setOf("a", "c", "d", "e"), computeRangeSelection(ids, anchorId = "c", hoveredId = "e", baseSelection = setOf("a")))
    assertEquals(setOf("a", "missing"), computeRangeSelection(ids, anchorId = "missing", hoveredId = "b", baseSelection = setOf("a")))
  }

  @Test
  fun mediaStorageHelper_inspectMediaDetails_formatsDateSizeAndLocation() {
    val tmpFile = File(context.cacheDir, "rincian_test_photo.jpg")
    tmpFile.writeBytes(ByteArray(2048) { 1 })
    val mem =
      LocalMemoryEntity(
        id = "mem_det_1",
        ownerUid = "u1",
        title = "Senja",
        locationName = "Yogyakarta",
        mediaType = "photo",
        mediaUri = Uri.fromFile(tmpFile).toString(),
        memoryDateMillis = 1_700_000_000_000L, // Nov 14, 2023 UTC
        createdAtMillis = 1_700_000_000_000L,
        updatedAtMillis = 1_700_000_000_000L,
      )
    val info = MediaStorageHelper.inspectMediaDetails(context, mem, ZoneId.of("UTC"))
    assertEquals("rincian_test_photo.jpg", info.fileName)
    assertEquals("2023", info.year)
    assertEquals("14", info.dayOfMonth)
    assertEquals(2048L, info.sizeBytes)
    assertTrue(info.sizeFormatted.contains("KB"))
    assertTrue(info.storageLocationLabel.contains("rincian_test_photo.jpg"))
    tmpFile.delete()
  }

  @Test
  fun qrCodeGenerator_extractInviteCode_handlesDeepLinksAndPlainCodes() {
    assertEquals("AB12CD", QrCodeGenerator.extractInviteCode("evoria://invite?code=ab12cd"))
    assertEquals("ZX98QW", QrCodeGenerator.extractInviteCode("https://evoria.app/join?code=zx98qw&ref=qr"))
    assertEquals("KODE77", QrCodeGenerator.extractInviteCode("  kode77  "))
    assertNull(QrCodeGenerator.extractInviteCode("not valid text!!"))
    assertNull(QrCodeGenerator.extractInviteCode("12"))
  }

  @Test
  fun securityPreferences_customAccentHexAndSyncLockMirror_workProperly() = runBlocking {
    val prefs = SecurityPreferences(context)
    prefs.setCustomAccentHex(0xFF4F46E5L)
    assertEquals(0xFF4F46E5L, prefs.userPrefsFlow.first().customAccentHex)

    // Choosing a named preset clears custom color cleanly
    prefs.setAccentPreset(EvoriaAccentPreset.OCEAN)
    assertNull(prefs.userPrefsFlow.first().customAccentHex)
    assertEquals(EvoriaAccentPreset.OCEAN, prefs.userPrefsFlow.first().accentPreset)

    // Synchronous background timestamp mirror updates immediately
    val stamped = prefs.recordBackgroundTimestampSync(nowMillis = 999_888L)
    assertEquals(999_888L, stamped)
    assertTrue(prefs.securitySettingsFlow.first().lastBackgroundMillis >= 999_888L)
  }

  @Test
  fun evoriaNotificationHelper_routesKindsAndRespectsCategoryToggles() {
    assertEquals(EvoriaNotificationHelper.CHANNEL_INVITES, EvoriaNotificationHelper.channelForKind("partner_invite"))
    assertEquals(EvoriaNotificationHelper.CHANNEL_CAPSULES, EvoriaNotificationHelper.channelForKind("capsule_ready"))
    assertEquals(EvoriaNotificationHelper.CHANNEL_SECURITY, EvoriaNotificationHelper.channelForKind("security_pin"))
    assertEquals(EvoriaNotificationHelper.CHANNEL_SHARES, EvoriaNotificationHelper.channelForKind("share_memory"))

    val disabledShares = AppUserPrefs(notificationShares = false, notificationInvites = true)
    assertFalse(EvoriaNotificationHelper.isCategoryEnabled("share_music", disabledShares))
    assertTrue(EvoriaNotificationHelper.isCategoryEnabled("invite", disabledShares))
  }

  @Test
  fun uploadQueueScreen_displaysMediaTypeAlbumAndDiagnostics() {
    val now = 1_710_000_000_000L
    val mem =
      LocalMemoryEntity(
        id = "m_q_1",
        ownerUid = "u1",
        albumId = "alb_q_1",
        title = "Video Liburan",
        mediaType = "video",
        mediaUri = "",
        memoryDateMillis = now,
        createdAtMillis = now,
        updatedAtMillis = now,
      )
    val album = LocalAlbumEntity("alb_q_1", "u1", "Pantai Selatan", "", null, "shared", null, now, now)
    val job =
      UploadJobEntity(
        id = "job_1",
        memoryId = "m_q_1",
        mediaUri = "",
        targetAlbumId = "alb_q_1",
        targetVisibility = "shared",
        caption = "Video Liburan",
        status = "queued",
        attemptCount = 2,
        lastError = null,
        createdAtMillis = now,
        updatedAtMillis = now,
      )

    composeRule.setContent {
      EvoriaTheme {
        UploadQueueScreen(
          jobs = listOf(job),
          memories = listOf(mem),
          albums = listOf(album),
          onRetry = {},
          onDelete = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("upload_job_card_job_1").assertExists()
    composeRule.onNodeWithText("Video Liburan").assertExists()
    composeRule.onNodeWithText("Video").assertExists()
    composeRule.onNodeWithText("Album: Pantai Selatan", substring = true).assertExists()
  }

  @Test
  fun shareInboxScreen_displaysIncomingMusicAndInvokesSaveAndPlay() {
    var savedPlayedFlag: Boolean? = null
    val sharedPlaylist =
      SharedMusicDto(
        id = "sm_1",
        senderUid = "partner_uid",
        senderName = "Nadia",
        recipientUid = "my_uid",
        memberUids = listOf("my_uid", "partner_uid"),
        kind = "playlist",
        title = "Lagu Senja Kita",
        subtitle = "Pilihan terbaik",
        tracksJson = """[{"title":"Monokrom","artist":"Tulus"}]""",
        status = "pending",
      )

    composeRule.setContent {
      EvoriaTheme {
        ShareInboxScreen(
          currentUid = "my_uid",
          requests = emptyList(),
          sharedMusic = listOf(sharedPlaylist),
          onAccept = {},
          onDecline = {},
          onRevoke = {},
          onAcceptSharedMusic = { _, playNow -> savedPlayedFlag = playNow },
          onDeclineSharedMusic = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("share_inbox_music_section").assertExists()
    composeRule.onNodeWithTag("shared_music_card_sm_1").assertExists()
    composeRule.onNodeWithTag("accept_music_play_sm_1").performClick()
    composeRule.waitForIdle()
    assertEquals(true, savedPlayedFlag)
  }

  @Test
  fun appearanceSettingsScreen_rendersFreeColorPaletteAndAppliesCustomAccent() {
    var appliedHex: Long? = null
    composeRule.setContent {
      EvoriaTheme {
        AppearanceSettingsScreen(
          userPrefs = AppUserPrefs(customAccentHex = 0xFF00897BL),
          onSetThemeMode = {},
          onSetAccentPreset = {},
          onSetCustomAccentHex = { appliedHex = it },
          onSetNavbarThemeStyle = {},
          onSetNavbarGlassIntensity = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("tema_custom_color_card").assertExists()
    composeRule.onNodeWithText("Palet Warna Bebas").assertExists()
    composeRule.onNodeWithTag("tema_apply_custom_hex").performScrollTo().performClick()
    composeRule.waitForIdle()
    assertNotNull(appliedHex)
  }
}
