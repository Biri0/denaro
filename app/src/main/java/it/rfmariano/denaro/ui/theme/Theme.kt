package it.rfmariano.denaro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalDenaroColors = staticCompositionLocalOf { LightExtendedColors }

/** Denaro semantic colors (positive / warning) for the current theme. */
val MaterialTheme.denaroColors: DenaroExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalDenaroColors.current

fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun DenaroTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = darkTheme ?: isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && supportsDynamicColor() ->
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    CompositionLocalProvider(
        LocalDenaroColors provides if (useDarkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = DenaroShapes,
            typography = Typography,
            content = content,
        )
    }
}
