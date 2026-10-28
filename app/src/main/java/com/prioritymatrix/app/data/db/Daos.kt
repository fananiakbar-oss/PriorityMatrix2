package com.prioritymatrix.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate, dueTime, createdAt DESC")
    fun observeAll(): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskWithSubtasks?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskWithSubtasks?

    @Transaction
    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskWithSubtasks>

    @Transaction
    @Query("SELECT * FROM tasks WHERE status != 'COMPLETED' AND isArchived = 0 AND dueDate IS NOT NULL")
    suspend fun getActiveWithDeadline(): List<TaskWithSubtasks>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET isImportant = :important, isUrgent = :urgent, quadrant = :quadrant, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setQuadrant(ids: List<Long>, important: Boolean, urgent: Boolean, quadrant: Int, now: Long)

    @Query("UPDATE tasks SET isArchived = :archived, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<Long>, archived: Boolean, now: Long)

    @Query("UPDATE tasks SET dueDate = :date, updatedAt = :now WHERE id = :id")
    suspend fun setDueDate(id: Long, date: LocalDate?, now: Long)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM tasks WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tasks WHERE isDemo = 1")
    fun observeDemoCount(): Flow<Int>
}

@Dao
interface SubtaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SubtaskEntity>)

    @Update
    suspend fun update(item: SubtaskEntity)

    @Query("UPDATE subtasks SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("SELECT * FROM subtasks")
    suspend fun getAll(): List<SubtaskEntity>

    @Query("DELETE FROM subtasks")
    suspend fun deleteAll()
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY isArchived, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(p: ProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProjectEntity>)

    @Update
    suspend fun update(p: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM projects WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int
}

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries")
    suspend fun getAll(): List<TimeEntryEntity>

    @Query("SELECT * FROM time_entries WHERE taskId = :taskId ORDER BY startedAt DESC")
    fun observeForTask(taskId: Long): Flow<List<TimeEntryEntity>>

    @Query("SELECT taskId AS taskId, SUM(durationSeconds) AS seconds FROM time_entries WHERE taskId IS NOT NULL GROUP BY taskId")
    fun observeTrackedTotals(): Flow<List<TrackedTotal>>

    @Insert
    suspend fun insert(e: TimeEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TimeEntryEntity>)

    @Delete
    suspend fun delete(e: TimeEntryEntity)

    @Query("DELETE FROM time_entries WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM time_entries")
    suspend fun deleteAll()
}

@Dao
interface TimeBlockDao {
    @Query("SELECT * FROM time_blocks ORDER BY date, startMinute")
    fun observeAll(): Flow<List<TimeBlockEntity>>

    @Query("SELECT * FROM time_blocks")
    suspend fun getAll(): List<TimeBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(b: TimeBlockEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TimeBlockEntity>)

    @Query("DELETE FROM time_blocks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM time_blocks WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM time_blocks")
    suspend fun deleteAll()
}

@Dao
abstract class DailyPriorityDao {
    @Query("SELECT * FROM daily_priorities WHERE date = :date ORDER BY position")
    abstract fun observeForDate(date: LocalDate): Flow<List<DailyPriorityEntity>>

    @Query("SELECT * FROM daily_priorities")
    abstract suspend fun getAll(): List<DailyPriorityEntity>

    @Query("DELETE FROM daily_priorities WHERE date = :date")
    abstract suspend fun clearDate(date: LocalDate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<DailyPriorityEntity>)

    @Transaction
    open suspend fun replaceForDate(date: LocalDate, items: List<DailyPriorityEntity>) {
        clearDate(date)
        insertAll(items)
    }

    @Query("DELETE FROM daily_priorities")
    abstract suspend fun deleteAll()
}
