package com.example.ml

/**
 * Offline, zero-API Indonesian-to-English visual query translator and multi-prompt expander
 * tuned for CLIP ViT-B/32 visual retrieval.
 */
object IndonesianQueryTranslator {

  private val STOP_WORDS =
    setOf(
      "foto", "gambar", "potret", "video", "rekaman", "klip", "cari", "tampilkan",
      "yang", "di", "ke", "dari", "dan", "atau", "dengan", "untuk", "pada", "saat",
      "waktu", "ketika", "sedang", "lagi", "kita", "aku", "kamu", "kami", "mereka",
      "ini", "itu", "ada", "adalah", "suasana", "momen", "pemandangan", "sebuah",
    )

  // Phrase-level multi-word translations evaluated prior to single-word lookup
  private val PHRASE_MAP: List<Pair<String, String>> =
    listOf(
      "matahari terbenam" to "sunset",
      "matahari terbit" to "sunrise",
      "naik sepeda" to "riding a bicycle",
      "bersepeda" to "riding a bicycle",
      "bermain di taman" to "playing in a park",
      "main di taman" to "playing in a park",
      "ulang tahun" to "birthday cake celebration",
      "langit biru" to "blue sky",
      "malam hari" to "night scene",
      "tepi pantai" to "beach shore",
      "pinggir pantai" to "beach shore",
      "gunung berapi" to "volcano mountain",
      "air terjun" to "waterfall",
      "ruang tamu" to "living room",
      "meja makan" to "dining table",
      "jalan raya" to "street road",
      "kembang api" to "fireworks",
      "es krim" to "ice cream",
      "teh manis" to "iced tea",
      "kopi susu" to "iced latte coffee",
    )

  private val WORD_MAP: Map<String, String> =
    mapOf(
      // Hewan
      "kucing" to "cat",
      "anjing" to "dog",
      "burung" to "bird",
      "ikan" to "fish",
      "kuda" to "horse",
      "kelinci" to "rabbit",
      "kupu" to "butterfly",
      "hewan" to "animal",
      // Aktivitas & Kondisi
      "tidur" to "sleeping",
      "tertidur" to "sleeping",
      "bermain" to "playing",
      "main" to "playing",
      "berjalan" to "walking",
      "jalan" to "walking street",
      "berlari" to "running",
      "lari" to "running",
      "duduk" to "sitting",
      "berdiri" to "standing",
      "makan" to "eating food",
      "minum" to "drinking",
      "senyum" to "smiling",
      "tersenyum" to "smiling",
      "tertawa" to "laughing",
      "berenang" to "swimming",
      "memasak" to "cooking",
      "masak" to "cooking",
      "membaca" to "reading",
      "menyetir" to "driving",
      "mengendarai" to "riding",
      "naik" to "riding",
      "peluk" to "hugging",
      "berpelukan" to "couple hugging",
      // Alam & Cuaca
      "pantai" to "beach",
      "laut" to "ocean sea",
      "ombak" to "ocean waves",
      "pasir" to "sand",
      "pulau" to "island",
      "danau" to "lake",
      "sungai" to "river",
      "senja" to "sunset",
      "sore" to "golden hour afternoon",
      "pagi" to "morning light",
      "malam" to "night",
      "bintang" to "stars night sky",
      "bulan" to "moon",
      "matahari" to "sun",
      "langit" to "sky",
      "awan" to "clouds",
      "gunung" to "mountain",
      "bukit" to "hill",
      "hutan" to "forest",
      "pohon" to "tree",
      "bunga" to "flower",
      "mawar" to "rose flower",
      "taman" to "garden park",
      "rumput" to "green grass",
      "alam" to "nature landscape",
      "hujan" to "rain",
      "mendung" to "cloudy overcast",
      "salju" to "snow",
      // Kendaraan & Objek
      "mobil" to "car",
      "motor" to "motorcycle",
      "sepeda" to "bicycle",
      "kapal" to "boat ship",
      "perahu" to "boat",
      "kereta" to "train",
      "pesawat" to "airplane",
      "rumah" to "house",
      "gedung" to "building",
      "kota" to "city skyline",
      "jembatan" to "bridge",
      "jendela" to "window",
      "pintu" to "door",
      "lampu" to "lights",
      "buku" to "book",
      "laptop" to "laptop",
      "komputer" to "computer",
      "gitar" to "guitar",
      "piano" to "piano",
      "kamera" to "camera",
      "payung" to "umbrella",
      "sepatu" to "shoes",
      "baju" to "clothes",
      "topi" to "hat",
      "kacamata" to "glasses",
      // Makanan & Minuman
      "makanan" to "food dish",
      "kuliner" to "food cuisine",
      "kopi" to "coffee cup",
      "kafe" to "cafe coffee shop",
      "teh" to "tea",
      "roti" to "bread",
      "kue" to "cake pastry",
      "nasi" to "rice meal",
      "mie" to "noodles",
      "buah" to "fruit",
      "sarapan" to "breakfast",
      "restoran" to "restaurant",
      // Orang & Hubungan
      "orang" to "people",
      "manusia" to "person",
      "pria" to "man",
      "wanita" to "woman",
      "cewek" to "woman",
      "cowok" to "man",
      "anak" to "child",
      "bayi" to "baby",
      "keluarga" to "family",
      "teman" to "friends",
      "pasangan" to "couple",
      "berdua" to "couple two people",
      "bersama" to "together",
      "wajah" to "face portrait",
      "selfie" to "selfie portrait",
      "swafoto" to "selfie portrait",
      // Warna & Gaya
      "merah" to "red",
      "biru" to "blue",
      "hijau" to "green",
      "kuning" to "yellow",
      "hitam" to "black",
      "putih" to "white",
      "jingga" to "orange",
      "ungu" to "purple",
      "coklat" to "brown",
      "gelap" to "dark",
      "terang" to "bright",
      "cantik" to "beautiful",
      "indah" to "scenic beautiful",
      // Perayaan / Momen
      "ultah" to "birthday",
      "perayaan" to "celebration party",
      "pesta" to "party",
      "hadiah" to "gift present",
      "liburan" to "vacation travel",
      "wisata" to "travel sightseeing",
      "konser" to "concert stage",
    )

  /**
   * Produces up to 3 CLIP-friendly English/bilingual prompts for a user query.
   */
  fun buildVisualPrompts(query: String): List<String> {
    val cleanRaw = query.trim().lowercase().replace(Regex("\\s+"), " ")
    if (cleanRaw.isEmpty()) return emptyList()

    var working = cleanRaw
    val extractedPhrases = mutableListOf<String>()
    for ((indoPhrase, enPhrase) in PHRASE_MAP) {
      if (working.contains(indoPhrase)) {
        extractedPhrases.add(enPhrase)
        working = working.replace(indoPhrase, " ")
      }
    }

    val remainingTokens =
      working
        .split(Regex("[\\s,.;:!?/]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() && it !in STOP_WORDS }

    val translatedTokens =
      remainingTokens.map { token ->
        WORD_MAP[token] ?: token
      }

    val combinedEnglish =
      (translatedTokens + extractedPhrases)
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()

    val coreConcept = combinedEnglish.ifBlank { cleanRaw }
    val prompts = linkedSetOf<String>()
    prompts.add("a photo of $coreConcept")
    if (coreConcept.split(" ").size <= 4) {
      prompts.add(coreConcept)
    }
    if (combinedEnglish != cleanRaw && cleanRaw.length >= 3) {
      prompts.add("a photo of $cleanRaw")
    }
    return prompts.take(3)
  }
}
