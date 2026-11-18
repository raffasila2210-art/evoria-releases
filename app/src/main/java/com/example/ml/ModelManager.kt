package com.example.ml

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

@Serializable
data class VerifiedModelFileRecord(
  val fileName: String,
  val remotePath: String,
  val sha256: String,
  val byteSize: Long,
)

@Serializable
data class LocalModelManifest(
  val modelId: String,
  val modelVersion: String,
  val embeddingDim: Int,
  val inputSize: Int,
  val license: String,
  val verifiedAtMillis: Long,
  val files: List<VerifiedModelFileRecord>,
)

sealed interface ModelState {
  data object NotDownloaded : ModelState

  data class Downloading(
    val progress: Float,
    val fileLabel: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
  ) : ModelState

  data class Verifying(val fileLabel: String) : ModelState

  data class Ready(val manifest: LocalModelManifest) : ModelState

  data class UnsupportedDevice(val reason: String) : ModelState

  data class Error(val message: String) : ModelState
}

/**
 * Manages EVORIA Vision Search Lite on-device model artifacts (`Xenova/clip-vit-base-patch32`, MIT):
 * - Checks ABI/RAM/storage suitability for Android 11+ (API 30+)
 * - Fetches authoritative HuggingFace file metadata (SHA-256 LFS OID / ETag + exact byte size)
 * - Streams to `.part` temp files, verifies SHA-256 digest atomically, and persists `manifest.json`
 * - Supports cancellation and model cleanup to reclaim device storage
 */
class ModelManager(context: Context) {

  private val appContext = context.applicationContext
  private val modelDir = File(appContext.filesDir, MODEL_DIR_NAME)
  private val manifestFile = File(modelDir, MANIFEST_FILE_NAME)
  private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(25, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()
  }

  private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
  val state: StateFlow<ModelState> = _state.asStateFlow()

  private val cancelRequested = AtomicBoolean(false)
  @Volatile private var activeDownloadJob: Job? = null

  init {
    refreshStateSync()
  }

  fun modelVersion(): String = MODEL_VERSION

  fun visionModelFile(): File = File(modelDir, FILE_VISION_ONNX)

  fun textModelFile(): File = File(modelDir, FILE_TEXT_ONNX)

  fun vocabFile(): File = File(modelDir, FILE_VOCAB_JSON)

  fun mergesFile(): File = File(modelDir, FILE_MERGES_TXT)

  fun isReady(): Boolean = _state.value is ModelState.Ready

  fun isLowRamDevice(): Boolean {
    val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return am.isLowRamDevice
  }

  fun checkDeviceSupport(): String? {
    val abis = Build.SUPPORTED_ABIS.orEmpty().map { it.lowercase() }
    val supported = abis.any { it in SUPPORTED_ABIS }
    if (!supported) {
      return "Arsitektur CPU perangkat (${abis.joinToString()}) belum didukung runtime ONNX."
    }
    return null
  }

  fun refreshStateSync(): ModelState {
    val unsupported = checkDeviceSupport()
    if (unsupported != null) {
      return ModelState.UnsupportedDevice(unsupported).also { _state.value = it }
    }
    val manifest = readAndValidateManifestSync()
    val next =
      if (manifest != null) ModelState.Ready(manifest)
      else ModelState.NotDownloaded
    _state.value = next
    return next
  }

  suspend fun ensureReady(): ModelState = withContext(Dispatchers.IO) {
    refreshStateSync()
  }

  fun attachDownloadJob(job: Job) {
    activeDownloadJob = job
  }

  fun cancelDownload() {
    cancelRequested.set(true)
    activeDownloadJob?.cancel()
    activeDownloadJob = null
    if (_state.value is ModelState.Downloading || _state.value is ModelState.Verifying) {
      _state.value = ModelState.NotDownloaded
    }
  }

