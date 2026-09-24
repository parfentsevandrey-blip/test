package app.opal.core.tunnel

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import app.opal.core.data.AppLocaleStore
import app.opal.core.model.tunnel.TunnelState
import app.opal.core.model.tunnel.isTunnelActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile in the `:tunnel` process: reads the controller state directly. */
class TunnelTileService : TileService() {

    // Per-app language below Android 13 (the system applies it itself on 13+).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleStore.wrap(newBase))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private val controller
        get() = TunnelRuntime.get(this).controller

    override fun onStartListening() {
        observer?.cancel()
        observer = scope.launch { controller.snapshot.collect { render(it.state) } }
    }

    override fun onStopListening() {
        observer?.cancel()
        observer = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        val state = controller.snapshot.value.state
        if (state.isTunnelActive) {
            startService(
                Intent(this, TunnelService::class.java).setAction(TunnelService.ACTION_DISCONNECT)
            )
            return
        }
        if (VpnService.prepare(this) != null) {
            // VPN consent is granted only through an activity.
            openApp()
            return
        }
        try {
            TunnelService.connect(this)
        } catch (_: IllegalStateException) {
            // Background start not allowed on this device state: let the app do it.
            openApp()
        }
    }

    private fun openApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending =
            PendingIntentActivityWrapper(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT, false)
        TileServiceCompat.startActivityAndCollapse(this, pending)
    }

    private fun render(state: TunnelState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        tile.state =
            when {
                state == TunnelState.Connected -> Tile.STATE_ACTIVE
                state.isTunnelActive -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle =
                getString(
                    when {
                        state == TunnelState.Connected -> R.string.tile_connected
                        state.isTunnelActive -> R.string.tile_connecting
                        state == TunnelState.Standby -> R.string.tile_standby
                        else -> R.string.tile_off
                    }
                )
        }
        tile.updateTile()
    }
}
