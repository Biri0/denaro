package it.rfmariano.denaro.widget

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
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
import androidx.glance.unit.ColorProvider
import it.rfmariano.denaro.R
import it.rfmariano.denaro.quickentry.QuickEntryAction
import it.rfmariano.denaro.quickentry.QuickEntryIntent
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
            QuickEntryWidgetContent(kind)
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
    val showHeader = when (kind) {
        QuickWidgetKind.ALL -> size.height >= 150.dp
        else -> size.height >= 110.dp
    }
    val useSingleRow = kind != QuickWidgetKind.ALL || size.width >= 340.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetColor(WIDGET_BACKGROUND_LIGHT, WIDGET_BACKGROUND_DARK))
            .cornerRadius(24.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showHeader) {
            Text(
                text = LocalContext.current.getString(R.string.widget_header),
                style = TextStyle(
                    color = widgetColor(WIDGET_TEXT_LIGHT, WIDGET_TEXT_DARK),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
        }
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
            .background(widgetColor(WIDGET_ACTION_LIGHT, WIDGET_ACTION_DARK))
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
            colorFilter = ColorFilter.tint(widgetColor(visual.lightColor, visual.darkColor)),
        )
        if (showLabel) {
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = context.getString(visual.label),
                style = TextStyle(
                    color = widgetColor(WIDGET_TEXT_LIGHT, WIDGET_TEXT_DARK),
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

@Composable
private fun widgetColor(light: Color, dark: Color): ColorProvider {
    val nightMode = LocalContext.current.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
    return ColorProvider(if (nightMode == Configuration.UI_MODE_NIGHT_YES) dark else light)
}

private val WIDGET_BACKGROUND_LIGHT = Color(0xFFF7FAF8)
private val WIDGET_BACKGROUND_DARK = Color(0xFF171C19)
private val WIDGET_ACTION_LIGHT = Color(0xFFE7F2EC)
private val WIDGET_ACTION_DARK = Color(0xFF24342C)
private val WIDGET_TEXT_LIGHT = Color(0xFF1A1C1B)
private val WIDGET_TEXT_DARK = Color(0xFFE1E3E0)
