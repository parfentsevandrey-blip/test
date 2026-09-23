package app.rosa.weather.core.data.repository

import androidx.datastore.core.DataStore
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetConfigs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class WidgetConfigRepository @Inject constructor(private val store: DataStore<WidgetConfigs>) {
    val all: Flow<WidgetConfigs> = store.data

    fun config(widgetId: Int): Flow<WidgetConfig> = store.data.map { it[widgetId] }.distinctUntilChanged()

    suspend fun get(widgetId: Int): WidgetConfig = store.data.first()[widgetId]

    suspend fun snapshot(): WidgetConfigs = store.data.first()

    suspend fun put(widgetId: Int, config: WidgetConfig) {
        store.updateData { it.copy(byId = it.byId + (widgetId to config)) }
    }

    suspend fun remove(widgetIds: Collection<Int>) {
        store.updateData { it.copy(byId = it.byId - widgetIds.toSet()) }
    }

    /** Called after a backup restore remaps widget ids. */
    suspend fun remap(oldIds: IntArray, newIds: IntArray) {
        store.updateData { configs ->
            val byId = configs.byId.toMutableMap()
            oldIds.zip(newIds).forEach { (old, new) -> byId.remove(old)?.let { byId[new] = it } }
            configs.copy(byId = byId)
        }
    }
}
