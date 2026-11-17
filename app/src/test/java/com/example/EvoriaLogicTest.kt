package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.example.data.ai.EvoriaSearchEngine
import com.example.data.local.AppUserPrefs
import com.example.data.local.EvoriaAccentPreset
import com.example.data.local.EvoriaDatabase
import com.example.data.local.EvoriaSanctuaryRepository
import com.example.data.local.LocalAlbumEntity
import com.example.data.local.LocalMemoryEntity
import com.example.data.local.NavbarGlassIntensity
import com.example.data.local.NavbarThemeStyle
import com.example.data.local.PinVerifyResult
import com.example.data.local.PlaylistEntity
import com.example.data.local.SecurityPreferences
import com.example.data.local.SecuritySettings
import com.example.data.local.ThemeMode
import com.example.data.local.TimeCapsuleEntity
import com.example.data.local.TrackEntity
import com.example.data.local.canEditDeveloperSocials
import com.example.data.remote.SpotifyPlaylistImporter
import com.example.data.remote.YouTubeDataApiClient
import com.example.media.EvoriaPlayerManager
import com.example.media.MusicPlayerState
import com.example.ui.SearchUiState
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
import com.example.ui.screens.JelajahScreen
import com.example.ui.screens.KenanganScreen
import com.example.ui.screens.MusicAndSoundtrackScreen
import com.example.ui.screens.ProfilScreen
import com.example.ui.screens.filterAndSortMemories
import com.example.ui.screens.formatGallerySectionHeader
import com.example.ui.screens.normalizeSocialUrl
import com.example.ui.theme.EvoriaTheme
import com.example.util.DisplayModeCandidate
import com.example.util.selectLocked60HzDisplayModeId
import com.example.util.shouldMutateWindowAttributesFor60Fps
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
  fun lock60FpsPreference_defaultsTrueAndPersistsToggles() = runBlocking {
    val prefs = SecurityPreferences(context)
    assertTrue(AppUserPrefs().lock60Fps)
    prefs.setLock60Fps(false)
    assertFalse(prefs.userPrefsFlow.first().lock60Fps)
    prefs.setLock60Fps(true)
    assertTrue(prefs.userPrefsFlow.first().lock60Fps)
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
}
