package com.prioritymatrix.app.domain

import com.prioritymatrix.app.domain.model.Quadrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadrantClassificationTest {

    @Test fun importantAndUrgent_isDoFirst() = assertEquals(Quadrant.DO_FIRST, Quadrant.of(important = true, urgent = true))

    @Test fun importantNotUrgent_isSchedule() = assertEquals(Quadrant.SCHEDULE, Quadrant.of(important = true, urgent = false))

    @Test fun urgentButNotImportant_isDelegate_notDoFirst() {
        // Urgency must never imply importance.
        val q = Quadrant.of(important = false, urgent = true)
        assertEquals(Quadrant.DELEGATE, q)
        assertFalse(q.important)
    }

    @Test fun neither_isEliminate() = assertEquals(Quadrant.ELIMINATE, Quadrant.of(important = false, urgent = false))

    @Test fun quadrantFlagsRoundTrip() {
        Quadrant.entries.forEach { q -> assertEquals(q, Quadrant.of(q.important, q.urgent)) }
    }

    @Test fun taskQuadrantIsDerivedFromTwoIndependentFlags() {
        val t = task(Quadrant.SCHEDULE)
        assertEquals(Quadrant.SCHEDULE, t.quadrant)
        assertEquals(Quadrant.DO_FIRST, t.copy(isUrgent = true).quadrant)
        assertEquals(Quadrant.ELIMINATE, t.copy(isImportant = false).quadrant)
        assertEquals(Quadrant.DELEGATE, t.withQuadrant(Quadrant.DELEGATE).quadrant)
        assertTrue(t.withQuadrant(Quadrant.DELEGATE).isUrgent)
    }

    @Test fun fromNumber() {
        assertEquals(Quadrant.SCHEDULE, Quadrant.fromNumber(2))
        assertEquals(Quadrant.ELIMINATE, Quadrant.fromNumber(4))
        assertEquals(listOf(1, 2, 3, 4), Quadrant.entries.map { it.number })
    }
}