  suspend fun downloadAndVerifyModels(): Result<LocalModelManifest> = withContext(Dispatchers.IO) {
    val unsupported = checkDeviceSupport()
    if (unsupported != null) {
      _state.value = ModelState.UnsupportedDevice(unsupported)
      return@withContext Result.failure(IllegalStateException(unsupported))
    }

    // Return fast if valid manifest & files already exist
    readAndValidateManifestSync()?.let { existing ->
      _state.value = ModelState.Ready(existing)
      return@withContext Result.success(existing)
    }

    cancelRequested.set(false)
    if (!modelDir.exists()) {
      modelDir.mkdirs()
    }

    val freeBytes = availableInternalStorageBytes()
    if (freeBytes < MIN_REQUIRED_FREE_BYTES) {
      val freeMb = freeBytes / (1024L * 1024L)
      val msg = "Ruang penyimpanan tidak mencukupi (${freeMb} MB tersedia, butuh minimal 240 MB)."
      _state.value = ModelState.Error(msg)
      return@withContext Result.failure(IllegalStateException(msg))
    }

    return@withContext try {
      // Query Hugging Face repository tree API for authoritative file sizes & LFS SHA-256 digests
      val rootMeta = fetchTreeMetadata(HF_API_TREE_ROOT)
      val onnxMeta = fetchTreeMetadata(HF_API_TREE_ONNX)
      val remoteMetadataByPath = rootMeta + onnxMeta

      val specs = ARTIFACT_SPECS
      val expectedTotalBytes =
        specs.sumOf { spec ->
          remoteMetadataByPath[spec.remotePath]?.byteSize?.takeIf { it > 0L } ?: spec.approxBytes
        }.coerceAtLeast(1L)

      var completedBytesSoFar = 0L
      val verifiedRecords = mutableListOf<VerifiedModelFileRecord>()

      for (spec in specs) {
        if (cancelRequested.get()) throw CancellationException("Unduhan dibatalkan.")

        val meta = remoteMetadataByPath[spec.remotePath]
        val expectedSha = meta?.sha256
        val targetFile = File(modelDir, spec.localName)
        val tempFile = File(modelDir, "${spec.localName}.part")

        val fileExpectedBytes = meta?.byteSize?.takeIf { it > 0L } ?: spec.approxBytes

        // Reuse already-verified file if digest matches
        if (targetFile.exists() && expectedSha != null && computeSha256Hex(targetFile).equals(expectedSha, true)) {
          completedBytesSoFar += targetFile.length()
          verifiedRecords.add(
            VerifiedModelFileRecord(
              fileName = spec.localName,
              remotePath = spec.remotePath,
              sha256 = expectedSha.lowercase(),
              byteSize = targetFile.length(),
            )
          )
          _state.value =
            ModelState.Downloading(
              progress = (completedBytesSoFar.toFloat() / expectedTotalBytes.toFloat()).coerceIn(0f, 0.98f),
              fileLabel = spec.displayLabel,
              downloadedBytes = completedBytesSoFar,
              totalBytes = expectedTotalBytes,
            )
          continue
        }

        if (tempFile.exists()) tempFile.delete()

        val downloadUrl = "$HF_RESOLVE_BASE/${spec.remotePath}"
        val request = Request.Builder().url(downloadUrl).get().build()
        var headerLinkedSha: String? = null
        var actualSha = ""

        httpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            throw IllegalStateException("Gagal mengunduh ${spec.displayLabel} (HTTP ${response.code}).")
          }
          headerLinkedSha = normalizeHeaderSha256(response.header("X-Linked-Etag") ?: response.header("ETag"))
          val body = response.body ?: throw IllegalStateException("Respons unduhan ${spec.displayLabel} kosong.")
          val contentLen = body.contentLength().takeIf { it > 0L } ?: fileExpectedBytes
          val digest = MessageDigest.getInstance("SHA-256")
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          var fileRead = 0L

          body.byteStream().use { input ->
            FileOutputStream(tempFile).use { output ->
              while (true) {
                if (cancelRequested.get()) throw CancellationException("Unduhan dibatalkan.")
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                fileRead += read

                val overallRead = (completedBytesSoFar + fileRead).coerceAtMost(expectedTotalBytes)
                val progress =
                  (overallRead.toFloat() / maxOf(expectedTotalBytes, completedBytesSoFar + contentLen).toFloat())
                    .coerceIn(0f, 0.99f)
                _state.value =
                  ModelState.Downloading(
                    progress = progress,
                    fileLabel = spec.displayLabel,
                    downloadedBytes = overallRead,
                    totalBytes = expectedTotalBytes,
                  )
              }
              output.flush()
            }
          }
          actualSha = digest.digest().joinToString("") { "%02x".format(it) }
          completedBytesSoFar += fileRead
        }

        _state.value = ModelState.Verifying(spec.displayLabel)

        val authoritativeSha = expectedSha ?: headerLinkedSha
        if (!authoritativeSha.isNullOrBlank() && !actualSha.equals(authoritativeSha, ignoreCase = true)) {
          tempFile.delete()
          throw SecurityException("Verifikasi SHA-256 gagal untuk ${spec.displayLabel}.")
        }
        if (meta != null && meta.byteSize > 0L && tempFile.length() != meta.byteSize) {
          tempFile.delete()
          throw IllegalStateException("Ukuran file ${spec.displayLabel} tidak lengkap (${tempFile.length()} vs ${meta.byteSize} byte).")
        }
        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
          tempFile.copyTo(targetFile, overwrite = true)
          tempFile.delete()
        }

