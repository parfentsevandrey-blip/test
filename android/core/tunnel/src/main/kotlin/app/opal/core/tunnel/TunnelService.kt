package app.opal.core.tunnel

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.opal.core.model.settings.SplitTunnelMode
import app.opal.core.model.settings.SplitTunnelSettings
import app.opal.core.model.tunnel.TunnelSnapshot
import app.opal.core.tunnel.hev.HevTunnel
import app.opal.core.tunnel.ipc.TunnelBinder
import app.opal.core.tunnel.notify.TunnelNotifications
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * The VPN service, running in the `:tunnel` process (android:process=":tunnel") so a UI crash never
 * takes the tunnel down. It owns the Android side — VPN interface, foreground state, notification —
 * and delegates everything else to [TunnelController].
 */
class TunnelService : VpnService() {

    private val runtime by lazy { TunnelRuntime.get(this) }
    private val controller: TunnelController
        get() = runtime.controller

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: TunnelNotifications
    private var binder: TunnelBinder? = null
    private var foregroundType = 0

    override fun onCreate() {
        super.onCreate()
        notifications = TunnelNotifications(this).also { it.ensureChannel() }
        controller.host = vpnHost
        scope.launch { observeNotification() }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeNotification() {
        // Speed changes every second; the notification is refreshed at most every 3 s.
        combine(controller.snapshot, controller.traffic.sample(3.seconds)) { s, t -> s to t }
            .distinctUntilChanged()
            .collect { (snapshot, traffic) ->
                if (foregroundType != 0)
                    notifications.notify(notifications.build(snapshot, traffic))
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT ->
                scope.launch {
                    runtime.memory.update { it.copy(vpnWanted = false) }
                    controller.disconnect()
                }
            ACTION_STOP_STANDBY ->
                scope.launch { runtime.settings.update { it.copy(hotStandby = false) } }
            ACTION_NEW_IDENTITY -> scope.launch { controller.newIdentity() }
            ACTION_CONNECT,
            SERVICE_INTERFACE -> startTunnel()
            null -> {
                // Restarted by the system (START_STICKY / always-on): restore the user's intent.
                scope.launch {
                    if (runtime.memory.current().vpnWanted) startTunnel()
                    else if (controller.holds.value.isEmpty()) stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startTunnel() {
        // Must be foreground within seconds of startForegroundService(); do it before anything
        // else.
        goForeground(controller.snapshot.value)
        if (prepare(this) != null) {
            // Consent missing (revoked in Settings): the UI will ask again.
            controller.log.w(TAG, "VPN consent missing")
            scope.launch { controller.revoked() }
            return
        }
        scope.launch {
            runtime.memory.update { it.copy(vpnWanted = true) }
            controller.connect()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == SERVICE_INTERFACE) return super.onBind(intent)
        return binder ?: TunnelBinder(controller, runtime.scope).also { binder = it }
    }

    override fun onRevoke() {
        controller.log.w(TAG, "VPN revoked by the system")
        scope.launch {
            runtime.memory.update { it.copy(vpnWanted = false) }
            controller.revoked()
        }
    }

    override fun onDestroy() {
        if (controller.host === vpnHost) controller.host = null
        scope.cancel()
        super.onDestroy()
    }

    // --- VpnHost ------------------------------------------------------------------------------

    private val vpnHost =
        object : VpnHost {
            override fun establish(spec: VpnSpec): ParcelFileDescriptor? = establishInterface(spec)

            override fun setUnderlyingNetwork(network: Network?) {
                setUnderlyingNetworks(network?.let { arrayOf(it) })
            }

            override fun onHoldsChanged(holds: Set<TunnelController.Hold>) = updateForeground(holds)

            override fun restartProcess() {
                // START_STICKY (and always-on, if enabled) bring the service back; vpnWanted
                // decides.
                Process.killProcess(Process.myPid())
            }
        }

    private fun establishInterface(spec: VpnSpec): ParcelFileDescriptor? {
        if (prepare(this) != null) return null
        val builder =
            Builder()
                .setSession(getString(R.string.vpn_session_name))
                .setMtu(HevTunnel.MTU)
                .addAddress(HevTunnel.TUN_ADDRESS_V4, 32)
                .addAddress(HevTunnel.TUN_ADDRESS_V6, 128)
                // Everything, IPv4 and IPv6: nothing may bypass the tunnel.
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer(HevTunnel.DNS_ADDRESS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        spec.underlying?.let { builder.setUnderlyingNetworks(arrayOf(it)) }
        configureApps(builder, spec.splitTunnel)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setConfigureIntent(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }
        return try {
            builder.establish()
        } catch (e: IllegalStateException) {
            controller.log.e(TAG, "establish failed: ${e.message}")
            null
        } catch (e: SecurityException) {
            controller.log.e(TAG, "establish refused: ${e.message}")
            null
        }
    }

    /**
     * Our own app is always outside the tunnel (Tor and the transports must reach the network
     * directly, otherwise traffic would loop). In "only selected" mode an empty selection falls
     * back to "everything except us" — an empty allow-list would otherwise mean *all* apps,
     * including this one.
     */
    private fun configureApps(builder: Builder, split: SplitTunnelSettings) {
        when (split.mode) {
            SplitTunnelMode.AllExcept -> {
                builder.addDisallowedApplication(packageName)
                for (pkg in split.excluded) if (pkg != packageName) builder.tryDisallow(pkg)
            }
            SplitTunnelMode.OnlySelected -> {
                val added = split.included.count { it != packageName && builder.tryAllow(it) }
                if (added == 0) builder.addDisallowedApplication(packageName)
            }
        }
    }

    private fun Builder.tryDisallow(pkg: String): Boolean =
        try {
            addDisallowedApplication(pkg)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun Builder.tryAllow(pkg: String): Boolean =
        try {
            addAllowedApplication(pkg)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun updateForeground(holds: Set<TunnelController.Hold>) {
        scope.launch {
            val needsForeground =
                TunnelController.Hold.Vpn in holds || TunnelController.Hold.Standby in holds
            if (needsForeground) {
                goForeground(controller.snapshot.value)
            } else if (foregroundType != 0) {
                ServiceCompat.stopForeground(
                    this@TunnelService,
                    ServiceCompat.STOP_FOREGROUND_REMOVE,
                )
                foregroundType = 0
            }
            if (holds.isEmpty()) stopSelf()
        }
    }

    /**
     * `systemExempted` is the type for VPN apps (the user granted VPN consent). If the system
     * refuses it (e.g. hot standby without VPN consent on some builds) fall back to `specialUse`,
     * which has no daily time limit (unlike `dataSync` on Android 15+).
     */
    private fun goForeground(snapshot: TunnelSnapshot) {
        val notification = notifications.build(snapshot, controller.traffic.value)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(TunnelNotifications.NOTIFICATION_ID, notification)
            foregroundType = FOREGROUND_LEGACY
            return
        }
        for (type in
            listOf(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )) {
            try {
                ServiceCompat.startForeground(
                    this,
                    TunnelNotifications.NOTIFICATION_ID,
                    notification,
                    type,
                )
                foregroundType = type
                return
            } catch (e: ForegroundServiceStartNotAllowedException) {
                controller.log.w(TAG, "Foreground type $type refused: ${e.message}")
            } catch (e: SecurityException) {
                controller.log.w(TAG, "Foreground type $type refused: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "service"
        private const val FOREGROUND_LEGACY = -1

        const val ACTION_CONNECT = "app.opal.tunnel.CONNECT"
        const val ACTION_DISCONNECT = "app.opal.tunnel.DISCONNECT"
        const val ACTION_NEW_IDENTITY = "app.opal.tunnel.NEW_IDENTITY"
        const val ACTION_STOP_STANDBY = "app.opal.tunnel.STOP_STANDBY"
        /** Binding action for the AIDL control interface (anything but SERVICE_INTERFACE). */
        const val ACTION_BIND_CONTROL = "app.opal.tunnel.BIND_CONTROL"

        /** Starts the VPN. The caller must have obtained consent via [VpnService.prepare]. */
        fun connect(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TunnelService::class.java).setAction(ACTION_CONNECT),
            )
        }

        fun controlIntent(context: Context): Intent =
            Intent(context, TunnelService::class.java).setAction(ACTION_BIND_CONTROL)
    }
}
