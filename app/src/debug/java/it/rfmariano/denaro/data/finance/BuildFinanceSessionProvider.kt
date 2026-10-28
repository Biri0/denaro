package it.rfmariano.denaro.data.finance

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import it.rfmariano.denaro.data.backup.DenaroBackupService
import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.BudgetEntity
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
import it.rfmariano.denaro.data.local.clearFinanceRecords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

internal fun createBuildFinanceSessionProvider(context: Context): FinanceSessionProvider =
    DebugFinanceSessionProvider(context)

internal const val DEMO_DATABASE_NAME = "denaro_demo.db"

private const val LOG_TAG = "DenaroDemo"

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
            }.getOrElse { error ->
                Log.w(LOG_TAG, "Could not start the stored data source, using real data", error)
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
            val next = try {
                if (enabled) demoSession(localeTag) else productionSession()
            } catch (error: Throwable) {
                Log.w(LOG_TAG, "Could not change demo mode", error)
                throw error
            }
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
        demoSession(
            localeTag,
            referenceDate,
            recurrenceCutoff = endOfDay(referenceDate),
        ).also { _session.value = it }
    }

    private fun endOfDay(date: LocalDate): Instant = date
        .atTime(LocalTime.MAX)
        .atZone(ZoneId.systemDefault())
        .toInstant()

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
            backupService = DenaroBackupService(
                database,
                installedAppVersion(context),
                context.cacheDir,
            ),
        )
    }

    private suspend fun demoSession(
        localeTag: String,
        referenceDate: LocalDate = clock(),
        recurrenceCutoff: Instant? = null,
    ): FinanceSession = withContext(Dispatchers.IO) {
        val database = resetDemoDatabase(localeTag, referenceDate)
        val repository = FinanceRepository(database)
        // Marketing captures pass the reference date's end of day: a rule due after --date must
        // stay ungenerated no matter when the capture runs. Processing against the wall clock made
        // the same --date gain salary/rent rows from every month elapsed since then.
        val recurrenceStartupFailed = runCatching {
            if (recurrenceCutoff == null) {
                repository.processDueRecurrences()
            } else {
                repository.processDueRecurrences(recurrenceCutoff)
            }
        }.isFailure
        FinanceSession(
            id = ++nextSessionId,
            repository = repository,
            isDemo = true,
            initialDashboardMonth = YearMonth.from(referenceDate).minusMonths(1).toString(),
            recurrenceStartupFailed = recurrenceStartupFailed,
            backupService = DenaroBackupService(
                database,
                installedAppVersion(context),
                context.cacheDir,
            ),
        )
    }

    /**
     * Regenerates the demo database. Because demo data is disposable, a database that cannot be
     * reset — a stale file left behind by an older build, or a half-written one — is deleted and
     * recreated once instead of leaving demo mode permanently unavailable.
     */
    private suspend fun resetDemoDatabase(
        localeTag: String,
        referenceDate: LocalDate,
    ): DenaroDatabase = runCatching { seedDemoDatabase(localeTag, referenceDate) }
        .getOrElse { firstFailure ->
            if (firstFailure is CancellationException) throw firstFailure
            Log.w(LOG_TAG, "Could not reset the demo database, recreating it", firstFailure)
            deleteDemoDatabase()
            runCatching { seedDemoDatabase(localeTag, referenceDate) }
                .getOrElse { secondFailure ->
                    if (secondFailure is CancellationException) throw secondFailure
                    deleteDemoDatabase()
                    secondFailure.addSuppressed(firstFailure)
                    throw secondFailure
                }
        }

    private suspend fun seedDemoDatabase(
        localeTag: String,
        referenceDate: LocalDate,
    ): DenaroDatabase {
        val database = demoDatabase ?: EncryptedDatabaseFactory(
            context = context,
            databaseName = DEMO_DATABASE_NAME,
        ).open().also { demoDatabase = it }
        DemoDataSeeder(database).reset(
            referenceDate = referenceDate,
            zoneId = ZoneId.systemDefault(),
            language = demoLanguage(localeTag),
        )
        return database
    }

    private fun deleteDemoDatabase() {
        demoDatabase?.close()
        demoDatabase = null
        // Only the demo file: EncryptedDatabaseFactory.deleteDatabaseAndKey() would also drop the
        // key material shared with the real database.
        context.deleteDatabase(DEMO_DATABASE_NAME)
    }

    private companion object {
        const val PREFERENCES_NAME = "denaro_debug_preferences"
        const val KEY_DEMO_ENABLED = "demo_enabled"
    }
}

