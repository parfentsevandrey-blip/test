package app.opal.core.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The slot above the floating tab bar (like the tab bar accessory of iOS 26): floating glass that
 * stays in the thumb zone while the content scrolls. The scaffold owns the slot; a screen fills it
 * with [BottomAccessory] for as long as it is shown.
 */
@Stable
class BottomAccessoryHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    internal fun set(slot: (@Composable () -> Unit)?) {
        content = slot
    }
}

val LocalBottomAccessory = staticCompositionLocalOf<BottomAccessoryHost?> { null }

/** Shows [content] above the tab bar while the calling screen is in the composition. */
@Composable
fun BottomAccessory(content: @Composable () -> Unit) {
    val host = LocalBottomAccessory.current ?: return
    val latest by rememberUpdatedState(content)
    DisposableEffect(host) {
        val slot: @Composable () -> Unit = { latest() }
        host.set(slot)
        onDispose { if (host.content === slot) host.set(null) }
    }
}
