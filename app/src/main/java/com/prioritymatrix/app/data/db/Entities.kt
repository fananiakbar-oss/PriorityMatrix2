package com.prioritymatrix.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.TaskStatus
import com.prioritymatrix.app.domain.model.TimeEntrySource
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long,
    val icon: String,
    val isArchived: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Long
)

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("projectId"), Index("dueDate"), Index("status"), Index("quadrant")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val isImportant: Boolean,
    val isUrgent: Boolean,
    /** Denormalised from isImportant/isUrgent for queries; always rewritten on save. */
    val quadrant: Int,
    val priority: Priority,
    val startDate: LocalDate?,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val estimatedMinutes: Int?,
    val status: TaskStatus,
    val projectId: Long?,
    val tags: String,
    val notes: String,
    val reminderOffsets: String,
    val assignee: String,
    val recurrence: Recurrence,
    val isArchived: Boolean,
    val isDemo: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isDone: Boolean,
    val position: Int
)

data class TaskWithSubtasks(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val subtasks: List<SubtaskEntity>
)

@Entity(
    tableName = "time_entries",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("taskId"), Index("startedAt")]
)
data class TimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long?,
    val startedAt: Long,
    val durationSeconds: Int,
    val source: TimeEntrySource,
    val quadrant: Quadrant?,
    val isDemo: Boolean
)

@Entity(
    tableName = "time_blocks",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("taskId"), Index("date")]
)
data class TimeBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val taskId: Long?,
    val quadrant: Quadrant,
    val note: String,
    val isDemo: Boolean
)

@Entity(
    tableName = "daily_priorities",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["date", "taskId"], unique = true), Index("taskId")]
)
data class DailyPriorityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val taskId: Long,
    val position: Int
)

data class TrackedTotal(
    @ColumnInfo(name = "taskId") val taskId: Long,
    @ColumnInfo(name = "seconds") val seconds: Long
)
