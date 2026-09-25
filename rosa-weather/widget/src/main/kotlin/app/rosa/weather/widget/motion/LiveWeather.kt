package app.rosa.weather.widget.motion

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import app.rosa.weather.core.model.ForecastMoment
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetFace
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
enum class LiveWeather(
    private val tiles: IntArray,
    private val storm: Boolean = false,
    /** Tiles for the top row alone, where the sky is: falling stars above the fireflies. */
    private val sky: IntArray? = null,
    /** The calendar's seasons: taller tiles, a variant per column, the rows of a column joining. */
    private val seasonal: Boolean = false,
) {
    RainLight(intArrayOf(R.layout.motion_rain_light_a, R.layout.motion_rain_light_b, R.layout.motion_rain_light_c, R.layout.motion_rain_light_d)),
    RainHeavy(intArrayOf(R.layout.motion_rain_heavy_a, R.layout.motion_rain_heavy_b, R.layout.motion_rain_heavy_c, R.layout.motion_rain_heavy_d)),

    /** Heavy rain and lightning: every tile flashes together; the top row carries the bolts. */
    Storm(intArrayOf(R.layout.motion_storm_a, R.layout.motion_storm_b, R.layout.motion_storm_c, R.layout.motion_storm_d), storm = true),
    SnowLight(intArrayOf(R.layout.motion_snow_light)),
    SnowHeavy(intArrayOf(R.layout.motion_snow_heavy)),

    /** September's birch leaves and October's maple leaves, swinging down and turning over. */
    LeavesGold(intArrayOf(R.layout.motion_season_leaves_gold_a, R.layout.motion_season_leaves_gold_b, R.layout.motion_season_leaves_gold_c, R.layout.motion_season_leaves_gold_d), seasonal = true),
    LeavesRed(intArrayOf(R.layout.motion_season_leaves_red_a, R.layout.motion_season_leaves_red_b, R.layout.motion_season_leaves_red_c, R.layout.motion_season_leaves_red_d), seasonal = true),

    /** May's apple petals, fluttering on the wind. */
    Petals(intArrayOf(R.layout.motion_season_petals_a, R.layout.motion_season_petals_b, R.layout.motion_season_petals_c, R.layout.motion_season_petals_d), seasonal = true),

    /** July's dusk: fireflies over the rye, kept low in the top row so the sky stays clear. */
    Fireflies(
        intArrayOf(R.layout.motion_season_fireflies_a, R.layout.motion_season_fireflies_b, R.layout.motion_season_fireflies_c, R.layout.motion_season_fireflies_d),
        sky = intArrayOf(R.layout.motion_season_fireflies_low_a, R.layout.motion_season_fireflies_low_b, R.layout.motion_season_fireflies_low_c, R.layout.motion_season_fireflies_low_d),
        seasonal = true,
    ),

    /** August's night: stars falling in the sky of the top row, fireflies below. */
    Night(
        intArrayOf(R.layout.motion_season_fireflies_a, R.layout.motion_season_fireflies_b, R.layout.motion_season_fireflies_c, R.layout.motion_season_fireflies_d),
        sky = intArrayOf(R.layout.motion_season_meteors_a, R.layout.motion_season_meteors_b, R.layout.motion_season_meteors_c, R.layout.motion_season_meteors_d),
        seasonal = true,
    ),

    /** January's frost in the sunlit air: ice crystals glinting as they sink. */
    Frost(intArrayOf(R.layout.motion_season_glitter_a, R.layout.motion_season_glitter_b, R.layout.motion_season_glitter_c, R.layout.motion_season_glitter_d), seasonal = true),

    /** June's poplar fluff, wandering on the air. */
    Fluff(intArrayOf(R.layout.motion_season_fluff_a, R.layout.motion_season_fluff_b, R.layout.motion_season_fluff_c, R.layout.motion_season_fluff_d), seasonal = true),
    ;

    /** One tile repeated everywhere: snow, whose pattern never shows a seam. */
    val isSingleTile: Boolean get() = tiles.size == 1

    /** Whether a column's tiles repeat down it, so what falls runs on from one into the next. */
    val joinsDown: Boolean get() = seasonal

    val tileWidth: Float get() = TILE_WIDTH_DP
    val tileHeight: Float get() = if (seasonal) SEASON_TILE_HEIGHT_DP else TILE_HEIGHT_DP

    /** The tile at [column], [row]: never the same drops as a neighbour, bolts only along the top. */
    @LayoutRes
    fun tileAt(column: Int, row: Int): Int = when {
        sky != null && row == 0 -> sky[column % sky.size]
        tiles.size == 1 -> tiles[0]
        storm -> if (row == 0) tiles[column % 2] else tiles[2 + (column + row) % 2]
        seasonal -> tiles[column % tiles.size]
        else -> tiles[(column + 2 * row + row / 2) % 4]
    }

    fun columns(widthDp: Float): Int = ceil((widthDp + SLACK_DP) / tileWidth).toInt().coerceAtLeast(1)

    fun rows(heightDp: Float): Int = ceil((heightDp + SLACK_DP) / tileHeight).toInt().coerceAtLeast(1)

    companion object {
        const val TILE_WIDTH_DP = 180f
        const val TILE_HEIGHT_DP = 90f
        const val SEASON_TILE_HEIGHT_DP = 180f

        /** Launchers may show a widget a little larger than the size it was drawn for. */
        private const val SLACK_DP = 12f

        /** What moves on a widget with [config] at [moment], or null when nothing falls or flashes. */
        fun of(config: WidgetConfig, moment: ForecastMoment?): LiveWeather? {
            if (moment == null || config.face != WidgetFace.Weather || !config.liveWeather || !config.showWeatherArt || config.style == WidgetStyle.Paper) return null
            val visual = moment.visual
            return when {
                visual.lightning > 0.1f -> Storm
                visual.snow > 0.05f && visual.snow >= visual.rain -> if (visual.snow >= 0.55f) SnowHeavy else SnowLight
                visual.rain > 0.05f || visual.hail > 0.05f -> if (max(visual.rain, visual.hail) >= 0.5f) RainHeavy else RainLight
                else -> null
            }
        }

        /**
         * What moves over a calendar's painting of [month]: January's glinting frost, February's
         * and November's snow, December's heavier, April's rain, May's petals, June's poplar fluff,
         * the fireflies of July's dusk, August's falling stars, the leaves of September and October.
         * March stands still, thawing.
         */
        fun ofSeason(config: WidgetConfig, month: Int): LiveWeather? {
            if (config.face != WidgetFace.Calendar || !config.liveWeather) return null
            if (config.style != WidgetStyle.Sky && config.style != WidgetStyle.Glass) return null
            return when (month) {
                1 -> Frost
                2, 11 -> SnowLight
                12 -> SnowHeavy
                4 -> RainLight
                5 -> Petals
                6 -> Fluff
                7 -> Fireflies
                8 -> Night
                9 -> LeavesGold
                10 -> LeavesRed
                else -> null
            }
        }
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
    for (row in 0 until weather.rows(heightDp)) {
        for (column in 0 until weather.columns(widthDp)) {
            val tile = RemoteViews(packageName, weather.tileAt(column, row))
            tile.setViewLayoutMargin(R.id.motion_tile, RemoteViews.MARGIN_LEFT, column * weather.tileWidth, TypedValue.COMPLEX_UNIT_DIP)
            tile.setViewLayoutMargin(R.id.motion_tile, RemoteViews.MARGIN_TOP, row * weather.tileHeight, TypedValue.COMPLEX_UNIT_DIP)
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
    for (row in 0 until weather.rows(heightDp)) {
        for (column in 0 until weather.columns(widthDp)) {
            val tile = inflater.inflate(weather.tileAt(column, row), this, false)
            (tile.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = (column * weather.tileWidth * density).roundToInt()
                topMargin = (row * weather.tileHeight * density).roundToInt()
            }
            addView(tile)
        }
    }
}
