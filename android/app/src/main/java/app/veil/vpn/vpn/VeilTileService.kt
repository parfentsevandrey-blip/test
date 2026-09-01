package app.veil.vpn.vpn

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.veil.vpn.model.TunnelState
import app.veil.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.cancel

/**
 * A quick-settings tile, because the most common thing anyone does with a VPN
 * is turn it on and off without opening the app.
 */
class VeilTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var watcher: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val active = CoroutineScope(Dispatchers.Main.immediate)
        scope = active
        watcher = TunnelBus.state.onEach(::render).launchIn(active)
    }

    override fun onStopListening() {
        watcher?.cancel()
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        val state = TunnelBus.state.value
        if (state.isLive || state.isBusy) {
            startService(
                Intent(this, VeilVpnService::class.java)
                    .setAction(VeilVpnService.ACTION_DISCONNECT),
            )
            return
        }

        // Consent has to be granted from an Activity, so send the user there
        // the first time and start directly afterwards.
        if (VpnService.prepare(this) != null) {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return
        }
        startForegroundService(Intent(this, VeilVpnService::class.java))
    }

    private fun render(state: TunnelState) {
        val tile = qsTile ?: return
        tile.state = when {
            state.isLive -> Tile.STATE_ACTIVE
            state.isBusy -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        tile.subtitle = state.activeTransport?.label
        tile.updateTile()
    }
}
