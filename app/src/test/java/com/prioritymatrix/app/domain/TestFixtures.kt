package com.prioritymatrix.app.domain

import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TaskStatus
import com.prioritymatrix.app.domain.model.TimeEntry
import com.prioritymatrix.app.domain.model.TimeEntrySource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

val ZONE: ZoneId = ZoneOffset.UTC
val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 28, 10, 0) // Wednesday
val TODAY: LocalDate = NOW.toLocalDate()

fun LocalDateTime.millis(): Long = atZone(ZONE).toInstant().toEpochMilli()
fun LocalDate.millisAt(h: Int = 12): Long = atTime(h, 0).millis()

private var seq = 1L

fun task(
    q: Quadrant = Quadrant.DO_FIRST,
    due: LocalDate? = null,
    time: LocalTime? = null,
    done: Boolean = false,
    completedOn: LocalDate? = null,
    archived: Boolean = false,
    estimate: Int? = null,
    tracked: Int = 0,
    priority: Priority = Priority.MEDIUM,
    created: LocalDate = TODAY.minusDays(3),
    assignee: String = "",
    start: LocalDate? = null,
    title: String = "Task ${seq}"
) = Task(
    id = seq++,
    title = title,
    isImportant = q.important,
    isUrgent = q.urgent,
    dueDate = due,
    dueTime = time,
    status = if (done) TaskStatus.COMPLETED else TaskStatus.NOT_STARTED,
    completedAt = if (done) (completedOn ?: TODAY).millisAt() else null,
    isArchived = archived,
    estimatedMinutes = estimate,
    trackedMinutes = tracked,
    priority = priority,
    createdAt = created.millisAt(8),
    assignee = assignee,
    startDate = start
)

fun entry(q: Quadrant?, minutes: Int, on: LocalDate = TODAY, source: TimeEntrySource = TimeEntrySource.FOCUS, taskId: Long? = 1) =
    TimeEntry(taskId = taskId, startedAt = on.millisAt(9), durationSeconds = minutes * 60, source = source, quadrant = q)
