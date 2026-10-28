package com.prioritymatrix.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.prioritymatrix.app.domain.model.Quadrant
import java.time.LocalDate

object Routes {
    const val DASHBOARD = "dashboard"
    const val TASKS = "tasks"
    const val CALENDAR = "calendar"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val QUADRANT = "quadrant/{q}"
    const val TASK = "task/{id}"
    const val NEW_TASK = "newtask?important={important}&urgent={urgent}&date={date}&project={project}&title={title}"
    const val BOARD = "board"
    const val FOCUS = "focus?taskId={taskId}"
    const val PROJECTS = "projects"
    const val PROJECT = "project/{id}"
    const val PLANNING = "planning"
    const val REVIEW = "review"

    val bottomTabs = listOf(DASHBOARD, TASKS, CALENDAR, ANALYTICS, SETTINGS)
}

data class QuickAddRequest(
    val important: Boolean = true,
    val urgent: Boolean = false,
    val date: LocalDate? = null,
    val projectId: Long? = null
)

class AppNavigator(
    private val nav: NavHostController,
    val openQuickAdd: (QuickAddRequest) -> Unit
) {
    fun back() { if (!nav.popBackStack()) nav.navigate(Routes.DASHBOARD) }

    fun toTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun toTask(id: Long) = nav.navigate("task/$id")

    fun toNewTask(
        title: String = "", important: Boolean = true, urgent: Boolean = false,
        date: LocalDate? = null, projectId: Long? = null
    ) {
        val t = android.net.Uri.encode(title)
        nav.navigate("newtask?important=$important&urgent=$urgent&date=${date?.toEpochDay() ?: -1}&project=${projectId ?: -1}&title=$t")
    }

    fun toQuadrant(q: Quadrant) = nav.navigate("quadrant/${q.number}")
    fun toBoard() = nav.navigate(Routes.BOARD) { launchSingleTop = true }
    fun toFocus(taskId: Long? = null) = nav.navigate("focus?taskId=${taskId ?: -1}") { launchSingleTop = true }
    fun toProjects() = nav.navigate(Routes.PROJECTS) { launchSingleTop = true }
    fun toProject(id: Long) = nav.navigate("project/$id")
    fun toPlanning() = nav.navigate(Routes.PLANNING) { launchSingleTop = true }
    fun toReview() = nav.navigate(Routes.REVIEW) { launchSingleTop = true }

    /** Deep links from notifications ("task/12", "planning", "review", "focus"). */
    fun openDeepLink(route: String) {
        when {
            route.startsWith("task/") -> route.removePrefix("task/").toLongOrNull()?.let { toTask(it) }
            route == "planning" -> toPlanning()
            route == "review" -> toReview()
            route == "focus" -> toFocus()
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator> { error("AppNavigator not provided") }
