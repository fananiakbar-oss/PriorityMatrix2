package com.prioritymatrix.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.Task
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.ui.theme.palette
import java.time.LocalDateTime

@Composable
fun TaskCard(
    task: Task,
    project: Project?,
    now: LocalDateTime,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    showQuadrant: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val s = LocalStrings.current
    val p = task.quadrant.palette()
    val overdue = Deadlines.isOverdue(task, now)
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, label = "cardBg"
    )
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = if (selected) 0.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(p.main))
            CompletionCheck(task.isCompleted, p.main, onToggleComplete, Modifier.padding(start = 4.dp))
            Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 10.dp, end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (task.isDemo) { Spacer(Modifier.width(6.dp)); DemoBadge() }
                }
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (showQuadrant) QuadrantBadge(task.quadrant)
                    task.dueDate?.let { d ->
                        val label = buildString {
                            append(s.dueLabel(d, now.toLocalDate()))
                            task.dueTime?.let { append(" · ").append(s.formatTime(it)) }
                        }
                        Meta(Icons.Rounded.Event, label, if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.recurrence != Recurrence.NONE) Meta(Icons.Rounded.Repeat, null, MaterialTheme.colorScheme.onSurfaceVariant)
                    if (task.priority == Priority.HIGH || task.priority == Priority.CRITICAL) {
                        Meta(Icons.Rounded.Flag, s.priorityLabel(task.priority), if (task.priority == Priority.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.subtasks.isNotEmpty()) {
                        Meta(Icons.Rounded.TaskAlt, "${task.subtasks.count { it.isDone }}/${task.subtasks.size}", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.trackedMinutes > 0) Meta(Icons.Rounded.Timer, s.formatMinutes(task.trackedMinutes), MaterialTheme.colorScheme.onSurfaceVariant)
                    if (task.assignee.isNotBlank()) Meta(Icons.Rounded.Person, task.assignee, MaterialTheme.colorScheme.onSurfaceVariant)
                    project?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(Color(it.colorArgb), 8.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(it.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (trailing != null) trailing()
        }
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        if (text != null) {
            Spacer(Modifier.width(3.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
