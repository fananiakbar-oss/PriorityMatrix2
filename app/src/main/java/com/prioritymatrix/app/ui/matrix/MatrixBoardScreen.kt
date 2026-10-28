package com.prioritymatrix.app.ui.matrix

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.domain.logic.SmartPrioritizer
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalNavigator
import com.prioritymatrix.app.ui.common.AppScaffold
import com.prioritymatrix.app.ui.common.TaskActions
import com.prioritymatrix.app.ui.components.MoveToQuadrantSheet
import com.prioritymatrix.app.ui.theme.palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class MatrixBoardViewModel @Inject constructor(
    repo: TaskRepository,
    private val actions: TaskActions,
    private val clock: Clock
) : ViewModel() {
    val byQuadrant: StateFlow<Map<Quadrant, List<Task>>> = repo.tasks.map { all ->
        val now = LocalDateTime.now(clock)
        all.filter { it.isActive }.sortedByDescending { SmartPrioritizer.score(it, now) }.groupBy { it.quadrant }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun now(): LocalDateTime = LocalDateTime.now(clock)
    fun move(task: Task, q: Quadrant) { if (task.quadrant != q) actions.move(listOf(task.id), q) }
}

@Composable
fun MatrixBoardScreen(vm: MatrixBoardViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    val data by vm.byQuadrant.collectAsStateWithLifecycle()
    val now = remember(data) { vm.now() }

    val zones = remember { mutableStateMapOf<Quadrant, Rect>() }
    var boardCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragging by remember { mutableStateOf<Task?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var moveSheetFor by remember { mutableStateOf<Task?>(null) }
    val hovered = dragging?.let { zones.entries.firstOrNull { it.value.contains(dragPos) }?.key }

    AppScaffold(title = s.matrixBoard, subtitle = s.dragHint, onBack = { nav.back() }) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(12.dp).onGloballyPositioned { boardCoords = it }
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Quadrant.DO_FIRST to Quadrant.SCHEDULE, Quadrant.DELEGATE to Quadrant.ELIMINATE).forEach { (a, b) ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(a, b).forEach { q ->
                            DropZone(
                                q = q,
                                tasks = data[q].orEmpty(),
                                now = now,
                                highlighted = hovered == q && dragging?.quadrant != q,
                                draggingId = dragging?.id,
                                modifier = Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { zones[q] = it.boundsInRoot() },
                                onOpen = { nav.toTask(it.id) },
                                onMoveMenu = { moveSheetFor = it },
                                onDragStart = { task, startInRoot ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragging = task; dragPos = startInRoot
                                },
                                onDrag = { delta -> dragPos += delta },
                                onDragEnd = {
                                    val task = dragging
                                    val target = zones.entries.firstOrNull { it.value.contains(dragPos) }?.key
                                    if (task != null && target != null) vm.move(task, target)
                                    dragging = null
                                },
                                onDragCancel = { dragging = null }
                            )
                        }
                    }
                }
            }
            // Floating copy of the dragged task, following the finger.
            dragging?.let { task ->
                val origin = boardCoords?.positionInRoot() ?: Offset.Zero
                val p = (hovered ?: task.quadrant).palette()
                Surface(
                    color = p.container, contentColor = p.onContainer, shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(2.dp, p.main),
                    modifier = Modifier
                        .offset { IntOffset((dragPos.x - origin.x - 60.dp.toPx()).roundToInt(), (dragPos.y - origin.y - 24.dp.toPx()).roundToInt()) }
                        .widthIn(max = 220.dp)
                        .shadow(8.dp, MaterialTheme.shapes.medium)
                ) {
                    Text(task.title, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    moveSheetFor?.let { t ->
        MoveToQuadrantSheet(current = t.quadrant, onPick = { vm.move(t, it) }, onDismiss = { moveSheetFor = null })
    }
}

@Composable
private fun DropZone(
    q: Quadrant,
    tasks: List<Task>,
    now: LocalDateTime,
    highlighted: Boolean,
    draggingId: Long?,
    modifier: Modifier,
    onOpen: (Task) -> Unit,
    onMoveMenu: (Task) -> Unit,
    onDragStart: (Task, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val s = LocalStrings.current
    val p = q.palette()
    val border by animateColorAsState(if (highlighted) p.main else p.container, label = "zoneBorder")
    Surface(color = p.container, contentColor = p.onContainer, shape = MaterialTheme.shapes.large, border = BorderStroke(2.dp, border), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text("${s.quadrantShort(q)} · ${s.quadrantAction(q)}", style = MaterialTheme.typography.titleSmall, color = p.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${tasks.size} · ${s.quadrantTitle(q)}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(6.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tasks.isEmpty()) Text(s.emptyQuadrant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(0.6f))
                tasks.forEach { task ->
                    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val overdue = Deadlines.isOverdue(task, now)
                    Surface(
                        color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small, shadowElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (draggingId == task.id) 0.35f else 1f)
                            .onGloballyPositioned { coords = it }
                            .pointerInput(task.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset -> onDragStart(task, (coords?.positionInRoot() ?: Offset.Zero) + offset) },
                                    onDrag = { change, amount -> change.consume(); onDrag(amount) },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel
                                )
                            }
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpen(task) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                            Icon(Icons.Rounded.DragIndicator, null, Modifier.size(16.dp), tint = p.main)
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(task.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                task.dueDate?.let {
                                    Text(s.dueLabel(it, now.toLocalDate()), style = MaterialTheme.typography.labelSmall,
                                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onMoveMenu(task) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.SwapHoriz, s.moveTo, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
