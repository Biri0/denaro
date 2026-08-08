package it.rfmariano.denaro.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupPreloadingTest {
    @Test
    fun secondaryPreloadingWaitsForHome() {
        assertFalse(shouldPreloadSecondaryTopLevel(HomeUiState()))
        assertFalse(
            shouldPreloadSecondaryTopLevel(
                HomeUiState(
                    isLoading = false,
                    isDashboardLoading = true,
                    accounts = listOf(testAccount()),
                ),
            ),
        )
    }

    @Test
    fun secondaryPreloadingStartsAfterPopulatedHomeIsFullyReady() {
        assertTrue(
            shouldPreloadSecondaryTopLevel(
                HomeUiState(
                    isLoading = false,
                    isDashboardLoading = false,
                    accounts = listOf(testAccount()),
                ),
            ),
        )
    }

    @Test
    fun emptyHomeIsReadyWithoutWaitingForDashboard() {
        assertTrue(
            shouldPreloadSecondaryTopLevel(
                HomeUiState(isLoading = false, isDashboardLoading = true),
            ),
        )
    }

    @Test
    fun localAuthenticationRevealsAppBeforeProcessStateArrives() {
        assertTrue(
            shouldRevealApp(
                appLockEnabled = true,
                processUnlocked = false,
                authenticatedLocally = true,
            ),
        )
    }

    @Test
    fun lockedProcessRemainsHiddenBeforeAuthentication() {
        assertFalse(
            shouldRevealApp(
                appLockEnabled = true,
                processUnlocked = false,
                authenticatedLocally = false,
            ),
        )
    }

    private fun testAccount() = it.rfmariano.denaro.data.finance.AccountSummary(
        id = "account",
        name = "Checking",
        description = null,
        openingBalanceMinor = 0,
        balanceMinor = 0,
        currency = "EUR",
        archivedAt = null,
    )
}
