package com.prioritymatrix.app.ui.common

import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.di.ApplicationScope
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.i18n.StringsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared task operations with consistent feedback and undo. Runs in the application scope so an
 * undo keeps working after the originating screen is gone.
 */
@Singleton
class TaskActions @Inject constructor(
    private val repo: TaskRepository,
    private val snackbar: SnackbarBus,
    private val strings: StringsProvider,
    @ApplicationScope private val scope: CoroutineScope
) {
    private fun launchSafe(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure { snackbar.show(strings.current.errorGeneric) }
        }
    }

    fun setCompleted(task: Task, done: Boolean) = launchSafe {
        val result = repo.setCompleted(task.id, done) ?: return@launchSafe
        val s = strings.current
        if (done) {
            val next = result.nextRecurringId?.let { repo.getTask(it) }
            val msg = if (next?.dueDate != null) s.nextRecurring(s.formatDate(next.dueDate, "d MMM")) else s.taskCompleted
            snackbar.show(msg, s.undo) {
                repo.setCompleted(task.id, false)
                result.nextRecurringId?.let { repo.delete(listOf(it)) }
            }
        } else {
            snackbar.show(s.taskReopened)
        }
    }

    fun delete(ids: List<Long>) = launchSafe {
        if (ids.isEmpty()) return@launchSafe
        val snapshot = repo.delete(ids)
        snackbar.show(strings.current.tasksDeleted(snapshot.size), strings.current.undo) { repo.restore(snapshot) }
    }

    fun move(ids: List<Long>, quadrant: Quadrant) = launchSafe {
        if (ids.isEmpty()) return@launchSafe
        val previous = repo.moveToQuadrant(ids, quadrant)
        if (previous.values.all { it == quadrant }) return@launchSafe
        snackbar.show(strings.current.tasksMoved(ids.size, quadrant), strings.current.undo) { repo.restoreQuadrants(previous) }
    }

    fun archive(ids: List<Long>, archived: Boolean = true) = launchSafe {
        if (ids.isEmpty()) return@launchSafe
        repo.setArchived(ids, archived)
        if (archived) snackbar.show(strings.current.tasksArchived(ids.size), strings.current.undo) { repo.setArchived(ids, false) }
    }

    fun duplicate(id: Long) = launchSafe {
        repo.duplicate(id, strings.current.copySuffix)
        snackbar.show(strings.current.taskDuplicated)
    }

    fun reschedule(task: Task, date: LocalDate?) = launchSafe {
        val old = task.dueDate
        repo.reschedule(task.id, date)
        snackbar.show(strings.current.rescheduled, strings.current.undo) { repo.reschedule(task.id, old) }
    }

    fun setSubtaskDone(subtaskId: Long, done: Boolean) = launchSafe { repo.setSubtaskDone(subtaskId, done) }

    fun completeMany(ids: List<Long>) = launchSafe {
        ids.forEach { repo.setCompleted(it, true) }
        snackbar.show(strings.current.taskCompleted, strings.current.undo) { ids.forEach { repo.setCompleted(it, false) } }
    }
}
