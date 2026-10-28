package com.prioritymatrix.app.domain.logic

import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class SuggestedAction { DO_NOW, SCHEDULE, DELEGATE, MINIMIZE, NONE }

/** Why a suggestion was made. Every reason maps to a plain-language rule in the UI. */
enum class Reason {
    COMPLETED,
    OVERDUE_IMPORTANT,
    OVERDUE_NOT_IMPORTANT,
    IMPORTANT_AND_URGENT,
    IMPORTANT_DUE_SOON_NOT_MARKED_URGENT,
    IMPORTANT_NOT_URGENT,
    Q2_WITHOUT_DATE,
    URGENT_NOT_IMPORTANT,
    Q3_WITHOUT_ASSIGNEE,
    NEITHER,
    Q4_STALE,
    URGENT_BUT_FAR_DEADLINE
}

/** A classification change the user may accept. Never applied automatically. */
enum class ProposedChange { MARK_URGENT, MARK_NOT_URGENT, ARCHIVE }

data class Recommendation(
    val action: SuggestedAction,
    val reasons: List<Reason>,
    val proposedChange: ProposedChange? = null,
    /** Days until the deadline, used in reason texts. */
    val daysUntilDue: Long? = null
)

/**
 * Transparent, rule-based recommendations. Thresholds are constants so they can be shown to
 * the user and covered by tests. This object never mutates a task.
 */
object SmartPrioritizer {
    const val DUE_SOON_DAYS = 2L
    const val FAR_DEADLINE_DAYS = 7L
    const val STALE_DAYS = 14L

    fun recommend(task: Task, now: LocalDateTime, zone: ZoneId): Recommendation {
        val today = now.toLocalDate()
        if (task.isCompleted || task.isArchived) return Recommendation(SuggestedAction.NONE, listOf(Reason.COMPLETED))
        val days = Deadlines.daysUntilDue(task, today)
        val overdue = Deadlines.isOverdue(task, now)

        if (overdue) {
            return if (task.isImportant) {
                Recommendation(SuggestedAction.DO_NOW, listOf(Reason.OVERDUE_IMPORTANT), daysUntilDue = days)
            } else {
                Recommendation(
                    if (task.isUrgent) SuggestedAction.DELEGATE else SuggestedAction.MINIMIZE,
                    listOf(Reason.OVERDUE_NOT_IMPORTANT), daysUntilDue = days
                )
            }
        }

        return when (task.quadrant) {
            Quadrant.DO_FIRST -> {
                if (days != null && days > FAR_DEADLINE_DAYS) {
                    Recommendation(
                        SuggestedAction.SCHEDULE,
                        listOf(Reason.URGENT_BUT_FAR_DEADLINE),
                        ProposedChange.MARK_NOT_URGENT, days
                    )
                } else Recommendation(SuggestedAction.DO_NOW, listOf(Reason.IMPORTANT_AND_URGENT), daysUntilDue = days)
            }
            Quadrant.SCHEDULE -> {
                if (days != null && days <= DUE_SOON_DAYS) {
                    Recommendation(
                        SuggestedAction.DO_NOW,
                        listOf(Reason.IMPORTANT_DUE_SOON_NOT_MARKED_URGENT),
                        ProposedChange.MARK_URGENT, days
                    )
                } else {
                    val reasons = buildList {
                        add(Reason.IMPORTANT_NOT_URGENT)
                        if (task.dueDate == null && task.startDate == null) add(Reason.Q2_WITHOUT_DATE)
                    }
                    Recommendation(SuggestedAction.SCHEDULE, reasons, daysUntilDue = days)
                }
            }
            Quadrant.DELEGATE -> {
                if (days != null && days > FAR_DEADLINE_DAYS) {
                    Recommendation(
                        SuggestedAction.MINIMIZE,
                        listOf(Reason.URGENT_BUT_FAR_DEADLINE),
                        ProposedChange.MARK_NOT_URGENT, days
                    )
                } else {
                    val reasons = buildList {
                        add(Reason.URGENT_NOT_IMPORTANT)
                        if (task.assignee.isBlank()) add(Reason.Q3_WITHOUT_ASSIGNEE)
                    }
                    Recommendation(SuggestedAction.DELEGATE, reasons, daysUntilDue = days)
                }
            }
            Quadrant.ELIMINATE -> {
                val created = Instant.ofEpochMilli(task.createdAt).atZone(zone).toLocalDate()
                val stale = task.dueDate == null && ChronoUnit.DAYS.between(created, today) >= STALE_DAYS
                if (stale) Recommendation(SuggestedAction.MINIMIZE, listOf(Reason.NEITHER, Reason.Q4_STALE), ProposedChange.ARCHIVE, days)
                else Recommendation(SuggestedAction.MINIMIZE, listOf(Reason.NEITHER), daysUntilDue = days)
            }
        }
    }

    /**
     * Transparent attention score used to order "Today's priorities" and to suggest a Top 3.
     * quadrant weight (Q1 40, Q2 30, Q3 20, Q4 10) + overdue 30 + due today 20
     * + due within 3 days 10 + priority level × 5.
     */
    fun score(task: Task, now: LocalDateTime): Int {
        if (!task.isActive) return 0
        val today = now.toLocalDate()
        var s = when (task.quadrant) {
            Quadrant.DO_FIRST -> 40
            Quadrant.SCHEDULE -> 30
            Quadrant.DELEGATE -> 20
            Quadrant.ELIMINATE -> 10
        }
        when {
            Deadlines.isOverdue(task, now) -> s += 30
            Deadlines.isDueToday(task, today) -> s += 20
            Deadlines.isDueWithin(task, today, 3) -> s += 10
        }
        s += task.priority.level * 5
        return s
    }

    fun suggestTop3(tasks: List<Task>, now: LocalDateTime): List<Task> =
        tasks.filter { it.isActive }
            .sortedWith(compareByDescending<Task> { score(it, now) }.thenBy { it.dueDate ?: LocalDate.MAX })
            .take(3)
}
