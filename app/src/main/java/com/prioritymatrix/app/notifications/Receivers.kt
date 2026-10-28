package com.prioritymatrix.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.di.ApplicationScope
import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.i18n.StringsProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Runs [block] off the main thread while keeping the receiver alive. */
internal fun BroadcastReceiver.goAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try { block() } finally { pending.finish() }
    }
}

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: TaskRepository
    @Inject lateinit var strings: StringsProvider
    @Inject lateinit var clock: Clock
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_REMINDER
        if (taskId <= 0) return
        goAsync(scope) {
            val task = repo.getTask(taskId) ?: return@goAsync
            if (!task.isActive) return@goAsync
            val s = strings.load()
            val now = LocalDateTime.now(clock)
            if (kind == KIND_OVERDUE && !Deadlines.isOverdue(task, now)) return@goAsync
            val due = task.dueDate
            val dueText = buildString {
                if (due != null) append(s.dueLabel(due, LocalDate.now(clock)))
                task.dueTime?.let { append(" · ").append(s.formatTime(it)) }
            }
            val title = if (kind == KIND_OVERDUE) "${s.notifOverdueTitle}: ${task.title}" else task.title
            val nid = NotificationHelper.taskNotificationId(taskId)
            val builder = NotificationHelper.base(context, if (kind == KIND_OVERDUE) NotificationHelper.CH_OVERDUE else NotificationHelper.CH_REMINDERS)
                .setContentTitle(title)
                .setContentText("${s.quadrantShort(task.quadrant)} · ${s.quadrantAction(task.quadrant)} · ${s.notifDueIn(dueText)}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(NotificationHelper.openAppIntent(context, "task/$taskId", nid))
                .addAction(0, s.done, NotificationActionReceiver.pending(context, NotificationActionReceiver.ACTION_DONE, taskId))
                .addAction(0, s.notifSnooze, NotificationActionReceiver.pending(context, NotificationActionReceiver.ACTION_SNOOZE, taskId))
            NotificationHelper.notify(context, nid, builder)
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_KIND = "kind"
        const val KIND_REMINDER = "REMINDER"
        const val KIND_OVERDUE = "OVERDUE"
    }
}

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: TaskRepository
    @Inject lateinit var scheduler: AlarmReminderScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderReceiver.EXTRA_TASK_ID, -1)
        if (taskId <= 0) return
        NotificationHelper.cancel(context, NotificationHelper.taskNotificationId(taskId))
        when (intent.action?.substringBefore('#')) {
            ACTION_DONE -> goAsync(scope) { repo.setCompleted(taskId, true) }
            ACTION_SNOOZE -> scheduler.snooze(taskId, 10)
        }
    }

    companion object {
        const val ACTION_DONE = "com.prioritymatrix.app.ACTION_DONE"
        const val ACTION_SNOOZE = "com.prioritymatrix.app.ACTION_SNOOZE"

        fun pending(context: Context, action: String, taskId: Long): android.app.PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = "$action#$taskId"
                putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            }
            val code = (taskId % 100_000_000L).toInt() * 2 + if (action == ACTION_DONE) 0 else 1
            return android.app.PendingIntent.getBroadcast(context, code, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}

/** Alarms are cleared on reboot; restore them (and periodic work) on boot, update or permission change. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: TaskRepository
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        goAsync(scope) {
            repo.rescheduleAllReminders()
            workScheduler.applyCurrentSettings()
        }
    }
}
