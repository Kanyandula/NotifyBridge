package com.nyasa.notifybridge.applock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.UUID

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

    // androidx.biometric forbids BIOMETRIC_STRONG | DEVICE_CREDENTIAL below API 30
    // (throws IllegalArgumentException). Use BiometricPrompt on R+, fall back to
    // KeyguardManager.createConfirmDeviceCredentialIntent on API 26–29.
    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit, onFail: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptBiometric(activity, onSuccess, onFail)
        } else {
            promptDeviceCredential(activity, onSuccess, onFail)
        }
    }

    private fun promptBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFail: () -> Unit,
    ) {
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

    @Suppress("DEPRECATION") // createConfirmDeviceCredentialIntent is the documented <R fallback
    private fun promptDeviceCredential(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFail: () -> Unit,
    ) {
        val km = activity.getSystemService(KeyguardManager::class.java)
        val intent: Intent? = km?.takeIf { it.isDeviceSecure }
            ?.createConfirmDeviceCredentialIntent("Unlock NotifyBridge", null)
        if (intent == null) {
            onFail()
            return
        }
        val key = "applock-credential-" + UUID.randomUUID()
        var launcher: ActivityResultLauncher<Intent>? = null
        launcher = activity.activityResultRegistry.register(
            key,
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            launcher?.unregister()
            if (result.resultCode == Activity.RESULT_OK) onSuccess() else onFail()
        }
        launcher.launch(intent)
    }
}
