package app.opal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.glass.GlassIconButton
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.LocalHaptics
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.Capsule

/**
 * Secondary action inside content (paste, scan, copy…). Deliberately not glass: glass is reserved
 * for the few primary controls floating above the content.
 */
@Composable
fun PanelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = OpalTheme.colors
    val haptics = LocalHaptics.current
    val shape = Capsule()
    Row(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(colors.panel)
            .border(1.dp, colors.panelStroke, shape)
            .clickable(enabled = enabled, role = Role.Button) {
                haptics?.tap()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null)
            Icon(
                icon,
                contentDescription = null,
                tint = colors.onBackground,
                modifier = Modifier.size(18.dp),
            )
        Text(text, style = OpalTheme.type.label, color = colors.onBackground)
    }
}

/** Title row of a pushed screen: glass back button + title. */
@Composable
fun SubScreenHeader(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassIconButton(OpalIcons.ArrowBack, contentDescription = backLabel, onClick = onBack)
        Text(
            title,
            style = OpalTheme.type.headline,
            color = OpalTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}
