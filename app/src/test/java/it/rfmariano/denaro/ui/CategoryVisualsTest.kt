package it.rfmariano.denaro.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryVisualsTest {
    @Test
    fun catalogContainsSixtyUniqueIcons() {
        assertEquals(60, CategoryIconOptions.size)
        assertEquals(60, CategoryIconOptions.map(CategoryIconOption::name).toSet().size)
    }

    @Test
    fun suggestionsUnderstandItalianAliasesAndAccents() {
        assertEquals("graduation_cap", suggestCategoryIcon("Università"))
        assertEquals("dumbbell", suggestCategoryIcon("Palestra"))
        assertEquals("paw_print", suggestCategoryIcon("Animali"))
    }

    @Test
    fun searchRanksExactAliasesBeforeFuzzyMatches() {
        assertEquals("credit_card", searchCategoryIcons("carta").first().name)
        assertEquals("train_front", searchCategoryIcons("treno").first().name)
        assertTrue(searchCategoryIcons("zzzzzz").isEmpty())
    }

    @Test
    fun suggestionDebounceIsThreeHundredMilliseconds() {
        assertEquals(300L, CATEGORY_ICON_SUGGESTION_DEBOUNCE_MS)
    }
}
