package it.rfmariano.denaro.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.backup.BackupPreview
import it.rfmariano.denaro.data.backup.BackupRecordCounts
import it.rfmariano.denaro.data.finance.FinanceSessionProvider
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class ExportProtectionIntent { PASSWORD, NONE }

internal sealed interface ExportDocumentAction {
    data class RequestPassword(val uri: Uri) : ExportDocumentAction
    data class CreatePlain(val uri: Uri) : ExportDocumentAction
}

internal fun exportDocumentAction(
    uri: Uri?,
    protection: ExportProtectionIntent?,
): ExportDocumentAction? = when {
    uri == null || protection == null -> null
    protection == ExportProtectionIntent.PASSWORD -> ExportDocumentAction.RequestPassword(uri)
    else -> ExportDocumentAction.CreatePlain(uri)
}

internal enum class BackupOperation {
    CREATE,
    CHECK_RESTORE,
    RESTORE,
    ERASE,
}

@Composable
internal fun BackupSettings(
    financeSessionProvider: FinanceSessionProvider,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val session by financeSessionProvider.session.collectAsStateWithLifecycle()
    val activeSession = session
    val service = activeSession?.backupService
    val backupViewModel: BackupSettingsViewModel = viewModel(
        key = "backup-settings-${activeSession?.id ?: "unavailable"}",
        factory = viewModelFactory {
            BackupSettingsViewModel(
                activeSession?.let { currentSession ->
                    currentSession.backupService?.let {
                        ContentResolverBackupTaskRunner(
                            service = it,
                            contentResolver = context.applicationContext.contentResolver,
                            cacheDirectory = context.applicationContext.cacheDir,
                            processDueRecurrences = currentSession.repository::processDueRecurrences,
                        )
                    }
                },
            )
        },
    )
    val uiState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val restoreEnabled = service != null && session?.isDemo == false
    val eraseEnabled = restoreEnabled
    val busy = uiState.operation != null
    var pendingExportProtection by rememberSaveable {
        mutableStateOf<ExportProtectionIntent?>(null)
    }

    val backupFailed = stringResource(R.string.backup_failed)
    val backupCreated = stringResource(R.string.backup_created)
    val restoreFailed = stringResource(R.string.restore_failed)
    val restoreComplete = stringResource(R.string.restore_complete)
    val backupAlreadyCurrent = stringResource(R.string.backup_already_current)
    val chooseDenaroBackup = stringResource(R.string.choose_denaro_backup)
    val eraseComplete = stringResource(R.string.erase_data_complete)
    val eraseFailed = stringResource(R.string.erase_data_failed)
    val passwordRequired = stringResource(R.string.backup_password_required)
    val wrongPassword = stringResource(R.string.backup_wrong_password)
    val invalidBackup = stringResource(R.string.backup_invalid)

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri ->
        val protection = pendingExportProtection
        pendingExportProtection = null
        if (service != null) {
            when (val action = exportDocumentAction(uri, protection)) {
                is ExportDocumentAction.RequestPassword -> {
                    backupViewModel.requestExportPassword(action.uri)
                }

                is ExportDocumentAction.CreatePlain -> backupViewModel.create(action.uri, null)
                null -> Unit
            }
        }
    }

    val openDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val displayName = context.contentResolver.displayName(uri)
                if (isDenaroBackupFileName(displayName)) {
                    backupViewModel.inspect(uri, null)
                } else {
                    backupViewModel.publishResult(BackupResult.CHOOSE_DENARO_BACKUP)
                }
            }
        }

    val resultMessage = when (uiState.result) {
        BackupResult.CREATED -> backupCreated
        BackupResult.CREATE_FAILED -> backupFailed
        BackupResult.ALREADY_CURRENT -> backupAlreadyCurrent
        BackupResult.RESTORE_COMPLETE -> restoreComplete
        BackupResult.RESTORE_FAILED -> restoreFailed
        BackupResult.ERASE_COMPLETE -> eraseComplete
        BackupResult.ERASE_FAILED -> eraseFailed
        BackupResult.PASSWORD_REQUIRED -> passwordRequired
        BackupResult.WRONG_PASSWORD -> wrongPassword
        BackupResult.INVALID_BACKUP -> invalidBackup
        BackupResult.CHOOSE_DENARO_BACKUP -> chooseDenaroBackup
        null -> null
    }
    LaunchedEffect(uiState.result, resultMessage) {
        val result = uiState.result ?: return@LaunchedEffect
        val message = resultMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        backupViewModel.consumeResult(result)
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.create_backup)) },
        supportingContent = { Text(stringResource(R.string.create_backup_description)) },
        trailingContent = {
            BackupOperationIndicator(
                visible = uiState.operation == BackupOperation.CREATE,
                tag = "create_backup_progress",
            )
        },
        modifier = Modifier.clickable(enabled = service != null && !busy) {
            backupViewModel.showDialog(BackupDialog.PROTECTION)
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.restore_backup)) },
        supportingContent = {
            Text(
                stringResource(
                    if (session?.isDemo == true) {
                        R.string.restore_backup_demo_description
                    } else {
                        R.string.restore_backup_description
                    },
                ),
            )
        },
        trailingContent = {
            BackupOperationIndicator(
                visible = uiState.operation == BackupOperation.CHECK_RESTORE ||
                        uiState.operation == BackupOperation.RESTORE,
                tag = "restore_backup_progress",
            )
        },
        modifier = Modifier.clickable(enabled = restoreEnabled && !busy) {
            openDocument.launch(arrayOf(BACKUP_MIME_TYPE))
        },
    )
    EraseDataRow(
        enabled = eraseEnabled && !busy,
        isDemo = session?.isDemo == true,
        erasing = uiState.operation == BackupOperation.ERASE,
        onClick = { backupViewModel.showDialog(BackupDialog.ERASE_CONFIRM) },
    )

    when (uiState.dialog) {
        BackupDialog.PROTECTION -> AlertDialog(
            onDismissRequest = backupViewModel::dismissDialog,
            title = { Text(stringResource(R.string.protect_backup)) },
            text = { Text(stringResource(R.string.protect_backup_description)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingExportProtection = ExportProtectionIntent.PASSWORD
                    backupViewModel.showDialog(null)
                    createDocument.launch(defaultBackupName())
                }) { Text(stringResource(R.string.use_password)) }
            },
            dismissButton = {
                TextButton(onClick = { backupViewModel.showDialog(BackupDialog.PLAIN_WARNING) }) {
                    Text(stringResource(R.string.without_password))
                }
            },
        )

        BackupDialog.PLAIN_WARNING -> AlertDialog(
            onDismissRequest = backupViewModel::dismissDialog,
            title = { Text(stringResource(R.string.unprotected_backup)) },
            text = { Text(stringResource(R.string.unprotected_backup_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingExportProtection = ExportProtectionIntent.NONE
                    backupViewModel.showDialog(null)
                    createDocument.launch(defaultBackupName())
                }) { Text(stringResource(R.string.continue_action)) }
            },
            dismissButton = {
                TextButton(onClick = backupViewModel::dismissDialog) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )

        BackupDialog.CREATE_PASSWORD -> PasswordDialog(
            title = stringResource(R.string.backup_password),
            description = stringResource(R.string.backup_password_description),
            showConfirmation = true,
            onDismiss = backupViewModel::cancelPendingExport,
            onConfirm = { enteredPassword ->
                val uri = uiState.pendingUri
                if (uri == null) {
                    enteredPassword.fill('\u0000')
                    backupViewModel.dismissDialog()
                } else {
                    backupViewModel.create(uri, enteredPassword)
                }
            },
        )

        BackupDialog.RESTORE_PASSWORD -> PasswordDialog(
            title = stringResource(R.string.enter_backup_password),
            description = stringResource(R.string.enter_backup_password_description),
            showConfirmation = false,
            errorMessage = if (uiState.restorePasswordError) wrongPassword else null,
            onInputChanged = backupViewModel::clearRestorePasswordError,
            onDismiss = backupViewModel::dismissDialog,
            onConfirm = { entered ->
                val uri = uiState.pendingUri
                backupViewModel.clearRestorePasswordError()
                if (uri != null) {
                    backupViewModel.inspect(uri, entered)
                } else {
                    entered.fill('\u0000')
                }
            },
        )

        BackupDialog.RESTORE_CONFIRM -> uiState.candidate?.let { value ->
            RestoreConfirmationDialog(
                preview = value.preview,
                currentCounts = value.currentCounts,
                busy = busy,
                onDismiss = backupViewModel::dismissDialog,
                onConfirm = backupViewModel::confirmRestore,
            )
        }

        BackupDialog.ERASE_CONFIRM -> EraseDataConfirmationDialog(
            onDismiss = backupViewModel::dismissDialog,
            onConfirm = backupViewModel::confirmErase,
        )

        null -> Unit
    }

    uiState.operation?.let { BackupProgressDialog(it) }
}

