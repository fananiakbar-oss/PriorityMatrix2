package com.prioritymatrix.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Eisenhower quadrant. Derived ONLY from two independent flags; urgency never implies importance.
 */
enum class Quadrant(val number: Int, val important: Boolean, val urgent: Boolean) {
    DO_FIRST(1, important = true, urgent = true),
    SCHEDULE(2, important = true, urgent = false),
    DELEGATE(3, important = false, urgent = true),
    ELIMINATE(4, important = false, urgent = false);

    companion object {
        fun of(important: Boolean, urgent: Boolean): Quadrant = when {
            important && urgent -> DO_FIRST
            important -> SCHEDULE
            urgent -> DELEGATE
            else -> ELIMINATE
        }

        fun fromNumber(number: Int): Quadrant = entries.firstOrNull { it.number == number } ?: DO_FIRST
    }
}

enum class Priority(val level: Int) { LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4) }

enum class TaskStatus { NOT_STARTED, IN_PROGRESS, ON_HOLD, COMPLETED }

enum class Recurrence { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY }

enum class TimeEntrySource { FOCUS, MANUAL }

data class Subtask(
    val id: Long = 0,
    val taskId: Long = 0,
    val title: String,
    val isDone: Boolean = false,
    val position: Int = 0
)

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val isImportant: Boolean,
    val isUrgent: Boolean,
    val priority: Priority = Priority.MEDIUM,
    val startDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val estimatedMinutes: Int? = null,
    /** Sum of recorded time entries (focus sessions + manual logs). Read-only, derived. */
    val trackedMinutes: Int = 0,
    val status: TaskStatus = TaskStatus.NOT_STARTED,
    val projectId: Long? = null,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    /** Minutes before the deadline at which to remind. 0 = at the deadline. */
    val reminderOffsets: List<Int> = emptyList(),
    val assignee: String = "",
    val recurrence: Recurrence = Recurrence.NONE,
    val isArchived: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
    val subtasks: List<Subtask> = emptyList()
) {
    val quadrant: Quadrant get() = Quadrant.of(isImportant, isUrgent)
    val isCompleted: Boolean get() = status == TaskStatus.COMPLETED
    val isActive: Boolean get() = !isCompleted && !isArchived
    val subtaskProgress: Float
        get() = if (subtasks.isEmpty()) 0f else subtasks.count { it.isDone }.toFloat() / subtasks.size

    fun withQuadrant(q: Quadrant) = copy(isImportant = q.important, isUrgent = q.urgent)
}

data class Project(
    val id: Long = 0,
    val name: String,
    val colorArgb: Long,
    val icon: String,
    val isArchived: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Long = 0
)

data class TimeEntry(
    val id: Long = 0,
    val taskId: Long?,
    /** Epoch millis when the work started. */
    val startedAt: Long,
    val durationSeconds: Int,
    val source: TimeEntrySource,
    /** Quadrant of the task at the moment the time was recorded (null = not linked to a task). */
    val quadrant: Quadrant?,
    val isDemo: Boolean = false
) {
    val minutes: Int get() = durationSeconds / 60
}

data class TimeBlock(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val taskId: Long? = null,
    val quadrant: Quadrant = Quadrant.SCHEDULE,
    val note: String = "",
    val isDemo: Boolean = false
) {
    val durationMinutes: Int get() = endMinute - startMinute
}

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "Range end must not be before start" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)
    val days: Long get() = java.time.temporal.ChronoUnit.DAYS.between(start, endInclusive) + 1
}
