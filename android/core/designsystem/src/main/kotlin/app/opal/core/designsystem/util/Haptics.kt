package app.opal.core.designsystem.util

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Tactile feedback for state changes. Uses rich primitives (VibrationEffect.Composition) where the
 * vibrator supports them, otherwise the platform's haptic constants. Honors the user's haptic
 * settings (the system mutes both paths when touch feedback is off).
 */
class Haptics(private val view: View) {
    private val vibrator: Vibrator? = view.context.getSystemService(Vibrator::class.java)

    /** Light tick for taps on glass controls. */
    fun tap() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    /** The lightest tick: the tab bar lens crossing into another tab while dragged. */
    fun tick() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK
        )
    }

    /** Connection established: a soft rising "click-tick". */
    fun connected() {
        if (
            !compose(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE to 0.6f,
                VibrationEffect.Composition.PRIMITIVE_TICK to 0.8f,
            )
        ) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.LONG_PRESS
            )
        }
    }

    /** Disconnected. */
    fun disconnected() {
        if (!compose(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL to 0.5f)) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /** Something failed (blocked network, error). */
    fun error() {
        if (
            !compose(
                VibrationEffect.Composition.PRIMITIVE_THUD to 0.7f,
                VibrationEffect.Composition.PRIMITIVE_TICK to 0.4f,
            )
        ) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            )
        }
    }

    // Ids are always VibrationEffect.Composition.PRIMITIVE_* constants from the calls above.
    @SuppressLint("WrongConstant")
    private fun compose(vararg primitives: Pair<Int, Float>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return false
        val ids = primitives.map { it.first }.toIntArray()
        if (!v.areAllPrimitivesSupported(*ids)) return false
        val composition = VibrationEffect.startComposition()
        primitives.forEachIndexed { i, (id, scale) ->
            composition.addPrimitive(id, scale, if (i == 0) 0 else 40)
        }
        return runCatching { v.vibrate(composition.compose()) }.isSuccess
    }
}
