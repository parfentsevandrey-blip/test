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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java)
                ?.setApplicationNightMode(nightMode(Prefs.get(this).skin))
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
        // COLOUR is a widget skin — a card that carries the accent as its own ground — and the
        // app never offers it, so it can only be seen here if a widget's value ever reached the
        // app's own preference. It is a dark card, so it answers as one rather than throwing.
        @RequiresApi(Build.VERSION_CODES.R)
        fun nightMode(skin: Skin): Int = when (skin) {
            Skin.PAPER -> UiModeManager.MODE_NIGHT_NO
            Skin.INK, Skin.COLOUR -> UiModeManager.MODE_NIGHT_YES
            Skin.AUTO -> UiModeManager.MODE_NIGHT_AUTO
        }
    }
}
