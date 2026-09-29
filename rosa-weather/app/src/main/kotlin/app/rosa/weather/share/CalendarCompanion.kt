package app.rosa.weather.share

import android.content.Context
import android.content.Intent
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.sync.SyncReason
import app.rosa.weather.core.data.sync.WeatherSyncListener
import app.rosa.weather.core.model.WeatherShare
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tells Rosa Calendar, when it is installed, that the weather it borrows ([WeatherShareProvider])
 * has changed: after every sync, and when a city is added, removed or opened in the app — its
 * widgets may follow the city open here.
 */
@Singleton
class CalendarCompanion @Inject constructor(
    @ApplicationContext private val context: Context,
    private val places: PlacesRepository,
) : WeatherSyncListener {
    override suspend fun onWeatherChanged(reason: SyncReason) {
        // A render tick brings no new weather: the calendar keeps its own time.
        if (reason != SyncReason.Render) notifyCalendar()
    }

    /** Call once per process. */
    fun followPlaceChanges(scope: CoroutineScope) {
        scope.launch {
            places.saved
                .map { saved -> saved.selected?.id to saved.all.map { it.id } }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest {
                    delay(400) // let a swipe through several cities settle first
                    notifyCalendar()
                }
        }
    }

    private fun notifyCalendar() {
        if (!installed()) return
        context.sendBroadcast(Intent(WeatherShare.ACTION_WEATHER_CHANGED).setPackage(WeatherShare.CALENDAR_PACKAGE))
    }

    private fun installed(): Boolean = runCatching {
        context.packageManager.getPackageInfo(WeatherShare.CALENDAR_PACKAGE, 0)
        true
    }.getOrDefault(false)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CompanionBindings {
    @Binds
    @IntoSet
    abstract fun calendarListener(companion: CalendarCompanion): WeatherSyncListener
}
