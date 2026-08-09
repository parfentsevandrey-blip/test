package app.quire.calendar

import android.app.Application
import android.app.UiModeManager
import android.os.Build
import androidx.annotation.RequiresApi
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin

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
