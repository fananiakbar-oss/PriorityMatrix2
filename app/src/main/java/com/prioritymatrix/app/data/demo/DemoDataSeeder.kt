package com.prioritymatrix.app.data.demo

import androidx.room.withTransaction
import com.prioritymatrix.app.data.db.AppDatabase
import com.prioritymatrix.app.data.db.toEntity
import com.prioritymatrix.app.data.prefs.AppLanguage
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.Subtask
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TaskStatus
import com.prioritymatrix.app.domain.model.TimeBlock
import com.prioritymatrix.app.domain.model.TimeEntry
import com.prioritymatrix.app.domain.model.TimeEntrySource
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional showcase data. Every row is flagged isDemo = true, shown with a "DEMO" badge,
 * and can be removed in one action without touching the user's own data.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val db: AppDatabase,
    private val taskRepository: TaskRepository,
    private val clock: Clock
) {
    private data class Demo(
        val id: String, val en: String,
        val important: Boolean, val urgent: Boolean,
        val dueInDays: Int?, val time: LocalTime? = null,
        val project: String, val priority: Priority = Priority.MEDIUM,
        val estimate: Int? = null, val doneDaysAgo: Int? = null,
        val subtasks: List<Pair<String, String>> = emptyList(),
        val assignee: String = "", val tags: List<String> = emptyList(),
        val recurrence: Recurrence = Recurrence.NONE, val startInDays: Int? = null,
        val trackedMinutes: Int = 0, val trackedDaysAgo: Int = 0
    )

    private val demos = listOf(
        Demo("Siapkan laporan progres proyek bulanan", "Prepare monthly project progress report", true, true, 0, LocalTime.of(16, 0), "proj",
            Priority.CRITICAL, 120, subtasks = listOf("Kumpulkan data progres lapangan" to "Collect site progress data", "Perbarui kurva-S" to "Update S-curve", "Tulis ringkasan eksekutif" to "Write executive summary"),
            tags = listOf("laporan"), trackedMinutes = 50),
        Demo("Pantau tenggat kontrak subkontraktor", "Monitor subcontractor contractual deadlines", true, true, -1, LocalTime.of(10, 0), "proj", Priority.HIGH, 45, tags = listOf("kontrak")),
        Demo("Siapkan presentasi untuk manajemen", "Prepare management presentation", true, true, 1, LocalTime.of(9, 0), "proj", Priority.HIGH, 90, trackedMinutes = 75, trackedDaysAgo = 1),
        Demo("Evaluasi risiko proyek kuartal depan", "Evaluate project risks for next quarter", true, false, 6, null, "proj", Priority.HIGH, 120,
            subtasks = listOf("Perbarui register risiko" to "Update risk register", "Diskusi dengan tim QS" to "Discuss with QS team"), trackedMinutes = 60, trackedDaysAgo = 2),
        Demo("Tinjau jadwal proyek & jalur kritis", "Review project schedule & critical path", true, false, 4, null, "proj", Priority.MEDIUM, 90, trackedMinutes = 45, trackedDaysAgo = 3),
        Demo("Kursus manajemen proyek (PMP) modul 3", "Project management course (PMP) module 3", true, false, null, null, "learning", Priority.MEDIUM, 60, startInDays = 2, trackedMinutes = 50, trackedDaysAgo = 4),
        Demo("Olahraga pagi 30 menit", "30-minute morning workout", true, false, 0, LocalTime.of(6, 30), "health", Priority.MEDIUM, 30, recurrence = Recurrence.DAILY),
        Demo("Rencanakan liburan keluarga akhir tahun", "Plan year-end family holiday", true, false, 21, null, "family", Priority.LOW, 60),
        Demo("Koordinasi rapat mingguan dengan kontraktor", "Coordinate weekly meeting with contractor", false, true, 0, LocalTime.of(14, 0), "proj", Priority.MEDIUM, 30, assignee = "Rina (Admin Proyek)", trackedMinutes = 25),
        Demo("Balas email undangan vendor", "Reply to vendor invitation emails", false, true, 1, null, "work", Priority.LOW, 20),
        Demo("Pesan ruang rapat untuk audit", "Book meeting room for audit", false, true, 2, null, "work", Priority.LOW, 10, assignee = "Budi"),
        Demo("Rapikan folder dokumen lama", "Tidy up old document folders", false, false, null, null, "work", Priority.LOW, 30),
        Demo("Cek media sosial", "Check social media", false, false, null, null, "personal", Priority.LOW, 15, trackedMinutes = 20, trackedDaysAgo = 1),
        Demo("Kirim notulen rapat koordinasi", "Send coordination meeting minutes", false, true, -2, null, "proj", Priority.MEDIUM, 20, doneDaysAgo = 2),
        Demo("Verifikasi tagihan termin 2", "Verify progress payment invoice #2", true, true, -3, null, "proj", Priority.HIGH, 60, doneDaysAgo = 3, trackedMinutes = 70, trackedDaysAgo = 3),
        Demo("Susun rencana pengembangan diri 2027", "Draft 2027 professional development plan", true, false, -1, null, "learning", Priority.MEDIUM, 45, doneDaysAgo = 1, trackedMinutes = 40, trackedDaysAgo = 1),
        Demo("Medical check-up tahunan", "Annual medical check-up", true, false, 10, LocalTime.of(8, 0), "health", Priority.MEDIUM, 120)
    )

    suspend fun seed(language: AppLanguage) {
        val en = language == AppLanguage.EN
        val today = LocalDate.now(clock)
        val zone: ZoneId = clock.zone
        val now = clock.millis()
        val categories = db.projectDao().getAll().associateBy { it.icon }

        val demoProjectId = db.projectDao().insert(
            Project(
                name = if (en) "Office Tower B Project (demo)" else "Proyek Gedung Kantor B (demo)",
                colorArgb = 0xFFF97316, icon = "build", isDemo = true, createdAt = now
            ).toEntity()
        )
        fun projectFor(key: String): Long? = when (key) {
            "proj" -> demoProjectId
            "work" -> categories["work"]?.id
            "personal" -> categories["person"]?.id
            "family" -> categories["family"]?.id
            "health" -> categories["health"]?.id
            "learning" -> categories["school"]?.id
            else -> null
        }

        db.withTransaction {
            demos.forEachIndexed { index, d ->
                val due = d.dueInDays?.let { today.plusDays(it.toLong()) }
                val completedAt = d.doneDaysAgo?.let { today.minusDays(it.toLong()).atTime(15, 0).atZone(zone).toInstant().toEpochMilli() }
                val created = today.minusDays(5L + index % 4).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
                val task = Task(
                    title = if (en) d.en else d.id,
                    isImportant = d.important, isUrgent = d.urgent,
                    priority = d.priority, dueDate = due, dueTime = if (due != null) d.time else null,
                    startDate = d.startInDays?.let { today.plusDays(it.toLong()) },
                    estimatedMinutes = d.estimate, projectId = projectFor(d.project),
                    status = if (completedAt != null) TaskStatus.COMPLETED else if (d.trackedMinutes > 0) TaskStatus.IN_PROGRESS else TaskStatus.NOT_STARTED,
                    completedAt = completedAt, assignee = d.assignee, tags = d.tags, recurrence = d.recurrence,
                    reminderOffsets = if (due != null && completedAt == null) listOf(30) else emptyList(),
                    isDemo = true, createdAt = created, updatedAt = now
                )
                val id = db.taskDao().insert(task.toEntity())
                db.subtaskDao().insertAll(d.subtasks.mapIndexed { i, (sid, sen) ->
                    Subtask(title = if (en) sen else sid, isDone = i == 0 && d.trackedMinutes > 0).toEntity(id, i)
                })
                if (d.trackedMinutes > 0) {
                    val start = today.minusDays(d.trackedDaysAgo.toLong()).atTime(9 + index % 6, 0).atZone(zone).toInstant().toEpochMilli()
                    db.timeEntryDao().insert(
                        TimeEntry(taskId = id, startedAt = start, durationSeconds = d.trackedMinutes * 60,
                            source = TimeEntrySource.FOCUS, quadrant = Quadrant.of(d.important, d.urgent), isDemo = true).toEntity()
                    )
                }
            }
            // Protected strategic time blocks (Q2) for this week.
            db.timeBlockDao().insertAll(listOf(
                TimeBlock(title = if (en) "Weekly planning" else "Perencanaan mingguan", date = today.plusDays(1), startMinute = 8 * 60, endMinute = 9 * 60, isDemo = true).toEntity(),
                TimeBlock(title = if (en) "Risk review session" else "Sesi tinjauan risiko", date = today.plusDays(2), startMinute = 13 * 60, endMinute = 15 * 60, isDemo = true).toEntity(),
                TimeBlock(title = if (en) "Learning: PMP study" else "Belajar: materi PMP", date = today, startMinute = 19 * 60, endMinute = 20 * 60, isDemo = true).toEntity()
            ))
        }
        taskRepository.rescheduleAllReminders()
    }

    suspend fun clear() {
        db.withTransaction {
            db.timeEntryDao().deleteDemo()
            db.timeBlockDao().deleteDemo()
            db.taskDao().deleteDemo()
            db.projectDao().deleteDemo()
        }
    }
}
