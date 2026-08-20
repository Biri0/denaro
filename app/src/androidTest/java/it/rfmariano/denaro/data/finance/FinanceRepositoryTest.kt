package it.rfmariano.denaro.data.finance

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.data.local.DebtDirection
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
    fun balanceAdjustmentsReconcileBalanceAppearInActivityAndStayOutOfCashFlow() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val occurredAt = java.time.LocalDate.of(2026, 7, 15)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val repository = FinanceRepository(database, clock = { occurredAt })
            val accountId = repository.createAccount(accountInput("Cash"))

            val upwardId = repository.createBalanceAdjustment(accountId, 1_500)
            assertEquals(1_500L, repository.observeAccount(accountId).first()?.balanceMinor)
            val upward = repository.observeBalanceAdjustment(upwardId).first()
            assertEquals(0L, upward?.balanceBeforeMinor)
            assertEquals(1_500L, upward?.balanceAfterMinor)
            assertEquals(1_500L, upward?.deltaMinor)

            val downwardId = repository.createBalanceAdjustment(accountId, -250)
            assertEquals(-250L, repository.observeAccount(accountId).first()?.balanceMinor)
            assertEquals(
                -1_750L,
                repository.observeBalanceAdjustment(downwardId).first()?.deltaMinor
            )

            val page = database.activityDao().pagingSource(
                kind = ActivityKind.ADJUSTMENT.name,
                accountId = accountId,
                currency = "EUR",
                categoryId = null,
                fromDate = null,
                toDate = null,
            ).load(
                PagingSource.LoadParams.Refresh(null, 40, false),
            ) as PagingSource.LoadResult.Page
            assertEquals(2, page.data.size)
            assertTrue(page.data.all { it.kind == ActivityKind.ADJUSTMENT.name })

            val dashboard = repository.observeDashboard(
                DashboardFilter("EUR", accountId, "2026-07"),
            ).first()
            assertEquals(0L, dashboard.selected.incomeMinor)
            assertEquals(0L, dashboard.selected.expenseMinor)

            repository.deleteBalanceAdjustment(downwardId)
            assertEquals(1_500L, repository.observeAccount(accountId).first()?.balanceMinor)
            val unchangedFailure = runCatching {
                repository.createBalanceAdjustment(accountId, 1_500)
            }.exceptionOrNull()
            assertEquals("The account already has this balance", unchangedFailure?.message)
        } finally {
            database.close()
        }
    }

    @Test
    fun movementsExcludeDebtOpeningsAndRepayments() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val primaryId = repository.createAccount(accountInput("Primary"))
            val secondaryId = repository.createAccount(accountInput("Secondary"))
            val counterpartyId = repository.createCounterparty(CounterpartyInput("Alex", null))
            repository.createTransaction(
                TransactionInput(
                    accountId = primaryId,
                    amountMinor = 1_000,
                    type = TransactionType.EXPENSE,
                    occurredAt = 1,
                    description = null,
                    categoryId = null,
                ),
            )
            repository.createTransfer(transferInput(primaryId, secondaryId, occurredAt = 2))
            val debtId = repository.createDebt(
                DebtInput(
                    counterpartyId,
                    primaryId,
                    DebtDirection.BORROWED,
                    10_000,
                    3,
                    null,
                    null,
                ),
            )
            repository.createDebtRepayment(
                DebtRepaymentInput(debtId, primaryId, 2_500, 4, null),
            )

            val result = database.activityDao().pagingSource(
                kind = null,
                accountId = null,
                currency = null,
                categoryId = null,
                fromDate = null,
                toDate = null,
            ).load(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = 40,
                    placeholdersEnabled = false,
                ),
            ) as PagingSource.LoadResult.Page

            assertEquals(setOf("EXPENSE", "TRANSFER"), result.data.map { it.kind }.toSet())
            assertTrue(result.data.all { it.debtId == null })
        } finally {
            database.close()
        }
    }

    @Test
    fun debtOpeningsAndRepaymentsAffectBalancesButNotCashFlow() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val primaryId = repository.createAccount(accountInput("Primary"))
            val secondaryId = repository.createAccount(accountInput("Secondary"))
            val counterpartyId = repository.createCounterparty(CounterpartyInput("Alex", null))
            val openedAt = java.time.LocalDate.of(2026, 7, 1)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val debtId = repository.createDebt(
                DebtInput(
                    counterpartyId,
                    primaryId,
                    DebtDirection.BORROWED,
                    10_000,
                    openedAt,
                    null,
                    null
                ),
            )

            assertEquals(10_000L, repository.observeAccount(primaryId).first()?.balanceMinor)
            val repaymentId = repository.createDebtRepayment(
                DebtRepaymentInput(debtId, secondaryId, 2_500, openedAt + 86_400_000, null),
            )

            assertEquals(10_000L, repository.observeAccount(primaryId).first()?.balanceMinor)
            assertEquals(-2_500L, repository.observeAccount(secondaryId).first()?.balanceMinor)
            assertEquals(7_500L, repository.observeDebt(debtId).first()?.outstandingMinor)
            val dashboard =
                repository.observeDashboard(DashboardFilter("EUR", null, "2026-07")).first()
            assertEquals(0L, dashboard.selected.incomeMinor)
            assertEquals(0L, dashboard.selected.expenseMinor)

            repository.deleteDebtRepayment(repaymentId)
            assertEquals(0L, repository.observeAccount(secondaryId).first()?.balanceMinor)
            repository.deleteDebt(debtId)
            assertEquals(0L, repository.observeAccount(primaryId).first()?.balanceMinor)
        } finally {
            database.close()
        }
    }

    @Test
    fun debtRepaymentCannotExceedOutstandingAmount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val accountId = repository.createAccount(accountInput("Cash"))
            val counterpartyId = repository.createCounterparty(CounterpartyInput("Alex", null))
            val debtId = repository.createDebt(
                DebtInput(counterpartyId, accountId, DebtDirection.LENT, 1_000, 1, null, null),
            )
            val failure = runCatching {
                repository.createDebtRepayment(
                    DebtRepaymentInput(
                        debtId,
                        accountId,
                        1_001,
                        2,
                        null
                    )
                )
            }.exceptionOrNull()
            assertEquals("Repayment cannot exceed the outstanding amount", failure?.message)
        } finally {
            database.close()
        }
    }

    @Test
    fun subcategoriesAlwaysUseAndFollowParentColor() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val parentId = repository.createCategory(
                CategoryInput(TransactionType.EXPENSE, null, "Food", "utensils", 2),
            )
            val childId = repository.createCategory(
                CategoryInput(TransactionType.EXPENSE, parentId, "Groceries", "basket", 9),
            )

            assertEquals(2, repository.getCategory(childId)?.colorIndex)

            repository.updateCategory(
                parentId,
                CategoryInput(TransactionType.EXPENSE, null, "Food", "utensils", 6),
            )

            assertEquals(6, repository.getCategory(parentId)?.colorIndex)
            assertEquals(6, repository.getCategory(childId)?.colorIndex)
        } finally {
            database.close()
        }
    }

    @Test
    fun categoryAndNewParentAreCreatedAtomically() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val repository = FinanceRepository(database, clock = { 10 })

            val childId = repository.createCategoryWithNewParent(
                parentInput = CategoryInput(
                    TransactionType.EXPENSE,
                    null,
                    "Food",
                    "utensils",
                    4,
                ),
                childInput = CategoryInput(
                    TransactionType.EXPENSE,
                    null,
                    "Groceries",
                    "shopping_basket",
                    9,
                ),
            )

            val categories = database.categoryDao().getAll()
            val child = categories.single { it.id == childId }
            val parent = categories.single { it.id == child.parentId }
            assertEquals("Food", parent.name)
            assertEquals("Groceries", child.name)
            assertEquals(TransactionType.EXPENSE, parent.type)
            assertEquals(parent.type, child.type)
            assertEquals(4, parent.colorIndex)
            assertEquals(parent.colorIndex, child.colorIndex)
        } finally {
            database.close()
        }
    }

    @Test
    fun invalidChildRollsBackNewParentCreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val repository = FinanceRepository(database, clock = { 10 })

            val failure = runCatching {
                repository.createCategoryWithNewParent(
                    parentInput = CategoryInput(
                        TransactionType.INCOME,
                        null,
                        "Work",
                        "briefcase_business",
                        2,
                    ),
                    childInput = CategoryInput(
                        TransactionType.INCOME,
                        null,
                        "   ",
                        "banknote",
                        7,
                    ),
                )
            }.exceptionOrNull()

            assertEquals("Name is required", failure?.message)
            assertEquals(0, database.categoryDao().count())
        } finally {
            database.close()
        }
    }

    @Test
    fun duplicateNewParentDoesNotInsertChild() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val repository = FinanceRepository(database, clock = { 10 })
            repository.createCategory(
                CategoryInput(TransactionType.EXPENSE, null, "Food", "utensils", 1),
            )

            val failure = runCatching {
                repository.createCategoryWithNewParent(
                    parentInput = CategoryInput(
                        TransactionType.EXPENSE,
                        null,
                        "food",
                        "shapes",
                        3,
                    ),
                    childInput = CategoryInput(
                        TransactionType.EXPENSE,
                        null,
                        "Dining",
                        "utensils",
                        8,
                    ),
                )
            }.exceptionOrNull()

            assertEquals("A category with this name already exists", failure?.message)
            assertEquals(1, database.categoryDao().count())
        } finally {
            database.close()
        }
    }

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

    @Test
    fun transactionSuggestionsRankAccountsPerTypeByFrequencyThenRecency() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()

        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val alphaId = repository.createAccount(accountInput("Alpha"))
            val betaId = repository.createAccount(accountInput("Beta"))
            repeat(2) { index ->
                repository.createTransaction(
                    transactionInput(
                        accountId = alphaId,
                        categoryId = null,
                        description = "Alpha expense $index",
                    ).copy(occurredAt = index.toLong() + 1),
                )
            }
            repeat(2) { index ->
                repository.createTransaction(
                    transactionInput(
                        accountId = betaId,
                        categoryId = null,
                        description = "Beta expense $index",
                    ).copy(occurredAt = index.toLong() + 10),
                )
            }
            repository.createTransaction(
                transactionInput(betaId, null, "Income").copy(
                    type = TransactionType.INCOME,
                    occurredAt = 20,
                ),
            )

            assertEquals(
                betaId,
                repository.getPreferredTransactionAccountId(TransactionType.EXPENSE),
            )
            assertEquals(
                betaId,
                repository.getPreferredTransactionAccountId(TransactionType.INCOME),
            )

            repository.archiveAccount(betaId)

            assertEquals(
                alphaId,
                repository.getPreferredTransactionAccountId(TransactionType.EXPENSE),
            )
            assertNull(repository.getPreferredTransactionAccountId(TransactionType.INCOME))
        } finally {
            database.close()
        }
    }

    @Test
    fun recurrenceGeneratedTransactionsDoNotInfluenceAccountSuggestions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        var now = 10L

        try {
            val repository = FinanceRepository(database, clock = { now })
            val manualId = repository.createAccount(accountInput("Manual"))
            val recurringId = repository.createAccount(accountInput("Recurring"))
            repository.createTransaction(transactionInput(manualId, null, "Manual"))
            repository.createRecurringRule(
                recurringRuleInput(recurringId, nextOccurrenceAt = 1),
            )
            now = 100
            repository.processDueRecurrences()

            assertEquals(
                manualId,
                repository.getPreferredTransactionAccountId(TransactionType.EXPENSE),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun debtSuggestionsRankActiveAccountAndPersonPairsPerDirection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()

        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val alphaId = repository.createAccount(accountInput("Alpha"))
            val betaId = repository.createAccount(accountInput("Beta"))
            val alexId = repository.createCounterparty(CounterpartyInput("Alex", null))
            val samId = repository.createCounterparty(CounterpartyInput("Sam", null))
            repeat(2) { index ->
                repository.createDebt(
                    DebtInput(
                        alexId,
                        alphaId,
                        DebtDirection.BORROWED,
                        100,
                        index.toLong() + 1,
                        null,
                        null,
                    ),
                )
            }
            repeat(2) { index ->
                repository.createDebt(
                    DebtInput(
                        samId,
                        betaId,
                        DebtDirection.BORROWED,
                        100,
                        index.toLong() + 10,
                        null,
                        null,
                    ),
                )
            }
            repository.createDebt(
                DebtInput(
                    alexId,
                    alphaId,
                    DebtDirection.LENT,
                    100,
                    20,
                    null,
                    null,
                ),
            )

            assertEquals(
                DebtEntryDefaults(betaId, samId),
                repository.getDebtEntryDefaults(DebtDirection.BORROWED),
            )
            assertEquals(
                DebtEntryDefaults(alphaId, alexId),
                repository.getDebtEntryDefaults(DebtDirection.LENT),
            )

            repository.archiveAccount(betaId)

            assertEquals(
                DebtEntryDefaults(alphaId, alexId),
                repository.getDebtEntryDefaults(DebtDirection.BORROWED),
            )
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
