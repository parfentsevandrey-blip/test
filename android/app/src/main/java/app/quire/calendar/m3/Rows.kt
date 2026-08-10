package app.quire.calendar.m3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
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
