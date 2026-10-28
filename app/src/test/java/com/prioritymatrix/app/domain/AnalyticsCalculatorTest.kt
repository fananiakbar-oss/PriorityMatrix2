package com.prioritymatrix.app.domain

import com.prioritymatrix.app.domain.logic.AnalyticsCalculator
import com.prioritymatrix.app.domain.logic.InsightType
import com.prioritymatrix.app.domain.logic.RecommendationEngine
import com.prioritymatrix.app.domain.model.DateRange
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.TimeEntrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

class AnalyticsCalculatorTest {
    private val weekStart = TODAY.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val week = DateRange(weekStart, weekStart.plusDays(6))

    @Test fun countsActiveTasksPerQuadrant_excludingDoneAndArchived() {
        val tasks = listOf(
            task(Quadrant.DO_FIRST), task(Quadrant.DO_FIRST), task(Quadrant.SCHEDULE),
            task(Quadrant.DELEGATE, done = true), task(Quadrant.ELIMINATE, archived = true)
        )
        val r = AnalyticsCalculator.calculate(tasks, emptyList(), week, NOW, ZONE)
        assertEquals(2, r.activeByQuadrant[Quadrant.DO_FIRST])
        assertEquals(1, r.activeByQuadrant[Quadrant.SCHEDULE])
        assertEquals(0, r.activeByQuadrant[Quadrant.DELEGATE])
        assertEquals(0, r.activeByQuadrant[Quadrant.ELIMINATE])
        assertEquals(1, r.completedInRange)
        assertEquals(1, r.completedByQuadrant[Quadrant.DELEGATE])
    }

    @Test fun completionRate_isDoneOverDueInRange() {
        val tasks = listOf(
            task(due = TODAY, done = true), task(due = TODAY.minusDays(1), done = true),
            task(due = TODAY.minusDays(1)), task(due = TODAY.plusDays(30)) // outside week
        )
        val r = AnalyticsCalculator.calculate(tasks, emptyList(), week, NOW, ZONE)
        assertEquals(3, r.dueInRange)
        assertEquals(2, r.dueInRangeCompleted)
        assertEquals(2f / 3f, r.completionRate!!, 0.0001f)
        assertEquals(1, r.overdueNow)
        assertEquals(1f, r.todayCompletionRate!!, 0.0001f)
    }

    @Test fun completionRate_isNullWhenNothingDue() {
        val r = AnalyticsCalculator.calculate(listOf(task()), emptyList(), week, NOW, ZONE)
        assertNull(r.completionRate)
    }

    @Test fun trackedTime_usesEntriesOnly_andIsSeparateFromCounts() {
        // Lots of Q1 tasks but most TIME on Q2: counts and hours must not be mixed up.
        val tasks = List(5) { task(Quadrant.DO_FIRST) } + task(Quadrant.SCHEDULE)
        val entries = listOf(
            entry(Quadrant.SCHEDULE, 120), entry(Quadrant.DO_FIRST, 30), entry(null, 30, source = TimeEntrySource.MANUAL, taskId = null),
            entry(Quadrant.SCHEDULE, 600, on = TODAY.minusDays(20)) // outside range
        )
        val r = AnalyticsCalculator.calculate(tasks, entries, week, NOW, ZONE)
        assertEquals(5, r.activeByQuadrant[Quadrant.DO_FIRST])
        assertEquals(180, r.trackedMinutesTotal)
        assertEquals(120, r.q2Minutes)
        assertEquals(30, r.q1Minutes)
        assertEquals(120f / 180f, r.trackedShare(Quadrant.SCHEDULE), 0.0001f)
        assertEquals(30, r.trackedByQuadrant[null])
        assertEquals(2, r.focusSessions)
    }

    @Test fun estimateVsActual_onlyCompletedTasksWithEstimate() {
        val tasks = listOf(
            task(done = true, estimate = 60, tracked = 90),
            task(done = true, estimate = 30, tracked = 30),
            task(done = true, tracked = 100), // no estimate → excluded
            task(estimate = 50, tracked = 10) // not completed → excluded
        )
        val r = AnalyticsCalculator.calculate(tasks, emptyList(), week, NOW, ZONE)
        assertEquals(2, r.estimate.taskCount)
        assertEquals(90, r.estimate.estimatedMinutes)
        assertEquals(120, r.estimate.actualMinutes)
    }

    @Test fun buckets_dailyForWeek_weeklyForLongRanges() {
        val r = AnalyticsCalculator.calculate(listOf(task(done = true)), emptyList(), week, NOW, ZONE)
        assertEquals(7, r.buckets.size)
        assertFalse(r.bucketIsWeek)
        assertEquals(1, r.buckets.sumOf { it.completed })

        val long = DateRange(TODAY.minusDays(89), TODAY)
        val r2 = AnalyticsCalculator.calculate(listOf(task(done = true)), emptyList(), long, NOW, ZONE)
        assertTrue(r2.bucketIsWeek)
        assertEquals(r2.range.start, r2.buckets.first().start)
        assertEquals(1, r2.buckets.sumOf { it.completed })
    }

    @Test fun monthlyTrend_hasSixMonthsEndingWithRange() {
        val r = AnalyticsCalculator.calculate(listOf(task(done = true, completedOn = TODAY.minusMonths(2))), emptyList(), week, NOW, ZONE)
        assertEquals(6, r.monthlyTrend.size)
        assertEquals(java.time.YearMonth.from(TODAY), r.monthlyTrend.last().month)
        assertEquals(1, r.monthlyTrend[3].completed)
    }

    @Test fun insight_urgentDominates_whenQ1Above50AndQ2Below25() {
        val entries = listOf(entry(Quadrant.DO_FIRST, 160), entry(Quadrant.SCHEDULE, 20), entry(Quadrant.DELEGATE, 20))
        val r = AnalyticsCalculator.calculate(emptyList(), entries, week, NOW, ZONE)
        val insights = RecommendationEngine.insights(r, emptyList())
        val urgent = insights.first { it.type == InsightType.URGENT_DOMINATES }
        assertEquals(listOf(80, 10), urgent.values)
    }

    @Test fun insight_noTrackedTime_insteadOfFakeScores() {
        val r = AnalyticsCalculator.calculate(listOf(task()), listOf(entry(Quadrant.DO_FIRST, 20)), week, NOW, ZONE)
        val types = RecommendationEngine.insights(r, emptyList()).map { it.type }
        assertTrue(InsightType.NO_TRACKED_TIME in types)
        assertFalse(InsightType.URGENT_DOMINATES in types)
    }

    @Test fun insight_strongStrategicFocus() {
        val r = AnalyticsCalculator.calculate(emptyList(), listOf(entry(Quadrant.SCHEDULE, 90), entry(Quadrant.DO_FIRST, 60)), week, NOW, ZONE)
        assertTrue(RecommendationEngine.insights(r, emptyList()).any { it.type == InsightType.STRONG_STRATEGIC_FOCUS })
    }

    @Test fun insight_unscheduledQ2() {
        val active = List(3) { task(Quadrant.SCHEDULE) }
        val r = AnalyticsCalculator.calculate(active, emptyList(), week, NOW, ZONE)
        assertTrue(RecommendationEngine.insights(r, active).any { it.type == InsightType.UNSCHEDULED_Q2 })
    }
}
