package app.rosa.weather.core.data.repository

import androidx.datastore.core.DataStore
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Units
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<AppSettings>) {
    val settings: Flow<AppSettings> = store.data

    /** Units the user chose, or the regional defaults for the device locale. */
    val units: Flow<Units> = store.data.map { it.resolvedUnits() }.distinctUntilChanged()

    suspend fun current(): AppSettings = store.data.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.updateData(transform)
    }
}

fun AppSettings.resolvedUnits(locale: Locale = Locale.getDefault()): Units = units ?: Units.forCountry(locale.country)
