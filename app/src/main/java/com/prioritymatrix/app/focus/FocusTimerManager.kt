package com.prioritymatrix.app.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.prioritymatrix.app.data.prefs.SettingsRepository
import com.prioritymatrix.app.data.repository.TimeRepository
import com.prioritymatrix.app.di.ApplicationScope
import com.prioritymatrix.app.domain.model.TimeEntrySource
import com.prioritymatrix.app.i18n.StringsProvider
import com.prioritymatrix.app.notifications.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class FocusPhase { FOCUS, SHORT_BREAK, LONG_BREAK }
enum class TimerStatus { IDLE, RUNNING, PAUSED }

data class FocusState(
    val phase: FocusPhase = FocusPhase.FOCUS,
    val status: TimerStatus = TimerStatus.IDLE,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    /** Wall-clock end time while running; the remaining time is always derived from it. */
    val endAtMillis: Long = 0,
    /** When the current phase first started (for the recorded time entry). */
    val phaseStartedAt: Long = 0,
    val taskId: Long? = null,
    val completedFocusCount: Int = 0,
    val countDate: LocalDate? = null
) {
    val elapsedSeconds: Int get() = (totalSeconds - remainingSeconds).coerceAtLeast(0)
    val progress: Float get() = if (totalSeconds == 0) 0f else elapsedSeconds.toFloat() / totalSeconds
}

/**
 * Timestamp-based Pomodoro timer. Nothing is "counted down": remaining = endAt − now, so the
 * timer stays exact in the background, in Doze, and even after the process is killed (state is
 * persisted). A foreground service keeps a live notification; an alarm guarantees the end alert.
 */
