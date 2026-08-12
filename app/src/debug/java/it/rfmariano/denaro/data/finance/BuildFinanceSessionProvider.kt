package it.rfmariano.denaro.data.finance

import android.content.Context
import androidx.room.withTransaction
import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.CategoryEntity
import it.rfmariano.denaro.data.local.CounterpartyEntity
import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.data.local.DebtEntity
import it.rfmariano.denaro.data.local.DebtRepaymentEntity
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.EncryptedDatabaseFactory
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.RecurringRuleEntity
import it.rfmariano.denaro.data.local.TransactionEntity
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.data.local.TransferEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal fun createBuildFinanceSessionProvider(context: Context): FinanceSessionProvider =
    DebugFinanceSessionProvider(context)

internal const val DEMO_DATABASE_NAME = "denaro_demo.db"

internal class DebugFinanceSessionProvider(
    private val context: Context,
    private val clock: () -> LocalDate = LocalDate::now,
) : FinanceSessionProvider {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _session = MutableStateFlow<FinanceSession?>(null)
    private var nextSessionId = 0L
    private var productionDatabase: DenaroDatabase? = null
    private var demoDatabase: DenaroDatabase? = null

    override val session: StateFlow<FinanceSession?> = _session.asStateFlow()

    override suspend fun initialize(localeTag: String) {
        mutex.withLock {
            if (_session.value != null) return
            val demoRequested = preferences.getBoolean(KEY_DEMO_ENABLED, false)
            val next = runCatching {
                if (demoRequested) demoSession(localeTag) else productionSession()
            }.getOrElse {
                preferences.edit().putBoolean(KEY_DEMO_ENABLED, false).apply()
                productionSession()
            }
            _session.value = next
        }
    }

    override suspend fun setDemoMode(enabled: Boolean, localeTag: String) {
        mutex.withLock {
            val current = _session.value
            if (!enabled && current?.isDemo == false) return
            val next = if (enabled) demoSession(localeTag) else productionSession()
            preferences.edit().putBoolean(KEY_DEMO_ENABLED, enabled).apply()
            _session.value = next
        }
    }

    override fun clearRecurrenceStartupFailure(sessionId: Long) {
        _session.update { current ->
            if (current?.id == sessionId && current.recurrenceStartupFailed) {
                current.copy(recurrenceStartupFailed = false)
            } else {
                current
            }
        }
    }

    internal suspend fun prepareCapture(
        localeTag: String,
        referenceDate: LocalDate,
    ): FinanceSession = mutex.withLock {
        demoSession(localeTag, referenceDate).also { _session.value = it }
    }

    private suspend fun productionSession(): FinanceSession = withContext(Dispatchers.IO) {
        val database = productionDatabase ?: EncryptedDatabaseFactory(context).open().also {
            productionDatabase = it
        }
        val repository = FinanceRepository(database)
        val recurrenceStartupFailed = runCatching { repository.processDueRecurrences() }.isFailure
        FinanceSession(
            id = ++nextSessionId,
            repository = repository,
            isDemo = false,
            initialDashboardMonth = YearMonth.from(clock()).toString(),
            recurrenceStartupFailed = recurrenceStartupFailed,
        )
    }

    private suspend fun demoSession(
        localeTag: String,
        referenceDate: LocalDate = clock(),
    ): FinanceSession = withContext(Dispatchers.IO) {
        val database = demoDatabase ?: EncryptedDatabaseFactory(
            context = context,
            databaseName = DEMO_DATABASE_NAME,
        ).open().also { demoDatabase = it }
        DemoDataSeeder(database).reset(
            referenceDate = referenceDate,
            zoneId = ZoneId.systemDefault(),
            italian = localeTag.startsWith("it", ignoreCase = true),
        )
        val repository = FinanceRepository(database)
        val recurrenceStartupFailed = runCatching { repository.processDueRecurrences() }.isFailure
        FinanceSession(
            id = ++nextSessionId,
            repository = repository,
            isDemo = true,
            initialDashboardMonth = YearMonth.from(referenceDate).minusMonths(1).toString(),
            recurrenceStartupFailed = recurrenceStartupFailed,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "denaro_debug_preferences"
        const val KEY_DEMO_ENABLED = "demo_enabled"
    }
}

internal class DemoDataSeeder(
    private val database: DenaroDatabase,
) {
    suspend fun reset(referenceDate: LocalDate, zoneId: ZoneId, italian: Boolean) {
        val fixture = demoFixture(referenceDate, zoneId, italian)
        database.withTransaction {
            val sqlite = database.openHelper.writableDatabase
            sqlite.execSQL("DELETE FROM transactions")
            sqlite.execSQL("DELETE FROM transfers")
            sqlite.execSQL("DELETE FROM debt_repayments")
            sqlite.execSQL("DELETE FROM debts")
            sqlite.execSQL("DELETE FROM counterparties")
            sqlite.execSQL("DELETE FROM recurring_rules")
            sqlite.execSQL("DELETE FROM categories")
            sqlite.execSQL("DELETE FROM accounts")
            sqlite.execSQL("DELETE FROM legacy_imports")
            database.accountDao().insertAll(fixture.accounts)
            database.categoryDao().insertAll(fixture.categories)
            database.recurringRuleDao().insertAll(fixture.rules)
            database.transactionDao().insertAll(fixture.transactions)
            database.transferDao().insertAll(fixture.transfers)
            fixture.counterparties.forEach { database.counterpartyDao().insert(it) }
            fixture.debts.forEach { database.debtDao().insert(it) }
            fixture.repayments.forEach { database.debtDao().insertRepayment(it) }
        }
    }
}

internal data class DemoFixture(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val rules: List<RecurringRuleEntity>,
    val transactions: List<TransactionEntity>,
    val transfers: List<TransferEntity>,
    val counterparties: List<CounterpartyEntity>,
    val debts: List<DebtEntity>,
    val repayments: List<DebtRepaymentEntity>,
)

internal fun demoFixture(
    referenceDate: LocalDate,
    zoneId: ZoneId,
    italian: Boolean,
): DemoFixture {
    val timestamp = referenceDate.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
    fun epoch(date: LocalDate) = date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
    fun text(en: String, it: String) = if (italian) it else en
    fun id(kind: String, suffix: String) = "demo-$kind-$suffix"

    val checkingId = id("account", "checking")
    val savingsId = id("account", "savings")
    val cashId = id("account", "cash")
    val accounts = listOf(
        AccountEntity(
            checkingId,
            text("Everyday", "Conto quotidiano"),
            text("Salary and daily spending", "Stipendio e spese quotidiane"),
            320_000,
            "EUR",
            null,
            timestamp,
            timestamp
        ),
        AccountEntity(
            savingsId,
            text("Savings", "Risparmi"),
            text("Long-term goals", "Obiettivi a lungo termine"),
            850_000,
            "EUR",
            null,
            timestamp + 1,
            timestamp + 1
        ),
        AccountEntity(
            cashId,
            text("Cash", "Contanti"),
            null,
            9_000,
            "EUR",
            null,
            timestamp + 2,
            timestamp + 2
        ),
    )

    data class CategorySpec(
        val key: String,
        val type: TransactionType,
        val parent: String?,
        val en: String,
        val it: String,
        val icon: String,
        val color: Int,
    )

    val specs = listOf(
        CategorySpec("home", TransactionType.EXPENSE, null, "Home", "Casa", "house", 0),
        CategorySpec("rent", TransactionType.EXPENSE, "home", "Rent", "Affitto", "house", 0),
        CategorySpec(
            "utilities",
            TransactionType.EXPENSE,
            "home",
            "Utilities",
            "Utenze",
            "lightbulb",
            0
        ),
        CategorySpec("food", TransactionType.EXPENSE, null, "Food", "Alimentazione", "utensils", 2),
        CategorySpec(
            "groceries",
            TransactionType.EXPENSE,
            "food",
            "Groceries",
            "Spesa",
            "shopping_basket",
            2
        ),
        CategorySpec(
            "dining",
            TransactionType.EXPENSE,
            "food",
            "Restaurants",
            "Ristoranti",
            "utensils",
            2
        ),
        CategorySpec(
            "transport",
            TransactionType.EXPENSE,
            null,
            "Transport",
            "Trasporti",
            "car",
            4
        ),
        CategorySpec("health", TransactionType.EXPENSE, null, "Health", "Salute", "heart_pulse", 6),
        CategorySpec(
            "services",
            TransactionType.EXPENSE,
            null,
            "Services",
            "Servizi",
            "receipt_text",
            8
        ),
        CategorySpec(
            "subscriptions",
            TransactionType.EXPENSE,
            "services",
            "Subscriptions",
            "Abbonamenti",
            "refresh_cw",
            8
        ),
        CategorySpec(
            "leisure",
            TransactionType.EXPENSE,
            null,
            "Leisure",
            "Tempo libero",
            "gamepad_2",
            10
        ),
        CategorySpec("travel", TransactionType.EXPENSE, "leisure", "Travel", "Viaggi", "plane", 10),
        CategorySpec(
            "work",
            TransactionType.INCOME,
            null,
            "Work",
            "Lavoro",
            "briefcase_business",
            1
        ),
        CategorySpec(
            "salary",
            TransactionType.INCOME,
            "work",
            "Salary",
            "Stipendio",
            "banknote",
            1
        ),
        CategorySpec(
            "freelance",
            TransactionType.INCOME,
            null,
            "Freelance",
            "Attività autonoma",
            "laptop",
            7
        ),
    )
    val categories = specs.mapIndexed { index, spec ->
        CategoryEntity(
            id = id("category", spec.key),
            type = spec.type,
            parentId = spec.parent?.let { id("category", it) },
            name = text(spec.en, spec.it),
            iconName = spec.icon,
            colorIndex = spec.color,
            archivedAt = null,
            createdAt = timestamp + index,
            updatedAt = timestamp + index,
        )
    }

    val transactions = mutableListOf<TransactionEntity>()
    val transfers = mutableListOf<TransferEntity>()
    var sequence = 0
    fun transaction(
        month: YearMonth,
        day: Int,
        amount: Long,
        type: TransactionType,
        category: String,
        descriptionEn: String,
        descriptionIt: String,
        accountId: String = checkingId
    ) {
        val date = month.atDay(day.coerceAtMost(month.lengthOfMonth()))
        val eventTime = epoch(date)
        transactions += TransactionEntity(
            id = id("transaction", "%04d".format(sequence++)),
            accountId = accountId,
            recurringRuleId = null,
            categoryId = id("category", category),
            occurrenceKey = null,
            amountMinor = amount,
            type = type,
            occurredAt = eventTime,
            localDate = date.toString(),
            description = text(descriptionEn, descriptionIt),
            createdAt = eventTime,
            updatedAt = eventTime,
        )
    }

    fun transfer(
        month: YearMonth,
        day: Int,
        amount: Long,
        from: String = checkingId,
        to: String = savingsId,
        label: String
    ) {
        val date = month.atDay(day.coerceAtMost(month.lengthOfMonth()))
        val eventTime = epoch(date)
        transfers += TransferEntity(
            id = id("transfer", "%03d".format(transfers.size)),
            fromAccountId = from,
            toAccountId = to,
            amountMinor = amount,
            occurredAt = eventTime,
            localDate = date.toString(),
            description = label,
            createdAt = eventTime,
            updatedAt = eventTime,
        )
    }

    val showcaseMonth = YearMonth.from(referenceDate).minusMonths(1)
    (5 downTo 0).forEach { offset ->
        val month = showcaseMonth.minusMonths(offset.toLong())
        val monthIndex = 5 - offset
        transaction(
            month,
            27,
            285_000,
            TransactionType.INCOME,
            "salary",
            "Monthly salary",
            "Stipendio mensile"
        )
        if (monthIndex % 2 == 0) {
            transaction(
                month,
                18,
                32_000,
                TransactionType.INCOME,
                "freelance",
                "Design project",
                "Progetto di design"
            )
        }
        transaction(month, 2, 92_000, TransactionType.EXPENSE, "rent", "Rent", "Affitto")
        listOf(4, 11, 18, 25).forEachIndexed { index, day ->
            transaction(
                month,
                day,
                7_200L + index * 450L,
                TransactionType.EXPENSE,
                "groceries",
                "Groceries",
                "Spesa"
            )
        }
        transaction(
            month,
            8,
            14_500,
            TransactionType.EXPENSE,
            "utilities",
            "Energy and internet",
            "Energia e internet"
        )
        transaction(
            month,
            12,
            8_900,
            TransactionType.EXPENSE,
            "transport",
            "Public transport",
            "Trasporto pubblico"
        )
        transaction(
            month,
            16,
            12_500,
            TransactionType.EXPENSE,
            "dining",
            "Dinner with friends",
            "Cena con amici"
        )
        transaction(
            month,
            20,
            1_499,
            TransactionType.EXPENSE,
            "subscriptions",
            "Music subscription",
            "Abbonamento musica"
        )
        transaction(month, 23, 4_200, TransactionType.EXPENSE, "health", "Pharmacy", "Farmacia")
        if (monthIndex == 1 || monthIndex == 4) {
            transaction(
                month,
                14,
                175_000,
                TransactionType.EXPENSE,
                "travel",
                "Weekend trip",
                "Viaggio nel weekend"
            )
        }
        transfer(month, 28, 25_000, label = text("Monthly savings", "Risparmio mensile"))
    }

    val currentMonth = YearMonth.from(referenceDate)
    if (referenceDate.dayOfMonth >= 1) {
        transaction(
            currentMonth,
            1,
            4_850,
            TransactionType.EXPENSE,
            "groceries",
            "Groceries",
            "Spesa"
        )
    }
    if (referenceDate.dayOfMonth >= 5) {
        transaction(
            currentMonth,
            5,
            3_200,
            TransactionType.EXPENSE,
            "transport",
            "Train tickets",
            "Biglietti del treno"
        )
    }
    if (referenceDate.dayOfMonth >= 15) {
        transaction(currentMonth, 15, 6_800, TransactionType.EXPENSE, "dining", "Lunch", "Pranzo")
    }
    if (referenceDate.dayOfMonth >= 27) {
        transaction(
            currentMonth,
            27,
            285_000,
            TransactionType.INCOME,
            "salary",
            "Monthly salary",
            "Stipendio mensile"
        )
    }

    val nextMonth = YearMonth.from(referenceDate).plusMonths(1)
    fun rule(
        key: String,
        amount: Long,
        type: TransactionType,
        category: String,
        day: Int,
        descriptionEn: String,
        descriptionIt: String,
        active: Boolean = true
    ): RecurringRuleEntity {
        val nextDate = if (referenceDate.dayOfMonth < day) {
            YearMonth.from(referenceDate)
                .atDay(day.coerceAtMost(YearMonth.from(referenceDate).lengthOfMonth()))
        } else {
            nextMonth.atDay(day.coerceAtMost(nextMonth.lengthOfMonth()))
        }
        return RecurringRuleEntity(
            id = id("rule", key),
            accountId = checkingId,
            categoryId = id("category", category),
            amountMinor = amount,
            transactionType = type,
            description = text(descriptionEn, descriptionIt),
            frequency = RecurrenceFrequency.MONTHLY,
            intervalCount = 1,
            timezoneId = zoneId.id,
            anchorDay = day,
            anchorMonth = null,
            startAt = epoch(showcaseMonth.atDay(day.coerceAtMost(showcaseMonth.lengthOfMonth()))),
            lastGeneratedAt = epoch(showcaseMonth.atDay(day.coerceAtMost(showcaseMonth.lengthOfMonth()))),
            nextOccurrenceAt = epoch(nextDate),
            isActive = active,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    val rules = listOf(
        rule(
            "salary",
            285_000,
            TransactionType.INCOME,
            "salary",
            27,
            "Monthly salary",
            "Stipendio mensile"
        ),
        rule("rent", 92_000, TransactionType.EXPENSE, "rent", 2, "Rent", "Affitto"),
        rule(
            "music",
            1_499,
            TransactionType.EXPENSE,
            "subscriptions",
            20,
            "Music subscription",
            "Abbonamento musica"
        ),
        rule(
            "gym",
            3_900,
            TransactionType.EXPENSE,
            "health",
            10,
            "Gym membership",
            "Abbonamento palestra",
            active = false
        ),
    )

    val counterparties = listOf(
        CounterpartyEntity(
            id("counterparty", "alex"),
            "Alex",
            text("Friend", "Amico"),
            null,
            timestamp,
            timestamp
        ),
        CounterpartyEntity(
            id("counterparty", "studio"),
            text("Design studio", "Studio di design"),
            null,
            null,
            timestamp + 1,
            timestamp + 1
        ),
    )
    val borrowedDate = showcaseMonth.atDay(5)
    val lentDate = showcaseMonth.atDay(12)
    val debts = listOf(
        DebtEntity(
            id("debt", "alex"),
            id("counterparty", "alex"),
            checkingId,
            DebtDirection.BORROWED,
            60_000,
            "EUR",
            epoch(borrowedDate),
            borrowedDate.toString(),
            referenceDate.plusDays(10).toString(),
            text("Shared trip", "Viaggio insieme"),
            timestamp,
            timestamp
        ),
        DebtEntity(
            id("debt", "studio"),
            id("counterparty", "studio"),
            checkingId,
            DebtDirection.LENT,
            35_000,
            "EUR",
            epoch(lentDate),
            lentDate.toString(),
            null,
            text("Equipment deposit", "Deposito attrezzatura"),
            timestamp + 1,
            timestamp + 1
        ),
    )
    val repaymentDate = showcaseMonth.atDay(20)
    val repayments = listOf(
        DebtRepaymentEntity(
            id("repayment", "alex-1"),
            id("debt", "alex"),
            checkingId,
            15_000,
            epoch(repaymentDate),
            repaymentDate.toString(),
            null,
            timestamp,
            timestamp
        ),
    )

    return DemoFixture(
        accounts,
        categories,
        rules,
        transactions,
        transfers,
        counterparties,
        debts,
        repayments
    )
}
