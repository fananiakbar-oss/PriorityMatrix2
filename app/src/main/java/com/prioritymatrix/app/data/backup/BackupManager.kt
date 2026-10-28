package com.prioritymatrix.app.data.backup

import androidx.room.withTransaction
import com.prioritymatrix.app.data.db.AppDatabase
import com.prioritymatrix.app.data.db.DailyPriorityEntity
import com.prioritymatrix.app.data.db.ProjectEntity
import com.prioritymatrix.app.data.db.SubtaskEntity
import com.prioritymatrix.app.data.db.TaskEntity
import com.prioritymatrix.app.data.db.TimeBlockEntity
import com.prioritymatrix.app.data.db.TimeEntryEntity
import com.prioritymatrix.app.data.db.toDomain
import com.prioritymatrix.app.data.repository.ProjectRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.domain.logic.CsvCodec
import com.prioritymatrix.app.domain.logic.TaskCsvMapper
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.TaskStatus
import com.prioritymatrix.app.domain.model.TimeEntrySource
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

data class ImportSummary(val imported: Int, val skipped: Int)

/**
 * Full JSON backup/restore of every table, plus CSV import/export of tasks.
 * Restore parses and validates the WHOLE file before touching the database, then replaces
 * the data inside one transaction — a broken file can never leave the database half-written.
 */
