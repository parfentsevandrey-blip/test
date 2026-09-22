package app.papersky.weather.design

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import kotlin.random.Random

/**
 * Tactile vocabulary of the app.
 *
 * UI feedback goes through [View.performHapticFeedback] (respects the system "touch feedback"
 * switch and uses the device-tuned effects of Android 14+). Weather "textures" — raindrops on the
 * palm, a thunder rumble, a cloud puff — are composed from vibration primitives, and on Android 16+
 * devices with envelope support the thunder is a proper low, swelling rumble.
 */
class Haptics(context: Context) {
    var enabled: Boolean = true
    var strength: Float = 1f

    private val vibrator: Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.takeIf { it.hasVibrator() }

    private val primitives: Boolean by lazy {
        vibrator?.areAllPrimitivesSupported(
            Composition.PRIMITIVE_TICK, Composition.PRIMITIVE_CLICK, Composition.PRIMITIVE_LOW_TICK,
            Composition.PRIMITIVE_QUICK_RISE, Composition.PRIMITIVE_QUICK_FALL,
        ) == true
    }

    private val thud: Boolean by lazy { vibrator?.areAllPrimitivesSupported(Composition.PRIMITIVE_THUD) == true }
    private val spin: Boolean by lazy { vibrator?.areAllPrimitivesSupported(Composition.PRIMITIVE_SPIN) == true }

    private val envelopes: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && vibrator?.areEnvelopeEffectsSupported() == true
    }

    // --- Semantic UI feedback --------------------------------------------------------------

    fun tick(view: View) = perform(view, if (sdk34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun softTick(view: View) = perform(view, if (sdk34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.TEXT_HANDLE_MOVE)
    fun confirm(view: View) = perform(view, HapticFeedbackConstants.CONFIRM)
    fun reject(view: View) = perform(view, HapticFeedbackConstants.REJECT)
    fun press(view: View) = perform(view, HapticFeedbackConstants.VIRTUAL_KEY)
    fun toggle(view: View, on: Boolean) = perform(
        view,
        if (sdk34) (if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF) else HapticFeedbackConstants.CONTEXT_CLICK,
    )
    fun threshold(view: View) = perform(view, if (sdk34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.LONG_PRESS)
    fun dragStart(view: View) = perform(view, if (sdk34) HapticFeedbackConstants.DRAG_START else HapticFeedbackConstants.LONG_PRESS)
    fun gestureEnd(view: View) = perform(view, HapticFeedbackConstants.GESTURE_END)

    private fun perform(view: View, constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }

    // --- Weather textures ------------------------------------------------------------------

    /** One raindrop on the fingertip — light and slightly random. */
    fun raindrop() = compose {
        add(Composition.PRIMITIVE_TICK, (0.12f + Random.nextFloat() * 0.3f) * strength)
    }

    fun snowflake() = compose { add(Composition.PRIMITIVE_TICK, 0.08f * strength) }

    /** Tapping a cloud: a soft inhale-exhale. */
    fun puff() = compose {
        add(Composition.PRIMITIVE_QUICK_RISE, 0.35f * strength)
        add(Composition.PRIMITIVE_QUICK_FALL, 0.25f * strength, 20)
    }

    /** Flicking the sun. */
    fun whirl() {
        if (spin) compose { add(Composition.PRIMITIVE_SPIN, 0.55f * strength) }
        else compose { add(Composition.PRIMITIVE_QUICK_RISE, 0.5f * strength) }
    }

    /** Releasing the pull-cord: a click with a little spring-back. */
    fun cordRelease() = compose {
        add(Composition.PRIMITIVE_CLICK, 0.8f * strength)
        add(Composition.PRIMITIVE_TICK, 0.3f * strength, 70)
        add(Composition.PRIMITIVE_TICK, 0.15f * strength, 60)
    }

    /** Distant thunder; [closeness] 0..1 makes it sharper and stronger. */
    fun thunder(closeness: Float = 0.6f) {
        if (!enabled) return
        val v = vibrator ?: return
        val k = closeness.coerceIn(0f, 1f) * strength
        if (envelopes && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val effect = VibrationEffect.BasicEnvelopeBuilder()
                .setInitialSharpness(0.1f)
                .addControlPoint((0.9f * k).coerceIn(0.05f, 1f), 0.15f, 60)
                .addControlPoint((0.55f * k).coerceIn(0.05f, 1f), 0.05f, 180)
                .addControlPoint((0.7f * k).coerceIn(0.05f, 1f), 0.1f, 120)
                .addControlPoint(0f, 0f, 420)
                .build()
            vibrate(v, effect)
            return
        }
        if (primitives) {
            compose {
                if (thud) add(Composition.PRIMITIVE_THUD, 0.9f * k) else add(Composition.PRIMITIVE_CLICK, 0.9f * k)
                add(Composition.PRIMITIVE_LOW_TICK, 0.7f * k, 90)
                add(Composition.PRIMITIVE_LOW_TICK, 0.5f * k, 110)
                add(Composition.PRIMITIVE_LOW_TICK, 0.3f * k, 140)
            }
            return
        }
        if (v.hasAmplitudeControl()) {
            val amps = intArrayOf(0, (220 * k).toInt(), (120 * k).toInt(), (170 * k).toInt(), (60 * k).toInt(), 0)
            vibrate(v, VibrationEffect.createWaveform(longArrayOf(0, 60, 120, 90, 220, 10), amps, -1))
        }
    }

    private inline fun compose(block: Composition.() -> Unit) {
        if (!enabled || strength <= 0.01f) return
        val v = vibrator ?: return
        if (!primitives) return
        val effect = VibrationEffect.startComposition().apply(block).compose()
        vibrate(v, effect)
    }

    private fun Composition.add(id: Int, scale: Float, delay: Int = 0) {
        addPrimitive(id, scale.coerceIn(0f, 1f), delay)
    }

    private fun vibrate(v: Vibrator, effect: VibrationEffect) {
        // A texture is never worth a crash (e.g. an envelope outside a motor's limits).
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // USAGE_TOUCH honours the user's "touch feedback" setting.
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            }
        } catch (_: IllegalArgumentException) {
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        val sdk34 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics not provided") }

/** Haptics bound to the current view, so callers can simply say `haptic.tick()`. */
class ViewHaptics(val engine: Haptics, private val view: View) {
    fun tick() = engine.tick(view)
    fun softTick() = engine.softTick(view)
    fun confirm() = engine.confirm(view)
    fun reject() = engine.reject(view)
    fun press() = engine.press(view)
    fun toggle(on: Boolean) = engine.toggle(view, on)
    fun threshold() = engine.threshold(view)
    fun dragStart() = engine.dragStart(view)
    fun gestureEnd() = engine.gestureEnd(view)
}

@Composable
fun rememberHaptics(): ViewHaptics {
    val engine = LocalHaptics.current
    val view = LocalView.current
    return remember(engine, view) { ViewHaptics(engine, view) }
}
