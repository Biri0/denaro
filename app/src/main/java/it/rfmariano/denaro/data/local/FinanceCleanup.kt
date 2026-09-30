package it.rfmariano.denaro.data.local

/**
 * Deletes every finance record in FK-safe order, leaving the schema and the legacy import
 * history untouched.
 *
 * Both data erasure and the debug demo seeder clear the database through this function: two
 * hand-maintained table lists drift apart, and a table only one of them knows about leaves rows
 * behind that make the other fail. [BudgetEntity] used to be exactly that table.
 */
suspend fun DenaroDatabase.clearFinanceRecords() {
    val dao = backupDao()
    dao.deleteDebtRepayments()
    dao.deleteDebts()
    dao.deleteTransactions()
    dao.deleteTransfers()
    dao.deleteBalanceAdjustments()
    dao.deleteRecurringRules()
    dao.deleteBudgets()
    dao.deleteCategories()
    dao.deleteCounterparties()
    dao.deleteAccounts()
}