@Singleton
class BackupManager @Inject constructor(
    private val db: AppDatabase,
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val clock: Clock
) {
    companion object {
        const val FORMAT = "priority-matrix-backup"
        const val FORMAT_VERSION = 1
    }

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("formatVersion", FORMAT_VERSION)
        root.put("schemaVersion", AppDatabase.VERSION)
        root.put("exportedAt", clock.millis())
        root.put("projects", JSONArray().apply {
            db.projectDao().getAll().forEach { p ->
                put(JSONObject().put("id", p.id).put("name", p.name).put("colorArgb", p.colorArgb).put("icon", p.icon)
                    .put("isArchived", p.isArchived).put("isDemo", p.isDemo).put("createdAt", p.createdAt))
            }
        })
        root.put("tasks", JSONArray().apply {
            db.taskDao().getAll().forEach { tw ->
                val t = tw.task
                put(JSONObject().apply {
                    put("id", t.id); put("title", t.title); put("description", t.description)
                    put("isImportant", t.isImportant); put("isUrgent", t.isUrgent); put("priority", t.priority.name)
                    put("startDate", t.startDate?.toString() ?: JSONObject.NULL)
                    put("dueDate", t.dueDate?.toString() ?: JSONObject.NULL)
                    put("dueTime", t.dueTime?.toString() ?: JSONObject.NULL)
                    put("estimatedMinutes", t.estimatedMinutes ?: JSONObject.NULL)
                    put("status", t.status.name); put("projectId", t.projectId ?: JSONObject.NULL)
                    put("tags", t.tags); put("notes", t.notes); put("reminderOffsets", t.reminderOffsets)
                    put("assignee", t.assignee); put("recurrence", t.recurrence.name)
                    put("isArchived", t.isArchived); put("isDemo", t.isDemo)
                    put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
                    put("completedAt", t.completedAt ?: JSONObject.NULL)
                })
            }
        })
        root.put("subtasks", JSONArray().apply {
            db.subtaskDao().getAll().forEach { s ->
                put(JSONObject().put("id", s.id).put("taskId", s.taskId).put("title", s.title).put("isDone", s.isDone).put("position", s.position))
            }
        })
        root.put("timeEntries", JSONArray().apply {
            db.timeEntryDao().getAll().forEach { e ->
                put(JSONObject().put("id", e.id).put("taskId", e.taskId ?: JSONObject.NULL).put("startedAt", e.startedAt)
                    .put("durationSeconds", e.durationSeconds).put("source", e.source.name)
                    .put("quadrant", e.quadrant?.name ?: JSONObject.NULL).put("isDemo", e.isDemo))
            }
        })
        root.put("timeBlocks", JSONArray().apply {
            db.timeBlockDao().getAll().forEach { b ->
                put(JSONObject().put("id", b.id).put("title", b.title).put("date", b.date.toString()).put("startMinute", b.startMinute)
                    .put("endMinute", b.endMinute).put("taskId", b.taskId ?: JSONObject.NULL).put("quadrant", b.quadrant.name)
                    .put("note", b.note).put("isDemo", b.isDemo))
            }
        })
        root.put("dailyPriorities", JSONArray().apply {
            db.dailyPriorityDao().getAll().forEach { d ->
                put(JSONObject().put("id", d.id).put("date", d.date.toString()).put("taskId", d.taskId).put("position", d.position))
            }
        })
        return root.toString(2)
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)
    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }

    /** @throws IllegalArgumentException when the file is not a valid backup. */
    suspend fun restoreJson(json: String) {
        val root = runCatching { JSONObject(json) }.getOrElse { throw IllegalArgumentException("not_json") }
        require(root.optString("format") == FORMAT) { "wrong_format" }
        require(root.optInt("formatVersion") in 1..FORMAT_VERSION) { "unsupported_version" }

        val projectRows = root.getJSONArray("projects").mapObjects { o ->
            ProjectEntity(o.getLong("id"), o.getString("name"), o.getLong("colorArgb"), o.getString("icon"),
                o.optBoolean("isArchived"), o.optBoolean("isDemo"), o.getLong("createdAt"))
        }
        val taskRows = root.getJSONArray("tasks").mapObjects { o ->
            val important = o.getBoolean("isImportant"); val urgent = o.getBoolean("isUrgent")
            TaskEntity(
                id = o.getLong("id"), title = o.getString("title"), description = o.optString("description"),
                isImportant = important, isUrgent = urgent, quadrant = Quadrant.of(important, urgent).number,
                priority = Priority.valueOf(o.getString("priority")),
                startDate = o.optStringOrNull("startDate")?.let(LocalDate::parse),
                dueDate = o.optStringOrNull("dueDate")?.let(LocalDate::parse),
                dueTime = o.optStringOrNull("dueTime")?.let(LocalTime::parse),
                estimatedMinutes = o.optIntOrNull("estimatedMinutes"),
                status = TaskStatus.valueOf(o.getString("status")),
                projectId = o.optLongOrNull("projectId"),
                tags = o.optString("tags"), notes = o.optString("notes"), reminderOffsets = o.optString("reminderOffsets"),
                assignee = o.optString("assignee"), recurrence = Recurrence.valueOf(o.optString("recurrence", "NONE")),
                isArchived = o.optBoolean("isArchived"), isDemo = o.optBoolean("isDemo"),
                createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
                completedAt = o.optLongOrNull("completedAt")
            )
        }
        val subtaskRows = root.getJSONArray("subtasks").mapObjects { o ->
            SubtaskEntity(o.getLong("id"), o.getLong("taskId"), o.getString("title"), o.getBoolean("isDone"), o.getInt("position"))
        }
        val entryRows = root.getJSONArray("timeEntries").mapObjects { o ->
            TimeEntryEntity(o.getLong("id"), o.optLongOrNull("taskId"), o.getLong("startedAt"), o.getInt("durationSeconds"),
                TimeEntrySource.valueOf(o.getString("source")), o.optStringOrNull("quadrant")?.let(Quadrant::valueOf), o.optBoolean("isDemo"))
        }
        val blockRows = root.getJSONArray("timeBlocks").mapObjects { o ->
            TimeBlockEntity(o.getLong("id"), o.getString("title"), LocalDate.parse(o.getString("date")), o.getInt("startMinute"),
                o.getInt("endMinute"), o.optLongOrNull("taskId"), Quadrant.valueOf(o.getString("quadrant")), o.optString("note"), o.optBoolean("isDemo"))
        }
        val dailyRows = root.getJSONArray("dailyPriorities").mapObjects { o ->
            DailyPriorityEntity(o.getLong("id"), LocalDate.parse(o.getString("date")), o.getLong("taskId"), o.getInt("position"))
        }
        // Referential sanity: drop rows pointing to missing parents instead of failing the whole restore.
        val projectIds = projectRows.map { it.id }.toSet()
        val safeTasks = taskRows.map { if (it.projectId != null && it.projectId !in projectIds) it.copy(projectId = null) else it }
        val taskIds = safeTasks.map { it.id }.toSet()

        db.withTransaction {
            db.dailyPriorityDao().deleteAll()
            db.timeBlockDao().deleteAll()
            db.timeEntryDao().deleteAll()
            db.subtaskDao().deleteAll()
            db.taskDao().deleteAll()
            db.projectDao().deleteAll()
            db.projectDao().insertAll(projectRows)
            db.taskDao().insertAll(safeTasks)
            db.subtaskDao().insertAll(subtaskRows.filter { it.taskId in taskIds })
            db.timeEntryDao().insertAll(entryRows.map { if (it.taskId != null && it.taskId !in taskIds) it.copy(taskId = null) else it })
            db.timeBlockDao().insertAll(blockRows.map { if (it.taskId != null && it.taskId !in taskIds) it.copy(taskId = null) else it })
            db.dailyPriorityDao().insertAll(dailyRows.filter { it.taskId in taskIds })
        }
        tasks.rescheduleAllReminders()
    }

    suspend fun exportCsv(): String {
        val projectNames = db.projectDao().getAll().associate { it.id to it.name }
        val totals = db.timeEntryDao().getAll().filter { it.taskId != null }.groupBy { it.taskId!! }
            .mapValues { (_, v) -> v.sumOf { it.durationSeconds.toLong() } }
        val rows = db.taskDao().getAll().map { tw ->
            val t = tw.toDomain(totals[tw.task.id] ?: 0L)
            TaskCsvMapper.toRow(t, t.projectId?.let { projectNames[it] })
        }
        return CsvCodec.encode(listOf(TaskCsvMapper.HEADER) + rows)
    }

    /** Appends tasks from a CSV file. Unknown projects are created. */
    suspend fun importCsv(text: String): ImportSummary {
        return when (val result = TaskCsvMapper.parse(CsvCodec.decode(text), clock.millis())) {
            is TaskCsvMapper.Result.Error -> throw IllegalArgumentException(result.message)
            is TaskCsvMapper.Result.Ok -> {
                var imported = 0
                var skipped = result.skipped
                result.rows.forEach { row ->
                    val projectId = row.projectName?.let { projects.findOrCreate(it) }
                    val ok = runCatching { tasks.save(row.task.copy(projectId = projectId)) }.isSuccess
                    if (ok) imported++ else skipped++
                }
                ImportSummary(imported, skipped)
            }
        }
    }
}
