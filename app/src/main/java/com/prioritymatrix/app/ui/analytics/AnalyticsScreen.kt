package com.prioritymatrix.app.ui.analytics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prioritymatrix.app.domain.logic.AnalyticsReport
import com.prioritymatrix.app.domain.logic.Insight
import com.prioritymatrix.app.domain.logic.Severity
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalNavigator
import com.prioritymatrix.app.ui.common.AppScaffold
import com.prioritymatrix.app.ui.components.BarChart
import com.prioritymatrix.app.ui.components.ChartSegment
import com.prioritymatrix.app.ui.components.CompareBars
import com.prioritymatrix.app.ui.components.DonutChart
import com.prioritymatrix.app.ui.components.Legend
import com.prioritymatrix.app.ui.components.LineChart
import com.prioritymatrix.app.ui.components.StatPill
import com.prioritymatrix.app.ui.theme.LocalMatrixColors
import com.prioritymatrix.app.ui.theme.palette
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val ui by vm.state.collectAsStateWithLifecycle()
    var pickRange by remember { mutableStateOf(false) }
    val report = ui.report

    AppScaffold(
        title = s.navAnalytics,
        subtitle = report?.let { "${s.formatDate(it.range.start, "d MMM")} – ${s.formatDate(it.range.endInclusive, "d MMM yyyy")}" },
        actions = { IconButton(onClick = { nav.toReview() }) { Icon(Icons.Rounded.RateReview, s.weeklyReview) } }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(ui.kind == RangeKind.TODAY, onClick = { vm.setKind(RangeKind.TODAY) }, label = { Text(s.rangeToday) })
                FilterChip(ui.kind == RangeKind.WEEK, onClick = { vm.setKind(RangeKind.WEEK) }, label = { Text(s.rangeWeek) })
                FilterChip(ui.kind == RangeKind.MONTH, onClick = { vm.setKind(RangeKind.MONTH) }, label = { Text(s.rangeMonth) })
                FilterChip(ui.kind == RangeKind.CUSTOM, onClick = { pickRange = true }, label = { Text(s.rangeCustom) })
            }
            if (report != null) AnalyticsBody(report, ui.insights)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis!!
                        val end = state.selectedEndDateMillis ?: start
                        vm.setCustom(
                            Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate(),
                            Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                        pickRange = false
                    }
                ) { Text(s.apply) }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text(s.cancel) } }
        ) { DateRangePicker(state = state, modifier = Modifier.height(480.dp)) }
    }
}

@Composable
fun AnalyticsBody(r: AnalyticsReport, insights: List<Insight>) {
    val s = LocalStrings.current
    val colors = LocalMatrixColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatPill(s.completedInRange, r.completedInRange.toString(), Quadrant.DELEGATE.palette().main, Modifier.weight(1f))
        StatPill(s.overdueNow, r.overdueNow.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
        StatPill(s.totalTracked, s.formatHours(r.trackedMinutesTotal), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
    }

    // ---- Task counts ----
    ChartCard(s.taskCountsTitle, s.taskCountsNote) {
        val total = r.activeByQuadrant.values.sum()
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                Quadrant.entries.map { ChartSegment(s.quadrantShort(it), (r.activeByQuadrant[it] ?: 0).toFloat(), colors.of(it).main) },
                centerTitle = total.toString(), centerSubtitle = s.statActive.lowercase(), size = 130.dp
            )
            Spacer(Modifier.width(16.dp))
            Legend(Quadrant.entries.map { colors.of(it).main to "${s.quadrantShort(it)} ${s.quadrantAction(it)}: ${r.activeByQuadrant[it] ?: 0}" })
        }
    }

    // ---- Completion rate ----
    ChartCard(s.completionRate, s.completionRateNote) {
        RateRow("${s.formatDate(r.range.start, "d MMM")} – ${s.formatDate(r.range.endInclusive, "d MMM")}", r.completionRate, r.dueInRangeCompleted, r.dueInRange)
        RateRow(s.todayRate, r.todayCompletionRate, null, null)
        RateRow(s.weekRate, r.weekCompletionRate, null, null)
    }

    // ---- Completed per day/week ----
    ChartCard(if (r.bucketIsWeek) s.completedPerWeek else s.completedPerDay, s.taskCountsNote) {
        BarChart(
            values = r.buckets.map { it.completed.toFloat() },
            labels = r.buckets.map { if (r.bucketIsWeek) s.formatDate(it.start, "d/M") else if (r.buckets.size <= 7) s.dayShort(it.start.dayOfWeek) else it.start.dayOfMonth.toString() },
            color = MaterialTheme.colorScheme.primary
        )
    }

    // ---- Monthly trend ----
    ChartCard(s.monthlyTrend, null) {
        LineChart(
            series = listOf(
                MaterialTheme.colorScheme.primary to r.monthlyTrend.map { it.completed.toFloat() },
                Quadrant.SCHEDULE.palette().main to r.monthlyTrend.map { it.trackedMinutes / 60f }
            ),
            labels = r.monthlyTrend.map { s.formatDate(it.month.atDay(1), "MMM") }
        )
        Spacer(Modifier.height(8.dp))
        Legend(listOf(MaterialTheme.colorScheme.primary to s.trendCompleted, Quadrant.SCHEDULE.palette().main to s.trendHours))
    }

    // ---- Estimate vs actual ----
    ChartCard(s.estimateVsActualTitle, s.estimateBasis(r.estimate.taskCount)) {
        CompareBars(
            listOf(
                Triple(s.estimated, r.estimate.estimatedMinutes.toFloat(), MaterialTheme.colorScheme.outline),
                Triple(s.actual, r.estimate.actualMinutes.toFloat(), MaterialTheme.colorScheme.primary)
            ),
            valueLabel = { s.formatMinutes(it.toInt()) }
        )
    }

    // ---- Tracked time per quadrant ----
    TimeAllocationCard(r)

    // ---- Insights ----
    InsightsCard(insights)
}

