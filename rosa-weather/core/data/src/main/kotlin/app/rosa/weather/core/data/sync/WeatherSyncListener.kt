package app.rosa.weather.core.data.sync

enum class SyncReason { Periodic, CatchUp, UserRequest, AppForeground, Render, SystemEvent }

/**
 * Hook for anything that must react after weather data may have changed — primarily the widget
 * module, which re-renders every widget. Contributed via Hilt multibinding (`@IntoSet`).
 */
fun interface WeatherSyncListener {
    suspend fun onWeatherChanged(reason: SyncReason)
}
