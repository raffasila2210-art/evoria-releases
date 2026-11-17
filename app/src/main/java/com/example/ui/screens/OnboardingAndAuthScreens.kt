package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.R
import com.example.auth.onGoogleSignInClicked
import com.example.ui.components.EvoriaCard
import com.example.ui.components.EvoriaFormTextField
import com.example.ui.components.EvoriaSanctuaryBackdrop
import com.example.ui.components.rememberThumbnailRequest
import com.example.ui.theme.LocalEvoriaTokens
import kotlinx.coroutines.launch

private data class OnboardingPageData(
  val title: String,
  val body: String,
  val drawableRes: Int,
  val badge: String,
)

@Composable
fun OnboardingAndSignInScreen(
  onboardingAlreadyCompleted: Boolean,
  onCompleteOnboarding: () -> Unit,
  onAuthSuccess: () -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var authError by remember { mutableStateOf<String?>(null) }

  val pages = remember {
    listOf(
      OnboardingPageData(
        title = "A place you've been before.",
        body = "EVORIA adalah ruang tenang pribadi untuk dua orang—tempat foto, cerita, dan rasa rindu tersimpan hangat tanpa keramaian media sosial.",
        drawableRes = R.drawable.img_welcome_sanctuary_1794808522814,
        badge = "Ruang Berdua",
      ),
      OnboardingPageData(
        title = "Kendali Privasi di Tanganmu",
        body = "Setiap kenangan bersifat Pribadi secara default. Kamu yang memilih mana yang tetap disimpan sendiri dan mana yang dibagikan ke pasangan melalui EVORIA Share.",
        drawableRes = R.drawable.img_empty_memories_1794808523116,
        badge = "Local-First & Aman",
      ),
      OnboardingPageData(
        title = "Izin Transparan & Tanpa Paksaan",
        body = "Kami memakai Android Photo Picker (tanpa akses seluruh galeri) serta biometrik/PIN lokal opsional untuk menjaga jurnal, surat, dan kapsul waktu kalian.",
        drawableRes = R.drawable.img_time_capsule_seal_1794808539025,
        badge = "Siap Memulai",
      ),
    )
  }

  val pagerState = rememberPagerState(
    initialPage = if (onboardingAlreadyCompleted) pages.size else 0,
    pageCount = { pages.size + 1 },
  )

  EvoriaSanctuaryBackdrop {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 22.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          val logoReq = rememberThumbnailRequest(R.drawable.evoria_app_icon_uploaded, sizePx = 128)
          AsyncImage(
            model = logoReq,
            contentDescription = "Logo EVORIA",
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "EVORIA",
            style = MaterialTheme.typography.titleLarge,
            color = tokens.inkText,
          )
        }
        if (pagerState.currentPage < pages.size) {
          TextButton(
            onClick = {
              onCompleteOnboarding()
              scope.launch { pagerState.animateScrollToPage(pages.size) }
            },
            modifier = Modifier.testTag("skip_onboarding_button"),
          ) {
            Text("Lewati", color = tokens.mutedText)
          }
        }
      }

      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f).fillMaxWidth().widthIn(max = 540.dp),
      ) { page ->
        if (page < pages.size) {
          val item = pages[page]
          Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Surface(
              shape = RoundedCornerShape(28.dp),
              color = tokens.cardSurface,
              shadowElevation = 6.dp,
            ) {
              val heroReq = rememberThumbnailRequest(item.drawableRes, sizePx = 720)
              AsyncImage(
                model = heroReq,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth().height(290.dp),
                contentScale = ContentScale.Crop,
              )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Surface(
              shape = RoundedCornerShape(50),
              color = tokens.accentPeach.copy(alpha = 0.30f),
            ) {
              Text(
                text = item.badge,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.primaryTerracotta,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = item.title,
              style = MaterialTheme.typography.displayMedium,
              color = tokens.inkText,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = item.body,
              style = MaterialTheme.typography.bodyLarge,
              color = tokens.mutedText,
              textAlign = TextAlign.Center,
            )
          }
        } else {
          // Final page: Google Sign-In + Permission transparency
          Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            val emblemReq = rememberThumbnailRequest(R.drawable.evoria_app_icon_uploaded, sizePx = 220)
            AsyncImage(
              model = emblemReq,
              contentDescription = "Emblem EVORIA",
              modifier = Modifier.size(92.dp).clip(RoundedCornerShape(24.dp)),
              contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
              text = "Masuk ke Ruang Berdua",
              style = MaterialTheme.typography.headlineLarge,
              color = tokens.inkText,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Gunakan akun Google untuk menyinkronkan undangan pasangan, metadata kenangan bersama, dan cadangan akun secara aman.",
              style = MaterialTheme.typography.bodyMedium,
              color = tokens.mutedText,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            EvoriaCard {
              PermissionRow(Icons.Filled.PhotoLibrary, "Photo Picker Tanpa Izin Luas", "Hanya foto yang Anda pilih yang masuk ke EVORIA.")
              Spacer(modifier = Modifier.height(10.dp))
              PermissionRow(Icons.Filled.Lock, "Biometrik & PIN Lokal", "Kunci perangkat diproses oleh Android Keystore & BiometricPrompt.")
              Spacer(modifier = Modifier.height(10.dp))
              PermissionRow(Icons.Filled.Notifications, "Notifikasi Opsional", "Pengingat unggahan, undangan, dan kapsul waktu.")
            }
            Spacer(modifier = Modifier.height(18.dp))
            if (authError != null) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = tokens.roseError.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
              ) {
                Text(
                  text = authError.orEmpty(),
                  style = MaterialTheme.typography.bodySmall,
                  color = tokens.roseError,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }
            Button(
              onClick = {
                isLoading = true
                authError = null
                onCompleteOnboarding()
                onGoogleSignInClicked(
                  context = context,
                  credentialManager = credentialManager,
                  onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                  },
                  onAuthError = { msg ->
                    isLoading = false
                    authError = msg
                  },
                  scope = scope,
                  onAuthCancelled = {
                    isLoading = false
                    authError = "Masuk dibatalkan. Silakan pilih akun Google Anda untuk melanjutkan."
                  },
                )
              },
              enabled = !isLoading,
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
              modifier = Modifier.fillMaxWidth().height(54.dp).testTag("google_sign_in_button"),
            ) {
              if (isLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Menghubungkan akun Google...")
              } else {
                Text("Masuk dengan Google", style = MaterialTheme.typography.labelLarge)
              }
            }
          }
        }
      }

      // Page dots & Next CTA
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          repeat(pages.size + 1) { idx ->
            val active = pagerState.currentPage == idx
            Box(
              modifier =
                Modifier
                  .height(8.dp)
                  .width(if (active) 24.dp else 8.dp)
                  .clip(CircleShape)
                  .background(if (active) tokens.primaryTerracotta else tokens.outlineSoft)
            )
          }
        }
        if (pagerState.currentPage < pages.size) {
          Button(
            onClick = {
              val next = pagerState.currentPage + 1
              if (next == pages.size) onCompleteOnboarding()
              scope.launch { pagerState.animateScrollToPage(next) }
            },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
            modifier = Modifier.testTag("onboarding_next_button"),
          ) {
            Text(if (pagerState.currentPage == pages.lastIndex) "Mulai Masuk" else "Lanjut")
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
  val tokens = LocalEvoriaTokens.current
  Row(verticalAlignment = Alignment.Top) {
    Icon(icon, contentDescription = null, tint = tokens.primaryTerracotta, modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.inkText)
      Text(desc, style = MaterialTheme.typography.bodySmall, color = tokens.mutedText)
    }
  }
}

