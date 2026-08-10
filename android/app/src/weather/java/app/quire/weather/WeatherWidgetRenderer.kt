package app.quire.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.widget.RemoteViews
import app.quire.R
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import app.quire.calendar.core.Tokens
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The weather card.
 *
 * It is built to be the opposite of the widget it was asked to improve on: that one spends a
 * four-by-two placement on one temperature, a truncated place name and a truncated "feels like",
 * and says nothing at all about tomorrow. This one puts the place, the temperature, the sky, the
 * feels-like and five days into the same space, at sizes that get out of each other's way as the
 * card shrinks rather than clipping.
 *
 * It shares the calendar's palette, so a pair of Quire widgets sitting next to each other are
 * plainly the same object at two jobs — which is the other half of what makes a home screen look
 * arranged rather than assembled.
 */
object WeatherWidgetRenderer {

    /** Below this the strip loses days rather than squeezing them into columns nobody can read. */
    private const val DAY_MIN_COLUMN_DP = 34f

    /** Below this the card is only tall enough to be a "now", and gives the strip up. */
    private const val STRIP_MIN_CARD_DP = 128f

    /** What the layout's own padding costs, top and bottom, and the rule with its margins. */
    private const val PAD_VERTICAL_DP = 22f
    private const val PAD_SIDE_DP = 14f
    private const val RULE_BLOCK_DP = 11f

    /** Below this a column cannot hold "22° 11°", and shows the high alone rather than "22° …". */
    private const val BOTH_TEMPS_MIN_COLUMN_DP = 46f

    /** Below this a chance of rain is noise rather than news. */
    private const val RAIN_FLOOR = 20

    /** How big the day icon must still be for the rain line to be worth its height. */
    private const val RAIN_MIN_ICON_DP = 20f

    /** Below this the sky and the feels-like are dropped rather than truncated to initials. */
    private const val DETAIL_MIN_DP = 172

    /** What a compact TextView costs per sp of type, and what a day column's margins cost. */
    private const val LINE_HEIGHT = 1.25f
    private const val DAY_MARGINS_DP = 5f

    private const val NARROW_DP = 200

    fun build(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
        val options = manager.getAppWidgetOptions(widgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            .takeIf { it > 0 } ?: 250
        val heightKey = AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        val heightDp = options.getInt(heightKey, 0).takeIf { it > 0 }
            ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 }
            ?: 140
        return build(context, widgetId, widthDp, heightDp)
    }

    fun build(context: Context, widgetId: Int, widthDp: Int, heightDp: Int): RemoteViews {
        val prefs = Prefs.get(context)
        val wp = prefs.widget(widgetId)
        val palette = Tokens.widgetPalette(context, wp.skin, wp.accent, wp.dynamic)
        val filled = wp.skin == Skin.COLOUR
        val locale = Locale.getDefault()

        val root = RemoteViews(context.packageName, R.layout.weather_widget)
        root.setInt(R.id.surface, "setColorFilter", palette.surface)
        root.setInt(R.id.surface, "setImageAlpha", wp.opacity * 255 / 100)
        root.setViewVisibility(
            R.id.surface_border,
            if (filled) android.view.View.GONE else android.view.View.VISIBLE,
        )
        root.setInt(R.id.surface_border, "setColorFilter", palette.hairline)
        root.setInt(R.id.rule, "setBackgroundColor", palette.hairline)

        val forecast = WeatherStore.load(context)
        if (forecast == null) {
            // Nothing fetched yet: say so in the card rather than showing a plausible zero.
            root.setTextViewText(R.id.place, context.getString(R.string.weather))
            root.setTextColor(R.id.place, palette.inkMuted)
            root.setTextViewText(R.id.now_temperature, "—")
            root.setTextColor(R.id.now_temperature, palette.ink)
            root.setTextViewText(R.id.now_sky, context.getString(R.string.wx_waiting))
            root.setTextColor(R.id.now_sky, palette.inkMuted)
            root.setTextViewText(R.id.now_feels, "")
            root.setViewVisibility(R.id.now_icon, android.view.View.GONE)
            root.setViewVisibility(R.id.rule, android.view.View.GONE)
            root.setOnClickPendingIntent(R.id.widget_root, openIntent(context, widgetId))
            return root
        }

        val narrow = widthDp < NARROW_DP

        // The strip is dealt its share of the height first, and the "now" row is sized from what
        // is left — not the other way round. Sizing the temperature first is what produces a card
        // with a huge number and no forecast, which is the card this one exists to be better
        // than: five days are the reason to look at a weather widget rather than the clock.
        val columns = ((widthDp - 2 * PAD_SIDE_DP) / DAY_MIN_COLUMN_DP).toInt().coerceIn(0, 5)
        val placeSp = (widthDp * 0.042f).coerceIn(11f, 15f)
        val placeLineDp = placeSp * 1.45f
        val showStrip = heightDp >= STRIP_MIN_CARD_DP && columns >= 3
        val stripDp = if (showStrip) (heightDp * 0.38f).coerceIn(54f, 80f) else 0f

        val nowDp = (
            heightDp - PAD_VERTICAL_DP - placeLineDp -
                (if (showStrip) RULE_BLOCK_DP + stripDp else 0f)
            ).coerceAtLeast(34f)
        val iconDp = (nowDp * 0.90f).coerceIn(26f, 56f)
        // Past a point there is no honest room for the sky and the feels-like beside the number,
        // and a card that shows "S…" has spent the space and said nothing. Below it they go, and
        // the number takes the width they were using.
        val showDetail = widthDp >= DETAIL_MIN_DP
        // Height decides how big the number can be; width decides whether it fits beside the icon
        // and whatever else is on the row. Whichever runs out first is the one that governs.
        val tempSp = minOf(nowDp * 0.62f, widthDp * (if (showDetail) 0.16f else 0.26f))
            .coerceIn(22f, 44f)
        val skySp = (tempSp * 0.34f).coerceIn(11f, 15f)
        val feelsSp = (tempSp * 0.31f).coerceIn(10f, 13.5f)

        // Sizing a view from code is API 31 and up. Below it the layout's own dimensions stand,
        // which is why they are chosen to be a sensible middle rather than placeholders: an
        // Android 11 phone gets a card that is merely less finely fitted, not a broken one.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The row is given the height it was dealt rather than wrapping, so what is on it
            // sits in the middle of the space instead of at the top with a hole underneath.
            root.setViewLayoutHeight(R.id.now_row, nowDp, TypedValue.COMPLEX_UNIT_DIP)
        }
        root.setViewVisibility(
            R.id.now_detail,
            if (showDetail) android.view.View.VISIBLE else android.view.View.GONE,
        )

