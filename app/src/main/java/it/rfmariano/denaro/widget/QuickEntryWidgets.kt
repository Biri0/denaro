package it.rfmariano.denaro.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.color.colorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import it.rfmariano.denaro.R
import it.rfmariano.denaro.quickentry.QuickEntryAction
import it.rfmariano.denaro.quickentry.QuickEntryIntent
import it.rfmariano.denaro.ui.theme.DarkColorScheme
import it.rfmariano.denaro.ui.theme.DarkExtendedColors
import it.rfmariano.denaro.ui.theme.LightColorScheme
import it.rfmariano.denaro.ui.theme.LightExtendedColors

internal enum class QuickWidgetKind(
    val actions: List<QuickEntryAction>,
    val sizes: Set<DpSize>,
) {
    ALL(
        actions = listOf(
            QuickEntryAction.INCOME,
            QuickEntryAction.EXPENSE,
            QuickEntryAction.TRANSFER,
            QuickEntryAction.BORROW,
            QuickEntryAction.LEND,
        ),
        sizes = setOf(
            DpSize(180.dp, 120.dp),
            DpSize(280.dp, 120.dp),
            DpSize(360.dp, 72.dp),
            DpSize(280.dp, 160.dp),
        ),
    ),
    TRANSACTIONS(
        actions = listOf(
            QuickEntryAction.INCOME,
            QuickEntryAction.EXPENSE,
            QuickEntryAction.TRANSFER,
        ),
        sizes = setOf(
            DpSize(180.dp, 64.dp),
            DpSize(280.dp, 64.dp),
            DpSize(280.dp, 120.dp),
        ),
    ),
    DEBTS(
        actions = listOf(QuickEntryAction.BORROW, QuickEntryAction.LEND),
        sizes = setOf(
            DpSize(120.dp, 64.dp),
            DpSize(200.dp, 64.dp),
            DpSize(200.dp, 120.dp),
        ),
    ),
}

private class QuickEntryGlanceWidget(
    private val kind: QuickWidgetKind,
) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(kind.sizes)

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        provideContent {
            GlanceTheme(colors = DenaroWidgetColorsProvider) {
                QuickEntryWidgetContent(kind)
            }
        }
    }
}

class QuickEntryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickEntryGlanceWidget(QuickWidgetKind.ALL)
}

class QuickTransactionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget =
        QuickEntryGlanceWidget(QuickWidgetKind.TRANSACTIONS)
}

class QuickDebtWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickEntryGlanceWidget(QuickWidgetKind.DEBTS)
}

@Composable
internal fun QuickEntryWidgetContent(kind: QuickWidgetKind) {
    val size = LocalSize.current
    val showLabels = when (kind) {
        QuickWidgetKind.ALL -> size.width >= 260.dp && size.height >= 150.dp
        QuickWidgetKind.TRANSACTIONS -> size.width >= 260.dp
        QuickWidgetKind.DEBTS -> size.width >= 180.dp
    }
    val useSingleRow = kind != QuickWidgetKind.ALL || size.width >= 340.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (useSingleRow) {
            ActionRow(kind.actions, showLabels)
        } else {
            ActionRow(kind.actions.take(3), showLabels)
            Spacer(modifier = GlanceModifier.height(6.dp))
            ActionRow(kind.actions.drop(3), showLabels)
        }
    }
}

@Composable
private fun ColumnScope.ActionRow(actions: List<QuickEntryAction>, showLabels: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .defaultWeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            if (index > 0) {
                Spacer(modifier = GlanceModifier.width(8.dp))
            }
            QuickAction(action, showLabels)
        }
    }
}

