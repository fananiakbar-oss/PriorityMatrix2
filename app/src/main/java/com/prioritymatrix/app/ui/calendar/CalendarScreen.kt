package com.prioritymatrix.app.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TimeBlock
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalNavigator
import com.prioritymatrix.app.navigation.QuickAddRequest
import com.prioritymatrix.app.ui.common.AppScaffold
import com.prioritymatrix.app.ui.common.projectOf
import com.prioritymatrix.app.ui.components.ConfirmDialog
import com.prioritymatrix.app.ui.components.DatePickerModal
import com.prioritymatrix.app.ui.components.InlineEmpty
import com.prioritymatrix.app.ui.components.SectionHeader
import com.prioritymatrix.app.ui.components.TaskCard
import com.prioritymatrix.app.ui.components.TimePickerModal
import com.prioritymatrix.app.ui.theme.palette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

@Composable
fun CalendarScreen(vm: CalendarViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val st by vm.state.collectAsStateWithLifecycle()
    var moveTask by remember { mutableStateOf<Task?>(null) }
    var editBlock by remember { mutableStateOf<TimeBlock?>(null) }

    AppScaffold(
        title = s.navCalendar,
        subtitle = s.longDate(st.selected),
        actions = {
            IconButton(onClick = vm::goToday) { Icon(Icons.Rounded.Today, s.goToday) }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = {
                    editBlock = TimeBlock(title = "", date = st.selected, startMinute = 9 * 60, endMinute = 10 * 60)
                }) { Icon(Icons.Rounded.ViewTimeline, s.addTimeBlock) }
                ExtendedFloatingActionButton(
                    onClick = { nav.openQuickAdd(QuickAddRequest(date = st.selected)) },
                    icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(s.addTask) }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                CalendarMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = st.mode == m, onClick = { vm.setMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, CalendarMode.entries.size)
                    ) { Text(when (m) { CalendarMode.MONTH -> s.month; CalendarMode.WEEK -> s.week; CalendarMode.DAY -> s.day }) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }
                Text(
                    when (st.mode) {
                        CalendarMode.MONTH -> s.monthTitle(st.selected)
                        CalendarMode.WEEK -> {
                            val start = st.selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            "${s.formatDate(start, "d MMM")} – ${s.formatDate(start.plusDays(6), "d MMM yyyy")}"
                        }
                        CalendarMode.DAY -> s.formatDate(st.selected, "EEEE, d MMM")
                    },
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
            }
            when (st.mode) {
                CalendarMode.MONTH -> {
                    MonthGrid(st, onSelect = vm::select)
                    Agenda(st, Modifier.weight(1f), onOpen = { nav.toTask(it.id) }, onToggle = vm::toggleComplete, onMove = { moveTask = it }, onBlock = { editBlock = it })
                }
                CalendarMode.WEEK -> {
                    WeekStrip(st, onSelect = vm::select)
                    Agenda(st, Modifier.weight(1f), onOpen = { nav.toTask(it.id) }, onToggle = vm::toggleComplete, onMove = { moveTask = it }, onBlock = { editBlock = it })
                }
                CalendarMode.DAY -> DayTimeline(st, Modifier.weight(1f), onOpen = { nav.toTask(it.id) }, onBlock = { editBlock = it }, onMove = { moveTask = it })
            }
        }
    }

    moveTask?.let { t -> DatePickerModal(t.dueDate ?: st.selected, onPick = { vm.moveDeadline(t, it) }, onDismiss = { moveTask = null }) }
    editBlock?.let { b ->
        TimeBlockDialog(b, st.activeTasks, onDismiss = { editBlock = null }, onSave = { vm.saveBlock(it); editBlock = null }, onDelete = { vm.deleteBlock(b.id); editBlock = null })
    }
}

