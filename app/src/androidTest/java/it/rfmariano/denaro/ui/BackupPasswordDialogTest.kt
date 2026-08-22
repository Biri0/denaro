package it.rfmariano.denaro.ui

import android.net.Uri
import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.backup.BackupPreview
import it.rfmariano.denaro.data.backup.BackupProtection
import it.rfmariano.denaro.data.backup.BackupRecordCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupPasswordDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun continueImmediatelyShowsNonDismissibleCheckingProgress() {
        var checking by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                if (checking) {
                    BackupProgressDialog(BackupOperation.CHECK_RESTORE)
                } else {
                    PasswordDialog(
                        title = context.getString(R.string.enter_backup_password),
                        description = context.getString(R.string.enter_backup_password_description),
                        showConfirmation = false,
                        onDismiss = {},
                        onConfirm = { checking = true },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("password_input").performTextInput("secret")
        composeRule.onNodeWithText(context.getString(R.string.continue_action)).performClick()

        composeRule.onNodeWithTag("backup_progress_dialog").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.checking_backup)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.continue_action)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).assertDoesNotExist()
    }

    @Test
    fun progressDialogShowsOnlyForBackupAndRestoreOperations() {
        var operation by mutableStateOf(BackupOperation.CREATE)

        composeRule.setContent {
            MaterialTheme { BackupProgressDialog(operation) }
        }

        composeRule.onNodeWithText(context.getString(R.string.creating_backup)).assertExists()

        operation = BackupOperation.CHECK_RESTORE
        composeRule.onNodeWithText(context.getString(R.string.checking_backup)).assertExists()

        operation = BackupOperation.RESTORE
        composeRule.onNodeWithText(context.getString(R.string.restoring_backup)).assertExists()

        operation = BackupOperation.ERASE
        composeRule.onNodeWithTag("backup_progress_dialog").assertDoesNotExist()
    }

    @Test
    fun progressDialogCannotBeDismissedWithBack() {
        composeRule.setContent {
            MaterialTheme { BackupProgressDialog(BackupOperation.CREATE) }
        }

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        composeRule.onNodeWithTag("backup_progress_dialog").assertExists()
    }

    @Test
    fun wrongPasswordErrorIsVisibleUntilUserRetries() {
        var error by mutableStateOf(true)

        composeRule.setContent {
            MaterialTheme {
                PasswordDialog(
                    title = context.getString(R.string.enter_backup_password),
                    description = context.getString(R.string.enter_backup_password_description),
                    showConfirmation = false,
                    errorMessage = if (error) context.getString(R.string.backup_wrong_password) else null,
                    onInputChanged = { error = false },
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.backup_wrong_password)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.password)).performTextInput("retry")
        composeRule.onNodeWithText(context.getString(R.string.backup_wrong_password))
            .assertDoesNotExist()
    }

    @Test
    fun progressIndicatorBelongsOnlyToTheActiveOperation() {
        var operation by mutableStateOf<BackupOperation?>(BackupOperation.CREATE)

        composeRule.setContent {
            MaterialTheme {
                BackupOperationIndicator(
                    visible = operation == BackupOperation.CREATE,
                    tag = "create_backup_progress",
                )
                BackupOperationIndicator(
                    visible = operation == BackupOperation.CHECK_RESTORE ||
                            operation == BackupOperation.RESTORE,
                    tag = "restore_backup_progress",
                )
            }
        }

        composeRule.onNodeWithTag("create_backup_progress").assertExists()
        composeRule.onNodeWithTag("restore_backup_progress").assertDoesNotExist()

        operation = BackupOperation.CHECK_RESTORE
        composeRule.onNodeWithTag("create_backup_progress").assertDoesNotExist()
        composeRule.onNodeWithTag("restore_backup_progress").assertExists()
    }

    @Test
    fun operationFinishesBeforeResultIsShown() {
        var operationActive = true
        var resultObservedOperationActive = true

        composeRule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                publishBackupResult(
                    message = "done",
                    onOperationFinished = { operationActive = false },
                    showResult = { resultObservedOperationActive = operationActive },
                )
            }
        }

        assertFalse(resultObservedOperationActive)
    }

    @Test
    fun restoreConfirmationWarnsOnlyWhenCurrentFinanceDataExists() {
        var currentCounts by mutableStateOf(backupCounts(accounts = 0))
        val preview = BackupPreview(
            createdAt = 123_456L,
            appVersion = "2.0-test",
            protection = BackupProtection.NONE,
            counts = backupCounts(accounts = 3),
        )

        composeRule.setContent {
            MaterialTheme {
                RestoreConfirmationDialog(
                    preview = preview,
                    currentCounts = currentCounts,
                    busy = false,
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.restore_backup_neutral_question))
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.restore_into_empty, 3)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.restore_backup_question))
            .assertDoesNotExist()

        currentCounts = backupCounts(accounts = 2)
        composeRule.onNodeWithText(context.getString(R.string.restore_backup_question))
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.restore_replaces_records, 2))
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.restore_backup_neutral_question))
            .assertDoesNotExist()
    }

    @Test
    fun restoreAcceptsOnlyDenaroFileNames() {
        assertTrue(isDenaroBackupFileName("backup.denaro"))
        assertTrue(isDenaroBackupFileName("BACKUP.DENARO"))
        assertFalse(isDenaroBackupFileName("backup.zip"))
        assertFalse(isDenaroBackupFileName("backup.bin"))
        assertFalse(isDenaroBackupFileName("backup"))
        assertFalse(isDenaroBackupFileName(null))
    }

    @Test
    fun exportDocumentResultFailsClosedWhenProtectionIntentIsMissing() {
        val uri = Uri.parse("content://backup/export.denaro")

        assertNull(exportDocumentAction(uri, null))
        assertNull(exportDocumentAction(null, ExportProtectionIntent.PASSWORD))
        assertTrue(
            exportDocumentAction(uri, ExportProtectionIntent.PASSWORD) is
                    ExportDocumentAction.RequestPassword,
        )
        assertTrue(
            exportDocumentAction(uri, ExportProtectionIntent.NONE) is
                    ExportDocumentAction.CreatePlain,
        )
    }

    @Test
    fun imeActionMovesFocusToConfirmationAndTapMovesItBack() {
        composeRule.setContent {
            MaterialTheme {
                PasswordDialog(
                    title = context.getString(R.string.backup_password),
                    description = context.getString(R.string.backup_password_description),
                    showConfirmation = true,
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithTag("password_input").assertIsFocused().performImeAction()
        composeRule.onNodeWithTag("password_confirmation_input").assertIsFocused()
        composeRule.onNodeWithTag("password_input").performClick().assertIsFocused()
    }

    @Test
    fun forwardImeActionSubmitsOnlyMatchingNonEmptyPasswords() {
        var submitted = false

        composeRule.setContent {
            MaterialTheme {
                PasswordDialog(
                    title = context.getString(R.string.backup_password),
                    description = context.getString(R.string.backup_password_description),
                    showConfirmation = true,
                    onDismiss = {},
                    onConfirm = { submitted = true },
                )
            }
        }

        composeRule.onNodeWithTag("password_input").performTextInput("secret")
        composeRule.onNodeWithTag("password_confirmation_input").performClick()
        composeRule.onNodeWithTag("password_confirmation_input").performTextInput("different")
        composeRule.onNodeWithTag("password_confirmation_input").performImeAction()
        composeRule.runOnIdle { assertFalse(submitted) }

        composeRule.onNodeWithTag("password_confirmation_input").performTextClearance()
        composeRule.onNodeWithTag("password_confirmation_input").performTextInput("secret")
        composeRule.onNodeWithTag("password_confirmation_input").performImeAction()
        composeRule.runOnIdle { assertTrue(submitted) }
    }

    @Test
    fun eraseDataRequiresConfirmationAndCancelDoesNothing() {
        var showConfirmation by mutableStateOf(false)
        var eraseCount = 0

        composeRule.setContent {
            MaterialTheme {
                EraseDataRow(
                    enabled = true,
                    isDemo = false,
                    erasing = false,
                    onClick = { showConfirmation = true },
                )
                if (showConfirmation) {
                    EraseDataConfirmationDialog(
                        onDismiss = { showConfirmation = false },
                        onConfirm = {
                            eraseCount += 1
                            showConfirmation = false
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("erase_data_action").performClick()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeRule.runOnIdle { assertEquals(0, eraseCount) }

        composeRule.onNodeWithTag("erase_data_action").performClick()
        composeRule.onNodeWithTag("erase_data_confirm").performClick()
        composeRule.runOnIdle { assertEquals(1, eraseCount) }
    }

    @Test
    fun eraseDataIsDisabledInDemoAndShowsOnlyItsOwnProgress() {
        composeRule.setContent {
            MaterialTheme {
                EraseDataRow(
                    enabled = false,
                    isDemo = true,
                    erasing = true,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("erase_data_action").assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.erase_data_demo_description))
            .assertExists()
        composeRule.onNodeWithTag("erase_data_progress").assertExists()
        composeRule.onNodeWithTag("create_backup_progress").assertDoesNotExist()
        composeRule.onNodeWithTag("restore_backup_progress").assertDoesNotExist()
    }

    private fun backupCounts(accounts: Int) = BackupRecordCounts(
        accounts = accounts,
        categories = 0,
        recurringRules = 0,
        transactions = 0,
        balanceAdjustments = 0,
        transfers = 0,
        counterparties = 0,
        debts = 0,
        debtRepayments = 0,
    )
}
