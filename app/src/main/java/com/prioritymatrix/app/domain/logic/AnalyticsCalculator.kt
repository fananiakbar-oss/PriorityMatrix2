package com.prioritymatrix.app.domain.logic

import com.prioritymatrix.app.domain.model.DateRange
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TimeEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class Bucket(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val completed: Int,
    val due: Int,
    val dueCompleted: Int,
    val trackedMinutes: Int
)

data class MonthPoint(val month: YearMonth, val completed: Int, val trackedMinutes: Int)

data class EstimateComparison(val taskCount: Int, val estimatedMinutes: Int, val actualMinutes: Int)

data class AnalyticsReport(
    val range: DateRange,
    /** Task COUNTS — active (not completed, not archived) right now. */
    val activeByQuadrant: Map<Quadrant, Int>,
    /** Task COUNTS — completed within the range. */
    val completedByQuadrant: Map<Quadrant, Int>,
    val completedInRange: Int,
    val createdInRange: Int,
    val overdueNow: Int,
    val dueInRange: Int,
    val dueInRangeCompleted: Int,
    /** completed ÷ due for tasks whose deadline falls in the range. Null when nothing was due. */
    val completionRate: Float?,
    val todayCompletionRate: Float?,
    val weekCompletionRate: Float?,
    val buckets: List<Bucket>,
    val bucketIsWeek: Boolean,
    val monthlyTrend: List<MonthPoint>,
    val estimate: EstimateComparison,
    /** TRACKED TIME in minutes (focus sessions + manual logs) within the range. */
    val trackedMinutesTotal: Int,
    /** Key null = time not linked to any task. */
    val trackedByQuadrant: Map<Quadrant?, Int>,
    val focusSessions: Int
) {
    fun trackedShare(q: Quadrant?): Float =
        if (trackedMinutesTotal == 0) 0f else (trackedByQuadrant[q] ?: 0).toFloat() / trackedMinutesTotal

    val q1Minutes: Int get() = trackedByQuadrant[Quadrant.DO_FIRST] ?: 0
    val q2Minutes: Int get() = trackedByQuadrant[Quadrant.SCHEDULE] ?: 0
}

/** Pure analytics. Every number is computed from recorded tasks and time entries only. */
object AnalyticsCalculator {

    fun calculate(
        tasks: List<Task>,
        entries: List<TimeEntry>,
        range: DateRange,
        now: LocalDateTime,
        zone: ZoneId,
        monthsInTrend: Int = 6
    ): AnalyticsReport {
        val today = now.toLocalDate()
        fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

        val notArchived = tasks.filter { !it.isArchived }
        val active = notArchived.filter { it.isActive }
        val completedInRange = tasks.filter { t -> t.isCompleted && t.completedAt?.toDate()?.let { it in range } == true }
        val dueInRange = notArchived.filter { t -> t.dueDate?.let { it in range } == true }
        val entriesInRange = entries.filter { it.startedAt.toDate() in range }

        fun rate(due: List<Task>): Float? = if (due.isEmpty()) null else due.count { it.isCompleted }.toFloat() / due.size

        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekRange = DateRange(weekStart, weekStart.plusDays(6))

        val bucketIsWeek = range.days > 31
        val buckets = buildList {
            var cursor = if (bucketIsWeek) range.start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else range.start
            while (!cursor.isAfter(range.endInclusive)) {
                val end = if (bucketIsWeek) cursor.plusDays(6) else cursor
                val b = DateRange(
                    if (cursor.isBefore(range.start)) range.start else cursor,
                    if (end.isAfter(range.endInclusive)) range.endInclusive else end
                )
                val due = dueInRange.filter { it.dueDate!! in b }
                add(
                    Bucket(
                        start = b.start,
                        endInclusive = b.endInclusive,
                        completed = completedInRange.count { it.completedAt!!.toDate() in b },
                        due = due.size,
                        dueCompleted = due.count { it.isCompleted },
                        trackedMinutes = entriesInRange.filter { it.startedAt.toDate() in b }.sumOf { it.durationSeconds } / 60
                    )
                )
                cursor = end.plusDays(1)
            }
        }

        val endMonth = YearMonth.from(range.endInclusive)
        val trend = (monthsInTrend - 1 downTo 0).map { back ->
            val m = endMonth.minusMonths(back.toLong())
            MonthPoint(
                month = m,
                completed = tasks.count { t -> t.isCompleted && t.completedAt?.let { YearMonth.from(it.toDate()) == m } == true },
                trackedMinutes = entries.filter { YearMonth.from(it.startedAt.toDate()) == m }.sumOf { it.durationSeconds } / 60
            )
        }

        val withEstimate = completedInRange.filter { (it.estimatedMinutes ?: 0) > 0 }
        val estimate = EstimateComparison(
            taskCount = withEstimate.size,
            estimatedMinutes = withEstimate.sumOf { it.estimatedMinutes ?: 0 },
            actualMinutes = withEstimate.sumOf { it.trackedMinutes }
        )

        val trackedSeconds = entriesInRange.groupBy { it.quadrant }.mapValues { (_, v) -> v.sumOf { it.durationSeconds } }
        val trackedByQuadrant: Map<Quadrant?, Int> = trackedSeconds.mapValues { it.value / 60 }

        return AnalyticsReport(
            range = range,
            activeByQuadrant = Quadrant.entries.associateWith { q -> active.count { it.quadrant == q } },
            completedByQuadrant = Quadrant.entries.associateWith { q -> completedInRange.count { it.quadrant == q } },
            completedInRange = completedInRange.size,
            createdInRange = tasks.count { it.createdAt.toDate() in range },
            overdueNow = active.count { Deadlines.isOverdue(it, now) },
            dueInRange = dueInRange.size,
            dueInRangeCompleted = dueInRange.count { it.isCompleted },
            completionRate = rate(dueInRange),
            todayCompletionRate = rate(notArchived.filter { it.dueDate == today }),
            weekCompletionRate = rate(notArchived.filter { t -> t.dueDate?.let { it in weekRange } == true }),
            buckets = buckets,
            bucketIsWeek = bucketIsWeek,
            monthlyTrend = trend,
            estimate = estimate,
            trackedMinutesTotal = entriesInRange.sumOf { it.durationSeconds } / 60,
            trackedByQuadrant = trackedByQuadrant,
            focusSessions = entriesInRange.count { it.source == com.prioritymatrix.app.domain.model.TimeEntrySource.FOCUS }
        )
    }
}
