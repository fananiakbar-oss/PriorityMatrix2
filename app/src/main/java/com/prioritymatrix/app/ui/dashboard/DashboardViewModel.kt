package com.prioritymatrix.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.demo.DemoDataSeeder
import com.prioritymatrix.app.data.prefs.SettingsRepository
import com.prioritymatrix.app.data.repository.PlanningRepository
import com.prioritymatrix.app.data.repository.ProjectRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.domain.logic.SmartPrioritizer
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.i18n.StringsProvider
import com.prioritymatrix.app.ui.common.SnackbarBus
import com.prioritymatrix.app.ui.common.TaskActions
import com.prioritymatrix.app.ui.common.byId
import com.prioritymatrix.app.ui.common.minuteTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class QuadrantSummary(
    val quadrant: Quadrant,
    val active: Int,
    val overdue: Int,
    val completed: Int,
    val total: Int,
    val preview: List<Task>
) {
    val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

data class DashboardState(
    val loading: Boolean = true,
    val userName: String = "",
    val now: LocalDateTime = LocalDateTime.now(),
    val totalTasks: Int = 0,
    val activeCount: Int = 0,
    val dueTodayCount: Int = 0,
    val overdueCount: Int = 0,
    val completedTodayCount: Int = 0,
    val quadrants: List<QuadrantSummary> = emptyList(),
    val top3: List<Task> = emptyList(),
    val todayPriorities: List<Task> = emptyList(),
    val upcoming: List<Task> = emptyList(),
    val strategic: List<Task> = emptyList(),
    val recentlyCompleted: List<Task> = emptyList(),
    val projects: Map<Long, Project> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    taskRepo: TaskRepository,
    projectRepo: ProjectRepository,
    planning: PlanningRepository,
    settings: SettingsRepository,
    private val demo: DemoDataSeeder,
    private val actions: TaskActions,
    private val strings: StringsProvider,
    private val snackbar: SnackbarBus,
    private val clock: Clock
) : ViewModel() {

    private val ticker = minuteTicker(clock)
    private val top3Ids = ticker.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { planning.topPriorities(it) }

    val state: StateFlow<DashboardState> = combine(
        taskRepo.tasks, projectRepo.projects, top3Ids, settings.settings, ticker
    ) { tasks, projects, topIds, prefs, now ->
        build(tasks, projects, topIds, prefs.userName, now, clock.zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    private fun build(all: List<Task>, projects: List<Project>, topIds: List<Long>, name: String, now: LocalDateTime, zone: ZoneId): DashboardState {
        val today = now.toLocalDate()
        val live = all.filter { !it.isArchived }
        val active = live.filter { it.isActive }
        val byScore = compareByDescending<Task> { SmartPrioritizer.score(it, now) }.thenBy { it.dueDate ?: java.time.LocalDate.MAX }
        fun completedOn(t: Task) = t.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val byId = live.associateBy { it.id }
        return DashboardState(
            loading = false,
            userName = name,
            now = now,
            totalTasks = all.size,
            activeCount = active.size,
            dueTodayCount = active.count { Deadlines.isDueToday(it, today) },
            overdueCount = active.count { Deadlines.isOverdue(it, now) },
            completedTodayCount = live.count { it.isCompleted && completedOn(it) == today },
            quadrants = Quadrant.entries.map { q ->
                val inQ = live.filter { it.quadrant == q }
                val activeQ = inQ.filter { it.isActive }
                QuadrantSummary(
                    quadrant = q,
                    active = activeQ.size,
                    overdue = activeQ.count { Deadlines.isOverdue(it, now) },
                    completed = inQ.count { it.isCompleted },
                    total = inQ.size,
                    preview = activeQ.sortedWith(byScore).take(2)
                )
            },
            top3 = topIds.mapNotNull { byId[it] },
            todayPriorities = active.filter { Deadlines.isOverdue(it, now) || Deadlines.isDueToday(it, today) }.sortedWith(byScore).take(5),
            upcoming = active.filter { val d = it.dueDate; d != null && d.isAfter(today) && !d.isAfter(today.plusDays(7)) }
                .sortedBy { it.dueDate }.take(5),
            strategic = active.filter { it.quadrant == Quadrant.SCHEDULE }.sortedWith(compareBy<Task> { it.dueDate ?: java.time.LocalDate.MAX }).take(4),
            recentlyCompleted = live.filter { it.isCompleted }.sortedByDescending { it.completedAt ?: 0 }.take(4),
            projects = projects.byId()
        )
    }

    fun toggleComplete(task: Task) = actions.setCompleted(task, !task.isCompleted)

    fun loadDemo() = viewModelScope.launch {
        runCatching { demo.seed(strings.current.language) }
            .onSuccess { snackbar.show(strings.current.demoLoaded) }
            .onFailure { snackbar.show(strings.current.errorGeneric) }
    }
}
