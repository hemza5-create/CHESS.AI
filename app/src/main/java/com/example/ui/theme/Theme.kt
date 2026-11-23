package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TacticalDarkColorScheme = darkColorScheme(
    primary = TacticalEmerald,
    onPrimary = ObsidianBackground,
    primaryContainer = TacticalEmeraldSoft,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = GrandmasterGold,
    onSecondary = ObsidianBackground,
    secondaryContainer = GrandmasterGoldSoft,
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = Color(0xFF38BDF8),
    onTertiary = ObsidianBackground,
    error = ThreatCoral,
    onError = Color.White,
    errorContainer = ThreatCoralSoft,
    onErrorContainer = Color(0xFFFECACA),
    background = ObsidianBackground,
    onBackground = IvoryText,
    surface = SlateSurface,
    onSurface = IvoryText,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    outline = SlateOutline,
    outlineVariant = Color(0xFF222D3D)
)

private val TacticalLightColorScheme = lightColorScheme(
    primary = TacticalEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = AlabasterBackground,
    onBackground = DeepObsidianText,
    surface = AlabasterSurface,
    onSurface = DeepObsidianText,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = MutedInkText,
    outline = AlabasterOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

val TacticalShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ChessVisionTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TacticalDarkColorScheme else TacticalLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = TacticalShapes,
        content = content
    )
}
