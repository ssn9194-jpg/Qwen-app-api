package com.aistudio.superapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.aistudio.superapp.domain.model.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6750A4), secondary = Color(0xFF5B5FC7), tertiary = Color(0xFF006B5F),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF), secondary = Color(0xFFC5C7FF), tertiary = Color(0xFF76D7C4),
)
private val AmoledScheme = darkColorScheme(
    background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF080808),
    surfaceContainerHigh = Color(0xFF101010), primary = Color(0xFFD0BCFF), secondary = Color(0xFFC5C7FF),
)

@Composable
fun StudioTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when (mode) {
        ThemeMode.LIGHT -> LightScheme
        ThemeMode.DARK -> DarkScheme
        ThemeMode.AMOLED -> AmoledScheme
        ThemeMode.DYNAMIC -> if (Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) DarkScheme else LightScheme
        ThemeMode.SYSTEM -> if (dark) DarkScheme else LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
