package dev.halo.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.halo.app.Halo
import dev.halo.app.R
import dev.halo.core.DeviceInfo
import dev.halo.core.MemberInfo
import dev.halo.core.PeerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The steps of adding a device. */
private sealed interface Adding {
    data object Choose : Adding
    data object Scan : Adding
    data class Pair(val code: String) : Adding
    data object Manual : Adding
}

@Composable
fun HaloScreen(onConnect: () -> Unit, onDisconnect: () -> Unit, onMembersChanged: () -> Unit) {
    val running by Halo.running.collectAsStateWithLifecycle()
    val peers by Halo.peers.collectAsStateWithLifecycle()
    val members by Halo.members.collectAsStateWithLifecycle()
    val error by Halo.error.collectAsStateWithLifecycle()
    var device by remember { mutableStateOf<DeviceInfo?>(null) }
    var adding by remember { mutableStateOf<Adding?>(null) }
    val context = LocalContext.current
    var removing by remember { mutableStateOf<MemberInfo?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                device = Halo.device()
                Halo.reloadMembers()
            }.onFailure { Halo.reportError(it.message) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .safeDrawingPadding(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header() }
            item { PowerButton(running, onConnect, onDisconnect) }
            item { device?.let { DeviceCard(it) } }
            item { SectionTitle(stringResource(R.string.devices)) }
            if (members.isEmpty()) {
                item { Hint(stringResource(R.string.no_devices)) }
            }
            items(members, key = { it.id }) { member ->
                MemberRow(member, peers.find { it.id == member.id }, running) { removing = member }
            }
            item {
                OutlinedButton(onClick = { adding = Adding.Choose }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_device))
                }
            }
            error?.let { message -> item { Text(message, color = Danger, fontSize = 14.sp) } }
        }
        if (adding == Adding.Scan) {
            QrScanner(onCode = { adding = Adding.Pair(it) }, onClose = { adding = null })
        }
    }

    when (val step = adding) {
        Adding.Choose -> AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(stringResource(R.string.add_device)) },
            text = {
                Column {
                    Text(stringResource(R.string.add_how), color = Muted, fontSize = 14.sp)
                    TextButton(onClick = { adding = Adding.Scan }) { Text(stringResource(R.string.scan_qr)) }
                    TextButton(onClick = {
                        val code = paste(context)
                        if (code.startsWith("HALO/1/", ignoreCase = true)) {
                            adding = Adding.Pair(code)
                        } else {
                            Toast.makeText(context, R.string.no_code_in_clipboard, Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.paste_code)) }
                    TextButton(onClick = { adding = Adding.Manual }) { Text(stringResource(R.string.enter_id)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = null }) { Text(stringResource(R.string.cancel)) } },
        )
        is Adding.Pair -> PairingDialog(step.code) { member ->
            adding = null
            if (member != null) {
                Halo.reportError(null)
                onMembersChanged()
            }
        }
        Adding.Manual -> AddDeviceDialog(
            onDismiss = { adding = null },
            onAdd = { id, name, address ->
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { Halo.addDevice(id, name, address) } }
                        .onSuccess {
                            adding = null
                            Halo.reportError(null)
                            onMembersChanged()
                        }
                        .onFailure { Halo.reportError(it.message) }
                }
            },
        )
        Adding.Scan, null -> {}
    }
    removing?.let { member ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text(stringResource(R.string.remove_question, member.name)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { Halo.removeDevice(member.name) } }
                            .onSuccess { onMembersChanged() }
                            .onFailure { Halo.reportError(it.message) }
                    }
                }) { Text(stringResource(R.string.remove), color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.app_name), fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(stringResource(R.string.tagline), fontSize = 15.sp, color = Muted)
    }
}

/** The halo: a ring that glows and breathes while the network is up. */
@Composable
private fun PowerButton(running: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "glow",
    )
    val glow = if (running) pulse else 0.15f
    val ring = if (running) Accent else Muted
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(188.dp)
                .clip(CircleShape)
                .clickable { if (running) onDisconnect() else onConnect() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                drawCircle(
                    Brush.radialGradient(
                        0.55f to Color.Transparent,
                        0.78f to ring.copy(alpha = 0.35f * glow),
                        1f to Color.Transparent,
                        radius = radius,
                    ),
                )
                drawCircle(ring.copy(alpha = 0.25f + 0.75f * glow), radius = radius * 0.62f, style = Stroke(width = 6.dp.toPx()))
            }
            Text(
                stringResource(if (running) R.string.disconnect else R.string.connect),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            stringResource(if (running) R.string.connected else R.string.disconnected),
            color = if (running) Online else Muted,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo) {
    val context = LocalContext.current
    Panel {
        Text(stringResource(R.string.this_device), color = Muted, fontSize = 13.sp)
        Text(device.ip, color = Color.White, fontSize = 26.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.device_id), color = Muted, fontSize = 13.sp)
        Text(device.id, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copy(context, device.id) }) { Text(stringResource(R.string.copy)) }
            Button(onClick = { share(context, device.id) }) { Text(stringResource(R.string.share)) }
        }
    }
}

@Composable
private fun MemberRow(member: MemberInfo, peer: PeerState?, running: Boolean, onRemove: () -> Unit) {
    val online = running && peer?.connected == true
    val status = when {
        !running -> stringResource(R.string.offline)
        online && peer?.path != null && peer.rttMs != null ->
            stringResource(R.string.online_direct_rtt, peer.path!!, peer.rttMs!!.toInt())
        online && peer?.path != null -> stringResource(R.string.online_direct, peer.path!!)
        else -> stringResource(R.string.searching)
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (online) Online else Muted.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(member.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(member.ip, color = Accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Text(status, color = if (online) Online else Muted, fontSize = 13.sp)
                if (member.publicAddrs.isNotEmpty()) {
                    Text(stringResource(R.string.reachable_outside), color = Muted, fontSize = 12.sp)
                }
            }
            TextButton(onClick = onRemove) { Text("✕", color = Muted) }
        }
    }
}

@Composable
private fun AddDeviceDialog(onDismiss: () -> Unit, onAdd: (String, String, String?) -> Unit) {
    val context = LocalContext.current
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_device)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.trim() },
                    label = { Text(stringResource(R.string.device_id)) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { id = paste(context) }) { Text(stringResource(R.string.paste)) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.lowercase().replace(' ', '-') },
                    label = { Text(stringResource(R.string.name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    label = { Text(stringResource(R.string.address_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = id.isNotEmpty() && name.isNotEmpty(), onClick = { onAdd(id, name, address) }) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Muted, fontSize = 14.sp)
}

private fun copy(context: Context, id: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("Halo ID", id))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

private fun paste(context: Context): String =
    context.getSystemService(ClipboardManager::class.java)
        ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()

private fun share(context: Context, id: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, id) + "\n\nhalo add $id --name phone")
    context.startActivity(Intent.createChooser(send, null))
}
