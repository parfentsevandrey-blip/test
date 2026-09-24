package app.opal.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 keys. The back stack is plain state: a list of these. */
@Serializable
sealed interface Dest : NavKey {
    @Serializable data object Onboarding : Dest

    @Serializable data object Home : Dest

    @Serializable data object Apps : Dest

    @Serializable data object Connection : Dest

    @Serializable data object Settings : Dest

    @Serializable data object CustomBridges : Dest

    @Serializable data object ExitCountry : Dest

    @Serializable data object Diagnostics : Dest

    @Serializable data object Licenses : Dest

    @Serializable data object Autostart : Dest

    companion object {
        /** Top-level destinations reachable from the tab bar, in bar order. */
        val tabs: List<Dest> = listOf(Home, Apps, Connection, Settings)
    }
}
