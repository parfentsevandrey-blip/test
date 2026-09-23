package app.rosa.weather

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.rosa.weather.ui.RosaAppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
    }
}

private fun ObjectAnimator.doOnEndCompat(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = action()
    })
}
