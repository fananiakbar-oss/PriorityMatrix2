package com.prioritymatrix.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.backup.BackupManager
import com.prioritymatrix.app.data.demo.DemoDataSeeder
import com.prioritymatrix.app.data.prefs.AppSettings
import com.prioritymatrix.app.data.prefs.SettingsRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.focus.FocusTimerManager
import com.prioritymatrix.app.i18n.StringsProvider
import com.prioritymatrix.app.notifications.AlarmReminderScheduler
import com.prioritymatrix.app.notifications.WorkScheduler
import com.prioritymatrix.app.ui.common.SnackbarBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val backup: BackupManager,
    private val demo: DemoDataSeeder,
    private val tasks: TaskRepository,
    private val workScheduler: WorkScheduler,
    private val alarms: AlarmReminderScheduler,
    private val timer: FocusTimerManager,
    private val snackbar: SnackbarBus,
    private val strings: StringsProvider
) : ViewModel() {
    val state: StateFlow<AppSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val demoCount: StateFlow<Int> = tasks.demoCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun canScheduleExact(): Boolean = alarms.canScheduleExact()

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { settings.update(transform) }

    /** Notification-related changes must re-arm alarms and periodic work. */
    fun updateNotifications(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settings.update(transform)
        workScheduler.applyCurrentSettings()
        tasks.rescheduleAllReminders()
    }

    fun updatePomodoro(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settings.update(transform)
        timer.applyDurationsIfIdle()
    }

    fun onPermissionChanged() = viewModelScope.launch { tasks.rescheduleAllReminders() }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { snackbar.show(strings.current.errorGeneric) }
    }

    fun exportCsv(uri: Uri) = io {
        val text = backup.exportCsv()
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(("﻿" + text).toByteArray(Charsets.UTF_8)) } }
        snackbar.show(strings.current.exportDone)
    }

    fun backupJson(uri: Uri) = io {
        val text = backup.exportJson()
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
        snackbar.show(strings.current.exportDone)
    }

    fun importCsv(uri: Uri) = viewModelScope.launch {
        val s = strings.current
        try {
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } ?: ""
            val r = backup.importCsv(text)
            snackbar.show(s.importDone(r.imported, r.skipped))
        } catch (e: Exception) {
            snackbar.show(s.importFailed)
        }
    }

    fun restoreJson(uri: Uri) = viewModelScope.launch {
        val s = strings.current
        try {
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } ?: ""
            backup.restoreJson(text)
            snackbar.show(s.restoreDone)
        } catch (e: Exception) {
            snackbar.show(s.restoreFailed)
        }
    }

    fun loadDemo() = io { demo.seed(strings.current.language); snackbar.show(strings.current.demoLoaded) }
    fun removeDemo() = io { demo.clear(); snackbar.show(strings.current.demoRemoved) }
}
