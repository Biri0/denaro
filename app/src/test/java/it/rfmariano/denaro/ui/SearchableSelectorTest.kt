package it.rfmariano.denaro.ui

import it.rfmariano.denaro.data.finance.CategorySummary
import it.rfmariano.denaro.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchableSelectorTest {
    @Test
    fun thresholdEnablesSearchOnlyStrictlyAboveTenOptions() {
        assertFalse(shouldUseSearchableSelector(0))
        assertFalse(shouldUseSearchableSelector(10))
        assertTrue(shouldUseSearchableSelector(11))
        assertTrue(shouldUseSearchableSelector(50))
    }

    @Test
    fun searchMatchesCaseAndDiacriticsInsensitivelyAcrossFields() {
        val options = listOf(
            SelectorOption(
                id = "1",
                value = "1",
                label = "Café Milano",
                supportingText = "Supplier",
            ),
            SelectorOption(
                id = "2",
                value = "2",
                label = "José García",
                supportingText = "Work colleague",
            ),
            SelectorOption(
                id = "3",
                value = "3",
                label = "Alice Rossi",
                supportingText = "Family",
            ),
        )

        assertEquals(
            listOf("1"),
            filterSelectorOptions(options, "cafe").map(SelectorOption<String>::id),
        )
        assertEquals(
            listOf("2"),
            filterSelectorOptions(options, "jose work").map(SelectorOption<String>::id),
        )
        assertEquals(
            listOf("3"),
            filterSelectorOptions(options, "FAMILY").map(SelectorOption<String>::id),
        )
        assertEquals(
            listOf("1", "2", "3"),
            filterSelectorOptions(options, "   ").map(SelectorOption<String>::id),
        )
        assertTrue(filterSelectorOptions(options, "nonexistent").isEmpty())
    }

    @Test
    fun categoryTreeSearchPreservesParentsForMatchingChildrenAndChildrenForMatchingParents() {
        val categories = listOf(
            category("food", null, "Food"),
            category("groceries", "food", "Groceries"),
            category("dining", "food", "Dining out"),
            category("home", null, "Housing"),
            category("rent", "home", "Rent"),
        )

        assertEquals(
            listOf("food", "groceries", "dining"),
            filterCategoriesForSearch(categories, "food").map(CategorySummary::id),
        )
        assertEquals(
            listOf("food", "groceries"),
            filterCategoriesForSearch(categories, "groc").map(CategorySummary::id),
        )
        assertEquals(
            listOf("home", "rent"),
            filterCategoriesForSearch(categories, "rent").map(CategorySummary::id),
        )
        assertTrue(filterCategoriesForSearch(categories, "travel").isEmpty())
    }

    private fun category(
        id: String,
        parentId: String?,
        name: String,
    ) = CategorySummary(
        id = id,
        type = TransactionType.EXPENSE,
        parentId = parentId,
        name = name,
        iconName = "shapes",
        colorIndex = 0,
        archivedAt = null,
    )
}
