package at.creepervm1000.mobileclaw.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects a [StateFlow] that already has a value, so there's no nullable initial state to
 * handle at every call site.
 */
@Composable
fun <T> StateFlow<T>.collectAsStateSafe(): State<T> = collectAsState()

/**
 * Re-evaluates [read] every time the screen resumes.
 *
 * For state that lives outside the app — a permission the user toggles in Android's own
 * settings — there is nothing to observe, so the resume event is the signal to look again.
 */
@Composable
fun <T> rememberOnResume(read: () -> T): T {
    var value by remember { mutableStateOf(read()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) value = read()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return value
}

/**
 * Editable text backed by asynchronously persisted state.
 *
 * A field rendered straight from a DataStore flow loses characters while typing: every keystroke
 * has to round-trip through disk, and a keystroke landing before the previous one has echoed back
 * is overwritten by the stale stored value — the cursor appears to jump a character backwards.
 *
 * [rememberWriteThrough] keeps the displayed text local so typing is never blocked, and adopts
 * [stored] again only once the write it is waiting on has come back. That keeps external changes
 * visible — the agent renaming itself with set_agent_name, say — without fighting the keyboard.
 */
@Composable
fun rememberWriteThrough(stored: String, write: (String) -> Unit): WriteThroughText {
    var shown by remember { mutableStateOf(stored) }
    var awaiting by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(stored) {
        when (awaiting) {
            // Nothing in flight, so this is someone else's change: take it.
            null -> shown = stored
            // Our own write came back; later keystrokes may already have moved past it.
            stored -> awaiting = null
        }
    }

    return WriteThroughText(shown) { typed ->
        shown = typed
        awaiting = typed
        write(typed)
    }
}

/** The current text to display, and the callback to hand to a text field's onValueChange. */
data class WriteThroughText(val value: String, val onValueChange: (String) -> Unit)

/**
 * Standard destructive-action confirmation. Destructive taps that cannot be undone — clearing
 * the transcript, deleting a scheduled task — need an explicit yes before they happen.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm() }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("Cancel") }
        },
    )
}
