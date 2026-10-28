package com.prioritymatrix.app.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.i18n.StringsProvider
import com.prioritymatrix.app.notifications.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the process alive while a focus session runs and shows a live countdown notification.
 * The countdown uses the system chronometer, so the notification is only rebuilt on state changes.
 */
@AndroidEntryPoint
class FocusTimerService : LifecycleService() {
    @Inject lateinit var timer: FocusTimerManager
    @Inject lateinit var strings: StringsProvider
    @Inject lateinit var tasks: TaskRepository

    private var startedForeground = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE -> timer.pause()
            ACTION_RESUME -> timer.start()
            ACTION_STOP -> timer.stop(record = true)
        }
        if (!startedForeground) {
            goForeground(timer.state.value, null)
            observe()
        }
        return START_STICKY
    }

    private fun observe() {
        lifecycleScope.launch {
            timer.state
                .map { Triple(it.status, it.phase, it.endAtMillis to it.taskId) }
                .distinctUntilChanged()
                .collect {
                    val st = timer.state.value
                    if (st.status == TimerStatus.IDLE) {
                        ServiceCompat.stopForeground(this@FocusTimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val title = st.taskId?.let { tasks.getTask(it)?.title }
                        goForeground(st, title)
                    }
                }
        }
    }

    private fun goForeground(st: FocusState, taskTitle: String?) {
        val s = strings.current
        val running = st.status == TimerStatus.RUNNING
        val phaseLabel = when (st.phase) {
            FocusPhase.FOCUS -> s.notifFocusRunning
            FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK -> s.notifBreakRunning
        }
        val b = NotificationHelper.base(this, NotificationHelper.CH_FOCUS)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentTitle(if (running) phaseLabel else "$phaseLabel · ${s.notifPaused}")
            .setContentText(taskTitle ?: s.focusTimer)
            .setContentIntent(NotificationHelper.openAppIntent(this, "focus", NotificationHelper.ID_FOCUS_ONGOING))
        if (running) {
            b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(st.endAtMillis).setShowWhen(true)
            b.addAction(0, s.pause, actionIntent(ACTION_PAUSE))
        } else {
            val m = st.remainingSeconds / 60
            val sec = st.remainingSeconds % 60
            b.setSubText(String.format("%02d:%02d", m, sec))
            b.addAction(0, s.resume, actionIntent(ACTION_RESUME))
        }
        b.addAction(0, s.stop, actionIntent(ACTION_STOP))
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NotificationHelper.ID_FOCUS_ONGOING, b.build(), type)
        startedForeground = true
    }

    private fun actionIntent(action: String) = android.app.PendingIntent.getService(
        this, action.hashCode(), Intent(this, FocusTimerService::class.java).setAction(action),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val ACTION_PAUSE = "focus.pause"
        const val ACTION_RESUME = "focus.resume"
        const val ACTION_STOP = "focus.stop"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, FocusTimerService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusTimerService::class.java))
        }
    }
}

/** Fires at the planned end of a phase — guarantees completion even if the ticker was suspended. */
@AndroidEntryPoint
class FocusAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var timer: FocusTimerManager

    override fun onReceive(context: Context, intent: Intent) {
        timer.checkCompletion()
    }
}
