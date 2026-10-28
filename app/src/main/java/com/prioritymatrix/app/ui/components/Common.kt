package com.prioritymatrix.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.ui.theme.palette

val ProjectIcons: List<Pair<String, ImageVector>> = listOf(
    "work" to Icons.Rounded.Work,
    "person" to Icons.Rounded.Person,
    "family" to Icons.Rounded.FamilyRestroom,
    "health" to Icons.Rounded.Favorite,
    "school" to Icons.Rounded.School,
    "build" to Icons.Rounded.Construction,
    "folder" to Icons.Rounded.Folder,
    "star" to Icons.Rounded.Star,
    "home" to Icons.Rounded.Home,
    "flag" to Icons.Rounded.Flag,
    "code" to Icons.Rounded.Code,
    "money" to Icons.Rounded.Payments,
    "fitness" to Icons.Rounded.FitnessCenter,
    "travel" to Icons.Rounded.Flight,
    "book" to Icons.Rounded.AutoStories,
    "art" to Icons.Rounded.Palette
)

fun projectIcon(key: String): ImageVector = ProjectIcons.firstOrNull { it.first == key }?.second ?: Icons.Rounded.Folder

@Composable
fun QuadrantBadge(q: Quadrant, modifier: Modifier = Modifier, withAction: Boolean = false) {
    val p = q.palette()
    val s = LocalStrings.current
    Surface(color = p.container, contentColor = p.onContainer, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(p.main))
            Spacer(Modifier.width(5.dp))
            Text(
                if (withAction) "${s.quadrantShort(q)} · ${s.quadrantAction(q)}" else s.quadrantShort(q),
                style = MaterialTheme.typography.labelSmall, maxLines = 1
            )
        }
    }
}

@Composable
fun DemoBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(6.dp), modifier = modifier
    ) {
        Text(LocalStrings.current.demoBadge, Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

/** Round, animated completion check in the quadrant colour. 48dp touch target. */
@Composable
fun CompletionCheck(checked: Boolean, color: Color, onToggle: () -> Unit, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val bg by animateColorAsState(if (checked) color else Color.Transparent, label = "checkBg")
    val scale by animateFloatAsState(if (checked) 1f else 0.6f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "checkScale")
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(size).clip(CircleShape).background(bg).border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.7f).scale(scale))
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null, leading: ImageVector? = null, leadingTint: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            Icon(leading, null, tint = leadingTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(action) }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null, compact: Boolean = false) {
    Column(
        modifier.fillMaxWidth().padding(vertical = if (compact) 12.dp else 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(if (compact) 48.dp else 72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(if (compact) 24.dp else 34.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun InlineEmpty(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatPill(label: String, value: String, accent: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = modifier.then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick) else Modifier)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
fun ColorDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun cardArrangement() = Arrangement.spacedBy(10.dp)
