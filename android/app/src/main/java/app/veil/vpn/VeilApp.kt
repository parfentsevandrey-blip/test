package app.veil.vpn

import android.app.Application
import app.veil.vpn.core.VeilLog
import app.veil.vpn.data.BridgeRepository
import app.veil.vpn.data.SettingsRepository
import app.veil.vpn.data.StrategyMemory
import app.veil.vpn.net.MoatClient
import app.veil.vpn.net.SocksProxy
import app.veil.vpn.tor.PtRuntime
import app.veil.vpn.tor.StrategyPlanner
import app.veil.vpn.tor.TorController
import app.veil.vpn.vpn.TunnelBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn

/**
 * Holds the pieces that outlive any one screen or service.
 *
 * Deliberately hand-wired rather than injected: the object graph is small, the
 * wiring is the interesting part, and a dependency-injection framework would
 * hide the one thing worth seeing — that the bridge API is reached through the
 * same pluggable transport machinery the tunnel uses.
 */
class VeilApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsRepository(this) }
    val pt by lazy { PtRuntime(this) }
    val tor by lazy { TorController(this) }
    val memory by lazy { StrategyMemory(this) }

    /**
     * When a direct request to the bridge API fails, retry it through meek.
     * Starting lyrebird for this costs little and is the difference between
     * "cannot fetch bridges" and "fetched them through a CDN".
     */
    val moat by lazy {
        MoatClient {
            runCatching {
                SocksProxy.withTransportArgs(
                    host = "127.0.0.1",
                    port = pt.meekPortForFrontedRequests(),
                    args = MEEK_FRONT_ARGS,
                )
            }.getOrNull()
        }
    }

    val bridges by lazy { BridgeRepository(this, moat) }
    val planner by lazy { StrategyPlanner(this, bridges, memory, moat) }

    override fun onCreate() {
        super.onCreate()
        VeilLog.i("app", "Veil ${BuildConfig.VERSION_NAME} starting")

        scope.launch {
            bridges.load()
            memory.load()
        }

        // The Snowflake proxy is a standing preference, not part of a session:
        // it should run whenever the user has asked for it.
        settings.settings
            .map { it.runSnowflakeProxy }
            .distinctUntilChanged()
            .onEach { enabled ->
                if (enabled) {
                    runCatching { pt.startSnowflakeProxy { TunnelBus.noteSnowflakeClient() } }
                        .onFailure { VeilLog.w("app", "snowflake proxy: $it") }
                } else {
                    runCatching { pt.stopSnowflakeProxy() }
                }
            }
            .launchIn(scope)
    }

    private companion object {
        /**
         * The meek endpoint Tor Browser currently ships. Passed to lyrebird in
         * the SOCKS credential fields, exactly as tor would pass them.
         */
        const val MEEK_FRONT_ARGS =
            "url=https://1603026938.rsc.cdn77.org;front=www.phpmyadmin.net;utls=HelloRandomizedALPN"
    }
}