@Composable
fun TimeAllocationCard(r: AnalyticsReport) {
    val s = LocalStrings.current
    val colors = LocalMatrixColors.current
    ChartCard(s.timeAllocation, s.timeAllocationNote) {
        if (r.trackedMinutesTotal == 0) {
            Text(s.noTrackedYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ChartCard
        }
        val keys: List<Quadrant?> = Quadrant.entries + listOf(null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                keys.map { q -> ChartSegment(q?.let { s.quadrantShort(it) } ?: s.unlinkedTime, (r.trackedByQuadrant[q] ?: 0).toFloat(), q?.let { colors.of(it).main } ?: colors.unlinked) },
                centerTitle = s.formatHours(r.trackedMinutesTotal), centerSubtitle = s.focusSessions(r.focusSessions), size = 130.dp
            )
            Spacer(Modifier.width(16.dp))
            Legend(keys.filter { (r.trackedByQuadrant[it] ?: 0) > 0 }.map { q ->
                val pct = Math.round(r.trackedShare(q) * 100)
                (q?.let { colors.of(it).main } ?: colors.unlinked) to "${q?.let { s.quadrantShort(it) } ?: s.unlinkedTime}: $pct% · ${s.formatMinutes(r.trackedByQuadrant[q] ?: 0)}"
            })
        }
        Spacer(Modifier.height(16.dp))
        Text(s.strategicVsUrgent, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        CompareBars(
            listOf(
                Triple("Q2 · ${s.quadrantAction(Quadrant.SCHEDULE)}", r.q2Minutes.toFloat(), colors.q2.main),
                Triple("Q1 · ${s.quadrantAction(Quadrant.DO_FIRST)}", r.q1Minutes.toFloat(), colors.q1.main)
            ),
            valueLabel = { "${s.formatMinutes(it.toInt())} (${Math.round(it / r.trackedMinutesTotal * 100)}%)" }
        )
    }
}

@Composable
fun InsightsCard(insights: List<Insight>, emptyText: String? = null) {
    val s = LocalStrings.current
    ChartCard(s.insights, null) {
        if (insights.isEmpty() && emptyText != null) Text(emptyText, style = MaterialTheme.typography.bodyMedium)
        insights.forEachIndexed { i, ins ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            val (icon, tint) = when (ins.severity) {
                Severity.WARNING -> Icons.Rounded.Warning to Quadrant.DO_FIRST.palette().main
                Severity.POSITIVE -> Icons.Rounded.CheckCircle to Quadrant.DELEGATE.palette().main
                Severity.INFO -> Icons.Rounded.Info to MaterialTheme.colorScheme.primary
            }
            Row {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.insightTitle(ins), style = MaterialTheme.typography.titleSmall)
                    Text(s.insightBody(ins), style = MaterialTheme.typography.bodyMedium)
                    Text(s.insightRule(ins), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RateRow(label: String, rate: Float?, done: Int?, due: Int?) {
    val s = LocalStrings.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                rate?.let { "${Math.round(it * 100)}%" + if (done != null && due != null) " ($done/$due)" else "" } ?: s.noDueData,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { rate ?: 0f }, modifier = Modifier.fillMaxWidth().height(6.dp), drawStopIndicator = {})
    }
}

@Composable
fun ChartCard(title: String, note: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

