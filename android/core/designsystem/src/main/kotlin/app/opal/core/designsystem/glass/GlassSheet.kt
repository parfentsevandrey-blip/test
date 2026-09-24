package app.opal.core.designsystem.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.opal.core.designsystem.theme.LocalScreenCornerRadius
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.designsystem.theme.concentricRadius
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Glass bottom sheet floating 8 dp from the screen edges, corners concentric with the display.
 * Dismissed by the scrim, by dragging down, or by (predictive) back via NavigationBackHandler. Must
 * be placed in a full-screen box above the content.
 */
// Two root layers by design (scrim + sheet); the modifier styles the sheet itself.
@Suppress("ModifierNotUsedAtRoot")
@Composable
fun BoxScope.GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = visible,
        onBackCompleted = onDismiss,
    )
    val colors = OpalTheme.colors
    AnimatedVisibility(
        visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = if (colors.isDark) 0.45f else 0.25f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )
    }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val maxHeight =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.88f
    AnimatedVisibility(
        visible,
        enter = slideInVertically(spring(0.85f, 380f)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val radius = concentricRadius(LocalScreenCornerRadius.current, 8.dp, minimum = 24.dp)
        GlassSurface(
            shape = RoundedRectangle(radius),
            style = GlassStyle.Sheet,
            layer = GlassLayer.Floating,
            contentAlignment = Alignment.TopCenter,
            modifier =
                modifier
                    .navigationBarsPadding()
                    .padding(8.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .offset { IntOffset(0, drag.value.roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state =
                            rememberDraggableState { delta ->
                                scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                            },
                        onDragStopped = { velocity ->
                            if (drag.value > DISMISS_DISTANCE_PX || velocity > DISMISS_VELOCITY) {
                                onDismiss()
                                drag.snapTo(0f)
                            } else {
                                drag.animateTo(0f, spring(0.8f, 500f))
                            }
                        },
                    ),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 12.dp)
                        .size(width = 36.dp, height = 5.dp)
                        .clip(Capsule())
                        .background(colors.onGlassMuted.copy(alpha = 0.5f))
                )
                if (title != null) {
                    Text(
                        title,
                        style = OpalTheme.type.headline,
                        color = colors.onGlass,
                        modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
                    )
                }
                Column(Modifier.verticalScroll(rememberScrollState()), content = content)
            }
        }
    }
}

private const val DISMISS_DISTANCE_PX = 240f
private const val DISMISS_VELOCITY = 1_800f
