package app.rosa.calendar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.rosa.calendar.ui.CalendarAppRoot
import app.rosa.calendar.widget.CalendarPreviews
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // The splash dissolves by zooming into the page, as if you stepped into the painting.
        splash.setOnExitAnimationListener { provider ->
            val icon = provider.iconView
            val scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 6f)
            val scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 6f)
            val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
            listOf(scaleX, scaleY, fade).forEach {
                it.duration = 520
                it.interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            }
            fade.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = provider.remove()
            })
            scaleX.start()
            scaleY.start()
            fade.start()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CalendarAppRoot() }
        if (savedInstanceState == null) {
            // The widget picker's preview is drawn while the app is open anyway, once it has settled.
            lifecycleScope.launch(Dispatchers.Default) {
                delay(3_000)
                CalendarPreviews.publish(applicationContext)
            }
        }
    }
}
