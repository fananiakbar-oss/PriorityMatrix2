package com.prioritymatrix.app.domain.logic

import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Deadline rules. A task without a due time is due at the END of its due date. */
object Deadlines {

    /** Anchor time used for reminders of tasks that have a due date but no due time. */
    val DEFAULT_REMINDER_ANCHOR: LocalTime = LocalTime.of(9, 0)

    fun deadlineOf(task: Task): LocalDateTime? {
        val date = task.dueDate ?: return null
        return date.atTime(task.dueTime ?: LocalTime.MAX)
    }

    fun isOverdue(task: Task, now: LocalDateTime): Boolean {
        if (!task.isActive) return false
        val deadline = deadlineOf(task) ?: return false
        return now.isAfter(deadline)
    }

    fun isDueToday(task: Task, today: LocalDate): Boolean = task.dueDate == today

    /** Due between today and today+days (inclusive), not overdue. */
    fun isDueWithin(task: Task, today: LocalDate, days: Long): Boolean {
        val due = task.dueDate ?: return false
        return !due.isBefore(today) && !due.isAfter(today.plusDays(days))
    }

    /** Whole days from today until the due date (negative = in the past). Null if no deadline. */
    fun daysUntilDue(task: Task, today: LocalDate): Long? =
        task.dueDate?.let { ChronoUnit.DAYS.between(today, it) }

    /**
     * Epoch-millis trigger times for the task's reminders that are still in the future.
     * Without a due time, reminders are anchored at [anchor] on the due date.
     */
    fun reminderTriggers(
        task: Task,
        now: LocalDateTime,
        zone: ZoneId,
        anchor: LocalTime = DEFAULT_REMINDER_ANCHOR
    ): List<Long> {
        if (!task.isActive) return emptyList()
        val date = task.dueDate ?: return emptyList()
        val base = date.atTime(task.dueTime ?: anchor)
        return task.reminderOffsets.distinct().sorted()
            .map { base.minusMinutes(it.toLong()) }
            .filter { it.isAfter(now) }
            .map { it.atZone(zone).toInstant().toEpochMilli() }
    }

    /** Next occurrence date for a recurring task, or null when the task does not repeat. */
    fun nextOccurrence(date: LocalDate, recurrence: Recurrence): LocalDate? = when (recurrence) {
        Recurrence.NONE -> null
        Recurrence.DAILY -> date.plusDays(1)
        Recurrence.WEEKDAYS -> {
            var d = date.plusDays(1)
            while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
            d
        }
        Recurrence.WEEKLY -> date.plusWeeks(1)
        Recurrence.MONTHLY -> date.plusMonths(1)
        Recurrence.YEARLY -> date.plusYears(1)
    }

    /**
     * Builds the next instance of a recurring task after completion, shifting start and due
     * dates by the same interval. Returns null when the task does not repeat or has no dates.
     */
    fun nextRecurringInstance(task: Task, today: LocalDate, nowMillis: Long): Task? {
        if (task.recurrence == Recurrence.NONE) return null
        val reference = task.dueDate ?: task.startDate ?: today
        var next = nextOccurrence(reference, task.recurrence) ?: return null
        // If the task was completed very late, don't create an instance that is already in the past.
        while (next.isBefore(today)) next = nextOccurrence(next, task.recurrence) ?: return null
        val shift = ChronoUnit.DAYS.between(reference, next)
        return task.copy(
            id = 0,
            startDate = task.startDate?.plusDays(shift),
            dueDate = task.dueDate?.plusDays(shift) ?: if (task.startDate == null) next else null,
            status = com.prioritymatrix.app.domain.model.TaskStatus.NOT_STARTED,
            completedAt = null,
            trackedMinutes = 0,
            createdAt = nowMillis,
            updatedAt = nowMillis,
            subtasks = task.subtasks.map { it.copy(id = 0, taskId = 0, isDone = false) }
        )
    }
}
