package com.example.ml

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

data class ClipTokenizedInput(
  val inputIds: LongArray,
  val attentionMask: LongArray,
  val activeLength: Int,
)

/**
 * Pure-Kotlin OpenAI CLIP Byte-Level BPE tokenizer compatible with Xenova/clip-vit-base-patch32
 * (`vocab.json` + `merges.txt`, vocab size 49,408, BOS=49406, EOS=49407, maxLength=77).
 */
class ClipBpeTokenizer private constructor(
  private val encoder: Map<String, Int>,
  private val bpeRanks: Map<Pair<String, String>, Int>,
  private val bosTokenId: Int,
  private val eosTokenId: Int,
  private val maxLength: Int = DEFAULT_MAX_LENGTH,
) {

  private val byteEncoder: Map<Int, Char> = buildBytesToUnicodeMap()
  private val cache = ConcurrentHashMap<String, List<String>>()

  // Matches OpenAI CLIP tokenizer pattern cleanly in Java/Kotlin regex
  private val tokenPattern =
    Regex("<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+")

  fun encode(text: String): ClipTokenizedInput {
    val cleaned =
      text
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

    val bpeIds = mutableListOf<Int>()
    bpeIds.add(bosTokenId)

    if (cleaned.isNotEmpty()) {
      val matches = tokenPattern.findAll(cleaned)
      for (match in matches) {
        val word = match.value
        if (word == "<|startoftext|>" || word == "<|endoftext|>") continue
        val utf8Bytes = word.toByteArray(Charsets.UTF_8)
        val encodedWord = buildString(utf8Bytes.size) {
          for (b in utf8Bytes) {
            append(byteEncoder[b.toInt() and 0xFF] ?: '?')
          }
        }
        val bpeTokens = bpe(encodedWord)
        for (tok in bpeTokens) {
          encoder[tok]?.let { bpeIds.add(it) }
          if (bpeIds.size >= maxLength - 1) break
        }
        if (bpeIds.size >= maxLength - 1) break
      }
    }

    if (bpeIds.size >= maxLength) {
      while (bpeIds.size > maxLength - 1) {
        bpeIds.removeAt(bpeIds.lastIndex)
      }
    }
    bpeIds.add(eosTokenId)

    val activeLen = bpeIds.size
    val inputIds = LongArray(maxLength) { idx ->
      if (idx < activeLen) bpeIds[idx].toLong() else eosTokenId.toLong()
    }
    val attentionMask = LongArray(maxLength) { idx ->
      if (idx < activeLen) 1L else 0L
    }
    return ClipTokenizedInput(
      inputIds = inputIds,
      attentionMask = attentionMask,
      activeLength = activeLen,
    )
  }

  private fun bpe(token: String): List<String> {
    cache[token]?.let { return it }
    if (token.isEmpty()) return emptyList()
    if (token.length == 1) {
      val single = listOf("$token</w>")
      cache[token] = single
      return single
    }

    var word =
      MutableList(token.length) { idx ->
        if (idx == token.lastIndex) "${token[idx]}</w>" else token[idx].toString()
      }

    var pairs = getPairs(word)
    if (pairs.isEmpty()) {
      cache[token] = word
      return word
    }

    while (true) {
      val bigram = pairs.minByOrNull { bpeRanks[it] ?: Int.MAX_VALUE } ?: break
      if (bigram !in bpeRanks) break
      val first = bigram.first
      val second = bigram.second
      val nextWord = mutableListOf<String>()
      var i = 0
      while (i < word.size) {
        val j = indexOfFrom(word, first, i)
        if (j == -1) {
          nextWord.addAll(word.subList(i, word.size))
          break
        }
        if (j > i) {
          nextWord.addAll(word.subList(i, j))
        }
        if (j < word.lastIndex && word[j + 1] == second) {
          nextWord.add(first + second)
          i = j + 2
        } else {
          nextWord.add(word[j])
          i = j + 1
        }
      }
      word = nextWord
      if (word.size == 1) break
      pairs = getPairs(word)
    }

    cache[token] = word
    return word
  }

  private fun indexOfFrom(list: List<String>, target: String, startIdx: Int): Int {
    for (idx in startIdx until list.size) {
      if (list[idx] == target) return idx
    }
    return -1
  }

  private fun getPairs(word: List<String>): Set<Pair<String, String>> {
    if (word.size < 2) return emptySet()
    val set = LinkedHashSet<Pair<String, String>>(word.size)
    for (i in 0 until word.lastIndex) {
      set.add(word[i] to word[i + 1])
    }
    return set
  }

  companion object {
    const val DEFAULT_MAX_LENGTH = 77
    const val DEFAULT_BOS_ID = 49406
    const val DEFAULT_EOS_ID = 49407

    fun fromFiles(vocabFile: File, mergesFile: File, maxLength: Int = DEFAULT_MAX_LENGTH): ClipBpeTokenizer {
      val vocabJson = JSONObject(vocabFile.readText(Charsets.UTF_8))
      val encoder = HashMap<String, Int>(vocabJson.length())
      val keys = vocabJson.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        encoder[k] = vocabJson.getInt(k)
      }

      val mergesLines =
        mergesFile
          .readLines(Charsets.UTF_8)
          .drop(1) // skip "#version: 0.2" header
          .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val parts = trimmed.split(" ")
            if (parts.size == 2) parts[0] to parts[1] else null
          }
          .take(49152 - 256 - 2)

      val bpeRanks = HashMap<Pair<String, String>, Int>(mergesLines.size)
      mergesLines.forEachIndexed { idx, pair -> bpeRanks[pair] = idx }

      val bos = encoder["<|startoftext|>"] ?: DEFAULT_BOS_ID
      val eos = encoder["<|endoftext|>"] ?: DEFAULT_EOS_ID
      return ClipBpeTokenizer(
        encoder = encoder,
        bpeRanks = bpeRanks,
        bosTokenId = bos,
        eosTokenId = eos,
        maxLength = maxLength,
      )
    }

    fun fromMapsForTest(
      encoder: Map<String, Int>,
      merges: List<Pair<String, String>>,
      bosTokenId: Int = DEFAULT_BOS_ID,
      eosTokenId: Int = DEFAULT_EOS_ID,
      maxLength: Int = DEFAULT_MAX_LENGTH,
    ): ClipBpeTokenizer {
      val bpeRanks = HashMap<Pair<String, String>, Int>(merges.size)
      merges.forEachIndexed { idx, pair -> bpeRanks[pair] = idx }
      return ClipBpeTokenizer(
        encoder = encoder,
        bpeRanks = bpeRanks,
        bosTokenId = bosTokenId,
        eosTokenId = eosTokenId,
        maxLength = maxLength,
      )
    }

    private fun buildBytesToUnicodeMap(): Map<Int, Char> {
      val bs = mutableListOf<Int>()
      for (b in '!'.code..'~'.code) bs.add(b)
      for (b in '¡'.code..'¬'.code) bs.add(b)
      for (b in '®'.code..'ÿ'.code) bs.add(b)
      val cs = bs.toMutableList()
      var n = 0
      for (b in 0 until 256) {
        if (b !in bs) {
          bs.add(b)
          cs.add(256 + n)
          n++
        }
      }
      val map = HashMap<Int, Char>(256)
      for (i in bs.indices) {
        map[bs[i]] = cs[i].toChar()
      }
      return map
    }
  }
}
