package com.prioritymatrix.app.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.repository.ProjectRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.domain.logic.SmartPrioritizer
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TaskStatus
import com.prioritymatrix.app.ui.common.TaskActions
import com.prioritymatrix.app.ui.common.byId
import com.prioritymatrix.app.ui.common.minuteTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

enum class TaskTab { ACTIVE, COMPLETED, ARCHIVED }
enum class DeadlineFilter { OVERDUE, TODAY, WEEK, NONE }
enum class SortOption { SMART, DUE, PRIORITY, QUADRANT, CREATED, TITLE }

data class TaskFilters(
    val query: String = "",
    val tab: TaskTab = TaskTab.ACTIVE,
    val quadrants: Set<Quadrant> = emptySet(),
    val deadline: DeadlineFilter? = null,
    val projectId: Long? = null,
    val statuses: Set<TaskStatus> = emptySet(),
    val priorities: Set<Priority> = emptySet(),
    val assignee: String? = null,
    val sort: SortOption = SortOption.SMART
) {
    val activeFilterCount: Int
        get() = listOf(quadrants.isNotEmpty(), deadline != null, projectId != null, statuses.isNotEmpty(), priorities.isNotEmpty(), assignee != null).count { it }
}

data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val totalInTab: Int = 0,
    val hasAnyTasks: Boolean = false,
    val filters: TaskFilters = TaskFilters(),
    val projects: Map<Long, Project> = emptyMap(),
    val projectList: List<Project> = emptyList(),
    val assignees: List<String> = emptyList(),
    val selection: Set<Long> = emptySet(),
    val now: LocalDateTime = LocalDateTime.now(),
    val fixedQuadrant: Quadrant? = null
)

/** Pure filtering/sorting so it can be unit tested and reused. */
object TaskFiltering {
    fun apply(all: List<Task>, f: TaskFilters, now: LocalDateTime): List<Task> {
        val today = now.toLocalDate()
        val q = f.query.trim().lowercase()
        return all.asSequence()
            .filter {
                when (f.tab) {
                    TaskTab.ACTIVE -> it.isActive
                    TaskTab.COMPLETED -> it.isCompleted && !it.isArchived
                    TaskTab.ARCHIVED -> it.isArchived
                }
            }
            .filter { f.quadrants.isEmpty() || it.quadrant in f.quadrants }
            .filter { f.projectId == null || it.projectId == f.projectId }
            .filter { f.statuses.isEmpty() || it.status in f.statuses }
            .filter { f.priorities.isEmpty() || it.priority in f.priorities }
            .filter { f.assignee == null || it.assignee.equals(f.assignee, ignoreCase = true) }
            .filter {
                when (f.deadline) {
                    null -> true
                    DeadlineFilter.OVERDUE -> Deadlines.isOverdue(it, now)
                    DeadlineFilter.TODAY -> it.dueDate == today
                    DeadlineFilter.WEEK -> Deadlines.isDueWithin(it, today, 7)
                    DeadlineFilter.NONE -> it.dueDate == null
                }
            }
            .filter {
                q.isEmpty() || it.title.lowercase().contains(q) || it.description.lowercase().contains(q) ||
                    it.notes.lowercase().contains(q) || it.tags.any { tag -> tag.lowercase().contains(q) } ||
                    it.assignee.lowercase().contains(q)
            }
            .sortedWith(comparator(f.sort, now))
            .toList()
    }

    fun comparator(sort: SortOption, now: LocalDateTime): Comparator<Task> = when (sort) {
        SortOption.SMART -> compareByDescending<Task> { SmartPrioritizer.score(it, now) }.thenBy { it.dueDate ?: LocalDate.MAX }
        SortOption.DUE -> compareBy<Task> { it.dueDate ?: LocalDate.MAX }.thenBy { it.dueTime }
        SortOption.PRIORITY -> compareByDescending<Task> { it.priority.level }.thenBy { it.dueDate ?: LocalDate.MAX }
        SortOption.QUADRANT -> compareBy<Task> { it.quadrant.number }.thenBy { it.dueDate ?: LocalDate.MAX }
        SortOption.CREATED -> compareByDescending<Task> { it.createdAt }
        SortOption.TITLE -> compareBy<Task> { it.title.lowercase() }
    }
}

@HiltViewModel
class TasksViewModel @Inject constructor(
    savedState: SavedStateHandle,
    taskRepo: TaskRepository,
    projectRepo: ProjectRepository,
    private val actions: TaskActions,
    clock: Clock
) : ViewModel() {
    private val fixedQuadrant: Quadrant? = savedState.get<Int>("q")?.let { Quadrant.fromNumber(it) }
    private val filters = MutableStateFlow(TaskFilters(quadrants = fixedQuadrant?.let { setOf(it) } ?: emptySet()))
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<TasksUiState> = combine(
        taskRepo.tasks, projectRepo.projects, filters, selection, minuteTicker(clock)
    ) { tasks, projects, f, sel, now ->
        val list = TaskFiltering.apply(tasks, f, now)
        TasksUiState(
            tasks = list,
            totalInTab = list.size,
            hasAnyTasks = tasks.isNotEmpty(),
            filters = f,
            projects = projects.byId(),
            projectList = projects,
            assignees = tasks.map { it.assignee.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sorted(),
            selection = sel.filter { id -> list.any { it.id == id } }.toSet(),
            now = now,
            fixedQuadrant = fixedQuadrant
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState(fixedQuadrant = fixedQuadrant))

    fun updateFilters(transform: (TaskFilters) -> TaskFilters) = filters.update(transform)
    fun resetFilters() = filters.update { TaskFilters(query = it.query, tab = it.tab, sort = it.sort, quadrants = fixedQuadrant?.let { q -> setOf(q) } ?: emptySet()) }

    fun toggleSelection(id: Long) = selection.update { if (id in it) it - id else it + id }
    fun selectAll() = selection.update { state.value.tasks.map { it.id }.toSet() }
    fun clearSelection() = selection.update { emptySet() }

    fun toggleComplete(task: Task) = actions.setCompleted(task, !task.isCompleted)
    fun delete(ids: List<Long>) { actions.delete(ids); clearSelection() }
    fun move(ids: List<Long>, q: Quadrant) { actions.move(ids, q); clearSelection() }
    fun archive(ids: List<Long>, archived: Boolean) { actions.archive(ids, archived); clearSelection() }
    fun completeSelected() { actions.completeMany(selection.value.toList()); clearSelection() }
    fun duplicate(id: Long) = actions.duplicate(id)
    fun reschedule(task: Task, date: LocalDate?) = actions.reschedule(task, date)
}
