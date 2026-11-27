@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package it.rfmariano.denaro.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.ActivityItem
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.DebtMovementKind
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.ui.theme.denaroColors
import it.rfmariano.denaro.ui.theme.tabularNumbers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AmountText(
    amountMinor: Long,
    currency: String,
    fractionDigits: Int,
    amountsVisible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.titleMediumEmphasized,
) {
    Text(
        text = if (amountsVisible) {
            Money.format(amountMinor, currency, fractionDigits)
        } else {
            stringResource(R.string.amount_hidden)
        },
        modifier = modifier,
        color = color,
        style = style.tabularNumbers(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun AccountRow(
    account: AccountSummary,
    amountsVisible: Boolean,
    onClick: () -> Unit,
    shapes: ListItemShapes = ListItemDefaults.segmentedShapes(0, 1),
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val badgeBg = if (account.isSavings) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val badgeFg = if (account.isSavings) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = denaroSegmentedItemColors(),
        leadingContent = {
            DenaroIconBadge(
                icon = if (account.isSavings) {
                    R.drawable.ms_savings
                } else {
                    R.drawable.ms_account_balance_wallet
                },
                containerColor = badgeBg,
                contentColor = badgeFg,
                size = 42.dp,
                iconSize = 21.dp,
            )
        },
        supportingContent = account.description?.takeIf(String::isNotBlank)?.let { desc ->
            {
                Text(
                    text = desc,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmountText(
                    amountMinor = account.balanceMinor,
                    currency = account.currency,
                    fractionDigits = account.fractionDigits,
                    amountsVisible = amountsVisible,
                )
                if (trailingAction != null) {
                    Spacer(Modifier.width(8.dp))
                    trailingAction()
                }
            }
        },
    ) {
        Text(
            text = account.name,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ActivityRow(
    item: ActivityItem,
    amountsVisible: Boolean,
    onClick: () -> Unit,
    perspectiveAccountId: String? = null,
    showTransferSign: Boolean = true,
    shapes: ListItemShapes = ListItemDefaults.segmentedShapes(0, 1),
) {
    val positiveColor = MaterialTheme.denaroColors.positive
    val signedAmount = item.signedAmount(perspectiveAccountId, showTransferSign)
    val amountColor = when (item.kind) {
        ActivityKind.INCOME -> positiveColor
        ActivityKind.EXPENSE -> MaterialTheme.colorScheme.error
        ActivityKind.TRANSFER -> MaterialTheme.colorScheme.onSurface
        ActivityKind.ADJUSTMENT, ActivityKind.DEBT ->
            if (signedAmount >= 0) positiveColor else MaterialTheme.colorScheme.error
    }

    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = denaroSegmentedItemColors(),
        leadingContent = {
            if (item.kind != ActivityKind.TRANSFER && item.categoryId != null) {
                CategoryIcon(item.categoryIconName, item.categoryColorIndex)
            } else {
                ActivityIcon(item.kind)
            }
        },
        supportingContent = {
            Text(
                text = when (item.kind) {
                    ActivityKind.TRANSFER ->
                        "${item.accountName} → ${item.counterpartyAccountName.orEmpty()}"

                    ActivityKind.DEBT -> listOfNotNull(
                        item.accountName,
                        item.externalCounterpartyName,
                    ).joinToString(" · ")

                    ActivityKind.ADJUSTMENT -> item.accountName
                    else -> listOfNotNull(item.accountName, item.categoryName).joinToString(" · ")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    amountMinor = signedAmount,
                    currency = item.currency,
                    fractionDigits = item.fractionDigits,
                    amountsVisible = amountsVisible,
                    color = amountColor,
                )
                Text(
                    text = item.occurredAt.formattedDate(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(
            text = item.description ?: when (item.kind) {
                ActivityKind.INCOME -> stringResource(R.string.income)
                ActivityKind.EXPENSE -> stringResource(R.string.expense)
                ActivityKind.TRANSFER -> stringResource(R.string.transfer)
                ActivityKind.ADJUSTMENT -> stringResource(R.string.balance_adjustment)
                ActivityKind.DEBT -> when (item.debtMovement) {
                    DebtMovementKind.OPENING -> stringResource(
                        if (item.debtDirection == DebtDirection.BORROWED) {
                            R.string.borrowed_from
                        } else {
                            R.string.lent_to
                        },
                        item.externalCounterpartyName.orEmpty(),
                    )

                    DebtMovementKind.REPAYMENT -> stringResource(
                        if (item.debtDirection == DebtDirection.BORROWED) {
                            R.string.repaid_to
                        } else {
                            R.string.repaid_by
                        },
                        item.externalCounterpartyName.orEmpty(),
                    )

                    null -> stringResource(R.string.debt)
                }
            },
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun ActivityItem.signedAmount(
    perspectiveAccountId: String?,
    showTransferSign: Boolean = true,
): Long =
    when (kind) {
        ActivityKind.INCOME -> amountMinor
        ActivityKind.EXPENSE -> -amountMinor
        ActivityKind.ADJUSTMENT -> amountMinor
        ActivityKind.TRANSFER -> {
            if (!showTransferSign || counterpartyAccountId == perspectiveAccountId) {
                amountMinor
            } else {
                -amountMinor
            }
        }

        ActivityKind.DEBT -> when {
            debtDirection == DebtDirection.BORROWED &&
                    debtMovement == DebtMovementKind.OPENING -> amountMinor

            debtDirection == DebtDirection.LENT &&
                    debtMovement == DebtMovementKind.REPAYMENT -> amountMinor

            else -> -amountMinor
        }
    }

@Composable
private fun ActivityIcon(kind: ActivityKind) {
    @DrawableRes val icon = when (kind) {
        ActivityKind.INCOME -> R.drawable.ms_arrow_downward
        ActivityKind.EXPENSE -> R.drawable.ms_arrow_upward
        ActivityKind.TRANSFER -> R.drawable.ms_swap_vert
        ActivityKind.ADJUSTMENT -> R.drawable.ms_balance
        ActivityKind.DEBT -> R.drawable.ms_handshake
    }
    val containerColor = when (kind) {
        ActivityKind.INCOME -> MaterialTheme.denaroColors.positiveContainer
        ActivityKind.EXPENSE -> MaterialTheme.colorScheme.errorContainer
        ActivityKind.TRANSFER -> MaterialTheme.colorScheme.tertiaryContainer
        ActivityKind.ADJUSTMENT -> MaterialTheme.colorScheme.secondaryContainer
        ActivityKind.DEBT -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (kind) {
        ActivityKind.INCOME -> MaterialTheme.denaroColors.onPositiveContainer
        ActivityKind.EXPENSE -> MaterialTheme.colorScheme.onErrorContainer
        ActivityKind.TRANSFER -> MaterialTheme.colorScheme.onTertiaryContainer
        ActivityKind.ADJUSTMENT -> MaterialTheme.colorScheme.onSecondaryContainer
        ActivityKind.DEBT -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    DenaroIconBadge(
        icon = icon,
        containerColor = containerColor,
        contentColor = contentColor,
        size = 42.dp,
        iconSize = 21.dp,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DenaroIconBadge(
            icon = icon,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 72.dp,
            iconSize = 34.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        if (description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonAlpha()
    LoadingSkeletonContainer(modifier) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(136.dp),
            alpha = alpha,
            radius = 28.dp,
        )
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp),
            alpha = alpha,
            radius = 24.dp,
        )
        Spacer(Modifier.height(12.dp))
        SkeletonSectionHeader(alpha)
        repeat(4) { AccountSkeletonRow(alpha) }
    }
}

@Composable
fun DashboardLoadingSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonAlpha()
    LoadingSkeletonContainer(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) {
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(82.dp),
                    alpha = alpha,
                    radius = 20.dp,
                )
            }
        }
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(220.dp),
            alpha = alpha,
            radius = 28.dp,
        )
        SkeletonSectionHeader(alpha)
        repeat(3) { ActivitySkeletonRow(alpha) }
    }
}

@Composable
fun AccountListLoadingSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 7,
) {
    val alpha = rememberSkeletonAlpha()
    LoadingSkeletonContainer(modifier) {
        repeat(rowCount) { AccountSkeletonRow(alpha) }
    }
}

@Composable
fun AccountDetailLoadingSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonAlpha()
    LoadingSkeletonContainer(modifier) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(168.dp),
            alpha = alpha,
            radius = 28.dp,
        )
        SkeletonSectionHeader(alpha)
        repeat(3) { ActivitySkeletonRow(alpha) }
    }
}

@Composable
fun ActivityLoadingSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 7,
) {
    val alpha = rememberSkeletonAlpha()
    LoadingSkeletonContainer(modifier) {
        repeat(rowCount) { ActivitySkeletonRow(alpha) }
    }
}

@Composable
private fun LoadingSkeletonContainer(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val loadingLabel = stringResource(R.string.loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = loadingLabel },
        content = content,
    )
}

@Composable
private fun AccountSkeletonRow(alpha: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(42.dp), alpha, radius = 14.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBlock(
                modifier = Modifier
                    .width(132.dp)
                    .height(17.dp),
                alpha = alpha,
            )
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(
                modifier = Modifier
                    .width(164.dp)
                    .height(13.dp),
                alpha = alpha,
            )
        }
        SkeletonBlock(
            modifier = Modifier
                .width(88.dp)
                .height(18.dp),
            alpha = alpha,
        )
    }
}

@Composable
private fun ActivitySkeletonRow(alpha: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(42.dp), alpha, radius = 14.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBlock(
                modifier = Modifier
                    .width(150.dp)
                    .height(17.dp),
                alpha = alpha,
            )
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(
                modifier = Modifier
                    .width(104.dp)
                    .height(13.dp),
                alpha = alpha,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            SkeletonBlock(
                modifier = Modifier
                    .width(82.dp)
                    .height(17.dp),
                alpha = alpha,
            )
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(
                modifier = Modifier
                    .width(68.dp)
                    .height(13.dp),
                alpha = alpha,
            )
        }
    }
}

@Composable
private fun SkeletonSectionHeader(alpha: Float) {
    SkeletonBlock(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .width(116.dp)
            .height(17.dp),
        alpha = alpha,
    )
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    alpha: Float,
    radius: androidx.compose.ui.unit.Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(radius),
            ),
    )
}

@Composable
private fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton pulse")
    return transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton alpha",
    ).value
}

fun Long.formattedDate(): String = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate())
