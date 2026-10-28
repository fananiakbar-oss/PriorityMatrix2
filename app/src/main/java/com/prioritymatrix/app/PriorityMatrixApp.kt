package com.prioritymatrix.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.prioritymatrix.app.data.prefs.SettingsRepository
import com.prioritymatrix.app.data.repository.ProjectRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.di.ApplicationScope
import com.prioritymatrix.app.i18n.StringsProvider
import com.prioritymatrix.app.notifications.NotificationHelper
import com.prioritymatrix.app.notifications.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PriorityMatrixApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var strings: StringsProvider
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            runCatching {
                val s = strings.load()
                NotificationHelper.createChannels(this@PriorityMatrixApp, s)
                if (!settings.current().defaultCategoriesSeeded) {
                    projects.seedDefaults(s.defaultCategories)
                    settings.update { it.copy(defaultCategoriesSeeded = true) }
                }
                workScheduler.applyCurrentSettings()
                tasks.rescheduleAllReminders()
            }
        }
        // Keep channel names in the chosen language.
        scope.launch {
            strings.strings.collect { NotificationHelper.createChannels(this@PriorityMatrixApp, it) }
        }
    }
}
