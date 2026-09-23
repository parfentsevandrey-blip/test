package app.rosa.weather.core.designsystem.haptics

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import app.rosa.weather.core.model.HapticsLevel
import kotlin.random.Random

/**
 * Weather you can feel. Semantic haptics mapped onto the richest primitives the device offers:
 *
 *  1. Android 16+ envelope effects (`BasicEnvelopeBuilder`: intensity *and* sharpness over time)
 *     for thunder rumble and the pull-to-refresh swell;
 *  2. composition primitives (tick, low tick, thud, quick rise/fall) where supported;
 *  3. `HapticFeedbackConstants` on the view as a universally available fallback.
 *
 * Every call respects the user's level ([HapticsLevel.Off] / Subtle / Rich) and the system
 * touch-feedback setting (view-based effects honour it automatically).
 */
@Stable
@SuppressLint("InlinedApi") // API 34 feedback constants are only used via feedback(), which falls back on 13.
class RosaHaptics(private val view: View, private val vibrator: Vibrator?, var level: HapticsLevel) {

    @SuppressLint("WrongConstant") // The array only holds Composition.PRIMITIVE_* values.
    private val primitives: Set<Int> = runCatching {
        val all = intArrayOf(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_THUD,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_SPIN,
        )
        val supported = vibrator?.arePrimitivesSupported(*all) ?: BooleanArray(all.size)
        all.filterIndexed { i, _ -> supported[i] }.toSet()
    }.getOrDefault(emptySet())

    private val envelopes: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && runCatching { vibrator?.areEnvelopeEffectsSupported() == true }.getOrDefault(false)

    private val enabled get() = level != HapticsLevel.Off
    private val rich get() = level == HapticsLevel.Rich

    /** Scrubbing past an hour on the timeline. */
    fun tick() {
        if (!enabled) return
        if (!compose(VibrationEffect.Composition.PRIMITIVE_TICK to if (rich) 0.55f else 0.3f)) {
            feedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Crossing a meaningful boundary: sunrise, sunset, midnight, a new day. */
    fun milestone() {
        if (!enabled) return
        if (!compose(VibrationEffect.Composition.PRIMITIVE_CLICK to 0.85f)) {
            feedback(HapticFeedbackConstants.SEGMENT_TICK, HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** Finger lands on glass. */
    fun press() {
        if (!enabled) return
        if (!compose(VibrationEffect.Composition.PRIMITIVE_LOW_TICK to 0.6f)) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun confirm() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun reject() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    fun toggle(on: Boolean) {
        if (!enabled) return
        feedback(if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF, HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Snapping to a grid cell while resizing a widget preview. */
    fun snap() {
        if (!enabled) return
        if (!compose(VibrationEffect.Composition.PRIMITIVE_TICK to 0.9f, VibrationEffect.Composition.PRIMITIVE_LOW_TICK to 0.4f)) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Pull-to-refresh crossed its threshold. */
    fun thresholdReached() {
        if (!enabled) return
        if (envelopes && rich) {
            vibrate(
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.3f)
                    .addControlPoint(0.7f, 0.8f, 40)
                    .addControlPoint(0f, 0.8f, 60)
                    .build(),
            )
        } else if (!compose(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE to 0.7f)) {
            feedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** The released drop splashes onto the glass (refresh started). */
    fun splash() {
        if (!enabled) return
        if (!compose(VibrationEffect.Composition.PRIMITIVE_THUD to 0.5f, VibrationEffect.Composition.PRIMITIVE_QUICK_FALL to 0.6f)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    /** A drop hitting the pane: light, slightly randomised so rain never feels mechanical. */
    fun raindrop() {
        if (!rich) return
        compose(VibrationEffect.Composition.PRIMITIVE_LOW_TICK to (0.25f + Random.nextFloat() * 0.3f))
    }

    /**
     * Thunder: a sharp crack followed by a long, soft, low rumble. [distance] 0 (overhead) … 1
     * (far) — farther thunder is softer, duller and longer.
     */
    fun thunder(distance: Float) {
        if (!rich) return
        val d = distance.coerceIn(0f, 1f)
        if (envelopes) {
            vibrate(
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.6f - d * 0.3f)
                    .addControlPoint(0.95f - d * 0.45f, 0.5f - d * 0.3f, (30 + d * 60).toLong())
                    .addControlPoint(0.55f - d * 0.3f, 0.15f, (180 + d * 220).toLong())
                    .addControlPoint(0.3f - d * 0.15f, 0.08f, (260 + d * 300).toLong())
                    .addControlPoint(0f, 0.05f, (320 + d * 300).toLong())
                    .build(),
            )
            return
        }
        val composed = compose(
            VibrationEffect.Composition.PRIMITIVE_THUD to 1f - d * 0.5f,
            VibrationEffect.Composition.PRIMITIVE_SPIN to 0.4f - d * 0.2f,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK to 0.3f,
        )
        if (!composed && vibrator?.hasAmplitudeControl() == true) {
            val timings = longArrayOf(0, 40, 120, 220, 300)
            val amps = intArrayOf(0, (230 * (1 - d * 0.5f)).toInt(), 120, 60, 25)
            vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        } else if (!composed) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** View feedback with a fallback for constants introduced in Android 14 (we support 13). */
    private fun feedback(modern: Int, legacy: Int) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) modern else legacy)
    }

    private fun compose(vararg steps: Pair<Int, Float>): Boolean {
        if (steps.any { it.first !in primitives }) return false
        val composition = VibrationEffect.startComposition()
        steps.forEachIndexed { i, (primitive, scale) ->
            composition.addPrimitive(primitive, scale.coerceIn(0f, 1f), if (i == 0) 0 else 18)
        }
        return vibrate(composition.compose())
    }

    private fun vibrate(effect: VibrationEffect): Boolean = runCatching {
        vibrator?.vibrate(effect)
        vibrator != null
    }.getOrDefault(false)
}

val LocalHaptics = staticCompositionLocalOf<RosaHaptics?> { null }

@Composable
fun rememberRosaHaptics(level: HapticsLevel): RosaHaptics {
    val view = LocalView.current
    val haptics = remember(view) {
        val vibrator = runCatching {
            view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        }.getOrNull()?.takeIf { it.hasVibrator() }
        RosaHaptics(view, vibrator, level)
    }
    haptics.level = level
    return haptics
}
