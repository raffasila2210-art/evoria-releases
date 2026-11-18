package com.example.data.ai

import com.example.data.local.LocalMemoryEntity
import com.example.data.local.PhotoEmbeddingEntity
import com.example.data.local.PhotoIndexStatus
import com.example.ml.EmbeddingMath
import com.example.ml.IndonesianQueryTranslator
import com.example.ml.VisionEmbeddingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SemanticSearchMatch(
  val memoryId: String,
  val visualScore: Float,
  val metadataScore: Float,
  val combinedScore: Float,
)

data class VectorSearchResult(
  val matches: List<SemanticSearchMatch>,
  val translatedPrompts: List<String>,
  val indexedPhotoCount: Int,
  val usedVisualModel: Boolean,
)

/**
 * Pure on-device semantic vector + metadata hybrid search engine for EVORIA Vision Search Lite.
 * Zero network calls, zero Gemini API dependency.
 */
class LocalVectorSearchEngine(
  private val embeddingEngine: VisionEmbeddingEngine? = null,
) {

  suspend fun search(
    query: String,
    candidates: List<LocalMemoryEntity>,
    embeddingsByMemoryId: Map<String, PhotoEmbeddingEntity>,
    albumTitlesById: Map<String, String> = emptyMap(),
    metadataBaselineIds: List<String> = emptyList(),
    minVisualThreshold: Float = DEFAULT_VISUAL_THRESHOLD,
  ): VectorSearchResult = withContext(Dispatchers.Default) {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty() || candidates.isEmpty()) {
      return@withContext VectorSearchResult(
        matches = emptyList(),
        translatedPrompts = emptyList(),
        indexedPhotoCount = 0,
        usedVisualModel = false,
      )
    }

    val prompts = IndonesianQueryTranslator.buildVisualPrompts(cleanQuery)
    val engine = embeddingEngine
    val modelReady = engine != null && engine.isReady()

    val validEmbeddings =
      if (modelReady) {
        candidates.mapNotNull { mem ->
          val emb = embeddingsByMemoryId[mem.id] ?: return@mapNotNull null
          if (emb.indexStatus != PhotoIndexStatus.INDEXED) return@mapNotNull null
          if (emb.modelVersion != engine!!.modelVersion) return@mapNotNull null
          if (emb.embeddingBlob.isEmpty() || emb.embeddingDim <= 0) return@mapNotNull null
          mem.id to emb
        }.toMap()
      } else {
        emptyMap()
      }

    val queryVector =
      if (modelReady && validEmbeddings.isNotEmpty()) {
        engine!!.embedTextPrompts(prompts)
      } else {
        null
      }

    val metadataRankBoost = HashMap<String, Float>(metadataBaselineIds.size)
    metadataBaselineIds.forEachIndexed { idx, id ->
      // First local lexical matches get up to 0.35 boost decaying gently
      metadataRankBoost[id] = (0.35f - idx * 0.02f).coerceAtLeast(0.12f)
    }

    if (queryVector == null) {
      val lexicalOnly =
        metadataBaselineIds.map { id ->
          val metaScore = metadataRankBoost[id] ?: 0.15f
          SemanticSearchMatch(
            memoryId = id,
            visualScore = 0f,
            metadataScore = metaScore,
            combinedScore = metaScore,
          )
        }
      return@withContext VectorSearchResult(
        matches = lexicalOnly,
        translatedPrompts = prompts,
        indexedPhotoCount = validEmbeddings.size,
        usedVisualModel = false,
      )
    }

    val scoredVisual = mutableListOf<SemanticSearchMatch>()
    val rawVisualScores = HashMap<String, Float>(validEmbeddings.size)

    for ((memoryId, entity) in validEmbeddings) {
      val photoVec = EmbeddingMath.fromFloat16ByteArray(entity.embeddingBlob)
      val sim = EmbeddingMath.cosineSimilarity(queryVector, photoVec)
      if (sim.isFinite()) {
        rawVisualScores[memoryId] = sim
      }
    }

    // Dynamic relative cutoff prevents surfacing unrelated photos while catching clear matches
    val maxSim = rawVisualScores.values.maxOrNull() ?: 0f
    val effectiveCutoff =
      if (maxSim >= minVisualThreshold) {
        maxOf(minVisualThreshold, maxSim - MAX_SCORE_DROP_FROM_TOP)
      } else {
        minVisualThreshold
      }

    val candidateIds = candidates.map { it.id }.toSet()
    val allCandidateIds = linkedSetOf<String>().apply {
      addAll(rawVisualScores.keys)
      addAll(metadataRankBoost.keys.filter { it in candidateIds })
    }

    for (memId in allCandidateIds) {
      val visual = rawVisualScores[memId] ?: 0f
      val meta = metadataRankBoost[memId] ?: 0f
      val passesVisual = visual >= effectiveCutoff
      val passesMeta = meta > 0f
      if (!passesVisual && !passesMeta) continue

      val normalizedVisual = ((visual - 0.14f) / 0.24f).coerceIn(0f, 1f)
      val combined = normalizedVisual * 0.78f + meta * 0.65f + (if (passesVisual && passesMeta) 0.12f else 0f)
      scoredVisual.add(
        SemanticSearchMatch(
          memoryId = memId,
          visualScore = visual,
          metadataScore = meta,
          combinedScore = combined,
        )
      )
    }

    scoredVisual.sortByDescending { it.combinedScore }

    VectorSearchResult(
      matches = scoredVisual,
      translatedPrompts = prompts,
      indexedPhotoCount = validEmbeddings.size,
      usedVisualModel = true,
    )
  }

  companion object {
    // CLIP ViT-B/32 cosine similarities for positive image-text matches typically sit around 0.21..0.34
    const val DEFAULT_VISUAL_THRESHOLD = 0.21f
    private const val MAX_SCORE_DROP_FROM_TOP = 0.095f
  }
}
