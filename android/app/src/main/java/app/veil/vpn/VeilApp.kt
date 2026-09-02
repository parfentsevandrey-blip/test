package app.veil.vpn

import android.app.Application
import app.veil.vpn.core.VeilLog
import app.veil.vpn.data.NetworkContext
import app.veil.vpn.data.BridgeRepository
import app.veil.vpn.data.EndpointCooldown
import app.veil.vpn.data.SettingsRepository
import app.veil.vpn.data.StrategyMemory
import app.veil.vpn.data.VpnGateRepository
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

    /**
     * Outlives any one service.
     *
     * Tearing the tunnel down means calling into native code that cannot be
     * interrupted, and a scope tied to the service is cancelled the moment the
     * service is destroyed — which is exactly when the teardown still has work
     * to do. Leaving that work on a process-level scope is what stops a
     * cancelled connect from wedging the app.
     */
    val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings by lazy { SettingsRepository(this) }
    val pt by lazy { PtRuntime(this) }
    val tor by lazy { TorController(this) }
    val memory by lazy { StrategyMemory(this) }

    /**
     * Shared between the probe, which discovers blackholed endpoints, and the
     * planner, which has to stop proposing them.
     */
    val cooldown by lazy { EndpointCooldown() }

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
    val planner by lazy { StrategyPlanner(this, bridges, memory, moat, cooldown) }

    /**
     * The VPN Gate server list. Loaded lazily like the rest: the app is a Tor
     * client that also happens to know about volunteer VPN servers, and a user
     * who never chooses one should never pay for the list.
     */
    val vpnGate by lazy { VpnGateRepository(this) }

    override fun onCreate() {
        super.onCreate()
        VeilLog.i("app", "Veil ${BuildConfig.VERSION_NAME} starting")

        scope.launch {
            bridges.load()
            memory.load()
            // Fetch the country recommendation once, in the background, so the
            // first connect already has the bridges that have no other source
            // — WebTunnel above all. It goes through a fronted request when the
            // direct one is blocked, so it is slow; running it here, off the
            // connect path, is what keeps that slowness from mattering.
            val country = runCatching { NetworkContext.inspect(this@VeilApp).countryIso }.getOrNull()
            if (!country.isNullOrBlank()) {
                runCatching { bridges.refreshCountry(country) }
                    .onSuccess { count -> if (count != null && count > 0) VeilLog.i("app", "prefetched $count country bridge(s)") }
            }
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
