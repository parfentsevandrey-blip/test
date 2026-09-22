package app.papersky.weather

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.papersky.weather.ui.PaperskyRoot
import app.papersky.weather.widget.PaperskyWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Place requested by a widget tap, consumed once by the UI. */
    private val pendingPlace = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Hold the splash until the cached forecasts are in memory (never longer than ~0.7 s),
        // so the first frame already shows the right sky.
        val started = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { !cacheReady && SystemClock.uptimeMillis() - started < 700 }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(360)
                .withEndAction { provider.remove() }
                .start()
        }
        lifecycleScope.launch {
            container.weather.ensureLoaded()
            cacheReady = true
        }

        handle(intent)
        setContent {
            PaperskyRoot(container, pendingPlace, onPlaceHandled = { pendingPlace.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(PaperskyWidget.EXTRA_PLACE_ID)?.let { pendingPlace.value = it }
    }

    @Volatile private var cacheReady = false
}
