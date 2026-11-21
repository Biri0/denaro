package it.rfmariano.denaro.ui

import it.rfmariano.denaro.data.finance.DebtSummary
import it.rfmariano.denaro.data.local.DebtDirection
import java.time.LocalDate

internal enum class DebtStatusFilter { OPEN, SETTLED }

internal data class CounterpartyFilterOption(val id: String, val name: String)

internal data class DebtCurrencyTotals(
    val currency: String,
    val fractionDigits: Int,
    val youOweMinor: Long,
    val owedToYouMinor: Long,
) {
    val netMinor: Long get() = owedToYouMinor - youOweMinor
    val hasBothDirections: Boolean get() = youOweMinor > 0L && owedToYouMinor > 0L
}

internal fun debtCurrencyTotals(
    debts: List<DebtSummary>,
    counterpartyId: String? = null,
): List<DebtCurrencyTotals> =
    debts.asSequence()
        .filterNot(DebtSummary::isSettled)
        .filter { counterpartyId == null || it.counterpartyId == counterpartyId }
        .groupBy(DebtSummary::currency)
        .toSortedMap()
        .map { (currency, group) ->
            DebtCurrencyTotals(
                currency = currency,
                fractionDigits = group.first().fractionDigits,
                youOweMinor = group
                    .filter { it.direction == DebtDirection.BORROWED }
                    .sumOf(DebtSummary::outstandingMinor),
                owedToYouMinor = group
                    .filter { it.direction == DebtDirection.LENT }
                    .sumOf(DebtSummary::outstandingMinor),
            )
        }

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
