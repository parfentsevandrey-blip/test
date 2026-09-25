package app.rosa.weather.widget.provider

import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetDensity
import app.rosa.weather.core.model.WidgetFace
import app.rosa.weather.core.model.WidgetModule
import app.rosa.weather.core.model.WidgetStyle

/**
 * The entries in the launcher's widget picker. The weather ones share one adaptive engine and
 * differ only in their starting look; the calendar shows the month. Every one can be restyled later
 * in the widget studio.
 */
enum class WidgetKind(val providerClass: Class<*>, val defaultConfig: WidgetConfig) {
    Glass(GlassWidgetProvider::class.java, WidgetConfig(style = WidgetStyle.Glass)),
    Sky(SkyWidgetProvider::class.java, WidgetConfig(style = WidgetStyle.Sky, opacity = 1f)),
    Almanac(
        AlmanacWidgetProvider::class.java,
        WidgetConfig(
            style = WidgetStyle.Paper,
            density = WidgetDensity.Airy,
            modules = listOf(WidgetModule.Headline, WidgetModule.Daily, WidgetModule.SunPath, WidgetModule.Details),
        ),
    ),

    /** The month over a painting of its season. */
    Calendar(CalendarWidgetProvider::class.java, WidgetConfig(face = WidgetFace.Calendar, style = WidgetStyle.Sky, opacity = 1f));

    companion object {
        fun forProvider(className: String?): WidgetKind = entries.firstOrNull { it.providerClass.name == className } ?: Glass
    }
}
