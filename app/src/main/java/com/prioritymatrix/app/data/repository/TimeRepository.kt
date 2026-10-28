package com.prioritymatrix.app.data.repository

import com.prioritymatrix.app.data.db.AppDatabase
import com.prioritymatrix.app.data.db.toDomain
import com.prioritymatrix.app.data.db.toEntity
import com.prioritymatrix.app.domain.model.TimeBlock
import com.prioritymatrix.app.domain.model.TimeEntry
import com.prioritymatrix.app.domain.model.TimeEntrySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeRepository @Inject constructor(private val db: AppDatabase) {
    private val entryDao = db.timeEntryDao()
    private val blockDao = db.timeBlockDao()

    val entries: Flow<List<TimeEntry>> = entryDao.observeAll().map { l -> l.map { it.toDomain() } }
    val blocks: Flow<List<TimeBlock>> = blockDao.observeAll().map { l -> l.map { it.toDomain() } }

    fun entriesForTask(taskId: Long): Flow<List<TimeEntry>> = entryDao.observeForTask(taskId).map { l -> l.map { it.toDomain() } }

    /** Records worked time. The task's quadrant is snapshotted so analytics reflect what the work was at the time. */
    suspend fun logTime(taskId: Long?, startedAt: Long, durationSeconds: Int, source: TimeEntrySource): Long? {
        if (durationSeconds <= 0) return null
        val quadrant = taskId?.let { db.taskDao().get(it)?.toDomain()?.quadrant }
        val linkedId = if (quadrant == null) null else taskId
        return entryDao.insert(
            TimeEntry(taskId = linkedId, startedAt = startedAt, durationSeconds = durationSeconds, source = source, quadrant = quadrant).toEntity()
        )
    }

    suspend fun deleteEntry(entry: TimeEntry) = entryDao.delete(entry.toEntity())

    suspend fun saveBlock(block: TimeBlock): Long {
        require(block.title.isNotBlank() && block.endMinute > block.startMinute) { "Invalid time block" }
        return blockDao.upsert(block.toEntity())
    }

    suspend fun deleteBlock(id: Long) = blockDao.delete(id)
}
