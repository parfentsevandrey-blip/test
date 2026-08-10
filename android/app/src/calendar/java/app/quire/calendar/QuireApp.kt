package app.quire.calendar

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import app.quire.calendar.widget.SchemeWatch

/**
 * The one thing that has to happen before any window opens: telling the platform which mode this
 * app is in, so the system draws the right window background behind the first frame.
 *
 * Compose reads the same preference again and themes itself, but it cannot un-paint a window that
 * has already been laid down light while the app is dark. From Android 12 the framework itself
 * takes a per-app night mode, so there is nothing left for a compatibility layer to do here.
 */
class QuireApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Only when the user has actually chosen. `MODE_NIGHT_AUTO` is not "follow the system" —
        // it is "switch by the time of day", which is a different thing and the wrong one: it
        // takes a phone that is light at ten at night and makes this app dark anyway. Following
        // the system means saying nothing and letting the configuration through.
        val chosen = Prefs.get(this).skin.takeIf { it != Skin.AUTO }
        if (chosen != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(nightMode(chosen))
        }
        // Opening the app is one of the two chances to notice that the phone's colours moved
        // while the widgets on the home screen were holding still.
        SchemeWatch.repaintIfChanged(this)
    }

    /**
     * The other chance, and the one that catches it as it happens: picking new colours reconfigures
     * every running process, so if Quire is on screen when the user changes them the widgets are
     * repainted before they can be looked at again.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        SchemeWatch.repaintIfChanged(this)
    }

    companion object {
        /**
         * The platform's night mode for a skin the user picked.
         *
         * AUTO is deliberately absent: there is no value here that means "follow the system", so
         * following the system is done by not calling the setter at all. COLOUR is a widget skin —
         * a card carrying the accent as its ground — and the app never offers it; it can only
         * arrive here if a widget's value once reached the app's own preference, and it is a dark
         * card, so it answers as one rather than throwing.
         */
        @RequiresApi(Build.VERSION_CODES.R)
        fun nightMode(skin: Skin): Int = when (skin) {
            Skin.PAPER -> UiModeManager.MODE_NIGHT_NO
            Skin.INK, Skin.COLOUR -> UiModeManager.MODE_NIGHT_YES
            Skin.AUTO -> error("AUTO means following the system, which is done by saying nothing")
        }
    }
}
