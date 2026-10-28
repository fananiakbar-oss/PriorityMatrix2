package com.prioritymatrix.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ChartSegment(val label: String, val value: Float, val color: Color)

@Composable
private fun rememberEntrance(key: Any?): Animatable<Float, AnimationVector1D> {
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    return anim
}

/** Donut chart. Empty data renders a neutral ring. */
@Composable
fun DonutChart(
    segments: List<ChartSegment>,
    centerTitle: String,
    centerSubtitle: String,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    thickness: Dp = 18.dp
) {
    val total = segments.sumOf { it.value.toDouble() }.toFloat()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress = rememberEntrance(segments.map { it.value })
    val description = segments.joinToString { "${it.label}: ${it.value}" }
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total > 0f) {
                var start = -90f
                val gap = if (segments.count { it.value > 0 } > 1) 2.5f else 0f
                segments.filter { it.value > 0 }.forEach { seg ->
                    val sweep = 360f * seg.value / total * progress.value
                    drawArc(seg.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTitle, style = MaterialTheme.typography.headlineSmall)
            Text(centerSubtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun Legend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (c, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Vertical bar chart with optional per-bar colors; labels are thinned automatically. */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    highlightIndex: Int? = null
) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val progress = rememberEntrance(values)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val dim = color.copy(alpha = 0.55f)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(16.dp)) {
            Text(max.toInt().toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = values.joinToString() }) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 3))
                val h = size.height * (v / max) * progress.value
                if (h > 0f) {
                    drawRoundRect(
                        if (highlightIndex == null || highlightIndex == i) color else dim,
                        Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 3)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val step = when {
            labels.size <= 8 -> 1
            labels.size <= 16 -> 2
            else -> (labels.size + 6) / 7
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % step == 0) l else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Line chart for one or two series, each normalised to its own maximum. */
@Composable
fun LineChart(
    series: List<Pair<Color, List<Float>>>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp
) {
    val progress = rememberEntrance(series.map { it.second })
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp, vertical = 8.dp)) {
            for (g in 0..3) {
                val y = size.height * g / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            series.forEach { (c, values) ->
                if (values.isEmpty()) return@forEach
                val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
                val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
                val points = values.mapIndexed { i, v -> Offset(stepX * i, size.height - size.height * (v / max) * progress.value) }
                val path = Path().apply {
                    points.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
                }
                drawPath(path, c, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                points.forEach { pt ->
                    drawCircle(c, 5.dp.toPx(), pt)
                    drawCircle(surface, 2.5.dp.toPx(), pt)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
    }
}

/** Two horizontal bars compared against a shared maximum (e.g. Q2 vs Q1 minutes). */
@Composable
fun CompareBars(items: List<Triple<String, Float, Color>>, valueLabel: (Float) -> String, modifier: Modifier = Modifier) {
    val max = (items.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(1f)
    val progress = rememberEntrance(items.map { it.second })
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, v, c) ->
            Column {
                Row {
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(valueLabel(v), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(track)) {
                    Box(Modifier.width(maxWidth * (v / max) * progress.value).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c))
                }
            }
        }
    }
}
