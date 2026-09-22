package app.papersky.weather.core.data

import androidx.datastore.core.DataStore
import app.papersky.weather.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SettingsRepository(private val store: DataStore<UserSettings>) {
    val settings: Flow<UserSettings> = store.data

    suspend fun current(): UserSettings = store.data.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.updateData(transform)
    }
}
