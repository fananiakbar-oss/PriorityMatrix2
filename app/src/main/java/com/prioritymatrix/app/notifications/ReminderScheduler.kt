package com.prioritymatrix.app.notifications

import com.prioritymatrix.app.domain.model.Task

/** Schedules per-task deadline reminders. Implemented with AlarmManager; faked in tests. */
interface ReminderScheduler {
    suspend fun schedule(task: Task)
    fun cancel(taskId: Long)
}
