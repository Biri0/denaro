package it.rfmariano.denaro.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.DebtSummary
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.ui.theme.DenaroTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtsNetBalanceUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {}
        }
        composeRule.waitForIdle()
    }

    @Test
    fun filteringByCounterpartyShowsNetBalanceAndGrossBreakdown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val debts = listOf(
            debt("a1", "alice", "Alice", DebtDirection.LENT, 120_00L),
            debt("a2", "alice", "Alice", DebtDirection.BORROWED, 40_00L),
            debt("b1", "bob", "Bob", DebtDirection.BORROWED, 30_00L),
            debt("b2", "bob", "Bob", DebtDirection.LENT, 30_00L),
        )

        composeRule.setContent {
            DenaroTheme {
                DebtsPanel(
                    debts = debts,
                    amountsVisible = true,
                    onAddDebt = {},
                    onDebtClick = {},
                )
            }
        }

        // Unfiltered shows gross totals across everyone (no net label)
        composeRule.onNodeWithText(context.getString(R.string.owed_to_you_net)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.you_owe)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.owed_to_you)).assertExists()

        // Filter by Alice -> Owed to you (net) €80.00 + breakdown
        composeRule.onNodeWithText(context.getString(R.string.filter_counterparty)).performClick()
        composeRule.onNode(hasText("Alice") and hasAnyAncestor(isPopup())).performClick()

        composeRule.onNodeWithText(context.getString(R.string.owed_to_you_net)).assertExists()
        composeRule.onNodeWithText(Money.format(80_00L, "EUR", 2)).assertExists()
        val expectedBreakdown =
            "${context.getString(R.string.owed_to_you)} ${Money.format(120_00L, "EUR", 2)} · " +
                    "${context.getString(R.string.you_owe)} ${Money.format(40_00L, "EUR", 2)}"
        composeRule.onNodeWithText(expectedBreakdown).assertExists()

        // Filter by Bob -> Even (net) €0.00
        composeRule.onNodeWithText(context.getString(R.string.filter_counterparty)).performClick()
        composeRule.onNode(hasText("Bob") and hasAnyAncestor(isPopup())).performClick()

        composeRule.onNodeWithText(context.getString(R.string.net_even)).assertExists()
        composeRule.onNodeWithText(Money.format(0L, "EUR", 2)).assertExists()
    }

    private fun debt(
        id: String,
        counterpartyId: String,
        counterpartyName: String,
        direction: DebtDirection,
        principalMinor: Long,
    ) = DebtSummary(
        id = id,
        counterpartyId = counterpartyId,
        counterpartyName = counterpartyName,
        accountId = "account",
        accountName = "Everyday",
        direction = direction,
        principalMinor = principalMinor,
        repaidMinor = 0L,
        currency = "EUR",
        openedAt = 1L,
        localDate = "2026-11-01",
        dueDate = null,
        note = null,
        fractionDigits = 2,
    )
}
