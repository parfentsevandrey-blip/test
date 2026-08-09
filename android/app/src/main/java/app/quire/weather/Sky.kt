package app.quire.weather

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.quire.calendar.R

/**
 * The eleven states of the sky this app draws, and the WMO code table folded into them.
 *
 * Open-Meteo answers in WMO 4677 codes, which distinguish things a person standing outside does
 * not — light, moderate and dense drizzle are three codes and one picture. Folding them here means
 * every surface that shows weather agrees about what it is looking at, and the icon set stays a
 * size somebody can actually recognise at a glance.
 *
 * Each state names two drawables because the same sky reads differently at night: a clear noon and
 * a clear midnight are the same code and must not be the same picture.
 */
enum class Sky(
    @field:DrawableRes val dayIcon: Int,
    @field:DrawableRes val nightIcon: Int,
    @field:StringRes val label: Int,
) {
    CLEAR(R.drawable.wx_clear_day, R.drawable.wx_clear_night, R.string.wx_clear),
    MOSTLY_CLEAR(R.drawable.wx_partly_day, R.drawable.wx_partly_night, R.string.wx_mostly_clear),
    PARTLY_CLOUDY(R.drawable.wx_partly_day, R.drawable.wx_partly_night, R.string.wx_partly_cloudy),
    OVERCAST(R.drawable.wx_cloudy, R.drawable.wx_cloudy, R.string.wx_overcast),
    FOG(R.drawable.wx_fog, R.drawable.wx_fog, R.string.wx_fog),
    DRIZZLE(R.drawable.wx_drizzle, R.drawable.wx_drizzle, R.string.wx_drizzle),
    RAIN(R.drawable.wx_rain, R.drawable.wx_rain, R.string.wx_rain),
    SHOWERS(R.drawable.wx_showers, R.drawable.wx_showers, R.string.wx_showers),
    SLEET(R.drawable.wx_sleet, R.drawable.wx_sleet, R.string.wx_sleet),
    SNOW(R.drawable.wx_snow, R.drawable.wx_snow, R.string.wx_snow),
    THUNDER(R.drawable.wx_thunder, R.drawable.wx_thunder, R.string.wx_thunder),
    ;

    /** The picture for this sky at this hour. */
    @DrawableRes
    fun icon(day: Boolean): Int = if (day) dayIcon else nightIcon

    companion object {
        /**
         * WMO 4677, as Open-Meteo reports it. An unknown code is overcast rather than a crash:
         * a forecast with one number this app has not seen is still a forecast.
         */
        fun of(code: Int): Sky = when (code) {
            0 -> CLEAR
            1 -> MOSTLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57 -> SLEET
            61, 63, 65 -> RAIN
            66, 67 -> SLEET
            71, 73, 75, 77 -> SNOW
            80, 81, 82 -> SHOWERS
            85, 86 -> SNOW
            95, 96, 99 -> THUNDER
            else -> OVERCAST
        }
    }
}
