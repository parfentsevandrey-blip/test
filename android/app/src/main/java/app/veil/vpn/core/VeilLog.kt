package app.veil.vpn.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A bounded, in-memory log. Diagnosing why a connection failed in a censored
 * network is most of the support burden for a tool like this, so the log is a
 * first-class screen — but it never touches disk, because a log file is
 * evidence sitting on the user's device.
 */
object VeilLog {

    private const val TAG = "Veil"
    private const val CAPACITY = 600

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timestampMillis: Long,
        val level: Level,
        val source: String,
        val message: String,
    ) {
        fun format(): String = "${TIME.format(Date(timestampMillis))}  $source: $message"
    }

    private val TIME = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val entries = ArrayDeque<Entry>(CAPACITY)
    private val _lines = MutableStateFlow<List<Entry>>(emptyList())
    val lines: StateFlow<List<Entry>> = _lines.asStateFlow()

    fun d(source: String, message: String) = add(Level.DEBUG, source, message)
    fun i(source: String, message: String) = add(Level.INFO, source, message)
    fun w(source: String, message: String) = add(Level.WARN, source, message)
    fun e(source: String, message: String, error: Throwable? = null) =
        add(Level.ERROR, source, if (error == null) message else "$message: $error")

    @Synchronized
    private fun add(level: Level, source: String, message: String) {
        when (level) {
            Level.DEBUG -> Log.d(TAG, "$source: $message")
            Level.INFO -> Log.i(TAG, "$source: $message")
            Level.WARN -> Log.w(TAG, "$source: $message")
            Level.ERROR -> Log.e(TAG, "$source: $message")
        }
        entries.addLast(Entry(System.currentTimeMillis(), level, source, message))
        while (entries.size > CAPACITY) entries.removeFirst()
        _lines.value = entries.toList()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        _lines.value = emptyList()
    }

    fun dump(): String = _lines.value.joinToString("\n") { it.format() }
}
