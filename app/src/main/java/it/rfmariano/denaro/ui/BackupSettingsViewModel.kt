package it.rfmariano.denaro.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.rfmariano.denaro.data.backup.BackupException
import it.rfmariano.denaro.data.backup.BackupInspection
import it.rfmariano.denaro.data.backup.BackupPreview
import it.rfmariano.denaro.data.backup.BackupRecordCounts
import it.rfmariano.denaro.data.backup.BackupTemporaryFiles
import it.rfmariano.denaro.data.backup.DenaroBackupService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

internal enum class BackupDialog {
    PROTECTION,
    PLAIN_WARNING,
    CREATE_PASSWORD,
    RESTORE_PASSWORD,
    RESTORE_CONFIRM,
    ERASE_CONFIRM,
}

internal data class RestoreCandidate(
    val uri: Uri,
    val preview: BackupPreview,
    val currentCounts: BackupRecordCounts,
    val password: CharArray?,
    val contentDigest: String,
)

internal enum class BackupResult {
    CREATED,
    CREATE_FAILED,
    ALREADY_CURRENT,
    RESTORE_COMPLETE,
    RESTORE_FAILED,
    ERASE_COMPLETE,
    ERASE_FAILED,
    PASSWORD_REQUIRED,
    WRONG_PASSWORD,
    INVALID_BACKUP,
    CHOOSE_DENARO_BACKUP,
}

internal data class BackupSettingsUiState(
    val dialog: BackupDialog? = null,
    val operation: BackupOperation? = null,
    val pendingUri: Uri? = null,
    val candidate: RestoreCandidate? = null,
    val restorePasswordError: Boolean = false,
    val result: BackupResult? = null,
)

internal interface BackupTaskRunner {
    suspend fun create(uri: Uri, password: CharArray?)
    suspend fun discardExport(uri: Uri)
    suspend fun inspect(uri: Uri, password: CharArray?): BackupInspection
    suspend fun restore(uri: Uri, password: CharArray?, expectedContentDigest: String)
    suspend fun eraseFinanceData()
}

