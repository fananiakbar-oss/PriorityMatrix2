package com.prioritymatrix.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prioritymatrix.app.MainViewModel
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.ui.analytics.AnalyticsScreen
import com.prioritymatrix.app.ui.analytics.WeeklyReviewScreen
import com.prioritymatrix.app.ui.calendar.CalendarScreen
import com.prioritymatrix.app.ui.common.SnackbarBus
import com.prioritymatrix.app.ui.common.SnackbarEvent
import com.prioritymatrix.app.ui.dashboard.DashboardScreen
import com.prioritymatrix.app.ui.focus.FocusScreen
import com.prioritymatrix.app.ui.matrix.MatrixBoardScreen
import com.prioritymatrix.app.ui.planning.DailyPlanningScreen
import com.prioritymatrix.app.ui.projects.ProjectDetailScreen
import com.prioritymatrix.app.ui.projects.ProjectsScreen
import com.prioritymatrix.app.ui.settings.SettingsScreen
import com.prioritymatrix.app.ui.task.QuickAddSheet
import com.prioritymatrix.app.ui.task.TaskEditorScreen
import com.prioritymatrix.app.ui.tasks.TasksScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

val LocalSnackbarHost = staticCompositionLocalOf { SnackbarHostState() }

@HiltViewModel
class RootViewModel @Inject constructor(bus: SnackbarBus) : ViewModel() {
    val events: SharedFlow<SnackbarEvent> = bus.events
    fun runAction(action: suspend () -> Unit) { viewModelScope.launch { runCatching { action() } } }
}

private data class Tab(val route: String, val icon: ImageVector, val label: (com.prioritymatrix.app.i18n.AppStrings) -> String)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, Icons.Rounded.GridView) { it.navDashboard },
    Tab(Routes.TASKS, Icons.AutoMirrored.Rounded.FormatListBulleted) { it.navTasks },
    Tab(Routes.CALENDAR, Icons.Rounded.CalendarMonth) { it.navCalendar },
    Tab(Routes.ANALYTICS, Icons.Rounded.Insights) { it.navAnalytics },
    Tab(Routes.SETTINGS, Icons.Rounded.Settings) { it.navSettings }
)

@Composable
fun AppRoot(mainVm: MainViewModel, rootVm: RootViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val nav = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }
    var quickAdd by remember { mutableStateOf<QuickAddRequest?>(null) }
    val navigator = remember(nav) { AppNavigator(nav) { req -> quickAdd = req } }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in Routes.bottomTabs

    LaunchedEffect(Unit) {
        rootVm.events.collect { e ->
            snackbarHost.currentSnackbarData?.dismiss()
            launch {
                val r = snackbarHost.showSnackbar(
                    e.message, actionLabel = e.actionLabel,
                    duration = if (e.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
                )
                if (r == SnackbarResult.ActionPerformed) e.onAction?.let { rootVm.runAction(it) }
            }
        }
    }
    val pending by mainVm.pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let { navigator.openDeepLink(it); mainVm.consumeRoute() }
    }

    CompositionLocalProvider(LocalNavigator provides navigator, LocalSnackbarHost provides snackbarHost) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                AnimatedVisibility(showBottomBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigator.toTab(tab.route) },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label(s), maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } }
            ) {
                composable(Routes.DASHBOARD) { DashboardScreen() }
                composable(Routes.TASKS) { TasksScreen(quadrantNumber = null) }
                composable(Routes.CALENDAR) { CalendarScreen() }
                composable(Routes.ANALYTICS) { AnalyticsScreen() }
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(Routes.QUADRANT, arguments = listOf(navArgument("q") { type = NavType.IntType })) { e ->
                    TasksScreen(quadrantNumber = e.arguments?.getInt("q"))
                }
                composable(Routes.TASK, arguments = listOf(navArgument("id") { type = NavType.LongType })) { TaskEditorScreen() }
                composable(
                    Routes.NEW_TASK,
                    arguments = listOf(
                        navArgument("important") { type = NavType.BoolType; defaultValue = true },
                        navArgument("urgent") { type = NavType.BoolType; defaultValue = false },
                        navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("project") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { TaskEditorScreen() }
                composable(Routes.BOARD) { MatrixBoardScreen() }
                composable(Routes.FOCUS, arguments = listOf(navArgument("taskId") { type = NavType.LongType; defaultValue = -1L })) { FocusScreen() }
                composable(Routes.PROJECTS) { ProjectsScreen() }
                composable(Routes.PROJECT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { ProjectDetailScreen() }
                composable(Routes.PLANNING) { DailyPlanningScreen() }
                composable(Routes.REVIEW) { WeeklyReviewScreen() }
            }
        }

        quickAdd?.let { req ->
            QuickAddSheet(
                request = req,
                onDismiss = { quickAdd = null },
                onExpand = { title, important, urgent, date, projectId ->
                    quickAdd = null
                    navigator.toNewTask(title, important, urgent, date, projectId)
                }
            )
        }
    }
}

