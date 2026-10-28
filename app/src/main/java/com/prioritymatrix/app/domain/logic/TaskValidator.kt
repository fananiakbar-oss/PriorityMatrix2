package com.prioritymatrix.app.domain.logic

import com.prioritymatrix.app.domain.model.Task

enum class ValidationError { TITLE_EMPTY, TITLE_TOO_LONG, START_AFTER_DUE, TIME_WITHOUT_DATE, ESTIMATE_INVALID, REMINDER_WITHOUT_DATE }

object TaskValidator {
    const val MAX_TITLE = 200

    fun validate(task: Task): List<ValidationError> = buildList {
        val title = task.title.trim()
        if (title.isEmpty()) add(ValidationError.TITLE_EMPTY)
        if (title.length > MAX_TITLE) add(ValidationError.TITLE_TOO_LONG)
        if (task.startDate != null && task.dueDate != null && task.startDate.isAfter(task.dueDate)) add(ValidationError.START_AFTER_DUE)
        if (task.dueTime != null && task.dueDate == null) add(ValidationError.TIME_WITHOUT_DATE)
        if (task.estimatedMinutes != null && (task.estimatedMinutes <= 0 || task.estimatedMinutes > 60 * 24 * 30)) add(ValidationError.ESTIMATE_INVALID)
        if (task.reminderOffsets.isNotEmpty() && task.dueDate == null) add(ValidationError.REMINDER_WITHOUT_DATE)
    }
}
