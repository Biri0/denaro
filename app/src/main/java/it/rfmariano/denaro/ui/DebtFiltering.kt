package it.rfmariano.denaro.ui

import it.rfmariano.denaro.data.finance.DebtSummary
import it.rfmariano.denaro.data.local.DebtDirection
import java.time.LocalDate

internal enum class DebtStatusFilter { OPEN, SETTLED }

internal data class CounterpartyFilterOption(val id: String, val name: String)

internal fun DebtSummary.isOverdue(today: LocalDate): Boolean =
    !isSettled && dueDate?.let { LocalDate.parse(it) < today } == true

internal fun debtDisplayComparator(today: LocalDate): Comparator<DebtSummary> =
    compareBy<DebtSummary> {
        when {
            it.isSettled -> 2
            it.isOverdue(today) -> 0
            else -> 1
        }
    }.thenBy { it.dueDate ?: "9999-12-31" }.thenByDescending { it.openedAt }

internal fun filterDebts(
    debts: List<DebtSummary>,
    direction: DebtDirection?,
    status: DebtStatusFilter,
    today: LocalDate,
    counterpartyId: String? = null,
): List<DebtSummary> =
    debts.asSequence()
        .filter { direction == null || it.direction == direction }
        .filter { if (status == DebtStatusFilter.OPEN) !it.isSettled else it.isSettled }
        .filter { counterpartyId == null || it.counterpartyId == counterpartyId }
        .sortedWith(debtDisplayComparator(today))
        .toList()

internal fun counterpartyFilterOptions(debts: List<DebtSummary>): List<CounterpartyFilterOption> =
    debts.asSequence()
        .map { it.counterpartyId to it.counterpartyName }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
        .map { (id, name) -> CounterpartyFilterOption(id, name) }
        .toList()
