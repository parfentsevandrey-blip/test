package app.rosa.calendar.weather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.rosa.calendar.widget.calendarGraph
import app.rosa.calendar.widget.redrawCalendars
import app.rosa.weather.core.model.WeatherShare

/** Rosa Weather's weather, or the city it has open, changed: the calendars show it now. */
class WeatherChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WeatherShare.ACTION_WEATHER_CHANGED) return
        context.calendarGraph().sharedWeather().changed()
        redrawCalendars(context)
    }
}
