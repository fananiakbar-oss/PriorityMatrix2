package com.prioritymatrix.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prioritymatrix.app.data.prefs.ThemeMode

private val LightScheme = lightColorScheme(
    primary = InkIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF111A45),
    secondary = Color(0xFF5B5F72),
    secondaryContainer = Color(0xFFE2E3F3),
    onSecondaryContainer = Color(0xFF181B2A),
    tertiary = Color(0xFF22A06B),
    background = LightBackground,
    onBackground = Color(0xFF1A1C22),
    surface = Color.White,
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF5C6070),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFF1F2F6),
    surfaceContainerHigh = Color(0xFFEBEDF2),
    surfaceContainerHighest = Color(0xFFE4E6EC),
    outline = Color(0xFFC6C9D3),
    outlineVariant = Color(0xFFE3E5EB),
    error = Color(0xFFD92D20),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A)
)

private val DarkScheme = darkColorScheme(
    primary = InkIndigoDark,
    onPrimary = Color(0xFF1B2556),
    primaryContainer = Color(0xFF2F3A6E),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC3C5DA),
    secondaryContainer = Color(0xFF2E3142),
    onSecondaryContainer = Color(0xFFE2E3F3),
    tertiary = Color(0xFF4ADE80),
    background = DarkBackground,
    onBackground = Color(0xFFE4E5EA),
    surface = DarkSurface,
    onSurface = Color(0xFFE4E5EA),
    surfaceVariant = Color(0xFF262A32),
    onSurfaceVariant = Color(0xFFA9ADBA),
    surfaceContainerLowest = Color(0xFF0C0E11),
    surfaceContainerLow = Color(0xFF16191E),
    surfaceContainer = Color(0xFF1E2128),
    surfaceContainerHigh = Color(0xFF252931),
    surfaceContainerHighest = Color(0xFF2E323B),
    outline = Color(0xFF474C57),
    outlineVariant = Color(0xFF30343D),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF4A1C17),
    onErrorContainer = Color(0xFFFFDAD5)
)

private val base = Typography()
val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
)

val BigNumber = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, lineHeight = 32.sp)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PriorityMatrixTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMatrixColors provides if (dark) DarkMatrixColors else LightMatrixColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