internal class ContentResolverBackupTaskRunner(
    private val service: DenaroBackupService,
    private val contentResolver: ContentResolver,
    private val cacheDirectory: File,
    private val processDueRecurrences: suspend () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupTaskRunner {
    override suspend fun create(uri: Uri, password: CharArray?) = withContext(ioDispatcher) {
        var stagedBackup: File? = null
        try {
            val staged = BackupTemporaryFiles.createExport(cacheDirectory)
            stagedBackup = staged
            FileOutputStream(staged).use { output ->
                service.create(output, password)
            }
            contentResolver.openOutputStream(uri, "wt").use { output ->
                FileInputStream(staged).use { input ->
                    input.copyTo(requireNotNull(output))
                }
            }
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
            throw error
        } finally {
            stagedBackup?.delete()
        }
        Unit
    }

    override suspend fun discardExport(uri: Uri) = withContext(ioDispatcher) {
        runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
        Unit
    }

    override suspend fun inspect(uri: Uri, password: CharArray?) = withContext(ioDispatcher) {
        contentResolver.openInputStream(uri).use { input ->
            service.inspect(requireNotNull(input), password)
        }
    }

    override suspend fun restore(
        uri: Uri,
        password: CharArray?,
        expectedContentDigest: String,
    ) = withContext(ioDispatcher) {
        contentResolver.openInputStream(uri).use { input ->
            service.restore(
                requireNotNull(input),
                password,
                expectedContentDigest,
                postRestoreInTransaction = processDueRecurrences,
            )
        }
    }

    override suspend fun eraseFinanceData() = withContext(ioDispatcher) {
        service.eraseFinanceData()
    }
}

internal class BackupSettingsViewModel(
    private val runner: BackupTaskRunner?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupSettingsUiState())
    val uiState: StateFlow<BackupSettingsUiState> = _uiState.asStateFlow()

    fun showDialog(dialog: BackupDialog?) {
        _uiState.update { it.copy(dialog = dialog) }
    }

    fun requestExportPassword(uri: Uri) {
        _uiState.update {
            it.copy(dialog = BackupDialog.CREATE_PASSWORD, pendingUri = uri)
        }
    }

    fun dismissDialog() {
        _uiState.update { current ->
            if (current.dialog == BackupDialog.RESTORE_CONFIRM) {
                current.candidate?.password?.fill('\u0000')
            }
            current.copy(
                dialog = null,
                pendingUri = null,
                candidate = if (current.dialog == BackupDialog.RESTORE_CONFIRM) null else current.candidate,
                restorePasswordError = false,
            )
        }
    }

    fun cancelPendingExport() {
        val pendingUri = _uiState.value
            .takeIf { it.dialog == BackupDialog.CREATE_PASSWORD }
            ?.pendingUri
        dismissDialog()
        val activeRunner = runner ?: return
        pendingUri ?: return
        viewModelScope.launch {
            try {
                activeRunner.discardExport(pendingUri)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
            }
        }
    }

    fun create(uri: Uri, password: CharArray?) {
        val activeRunner = runner
        if (activeRunner == null) {
            password?.fill('\u0000')
            return
        }
        _uiState.update { it.copy(dialog = null, operation = BackupOperation.CREATE) }
        viewModelScope.launch {
            val result = try {
                activeRunner.create(uri, password)
                BackupResult.CREATED
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                BackupResult.CREATE_FAILED
            } finally {
                password?.fill('\u0000')
            }
            finishOperation(result)
        }
    }

    fun inspect(uri: Uri, suppliedPassword: CharArray?) {
        val activeRunner = runner
        if (activeRunner == null) {
            suppliedPassword?.fill('\u0000')
            return
        }
        _uiState.update {
            it.copy(dialog = null, operation = BackupOperation.CHECK_RESTORE)
        }
        viewModelScope.launch {
            try {
                val inspection = activeRunner.inspect(uri, suppliedPassword)
                if (inspection.hasFinanceChanges) {
                    val candidate = RestoreCandidate(
                        uri = uri,
                        preview = inspection.preview,
                        currentCounts = inspection.currentCounts,
                        password = suppliedPassword?.copyOf(),
                        contentDigest = inspection.contentDigest,
                    )
                    _uiState.update {
                        it.copy(
                            dialog = BackupDialog.RESTORE_CONFIRM,
                            operation = null,
                            pendingUri = null,
                            candidate = candidate,
                            restorePasswordError = false,
                        )
                    }
                } else {
                    finishOperation(BackupResult.ALREADY_CURRENT)
                }
            } catch (_: BackupException.PasswordRequired) {
                _uiState.update {
                    it.copy(
                        dialog = BackupDialog.RESTORE_PASSWORD,
                        operation = null,
                        pendingUri = uri,
                        restorePasswordError = false,
                    )
                }
            } catch (_: BackupException.AuthenticationFailed) {
                _uiState.update {
                    it.copy(
                        dialog = BackupDialog.RESTORE_PASSWORD,
                        operation = null,
                        pendingUri = uri,
                        restorePasswordError = true,
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                finishOperation(error.toRestoreResult())
            } finally {
                suppliedPassword?.fill('\u0000')
            }
        }
    }

    fun clearRestorePasswordError() {
        _uiState.update { it.copy(restorePasswordError = false) }
    }

    fun confirmRestore() {
        val activeRunner = runner ?: return
        val candidate = _uiState.value.candidate ?: return
        _uiState.update {
            it.copy(dialog = null, operation = BackupOperation.RESTORE)
        }
        viewModelScope.launch {
            val result = try {
                activeRunner.restore(
                    candidate.uri,
                    candidate.password,
                    candidate.contentDigest,
                )
                BackupResult.RESTORE_COMPLETE
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                error.toRestoreResult()
            } finally {
                candidate.password?.fill('\u0000')
            }
            _uiState.update { it.copy(candidate = null) }
            finishOperation(result)
        }
    }

    fun confirmErase() {
        val activeRunner = runner ?: return
        _uiState.update { it.copy(dialog = null, operation = BackupOperation.ERASE) }
        viewModelScope.launch {
            val result = try {
                activeRunner.eraseFinanceData()
                BackupResult.ERASE_COMPLETE
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                BackupResult.ERASE_FAILED
            }
            finishOperation(result)
        }
    }

    fun publishResult(result: BackupResult) {
        _uiState.update { it.copy(result = result) }
    }

    fun consumeResult(result: BackupResult) {
        _uiState.update { current ->
            if (current.result == result) current.copy(result = null) else current
        }
    }

    override fun onCleared() {
        _uiState.value.candidate?.password?.fill('\u0000')
    }

    private fun finishOperation(result: BackupResult) {
        _uiState.update {
            it.copy(
                dialog = null,
                operation = null,
                pendingUri = null,
                restorePasswordError = false,
                result = result,
            )
        }
    }

    private fun Throwable.toRestoreResult(): BackupResult = when (this) {
        is BackupException.PasswordRequired -> BackupResult.PASSWORD_REQUIRED
        is BackupException.AuthenticationFailed -> BackupResult.WRONG_PASSWORD
        is BackupException.InvalidBackup, is BackupException.UnsupportedVersion ->
            BackupResult.INVALID_BACKUP

        else -> BackupResult.RESTORE_FAILED
    }
}
