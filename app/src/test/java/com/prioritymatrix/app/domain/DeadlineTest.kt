package com.prioritymatrix.app.domain

import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.domain.logic.TaskValidator
import com.prioritymatrix.app.domain.logic.ValidationError
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.Subtask
import com.prioritymatrix.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DeadlineTest {

    @Test fun taskWithoutTime_isNotOverdueUntilEndOfDay() {
        val t = task(due = TODAY)
        assertFalse(Deadlines.isOverdue(t, NOW))
        assertFalse(Deadlines.isOverdue(t, TODAY.atTime(23, 59)))
        assertTrue(Deadlines.isOverdue(t, TODAY.plusDays(1).atTime(0, 0, 1)))
    }

    @Test fun taskWithTime_isOverdueAfterThatTime() {
        val t = task(due = TODAY, time = LocalTime.of(9, 30))
        assertTrue(Deadlines.isOverdue(t, NOW)) // NOW = 10:00
        assertFalse(Deadlines.isOverdue(t.copy(dueTime = LocalTime.of(10, 30)), NOW))
    }

    @Test fun completedOrArchivedTasks_areNeverOverdue() {
        assertFalse(Deadlines.isOverdue(task(due = TODAY.minusDays(3), done = true), NOW))
        assertFalse(Deadlines.isOverdue(task(due = TODAY.minusDays(3), archived = true), NOW))
    }

    @Test fun noDeadline_isNeverOverdue() {
        assertFalse(Deadlines.isOverdue(task(), NOW))
        assertNull(Deadlines.daysUntilDue(task(), TODAY))
    }

    @Test fun dueWithinWindow() {
        assertTrue(Deadlines.isDueWithin(task(due = TODAY.plusDays(7)), TODAY, 7))
        assertFalse(Deadlines.isDueWithin(task(due = TODAY.plusDays(8)), TODAY, 7))
        assertFalse(Deadlines.isDueWithin(task(due = TODAY.minusDays(1)), TODAY, 7))
        assertEquals(3L, Deadlines.daysUntilDue(task(due = TODAY.plusDays(3)), TODAY))
    }

    @Test fun reminderTriggers_useDueTimeAndSkipPast() {
        val t = task(due = TODAY, time = LocalTime.of(12, 0)).copy(reminderOffsets = listOf(0, 30, 180))
        val triggers = Deadlines.reminderTriggers(t, NOW, ZONE)
        // 09:00 (180 min before) is already past at 10:00.
        assertEquals(listOf(TODAY.atTime(11, 30).millis(), TODAY.atTime(12, 0).millis()), triggers)
    }

    @Test fun reminderTriggers_withoutTime_anchorAtNine() {
        val t = task(due = TODAY.plusDays(1)).copy(reminderOffsets = listOf(60))
        assertEquals(listOf(TODAY.plusDays(1).atTime(8, 0).millis()), Deadlines.reminderTriggers(t, NOW, ZONE))
    }

    @Test fun reminderTriggers_emptyForCompleted() {
        val t = task(due = TODAY.plusDays(1), done = true).copy(reminderOffsets = listOf(0))
        assertTrue(Deadlines.reminderTriggers(t, NOW, ZONE).isEmpty())
    }

    @Test fun nextOccurrence_rules() {
        val friday = LocalDate.of(2026, 10, 30)
        assertEquals(LocalDate.of(2026, 11, 2), Deadlines.nextOccurrence(friday, Recurrence.WEEKDAYS))
        assertEquals(friday.plusDays(1), Deadlines.nextOccurrence(friday, Recurrence.DAILY))
        assertEquals(friday.plusWeeks(1), Deadlines.nextOccurrence(friday, Recurrence.WEEKLY))
        assertEquals(LocalDate.of(2026, 2, 28), Deadlines.nextOccurrence(LocalDate.of(2026, 1, 31), Recurrence.MONTHLY))
        assertNull(Deadlines.nextOccurrence(friday, Recurrence.NONE))
    }

    @Test fun nextRecurringInstance_shiftsDatesAndResetsState() {
        val t = task(Quadrant.SCHEDULE, due = TODAY, start = TODAY.minusDays(1), done = true).copy(
            recurrence = Recurrence.WEEKLY,
            subtasks = listOf(Subtask(id = 5, taskId = 1, title = "a", isDone = true))
        )
        val next = Deadlines.nextRecurringInstance(t, TODAY, NOW.millis())!!
        assertEquals(TODAY.plusWeeks(1), next.dueDate)
        assertEquals(TODAY.minusDays(1).plusWeeks(1), next.startDate)
        assertEquals(TaskStatus.NOT_STARTED, next.status)
        assertNull(next.completedAt)
        assertEquals(0L, next.id)
        assertFalse(next.subtasks.single().isDone)
        assertEquals(Quadrant.SCHEDULE, next.quadrant)
    }

    @Test fun nextRecurringInstance_skipsPastOccurrences() {
        val t = task(due = TODAY.minusDays(10)).copy(recurrence = Recurrence.DAILY)
        val next = Deadlines.nextRecurringInstance(t, TODAY, NOW.millis())!!
        assertFalse(next.dueDate!!.isBefore(TODAY))
    }

    @Test fun validation() {
        assertEquals(listOf(ValidationError.TITLE_EMPTY), TaskValidator.validate(task(title = "  ")))
        assertTrue(ValidationError.START_AFTER_DUE in TaskValidator.validate(task(due = TODAY, start = TODAY.plusDays(1))))
        assertTrue(ValidationError.TIME_WITHOUT_DATE in TaskValidator.validate(task(time = LocalTime.NOON)))
        assertTrue(ValidationError.ESTIMATE_INVALID in TaskValidator.validate(task(estimate = -1)))
        assertTrue(ValidationError.REMINDER_WITHOUT_DATE in TaskValidator.validate(task().copy(reminderOffsets = listOf(10))))
        assertTrue(TaskValidator.validate(task(due = TODAY, start = TODAY, estimate = 30)).isEmpty())
    }
}
