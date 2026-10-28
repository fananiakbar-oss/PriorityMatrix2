package com.prioritymatrix.app.ui.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.repository.ProjectRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalNavigator
import com.prioritymatrix.app.navigation.QuickAddRequest
import com.prioritymatrix.app.ui.common.AppScaffold
import com.prioritymatrix.app.ui.common.TaskActions
import com.prioritymatrix.app.ui.components.ConfirmDialog
import com.prioritymatrix.app.ui.components.DemoBadge
import com.prioritymatrix.app.ui.components.EmptyState
import com.prioritymatrix.app.ui.components.ProjectIcons
import com.prioritymatrix.app.ui.components.SectionHeader
import com.prioritymatrix.app.ui.components.StatPill
import com.prioritymatrix.app.ui.components.TaskCard
import com.prioritymatrix.app.ui.components.projectIcon
import com.prioritymatrix.app.ui.theme.ProjectColors
import com.prioritymatrix.app.ui.theme.palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

data class ProjectStats(val project: Project, val active: Int, val completed: Int, val overdue: Int, val byQuadrant: Map<Quadrant, Int>) {
    val total: Int get() = active + completed
    val completion: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

private fun stats(p: Project, tasks: List<Task>, now: LocalDateTime): ProjectStats {
    val mine = tasks.filter { it.projectId == p.id && !it.isArchived }
    val active = mine.filter { it.isActive }
    return ProjectStats(p, active.size, mine.count { it.isCompleted }, active.count { Deadlines.isOverdue(it, now) },
        Quadrant.entries.associateWith { q -> active.count { it.quadrant == q } })
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projects: ProjectRepository,
    tasks: TaskRepository,
    private val clock: Clock
) : ViewModel() {
    val items: StateFlow<List<ProjectStats>> = combine(projects.projects, tasks.tasks) { ps, ts ->
        val now = LocalDateTime.now(clock)
        ps.map { stats(it, ts, now) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(p: Project) = viewModelScope.launch { runCatching { projects.save(p) } }
    fun delete(id: Long) = viewModelScope.launch { projects.delete(id) }
}

@Composable
fun ProjectsScreen(vm: ProjectsViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val items by vm.items.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Project?>(null) }

    AppScaffold(
        title = s.projects, onBack = { nav.back() },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = Project(name = "", colorArgb = ProjectColors.first(), icon = "folder") },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(s.newProject) })
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(Icons.Rounded.FolderOpen, s.projects, s.noProjects, Modifier.padding(padding))
        } else LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.project.id }) { ps -> ProjectCard(ps, onClick = { nav.toProject(ps.project.id) }, onEdit = { editing = ps.project }) }
        }
    }
    editing?.let { p -> ProjectDialog(p, onDismiss = { editing = null }, onSave = { vm.save(it); editing = null }) }
}

@Composable
private fun ProjectCard(ps: ProjectStats, onClick: () -> Unit, onEdit: () -> Unit) {
    val s = LocalStrings.current
    val color = Color(ps.project.colorArgb)
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(projectIcon(ps.project.icon), null, tint = color)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ps.project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (ps.project.isDemo) { Spacer(Modifier.width(6.dp)); DemoBadge() }
                    }
                    Text(s.projectStats(ps.active, ps.completed) + if (ps.overdue > 0) " · ${s.overdueCount(ps.overdue)}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, s.edit) }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { ps.completion }, color = color, trackColor = color.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth().height(6.dp), drawStopIndicator = {})
            Spacer(Modifier.height(8.dp))
            DistributionBar(ps.byQuadrant)
        }
    }
}

@Composable
private fun DistributionBar(byQuadrant: Map<Quadrant, Int>) {
    val total = byQuadrant.values.sum()
    if (total == 0) return
    Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
        Quadrant.entries.forEach { q ->
            val n = byQuadrant[q] ?: 0
            if (n > 0) Box(Modifier.weight(n.toFloat()).height(8.dp).background(q.palette().main))
        }
    }
}

