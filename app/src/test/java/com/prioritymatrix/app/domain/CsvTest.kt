package com.prioritymatrix.app.domain

import com.prioritymatrix.app.domain.logic.CsvCodec
import com.prioritymatrix.app.domain.logic.TaskCsvMapper
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.Subtask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class CsvTest {

    @Test fun codecRoundTrip_withQuotesCommasAndNewlines() {
        val rows = listOf(listOf("a", "b,c", "say \"hi\""), listOf("multi\nline", "", " padded "))
        assertEquals(rows, CsvCodec.decode(CsvCodec.encode(rows)))
    }

    @Test fun decodeIgnoresBomAndBlankLines() {
        assertEquals(listOf(listOf("title"), listOf("x")), CsvCodec.decode("﻿title\r\n\r\nx\r\n"))
    }

    @Test fun taskRoundTrip() {
        val t = task(Quadrant.DELEGATE, due = TODAY, time = LocalTime.of(14, 30), estimate = 45, priority = Priority.HIGH, assignee = "Budi", title = "Rapat, \"penting\"")
            .copy(tags = listOf("kontrak", "rapat"), recurrence = Recurrence.WEEKLY, reminderOffsets = listOf(0, 30),
                subtasks = listOf(Subtask(title = "Agenda", isDone = true), Subtask(title = "Notulen")))
        val text = CsvCodec.encode(listOf(TaskCsvMapper.HEADER, TaskCsvMapper.toRow(t, "Proyek A")))
        val result = TaskCsvMapper.parse(CsvCodec.decode(text), NOW.millis()) as TaskCsvMapper.Result.Ok
        val parsed = result.rows.single()
        assertEquals("Proyek A", parsed.projectName)
        with(parsed.task) {
            assertEquals(t.title, title)
            assertEquals(Quadrant.DELEGATE, quadrant)
            assertEquals(t.dueDate, dueDate)
            assertEquals(t.dueTime, dueTime)
            assertEquals(45, estimatedMinutes)
            assertEquals(Priority.HIGH, priority)
            assertEquals(listOf("kontrak", "rapat"), tags)
            assertEquals(Recurrence.WEEKLY, recurrence)
            assertEquals(listOf(0, 30), reminderOffsets)
            assertEquals("Budi", assignee)
            assertEquals(listOf("Agenda" to true, "Notulen" to false), subtasks.map { it.title to it.isDone })
        }
    }

    @Test fun parse_requiresTitleColumn_andSkipsInvalidRows() {
        assertTrue(TaskCsvMapper.parse(listOf(listOf("name")), 0) is TaskCsvMapper.Result.Error)
        val ok = TaskCsvMapper.parse(listOf(listOf("title", "due_date"), listOf("A", "2026-13-40"), listOf("", ""), listOf("B", "")), 0) as TaskCsvMapper.Result.Ok
        assertEquals(listOf("B"), ok.rows.map { it.task.title })
        assertEquals(2, ok.skipped)
    }

    @Test fun parse_minimalFile_defaultsToQ4() {
        val ok = TaskCsvMapper.parse(listOf(listOf("Title"), listOf("Just a title")), 0) as TaskCsvMapper.Result.Ok
        assertEquals(Quadrant.ELIMINATE, ok.rows.single().task.quadrant)
    }
}
