package com.example.data.local

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.evoriaDataStore: DataStore<Preferences> by preferencesDataStore(name = "evoria_prefs")

const val DEVELOPER_ADMIN_EMAIL = "musyawalraffa@gmail.com"
const val DEFAULT_DEV_DISPLAY_NAME = "RAFFA AULIA MUSYAWAL"
const val DEFAULT_DEV_ROLE_LABEL = "Pengembang"

fun canEditDeveloperSocials(email: String?): Boolean =
  email?.trim()?.equals(DEVELOPER_ADMIN_EMAIL, ignoreCase = true) == true

fun canEditAboutPage(email: String?): Boolean = canEditDeveloperSocials(email)

enum class ThemeMode(val labelId: String) {
  SYSTEM("Ikuti Sistem"),
  LIGHT("Warm Solitude (Terang)"),
  DARK("Midnight Longing (Gelap)"),
}

enum class EvoriaAccentPreset(
  val labelId: String,
  val lightPrimaryHex: Long,
  val lightPeachHex: Long,
  val darkPrimaryHex: Long,
  val darkPeachHex: Long,
) {
  TERRACOTTA("Senja Terakota", 0xFFA46B55L, 0xFFE9B99EL, 0xFFC58D73L, 0xFFE4B49AL),
  SAGE_MIST("Lumut Teduh", 0xFF537559L, 0xFFB8D4BCL, 0xFF81AA87L, 0xFFB4D6B9L),
  ROSEWOOD("Mawar Hangat", 0xFFA6546AL, 0xFFEAB7C4L, 0xFFD48399L, 0xFFF0C2CEL),
  OCEAN_DUSK("Biru Senja", 0xFF4A6B82L, 0xFFB3CEDEL, 0xFF7CA3BEL, 0xFFB7D3E8L),
}

enum class NavbarThemeStyle(val labelId: String, val descId: String) {
  FROST_AURORA("Kaca Senja", "Efek embun hangat dengan bias aksen tema"),
  CRYSTAL_CLEAR("Kristal Bening", "Kapsul jernih kontras tinggi dan kilau tepi tajam"),
  VELVET_NOIR("Beludru Gelap", "Pilar gelap pekat dengan pendaran emas hangat"),
  ROSE_QUARTZ("Embun Mawar", "Nuansa peach bercahaya dengan pembiasan prisma"),
}

enum class NavbarGlassIntensity(
  val labelId: String,
  val glowMultiplier: Float,
  val dispersionMultiplier: Float,
) {
  SUBTLE("Lembut", 0.65f, 0.55f),
  BALANCED("Seimbang", 1.0f, 1.0f),
  VIVID("Maksimal", 1.35f, 1.45f),
}

enum class LockMethod(val labelId: String) {
  PIN("PIN 6 Digit"),
  FINGERPRINT("Sidik Jari"),
  FACE("Face Unlock"),
}

enum class AutoLockTimeout(val seconds: Long, val labelId: String) {
  IMMEDIATE(0L, "Segera saat keluar"),
  SECONDS_30(30L, "Setelah 30 detik"),
  MINUTE_1(60L, "Setelah 1 menit"),
  MINUTES_5(300L, "Setelah 5 menit"),
}

data class SecuritySettings(
  val appLockEnabled: Boolean = false,
  val hasPinConfigured: Boolean = false,
  val preferredMethod: LockMethod = LockMethod.PIN,
  val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATE,
  val failedAttempts: Int = 0,
  val cooldownUntilMillis: Long = 0L,
  val lastBackgroundMillis: Long = 0L,
)

