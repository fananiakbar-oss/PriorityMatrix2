package com.prioritymatrix.app.ui.task

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.prefs.SettingsRepository
import com.prioritymatrix.app.data.repository.ProjectRepository
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.domain.logic.TaskValidator
import com.prioritymatrix.app.domain.logic.ValidationError
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.i18n.StringsProvider
import com.prioritymatrix.app.navigation.QuickAddRequest
import com.prioritymatrix.app.ui.common.SnackbarBus
import com.prioritymatrix.app.ui.components.ClassificationToggles
import com.prioritymatrix.app.ui.components.ColorDot
import com.prioritymatrix.app.ui.components.DatePickerModal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    projectRepo: ProjectRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val snackbar: SnackbarBus,
    private val strings: StringsProvider,
    private val clock: Clock
) : ViewModel() {
    val projects: StateFlow<List<Project>> = projectRepo.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val today: LocalDate get() = LocalDate.now(clock)

    /** Returns validation errors; empty list means saved. */
    suspend fun save(title: String, important: Boolean, urgent: Boolean, date: LocalDate?, projectId: Long?): List<ValidationError> {
        val prefs = settings.current()
        val task = Task(
            title = title.trim(), isImportant = important, isUrgent = urgent, dueDate = date, projectId = projectId,
            reminderOffsets = if (date != null && prefs.deadlineReminders) listOf(prefs.defaultReminderOffset) else emptyList()
        )
        val errors = TaskValidator.validate(task)
        if (errors.isNotEmpty()) return errors
        return runCatching { tasks.save(task); snackbar.show(strings.current.taskSaved); emptyList<ValidationError>() }
            .getOrElse { snackbar.show(strings.current.errorGeneric); emptyList() }
    }

    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}

private enum class QuickDue { NONE, TODAY, TOMORROW, CUSTOM }

@Composable
fun QuickAddSheet(
    request: QuickAddRequest,
    onDismiss: () -> Unit,
    onExpand: (title: String, important: Boolean, urgent: Boolean, date: LocalDate?, projectId: Long?) -> Unit,
    vm: QuickAddViewModel = hiltViewModel()
) {
    val s = LocalStrings.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val today = remember { vm.today }

    var title by rememberSaveable { mutableStateOf("") }
    var important by rememberSaveable { mutableStateOf(request.important) }
    var urgent by rememberSaveable { mutableStateOf(request.urgent) }
    var date by remember { mutableStateOf(request.date) }
    var projectId by remember { mutableStateOf(request.projectId) }
    var error by remember { mutableStateOf<ValidationError?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val due = when (date) {
        null -> QuickDue.NONE
        today -> QuickDue.TODAY
        today.plusDays(1) -> QuickDue.TOMORROW
        else -> QuickDue.CUSTOM
    }

    fun submit() {
        vm.launch {
            val errors = vm.save(title, important, urgent, date, projectId)
            if (errors.isEmpty()) {
                scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
            } else error = errors.first()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp)) {
            Text(s.quickAddTitle, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(TaskValidator.MAX_TITLE); error = null },
                placeholder = { Text(s.titleHint) },
                isError = error != null,
                supportingText = error?.let { e -> { Text(s.validation(e)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            Spacer(Modifier.height(12.dp))
            ClassificationToggles(important, urgent, { important = it }, { urgent = it })
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(due == QuickDue.NONE, onClick = { date = null }, label = { Text(s.noDeadline) })
                FilterChip(due == QuickDue.TODAY, onClick = { date = today }, label = { Text(s.today) })
                FilterChip(due == QuickDue.TOMORROW, onClick = { date = today.plusDays(1) }, label = { Text(s.tomorrow) })
                FilterChip(
                    due == QuickDue.CUSTOM, onClick = { pickDate = true },
                    leadingIcon = { Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)) },
                    label = { Text(if (due == QuickDue.CUSTOM) s.formatDate(date!!, "d MMM") else s.pickDate) }
                )
                Box {
                    val p = projects.firstOrNull { it.id == projectId }
                    FilterChip(
                        p != null, onClick = { projectMenu = true },
                        leadingIcon = { if (p != null) ColorDot(Color(p.colorArgb)) else Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)) },
                        label = { Text(p?.name ?: s.noProject) }
                    )
                    DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                        DropdownMenuItem(text = { Text(s.noProject) }, onClick = { projectId = null; projectMenu = false })
                        projects.filter { !it.isArchived }.forEach { pr ->
                            DropdownMenuItem(
                                text = { Text(pr.name) }, leadingIcon = { ColorDot(Color(pr.colorArgb)) },
                                onClick = { projectId = pr.id; projectMenu = false }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onExpand(title, important, urgent, date, projectId) }) {
                    Icon(Icons.Rounded.OpenInFull, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.moreDetails)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { submit() }) { Text(s.save) }
            }
        }
    }

    if (pickDate) DatePickerModal(date, onPick = { date = it }, onDismiss = { pickDate = false })
}
