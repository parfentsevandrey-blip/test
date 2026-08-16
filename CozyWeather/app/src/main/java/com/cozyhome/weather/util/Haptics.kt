package com.cozyhome.weather.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Rich haptics on top of predefined effects and composition primitives
 * (Pixel-class vibrators support all of these).
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    fun tick(context: Context) {
        vibrator(context).vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    fun click(context: Context) {
        vibrator(context).vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    fun heavy(context: Context) {
        vibrator(context).vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }

    /** A soft "whoosh + thump" for entering the cozy world. */
    fun enterWorld(context: Context) {
        val v = vibrator(context)
        if (v.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_THUD,
            )
        ) {
            v.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 80)
                    .compose()
            )
        } else {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        }
    }

    /** A gentle rising confirmation, e.g. after a successful refresh. */
    fun success(context: Context) {
        val v = vibrator(context)
        if (v.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
            )
        ) {
            v.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.6f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 60)
                    .compose()
            )
        } else {
            click(context)
        }
    }
}
