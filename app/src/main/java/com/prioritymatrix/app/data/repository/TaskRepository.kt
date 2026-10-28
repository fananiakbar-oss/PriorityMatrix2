package com.prioritymatrix.app.data.repository

import androidx.room.withTransaction
import com.prioritymatrix.app.data.db.AppDatabase
import com.prioritymatrix.app.data.db.toDomain
import com.prioritymatrix.app.data.db.toEntity
import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.domain.logic.TaskValidator
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TaskStatus
import com.prioritymatrix.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class CompletionResult(val taskId: Long, val nextRecurringId: Long?)

@Singleton
class TaskRepository @Inject constructor(
    private val db: AppDatabase,
    private val reminders: ReminderScheduler,
    private val clock: Clock
) {
    private val taskDao = db.taskDao()
    private val subtaskDao = db.subtaskDao()
    private val timeDao = db.timeEntryDao()

    /** All tasks (including completed and archived) with tracked time attached. */
    val tasks: Flow<List<Task>> = combine(taskDao.observeAll(), timeDao.observeTrackedTotals()) { list, totals ->
        val byTask = totals.associate { it.taskId to it.seconds }
        list.map { it.toDomain(byTask[it.task.id] ?: 0L) }
    }

    val demoCount: Flow<Int> = taskDao.observeDemoCount()

    fun observeTask(id: Long): Flow<Task?> = combine(taskDao.observe(id), timeDao.observeTrackedTotals()) { t, totals ->
        t?.toDomain(totals.firstOrNull { it.taskId == id }?.seconds ?: 0L)
    }

    suspend fun getTask(id: Long): Task? = taskDao.get(id)?.toDomain()

    suspend fun getAll(): List<Task> = taskDao.getAll().map { it.toDomain() }

    /**
     * Inserts or updates a task together with its subtasks. Throws [IllegalArgumentException]
     * when the task is invalid (the UI validates first, this is a last line of defence).
     * When a task becomes completed through this call, its next recurring instance is created.
     */
    suspend fun save(task: Task): Long {
        val errors = TaskValidator.validate(task)
        require(errors.isEmpty()) { "Invalid task: $errors" }
        val now = clock.millis()
        val previous = if (task.id != 0L) taskDao.get(task.id)?.toDomain() else null
        val becameCompleted = task.isCompleted && previous?.isCompleted != true
        val toSave = task.copy(
            createdAt = previous?.createdAt ?: task.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
            completedAt = if (task.isCompleted) (task.completedAt ?: previous?.completedAt ?: now) else null
        )
        val id = db.withTransaction {
            val id = if (previous == null) taskDao.insert(toSave.copy(id = if (task.id != 0L) task.id else 0L).toEntity())
            else { taskDao.update(toSave.toEntity()); toSave.id }
            subtaskDao.deleteForTask(id)
            subtaskDao.insertAll(toSave.subtasks.filter { it.title.isNotBlank() }.mapIndexed { i, s -> s.toEntity(id, i) })
            id
        }
        reminders.schedule(toSave.copy(id = id))
        if (becameCompleted) createNextRecurring(toSave.copy(id = id))
        return id
    }

    private suspend fun createNextRecurring(completed: Task): Long? {
        val next = Deadlines.nextRecurringInstance(completed, LocalDate.now(clock), clock.millis()) ?: return null
        return save(next)
    }

    suspend fun setCompleted(id: Long, done: Boolean): CompletionResult? {
        val task = taskDao.get(id)?.toDomain() ?: return null
        if (task.isCompleted == done) return CompletionResult(id, null)
        val now = clock.millis()
        return if (done) {
            val updated = task.copy(status = TaskStatus.COMPLETED, completedAt = now, updatedAt = now)
            taskDao.update(updated.toEntity())
            reminders.cancel(id)
            CompletionResult(id, createNextRecurring(updated))
        } else {
            val updated = task.copy(status = TaskStatus.NOT_STARTED, completedAt = null, updatedAt = now)
            taskDao.update(updated.toEntity())
            reminders.schedule(updated)
            CompletionResult(id, null)
        }
    }

    suspend fun setStatus(id: Long, status: TaskStatus) {
        val task = taskDao.get(id)?.toDomain() ?: return
        if (status == TaskStatus.COMPLETED) { setCompleted(id, true); return }
        val now = clock.millis()
        val updated = task.copy(status = status, completedAt = null, updatedAt = now)
        taskDao.update(updated.toEntity())
        reminders.schedule(updated)
    }

    /** Deletes tasks and returns snapshots that [restore] can put back (undo). */
    suspend fun delete(ids: List<Long>): List<Task> {
        val snapshot = ids.mapNotNull { taskDao.get(it)?.toDomain() }
        ids.forEach { reminders.cancel(it) }
        taskDao.delete(ids)
        return snapshot
    }

    suspend fun restore(tasks: List<Task>) {
        db.withTransaction {
            taskDao.insertAll(tasks.map { it.toEntity() })
            tasks.forEach { t ->
                subtaskDao.deleteForTask(t.id)
                subtaskDao.insertAll(t.subtasks.mapIndexed { i, s -> s.toEntity(t.id, i) })
            }
        }
        tasks.forEach { reminders.schedule(it) }
    }

    /** Explicit, user-initiated move. Returns previous quadrants for undo. */
    suspend fun moveToQuadrant(ids: List<Long>, quadrant: Quadrant): Map<Long, Quadrant> {
        val previous = ids.mapNotNull { id -> taskDao.get(id)?.let { id to it.toDomain().quadrant } }.toMap()
        taskDao.setQuadrant(ids, quadrant.important, quadrant.urgent, quadrant.number, clock.millis())
        return previous
    }

    suspend fun restoreQuadrants(previous: Map<Long, Quadrant>) {
        previous.forEach { (id, q) -> taskDao.setQuadrant(listOf(id), q.important, q.urgent, q.number, clock.millis()) }
    }

    suspend fun setArchived(ids: List<Long>, archived: Boolean) {
        taskDao.setArchived(ids, archived, clock.millis())
        ids.forEach { id ->
            if (archived) reminders.cancel(id) else taskDao.get(id)?.toDomain()?.let { reminders.schedule(it) }
        }
    }

    suspend fun duplicate(id: Long, titleSuffix: String): Long? {
        val t = taskDao.get(id)?.toDomain() ?: return null
        val now = clock.millis()
        val copy = t.copy(
            id = 0,
            title = (t.title + titleSuffix).take(TaskValidator.MAX_TITLE),
            status = TaskStatus.NOT_STARTED,
            completedAt = null,
            isArchived = false,
            isDemo = false,
            createdAt = now,
            updatedAt = now,
            subtasks = t.subtasks.map { it.copy(id = 0, taskId = 0, isDone = false) }
        )
        return save(copy)
    }

    suspend fun reschedule(id: Long, date: LocalDate?) {
        taskDao.setDueDate(id, date, clock.millis())
        val t = taskDao.get(id)?.toDomain() ?: return
        if (date == null && t.dueTime != null) taskDao.update(t.copy(dueTime = null, reminderOffsets = emptyList()).toEntity())
        taskDao.get(id)?.toDomain()?.let { reminders.schedule(it) }
    }

    suspend fun setSubtaskDone(subtaskId: Long, done: Boolean) = subtaskDao.setDone(subtaskId, done)

    suspend fun rescheduleAllReminders() {
        taskDao.getActiveWithDeadline().forEach { reminders.schedule(it.toDomain()) }
    }
}
