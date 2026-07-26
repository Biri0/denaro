package it.rfmariano.denaro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DenaroGreenDark,
    secondary = DenaroNeutralDark,
    tertiary = DenaroAmberDark,
    background = DenaroBackgroundDark,
    surface = DenaroSurfaceDark,
    surfaceVariant = Color(0xFF27302B),
    error = Color(0xFFFFB4AB),
)

private val LightColorScheme = lightColorScheme(
    primary = DenaroGreen,
    secondary = DenaroNeutral,
    tertiary = DenaroAmber,
    background = DenaroBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFE0E9E3),
    error = Color(0xFFBA1A1A),
)

@Composable
fun DenaroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
