package com.prioritymatrix.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.prioritymatrix.app.data.prefs.SettingsRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.data.repository.TimeRepository
import com.prioritymatrix.app.domain.logic.AnalyticsCalculator
import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.domain.model.DateRange
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.i18n.StringsProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class DailyPlanningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val strings: StringsProvider,
    private val clock: Clock
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.current().dailyPlanningEnabled) return Result.success()
        val s = strings.load()
        val now = LocalDateTime.now(clock)
        val active = tasks.tasks.first().filter { it.isActive }
        val dueToday = active.count { Deadlines.isDueToday(it, now.toLocalDate()) }
        val overdue = active.count { Deadlines.isOverdue(it, now) }
        NotificationHelper.notify(
            applicationContext, NotificationHelper.ID_PLANNING,
            NotificationHelper.base(applicationContext, NotificationHelper.CH_PLANNING)
                .setContentTitle(s.notifPlanningTitle)
                .setContentText(s.notifPlanningBody(dueToday, overdue))
                .setContentIntent(NotificationHelper.openAppIntent(applicationContext, "planning", NotificationHelper.ID_PLANNING))
        )
        return Result.success()
    }
}

@HiltWorker
class WeeklyReviewWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tasks: TaskRepository,
    private val time: TimeRepository,
    private val settings: SettingsRepository,
    private val strings: StringsProvider,
    private val clock: Clock
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.current().weeklyReviewEnabled) return Result.success()
        val s = strings.load()
        val now = LocalDateTime.now(clock)
        val start = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val report = AnalyticsCalculator.calculate(tasks.tasks.first(), time.entries.first(), DateRange(start, start.plusDays(6)), now, clock.zone)
        val q2 = Math.round(report.trackedShare(Quadrant.SCHEDULE) * 100)
        NotificationHelper.notify(
            applicationContext, NotificationHelper.ID_REVIEW,
            NotificationHelper.base(applicationContext, NotificationHelper.CH_PLANNING)
                .setContentTitle(s.notifReviewTitle)
                .setContentText(s.notifReviewBody(report.completedInRange, q2))
                .setContentIntent(NotificationHelper.openAppIntent(applicationContext, "review", NotificationHelper.ID_REVIEW))
        )
        return Result.success()
    }
}

/** Safety net: re-arms every deadline alarm twice a day (time-zone changes, OEM alarm clearing). */
@HiltWorker
class ReminderResyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tasks: TaskRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        tasks.rescheduleAllReminders()
        return Result.success()
    }
}

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val clock: Clock
) {
    private val wm get() = WorkManager.getInstance(context)

    suspend fun applyCurrentSettings() {
        val s = settings.current()
        val now = LocalDateTime.now(clock)

        if (s.dailyPlanningEnabled) {
            val target = LocalTime.of(s.dailyPlanningMinute / 60, s.dailyPlanningMinute % 60)
            var next = now.toLocalDate().atTime(target)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val req = PeriodicWorkRequestBuilder<DailyPlanningWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, req)
        } else wm.cancelUniqueWork(DAILY)

        if (s.weeklyReviewEnabled) {
            val target = LocalTime.of(s.weeklyReviewMinute / 60, s.weeklyReviewMinute % 60)
            val day = DayOfWeek.of(s.weeklyReviewDay)
            var next: LocalDateTime = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atTime(target)
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            val req = PeriodicWorkRequestBuilder<WeeklyReviewWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(WEEKLY, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, req)
        } else wm.cancelUniqueWork(WEEKLY)

        wm.enqueueUniquePeriodicWork(
            RESYNC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderResyncWorker>(12, TimeUnit.HOURS).build()
        )
    }

    companion object {
        const val DAILY = "daily_planning"
        const val WEEKLY = "weekly_review"
        const val RESYNC = "reminder_resync"
    }
}
