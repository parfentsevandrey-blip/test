package app.rosa.weather.ui.common

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.rosa.weather.core.designsystem.sky.SkyParams
import app.rosa.weather.core.model.ForecastMoment
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.momentAt

/**
 * The one sky behind every screen. Screens don't own a background; they *tune* this shared sky
 * (the home pager morphs it between cities, the timeline scrubs it through time), and every glass
 * surface in the app refracts it.
 */
@Stable
class SkyController(initial: ForecastMoment) {
    var palette by mutableStateOf(paletteOf(initial))
        private set
    var params by mutableStateOf(SkyParams.from(initial, palette))
        private set
    var transitionMillis by mutableIntStateOf(1400)
        private set

    /** @param immediate true while the user is dragging (pager, timeline): follow the finger exactly. */
    fun show(moment: ForecastMoment, immediate: Boolean) {
        val p = paletteOf(moment)
        transitionMillis = if (immediate) 0 else 1400
        palette = p
        params = SkyParams.from(moment, p)
    }

    /** Blend two moments (e.g. two cities while swiping between them). */
    fun blend(a: ForecastMoment, b: ForecastMoment, fraction: Float) {
        val pa = paletteOf(a)
        val pb = paletteOf(b)
        transitionMillis = 0
        palette = if (fraction < 0.5f) pa else pb
        params = SkyParams.from(a, pa).lerp(SkyParams.from(b, pb), fraction)
    }

    companion object {
        fun paletteOf(m: ForecastMoment) = SkyPalette.of(m.sun.elevation, m.visual, m.moonPhase.illumination)

        /** A calm dusk sky shown before any forecast exists. */
        fun placeholder(nowEpochSeconds: Long): ForecastMoment =
            SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = nowEpochSeconds).momentAt(nowEpochSeconds)
    }
}

val LocalSky = staticCompositionLocalOf<SkyController> { error("SkyController not provided") }
