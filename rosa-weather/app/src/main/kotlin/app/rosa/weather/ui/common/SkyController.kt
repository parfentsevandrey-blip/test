package app.rosa.weather.ui.common

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.rosa.weather.core.designsystem.sky.SkyParams
import app.rosa.weather.core.designsystem.sky.SkyStage
import app.rosa.weather.core.model.Appearance
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
    /** Real sky or a fixed mood (Settings → Appearance); weather stays real either way. */
    var appearance by mutableStateOf(Appearance.Auto)
        private set
    var palette by mutableStateOf(paletteOf(initial, Appearance.Auto))
        private set
    var params by mutableStateOf(SkyParams.from(initial, palette))
        private set
    var transitionMillis by mutableIntStateOf(1400)
        private set

    /** The free sky the sun and moon travel in, measured by the home screen beside its numerals. */
    var stage by mutableStateOf(SkyStage.Default)
        private set

    // What is on screen now, so a change of mood can relight it.
    private var shownA = initial
    private var shownB: ForecastMoment? = null
    private var fraction = 0f

    fun placeBody(stage: SkyStage) {
        if (stage != this.stage) this.stage = stage
    }

    /** Switch the mood; the sky melts into the new light instead of cutting. */
    fun applyAppearance(mode: Appearance) {
        if (mode == appearance) return
        appearance = mode
        transitionMillis = 900
        relight()
    }

    /** @param immediate true while the user is dragging (pager, timeline): follow the finger exactly. */
    fun show(moment: ForecastMoment, immediate: Boolean) {
        shownA = moment
        shownB = null
        transitionMillis = if (immediate) 0 else 1400
        relight()
    }

    /** Blend two moments (e.g. two cities while swiping between them). */
    fun blend(a: ForecastMoment, b: ForecastMoment, fraction: Float) {
        shownA = a
        shownB = b
        this.fraction = fraction
        transitionMillis = 0
        relight()
    }

    private fun relight() {
        val a = shownA
        val pa = paletteOf(a, appearance)
        val b = shownB
        if (b == null) {
            palette = pa
            params = SkyParams.from(a, pa, appearance)
        } else {
            val pb = paletteOf(b, appearance)
            palette = if (fraction < 0.5f) pa else pb
            params = SkyParams.from(a, pa, appearance).lerp(SkyParams.from(b, pb, appearance), fraction)
        }
    }

    companion object {
        fun paletteOf(m: ForecastMoment, appearance: Appearance = Appearance.Auto) =
            SkyPalette.of(appearance, m.sun.elevation, m.visual, m.moonPhase.illumination)

        /** A calm dusk sky shown before any forecast exists. */
        fun placeholder(nowEpochSeconds: Long): ForecastMoment =
            SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = nowEpochSeconds).momentAt(nowEpochSeconds)
    }
}

val LocalSky = staticCompositionLocalOf<SkyController> { error("SkyController not provided") }
