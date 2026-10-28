package com.prioritymatrix.app.ui.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.prefs.SettingsRepository
import com.prioritymatrix.app.data.repository.ProjectRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.data.repository.TimeRepository
import com.prioritymatrix.app.domain.logic.ProposedChange
import com.prioritymatrix.app.domain.logic.Recommendation
import com.prioritymatrix.app.domain.logic.SmartPrioritizer
import com.prioritymatrix.app.domain.logic.TaskValidator
import com.prioritymatrix.app.domain.logic.ValidationError
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.Subtask
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TaskStatus
import com.prioritymatrix.app.domain.model.TimeEntry
import com.prioritymatrix.app.domain.model.TimeEntrySource
import com.prioritymatrix.app.i18n.StringsProvider
import com.prioritymatrix.app.ui.common.SnackbarBus
import com.prioritymatrix.app.ui.common.TaskActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class SubtaskDraft(val key: Long, val id: Long, val title: String, val done: Boolean)

data class TaskForm(
    val title: String = "",
    val description: String = "",
    val important: Boolean = true,
    val urgent: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val startDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val estimate: String = "",
    val status: TaskStatus = TaskStatus.NOT_STARTED,
    val projectId: Long? = null,
    val tags: String = "",
    val notes: String = "",
    val reminders: Set<Int> = emptySet(),
    val assignee: String = "",
    val recurrence: Recurrence = Recurrence.NONE,
    val subtasks: List<SubtaskDraft> = emptyList()
)

