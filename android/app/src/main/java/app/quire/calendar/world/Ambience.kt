package app.quire.calendar.world

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import app.quire.engine.anim.clamp
import app.quire.engine.anim.lerp
import app.quire.engine.design.Metrics
import app.quire.engine.design.Oklch
import app.quire.engine.design.Theme
import app.quire.engine.fx.Shaders
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// ---- the field ------------------------------------------------------------------------------

// How many turns of the field one month of travel is worth, and how many the whole pinch from
// the month to the year is. Both are deliberately small: the point is that the ground shifts
// under the calendar as it is moved through, not that it is a second thing to watch.
//
// The size of these numbers is also a cost. Where AGSL is unavailable the field is painted by
// hand into a small buffer and repainted whenever the phase crosses one of its 192 quantisation
// steps, so a month of travel here is about ten repaints of a 160-pixel-wide picture rather than
// one per frame. Raising either of these raises that count in proportion.
private const val TRAVEL_TURNS = 0.055f
private const val ZOOM_TURNS = 0.20f

private const val TAU = 6.2831855f

private const val POOL_STOPS = 3
private const val POOL_RADIUS = 0.92f
private const val POOL_A_ALPHA = 0.55f
private const val POOL_B_ALPHA = 0.45f
private const val POOL_MID_ALPHA = 0.24f
private const val BACKGROUND_GRAIN = 0.55f

// ---- the layers, and how far each one answers the hand -----------------------------------

// Three depths, and the whole point is that the numbers differ. The eye's own slide moves the
// plates about nine dp at full tilt (WorldView.TILT_REACH, held constant in pixels across the
// levels), so both of these stay under it: a layer behind the plates that moved with them would
// be pinned to them, and one that moved further would be in front of them.
//
// Each grows towards the year. At the month there is one card to read and a swimming ground is
// only a distraction; at the year the wall is the whole screen and the only way to feel that it
// stands in front of something is to see that something move at its own rate.
private const val FIELD_TILT_MONTH_DP = 2f
private const val FIELD_TILT_YEAR_DP = 5f
private const val VEIL_TILT_MONTH_DP = 3.5f
private const val VEIL_TILT_YEAR_DP = 8f

// What the field is overscanned by, so sliding it never walks its own edge into the viewport.
private const val OVERSCAN_DP = 12f

// ---- the depth veil --------------------------------------------------------------------------

// How far the veil's colour is dropped below the canvas. It is not black: a neutral black over a
// warm ground reads as dirt, where the canvas walked a hair darker reads as the same ground
// further away, which is the whole idea.
private const val VEIL_DARKEN = 0.20f

// The veil is clear in the middle and closes in at the rim, so it quiets the field around the
// wall without touching what the wall is standing on.
private const val VEIL_RADIUS = 0.78f
private const val VEIL_STOPS = 3
private const val VEIL_MID = 0.62f
private const val VEIL_MID_ALPHA = 0.22f

// How strong the veil is once the year has formed. Zero at the month: one plate on a quiet field
// needs no help, and a vignette that is always there is a filter rather than a depth cue.
//
// This is the one number here that can afford to be generous, because the veil only ever darkens
// ground — the cards over it are opaque and the strip and the title are drawn after it. At the
// screen's corner it works out at about a quarter of the walk, and behind the wall's outer cards
// at about a seventh, which is enough to separate the twelve without dimming any of them.
private const val VEIL_AT_YEAR = 0.70f

// And how far the whole world is taken down behind an open day. This one is flat rather than
// radial — the day is a sheet laid over everything, so what is behind it recedes evenly.
private const val SHADE_AT_OPEN = 0.28f

private const val MIN_ALPHA = 1f / 255f

/**
 * The ground the world stands on: two soft pools of the theme's aurora hues over its canvas, a
 * depth veil that closes in as the year forms, and the shade the world recedes behind when a day
 * is opened.
 *
 * Nothing in here is timed. The field's phase is a function of where the camera is standing —
 * how far along the calendar, and how far back — so a screen that has settled paints the same
 * ground for ever and asks for no frames, and a gesture that is reversed retraces its own path
 * rather than leaving the background somewhere new. That is the difference between a background
 * that answers the calendar and one that merely ticks.
 *
 * Depth is three rates rather than three pictures. The field slides least with the hand, the veil
 * more, and the plates — moved by the camera itself, not by this file — most of all; the eye
 * reads the differences as distance. All three grow towards the year, where the wall covers the
 * screen and the ground is the only thing left that can show it is a wall.
 *
 * Every paint, matrix and gradient is a field, and the two pool gradients and the veil are built
 * once per palette and only re-placed by a matrix, so a moving background allocates nothing.
 * Not thread safe: call it from the thread that draws.
 */
class Ambience {

    // Filtering is asked for here as well as on the shader: Shaders' stand-in is a few dozen
    // pixels stretched across the whole screen, and BitmapShader.setFilterMode only exists from
    // API 33, so below that the paint is the only thing that can smooth it.
    private val fieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val poolMatrix = Matrix()
    private val poolStops = FloatArray(POOL_STOPS)
    private val poolAColours = IntArray(POOL_STOPS)
    private val poolBColours = IntArray(POOL_STOPS)
    private var poolA: RadialGradient? = null
    private var poolB: RadialGradient? = null

