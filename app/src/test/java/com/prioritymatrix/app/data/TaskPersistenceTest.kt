package com.prioritymatrix.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prioritymatrix.app.data.backup.BackupManager
import com.prioritymatrix.app.data.db.AppDatabase
import com.prioritymatrix.app.data.repository.ProjectRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.data.repository.TimeRepository
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.Subtask
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TaskStatus
import com.prioritymatrix.app.domain.model.TimeEntrySource
import com.prioritymatrix.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private class FakeReminders : ReminderScheduler {
    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()
    override suspend fun schedule(task: Task) { scheduled += task.id }
    override fun cancel(taskId: Long) { cancelled += taskId }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class TaskPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var time: TimeRepository
    private lateinit var projects: ProjectRepository
    private lateinit var reminders: FakeReminders
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneOffset.UTC)
    private val today: LocalDate = LocalDate.now(clock)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        reminders = FakeReminders()
        repo = TaskRepository(db, reminders, clock)
        time = TimeRepository(db)
        projects = ProjectRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private fun newTask(title: String = "Siapkan laporan progres", q: Quadrant = Quadrant.DO_FIRST) = Task(
        title = title, isImportant = q.important, isUrgent = q.urgent, dueDate = today, dueTime = LocalTime.of(16, 0),
        estimatedMinutes = 90, tags = listOf("laporan", "proyek"), reminderOffsets = listOf(30, 0),
        subtasks = listOf(Subtask(title = "Kumpulkan data"), Subtask(title = "Tulis ringkasan", isDone = true))
    )

    @Test fun saveAndReadBack_allFieldsPersist() = runBlocking<Unit> {
        val id = repo.save(newTask())
        val t = repo.getTask(id)!!
        assertEquals("Siapkan laporan progres", t.title)
        assertEquals(Quadrant.DO_FIRST, t.quadrant)
        assertEquals(today, t.dueDate)
        assertEquals(LocalTime.of(16, 0), t.dueTime)
        assertEquals(90, t.estimatedMinutes)
        assertEquals(listOf("laporan", "proyek"), t.tags)
        assertEquals(listOf(0, 30), t.reminderOffsets)
        assertEquals(listOf("Kumpulkan data", "Tulis ringkasan"), t.subtasks.map { it.title })
        assertTrue(t.subtasks[1].isDone)
        assertEquals(clock.millis(), t.createdAt)
        assertTrue(id in reminders.scheduled)
    }

    @Test fun update_replacesSubtasks_andKeepsCreatedAt() = runBlocking<Unit> {
        val id = repo.save(newTask())
        val original = repo.getTask(id)!!
        repo.save(original.copy(title = "Baru", isUrgent = false, subtasks = listOf(Subtask(title = "Satu-satunya"))))
        val t = repo.getTask(id)!!
        assertEquals("Baru", t.title)
        assertEquals(Quadrant.SCHEDULE, t.quadrant)
        assertEquals(listOf("Satu-satunya"), t.subtasks.map { it.title })
        assertEquals(original.createdAt, t.createdAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTask_isRejected() {
        runBlocking { repo.save(newTask(title = " ")) }
    }

    @Test fun completeRecurring_createsNextInstance_andUndoWorks() = runBlocking<Unit> {
        val id = repo.save(newTask().copy(recurrence = Recurrence.DAILY))
        val result = repo.setCompleted(id, true)!!
        val done = repo.getTask(id)!!
        assertEquals(TaskStatus.COMPLETED, done.status)
        assertNotNull(done.completedAt)
        assertTrue(id in reminders.cancelled)
        val next = repo.getTask(result.nextRecurringId!!)!!
        assertEquals(today.plusDays(1), next.dueDate)
        assertFalse(next.isCompleted)
        assertTrue(next.subtasks.none { it.isDone })

        repo.setCompleted(id, false)
        assertNull(repo.getTask(id)!!.completedAt)
    }

    @Test fun moveQuadrant_andRestoreForUndo() = runBlocking<Unit> {
        val id = repo.save(newTask())
        val previous = repo.moveToQuadrant(listOf(id), Quadrant.DELEGATE)
        val moved = repo.getTask(id)!!
        assertEquals(Quadrant.DELEGATE, moved.quadrant)
        assertFalse(moved.isImportant)
        repo.restoreQuadrants(previous)
        assertEquals(Quadrant.DO_FIRST, repo.getTask(id)!!.quadrant)
    }

    @Test fun deleteAndRestore_keepsIdsAndSubtasks() = runBlocking<Unit> {
        val id = repo.save(newTask())
        val snapshot = repo.delete(listOf(id))
        assertNull(repo.getTask(id))
        repo.restore(snapshot)
        val back = repo.getTask(id)!!
        assertEquals(2, back.subtasks.size)
    }

    @Test fun trackedTime_isAggregatedPerTask_withQuadrantSnapshot() = runBlocking<Unit> {
        val id = repo.save(newTask(q = Quadrant.SCHEDULE))
        time.logTime(id, clock.millis(), 25 * 60, TimeEntrySource.FOCUS)
        time.logTime(id, clock.millis(), 10 * 60, TimeEntrySource.MANUAL)
        val t = repo.tasks.first().single { it.id == id }
        assertEquals(35, t.trackedMinutes)
        val entries = time.entries.first()
        assertTrue(entries.all { it.quadrant == Quadrant.SCHEDULE })
    }

    @Test fun deletingProject_keepsTasks() = runBlocking<Unit> {
        val pid = projects.save(Project(name = "Proyek A", colorArgb = 0xFF000000, icon = "work"))
        val id = repo.save(newTask().copy(projectId = pid))
        projects.delete(pid)
        val t = repo.getTask(id)!!
        assertNull(t.projectId)
    }

    @Test fun backupRestore_roundTrip() = runBlocking<Unit> {
        val pid = projects.save(Project(name = "Proyek A", colorArgb = 0xFF123456, icon = "work"))
        val id = repo.save(newTask().copy(projectId = pid))
        time.logTime(id, clock.millis(), 1500, TimeEntrySource.FOCUS)
        val backup = BackupManager(db, repo, projects, clock)
        val json = backup.exportJson()

        repo.delete(listOf(id))
        repo.save(newTask(title = "Akan hilang"))
        backup.restoreJson(json)

        val all = repo.tasks.first()
        assertEquals(listOf("Siapkan laporan progres"), all.map { it.title })
        assertEquals(pid, all.single().projectId)
        assertEquals(25, all.single().trackedMinutes)
        assertEquals(2, all.single().subtasks.size)
    }

    @Test fun restore_rejectsInvalidFile_withoutTouchingData() = runBlocking<Unit> {
        repo.save(newTask())
        val backup = BackupManager(db, repo, projects, clock)
        val failed = runCatching { backup.restoreJson("{\"format\":\"something-else\"}") }.isFailure
        assertTrue(failed)
        assertEquals(1, repo.tasks.first().size)
    }

    @Test fun csvExportImport_roundTrip() = runBlocking<Unit> {
        val pid = projects.save(Project(name = "Proyek A", colorArgb = 0xFF123456, icon = "work"))
        repo.save(newTask().copy(projectId = pid))
        val backup = BackupManager(db, repo, projects, clock)
        val csv = backup.exportCsv()
        val summary = backup.importCsv(csv)
        assertEquals(1, summary.imported)
        val all = repo.tasks.first()
        assertEquals(2, all.size)
        assertTrue(all.all { it.projectId == pid })
    }
}
