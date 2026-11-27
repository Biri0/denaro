@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.CategoryShare
import it.rfmariano.denaro.data.finance.DashboardSnapshot
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.finance.MonthlyCashFlow
import it.rfmariano.denaro.ui.theme.denaroColors
import it.rfmariano.denaro.ui.theme.tabularNumbers
import java.time.YearMonth
import java.time.format.TextStyle

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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = onPreviousMonth) {
                    Icon(
                        painterResource(R.drawable.ms_chevron_left),
                        contentDescription = stringResource(R.string.previous_month),
                    )
                }
                Text(
                    text = month.month.getDisplayName(TextStyle.FULL, locale)
                        .replaceFirstChar { it.titlecase(locale) } + " ${month.year}",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = onNextMonth,
                    enabled = month < YearMonth.now(),
                ) {
                    Icon(
                        painterResource(R.drawable.ms_chevron_right),
                        contentDescription = stringResource(R.string.next_month),
                    )
                }
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
        AdaptiveSelector(
            label = stringResource(R.string.filter_account),
            placeholder = stringResource(R.string.all_accounts),
            selectedText = accounts.find { it.id == state.selectedAccountId }?.name.orEmpty(),
            options = accounts.map { account ->
                SelectorOption<String?>(
                    id = account.id,
                    value = account.id,
                    label = account.name,
                    selected = account.id == state.selectedAccountId,
                )
            },
            onSelected = onAccount,
            clearOption = SelectorOption(
                id = "__all_accounts__",
                value = null,
                label = stringResource(R.string.all_accounts),
                selected = state.selectedAccountId == null,
            ),
        )
    }
}