    private val veilStops = FloatArray(VEIL_STOPS)
    private val veilColours = IntArray(VEIL_STOPS)
    private var veil: RadialGradient? = null

    private var theme: Theme? = null
    private var metrics: Metrics? = null

    private var canvasColour = 0
    private var poolColourA = 0
    private var poolColourB = 0
    private var veilColour = 0

    private var fieldTiltMonth = 0f
    private var fieldTiltYear = 0f
    private var veilTiltMonth = 0f
    private var veilTiltYear = 0f
    private var overscan = 0f

    /**
     * The only source of colour and size here; both are re-read whenever either moves.
     *
     * Every gradient is rebuilt on this call and on no other, which is what keeps the drawing
     * path free of allocation.
     */
    fun configure(theme: Theme, metrics: Metrics) {
        this.theme = theme
        this.metrics = metrics
        canvasColour = theme.canvas
        poolColourA = withAlpha(theme.auroraA, POOL_A_ALPHA)
        poolColourB = withAlpha(theme.auroraB, POOL_B_ALPHA)
        buildPools(theme)
        buildVeil(theme)
        fieldTiltMonth = metrics.dp(FIELD_TILT_MONTH_DP)
        fieldTiltYear = metrics.dp(FIELD_TILT_YEAR_DP)
        veilTiltMonth = metrics.dp(VEIL_TILT_MONTH_DP)
        veilTiltYear = metrics.dp(VEIL_TILT_YEAR_DP)
        overscan = metrics.dp(OVERSCAN_DP)
    }

    /**
     * Paints the ground, under everything the world draws.
     *
     * @param travel the camera's station along the corridor, in months; the field turns slowly
     *     with it, so moving through the calendar shifts the ground underneath.
     * @param zoom how far back the eye stands, 0 at the month and 1 at the year.
     * @param grid how far the year wall has formed, which is what the veil and the parallax
     *     rates answer — the wall is what they are there to stand behind.
     * @param tiltX how far the phone is tilted left or right, −1..1, as the world reads it.
     * @param tiltY the same about the other axis.
     */
    fun drawField(
        canvas: Canvas,
        width: Float,
        height: Float,
        travel: Float,
        zoom: Float,
        grid: Float,
        tiltX: Float,
        tiltY: Float,
    ) {
        if (!(width > 0f && height > 0f)) return
        // The camera's slide moves the plates against the screen; a layer behind them has to move
        // the same way and less far, or it is not behind them. Both signs follow from Camera3D:
        // pushing the eye one way carries what it is looking at the other.
        val fieldReach = lerp(fieldTiltMonth, fieldTiltYear, grid)
        val dx = -tiltX * fieldReach
        val dy = -tiltY * fieldReach

        // The field is painted a little larger than the screen and slid inside that margin, so
        // its own edge is never walked into view.
        val fieldWidth = width + 2f * overscan
        val fieldHeight = height + 2f * overscan
        canvas.save()
        canvas.translate(dx - overscan, dy - overscan)
        paintField(canvas, fieldWidth, fieldHeight, phaseFor(travel, zoom))
        canvas.restore()

        val strength = VEIL_AT_YEAR * clamp(grid, 0f, 1f)
        if (strength <= MIN_ALPHA) return
        val gradient = veil ?: return
        val veilReach = lerp(veilTiltMonth, veilTiltYear, grid)
        // Nearer than the field and still further than the plates, which is the whole of the
        // depth: three layers answering one hand at three rates.
        val vx = -tiltX * veilReach
        val vy = -tiltY * veilReach
        val span = max(width, height) * VEIL_RADIUS
        poolMatrix.setScale(span, span)
        poolMatrix.postTranslate(width * 0.5f + vx, height * 0.5f + vy)
        gradient.setLocalMatrix(poolMatrix)
        veilPaint.shader = gradient
        veilPaint.alpha = toAlpha(strength)
        canvas.drawRect(0f, 0f, width, height, veilPaint)
        veilPaint.shader = null
        veilPaint.alpha = 255
    }

    /**
     * Takes the whole world down behind an open day, drawn over the plates and under the panel.
     *
     * Flat rather than radial, and deliberately so: the day is a sheet laid over everything, and
     * what is behind a sheet recedes evenly rather than at its corners.
     *
     * @param openness 0 the day is still a tile in its plate, 1 the panel is open.
     */
    fun drawShade(canvas: Canvas, width: Float, height: Float, openness: Float) {
        val strength = SHADE_AT_OPEN * clamp(openness, 0f, 1f)
        if (strength <= MIN_ALPHA) return
        veilPaint.shader = null
        veilPaint.color = withAlpha(veilColour, strength)
        canvas.drawRect(0f, 0f, width, height, veilPaint)
        veilPaint.color = OPAQUE_BLACK
    }

    // ---- the field, either way -----------------------------------------

