package com.prioritymatrix.app.domain.logic

import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.Subtask
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime

/** Maps tasks to/from CSV rows. Project is exported by name so files stay human-readable. */
object TaskCsvMapper {
    val HEADER = listOf(
        "title", "description", "important", "urgent", "quadrant", "priority", "status",
        "start_date", "due_date", "due_time", "estimated_minutes", "tracked_minutes", "project",
        "tags", "assignee", "recurrence", "reminders", "notes", "archived", "subtasks"
    )

    fun toRow(task: Task, projectName: String?): List<String> = listOf(
        task.title,
        task.description,
        task.isImportant.toString(),
        task.isUrgent.toString(),
        "Q${task.quadrant.number}",
        task.priority.name,
        task.status.name,
        task.startDate?.toString().orEmpty(),
        task.dueDate?.toString().orEmpty(),
        task.dueTime?.toString().orEmpty(),
        task.estimatedMinutes?.toString().orEmpty(),
        task.trackedMinutes.toString(),
        projectName.orEmpty(),
        task.tags.joinToString(";"),
        task.assignee,
        task.recurrence.name,
        task.reminderOffsets.joinToString(";"),
        task.notes,
        task.isArchived.toString(),
        task.subtasks.joinToString("|") { (if (it.isDone) "[x] " else "[ ] ") + it.title.replace("|", "/") }
    )

    data class ParsedRow(val task: Task, val projectName: String?)

    sealed interface Result {
        data class Ok(val rows: List<ParsedRow>, val skipped: Int) : Result
        data class Error(val message: String) : Result
    }

    fun parse(rows: List<List<String>>, nowMillis: Long): Result {
        if (rows.isEmpty()) return Result.Error("empty")
        val header = rows.first().map { it.trim().lowercase() }
        val titleIdx = header.indexOf("title")
        if (titleIdx < 0) return Result.Error("missing_title_column")
        fun List<String>.col(name: String): String? {
            val idx = header.indexOf(name)
            return if (idx in indices) this[idx].trim() else null
        }
        var skipped = 0
        val parsed = rows.drop(1).mapNotNull { r ->
            val title = r.getOrNull(titleIdx)?.trim().orEmpty()
            if (title.isEmpty()) { skipped++; return@mapNotNull null }
            runCatching {
                val status = r.col("status")?.let { s -> TaskStatus.entries.firstOrNull { it.name == s } } ?: TaskStatus.NOT_STARTED
                ParsedRow(
                    Task(
                        title = title.take(200),
                        description = r.col("description").orEmpty(),
                        isImportant = r.col("important").toBool(),
                        isUrgent = r.col("urgent").toBool(),
                        priority = r.col("priority")?.let { p -> Priority.entries.firstOrNull { it.name == p } } ?: Priority.MEDIUM,
                        status = status,
                        startDate = r.col("start_date")?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                        dueDate = r.col("due_date")?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                        dueTime = r.col("due_time")?.takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                        estimatedMinutes = r.col("estimated_minutes")?.toIntOrNull()?.takeIf { it > 0 },
                        tags = r.col("tags")?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                        assignee = r.col("assignee").orEmpty(),
                        recurrence = r.col("recurrence")?.let { x -> Recurrence.entries.firstOrNull { it.name == x } } ?: Recurrence.NONE,
                        reminderOffsets = r.col("reminders")?.split(';')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty(),
                        notes = r.col("notes").orEmpty(),
                        isArchived = r.col("archived").toBool(),
                        createdAt = nowMillis,
                        updatedAt = nowMillis,
                        completedAt = if (status == TaskStatus.COMPLETED) nowMillis else null,
                        subtasks = r.col("subtasks").orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() }
                            .mapIndexed { i, s ->
                                val done = s.startsWith("[x]", ignoreCase = true)
                                Subtask(title = s.removePrefix("[x]").removePrefix("[X]").removePrefix("[ ]").trim(), isDone = done, position = i)
                            }
                    ),
                    r.col("project")?.takeIf { it.isNotEmpty() }
                )
            }.getOrElse { skipped++; null }
        }
        return Result.Ok(parsed, skipped)
    }

    private fun String?.toBool(): Boolean = when (this?.trim()?.lowercase()) {
        "true", "1", "yes", "ya", "y" -> true
        else -> false
    }
}
