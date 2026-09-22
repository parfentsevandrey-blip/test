package app.papersky.weather.core.data

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import app.papersky.weather.core.model.Forecast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** DataStore serializer for any kotlinx-serializable value, stored as JSON. */
class JsonSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        AppJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(AppJson.encodeToString(serializer, t).encodeToByteArray())
    }
}

fun <T> jsonDataStore(
    context: Context,
    fileName: String,
    default: T,
    serializer: KSerializer<T>,
    scope: CoroutineScope,
): DataStore<T> = DataStoreFactory.create(
    serializer = JsonSerializer(default, serializer),
    corruptionHandler = ReplaceFileCorruptionHandler { default },
    scope = scope,
    produceFile = { context.dataStoreFile(fileName) },
)

/**
 * One JSON file per place, mirrored in memory. Forecasts are comparatively large (~400 hourly
 * rows) so keeping them apart means refreshing one city never rewrites the others.
 */
class ForecastStore(private val dir: File) {
    private val mutex = Mutex()
    private val cache = MutableStateFlow<Map<String, Forecast>?>(null)

    suspend fun ensureLoaded() {
        if (cache.value != null) return
        mutex.withLock {
            if (cache.value != null) return
            val loaded = withContext(Dispatchers.IO) {
                dir.mkdirs()
                dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
                    runCatching {
                        AppJson.decodeFromString(Forecast.serializer(), AtomicFile(file).readFully().decodeToString())
                    }.getOrNull()
                }.associateBy { it.placeId }
            }
            cache.value = loaded
        }
    }

    /** Synchronous read of what is already in memory; call [ensureLoaded] first. */
    fun peek(placeId: String): Forecast? = cache.value?.get(placeId)

    fun observe(placeId: String): Flow<Forecast?> = cache
        .onStart { ensureLoaded() }
        .filterNotNull()
        .map { it[placeId] }
        .distinctUntilChanged()

    suspend fun put(forecast: Forecast) {
        ensureLoaded()
        mutex.withLock {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                val file = AtomicFile(fileFor(forecast.placeId))
                val out = file.startWrite()
                try {
                    out.write(AppJson.encodeToString(Forecast.serializer(), forecast).encodeToByteArray())
                    file.finishWrite(out)
                } catch (e: Exception) {
                    file.failWrite(out)
                    throw e
                }
            }
            cache.update { it.orEmpty() + (forecast.placeId to forecast) }
        }
    }

    suspend fun remove(placeId: String) {
        ensureLoaded()
        mutex.withLock {
            withContext(Dispatchers.IO) { AtomicFile(fileFor(placeId)).delete() }
            cache.update { it.orEmpty() - placeId }
        }
    }

    private fun fileFor(placeId: String) = File(dir, placeId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")
}
