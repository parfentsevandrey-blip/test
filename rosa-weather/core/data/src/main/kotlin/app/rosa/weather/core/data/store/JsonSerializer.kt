package app.rosa.weather.core.data.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/** Typed DataStore persistence through kotlinx.serialization JSON. */
internal class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
    private val json: Json = StoreJson,
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T = try {
        json.decodeFromStream(serializer, input)
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        json.encodeToStream(serializer, t, output)
    }
}

internal val StoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    coerceInputValues = true
    allowSpecialFloatingPointValues = true
}
