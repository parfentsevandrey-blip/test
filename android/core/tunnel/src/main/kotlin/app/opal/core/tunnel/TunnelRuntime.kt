package app.opal.core.tunnel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.telephony.TelephonyManager
import app.opal.core.data.OpalStores
import app.opal.core.data.SettingsRepository
import app.opal.core.data.TunnelMemoryRepository
import app.opal.core.model.bridge.BuiltinBridges
import app.opal.core.tunnel.bridges.BridgeCatalog
import app.opal.core.tunnel.bridges.MoatClient
import app.opal.core.tunnel.hev.HevTunnel
import app.opal.core.tunnel.net.NetworkMonitor
import app.opal.core.tunnel.pt.Transports
import app.opal.core.tunnel.tor.CTorEngine
import app.opal.core.tunnel.tor.TorFiles
import app.opal.core.tunnel.util.LogBuffer
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI graph of the `:tunnel` process (one per process, created on first use by the VPN
 * service, the tile or the binder). Nothing here is touched by the UI process.
 */
internal class TunnelRuntime private constructor(context: Context) {
    private val app = context.applicationContext

    val debuggable = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val log = LogBuffer(mirrorToLogcat = debuggable)
    val files = TorFiles(app)
    val settings = SettingsRepository(OpalStores.settings(app))
    val memory = TunnelMemoryRepository(OpalStores.tunnelMemory(app))
    val network = NetworkMonitor(app, scope)
    private val transports = Transports(files.transportState, debuggable)
    private val bundledBridges =
        app.assets.open("pt_config.json").use {
            BuiltinBridges.parsePtConfig(it.readBytes().decodeToString())
        }

    val controller =
        TunnelController(
            scope = scope,
            settingsRepo = settings,
            memoryRepo = memory,
            engine = CTorEngine(files, scope, debuggable),
            transports = transports,
            hev = HevTunnel(app),
            catalog = BridgeCatalog(bundledBridges),
            moat = MoatClient(transports),
            files = files,
            network = network,
            log = log,
            countryHint = { countryHint(app) },
            versionCode = versionCode(app),
            debuggable = debuggable,
        )

    companion object {
        // Holds only the application context.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: TunnelRuntime? = null

        fun get(context: Context): TunnelRuntime =
            instance
                ?: synchronized(this) { instance ?: TunnelRuntime(context).also { instance = it } }

        private fun versionCode(context: Context): Long {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else @Suppress("DEPRECATION") info.versionCode.toLong()
        }

        /** Country for the Settings API when asking through Tor (no permission needed). */
        private fun countryHint(context: Context): String? {
            val tm = context.getSystemService(TelephonyManager::class.java)
            val candidates =
                listOf(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
            return candidates.firstOrNull { !it.isNullOrBlank() && it.length == 2 }?.lowercase()
        }
    }
}
