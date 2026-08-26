package it.rfmariano.denaro.data.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class LanguageOption(val languageTag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    ITALIAN("it"),
}

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: LanguageOption = LanguageOption.SYSTEM,
    val amountsVisible: Boolean = true,
    val showAllCurrencies: Boolean = false,
)

class AppPreferencesRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readPreferences())

    val state: StateFlow<AppPreferences> = _state.asStateFlow()

    fun setThemeMode(value: ThemeMode) = update {
        preferences.edit().putString(KEY_THEME, value.name).apply()
        copy(themeMode = value)
    }.also { applyTheme(value) }

    fun setLanguage(value: LanguageOption) {
        update {
            preferences.edit().putString(KEY_LANGUAGE, value.name).apply()
            copy(language = value)
        }
        applyLanguage(value)
    }

    fun setAmountsVisible(value: Boolean) {
        update {
            preferences.edit().putBoolean(KEY_AMOUNTS_VISIBLE, value).apply()
            copy(amountsVisible = value)
        }
    }

    fun setShowAllCurrencies(value: Boolean) {
        update {
            preferences.edit().putBoolean(KEY_SHOW_ALL_CURRENCIES, value).apply()
            copy(showAllCurrencies = value)
        }
    }

    fun applyStoredLanguage() {
        applyLanguage(_state.value.language)
    }

    fun applyStoredTheme() {
        applyTheme(_state.value.themeMode)
    }

    fun restoreGeneralPreferences(value: AppPreferences) {
        check(
            preferences.edit()
                .putString(KEY_THEME, value.themeMode.name)
                .putString(KEY_LANGUAGE, value.language.name)
                .putBoolean(KEY_AMOUNTS_VISIBLE, value.amountsVisible)
                .putBoolean(KEY_SHOW_ALL_CURRENCIES, value.showAllCurrencies)
                .commit(),
        ) { "Could not restore preferences" }
        _state.value = value
        applyTheme(value.themeMode)
        applyLanguage(value.language)
    }

    private fun update(block: AppPreferences.() -> AppPreferences) {
        _state.value = _state.value.block()
    }

    private fun readPreferences(): AppPreferences {
        preferences.edit().remove(KEY_LEGACY_CURRENCY).apply()
        val theme = preferences.getString(KEY_THEME, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val language = preferences.getString(KEY_LANGUAGE, null)
            ?.let { runCatching { LanguageOption.valueOf(it) }.getOrNull() }
            ?: LanguageOption.SYSTEM
        val amountsVisible = preferences.getBoolean(KEY_AMOUNTS_VISIBLE, true)
        val showAllCurrencies = preferences.getBoolean(KEY_SHOW_ALL_CURRENCIES, false)
        return AppPreferences(theme, language, amountsVisible, showAllCurrencies)
    }

    private fun applyLanguage(value: LanguageOption) {
        val locales = value.languageTag
            ?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    private fun applyTheme(value: ThemeMode) {
        val mode = when (value) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "denaro_preferences"
        const val KEY_THEME = "theme"
        const val KEY_LANGUAGE = "language"
        const val KEY_AMOUNTS_VISIBLE = "amounts_visible"
        const val KEY_SHOW_ALL_CURRENCIES = "show_all_currencies"
        const val KEY_LEGACY_CURRENCY = "default_currency"
    }
}
