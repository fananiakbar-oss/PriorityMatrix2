package com.prioritymatrix.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        TimeEntryEntity::class,
        TimeBlockEntity::class,
        DailyPriorityEntity::class
    ],
    version = AppDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun timeBlockDao(): TimeBlockDao
    abstract fun dailyPriorityDao(): DailyPriorityDao

    companion object {
        const val VERSION = 1
        const val NAME = "priority_matrix.db"
    }
}

/**
 * Explicit migrations. The database is NEVER rebuilt destructively, so user data survives updates.
 * When the schema changes: bump [AppDatabase.VERSION], add a Migration(n, n+1) here and keep the
 * exported schema JSON under app/schemas for MigrationTestHelper.
 *
 * Example for a future version 2:
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE tasks ADD COLUMN colorOverride INTEGER")
 *     }
 * }
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
