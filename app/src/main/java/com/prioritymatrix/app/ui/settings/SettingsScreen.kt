package com.prioritymatrix.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.prioritymatrix.app.BuildConfig
import com.prioritymatrix.app.data.prefs.AppLanguage
import com.prioritymatrix.app.data.prefs.ThemeMode
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.notifications.NotificationHelper
import com.prioritymatrix.app.ui.common.AppScaffold
import com.prioritymatrix.app.ui.components.ConfirmDialog
import com.prioritymatrix.app.ui.components.TimePickerModal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val prefs by vm.state.collectAsStateWithLifecycle()
    val demoCount by vm.demoCount.collectAsStateWithLifecycle()
    val p = prefs ?: return
    var notificationsAllowed by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    var exactAllowed by remember { mutableStateOf(vm.canScheduleExact()) }
    var timePicker by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var confirmRemoveDemo by remember { mutableStateOf(false) }

    // Re-check permissions whenever the user returns from system settings.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val n = NotificationHelper.canNotify(context)
            val e = vm.canScheduleExact()
            if (e != exactAllowed) vm.onPermissionChanged()
            notificationsAllowed = n; exactAllowed = e
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val today = LocalDate.now()
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::exportCsv) }
    val backupJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::backupJson) }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importCsv) }
    val restoreJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    AppScaffold(title = s.navSettings) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section(s.profile) {
                var name by remember(p.userName) { mutableStateOf(p.userName) }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40); vm.update { st -> st.copy(userName = name) } },
                    label = { Text(s.yourName) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }

            Section(s.appearance) {
                Label(s.languageLabel)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AppLanguage.entries.forEachIndexed { i, l ->
                        SegmentedButton(p.language == l, onClick = { vm.update { it.copy(language = l) } }, shape = SegmentedButtonDefaults.itemShape(i, AppLanguage.entries.size)) {
                            Text(s.languageName(l))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Label(s.theme)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(p.theme == m, onClick = { vm.update { it.copy(theme = m) } }, shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) {
                            Text(s.themeLabel(m))
                        }
                    }
                }
            }

            Section(s.notifications) {
                if (!notificationsAllowed) {
                    Banner(Icons.Rounded.Warning, s.notificationsDisabled, s.allow) {
                        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                }
                if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Banner(Icons.Rounded.Warning, s.exactAlarmOff, s.openSettings) {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        }
                    }
                }
                ToggleRow(s.deadlineReminders, p.deadlineReminders) { v -> vm.updateNotifications { it.copy(deadlineReminders = v) } }
                if (p.deadlineReminders) {
                    Label(s.defaultReminder)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 10, 30, 60, 1440).forEach { o ->
                            FilterChip(p.defaultReminderOffset == o, onClick = { vm.update { it.copy(defaultReminderOffset = o) } }, label = { Text(s.reminderLabel(o)) })
                        }
                    }
                }
                ToggleRow(s.overdueAlerts, p.overdueAlerts) { v -> vm.updateNotifications { it.copy(overdueAlerts = v) } }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ToggleRow(s.dailyPlanningReminder, p.dailyPlanningEnabled) { v -> vm.updateNotifications { it.copy(dailyPlanningEnabled = v) } }
                if (p.dailyPlanningEnabled) {
                    ValueRow(s.time, s.formatMinuteOfDay(p.dailyPlanningMinute)) { timePicker = "daily" }
                }
                ToggleRow(s.weeklyReviewReminder, p.weeklyReviewEnabled) { v -> vm.updateNotifications { it.copy(weeklyReviewEnabled = v) } }
                if (p.weeklyReviewEnabled) {
                    var dayMenu by remember { mutableStateOf(false) }
                    Box {
                        ValueRow(s.reviewDay, s.dayFull(DayOfWeek.of(p.weeklyReviewDay))) { dayMenu = true }
                        DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                            DayOfWeek.values().forEach { d ->
                                DropdownMenuItem(text = { Text(s.dayFull(d)) }, onClick = { dayMenu = false; vm.updateNotifications { it.copy(weeklyReviewDay = d.value) } })
                            }
                        }
                    }
                    ValueRow(s.time, s.formatMinuteOfDay(p.weeklyReviewMinute)) { timePicker = "weekly" }
                }
            }

            Section(s.pomodoro) {
                Stepper(s.focusLength, s.minutesValue(p.focusMinutes), { vm.updatePomodoro { it.copy(focusMinutes = it.focusMinutes - 5) } }, { vm.updatePomodoro { it.copy(focusMinutes = it.focusMinutes + 5) } }, p.focusMinutes > 5, p.focusMinutes < 180)
                Stepper(s.shortBreakLength, s.minutesValue(p.shortBreakMinutes), { vm.updatePomodoro { it.copy(shortBreakMinutes = it.shortBreakMinutes - 1) } }, { vm.updatePomodoro { it.copy(shortBreakMinutes = it.shortBreakMinutes + 1) } }, p.shortBreakMinutes > 1, p.shortBreakMinutes < 60)
                Stepper(s.longBreakLength, s.minutesValue(p.longBreakMinutes), { vm.updatePomodoro { it.copy(longBreakMinutes = it.longBreakMinutes - 5) } }, { vm.updatePomodoro { it.copy(longBreakMinutes = it.longBreakMinutes + 5) } }, p.longBreakMinutes > 5, p.longBreakMinutes < 90)
                Stepper(s.sessionsBeforeLong, s.sessionsValue(p.sessionsBeforeLongBreak), { vm.updatePomodoro { it.copy(sessionsBeforeLongBreak = it.sessionsBeforeLongBreak - 1) } }, { vm.updatePomodoro { it.copy(sessionsBeforeLongBreak = it.sessionsBeforeLongBreak + 1) } }, p.sessionsBeforeLongBreak > 1, p.sessionsBeforeLongBreak < 12)
                ToggleRow(s.autoStartNext, p.autoStartNext) { v -> vm.update { it.copy(autoStartNext = v) } }
            }

            Section(s.data) {
                ActionRow(Icons.Rounded.FileDownload, s.exportCsv) { exportCsv.launch("priority-matrix-$today.csv") }
                ActionRow(Icons.Rounded.FileUpload, s.importCsv) { importCsv.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/csv", "application/octet-stream")) }
                ActionRow(Icons.Rounded.Backup, s.backup) { backupJson.launch("priority-matrix-backup-$today.json") }
                ActionRow(Icons.Rounded.Restore, s.restore) { restoreJson.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Label(s.demoData)
                if (demoCount > 0) {
                    Text(s.demoLoadedInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ActionRow(Icons.Rounded.DeleteSweep, s.removeDemo) { confirmRemoveDemo = true }
                } else {
                    ActionRow(Icons.Rounded.Science, s.loadDemo) { vm.loadDemo() }
                }
            }

            Section(s.about) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(s.aboutBody, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text("Priority Matrix ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (timePicker) {
        "daily" -> TimePickerModal(LocalTime.of(p.dailyPlanningMinute / 60, p.dailyPlanningMinute % 60),
            onPick = { t -> vm.updateNotifications { it.copy(dailyPlanningMinute = t.hour * 60 + t.minute) } }, onDismiss = { timePicker = null })
        "weekly" -> TimePickerModal(LocalTime.of(p.weeklyReviewMinute / 60, p.weeklyReviewMinute % 60),
            onPick = { t -> vm.updateNotifications { it.copy(weeklyReviewMinute = t.hour * 60 + t.minute) } }, onDismiss = { timePicker = null })
    }
    confirmRestore?.let { uri ->
        ConfirmDialog(s.restoreConfirmTitle, s.restoreConfirmBody, s.restore, onConfirm = { vm.restoreJson(uri) }, onDismiss = { confirmRestore = null }, destructive = true)
    }
    if (confirmRemoveDemo) {
        ConfirmDialog(s.removeDemo + "?", s.demoLoadedInfo, s.delete, onConfirm = { vm.removeDemo() }, onDismiss = { confirmRemoveDemo = false }, destructive = true)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, canMinus: Boolean, canPlus: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus, enabled = canMinus, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Remove, null) }
        Text(value, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton(onClick = onPlus, enabled = canPlus, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Add, null) }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, action: String, onAction: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

