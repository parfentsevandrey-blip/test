package app.rosa.calendar.widget

import android.content.Context
import app.rosa.calendar.weather.SharedWeather
import app.rosa.weather.core.data.repository.WidgetConfigRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * What the calendar's receivers and jobs reach for, outside of any injected class. (Named apart
 * from the weather widgets' entry point, which the shared widget module brings along.)
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CalendarGraph {
    fun calendars(): CalendarWidgetUpdater
    fun calendarConfigs(): WidgetConfigRepository
    fun sharedWeather(): SharedWeather
}

internal fun Context.calendarGraph(): CalendarGraph =
    EntryPointAccessors.fromApplication(applicationContext, CalendarGraph::class.java)
