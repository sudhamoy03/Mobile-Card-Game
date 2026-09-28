package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.AccentColor
import com.example.data.AppThemeMode

@Composable
fun MobileCardGameTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    accentColor: AccentColor = AccentColor.GOLD,
    content: @Composable () -> Unit
) {
    val primaryColor = Color(accentColor.hex)

    val colorScheme = when (themeMode) {
        AppThemeMode.LIGHT -> lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.Black,
            primaryContainer = primaryColor.copy(alpha = 0.2f),
            onPrimaryContainer = Color.Black,
            secondary = CasinoGreenBorder,
            onSecondary = Color.White,
            background = LightBackground,
            onBackground = Color(0xFF191C1A),
            surface = LightSurface,
            onSurface = Color(0xFF191C1A),
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = Color(0xFF404943),
            outline = Color(0xFF707973)
        )
        AppThemeMode.AMOLED -> darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.Black,
            primaryContainer = primaryColor.copy(alpha = 0.25f),
            onPrimaryContainer = primaryColor,
            secondary = Color(0xFF225738),
            onSecondary = Color.White,
            background = AmoledBackground,
            onBackground = Color(0xFFE1E3DF),
            surface = AmoledSurface,
            onSurface = Color(0xFFE1E3DF),
            surfaceVariant = Color(0xFF1A1A1A),
            onSurfaceVariant = Color(0xFFC0C9C2),
            outline = Color(0xFF333333)
        )
        AppThemeMode.DARK -> darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.Black,
            primaryContainer = primaryColor.copy(alpha = 0.25f),
            onPrimaryContainer = primaryColor,
            secondary = CasinoGreenBorder,
            onSecondary = Color.White,
            background = CasinoGreenDeep,
            onBackground = Color(0xFFE1E3DF),
            surface = CasinoGreenDark,
            onSurface = Color(0xFFE1E3DF),
            surfaceVariant = CasinoGreenSurface,
            onSurfaceVariant = Color(0xFFC0C9C2),
            outline = CasinoGreenBorder
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
