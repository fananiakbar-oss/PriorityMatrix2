package com.prioritymatrix.app.ui.focus

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Task
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.data.repository.TimeRepository
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TimeEntrySource
import com.prioritymatrix.app.focus.FocusPhase
import com.prioritymatrix.app.focus.FocusState
import com.prioritymatrix.app.focus.FocusTimerManager
import com.prioritymatrix.app.focus.TimerStatus
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalNavigator
import com.prioritymatrix.app.ui.common.AppScaffold
import com.prioritymatrix.app.ui.theme.palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val timer: FocusTimerManager,
    tasks: TaskRepository,
    time: TimeRepository,
    private val clock: Clock
) : ViewModel() {
    val state: StateFlow<FocusState> = timer.state
    val activeTasks: StateFlow<List<Task>> = tasks.tasks.map { l -> l.filter { it.isActive }.sortedBy { it.quadrant.number } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Focus minutes recorded today (all sources), from stored time entries. */
    val todayMinutes: StateFlow<Int> = time.entries.map { list ->
        val today = LocalDate.now(clock)
        list.filter { Instant.ofEpochMilli(it.startedAt).atZone(clock.zone).toLocalDate() == today && it.source == TimeEntrySource.FOCUS }
            .sumOf { it.durationSeconds } / 60
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        val id = savedState.get<Long>("taskId")?.takeIf { it > 0 }
        viewModelScope.launch {
            timer.applyDurationsIfIdle()
            if (id != null && timer.state.value.status == TimerStatus.IDLE) timer.selectTask(id)
        }
    }

    fun start() = timer.start()
    fun pause() = timer.pause()
    fun stop(record: Boolean) = timer.stop(record)
    fun skip() = timer.skip()
    fun selectTask(id: Long?) = timer.selectTask(id)
    fun selectPhase(p: FocusPhase) = timer.selectPhase(p)
}

@Composable
fun FocusScreen(vm: FocusViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val st by vm.state.collectAsStateWithLifecycle()
    val tasks by vm.activeTasks.collectAsStateWithLifecycle()
    val todayMinutes by vm.todayMinutes.collectAsStateWithLifecycle()
    var taskMenu by remember { mutableStateOf(false) }
    var askStop by remember { mutableStateOf(false) }
    val task = tasks.firstOrNull { it.id == st.taskId }

    // Android 13+: the running-timer notification needs permission; ask once when the screen opens.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }

    val accent = when (st.phase) {
        FocusPhase.FOCUS -> (task?.quadrant ?: com.prioritymatrix.app.domain.model.Quadrant.SCHEDULE).palette().main
        else -> com.prioritymatrix.app.domain.model.Quadrant.DELEGATE.palette().main
    }
    val progress by animateFloatAsState(st.progress, label = "focusProgress")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    AppScaffold(title = s.focusTimer, onBack = { nav.back() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FocusPhase.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = st.phase == p, enabled = st.status == TimerStatus.IDLE, onClick = { vm.selectPhase(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, FocusPhase.entries.size)
                    ) {
                        Text(when (p) { FocusPhase.FOCUS -> s.phaseFocus; FocusPhase.SHORT_BREAK -> s.phaseShortBreak; FocusPhase.LONG_BREAK -> s.phaseLongBreak }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val arc = Size(size.width - stroke, size.height - stroke)
                    val tl = Offset(stroke / 2, stroke / 2)
                    drawArc(track, -90f, 360f, false, tl, arc, style = Stroke(stroke))
                    drawArc(accent, -90f, 360f * progress, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%02d:%02d".format(st.remainingSeconds / 60, st.remainingSeconds % 60),
                        fontSize = 56.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp
                    )
                    Text(
                        when (st.phase) { FocusPhase.FOCUS -> s.phaseFocus; FocusPhase.SHORT_BREAK -> s.phaseShortBreak; FocusPhase.LONG_BREAK -> s.phaseLongBreak } +
                            if (st.status == TimerStatus.PAUSED) " · ${s.notifPaused}" else "",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Box {
                AssistChip(
                    onClick = { taskMenu = true }, enabled = st.status == TimerStatus.IDLE || st.phase != FocusPhase.FOCUS,
                    leadingIcon = { Icon(Icons.Rounded.Task, null, tint = accent) },
                    label = { Text(task?.let { "${s.focusOn}: ${it.title}" } ?: s.noTaskLinked, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
                DropdownMenu(expanded = taskMenu, onDismissRequest = { taskMenu = false }) {
                    DropdownMenuItem(text = { Text(s.noTaskLinked) }, onClick = { vm.selectTask(null); taskMenu = false })
                    tasks.take(50).forEach { t ->
                        DropdownMenuItem(
                            text = { Text("${s.quadrantShort(t.quadrant)} · ${t.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = { vm.selectTask(t.id); taskMenu = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { askStop = true }, enabled = st.status != TimerStatus.IDLE, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Stop, s.stop)
                }
                FilledIconButton(
                    onClick = { if (st.status == TimerStatus.RUNNING) vm.pause() else vm.start() },
                    modifier = Modifier.size(84.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent)
                ) {
                    Icon(if (st.status == TimerStatus.RUNNING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (st.status == TimerStatus.RUNNING) s.pause else if (st.status == TimerStatus.PAUSED) s.resume else s.start,
                        Modifier.size(40.dp))
                }
                FilledTonalIconButton(onClick = { vm.skip() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, s.skip) }
            }
            Spacer(Modifier.height(28.dp))
            Text(s.sessionsDone(st.completedFocusCount), style = MaterialTheme.typography.titleSmall)
            Text("${s.todaysFocus}: ${s.formatMinutes(todayMinutes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(s.focusBackgroundNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (askStop) {
        val minutes = st.elapsedSeconds / 60
        AlertDialog(
            onDismissRequest = { askStop = false },
            title = { Text(s.stopSaveTitle) },
            text = { Text(if (st.phase == FocusPhase.FOCUS && minutes >= 1) s.stopSaveBody(minutes) else s.stop + "?") },
            confirmButton = { TextButton(onClick = { vm.stop(true); askStop = false }) { Text(if (st.phase == FocusPhase.FOCUS && minutes >= 1) s.stopAndSave else s.stop) } },
            dismissButton = {
                Row {
                    if (st.phase == FocusPhase.FOCUS && minutes >= 1) TextButton(onClick = { vm.stop(false); askStop = false }) { Text(s.stopDiscard) }
                    TextButton(onClick = { askStop = false }) { Text(s.cancel) }
                }
            }
        )
    }
}

