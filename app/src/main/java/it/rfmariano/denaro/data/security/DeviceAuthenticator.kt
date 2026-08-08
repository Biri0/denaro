package it.rfmariano.denaro.data.security

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface DeviceAuthenticationResult {
    data object Success : DeviceAuthenticationResult
    data object Canceled : DeviceAuthenticationResult
    data object NotConfigured : DeviceAuthenticationResult
    data object Error : DeviceAuthenticationResult
}

interface DeviceAuthenticator {
    suspend fun authenticate(
        title: String,
        subtitle: String,
    ): DeviceAuthenticationResult

    fun openSecuritySettings()
}

class AndroidDeviceAuthenticator(
    private val activity: FragmentActivity,
) : DeviceAuthenticator {
    override suspend fun authenticate(
        title: String,
        subtitle: String,
    ): DeviceAuthenticationResult {
        val availability = BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)
        if (availability == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            return DeviceAuthenticationResult.NotConfigured
        }
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            return DeviceAuthenticationResult.Error
        }

        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(DeviceAuthenticationResult.Success)
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        if (!continuation.isActive) return
                        continuation.resume(
                            when (errorCode) {
                                BiometricPrompt.ERROR_CANCELED,
                                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                BiometricPrompt.ERROR_USER_CANCELED,
                                    -> DeviceAuthenticationResult.Canceled

                                BiometricPrompt.ERROR_NO_BIOMETRICS,
                                BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                                    -> DeviceAuthenticationResult.NotConfigured

                                else -> DeviceAuthenticationResult.Error
                            },
                        )
                    }
                },
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build()
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(promptInfo)
        }
    }

    override fun openSecuritySettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTHENTICATORS)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        activity.startActivity(intent)
    }

    private companion object {
        const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
