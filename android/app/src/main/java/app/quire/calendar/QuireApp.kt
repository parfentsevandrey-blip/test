package app.quire.calendar

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin

class QuireApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(nightMode(Prefs.get(this).skin))
    }

    companion object {
        // COLOUR is a widget skin — a card that carries the accent as its own ground — and the
        // app never offers it, so it can only be seen here if a widget's value ever reached the
        // app's own preference. It is a dark card, so it answers as one rather than throwing.
        fun nightMode(skin: Skin): Int = when (skin) {
            Skin.PAPER -> AppCompatDelegate.MODE_NIGHT_NO
            Skin.INK, Skin.COLOUR -> AppCompatDelegate.MODE_NIGHT_YES
            Skin.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}