data class AppUserPrefs(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val accentPreset: EvoriaAccentPreset = EvoriaAccentPreset.TERRACOTTA,
  val customAccentHex: Long? = null,
  val navbarThemeStyle: NavbarThemeStyle = NavbarThemeStyle.FROST_AURORA,
  val navbarGlassIntensity: NavbarGlassIntensity = NavbarGlassIntensity.BALANCED,
  val reducedMotionOverride: Boolean = false,
  val lock60Fps: Boolean = false,
  val onboardingCompleted: Boolean = false,
  val aiSearchConsent: Boolean = false,
  val activeTabIndex: Int = 0,
  val lastViewedMemoryId: String? = null,
  val lastTrackId: String? = null,
  val lastTrackPositionMs: Long = 0L,
  val searchHistory: List<String> = emptyList(),
  val notificationInvites: Boolean = true,
  val notificationShares: Boolean = true,
  val notificationCapsules: Boolean = true,
  val notificationSecurity: Boolean = true,
  val quickOpenShowcaseOnLaunch: Boolean = false,
  val devInstagram: String = "",
  val devTiktok: String = "",
  val devGithub: String = "",
  val devEmail: String = "",
  val devWebsite: String = "",
  val devDisplayName: String = DEFAULT_DEV_DISPLAY_NAME,
  val devRoleLabel: String = DEFAULT_DEV_ROLE_LABEL,
  val devBackgroundUri: String? = null,
  val devBackgroundType: String = "",
  val devUseBackgroundAsPageBackdrop: Boolean = false,
  val devAvatarUri: String? = null,
  val profileBannerUri: String? = null,
  val profileBannerType: String = "",
)

sealed interface PinVerifyResult {
  data object Success : PinVerifyResult
  data class Invalid(val remainingBeforeCooldown: Int) : PinVerifyResult
  data class Cooldown(val remainingSeconds: Long) : PinVerifyResult
}

class SecurityPreferences(private val context: Context) {

  private val syncPrefs = context.getSharedPreferences("evoria_security_sync", Context.MODE_PRIVATE)

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_PRESET = stringPreferencesKey("accent_preset")
    val CUSTOM_ACCENT_HEX = longPreferencesKey("custom_accent_hex")
    val NAVBAR_THEME_STYLE = stringPreferencesKey("navbar_theme_style")
    val NAVBAR_GLASS_INTENSITY = stringPreferencesKey("navbar_glass_intensity")
    val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    val LOCK_60_FPS = booleanPreferencesKey("lock_60_fps")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val AI_SEARCH_CONSENT = booleanPreferencesKey("ai_search_consent")
    val ACTIVE_TAB = intPreferencesKey("active_tab")
    val LAST_MEMORY_ID = stringPreferencesKey("last_memory_id")
    val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
    val LAST_TRACK_POS = longPreferencesKey("last_track_pos")
    val SEARCH_HISTORY = stringPreferencesKey("search_history")

