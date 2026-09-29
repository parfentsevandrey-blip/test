package app.rosa.calendar.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.HapticsLevel
import app.rosa.weather.core.model.WeekStart
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** When the paintings are lit by day, and when by the moon. */
enum class PaintingLight { Auto, Day, Night }

/** How the calendar app looks and what it shows; each widget keeps its own settings. */
@Serializable
data class CalendarSettings(
    val weekStart: WeekStart = WeekStart.Auto,
    /** The forecast in the days, from Rosa Weather. */
    val showWeather: Boolean = true,
    /** The phone's events on the month and in the list (once calendar access is allowed). */
    val showEvents: Boolean = true,
    val weekNumbers: Boolean = false,
    /** Each week's motion over its painting. */
    val livePainting: Boolean = true,
    val light: PaintingLight = PaintingLight.Auto,
    val tiltLighting: Boolean = true,
    val haptics: HapticsLevel = HapticsLevel.Rich,
) {
    /** What the shared design system reads: the glass lighting and the haptics. */
    fun asAppSettings(): AppSettings = AppSettings(haptics = haptics, tiltLighting = tiltLighting)
}

@Singleton
class CalendarSettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store: DataStore<CalendarSettings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { CalendarSettings() },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.dataStoreFile(FILE) },
    )

    val settings: Flow<CalendarSettings> = store.data

    suspend fun current(): CalendarSettings = store.data.first()

    suspend fun update(transform: (CalendarSettings) -> CalendarSettings) {
        store.updateData(transform)
    }

    private object SettingsSerializer : Serializer<CalendarSettings> {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = false
        }

        override val defaultValue = CalendarSettings()

        override suspend fun readFrom(input: InputStream): CalendarSettings = try {
            json.decodeFromString(CalendarSettings.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable calendar settings", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Unreadable calendar settings", e)
        }

        override suspend fun writeTo(t: CalendarSettings, output: OutputStream) {
            output.write(json.encodeToString(CalendarSettings.serializer(), t).encodeToByteArray())
        }
    }

    companion object {
        const val FILE = "calendar_settings.json"
    }
}
