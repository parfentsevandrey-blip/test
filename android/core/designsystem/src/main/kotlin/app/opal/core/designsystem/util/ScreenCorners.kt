package app.opal.core.designsystem.util

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Physical screen corner radius (API 31+ `WindowInsets.getRoundedCorner`), else a typical value.
 */
@Composable
fun rememberScreenCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var radius by remember { mutableStateOf(DEFAULT) }
    DisposableEffect(view, density) {
        fun read() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            val px =
                view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius
                    ?: 0
            if (px > 0) radius = with(density) { px.toDp() }
        }
        val listener =
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = read()

                override fun onViewDetachedFromWindow(v: View) = Unit
            }
        if (view.isAttachedToWindow) read() else view.addOnAttachStateChangeListener(listener)
        onDispose { view.removeOnAttachStateChangeListener(listener) }
    }
    return radius
}

private val DEFAULT = 32.dp
