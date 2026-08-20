package it.rfmariano.denaro.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickEntryWidgetTest {
    @Test
    fun compactCombinedWidgetShowsFiveIconActionsWithoutLabels() =
        runGlanceAppWidgetUnitTest {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            setContext(context)
            setAppWidgetSize(DpSize(180.dp, 120.dp))
            provideComposable { QuickEntryWidgetContent(QuickWidgetKind.ALL) }

            onAllNodes(hasClickAction()).assertCountEquals(5)
            onNode(hasContentDescriptionEqualTo(context.getString(R.string.income))).assertExists()
            onNode(hasContentDescriptionEqualTo(context.getString(R.string.expense))).assertExists()
            onNode(hasContentDescriptionEqualTo(context.getString(R.string.transfer))).assertExists()
            onNode(hasContentDescriptionEqualTo(context.getString(R.string.borrow))).assertExists()
            onNode(hasContentDescriptionEqualTo(context.getString(R.string.lend))).assertExists()
            onNode(hasTextEqualTo(context.getString(R.string.income))).assertDoesNotExist()
            onNode(hasTextEqualTo(context.getString(R.string.widget_header))).assertDoesNotExist()
        }

    @Test
    fun standardCombinedWidgetHidesLabelsAtShortHeight() = runGlanceAppWidgetUnitTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setContext(context)
        setAppWidgetSize(DpSize(280.dp, 120.dp))
        provideComposable { QuickEntryWidgetContent(QuickWidgetKind.ALL) }

        onAllNodes(hasClickAction()).assertCountEquals(5)
        onNode(hasTextEqualTo(context.getString(R.string.income))).assertDoesNotExist()
        onNode(hasTextEqualTo(context.getString(R.string.expense))).assertDoesNotExist()
        onNode(hasTextEqualTo(context.getString(R.string.transfer))).assertDoesNotExist()
        onNode(hasTextEqualTo(context.getString(R.string.borrow))).assertDoesNotExist()
        onNode(hasTextEqualTo(context.getString(R.string.lend))).assertDoesNotExist()
    }

    @Test
    fun tallCombinedWidgetShowsLabels() = runGlanceAppWidgetUnitTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setContext(context)
        setAppWidgetSize(DpSize(280.dp, 160.dp))
        provideComposable { QuickEntryWidgetContent(QuickWidgetKind.ALL) }

        onAllNodes(hasClickAction()).assertCountEquals(5)
        onNode(hasTextEqualTo(context.getString(R.string.income))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.expense))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.transfer))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.borrow))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.lend))).assertExists()
    }

    @Test
    fun wideTransactionWidgetAddsLabels() = runGlanceAppWidgetUnitTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setContext(context)
        setAppWidgetSize(DpSize(280.dp, 64.dp))
        provideComposable { QuickEntryWidgetContent(QuickWidgetKind.TRANSACTIONS) }

        onAllNodes(hasClickAction()).assertCountEquals(3)
        onNode(hasTextEqualTo(context.getString(R.string.income))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.expense))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.transfer))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.widget_header))).assertDoesNotExist()
    }

    @Test
    fun tallDebtWidgetAddsLabelsAndHeader() = runGlanceAppWidgetUnitTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setContext(context)
        setAppWidgetSize(DpSize(200.dp, 120.dp))
        provideComposable { QuickEntryWidgetContent(QuickWidgetKind.DEBTS) }

        onAllNodes(hasClickAction()).assertCountEquals(2)
        onNode(hasTextEqualTo(context.getString(R.string.borrow))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.lend))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.widget_header))).assertExists()
    }
}
