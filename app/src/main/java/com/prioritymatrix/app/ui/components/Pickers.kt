package com.prioritymatrix.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.ui.theme.palette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun DatePickerModal(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(s.select) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    ) { DatePicker(state = state) }
}

@Composable
fun TimePickerModal(initial: LocalTime?, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val t = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text(s.select) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } }
    )
}

/** Explicit alternative to drag & drop: pick the target quadrant. */
@Composable
fun MoveToQuadrantSheet(current: Quadrant?, onPick: (Quadrant) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(s.moveTo, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            listOf(Quadrant.DO_FIRST to Quadrant.SCHEDULE, Quadrant.DELEGATE to Quadrant.ELIMINATE).forEach { (a, b) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    listOf(a, b).forEach { q ->
                        val p = q.palette()
                        Surface(
                            color = p.container, contentColor = p.onContainer, shape = MaterialTheme.shapes.large,
                            border = if (q == current) BorderStroke(2.dp, p.main) else null,
                            modifier = Modifier.weight(1f).aspectRatio(1.6f).clip(MaterialTheme.shapes.large)
                                .clickable(role = Role.Button) { onPick(q); onDismiss() }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(s.quadrantShort(q), style = MaterialTheme.typography.labelLarge, color = p.main)
                                Text(s.quadrantAction(q), style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.weight(1f))
                                Text(s.quadrantTitle(q), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The two independent criteria. The quadrant preview updates instantly. */
@Composable
fun ClassificationToggles(important: Boolean, urgent: Boolean, onImportant: (Boolean) -> Unit, onUrgent: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val q = Quadrant.of(important, urgent)
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleCard(s.important, s.importantDesc, important, if (important) Icons.Rounded.Star else Icons.Rounded.StarOutline, onImportant, Modifier.weight(1f))
            ToggleCard(s.urgent, s.urgentDesc, urgent, if (urgent) Icons.Rounded.Bolt else Icons.Rounded.Schedule, onUrgent, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(targetState = q, label = "quadrantPreview") { target ->
            val p = target.palette()
            Surface(color = p.container, contentColor = p.onContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = p.main, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${s.quadrantShort(target)} · ${s.quadrantAction(target)}", style = MaterialTheme.typography.titleSmall)
                        Text(s.quadrantTitle(target), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleCard(label: String, desc: String, checked: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onChange: (Boolean) -> Unit, modifier: Modifier) {
    val container by animateColorAsState(if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "toggleBg")
    Surface(
        color = container, shape = MaterialTheme.shapes.medium,
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Switch) { onChange(!checked) }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(), modifier = Modifier.height(28.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