@Composable
private fun MonthGrid(st: CalendarState, onSelect: (LocalDate) -> Unit) {
    val s = LocalStrings.current
    val ym = YearMonth.from(st.selected)
    val first = ym.atDay(1)
    val gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = ((java.time.temporal.ChronoUnit.DAYS.between(gridStart, ym.atEndOfMonth()) / 7) + 1).toInt()
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row {
            DayOfWeek.values().forEach { d ->
                Text(s.dayShort(d), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        for (w in 0 until weeks) {
            Row {
                for (i in 0..6) {
                    val date = gridStart.plusDays((w * 7 + i).toLong())
                    val inMonth = date.month == ym.month
                    val tasks = st.tasksByDate[date].orEmpty()
                    val blocks = st.blocksByDate[date].orEmpty()
                    val selected = date == st.selected
                    val isToday = date == st.today
                    Column(
                        Modifier.weight(1f).aspectRatio(0.95f).padding(2.dp).clip(MaterialTheme.shapes.small)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onSelect(date) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape)
                                .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    !inMonth -> MaterialTheme.colorScheme.outline
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            tasks.filter { !it.isCompleted }.map { it.quadrant }.distinct().sortedBy { it.number }.take(3).forEach { q ->
                                Box(Modifier.size(5.dp).clip(CircleShape).background(q.palette().main))
                            }
                            if (blocks.isNotEmpty()) Box(Modifier.size(width = 7.dp, height = 3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(st: CalendarState, onSelect: (LocalDate) -> Unit) {
    val s = LocalStrings.current
    val start = st.selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0..6) {
            val d = start.plusDays(i.toLong())
            val tasks = st.tasksByDate[d].orEmpty().filter { !it.isCompleted }
            val selected = d == st.selected
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.medium, shadowElevation = if (selected) 0.dp else 1.dp,
                border = if (d == st.today && !selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { onSelect(d) }
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.dayShort(d.dayOfWeek), style = MaterialTheme.typography.labelSmall)
                    Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Quadrant.entries.forEach { q ->
                            val n = tasks.count { it.quadrant == q }
                            if (n > 0) Box(Modifier.size(width = (6 + n * 4).coerceAtMost(28).dp, height = 4.dp).clip(CircleShape).background(q.palette().main))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Agenda(st: CalendarState, modifier: Modifier, onOpen: (Task) -> Unit, onToggle: (Task) -> Unit, onMove: (Task) -> Unit, onBlock: (TimeBlock) -> Unit) {
    val s = LocalStrings.current
    val tasks = st.tasksByDate[st.selected].orEmpty()
    val starts = st.startsByDate[st.selected].orEmpty()
    val blocks = st.blocksByDate[st.selected].orEmpty()
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionHeader(s.formatDate(st.selected, "EEEE, d MMMM")) }
        if (tasks.isEmpty() && blocks.isEmpty() && starts.isEmpty()) item { InlineEmpty(s.noTasksOnDay) }
        items(blocks, key = { "b${it.id}" }) { b -> BlockCard(b) { onBlock(b) } }
        items(tasks + starts, key = { "t${it.id}_${it.dueDate == st.selected}" }) { t ->
            TaskCard(
                t, t.projectOf(st.projects), st.now, onClick = { onOpen(t) }, onToggleComplete = { onToggle(t) },
                trailing = { IconButton(onClick = { onMove(t) }) { Icon(Icons.Rounded.EditCalendar, s.moveDeadline) } }
            )
        }
    }
}

@Composable
private fun BlockCard(b: TimeBlock, onClick: () -> Unit) {
    val s = LocalStrings.current
    val p = b.quadrant.palette()
    Surface(color = p.container, contentColor = p.onContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ViewTimeline, null, tint = p.main)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.formatMinuteOfDay(b.startMinute)} – ${s.formatMinuteOfDay(b.endMinute)} · ${s.timeBlock} · ${s.quadrantShort(b.quadrant)}", style = MaterialTheme.typography.labelSmall)
            }
            if (b.isDemo) com.prioritymatrix.app.ui.components.DemoBadge()
        }
    }
}

@Composable
private fun DayTimeline(st: CalendarState, modifier: Modifier, onOpen: (Task) -> Unit, onBlock: (TimeBlock) -> Unit, onMove: (Task) -> Unit) {
    val s = LocalStrings.current
    val tasks = st.tasksByDate[st.selected].orEmpty()
    val untimed = tasks.filter { it.dueTime == null }
    val timed = tasks.filter { it.dueTime != null }
    val blocks = st.blocksByDate[st.selected].orEmpty()
    val startHour = 6
    val hourHeight = 56.dp
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (untimed.isNotEmpty()) {
            SectionHeader(s.allDay)
            untimed.forEach { t ->
                TaskCard(t, t.projectOf(st.projects), st.now, onClick = { onOpen(t) }, onToggleComplete = {}, modifier = Modifier.padding(bottom = 6.dp),
                    trailing = { IconButton(onClick = { onMove(t) }) { Icon(Icons.Rounded.EditCalendar, s.moveDeadline) } })
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(hourHeight * (24 - startHour)).padding(bottom = 8.dp)) {
            val laneStart = 52.dp
            Column {
                for (h in startHour until 24) {
                    Row(Modifier.height(hourHeight)) {
                        Text("%02d:00".format(h), Modifier.width(laneStart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
            }
            fun top(minute: Int) = hourHeight * ((minute - startHour * 60).coerceAtLeast(0) / 60f)
            val laneWidth = maxWidth - laneStart
            blocks.forEach { b ->
                val p = b.quadrant.palette()
                val h = (hourHeight * (b.durationMinutes / 60f)).coerceAtLeast(28.dp)
                Surface(
                    color = p.container, contentColor = p.onContainer, shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, p.main),
                    modifier = Modifier.offset(x = laneStart, y = top(b.startMinute)).width(laneWidth * 0.55f).height(h)
                        .clip(MaterialTheme.shapes.small).clickable { onBlock(b) }
                ) {
                    Column(Modifier.padding(6.dp)) {
                        Text(b.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.formatMinuteOfDay(b.startMinute)}–${s.formatMinuteOfDay(b.endMinute)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            timed.forEach { t ->
                val p = t.quadrant.palette()
                val minute = t.dueTime!!.hour * 60 + t.dueTime.minute
                Surface(
                    color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small, shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, p.main),
                    modifier = Modifier.offset(x = laneStart + laneWidth * 0.58f, y = top(minute) - 14.dp).width(laneWidth * 0.42f)
                        .clip(MaterialTheme.shapes.small).clickable { onOpen(t) }
                ) {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(p.main))
                        Spacer(Modifier.width(4.dp))
                        Text("${s.formatTime(t.dueTime)} ${t.title}", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (st.selected == st.today) {
                val nowMinute = st.now.hour * 60 + st.now.minute
                if (nowMinute >= startHour * 60) {
                    Box(Modifier.offset(x = laneStart, y = top(nowMinute)).width(laneWidth).height(2.dp).background(MaterialTheme.colorScheme.error))
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun TimeBlockDialog(block: TimeBlock, tasks: List<Task>, onDismiss: () -> Unit, onSave: (TimeBlock) -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    var b by remember { mutableStateOf(block) }
    var picker by remember { mutableStateOf<String?>(null) }
    var taskMenu by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(false) }
    val invalid = b.endMinute <= b.startMinute
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (block.id == 0L) s.newTimeBlock else s.timeBlock) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.blockHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = b.title, onValueChange = { b = b.copy(title = it.take(120)) }, label = { Text(s.blockTitle) }, singleLine = true)
                AssistChip(onClick = { date = true }, label = { Text(s.formatDate(b.date, "EEE, d MMM yyyy")) }, leadingIcon = { Icon(Icons.Rounded.EventAvailable, null) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { picker = "start" }, label = { Text("${s.blockStart} ${s.formatMinuteOfDay(b.startMinute)}") })
                    AssistChip(onClick = { picker = "end" }, label = { Text("${s.blockEnd} ${s.formatMinuteOfDay(b.endMinute)}") })
                }
                if (invalid) Text(s.blockInvalid, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(s.blockColor, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Quadrant.entries.forEach { q ->
                        FilterChip(b.quadrant == q, onClick = { b = b.copy(quadrant = q) }, label = { Text(s.quadrantShort(q)) },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(q.palette().main)) })
                    }
                }
                Box {
                    val linked = tasks.firstOrNull { it.id == b.taskId }
                    AssistChip(onClick = { taskMenu = true }, label = { Text(linked?.title ?: s.blockLinkTask, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    DropdownMenu(expanded = taskMenu, onDismissRequest = { taskMenu = false }) {
                        DropdownMenuItem(text = { Text(s.none) }, onClick = { b = b.copy(taskId = null); taskMenu = false })
                        tasks.sortedBy { if (it.quadrant == Quadrant.SCHEDULE) 0 else it.quadrant.number }.take(40).forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(t.quadrant.palette().main)) },
                                onClick = {
                                    b = b.copy(taskId = t.id, quadrant = t.quadrant, title = b.title.ifBlank { t.title })
                                    taskMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !invalid && b.title.isNotBlank(), onClick = { onSave(b) }) { Text(s.save) } },
        dismissButton = {
            Row {
                if (block.id != 0L) TextButton(onClick = { askDelete = true }) { Text(s.delete, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(s.cancel) }
            }
        }
    )
    when (picker) {
        "start" -> TimePickerModal(LocalTime.of(b.startMinute / 60, b.startMinute % 60), onPick = { t -> b = b.copy(startMinute = t.hour * 60 + t.minute) }, onDismiss = { picker = null })
        "end" -> TimePickerModal(LocalTime.of(b.endMinute / 60 % 24, b.endMinute % 60), onPick = { t -> b = b.copy(endMinute = t.hour * 60 + t.minute) }, onDismiss = { picker = null })
    }
    if (date) DatePickerModal(b.date, onPick = { b = b.copy(date = it) }, onDismiss = { date = false })
    if (askDelete) ConfirmDialog(s.delete + "?", b.title, s.delete, onConfirm = onDelete, onDismiss = { askDelete = false }, destructive = true)
}
