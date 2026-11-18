package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.EvoriaSearchEngine
import com.example.data.ai.LocalVectorSearchEngine
import com.example.data.local.EvoriaDatabase
import com.example.data.local.EvoriaSanctuaryRepository
import com.example.data.local.LocalMemoryEntity
import com.example.data.local.PhotoEmbeddingEntity
import com.example.data.local.PhotoIndexStatus
import com.example.ml.ClipBpeTokenizer
import com.example.ml.EmbeddingMath
import com.example.ml.EvoriaVisionRuntime
import com.example.ml.IndexingStats
import com.example.ml.IndonesianQueryTranslator
import com.example.ml.LocalModelManifest
import com.example.ml.ModelState
import com.example.ml.VisionEmbeddingEngine
import com.example.ui.SearchUiState
import com.example.ui.screens.JelajahScreen
import com.example.ui.theme.EvoriaTheme
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EvoriaVisionSearchTest {

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
  fun embeddingMath_normalizesAndRoundTripsFloat16Accurately() {
    val raw = FloatArray(512) { idx -> ((idx % 29) - 14) * 0.13f + 0.05f }
    val normed = EmbeddingMath.l2Normalize(raw)
    val normSq = normed.sumOf { (it * it).toDouble() }
    assertTrue("Expected unit L2 norm, got $normSq", abs(sqrt(normSq) - 1.0) < 1e-4)

    val packed = EmbeddingMath.toFloat16ByteArray(normed)
    assertEquals(1024, packed.size)

    val unpacked = EmbeddingMath.fromFloat16ByteArray(packed)
    assertEquals(512, unpacked.size)

    for (i in normed.indices) {
      assertTrue("Delta too large at $i", abs(normed[i] - unpacked[i]) < 0.002f)
    }
    val selfSim = EmbeddingMath.cosineSimilarity(normed, unpacked)
    assertTrue("Expected cosine > 0.9999, got $selfSim", selfSim > 0.9999f)
  }

  @Test
  fun indonesianQueryTranslator_expandsNaturalLanguageVisualConcepts() {
    val catPrompts = IndonesianQueryTranslator.buildVisualPrompts("kucing tidur")
    assertTrue(catPrompts.any { "cat" in it && "sleeping" in it })

    val beachPrompts = IndonesianQueryTranslator.buildVisualPrompts("pantai saat matahari terbenam")
    assertTrue(beachPrompts.any { "beach" in it && "sunset" in it })

    val carPrompts = IndonesianQueryTranslator.buildVisualPrompts("mobil merah")
    assertTrue(carPrompts.any { "car" in it && "red" in it })

    val bikePrompts = IndonesianQueryTranslator.buildVisualPrompts("orang naik sepeda")
    assertTrue(bikePrompts.any { "bicycle" in it })
  }

  @Test
  fun clipBpeTokenizer_outputsFixed77LengthWithBosEosAndMask() {
    val encoder =
      mapOf(
        "<|startoftext|>" to ClipBpeTokenizer.DEFAULT_BOS_ID,
        "<|endoftext|>" to ClipBpeTokenizer.DEFAULT_EOS_ID,
        "c" to 10,
        "a" to 11,
        "t</w>" to 12,
        "ca" to 100,
        "cat</w>" to 2368,
      )
    val merges = listOf("c" to "a", "ca" to "t</w>")
    val tokenizer = ClipBpeTokenizer.fromMapsForTest(encoder, merges)

    val result = tokenizer.encode("cat")
    assertEquals(77, result.inputIds.size)
    assertEquals(77, result.attentionMask.size)
    assertEquals(ClipBpeTokenizer.DEFAULT_BOS_ID.toLong(), result.inputIds[0])
    assertEquals(2368L, result.inputIds[1])
    assertEquals(ClipBpeTokenizer.DEFAULT_EOS_ID.toLong(), result.inputIds[2])
    assertEquals(ClipBpeTokenizer.DEFAULT_EOS_ID.toLong(), result.inputIds[76])
    assertEquals(3, result.activeLength)
    assertEquals(1L, result.attentionMask[0])
    assertEquals(1L, result.attentionMask[1])
    assertEquals(1L, result.attentionMask[2])
    assertEquals(0L, result.attentionMask[3])
  }

  @Test
  fun localVectorSearchEngine_ranksVisualConceptsWithoutMetadataTags() = runBlocking {
    val fakeEngine = FakeClipEmbeddingEngine()
    val vectorEngine = LocalVectorSearchEngine(fakeEngine)

    val catPhoto = dummyPhotoMemory("m_cat", "Galeri Kita")
    val beachPhoto = dummyPhotoMemory("m_beach", "Galeri Kita")
    val carPhoto = dummyPhotoMemory("m_car", "Galeri Kita")

    val embeddings =
      mapOf(
        "m_cat" to embeddingEntity("m_cat", basisVector(dim = 0)),
        "m_beach" to embeddingEntity("m_beach", basisVector(dim = 1)),
        "m_car" to embeddingEntity("m_car", basisVector(dim = 2)),
      )

    val catSearch =
      vectorEngine.search(
        query = "kucing tidur",
        candidates = listOf(catPhoto, beachPhoto, carPhoto),
        embeddingsByMemoryId = embeddings,
      )
    assertTrue(catSearch.usedVisualModel)
    assertEquals(listOf("m_cat"), catSearch.matches.map { it.memoryId })
    assertTrue(catSearch.matches.first().visualScore > 0.9f)

    val sunsetSearch =
      vectorEngine.search(
        query = "pantai saat matahari terbenam",
        candidates = listOf(catPhoto, beachPhoto, carPhoto),
        embeddingsByMemoryId = embeddings,
      )
    assertEquals(listOf("m_beach"), sunsetSearch.matches.map { it.memoryId })
  }

  @Test
  fun roomPhotoEmbeddingAndSearchEngine_workEndToEndOfflineWithoutGemini() = runBlocking {
    val catMem = dummyPhotoMemory("m1", "Galeri Kita")
    val mountainMem = dummyPhotoMemory("m2", "Galeri Kita")
    repo.saveMemoryAndEnqueue(catMem, enqueueUploadJob = false)
    repo.saveMemoryAndEnqueue(mountainMem, enqueueUploadJob = false)

    repo.upsertPhotoEmbedding(embeddingEntity("m1", basisVector(dim = 0)))
    repo.upsertPhotoEmbedding(embeddingEntity("m2", basisVector(dim = 3)))

    val stats =
      EvoriaVisionRuntime.computeIndexingStats(
        memories = listOf(catMem, mountainMem),
        embeddings = repo.getPhotoEmbeddingsSnapshot("u1"),
        modelVersion = FakeClipEmbeddingEngine.MODEL_VER,
      )
    assertEquals(2, stats.totalPhotoCount)
    assertEquals(2, stats.indexedCount)
    assertEquals(0, stats.pendingCount)

    val engine =
      EvoriaSearchEngine(
        context = context,
        embeddingEngine = FakeClipEmbeddingEngine(),
        sanctuaryRepo = repo,
        ownerUidProvider = { "u1" },
      )
    val outcome =
      engine.searchMemories(
        query = "pemandangan gunung",
        candidates = listOf(catMem, mountainMem),
        allowCloudAi = false,
      )
    assertFalse(outcome.usedCloudAi)
    assertTrue(outcome.usedOnDeviceVision)
    assertEquals(listOf("m2"), outcome.matchingMemoryIds)
  }

  @Test
  fun reflectionAndStoryFallbacks_succeedInIndonesianWithoutTimeoutFailure() {
    val engine = EvoriaSearchEngine()
    val insight =
      engine.buildLocalReflectionInsight(
        recentMoods = listOf("lelah (banyak kerjaan hari ini)"),
        recentJournalSnippets = listOf("Hari Panjang: Tetap berusaha tenang."),
      )
    assertTrue(insight.promptQuestion.isNotBlank())
    assertTrue(insight.warmInsight.isNotBlank())
    assertFalse(insight.warmInsight.contains("habis waktu", ignoreCase = true))

    val draft = engine.buildLocalMemoryStoryDraft(seedTitle = "", location = "Bandung")
    assertTrue("Bandung" in draft.title)
    assertTrue("Bandung" in draft.story)
    assertTrue(draft.tagsCsv.isNotBlank())
  }

  @Test
  fun jelajahScreen_displaysOnDeviceVisionStatusAndDownloadAction() {
    var downloadTapped = false
    composeRule.setContent {
      EvoriaTheme {
        JelajahScreen(
          memories = emptyList(),
          albums = emptyList(),
          searchHistory = emptyList(),
          searchState = SearchUiState.Idle,
          onRunSearch = { _, _, _, _ -> },
          onClearHistory = {},
          onOpenMemory = {},
          modelState = ModelState.NotDownloaded,
          onDownloadModel = { downloadTapped = true },
        )
      }
    }

    composeRule.onNodeWithTag("vision_status_card").assertIsDisplayed()
    composeRule.onNodeWithTag("vision_download_button").assertIsDisplayed().performClick()
    assertTrue(downloadTapped)
  }

  @Test
  fun jelajahScreen_displaysIndexingProgressWhenModelReady() {
    val manifest =
      LocalModelManifest(
        modelId = "Xenova/clip-vit-base-patch32",
        modelVersion = FakeClipEmbeddingEngine.MODEL_VER,
        embeddingDim = 512,
        inputSize = 224,
        license = "MIT",
        verifiedAtMillis = 1234L,
        files = emptyList(),
      )
    var indexTapped = false
    composeRule.setContent {
      EvoriaTheme {
        JelajahScreen(
          memories = emptyList(),
          albums = emptyList(),
          searchHistory = emptyList(),
          searchState = SearchUiState.Idle,
          onRunSearch = { _, _, _, _ -> },
          onClearHistory = {},
          onOpenMemory = {},
          modelState = ModelState.Ready(manifest),
          indexingStats = IndexingStats(totalPhotoCount = 3, indexedCount = 2, pendingCount = 1),
          isIndexingPhotos = false,
          onTriggerIndexing = { indexTapped = true },
        )
      }
    }

    composeRule.onNodeWithText("2/3 foto terindeks (1 menunggu)", substring = true).assertIsDisplayed()
    composeRule.onNodeWithTag("vision_index_now_button").assertIsDisplayed().performClick()
    assertTrue(indexTapped)
  }

  private fun dummyPhotoMemory(id: String, title: String) =
    LocalMemoryEntity(
      id = id,
      ownerUid = "u1",
      title = title,
      caption = "",
      story = "",
      locationName = "",
      mediaUri = "file:///tmp/$id.jpg",
      mediaType = "photo",
      tagsCsv = "galeri",
      memoryDateMillis = 1000L,
      createdAtMillis = 1000L,
      updatedAtMillis = 1000L,
    )

  private fun embeddingEntity(memoryId: String, vector: FloatArray) =
    PhotoEmbeddingEntity(
      memoryId = memoryId,
      ownerUid = "u1",
      fileUri = "file:///tmp/$memoryId.jpg",
      embeddingBlob = EmbeddingMath.toFloat16ByteArray(vector),
      embeddingDim = vector.size,
      modelVersion = FakeClipEmbeddingEngine.MODEL_VER,
      indexStatus = PhotoIndexStatus.INDEXED,
      contentSignature = "sig_$memoryId",
      indexedAtMillis = 1000L,
    )

  private fun basisVector(dim: Int, totalDims: Int = 512): FloatArray =
    FloatArray(totalDims) { idx -> if (idx == dim) 1f else 0f }

  private class FakeClipEmbeddingEngine : VisionEmbeddingEngine {
    override val modelVersion: String = MODEL_VER
    override val embeddingDim: Int = 512

    override fun isReady(): Boolean = true

    override suspend fun embedImage(uriString: String): FloatArray? = null

    override suspend fun embedTextPrompts(prompts: List<String>): FloatArray? {
      val joined = prompts.joinToString(" ").lowercase()
      val targetDim =
        when {
          "cat" in joined || "kucing" in joined -> 0
          "beach" in joined || "sunset" in joined || "pantai" in joined -> 1
          "car" in joined || "mobil" in joined -> 2
          "mountain" in joined || "gunung" in joined -> 3
          else -> 4
        }
      return FloatArray(embeddingDim) { idx -> if (idx == targetDim) 1f else 0f }
    }

    override suspend fun unloadVisionEncoder() {}

    override suspend fun releaseAll() {}

    companion object {
      const val MODEL_VER = "clip-vit-b32-int8-v1"
    }
  }
}
