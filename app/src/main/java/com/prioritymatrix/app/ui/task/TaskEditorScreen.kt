package com.prioritymatrix.app.ui.task

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prioritymatrix.app.domain.logic.ProposedChange
import com.prioritymatrix.app.domain.logic.SuggestedAction
import com.prioritymatrix.app.domain.logic.TaskValidator
import com.prioritymatrix.app.domain.logic.ValidationError
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.TaskStatus
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalNavigator
import com.prioritymatrix.app.ui.common.AppScaffold
import com.prioritymatrix.app.ui.components.ClassificationToggles
import com.prioritymatrix.app.ui.components.ColorDot
import com.prioritymatrix.app.ui.components.ConfirmDialog
import com.prioritymatrix.app.ui.components.DatePickerModal
import com.prioritymatrix.app.ui.components.DemoBadge
import com.prioritymatrix.app.ui.components.TimePickerModal
import com.prioritymatrix.app.ui.theme.palette
import java.time.Instant
import java.time.ZoneId

private val REMINDER_OPTIONS = listOf(0, 10, 30, 60, 180, 1440)

@Composable
fun TaskEditorScreen(vm: TaskEditorViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val state by vm.state.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val entries by vm.timeEntries.collectAsStateWithLifecycle()
    val tracked by vm.trackedMinutes.collectAsStateWithLifecycle()
    val f = state.form

    var askDiscard by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var pendingProposal by remember { mutableStateOf<ProposedChange?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var logDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.closed.collect { nav.back() } }
    BackHandler(enabled = state.dirty) { askDiscard = true }
    val goBack: () -> Unit = {
        if (state.dirty) {
            askDiscard = true
        } else {
            nav.back()
        }
    }

    AppScaffold(
        title = if (state.isNew) s.newTask else s.editTask,
        onBack = goBack,
        actions = {
            if (!state.isNew) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, s.more) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(s.duplicate) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; vm.duplicate() })
                        val archived = state.base?.isArchived == true
                        DropdownMenuItem(
                            text = { Text(if (archived) s.unarchive else s.archive) },
                            leadingIcon = { Icon(if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                            onClick = { menu = false; vm.toggleArchive() }
                        )
                        DropdownMenuItem(
                            text = { Text(s.delete, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; askDelete = true }
                        )
                    }
                }
            }
            TextButton(onClick = vm::save) { Text(s.save) }
        }
    ) { padding ->
        if (state.loading) return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.base?.isDemo == true) Row { DemoBadge() }
            val titleError = state.errors.firstOrNull { it == ValidationError.TITLE_EMPTY || it == ValidationError.TITLE_TOO_LONG }
            OutlinedTextField(
                value = f.title, onValueChange = { v -> vm.update { it.copy(title = v.take(TaskValidator.MAX_TITLE)) } },
                label = { Text(s.titleHint) }, isError = titleError != null,
                supportingText = titleError?.let { e -> { Text(s.validation(e)) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = f.description, onValueChange = { v -> vm.update { it.copy(description = v) } },
                label = { Text(s.description) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )

            EditorSection(s.classification) {
                ClassificationToggles(f.important, f.urgent, { v -> vm.update { it.copy(important = v) } }, { v -> vm.update { it.copy(urgent = v) } })
            }

            state.recommendation?.takeIf { it.action != SuggestedAction.NONE && !state.isNew }?.let { rec ->
                val p = com.prioritymatrix.app.domain.model.Quadrant.of(f.important, f.urgent).palette()
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = p.main)
                            Spacer(Modifier.width(8.dp))
                            Text("${s.smartSuggestion}: ${s.suggestedAction(rec.action)}", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        rec.reasons.forEach { r -> Text("• " + s.reason(r, rec.daysUntilDue), style = MaterialTheme.typography.bodySmall) }
                        rec.proposedChange?.let { c ->
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = { pendingProposal = c }) { Text(s.proposedChange(c)) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(s.smartNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            EditorSection(s.schedule) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(s.startDate, f.startDate?.let { s.formatDate(it, "d MMM yyyy") }, Modifier.weight(1f), onClick = { picker = "start" }, onClear = { vm.update { it.copy(startDate = null) } })
                    DateField(s.dueDate, f.dueDate?.let { s.formatDate(it, "d MMM yyyy") }, Modifier.weight(1f), onClick = { picker = "due" }, onClear = { vm.update { it.copy(dueDate = null) } })
                }
                Spacer(Modifier.height(8.dp))
                DateField(
                    s.dueTime, f.dueTime?.let { s.formatTime(it) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Schedule,
                    enabled = f.dueDate != null, onClick = { picker = "time" }, onClear = { vm.update { it.copy(dueTime = null) } }
                )
                state.errors.filter { it == ValidationError.START_AFTER_DUE || it == ValidationError.TIME_WITHOUT_DATE || it == ValidationError.REMINDER_WITHOUT_DATE }
                    .forEach { Text(s.validation(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(12.dp))
                Label(s.recurrence)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Recurrence.entries.forEach { r ->
                        FilterChip(f.recurrence == r, onClick = { vm.update { it.copy(recurrence = r) } }, label = { Text(s.recurrenceLabel(r)) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Label(s.reminders)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REMINDER_OPTIONS.forEach { o ->
                        FilterChip(
                            selected = o in f.reminders, enabled = f.dueDate != null,
                            onClick = { vm.update { it.copy(reminders = if (o in it.reminders) it.reminders - o else it.reminders + o) } },
                            label = { Text(s.reminderLabel(o)) }
                        )
                    }
                }
                if (f.dueDate != null && f.dueTime == null) Text(s.reminderNoTimeNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            EditorSection(s.details) {
                Label(s.priority)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Priority.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = f.priority == p, onClick = { vm.update { it.copy(priority = p) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Priority.entries.size)
                        ) { Text(s.priorityLabel(p), maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Label(s.status)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskStatus.entries.forEach { st ->
                        FilterChip(f.status == st, onClick = { vm.update { it.copy(status = st) } }, label = { Text(s.statusLabel(st)) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Label(s.project)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(f.projectId == null, onClick = { vm.update { it.copy(projectId = null) } }, label = { Text(s.noProject) })
                    projects.filter { !it.isArchived || it.id == f.projectId }.forEach { p ->
                        FilterChip(
                            f.projectId == p.id, onClick = { vm.update { it.copy(projectId = p.id) } },
                            leadingIcon = { ColorDot(Color(p.colorArgb)) }, label = { Text(p.name) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = f.estimate, onValueChange = { v -> vm.update { it.copy(estimate = v.filter(Char::isDigit).take(5)) } },
                    label = { Text(s.estimate) }, singleLine = true,
                    isError = ValidationError.ESTIMATE_INVALID in state.errors,
                    supportingText = if (ValidationError.ESTIMATE_INVALID in state.errors) ({ Text(s.validation(ValidationError.ESTIMATE_INVALID)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = f.assignee, onValueChange = { v -> vm.update { it.copy(assignee = v.take(60)) } }, label = { Text(s.assignee) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = f.tags, onValueChange = { v -> vm.update { it.copy(tags = v) } }, label = { Text(s.tags) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            EditorSection(s.subtasks) {
                f.subtasks.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = d.done, onCheckedChange = { c -> vm.update { form -> form.copy(subtasks = form.subtasks.map { if (it.key == d.key) it.copy(done = c) else it }) } })
                        Text(
                            d.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (d.done) TextDecoration.LineThrough else null
                        )
                        IconButton(onClick = { vm.update { form -> form.copy(subtasks = form.subtasks.filter { it.key != d.key }) } }) { Icon(Icons.Rounded.Close, s.delete) }
                    }
                }
                var newSub by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = newSub, onValueChange = { newSub = it.take(200) }, placeholder = { Text(s.addSubtask) }, singleLine = true,
                    trailingIcon = { IconButton(onClick = { vm.addSubtask(newSub); newSub = "" }) { Icon(Icons.Rounded.Add, s.addSubtask) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.addSubtask(newSub); newSub = "" }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            EditorSection(s.notes) {
                OutlinedTextField(value = f.notes, onValueChange = { v -> vm.update { it.copy(notes = v) } }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }

            if (!state.isNew) {
                EditorSection(s.trackedTime) {
                    val est = f.estimate.toIntOrNull()
                    Text(s.estimateVsActual(est, tracked), style = MaterialTheme.typography.titleSmall)
                    if (est != null && est > 0) {
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (tracked.toFloat() / est).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (tracked > est) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (entries.isEmpty()) Text(s.noTimeLogged, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    entries.take(8).forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (e.source == com.prioritymatrix.app.domain.model.TimeEntrySource.FOCUS) Icons.Rounded.Timer else Icons.Rounded.Event, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            val date = Instant.ofEpochMilli(e.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                            Text("${s.formatDate(date, "d MMM")} · ${s.formatMinutes(e.minutes)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { vm.deleteEntry(e) }) { Icon(Icons.Rounded.Close, s.delete, Modifier.size(18.dp)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { logDialog = true }) { Text(s.logTime) }
                        Button(onClick = { state.base?.id?.let { nav.toFocus(it) } }) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(s.startFocus)
                        }
                    }
                }
            }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(s.save) }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (picker) {
        "start" -> DatePickerModal(f.startDate ?: f.dueDate, onPick = { d -> vm.update { it.copy(startDate = d) } }, onDismiss = { picker = null })
        "due" -> DatePickerModal(f.dueDate, onPick = { d -> vm.update { it.copy(dueDate = d) } }, onDismiss = { picker = null })
        "time" -> TimePickerModal(f.dueTime, onPick = { t -> vm.update { it.copy(dueTime = t) } }, onDismiss = { picker = null })
    }
    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text(s.unsavedChanges) },
            confirmButton = { TextButton(onClick = { askDiscard = false; nav.back() }) { Text(s.discard) } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text(s.keepEditing) } }
        )
    }
    if (askDelete) {
        ConfirmDialog(s.confirmDeleteTitle(1), s.confirmDeleteBody, s.delete, onConfirm = vm::delete, onDismiss = { askDelete = false }, destructive = true)
    }
    pendingProposal?.let { c ->
        ConfirmDialog(s.confirmReclassTitle, s.proposedChangeConfirm(c), s.apply, onConfirm = { vm.applyProposal(c) }, onDismiss = { pendingProposal = null })
    }
    if (logDialog) LogTimeDialog(onDismiss = { logDialog = false }, onLog = vm::logTime)
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun DateField(
    label: String, value: String?, modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Event,
    enabled: Boolean = true, onClick: () -> Unit, onClear: () -> Unit
) {
    AssistChip(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp),
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        label = {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value ?: "—", style = MaterialTheme.typography.bodyMedium)
            }
        },
        trailingIcon = {
            if (value != null) IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, LocalStrings.current.clear, Modifier.size(16.dp))
            }
        }
    )
}

@Composable
private fun LogTimeDialog(onDismiss: () -> Unit, onLog: (Int) -> Unit) {
    val s = LocalStrings.current
    var text by remember { mutableStateOf("25") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.logTime) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.filter(Char::isDigit).take(4) }, label = { Text(s.logTimeMinutes) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toIntOrNull()?.takeIf { it > 0 }?.let(onLog); onDismiss() }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}
