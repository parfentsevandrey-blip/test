package app.opal.core.designsystem.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.theme.LocalHaptics
import app.opal.core.designsystem.theme.LocalReducedMotion
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.Capsule
import kotlinx.collections.immutable.ImmutableList

/** Segmented control on glass; the selection pill slides with a spring. */
@Composable
fun GlassSegmented(
    options: ImmutableList<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpalTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    GlassSurface(shape = Capsule(), style = GlassStyle.Control, modifier = modifier.height(44.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
            val segment = maxWidth / options.size
            val offset by
                animateDpAsState(
                    targetValue = segment * selectedIndex,
                    animationSpec =
                        if (reduced) spring(stiffness = Spring.StiffnessHigh)
                        else spring(0.78f, 420f),
                    label = "segment",
                )
            Box(
                Modifier.offset { IntOffset(offset.roundToPx(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .clip(Capsule())
                    .background(colors.glassTintStrong)
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
                options.forEachIndexed { index, label ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(Capsule()).selectable(
                            selected = index == selectedIndex,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (index != selectedIndex) {
                                haptics?.tap()
                                onSelect(index)
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = OpalTheme.type.label,
                            color =
                                if (index == selectedIndex) colors.onGlass else colors.onGlassMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
