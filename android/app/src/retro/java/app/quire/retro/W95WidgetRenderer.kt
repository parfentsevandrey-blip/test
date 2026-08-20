package app.quire.retro

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import app.quire.R
import app.quire.weather.DayForecast
import app.quire.weather.Sky
import app.quire.weather.WeatherSettings
import app.quire.weather.WeatherStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The home-screen card as a Windows 95 window.
 *
 * The modern card spends its effort on adapting: palettes resolved by the launcher, type sized
 * against the box, things leaving in order as the card shrinks. This one adapts too — a widget
 * that clips is broken in any decade — but everything it draws is fixed by the era: the grey is
 * `#C0C0C0` because that is what the grey was, the title bar is navy to blue because that is
 * what an active window looked like, and nothing follows the wallpaper, because in 1995 nothing
 * followed the wallpaper.
 *
 * It shares the forecast, the store and the wake-ups with the modern app — see the `wxcore`
 * source set — and differs only here, at the paint.
 */
internal object W95WidgetRenderer {

    /** Below this a column cannot hold a weekday and two numbers; the strip goes instead. */
    private const val DAY_MIN_COLUMN_DP = 38f

    /** Below this the card is only tall enough to be a "now" with a title bar over it. */
    private const val STRIP_MIN_CARD_DP = 132f

    private const val PAD_DP = 10f

    fun build(context: Context, widgetId: Int, widthDp: Int, heightDp: Int): RemoteViews {
        val settings = WeatherSettings.get(context)
        val locale = Locale.ENGLISH
        val root = RemoteViews(context.packageName, R.layout.w95_widget)

        val forecast = WeatherStore.load(context)
        val place = forecast?.place?.takeIf { it.isNotBlank() } ?: "Untitled"
        root.setTextViewText(R.id.w95_title, "Weather - $place")
        root.setOnClickPendingIntent(R.id.widget_root, openIntent(context, widgetId))
        root.setOnClickPendingIntent(R.id.w95_close, openIntent(context, widgetId))

        if (forecast == null) {
            // A dialog's own way of saying nothing has arrived: the status bar says it, and the
            // client area shows a dash rather than a plausible zero.
            root.setTextViewText(R.id.w95_temperature, "--")
            root.setTextViewText(R.id.w95_sky, "No data")
            root.setTextViewText(R.id.w95_feels, "")
            root.setViewVisibility(R.id.w95_icon, android.view.View.GONE)
            root.setViewVisibility(R.id.w95_strip, android.view.View.GONE)
            root.setTextViewText(R.id.w95_status_text, "Connecting...")
            root.setTextViewText(R.id.w95_status_stamp, "--:--")
            return root
        }

        // Type is asked for in sp and budgeted in dp, so the same font-scale arithmetic the
        // modern card learned applies here: the layout is kept, the extra size is given up.
        val scale = context.resources.configuration.fontScale.coerceIn(0.85f, 2f)
        val columns = ((widthDp - 2 * PAD_DP) / DAY_MIN_COLUMN_DP).toInt().coerceIn(0, 5)
        val showStrip = heightDp >= STRIP_MIN_CARD_DP && columns >= 3
        val tempSp = minOf(widthDp * 0.13f, heightDp * 0.24f, 44f / scale).coerceIn(16f, 34f)
        val skySp = (tempSp * 0.40f).coerceIn(10f, 13f)
        val iconDp = (tempSp * 1.15f).coerceIn(18f, 40f)

        root.setViewVisibility(R.id.w95_icon, android.view.View.VISIBLE)
        root.setImageViewResource(R.id.w95_icon, glyph(forecast.now.sky, forecast.now.day))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            root.setViewLayoutWidth(R.id.w95_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
            root.setViewLayoutHeight(R.id.w95_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
        }

        root.setTextViewText(R.id.w95_temperature, settings.write(forecast.now.temperature))
        root.setTextViewTextSize(R.id.w95_temperature, TypedValue.COMPLEX_UNIT_SP, tempSp)
        root.setTextViewText(R.id.w95_sky, skyWord(forecast.now.sky))
        root.setTextViewTextSize(R.id.w95_sky, TypedValue.COMPLEX_UNIT_SP, skySp)
        root.setTextViewText(
            R.id.w95_feels,
            "Feels like " + settings.write(forecast.now.feelsLike),
        )
        root.setTextViewTextSize(R.id.w95_feels, TypedValue.COMPLEX_UNIT_SP, skySp - 1f)
        // Below this width the detail column is two truncated words; the number takes it back.
        root.setViewVisibility(
            R.id.w95_detail,
            if (widthDp >= 180) android.view.View.VISIBLE else android.view.View.GONE,
        )

        root.removeAllViews(R.id.w95_strip)
        if (!showStrip) {
            root.setViewVisibility(R.id.w95_strip, android.view.View.GONE)
        } else {
            root.setViewVisibility(R.id.w95_strip, android.view.View.VISIBLE)
            val today = LocalDate.now()
            forecast.ahead(columns).forEach { day ->
                root.addView(R.id.w95_strip, dayColumn(context, day, today, settings, locale, scale))
            }
        }

        root.setTextViewText(R.id.w95_status_text, "Ready")
        root.setTextViewText(
            R.id.w95_status_stamp,
            DateTimeFormatter.ofPattern("HH:mm", locale).format(
                java.time.Instant.ofEpochMilli(forecast.fetched)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime(),
            ),
        )
        root.setContentDescription(R.id.widget_root, "Weather - $place")
        return root
    }

    private fun dayColumn(
        context: Context,
        day: DayForecast,
        today: LocalDate,
        settings: WeatherSettings,
        locale: Locale,
        scale: Float,
    ): RemoteViews {
        val column = RemoteViews(context.packageName, R.layout.w95_day)
        val name = if (day.date == today) {
            "Today"
        } else {
            day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        }
        column.setTextViewText(R.id.w95_day_name, name)
        // A forecast icon is always the daytime one: it describes a whole day.
        column.setImageViewResource(R.id.w95_day_icon, glyph(day.sky, true))
        column.setTextViewText(R.id.w95_day_high, settings.write(day.high))
        column.setTextViewText(R.id.w95_day_low, settings.write(day.low))
        // The low is dropped rather than squeezed when the type is turned up past the room.
        column.setViewVisibility(
            R.id.w95_day_low,
            if (scale > 1.4f) android.view.View.GONE else android.view.View.VISIBLE,
        )
        return column
    }

    /** The blocky sixteen-colour glyph for a sky; see gen_w95_icons for how they are drawn. */
    internal fun glyph(sky: Sky, day: Boolean): Int = when (sky) {
        Sky.CLEAR -> if (day) R.drawable.w95_clear_day else R.drawable.w95_clear_night
        Sky.MOSTLY_CLEAR, Sky.PARTLY_CLOUDY ->
            if (day) R.drawable.w95_partly_day else R.drawable.w95_partly_night
        Sky.OVERCAST -> R.drawable.w95_cloudy
        Sky.FOG -> R.drawable.w95_fog
        Sky.DRIZZLE -> R.drawable.w95_drizzle
        Sky.RAIN -> R.drawable.w95_rain
        Sky.SHOWERS -> R.drawable.w95_showers
        Sky.SLEET -> R.drawable.w95_sleet
        Sky.SNOW -> R.drawable.w95_snow
        Sky.THUNDER -> R.drawable.w95_thunder
    }

    private fun openIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("quire://weather"))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0x9500 + widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
