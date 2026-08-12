package it.rfmariano.denaro.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.ActivityItem
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.ui.theme.Positive
import it.rfmariano.denaro.ui.theme.PositiveDark
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.composables.icons.lucide.R as LucideR

@Composable
fun AmountText(
    amountMinor: Long,
    currency: String,
    amountsVisible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Text(
        text = if (amountsVisible) {
            Money.format(amountMinor, currency)
        } else {
            stringResource(R.string.amount_hidden)
        },
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun AccountRow(
    account: AccountSummary,
    amountsVisible: Boolean,
    onClick: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_wallet),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.titleMedium)
            account.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        AmountText(
            amountMinor = account.balanceMinor,
            currency = account.currency,
            amountsVisible = amountsVisible,
        )
        if (trailingAction != null) {
            Spacer(Modifier.width(8.dp))
            trailingAction()
        }
    }
}

@Composable
fun ActivityRow(
    item: ActivityItem,
    amountsVisible: Boolean,
    onClick: () -> Unit,
    perspectiveAccountId: String? = null,
    showTransferSign: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.kind != ActivityKind.TRANSFER && item.categoryId != null) {
            CategoryIcon(item.categoryIconName, item.categoryColorIndex)
        } else {
            ActivityIcon(item.kind)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.description ?: when (item.kind) {
                    ActivityKind.INCOME -> stringResource(R.string.income)
                    ActivityKind.EXPENSE -> stringResource(R.string.expense)
                    ActivityKind.TRANSFER -> stringResource(R.string.transfer)
                    ActivityKind.DEBT -> when (item.debtMovement) {
                        it.rfmariano.denaro.data.finance.DebtMovementKind.OPENING -> stringResource(
                            if (item.debtDirection == it.rfmariano.denaro.data.local.DebtDirection.BORROWED) R.string.borrowed_from else R.string.lent_to,
                            item.externalCounterpartyName.orEmpty(),
                        )

                        it.rfmariano.denaro.data.finance.DebtMovementKind.REPAYMENT -> stringResource(
                            if (item.debtDirection == it.rfmariano.denaro.data.local.DebtDirection.BORROWED) R.string.repaid_to else R.string.repaid_by,
                            item.externalCounterpartyName.orEmpty(),
                        )

                        null -> stringResource(R.string.debt)
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                text = when (item.kind) {
                    ActivityKind.TRANSFER -> "${item.accountName} → ${item.counterpartyAccountName.orEmpty()}"
                    ActivityKind.DEBT -> listOfNotNull(
                        item.accountName,
                        item.externalCounterpartyName
                    ).joinToString(" · ")

                    else -> listOfNotNull(item.accountName, item.categoryName).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val signedAmount = item.signedAmount(perspectiveAccountId, showTransferSign)
            AmountText(
                amountMinor = signedAmount,
                currency = item.currency,
                amountsVisible = amountsVisible,
                color = when (item.kind) {
                    ActivityKind.INCOME -> if (androidx.compose.foundation.isSystemInDarkTheme()) {
                        PositiveDark
                    } else {
                        Positive
                    }

                    ActivityKind.EXPENSE -> MaterialTheme.colorScheme.error
                    ActivityKind.TRANSFER -> MaterialTheme.colorScheme.onSurface
                    ActivityKind.DEBT -> if (signedAmount >= 0) {
                        if (androidx.compose.foundation.isSystemInDarkTheme()) PositiveDark else Positive
                    } else MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = item.occurredAt.formattedDate(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun ActivityItem.signedAmount(
    perspectiveAccountId: String?,
    showTransferSign: Boolean = true,
): Long =
    when (kind) {
        ActivityKind.INCOME -> amountMinor
        ActivityKind.EXPENSE -> -amountMinor
        ActivityKind.TRANSFER -> {
            if (!showTransferSign || counterpartyAccountId == perspectiveAccountId) {
                amountMinor
            } else {
                -amountMinor
            }
        }

        ActivityKind.DEBT -> when {
            debtDirection == it.rfmariano.denaro.data.local.DebtDirection.BORROWED &&
                    debtMovement == it.rfmariano.denaro.data.finance.DebtMovementKind.OPENING -> amountMinor

            debtDirection == it.rfmariano.denaro.data.local.DebtDirection.LENT &&
                    debtMovement == it.rfmariano.denaro.data.finance.DebtMovementKind.REPAYMENT -> amountMinor

            else -> -amountMinor
        }
    }

@Composable
private fun ActivityIcon(kind: ActivityKind) {
    @DrawableRes val icon = when (kind) {
        ActivityKind.INCOME -> LucideR.drawable.lucide_ic_arrow_down
        ActivityKind.EXPENSE -> LucideR.drawable.lucide_ic_arrow_up
        ActivityKind.TRANSFER -> LucideR.drawable.lucide_ic_arrow_down_up
        ActivityKind.DEBT -> LucideR.drawable.lucide_ic_hand_coins
    }
    val tint = when (kind) {
        ActivityKind.INCOME -> if (androidx.compose.foundation.isSystemInDarkTheme()) {
            PositiveDark
        } else {
            Positive
        }

        ActivityKind.EXPENSE -> MaterialTheme.colorScheme.error
        ActivityKind.TRANSFER -> MaterialTheme.colorScheme.tertiary
        ActivityKind.DEBT -> MaterialTheme.colorScheme.secondary
    }
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
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
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .alpha(0.7f),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonAlpha()
    LoadingSkeletonContainer(modifier) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            SkeletonBlock(
                modifier = Modifier
                    .width(84.dp)
                    .height(14.dp),
                alpha = alpha,
            )
            Spacer(Modifier.height(12.dp))
            SkeletonBlock(
                modifier = Modifier
                    .width(190.dp)
                    .height(30.dp),
                alpha = alpha,
            )
        }
        SkeletonSectionHeader(alpha)
        repeat(6) { AccountSkeletonRow(alpha) }
    }
}

@Composable
fun DashboardLoadingSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonAlpha()
    LoadingSkeletonContainer(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) {
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(76.dp),
                    alpha = alpha,
                )
            }
        }
        SkeletonBlock(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .width(142.dp)
                .height(18.dp),
            alpha = alpha,
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(170.dp),
            alpha = alpha,
        )
        SkeletonSectionHeader(alpha)
        repeat(4) { ActivitySkeletonRow(alpha) }
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
        Column(modifier = Modifier.padding(20.dp)) {
            SkeletonBlock(
                modifier = Modifier
                    .width(110.dp)
                    .height(14.dp),
                alpha = alpha,
            )
            Spacer(Modifier.height(10.dp))
            SkeletonBlock(
                modifier = Modifier
                    .width(180.dp)
                    .height(30.dp),
                alpha = alpha,
            )
            Spacer(Modifier.height(20.dp))
            SkeletonBlock(
                modifier = Modifier
                    .width(220.dp)
                    .height(14.dp),
                alpha = alpha,
            )
        }
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(22.dp), alpha)
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
                    .width(176.dp)
                    .height(13.dp),
                alpha = alpha,
            )
        }
        SkeletonBlock(
            modifier = Modifier
                .width(92.dp)
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(36.dp), alpha)
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
) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
            ),
    )
}

@Composable
private fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton pulse")
    return transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.78f,
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
