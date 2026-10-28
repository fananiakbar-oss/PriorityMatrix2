package com.prioritymatrix.app.domain.logic

import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task

enum class InsightType {
    NO_TRACKED_TIME,
    URGENT_DOMINATES,
    STRONG_STRATEGIC_FOCUS,
    LOW_VALUE_TIME,
    OVERDUE_TASKS,
    UNSCHEDULED_Q2,
    LOW_COMPLETION,
    ESTIMATE_OVERRUN
}

enum class Severity { INFO, POSITIVE, WARNING }

/** [values] are the measured numbers the rule used, shown to the user for transparency. */
data class Insight(val type: InsightType, val severity: Severity, val values: List<Int>)

/**
 * Rule-based insights. No hidden scores: each rule and threshold is listed here and explained
 * in the UI next to the insight.
 */
object RecommendationEngine {
    const val MIN_TRACKED_MINUTES = 60
    const val URGENT_SHARE_THRESHOLD = 0.5f
    const val STRATEGIC_LOW_THRESHOLD = 0.25f
    const val STRATEGIC_GOOD_THRESHOLD = 0.4f
    const val LOW_VALUE_THRESHOLD = 0.4f
    const val UNSCHEDULED_Q2_MIN = 3
    const val LOW_COMPLETION_THRESHOLD = 0.5f
    const val LOW_COMPLETION_MIN_DUE = 4
    const val OVERRUN_THRESHOLD = 1.25f

    fun insights(report: AnalyticsReport, activeTasks: List<Task>): List<Insight> = buildList {
        val total = report.trackedMinutesTotal
        if (total < MIN_TRACKED_MINUTES) {
            add(Insight(InsightType.NO_TRACKED_TIME, Severity.INFO, listOf(total)))
        } else {
            val q1 = report.trackedShare(Quadrant.DO_FIRST)
            val q2 = report.trackedShare(Quadrant.SCHEDULE)
            val lowValue = report.trackedShare(Quadrant.DELEGATE) + report.trackedShare(Quadrant.ELIMINATE)
            if (q1 > URGENT_SHARE_THRESHOLD && q2 < STRATEGIC_LOW_THRESHOLD) {
                add(Insight(InsightType.URGENT_DOMINATES, Severity.WARNING, listOf(pct(q1), pct(q2))))
            }
            if (q2 >= STRATEGIC_GOOD_THRESHOLD) {
                add(Insight(InsightType.STRONG_STRATEGIC_FOCUS, Severity.POSITIVE, listOf(pct(q2))))
            }
            if (lowValue > LOW_VALUE_THRESHOLD) {
                add(Insight(InsightType.LOW_VALUE_TIME, Severity.WARNING, listOf(pct(lowValue))))
            }
        }
        if (report.overdueNow > 0) {
            add(Insight(InsightType.OVERDUE_TASKS, Severity.WARNING, listOf(report.overdueNow)))
        }
        val unscheduledQ2 = activeTasks.count { it.quadrant == Quadrant.SCHEDULE && it.dueDate == null && it.startDate == null }
        if (unscheduledQ2 >= UNSCHEDULED_Q2_MIN) {
            add(Insight(InsightType.UNSCHEDULED_Q2, Severity.INFO, listOf(unscheduledQ2)))
        }
        val rate = report.completionRate
        if (rate != null && report.dueInRange >= LOW_COMPLETION_MIN_DUE && rate < LOW_COMPLETION_THRESHOLD) {
            add(Insight(InsightType.LOW_COMPLETION, Severity.WARNING, listOf(pct(rate), report.dueInRange)))
        }
        val est = report.estimate
        if (est.taskCount >= 2 && est.estimatedMinutes > 0 && est.actualMinutes > est.estimatedMinutes * OVERRUN_THRESHOLD) {
            add(Insight(InsightType.ESTIMATE_OVERRUN, Severity.INFO, listOf(est.estimatedMinutes, est.actualMinutes)))
        }
    }

    private fun pct(f: Float): Int = Math.round(f * 100)
}
