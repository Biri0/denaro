package it.rfmariano.denaro.data.backup

import android.util.Base64
import androidx.room.withTransaction
import it.rfmariano.denaro.data.finance.CurrencyCatalog
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.BalanceAdjustmentEntity
import it.rfmariano.denaro.data.local.CategoryEntity
import it.rfmariano.denaro.data.local.CounterpartyEntity
import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.data.local.DebtEntity
import it.rfmariano.denaro.data.local.DebtRepaymentEntity
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.RecurringRuleEntity
import it.rfmariano.denaro.data.local.TransactionEntity
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.data.local.TransferEntity
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class BackupProtection { PASSWORD, NONE }

data class BackupRecordCounts(
    val accounts: Int,
    val categories: Int,
    val recurringRules: Int,
    val transactions: Int,
    val balanceAdjustments: Int,
    val transfers: Int,
    val counterparties: Int,
    val debts: Int,
    val debtRepayments: Int,
) {
    val total: Int
        get() = accounts + categories + recurringRules + transactions + balanceAdjustments +
                transfers + counterparties + debts + debtRepayments
}

data class BackupPreview(
    val createdAt: Long,
    val appVersion: String,
    val protection: BackupProtection,
    val counts: BackupRecordCounts,
)

data class BackupInspection(
    val preview: BackupPreview,
    val currentCounts: BackupRecordCounts,
    val hasFinanceChanges: Boolean,
    val contentDigest: String,
)

sealed class BackupException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class PasswordRequired : BackupException("This backup requires a password")
    class EmptyPassword : BackupException("The backup password cannot be empty")
    class AuthenticationFailed(cause: Throwable? = null) :
        BackupException("The password is incorrect or the backup is damaged", cause)

    class InvalidBackup(message: String, cause: Throwable? = null) : BackupException(message, cause)
    class UnsupportedVersion(val version: Int) :
        BackupException("Backup version $version is newer than this app supports")
}

