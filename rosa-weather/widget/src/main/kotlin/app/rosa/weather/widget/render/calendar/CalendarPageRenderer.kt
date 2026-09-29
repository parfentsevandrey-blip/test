package app.rosa.weather.widget.render.calendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import app.rosa.weather.widget.render.WidgetFonts
import app.rosa.weather.widget.render.WidgetRenderRequest
import kotlin.math.roundToInt

/**
 * Draws a calendar page — the month over its season's painting — into a bitmap, or straight onto a
 * canvas for the studio's preview. The calendar app's widget and screens draw with it; the weather
 * widgets never do.
 */
class CalendarPageRenderer(context: Context) {
    private val calendar = CalendarRenderer(context, WidgetFonts.get(context))

    fun render(request: WidgetRenderRequest, pxPerDp: Float): Bitmap = renderCalendar(request, pxPerDp).first

    /** The page's picture, and where it answers taps. */
    fun renderCalendar(request: WidgetRenderRequest, pxPerDp: Float): Pair<Bitmap, CalendarTargets> {
        val w = (request.widthDp * pxPerDp).roundToInt().coerceAtLeast(1)
        val h = (request.heightDp * pxPerDp).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        canvas.scale(pxPerDp, pxPerDp)
        return bitmap to calendar.draw(canvas, request)
    }

    /** Draws in dp units; callers scale the canvas. */
    fun draw(canvas: Canvas, request: WidgetRenderRequest): CalendarTargets = calendar.draw(canvas, request)
}