data class EditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val form: TaskForm = TaskForm(),
    val original: TaskForm? = null,
    val base: Task? = null,
    val errors: List<ValidationError> = emptyList(),
    val recommendation: Recommendation? = null
) {
    val dirty: Boolean get() = original != null && form != original
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    projectRepo: ProjectRepository,
    private val time: TimeRepository,
    private val settings: SettingsRepository,
    private val actions: TaskActions,
    private val snackbar: SnackbarBus,
    private val strings: StringsProvider,
    private val clock: Clock
) : ViewModel() {
    private val taskId: Long = savedState.get<Long>("id") ?: 0L
    private var keySeq = 1L
    private var defaultReminder: Int? = 30

    private val _state = MutableStateFlow(EditorState(isNew = taskId == 0L))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closed: SharedFlow<Unit> = _closed

    val projects: StateFlow<List<Project>> = projectRepo.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val timeEntries: StateFlow<List<TimeEntry>> =
        (if (taskId != 0L) time.entriesForTask(taskId) else flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trackedMinutes: StateFlow<Int> =
        (if (taskId != 0L) tasks.observeTask(taskId) else flowOf(null))
            .map { it?.trackedMinutes ?: 0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            val prefs0 = settings.current()
            defaultReminder = if (prefs0.deadlineReminders) prefs0.defaultReminderOffset else null
            if (taskId != 0L) {
                val task = tasks.observeTask(taskId).first()
                if (task == null) { _closed.tryEmit(Unit); return@launch }
                val form = task.toForm()
                _state.value = EditorState(
                    loading = false, isNew = false, form = form, original = form, base = task,
                    recommendation = SmartPrioritizer.recommend(task, LocalDateTime.now(clock), clock.zone)
                )
            } else {
                val date = savedState.get<Long>("date")?.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay)
                val prefs = settings.current()
                val form = TaskForm(
                    title = savedState.get<String>("title").orEmpty(),
                    important = savedState.get<Boolean>("important") ?: true,
                    urgent = savedState.get<Boolean>("urgent") ?: false,
                    dueDate = date,
                    projectId = savedState.get<Long>("project")?.takeIf { it > 0 },
                    reminders = if (date != null && prefs.deadlineReminders) setOf(prefs.defaultReminderOffset) else emptySet()
                )
                _state.value = EditorState(loading = false, isNew = true, form = form, original = TaskForm())
            }
        }
    }

    private fun Task.toForm() = TaskForm(
        title = title, description = description, important = isImportant, urgent = isUrgent, priority = priority,
        startDate = startDate, dueDate = dueDate, dueTime = dueTime, estimate = estimatedMinutes?.toString().orEmpty(),
        status = status, projectId = projectId, tags = tags.joinToString(", "), notes = notes,
        reminders = reminderOffsets.toSet(), assignee = assignee, recurrence = recurrence,
        subtasks = subtasks.map { SubtaskDraft(keySeq++, it.id, it.title, it.isDone) }
    )

    fun update(transform: (TaskForm) -> TaskForm) = _state.update { st ->
        var f = transform(st.form)
        // First deadline on a task: apply the default reminder.
        val def = defaultReminder
        if (st.form.dueDate == null && f.dueDate != null && f.reminders.isEmpty() && def != null) {
            f = f.copy(reminders = setOf(def))
        }
        if (f.dueDate == null) f = f.copy(dueTime = null, reminders = emptySet())
        st.copy(form = f, errors = emptyList())
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        update { it.copy(subtasks = it.subtasks + SubtaskDraft(keySeq++, 0, title.trim(), false)) }
    }

    fun buildTask(): Task {
        val st = _state.value
        val f = st.form
        val base = st.base
        return Task(
            id = base?.id ?: 0L,
            title = f.title.trim(),
            description = f.description.trim(),
            isImportant = f.important,
            isUrgent = f.urgent,
            priority = f.priority,
            startDate = f.startDate,
            dueDate = f.dueDate,
            dueTime = f.dueTime,
            estimatedMinutes = f.estimate.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: -1 },
            status = f.status,
            projectId = f.projectId,
            tags = f.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            notes = f.notes,
            reminderOffsets = if (f.dueDate != null) f.reminders.toList() else emptyList(),
            assignee = f.assignee.trim(),
            recurrence = f.recurrence,
            isArchived = base?.isArchived ?: false,
            isDemo = base?.isDemo ?: false,
            createdAt = base?.createdAt ?: 0L,
            completedAt = base?.completedAt,
            subtasks = f.subtasks.filter { it.title.isNotBlank() }.mapIndexed { i, d -> Subtask(id = 0, title = d.title, isDone = d.done, position = i) }
        )
    }

    fun save() {
        val task = buildTask()
        val errors = TaskValidator.validate(task)
        if (errors.isNotEmpty()) { _state.update { it.copy(errors = errors) }; return }
        viewModelScope.launch {
            runCatching { tasks.save(task) }
                .onSuccess {
                    snackbar.show(strings.current.taskSaved)
                    _state.update { it.copy(original = it.form) }
                    _closed.tryEmit(Unit)
                }
                .onFailure { snackbar.show(strings.current.errorGeneric) }
        }
    }

    fun delete() {
        val id = _state.value.base?.id ?: return
        actions.delete(listOf(id))
        _state.update { it.copy(original = it.form) }
        _closed.tryEmit(Unit)
    }

    fun duplicate() { _state.value.base?.id?.let { actions.duplicate(it) } }

    fun toggleArchive() {
        val base = _state.value.base ?: return
        actions.archive(listOf(base.id), !base.isArchived)
        _state.update { it.copy(original = it.form) }
        _closed.tryEmit(Unit)
    }

    /** Applies a Smart Prioritization proposal — only called after the user confirmed it. */
    fun applyProposal(change: ProposedChange) {
        when (change) {
            ProposedChange.MARK_URGENT -> { update { it.copy(urgent = true) }; save() }
            ProposedChange.MARK_NOT_URGENT -> { update { it.copy(urgent = false) }; save() }
            ProposedChange.ARCHIVE -> toggleArchive()
        }
    }

    fun logTime(minutes: Int) {
        val id = _state.value.base?.id ?: return
        if (minutes <= 0) return
        viewModelScope.launch {
            time.logTime(id, clock.millis() - minutes * 60_000L, minutes * 60, TimeEntrySource.MANUAL)
        }
    }

    fun deleteEntry(entry: TimeEntry) = viewModelScope.launch { time.deleteEntry(entry) }
}
