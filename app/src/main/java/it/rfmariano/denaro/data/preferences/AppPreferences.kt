package it.rfmariano.denaro.data.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import it.rfmariano.denaro.data.export.DEFAULT_STATEMENT_COLUMNS
import it.rfmariano.denaro.data.export.StatementColumn
import it.rfmariano.denaro.data.export.StatementDateRange
import it.rfmariano.denaro.data.export.StatementLayout
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
    val statementAccountIds: Set<String>? = null,
    val statementDateRange: StatementDateRange = StatementDateRange.THIS_YEAR,
    val statementFromDate: String? = null,
    val statementToDate: String? = null,
    val statementColumns: Set<StatementColumn> = DEFAULT_STATEMENT_COLUMNS,
    val statementLayout: StatementLayout = StatementLayout.GROUPED,
    val statementAccountColumn: Boolean = true,
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

    fun setStatementAccountIds(value: Set<String>) {
        update {
            preferences.edit().putString(KEY_STATEMENT_ACCOUNT_IDS, value.joinToString(",")).apply()
            copy(statementAccountIds = value)
        }
    }

    fun setStatementDateRange(value: StatementDateRange) {
        update {
            preferences.edit().putString(KEY_STATEMENT_DATE_RANGE, value.name).apply()
            copy(statementDateRange = value)
        }
    }

    fun setStatementCustomDates(from: String?, to: String?) {
        update {
            preferences.edit()
                .putString(KEY_STATEMENT_FROM_DATE, from)
                .putString(KEY_STATEMENT_TO_DATE, to)
                .apply()
            copy(statementFromDate = from, statementToDate = to)
        }
    }

    fun setStatementColumns(value: Set<StatementColumn>) {
        update {
            preferences.edit()
                .putString(KEY_STATEMENT_COLUMNS, value.joinToString(",") { it.name })
                .apply()
            copy(statementColumns = value)
        }
    }

    fun setStatementLayout(value: StatementLayout) {
        update {
            preferences.edit().putString(KEY_STATEMENT_LAYOUT, value.name).apply()
            copy(statementLayout = value)
        }
    }

    fun setStatementAccountColumn(value: Boolean) {
        update {
            preferences.edit().putBoolean(KEY_STATEMENT_ACCOUNT_COLUMN, value).apply()
            copy(statementAccountColumn = value)
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
                .putString(KEY_STATEMENT_ACCOUNT_IDS, value.statementAccountIds?.joinToString(","))
                .putString(KEY_STATEMENT_DATE_RANGE, value.statementDateRange.name)
                .putString(KEY_STATEMENT_FROM_DATE, value.statementFromDate)
                .putString(KEY_STATEMENT_TO_DATE, value.statementToDate)
                .putString(
                    KEY_STATEMENT_COLUMNS,
                    value.statementColumns.joinToString(",") { it.name })
                .putString(KEY_STATEMENT_LAYOUT, value.statementLayout.name)
                .putBoolean(KEY_STATEMENT_ACCOUNT_COLUMN, value.statementAccountColumn)
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
        val statementAccountIds = preferences.getString(KEY_STATEMENT_ACCOUNT_IDS, null)
            ?.split(",")
            ?.filter(String::isNotBlank)
            ?.toSet()
        val statementDateRange = preferences.getString(KEY_STATEMENT_DATE_RANGE, null)
            ?.let { runCatching { StatementDateRange.valueOf(it) }.getOrNull() }
            ?: StatementDateRange.THIS_YEAR
        val statementFromDate = preferences.getString(KEY_STATEMENT_FROM_DATE, null)
        val statementToDate = preferences.getString(KEY_STATEMENT_TO_DATE, null)
        val statementColumns = preferences.getString(KEY_STATEMENT_COLUMNS, null)
            ?.split(",")
            ?.mapNotNull { runCatching { StatementColumn.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf(Set<StatementColumn>::isNotEmpty)
            ?: DEFAULT_STATEMENT_COLUMNS
        val statementLayout = preferences.getString(KEY_STATEMENT_LAYOUT, null)
            ?.let { runCatching { StatementLayout.valueOf(it) }.getOrNull() }
            ?: StatementLayout.GROUPED
        val statementAccountColumn = preferences.getBoolean(KEY_STATEMENT_ACCOUNT_COLUMN, true)
        return AppPreferences(
            theme,
            language,
            amountsVisible,
            showAllCurrencies,
            statementAccountIds,
            statementDateRange,
            statementFromDate,
            statementToDate,
            statementColumns,
            statementLayout,
            statementAccountColumn,
        )
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
        const val KEY_STATEMENT_ACCOUNT_IDS = "statement_account_ids"
        const val KEY_STATEMENT_DATE_RANGE = "statement_date_range"
        const val KEY_STATEMENT_FROM_DATE = "statement_from_date"
        const val KEY_STATEMENT_TO_DATE = "statement_to_date"
        const val KEY_STATEMENT_COLUMNS = "statement_columns"
        const val KEY_STATEMENT_LAYOUT = "statement_layout"
        const val KEY_STATEMENT_ACCOUNT_COLUMN = "statement_account_column"
    }
}
