package com.prioritymatrix.app.domain

import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.ui.tasks.DeadlineFilter
import com.prioritymatrix.app.ui.tasks.SortOption
import com.prioritymatrix.app.ui.tasks.TaskFilters
import com.prioritymatrix.app.ui.tasks.TaskFiltering
import com.prioritymatrix.app.ui.tasks.TaskTab
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskFilteringTest {
    private val overdue = task(Quadrant.DO_FIRST, due = TODAY.minusDays(1), title = "Laporan progres")
    private val today = task(Quadrant.SCHEDULE, due = TODAY, priority = Priority.CRITICAL, title = "Evaluasi risiko")
    private val none = task(Quadrant.DELEGATE, assignee = "Rina", title = "Rapat")
    private val done = task(Quadrant.ELIMINATE, done = true, title = "Arsip lama")
    private val archived = task(Quadrant.ELIMINATE, archived = true, title = "Media sosial")
    private val all = listOf(overdue, today, none, done, archived)

    private fun run(f: TaskFilters) = TaskFiltering.apply(all, f, NOW).map { it.id }

    @Test fun tabs() {
        assertEquals(setOf(overdue.id, today.id, none.id), run(TaskFilters()).toSet())
        assertEquals(listOf(done.id), run(TaskFilters(tab = TaskTab.COMPLETED)))
        assertEquals(listOf(archived.id), run(TaskFilters(tab = TaskTab.ARCHIVED)))
    }

    @Test fun filters() {
        assertEquals(listOf(overdue.id), run(TaskFilters(deadline = DeadlineFilter.OVERDUE)))
        assertEquals(listOf(today.id), run(TaskFilters(deadline = DeadlineFilter.TODAY)))
        assertEquals(listOf(none.id), run(TaskFilters(deadline = DeadlineFilter.NONE)))
        assertEquals(listOf(today.id), run(TaskFilters(quadrants = setOf(Quadrant.SCHEDULE))))
        assertEquals(listOf(today.id), run(TaskFilters(priorities = setOf(Priority.CRITICAL))))
        assertEquals(listOf(none.id), run(TaskFilters(assignee = "rina")))
    }

    @Test fun search_isCaseInsensitive() {
        assertEquals(listOf(today.id), run(TaskFilters(query = "RISIKO")))
    }

    @Test fun sorting() {
        assertEquals(listOf(overdue.id, today.id, none.id), run(TaskFilters(sort = SortOption.DUE)))
        assertEquals(listOf(overdue.id, today.id, none.id), run(TaskFilters(sort = SortOption.QUADRANT)))
        assertEquals(listOf(today.id, overdue.id, none.id), run(TaskFilters(sort = SortOption.PRIORITY)))
        assertEquals(listOf(today.id, overdue.id, none.id), run(TaskFilters(sort = SortOption.TITLE)))
    }
}