    private fun paintField(canvas: Canvas, width: Float, height: Float, phase: Float) {
        // A RuntimeShader is a GPU program: handing one to a software canvas throws rather than
        // degrading, and this view is drawn into a plain Bitmap more often than it looks — a
        // screenshot, a print, a render test, or simply a window that lost its hardware layer.
        // The support flag answers "does this Android build have AGSL", which is not the same
        // question as "can this canvas run it", so both have to be asked.
        if (Shaders.supported && canvas.isHardwareAccelerated) {
            val shader = Shaders.background(
                width = width,
                height = height,
                colourA = poolColourA,
                colourB = poolColourB,
                base = canvasColour,
                t = phase,
                grain = BACKGROUND_GRAIN,
            )
            if (shader != null) {
                fieldPaint.shader = shader
                canvas.drawRect(0f, 0f, width, height, fieldPaint)
                fieldPaint.shader = null
                return
            }
        }
        // No runtime shaders: the same picture by hand, two soft pools of the theme's aurora
        // hues laid over its canvas. Both gradients are built at unit radius once per palette
        // and only placed by a matrix here, so a drifting background allocates nothing.
        canvas.drawColor(canvasColour)
        val angle = phase * TAU
        val span = max(width, height) * POOL_RADIUS
        drawPool(
            canvas = canvas,
            gradient = poolA,
            cx = width * (0.30f + 0.14f * cos(angle)),
            cy = height * (0.32f + 0.10f * sin(angle * 1.3f + 0.7f)),
            radius = span,
            width = width,
            height = height,
        )
        drawPool(
            canvas = canvas,
            gradient = poolB,
            cx = width * (0.72f + 0.12f * cos(angle * 0.8f + 2.1f)),
            cy = height * (0.70f + 0.13f * sin(angle * 1.1f)),
            radius = span,
            width = width,
            height = height,
        )
    }

    /**
     * Where the field stands, in turns.
     *
     * A pure function of the two coordinates the world is navigated by, so it is the same number
     * for the same place every time it is asked: reversing a pinch reverses the ground with it,
     * and a screen nobody is touching is not merely still but permanently still.
     */
    private fun phaseFor(travel: Float, zoom: Float): Float =
        travel * TRAVEL_TURNS + clamp(zoom, 0f, 1f) * ZOOM_TURNS

    private fun drawPool(
        canvas: Canvas,
        gradient: RadialGradient?,
        cx: Float,
        cy: Float,
        radius: Float,
        width: Float,
        height: Float,
    ) {
        if (gradient == null) return
        poolMatrix.setScale(radius, radius)
        poolMatrix.postTranslate(cx, cy)
        gradient.setLocalMatrix(poolMatrix)
        fieldPaint.shader = gradient
        canvas.drawRect(0f, 0f, width, height, fieldPaint)
        fieldPaint.shader = null
    }

    private fun buildPools(theme: Theme) {
        poolStops[0] = 0f
        poolStops[1] = 0.55f
        poolStops[2] = 1f
        poolAColours[0] = poolColourA
        poolAColours[1] = withAlpha(theme.auroraA, POOL_MID_ALPHA)
        poolAColours[2] = withAlpha(theme.auroraA, 0f)
        poolBColours[0] = poolColourB
        poolBColours[1] = withAlpha(theme.auroraB, POOL_MID_ALPHA)
        poolBColours[2] = withAlpha(theme.auroraB, 0f)
        poolA = RadialGradient(0f, 0f, 1f, poolAColours, poolStops, Shader.TileMode.CLAMP)
        poolB = RadialGradient(0f, 0f, 1f, poolBColours, poolStops, Shader.TileMode.CLAMP)
    }

    private fun buildVeil(theme: Theme) {
        // The canvas walked down rather than a neutral black: the palette is built around one
        // hue and a grey shadow over a warm ground reads as dirt on the screen.
        veilColour = opaque(Oklch.lighten(theme.canvas, -VEIL_DARKEN))
        veilStops[0] = 0f
        veilStops[1] = VEIL_MID
        veilStops[2] = 1f
        veilColours[0] = withAlpha(veilColour, 0f)
        veilColours[1] = withAlpha(veilColour, VEIL_MID_ALPHA)
        veilColours[2] = veilColour
        veil = RadialGradient(0f, 0f, 1f, veilColours, veilStops, Shader.TileMode.CLAMP)
    }

    private fun withAlpha(colour: Int, alpha: Float): Int {
        // The colour's own alpha is scaled rather than replaced, so a theme colour that is
        // already a wash fades from what it is instead of to full.
        val source = (colour ushr 24) and 0xFF
        return (toAlpha(source / 255f * alpha) shl 24) or (colour and RGB_MASK)
    }

    private fun toAlpha(value: Float): Int = (clamp(value, 0f, 1f) * 255f + 0.5f).toInt()

    private fun opaque(colour: Int): Int = OPAQUE_BLACK or (colour and RGB_MASK)

    private companion object {
        const val RGB_MASK = 0x00FFFFFF
        const val OPAQUE_BLACK = 0xFF000000.toInt()
    }
}
