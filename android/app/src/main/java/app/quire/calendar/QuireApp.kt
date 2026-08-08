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
        fun nightMode(skin: Skin): Int = when (skin) {
            Skin.PAPER -> AppCompatDelegate.MODE_NIGHT_NO
            Skin.INK -> AppCompatDelegate.MODE_NIGHT_YES
            Skin.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}
