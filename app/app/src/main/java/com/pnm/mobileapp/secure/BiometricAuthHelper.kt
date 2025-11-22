package com.pnm.mobileapp.secure

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper for biometric authentication before key generation
 */
object BiometricAuthHelper {
    /**
     * Check if biometric authentication is available
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Request biometric authentication
     * @return true if authentication successful, false otherwise
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Authenticate to generate secure key",
        subtitle: String = "Use your fingerprint or face to authenticate"
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    continuation.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    continuation.resume(false)
                }

                override fun onAuthenticationFailed() {
                    continuation.resume(false)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

