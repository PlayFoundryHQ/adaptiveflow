package io.github.playfoundryhq.adaptiveflow.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import io.github.playfoundryhq.adaptiveflow.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * One place to raise a transient message from anywhere in the tree, shown as a
 * themed Material `Snackbar` (replaces the app's scattered `Toast`s, which
 * ignored dark mode and stacked awkwardly).
 */
object AppSnackbar {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun show(message: String) {
        _messages.tryEmit(message)
    }
}

@Composable
fun AppSnackbarHost(hostState: SnackbarHostState) {
    LaunchedEffect(Unit) {
        AppSnackbar.messages.collect { msg ->
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(msg)
        }
    }
    val c = AppTheme.colors
    SnackbarHost(hostState) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = c.textPrimary,
            contentColor = c.surface,
            actionContentColor = c.accent,
            shape = RoundedCornerShape(12.dp),
        )
    }
}
