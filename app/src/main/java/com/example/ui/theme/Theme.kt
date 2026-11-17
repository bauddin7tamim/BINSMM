package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemePreference

private val BinsmmLightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = PureWhite,
    primaryContainer = IceBlue,
    onPrimaryContainer = DeepRoyalBlue,
    secondary = VividPurple,
    onSecondary = PureWhite,
    secondaryContainer = SoftLavender,
    onSecondaryContainer = DeepAmethyst,
    tertiary = ElectricIndigo,
    onTertiary = PureWhite,
    tertiaryContainer = MistSurface,
    onTertiaryContainer = InkPrimary,
    background = SnowCanvas,
    onBackground = InkPrimary,
    surface = PureWhite,
    onSurface = InkPrimary,
    surfaceVariant = MistSurface,
    onSurfaceVariant = InkSecondary,
    outline = SlateBorder,
    outlineVariant = SlateBorder,
    error = RoseError,
    onError = PureWhite,
    errorContainer = RoseContainer,
    onErrorContainer = RoseError
)

private val BinsmmDarkColorScheme = darkColorScheme(
    primary = SkyGlow,
    onPrimary = MidnightCanvas,
    primaryContainer = DeepRoyalBlue,
    onPrimaryContainer = PureWhite,
    secondary = VioletGlow,
    onSecondary = MidnightCanvas,
    secondaryContainer = DeepAmethyst,
    onSecondaryContainer = PureWhite,
    tertiary = ElectricIndigo,
    onTertiary = PureWhite,
    tertiaryContainer = ElevatedNavyCard,
    onTertiaryContainer = DarkTextPrimary,
    background = MidnightCanvas,
    onBackground = DarkTextPrimary,
    surface = DeepNavySurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = ElevatedNavyCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = NavyBorder,
    outlineVariant = NavyBorder,
    error = RoseError,
    onError = PureWhite,
    errorContainer = RoseError.copy(alpha = 0.2f),
    onErrorContainer = RoseContainer
)

val BinsmmShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BINSMMTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (isDark) BinsmmDarkColorScheme else BinsmmLightColorScheme,
        typography = BinsmmTypography,
        shapes = BinsmmShapes,
        content = content
    )
}