@Composable
fun DashboardSummary(snapshot: DashboardSnapshot, amountsVisible: Boolean) {
    val positiveColor = MaterialTheme.denaroColors.positive
    val netColor = when {
        snapshot.selected.netMinor > 0L -> positiveColor
        snapshot.selected.netMinor < 0L -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryCard(
                label = R.string.income,
                amount = snapshot.selected.incomeMinor,
                currency = snapshot.filter.currency,
                fractionDigits = snapshot.fractionDigits,
                visible = amountsVisible,
                amountColor = positiveColor,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = R.string.expense,
                amount = snapshot.selected.expenseMinor,
                currency = snapshot.filter.currency,
                fractionDigits = snapshot.fractionDigits,
                visible = amountsVisible,
                amountColor = MaterialTheme.colorScheme.error,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = R.string.net,
                amount = snapshot.selected.netMinor,
                currency = snapshot.filter.currency,
                fractionDigits = snapshot.fractionDigits,
                visible = amountsVisible,
                amountColor = netColor,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            )
        }
        val expenseDelta = snapshot.selected.expenseMinor - snapshot.previousComparable.expenseMinor
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = if (amountsVisible) {
                    stringResource(
                        R.string.expense_change_previous,
                        (if (expenseDelta >= 0) "+" else "") + Money.format(
                            expenseDelta,
                            snapshot.filter.currency,
                            snapshot.fractionDigits,
                        ),
                    )
                } else {
                    stringResource(
                        R.string.expense_change_previous,
                        stringResource(R.string.amount_hidden),
                    )
                },
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SummaryCard(
    label: Int,
    amount: Long,
    currency: String,
    fractionDigits: Int,
    visible: Boolean,
    amountColor: Color,
    containerColor: Color,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.largeIncreased,
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (visible) {
                    Money.format(amount, currency, fractionDigits)
                } else {
                    stringResource(R.string.amount_hidden)
                },
                style = MaterialTheme.typography.titleMediumEmphasized.tabularNumbers(),
                color = amountColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MonthlyCashFlowChart(
    months: List<MonthlyCashFlow>,
    currency: String,
    fractionDigits: Int,
    amountsVisible: Boolean,
) {
    if (months.isEmpty()) return
    val maxAmount = months.maxOf { maxOf(it.incomeMinor, it.expenseMinor) }.coerceAtLeast(1)
    val incomeColor = MaterialTheme.denaroColors.positive
    val expenseColor = MaterialTheme.colorScheme.error
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val locale = LocalConfiguration.current.locales[0]
    val description = if (amountsVisible) {
        months.joinToString { month ->
            "${month.month}: ${
                Money.format(month.incomeMinor, currency, fractionDigits)
            }, ${Money.format(month.expenseMinor, currency, fractionDigits)}"
        }
    } else {
        stringResource(R.string.amounts_hidden_chart_note)
    }

    DenaroSectionCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(20.dp),
    ) {
        Text(
            text = stringResource(R.string.six_month_trend),
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChartLegend(incomeColor, stringResource(R.string.income))
            ChartLegend(expenseColor, stringResource(R.string.expense))
        }
        Spacer(Modifier.height(16.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp)
                .semantics { contentDescription = description },
        ) {
            val groupWidth = size.width / months.size
            val barWidth = (groupWidth * 0.28f).coerceAtMost(22.dp.toPx())
            val chartHeight = size.height - 8.dp.toPx()
            val gap = 3.dp.toPx()
            val radius = CornerRadius(6.dp.toPx())

            drawLine(
                color = baselineColor,
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = 1.dp.toPx(),
            )

            months.forEachIndexed { index, month ->
                val center = groupWidth * (index + 0.5f)
                val incomeHeight = (chartHeight * month.incomeMinor / maxAmount)
                    .coerceAtLeast(if (month.incomeMinor > 0) 4.dp.toPx() else 0f)
                val expenseHeight = (chartHeight * month.expenseMinor / maxAmount)
                    .coerceAtLeast(if (month.expenseMinor > 0) 4.dp.toPx() else 0f)
                if (incomeHeight > 0f) {
                    drawRoundRect(
                        color = incomeColor,
                        topLeft = Offset(center - barWidth - gap / 2f, chartHeight - incomeHeight),
                        size = Size(barWidth, incomeHeight),
                        cornerRadius = radius,
                    )
                }
                if (expenseHeight > 0f) {
                    drawRoundRect(
                        color = expenseColor,
                        topLeft = Offset(center + gap / 2f, chartHeight - expenseHeight),
                        size = Size(barWidth, expenseHeight),
                        cornerRadius = radius,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            months.forEach { month ->
                Text(
                    text = YearMonth.parse(month.month).month.getDisplayName(
                        TextStyle.SHORT,
                        locale
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        if (!amountsVisible) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.amounts_hidden_chart_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun CategoryBreakdown(
    dashboard: DashboardSnapshot,
    amountsVisible: Boolean,
    onCategoryClick: (ActivityKind, String?) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(MODE_EXPENSE) }
    DenaroSectionCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(20.dp),
    ) {
        Text(
            text = stringResource(R.string.by_category),
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Spacer(Modifier.height(12.dp))
        DenaroSegmentedChoiceRow(
            options = listOf(MODE_EXPENSE, MODE_INCOME, MODE_BOTH),
            selectedOption = mode,
            onSelect = { mode = it },
            label = { option ->
                stringResource(
                    when (option) {
                        MODE_EXPENSE -> R.string.expense
                        MODE_INCOME -> R.string.income
                        else -> R.string.both
                    },
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        when (mode) {
            MODE_BOTH -> {
                CategoryShareSection(R.string.expense)
                CategoryShareList(
                    dashboard.expenseCategories,
                    ActivityKind.EXPENSE,
                    dashboard,
                    amountsVisible,
                    onCategoryClick,
                )
                Spacer(Modifier.height(12.dp))
                CategoryShareSection(R.string.income)
                CategoryShareList(
                    dashboard.incomeCategories,
                    ActivityKind.INCOME,
                    dashboard,
                    amountsVisible,
                    onCategoryClick,
                )
            }

            MODE_INCOME -> CategoryShareList(
                dashboard.incomeCategories,
                ActivityKind.INCOME,
                dashboard,
                amountsVisible,
                onCategoryClick,
            )

            else -> CategoryShareList(
                dashboard.expenseCategories,
                ActivityKind.EXPENSE,
                dashboard,
                amountsVisible,
                onCategoryClick,
            )
        }
    }
}

private const val MODE_EXPENSE = "EXPENSE"
private const val MODE_INCOME = "INCOME"
private const val MODE_BOTH = "BOTH"

@Composable
private fun CategoryShareSection(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun CategoryShareList(
    shares: List<CategoryShare>,
    kind: ActivityKind,
    dashboard: DashboardSnapshot,
    amountsVisible: Boolean,
    onCategoryClick: (ActivityKind, String?) -> Unit,
) {
    if (shares.isEmpty()) {
        Text(
            text = stringResource(R.string.no_data_for_period),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }
    val total = shares.sumOf(CategoryShare::amountMinor).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shares.forEach { share ->
            val fraction = share.amountMinor.toFloat() / total
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onCategoryClick(kind, share.categoryId) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(share.iconName ?: "circle_help", share.colorIndex)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = share.name ?: stringResource(R.string.no_category),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (amountsVisible) {
                                Money.format(
                                    share.amountMinor,
                                    dashboard.filter.currency,
                                    dashboard.fractionDigits,
                                )
                            } else {
                                stringResource(R.string.amount_hidden)
                            },
                            style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                        )
                    }
                    DenaroLinearMeter(
                        progress = fraction,
                        color = CategoryPalette[(share.colorIndex ?: 0).mod(CategoryPalette.size)],
                        height = 7.dp,
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}% · ${share.transactionCount}",
                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
