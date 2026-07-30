package it.rfmariano.denaro.data.finance

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.ActivityRecord
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.RecurringRuleEntity
import it.rfmariano.denaro.data.local.TransactionEntity
import it.rfmariano.denaro.data.local.TransferEntity
import it.rfmariano.denaro.data.local.TransferPairUsage
import it.rfmariano.denaro.data.local.UuidV7
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

class FinanceRepository(
    private val database: DenaroDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val recurrenceProcessor = RecurrenceProcessor(database, clock)

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
                )
            }
        }

    fun observeRecentActivity(limit: Int = 8): Flow<List<ActivityItem>> =
        database.activityDao().observeRecent(limit).map { records ->
            records.map { it.toItem() }
        }

    fun activityPager(
        kind: ActivityKind?,
        accountId: String?,
    ): Flow<PagingData<ActivityItem>> = Pager(
        config = PagingConfig(
            pageSize = 40,
            initialLoadSize = 40,
            prefetchDistance = 10,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            database.activityDao().pagingSource(kind?.name, accountId)
        },
    ).flow.map { data -> data.map { it.toItem() } }

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
        validateTransaction(input)
        requireActiveAccount(input.accountId)
        val existing = requireNotNull(database.transactionDao().getById(id)) {
            "Transaction not found"
        }
        database.transactionDao().update(
            existing.copy(
                accountId = input.accountId,
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
        validateRecurringRule(input)
        requireActiveAccount(input.accountId)
        val existing = requireNotNull(database.recurringRuleDao().getById(id)) {
            "Scheduled transaction not found"
        }
        val zoneId = ZoneId.of(existing.timezoneId)
        val occurrence = Instant.ofEpochMilli(input.nextOccurrenceAt).atZone(zoneId)
        database.recurringRuleDao().update(
            existing.copy(
                accountId = input.accountId,
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

    private fun validateTransaction(input: TransactionInput) {
        require(input.amountMinor > 0) { "Amount must be greater than zero" }
        require(input.occurredAt >= 0) { "Date is invalid" }
    }

    private fun validateRecurringRule(input: RecurringRuleInput) {
        require(input.amountMinor > 0) { "Amount must be greater than zero" }
        require(input.intervalCount > 0) { "Interval must be greater than zero" }
        require(input.nextOccurrenceAt >= 0) { "Date is invalid" }
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
        )
}

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
