package com.prioritymatrix.app.data.db

import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Subtask
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TimeBlock
import com.prioritymatrix.app.domain.model.TimeEntry

private fun String.splitList(): List<String> = split(';').map { it.trim() }.filter { it.isNotEmpty() }

fun TaskWithSubtasks.toDomain(trackedSeconds: Long = 0): Task = Task(
    id = task.id,
    title = task.title,
    description = task.description,
    isImportant = task.isImportant,
    isUrgent = task.isUrgent,
    priority = task.priority,
    startDate = task.startDate,
    dueDate = task.dueDate,
    dueTime = task.dueTime,
    estimatedMinutes = task.estimatedMinutes,
    trackedMinutes = (trackedSeconds / 60).toInt(),
    status = task.status,
    projectId = task.projectId,
    tags = task.tags.splitList(),
    notes = task.notes,
    reminderOffsets = task.reminderOffsets.splitList().mapNotNull { it.toIntOrNull() },
    assignee = task.assignee,
    recurrence = task.recurrence,
    isArchived = task.isArchived,
    isDemo = task.isDemo,
    createdAt = task.createdAt,
    updatedAt = task.updatedAt,
    completedAt = task.completedAt,
    subtasks = subtasks.sortedBy { it.position }.map { it.toDomain() }
)

fun SubtaskEntity.toDomain() = Subtask(id, taskId, title, isDone, position)
fun Subtask.toEntity(taskId: Long, position: Int) = SubtaskEntity(id = 0, taskId = taskId, title = title, isDone = isDone, position = position)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title.trim(),
    description = description.trim(),
    isImportant = isImportant,
    isUrgent = isUrgent,
    quadrant = quadrant.number,
    priority = priority,
    startDate = startDate,
    dueDate = dueDate,
    dueTime = dueTime,
    estimatedMinutes = estimatedMinutes,
    status = status,
    projectId = projectId,
    tags = tags.map { it.replace(";", ",").trim() }.filter { it.isNotEmpty() }.distinct().joinToString(";"),
    notes = notes,
    reminderOffsets = reminderOffsets.distinct().sorted().joinToString(";"),
    assignee = assignee.trim(),
    recurrence = recurrence,
    isArchived = isArchived,
    isDemo = isDemo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)

fun ProjectEntity.toDomain() = Project(id, name, colorArgb, icon, isArchived, isDemo, createdAt)
fun Project.toEntity() = ProjectEntity(id, name.trim(), colorArgb, icon, isArchived, isDemo, createdAt)

fun TimeEntryEntity.toDomain() = TimeEntry(id, taskId, startedAt, durationSeconds, source, quadrant, isDemo)
fun TimeEntry.toEntity() = TimeEntryEntity(id, taskId, startedAt, durationSeconds, source, quadrant, isDemo)

fun TimeBlockEntity.toDomain() = TimeBlock(id, title, date, startMinute, endMinute, taskId, quadrant, note, isDemo)
fun TimeBlock.toEntity() = TimeBlockEntity(id, title.trim(), date, startMinute, endMinute, taskId, quadrant, note, isDemo)

