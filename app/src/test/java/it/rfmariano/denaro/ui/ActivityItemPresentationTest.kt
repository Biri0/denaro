package it.rfmariano.denaro.ui

import it.rfmariano.denaro.data.finance.ActivityItem
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.DebtMovementKind
import it.rfmariano.denaro.data.local.DebtDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityItemPresentationTest {
    @Test
    fun transferSignUsesSelectedAccountPerspective() {
        val transfer = activityItem(
            kind = ActivityKind.TRANSFER,
            accountId = "source",
            counterpartyAccountId = "destination",
        )

        assertEquals(-1_000L, transfer.signedAmount(null))
        assertEquals(-1_000L, transfer.signedAmount("source"))
        assertEquals(1_000L, transfer.signedAmount("destination"))
    }

    @Test
    fun transferCanBeDisplayedWithoutDirectionSign() {
        val transfer = activityItem(
            kind = ActivityKind.TRANSFER,
            accountId = "source",
            counterpartyAccountId = "destination",
        )

        assertEquals(
            1_000L,
            transfer.signedAmount("source", showTransferSign = false),
        )
        assertEquals(
            1_000L,
            transfer.signedAmount(null, showTransferSign = false),
        )
    }

    @Test
    fun transactionSignsDoNotDependOnAccountPerspective() {
        assertEquals(
            1_000L,
            activityItem(ActivityKind.INCOME).signedAmount("account"),
        )
        assertEquals(
            -1_000L,
            activityItem(ActivityKind.EXPENSE).signedAmount("account"),
        )
    }

    @Test
    fun debtMovementSignsFollowDirectionAndMovement() {
        assertEquals(
            1_000L,
            activityItem(ActivityKind.DEBT).copy(
                debtDirection = DebtDirection.BORROWED,
                debtMovement = DebtMovementKind.OPENING,
            ).signedAmount(null),
        )
        assertEquals(
            -1_000L,
            activityItem(ActivityKind.DEBT).copy(
                debtDirection = DebtDirection.BORROWED,
                debtMovement = DebtMovementKind.REPAYMENT,
            ).signedAmount(null),
        )
        assertEquals(
            -1_000L,
            activityItem(ActivityKind.DEBT).copy(
                debtDirection = DebtDirection.LENT,
                debtMovement = DebtMovementKind.OPENING,
            ).signedAmount(null),
        )
    }

    private fun activityItem(
        kind: ActivityKind,
        accountId: String = "account",
        counterpartyAccountId: String? = null,
    ) = ActivityItem(
        id = "activity",
        kind = kind,
        accountId = accountId,
        counterpartyAccountId = counterpartyAccountId,
        accountName = "Account",
        counterpartyAccountName = null,
        currency = "EUR",
        amountMinor = 1_000,
        occurredAt = 1,
        localDate = "1970-01-01",
        description = null,
        recurringRuleId = null,
    )
}
