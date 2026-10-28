package com.prioritymatrix.app.ui.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.repository.PlanningRepository
import com.prioritymatrix.app.data.repository.ProjectRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.domain.logic.SmartPrioritizer
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalNavigator
import com.prioritymatrix.app.ui.common.AppScaffold
import com.prioritymatrix.app.ui.common.TaskActions
import com.prioritymatrix.app.ui.common.byId
import com.prioritymatrix.app.ui.common.projectOf
import com.prioritymatrix.app.ui.components.InlineEmpty
import com.prioritymatrix.app.ui.components.SectionHeader
import com.prioritymatrix.app.ui.components.TaskCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class PlanningUi(
    val date: LocalDate = LocalDate.now(),
    val now: LocalDateTime = LocalDateTime.now(),
    val chosen: List<Task> = emptyList(),
    val suggested: List<Task> = emptyList(),
    val others: List<Task> = emptyList(),
    val projects: Map<Long, Project> = emptyMap()
)

@HiltViewModel
class DailyPlanningViewModel @Inject constructor(
    tasks: TaskRepository,
    projects: ProjectRepository,
    private val planning: PlanningRepository,
    private val actions: TaskActions,
    private val clock: Clock
) : ViewModel() {
    private val today = LocalDate.now(clock)

    val state: StateFlow<PlanningUi> = combine(tasks.tasks, planning.topPriorities(today), projects.projects) { all, ids, projectList ->
        val now = LocalDateTime.now(clock)
        val byId = all.associateBy { it.id }
        val chosen = ids.mapNotNull { byId[it] }
        val active = all.filter { it.isActive && it.id !in ids }
        val suggested = SmartPrioritizer.suggestTop3(active, now)
        PlanningUi(
            date = today, now = now, chosen = chosen, suggested = suggested,
            others = active.filter { t -> suggested.none { it.id == t.id } }
                .sortedByDescending { SmartPrioritizer.score(it, now) },
            projects = projectList.byId()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanningUi())

    private fun ids() = state.value.chosen.map { it.id }

    fun add(task: Task) = viewModelScope.launch {
        val current = ids()
        if (current.size < PlanningRepository.MAX) planning.setTopPriorities(today, current + task.id)
    }

    fun remove(task: Task) = viewModelScope.launch { planning.setTopPriorities(today, ids() - task.id) }

    fun useSuggestion() = viewModelScope.launch {
        val current = ids()
        planning.setTopPriorities(today, (current + state.value.suggested.map { it.id }).distinct().take(PlanningRepository.MAX))
    }

    fun toggle(task: Task) = actions.setCompleted(task, !task.isCompleted)
}

@Composable
fun DailyPlanningScreen(vm: DailyPlanningViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val ui by vm.state.collectAsStateWithLifecycle()
    val full = ui.chosen.size >= 3

    AppScaffold(title = s.dailyPlanning, subtitle = s.longDate(ui.date), onBack = { nav.back() }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionHeader("${s.top3Today} · ${s.top3Progress(ui.chosen.count { it.isCompleted }, ui.chosen.size)}")
                Text(s.chooseTop3, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ui.chosen.isEmpty()) item { InlineEmpty(s.planYourDayBody) }
            items(ui.chosen, key = { "c${it.id}" }) { t ->
                TaskCard(t, t.projectOf(ui.projects), ui.now, onClick = { nav.toTask(t.id) }, onToggleComplete = { vm.toggle(t) },
                    modifier = Modifier.animateItem(),
                    trailing = { IconButton(onClick = { vm.remove(t) }) { Icon(Icons.Rounded.RemoveCircle, s.delete, tint = MaterialTheme.colorScheme.error) } })
            }
            if (ui.suggested.isNotEmpty() && !full) {
                item {
                    Row {
                        SectionHeader(s.suggestedForYou, Modifier.weight(1f))
                    }
                    Text(s.scoreExplain, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { vm.useSuggestion() }, modifier = Modifier.padding(top = 6.dp)) { Text(s.useSuggestion) }
                }
                items(ui.suggested, key = { "s${it.id}" }) { t -> Candidate(t, ui, full, vm) }
            }
            if (ui.others.isNotEmpty() && !full) {
                item { SectionHeader(s.otherActiveTasks) }
                items(ui.others.take(30), key = { "o${it.id}" }) { t -> Candidate(t, ui, full, vm) }
            }
            if (full) item { Text(s.top3Max, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Candidate(t: Task, ui: PlanningUi, full: Boolean, vm: DailyPlanningViewModel) {
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    TaskCard(t, t.projectOf(ui.projects), ui.now, onClick = { nav.toTask(t.id) }, onToggleComplete = { vm.toggle(t) },
        trailing = { IconButton(onClick = { vm.add(t) }, enabled = !full) { Icon(Icons.Rounded.AddCircleOutline, s.addTask, tint = MaterialTheme.colorScheme.primary) } })
}
