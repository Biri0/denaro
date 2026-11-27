package it.rfmariano.denaro.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Denaro color system.
 *
 * Generated from the Denaro green seed (#006C4C) with the Material 3 tonal-spot
 * scheme (material-color-utilities, 2021 spec). Semantic positive and warning
 * roles are harmonized toward the seed so they sit naturally next to the brand.
 */
const val DenaroSeedArgb: Long = 0xFF006C4C

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1F6A4E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F2CE),
    onPrimaryContainer = Color(0xFF005138),
    inversePrimary = Color(0xFF8DD5B3),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF354B41),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FB),
    onTertiaryContainer = Color(0xFF244C5B),
    background = Color(0xFFF5FBF5),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF5),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    surfaceTint = Color(0xFF1F6A4E),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFECF2ED),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF5FBF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5EF),
    surfaceContainer = Color(0xFFEAEFEA),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DE),
    surfaceDim = Color(0xFFD6DBD6),
    primaryFixed = Color(0xFFA8F2CE),
    primaryFixedDim = Color(0xFF8DD5B3),
    onPrimaryFixed = Color(0xFF002115),
    onPrimaryFixedVariant = Color(0xFF005138),
    secondaryFixed = Color(0xFFCEE9DA),
    secondaryFixedDim = Color(0xFFB3CCBE),
    onSecondaryFixed = Color(0xFF092016),
    onSecondaryFixedVariant = Color(0xFF354B41),
    tertiaryFixed = Color(0xFFC1E8FB),
    tertiaryFixedDim = Color(0xFFA5CCDF),
    onTertiaryFixed = Color(0xFF001F29),
    onTertiaryFixedVariant = Color(0xFF244C5B),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8DD5B3),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFFA8F2CE),
    inversePrimary = Color(0xFF1F6A4E),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFA5CCDF),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceTint = Color(0xFF8DD5B3),
    inverseSurface = Color(0xFFDEE4DE),
    inverseOnSurface = Color(0xFF2C322E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF404943),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353B37),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    surfaceDim = Color(0xFF0F1512),
    primaryFixed = Color(0xFFA8F2CE),
    primaryFixedDim = Color(0xFF8DD5B3),
    onPrimaryFixed = Color(0xFF002115),
    onPrimaryFixedVariant = Color(0xFF005138),
    secondaryFixed = Color(0xFFCEE9DA),
    secondaryFixedDim = Color(0xFFB3CCBE),
    onSecondaryFixed = Color(0xFF092016),
    onSecondaryFixedVariant = Color(0xFF354B41),
    tertiaryFixed = Color(0xFFC1E8FB),
    tertiaryFixedDim = Color(0xFFA5CCDF),
    onTertiaryFixed = Color(0xFF001F29),
    onTertiaryFixedVariant = Color(0xFF244C5B),
)

/** Semantic roles Material 3 doesn't provide: money in (positive) and caution (warning). */
@Immutable
data class DenaroExtendedColors(
    val positive: Color,
    val onPositive: Color,
    val positiveContainer: Color,
    val onPositiveContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightExtendedColors = DenaroExtendedColors(
    positive = Color(0xFF006D43),
    onPositive = Color(0xFFFFFFFF),
    positiveContainer = Color(0xFF96F6BE),
    onPositiveContainer = Color(0xFF002111),
    warning = Color(0xFF686005),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFF0E582),
    onWarningContainer = Color(0xFF1F1C00),
)

internal val DarkExtendedColors = DenaroExtendedColors(
    positive = Color(0xFF7ADAA3),
    onPositive = Color(0xFF00391F),
    positiveContainer = Color(0xFF005231),
    onPositiveContainer = Color(0xFF96F6BE),
    warning = Color(0xFFD4C969),
    onWarning = Color(0xFF363100),
    warningContainer = Color(0xFF4E4800),
    onWarningContainer = Color(0xFFF0E582),
)

// Legacy aliases kept for the Glance widget, mapped onto the new palette.
val DenaroGreen = LightColorScheme.primary
val DenaroGreenDark = DarkColorScheme.primary
val DenaroNeutral = LightColorScheme.secondary
val DenaroNeutralDark = DarkColorScheme.secondary
val DenaroAmber = LightExtendedColors.warning
val DenaroAmberDark = DarkExtendedColors.warning
val DenaroBackground = LightColorScheme.surfaceContainerLow
val DenaroBackgroundDark = DarkColorScheme.surfaceContainerLow
val DenaroSurfaceDark = DarkColorScheme.surfaceContainer
val DenaroActionSurfaceLight = LightColorScheme.primaryContainer
val DenaroActionSurfaceDark = DarkColorScheme.primaryContainer
val DenaroOnSurfaceLight = LightColorScheme.onSurface
val DenaroOnSurfaceDark = DarkColorScheme.onSurface
val Positive = LightExtendedColors.positive
val PositiveDark = DarkExtendedColors.positive
