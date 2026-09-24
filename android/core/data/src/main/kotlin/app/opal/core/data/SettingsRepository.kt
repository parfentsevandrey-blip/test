package app.opal.core.data

import androidx.datastore.core.DataStore
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.TunnelMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

class SettingsRepository(private val store: DataStore<AppSettings>) {

    val settings: Flow<AppSettings> = store.data.distinctUntilChanged()

    suspend fun current(): AppSettings = store.data.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.updateData(transform)
    }
}

class TunnelMemoryRepository(private val store: DataStore<TunnelMemory>) {

    val memory: Flow<TunnelMemory> = store.data.distinctUntilChanged()

    suspend fun current(): TunnelMemory = store.data.first()

    suspend fun update(transform: (TunnelMemory) -> TunnelMemory) {
        store.updateData(transform)
    }
}
