package app.opal

import android.annotation.SuppressLint
import android.content.Context
import app.opal.core.data.InstalledAppsRepository
import app.opal.core.data.OpalStores
import app.opal.core.data.SettingsRepository
import app.opal.core.data.TunnelMemoryRepository
import app.opal.core.tunnel.ipc.TunnelClient

/**
 * Manual DI for the UI process. A handful of singletons, created once; no reflection, no annotation
 * processing (see CLAUDE.md ADR 15).
 */
// Holds only application-scoped objects (application context inside TunnelClient).
@SuppressLint("StaticFieldLeak")
object AppGraph {
    lateinit var settings: SettingsRepository
        private set

    lateinit var tunnel: TunnelClient
        private set

    lateinit var apps: InstalledAppsRepository
        private set

    /** Read-only here: written by the `:tunnel` process. */
    lateinit var memory: TunnelMemoryRepository
        private set

    fun init(context: Context) {
        settings = SettingsRepository(OpalStores.settings(context))
        tunnel = TunnelClient(context)
        apps = InstalledAppsRepository(context)
        memory = TunnelMemoryRepository(OpalStores.tunnelMemory(context))
    }
}
