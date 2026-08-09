package app.quire.calendar.m3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quire.R
import app.quire.calendar.core.AgendaEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One entry, in full.
 *
 * Tapping an entry used to hand it straight to whatever calendar app is installed, which is a long
 * way to go to read a room number. The sheet answers the question in place and keeps the handover
 * as a button, for when the answer is not enough.
 *
 * Quire never writes to a calendar, so there is nothing here that edits: what it offers is what a
 * reader wants — the whole of it legibly, and a way to pass it on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    entry: AgendaEntry,
    onOpen: (AgendaEntry) -> Unit,
    onShare: (AgendaEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        EventDetails(entry, onOpen, onShare)
    }
}

/**
 * What the sheet contains, separately from the sheet.
 *
 * A modal sheet composes into a window of its own, which a screenshot of the screen underneath
 * does not include — so this is where the picture that gets looked at comes from, and it is the
 * same composable the sheet shows rather than a copy of it.
 */
@Composable
fun EventDetails(
    entry: AgendaEntry,
    onOpen: (AgendaEntry) -> Unit,
    onShare: (AgendaEntry) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val locale = rememberLocale()
    val zone = ZoneId.systemDefault()

    val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val from = Instant.ofEpochMilli(entry.begin).atZone(zone)
    val to = Instant.ofEpochMilli(entry.end).atZone(zone)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(6.dp)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(if (entry.colour != 0) Color(entry.colour) else scheme.tertiary),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.title.ifBlank { stringResource(R.string.nothing_scheduled) },
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        Spacer(Modifier.height(20.dp))

        Detail(
            label = stringResource(R.string.sheet_when),
            value = if (entry.allDay) {
                "${day.format(from)} · ${stringResource(R.string.all_day)}"
            } else if (from.toLocalDate() == to.toLocalDate()) {
                "${day.format(from)}\n${clock.format(from)} – ${clock.format(to)}"
            } else {
                "${day.format(from)} ${clock.format(from)}\n" +
                    "${day.format(to)} ${clock.format(to)}"
            },
        )
        entry.location?.takeIf { it.isNotBlank() }?.let {
            Detail(stringResource(R.string.sheet_where), it)
        }
        entry.calendarName?.takeIf { it.isNotBlank() }?.let {
            Detail(stringResource(R.string.sheet_calendar), it)
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onOpen(entry) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sheet_open))
            }
            OutlinedButton(onClick = { onShare(entry) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sheet_share))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
