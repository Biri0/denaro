package it.rfmariano.denaro.data.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SecurityPreferences(
    val appLockEnabled: Boolean = false,
    val screenSecurityEnabled: Boolean = true,
)

class SecurityPreferencesRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readPreferences())

    val state: StateFlow<SecurityPreferences> = _state.asStateFlow()

    fun setAppLockEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()
        _state.value = _state.value.copy(appLockEnabled = value)
    }

    fun setScreenSecurityEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_SCREEN_SECURITY_ENABLED, value).apply()
        _state.value = _state.value.copy(screenSecurityEnabled = value)
    }

    private fun readPreferences() = SecurityPreferences(
        appLockEnabled = preferences.getBoolean(KEY_APP_LOCK_ENABLED, false),
        screenSecurityEnabled = preferences.getBoolean(KEY_SCREEN_SECURITY_ENABLED, true),
    )

    companion object {
        const val PREFERENCES_NAME = "denaro_security_preferences"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_SCREEN_SECURITY_ENABLED = "screen_security_enabled"
    }
}

class ProcessUnlockSession(appLockEnabledAtProcessStart: Boolean) {
    private val _unlocked = MutableStateFlow(!appLockEnabledAtProcessStart)
    private var backgroundedAtElapsedRealtimeMillis: Long? = null

    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun unlock() {
        _unlocked.value = true
    }

    fun onAppBackgrounded(elapsedRealtimeMillis: Long) {
        backgroundedAtElapsedRealtimeMillis = elapsedRealtimeMillis
    }

    fun onAppForegrounded(
        elapsedRealtimeMillis: Long,
        appLockEnabled: Boolean,
    ) {
        val backgroundedAt = backgroundedAtElapsedRealtimeMillis
        backgroundedAtElapsedRealtimeMillis = null
        if (
            appLockEnabled &&
            backgroundedAt != null &&
            elapsedRealtimeMillis - backgroundedAt >= BACKGROUND_LOCK_TIMEOUT_MILLIS
        ) {
            _unlocked.value = false
        }
    }

    companion object {
        const val BACKGROUND_LOCK_TIMEOUT_MILLIS = 60_000L
    }
}