@Composable
private fun RowScope.QuickAction(action: QuickEntryAction, showLabel: Boolean) {
    val context = LocalContext.current
    val visual = action.visual()
    Column(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .background(ColorProvider(visual.lightTile, visual.darkTile))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity(QuickEntryIntent.create(context, action)))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(visual.icon),
            contentDescription = context.getString(visual.label),
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(ColorProvider(visual.lightColor, visual.darkColor)),
        )
        if (showLabel) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = context.getString(visual.label),
                style = TextStyle(
                    color = ColorProvider(visual.lightColor, visual.darkColor),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

private data class ActionVisual(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    val lightColor: Color,
    val darkColor: Color,
    val lightTile: Color,
    val darkTile: Color,
)

private fun QuickEntryAction.visual(): ActionVisual = when (this) {
    QuickEntryAction.INCOME -> ActionVisual(
        icon = R.drawable.ms_arrow_downward,
        label = R.string.income,
        lightColor = LightExtendedColors.onPositiveContainer,
        darkColor = DarkExtendedColors.onPositiveContainer,
        lightTile = LightExtendedColors.positiveContainer,
        darkTile = DarkExtendedColors.positiveContainer,
    )

    QuickEntryAction.EXPENSE -> ActionVisual(
        icon = R.drawable.ms_arrow_upward,
        label = R.string.expense,
        lightColor = LightColorScheme.onErrorContainer,
        darkColor = DarkColorScheme.error,
        lightTile = LightColorScheme.errorContainer,
        darkTile = Color(0xFF5A1D22),
    )

    QuickEntryAction.TRANSFER -> ActionVisual(
        icon = R.drawable.ms_swap_vert,
        label = R.string.transfer,
        lightColor = LightColorScheme.onTertiaryContainer,
        darkColor = DarkColorScheme.onTertiaryContainer,
        lightTile = LightColorScheme.tertiaryContainer,
        darkTile = DarkColorScheme.tertiaryContainer,
    )

    QuickEntryAction.BORROW -> ActionVisual(
        icon = R.drawable.ms_download,
        label = R.string.borrow,
        lightColor = LightExtendedColors.onWarningContainer,
        darkColor = DarkExtendedColors.onWarningContainer,
        lightTile = LightExtendedColors.warningContainer,
        darkTile = DarkExtendedColors.warningContainer,
    )

    QuickEntryAction.LEND -> ActionVisual(
        icon = R.drawable.ms_upload,
        label = R.string.lend,
        lightColor = LightColorScheme.onSecondaryContainer,
        darkColor = DarkColorScheme.onSecondaryContainer,
        lightTile = LightColorScheme.secondaryContainer,
        darkTile = DarkColorScheme.secondaryContainer,
    )
}

private val DenaroWidgetColorsProvider = colorProviders(
    primary = ColorProvider(LightColorScheme.primary, DarkColorScheme.primary),
    onPrimary = ColorProvider(LightColorScheme.onPrimary, DarkColorScheme.onPrimary),
    primaryContainer = ColorProvider(
        LightColorScheme.primaryContainer,
        DarkColorScheme.primaryContainer,
    ),
    onPrimaryContainer = ColorProvider(
        LightColorScheme.onPrimaryContainer,
        DarkColorScheme.onPrimaryContainer,
    ),
    secondary = ColorProvider(LightColorScheme.secondary, DarkColorScheme.secondary),
    onSecondary = ColorProvider(LightColorScheme.onSecondary, DarkColorScheme.onSecondary),
    secondaryContainer = ColorProvider(
        LightColorScheme.secondaryContainer,
        DarkColorScheme.secondaryContainer,
    ),
    onSecondaryContainer = ColorProvider(
        LightColorScheme.onSecondaryContainer,
        DarkColorScheme.onSecondaryContainer,
    ),
    tertiary = ColorProvider(LightColorScheme.tertiary, DarkColorScheme.tertiary),
    onTertiary = ColorProvider(LightColorScheme.onTertiary, DarkColorScheme.onTertiary),
    tertiaryContainer = ColorProvider(
        LightColorScheme.tertiaryContainer,
        DarkColorScheme.tertiaryContainer,
    ),
    onTertiaryContainer = ColorProvider(
        LightColorScheme.onTertiaryContainer,
        DarkColorScheme.onTertiaryContainer,
    ),
    error = ColorProvider(LightColorScheme.error, DarkColorScheme.error),
    errorContainer = ColorProvider(
        LightColorScheme.errorContainer,
        DarkColorScheme.errorContainer,
    ),
    onError = ColorProvider(LightColorScheme.onError, DarkColorScheme.onError),
    onErrorContainer = ColorProvider(
        LightColorScheme.onErrorContainer,
        DarkColorScheme.onErrorContainer,
    ),
    background = ColorProvider(
        LightColorScheme.surfaceContainerLow,
        DarkColorScheme.surfaceContainerLow
    ),
    onBackground = ColorProvider(LightColorScheme.onSurface, DarkColorScheme.onSurface),
    surface = ColorProvider(LightColorScheme.surfaceContainer, DarkColorScheme.surfaceContainer),
    onSurface = ColorProvider(LightColorScheme.onSurface, DarkColorScheme.onSurface),
    surfaceVariant = ColorProvider(
        LightColorScheme.surfaceContainerHigh,
        DarkColorScheme.surfaceContainerHigh,
    ),
    onSurfaceVariant = ColorProvider(
        LightColorScheme.onSurfaceVariant,
        DarkColorScheme.onSurfaceVariant,
    ),
    outline = ColorProvider(LightColorScheme.outline, DarkColorScheme.outline),
    inverseOnSurface = ColorProvider(
        LightColorScheme.inverseOnSurface,
        DarkColorScheme.inverseOnSurface,
    ),
    inverseSurface = ColorProvider(
        LightColorScheme.inverseSurface,
        DarkColorScheme.inverseSurface,
    ),
    inversePrimary = ColorProvider(
        LightColorScheme.inversePrimary,
        DarkColorScheme.inversePrimary,
    ),
    widgetBackground = ColorProvider(
        LightColorScheme.surfaceContainerLow,
        DarkColorScheme.surfaceContainer,
    ),
)