@Composable
internal fun RestoreConfirmationDialog(
    preview: BackupPreview,
    currentCounts: BackupRecordCounts,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val replacesCurrentData = currentCounts.total > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (replacesCurrentData) R.string.restore_backup_question
                    else R.string.restore_backup_neutral_question,
                ),
            )
        },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.backup_preview,
                        DateFormat.getDateTimeInstance().format(Date(preview.createdAt)),
                        preview.appVersion,
                        preview.counts.total,
                    ),
                )
                Text(
                    stringResource(
                        if (replacesCurrentData) R.string.restore_replaces_records
                        else R.string.restore_into_empty,
                        if (replacesCurrentData) currentCounts.total else preview.counts.total,
                    ),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onConfirm) {
                Text(stringResource(R.string.restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun BackupProgressDialog(operation: BackupOperation) {
    val title = when (operation) {
        BackupOperation.CREATE -> stringResource(R.string.creating_backup)
        BackupOperation.CHECK_RESTORE -> stringResource(R.string.checking_backup)
        BackupOperation.RESTORE -> stringResource(R.string.restoring_backup)
        BackupOperation.ERASE -> return
    }

    BackHandler { }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .testTag("backup_progress_dialog"),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("backup_dialog_progress"),
                    strokeWidth = 3.dp,
                )
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.backup_progress_description),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun EraseDataRow(
    enabled: Boolean,
    isDemo: Boolean,
    erasing: Boolean,
    onClick: () -> Unit,
) {
    val actionColor = MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.38f)
    ListItem(
        headlineContent = { Text(stringResource(R.string.erase_data), color = actionColor) },
        supportingContent = {
            Text(
                stringResource(
                    if (isDemo) R.string.erase_data_demo_description else R.string.erase_data_description,
                ),
            )
        },
        trailingContent = {
            BackupOperationIndicator(visible = erasing, tag = "erase_data_progress")
        },
        modifier = Modifier
            .testTag("erase_data_action")
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
internal fun EraseDataConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.erase_data_question)) },
        text = { Text(stringResource(R.string.erase_data_warning)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("erase_data_confirm"),
            ) {
                Text(stringResource(R.string.erase), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun BackupOperationIndicator(visible: Boolean, tag: String) {
    if (visible) CircularProgressIndicator(Modifier.testTag(tag))
}

internal suspend fun publishBackupResult(
    message: String,
    onOperationFinished: () -> Unit,
    showResult: suspend (String) -> Unit,
) {
    onOperationFinished()
    showResult(message)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PasswordDialog(
    title: String,
    description: String,
    showConfirmation: Boolean,
    errorMessage: String? = null,
    onInputChanged: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    val editor = rememberTextFieldState()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var editingConfirmation by remember { mutableStateOf(false) }
    val editorText = editor.text.toString()
    val currentPassword = if (editingConfirmation) password else editorText
    val currentConfirmation = if (editingConfirmation) editorText else confirmation
    val hasPassword = currentPassword.isNotEmpty()
    val matches = !showConfirmation || currentPassword == currentConfirmation
    val canConfirm = hasPassword && matches
    val editorFocusRequester = remember { FocusRequester() }

    fun editConfirmation(editConfirmation: Boolean) {
        if (!showConfirmation || editingConfirmation == editConfirmation) return
        if (editingConfirmation) confirmation = editor.text.toString()
        else password = editor.text.toString()
        val replacement = if (editConfirmation) confirmation else password
        editor.edit {
            replace(0, length, replacement)
            selection = TextRange(length)
        }
        editingConfirmation = editConfirmation
    }

    fun submit() {
        if (canConfirm) onConfirm(currentPassword.toCharArray())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LaunchedEffect(Unit) {
                editorFocusRequester.requestFocus()
            }
            LaunchedEffect(editor) {
                var initialValue = true
                snapshotFlow { editor.text }.collect {
                    if (initialValue) initialValue = false else onInputChanged()
                }
            }
            Column {
                Text(description)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(if (showConfirmation) 120.dp else 56.dp),
                ) {
                    if (showConfirmation) {
                        InactivePasswordField(
                            value = if (editingConfirmation) password else confirmation,
                            label = stringResource(
                                if (editingConfirmation) R.string.password
                                else R.string.confirm_password,
                            ),
                            onClick = { editConfirmation(!editingConfirmation) },
                            modifier = Modifier
                                .offset(y = if (editingConfirmation) 0.dp else 64.dp)
                                .testTag(
                                    if (editingConfirmation) "password_input"
                                    else "password_confirmation_input",
                                ),
                        )
                    }
                    OutlinedSecureTextField(
                        state = editor,
                        label = {
                            Text(
                                stringResource(
                                    if (editingConfirmation) R.string.confirm_password
                                    else R.string.password,
                                ),
                            )
                        },
                        textObfuscationMode = TextObfuscationMode.Hidden,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                        onKeyboardAction = {
                            if (showConfirmation && !editingConfirmation) editConfirmation(true)
                            else submit()
                        },
                        isError = errorMessage != null && !editingConfirmation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = if (editingConfirmation) 64.dp else 0.dp)
                            .focusRequester(editorFocusRequester)
                            .testTag(
                                if (editingConfirmation) "password_confirmation_input"
                                else "password_input",
                            ),
                    )
                }
                if (errorMessage != null) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        stringResource(
                            if (hasPassword) R.string.password_guidance else R.string.password_required,
                        ),
                        Modifier.padding(top = 8.dp),
                    )
                }
                if (showConfirmation && currentConfirmation.isNotEmpty() && !matches) {
                    Text(
                        stringResource(R.string.passwords_do_not_match),
                        Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = ::submit) {
                Text(stringResource(R.string.continue_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun InactivePasswordField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = "\u2022".repeat(value.length),
        onValueChange = {},
        enabled = false,
        label = { Text(label) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private fun defaultBackupName(): String =
    "denaro-backup-${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())}.denaro"

internal fun isDenaroBackupFileName(displayName: String?): Boolean =
    displayName?.endsWith(BACKUP_EXTENSION, ignoreCase = true) == true

private fun ContentResolver.displayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
    }
}.getOrNull()

private const val BACKUP_MIME_TYPE = "application/octet-stream"
private const val BACKUP_EXTENSION = ".denaro"
