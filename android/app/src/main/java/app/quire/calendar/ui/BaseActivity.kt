package app.quire.calendar.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Tokens

/**
 * targetSdk 35 draws every activity edge to edge, so each screen pads itself off
 * the system bars rather than relying on the framework to do it.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var prefs: Prefs
    protected lateinit var palette: Palette
    private var signatureAtStart: String = ""

    /**
     * Settings that are not part of the Android configuration and therefore do
     * not recreate the activity on their own. Screens widen this as needed.
     */
    protected open fun settingsSignature(): String = prefs.accent.key

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs.get(this)
        super.onCreate(savedInstanceState)
        signatureAtStart = settingsSignature()
        palette = Tokens.palette(Tokens.isSystemDark(this), prefs.accent)

        window.decorView.setBackgroundColor(palette.canvas)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = palette.canvas
            window.navigationBarColor =
                if (palette.dark || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    palette.canvas
                } else {
                    palette.ink
                }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
        }
    }

    override fun onResume() {
        super.onResume()
        if (settingsSignature() != signatureAtStart) recreate()
    }

    protected fun padForSystemBars(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            target.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
