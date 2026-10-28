package com.prioritymatrix.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.prioritymatrix.app.domain.model.Quadrant

@Immutable
data class QuadrantPalette(val main: Color, val container: Color, val onContainer: Color)

@Immutable
data class MatrixColors(
    val q1: QuadrantPalette,
    val q2: QuadrantPalette,
    val q3: QuadrantPalette,
    val q4: QuadrantPalette,
    val unlinked: Color
) {
    fun of(q: Quadrant): QuadrantPalette = when (q) {
        Quadrant.DO_FIRST -> q1
        Quadrant.SCHEDULE -> q2
        Quadrant.DELEGATE -> q3
        Quadrant.ELIMINATE -> q4
    }
}

val LightMatrixColors = MatrixColors(
    q1 = QuadrantPalette(Color(0xFFF59E0B), Color(0xFFFEF1D6), Color(0xFF6B3A00)),
    q2 = QuadrantPalette(Color(0xFFEF5B5B), Color(0xFFFDE3E3), Color(0xFF7A1C1C)),
    q3 = QuadrantPalette(Color(0xFF22A06B), Color(0xFFDDF4E8), Color(0xFF0E4D31)),
    q4 = QuadrantPalette(Color(0xFF3B82F6), Color(0xFFDFEBFE), Color(0xFF16367A)),
    unlinked = Color(0xFF9AA0AE)
)

val DarkMatrixColors = MatrixColors(
    q1 = QuadrantPalette(Color(0xFFFBBF24), Color(0xFF3A2A0A), Color(0xFFFDE2A8)),
    q2 = QuadrantPalette(Color(0xFFFF8A80), Color(0xFF3D1A1A), Color(0xFFFFCFCB)),
    q3 = QuadrantPalette(Color(0xFF4ADE80), Color(0xFF11301F), Color(0xFFBDF5D3)),
    q4 = QuadrantPalette(Color(0xFF7AB0FF), Color(0xFF13233F), Color(0xFFCFE0FF)),
    unlinked = Color(0xFF6B7180)
)

val LocalMatrixColors = staticCompositionLocalOf { LightMatrixColors }

@Composable
@ReadOnlyComposable
fun Quadrant.palette(): QuadrantPalette = LocalMatrixColors.current.of(this)

// Brand / neutral tokens
val InkIndigo = Color(0xFF3B4A86)
val InkIndigoDark = Color(0xFFB8C3FF)
val LightBackground = Color(0xFFF6F7F9)
val DarkBackground = Color(0xFF101216)
val DarkSurface = Color(0xFF1A1D23)

val ProjectColors = listOf(
    0xFF3B4A86, 0xFF6366F1, 0xFF8B5CF6, 0xFFEC4899, 0xFFEF4444, 0xFFF97316,
    0xFFF59E0B, 0xFF84CC16, 0xFF10B981, 0xFF14B8A6, 0xFF0EA5E9, 0xFF64748B
)
