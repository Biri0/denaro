package it.rfmariano.denaro.data.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import it.rfmariano.denaro.data.finance.SupportedCurrencies
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
    val defaultCurrency: String = DEFAULT_CURRENCY,
    val amountsVisible: Boolean = true,
) {
    companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}

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

    fun setDefaultCurrency(value: String) {
        require(value in SupportedCurrencies) { "Unsupported currency" }
        update {
            preferences.edit().putString(KEY_CURRENCY, value).apply()
            copy(defaultCurrency = value)
        }
    }

    fun setAmountsVisible(value: Boolean) {
        update {
            preferences.edit().putBoolean(KEY_AMOUNTS_VISIBLE, value).apply()
            copy(amountsVisible = value)
        }
    }

    fun applyStoredLanguage() {
        applyLanguage(_state.value.language)
    }

    fun applyStoredTheme() {
        applyTheme(_state.value.themeMode)
    }

    private fun update(block: AppPreferences.() -> AppPreferences) {
        _state.value = _state.value.block()
    }

    private fun readPreferences(): AppPreferences {
        val theme = preferences.getString(KEY_THEME, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val language = preferences.getString(KEY_LANGUAGE, null)
            ?.let { runCatching { LanguageOption.valueOf(it) }.getOrNull() }
            ?: LanguageOption.SYSTEM
        val currency = preferences.getString(KEY_CURRENCY, null)
            ?.takeIf { it in SupportedCurrencies }
            ?: AppPreferences.DEFAULT_CURRENCY
        val amountsVisible = preferences.getBoolean(KEY_AMOUNTS_VISIBLE, true)
        return AppPreferences(theme, language, currency, amountsVisible)
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
        const val KEY_CURRENCY = "default_currency"
        const val KEY_AMOUNTS_VISIBLE = "amounts_visible"
    }
}
