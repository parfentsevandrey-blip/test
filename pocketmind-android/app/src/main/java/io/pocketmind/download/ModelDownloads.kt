package io.pocketmind.download

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import io.pocketmind.models.LlmModel
import io.pocketmind.models.ModelCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DownloadState {
    data object NotDownloaded : DownloadState
    data class Queued(val downloadId: Long) : DownloadState
    data class Downloading(val downloadId: Long, val downloadedBytes: Long, val totalBytes: Long) : DownloadState {
        val progress get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    data class Paused(val downloadId: Long, val downloadedBytes: Long, val totalBytes: Long, val reason: String) : DownloadState
    data class Failed(val reason: String) : DownloadState
    data class Downloaded(val file: File) : DownloadState
}

/**
 * Downloads models through the system [DownloadManager]: transfers survive the
 * app being closed, resume after network loss and show a system notification.
 */
class ModelDownloads(
    private val context: Context,
    private val prefs: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val dir: File = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private var poller: Job? = null

    init {
        scope.launch { refresh() }
    }

    fun fileFor(model: LlmModel) = File(dir, model.fileName)
    private fun partFileFor(model: LlmModel) = File(dir, model.fileName + ".part")
    private fun idKey(model: LlmModel) = longPreferencesKey("download_${model.id}")

    fun downloadedFile(model: LlmModel): File? = (_states.value[model.id] as? DownloadState.Downloaded)?.file

    fun start(model: LlmModel) {
        scope.launch {
            val part = partFileFor(model)
            part.delete()
            val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
                .setTitle(model.name)
                .setDescription("Нейросеть для PocketMind")
                .setMimeType("application/octet-stream")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(part))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .addRequestHeader("User-Agent", "PocketMind/1.0 (Android)")
            val id = manager.enqueue(request)
            prefs.edit { it[idKey(model)] = id }
            _states.update { it + (model.id to DownloadState.Queued(id)) }
            ensurePolling()
        }
    }

    fun cancel(model: LlmModel) {
        scope.launch {
            prefs.data.first()[idKey(model)]?.let { manager.remove(it) }
            prefs.edit { it.remove(idKey(model)) }
            partFileFor(model).delete()
            _states.update { it + (model.id to DownloadState.NotDownloaded) }
        }
    }

    fun delete(model: LlmModel) {
        scope.launch(Dispatchers.IO) {
            fileFor(model).delete()
            partFileFor(model).delete()
            _states.update { it + (model.id to DownloadState.NotDownloaded) }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val stored = prefs.data.first()
        val result = ModelCatalog.models.associate { model ->
            model.id to resolveState(model, stored[idKey(model)])
        }
        _states.value = result
        if (result.values.any { it.isActive() }) ensurePolling()
    }

    private suspend fun resolveState(model: LlmModel, downloadId: Long?): DownloadState {
        val file = fileFor(model)
        if (file.exists() && file.length() == model.sizeBytes) return DownloadState.Downloaded(file)
        if (downloadId == null) return DownloadState.NotDownloaded

        val state = manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
            if (cursor.moveToFirst()) readState(cursor, downloadId) else null
        } ?: return DownloadState.NotDownloaded.also { prefs.edit { it.remove(idKey(model)) } }

        return when (state) {
            is CursorState.Running -> DownloadState.Downloading(downloadId, state.downloaded, state.total.takeIf { it > 0 } ?: model.sizeBytes)
            is CursorState.Pending -> DownloadState.Queued(downloadId)
            is CursorState.Paused -> DownloadState.Paused(downloadId, state.downloaded, model.sizeBytes, state.reason)
            is CursorState.Failed -> {
                prefs.edit { it.remove(idKey(model)) }
                manager.remove(downloadId)
                DownloadState.Failed(state.reason)
            }
            CursorState.Successful -> {
                prefs.edit { it.remove(idKey(model)) }
                val part = partFileFor(model)
                when {
                    part.length() != model.sizeBytes -> {
                        part.delete()
                        DownloadState.Failed("Файл скачан не полностью, попробуйте ещё раз")
                    }
                    part.renameTo(file) -> DownloadState.Downloaded(file)
                    else -> DownloadState.Failed("Не удалось сохранить файл модели")
                }
            }
        }
    }

    private fun ensurePolling() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                delay(700)
                refresh()
                if (_states.value.values.none { it.isActive() }) break
            }
        }
    }

    private fun DownloadState.isActive() =
        this is DownloadState.Queued || this is DownloadState.Downloading || this is DownloadState.Paused

    private sealed interface CursorState {
        data class Running(val downloaded: Long, val total: Long) : CursorState
        data object Pending : CursorState
        data class Paused(val downloaded: Long, val reason: String) : CursorState
        data class Failed(val reason: String) : CursorState
        data object Successful : CursorState
    }

    private fun readState(cursor: Cursor, id: Long): CursorState {
        fun long(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
        fun int(column: String) = cursor.getInt(cursor.getColumnIndexOrThrow(column))
        val downloaded = long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val total = long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        return when (int(DownloadManager.COLUMN_STATUS)) {
            DownloadManager.STATUS_SUCCESSFUL -> CursorState.Successful
            DownloadManager.STATUS_RUNNING -> CursorState.Running(downloaded, total)
            DownloadManager.STATUS_PENDING -> CursorState.Pending
            DownloadManager.STATUS_PAUSED -> CursorState.Paused(
                downloaded,
                when (int(DownloadManager.COLUMN_REASON)) {
                    DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Ожидание сети"
                    DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Ожидание Wi-Fi"
                    DownloadManager.PAUSED_WAITING_TO_RETRY -> "Повторная попытка"
                    else -> "Пауза"
                },
            )
            else -> CursorState.Failed(
                when (int(DownloadManager.COLUMN_REASON)) {
                    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Недостаточно места на устройстве"
                    DownloadManager.ERROR_CANNOT_RESUME -> "Загрузку нельзя продолжить, начните заново"
                    DownloadManager.ERROR_HTTP_DATA_ERROR,
                    DownloadManager.ERROR_TOO_MANY_REDIRECTS,
                    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Ошибка сети при загрузке"
                    else -> "Ошибка загрузки (#$id)"
                },
            )
        }
    }
}
