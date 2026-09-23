package app.papersky.weather.widget.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.wrapContentWidth
import app.papersky.weather.R
import app.papersky.weather.widget.layout.LINE
import app.papersky.weather.widget.layout.TextMeasure
import app.papersky.weather.widget.layout.WFont

/**
 * "Islands" of classic RemoteViews inside the Glance tree. They give widgets things Glance's
 * own components can't: bundled variable fonts, a live TextClock and a self-animating
 * ViewFlipper.
 */
enum class TextAlign(val gravity: Int) {
    Start(Gravity.CENTER_VERTICAL or Gravity.START),
    Center(Gravity.CENTER),
    End(Gravity.CENTER_VERTICAL or Gravity.END),
}

private fun layoutFor(font: WFont, shadow: Boolean): Int = when (font) {
    WFont.DisplayLight -> if (shadow) R.layout.rv_text_display_light_shadow else R.layout.rv_text_display_light
    WFont.Display -> if (shadow) R.layout.rv_text_display_shadow else R.layout.rv_text_display
    WFont.Body -> if (shadow) R.layout.rv_text_body_shadow else R.layout.rv_text_body
    WFont.BodyBold -> if (shadow) R.layout.rv_text_body_bold_shadow else R.layout.rv_text_body_bold
    WFont.Hand -> if (shadow) R.layout.rv_text_hand_shadow else R.layout.rv_text_hand
}

@Composable
fun WText(
    text: String,
    font: WFont,
    size: Float,
    color: Int,
    modifier: GlanceModifier = GlanceModifier,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    shadow: Boolean = false,
    /** Stretch the TextView across its island (needed for centre/end alignment). */
    fill: Boolean = align != TextAlign.Start,
) {
    val context = LocalContext.current
    val rv = RemoteViews(context.packageName, layoutFor(font, shadow)).apply {
        if (fill) setViewLayoutWidth(R.id.text, ViewGroup.LayoutParams.MATCH_PARENT.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        setTextViewText(R.id.text, text)
        setTextViewTextSize(R.id.text, TypedValue.COMPLEX_UNIT_DIP, size)
        setTextColor(R.id.text, color)
        setInt(R.id.text, "setGravity", align.gravity)
        if (maxLines != 1) setInt(R.id.text, "setMaxLines", maxLines)
        setContentDescription(R.id.text, text)
    }
    // Glance sizes AndroidRemoteViews to match_parent unless told otherwise; the caller's own
    // width (fixed, fill or weight) still wins because the last size modifier applies.
    AndroidRemoteViews(rv, GlanceModifier.wrapContentWidth().then(modifier))
}

@Composable
fun WClock(size: Float, color: Int, zoneId: String, modifier: GlanceModifier = GlanceModifier, shadow: Boolean = false, align: TextAlign = TextAlign.Start) {
    val context = LocalContext.current
    val rv = RemoteViews(context.packageName, if (shadow) R.layout.rv_clock_shadow else R.layout.rv_clock).apply {
        if (align != TextAlign.Start) setViewLayoutWidth(R.id.clock, ViewGroup.LayoutParams.MATCH_PARENT.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_DIP, size)
        setTextColor(R.id.clock, color)
        setString(R.id.clock, "setTimeZone", zoneId)
        setInt(R.id.clock, "setGravity", align.gravity)
    }
    AndroidRemoteViews(rv, GlanceModifier.wrapContentWidth().then(modifier))
}

/** A line that quietly flips through observations every few seconds, right on the home screen. */
@Composable
fun WWhisper(lines: List<String>, size: Float, color: Int, modifier: GlanceModifier = GlanceModifier, shadow: Boolean = false) {
    val context = LocalContext.current
    val itemLayout = if (shadow) R.layout.rv_whisper_item_shadow else R.layout.rv_whisper_item
    val rv = RemoteViews(context.packageName, R.layout.rv_whisper).apply {
        lines.take(6).forEach { line ->
            val item = RemoteViews(context.packageName, itemLayout).apply {
                setTextViewText(R.id.text, line)
                setTextViewTextSize(R.id.text, TypedValue.COMPLEX_UNIT_DIP, size)
                setTextColor(R.id.text, color)
            }
            addView(R.id.flipper, item)
        }
    }
    AndroidRemoteViews(rv, modifier)
}

/** Measures with the exact fonts the widget uses, so the planner's decisions hold on screen. */
class PaintMeasure private constructor(context: Context) : TextMeasure {
    private val paints: Map<WFont, Paint> = WFont.entries.associateWith { font ->
        val id = when (font) {
            WFont.DisplayLight -> R.font.display_light
            WFont.Display -> R.font.display_medium
            WFont.Body -> R.font.body_medium
            WFont.BodyBold -> R.font.body_bold
            WFont.Hand -> R.font.hand
        }
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT
            textSize = 100f
            if (font == WFont.DisplayLight) letterSpacing = -0.01f
            if (font == WFont.BodyBold) letterSpacing = 0.02f
            fontFeatureSettings = "tnum"
        }
    }

    override fun width(text: String, font: WFont, sizeDp: Float): Float {
        val paint = paints.getValue(font)
        return synchronized(paint) { paint.measureText(text) } * sizeDp / 100f
    }

    /** Height a single line occupies with font padding excluded. */
    fun lineHeight(sizeDp: Float): Float = sizeDp * LINE

    companion object {
        @Volatile private var instance: PaintMeasure? = null
        fun get(context: Context): PaintMeasure =
            instance ?: synchronized(this) { instance ?: PaintMeasure(context.applicationContext).also { instance = it } }
    }
}
