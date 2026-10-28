package com.prioritymatrix.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.prefs.AppSettings
import com.prioritymatrix.app.data.prefs.SettingsRepository
import com.prioritymatrix.app.i18n.AppStrings
import com.prioritymatrix.app.i18n.LocalStrings
import com.prioritymatrix.app.navigation.AppRoot
import com.prioritymatrix.app.notifications.NotificationHelper
import com.prioritymatrix.app.ui.theme.PriorityMatrixTheme
import com.prioritymatrix.app.ui.theme.isDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()
    fun openRoute(route: String?) { if (route != null) _pendingRoute.value = route }
    fun consumeRoute() { _pendingRoute.value = null }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) vm.openRoute(intent?.getStringExtra(NotificationHelper.EXTRA_ROUTE))
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val current = settings ?: AppSettings()
            val strings = remember(current.language) { AppStrings(current.language) }
            PriorityMatrixTheme(dark = isDarkTheme(current.theme)) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    if (settings != null) AppRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.openRoute(intent.getStringExtra(NotificationHelper.EXTRA_ROUTE))
    }
}
