package app.opal

import android.content.Context
import app.opal.core.data.InstalledAppsRepository
import app.opal.core.data.OpalStores
import app.opal.core.data.SettingsRepository
import app.opal.core.tunnel.ipc.TunnelClient

/**
 * Manual DI for the UI process. A handful of singletons, created once; no reflection, no annotation
 * processing (see CLAUDE.md ADR 15).
 */
object AppGraph {
    lateinit var settings: SettingsRepository
        private set

    lateinit var tunnel: TunnelClient
        private set

    lateinit var apps: InstalledAppsRepository
        private set

    fun init(context: Context) {
        settings = SettingsRepository(OpalStores.settings(context))
        tunnel = TunnelClient(context)
        apps = InstalledAppsRepository(context)
    }
}