        verifiedRecords.add(
          VerifiedModelFileRecord(
            fileName = spec.localName,
            remotePath = spec.remotePath,
            sha256 = (authoritativeSha ?: actualSha).lowercase(),
            byteSize = targetFile.length(),
          )
        )
      }

      val manifest =
        LocalModelManifest(
          modelId = MODEL_ID,
          modelVersion = MODEL_VERSION,
          embeddingDim = EMBEDDING_DIM,
          inputSize = INPUT_SIZE,
          license = MODEL_LICENSE,
          verifiedAtMillis = System.currentTimeMillis(),
          files = verifiedRecords,
        )
      manifestFile.writeText(json.encodeToString(manifest), Charsets.UTF_8)
      _state.value = ModelState.Ready(manifest)
      Result.success(manifest)
    } catch (e: CancellationException) {
      cleanupPartialFiles()
      _state.value = ModelState.NotDownloaded
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Model download/verification failed", e)
      cleanupPartialFiles()
      val msg = e.localizedMessage ?: "Gagal mengunduh model AI lokal."
      _state.value = ModelState.Error(msg)
      Result.failure(e)
    }
  }

  suspend fun deleteModels(): Unit = withContext(Dispatchers.IO) {
    cancelDownload()
    if (modelDir.exists()) {
      modelDir.deleteRecursively()
    }
    _state.value = ModelState.NotDownloaded
  }

  fun installedModelSizeBytes(): Long {
    if (!modelDir.exists()) return 0L
    return modelDir.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".part") }.sumOf { it.length() }
  }

  private fun readAndValidateManifestSync(): LocalModelManifest? {
    if (!manifestFile.exists()) return null
    val manifest =
      runCatching { json.decodeFromString<LocalModelManifest>(manifestFile.readText(Charsets.UTF_8)) }.getOrNull()
        ?: return null
    if (manifest.modelVersion != MODEL_VERSION) return null
    if (manifest.files.size < ARTIFACT_SPECS.size) return null

    for (rec in manifest.files) {
      val f = File(modelDir, rec.fileName)
      if (!f.exists() || f.length() != rec.byteSize || f.length() == 0L) {
        return null
      }
      if (rec.sha256.length == 64) {
        val actual = computeSha256Hex(f)
        if (!actual.equals(rec.sha256, ignoreCase = true)) {
          Log.w(TAG, "Checksum mismatch on ${rec.fileName}; invalidating manifest.")
          return null
        }
      }
    }
    return manifest
  }

  private fun fetchTreeMetadata(apiUrl: String): Map<String, RemoteFileMeta> =
    runCatching {
      val req = Request.Builder().url(apiUrl).get().build()
      httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@runCatching emptyMap()
        val raw = resp.body?.string().orEmpty()
        if (raw.isBlank()) return@runCatching emptyMap()
        val arr = JSONArray(raw)
        val result = LinkedHashMap<String, RemoteFileMeta>()
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          if (obj.optString("type") != "file") continue
          val path = obj.optString("path").trim()
          if (path.isEmpty()) continue
          val lfs = obj.optJSONObject("lfs")
          val sha = lfs?.optString("oid")?.trim()?.takeIf { it.length == 64 }
          val size = lfs?.optLong("size", 0L)?.takeIf { it > 0L } ?: obj.optLong("size", 0L)
          result[path] = RemoteFileMeta(sha256 = sha, byteSize = size)
        }
        result
      }
    }.getOrDefault(emptyMap())

  private fun normalizeHeaderSha256(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim().trim('"').removePrefix("W/").trim('"')
    return cleaned.takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F' } }
  }

  private fun computeSha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
    file.inputStream().use { input ->
      while (true) {
        val n = input.read(buf)
        if (n == -1) break
        digest.update(buf, 0, n)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun cleanupPartialFiles() {
    modelDir.listFiles().orEmpty().filter { it.name.endsWith(".part") }.forEach { it.delete() }
  }

  private fun availableInternalStorageBytes(): Long {
    val path = appContext.filesDir
    val stat = StatFs(path.absolutePath)
    return stat.availableBytes
  }

  private data class RemoteFileMeta(
    val sha256: String?,
    val byteSize: Long,
  )

  private data class ArtifactSpec(
    val localName: String,
    val remotePath: String,
    val displayLabel: String,
    val approxBytes: Long,
  )

  companion object {
    private const val TAG = "ModelManager"
    const val MODEL_ID = "Xenova/clip-vit-base-patch32"
    const val MODEL_VERSION = "clip-vit-b32-int8-v1"
    const val MODEL_LICENSE = "MIT"
    const val EMBEDDING_DIM = 512
    const val INPUT_SIZE = 224

    private const val MODEL_DIR_NAME = "evoria_models/clip_vit_b32"
    private const val MANIFEST_FILE_NAME = "manifest.json"
    private const val FILE_VISION_ONNX = "vision_model_int8.onnx"
    private const val FILE_TEXT_ONNX = "text_model_quantized.onnx"
    private const val FILE_VOCAB_JSON = "vocab.json"
    private const val FILE_MERGES_TXT = "merges.txt"

    private const val HF_RESOLVE_BASE = "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main"
    private const val HF_API_TREE_ROOT = "https://huggingface.co/api/models/Xenova/clip-vit-base-patch32/tree/main"
    private const val HF_API_TREE_ONNX = "https://huggingface.co/api/models/Xenova/clip-vit-base-patch32/tree/main/onnx"

    private const val MIN_REQUIRED_FREE_BYTES = 240L * 1024L * 1024L
    private const val DEFAULT_BUFFER_SIZE = 32 * 1024

    private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    private val ARTIFACT_SPECS =
      listOf(
        ArtifactSpec(
          localName = FILE_VISION_ONNX,
          remotePath = "onnx/vision_model_int8.onnx",
          displayLabel = "Vision Encoder (INT8)",
          approxBytes = 88_600_000L,
        ),
        ArtifactSpec(
          localName = FILE_TEXT_ONNX,
          remotePath = "onnx/text_model_quantized.onnx",
          displayLabel = "Text Encoder (Quantized)",
          approxBytes = 64_500_000L,
        ),
        ArtifactSpec(
          localName = FILE_VOCAB_JSON,
          remotePath = "vocab.json",
          displayLabel = "Kosakata Tokenizer",
          approxBytes = 862_000L,
        ),
        ArtifactSpec(
          localName = FILE_MERGES_TXT,
          remotePath = "merges.txt",
          displayLabel = "Aturan BPE Tokenizer",
          approxBytes = 525_000L,
        ),
      )
  }
}
