package com.prioritymatrix.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.repository.ProjectRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.data.repository.TimeRepository
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TimeBlock
import com.prioritymatrix.app.i18n.StringsProvider
import com.prioritymatrix.app.ui.common.SnackbarBus
import com.prioritymatrix.app.ui.common.TaskActions
import com.prioritymatrix.app.ui.common.byId
import com.prioritymatrix.app.ui.common.minuteTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

enum class CalendarMode { MONTH, WEEK, DAY }

data class CalendarState(
    val mode: CalendarMode = CalendarMode.MONTH,
    val selected: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val now: LocalDateTime = LocalDateTime.now(),
    /** Tasks (not archived) by due date. */
    val tasksByDate: Map<LocalDate, List<Task>> = emptyMap(),
    /** Tasks whose start date is this day (shown as "starts"). */
    val startsByDate: Map<LocalDate, List<Task>> = emptyMap(),
    val blocksByDate: Map<LocalDate, List<TimeBlock>> = emptyMap(),
    val projects: Map<Long, Project> = emptyMap(),
    val activeTasks: List<Task> = emptyList()
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    tasks: TaskRepository,
    projects: ProjectRepository,
    private val time: TimeRepository,
    private val actions: TaskActions,
    private val snackbar: SnackbarBus,
    private val strings: StringsProvider,
    private val clock: Clock
) : ViewModel() {
    private val mode = MutableStateFlow(CalendarMode.MONTH)
    private val selected = MutableStateFlow(LocalDate.now(clock))

    val state: StateFlow<CalendarState> = combine(
        tasks.tasks, time.blocks, projects.projects, combine(mode, selected) { m, d -> m to d }, minuteTicker(clock)
    ) { all, blocks, projectList, (m, d), now ->
        val live = all.filter { !it.isArchived }
        CalendarState(
            mode = m, selected = d, today = now.toLocalDate(), now = now,
            tasksByDate = live.filter { it.dueDate != null }.groupBy { it.dueDate!! }
                .mapValues { (_, v) -> v.sortedWith(compareBy<Task> { it.isCompleted }.thenBy { it.dueTime }.thenBy { it.quadrant.number }) },
            startsByDate = live.filter { it.startDate != null && it.startDate != it.dueDate && it.isActive }.groupBy { it.startDate!! },
            blocksByDate = blocks.groupBy { it.date },
            projects = projectList.byId(),
            activeTasks = live.filter { it.isActive }.sortedBy { it.quadrant.number }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun setMode(m: CalendarMode) { mode.value = m }
    fun select(d: LocalDate) { selected.value = d }
    fun shift(step: Long) {
        selected.value = when (mode.value) {
            CalendarMode.MONTH -> selected.value.plusMonths(step).withDayOfMonth(1)
            CalendarMode.WEEK -> selected.value.plusWeeks(step)
            CalendarMode.DAY -> selected.value.plusDays(step)
        }
    }
    fun goToday() { selected.value = LocalDate.now(clock) }

    fun toggleComplete(t: Task) = actions.setCompleted(t, !t.isCompleted)
    fun moveDeadline(t: Task, d: LocalDate) = actions.reschedule(t, d)

    fun saveBlock(b: TimeBlock) = viewModelScope.launch {
        runCatching { time.saveBlock(b) }.onFailure { snackbar.show(strings.current.errorGeneric) }
    }
    fun deleteBlock(id: Long) = viewModelScope.launch { time.deleteBlock(id) }
}