@Composable
fun FirstProfileSetupDialogContent(
  initialName: String,
  onSaveProfile: (displayName: String, nickname: String) -> Unit,
  onOpenConnectPartner: () -> Unit,
  onSkip: () -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  var name by remember { mutableStateOf(initialName) }
  var nickname by remember { mutableStateOf("") }

  EvoriaSanctuaryBackdrop {
    Box(
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tokens.sageSuccess, modifier = Modifier.size(44.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          "Selamat Datang di EVORIA",
          style = MaterialTheme.typography.displayMedium,
          color = tokens.inkText,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          "Siapkan nama panggilan kalian sebelum memasuki beranda kenangan.",
          style = MaterialTheme.typography.bodyMedium,
          color = tokens.mutedText,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        EvoriaCard(elevated = true) {
          EvoriaFormTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nama Tampilan Anda",
            singleLine = true,
            modifier = Modifier.testTag("profile_setup_name_input"),
          )
          Spacer(modifier = Modifier.height(12.dp))
          EvoriaFormTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = "Panggilan Sayang untuk Pasangan (Opsional)",
            singleLine = true,
          )
          Spacer(modifier = Modifier.height(18.dp))
          Button(
            onClick = {
              onSaveProfile(name.ifBlank { initialName }, nickname)
              onOpenConnectPartner()
            },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.primaryTerracotta),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_and_connect_button"),
          ) {
            Text("Simpan & Hubungkan Pasangan")
          }
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = {
              onSaveProfile(name.ifBlank { initialName }, nickname)
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_and_enter_home_button"),
          ) {
            Text("Simpan & Masuk ke Beranda", color = tokens.inkText)
          }
        }
      }
    }
  }
}
