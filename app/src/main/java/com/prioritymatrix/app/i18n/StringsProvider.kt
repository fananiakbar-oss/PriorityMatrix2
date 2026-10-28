package com.prioritymatrix.app.i18n

import com.prioritymatrix.app.data.prefs.AppLanguage
import com.prioritymatrix.app.data.prefs.SettingsRepository
import com.prioritymatrix.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Current-language strings for code outside Compose (snackbars, notifications). */
@Singleton
class StringsProvider @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope
) {
    private val cache = mapOf(AppLanguage.ID to AppStrings(AppLanguage.ID), AppLanguage.EN to AppStrings(AppLanguage.EN))

    val strings: StateFlow<AppStrings> = settings.settings
        .map { it.language }
        .distinctUntilChanged()
        .map { cache.getValue(it) }
        .stateIn(scope, SharingStarted.Eagerly, cache.getValue(AppLanguage.ID))

    val current: AppStrings get() = strings.value

    /** Reads the persisted language — use from receivers/workers that may run before the flow emits. */
    suspend fun load(): AppStrings = cache.getValue(settings.current().language)
}
