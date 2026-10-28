package com.prioritymatrix.app.data.repository

import com.prioritymatrix.app.data.db.AppDatabase
import com.prioritymatrix.app.data.db.toDomain
import com.prioritymatrix.app.data.db.toEntity
import com.prioritymatrix.app.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    db: AppDatabase,
    private val clock: Clock
) {
    private val dao = db.projectDao()

    val projects: Flow<List<Project>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Project? = dao.get(id)?.toDomain()

    suspend fun save(project: Project): Long {
        require(project.name.isNotBlank()) { "Project name required" }
        return if (project.id == 0L) dao.insert(project.copy(createdAt = clock.millis()).toEntity())
        else { dao.update(project.toEntity()); project.id }
    }

    /** Tasks of a deleted project are kept and become "no project" (FK SET NULL). */
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun findOrCreate(name: String, colorArgb: Long = 0xFF6366F1, icon: String = "folder"): Long {
        val existing = dao.getAll().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        return existing?.id ?: save(Project(name = name.trim(), colorArgb = colorArgb, icon = icon))
    }

    /** Creates the default categories once (localized names). */
    suspend fun seedDefaults(names: List<Pair<String, String>>) {
        if (dao.count() > 0) return
        val colors = listOf(0xFF3B4A86, 0xFF8B5CF6, 0xFFEC4899, 0xFF10B981, 0xFF0EA5E9)
        names.forEachIndexed { i, (name, icon) ->
            dao.insert(Project(name = name, colorArgb = colors[i % colors.size], icon = icon, createdAt = clock.millis()).toEntity())
        }
    }
}
