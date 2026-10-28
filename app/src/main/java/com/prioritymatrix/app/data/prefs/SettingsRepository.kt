package com.prioritymatrix.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLanguage(val tag: String) { ID("id"), EN("en") }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val userName: String = "",
    val language: AppLanguage = AppLanguage.ID,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val deadlineReminders: Boolean = true,
    /** Applied to new tasks that get a deadline. */
    val defaultReminderOffset: Int = 30,
    val overdueAlerts: Boolean = true,
    val dailyPlanningEnabled: Boolean = true,
    val dailyPlanningMinute: Int = 7 * 60 + 30,
    val weeklyReviewEnabled: Boolean = true,
    /** ISO day of week, 1 = Monday … 7 = Sunday. */
    val weeklyReviewDay: Int = 7,
    val weeklyReviewMinute: Int = 18 * 60,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val sessionsBeforeLongBreak: Int = 4,
    val autoStartNext: Boolean = false,
    val defaultCategoriesSeeded: Boolean = false
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val userName = stringPreferencesKey("user_name")
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val deadlineReminders = booleanPreferencesKey("deadline_reminders")
        val defaultReminderOffset = intPreferencesKey("default_reminder_offset")
        val overdueAlerts = booleanPreferencesKey("overdue_alerts")
        val dailyPlanningEnabled = booleanPreferencesKey("daily_planning_enabled")
        val dailyPlanningMinute = intPreferencesKey("daily_planning_minute")
        val weeklyReviewEnabled = booleanPreferencesKey("weekly_review_enabled")
        val weeklyReviewDay = intPreferencesKey("weekly_review_day")
        val weeklyReviewMinute = intPreferencesKey("weekly_review_minute")
        val focusMinutes = intPreferencesKey("focus_minutes")
        val shortBreak = intPreferencesKey("short_break")
        val longBreak = intPreferencesKey("long_break")
        val sessionsBeforeLong = intPreferencesKey("sessions_before_long")
        val autoStartNext = booleanPreferencesKey("auto_start_next")
        val categoriesSeeded = booleanPreferencesKey("categories_seeded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        val p = this
        return AppSettings(
            userName = p[K.userName] ?: d.userName,
            language = p[K.language]?.let { v -> AppLanguage.entries.firstOrNull { it.name == v } } ?: d.language,
            theme = p[K.theme]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: d.theme,
            deadlineReminders = p[K.deadlineReminders] ?: d.deadlineReminders,
            defaultReminderOffset = p[K.defaultReminderOffset] ?: d.defaultReminderOffset,
            overdueAlerts = p[K.overdueAlerts] ?: d.overdueAlerts,
            dailyPlanningEnabled = p[K.dailyPlanningEnabled] ?: d.dailyPlanningEnabled,
            dailyPlanningMinute = p[K.dailyPlanningMinute] ?: d.dailyPlanningMinute,
            weeklyReviewEnabled = p[K.weeklyReviewEnabled] ?: d.weeklyReviewEnabled,
            weeklyReviewDay = p[K.weeklyReviewDay] ?: d.weeklyReviewDay,
            weeklyReviewMinute = p[K.weeklyReviewMinute] ?: d.weeklyReviewMinute,
            focusMinutes = p[K.focusMinutes] ?: d.focusMinutes,
            shortBreakMinutes = p[K.shortBreak] ?: d.shortBreakMinutes,
            longBreakMinutes = p[K.longBreak] ?: d.longBreakMinutes,
            sessionsBeforeLongBreak = p[K.sessionsBeforeLong] ?: d.sessionsBeforeLongBreak,
            autoStartNext = p[K.autoStartNext] ?: d.autoStartNext,
            defaultCategoriesSeeded = p[K.categoriesSeeded] ?: d.defaultCategoriesSeeded
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val old = p.toSettings()
            val n = transform(old)
            p[K.userName] = n.userName.take(40)
            p[K.language] = n.language.name
            p[K.theme] = n.theme.name
            p[K.deadlineReminders] = n.deadlineReminders
            p[K.defaultReminderOffset] = n.defaultReminderOffset
            p[K.overdueAlerts] = n.overdueAlerts
            p[K.dailyPlanningEnabled] = n.dailyPlanningEnabled
            p[K.dailyPlanningMinute] = n.dailyPlanningMinute.coerceIn(0, 24 * 60 - 1)
            p[K.weeklyReviewEnabled] = n.weeklyReviewEnabled
            p[K.weeklyReviewDay] = n.weeklyReviewDay.coerceIn(1, 7)
            p[K.weeklyReviewMinute] = n.weeklyReviewMinute.coerceIn(0, 24 * 60 - 1)
            p[K.focusMinutes] = n.focusMinutes.coerceIn(1, 180)
            p[K.shortBreak] = n.shortBreakMinutes.coerceIn(1, 60)
            p[K.longBreak] = n.longBreakMinutes.coerceIn(1, 90)
            p[K.sessionsBeforeLong] = n.sessionsBeforeLongBreak.coerceIn(1, 12)
            p[K.autoStartNext] = n.autoStartNext
            p[K.categoriesSeeded] = n.defaultCategoriesSeeded
        }
    }
}
