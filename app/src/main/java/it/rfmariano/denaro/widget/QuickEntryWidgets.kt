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
import it.rfmariano.denaro.ui.theme.DenaroActionSurfaceDark
import it.rfmariano.denaro.ui.theme.DenaroActionSurfaceLight
import it.rfmariano.denaro.ui.theme.DenaroAmber
import it.rfmariano.denaro.ui.theme.DenaroAmberDark
import it.rfmariano.denaro.ui.theme.DenaroBackground
import it.rfmariano.denaro.ui.theme.DenaroBackgroundDark
import it.rfmariano.denaro.ui.theme.DenaroGreen
import it.rfmariano.denaro.ui.theme.DenaroGreenDark
import it.rfmariano.denaro.ui.theme.DenaroNeutral
import it.rfmariano.denaro.ui.theme.DenaroNeutralDark
import it.rfmariano.denaro.ui.theme.DenaroOnSurfaceDark
import it.rfmariano.denaro.ui.theme.DenaroOnSurfaceLight
import it.rfmariano.denaro.ui.theme.DenaroSurfaceDark
import it.rfmariano.denaro.ui.theme.LightColorScheme
import com.composables.icons.lucide.R as LucideR

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
            .cornerRadius(24.dp)
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
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
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
            .background(GlanceTheme.colors.surface)
            .cornerRadius(18.dp)
            .clickable(actionStartActivity(QuickEntryIntent.create(context, action)))
            .padding(vertical = 7.dp, horizontal = 4.dp),
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
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = context.getString(visual.label),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
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
)

private fun QuickEntryAction.visual(): ActionVisual = when (this) {
    QuickEntryAction.INCOME -> ActionVisual(
        LucideR.drawable.lucide_ic_arrow_down,
        R.string.income,
        Color(0xFF237A45),
        Color(0xFF68D391),
    )

    QuickEntryAction.EXPENSE -> ActionVisual(
        LucideR.drawable.lucide_ic_arrow_up,
        R.string.expense,
        Color(0xFFBA1A1A),
        Color(0xFFFFB4AB),
    )

    QuickEntryAction.TRANSFER -> ActionVisual(
        LucideR.drawable.lucide_ic_arrow_down_up,
        R.string.transfer,
        Color(0xFF7A5F00),
        Color(0xFFE7C353),
    )

    QuickEntryAction.BORROW -> ActionVisual(
        LucideR.drawable.lucide_ic_arrow_down_to_line,
        R.string.borrow,
        Color(0xFF006C4C),
        Color(0xFF63DBAC),
    )

    QuickEntryAction.LEND -> ActionVisual(
        LucideR.drawable.lucide_ic_arrow_up_from_line,
        R.string.lend,
        Color(0xFF53645B),
        Color(0xFFB8CCC0),
    )
}

private val DenaroWidgetColorsProvider = colorProviders(
    primary = ColorProvider(DenaroGreen, DenaroGreenDark),
    onPrimary = ColorProvider(LightColorScheme.onPrimary, DarkColorScheme.onPrimary),
    primaryContainer = ColorProvider(DenaroActionSurfaceLight, DenaroActionSurfaceDark),
    onPrimaryContainer = ColorProvider(DenaroOnSurfaceLight, DenaroOnSurfaceDark),
    secondary = ColorProvider(DenaroNeutral, DenaroNeutralDark),
    onSecondary = ColorProvider(LightColorScheme.onSecondary, DarkColorScheme.onSecondary),
    secondaryContainer = ColorProvider(DenaroActionSurfaceLight, DenaroActionSurfaceDark),
    onSecondaryContainer = ColorProvider(DenaroOnSurfaceLight, DenaroOnSurfaceDark),
    tertiary = ColorProvider(DenaroAmber, DenaroAmberDark),
    onTertiary = ColorProvider(LightColorScheme.onTertiary, DarkColorScheme.onTertiary),
    tertiaryContainer = ColorProvider(DenaroActionSurfaceLight, DenaroActionSurfaceDark),
    onTertiaryContainer = ColorProvider(DenaroOnSurfaceLight, DenaroOnSurfaceDark),
    error = ColorProvider(LightColorScheme.error, DarkColorScheme.error),
    errorContainer = ColorProvider(LightColorScheme.errorContainer, DarkColorScheme.errorContainer),
    onError = ColorProvider(LightColorScheme.onError, DarkColorScheme.onError),
    onErrorContainer = ColorProvider(
        LightColorScheme.onErrorContainer,
        DarkColorScheme.onErrorContainer
    ),
    background = ColorProvider(DenaroBackground, DenaroBackgroundDark),
    onBackground = ColorProvider(DenaroOnSurfaceLight, DenaroOnSurfaceDark),
    surface = ColorProvider(DenaroActionSurfaceLight, DenaroActionSurfaceDark),
    onSurface = ColorProvider(DenaroOnSurfaceLight, DenaroOnSurfaceDark),
    surfaceVariant = ColorProvider(DenaroActionSurfaceLight, DenaroActionSurfaceDark),
    onSurfaceVariant = ColorProvider(DenaroOnSurfaceLight, DenaroOnSurfaceDark),
    outline = ColorProvider(LightColorScheme.outline, DarkColorScheme.outline),
    inverseOnSurface = ColorProvider(
        LightColorScheme.inverseOnSurface,
        DarkColorScheme.inverseOnSurface
    ),
    inverseSurface = ColorProvider(LightColorScheme.inverseSurface, DarkColorScheme.inverseSurface),
    inversePrimary = ColorProvider(LightColorScheme.inversePrimary, DarkColorScheme.inversePrimary),
    widgetBackground = ColorProvider(DenaroBackground, DenaroSurfaceDark),
)
