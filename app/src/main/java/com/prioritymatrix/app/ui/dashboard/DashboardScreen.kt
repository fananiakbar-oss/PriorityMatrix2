package com.prioritymatrix.app.ui.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EmojiObjects
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalNavigator
import com.prioritymatrix.app.navigation.LocalSnackbarHost
import com.prioritymatrix.app.navigation.QuickAddRequest
import com.prioritymatrix.app.ui.components.EmptyState
import com.prioritymatrix.app.ui.components.InlineEmpty
import com.prioritymatrix.app.ui.components.SectionHeader
import com.prioritymatrix.app.ui.components.StatPill
import com.prioritymatrix.app.ui.components.TaskCard
import com.prioritymatrix.app.ui.common.projectOf
import com.prioritymatrix.app.ui.theme.BigNumber
import com.prioritymatrix.app.ui.theme.palette

@Composable
fun DashboardScreen(vm: DashboardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val errorColor = MaterialTheme.colorScheme.error

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(LocalSnackbarHost.current) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.openQuickAdd(QuickAddRequest()) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(s.addTask) }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Header(state, onFocus = { nav.toFocus() }, onProjects = { nav.toProjects() }, onBoard = { nav.toBoard() }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill(s.statActive, state.activeCount.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { nav.toTab("tasks") }
                    StatPill(s.statDueToday, state.dueTodayCount.toString(), Quadrant.DO_FIRST.palette().main, Modifier.weight(1f)) { nav.toTab("calendar") }
                    StatPill(s.statOverdue, state.overdueCount.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f)) { nav.toTab("tasks") }
                    StatPill(s.statCompleted, state.completedTodayCount.toString(), Quadrant.DELEGATE.palette().main, Modifier.weight(1f))
                }
            }
            item {
                SectionHeader(s.eisenhowerMatrix, action = s.matrixBoard, onAction = { nav.toBoard() })
                MatrixGrid(state.quadrants) { nav.toQuadrant(it) }
            }
            if (!state.loading && state.totalTasks == 0) {
                item {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                        Column(Modifier.padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(Icons.Rounded.EmojiObjects, s.welcomeTitle, s.welcomeBody, compact = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { nav.openQuickAdd(QuickAddRequest()) }) { Text(s.addTask) }
                                OutlinedButton(onClick = { vm.loadDemo() }) { Text(s.loadDemo) }
                            }
                        }
                    }
                }
            }
            item { Top3Card(state, onPlan = { nav.toPlanning() }, onToggle = vm::toggleComplete, onOpen = { nav.toTask(it.id) }) }

            section(s.todaysPriorities, Icons.Rounded.Warning, errorColor.takeIf { state.overdueCount > 0 },
                state.todayPriorities, s.nothingToday, state, vm::toggleComplete, { nav.toTask(it) }, s.seeAll) { nav.toTab("tasks") }
            section(s.upcomingDeadlines, Icons.Rounded.CalendarToday, null, state.upcoming, s.nothingUpcoming, state, vm::toggleComplete, { nav.toTask(it) }, s.seeAll) { nav.toTab("calendar") }
            section(s.strategicActivities, Icons.Rounded.TrackChanges, null, state.strategic, s.nothingStrategic, state, vm::toggleComplete, { nav.toTask(it) }, s.seeAll) { nav.toQuadrant(Quadrant.SCHEDULE) }
            section(s.recentlyCompleted, Icons.Rounded.DoneAll, null, state.recentlyCompleted, s.nothingCompleted, state, vm::toggleComplete, { nav.toTask(it) }, null, null)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color?,
    tasks: List<Task>,
    empty: String,
    state: DashboardState,
    onToggle: (Task) -> Unit,
    onOpen: (Long) -> Unit,
    action: String?,
    onAction: (() -> Unit)?
) {
    item(key = "h_$title") {
        SectionHeader(title, leading = icon, leadingTint = tint ?: MaterialTheme.colorScheme.primary, action = action, onAction = onAction)
    }
    if (tasks.isEmpty()) item(key = "e_$title") { InlineEmpty(empty) }
    items(tasks, key = { "${title}_${it.id}" }) { t ->
        TaskCard(t, t.projectOf(state.projects), state.now, onClick = { onOpen(t.id) }, onToggleComplete = { onToggle(t) }, modifier = Modifier.animateItem())
    }
}