internal enum class DemoLanguage {
    ENGLISH,
    ITALIAN,
    SPANISH,
}

internal fun demoLanguage(localeTag: String): DemoLanguage =
    when (Locale.forLanguageTag(localeTag).language) {
        "it" -> DemoLanguage.ITALIAN
        "es" -> DemoLanguage.SPANISH
        else -> DemoLanguage.ENGLISH
    }

internal class DemoDataSeeder(
    private val database: DenaroDatabase,
) {
    suspend fun reset(
        referenceDate: LocalDate,
        zoneId: ZoneId,
        language: DemoLanguage = DemoLanguage.ENGLISH,
    ) {
        val fixture = demoFixture(referenceDate, zoneId, language)
        database.withTransaction {
            database.clearFinanceRecords()
            database.openHelper.writableDatabase.execSQL("DELETE FROM legacy_imports")
            database.accountDao().insertAll(fixture.accounts)
            database.categoryDao().insertAll(fixture.categories)
            fixture.budgets.forEach { database.budgetDao().insert(it) }
            database.recurringRuleDao().insertAll(fixture.rules)
            database.transactionDao().insertAll(fixture.transactions)
            database.transferDao().insertAll(fixture.transfers)
            fixture.counterparties.forEach { database.counterpartyDao().insert(it) }
            fixture.debts.forEach { database.debtDao().insert(it) }
            fixture.repayments.forEach { database.debtDao().insertRepayment(it) }
        }
    }

    suspend fun reset(referenceDate: LocalDate, zoneId: ZoneId, italian: Boolean) {
        reset(
            referenceDate = referenceDate,
            zoneId = zoneId,
            language = if (italian) DemoLanguage.ITALIAN else DemoLanguage.ENGLISH,
        )
    }
}

internal data class DemoFixture(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val budgets: List<BudgetEntity>,
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
): DemoFixture = demoFixture(
    referenceDate = referenceDate,
    zoneId = zoneId,
    language = if (italian) DemoLanguage.ITALIAN else DemoLanguage.ENGLISH,
)

