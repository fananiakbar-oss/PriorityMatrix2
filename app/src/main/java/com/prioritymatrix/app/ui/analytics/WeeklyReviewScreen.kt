package com.prioritymatrix.app.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalNavigator
import com.prioritymatrix.app.ui.common.AppScaffold
import com.prioritymatrix.app.ui.components.ColorDot
import com.prioritymatrix.app.ui.components.StatPill
import com.prioritymatrix.app.ui.theme.palette

@Composable
fun WeeklyReviewScreen(vm: WeeklyReviewViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val ui by vm.state.collectAsStateWithLifecycle()
    val r = ui.report

    AppScaffold(title = s.weeklyReview, onBack = { nav.back() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }
                Text(
                    r?.let { "${s.formatDate(it.range.start, "d MMM")} – ${s.formatDate(it.range.endInclusive, "d MMM yyyy")}" } ?: "",
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.shift(1) }, enabled = ui.weekOffset < 0) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
            }
            if (r == null) return@Column
            ChartCard(s.reviewSummary, null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill(s.reviewCompleted, r.completedInRange.toString(), Quadrant.DELEGATE.palette().main, Modifier.weight(1f))
                    StatPill(s.reviewOverdue, ui.overdue.size.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    StatPill(s.completionRate, r.completionRate?.let { "${Math.round(it * 100)}%" } ?: "–", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Quadrant.entries.forEach { q ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        ColorDot(q.palette().main)
                        Text("  ${s.quadrantShort(q)} ${s.quadrantAction(q)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("${r.completedByQuadrant[q] ?: 0} ${s.statCompleted.lowercase()}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            ChartCard(s.reviewCompleted, null) {
                if (ui.completed.isEmpty()) Text(s.reviewNothingCompleted, style = MaterialTheme.typography.bodyMedium)
                ui.completed.take(12).forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        ColorDot(t.quadrant.palette().main)
                        Text("  ${t.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            ChartCard(s.reviewOverdue, null) {
                if (ui.overdue.isEmpty()) Text(s.reviewNoOverdue, style = MaterialTheme.typography.bodyMedium)
                ui.overdue.take(12).forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        ColorDot(t.quadrant.palette().main)
                        Text("  ${t.title}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        t.dueDate?.let { Text(s.dueLabel(it, ui.now.toLocalDate()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            TimeAllocationCard(r)
            InsightsCard(ui.insights, emptyText = s.reviewNoInsights)
            Button(onClick = { nav.toPlanning() }, modifier = Modifier.fillMaxWidth()) { Text(s.dailyPlanning) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
