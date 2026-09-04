package app.veil.vpn

import android.app.Application
import app.veil.vpn.core.VeilLog
import app.veil.vpn.data.NetworkContext
import app.veil.vpn.data.BridgeRepository
import app.veil.vpn.data.EndpointCooldown
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private var parkTimer: Job? = null

    /**
     * Puts the engine to sleep on disconnect instead of destroying it.
     *
     * Almost none of the time a connect takes is spent starting tor or the
     * transports. It is spent fetching and validating the directory consensus,
     * and a process that has already done that can be pointed at a route with a
     * single control-port command. Killing it on every disconnect meant paying
     * that cost again every time, including when the user reconnected ten
     * seconds later — which, on a network that keeps cutting the tunnel, is
     * exactly what they spend their day doing.
     *
     * So the process is kept, with its network off and no listeners open, and
     * torn down for real only if nothing has come back for a while. The window
     * is short on purpose: a tor sitting idle is memory and a little battery,
     * and a user who has disconnected and walked away should not be paying for
     * a reconnection they are not going to make.
     */
    fun parkEngine() {
        parkTimer?.cancel()
        parkTimer = teardownScope.launch {
            // First the link is held, not just the process. The tunnel is down
            // — no traffic from the phone goes anywhere near tor — but tor's own
            // connection to its bridge, and the circuit through it, are left
            // running for a short while. A reconnect inside that window is what
            // a commercial client's reconnect feels like, and for the first
            // time honestly so: the circuit is already there, the very first
            // stream goes straight through, and the green screen means it.
            // Most reconnects are this one — a toggle off and back on, or a
            // tunnel that dropped and is being brought back — and rebuilding a
            // Snowflake link for each of them was half a minute every time.
            // The window is short because a held link is a little idle traffic
            // on a metered connection, and a user who has really left should
            // not be paying for it.
            if (tor.isRunning) {
                VeilLog.i("app", "holding the link for ${LINK_HOLD_MILLIS / 1000}s in case of a reconnect")
                delay(LINK_HOLD_MILLIS)
            }
            // Nothing came back: drop the link but keep the process, with the
            // directory it fetched, so a later reconnect still skips that part
            // even though it has to find its bridge again.
            val parked = runCatching { tor.park() }.getOrDefault(false)
            if (!parked) {
                runCatching { tor.stop() }
                runCatching { pt.stopAll() }
                return@launch
            }
            delay(WARM_WINDOW_MILLIS)
            VeilLog.i("app", "nothing reconnected; shutting the engine down")
            runCatching { tor.stop() }
            runCatching { pt.stopAll() }
        }
    }

    /** Called when a connect starts, so a parked engine is not shut down mid-use. */
    fun wakeEngine() {
        parkTimer?.cancel()
        parkTimer = null
    }

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
         * How long a parked engine is kept before it is really shut down.
         *
         * Long enough to cover the thing this exists for — a tunnel that drops
         * and is reconnected, or a user who cancels and immediately tries
         * again — and short enough that a phone put in a pocket is not running
         * a tor process for the rest of the afternoon.
         */
        const val WARM_WINDOW_MILLIS = 10 * 60 * 1000L

        /**
         * How long the link to the bridge stays up after a disconnect, before
         * the process is parked with its network off.
         *
         * Long enough for the reconnects that actually happen — a toggle, a
         * drop, a change of mind — and short enough that the idle padding a
         * live Tor connection sends is a few tens of kilobytes at most.
         */
        const val LINK_HOLD_MILLIS = 45 * 1000L

        /**
         * The meek endpoint Tor Browser currently ships. Passed to lyrebird in
         * the SOCKS credential fields, exactly as tor would pass them.
         */
        const val MEEK_FRONT_ARGS =
            "url=https://1603026938.rsc.cdn77.org;front=www.phpmyadmin.net;utls=HelloRandomizedALPN"
    }
}
