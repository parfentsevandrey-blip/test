package app.rosa.weather.core.model

import kotlinx.serialization.Serializable

/** What a home-screen widget shows. */
@Serializable
enum class WidgetFace {
    /** The weather: the adaptive layout of hero, hours, days and details. */
    Weather,

    /** The month as a grid, over a painting of its season. */
    Calendar,
}

/** Visual treatment of a home-screen widget. */
@Serializable
enum class WidgetStyle {
    /** Liquid-glass pane tinted by the sky, with a lensing rim and specular edge. */
    Glass,

    /** A painted window onto the live sky: gradient, sun or moon, clouds and precipitation. */
    Sky,

    /** No pane at all — type floats directly on the wallpaper (like iOS "clear" widgets). */
    Clear,

    /** Material You tonal surface driven by the wallpaper's dynamic colours. */
    Tonal,

    /** Warm paper and ink: an analogue almanac page. */
    Paper,
}

@Serializable
enum class WidgetTheme { Auto, Light, Dark }

@Serializable
enum class WidgetAccent { Sky, Temperature, Dynamic, Mono }

/** Content blocks the adaptive layout may place, in the user's preferred priority order. */
@Serializable
enum class WidgetModule { Headline, Hourly, Daily, Details, Nowcast, SunPath }

@Serializable
enum class WidgetDensity { Compact, Balanced, Airy }

@Serializable
enum class WidgetTapAction { OpenApp, Refresh }

@Serializable
data class WidgetConfig(
    val face: WidgetFace = WidgetFace.Weather,
    /** A place id, [Place.CURRENT_ID], or [Place.FOLLOW_APP_ID] (the default). */
    val placeId: String = Place.FOLLOW_APP_ID,
    val style: WidgetStyle = WidgetStyle.Glass,
    val theme: WidgetTheme = WidgetTheme.Auto,
    val accent: WidgetAccent = WidgetAccent.Sky,
    /** Pane opacity 0..1 (ignored for [WidgetStyle.Clear]). */
    val opacity: Float = 0.72f,
    /** Corner radius in dp; negative means "use the launcher's system radius". */
    val cornerRadiusDp: Float = -1f,
    val textScale: Float = 1f,
    val density: WidgetDensity = WidgetDensity.Balanced,
    val modules: List<WidgetModule> = DefaultModules,
    val showLocation: Boolean = true,
    val showFeelsLike: Boolean = true,
    val showUpdatedTime: Boolean = false,
    val showWeatherArt: Boolean = true,
    /** Rain, snow and lightning move on the home screen while they happen (with [showWeatherArt]). */
    val liveWeather: Boolean = true,
    /** A thick, lit glass bezel around the pane (not drawn for [WidgetStyle.Paper]). */
    val glassRim: Boolean = true,
    val tapAction: WidgetTapAction = WidgetTapAction.OpenApp,
    /** For a [WidgetFace.Calendar] widget. */
    val calendar: CalendarOptions = CalendarOptions(),
) {
    fun has(module: WidgetModule) = module in modules

    companion object {
        val DefaultModules = listOf(
            WidgetModule.Headline,
            WidgetModule.Hourly,
            WidgetModule.Daily,
            WidgetModule.Details,
            WidgetModule.Nowcast,
        )
    }
}

@Serializable
data class WidgetConfigs(val byId: Map<Int, WidgetConfig> = emptyMap()) {
    operator fun get(id: Int): WidgetConfig = byId[id] ?: WidgetConfig()
}
