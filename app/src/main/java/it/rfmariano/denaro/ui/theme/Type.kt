package it.rfmariano.denaro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val Baseline = Typography()

/**
 * Material 3 Expressive type scale.
 *
 * Starts from the M3 baseline (including the *Emphasized* styles) and gives
 * display/headline roles a heavier, tighter treatment for big money figures.
 */
val Typography = Baseline.copy(
    displayLarge = Baseline.displayLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = Baseline.displayMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.25).sp
    ),
    displaySmall = Baseline.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineLarge = Baseline.headlineLarge.copy(fontWeight = FontWeight.Medium),
    headlineMedium = Baseline.headlineMedium.copy(fontWeight = FontWeight.Medium),
    headlineSmall = Baseline.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = Baseline.titleLarge.copy(fontWeight = FontWeight.Medium),
    displayLargeEmphasized = Baseline.displayLargeEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1).sp,
    ),
    displayMediumEmphasized = Baseline.displayMediumEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    displaySmallEmphasized = Baseline.displaySmallEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    headlineLargeEmphasized = Baseline.headlineLargeEmphasized.copy(fontWeight = FontWeight.SemiBold),
    headlineMediumEmphasized = Baseline.headlineMediumEmphasized.copy(fontWeight = FontWeight.SemiBold),
    headlineSmallEmphasized = Baseline.headlineSmallEmphasized.copy(fontWeight = FontWeight.SemiBold),
    titleLargeEmphasized = Baseline.titleLargeEmphasized.copy(fontWeight = FontWeight.SemiBold),
    titleMediumEmphasized = Baseline.titleMediumEmphasized.copy(fontWeight = FontWeight.SemiBold),
)

/** Tabular figures keep amounts aligned in lists and totals. */
fun TextStyle.tabularNumbers(): TextStyle = copy(
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle.Default,
)
