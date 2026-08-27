package it.rfmariano.denaro.data.export

import it.rfmariano.denaro.data.local.BalanceAdjustmentEntity
import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.data.local.DebtEntity
import it.rfmariano.denaro.data.local.DebtRepaymentEntity
import it.rfmariano.denaro.data.local.TransactionEntity
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.data.local.TransferEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatementExporterTest {
    private val exporter = StatementExporter()

    @Test
    fun runningBalanceAccumulatesAllMovementTypesAndMatchesAccountBalance() {
        val snapshot = sampleSnapshot()

        val exported = exporter.export(
            snapshot,
            DEFAULT_STATEMENT_COLUMNS,
            null,
            null,
            StatementLayout.GROUPED
        )

        val lines = csvLines(exported.content)
        assertEquals(8, lines.size)
        assertEquals("1850.00", lines.last()[5])
    }

    @Test
    fun mapsIncomeAndExpenseToCreditAndDebitColumns() {
        val rows = exporter.rows(sampleSnapshot())

        val income = rows.first { it.description == "Salary" }
        assertEquals(500_00L, income.creditMinor)
        assertNull(income.debitMinor)
        assertEquals("cat1", income.categoryName)

        val expense = rows.first { it.description == "Rent" }
        assertEquals(200_00L, expense.debitMinor)
        assertNull(expense.creditMinor)
    }

    @Test
    fun mapsTransfersAndDebtsToCorrectDirection() {
        val rows = exporter.rows(sampleSnapshot())

        val transferIn = rows.first { it.description == "Transfer from savings" }
        assertEquals(300_00L, transferIn.creditMinor)
        assertNull(transferIn.debitMinor)

        val transferOut = rows.first { it.description == "Transfer to savings" }
        assertEquals(100_00L, transferOut.debitMinor)
        assertNull(transferOut.creditMinor)

        val borrowed = rows.first { it.description == "Borrowed from Bob" }
        assertEquals(400_00L, borrowed.creditMinor)

        val repayment = rows.first { it.description == "Repaid to Bob" }
        assertEquals(100_00L, repayment.debitMinor)
    }

    @Test
    fun filtersRowsByDateRangeButStillAccumulatesBalanceBeforeRange() {
        val exported = exporter.export(
            sampleSnapshot(),
            DEFAULT_STATEMENT_COLUMNS,
            LocalDate.of(2025, 1, 10),
            LocalDate.of(2025, 1, 31),
            StatementLayout.GROUPED,
        )

        val lines = csvLines(exported.content)
        assertEquals(6, lines.size)
        assertEquals("1300.00", lines[1][5])
        assertEquals("1950.00", lines.last()[5])
    }

    @Test
    fun allTimeRangeIncludesFutureDatedTransactions() {
        val snapshot = sampleSnapshot().copy(
            transactions = sampleSnapshot().transactions + listOf(
                transaction(
                    "t3",
                    "checking",
                    TransactionType.INCOME,
                    999_00L,
                    99,
                    "2099-01-01",
                    "Future income",
                    "cat1"
                ),
            ),
        )

        val exported = exporter.export(
            snapshot,
            DEFAULT_STATEMENT_COLUMNS,
            null,
            null,
            StatementLayout.GROUPED,
        )

        val lines = csvLines(exported.content)
        assertTrue(lines.any { it[0] == "2099-01-01" })
        assertEquals(9, lines.size)
    }

    @Test
    fun writesOnlySelectedColumnsInHeaderAndBody() {
        val columns =
            setOf(StatementColumn.DATE, StatementColumn.DESCRIPTION, StatementColumn.BALANCE)

        val exported =
            exporter.export(sampleSnapshot(), columns, null, null, StatementLayout.GROUPED)

        val lines = csvLines(exported.content)
        assertEquals("Date,Description,Balance", lines.first().joinToString(","))
        assertEquals(3, lines[1].size)
        assertEquals(8, lines.size)
    }

    @Test
    fun escapesCommasQuotesAndNewlines() {
        val snapshot = sampleSnapshot().copy(
            accounts = listOf(account("checking", "Checking")),
            transactions = listOf(
                transaction(
                    "t1",
                    "checking",
                    TransactionType.EXPENSE,
                    100_00L,
                    1,
                    "2025-01-01",
                    "Cafe, \"bar\"\nline",
                ),
            ),
            transfers = emptyList(),
            balanceAdjustments = emptyList(),
            debts = emptyList(),
            debtRepayments = emptyList(),
        )

        val exported = exporter.export(
            snapshot,
            DEFAULT_STATEMENT_COLUMNS,
            null,
            null,
            StatementLayout.GROUPED
        )

        val body = exported.content.removePrefix("\uFEFF")
        assertTrue(body.contains("\"Cafe, \"\"bar\"\"\nline\""))
    }

    @Test
    fun neutralizesSpreadsheetFormulaTriggersInTextFields() {
        val snapshot = sampleSnapshot().copy(
            accounts = listOf(account("checking", "Checking", opening = -500_00L)),
            categoryNames = mapOf("catFormula" to "+SUM(1,2)"),
            transactions = listOf(
                transaction(
                    "t1",
                    "checking",
                    TransactionType.EXPENSE,
                    100_00L,
                    1,
                    "2025-01-01",
                    "=1+2",
                    "catFormula",
                ),
            ),
            transfers = emptyList(),
            balanceAdjustments = emptyList(),
            debts = emptyList(),
            debtRepayments = emptyList(),
        )

        val exported = exporter.export(
            snapshot,
            DEFAULT_STATEMENT_COLUMNS,
            null,
            null,
            StatementLayout.GROUPED,
        )

        val body = exported.content.removePrefix("\uFEFF")
        assertTrue(body.contains("'=1+2"))
        assertTrue(body.contains("\"'+SUM(1,2)\""))
        assertTrue(body.contains("-600.00"))
    }

    @Test
    fun dateRangeBoundsResolvePresets() {
        val today = LocalDate.of(2026, 8, 27)
        assertEquals(
            LocalDate.of(2026, 8, 1) to today,
            statementDateRangeBounds(StatementDateRange.THIS_MONTH, null, null, today),
        )
        assertEquals(
            LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 7, 31),
            statementDateRangeBounds(StatementDateRange.LAST_MONTH, null, null, today),
        )
        assertEquals(
            LocalDate.of(2026, 1, 1) to today,
            statementDateRangeBounds(StatementDateRange.THIS_YEAR, null, null, today),
        )
        assertEquals(
            LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 12, 31),
            statementDateRangeBounds(StatementDateRange.LAST_YEAR, null, null, today),
        )
        assertEquals(
            null to null,
            statementDateRangeBounds(StatementDateRange.ALL_TIME, null, null, today),
        )
        assertEquals(
            LocalDate.of(2025, 1, 1) to LocalDate.of(2026, 1, 1),
            statementDateRangeBounds(
                StatementDateRange.CUSTOM,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1),
                today,
            ),
        )
    }

    @Test
    fun multiAccountIncludesAccountColumnWhenRequested() {
        val exported = exporter.export(
            twoAccountSnapshot(),
            setOf(
                StatementColumn.ACCOUNT,
                StatementColumn.DATE,
                StatementColumn.DESCRIPTION,
                StatementColumn.CREDIT,
                StatementColumn.DEBIT,
                StatementColumn.BALANCE,
            ),
            null,
            null,
            StatementLayout.GROUPED,
        )

        val lines = csvLines(exported.content)
        assertEquals(
            "Account,Date,Description,Credit,Debit,Balance",
            lines.first().joinToString(",")
        )

        val checkingRows = lines.drop(1).filter { it[0] == "Checking" }
        val savingsRows = lines.drop(1).filter { it[0] == "Savings" }
        assertEquals("1600.00", checkingRows.last()[5])
        assertEquals("100.00", savingsRows.last()[5])
    }

    @Test
    fun singleAccountStripsAccountColumnEvenWhenRequested() {
        val exported = exporter.export(
            sampleSnapshot(),
            setOf(StatementColumn.ACCOUNT, StatementColumn.DATE, StatementColumn.DESCRIPTION),
            null,
            null,
            StatementLayout.GROUPED,
        )

        assertEquals("Date,Description", csvLines(exported.content).first().joinToString(","))
    }

    @Test
    fun chronologicalLayoutOrdersByDateAcrossAccounts() {
        val exported = exporter.export(
            twoAccountSnapshot(),
            setOf(StatementColumn.DATE, StatementColumn.DESCRIPTION, StatementColumn.BALANCE),
            null,
            null,
            StatementLayout.CHRONOLOGICAL,
        )

        val lines = csvLines(exported.content)
        val dates = lines.drop(1).map { it[0] }
        assertEquals(
            listOf("2025-01-05", "2025-01-08", "2025-01-10", "2025-01-15", "2025-01-15"),
            dates
        )
    }

    @Test
    fun groupedLayoutOrdersByAccountThenDate() {
        val exported = exporter.export(
            twoAccountSnapshot(),
            setOf(
                StatementColumn.ACCOUNT,
                StatementColumn.DATE,
                StatementColumn.DESCRIPTION,
                StatementColumn.BALANCE
            ),
            null,
            null,
            StatementLayout.GROUPED,
        )

        val lines = csvLines(exported.content)
        val accounts = lines.drop(1).map { it[0] }
        assertEquals(
            listOf("Checking", "Checking", "Checking", "Savings", "Savings"),
            accounts,
        )
    }

    @Test
    fun transferAppearsInBothSelectedAccounts() {
        val rows = exporter.rows(twoAccountSnapshot())

        assertEquals(1, rows.count { it.description == "Transfer from Savings" })
        assertEquals(1, rows.count { it.description == "Transfer to Checking" })
    }

    @Test
    fun dropsRowsBlankInUnselectedMoneyColumnButKeepsBalance() {
        val columns = setOf(
            StatementColumn.DATE,
            StatementColumn.DESCRIPTION,
            StatementColumn.CREDIT,
            StatementColumn.BALANCE,
        )

        val exported =
            exporter.export(sampleSnapshot(), columns, null, null, StatementLayout.GROUPED)

        val lines = csvLines(exported.content)
        val descriptions = lines.drop(1).map { it[1] }
        assertEquals(
            listOf(
                "Salary",
                "Transfer from savings",
                "Balance adjustment",
                "Borrowed from Bob"
            ), descriptions
        )
        assertEquals("1950.00", lines.last()[3])
    }

    private fun sampleSnapshot() = StatementSnapshot(
        accounts = listOf(account("checking", "Checking", opening = 1000_00L)),
        accountNames = mapOf("checking" to "Checking", "savings" to "savings"),
        categoryNames = mapOf("cat1" to "cat1"),
        counterpartyNames = mapOf("bob" to "Bob"),
        transactions = listOf(
            transaction(
                "t1",
                "checking",
                TransactionType.INCOME,
                500_00L,
                5,
                "2025-01-05",
                "Salary",
                "cat1"
            ),
            transaction(
                "t2",
                "checking",
                TransactionType.EXPENSE,
                200_00L,
                10,
                "2025-01-10",
                "Rent",
                null
            ),
        ),
        transfers = listOf(
            transfer("tr1", "savings", "checking", 300_00L, 15, "2025-01-15"),
            transfer("tr2", "checking", "savings", 100_00L, 20, "2025-01-20"),
        ),
        balanceAdjustments = listOf(
            adjustment("a1", "checking", 50_00L, 25, "2025-01-25"),
        ),
        debts = listOf(
            debt("d1", "bob", "checking", 400_00L, 30, "2025-01-30"),
        ),
        debtRepayments = listOf(
            repayment("r1", "d1", "checking", 100_00L, 32, "2025-02-01"),
        ),
    )

    private fun twoAccountSnapshot() = StatementSnapshot(
        accounts = listOf(
            account("checking", "Checking", opening = 1000_00L),
            account("savings", "Savings", opening = 500_00L),
        ),
        accountNames = mapOf("checking" to "Checking", "savings" to "Savings"),
        categoryNames = mapOf("cat1" to "cat1"),
        counterpartyNames = emptyMap(),
        transactions = listOf(
            transaction(
                "t1",
                "checking",
                TransactionType.INCOME,
                500_00L,
                5,
                "2025-01-05",
                "Salary",
                "cat1"
            ),
            transaction(
                "t2",
                "checking",
                TransactionType.EXPENSE,
                200_00L,
                10,
                "2025-01-10",
                "Rent",
                null
            ),
            transaction(
                "t3",
                "savings",
                TransactionType.EXPENSE,
                100_00L,
                8,
                "2025-01-08",
                "Fee",
                null
            ),
        ),
        transfers = listOf(
            transfer("tr1", "savings", "checking", 300_00L, 15, "2025-01-15"),
        ),
        balanceAdjustments = emptyList(),
        debts = emptyList(),
        debtRepayments = emptyList(),
    )

    private fun account(id: String, name: String, opening: Long = 0L) =
        StatementAccount(id, name, "EUR", 2, opening)

    private fun transaction(
        id: String,
        accountId: String,
        type: TransactionType,
        amount: Long,
        occurredAt: Long,
        localDate: String,
        description: String,
        categoryId: String? = null,
    ) = TransactionEntity(
        id = id,
        accountId = accountId,
        recurringRuleId = null,
        categoryId = categoryId,
        occurrenceKey = null,
        amountMinor = amount,
        type = type,
        occurredAt = occurredAt,
        localDate = localDate,
        description = description,
        createdAt = occurredAt,
        updatedAt = occurredAt,
    )

    private fun transfer(
        id: String,
        from: String,
        to: String,
        amount: Long,
        occurredAt: Long,
        localDate: String,
    ) = TransferEntity(
        id = id,
        fromAccountId = from,
        toAccountId = to,
        amountMinor = amount,
        occurredAt = occurredAt,
        localDate = localDate,
        description = null,
        createdAt = occurredAt,
        updatedAt = occurredAt,
    )

    private fun adjustment(
        id: String,
        accountId: String,
        delta: Long,
        occurredAt: Long,
        localDate: String
    ) =
        BalanceAdjustmentEntity(
            id = id,
            accountId = accountId,
            deltaMinor = delta,
            balanceBeforeMinor = 0,
            balanceAfterMinor = 0,
            occurredAt = occurredAt,
            localDate = localDate,
            createdAt = occurredAt,
        )

    private fun debt(
        id: String,
        counterpartyId: String,
        accountId: String,
        principal: Long,
        openedAt: Long,
        localDate: String
    ) =
        DebtEntity(
            id = id,
            counterpartyId = counterpartyId,
            accountId = accountId,
            direction = DebtDirection.BORROWED,
            principalMinor = principal,
            currency = "EUR",
            openedAt = openedAt,
            localDate = localDate,
            dueDate = null,
            note = null,
            createdAt = openedAt,
            updatedAt = openedAt,
        )

    private fun repayment(
        id: String,
        debtId: String,
        accountId: String,
        amount: Long,
        occurredAt: Long,
        localDate: String
    ) =
        DebtRepaymentEntity(
            id = id,
            debtId = debtId,
            accountId = accountId,
            amountMinor = amount,
            occurredAt = occurredAt,
            localDate = localDate,
            note = null,
            createdAt = occurredAt,
            updatedAt = occurredAt,
        )

    private fun csvLines(content: String): List<List<String>> =
        content.removePrefix("\uFEFF")
            .split("\r\n")
            .filter(String::isNotEmpty)
            .map { line -> line.split(",") }
}
