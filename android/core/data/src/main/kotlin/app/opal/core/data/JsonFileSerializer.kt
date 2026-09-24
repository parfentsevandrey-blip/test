package app.opal.core.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** DataStore serializer that stores a @Serializable value as UTF-8 JSON. */
internal class JsonFileSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString(serializer, bytes.decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Cannot decode ${serializer.descriptor.serialName}", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Cannot decode ${serializer.descriptor.serialName}", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
            // New enum values written by a newer app version must not wipe older settings.
            coerceInputValues = true
        }
    }
}
