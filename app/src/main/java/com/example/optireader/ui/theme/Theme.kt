package com.example.optireader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val AmoledBlack = Color.Black

/** True black for OLED; includes surface containers so drawer / settings are not M3 default gray. */
private val AmoledDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = AmoledBlack,
    onBackground = Color(0xFFE3E3E3),
    surface = AmoledBlack,
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = AmoledBlack,
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceDim = AmoledBlack,
    surfaceBright = AmoledBlack,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledBlack,
    surfaceContainer = AmoledBlack,
    surfaceContainerHigh = AmoledBlack,
    surfaceContainerHighest = AmoledBlack,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

@Composable
fun OptireaderTheme(
    darkTheme: Boolean,
    amoledDark: Boolean,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme && amoledDark -> AmoledDarkColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