        root.setTextViewText(R.id.place, forecast.place.ifBlank { context.getString(R.string.weather) })
        root.setTextColor(R.id.place, palette.inkMuted)
        root.setTextViewTextSize(R.id.place, TypedValue.COMPLEX_UNIT_SP, placeSp)

        root.setViewVisibility(R.id.now_icon, android.view.View.VISIBLE)
        root.setImageViewResource(R.id.now_icon, forecast.now.sky.icon(forecast.now.day))
        root.setInt(R.id.now_icon, "setColorFilter", palette.accent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            root.setViewLayoutWidth(R.id.now_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
            root.setViewLayoutHeight(R.id.now_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
        }

        root.setTextViewText(
            R.id.now_temperature,
            WeatherRepository.degrees(forecast.now.temperature, locale),
        )
        root.setTextColor(R.id.now_temperature, palette.ink)
        root.setTextViewTextSize(R.id.now_temperature, TypedValue.COMPLEX_UNIT_SP, tempSp)

        root.setTextViewText(R.id.now_sky, context.getString(forecast.now.sky.label))
        root.setTextColor(R.id.now_sky, palette.ink)
        root.setTextViewTextSize(R.id.now_sky, TypedValue.COMPLEX_UNIT_SP, skySp)

        // On a narrow card the words "feels like" are what goes, not the number: the number is
        // the part somebody is reading it for.
        val feels = WeatherRepository.degrees(forecast.now.feelsLike, locale)
        root.setTextViewText(
            R.id.now_feels,
            if (narrow) feels else context.getString(R.string.wx_feels_like, feels),
        )
        root.setTextColor(R.id.now_feels, palette.inkFaint)
        root.setTextViewTextSize(R.id.now_feels, TypedValue.COMPLEX_UNIT_SP, feelsSp)

        root.removeAllViews(R.id.strip)
        if (!showStrip) {
            root.setViewVisibility(R.id.rule, android.view.View.GONE)
            root.setViewVisibility(R.id.strip, android.view.View.GONE)
        } else {
            root.setViewVisibility(R.id.rule, android.view.View.VISIBLE)
            root.setViewVisibility(R.id.strip, android.view.View.VISIBLE)
            val today = LocalDate.now()
            // The two lines of type are sized first and the icon takes what is left, rather than
            // all three being guessed at independently — which is how a column comes to be taller
            // than the strip it sits in, and how the lows came to be sliced off the bottom.
            val nameSp = (stripDp * 0.155f).coerceIn(9f, 12f)
            val tempSp = (stripDp * 0.21f).coerceIn(11f, 15f)
            val rainSp = (nameSp - 0.5f).coerceAtLeast(8f)
            // The rain line is offered to the strip only if two things are true: some day in it
            // has a chance worth writing, and paying for the line still leaves an icon worth
            // looking at. A row of blanks on a dry week is a line spent saying nothing, and an
            // eleven-point icon is a smudge.
            val shown = forecast.ahead(columns)
            val wet = shown.any { it.rain >= RAIN_FLOOR }
            val withRain = stripDp - LINE_HEIGHT * (nameSp + tempSp + rainSp) - DAY_MARGINS_DP
            val showRain = wet && withRain >= RAIN_MIN_ICON_DP
            val dayIconDp = (
                stripDp - LINE_HEIGHT * (nameSp + tempSp + if (showRain) rainSp else 0f) -
                    DAY_MARGINS_DP
                ).coerceIn(16f, 34f)
            // Narrow columns keep the high and drop the low. Showing more days badly is worse
            // than showing the same days with one number each: "22° …" is not a temperature.
            val columnDp = (widthDp - 2 * PAD_SIDE_DP) / columns
            val bothTemps = columnDp >= BOTH_TEMPS_MIN_COLUMN_DP

            shown.forEach { day ->
                root.addView(
                    R.id.strip,
                    dayColumn(
                        context, day, today, locale, palette,
                        dayIconDp, nameSp, tempSp, bothTemps, showRain, rainSp,
                    ),
                )
            }
        }

        root.setOnClickPendingIntent(R.id.widget_root, openIntent(context, widgetId))
        return root
    }

    private fun dayColumn(
        context: Context,
        day: DayForecast,
        today: LocalDate,
        locale: Locale,
        palette: app.quire.calendar.core.Palette,
        iconDp: Float,
        nameSp: Float,
        highSp: Float,
        bothTemps: Boolean,
        showRain: Boolean,
        rainSp: Float,
    ): RemoteViews {
        val column = RemoteViews(context.packageName, R.layout.weather_day)
        val isToday = day.date == today

        column.setTextViewText(
            R.id.day_name,
            if (isToday) {
                context.getString(R.string.wx_today)
            } else {
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
            },
        )
        column.setTextColor(R.id.day_name, if (isToday) palette.accent else palette.inkFaint)
        column.setTextViewTextSize(R.id.day_name, TypedValue.COMPLEX_UNIT_SP, nameSp)

        // A forecast icon is always the daytime one: it describes a whole day, and half of every
        // day is not night in any sense a person means by it.
        column.setImageViewResource(R.id.day_icon, day.sky.dayIcon)
        column.setInt(R.id.day_icon, "setColorFilter", palette.inkMuted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            column.setViewLayoutWidth(R.id.day_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
            column.setViewLayoutHeight(R.id.day_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
        }

        // High and low on one line, told apart by weight of ink rather than by position. A styled
        // CharSequence survives the trip through RemoteViews, so this costs nothing extra.
        val high = WeatherRepository.degrees(day.high, locale)
        val low = WeatherRepository.degrees(day.low, locale)
        val temps = SpannableString(if (bothTemps) "$high $low" else high)
        if (bothTemps) {
            temps.setSpan(
                ForegroundColorSpan(palette.inkFaint),
                high.length + 1,
                temps.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        column.setTextViewText(R.id.day_temps, temps)
        column.setTextColor(R.id.day_temps, palette.ink)
        column.setTextViewTextSize(R.id.day_temps, TypedValue.COMPLEX_UNIT_SP, highSp)

        if (showRain) {
            // Present on every column so the five stay level, written on the ones that have
            // something to say. Below the floor a percentage is noise: nobody changes a plan
            // over a one-in-ten chance, and a strip of small numbers reads as a strip of small
            // numbers whatever they are.
            column.setViewVisibility(R.id.day_rain, android.view.View.VISIBLE)
            column.setTextViewText(
                R.id.day_rain,
                if (day.rain >= RAIN_FLOOR) "${day.rain}%" else " ",
            )
            column.setTextColor(R.id.day_rain, palette.accent)
            column.setTextViewTextSize(R.id.day_rain, TypedValue.COMPLEX_UNIT_SP, rainSp)
        } else {
            column.setViewVisibility(R.id.day_rain, android.view.View.GONE)
        }

        return column
    }

    /** Tapping anywhere on the card opens the weather the card is showing. */
    private fun openIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("quire://weather"))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0x7700 + widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
