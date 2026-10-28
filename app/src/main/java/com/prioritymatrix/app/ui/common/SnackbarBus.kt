package com.prioritymatrix.app.ui.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (suspend () -> Unit)? = null
)

/** App-wide snackbar channel so feedback/undo survives navigation. */
@Singleton
class SnackbarBus @Inject constructor() {
    private val _events = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SnackbarEvent> = _events.asSharedFlow()

    fun show(message: String, actionLabel: String? = null, onAction: (suspend () -> Unit)? = null) {
        _events.tryEmit(SnackbarEvent(message, actionLabel, onAction))
    }
}
