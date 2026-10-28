package com.prioritymatrix.app.domain

import com.prioritymatrix.app.domain.logic.ProposedChange
import com.prioritymatrix.app.domain.logic.Reason
import com.prioritymatrix.app.domain.logic.SmartPrioritizer
import com.prioritymatrix.app.domain.logic.SuggestedAction
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Quadrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPrioritizerTest {

    @Test fun recommendationNeverMutatesTheTask() {
        val t = task(Quadrant.SCHEDULE, due = TODAY.plusDays(1))
        val before = t.copy()
        val rec = SmartPrioritizer.recommend(t, NOW, ZONE)
        assertEquals(before, t)
        // Only a proposal — the user must confirm.
        assertEquals(ProposedChange.MARK_URGENT, rec.proposedChange)
        assertEquals(SuggestedAction.DO_NOW, rec.action)
    }

    @Test fun q1_doNow() = assertEquals(SuggestedAction.DO_NOW, SmartPrioritizer.recommend(task(Quadrant.DO_FIRST, due = TODAY), NOW, ZONE).action)

    @Test fun q2_schedule_andFlagsMissingDate() {
        val rec = SmartPrioritizer.recommend(task(Quadrant.SCHEDULE), NOW, ZONE)
        assertEquals(SuggestedAction.SCHEDULE, rec.action)
        assertTrue(Reason.Q2_WITHOUT_DATE in rec.reasons)
        assertNull(rec.proposedChange)
    }

    @Test fun q3_delegate_andFlagsMissingAssignee() {
        val rec = SmartPrioritizer.recommend(task(Quadrant.DELEGATE, due = TODAY), NOW, ZONE)
        assertEquals(SuggestedAction.DELEGATE, rec.action)
        assertTrue(Reason.Q3_WITHOUT_ASSIGNEE in rec.reasons)
        val assigned = SmartPrioritizer.recommend(task(Quadrant.DELEGATE, due = TODAY, assignee = "Rina"), NOW, ZONE)
        assertTrue(Reason.Q3_WITHOUT_ASSIGNEE !in assigned.reasons)
    }

    @Test fun urgentWithFarDeadline_proposesNotUrgent() {
        val rec = SmartPrioritizer.recommend(task(Quadrant.DO_FIRST, due = TODAY.plusDays(20)), NOW, ZONE)
        assertEquals(ProposedChange.MARK_NOT_URGENT, rec.proposedChange)
    }

    @Test fun staleQ4_proposesArchive() {
        val rec = SmartPrioritizer.recommend(task(Quadrant.ELIMINATE, created = TODAY.minusDays(30)), NOW, ZONE)
        assertEquals(SuggestedAction.MINIMIZE, rec.action)
        assertEquals(ProposedChange.ARCHIVE, rec.proposedChange)
    }

    @Test fun overdueImportant_doNow() {
        val rec = SmartPrioritizer.recommend(task(Quadrant.SCHEDULE, due = TODAY.minusDays(2)), NOW, ZONE)
        assertEquals(SuggestedAction.DO_NOW, rec.action)
        assertEquals(listOf(Reason.OVERDUE_IMPORTANT), rec.reasons)
    }

    @Test fun completed_noAction() {
        assertEquals(SuggestedAction.NONE, SmartPrioritizer.recommend(task(done = true), NOW, ZONE).action)
    }

    @Test fun score_isTransparentSum() {
        // Q1 (40) + overdue (30) + HIGH priority (3*5)
        assertEquals(85, SmartPrioritizer.score(task(Quadrant.DO_FIRST, due = TODAY.minusDays(1), priority = Priority.HIGH), NOW))
        // Q4 (10) + due today (20) + MEDIUM (10)
        assertEquals(40, SmartPrioritizer.score(task(Quadrant.ELIMINATE, due = TODAY), NOW))
        assertEquals(0, SmartPrioritizer.score(task(done = true), NOW))
    }

    @Test fun suggestTop3_ordersByScore() {
        val a = task(Quadrant.ELIMINATE)
        val b = task(Quadrant.DO_FIRST, due = TODAY)
        val c = task(Quadrant.SCHEDULE)
        val d = task(Quadrant.DELEGATE, due = TODAY.minusDays(1))
        val top = SmartPrioritizer.suggestTop3(listOf(a, b, c, d, task(done = true)), NOW)
        assertEquals(listOf(b.id, d.id, c.id), top.map { it.id })
    }
}
