package app.quire.engine.design

import kotlin.math.max

/**
 * One place that answers "how big should this be", so density is a single knob.
 *
 * Nothing here reads a resource: the widget renderer and the in-app canvas share this file, and
 * only one of them has a themed context to read from.
 *
 * @param density pixels per dp for the target display, from DisplayMetrics.
 * @property scale the user's own size preference, applied on top of the display's density.
 */
class Metrics(density: Float, val scale: Float = 1f) {

    // A zero density is not a display, it is an uninitialised one; falling back to 1 keeps a
    // half-built preview drawing something instead of collapsing every size to nothing.
    private val basis: Float = if (density > 0f) density else 1f
    private val perDp: Float = basis * if (scale > 0f) scale else 1f

    /** Turns a design measurement into pixels, and the only conversion anything should use. */
    fun dp(v: Float): Float = v * perDp

    /**
     * The thinnest line that still lands on a whole pixel. Deliberately outside [scale]: a rule
     * is a rule at any size, and a scaled hairline only turns grey.
     */
    val hairline: Float = max(1f, basis * 0.5f)

    /** The corner of anything small enough to read as a control: chips, fields, buttons. */
    val radiusSmall: Float = dp(8f)

    /** The corner of anything that reads as a plane: cards, sheets, the stage itself. */
    val radiusLarge: Float = dp(28f)

    /** The breathing room at the edge of a plane, and the gap between two of them. */
    val gutter: Float = dp(16f)

    /** The height of one list row, and the rhythm everything vertical is tuned against. */
    val rowHeight: Float = dp(56f)

    /**
     * Turns a type size into pixels. Text rides the same [scale] as everything else rather than
     * the system font scale, so a line of text never outgrows the row drawn to hold it.
     */
    fun sp(v: Float): Float = v * perDp
}