@Singleton
class FocusTimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val time: TimeRepository,
    private val strings: StringsProvider,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("focus_timer", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<FocusState> = _state.asStateFlow()
    private val mutex = Mutex()
    private var ticker: Job? = null

    init {
        scope.launch {
            applyDurationsIfIdle()
            if (_state.value.status == TimerStatus.RUNNING) {
                checkCompletion()
                if (_state.value.status == TimerStatus.RUNNING) {
                    startTicker()
                    FocusTimerService.start(context)
                }
            }
        }
    }

    private suspend fun durationFor(phase: FocusPhase): Int {
        val s = settings.current()
        return 60 * when (phase) {
            FocusPhase.FOCUS -> s.focusMinutes
            FocusPhase.SHORT_BREAK -> s.shortBreakMinutes
            FocusPhase.LONG_BREAK -> s.longBreakMinutes
        }
    }

    /** Picks up changed Pomodoro lengths when nothing is running. */
    suspend fun applyDurationsIfIdle() = mutex.withLock {
        val st = _state.value
        if (st.status == TimerStatus.IDLE) {
            val d = durationFor(st.phase)
            set(st.copy(totalSeconds = d, remainingSeconds = d))
        }
        resetDailyCountIfNeeded()
    }

    private fun resetDailyCountIfNeeded() {
        val today = LocalDate.now(clock)
        val st = _state.value
        if (st.countDate != today) set(st.copy(completedFocusCount = 0, countDate = today))
    }

    fun selectTask(taskId: Long?) = scope.launch { mutex.withLock { set(_state.value.copy(taskId = taskId)) } }

    fun selectPhase(phase: FocusPhase) = scope.launch {
        mutex.withLock {
            if (_state.value.status != TimerStatus.IDLE) return@withLock
            val d = durationFor(phase)
            set(_state.value.copy(phase = phase, totalSeconds = d, remainingSeconds = d))
        }
    }

    fun start() = scope.launch {
        mutex.withLock {
            val st = _state.value
            if (st.status == TimerStatus.RUNNING) return@withLock
            val now = clock.millis()
            val remaining = if (st.status == TimerStatus.PAUSED) st.remainingSeconds else durationFor(st.phase)
            val total = if (st.status == TimerStatus.PAUSED) st.totalSeconds else remaining
            set(
                st.copy(
                    status = TimerStatus.RUNNING,
                    totalSeconds = total,
                    remainingSeconds = remaining,
                    endAtMillis = now + remaining * 1000L,
                    phaseStartedAt = if (st.status == TimerStatus.PAUSED && st.phaseStartedAt > 0) st.phaseStartedAt else now
                )
            )
            scheduleEndAlarm(now + remaining * 1000L)
        }
        startTicker()
        FocusTimerService.start(context)
    }

    fun pause() = scope.launch {
        mutex.withLock {
            val st = _state.value
            if (st.status != TimerStatus.RUNNING) return@withLock
            set(st.copy(status = TimerStatus.PAUSED, remainingSeconds = remainingOf(st)))
            cancelEndAlarm()
        }
        ticker?.cancel()
    }

    /** Stops the session. When [record] is true, worked focus time (≥ 1 min) is saved. */
    fun stop(record: Boolean) = scope.launch {
        var recordEntry: Triple<Long?, Long, Int>? = null
        mutex.withLock {
            val st = _state.value
            val current = if (st.status == TimerStatus.RUNNING) st.copy(remainingSeconds = remainingOf(st)) else st
            recordEntry = if (record && current.phase == FocusPhase.FOCUS && current.elapsedSeconds >= 60)
                Triple(current.taskId, current.phaseStartedAt, current.elapsedSeconds) else null
            val d = durationFor(FocusPhase.FOCUS)
            set(current.copy(phase = FocusPhase.FOCUS, status = TimerStatus.IDLE, totalSeconds = d, remainingSeconds = d, endAtMillis = 0, phaseStartedAt = 0))
            cancelEndAlarm()
        }
        ticker?.cancel()
        recordEntry?.let { (taskId, startedAt, seconds) -> time.logTime(taskId, startedAt, seconds, TimeEntrySource.FOCUS) }
    }

    /** Skips to the next phase without recording a break. Focus time already worked is recorded. */
    fun skip() = scope.launch { completePhase(skipped = true) }

    fun checkCompletion() = scope.launch {
        val st = _state.value
        if (st.status == TimerStatus.RUNNING && clock.millis() >= st.endAtMillis) completePhase(skipped = false)
    }

    private suspend fun completePhase(skipped: Boolean) {
        var logged: Triple<Long?, Long, Int>? = null
        var autoStart = false
        var finishedPhase = FocusPhase.FOCUS
        mutex.withLock {
            val st = _state.value
            if (st.status == TimerStatus.IDLE && !skipped) return
            resetDailyCountIfNeeded()
            val cur = _state.value.let { if (it.status == TimerStatus.RUNNING) it.copy(remainingSeconds = remainingOf(it)) else it }
            finishedPhase = cur.phase
            val worked = if (skipped) cur.elapsedSeconds else cur.totalSeconds
            var count = cur.completedFocusCount
            if (cur.phase == FocusPhase.FOCUS && cur.status != TimerStatus.IDLE && worked >= 60) {
                logged = Triple(cur.taskId, cur.phaseStartedAt, worked)
            }
            val nextPhase = if (cur.phase == FocusPhase.FOCUS) {
                if (!skipped) count += 1
                val every = settings.current().sessionsBeforeLongBreak
                if (count > 0 && count % every == 0 && !skipped) FocusPhase.LONG_BREAK else FocusPhase.SHORT_BREAK
            } else FocusPhase.FOCUS
            val d = durationFor(nextPhase)
            autoStart = settings.current().autoStartNext && !skipped
            set(cur.copy(phase = nextPhase, status = TimerStatus.IDLE, totalSeconds = d, remainingSeconds = d,
                endAtMillis = 0, phaseStartedAt = 0, completedFocusCount = count))
            cancelEndAlarm()
        }
        ticker?.cancel()
        logged?.let { (taskId, startedAt, seconds) -> time.logTime(taskId, startedAt, seconds, TimeEntrySource.FOCUS) }
        if (!skipped) notifyPhaseDone(finishedPhase)
        if (autoStart) start()
    }

    private fun remainingOf(st: FocusState): Int =
        ((st.endAtMillis - clock.millis() + 999) / 1000).toInt().coerceIn(0, st.totalSeconds)

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val st = _state.value
                if (st.status != TimerStatus.RUNNING) break
                val remaining = remainingOf(st)
                _state.update { it.copy(remainingSeconds = remaining) }
                if (remaining <= 0) {
                    // Complete in a separate coroutine: completePhase cancels this ticker.
                    scope.launch { completePhase(skipped = false) }
                    break
                }
                delay(250)
            }
        }
    }

    private fun notifyPhaseDone(phase: FocusPhase) {
        val s = strings.current
        NotificationHelper.notify(
            context, NotificationHelper.ID_FOCUS_DONE,
            NotificationHelper.base(context, NotificationHelper.CH_FOCUS_ALERT)
                .setContentTitle(s.focusTimer)
                .setContentText(if (phase == FocusPhase.FOCUS) s.notifFocusDone else s.notifBreakDone)
                .setContentIntent(NotificationHelper.openAppIntent(context, "focus", NotificationHelper.ID_FOCUS_DONE))
        )
    }

    // ----- end-of-session alarm -----
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun endAlarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 4242, Intent(context, FocusAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleEndAlarm(at: Long) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms())
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endAlarmIntent())
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endAlarmIntent())
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endAlarmIntent())
        }
    }

    private fun cancelEndAlarm() = alarmManager.cancel(endAlarmIntent())

    // ----- persistence -----
    private fun set(s: FocusState) {
        _state.value = s
        prefs.edit()
            .putString("phase", s.phase.name).putString("status", s.status.name)
            .putInt("total", s.totalSeconds).putInt("remaining", s.remainingSeconds)
            .putLong("endAt", s.endAtMillis).putLong("startedAt", s.phaseStartedAt)
            .putLong("taskId", s.taskId ?: -1L).putInt("count", s.completedFocusCount)
            .putLong("countDate", s.countDate?.toEpochDay() ?: Long.MIN_VALUE)
            .apply()
    }

    private fun restore(): FocusState {
        if (!prefs.contains("phase")) return FocusState()
        return FocusState(
            phase = runCatching { FocusPhase.valueOf(prefs.getString("phase", null)!!) }.getOrDefault(FocusPhase.FOCUS),
            status = runCatching { TimerStatus.valueOf(prefs.getString("status", null)!!) }.getOrDefault(TimerStatus.IDLE),
            totalSeconds = prefs.getInt("total", 25 * 60),
            remainingSeconds = prefs.getInt("remaining", 25 * 60),
            endAtMillis = prefs.getLong("endAt", 0),
            phaseStartedAt = prefs.getLong("startedAt", 0),
            taskId = prefs.getLong("taskId", -1L).takeIf { it > 0 },
            completedFocusCount = prefs.getInt("count", 0),
            countDate = prefs.getLong("countDate", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
        )
    }


}
