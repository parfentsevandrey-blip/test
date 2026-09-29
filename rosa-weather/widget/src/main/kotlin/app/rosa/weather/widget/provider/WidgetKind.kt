package app.rosa.weather.widget.provider

import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetDensity
import app.rosa.weather.core.model.WidgetModule
import app.rosa.weather.core.model.WidgetStyle

/**
 * The weather widgets in the launcher's picker: one adaptive engine, differing only in their
 * starting look. Every one can be restyled later in the widget studio. (The calendar is an app of
 * its own, Rosa Calendar.)
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
    ;

    companion object {
        fun forProvider(className: String?): WidgetKind = entries.firstOrNull { it.providerClass.name == className } ?: Glass
    }
}
