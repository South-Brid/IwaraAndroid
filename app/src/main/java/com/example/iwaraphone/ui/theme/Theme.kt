package com.example.iwaraphone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Pink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E3),
    onSecondaryContainer = Color(0xFF3E0018),
    background = LightBackground,
    surface = Color.White,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF5F6670),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00382F),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealContainer,
    secondary = PinkLight,
    onSecondary = Color(0xFF5E1130),
    secondaryContainer = Color(0xFF7A2945),
    onSecondaryContainer = Color(0xFFFFD9E3),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFAEB4BC),
    surfaceContainer = DarkSurface,
    surfaceContainerLow = DarkSurface,
)

/** 固定使用品牌色（不跟随系统壁纸取色），跟随系统深浅色模式 */
@Composable
fun IwaraPhoneTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
