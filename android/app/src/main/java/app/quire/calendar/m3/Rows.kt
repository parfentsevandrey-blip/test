package app.quire.calendar.m3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * The settings row, shared by the app's own settings and by the widget's configuration screen.
 *
 * A run of these is drawn as one connected block rather than as a list with dividers: Android's
 * own settings from 16 onwards round the outer corners of a group and square off the inner ones,
 * which is exactly what `segmentedShapes` computes from a row's position. Related switches then
 * read as one decision with several parts instead of as a wall of unrelated ones.
 */
@Composable
internal fun SettingGroup(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        content()
    }
}

/**
 * One switch, as a row that toggles wherever it is touched.
 *
 * The row itself is the control — `SegmentedListItem`'s toggleable form carries the checked state
 * for accessibility, so a screen reader announces one switch rather than reading out a label and a
 * separate control it cannot connect to it. That is also why the `Switch` takes a null callback:
 * it is the indicator, not a second target.
 */
@Composable
internal fun SettingRow(
    index: Int,
    count: Int,
    title: String,
    hint: String?,
    checked: Boolean,
    tint: Color? = null,
    onChange: (Boolean) -> Unit,
) {
    // The platform's own on/off pulses, so a switch flipped in here feels like a switch flipped
    // in the system settings — two different clicks for the two directions.
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(
                if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
            )
            onChange(it)
        },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        supportingContent = hint?.let { { Text(it) } },
        leadingContent = tint?.let {
            { Box(Modifier.size(12.dp).clip(CircleShape).background(it)) }
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) {
        Text(title)
    }
}

/**
 * One choice from a few, as Expressive's connected toggle-button group.
 *
 * This replaced two copies of `SingleChoiceSegmentedButtonRow`. The connected group is the shape
 * M3 Expressive draws this pattern in on Android 16 and 17 — outer corners full, inner edges
 * nearly square, a hair of space between segments, and the checked segment morphing towards a
 * pill — and the segmented row is the previous generation of the same idea. Shared between the
 * apps, so the units in the weather and the theme in the calendar are one control, not two
 * lookalikes.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChoiceRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEachIndexed { index, label ->
                val shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                TonalToggleButton(
                    checked = index == selected,
                    // A toggle in a single-choice group only ever turns on: the segment that is
                    // already chosen stays chosen, the way a radio button holds its ground.
                    onCheckedChange = { on ->
                        if (on && index != selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        }
                    },
                    shapes = shapes,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}
