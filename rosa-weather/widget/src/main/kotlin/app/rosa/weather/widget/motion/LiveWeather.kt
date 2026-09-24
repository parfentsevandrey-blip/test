package app.rosa.weather.widget.motion

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import app.rosa.weather.core.model.ForecastMoment
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.widget.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Weather that moves on the home screen. A launcher never runs our code between updates, but it
 * does play an AnimatedVectorDrawable inside a ProgressBar — on its own render thread, the way a
 * system spinner turns, and only while the widget is on screen. So rain, snow and lightning are
 * vector tiles laid over the widget's picture: [TILE_WIDTH_DP] × [TILE_HEIGHT_DP] each, joining
 * seamlessly side by side, in variants that never repeat a neighbour. The tiles themselves are
 * generated (MotionResources, in the widget's tests).
 */
enum class LiveWeather(private val tiles: IntArray, private val storm: Boolean = false) {
    RainLight(intArrayOf(R.layout.motion_rain_light_a, R.layout.motion_rain_light_b, R.layout.motion_rain_light_c, R.layout.motion_rain_light_d)),
    RainHeavy(intArrayOf(R.layout.motion_rain_heavy_a, R.layout.motion_rain_heavy_b, R.layout.motion_rain_heavy_c, R.layout.motion_rain_heavy_d)),

    /** Heavy rain and lightning: every tile flashes together; the top row carries the bolts. */
    Storm(intArrayOf(R.layout.motion_storm_a, R.layout.motion_storm_b, R.layout.motion_storm_c, R.layout.motion_storm_d), storm = true),
    SnowLight(intArrayOf(R.layout.motion_snow_light)),
    SnowHeavy(intArrayOf(R.layout.motion_snow_heavy)),
    ;

    /** The tile at [column], [row]: never the same drops as a neighbour, bolts only along the top. */
    @LayoutRes
    fun tileAt(column: Int, row: Int): Int = when {
        tiles.size == 1 -> tiles[0]
        storm -> if (row == 0) tiles[column % 2] else tiles[2 + (column + row) % 2]
        else -> tiles[(column + 2 * row + row / 2) % 4]
    }

    companion object {
        const val TILE_WIDTH_DP = 180f
        const val TILE_HEIGHT_DP = 90f

        /** Launchers may show a widget a little larger than the size it was drawn for. */
        private const val SLACK_DP = 12f

        /** What moves on a widget with [config] at [moment], or null when nothing falls or flashes. */
        fun of(config: WidgetConfig, moment: ForecastMoment?): LiveWeather? {
            if (moment == null || !config.liveWeather || !config.showWeatherArt || config.style == WidgetStyle.Paper) return null
            val visual = moment.visual
            return when {
                visual.lightning > 0.1f -> Storm
                visual.snow > 0.05f && visual.snow >= visual.rain -> if (visual.snow >= 0.55f) SnowHeavy else SnowLight
                visual.rain > 0.05f || visual.hail > 0.05f -> if (max(visual.rain, visual.hail) >= 0.5f) RainHeavy else RainLight
                else -> null
            }
        }

        fun columns(widthDp: Float): Int = ceil((widthDp + SLACK_DP) / TILE_WIDTH_DP).toInt().coerceAtLeast(1)

        fun rows(heightDp: Float): Int = ceil((heightDp + SLACK_DP) / TILE_HEIGHT_DP).toInt().coerceAtLeast(1)
    }
}

/**
 * Lays [weather]'s tiles over the widget (or takes them away), cut to the widget's rounded corners.
 * Tiles carry stable ids, so an update showing the same weather keeps them — drops mid-run and all.
 */
internal fun RemoteViews.setLiveWeather(packageName: String, weather: LiveWeather?, widthDp: Float, heightDp: Float, cornerRadiusDp: Float) {
    removeAllViews(R.id.widget_motion)
    if (weather == null) {
        setViewVisibility(R.id.widget_motion, View.GONE)
        return
    }
    setViewVisibility(R.id.widget_motion, View.VISIBLE)
    setViewOutlinePreferredRadius(R.id.widget_motion, cornerRadiusDp, TypedValue.COMPLEX_UNIT_DIP)
    for (row in 0 until LiveWeather.rows(heightDp)) {
        for (column in 0 until LiveWeather.columns(widthDp)) {
            val tile = RemoteViews(packageName, weather.tileAt(column, row))
            tile.setViewLayoutMargin(R.id.motion_tile, RemoteViews.MARGIN_LEFT, column * LiveWeather.TILE_WIDTH_DP, TypedValue.COMPLEX_UNIT_DIP)
            tile.setViewLayoutMargin(R.id.motion_tile, RemoteViews.MARGIN_TOP, row * LiveWeather.TILE_HEIGHT_DP, TypedValue.COMPLEX_UNIT_DIP)
            addStableView(R.id.widget_motion, tile, row * 64 + column + 1)
        }
    }
}

/** The same tiles as views of our own, for previews inside the app. */
internal fun FrameLayout.showLiveWeather(weather: LiveWeather?, widthDp: Float, heightDp: Float) {
    removeAllViews()
    if (weather == null) return
    val inflater = LayoutInflater.from(context)
    val density = resources.displayMetrics.density
    for (row in 0 until LiveWeather.rows(heightDp)) {
        for (column in 0 until LiveWeather.columns(widthDp)) {
            val tile = inflater.inflate(weather.tileAt(column, row), this, false)
            (tile.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = (column * LiveWeather.TILE_WIDTH_DP * density).roundToInt()
                topMargin = (row * LiveWeather.TILE_HEIGHT_DP * density).roundToInt()
            }
            addView(tile)
        }
    }
}
