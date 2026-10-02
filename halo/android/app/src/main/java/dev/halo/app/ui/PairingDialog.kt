package dev.halo.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.halo.app.Halo
import dev.halo.app.R
import dev.halo.core.MemberInfo
import dev.halo.core.Pairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Stage {
    data object Connecting : Stage
    data class Compare(val peer: String, val emoji: List<String>) : Stage
    data class Waiting(val peer: String) : Stage
    data class Done(val member: MemberInfo) : Stage
    data class Failed(val message: String) : Stage
}

/** Joins the pairing behind `code`: the user compares four emoji, both sides confirm. */
@Composable
fun PairingDialog(code: String, onFinished: (MemberInfo?) -> Unit) {
    var stage by remember { mutableStateOf<Stage>(Stage.Connecting) }
    var pairing by remember { mutableStateOf<Pairing?>(null) }
    val scope = rememberCoroutineScope()
    val declined = stringResource(R.string.pairing_declined)

    LaunchedEffect(code) {
        runCatching { withContext(Dispatchers.IO) { Halo.pairJoin(code) } }
            .onSuccess {
                pairing = it
                stage = Stage.Compare(it.peerName(), it.emoji())
            }
            .onFailure { stage = Stage.Failed(it.message.orEmpty()) }
    }
    DisposableEffect(Unit) { onDispose { pairing?.close() } }

    fun answer(accept: Boolean) {
        val current = pairing ?: return
        val peer = current.peerName()
        stage = Stage.Waiting(peer)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Halo.pairConfirm(current, accept) } }
                .onSuccess { member -> stage = if (member != null) Stage.Done(member) else Stage.Failed(declined) }
                .onFailure { stage = Stage.Failed(it.message.orEmpty()) }
        }
    }

    AlertDialog(
        // Only the buttons end a pairing: the other device waits for an answer.
        onDismissRequest = {},
        title = { Text(stringResource(R.string.pairing)) },
        text = {
            when (val current = stage) {
                Stage.Connecting -> Busy(stringResource(R.string.pairing_connecting))
                is Stage.Compare -> Column {
                    Text(stringResource(R.string.pairing_compare, current.peer))
                    Spacer(Modifier.height(12.dp))
                    Text(current.emoji.joinToString("  "), fontSize = 34.sp)
                }
                is Stage.Waiting -> Busy(stringResource(R.string.pairing_waiting, current.peer))
                is Stage.Done -> Text(stringResource(R.string.pairing_done, current.member.name))
                is Stage.Failed -> Text(current.message, color = Danger)
            }
        },
        confirmButton = {
            when (val current = stage) {
                is Stage.Compare -> TextButton(onClick = { answer(true) }) { Text(stringResource(R.string.pairing_match)) }
                is Stage.Done -> TextButton(onClick = { onFinished(current.member) }) { Text(stringResource(R.string.ok)) }
                is Stage.Failed -> TextButton(onClick = { onFinished(null) }) { Text(stringResource(R.string.ok)) }
                else -> {}
            }
        },
        dismissButton = {
            if (stage is Stage.Compare) {
                TextButton(onClick = { answer(false) }) { Text(stringResource(R.string.pairing_mismatch), color = Danger) }
            }
        },
    )
}

@Composable
private fun Busy(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(12.dp))
        Text(text)
    }
}
