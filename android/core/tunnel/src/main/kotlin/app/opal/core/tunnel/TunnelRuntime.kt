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
import java.util.TimeZone
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
    private val regionalSnowflake =
        app.assets.open("snowflake_regional.json").use {
            BuiltinBridges.parseRegionalSnowflake(it.readBytes().decodeToString())
        }

    val controller =
        TunnelController(
            scope = scope,
            settingsRepo = settings,
            memoryRepo = memory,
            engine = CTorEngine(files, scope, debuggable),
            transports = transports,
            hev = HevTunnel(app),
            catalog =
                BridgeCatalog(bundledBridges, regionalSnowflake, { countryHint(app) }) {
                    // adb shell run-as app.opal.debug touch files/debug_break_snowflake
                    debuggable && java.io.File(app.filesDir, "debug_break_snowflake").exists()
                },
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

        /**
         * Country for the Settings API and the regional Snowflake set (no permission needed):
         * mobile network, else SIM, else a Russian time zone (Wi-Fi-only devices), else locale.
         */
        private fun countryHint(context: Context): String? {
            val tm = context.getSystemService(TelephonyManager::class.java)
            fun valid(cc: String?) = cc?.takeIf { it.length == 2 }?.lowercase()
            return valid(tm?.networkCountryIso)
                ?: valid(tm?.simCountryIso)
                ?: "ru".takeIf { TimeZone.getDefault().id in RUSSIAN_TIME_ZONES }
                ?: valid(Locale.getDefault().country)
        }

        /** tzdata zone1970.tab entries whose country codes include RU. */
        private val RUSSIAN_TIME_ZONES =
            setOf(
                "Europe/Kaliningrad",
                "Europe/Moscow",
                "Europe/Kirov",
                "Europe/Volgograd",
                "Europe/Astrakhan",
                "Europe/Saratov",
                "Europe/Ulyanovsk",
                "Europe/Samara",
                "Europe/Simferopol",
                "Asia/Yekaterinburg",
                "Asia/Omsk",
                "Asia/Novosibirsk",
                "Asia/Barnaul",
                "Asia/Tomsk",
                "Asia/Novokuznetsk",
                "Asia/Krasnoyarsk",
                "Asia/Irkutsk",
                "Asia/Chita",
                "Asia/Yakutsk",
                "Asia/Khandyga",
                "Asia/Vladivostok",
                "Asia/Ust-Nera",
                "Asia/Magadan",
                "Asia/Sakhalin",
                "Asia/Srednekolymsk",
                "Asia/Kamchatka",
                "Asia/Anadyr",
            )
    }
}
