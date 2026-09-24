package app.opal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.LocalHaptics
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.RoundedRectangle

/**
 * Content panel: translucent but *not* glass (no refraction) — content stays calm and readable;
 * glass is reserved for controls floating above it.
 */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = OpalTheme.colors
    val shape = RoundedRectangle(PanelRadius)
    Column(
        modifier.clip(shape).background(colors.panel).border(1.dp, colors.panelStroke, shape),
        content = content,
    )
}

val PanelRadius = 24.dp

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = OpalTheme.type.caption,
        color = OpalTheme.colors.onBackgroundMuted,
        modifier =
            modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
fun PanelDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(start = 56.dp), color = OpalTheme.colors.panelStroke)
}

/** A row in a panel: icon, title, optional subtitle and a trailing slot. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = OpalTheme.colors
    val click =
        if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick)
        else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .then(click)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) colors.onBackground else colors.onBackgroundMuted,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = OpalTheme.type.body,
                color = if (enabled) colors.onBackground else colors.onBackgroundMuted,
            )
            if (subtitle != null)
                Text(subtitle, style = OpalTheme.type.callout, color = colors.onBackgroundMuted)
        }
        trailing()
    }
}

/** Setting with a switch; the whole row toggles (large touch target, one TalkBack node). */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptics = LocalHaptics.current
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        modifier =
            modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch) {
                haptics?.tap()
                onCheckedChange(it)
            },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = OpalTheme.colors.connected.copy(alpha = 0.9f),
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = OpalTheme.colors.onBackgroundMuted.copy(alpha = 0.25f),
                    uncheckedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White,
                ),
        )
    }
}

@Composable
fun ChevronRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
    ) {
        if (value != null)
            Text(value, style = OpalTheme.type.callout, color = OpalTheme.colors.onBackgroundMuted)
        Icon(
            OpalIcons.ChevronRight,
            contentDescription = null,
            tint = OpalTheme.colors.onBackgroundMuted,
        )
    }
}

/** Plain-text explanatory block (warnings, honest limitations). */
@Composable
fun Note(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = OpalIcons.Info,
    tint: Color = Color.Unspecified,
) {
    val colors = OpalTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (tint == Color.Unspecified) colors.onBackgroundMuted else tint,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = OpalTheme.type.callout, color = colors.onBackgroundMuted)
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
        Text(
            text,
            style = OpalTheme.type.title,
            color = OpalTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * One slice of a panel for lazy lists, where rows cannot share a parent: the first and last slices
 * carry the rounded corners, so consecutive slices read as one panel.
 */
@Composable
fun PanelSegment(
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val top = if (first) PanelRadius else 0.dp
    val bottom = if (last) PanelRadius else 0.dp
    Column(
        modifier
            .clip(com.kyant.shapes.UnevenRoundedRectangle(top, top, bottom, bottom))
            .background(OpalTheme.colors.panel),
        content = content,
    )
}
