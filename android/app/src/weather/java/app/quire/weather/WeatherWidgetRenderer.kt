package app.quire.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import app.quire.R
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import app.quire.calendar.core.Tokens
import app.quire.calendar.core.WidgetPaint
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

    /**
     * A rough character width, as a fraction of the type size, for the medium-weight face the
     * day names are set in. Only ever used to decide whether a word fits, never to place one.
     */
    private const val NAME_CHAR_WIDTH = 0.62f

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
        val paint = WidgetPaint.of(context, wp.skin, wp.accent, wp.dynamic)
        val filled = wp.skin == Skin.COLOUR
        val locale = Locale.getDefault()

        val root = RemoteViews(context.packageName, R.layout.weather_widget)
        paint.tint(root, R.id.surface, "setColorFilter", R.color.widget_surface) { it.surface }
        root.setInt(R.id.surface, "setImageAlpha", wp.opacity * 255 / 100)
        root.setViewVisibility(
            R.id.surface_border,
            if (filled) android.view.View.GONE else android.view.View.VISIBLE,
        )
        paint.tint(root, R.id.surface_border, "setColorFilter", R.color.widget_hairline_strong) { it.hairline }
        paint.tint(root, R.id.rule, "setBackgroundColor", R.color.widget_hairline) { it.hairline }

        val forecast = WeatherStore.load(context)

        // The falling layer goes in before anything is written on the card, so the weather is
        // in the card whether or not the rest of the paint lands.
        skyMotion(context, root, paint, forecast?.now?.sky)

        if (forecast == null) {
            // Nothing fetched yet: say so in the card rather than showing a plausible zero.
            root.setTextViewText(R.id.place, context.getString(R.string.weather))
            paint.tint(root, R.id.place, "setTextColor", R.color.widget_ink_muted) { it.inkMuted }
            root.setTextViewText(R.id.now_temperature, "—")
            paint.tint(root, R.id.now_temperature, "setTextColor", R.color.widget_ink) { it.ink }
            root.setTextViewText(R.id.now_sky, context.getString(R.string.wx_waiting))
            paint.tint(root, R.id.now_sky, "setTextColor", R.color.widget_ink_muted) { it.inkMuted }
            root.setTextViewText(R.id.now_feels, "")
            root.setViewVisibility(R.id.now_icon, android.view.View.GONE)
            root.setViewVisibility(R.id.rule, android.view.View.GONE)
            root.setOnClickPendingIntent(R.id.widget_root, openIntent(context, widgetId))
            return root
        }

        val narrow = widthDp < NARROW_DP

        // A widget is a fixed rectangle on somebody's home screen. It cannot scroll and it cannot
        // grow, so type set in sp against a budget kept in dp overflows the moment the phone's
        // font scale goes above one — which is exactly what sliced the chance of rain off the
        // bottom of a real card. Every size below is still asked for in sp, so the scale is
        // honoured as far as it fits; where it would not fit, the card keeps the layout and gives
        // up the extra size rather than keeping the size and giving up the layout.
        val scale = context.resources.configuration.fontScale.coerceIn(0.85f, 2f)

        // The strip is dealt its share of the height first, and the "now" row is sized from what
        // is left — not the other way round. Sizing the temperature first is what produces a card
        // with a huge number and no forecast, which is the card this one exists to be better
        // than: five days are the reason to look at a weather widget rather than the clock.
        val columns = ((widthDp - 2 * PAD_SIDE_DP) / DAY_MIN_COLUMN_DP).toInt().coerceIn(0, 5)
        val placeSp = minOf(widthDp * 0.042f, widthDp * 0.048f / scale).coerceIn(10f, 15f)
        val placeLineDp = placeSp * 1.45f * scale
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
        val tempSp = minOf(
            nowDp * 0.62f,
            nowDp * 0.72f / scale,
            widthDp * (if (showDetail) 0.16f else 0.26f),
        ).coerceIn(20f, 44f)
        val skySp = minOf(tempSp * 0.34f, nowDp * 0.30f / scale).coerceIn(9.5f, 15f)
        val feelsSp = minOf(tempSp * 0.31f, nowDp * 0.26f / scale).coerceIn(8.5f, 13.5f)

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
        paint.tint(root, R.id.place, "setTextColor", R.color.widget_ink_muted) { it.inkMuted }
        root.setTextViewTextSize(R.id.place, TypedValue.COMPLEX_UNIT_SP, placeSp)

        root.setViewVisibility(R.id.now_icon, android.view.View.VISIBLE)
        root.setImageViewResource(R.id.now_icon, forecast.now.sky.icon(forecast.now.day))
        paint.tint(root, R.id.now_icon, "setColorFilter", R.color.widget_accent) { it.accent }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            root.setViewLayoutWidth(R.id.now_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
            root.setViewLayoutHeight(R.id.now_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
        }

        root.setTextViewText(
            R.id.now_temperature,
            WeatherRepository.degrees(forecast.now.temperature, locale),
        )
        paint.tint(root, R.id.now_temperature, "setTextColor", R.color.widget_ink) { it.ink }
        root.setTextViewTextSize(R.id.now_temperature, TypedValue.COMPLEX_UNIT_SP, tempSp)

        root.setTextViewText(R.id.now_sky, context.getString(forecast.now.sky.label))
        paint.tint(root, R.id.now_sky, "setTextColor", R.color.widget_ink) { it.ink }
        root.setTextViewTextSize(R.id.now_sky, TypedValue.COMPLEX_UNIT_SP, skySp)

        // The feels-like yields its line to the minute-cast when the sky is about to turn:
        // what the next twenty minutes will do is worth more than how the current ones feel.
        // Set in the accent because it is the one line on the card that is news — the same
        // weight the chance-of-rain figures carry in the strip below.
        val soon = forecast.soon(java.time.LocalDateTime.now())
        if (soon != null) {
            root.setTextViewText(
                R.id.now_feels,
                context.getString(
                    when {
                        !soon.starts -> R.string.wx_wet_ends
                        soon.snow -> R.string.wx_snow_in
                        else -> R.string.wx_rain_in
                    },
                    soon.minutes,
                ),
            )
            paint.tint(root, R.id.now_feels, "setTextColor", R.color.widget_accent) { it.accent }
        } else {
            // On a narrow card the words "feels like" are what goes, not the number: the number
            // is the part somebody is reading it for.
            val feels = WeatherRepository.degrees(forecast.now.feelsLike, locale)
            root.setTextViewText(
                R.id.now_feels,
                if (narrow) feels else context.getString(R.string.wx_feels_like, feels),
            )
            paint.tint(root, R.id.now_feels, "setTextColor", R.color.widget_ink_faint) { it.inkFaint }
        }
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
            val nameSp = minOf(stripDp * 0.155f, stripDp * 0.175f / scale).coerceIn(8f, 12f)
            val tempSp = minOf(stripDp * 0.21f, stripDp * 0.24f / scale).coerceIn(9.5f, 15f)
            val rainSp = (nameSp - 0.5f).coerceAtLeast(7.5f)
            // The rain line is offered to the strip only if two things are true: some day in it
            // has a chance worth writing, and paying for the line still leaves an icon worth
            // looking at. A row of blanks on a dry week is a line spent saying nothing, and an
            // eleven-point icon is a smudge.
            val shown = forecast.ahead(columns)
            val wet = shown.any { it.rain >= RAIN_FLOOR }
            val lines = LINE_HEIGHT * scale
            val withRain = stripDp - lines * (nameSp + tempSp + rainSp) - DAY_MARGINS_DP
            val showRain = wet && withRain >= RAIN_MIN_ICON_DP
            val dayIconDp = (
                stripDp - lines * (nameSp + tempSp + if (showRain) rainSp else 0f) - DAY_MARGINS_DP
                ).coerceIn(14f, 34f)
            // Narrow columns keep the high and drop the low. Showing more days badly is worse
            // than showing the same days with one number each: "22° …" is not a temperature.
            val columnDp = (widthDp - 2 * PAD_SIDE_DP) / columns
            val bothTemps = columnDp >= BOTH_TEMPS_MIN_COLUMN_DP
            // "Today" is a word, and words are a different length in every language: the Russian
            // one is seven characters and came back from a real phone as "Сег…". Whether it is
            // written is decided by whether it fits — estimated from its own length at its own
            // size — and where it does not, the short weekday stands in. Nothing is lost: today's
            // column is in the accent colour either way, which is what actually marks it.
            val todayWord = context.getString(R.string.wx_today)
            val nameWidthDp = todayWord.length * nameSp * scale * NAME_CHAR_WIDTH
            val nameToday = nameWidthDp <= columnDp - 2f

            shown.forEach { day ->
                root.addView(
                    R.id.strip,
                    dayColumn(
                        context, day, today, locale, paint,
                        dayIconDp, nameSp, tempSp, bothTemps, showRain, rainSp, nameToday,
                        columnDp, scale,
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
        paint: WidgetPaint,
        iconDp: Float,
        nameSp: Float,
        highSp: Float,
        bothTemps: Boolean,
        showRain: Boolean,
        rainSp: Float,
        nameToday: Boolean,
        columnDp: Float,
        scale: Float,
    ): RemoteViews {
        val column = RemoteViews(context.packageName, R.layout.weather_day)
        val isToday = day.date == today

        column.setTextViewText(
            R.id.day_name,
            if (isToday && nameToday) {
                context.getString(R.string.wx_today)
            } else {
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
            },
        )
        paint.tint(
            column, R.id.day_name, "setTextColor",
            if (isToday) R.color.widget_accent else R.color.widget_ink_faint,
        ) { if (isToday) it.accent else it.inkFaint }
        column.setTextViewTextSize(R.id.day_name, TypedValue.COMPLEX_UNIT_SP, nameSp)

        // A forecast icon is always the daytime one: it describes a whole day, and half of every
        // day is not night in any sense a person means by it.
        column.setImageViewResource(R.id.day_icon, day.sky.dayIcon)
        paint.tint(column, R.id.day_icon, "setColorFilter", R.color.widget_ink_muted) { it.inkMuted }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            column.setViewLayoutWidth(R.id.day_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
            column.setViewLayoutHeight(R.id.day_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
        }

        // High and low on one line, told apart by weight of ink rather than by position. They
        // were one styled string for a while; a ForegroundColorSpan is a baked colour, and a
        // baked colour cannot wear a day face and a night face at once the way everything else
        // on the card now does.
        val high = WeatherRepository.degrees(day.high, locale)
        val low = WeatherRepository.degrees(day.low, locale)
        // Whether the low fits is measured, not assumed: as one ellipsized string the pair used
        // to fail politely by losing the low behind a "…", which is the same information loss
        // wearing a tidier face. Estimated from its own length at its own size, like the word
        // "Today" is, and the low is dropped whole when it will not fit whole.
        val pairDp = (high.length + low.length) * highSp * scale * NAME_CHAR_WIDTH + 6f
        val showLow = bothTemps && pairDp <= columnDp - 2f
        column.setTextViewText(R.id.day_high, high)
        paint.tint(column, R.id.day_high, "setTextColor", R.color.widget_ink) { it.ink }
        column.setTextViewTextSize(R.id.day_high, TypedValue.COMPLEX_UNIT_SP, highSp)
        if (showLow) {
            column.setViewVisibility(R.id.day_low, android.view.View.VISIBLE)
            column.setTextViewText(R.id.day_low, low)
            paint.tint(column, R.id.day_low, "setTextColor", R.color.widget_ink_faint) { it.inkFaint }
            column.setTextViewTextSize(R.id.day_low, TypedValue.COMPLEX_UNIT_SP, highSp)
        } else {
            column.setViewVisibility(R.id.day_low, android.view.View.GONE)
        }

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
            paint.tint(column, R.id.day_rain, "setTextColor", R.color.widget_accent) { it.accent }
            column.setTextViewTextSize(R.id.day_rain, TypedValue.COMPLEX_UNIT_SP, rainSp)
        } else {
            column.setViewVisibility(R.id.day_rain, android.view.View.GONE)
        }

        return column
    }

    // ---- the falling layer ---------------------------------------------

    private val RAIN_FRAMES = intArrayOf(
        R.drawable.precip_rain_0, R.drawable.precip_rain_1,
        R.drawable.precip_rain_2, R.drawable.precip_rain_3,
    )
    private val SNOW_FRAMES = intArrayOf(
        R.drawable.precip_snow_0, R.drawable.precip_snow_1,
        R.drawable.precip_snow_2, R.drawable.precip_snow_3,
    )

    // How loud the layer is, out of 255. It sits behind the type: legible weather, not a
    // watermark over the numbers. Drizzle is fainter than rain because drizzle is fainter
    // than rain; the flash is the loudest thing the card ever does and it lasts one beat.
    private const val DRIZZLE_ALPHA = 64
    private const val RAIN_ALPHA = 100
    private const val SNOW_ALPHA = 120
    private const val FLASH_ALPHA = 150

    /**
     * Fills the flipper with the phases of whatever is falling, or empties it.
     *
     * The flipper steps through its children in the launcher's process on its own clock — the
     * one animation a RemoteViews widget can run without waking its app. Four phase frames make
     * a seamless loop; thunder runs the loop twice with the last beat swapped for a lightning
     * frame, so the flash lands every eighth beat instead of strobing on every fourth.
     *
     * Dry skies get an empty, GONE flipper, which costs the launcher nothing — a card with no
     * weather falling must be exactly the card this app shipped before it learned to do this.
     */
    private fun skyMotion(context: Context, root: RemoteViews, paint: WidgetPaint, sky: Sky?) {
        root.removeAllViews(R.id.sky_motion)

        val frames: IntArray
        val alpha: Int
        when (sky) {
            Sky.DRIZZLE -> { frames = RAIN_FRAMES; alpha = DRIZZLE_ALPHA }
            Sky.RAIN, Sky.SHOWERS, Sky.THUNDER -> { frames = RAIN_FRAMES; alpha = RAIN_ALPHA }
            Sky.SNOW, Sky.SLEET -> { frames = SNOW_FRAMES; alpha = SNOW_ALPHA }
            else -> {
                root.setViewVisibility(R.id.sky_motion, android.view.View.GONE)
                return
            }
        }

        root.setViewVisibility(R.id.sky_motion, android.view.View.VISIBLE)
        val laps = if (sky == Sky.THUNDER) 2 else 1
        for (lap in 0 until laps) {
            for ((index, res) in frames.withIndex()) {
                if (sky == Sky.THUNDER && lap == 1 && index == frames.lastIndex) continue
                root.addView(R.id.sky_motion, phase(context, paint, res, alpha, flash = false))
            }
        }
        if (sky == Sky.THUNDER) {
            root.addView(
                R.id.sky_motion,
                phase(context, paint, R.drawable.precip_flash, FLASH_ALPHA, flash = true),
            )
        }
    }

    /** One frame of the layer: the drops in muted ink, the lightning in the accent. */
    private fun phase(
        context: Context,
        paint: WidgetPaint,
        res: Int,
        alpha: Int,
        flash: Boolean,
    ): RemoteViews {
        val frame = RemoteViews(context.packageName, R.layout.precip_frame)
        frame.setImageViewResource(R.id.precip_frame, res)
        if (flash) {
            paint.tint(frame, R.id.precip_frame, "setColorFilter", R.color.widget_accent) { it.accent }
        } else {
            paint.tint(frame, R.id.precip_frame, "setColorFilter", R.color.widget_ink_muted) { it.inkMuted }
        }
        frame.setInt(R.id.precip_frame, "setImageAlpha", alpha)
        return frame
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
