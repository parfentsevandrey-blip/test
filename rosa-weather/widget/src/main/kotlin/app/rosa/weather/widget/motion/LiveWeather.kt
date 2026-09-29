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
import app.rosa.weather.widget.render.WidgetBackground
import app.rosa.weather.widget.render.calendar.WeekArt
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Weather that moves on the home screen. A launcher never runs our code between updates, but it
 * does play an AnimatedVectorDrawable inside a ProgressBar — on its own render thread, the way a
 * system spinner turns, and only while the widget is on screen. So rain, snow and lightning are
 * vector tiles laid over the widget's picture: [TILE_WIDTH_DP] × [TILE_HEIGHT_DP] each, joining
 * seamlessly side by side, in variants that never repeat a neighbour — and in every other weather
 * the sky moves: the sun's rays turn round it, clouds drift, stars twinkle, fog rolls by ([LiveScene]).
 * The tiles themselves are generated (MotionResources, in the widget's tests).
 */
enum class LiveWeather(
    private val tiles: IntArray,
    private val storm: Boolean = false,
    /** Tiles for the top row alone, where the sky is: falling stars above the fireflies. */
    private val sky: IntArray? = null,
    /** The calendar's seasons: taller tiles, a variant per column, the rows of a column joining. */
    private val seasonal: Boolean = false,
    /** Only along the top, where the sky is: birds, clouds. */
    val skyOnly: Boolean = false,
    /** Square tiles, [SEASON_TILE_HEIGHT_DP] tall, in variants that never repeat a neighbour: stars. */
    private val square: Boolean = false,
    /** Drifting sideways over [WIDE_TILE_WIDTH_DP]-wide tiles, one pattern along the row: clouds, fog. */
    private val wide: Boolean = false,
    /** Laid once, [anchorSizeDp] across, centred where the sun or moon is; 0 for tiles. */
    val anchorSizeDp: Float = 0f,
    /** Rain, snow or lightning: while it moves, the picture leaves it out. */
    val falls: Boolean = false,
) {
    RainLight(intArrayOf(R.layout.motion_rain_light_a, R.layout.motion_rain_light_b, R.layout.motion_rain_light_c, R.layout.motion_rain_light_d), falls = true),
    RainHeavy(intArrayOf(R.layout.motion_rain_heavy_a, R.layout.motion_rain_heavy_b, R.layout.motion_rain_heavy_c, R.layout.motion_rain_heavy_d), falls = true),

    /** Heavy rain and lightning: every tile flashes together; the top row carries the bolts. */
    Storm(intArrayOf(R.layout.motion_storm_a, R.layout.motion_storm_b, R.layout.motion_storm_c, R.layout.motion_storm_d), storm = true, falls = true),
    SnowLight(intArrayOf(R.layout.motion_snow_light), falls = true),
    SnowHeavy(intArrayOf(R.layout.motion_snow_heavy), falls = true),

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

    /** A blizzard: snow driven slantwise across in streaks and gusts. */
    Blizzard(intArrayOf(R.layout.motion_season_blizzard), seasonal = true),

    /** Sparks from a fire going up, flickering out as they rise: a hearth, a campfire, Maslenitsa. */
    Embers(intArrayOf(R.layout.motion_season_embers_a, R.layout.motion_season_embers_b, R.layout.motion_season_embers_c, R.layout.motion_season_embers_d), seasonal = true),

    /** Drops falling from the eaves and the trees in the thaw, each catching the sun. */
    Drips(intArrayOf(R.layout.motion_season_drips_a, R.layout.motion_season_drips_b, R.layout.motion_season_drips_c, R.layout.motion_season_drips_d), seasonal = true),

    /** Dust in the sunlight of a room, or pollen on a warm evening: motes drifting and glinting. */
    Motes(intArrayOf(R.layout.motion_season_motes_a, R.layout.motion_season_motes_b, R.layout.motion_season_motes_c, R.layout.motion_season_motes_d), seasonal = true),

    /** Birds wheeling in the sky: rooks, gulls, cranes — in the top row alone, where the sky is. */
    Birds(intArrayOf(R.layout.motion_season_birds_a, R.layout.motion_season_birds_b, R.layout.motion_season_birds_c, R.layout.motion_season_birds_d), seasonal = true, skyOnly = true),

    /** Lilac's small purple and white florets, coming down. */
    Lilac(intArrayOf(R.layout.motion_season_lilac_a, R.layout.motion_season_lilac_b, R.layout.motion_season_lilac_c, R.layout.motion_season_lilac_d), seasonal = true),

    /** Butterflies over a summer meadow, fluttering on their wandering ways. */
    Butterflies(intArrayOf(R.layout.motion_season_butterflies_a, R.layout.motion_season_butterflies_b, R.layout.motion_season_butterflies_c, R.layout.motion_season_butterflies_d), seasonal = true),

    /** Mist drifting slowly over water, thickening and thinning. */
    Mist(intArrayOf(R.layout.motion_season_mist_a, R.layout.motion_season_mist_b, R.layout.motion_season_mist_c, R.layout.motion_season_mist_d), seasonal = true),

    /** The orange of a maple alley in October, falling thick. */
    LeavesOrange(intArrayOf(R.layout.motion_season_leaves_orange_a, R.layout.motion_season_leaves_orange_b, R.layout.motion_season_leaves_orange_c, R.layout.motion_season_leaves_orange_d), seasonal = true),

    /** Lights twinkling: the New Year's garlands and the glints of the ice. */
    Twinkle(intArrayOf(R.layout.motion_season_twinkle_a, R.layout.motion_season_twinkle_b, R.layout.motion_season_twinkle_c, R.layout.motion_season_twinkle_d), seasonal = true),

    // The sky's own motion for the weather widget. New entries go last: the calendar keeps its
    // pages on disk with these ordinals.

    /** The sun: two fans of broad, faint beams turning slowly round it, each its own way, its glow breathing. */
    Sun(intArrayOf(R.layout.motion_sky_sun), anchorSizeDp = 260f),

    /** The moon's halo breathing, a few glints winking near it. */
    Moon(intArrayOf(R.layout.motion_sky_moon), anchorSizeDp = 150f),

    /** Wisps of cloud drifting across the sky, thickening and thinning as they go. */
    Clouds(intArrayOf(R.layout.motion_sky_clouds), skyOnly = true, wide = true),

    /** The same by night, dim and blue. */
    CloudsNight(intArrayOf(R.layout.motion_sky_clouds_night), skyOnly = true, wide = true),

    /** A low grey sky drifting slowly by. */
    Overcast(intArrayOf(R.layout.motion_sky_overcast), skyOnly = true, wide = true),

    /** A clear night: stars twinkling, the brightest flashing, now and then one falling. */
    Stars(intArrayOf(R.layout.motion_sky_stars_a, R.layout.motion_sky_stars_b, R.layout.motion_sky_stars_c, R.layout.motion_sky_stars_d), square = true),

    /** Banks of fog rolling by. */
    Fog(intArrayOf(R.layout.motion_sky_fog), wide = true),
    ;

    /** One tile repeated everywhere: snow, clouds, fog, whose pattern never shows a seam. */
    val isSingleTile: Boolean get() = tiles.size == 1

    /** Laid once where the sun or moon is, not tiled. */
    val anchored: Boolean get() = anchorSizeDp > 0f

    /** Whether a column's tiles repeat down it, so what falls runs on from one into the next. */
    val joinsDown: Boolean get() = seasonal

    val tileWidth: Float get() = if (anchored) anchorSizeDp else if (wide) WIDE_TILE_WIDTH_DP else TILE_WIDTH_DP
    val tileHeight: Float get() = if (anchored) anchorSizeDp else if (seasonal || square || wide) SEASON_TILE_HEIGHT_DP else TILE_HEIGHT_DP

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

    fun rows(heightDp: Float): Int = if (skyOnly) 1 else ceil((heightDp + SLACK_DP) / tileHeight).toInt().coerceAtLeast(1)

    companion object {
        const val TILE_WIDTH_DP = 180f
        const val TILE_HEIGHT_DP = 90f
        const val SEASON_TILE_HEIGHT_DP = 180f
        const val WIDE_TILE_WIDTH_DP = 360f

        /** Launchers may show a widget a little larger than the size it was drawn for. */
        private const val SLACK_DP = 12f

        /** What falls or flashes on a widget with [config] at [moment]: rain, snow, a storm; else null. */
        fun of(config: WidgetConfig, moment: ForecastMoment?): LiveWeather? {
            if (moment == null || !shows(config)) return null
            val visual = moment.visual
            return when {
                visual.lightning > 0.1f -> Storm
                visual.snow > 0.05f && visual.snow >= visual.rain -> if (visual.snow >= 0.55f) SnowHeavy else SnowLight
                visual.rain > 0.05f || visual.hail > 0.05f -> if (max(visual.rain, visual.hail) >= 0.5f) RainHeavy else RainLight
                else -> null
            }
        }

        /** Whether a weather widget with [config] shows its weather moving at all. */
        internal fun shows(config: WidgetConfig): Boolean =
            config.face == WidgetFace.Weather && config.liveWeather && config.showWeatherArt && config.style != WidgetStyle.Paper

        /**
         * What moves over a calendar's picture of [week] (1..52): each week's own — the frost
         * glinting in January's sun, the blizzard, sparks going up from the fire, drops from the
         * eaves, rooks and cranes crossing the sky, petals, fluff, butterflies, fireflies, falling
         * stars, mist on the water, the leaves of September and October, the New Year's lights.
         */
        fun ofSeason(config: WidgetConfig, week: Int): LiveWeather? {
            if (config.face != WidgetFace.Calendar || !config.liveWeather) return null
            if (config.style != WidgetStyle.Sky && config.style != WidgetStyle.Glass) return null
            return WeekArt.of(week).motion
        }
    }
}

/**
 * Everything that moves over a weather widget, far to near. In rain, snow or a storm, that; in
 * every other weather the sky itself — stars, the sun's rays or the moon's halo where the picture
 * has them ([bodyX], [bodyY]: fractions of the widget), clouds drifting, fog rolling by — so the
 * widget is never still.
 */
data class LiveScene(val layers: List<LiveWeather>, val bodyX: Float = 0.5f, val bodyY: Float = 0.2f) {
    /** Rain, snow or lightning move: the picture leaves them out. */
    val falls: Boolean get() = layers.any { it.falls }

    companion object {
        fun of(config: WidgetConfig, moment: ForecastMoment?): LiveScene? {
            if (moment == null || !LiveWeather.shows(config)) return null
            // The sun or the moon, where the picture draws it.
            val sunUp = moment.sun.elevation > -4
            val body = if (sunUp) {
                WidgetBackground.anchorFor(moment.sun.elevation, moment.sun.azimuth, true, moment.moonPhase.phase)
            } else {
                WidgetBackground.anchorFor(moment.moon.elevation, moment.moon.azimuth, false, moment.moonPhase.phase)
            }
            LiveWeather.of(config, moment)?.let { return LiveScene(listOf(it), body.x, body.y) }
            val visual = moment.visual
            val clouds = visual.cloudCover
            // Fog hides the sun and the moon: a pale disc at most, no beams, no halo.
            val clear = clouds < 0.8f && visual.fog < 0.45f
            val layers = buildList {
                if (sunUp) {
                    // The sun shines through anything short of overcast, as the picture draws it.
                    if (clear) add(LiveWeather.Sun)
                } else {
                    if (clouds < 0.6f && visual.fog < 0.45f) add(LiveWeather.Stars)
                    if (clear && moment.moon.elevation > -2) add(LiveWeather.Moon)
                }
                when {
                    clouds >= 0.8f -> add(if (sunUp) LiveWeather.Overcast else LiveWeather.CloudsNight)
                    clouds >= 0.3f -> add(if (sunUp) LiveWeather.Clouds else LiveWeather.CloudsNight)
                }
                if (visual.fog >= 0.45f) add(LiveWeather.Fog)
            }
            return if (layers.isEmpty()) null else LiveScene(layers, body.x, body.y)
        }
    }
}

/**
 * Lays [scene]'s layers over the widget (or takes them away), cut to the widget's rounded corners.
 * Tiles carry stable ids, so an update showing the same weather keeps them — drops mid-run and all.
 */
fun RemoteViews.setLiveScene(packageName: String, scene: LiveScene?, widthDp: Float, heightDp: Float, cornerRadiusDp: Float) {
    removeAllViews(R.id.widget_motion)
    if (scene == null || scene.layers.isEmpty()) {
        setViewVisibility(R.id.widget_motion, View.GONE)
        return
    }
    setViewVisibility(R.id.widget_motion, View.VISIBLE)
    setViewOutlinePreferredRadius(R.id.widget_motion, cornerRadiusDp, TypedValue.COMPLEX_UNIT_DIP)
    scene.forEachTile(widthDp, heightDp) { layer, id, tileLayout, left, top ->
        val tile = RemoteViews(packageName, tileLayout)
        tile.setViewLayoutMargin(R.id.motion_tile, RemoteViews.MARGIN_LEFT, left, TypedValue.COMPLEX_UNIT_DIP)
        tile.setViewLayoutMargin(R.id.motion_tile, RemoteViews.MARGIN_TOP, top, TypedValue.COMPLEX_UNIT_DIP)
        addStableView(R.id.widget_motion, tile, layer * 4096 + id)
    }
}

/** Lays [weather]'s tiles over the widget (or takes them away): a scene of one layer. */
fun RemoteViews.setLiveWeather(packageName: String, weather: LiveWeather?, widthDp: Float, heightDp: Float, cornerRadiusDp: Float) =
    setLiveScene(packageName, weather?.let { LiveScene(listOf(it)) }, widthDp, heightDp, cornerRadiusDp)

/** The same tiles as views of our own, for previews inside the app. */
fun FrameLayout.showLiveScene(scene: LiveScene?, widthDp: Float, heightDp: Float) {
    removeAllViews()
    if (scene == null) return
    val inflater = LayoutInflater.from(context)
    val density = resources.displayMetrics.density
    scene.forEachTile(widthDp, heightDp) { _, _, tileLayout, left, top ->
        val tile = inflater.inflate(tileLayout, this, false)
        (tile.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = (left * density).roundToInt()
            topMargin = (top * density).roundToInt()
        }
        addView(tile)
    }
}

/** A single layer's tiles as views of our own, for previews inside the app. */
fun FrameLayout.showLiveWeather(weather: LiveWeather?, widthDp: Float, heightDp: Float) =
    showLiveScene(weather?.let { LiveScene(listOf(it)) }, widthDp, heightDp)

/**
 * Every tile of the scene, far to near: its layer's index, an id stable within the layer, its
 * layout and where it goes (dp from the widget's top left). The sun's and moon's are centred on it.
 */
private inline fun LiveScene.forEachTile(widthDp: Float, heightDp: Float, place: (layer: Int, id: Int, tile: Int, left: Float, top: Float) -> Unit) {
    layers.forEachIndexed { index, weather ->
        if (weather.anchored) {
            val half = weather.anchorSizeDp / 2f
            place(index, 1, weather.tileAt(0, 0), bodyX * widthDp - half, bodyY * heightDp - half)
        } else {
            for (row in 0 until weather.rows(heightDp)) {
                for (column in 0 until weather.columns(widthDp)) {
                    place(index, row * 64 + column + 1, weather.tileAt(column, row), column * weather.tileWidth, row * weather.tileHeight)
                }
            }
        }
    }
}
