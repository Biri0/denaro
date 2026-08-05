package it.rfmariano.denaro.ui

import it.rfmariano.denaro.data.finance.AccountSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingStatePresentationTest {
    @Test
    fun loadingCollectionsNeverRenderAsEmpty() {
        assertFalse(shouldShowEmptyState(isLoading = true, itemCount = 0))
        assertFalse(shouldShowEmptyState(isLoading = true, itemCount = 6))
    }

    @Test
    fun emptyStateRequiresCompletedEmptyResult() {
        assertTrue(shouldShowEmptyState(isLoading = false, itemCount = 0))
        assertFalse(shouldShowEmptyState(isLoading = false, itemCount = 1))
    }

    @Test
    fun viewModelStatesStartLoading() {
        assertTrue(HomeUiState().isLoading)
        assertTrue(HomeUiState().isDashboardLoading)
        assertTrue(AccountsUiState().isLoading)
        assertTrue(AccountDetailUiState().isLoading)
    }

    @Test
    fun emptyHomeIsFullyDrawnWhenAccountLoadingFinishes() {
        assertTrue(
            isHomeFullyDrawn(
                HomeUiState(
                    isLoading = false,
                    isDashboardLoading = true,
                ),
            ),
        )
    }

    @Test
    fun populatedHomeWaitsForDashboard() {
        val account = AccountSummary(
            id = "account",
            name = "Account",
            description = null,
            openingBalanceMinor = 0,
            balanceMinor = 0,
            currency = "EUR",
            archivedAt = null,
        )

        assertFalse(
            isHomeFullyDrawn(
                HomeUiState(
                    isLoading = false,
                    isDashboardLoading = true,
                    accounts = listOf(account),
                ),
            ),
        )
        assertTrue(
            isHomeFullyDrawn(
                HomeUiState(
                    isLoading = false,
                    isDashboardLoading = false,
                    accounts = listOf(account),
                ),
            ),
        )
    }
}
