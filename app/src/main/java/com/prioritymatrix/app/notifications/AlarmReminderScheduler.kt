package com.prioritymatrix.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.prioritymatrix.app.data.prefs.SettingsRepository
import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.domain.model.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deadline reminders via AlarmManager. Uses exact alarms when the user allows them
 * (Android 12+), otherwise falls back to inexact alarms that still fire in Doze.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val clock: Clock
) : ReminderScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override suspend fun schedule(task: Task) {
        cancel(task.id)
        if (task.id == 0L || !task.isActive) return
        val prefs = settings.current()
        val now = LocalDateTime.now(clock)
        if (prefs.deadlineReminders) {
            Deadlines.reminderTriggers(task, now, clock.zone).take(MAX_REMINDERS).forEachIndexed { i, at ->
                set(at, pendingIntent(task.id, i, ReminderReceiver.KIND_REMINDER))
            }
        }
        if (prefs.overdueAlerts) {
            val deadline = Deadlines.deadlineOf(task)
            if (deadline != null && deadline.isAfter(now)) {
                val at = deadline.plusMinutes(1).atZone(clock.zone).toInstant().toEpochMilli()
                set(at, pendingIntent(task.id, SLOT_OVERDUE, ReminderReceiver.KIND_OVERDUE))
            }
        }
    }

    fun snooze(taskId: Long, minutes: Int) {
        set(clock.millis() + minutes * 60_000L, pendingIntent(taskId, SLOT_SNOOZE, ReminderReceiver.KIND_REMINDER))
    }

    override fun cancel(taskId: Long) {
        for (slot in 0 until SLOTS) {
            val kind = if (slot == SLOT_OVERDUE) ReminderReceiver.KIND_OVERDUE else ReminderReceiver.KIND_REMINDER
            existing(taskId, slot, kind)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun set(atMillis: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun intent(taskId: Long, kind: String) = Intent(context, ReminderReceiver::class.java).apply {
        action = "com.prioritymatrix.app.REMINDER.$kind.$taskId"
        putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        putExtra(ReminderReceiver.EXTRA_KIND, kind)
    }

    private fun requestCode(taskId: Long, slot: Int): Int = ((taskId % 200_000_000L) * SLOTS + slot).toInt()

    private fun pendingIntent(taskId: Long, slot: Int, kind: String): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode(taskId, slot), intent(taskId, kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun existing(taskId: Long, slot: Int, kind: String): PendingIntent? =
        PendingIntent.getBroadcast(context, requestCode(taskId, slot), intent(taskId, kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)

    companion object {
        const val MAX_REMINDERS = 5
        const val SLOT_SNOOZE = 6
        const val SLOT_OVERDUE = 7
        const val SLOTS = 8
    }
}
