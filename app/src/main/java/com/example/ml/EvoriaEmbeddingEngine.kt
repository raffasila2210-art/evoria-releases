package com.example.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Abstraction for on-device multimodal embedding generation.
 * Decouples search & background workers from ONNX Runtime so tests can inject deterministic engines.
 */
interface VisionEmbeddingEngine {
  val modelVersion: String
  val embeddingDim: Int

  fun isReady(): Boolean

  suspend fun embedImage(uriString: String): FloatArray?

  suspend fun embedTextPrompts(prompts: List<String>): FloatArray?

  suspend fun unloadVisionEncoder()

  suspend fun releaseAll()
}

/**
 * ONNX Runtime Android implementation using Xenova/clip-vit-base-patch32 (MIT).
 * - Lazy loads vision and text encoders independently to stay within low-RAM limits
 * - Executes all OrtSession create/run/close calls on a single-thread ML dispatcher
 * - Caches recent query embeddings in a bounded LRU map (max 32 queries)
 */
class OnnxClipEmbeddingEngine(
  context: Context,
  private val modelManager: ModelManager,
) : VisionEmbeddingEngine {

  private val appContext = context.applicationContext
  private val mlDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "evoria-clip-ml").apply {
        priority = Thread.NORM_PRIORITY - 1
        isDaemon = true
      }
    }.asCoroutineDispatcher()

  private val sessionMutex = Mutex()
  private var ortEnv: OrtEnvironment? = null
  private var visionSession: OrtSession? = null
  private var textSession: OrtSession? = null
  private var tokenizer: ClipBpeTokenizer? = null

  private val queryCache =
    object : LinkedHashMap<String, FloatArray>(32, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
        size > MAX_QUERY_CACHE_SIZE
    }

  override val modelVersion: String
    get() = modelManager.modelVersion()

  override val embeddingDim: Int = ModelManager.EMBEDDING_DIM

  override fun isReady(): Boolean = modelManager.isReady()

  override suspend fun embedImage(uriString: String): FloatArray? =
    withContext(mlDispatcher) {
      sessionMutex.withLock {
        if (!modelManager.isReady()) return@withLock null
        val nchwPixels = ImagePreprocessor.preprocessUriToClipNchw(appContext, uriString)
          ?: return@withLock null

        val env = ensureOrtEnv() ?: return@withLock null
        val session = ensureVisionSession(env) ?: return@withLock null

        val inputName =
          session.inputNames.firstOrNull { it.contains("pixel", ignoreCase = true) }
            ?: session.inputNames.firstOrNull()
            ?: "pixel_values"
        val shape = longArrayOf(1L, 3L, ModelManager.INPUT_SIZE.toLong(), ModelManager.INPUT_SIZE.toLong())

        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        try {
          inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(nchwPixels), shape)
          results = session.run(mapOf(inputName to inputTensor))
          val outputName =
            session.outputNames.firstOrNull { it.contains("image_embeds", ignoreCase = true) }
              ?: session.outputNames.firstOrNull { it.contains("pooler", ignoreCase = true) }
              ?: session.outputNames.firstOrNull()
          val rawVector = extractFloatVector(results, outputName) ?: return@withLock null
          EmbeddingMath.l2Normalize(rawVector)
        } catch (e: Exception) {
          Log.e(TAG, "Image embedding inference failed for $uriString", e)
          null
        } catch (e: LinkageError) {
          Log.e(TAG, "ONNX Runtime native linkage error during image inference", e)
          null
        } finally {
          runCatching { results?.close() }
          runCatching { inputTensor?.close() }
        }
      }
    }

  override suspend fun embedTextPrompts(prompts: List<String>): FloatArray? =
    withContext(mlDispatcher) {
      val cleanPrompts = prompts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
      if (cleanPrompts.isEmpty()) return@withContext null

      val cacheKey = cleanPrompts.joinToString(" | ").lowercase()
      sessionMutex.withLock {
        queryCache[cacheKey]?.let { return@withLock it }
        if (!modelManager.isReady()) return@withLock null

        val env = ensureOrtEnv() ?: return@withLock null
        val session = ensureTextSession(env) ?: return@withLock null
        val tok = ensureTokenizer() ?: return@withLock null

        val sumVector = FloatArray(embeddingDim)
        var validCount = 0

        for (prompt in cleanPrompts) {
          val encoded = tok.encode(prompt)
          val shape = longArrayOf(1L, encoded.inputIds.size.toLong())
          val inputs = LinkedHashMap<String, OnnxTensor>()
          var results: OrtSession.Result? = null
          try {
            val idsName =
              session.inputNames.firstOrNull { it.contains("input_ids", ignoreCase = true) }
                ?: "input_ids"
            inputs[idsName] = OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.inputIds), shape)

            session.inputNames
              .firstOrNull { it.contains("attention_mask", ignoreCase = true) }
              ?.let { maskName ->
                inputs[maskName] =
                  OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.attentionMask), shape)
              }

            results = session.run(inputs)
            val outputName =
              session.outputNames.firstOrNull { it.contains("text_embeds", ignoreCase = true) }
                ?: session.outputNames.firstOrNull { it.contains("pooler", ignoreCase = true) }
                ?: session.outputNames.firstOrNull()
            val vec = extractFloatVector(results, outputName)
            if (vec != null && vec.size == embeddingDim) {
              val normed = EmbeddingMath.l2Normalize(vec)
              for (i in sumVector.indices) {
                sumVector[i] += normed[i]
              }
              validCount++
            }
          } catch (e: Exception) {
            Log.e(TAG, "Text embedding inference failed for prompt='$prompt'", e)
          } catch (e: LinkageError) {
            Log.e(TAG, "ONNX Runtime native linkage error during text inference", e)
          } finally {
            runCatching { results?.close() }
            inputs.values.forEach { tensor -> runCatching { tensor.close() } }
          }
        }

        if (validCount == 0) return@withLock null
        val finalNormed = EmbeddingMath.l2Normalize(sumVector)
        queryCache[cacheKey] = finalNormed
        finalNormed
      }
    }

  override suspend fun unloadVisionEncoder() {
    withContext(mlDispatcher) {
      sessionMutex.withLock {
        runCatching { visionSession?.close() }
        visionSession = null
      }
    }
  }

  override suspend fun releaseAll() {
    withContext(mlDispatcher) {
      sessionMutex.withLock {
        runCatching { visionSession?.close() }
        runCatching { textSession?.close() }
        visionSession = null
        textSession = null
        tokenizer = null
        queryCache.clear()
      }
    }
  }

  private fun ensureOrtEnv(): OrtEnvironment? =
    ortEnv
      ?: runCatching { OrtEnvironment.getEnvironment() }
        .onFailure { Log.e(TAG, "Failed to initialize ONNX Runtime environment", it) }
        .getOrNull()
        ?.also { ortEnv = it }

  private fun createSessionOptions(): OrtSession.SessionOptions {
    val cores = Runtime.getRuntime().availableProcessors()
    val threads = if (modelManager.isLowRamDevice()) 2 else cores.coerceIn(2, 4)
    return OrtSession.SessionOptions().apply {
      setIntraOpNumThreads(threads)
      setInterOpNumThreads(1)
      setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
      setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
    }
  }

  private fun ensureVisionSession(env: OrtEnvironment): OrtSession? {
    visionSession?.let { return it }
    val file = modelManager.visionModelFile()
    if (!file.exists()) return null
    return createSessionOptions().use { opts ->
      runCatching { env.createSession(file.absolutePath, opts) }
        .onFailure { Log.e(TAG, "Failed to open vision ONNX session", it) }
        .getOrNull()
        ?.also { visionSession = it }
    }
  }

  private fun ensureTextSession(env: OrtEnvironment): OrtSession? {
    textSession?.let { return it }
    val file = modelManager.textModelFile()
    if (!file.exists()) return null
    return createSessionOptions().use { opts ->
      runCatching { env.createSession(file.absolutePath, opts) }
        .onFailure { Log.e(TAG, "Failed to open text ONNX session", it) }
        .getOrNull()
        ?.also { textSession = it }
    }
  }

  private fun ensureTokenizer(): ClipBpeTokenizer? {
    tokenizer?.let { return it }
    val vocab = modelManager.vocabFile()
    val merges = modelManager.mergesFile()
    if (!vocab.exists() || !merges.exists()) return null
    return runCatching { ClipBpeTokenizer.fromFiles(vocab, merges) }
      .onFailure { Log.e(TAG, "Failed to load CLIP BPE tokenizer", it) }
      .getOrNull()
      ?.also { tokenizer = it }
  }

  private fun extractFloatVector(result: OrtSession.Result, preferredName: String?): FloatArray? {
    val value =
      if (preferredName != null) {
        val opt = result.get(preferredName)
        if (opt.isPresent) opt.get() else if (result.size() > 0) result.get(0) else null
      } else {
        if (result.size() > 0) result.get(0) else null
      } ?: return null

    val tensor = value as? OnnxTensor ?: return null
    val buf = tensor.floatBuffer ?: return null
    val arr = FloatArray(buf.remaining())
    buf.get(arr)
    return if (arr.size > embeddingDim) arr.copyOf(embeddingDim) else arr
  }

  companion object {
    private const val TAG = "OnnxClipEmbeddingEngine"
    private const val MAX_QUERY_CACHE_SIZE = 32
  }
}
