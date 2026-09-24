package app.opal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.LocalHaptics
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/** Search field on a content panel (content layer, not glass: typing needs a calm surface). */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = OpalTheme.colors
    val shape = Capsule()
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(colors.panel)
            .border(1.dp, colors.panelStroke, shape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            OpalIcons.Search,
            contentDescription = null,
            tint = colors.onBackgroundMuted,
            modifier = Modifier.size(20.dp),
        )
        Box(Modifier.weight(1f)) {
            if (value.isEmpty())
                Text(placeholder, style = OpalTheme.type.body, color = colors.onBackgroundMuted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = OpalTheme.type.body.copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(40.dp)
                    .clip(Capsule())
                    .clickable(role = Role.Button) { onValueChange("") }
                    .semantics { contentDescription = clearLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    OpalIcons.Close,
                    contentDescription = null,
                    tint = colors.onBackgroundMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Multi-line monospace editor (bridge lines, diagnostics). */
@Composable
fun CodeEditor(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 6,
    readOnly: Boolean = false,
) {
    val colors = OpalTheme.colors
    val shape = RoundedRectangle(PanelRadius)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panel)
            .border(1.dp, colors.panelStroke, shape)
            .padding(16.dp)
    ) {
        val style =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = OpalTheme.type.callout.fontSize,
                lineHeight = OpalTheme.type.callout.lineHeight,
                color = colors.onBackground,
            )
        if (value.isEmpty()) Text(placeholder, style = style.copy(color = colors.onBackgroundMuted))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            minLines = minLines,
            readOnly = readOnly,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions.Default,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        )
    }
}

/** Single choice row; the whole row is the touch target. */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = OpalTheme.colors
    val haptics = LocalHaptics.current
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton) {
                if (!selected) haptics?.tap()
                onSelect()
            }
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = colors.accent,
                    unselectedColor = colors.onBackgroundMuted,
                ),
        )
        if (icon != null)
            Icon(
                icon,
                contentDescription = null,
                tint = colors.onBackground,
                modifier = Modifier.size(24.dp),
            )
        leading?.invoke(this)
        Column(Modifier.weight(1f)) {
            Text(title, style = OpalTheme.type.body, color = colors.onBackground)
            if (subtitle != null)
                Text(subtitle, style = OpalTheme.type.callout, color = colors.onBackgroundMuted)
        }
        trailing()
    }
}

/** Multiple choice row with a leading slot (e.g. app icon). */
@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = OpalTheme.colors
    val haptics = LocalHaptics.current
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox) {
                haptics?.tap()
                onCheckedChange(it)
            }
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke(this)
        Column(Modifier.weight(1f)) {
            Text(title, style = OpalTheme.type.body, color = colors.onBackground, maxLines = 2)
            if (subtitle != null)
                Text(
                    subtitle,
                    style = OpalTheme.type.caption,
                    color = colors.onBackgroundMuted,
                    maxLines = 1,
                )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = colors.accent,
                    uncheckedColor = colors.onBackgroundMuted,
                    checkmarkColor = colors.background,
                ),
        )
    }
}
