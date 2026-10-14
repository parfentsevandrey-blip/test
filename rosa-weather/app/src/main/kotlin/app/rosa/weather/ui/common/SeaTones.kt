package app.rosa.weather.ui.common

import app.rosa.weather.core.designsystem.seaglass.SeaTone
import app.rosa.weather.core.model.WeatherCondition

/**
 * The glass the weather is shown in, in sea glass: cyan for a clear day (peach when it is hot),
 * periwinkle under cloud and rain, lilac at night and in storms, milky in fog and snow.
 */
fun seaToneFor(condition: WeatherCondition, isDay: Boolean, temperature: Double): SeaTone = when (condition.family) {
    WeatherCondition.Family.Clear -> when {
        !isDay -> SeaTone.Lilac
        temperature >= 27.0 -> SeaTone.Peach
        else -> SeaTone.Cyan
    }
    WeatherCondition.Family.Cloudy -> if (isDay) SeaTone.Periwinkle else SeaTone.Lilac
    WeatherCondition.Family.Rain -> SeaTone.Periwinkle
    WeatherCondition.Family.Storm -> SeaTone.Lilac
    WeatherCondition.Family.Fog, WeatherCondition.Family.Snow, WeatherCondition.Family.Ice -> SeaTone.Milk
}
