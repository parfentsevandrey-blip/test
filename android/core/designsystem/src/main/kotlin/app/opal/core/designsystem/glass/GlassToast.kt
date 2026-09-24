package app.opal.core.designsystem.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay

@Stable
class ToastState {
    var current by mutableStateOf<ToastMessage?>(null)
        private set

    fun show(text: String, icon: ImageVector? = null) {
        current = ToastMessage(text, icon, System.nanoTime())
    }

    internal fun dismiss(message: ToastMessage) {
        if (current == message) current = null
    }
}

data class ToastMessage(val text: String, val icon: ImageVector?, val id: Long)

@Composable fun rememberToastState(): ToastState = remember { ToastState() }

/** A floating glass capsule for short confirmations; announced by TalkBack. */
@Composable
fun GlassToast(state: ToastState, modifier: Modifier = Modifier) {
    val message = state.current
    LaunchedEffect(message) {
        if (message != null) {
            delay(TOAST_MILLIS)
            state.dismiss(message)
        }
    }
    var last by remember { mutableStateOf<ToastMessage?>(null) }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        enter = scaleIn(spring(0.6f, 500f), initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(targetScale = 0.9f) + fadeOut(),
        modifier = modifier,
    ) {
        val shown = last ?: return@AnimatedVisibility
        GlassSurface(shape = Capsule(), style = GlassStyle.Bar, layer = GlassLayer.Floating) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 12.dp).semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                shown.icon?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Text(
                    shown.text,
                    style = OpalTheme.type.bodyStrong,
                    color = OpalTheme.colors.onGlass,
                )
            }
        }
    }
}

private const val TOAST_MILLIS = 2_600L
