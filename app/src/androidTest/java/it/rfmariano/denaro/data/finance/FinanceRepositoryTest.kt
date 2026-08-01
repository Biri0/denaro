package it.rfmariano.denaro.data.finance

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceRepositoryTest {
    @Test
    fun dashboardGroupsSubcategoriesAndExcludesTransfers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val sourceId = repository.createAccount(accountInput("Cash"))
            val destinationId = repository.createAccount(accountInput("Savings"))
            val parentId = repository.createCategory(
                CategoryInput(TransactionType.EXPENSE, null, "Food", "utensils", 1),
            )
            val childId = repository.createCategory(
                CategoryInput(TransactionType.EXPENSE, parentId, "Groceries", "shopping_basket", 1),
            )
            val occurredAt = java.time.LocalDate.of(2026, 7, 12)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            repository.createTransaction(
                TransactionInput(
                    accountId = sourceId,
                    amountMinor = 2_500,
                    type = TransactionType.EXPENSE,
                    occurredAt = occurredAt,
                    description = null,
                    categoryId = childId,
                ),
            )
            val previousOccurredAt = java.time.LocalDate.of(2026, 6, 12)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            repository.createTransaction(
                TransactionInput(
                    accountId = sourceId,
                    amountMinor = 700,
                    type = TransactionType.EXPENSE,
                    occurredAt = previousOccurredAt,
                    description = null,
                    categoryId = childId,
                ),
            )
            repository.createTransfer(
                transferInput(sourceId, destinationId, occurredAt),
            )

            val dashboard = repository.observeDashboard(
                DashboardFilter("EUR", null, "2026-07"),
            ).first()

            assertEquals(2_500L, dashboard.selected.expenseMinor)
            assertEquals(0L, dashboard.selected.incomeMinor)
            assertEquals(parentId, dashboard.expenseCategories.single().categoryId)
            assertEquals("Food", dashboard.expenseCategories.single().name)
            assertEquals(700L, dashboard.months.first { it.month == "2026-06" }.expenseMinor)
            assertEquals(2_500L, dashboard.months.first { it.month == "2026-07" }.expenseMinor)
        } finally {
            database.close()
        }
    }

    @Test
    fun scheduledRuleCreatesDueActivityAndDeletionKeepsHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            DenaroDatabase::class.java,
        ).build()

        try {
            val repository = FinanceRepository(database, clock = { 1_000 })
            val accountId = repository.createAccount(accountInput("Cash"))
            val ruleId = repository.createRecurringRule(
                recurringRuleInput(accountId, nextOccurrenceAt = 0),
            )

            assertEquals(1, database.transactionDao().count())
            repository.deleteRecurringRule(ruleId)

            assertEquals(1, database.transactionDao().count())
            assertNull(database.transactionDao().observeAll().first().single().recurringRuleId)
        } finally {
            database.close()
        }
    }

    @Test
    fun resumeSkipsOccurrencesMissedWhilePaused() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            DenaroDatabase::class.java,
        ).build()
        var now = 0L

        try {
            val repository = FinanceRepository(database, clock = { now })
            val accountId = repository.createAccount(accountInput("Cash"))
            val ruleId = repository.createRecurringRule(
                recurringRuleInput(accountId, nextOccurrenceAt = 86_400_000),
            )
            repository.pauseRecurringRule(ruleId)
            now = 3 * 86_400_000L
            repository.resumeRecurringRule(ruleId)

            val rule = requireNotNull(repository.getRecurringRule(ruleId))
            assertTrue(rule.isActive)
            assertTrue(rule.nextOccurrenceAt > now)
            assertEquals(0, database.transactionDao().count())
        } finally {
            database.close()
        }
    }

    @Test
    fun transferCreateAndUpdateRejectNegativeTimestamps() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            DenaroDatabase::class.java,
        ).build()

        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val sourceId = repository.createAccount(accountInput("Source"))
            val destinationId = repository.createAccount(accountInput("Destination"))
            val validTransferId = repository.createTransfer(
                transferInput(sourceId, destinationId, occurredAt = 1),
            )

            val createFailure = runCatching {
                repository.createTransfer(
                    transferInput(sourceId, destinationId, occurredAt = -1),
                )
            }.exceptionOrNull()
            assertTrue(createFailure is IllegalArgumentException)
            assertEquals("Date is invalid", createFailure?.message)
            assertEquals(1, database.transferDao().count())

            val updateFailure = runCatching {
                repository.updateTransfer(
                    validTransferId,
                    transferInput(sourceId, destinationId, occurredAt = -1),
                )
            }.exceptionOrNull()
            assertTrue(updateFailure is IllegalArgumentException)
            assertEquals("Date is invalid", updateFailure?.message)
            assertEquals(1L, database.transferDao().getById(validTransferId)?.occurredAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun archivedCategoryCanRemainOnExistingActivitiesButCannotBeNewlyAssigned() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            DenaroDatabase::class.java,
        ).build()

        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val accountId = repository.createAccount(accountInput("Cash"))
            val categoryId = repository.createCategory(
                CategoryInput(TransactionType.EXPENSE, null, "Food", "utensils", 1),
            )
            val transactionId = repository.createTransaction(
                transactionInput(accountId, categoryId, "Before"),
            )
            val ruleId = repository.createRecurringRule(
                recurringRuleInput(accountId, nextOccurrenceAt = 100).copy(
                    categoryId = categoryId,
                ),
            )
            val uncategorizedId = repository.createTransaction(
                transactionInput(accountId, null, "Uncategorized"),
            )
            repository.archiveCategory(categoryId)

            repository.updateTransaction(
                transactionId,
                transactionInput(accountId, categoryId, "After"),
            )
            repository.updateRecurringRule(
                ruleId,
                recurringRuleInput(accountId, nextOccurrenceAt = 100).copy(
                    description = "Updated rent",
                    categoryId = categoryId,
                ),
            )

            assertEquals(categoryId, repository.getTransaction(transactionId)?.categoryId)
            assertEquals("After", repository.getTransaction(transactionId)?.description)
            assertEquals(categoryId, repository.getRecurringRule(ruleId)?.categoryId)

            val createFailure = runCatching {
                repository.createTransaction(transactionInput(accountId, categoryId, "New"))
            }.exceptionOrNull()
            assertEquals("Category is archived", createFailure?.message)

            val reassignmentFailure = runCatching {
                repository.updateTransaction(
                    uncategorizedId,
                    transactionInput(accountId, categoryId, "Reassigned"),
                )
            }.exceptionOrNull()
            assertEquals("Category is archived", reassignmentFailure?.message)
        } finally {
            database.close()
        }
    }

    @Test
    fun restoringParentOnlyRestoresChildrenArchivedByItsCascade() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            DenaroDatabase::class.java,
        ).build()
        var now = 10L

        try {
            val repository = FinanceRepository(database, clock = { now })
            val parentId = repository.createCategory(
                CategoryInput(TransactionType.EXPENSE, null, "Food", "utensils", 1),
            )
            val cascadeChildId = repository.createCategory(
                CategoryInput(TransactionType.EXPENSE, parentId, "Dining", "utensils", 1),
            )
            val independentChildId = repository.createCategory(
                CategoryInput(TransactionType.EXPENSE, parentId, "Groceries", "basket", 1),
            )
            repository.archiveCategory(independentChildId)
            now = 20
            repository.archiveCategory(parentId)

            assertEquals(parentId, repository.getCategory(cascadeChildId)?.archivedByParentId)
            assertNull(repository.getCategory(independentChildId)?.archivedByParentId)

            now = 30
            repository.restoreCategory(parentId)

            assertNull(repository.getCategory(parentId)?.archivedAt)
            assertNull(repository.getCategory(cascadeChildId)?.archivedAt)
            assertNull(repository.getCategory(cascadeChildId)?.archivedByParentId)
            assertEquals(10L, repository.getCategory(independentChildId)?.archivedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun transferSuggestionsExcludeArchivedAndIncompatibleAccounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            DenaroDatabase::class.java,
        ).build()

        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val alphaId = repository.createAccount(accountInput("Alpha"))
            val betaId = repository.createAccount(accountInput("Beta"))
            val archivedId = repository.createAccount(accountInput("Archived"))
            repository.createAccount(accountInput("Yen", currency = "JPY"))
            repeat(2) {
                repository.createTransfer(
                    transferInput(alphaId, betaId, occurredAt = it.toLong() + 1),
                )
            }
            repeat(3) {
                repository.createTransfer(
                    transferInput(archivedId, betaId, occurredAt = it.toLong() + 10),
                )
            }
            repository.archiveAccount(archivedId)

            val suggestions = repository.getTransferAccountSuggestions()

            assertEquals(alphaId, suggestions.preferredSourceId)
            assertEquals(betaId, suggestions.preferredDestinationIds[alphaId])
            assertEquals(null, suggestions.preferredDestinationIds[archivedId])
        } finally {
            database.close()
        }
    }

    private fun accountInput(
        name: String,
        currency: String = "EUR",
    ) = AccountInput(
        name = name,
        description = null,
        openingBalanceMinor = 0,
        currency = currency,
    )

    private fun transferInput(
        sourceId: String,
        destinationId: String,
        occurredAt: Long,
    ) = TransferInput(
        fromAccountId = sourceId,
        toAccountId = destinationId,
        amountMinor = 100,
        occurredAt = occurredAt,
        description = null,
    )

    private fun transactionInput(
        accountId: String,
        categoryId: String?,
        description: String,
    ) = TransactionInput(
        accountId = accountId,
        amountMinor = 100,
        type = TransactionType.EXPENSE,
        occurredAt = 1,
        description = description,
        categoryId = categoryId,
    )

    private fun recurringRuleInput(
        accountId: String,
        nextOccurrenceAt: Long,
    ) = RecurringRuleInput(
        accountId = accountId,
        amountMinor = 100,
        transactionType = TransactionType.EXPENSE,
        description = "Rent",
        frequency = RecurrenceFrequency.DAILY,
        intervalCount = 1,
        nextOccurrenceAt = nextOccurrenceAt,
    )
}