@Composable
fun ProjectDialog(project: Project, onDismiss: () -> Unit, onSave: (Project) -> Unit) {
    val s = LocalStrings.current
    var p by remember { mutableStateOf(project) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project.id == 0L) s.newProject else s.editProject) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = p.name, onValueChange = { p = p.copy(name = it.take(60)) }, label = { Text(s.projectName) }, singleLine = true)
                Text(s.color, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectColors.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c)).clickable { p = p.copy(colorArgb = c) },
                            contentAlignment = Alignment.Center
                        ) { if (p.colorArgb == c) Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White)) }
                    }
                }
                Text(s.icon, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProjectIcons.forEach { (key, icon) ->
                        val sel = p.icon == key
                        Surface(
                            shape = CircleShape,
                            color = if (sel) Color(p.colorArgb).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer,
                            border = if (sel) BorderStroke(2.dp, Color(p.colorArgb)) else null,
                            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { p = p.copy(icon = key) }
                        ) { Box(contentAlignment = Alignment.Center) { Icon(icon, key, tint = if (sel) Color(p.colorArgb) else MaterialTheme.colorScheme.onSurfaceVariant) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = p.name.isNotBlank(), onClick = { onSave(p) }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

// ---------------- Project detail ----------------

data class ProjectDetailUi(
    val stats: ProjectStats? = null,
    val tasks: List<Task> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now(),
    val missing: Boolean = false
)

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val projects: ProjectRepository,
    tasks: TaskRepository,
    private val actions: TaskActions,
    private val clock: Clock
) : ViewModel() {
    val projectId: Long = savedState.get<Long>("id") ?: 0L

    val state: StateFlow<ProjectDetailUi> = combine(projects.projects, tasks.tasks) { ps, ts ->
        val now = LocalDateTime.now(clock)
        val p = ps.firstOrNull { it.id == projectId } ?: return@combine ProjectDetailUi(missing = true)
        ProjectDetailUi(
            stats = stats(p, ts, now),
            tasks = ts.filter { it.projectId == projectId && !it.isArchived }
                .sortedWith(compareBy<Task> { it.isCompleted }.thenBy { it.quadrant.number }.thenBy { it.dueDate ?: java.time.LocalDate.MAX }),
            now = now
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailUi())

    fun toggle(t: Task) = actions.setCompleted(t, !t.isCompleted)
    fun save(p: Project) = viewModelScope.launch { runCatching { projects.save(p) } }
    fun delete() = viewModelScope.launch { projects.delete(projectId) }
}

@Composable
fun ProjectDetailScreen(vm: ProjectDetailViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val ui by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val st = ui.stats

    AppScaffold(
        title = st?.project?.name ?: s.projects, onBack = { nav.back() },
        actions = {
            if (st != null) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, s.edit) }
                IconButton(onClick = { askDelete = true }) { Icon(Icons.Rounded.Delete, s.delete) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.openQuickAdd(QuickAddRequest(projectId = vm.projectId)) },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(s.addTask) })
        }
    ) { padding ->
        if (st == null) return@AppScaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill(s.statActive, st.active.toString(), Color(st.project.colorArgb), Modifier.weight(1f))
                    StatPill(s.tabCompleted, st.completed.toString(), Quadrant.DELEGATE.palette().main, Modifier.weight(1f))
                    StatPill(s.statOverdue, st.overdue.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, shadowElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(s.distribution, style = MaterialTheme.typography.titleMedium)
                        Text("${s.completionRate}: ${Math.round(st.completion * 100)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        listOf(Quadrant.DO_FIRST to Quadrant.SCHEDULE, Quadrant.DELEGATE to Quadrant.ELIMINATE).forEach { (a, b) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                listOf(a, b).forEach { q ->
                                    val p = q.palette()
                                    Surface(color = p.container, contentColor = p.onContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("${s.quadrantShort(q)} · ${s.quadrantAction(q)}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text((st.byQuadrant[q] ?: 0).toString(), style = MaterialTheme.typography.headlineSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { SectionHeader(s.projectTasks) }
            items(ui.tasks, key = { it.id }) { t ->
                TaskCard(t, null, ui.now, onClick = { nav.toTask(t.id) }, onToggleComplete = { vm.toggle(t) }, modifier = Modifier.animateItem())
            }
        }
    }
    if (editing && st != null) ProjectDialog(st.project, onDismiss = { editing = false }, onSave = { vm.save(it); editing = false })
    if (askDelete) ConfirmDialog(s.deleteProject, s.deleteProjectBody, s.delete, onConfirm = { vm.delete(); nav.back() }, onDismiss = { askDelete = false }, destructive = true)
}
