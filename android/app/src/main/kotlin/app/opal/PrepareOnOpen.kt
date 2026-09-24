package app.opal

import app.opal.core.model.tunnel.TunnelState

/**
 * "Prepare on open": Tor starts bootstrapping as soon as the app is on screen, so a tap on
 * "Connect" finds it (nearly) ready. The tunnel stops it again after [TIMEOUT_MS] unless the VPN
 * was started meanwhile, and immediately when the app leaves the screen.
 *
 * Only after onboarding: the first bootstrap may query the Settings API directly, which the user
 * must have been told about.
 */
internal object PrepareOnOpen {
    private const val TIMEOUT_MS = 60_000L

    suspend fun onAppVisible() {
        val settings = AppGraph.settings.current()
        if (!settings.onboardingCompleted || !settings.prepareOnOpen) return
        if (AppGraph.tunnel.snapshot.value.state != TunnelState.Off) return
        AppGraph.tunnel.prewarm(TIMEOUT_MS)
    }

    fun onAppHidden() {
        AppGraph.tunnel.cancelPrewarm()
    }
}
