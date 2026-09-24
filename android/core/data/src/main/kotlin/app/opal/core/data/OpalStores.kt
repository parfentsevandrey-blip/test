package app.opal.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.TunnelMemory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide DataStore instances. Both the UI process and `:tunnel` read the same files, so the
 * multi-process implementation is used (file locks + version counter; changes propagate). There
 * must be exactly one instance per file per process, hence the lazy singletons.
 */
object OpalStores {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var settings: DataStore<AppSettings>? = null
    @Volatile private var memory: DataStore<TunnelMemory>? = null

    fun settings(context: Context): DataStore<AppSettings> =
        settings
            ?: synchronized(this) {
                settings
                    ?: create(context, "settings.json", AppSettings.serializer(), AppSettings())
                        .also {
                            settings = it
                        }
            }

    fun tunnelMemory(context: Context): DataStore<TunnelMemory> =
        memory
            ?: synchronized(this) {
                memory
                    ?: create(
                            context,
                            "tunnel_memory.json",
                            TunnelMemory.serializer(),
                            TunnelMemory(),
                        )
                        .also { memory = it }
            }

    private fun <T> create(
        context: Context,
        name: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        default: T,
    ): DataStore<T> {
        val dir = File(context.applicationContext.noBackupFilesDir, "datastore")
        return MultiProcessDataStoreFactory.create(
            serializer = JsonFileSerializer(serializer, default),
            corruptionHandler = ReplaceFileCorruptionHandler { default },
            migrations = emptyList(),
            scope = scope,
            produceFile = { File(dir, name) },
        )
    }
}
