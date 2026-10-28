package com.prioritymatrix.app.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.domain.model.TaskStatus
import com.prioritymatrix.app.i18n.AppStrings
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalNavigator
import com.prioritymatrix.app.navigation.QuickAddRequest
import com.prioritymatrix.app.ui.common.AppScaffold
import com.prioritymatrix.app.ui.common.projectOf
import com.prioritymatrix.app.ui.components.ConfirmDialog
import com.prioritymatrix.app.ui.components.DatePickerModal
import com.prioritymatrix.app.ui.components.EmptyState
import com.prioritymatrix.app.ui.components.MoveToQuadrantSheet
import com.prioritymatrix.app.ui.components.TaskCard
import com.prioritymatrix.app.ui.theme.palette

@Composable
fun TasksScreen(quadrantNumber: Int?, vm: TasksViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val nav = LocalNavigator.current
    val fixed = state.fixedQuadrant
    var moveTarget by remember { mutableStateOf<List<Long>?>(null) }
    var confirmDelete by remember { mutableStateOf<List<Long>?>(null) }
    var rescheduleTask by remember { mutableStateOf<Task?>(null) }
    val selecting = state.selection.isNotEmpty()

    AppScaffold(
        title = if (selecting) s.nSelected(state.selection.size) else fixed?.let { "${s.quadrantShort(it)} · ${s.quadrantAction(it)}" } ?: s.navTasks,
        subtitle = if (selecting) null else fixed?.let { s.quadrantTitle(it) },
        onBack = when {
            selecting -> ({ vm.clearSelection() })
            quadrantNumber != null -> ({ nav.back() })
            else -> null
        },
        actions = {
            if (selecting) {
                IconButton(onClick = vm::selectAll) { Icon(Icons.Rounded.SelectAll, s.selectAll) }
            } else {
                SortMenu(state.filters.sort) { opt -> vm.updateFilters { it.copy(sort = opt) } }
            }
        },
        floatingActionButton = {
            if (!selecting) ExtendedFloatingActionButton(
                onClick = { nav.openQuickAdd(QuickAddRequest(important = fixed?.important ?: true, urgent = fixed?.urgent ?: false)) },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(s.addTask) }
            )
        },
        bottomBar = {
            AnimatedVisibility(selecting) {
                BottomAppBar {
                    val ids = state.selection.toList()
                    val tab = state.filters.tab
                    if (tab != TaskTab.COMPLETED) IconButton(onClick = vm::completeSelected) { Icon(Icons.Rounded.DoneAll, s.markComplete) }
                    IconButton(onClick = { moveTarget = ids }) { Icon(Icons.Rounded.OpenWith, s.moveTo) }
                    if (tab == TaskTab.ARCHIVED) IconButton(onClick = { vm.archive(ids, false) }) { Icon(Icons.Rounded.Unarchive, s.unarchive) }
                    else IconButton(onClick = { vm.archive(ids, true) }) { Icon(Icons.Rounded.Archive, s.archive) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { confirmDelete = ids }) { Icon(Icons.Rounded.Delete, s.delete, tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (fixed != null) QuadrantHint(fixed)
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = { q -> vm.updateFilters { it.copy(query = q) } },
                placeholder = { Text(s.search) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (state.filters.query.isNotEmpty()) IconButton(onClick = { vm.updateFilters { it.copy(query = "") } }) { Icon(Icons.Rounded.Close, s.clear) }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            FilterRow(state, vm)
            PrimaryTabRow(selectedTabIndex = state.filters.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                TaskTab.entries.forEach { tab ->
                    Tab(
                        selected = state.filters.tab == tab,
                        onClick = { vm.clearSelection(); vm.updateFilters { it.copy(tab = tab) } },
                        text = { Text(when (tab) { TaskTab.ACTIVE -> s.tabActive; TaskTab.COMPLETED -> s.tabCompleted; TaskTab.ARCHIVED -> s.tabArchived }) }
                    )
                }
            }
            if (state.tasks.isEmpty()) {
                if (!state.hasAnyTasks) EmptyState(Icons.Rounded.Inbox, s.noTasksTitle, s.noTasksBody)
                else EmptyState(Icons.Rounded.SearchOff, s.noResultsTitle, s.noResultsBody,
                    actionLabel = if (state.filters.activeFilterCount > 0) s.resetFilters else null, onAction = { vm.resetFilters() })
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        SwipeableTask(
                            task = task,
                            enabled = !selecting,
                            onComplete = { vm.toggleComplete(task) },
                            onDelete = { vm.delete(listOf(task.id)) },
                            modifier = Modifier.animateItem()
                        ) {
                            TaskCard(
                                task = task,
                                project = task.projectOf(state.projects),
                                now = state.now,
                                selected = task.id in state.selection,
                                showQuadrant = fixed == null,
                                onClick = { if (selecting) vm.toggleSelection(task.id) else nav.toTask(task.id) },
                                onLongClick = { vm.toggleSelection(task.id) },
                                onToggleComplete = { vm.toggleComplete(task) },
                                trailing = {
                                    if (!selecting) TaskOverflow(
                                        task,
                                        onMove = { moveTarget = listOf(task.id) },
                                        onDuplicate = { vm.duplicate(task.id) },
                                        onReschedule = { rescheduleTask = task },
                                        onArchive = { vm.archive(listOf(task.id), !task.isArchived) },
                                        onDelete = { confirmDelete = listOf(task.id) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    moveTarget?.let { ids ->
        val current = state.tasks.firstOrNull { it.id == ids.firstOrNull() }?.quadrant
        MoveToQuadrantSheet(current = if (ids.size == 1) current else null, onPick = { vm.move(ids, it) }, onDismiss = { moveTarget = null })
    }
    confirmDelete?.let { ids ->
        ConfirmDialog(s.confirmDeleteTitle(ids.size), s.confirmDeleteBody, s.delete, onConfirm = { vm.delete(ids) }, onDismiss = { confirmDelete = null }, destructive = true)
    }
    rescheduleTask?.let { t ->
        DatePickerModal(t.dueDate, onPick = { vm.reschedule(t, it) }, onDismiss = { rescheduleTask = null })
    }
}

@Composable
private fun QuadrantHint(q: Quadrant) {
    val p = q.palette()
    Surface(color = p.container, contentColor = p.onContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(MaterialTheme.shapes.extraSmall).background(p.main))
            Spacer(Modifier.width(10.dp))
            Text(LocalStrings.current.quadrantHint(q), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SwipeableTask(task: Task, enabled: Boolean, onComplete: () -> Unit, onDelete: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> { onComplete(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.35f }
    )
    val doneColor = task.quadrant.palette().main
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            val dir = state.dismissDirection
            val (color, icon, align) = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(doneColor, Icons.Rounded.Done, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.error, Icons.Rounded.Delete, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, Icons.Rounded.Done, Alignment.Center)
            }
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(color).padding(horizontal = 20.dp),
                contentAlignment = align
            ) { if (dir != SwipeToDismissBoxValue.Settled) Icon(icon, null, tint = Color.White) }
        }
    ) { content() }
}

@Composable
private fun TaskOverflow(task: Task, onMove: () -> Unit, onDuplicate: () -> Unit, onReschedule: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, s.more) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(s.moveTo) }, leadingIcon = { Icon(Icons.Rounded.OpenWith, null) }, onClick = { open = false; onMove() })
            DropdownMenuItem(text = { Text(s.reschedule) }, leadingIcon = { Icon(Icons.Rounded.EventRepeat, null) }, onClick = { open = false; onReschedule() })
            DropdownMenuItem(text = { Text(s.duplicate) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { open = false; onDuplicate() })
            DropdownMenuItem(
                text = { Text(if (task.isArchived) s.unarchive else s.archive) },
                leadingIcon = { Icon(if (task.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                onClick = { open = false; onArchive() }
            )
            DropdownMenuItem(text = { Text(s.delete, color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
private fun SortMenu(current: SortOption, onPick: (SortOption) -> Unit) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, s.sort) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOption.entries.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(sortLabel(s, opt)) },
                    trailingIcon = { if (opt == current) Icon(Icons.Rounded.Check, null) },
                    onClick = { open = false; onPick(opt) }
                )
            }
        }
    }
}

private fun sortLabel(s: AppStrings, o: SortOption) = when (o) {
    SortOption.SMART -> s.sortSmart
    SortOption.DUE -> s.sortDue
    SortOption.PRIORITY -> s.sortPriority
    SortOption.QUADRANT -> s.sortQuadrant
    SortOption.CREATED -> s.sortCreated
    SortOption.TITLE -> s.sortTitle
}

@Composable
private fun FilterRow(state: TasksUiState, vm: TasksViewModel) {
    val s = LocalStrings.current
    val f = state.filters
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.fixedQuadrant == null) {
            MultiFilter(
                label = s.filterQuadrant, selected = f.quadrants, options = Quadrant.entries,
                optionLabel = { "${s.quadrantShort(it)} · ${s.quadrantAction(it)}" },
                onChange = { v -> vm.updateFilters { it.copy(quadrants = v) } }
            )
        }
        SingleFilter(
            label = s.filterDeadline, selected = f.deadline, options = DeadlineFilter.entries,
            optionLabel = {
                when (it) {
                    DeadlineFilter.OVERDUE -> s.deadlineOverdue; DeadlineFilter.TODAY -> s.deadlineToday
                    DeadlineFilter.WEEK -> s.deadlineWeek; DeadlineFilter.NONE -> s.deadlineNone
                }
            },
            onChange = { v -> vm.updateFilters { it.copy(deadline = v) } }
        )
        SingleFilter(
            label = s.filterProject, selected = state.projectList.firstOrNull { it.id == f.projectId },
            options = state.projectList, optionLabel = { it.name },
            onChange = { v -> vm.updateFilters { it.copy(projectId = v?.id) } }
        )
        MultiFilter(
            label = s.filterStatus, selected = f.statuses, options = TaskStatus.entries.filter { it != TaskStatus.COMPLETED },
            optionLabel = { s.statusLabel(it) }, onChange = { v -> vm.updateFilters { it.copy(statuses = v) } }
        )
        MultiFilter(
            label = s.filterPriority, selected = f.priorities, options = Priority.entries.reversed(),
            optionLabel = { s.priorityLabel(it) }, onChange = { v -> vm.updateFilters { it.copy(priorities = v) } }
        )
        if (state.assignees.isNotEmpty()) {
            SingleFilter(
                label = s.filterAssignee, selected = f.assignee, options = state.assignees, optionLabel = { it },
                onChange = { v -> vm.updateFilters { it.copy(assignee = v) } }
            )
        }
        if (f.activeFilterCount > 0) TextButton(onClick = vm::resetFilters) { Text(s.resetFilters) }
    }
}

@Composable
private fun <T> MultiFilter(label: String, selected: Set<T>, options: List<T>, optionLabel: (T) -> String, onChange: (Set<T>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected.isNotEmpty(),
            onClick = { open = true },
            label = { Text(if (selected.isEmpty()) label else "$label · ${selected.size}") },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(optionLabel(o)) },
                    leadingIcon = { if (o in selected) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                    onClick = { onChange(if (o in selected) selected - o else selected + o) }
                )
            }
        }
    }
}

@Composable
private fun <T> SingleFilter(label: String, selected: T?, options: List<T>, optionLabel: (T) -> String, onChange: (T?) -> Unit) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected?.let { "$label · ${optionLabel(it)}" } ?: label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(s.all) }, onClick = { open = false; onChange(null) })
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(optionLabel(o)) },
                    trailingIcon = { if (o == selected) Icon(Icons.Rounded.Check, null) },
                    onClick = { open = false; onChange(o) }
                )
            }
        }
    }
}
