package app.rosa.weather.core.data.store

import app.rosa.weather.core.model.Forecast
import kotlinx.serialization.Serializable

@Serializable
data class ForecastCache(val byPlaceId: Map<String, Forecast> = emptyMap())
