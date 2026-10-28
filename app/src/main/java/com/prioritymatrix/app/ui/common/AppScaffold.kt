package com.prioritymatrix.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.LocalSnackbarHost
import com.prioritymatrix.app.domain.model.Project
import com.prioritymatrix.app.domain.model.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDateTime

@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(LocalSnackbarHost.current) },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, LocalStrings.current.back) }
                },
                actions = actions,
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        content = content
    )
}

/** Emits the current time every minute so overdue/today states refresh on their own. */
fun minuteTicker(clock: Clock): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now(clock)
        emit(now)
        delay((60 - now.second) * 1000L)
    }
}

fun List<Project>.byId(): Map<Long, Project> = associateBy { it.id }

fun Task.projectOf(projects: Map<Long, Project>): Project? = projectId?.let { projects[it] }
