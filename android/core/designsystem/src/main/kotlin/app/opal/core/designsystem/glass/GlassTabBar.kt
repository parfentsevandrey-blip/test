package app.opal.core.designsystem.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opal.core.designsystem.theme.LocalHaptics
import app.opal.core.designsystem.theme.LocalReducedMotion
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.Capsule
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class TabItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    /** Stable id for UI automation (baseline profile generator, benchmarks). */
    val testTag: String = label,
)

/**
 * Floating glass tab bar. The selection pill slides with a spring; while content scrolls down
 * ([compact]) the bar shrinks and hides labels, leaving more room for content.
 */
@Composable
fun GlassTabBar(
    items: ImmutableList<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = OpalTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val motion =
        if (reduced) spring<androidx.compose.ui.unit.Dp>(stiffness = Spring.StiffnessHigh)
        else spring(0.74f, 380f)
    val height by animateDpAsState(if (compact) 52.dp else 64.dp, motion, label = "tabBarHeight")
    // At large font scales the labels would not fit: icons only (each keeps its label for
    // TalkBack).
    val iconsOnly = compact || LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val labelAlpha by animateFloatAsState(if (iconsOnly) 0f else 1f, label = "tabLabels")
    GlassSurface(
        shape = Capsule(),
        style = GlassStyle.Bar,
        layer = GlassLayer.Floating,
        modifier = modifier.height(height),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
            val slot = maxWidth / items.size
            val x by animateDpAsState(slot * selectedIndex, motion, label = "tabPill")
            Box(
                Modifier.offset { IntOffset(x.roundToPx(), 0) }
                    .width(slot)
                    .fillMaxHeight()
                    .clip(Capsule())
                    .background(colors.glassTintStrong)
            )
            Row(
                Modifier.fillMaxSize().selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    Column(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .testTag(item.testTag)
                            .clip(Capsule())
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!selected) {
                                    haptics?.tap()
                                    onSelect(index)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            if (selected) item.selectedIcon else item.icon,
                            contentDescription = if (labelAlpha < 0.5f) item.label else null,
                            tint = if (selected) colors.accent else colors.onGlass,
                            modifier = Modifier.size(24.dp),
                        )
                        if (labelAlpha > 0.01f) {
                            // Long labels (ru: «Подключение») shrink a little instead of clipping.
                            BasicText(
                                item.label,
                                style =
                                    OpalTheme.type.caption.copy(
                                        color =
                                            if (selected) colors.onGlass else colors.onGlassMuted
                                    ),
                                maxLines = 1,
                                autoSize =
                                    TextAutoSize.StepBased(
                                        minFontSize = 9.sp,
                                        maxFontSize = OpalTheme.type.caption.fontSize,
                                        stepSize = 0.5.sp,
                                    ),
                                modifier =
                                    Modifier.padding(horizontal = 2.dp).graphicsLayer {
                                        alpha = labelAlpha
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val LARGE_FONT_SCALE = 1.3f