    val NOTIF_INVITES = booleanPreferencesKey("notif_invites")
    val NOTIF_SHARES = booleanPreferencesKey("notif_shares")
    val NOTIF_CAPSULES = booleanPreferencesKey("notif_capsules")
    val NOTIF_SECURITY = booleanPreferencesKey("notif_security")

    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val PREFERRED_LOCK_METHOD = stringPreferencesKey("preferred_lock_method")
    val AUTO_LOCK_TIMEOUT = stringPreferencesKey("auto_lock_timeout")
    val PIN_SALT = stringPreferencesKey("pin_salt")
    val PIN_HASH = stringPreferencesKey("pin_hash")
    val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
    val COOLDOWN_UNTIL = longPreferencesKey("cooldown_until")
    val LAST_BACKGROUND = longPreferencesKey("last_background")
    val QUICK_OPEN_SHOWCASE = booleanPreferencesKey("quick_open_showcase")
    val DEV_INSTAGRAM = stringPreferencesKey("dev_instagram")
    val DEV_TIKTOK = stringPreferencesKey("dev_tiktok")
    val DEV_GITHUB = stringPreferencesKey("dev_github")
    val DEV_EMAIL = stringPreferencesKey("dev_email")
    val DEV_WEBSITE = stringPreferencesKey("dev_website")
    val DEV_DISPLAY_NAME = stringPreferencesKey("dev_display_name")
    val DEV_ROLE_LABEL = stringPreferencesKey("dev_role_label")
    val DEV_BACKGROUND_URI = stringPreferencesKey("dev_background_uri")
    val DEV_BACKGROUND_TYPE = stringPreferencesKey("dev_background_type")
    val DEV_USE_BG_AS_PAGE_BACKDROP = booleanPreferencesKey("dev_use_bg_as_page_backdrop")
    val DEV_AVATAR_URI = stringPreferencesKey("dev_avatar_uri")
    val PROFILE_BANNER_URI = stringPreferencesKey("profile_banner_uri")
    val PROFILE_BANNER_TYPE = stringPreferencesKey("profile_banner_type")
  }

  fun readInitialLockSnapshotSync(): Pair<Boolean, Boolean> {
    val enabled = syncPrefs.getBoolean("app_lock_enabled_sync", false)
    val hasPin = syncPrefs.getBoolean("has_pin_sync", false)
    return enabled to hasPin
  }

  val securitySettingsFlow: Flow<SecuritySettings> =
    context.evoriaDataStore.data.map { prefs ->
      val bgFromStore = prefs[Keys.LAST_BACKGROUND] ?: 0L
      val bgFromSync = syncPrefs.getLong("last_background_sync", 0L)
      val pinInStore = !prefs[Keys.PIN_HASH].isNullOrBlank() && !prefs[Keys.PIN_SALT].isNullOrBlank()
      val hasPin = pinInStore || syncPrefs.getBoolean("has_pin_sync", false)
      val lockEnabled =
        prefs[Keys.APP_LOCK_ENABLED] ?: syncPrefs.getBoolean("app_lock_enabled_sync", false)
      SecuritySettings(
        appLockEnabled = lockEnabled && hasPin,
        hasPinConfigured = hasPin,
        preferredMethod =
          runCatching { LockMethod.valueOf(prefs[Keys.PREFERRED_LOCK_METHOD] ?: "PIN") }
            .getOrDefault(LockMethod.PIN),
        autoLockTimeout =
          runCatching { AutoLockTimeout.valueOf(prefs[Keys.AUTO_LOCK_TIMEOUT] ?: "IMMEDIATE") }
            .getOrDefault(AutoLockTimeout.IMMEDIATE),
        failedAttempts = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
        cooldownUntilMillis = prefs[Keys.COOLDOWN_UNTIL] ?: 0L,
        lastBackgroundMillis = maxOf(bgFromStore, bgFromSync),
      )
    }

  val userPrefsFlow: Flow<AppUserPrefs> =
    context.evoriaDataStore.data.map { prefs ->
      val historyRaw = prefs[Keys.SEARCH_HISTORY].orEmpty()
      val history =
        if (historyRaw.isBlank()) emptyList()
        else historyRaw.split("||").filter { it.isNotBlank() }.take(10)
      AppUserPrefs(
        themeMode =
          runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM),
        accentPreset =
          runCatching { EvoriaAccentPreset.valueOf(prefs[Keys.ACCENT_PRESET] ?: "TERRACOTTA") }
            .getOrDefault(EvoriaAccentPreset.TERRACOTTA),
        customAccentHex = prefs[Keys.CUSTOM_ACCENT_HEX]?.takeIf { it != 0L },
        navbarThemeStyle =
          runCatching { NavbarThemeStyle.valueOf(prefs[Keys.NAVBAR_THEME_STYLE] ?: "FROST_AURORA") }
            .getOrDefault(NavbarThemeStyle.FROST_AURORA),
        navbarGlassIntensity =
          runCatching { NavbarGlassIntensity.valueOf(prefs[Keys.NAVBAR_GLASS_INTENSITY] ?: "BALANCED") }
            .getOrDefault(NavbarGlassIntensity.BALANCED),
        reducedMotionOverride = prefs[Keys.REDUCED_MOTION] ?: false,
        lock60Fps = prefs[Keys.LOCK_60_FPS] ?: false,
        onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
        aiSearchConsent = prefs[Keys.AI_SEARCH_CONSENT] ?: false,
        activeTabIndex = (prefs[Keys.ACTIVE_TAB] ?: 0).coerceIn(0, 4),
        lastViewedMemoryId = prefs[Keys.LAST_MEMORY_ID]?.takeIf { it.isNotBlank() },
        lastTrackId = prefs[Keys.LAST_TRACK_ID]?.takeIf { it.isNotBlank() },
        lastTrackPositionMs = prefs[Keys.LAST_TRACK_POS] ?: 0L,
        searchHistory = history,
        notificationInvites = prefs[Keys.NOTIF_INVITES] ?: true,
        notificationShares = prefs[Keys.NOTIF_SHARES] ?: true,
        notificationCapsules = prefs[Keys.NOTIF_CAPSULES] ?: true,
        notificationSecurity = prefs[Keys.NOTIF_SECURITY] ?: true,
        quickOpenShowcaseOnLaunch = prefs[Keys.QUICK_OPEN_SHOWCASE] ?: false,
        devInstagram = prefs[Keys.DEV_INSTAGRAM].orEmpty(),
        devTiktok = prefs[Keys.DEV_TIKTOK].orEmpty(),
        devGithub = prefs[Keys.DEV_GITHUB].orEmpty(),
        devEmail = prefs[Keys.DEV_EMAIL].orEmpty(),
        devWebsite = prefs[Keys.DEV_WEBSITE].orEmpty(),
        devDisplayName = prefs[Keys.DEV_DISPLAY_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_DEV_DISPLAY_NAME,
        devRoleLabel = prefs[Keys.DEV_ROLE_LABEL]?.takeIf { it.isNotBlank() } ?: DEFAULT_DEV_ROLE_LABEL,
        devBackgroundUri = prefs[Keys.DEV_BACKGROUND_URI]?.takeIf { it.isNotBlank() },
        devBackgroundType = prefs[Keys.DEV_BACKGROUND_TYPE].orEmpty(),
        devUseBackgroundAsPageBackdrop = prefs[Keys.DEV_USE_BG_AS_PAGE_BACKDROP] ?: false,
        devAvatarUri = prefs[Keys.DEV_AVATAR_URI]?.takeIf { it.isNotBlank() },
        profileBannerUri = prefs[Keys.PROFILE_BANNER_URI]?.takeIf { it.isNotBlank() },
        profileBannerType = prefs[Keys.PROFILE_BANNER_TYPE].orEmpty(),
      )
    }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.evoriaDataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setAccentPreset(preset: EvoriaAccentPreset) {
    context.evoriaDataStore.edit {
      it[Keys.ACCENT_PRESET] = preset.name
      it.remove(Keys.CUSTOM_ACCENT_HEX)
    }
  }

  suspend fun setCustomAccentHex(colorHex: Long?) {
    context.evoriaDataStore.edit {
      if (colorHex == null || colorHex == 0L) {
        it.remove(Keys.CUSTOM_ACCENT_HEX)
      } else {
        it[Keys.CUSTOM_ACCENT_HEX] = colorHex
      }
    }
  }

  suspend fun setNavbarThemeStyle(style: NavbarThemeStyle) {
    context.evoriaDataStore.edit { it[Keys.NAVBAR_THEME_STYLE] = style.name }
  }

  suspend fun setNavbarGlassIntensity(intensity: NavbarGlassIntensity) {
    context.evoriaDataStore.edit { it[Keys.NAVBAR_GLASS_INTENSITY] = intensity.name }
  }

  suspend fun setReducedMotionOverride(enabled: Boolean) {
    context.evoriaDataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
  }

  suspend fun setLock60Fps(enabled: Boolean) {
    context.evoriaDataStore.edit { it[Keys.LOCK_60_FPS] = enabled }
  }

  suspend fun setOnboardingCompleted(completed: Boolean) {
    context.evoriaDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
  }

  suspend fun setQuickOpenShowcase(enabled: Boolean) {
    context.evoriaDataStore.edit { it[Keys.QUICK_OPEN_SHOWCASE] = enabled }
  }

  suspend fun updateDeveloperSocials(
    instagram: String,
    tiktok: String,
    github: String,
    email: String,
    website: String,
  ) {
    context.evoriaDataStore.edit {
      it[Keys.DEV_INSTAGRAM] = instagram.trim()
      it[Keys.DEV_TIKTOK] = tiktok.trim()
      it[Keys.DEV_GITHUB] = github.trim()
      it[Keys.DEV_EMAIL] = email.trim()
      it[Keys.DEV_WEBSITE] = website.trim()
    }
  }

  suspend fun updateDeveloperAboutInfo(
    displayName: String,
    roleLabel: String,
    useBackgroundAsPageBackdrop: Boolean,
  ) {
    context.evoriaDataStore.edit {
      it[Keys.DEV_DISPLAY_NAME] = displayName.trim().ifBlank { DEFAULT_DEV_DISPLAY_NAME }
      it[Keys.DEV_ROLE_LABEL] = roleLabel.trim().ifBlank { DEFAULT_DEV_ROLE_LABEL }
      it[Keys.DEV_USE_BG_AS_PAGE_BACKDROP] = useBackgroundAsPageBackdrop
    }
  }

  suspend fun setDeveloperBackground(uri: String?, mediaType: String) {
    context.evoriaDataStore.edit {
      it[Keys.DEV_BACKGROUND_URI] = uri.orEmpty()
      it[Keys.DEV_BACKGROUND_TYPE] = if (uri.isNullOrBlank()) "" else mediaType
    }
  }

  suspend fun clearDeveloperBackground() {
    context.evoriaDataStore.edit {
      it.remove(Keys.DEV_BACKGROUND_URI)
      it.remove(Keys.DEV_BACKGROUND_TYPE)
      it[Keys.DEV_USE_BG_AS_PAGE_BACKDROP] = false
    }
  }

  suspend fun setDeveloperAvatar(uri: String?) {
    context.evoriaDataStore.edit {
      if (uri.isNullOrBlank()) {
        it.remove(Keys.DEV_AVATAR_URI)
      } else {
        it[Keys.DEV_AVATAR_URI] = uri
      }
    }
  }

  suspend fun clearDeveloperAvatar() {
    context.evoriaDataStore.edit {
      it.remove(Keys.DEV_AVATAR_URI)
    }
  }

  suspend fun setProfileBanner(uri: String?, mediaType: String) {
    context.evoriaDataStore.edit {
      it[Keys.PROFILE_BANNER_URI] = uri.orEmpty()
      it[Keys.PROFILE_BANNER_TYPE] = if (uri.isNullOrBlank()) "" else mediaType
    }
  }

  suspend fun clearProfileBanner() {
    context.evoriaDataStore.edit {
      it.remove(Keys.PROFILE_BANNER_URI)
      it.remove(Keys.PROFILE_BANNER_TYPE)
    }
  }

  suspend fun setAiSearchConsent(consent: Boolean) {
    context.evoriaDataStore.edit { it[Keys.AI_SEARCH_CONSENT] = consent }
  }

  suspend fun setActiveTab(index: Int) {
    context.evoriaDataStore.edit { it[Keys.ACTIVE_TAB] = index.coerceIn(0, 4) }
  }

  suspend fun setLastViewedMemoryId(id: String?) {
    context.evoriaDataStore.edit { it[Keys.LAST_MEMORY_ID] = id.orEmpty() }
  }

  suspend fun savePlaybackState(trackId: String?, positionMs: Long) {
    context.evoriaDataStore.edit {
      it[Keys.LAST_TRACK_ID] = trackId.orEmpty()
      it[Keys.LAST_TRACK_POS] = positionMs.coerceAtLeast(0L)
    }
  }

  suspend fun addSearchHistory(query: String) {
    val clean = query.trim()
    if (clean.isEmpty()) return
    context.evoriaDataStore.edit { prefs ->
      val current =
        prefs[Keys.SEARCH_HISTORY].orEmpty().split("||").filter { it.isNotBlank() && it != clean }
      val updated = (listOf(clean) + current).take(8)
      prefs[Keys.SEARCH_HISTORY] = updated.joinToString("||")
    }
  }

  suspend fun clearSearchHistory() {
    context.evoriaDataStore.edit { it[Keys.SEARCH_HISTORY] = "" }
  }

  suspend fun updateNotificationPrefs(
    invites: Boolean,
    shares: Boolean,
    capsules: Boolean,
    security: Boolean,
  ) {
    context.evoriaDataStore.edit {
      it[Keys.NOTIF_INVITES] = invites
      it[Keys.NOTIF_SHARES] = shares
      it[Keys.NOTIF_CAPSULES] = capsules
      it[Keys.NOTIF_SECURITY] = security
    }
  }

  suspend fun setAppLockEnabled(enabled: Boolean) {
    syncPrefs.edit().putBoolean("app_lock_enabled_sync", enabled).commit()
    context.evoriaDataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
  }

  suspend fun setPreferredLockMethod(method: LockMethod) {
    context.evoriaDataStore.edit { it[Keys.PREFERRED_LOCK_METHOD] = method.name }
  }

  suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
    context.evoriaDataStore.edit { it[Keys.AUTO_LOCK_TIMEOUT] = timeout.name }
  }

  fun recordBackgroundTimestampSync(nowMillis: Long = System.currentTimeMillis()): Long {
    syncPrefs.edit().putLong("last_background_sync", nowMillis).commit()
    return nowMillis
  }

  suspend fun recordBackgroundTimestamp(nowMillis: Long = System.currentTimeMillis()) {
    syncPrefs.edit().putLong("last_background_sync", nowMillis).commit()
    context.evoriaDataStore.edit { it[Keys.LAST_BACKGROUND] = nowMillis }
  }

  suspend fun configurePin(pin: String) {
    require(pin.length == 6 && pin.all { it.isDigit() }) { "PIN harus terdiri dari 6 angka." }
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val hash = derivePinHash(pin, salt)
    syncPrefs.edit()
      .putBoolean("app_lock_enabled_sync", true)
      .putBoolean("has_pin_sync", true)
      .commit()
    context.evoriaDataStore.edit {
      it[Keys.PIN_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
      it[Keys.PIN_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
      it[Keys.FAILED_ATTEMPTS] = 0
      it[Keys.COOLDOWN_UNTIL] = 0L
      it[Keys.APP_LOCK_ENABLED] = true
    }
  }

  suspend fun clearPinAndDisableLock() {
    syncPrefs.edit()
      .putBoolean("app_lock_enabled_sync", false)
      .putBoolean("has_pin_sync", false)
      .commit()
    context.evoriaDataStore.edit {
      it.remove(Keys.PIN_SALT)
      it.remove(Keys.PIN_HASH)
      it[Keys.APP_LOCK_ENABLED] = false
      it[Keys.FAILED_ATTEMPTS] = 0
      it[Keys.COOLDOWN_UNTIL] = 0L
    }
  }

  suspend fun verifyPin(pin: String, nowMillis: Long = System.currentTimeMillis()): PinVerifyResult {
    val prefs = context.evoriaDataStore.data.first()
    val cooldownUntil = prefs[Keys.COOLDOWN_UNTIL] ?: 0L
    if (nowMillis < cooldownUntil) {
      val rem = ((cooldownUntil - nowMillis) / 1000L).coerceAtLeast(1L)
      return PinVerifyResult.Cooldown(rem)
    }

    val saltB64 = prefs[Keys.PIN_SALT] ?: return PinVerifyResult.Invalid(5)
    val hashB64 = prefs[Keys.PIN_HASH] ?: return PinVerifyResult.Invalid(5)
    val salt = Base64.decode(saltB64, Base64.NO_WRAP)
    val expectedHash = Base64.decode(hashB64, Base64.NO_WRAP)
    val actualHash = derivePinHash(pin, salt)

    return if (MessageDigest.isEqual(expectedHash, actualHash)) {
      context.evoriaDataStore.edit {
        it[Keys.FAILED_ATTEMPTS] = 0
        it[Keys.COOLDOWN_UNTIL] = 0L
      }
      PinVerifyResult.Success
    } else {
      val attempts = (prefs[Keys.FAILED_ATTEMPTS] ?: 0) + 1
      val cooldownSec =
        when {
          attempts >= 8 -> 120L
          attempts >= 5 -> 30L
          else -> 0L
        }
      context.evoriaDataStore.edit {
        it[Keys.FAILED_ATTEMPTS] = attempts
        it[Keys.COOLDOWN_UNTIL] = if (cooldownSec > 0L) nowMillis + cooldownSec * 1000L else 0L
      }
      if (cooldownSec > 0L) {
        PinVerifyResult.Cooldown(cooldownSec)
      } else {
        PinVerifyResult.Invalid(remainingBeforeCooldown = (5 - attempts).coerceAtLeast(0))
      }
    }
  }

  private fun derivePinHash(pin: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(pin.toCharArray(), salt, 65_536, 256)
    return try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }
}
