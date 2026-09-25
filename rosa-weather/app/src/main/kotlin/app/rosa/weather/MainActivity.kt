package app.rosa.weather

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.ui.RosaAppRoot
import app.rosa.weather.widget.WidgetPreviews
import app.rosa.weather.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var places: PlacesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // The splash dissolves by zooming *into* the drop, as if you fell through the glass.
        splash.setOnExitAnimationListener { provider ->
            val icon = provider.iconView
            val scale = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 7f)
            val scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 7f)
            val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
            listOf(scale, scaleY, fade).forEach {
                it.duration = 520
                it.interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            }
            fade.doOnEndCompat { provider.remove() }
            scale.start()
            scaleY.start()
            fade.start()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RosaAppRoot() }
        // Only a fresh launch: after recreation the intent would drag the user back to that city.
        if (savedInstanceState == null) {
            openPlaceFrom(intent)
            // The widget picker's previews are drawn while the app is open anyway, once it has
            // settled — not whenever a widget wakes the app, where a tap would wait behind them.
            lifecycleScope.launch(Dispatchers.Default) {
                delay(3_000)
                WidgetPreviews.publish(applicationContext)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPlaceFrom(intent)
    }

    /** A tap on a widget pinned to a city opens that city, not whichever one was last viewed. */
    private fun openPlaceFrom(intent: Intent?) {
        val placeId = intent?.getStringExtra(WidgetUpdater.EXTRA_PLACE_ID) ?: return
        lifecycleScope.launch {
            // Never select a city that is gone: the pager would lose track of what is open.
            if (places.snapshot().all.any { it.id == placeId }) places.select(placeId)
        }
    }
}

private fun ObjectAnimator.doOnEndCompat(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = action()
    })
}
