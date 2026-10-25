package it.rfmariano.denaro.data.finance

import it.rfmariano.denaro.data.preferences.LanguageOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPresetsTest {

    @Test
    fun starterCategoryLanguageFollowsLocale() {
        assertEquals(StarterCategoryLanguage.ITALIAN, starterCategoryLanguage("it"))
        assertEquals(StarterCategoryLanguage.SPANISH, starterCategoryLanguage("es"))
        assertEquals(StarterCategoryLanguage.ENGLISH, starterCategoryLanguage("en"))
        assertEquals(StarterCategoryLanguage.ENGLISH, starterCategoryLanguage("fr"))
        assertEquals(StarterCategoryLanguage.ENGLISH, starterCategoryLanguage(null))
    }

    @Test
    fun spanishLanguageOptionUsesCorrectTag() {
        assertEquals("es", LanguageOption.SPANISH.languageTag)
        assertTrue(LanguageOption.entries.contains(LanguageOption.SPANISH))
        assertEquals(LanguageOption.SPANISH, LanguageOption.valueOf("SPANISH"))
    }

    @Test
    fun starterCategoryLanguageOptionContainsSpanish() {
        assertTrue(StarterCategoryLanguage.entries.contains(StarterCategoryLanguage.SPANISH))
        assertEquals(StarterCategoryLanguage.SPANISH, StarterCategoryLanguage.valueOf("SPANISH"))
    }

    @Test
    fun spanishStarterCategoriesMatchStructureOfEnglishAndItalian() {
        val english = starterCategories(StarterCategoryLanguage.ENGLISH)
        val italian = starterCategories(StarterCategoryLanguage.ITALIAN)
        val spanish = starterCategories(StarterCategoryLanguage.SPANISH)

        assertEquals(english.size, spanish.size)
        assertEquals(italian.size, spanish.size)

        for (i in english.indices) {
            val en = english[i]
            val it = italian[i]
            val es = spanish[i]

            assertEquals(en.type, es.type)
            assertEquals(it.type, es.type)

            assertEquals(en.iconName, es.iconName)
            assertEquals(it.iconName, es.iconName)

            assertTrue(es.name.isNotBlank())

            assertEquals(en.children.size, es.children.size)
            assertEquals(it.children.size, es.children.size)

            for (j in en.children.indices) {
                val enChild = en.children[j]
                val itChild = it.children[j]
                val esChild = es.children[j]

                assertEquals(enChild.second, esChild.second)
                assertEquals(itChild.second, esChild.second)

                assertTrue(esChild.first.isNotBlank())
            }
        }
    }
}