internal fun demoFixture(
    referenceDate: LocalDate,
    zoneId: ZoneId,
    language: DemoLanguage = DemoLanguage.ENGLISH,
): DemoFixture {
    val timestamp = referenceDate.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
    fun epoch(date: LocalDate) = date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
    fun text(en: String, it: String, es: String) = when (language) {
        DemoLanguage.ENGLISH -> en
        DemoLanguage.ITALIAN -> it
        DemoLanguage.SPANISH -> es
    }
    fun id(kind: String, suffix: String) = "demo-$kind-$suffix"

    val checkingId = id("account", "checking")
    val savingsId = id("account", "savings")
    val cashId = id("account", "cash")
    val accounts = listOf(
        AccountEntity(
            checkingId,
            text("Everyday", "Conto quotidiano", "Cuenta diaria"),
            text(
                "Salary and daily spending",
                "Stipendio e spese quotidiane",
                "Salario y gastos diarios"
            ),
            320_000,
            "EUR",
            null,
            timestamp,
            timestamp
        ),
        AccountEntity(
            id = savingsId,
            name = text("Savings", "Risparmi", "Ahorros"),
            description = text(
                "Long-term goals",
                "Obiettivi a lungo termine",
                "Objetivos a largo plazo"
            ),
            openingBalanceMinor = 850_000,
            currency = "EUR",
            archivedAt = null,
            createdAt = timestamp + 1,
            updatedAt = timestamp + 1,
            isSavings = true,
            savingsTargetMinor = 1_500_000,
        ),
        AccountEntity(
            cashId,
            text("Cash", "Contanti", "Efectivo"),
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
        val es: String,
        val icon: String,
        val color: Int,
    )

    val specs = listOf(
        CategorySpec("home", TransactionType.EXPENSE, null, "Home", "Casa", "Hogar", "house", 0),
        CategorySpec(
            "rent",
            TransactionType.EXPENSE,
            "home",
            "Rent",
            "Affitto",
            "Alquiler",
            "house",
            0
        ),
        CategorySpec(
            "utilities",
            TransactionType.EXPENSE,
            "home",
            "Utilities",
            "Utenze",
            "Servicios básicos",
            "lightbulb",
            0
        ),
        CategorySpec(
            "food",
            TransactionType.EXPENSE,
            null,
            "Food",
            "Alimentazione",
            "Alimentación",
            "utensils",
            2
        ),
        CategorySpec(
            "groceries",
            TransactionType.EXPENSE,
            "food",
            "Groceries",
            "Spesa",
            "Supermercado",
            "shopping_basket",
            2
        ),
        CategorySpec(
            "dining",
            TransactionType.EXPENSE,
            "food",
            "Restaurants",
            "Ristoranti",
            "Restaurantes",
            "utensils",
            2
        ),
        CategorySpec(
            "transport",
            TransactionType.EXPENSE,
            null,
            "Transport",
            "Trasporti",
            "Transporte",
            "car",
            4
        ),
        CategorySpec(
            "health",
            TransactionType.EXPENSE,
            null,
            "Health",
            "Salute",
            "Salud",
            "heart_pulse",
            6
        ),
        CategorySpec(
            "services",
            TransactionType.EXPENSE,
            null,
            "Services",
            "Servizi",
            "Servicios",
            "receipt_text",
            8
        ),
        CategorySpec(
            "subscriptions",
            TransactionType.EXPENSE,
            "services",
            "Subscriptions",
            "Abbonamenti",
            "Suscripciones",
            "refresh_cw",
            8
        ),
        CategorySpec(
            "leisure",
            TransactionType.EXPENSE,
            null,
            "Leisure",
            "Tempo libero",
            "Ocio",
            "gamepad_2",
            10
        ),
        CategorySpec(
            "travel",
            TransactionType.EXPENSE,
            "leisure",
            "Travel",
            "Viaggi",
            "Viajes",
            "plane",
            10
        ),
        CategorySpec(
            "work",
            TransactionType.INCOME,
            null,
            "Work",
            "Lavoro",
            "Trabajo",
            "briefcase_business",
            1
        ),
        CategorySpec(
            "salary",
            TransactionType.INCOME,
            "work",
            "Salary",
            "Stipendio",
            "Salario",
            "banknote",
            1
        ),
        CategorySpec(
            "freelance",
            TransactionType.INCOME,
            null,
            "Freelance",
            "Attività autonoma",
            "Trabajo autónomo",
            "laptop",
            7
        ),
    )
    val categories = specs.mapIndexed { index, spec ->
        CategoryEntity(
            id = id("category", spec.key),
            type = spec.type,
            parentId = spec.parent?.let { id("category", it) },
            name = text(spec.en, spec.it, spec.es),
            iconName = spec.icon,
            colorIndex = spec.color,
            archivedAt = null,
            createdAt = timestamp + index,
            updatedAt = timestamp + index,
        )
    }

    fun budget(category: String, amountMinor: Long) = BudgetEntity(
        id = id("budget", category),
        categoryId = id("category", category),
        currency = "EUR",
        amountMinor = amountMinor,
        createdAt = timestamp,
        updatedAt = timestamp,
    )

    // Budgets must target top-level expense categories: FinanceRepository.validateBudget rejects
    // subcategories, and BudgetDao.observeSpendByCategory rolls child spending up to the parent
    // (COALESCE(parent.id, category.id)), so a budget on Rent or Groceries would never accrue a
    // cent. Amounts sit just above what a completed showcase month spends on the same rollup,
    // except Leisure, which the trip in the opening month deliberately overshoots.
    val budgets = listOf(
        budget("home", 115_000),
        budget("food", 50_000),
        budget("transport", 12_000),
        budget("health", 5_000),
        budget("leisure", 4_000),
    )

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
        descriptionEs: String,
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
            description = text(descriptionEn, descriptionIt, descriptionEs),
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
    // The dashboard queries from selectedMonth - 6 while rendering only six months of it, so one
    // month beyond the rendered window is generated as well: stepping back from the month demo
    // opens on keeps its oldest bar populated instead of showing an empty February.
    (6 downTo 0).forEach { offset ->
        val month = showcaseMonth.minusMonths(offset.toLong())
        transaction(
            month,
            27,
            285_000,
            TransactionType.INCOME,
            "salary",
            "Monthly salary",
            "Stipendio mensile",
            "Salario mensual"
        )
        if (offset % 2 == 1) {
            transaction(
                month,
                18,
                32_000,
                TransactionType.INCOME,
                "freelance",
                "Design project",
                "Progetto di design",
                "Proyecto de diseño"
            )
        }
        transaction(
            month,
            2,
            92_000,
            TransactionType.EXPENSE,
            "rent",
            "Rent",
            "Affitto",
            "Alquiler"
        )
        listOf(4, 11, 18, 25).forEachIndexed { index, day ->
            transaction(
                month,
                day,
                7_200L + index * 450L,
                TransactionType.EXPENSE,
                "groceries",
                "Groceries",
                "Spesa",
                "Supermercado"
            )
        }
        transaction(
            month,
            8,
            14_500,
            TransactionType.EXPENSE,
            "utilities",
            "Energy and internet",
            "Energia e internet",
            "Energía e internet"
        )
        transaction(
            month,
            12,
            8_900,
            TransactionType.EXPENSE,
            "transport",
            "Public transport",
            "Trasporto pubblico",
            "Transporte público"
        )
        transaction(
            month,
            16,
            12_500,
            TransactionType.EXPENSE,
            "dining",
            "Dinner with friends",
            "Cena con amici",
            "Cena con amigos"
        )
        transaction(
            month,
            19,
            1_800,
            TransactionType.EXPENSE,
            "leisure",
            "Movie night",
            "Serata al cinema",
            "Noche de cine"
        )
        transaction(
            month,
            20,
            1_499,
            TransactionType.EXPENSE,
            "subscriptions",
            "Music subscription",
            "Abbonamento musica",
            "Suscripción de música"
        )
        transaction(
            month,
            23,
            4_200,
            TransactionType.EXPENSE,
            "health",
            "Pharmacy",
            "Farmacia",
            "Farmacia"
        )
        // The month demo opens on gets a modest weekend away instead of a full holiday: it is
        // still enough to push Leisure past its budget — the over-budget card is the point of the
        // first screen — but not enough to make that month the deficit one, because Home doubles
        // as the hero shot on the README and the store listing. The two proper holidays sit two
        // and five months back, which is exactly two deficit months inside the six the dashboard
        // renders (DemoFixtureTest.completedWindowContainsExactlyTwoDeficitMonths).
        if (offset == 0) {
            transaction(
                month,
                14,
                40_000,
                TransactionType.EXPENSE,
                "travel",
                "Seaside weekend",
                "Weekend al mare",
                "Escapada a la playa"
            )
        }
        if (offset == 2 || offset == 5) {
            transaction(
                month,
                14,
                175_000,
                TransactionType.EXPENSE,
                "travel",
                "Weekend trip",
                "Viaggio nel weekend",
                "Viaje de fin de semana"
            )
        }
        transfer(
            month,
            28,
            25_000,
            label = text("Monthly savings", "Risparmio mensile", "Ahorro mensual")
        )
    }

    // Every category a showcase month spends on gets a day-gated current-month counterpart, so no
    // budget reads 0% once the month demo opens on is stepped forward to today.
    val currentMonth = YearMonth.from(referenceDate)
    if (referenceDate.dayOfMonth >= 1) {
        transaction(
            currentMonth,
            1,
            4_850,
            TransactionType.EXPENSE,
            "groceries",
            "Groceries",
            "Spesa",
            "Supermercado"
        )
    }
    if (referenceDate.dayOfMonth >= 2) {
        transaction(
            currentMonth,
            2,
            92_000,
            TransactionType.EXPENSE,
            "rent",
            "Rent",
            "Affitto",
            "Alquiler"
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
            "Biglietti del treno",
            "Billetes de tren"
        )
    }
    if (referenceDate.dayOfMonth >= 8) {
        transaction(
            currentMonth,
            8,
            14_500,
            TransactionType.EXPENSE,
            "utilities",
            "Energy and internet",
            "Energia e internet",
            "Energía e internet"
        )
    }
    if (referenceDate.dayOfMonth >= 15) {
        transaction(
            currentMonth,
            15,
            6_800,
            TransactionType.EXPENSE,
            "dining",
            "Lunch",
            "Pranzo",
            "Almuerzo"
        )
    }
    if (referenceDate.dayOfMonth >= 19) {
        transaction(
            currentMonth,
            19,
            1_800,
            TransactionType.EXPENSE,
            "leisure",
            "Movie night",
            "Serata al cinema",
            "Noche de cine"
        )
    }
    if (referenceDate.dayOfMonth >= 20) {
        transaction(
            currentMonth,
            20,
            1_499,
            TransactionType.EXPENSE,
            "subscriptions",
            "Music subscription",
            "Abbonamento musica",
            "Suscripción de música"
        )
    }
    if (referenceDate.dayOfMonth >= 23) {
        transaction(
            currentMonth,
            23,
            4_200,
            TransactionType.EXPENSE,
            "health",
            "Pharmacy",
            "Farmacia",
            "Farmacia"
        )
    }
    if (referenceDate.dayOfMonth >= 27) {
        transaction(
            currentMonth,
            27,
            285_000,
            TransactionType.INCOME,
            "salary",
            "Monthly salary",
            "Stipendio mensile",
            "Salario mensual"
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
        descriptionEs: String,
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
            description = text(descriptionEn, descriptionIt, descriptionEs),
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
            "Stipendio mensile",
            "Salario mensual"
        ),
        rule(
            "rent",
            92_000,
            TransactionType.EXPENSE,
            "rent",
            2,
            "Rent",
            "Affitto",
            "Alquiler"
        ),
        rule(
            "music",
            1_499,
            TransactionType.EXPENSE,
            "subscriptions",
            20,
            "Music subscription",
            "Abbonamento musica",
            "Suscripción de música"
        ),
        rule(
            "gym",
            3_900,
            TransactionType.EXPENSE,
            "health",
            10,
            "Gym membership",
            "Abbonamento palestra",
            "Cuota del gimnasio",
            active = false
        ),
    )

    val counterparties = listOf(
        CounterpartyEntity(
            id("counterparty", "alex"),
            "Alex",
            text("Friend", "Amico", "Amigo"),
            null,
            timestamp,
            timestamp
        ),
        CounterpartyEntity(
            id("counterparty", "studio"),
            text("Design studio", "Studio di design", "Estudio de diseño"),
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
            text("Shared trip", "Viaggio insieme", "Viaje compartido"),
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
            text("Equipment deposit", "Deposito attrezzatura", "Depósito de equipo"),
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
        budgets,
        rules,
        transactions,
        transfers,
        counterparties,
        debts,
        repayments
    )
}
