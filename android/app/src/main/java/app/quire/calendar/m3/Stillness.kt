package app.quire.calendar.m3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether this device is currently asking both apps to hold still.
 *
 * Two switches mean the same sentence — the accessibility one ("remove animations") and the
 * battery one — and until now exactly one place in either app listened to either of them:
 * `rememberSkyClock`, which froze the sky and nothing else. Everything else carried on. With
 * animations switched off the page still laid six blocks out on a 70ms stagger, the hero still
 * counted, the curve still drew itself, presses still dipped, months still slid, and the
 * accelerometer still ran at SENSOR_DELAY_GAME — under a setting that exists precisely to stop
 * that.
 *
 * It is read live rather than once. The old code sampled both settings inside `remember(context)`,
 * so a battery saver switched on mid-session never arrived; WebKit's guidance on the same problem
 * in CSS says the same thing — listen for the change, do not sample once.
 */
@Composable
fun rememberStillness(): State<Boolean> {
    val context = LocalContext.current
    val still = remember { mutableStateOf(stillNow(context)) }
    DisposableEffect(context) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                still.value = stillNow(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                still.value = stillNow(context ?: return)
            }
        }
        // Not exported: a system broadcast, and the receiver is torn down with the composition.
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        still.value = stillNow(context)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return still
}

private fun stillNow(context: Context): Boolean {
    val animated = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f
    val saving = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    return !animated || saving
}

/** Whether the surrounding theme is running still. Defaults to false so a preview still moves. */
val LocalStillness = compositionLocalOf { false }

/**
 * The motion scheme for a device that has asked for stillness.
 *
 * Substituted in the theme rather than at each call site, which is what makes the contract one
 * decision instead of forty: every animation in both apps already resolves its spec through
 * `MaterialTheme.motionScheme`, so replacing the scheme stops all of them at once and cannot be
 * forgotten at a new call site.
 *
 * The three spatial specs snap. The three effects specs do **not** — they keep a short linear
 * fade, and that is deliberate rather than sloppy. WCAG 2.3.3 excludes colour and opacity from
 * what it calls motion, and Apple's own guidance is that meaningful motion under Reduce Motion is
 * *replaced*, not deleted. The crossfades are what keep a value that changed from changing behind
 * your back; cutting them too would take the protection against change blindness away along with
 * the decoration, which is the opposite of what the setting asked for.
 */
object CalmMotionScheme : MotionScheme {
    private val Snap: FiniteAnimationSpec<Any> = snap()
    private val Fade: FiniteAnimationSpec<Any> = tween(80, easing = LinearEasing)

    @Suppress("UNCHECKED_CAST")
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = Snap as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = Snap as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = Snap as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = Fade as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = Fade as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = Fade as FiniteAnimationSpec<T>
}
