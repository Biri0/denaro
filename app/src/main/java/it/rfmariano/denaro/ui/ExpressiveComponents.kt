@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package it.rfmariano.denaro.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.rfmariano.denaro.R

/**
 * Connected Material 3 Expressive single-choice button group with shape-morphing
 * checked/pressed states. Preserves [selected] semantics for accessibility and tests.
 */
@Composable
fun <T> DenaroSegmentedChoiceRow(
    options: List<T>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selectedOption
            val shapes = when {
                options.size == 1 -> ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.MinHeight)
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            FilledTonalToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                shapes = shapes,
                colors = if (!enabled && isSelected) {
                    FilledTonalToggleButtonDefaults.colors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                            alpha = 0.78f,
                        ),
                        disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                            alpha = 0.88f,
                        ),
                    )
                } else {
                    FilledTonalToggleButtonDefaults.colors()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.RadioButton
                        selected = isSelected
                    },
            ) {
                Text(
                    text = label(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Shared container color for M3 Expressive segmented list rows. */
@Composable
fun denaroSegmentedItemColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = containerColor,
)

/** Soft tonal card container for grouping dashboard sections, summaries, and editor forms. */
@Composable
fun DenaroSectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Clean, flat linear progress meter for budgets and category shares (never wavy). */
@Composable
fun DenaroLinearMeter(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 8.dp,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "meter progress",
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Expressive morphing loading indicator for startup and modal progress states. */
@Composable
fun DenaroLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    ContainedLoadingIndicator(modifier = modifier)
}

/** Consistent expressive top app bar colors transitioning smoothly on scroll. */
@Composable
fun denaroTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Soft squircle/pill icon badge used in list rows, empty states, and lock/migration screens. */
@Composable
fun DenaroIconBadge(
    @DrawableRes icon: Int,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

data class FabMenuAction(
    val label: String,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit,
)

/** Material 3 Expressive FAB menu for quick-adding financial records from Home. */
@Composable
fun DenaroQuickAddFabMenu(
    actions: List<FabMenuAction>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val quickAddLabel = stringResource(R.string.quick_add)
    val closeMenuLabel = stringResource(R.string.close_menu)

    BackHandler(enabled = expanded) { expanded = false }

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = !expanded },
                containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                    initialColor = MaterialTheme.colorScheme.primaryContainer,
                    finalColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.semantics {
                    contentDescription = if (expanded) closeMenuLabel else quickAddLabel
                    stateDescription = if (expanded) closeMenuLabel else quickAddLabel
                },
            ) {
                val iconRes by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) R.drawable.ms_close else R.drawable.ms_add
                    }
                }
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (checkedProgress > 0.5f) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    action.onClick()
                },
                icon = {
                    Icon(
                        painter = painterResource(action.icon),
                        contentDescription = null,
                    )
                },
                text = { Text(action.label) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
