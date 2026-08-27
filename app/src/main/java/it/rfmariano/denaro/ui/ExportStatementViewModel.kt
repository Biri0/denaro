package it.rfmariano.denaro.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.rfmariano.denaro.data.export.DEFAULT_STATEMENT_COLUMNS
import it.rfmariano.denaro.data.export.StatementColumn
import it.rfmariano.denaro.data.export.StatementDateRange
import it.rfmariano.denaro.data.export.StatementExporter
import it.rfmariano.denaro.data.export.StatementLabels
import it.rfmariano.denaro.data.export.StatementLayout
import it.rfmariano.denaro.data.export.statementDateRangeBounds
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal enum class ExportStatementResult { SUCCESS, FAILED }

internal data class ExportStatementUiState(
    val accounts: List<AccountSummary> = emptyList(),
    val selectedAccountIds: Set<String> = emptySet(),
    val dateRange: StatementDateRange = StatementDateRange.THIS_YEAR,
    val customFromDate: LocalDate? = null,
    val customToDate: LocalDate? = null,
    val columns: Set<StatementColumn> = DEFAULT_STATEMENT_COLUMNS,
    val layout: StatementLayout = StatementLayout.GROUPED,
    val accountColumnEnabled: Boolean = true,
    val exporting: Boolean = false,
    val result: ExportStatementResult? = null,
)

internal class ExportStatementViewModel(
    private val repository: FinanceRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExportStatementUiState())
    val uiState: StateFlow<ExportStatementUiState> = _uiState.asStateFlow()

    init {
        val saved = preferencesRepository.state.value
        _uiState.update {
            it.copy(
                dateRange = saved.statementDateRange,
                customFromDate = saved.statementFromDate?.let(::parseLocalDate),
                customToDate = saved.statementToDate?.let(::parseLocalDate),
                columns = saved.statementColumns,
                layout = saved.statementLayout,
                accountColumnEnabled = saved.statementAccountColumn,
            )
        }
        viewModelScope.launch {
            repository.observeActiveAccounts().collect { accounts ->
                _uiState.update { current ->
                    val savedSelection = preferencesRepository.state.value.statementAccountIds
                    val selected = savedSelection
                        ?.filter { id -> accounts.any { it.id == id } }
                        ?.toSet()
                        ?: accounts.map { it.id }.toSet()
                    current.copy(accounts = accounts, selectedAccountIds = selected)
                }
            }
        }
    }

    fun toggleAccount(id: String, selected: Boolean) {
        val accounts = _uiState.value.accounts
        val current = _uiState.value.selectedAccountIds
            .filter { accountId -> accounts.any { it.id == accountId } }
            .toMutableSet()
            .apply { if (selected) add(id) else remove(id) }
        _uiState.update { it.copy(selectedAccountIds = current) }
        preferencesRepository.setStatementAccountIds(current)
    }

    fun selectAllAccounts(selected: Boolean) {
        val current = if (selected) _uiState.value.accounts.map { it.id }.toSet() else emptySet()
        _uiState.update { it.copy(selectedAccountIds = current) }
        preferencesRepository.setStatementAccountIds(current)
    }

    fun selectDateRange(range: StatementDateRange) {
        _uiState.update { it.copy(dateRange = range) }
        preferencesRepository.setStatementDateRange(range)
    }

    fun selectCustomDates(from: LocalDate?, to: LocalDate?) {
        _uiState.update { it.copy(customFromDate = from, customToDate = to) }
        preferencesRepository.setStatementCustomDates(from?.toString(), to?.toString())
    }

    fun toggleColumn(column: StatementColumn, enabled: Boolean) {
        val columns = _uiState.value.columns.toMutableSet().apply {
            if (enabled) add(column) else remove(column)
        }
        _uiState.update { it.copy(columns = columns) }
        preferencesRepository.setStatementColumns(columns)
    }

    fun setAllColumns(selected: Boolean) {
        val columns = if (selected) DEFAULT_STATEMENT_COLUMNS else emptySet()
        _uiState.update { it.copy(columns = columns, accountColumnEnabled = selected) }
        preferencesRepository.setStatementColumns(columns)
        preferencesRepository.setStatementAccountColumn(selected)
    }

    fun selectLayout(layout: StatementLayout) {
        _uiState.update { it.copy(layout = layout) }
        preferencesRepository.setStatementLayout(layout)
    }

    fun setAccountColumn(enabled: Boolean) {
        _uiState.update { it.copy(accountColumnEnabled = enabled) }
        preferencesRepository.setStatementAccountColumn(enabled)
    }

    fun export(uri: Uri, labels: StatementLabels) {
        val state = _uiState.value
        val accountIds = state.selectedAccountIds
        if (accountIds.isEmpty()) return
        _uiState.update { it.copy(exporting = true) }
        viewModelScope.launch {
            val result = try {
                withContext(ioDispatcher) {
                    val snapshot = repository.statementSnapshot(accountIds)
                    val (from, to) = statementDateRangeBounds(
                        state.dateRange,
                        state.customFromDate,
                        state.customToDate,
                    )
                    val columns = if (accountIds.size > 1 && state.accountColumnEnabled) {
                        state.columns + StatementColumn.ACCOUNT
                    } else {
                        state.columns
                    }
                    val exported = StatementExporter(labels).export(
                        snapshot = snapshot,
                        columns = columns,
                        fromDate = from,
                        toDate = to,
                        layout = state.layout,
                    )
                    contentResolver.openOutputStream(uri, "wt").use { output ->
                        requireNotNull(output).write(exported.content.toByteArray(Charsets.UTF_8))
                    }
                }
                ExportStatementResult.SUCCESS
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.e("ExportStatement", "Failed to export statement", error)
                runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
                ExportStatementResult.FAILED
            }
            _uiState.update { it.copy(exporting = false, result = result) }
        }
    }

    fun consumeResult() {
        _uiState.update { it.copy(result = null) }
    }

    private fun parseLocalDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()
}
