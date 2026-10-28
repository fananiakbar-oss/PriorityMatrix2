package com.prioritymatrix.app.data.repository

import com.prioritymatrix.app.data.db.AppDatabase
import com.prioritymatrix.app.data.db.DailyPriorityEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanningRepository @Inject constructor(db: AppDatabase) {
    private val dao = db.dailyPriorityDao()

    fun topPriorities(date: LocalDate): Flow<List<Long>> = dao.observeForDate(date).map { l -> l.map { it.taskId } }

    suspend fun setTopPriorities(date: LocalDate, taskIds: List<Long>) {
        dao.replaceForDate(date, taskIds.distinct().take(MAX).mapIndexed { i, id -> DailyPriorityEntity(date = date, taskId = id, position = i) })
    }

    companion object { const val MAX = 3 }
}
