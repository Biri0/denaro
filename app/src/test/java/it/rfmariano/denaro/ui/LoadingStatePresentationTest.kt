package it.rfmariano.denaro.ui

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
}
