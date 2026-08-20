package it.rfmariano.denaro.quickentry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickEntryIntentTest {
    @Test
    fun parsesEverySupportedQuickEntryAction() {
        QuickEntryAction.entries.forEach { action ->
            assertEquals(
                action,
                QuickEntryIntent.parseAction(
                    "it.rfmariano.denaro.action.QUICK_ENTRY.${action.name}",
                ),
            )
        }
    }

    @Test
    fun ignoresRegularAndUnknownActions() {
        assertNull(QuickEntryIntent.parseAction(null))
        assertNull(QuickEntryIntent.parseAction("android.intent.action.MAIN"))
        assertNull(QuickEntryIntent.parseAction("it.rfmariano.denaro.action.QUICK_ENTRY.UNKNOWN"))
    }
}
