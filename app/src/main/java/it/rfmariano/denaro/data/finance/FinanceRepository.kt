package it.rfmariano.denaro.data.finance

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.ActivityRecord
import it.rfmariano.denaro.data.local.CategoryEntity
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.RecurringRuleEntity
import it.rfmariano.denaro.data.local.TransactionEntity
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.data.local.TransferEntity
import it.rfmariano.denaro.data.local.TransferPairUsage
import it.rfmariano.denaro.data.local.UuidV7
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class FinanceRepository(
    private val database: DenaroDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val recurrenceProcessor = RecurrenceProcessor(database, clock)

    fun observeCategories(includeArchived: Boolean = false): Flow<List<CategorySummary>> =
        database.categoryDao().observeAll().map { categories ->
            categories
                .filter { includeArchived || it.archivedAt == null }
                .map { it.toSummary() }
        }

    suspend fun getCategory(categoryId: String): CategoryEntity? =
        database.categoryDao().getById(categoryId)

    suspend fun createCategory(input: CategoryInput): String {
        validateCategory(input)
        val timestamp = clock()
        val id = UuidV7.generate()
        val colorIndex = input.parentId
            ?.let { parentId ->
                requireNotNull(
                    database.categoryDao().getById(parentId)
                ).colorIndex
            }
            ?: input.colorIndex.coerceIn(0, 11)
        database.categoryDao().insert(
            CategoryEntity(
                id = id,
                type = input.type,
                parentId = input.parentId,
                name = input.name.trim(),
                iconName = input.iconName,
                colorIndex = colorIndex,
                archivedAt = null,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return id
    }

    suspend fun updateCategory(categoryId: String, input: CategoryInput) {
        validateCategory(input, categoryId)
        val existing = requireNotNull(database.categoryDao().getById(categoryId)) {
            "Category not found"
        }
        require(existing.type == input.type) { "Category type cannot be changed" }
        val colorIndex = input.parentId
            ?.let { parentId ->
                requireNotNull(
                    database.categoryDao().getById(parentId)
                ).colorIndex
            }
            ?: input.colorIndex.coerceIn(0, 11)
        val timestamp = clock()
        database.withTransaction {
            database.categoryDao().update(
                existing.copy(
                    parentId = input.parentId,
                    name = input.name.trim(),
                    iconName = input.iconName,
                    colorIndex = colorIndex,
                    updatedAt = timestamp,
                ),
            )
            if (input.parentId == null) {
                database.categoryDao().updateChildrenColor(categoryId, colorIndex, timestamp)
            }
        }
    }

    suspend fun archiveCategory(categoryId: String) {
        val category = requireNotNull(database.categoryDao().getById(categoryId)) {
            "Category not found"
        }
        val now = clock()
        database.withTransaction {
            database.categoryDao().setArchived(categoryId, now, now)
            if (category.parentId == null) {
                database.categoryDao().archiveActiveChildren(categoryId, now, now)
            }
        }
    }

    suspend fun restoreCategory(categoryId: String) {
        val category = requireNotNull(database.categoryDao().getById(categoryId)) {
            "Category not found"
        }
        category.parentId?.let { parentId ->
            require(database.categoryDao().getById(parentId)?.archivedAt == null) {
                "Restore the parent category first"
            }
        }
        val now = clock()
        database.withTransaction {
            database.categoryDao().setArchived(categoryId, null, now)
            if (category.parentId == null) {
                database.categoryDao().restoreChildrenArchivedByParent(categoryId, now)
            }
        }
    }

    suspend fun applyStarterCategories(language: StarterCategoryLanguage) {
        require(database.categoryDao().count() == 0) {
            "Starter categories can only be added to an empty list"
        }
        val now = clock()
        val palette = (0..11).shuffled()
        database.withTransaction {
            starterCategories(language).forEachIndexed { index, starter ->
                val parentId = UuidV7.generate()
                database.categoryDao().insert(
                    CategoryEntity(
                        id = parentId,
                        type = starter.type,
                        parentId = null,
                        name = starter.name,
                        iconName = starter.iconName,
                        colorIndex = palette[index % palette.size],
                        archivedAt = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                starter.children.forEach { (name, icon) ->
                    database.categoryDao().insert(
                        CategoryEntity(
                            id = UuidV7.generate(),
                            type = starter.type,
                            parentId = parentId,
                            name = name,
                            iconName = icon,
                            colorIndex = palette[index % palette.size],
                            archivedAt = null,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }
    }

    fun observeActiveAccounts(): Flow<List<AccountSummary>> =
        combine(
            database.accountDao().observeActive(),
            database.accountBalanceDao().observeAll(),
        ) { accounts, balances ->
            val balancesById = balances.associateBy { it.accountId }
            accounts.map { account ->
                account.toSummary(balancesById[account.id]?.balanceMinor)
            }
        }

    fun observeArchivedAccounts(): Flow<List<AccountSummary>> =
        combine(
            database.accountDao().observeAll(),
            database.accountBalanceDao().observeAll(),
        ) { accounts, balances ->
            val balancesById = balances.associateBy { it.accountId }
            accounts
                .filter { it.archivedAt != null }
                .sortedBy { it.name.lowercase() }
                .map { account ->
                    account.toSummary(balancesById[account.id]?.balanceMinor)
                }
        }

    fun observeAccount(accountId: String): Flow<AccountSummary?> =
        combine(
            database.accountDao().observeById(accountId),
            database.accountBalanceDao().observeByAccount(accountId),
        ) { account, balance ->
            account?.toSummary(balance?.balanceMinor)
        }

    fun observeRecurringRules(accountId: String): Flow<List<RecurringRuleSummary>> =
        database.recurringRuleDao().observeForAccount(accountId).map { rules ->
            rules.map {
                RecurringRuleSummary(
                    id = it.id,
                    amountMinor = it.amountMinor,
                    transactionType = it.transactionType,
                    description = it.description,
                    frequency = it.frequency,
                    intervalCount = it.intervalCount,
                    nextOccurrenceAt = it.nextOccurrenceAt,
                    isActive = it.isActive,
                    categoryId = it.categoryId,
                )
            }
        }

    fun activityPager(
        kind: ActivityKind?,
        accountId: String?,
    ): Flow<PagingData<ActivityItem>> = activityPager(
        ActivityFilter(kind = kind, accountId = accountId),
    )

    fun activityPager(filter: ActivityFilter): Flow<PagingData<ActivityItem>> = Pager(
        config = PagingConfig(
            pageSize = 40,
            initialLoadSize = 40,
            prefetchDistance = 10,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            database.activityDao().pagingSource(
                kind = filter.kind?.name,
                accountId = filter.accountId,
                currency = filter.currency,
                categoryId = filter.categoryId,
                fromDate = filter.fromDate,
                toDate = filter.toDate,
            )
        },
    ).flow.map { data -> data.map { it.toItem() } }

    fun observeDashboard(filter: DashboardFilter): Flow<DashboardSnapshot> {
        val selectedMonth = YearMonth.parse(filter.selectedMonth)
        val fromMonth = selectedMonth.minusMonths(6)
        return combine(
            database.activityDao().observeAnalytics(
                fromDate = fromMonth.atDay(1).toString(),
                toDate = selectedMonth.plusMonths(1).atDay(1).toString(),
                currency = filter.currency,
                accountId = filter.accountId,
            ),
            observeCategories(includeArchived = true),
        ) { records, categories ->
            val categoriesById = categories.associateBy(CategorySummary::id)
            val months = (5L downTo 0L).map { offset ->
                val month = selectedMonth.minusMonths(offset)
                records.monthSummary(month)
            }
            val selectedRecords =
                records.filter { YearMonth.from(LocalDate.parse(it.localDate)) == selectedMonth }
            val previousMonth = selectedMonth.minusMonths(1)
            val now = LocalDate.now()
            val comparableDay = if (selectedMonth == YearMonth.from(now)) {
                minOf(now.dayOfMonth, previousMonth.lengthOfMonth())
            } else {
                previousMonth.lengthOfMonth()
            }
            val previousComparable = records
                .filter {
                    val date = LocalDate.parse(it.localDate)
                    YearMonth.from(date) == previousMonth && date.dayOfMonth <= comparableDay
                }
                .monthSummary(previousMonth)
            DashboardSnapshot(
                filter = filter,
                months = months,
                selected = selectedRecords.monthSummary(selectedMonth),
                previousComparable = previousComparable,
                incomeCategories = selectedRecords.categoryShares(
                    TransactionType.INCOME,
                    categoriesById,
                ),
                expenseCategories = selectedRecords.categoryShares(
                    TransactionType.EXPENSE,
                    categoriesById,
                ),
            )
        }.flowOn(Dispatchers.Default)
    }

    suspend fun createAccount(input: AccountInput): String {
        validateAccount(input)
        val timestamp = clock()
        val id = UuidV7.generate()
        database.accountDao().insert(
            AccountEntity(
                id = id,
                name = input.name.trim(),
                description = input.description.normalized(),
                openingBalanceMinor = input.openingBalanceMinor,
                currency = input.currency,
                archivedAt = null,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return id
    }

    suspend fun updateAccount(accountId: String, input: AccountInput) {
        validateAccount(input)
        val existing = requireNotNull(database.accountDao().getById(accountId)) {
            "Account not found"
        }
        require(input.currency == existing.currency) {
            "Account currency cannot be changed"
        }
        database.accountDao().update(
            existing.copy(
                name = input.name.trim(),
                description = input.description.normalized(),
                openingBalanceMinor = input.openingBalanceMinor,
                updatedAt = clock(),
            ),
        )
    }

    suspend fun archiveAccount(accountId: String) {
        val timestamp = clock()
        database.withTransaction {
            requireNotNull(database.accountDao().getById(accountId)) {
                "Account not found"
            }
            database.recurringRuleDao().deactivateForAccount(accountId, timestamp)
            database.accountDao().setArchived(accountId, timestamp, timestamp)
        }
    }

    suspend fun restoreAccount(accountId: String) {
        requireNotNull(database.accountDao().getById(accountId)) {
            "Account not found"
        }
        val timestamp = clock()
        database.accountDao().setArchived(accountId, null, timestamp)
    }

    suspend fun getTransaction(id: String): TransactionEntity? =
        database.transactionDao().getById(id)

    suspend fun createTransaction(input: TransactionInput): String {
        validateTransaction(input)
        val account = requireActiveAccount(input.accountId)
        val timestamp = clock()
        val id = UuidV7.generate()
        database.transactionDao().insert(
            TransactionEntity(
                id = id,
                accountId = account.id,
                recurringRuleId = null,
                categoryId = input.categoryId,
                occurrenceKey = null,
                amountMinor = input.amountMinor,
                type = input.type,
                occurredAt = input.occurredAt,
                localDate = input.occurredAt.localDate(),
                description = input.description.normalized(),
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return id
    }

    suspend fun updateTransaction(id: String, input: TransactionInput) {
        val existing = requireNotNull(database.transactionDao().getById(id)) {
            "Transaction not found"
        }
        validateTransaction(input, existing.categoryId)
        requireActiveAccount(input.accountId)
        database.transactionDao().update(
            existing.copy(
                accountId = input.accountId,
                categoryId = input.categoryId,
                amountMinor = input.amountMinor,
                type = input.type,
                occurredAt = input.occurredAt,
                localDate = input.occurredAt.localDate(),
                description = input.description.normalized(),
                updatedAt = clock(),
            ),
        )
    }

    suspend fun deleteTransaction(id: String) {
        val existing = requireNotNull(database.transactionDao().getById(id)) {
            "Transaction not found"
        }
        database.transactionDao().delete(existing)
    }

    suspend fun getTransfer(id: String): TransferEntity? =
        database.transferDao().getById(id)

    suspend fun getTransferAccountSuggestions(): TransferAccountSuggestions =
        buildTransferAccountSuggestions(
            accounts = database.accountDao().observeActive().first(),
            usage = database.transferDao().getPairUsage(),
        )

    suspend fun createTransfer(input: TransferInput): String {
        validateTransfer(input)
        val timestamp = clock()
        val id = UuidV7.generate()
        database.transferDao().insert(input.toEntity(id, timestamp, timestamp))
        return id
    }

    suspend fun updateTransfer(id: String, input: TransferInput) {
        validateTransfer(input)
        val existing = requireNotNull(database.transferDao().getById(id)) {
            "Transfer not found"
        }
        database.transferDao().update(
            input.toEntity(id, existing.createdAt, clock()),
        )
    }

    suspend fun deleteTransfer(id: String) {
        val existing = requireNotNull(database.transferDao().getById(id)) {
            "Transfer not found"
        }
        database.transferDao().delete(existing)
    }

    suspend fun processDueRecurrences() {
        recurrenceProcessor.processDueRules()
    }

    suspend fun getRecurringRule(id: String): RecurringRuleEntity? =
        database.recurringRuleDao().getById(id)

    suspend fun createRecurringRule(input: RecurringRuleInput): String {
        validateRecurringRule(input)
        requireActiveAccount(input.accountId)
        val timestamp = clock()
        val id = UuidV7.generate()
        val zoneId = ZoneId.systemDefault()
        val occurrence = Instant.ofEpochMilli(input.nextOccurrenceAt).atZone(zoneId)
        database.recurringRuleDao().insert(
            RecurringRuleEntity(
                id = id,
                accountId = input.accountId,
                categoryId = input.categoryId,
                amountMinor = input.amountMinor,
                transactionType = input.transactionType,
                description = input.description.normalized(),
                frequency = input.frequency,
                intervalCount = input.intervalCount,
                timezoneId = zoneId.id,
                anchorDay = occurrence.anchorDay(input.frequency),
                anchorMonth = occurrence.anchorMonth(input.frequency),
                startAt = input.nextOccurrenceAt,
                lastGeneratedAt = null,
                nextOccurrenceAt = input.nextOccurrenceAt,
                isActive = true,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        processDueRecurrences()
        return id
    }

    suspend fun updateRecurringRule(id: String, input: RecurringRuleInput) {
        val existing = requireNotNull(database.recurringRuleDao().getById(id)) {
            "Scheduled transaction not found"
        }
        validateRecurringRule(input, existing.categoryId)
        requireActiveAccount(input.accountId)
        val zoneId = ZoneId.of(existing.timezoneId)
        val occurrence = Instant.ofEpochMilli(input.nextOccurrenceAt).atZone(zoneId)
        database.recurringRuleDao().update(
            existing.copy(
                accountId = input.accountId,
                categoryId = input.categoryId,
                amountMinor = input.amountMinor,
                transactionType = input.transactionType,
                description = input.description.normalized(),
                frequency = input.frequency,
                intervalCount = input.intervalCount,
                anchorDay = occurrence.anchorDay(input.frequency),
                anchorMonth = occurrence.anchorMonth(input.frequency),
                nextOccurrenceAt = input.nextOccurrenceAt,
                updatedAt = clock(),
            ),
        )
        if (existing.isActive) processDueRecurrences()
    }

    suspend fun pauseRecurringRule(id: String) {
        val existing = requireNotNull(database.recurringRuleDao().getById(id)) {
            "Scheduled transaction not found"
        }
        database.recurringRuleDao().update(
            existing.copy(isActive = false, updatedAt = clock()),
        )
    }

    suspend fun resumeRecurringRule(id: String) {
        var existing = requireNotNull(database.recurringRuleDao().getById(id)) {
            "Scheduled transaction not found"
        }
        requireActiveAccount(existing.accountId)
        val now = clock()
        while (existing.nextOccurrenceAt <= now) {
            existing = existing.copy(
                nextOccurrenceAt = RecurrenceCalculator.nextOccurrence(
                    existing,
                    existing.nextOccurrenceAt,
                ),
            )
        }
        database.recurringRuleDao().update(
            existing.copy(isActive = true, updatedAt = now),
        )
    }

    suspend fun deleteRecurringRule(id: String) {
        val existing = requireNotNull(database.recurringRuleDao().getById(id)) {
            "Scheduled transaction not found"
        }
        database.recurringRuleDao().delete(existing)
    }

    private suspend fun validateTransfer(input: TransferInput) {
        require(input.amountMinor > 0) { "Amount must be greater than zero" }
        require(input.occurredAt >= 0) { "Date is invalid" }
        require(input.fromAccountId != input.toAccountId) {
            "Choose two different accounts"
        }
        val source = requireActiveAccount(input.fromAccountId)
        val target = requireActiveAccount(input.toAccountId)
        require(source.currency == target.currency) {
            "Transfers require accounts with the same currency"
        }
    }

    private suspend fun requireActiveAccount(id: String): AccountEntity {
        val account = requireNotNull(database.accountDao().getById(id)) {
            "Account not found"
        }
        require(account.archivedAt == null) { "Archived accounts cannot be changed" }
        return account
    }

    private fun validateAccount(input: AccountInput) {
        require(input.name.isNotBlank()) { "Name is required" }
        require(input.currency in SupportedCurrencies) { "Unsupported currency" }
    }

    private suspend fun validateTransaction(
        input: TransactionInput,
        allowedArchivedCategoryId: String? = null,
    ) {
        require(input.amountMinor > 0) { "Amount must be greater than zero" }
        require(input.occurredAt >= 0) { "Date is invalid" }
        validateCategoryForType(input.categoryId, input.type, allowedArchivedCategoryId)
    }

    private suspend fun validateRecurringRule(
        input: RecurringRuleInput,
        allowedArchivedCategoryId: String? = null,
    ) {
        require(input.amountMinor > 0) { "Amount must be greater than zero" }
        require(input.intervalCount > 0) { "Interval must be greater than zero" }
        require(input.nextOccurrenceAt >= 0) { "Date is invalid" }
        validateCategoryForType(
            input.categoryId,
            input.transactionType,
            allowedArchivedCategoryId,
        )
    }

    private suspend fun validateCategory(input: CategoryInput, editingId: String? = null) {
        require(input.name.isNotBlank()) { "Name is required" }
        require(editingId == null || input.parentId != editingId) {
            "A category cannot be its own parent"
        }
        input.parentId?.let { parentId ->
            val parent = requireNotNull(database.categoryDao().getById(parentId)) {
                "Parent category not found"
            }
            require(parent.parentId == null) { "Only one subcategory level is supported" }
            require(parent.type == input.type) { "Parent category has a different type" }
            require(parent.archivedAt == null) { "Parent category is archived" }
            if (editingId != null) {
                require(database.categoryDao().getAll().none { it.parentId == editingId }) {
                    "A category with subcategories cannot become a subcategory"
                }
            }
        }
        val normalizedName = input.name.trim()
        require(
            database.categoryDao().getAll().none {
                it.id != editingId &&
                        it.type == input.type &&
                        it.parentId == input.parentId &&
                        it.name.equals(normalizedName, ignoreCase = true)
            },
        ) { "A category with this name already exists" }
    }

    private suspend fun validateCategoryForType(
        categoryId: String?,
        type: TransactionType,
        allowedArchivedCategoryId: String? = null,
    ) {
        if (categoryId == null) return
        val category = requireNotNull(database.categoryDao().getById(categoryId)) {
            "Category not found"
        }
        require(category.type == type) { "Category has a different type" }
        require(category.archivedAt == null || category.id == allowedArchivedCategoryId) {
            "Category is archived"
        }
    }

    private fun TransferInput.toEntity(
        id: String,
        createdAt: Long,
        updatedAt: Long,
    ) = TransferEntity(
        id = id,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amountMinor = amountMinor,
        occurredAt = occurredAt,
        localDate = occurredAt.localDate(),
        description = description.normalized(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Long.localDate(): String =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun java.time.ZonedDateTime.anchorDay(
        frequency: RecurrenceFrequency,
    ): Int? = when (frequency) {
        RecurrenceFrequency.MONTHLY, RecurrenceFrequency.YEARLY -> dayOfMonth
        else -> null
    }

    private fun java.time.ZonedDateTime.anchorMonth(
        frequency: RecurrenceFrequency,
    ): Int? = if (frequency == RecurrenceFrequency.YEARLY) monthValue else null

    private fun AccountEntity.toSummary(balanceMinor: Long?): AccountSummary =
        AccountSummary(
            id = id,
            name = name,
            description = description,
            openingBalanceMinor = openingBalanceMinor,
            balanceMinor = balanceMinor ?: openingBalanceMinor,
            currency = currency,
            archivedAt = archivedAt,
        )

    private fun ActivityRecord.toItem(): ActivityItem =
        ActivityItem(
            id = id,
            kind = ActivityKind.valueOf(kind),
            accountId = accountId,
            counterpartyAccountId = counterpartyAccountId,
            accountName = accountName,
            counterpartyAccountName = counterpartyAccountName,
            currency = currency,
            amountMinor = amountMinor,
            occurredAt = occurredAt,
            localDate = localDate,
            description = description,
            recurringRuleId = recurringRuleId,
            categoryId = categoryId,
            categoryParentId = categoryParentId,
            categoryName = categoryName,
            categoryIconName = categoryIconName,
            categoryColorIndex = categoryColorIndex,
        )

    private fun CategoryEntity.toSummary() = CategorySummary(
        id = id,
        type = type,
        parentId = parentId,
        name = name,
        iconName = iconName,
        colorIndex = colorIndex,
        archivedAt = archivedAt,
    )
}

private fun List<it.rfmariano.denaro.data.local.AnalyticsRecord>.monthSummary(
    month: YearMonth,
): MonthlyCashFlow {
    val monthlyRecords = filter { YearMonth.from(LocalDate.parse(it.localDate)) == month }
    return MonthlyCashFlow(
        month = month.toString(),
        incomeMinor = monthlyRecords.filter { it.transactionType == TransactionType.INCOME.name }
            .sumOf { it.amountMinor },
        expenseMinor = monthlyRecords.filter { it.transactionType == TransactionType.EXPENSE.name }
            .sumOf { it.amountMinor },
    )
}

private fun List<it.rfmariano.denaro.data.local.AnalyticsRecord>.categoryShares(
    type: TransactionType,
    categoriesById: Map<String, CategorySummary>,
): List<CategoryShare> = filter { it.transactionType == type.name }
    .groupBy { record ->
        record.categoryId?.let { id -> categoriesById[id]?.parentId ?: id }
    }
    .map { (categoryId, records) ->
        val category = categoryId?.let(categoriesById::get)
        CategoryShare(
            categoryId = categoryId,
            name = category?.name,
            iconName = category?.iconName,
            colorIndex = category?.colorIndex,
            amountMinor = records.sumOf { it.amountMinor },
            transactionCount = records.size,
        )
    }
    .sortedByDescending(CategoryShare::amountMinor)

internal fun buildTransferAccountSuggestions(
    accounts: List<AccountEntity>,
    usage: List<TransferPairUsage>,
): TransferAccountSuggestions {
    val compatibleDestinations = accounts.associate { source ->
        source.id to accounts.filter { candidate ->
            candidate.id != source.id && candidate.currency == source.currency
        }
    }
    val eligibleSources = accounts.filter { compatibleDestinations.getValue(it.id).isNotEmpty() }
    val sourceUsage = usage.groupBy(TransferPairUsage::fromAccountId)
    val preferredSource = eligibleSources.sortedWith(
        compareByDescending<AccountEntity> { source ->
            sourceUsage[source.id].orEmpty().sumOf(TransferPairUsage::transferCount)
        }.thenByDescending { source ->
            sourceUsage[source.id].orEmpty().maxOfOrNull(
                TransferPairUsage::lastOccurredAt,
            ) ?: Long.MIN_VALUE
        }.thenBy { it.name.lowercase() }
            .thenBy(AccountEntity::createdAt)
            .thenBy(AccountEntity::id),
    ).firstOrNull()

    val preferredDestinations = eligibleSources.mapNotNull { source ->
        val candidates = compatibleDestinations.getValue(source.id)
        val candidatesById = candidates.associateBy(AccountEntity::id)
        val usedCandidates = sourceUsage[source.id].orEmpty()
            .filter { it.toAccountId in candidatesById }
            .sortedWith(
                compareByDescending<TransferPairUsage> { it.transferCount }
                    .thenByDescending { it.lastOccurredAt }
                    .thenBy { candidatesById.getValue(it.toAccountId).name.lowercase() }
                    .thenBy(TransferPairUsage::toAccountId),
            )
        val destinationId = usedCandidates.firstOrNull()?.toAccountId
            ?: candidates.singleOrNull()?.id
        destinationId?.let { source.id to it }
    }.toMap()

    return TransferAccountSuggestions(
        preferredSourceId = preferredSource?.id,
        preferredDestinationIds = preferredDestinations,
    )
}