@Composable
private fun Header(state: DashboardState, onFocus: () -> Unit, onProjects: () -> Unit, onBoard: () -> Unit) {
    val s = LocalStrings.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.greeting(state.now.hour, state.userName), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(s.longDate(state.now.toLocalDate()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = onFocus) { Icon(Icons.Rounded.Timer, s.focusTimer) }
        FilledTonalIconButton(onClick = onProjects) { Icon(Icons.Rounded.Folder, s.projects) }
        FilledTonalIconButton(onClick = onBoard) { Icon(Icons.Rounded.OpenWith, s.matrixBoard) }
    }
}

@Composable
private fun MatrixGrid(quadrants: List<QuadrantSummary>, onOpen: (Quadrant) -> Unit) {
    if (quadrants.size < 4) { Box(Modifier.fillMaxWidth().height(360.dp)); return }
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.urgent, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(s.notUrgent, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuadrantCard(quadrants[0], Modifier.weight(1f), onOpen)
            QuadrantCard(quadrants[1], Modifier.weight(1f), onOpen)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuadrantCard(quadrants[2], Modifier.weight(1f), onOpen)
            QuadrantCard(quadrants[3], Modifier.weight(1f), onOpen)
        }
    }
}

@Composable
private fun QuadrantCard(q: QuadrantSummary, modifier: Modifier, onOpen: (Quadrant) -> Unit) {
    val s = LocalStrings.current
    val p = q.quadrant.palette()
    val progress by animateFloatAsState(q.progress, label = "qProgress")
    Surface(
        color = p.container, contentColor = p.onContainer, shape = MaterialTheme.shapes.large,
        modifier = modifier.heightIn(min = 188.dp).clip(MaterialTheme.shapes.large).clickable(role = Role.Button) { onOpen(q.quadrant) }
    ) {
        Column(Modifier.padding(14.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(p.main), contentAlignment = Alignment.Center) {
                    Text(q.quadrant.number.toString(), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.weight(1f))
                if (q.overdue > 0) {
                    Surface(color = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError, shape = RoundedCornerShape(50)) {
                        Text(s.overdueCount(q.overdue), Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(s.quadrantAction(q.quadrant), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.quadrantTitle(q.quadrant), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = p.onContainer.copy(alpha = 0.8f))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(q.active.toString(), style = BigNumber, color = p.onContainer)
                Spacer(Modifier.width(4.dp))
                Text(s.statActive.lowercase(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = p.main, trackColor = p.main.copy(alpha = 0.2f), drawStopIndicator = {}
            )
            Text(s.doneOf(q.completed, q.total), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.height(6.dp))
            if (q.preview.isEmpty()) {
                Text(s.emptyQuadrant, style = MaterialTheme.typography.bodySmall, color = p.onContainer.copy(alpha = 0.6f))
            } else q.preview.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(p.main))
                    Spacer(Modifier.width(6.dp))
                    Text(t.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Top3Card(state: DashboardState, onPlan: () -> Unit, onToggle: (Task) -> Unit, onOpen: (Task) -> Unit) {
    val s = LocalStrings.current
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(s.top3Today, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.top3.isNotEmpty()) Text(s.top3Progress(state.top3.count { it.isCompleted }, state.top3.size), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            if (state.top3.isEmpty()) {
                Text(s.planYourDayBody, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onPlan) { Icon(Icons.Rounded.Checklist, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.planYourDay) }
            } else {
                state.top3.forEachIndexed { i, t ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpen(t) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.prioritymatrix.app.ui.components.CompletionCheck(t.isCompleted, t.quadrant.palette().main, { onToggle(t) })
                        Text("${i + 1}. ${t.title}", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            textDecoration = if (t.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onPlan) { Text(s.dailyPlanning) }
            }
        }
    }
}
