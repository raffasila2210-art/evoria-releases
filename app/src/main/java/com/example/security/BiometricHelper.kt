package com.example.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

data class BiometricCapabilities(
  val canUseBiometric: Boolean,
  val hasFingerprintHardware: Boolean,
  val hasFaceHardware: Boolean,
  val statusDescription: String,
)

object BiometricHelper {

  fun inspectCapabilities(context: Context): BiometricCapabilities {
    val manager = BiometricManager.from(context)
    val strongOrWeak =
      manager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.BIOMETRIC_WEAK
      )
    val pm = context.packageManager
    val hasFp = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    val hasFace =
      android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
        pm.hasSystemFeature(PackageManager.FEATURE_FACE)

    val statusDesc =
      when (strongOrWeak) {
        BiometricManager.BIOMETRIC_SUCCESS -> "Biometrik perangkat siap digunakan."
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Perangkat tidak memiliki sensor biometrik."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Sensor biometrik sedang tidak tersedia."
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
          "Belum ada sidik jari atau wajah yang terdaftar di setelan perangkat."
        else -> "Gunakan PIN 6 digit EVORIA sebagai pengaman utama."
      }

    return BiometricCapabilities(
      canUseBiometric = strongOrWeak == BiometricManager.BIOMETRIC_SUCCESS,
      hasFingerprintHardware = hasFp,
      hasFaceHardware = hasFace,
      statusDescription = statusDesc,
    )
  }

  fun authenticate(
    activity: FragmentActivity,
    title: String = "Buka Kunci EVORIA",
    subtitle: String = "Gunakan sidik jari atau pengenalan wajah Anda",
    negativeButtonText: String = "Gunakan PIN",
    onSuccess: () -> Unit,
    onFallbackPin: () -> Unit,
    onError: (String) -> Unit,
  ) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt =
      BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            if (
              errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            ) {
              onFallbackPin()
            } else {
              onError(errString.toString())
            }
          }

          override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            onError("Biometrik tidak cocok. Silakan coba lagi atau gunakan PIN.")
          }
        },
      )

    val promptInfo =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(negativeButtonText)
        .setAllowedAuthenticators(
          BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .build()

    prompt.authenticate(promptInfo)
  }
}
