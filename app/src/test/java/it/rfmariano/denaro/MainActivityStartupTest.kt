package it.rfmariano.denaro

import it.rfmariano.denaro.data.migration.MigrationResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityStartupTest {
    @Test
    fun splashRemainsWhileMigrationIsPending() {
        assertTrue(shouldKeepSplashVisible(null, hasFinanceSession = false))
    }

    @Test
    fun splashRemainsWhileSuccessfulMigrationAwaitsSession() {
        assertTrue(
            shouldKeepSplashVisible(
                MigrationResult.NotNeeded,
                hasFinanceSession = false,
            ),
        )
        assertTrue(
            shouldKeepSplashVisible(
                successfulMigration(),
                hasFinanceSession = false,
            ),
        )
    }

    @Test
    fun splashDismissesWhenSessionIsReady() {
        assertFalse(
            shouldKeepSplashVisible(
                MigrationResult.NotNeeded,
                hasFinanceSession = true,
            ),
        )
    }

    @Test
    fun splashDismissesToShowMigrationFailure() {
        assertFalse(
            shouldKeepSplashVisible(
                MigrationResult.Failure("Migration failed"),
                hasFinanceSession = false,
            ),
        )
    }

    private fun successfulMigration() = MigrationResult.Success(
        accountCount = 0,
        transactionCount = 0,
        transferCount = 0,
        recurringRuleCount = 0,
        warnings = emptyList(),
    )
}
