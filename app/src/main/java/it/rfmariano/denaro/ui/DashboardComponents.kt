package it.rfmariano.denaro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.CategoryShare
import it.rfmariano.denaro.data.finance.DashboardSnapshot
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.finance.MonthlyCashFlow
import java.time.YearMonth
import java.time.format.TextStyle
import com.composables.icons.lucide.R as LucideR

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DashboardControls(
    state: HomeUiState,
    onCurrency: (String) -> Unit,
    onAccount: (String?) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val currencies = state.accounts.map { it.currency }.distinct().sorted()
    val accounts = state.accounts.filter { it.currency == state.selectedCurrency }
    val month = YearMonth.parse(state.selectedMonth)
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    painterResource(LucideR.drawable.lucide_ic_chevron_left),
                    contentDescription = stringResource(R.string.previous_month)
                )
            }
            Text(
                month.month.getDisplayName(TextStyle.FULL, locale)
                    .replaceFirstChar { it.titlecase(locale) } + " ${month.year}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNextMonth, enabled = month < YearMonth.now()) {
                Icon(
                    painterResource(LucideR.drawable.lucide_ic_chevron_right),
                    contentDescription = stringResource(R.string.next_month)
                )
            }
        }
        if (currencies.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                currencies.forEach { currency ->
                    FilterChip(
                        selected = currency == state.selectedCurrency,
                        onClick = { onCurrency(currency) },
                        label = { Text(currency) },
                    )
                }
            }
        }
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = accounts.find { it.id == state.selectedAccountId }?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.filter_account)) },
                placeholder = { Text(stringResource(R.string.all_accounts)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.all_accounts)) },
                    onClick = { onAccount(null); expanded = false })
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = { onAccount(account.id); expanded = false })
                }
            }
        }
    }
}

@Composable
fun DashboardSummary(snapshot: DashboardSnapshot, amountsVisible: Boolean) {
    Column(Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(
                R.string.income,
                snapshot.selected.incomeMinor,
                snapshot.filter.currency,
                amountsVisible,
                Modifier.weight(1f)
            )
            SummaryCard(
                R.string.expense,
                snapshot.selected.expenseMinor,
                snapshot.filter.currency,
                amountsVisible,
                Modifier.weight(1f)
            )
            SummaryCard(
                R.string.net,
                snapshot.selected.netMinor,
                snapshot.filter.currency,
                amountsVisible,
                Modifier.weight(1f)
            )
        }
        val expenseDelta = snapshot.selected.expenseMinor - snapshot.previousComparable.expenseMinor
        Text(
            text = if (amountsVisible) {
                stringResource(
                    R.string.expense_change_previous,
                    (if (expenseDelta >= 0) "+" else "") + Money.format(
                        expenseDelta,
                        snapshot.filter.currency
                    ),
                )
            } else {
                stringResource(
                    R.string.expense_change_previous,
                    stringResource(R.string.amount_hidden)
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SummaryCard(
    label: Int,
    amount: Long,
    currency: String,
    visible: Boolean,
    modifier: Modifier
) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (visible) Money.format(
                    amount,
                    currency
                ) else stringResource(R.string.amount_hidden),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MonthlyCashFlowChart(months: List<MonthlyCashFlow>, currency: String, amountsVisible: Boolean) {
    if (months.isEmpty()) return
    val maxAmount = months.maxOf { maxOf(it.incomeMinor, it.expenseMinor) }.coerceAtLeast(1)
    val incomeColor = Color(0xFF2E7D32)
    val expenseColor = MaterialTheme.colorScheme.error
    val locale = LocalConfiguration.current.locales[0]
    val description = if (amountsVisible) months.joinToString { month ->
        "${month.month}: ${
            Money.format(
                month.incomeMinor,
                currency
            )
        }, ${Money.format(month.expenseMinor, currency)}"
    } else stringResource(R.string.amounts_hidden_chart_note)
    Column(Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.six_month_trend), style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChartLegend(incomeColor, stringResource(R.string.income))
            ChartLegend(expenseColor, stringResource(R.string.expense))
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .semantics { contentDescription = description },
        ) {
            val groupWidth = size.width / months.size
            val barWidth = groupWidth * 0.27f
            val chartHeight = size.height - 28.dp.toPx()
            months.forEachIndexed { index, month ->
                val center = groupWidth * (index + 0.5f)
                val incomeHeight = chartHeight * month.incomeMinor / maxAmount
                val expenseHeight = chartHeight * month.expenseMinor / maxAmount
                drawRoundRect(
                    incomeColor,
                    topLeft = Offset(center - barWidth - 2.dp.toPx(), chartHeight - incomeHeight),
                    size = Size(barWidth, incomeHeight),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
                drawRoundRect(
                    expenseColor,
                    topLeft = Offset(center + 2.dp.toPx(), chartHeight - expenseHeight),
                    size = Size(barWidth, expenseHeight),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            months.forEach { month ->
                Text(
                    YearMonth.parse(month.month).month.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        if (!amountsVisible) Text(
            stringResource(R.string.amounts_hidden_chart_note),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier
            .width(12.dp)
            .height(12.dp)
            .padding(1.dp))
        Canvas(Modifier
            .width(12.dp)
            .height(12.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun CategoryBreakdown(
    dashboard: DashboardSnapshot,
    amountsVisible: Boolean,
    onCategoryClick: (ActivityKind, String?) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(ActivityKind.EXPENSE) }
    val shares =
        if (kind == ActivityKind.EXPENSE) dashboard.expenseCategories else dashboard.incomeCategories
    val total = shares.sumOf(CategoryShare::amountMinor).coerceAtLeast(1)
    Column(Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.by_category), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)) {
            listOf(ActivityKind.EXPENSE, ActivityKind.INCOME).forEachIndexed { index, option ->
                SegmentedButton(
                    selected = kind == option,
                    onClick = { kind = option },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(stringResource(if (option == ActivityKind.EXPENSE) R.string.expense else R.string.income)) }
            }
        }
        if (shares.isEmpty()) {
            Text(
                stringResource(R.string.no_data_for_period),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            shares.forEach { share ->
                val fraction = share.amountMinor.toFloat() / total
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(kind, share.categoryId) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(share.iconName ?: "circle_help", share.colorIndex)
                    Column(Modifier
                        .weight(1f)
                        .padding(start = 12.dp)) {
                        Row {
                            Text(
                                share.name ?: stringResource(R.string.no_category),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (amountsVisible) Money.format(
                                    share.amountMinor,
                                    dashboard.filter.currency
                                ) else stringResource(R.string.amount_hidden)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp),
                            color = CategoryPalette[(share.colorIndex
                                ?: 0).mod(CategoryPalette.size)],
                        )
                        Text(
                            "${(fraction * 100).toInt()}% · ${share.transactionCount}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
