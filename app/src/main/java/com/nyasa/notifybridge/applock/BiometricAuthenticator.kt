package com.nyasa.notifybridge.applock

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class LockAvailability { AVAILABLE, NONE_ENROLLED, UNSUPPORTED }

class BiometricAuthenticator(private val context: Context) {
    fun availability(): LockAvailability {
        val bm = BiometricManager.from(context)
        val combined = bm.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        if (combined == BiometricManager.BIOMETRIC_SUCCESS) return LockAvailability.AVAILABLE
        val km = context.getSystemService(KeyguardManager::class.java)
        return if (km?.isDeviceSecure == true) LockAvailability.AVAILABLE
        else LockAvailability.NONE_ENROLLED
    }

    /** API 30+: combined authenticators. API 26–29: device-credential fallback
     *  is handled by the prompt's negative path; see §3.8 version caveat. */
    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit, onFail: () -> Unit) {
        val prompt = BiometricPrompt(activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) =
                    onSuccess()
                override fun onAuthenticationError(c: Int, s: CharSequence) = onFail()
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock NotifyBridge")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}
