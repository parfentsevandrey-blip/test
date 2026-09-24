package app.opal.core.designsystem.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.theme.LocalHaptics
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.Capsule

/** A glass button (capsule by default) with the liquid press response and a haptic tick. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = Capsule(),
    style: GlassStyle = GlassStyle.Control,
    layer: GlassLayer = GlassLayer.InContent,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        shape = shape,
        style = style,
        layer = layer,
        interactive = enabled,
        modifier =
            modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    haptics?.tap()
                    onClick()
                },
    ) {
        Row(
            Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides OpalTheme.colors.onGlass) {
                content()
            }
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    layer: GlassLayer = GlassLayer.InContent,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        layer = layer,
        contentPadding = PaddingValues(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

/** Small capsule for transient facts (transport in use) or filters. */
@Composable
fun GlassChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = OpalTheme.colors
    val haptics = LocalHaptics.current
    val clickable =
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) {
                haptics?.tap()
                onClick()
            }
        } else {
            Modifier
        }
    GlassSurface(
        shape = Capsule(),
        style = GlassStyle.Control,
        tint =
            if (selected) colors.accent.copy(alpha = 0.22f)
            else androidx.compose.ui.graphics.Color.Unspecified,
        interactive = onClick != null,
        modifier =
            modifier
                .defaultMinSize(minHeight = 36.dp)
                .semantics { this.selected = selected }
                .then(clickable),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = OpalTheme.type.label, color = colors.onGlass)
        }
    }
}

private const val DISABLED_ALPHA = 0.45f