class DenaroBackupService(
    private val database: DenaroDatabase,
    private val appVersion: String,
    private val cacheDirectory: File = File(requireNotNull(System.getProperty("java.io.tmpdir"))),
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    init {
        BackupTemporaryFiles.deleteStale(cacheDirectory)
    }

    suspend fun currentCounts(): BackupRecordCounts = database.withTransaction {
        val dao = database.backupDao()
        BackupRecordCounts(
            accounts = dao.accounts().size,
            categories = dao.categories().size,
            recurringRules = dao.recurringRules().size,
            transactions = dao.transactions().size,
            balanceAdjustments = dao.balanceAdjustments().size,
            transfers = dao.transfers().size,
            counterparties = dao.counterparties().size,
            debts = dao.debts().size,
            debtRepayments = dao.debtRepayments().size,
        )
    }

    suspend fun create(
        output: OutputStream,
        password: CharArray?,
    ): BackupPreview {
        if (password != null && password.isEmpty()) {
            throw BackupException.EmptyPassword()
        }
        val payload = database.withTransaction { snapshot() }
        if (payload.counts().total > MAX_RECORDS) invalid("Backup contains too many records")
        val protection = if (password == null) BackupProtection.NONE else BackupProtection.PASSWORD
        writeEnvelope(output, payload, protection, password)
        return payload.preview(protection)
    }

    suspend fun inspect(input: InputStream, password: CharArray?): BackupInspection {
        val decoded = readEnvelope(input, password)
        validate(decoded.payload)
        val upgraded = decoded.payload.upgradedToCurrentVersion()
        val current = database.withTransaction { snapshot() }
        return BackupInspection(
            preview = upgraded.preview(decoded.protection),
            currentCounts = current.counts(),
            hasFinanceChanges = !upgraded.sameFinanceDataAs(current),
            contentDigest = decoded.contentDigest.base64(),
        )
    }

    suspend fun restore(
        input: InputStream,
        password: CharArray?,
        expectedContentDigest: String,
        postRestoreInTransaction: suspend () -> Unit,
    ) {
        val decoded = readEnvelope(input, password)
        val expectedDigest = expectedContentDigest.decodeBase64()
        if (!MessageDigest.isEqual(expectedDigest, decoded.contentDigest)) {
            throw BackupException.InvalidBackup("The backup changed after it was inspected")
        }
        validate(decoded.payload)
        val payload = decoded.payload.upgradedToCurrentVersion()
        database.withTransaction {
            deleteFinanceData()
            val dao = database.backupDao()

            payload.accounts.map { it.toEntity(payload.schemaVersion) }.also {
                if (it.isNotEmpty()) database.accountDao().insertAll(it)
            }
            topologicallySortedCategories(payload.categories).map(BackupCategory::toEntity).also {
                if (it.isNotEmpty()) database.categoryDao().insertAll(it)
            }
            payload.counterparties.map(BackupCounterparty::toEntity).also {
                if (it.isNotEmpty()) dao.insertCounterparties(it)
            }
            payload.recurringRules.map(BackupRecurringRule::toEntity).also {
                if (it.isNotEmpty()) database.recurringRuleDao().insertAll(it)
            }
            payload.transactions.map(BackupTransaction::toEntity).also {
                if (it.isNotEmpty()) database.transactionDao().insertAll(it)
            }
            payload.balanceAdjustments.map(BackupBalanceAdjustment::toEntity).also {
                if (it.isNotEmpty()) dao.insertBalanceAdjustments(it)
            }
            payload.transfers.map(BackupTransfer::toEntity).also {
                if (it.isNotEmpty()) database.transferDao().insertAll(it)
            }
            payload.debts.map(BackupDebt::toEntity).also {
                if (it.isNotEmpty()) dao.insertDebts(it)
            }
            payload.debtRepayments.map(BackupDebtRepayment::toEntity).also {
                if (it.isNotEmpty()) dao.insertDebtRepayments(it)
            }
            postRestoreInTransaction()
        }
    }

    suspend fun eraseFinanceData() {
        database.withTransaction { deleteFinanceData() }
    }

    private suspend fun deleteFinanceData() {
        val dao = database.backupDao()
        dao.deleteDebtRepayments()
        dao.deleteDebts()
        dao.deleteTransactions()
        dao.deleteTransfers()
        dao.deleteBalanceAdjustments()
        dao.deleteRecurringRules()
        dao.deleteCategories()
        dao.deleteCounterparties()
        dao.deleteAccounts()
    }

    private suspend fun snapshot(): BackupPayload {
        val dao = database.backupDao()
        return BackupPayload(
            schemaVersion = PAYLOAD_VERSION,
            createdAt = now(),
            appVersion = appVersion,
            accounts = dao.accounts().map(BackupAccount::from),
            categories = dao.categories().map(BackupCategory::from),
            recurringRules = dao.recurringRules().map(BackupRecurringRule::from),
            transactions = dao.transactions().map(BackupTransaction::from),
            balanceAdjustments = dao.balanceAdjustments().map(BackupBalanceAdjustment::from),
            transfers = dao.transfers().map(BackupTransfer::from),
            counterparties = dao.counterparties().map(BackupCounterparty::from),
            debts = dao.debts().map(BackupDebt::from),
            debtRepayments = dao.debtRepayments().map(BackupDebtRepayment::from),
        )
    }

    private fun writeEnvelope(
        output: OutputStream,
        payload: BackupPayload,
        protection: BackupProtection,
        password: CharArray?,
    ) {
        val serialized = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        if (serialized.size > MAX_UNCOMPRESSED_BYTES) invalid("Backup is too large")
        val compressed = gzip(serialized)
        val salt = if (protection == BackupProtection.PASSWORD) randomBytes(SALT_BYTES) else null
        val iv = if (protection == BackupProtection.PASSWORD) randomBytes(IV_BYTES) else null
        val header = BackupHeader(
            protection = protection.name,
            iterations = if (protection == BackupProtection.PASSWORD) PBKDF2_ITERATIONS else null,
            salt = salt?.base64(),
            iv = iv?.base64(),
            checksum = if (protection == BackupProtection.NONE) sha256(compressed).base64() else null,
        )
        val headerBytes = json.encodeToString(header).toByteArray(Charsets.UTF_8)
        val storedPayload = if (protection == BackupProtection.PASSWORD) {
            encrypt(
                compressed,
                requireNotNull(password),
                requireNotNull(salt),
                requireNotNull(iv),
                headerBytes
            )
        } else {
            compressed
        }
        if (storedPayload.size > MAX_STORED_PAYLOAD_BYTES) invalid("Backup is too large")
        DataOutputStream(output.buffered()).use { data ->
            data.write(MAGIC)
            data.writeInt(headerBytes.size)
            data.write(headerBytes)
            data.write(storedPayload)
        }
    }

    private fun readEnvelope(input: InputStream, password: CharArray?): DecodedBackup {
        try {
            val contentDigest = MessageDigest.getInstance("SHA-256")
            DataInputStream(DigestInputStream(input.buffered(), contentDigest)).use { data ->
                val magic = ByteArray(MAGIC.size)
                data.readFully(magic)
                if (!magic.contentEquals(MAGIC)) throw BackupException.InvalidBackup("Not a Denaro backup")
                val headerLength = data.readInt()
                if (headerLength !in 1..MAX_HEADER_BYTES) {
                    throw BackupException.InvalidBackup("Invalid backup header")
                }
                val headerBytes = ByteArray(headerLength).also(data::readFully)
                val header = runCatching {
                    json.decodeFromString<BackupHeader>(headerBytes.toString(Charsets.UTF_8))
                }.getOrElse { throw BackupException.InvalidBackup("Invalid backup header", it) }
                if (header.envelopeVersion > ENVELOPE_VERSION) {
                    throw BackupException.UnsupportedVersion(header.envelopeVersion)
                }
                val protection = runCatching { BackupProtection.valueOf(header.protection) }
                    .getOrElse { throw BackupException.InvalidBackup("Unsupported backup protection") }
                if (protection == BackupProtection.PASSWORD && password == null) {
                    throw BackupException.PasswordRequired()
                }
                val compressedFile = decodeStoredPayloadToFile(
                    data = data,
                    protection = protection,
                    password = password,
                    header = header,
                    headerBytes = headerBytes,
                )
                try {
                    val payload = runCatching {
                        FileInputStream(compressedFile).buffered().use {
                            decodePayload(it, MAX_UNCOMPRESSED_BYTES, json)
                        }
                    }.getOrElse { throw BackupException.InvalidBackup("Invalid backup data", it) }
                    if (payload.schemaVersion > PAYLOAD_VERSION) {
                        throw BackupException.UnsupportedVersion(payload.schemaVersion)
                    }
                    return DecodedBackup(protection, payload, contentDigest.digest())
                } finally {
                    compressedFile.delete()
                }
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw BackupException.InvalidBackup("Could not read the backup", error)
        }
    }

    private fun decodeStoredPayloadToFile(
        data: InputStream,
        protection: BackupProtection,
        password: CharArray?,
        header: BackupHeader,
        headerBytes: ByteArray,
    ): File {
        val outputFile = BackupTemporaryFiles.createRestore(cacheDirectory)
        try {
            val stored = LimitedInputStream(data, MAX_STORED_PAYLOAD_BYTES)
            FileOutputStream(outputFile).buffered().use { output ->
                when (protection) {
                    BackupProtection.NONE -> copyAndVerifyPlain(stored, output, header)
                    BackupProtection.PASSWORD -> decryptTo(
                        stored,
                        output,
                        requireNotNull(password),
                        header,
                        headerBytes,
                    )
                }
            }
            return outputFile
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        }
    }

    private fun copyAndVerifyPlain(
        input: InputStream,
        output: OutputStream,
        header: BackupHeader,
    ) {
        val expected = header.checksum?.decodeBase64()
            ?: throw BackupException.InvalidBackup("Missing backup checksum")
        val digest = MessageDigest.getInstance("SHA-256")
        copyInChunks(input) { bytes, count ->
            digest.update(bytes, 0, count)
            output.write(bytes, 0, count)
        }
        if (!MessageDigest.isEqual(expected, digest.digest())) {
            throw BackupException.InvalidBackup("The backup is damaged")
        }
    }

    private fun decryptTo(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        header: BackupHeader,
        headerBytes: ByteArray,
    ) {
        val salt = header.salt?.decodeBase64()
            ?: throw BackupException.InvalidBackup("Missing encryption salt")
        val iv = header.iv?.decodeBase64()
            ?: throw BackupException.InvalidBackup("Missing encryption nonce")
        if (header.iterations != PBKDF2_ITERATIONS || salt.size != SALT_BYTES || iv.size != IV_BYTES) {
            throw BackupException.InvalidBackup("Unsupported encryption parameters")
        }
        try {
            val cipher = Cipher.getInstance(AES_GCM).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    deriveKey(password, salt),
                    GCMParameterSpec(GCM_TAG_BITS, iv)
                )
                updateAAD(headerBytes)
            }
            copyInChunks(input) { bytes, count ->
                cipher.update(bytes, 0, count)?.let(output::write)
            }
            output.write(cipher.doFinal())
        } catch (error: AEADBadTagException) {
            throw BackupException.AuthenticationFailed(error)
        }
    }

    private fun validate(payload: BackupPayload) {
        if (payload.schemaVersion !in LEGACY_PAYLOAD_VERSION..PAYLOAD_VERSION) {
            throw BackupException.InvalidBackup("Unsupported backup data version")
        }

        val counts = payload.counts()
        if (counts.total > MAX_RECORDS) throw BackupException.InvalidBackup("Backup contains too many records")
        if (payload.createdAt < 0L || payload.appVersion.isBlank()) invalid("Invalid backup metadata")
        val accountIds = uniqueIds("account", payload.accounts.map { it.id })
        val categoryIds = uniqueIds("category", payload.categories.map { it.id })
        val ruleIds = uniqueIds("recurring rule", payload.recurringRules.map { it.id })
        uniqueIds("transaction", payload.transactions.map { it.id })
        uniqueIds("adjustment", payload.balanceAdjustments.map { it.id })
        uniqueIds("transfer", payload.transfers.map { it.id })
        val counterpartyIds = uniqueIds("counterparty", payload.counterparties.map { it.id })
        val debtIds = uniqueIds("debt", payload.debts.map { it.id })
        uniqueIds("repayment", payload.debtRepayments.map { it.id })

        payload.accounts.forEach {
            val fractionDigits = it.resolvedFractionDigits(payload.schemaVersion)
            if (it.name.isBlank() || !CurrencyCatalog.isValid(it.currency) ||
                !Money.isValidFractionDigits(fractionDigits)
            ) {
                invalid("Invalid account")
            }
        }
        payload.accounts.groupBy(BackupAccount::currency).forEach { (_, accounts) ->
            if (accounts.map { it.resolvedFractionDigits(payload.schemaVersion) }
                    .distinct().size > 1) {
                invalid("Accounts using one currency must use the same fraction digits")
            }
        }
        val accountsById = payload.accounts.associateBy { it.id }
        val categoriesById = payload.categories.associateBy { it.id }
        payload.categories.forEach {
            parseTransactionType(it.type)
            if (it.name.isBlank() || it.parentId != null && it.parentId !in categoryIds) invalid("Invalid category")
            if (it.parentId != null && categoriesById[it.parentId]?.type != it.type) invalid("Invalid category hierarchy")
            if (it.parentId != null && categoriesById[it.parentId]?.parentId != null) invalid("Invalid category hierarchy")
        }
        topologicallySortedCategories(payload.categories)
        payload.recurringRules.forEach {
            val type = parseTransactionType(it.transactionType)
            if (it.accountId !in accountIds || it.categoryId != null && it.categoryId !in categoryIds) invalid(
                "Invalid recurring rule"
            )
            if (it.categoryId != null && categoriesById[it.categoryId]?.type != type.name) invalid("Invalid recurring category")
            val frequency = parseFrequency(it.frequency)
            val hasValidAnchors = when (frequency) {
                RecurrenceFrequency.DAILY, RecurrenceFrequency.WEEKLY ->
                    it.anchorDay == null && it.anchorMonth == null

                RecurrenceFrequency.MONTHLY ->
                    it.anchorDay in 1..31 && it.anchorMonth == null

                RecurrenceFrequency.YEARLY ->
                    it.anchorDay in 1..31 && it.anchorMonth in 1..12
            }
            if (!hasValidAnchors) invalid("Invalid recurring rule anchors")
            runCatching { ZoneId.of(it.timezoneId) }.getOrElse { invalid("Invalid recurring timezone") }
            if (it.intervalCount <= 0 || it.amountMinor <= 0 || it.nextOccurrenceAt < 0) {
                invalid("Invalid recurring rule")
            }
        }
        payload.transactions.forEach {
            val type = parseTransactionType(it.type)
            if (it.accountId !in accountIds || it.categoryId != null && it.categoryId !in categoryIds) invalid(
                "Invalid transaction"
            )
            if (it.recurringRuleId != null && it.recurringRuleId !in ruleIds) invalid("Invalid recurring transaction")
            if (it.categoryId != null && categoriesById[it.categoryId]?.type != type.name) invalid("Invalid transaction category")
            validDate(it.localDate)
            if (it.amountMinor <= 0) invalid("Invalid transaction amount")
        }
        payload.balanceAdjustments.forEach {
            if (it.accountId !in accountIds) invalid("Invalid balance adjustment")
            validDate(it.localDate)
        }
        payload.transfers.forEach {
            if (it.fromAccountId !in accountIds || it.toAccountId !in accountIds || it.fromAccountId == it.toAccountId) invalid(
                "Invalid transfer"
            )
            if (accountsById.getValue(it.fromAccountId).currency !=
                accountsById.getValue(it.toAccountId).currency
            ) invalid("Transfer accounts use different currencies")
            validDate(it.localDate)
            if (it.amountMinor <= 0) invalid("Invalid transfer amount")
        }
        payload.counterparties.forEach { if (it.name.isBlank()) invalid("Invalid counterparty") }
        payload.debts.forEach {
            parseDebtDirection(it.direction)
            if (it.counterpartyId !in counterpartyIds || it.accountId !in accountIds ||
                !CurrencyCatalog.isValid(it.currency)
            ) invalid("Invalid debt")
            if (accountsById.getValue(it.accountId).currency != it.currency) {
                invalid("Debt currency does not match its account")
            }
            validDate(it.localDate)
            it.dueDate?.let(::validDate)
            if (it.principalMinor <= 0) invalid("Invalid debt amount")
        }
        val debtsById = payload.debts.associateBy { it.id }
        val repaidByDebt = mutableMapOf<String, Long>()
        payload.debtRepayments.forEach {
            if (it.debtId !in debtIds || it.accountId !in accountIds) invalid("Invalid debt repayment")
            val debt = debtsById.getValue(it.debtId)
            val account = accountsById.getValue(it.accountId)
            validDate(it.localDate)
            if (it.amountMinor <= 0) invalid("Invalid repayment amount")
            if (it.occurredAt < debt.openedAt) invalid("Repayment is before the debt opening")
            if (account.currency != debt.currency) invalid("Repayment currency does not match the debt")
            val total = try {
                Math.addExact(repaidByDebt[it.debtId] ?: 0L, it.amountMinor)
            } catch (_: ArithmeticException) {
                invalid("Invalid repayment total")
            }
            if (total > debt.principalMinor) invalid("Repayments exceed the debt principal")
            repaidByDebt[it.debtId] = total
        }
    }

    private fun encrypt(
        data: ByteArray,
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val key = deriveKey(password, salt)
        return Cipher.getInstance(AES_GCM).run {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(aad)
            doFinal(data)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(PBKDF2).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    private fun BackupPayload.preview(protection: BackupProtection) =
        BackupPreview(createdAt, appVersion, protection, counts())

    private fun BackupPayload.counts() = BackupRecordCounts(
        accounts.size, categories.size, recurringRules.size, transactions.size,
        balanceAdjustments.size, transfers.size, counterparties.size, debts.size,
        debtRepayments.size,
    )

    private fun BackupPayload.sameFinanceDataAs(other: BackupPayload): Boolean =
        normalizedAccounts() == other.normalizedAccounts() &&
                categories.associateBy { it.id } == other.categories.associateBy { it.id } &&
                recurringRules.associateBy { it.id } == other.recurringRules.associateBy { it.id } &&
                transactions.associateBy { it.id } == other.transactions.associateBy { it.id } &&
                balanceAdjustments.associateBy { it.id } == other.balanceAdjustments.associateBy { it.id } &&
                transfers.associateBy { it.id } == other.transfers.associateBy { it.id } &&
                counterparties.associateBy { it.id } == other.counterparties.associateBy { it.id } &&
                debts.associateBy { it.id } == other.debts.associateBy { it.id } &&
                debtRepayments.associateBy { it.id } == other.debtRepayments.associateBy { it.id }

    private fun BackupPayload.normalizedAccounts(): Map<String, BackupAccount> =
        accounts.associate { account ->
            account.id to account.copy(
                fractionDigits = account.resolvedFractionDigits(schemaVersion),
            )
        }

    private fun BackupPayload.upgradedToCurrentVersion(): BackupPayload {
        if (schemaVersion == PAYLOAD_VERSION) return this
        val accountsByCurrency =
            accounts.associate { it.currency to Money.fractionDigitsForCurrency(it.currency) }
        val accountCurrencyById = accounts.associate { it.id to it.currency }
        val debtCurrencyById = debts.associate { it.id to it.currency }
        fun scaled(currency: String, value: Long): Long {
            val target = accountsByCurrency[currency] ?: return value
            return Money.convertMinorUnits(value, 2, target)
        }
        return copy(
            schemaVersion = PAYLOAD_VERSION,
            accounts = accounts.map { account ->
                val target = accountsByCurrency[account.currency] ?: 2
                account.copy(
                    fractionDigits = target,
                    openingBalanceMinor = scaled(account.currency, account.openingBalanceMinor),
                )
            },
            transactions = transactions.map { transaction ->
                val currency = accountCurrencyById[transaction.accountId] ?: return@map transaction
                transaction.copy(amountMinor = scaled(currency, transaction.amountMinor))
            },
            transfers = transfers.map { transfer ->
                val currency = accountCurrencyById[transfer.fromAccountId] ?: return@map transfer
                transfer.copy(amountMinor = scaled(currency, transfer.amountMinor))
            },
            balanceAdjustments = balanceAdjustments.map { adjustment ->
                val currency = accountCurrencyById[adjustment.accountId] ?: return@map adjustment
                adjustment.copy(
                    deltaMinor = scaled(currency, adjustment.deltaMinor),
                    balanceBeforeMinor = scaled(currency, adjustment.balanceBeforeMinor),
                    balanceAfterMinor = scaled(currency, adjustment.balanceAfterMinor),
                )
            },
            recurringRules = recurringRules.map { rule ->
                val currency = accountCurrencyById[rule.accountId] ?: return@map rule
                rule.copy(amountMinor = scaled(currency, rule.amountMinor))
            },
            debts = debts.map { debt ->
                debt.copy(principalMinor = scaled(debt.currency, debt.principalMinor))
            },
            debtRepayments = debtRepayments.map { repayment ->
                val currency = debtCurrencyById[repayment.debtId] ?: return@map repayment
                repayment.copy(amountMinor = scaled(currency, repayment.amountMinor))
            },
        )
    }

    private companion object {
        val MAGIC = "DENARO_BACKUP\n".toByteArray(Charsets.US_ASCII)
        const val ENVELOPE_VERSION = 1
        const val LEGACY_PAYLOAD_VERSION = 1
        const val PAYLOAD_VERSION = 2
        const val PBKDF2_ITERATIONS = 600_000
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val AES_KEY_BITS = 256
        const val GCM_TAG_BITS = 128
        const val PBKDF2 = "PBKDF2WithHmacSHA256"
        const val AES_GCM = "AES/GCM/NoPadding"
        const val MAX_HEADER_BYTES = 16 * 1024
        const val MAX_STORED_PAYLOAD_BYTES = 16 * 1024 * 1024
        const val MAX_UNCOMPRESSED_BYTES = 16 * 1024 * 1024
        const val MAX_RECORDS = 50_000
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}

private data class DecodedBackup(
    val protection: BackupProtection,
    val payload: BackupPayload,
    val contentDigest: ByteArray,
)

@Serializable
private data class BackupHeader(
    val envelopeVersion: Int = 1,
    val protection: String,
    val iterations: Int? = null,
    val salt: String? = null,
    val iv: String? = null,
    val checksum: String? = null,
)

@Serializable
private data class BackupPayload(
    val schemaVersion: Int = 1,
    val createdAt: Long,
    val appVersion: String,
    val accounts: List<BackupAccount> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val recurringRules: List<BackupRecurringRule> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val balanceAdjustments: List<BackupBalanceAdjustment> = emptyList(),
    val transfers: List<BackupTransfer> = emptyList(),
    val counterparties: List<BackupCounterparty> = emptyList(),
    val debts: List<BackupDebt> = emptyList(),
    val debtRepayments: List<BackupDebtRepayment> = emptyList(),
)

@Serializable
private data class BackupAccount(
    val id: String,
    val name: String,
    val description: String?,
    val openingBalanceMinor: Long,
    val currency: String,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val fractionDigits: Int? = null,
) {
    fun resolvedFractionDigits(schemaVersion: Int): Int =
        if (schemaVersion == 1) 2 else fractionDigits ?: invalid("Missing account fraction digits")

    fun toEntity(schemaVersion: Int) = AccountEntity(
        id = id,
        name = name,
        description = description,
        openingBalanceMinor = openingBalanceMinor,
        currency = currency,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        fractionDigits = resolvedFractionDigits(schemaVersion),
    )

    companion object {
        fun from(v: AccountEntity) = BackupAccount(
            id = v.id,
            name = v.name,
            description = v.description,
            openingBalanceMinor = v.openingBalanceMinor,
            currency = v.currency,
            archivedAt = v.archivedAt,
            createdAt = v.createdAt,
            updatedAt = v.updatedAt,
            fractionDigits = v.fractionDigits,
        )
    }
}

@Serializable
private data class BackupCategory(
    val id: String,
    val type: String,
    val parentId: String?,
    val name: String,
    val iconName: String,
    val colorIndex: Int,
    val archivedAt: Long?,
    val archivedByParentId: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toEntity() = CategoryEntity(
        id,
        parseTransactionType(type),
        parentId,
        name,
        iconName,
        colorIndex,
        archivedAt,
        archivedByParentId,
        createdAt,
        updatedAt
    )

    companion object {
        fun from(v: CategoryEntity) = BackupCategory(
            v.id,
            v.type.name,
            v.parentId,
            v.name,
            v.iconName,
            v.colorIndex,
            v.archivedAt,
            v.archivedByParentId,
            v.createdAt,
            v.updatedAt
        )
    }
}

@Serializable
private data class BackupRecurringRule(
    val id: String,
    val accountId: String,
    val categoryId: String?,
    val amountMinor: Long,
    val transactionType: String,
    val description: String?,
    val frequency: String,
    val intervalCount: Int,
    val timezoneId: String,
    val anchorDay: Int?,
    val anchorMonth: Int?,
    val startAt: Long,
    val lastGeneratedAt: Long?,
    val nextOccurrenceAt: Long,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toEntity() = RecurringRuleEntity(
        id,
        accountId,
        categoryId,
        amountMinor,
        parseTransactionType(transactionType),
        description,
        parseFrequency(frequency),
        intervalCount,
        timezoneId,
        anchorDay,
        anchorMonth,
        startAt,
        lastGeneratedAt,
        nextOccurrenceAt,
        isActive,
        createdAt,
        updatedAt
    )

    companion object {
        fun from(v: RecurringRuleEntity) = BackupRecurringRule(
            v.id,
            v.accountId,
            v.categoryId,
            v.amountMinor,
            v.transactionType.name,
            v.description,
            v.frequency.name,
            v.intervalCount,
            v.timezoneId,
            v.anchorDay,
            v.anchorMonth,
            v.startAt,
            v.lastGeneratedAt,
            v.nextOccurrenceAt,
            v.isActive,
            v.createdAt,
            v.updatedAt
        )
    }
}

@Serializable
private data class BackupTransaction(
    val id: String, val accountId: String, val recurringRuleId: String?, val categoryId: String?,
    val occurrenceKey: String?, val amountMinor: Long, val type: String, val occurredAt: Long,
    val localDate: String, val description: String?, val createdAt: Long, val updatedAt: Long,
) {
    fun toEntity() = TransactionEntity(
        id,
        accountId,
        recurringRuleId,
        categoryId,
        occurrenceKey,
        amountMinor,
        parseTransactionType(type),
        occurredAt,
        localDate,
        description,
        createdAt,
        updatedAt
    )

    companion object {
        fun from(v: TransactionEntity) = BackupTransaction(
            v.id,
            v.accountId,
            v.recurringRuleId,
            v.categoryId,
            v.occurrenceKey,
            v.amountMinor,
            v.type.name,
            v.occurredAt,
            v.localDate,
            v.description,
            v.createdAt,
            v.updatedAt
        )
    }
}

@Serializable
private data class BackupBalanceAdjustment(
    val id: String, val accountId: String, val deltaMinor: Long, val balanceBeforeMinor: Long,
    val balanceAfterMinor: Long, val occurredAt: Long, val localDate: String, val createdAt: Long,
) {
    fun toEntity() = BalanceAdjustmentEntity(
        id,
        accountId,
        deltaMinor,
        balanceBeforeMinor,
        balanceAfterMinor,
        occurredAt,
        localDate,
        createdAt
    )

    companion object {
        fun from(v: BalanceAdjustmentEntity) = BackupBalanceAdjustment(
            v.id,
            v.accountId,
            v.deltaMinor,
            v.balanceBeforeMinor,
            v.balanceAfterMinor,
            v.occurredAt,
            v.localDate,
            v.createdAt
        )
    }
}

@Serializable
private data class BackupTransfer(
    val id: String, val fromAccountId: String, val toAccountId: String, val amountMinor: Long,
    val occurredAt: Long, val localDate: String, val description: String?, val createdAt: Long,
    val updatedAt: Long,
) {
    fun toEntity() = TransferEntity(
        id,
        fromAccountId,
        toAccountId,
        amountMinor,
        occurredAt,
        localDate,
        description,
        createdAt,
        updatedAt
    )

    companion object {
        fun from(v: TransferEntity) = BackupTransfer(
            v.id,
            v.fromAccountId,
            v.toAccountId,
            v.amountMinor,
            v.occurredAt,
            v.localDate,
            v.description,
            v.createdAt,
            v.updatedAt
        )
    }
}

@Serializable
private data class BackupCounterparty(
    val id: String, val name: String, val note: String?, val archivedAt: Long?, val createdAt: Long,
    val updatedAt: Long,
) {
    fun toEntity() = CounterpartyEntity(id, name, note, archivedAt, createdAt, updatedAt)

    companion object {
        fun from(v: CounterpartyEntity) =
            BackupCounterparty(v.id, v.name, v.note, v.archivedAt, v.createdAt, v.updatedAt)
    }
}

@Serializable
private data class BackupDebt(
    val id: String, val counterpartyId: String, val accountId: String, val direction: String,
    val principalMinor: Long, val currency: String, val openedAt: Long, val localDate: String,
    val dueDate: String?, val note: String?, val createdAt: Long, val updatedAt: Long,
) {
    fun toEntity() = DebtEntity(
        id,
        counterpartyId,
        accountId,
        parseDebtDirection(direction),
        principalMinor,
        currency,
        openedAt,
        localDate,
        dueDate,
        note,
        createdAt,
        updatedAt
    )

    companion object {
        fun from(v: DebtEntity) = BackupDebt(
            v.id,
            v.counterpartyId,
            v.accountId,
            v.direction.name,
            v.principalMinor,
            v.currency,
            v.openedAt,
            v.localDate,
            v.dueDate,
            v.note,
            v.createdAt,
            v.updatedAt
        )
    }
}

@Serializable
private data class BackupDebtRepayment(
    val id: String, val debtId: String, val accountId: String, val amountMinor: Long,
    val occurredAt: Long, val localDate: String, val note: String?, val createdAt: Long,
    val updatedAt: Long,
) {
    fun toEntity() = DebtRepaymentEntity(
        id,
        debtId,
        accountId,
        amountMinor,
        occurredAt,
        localDate,
        note,
        createdAt,
        updatedAt
    )

    companion object {
        fun from(v: DebtRepaymentEntity) = BackupDebtRepayment(
            v.id,
            v.debtId,
            v.accountId,
            v.amountMinor,
            v.occurredAt,
            v.localDate,
            v.note,
            v.createdAt,
            v.updatedAt
        )
    }
}

private fun parseTransactionType(value: String) = runCatching { TransactionType.valueOf(value) }
    .getOrElse { invalid("Invalid transaction type") }

private fun parseFrequency(value: String) = runCatching { RecurrenceFrequency.valueOf(value) }
    .getOrElse { invalid("Invalid recurrence frequency") }

private fun parseDebtDirection(value: String) = runCatching { DebtDirection.valueOf(value) }
    .getOrElse { invalid("Invalid debt direction") }

private fun validDate(value: String) {
    runCatching { LocalDate.parse(value) }.getOrElse { invalid("Invalid date") }
}

private fun invalid(message: String): Nothing = throw BackupException.InvalidBackup(message)

private fun uniqueIds(kind: String, values: List<String>): Set<String> {
    if (values.any(String::isBlank) || values.toSet().size != values.size) invalid("Duplicate or empty $kind ID")
    return values.toSet()
}

private fun topologicallySortedCategories(values: List<BackupCategory>): List<BackupCategory> {
    val remaining = values.associateBy { it.id }.toMutableMap()
    val sorted = mutableListOf<BackupCategory>()
    val inserted = mutableSetOf<String>()
    while (remaining.isNotEmpty()) {
        val ready = remaining.values.filter { it.parentId == null || it.parentId in inserted }
        if (ready.isEmpty()) invalid("Category hierarchy contains a cycle")
        ready.sortedWith(compareBy<BackupCategory> { it.createdAt }.thenBy { it.id }).forEach {
            sorted += it
            inserted += it.id
            remaining.remove(it.id)
        }
    }
    return sorted
}

private fun gzip(value: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
    GZIPOutputStream(output).use { it.write(value) }
    output.toByteArray()
}

@OptIn(ExperimentalSerializationApi::class)
private fun decodePayload(
    value: InputStream,
    uncompressedLimit: Int,
    json: Json,
): BackupPayload = try {
    GZIPInputStream(value).use { compressed ->
        LimitedInputStream(compressed, uncompressedLimit).use { bounded ->
            val payload = json.decodeFromStream<BackupPayload>(bounded)
            bounded.drain()
            payload
        }
    }
} catch (error: BackupException) {
    throw error
} catch (error: Exception) {
    throw BackupException.InvalidBackup("The backup payload is damaged", error)
}

private fun InputStream.drain() {
    val buffer = ByteArray(16 * 1024)
    while (read(buffer) >= 0) {
        // Consume the complete stream so bounds and gzip integrity are finalized.
    }
}

internal class LimitedInputStream(
    private val delegate: InputStream,
    limit: Int,
) : InputStream() {
    private var remaining = limit.toLong()

    override fun read(): Int {
        if (remaining == 0L) return checkForOverflow()
        val value = delegate.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return checkForOverflow()
        val count = delegate.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) remaining -= count
        return count
    }

    override fun close() = delegate.close()

    private fun checkForOverflow(): Int {
        if (delegate.read() < 0) return -1
        throw BackupException.InvalidBackup("Backup is too large")
    }
}

private inline fun copyInChunks(input: InputStream, consume: (ByteArray, Int) -> Unit) {
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        consume(buffer, count)
    }
}

private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
private fun String.decodeBase64(): ByteArray = try {
    Base64.decode(this, Base64.NO_WRAP)
} catch (error: IllegalArgumentException) {
    throw BackupException.InvalidBackup("Invalid encoded backup header", error)
}
