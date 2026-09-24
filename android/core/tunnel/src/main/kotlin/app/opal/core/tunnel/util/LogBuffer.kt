package app.opal.core.tunnel.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory log (release builds never write logs to disk or logcat). Entries never contain
 * destinations: Tor runs with SafeLogging (addresses scrubbed) and our own messages avoid them; the
 * export additionally redacts anything that looks like an address, fingerprint or bridge secret.
 */
class LogBuffer(private val capacity: Int = 500, private val mirrorToLogcat: Boolean = false) {

    enum class Level {
        Debug,
        Info,
        Warn,
        Error,
    }

    data class Entry(val time: Long, val level: Level, val source: String, val message: String)

    private val entries = ArrayDeque<Entry>(capacity)

    @Synchronized
    fun add(level: Level, source: String, message: String) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(Entry(System.currentTimeMillis(), level, source, message))
        if (mirrorToLogcat) {
            val tag = "Opal/$source"
            when (level) {
                Level.Debug -> Log.d(tag, message)
                Level.Info -> Log.i(tag, message)
                Level.Warn -> Log.w(tag, message)
                Level.Error -> Log.e(tag, message)
            }
        }
    }

    fun d(source: String, message: String) = add(Level.Debug, source, message)

    fun i(source: String, message: String) = add(Level.Info, source, message)

    fun w(source: String, message: String) = add(Level.Warn, source, message)

    fun e(source: String, message: String) = add(Level.Error, source, message)

    @Synchronized fun snapshot(): List<Entry> = entries.toList()

    fun export(): String {
        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
        return snapshot().joinToString("\n") { e ->
            "${format.format(Date(e.time))} ${e.level.name.first()} ${e.source}: ${redact(e.message)}"
        }
    }

    companion object {
        private val IPV4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b")
        private val IPV6 =
            Regex(
                "\\[[0-9a-fA-F:.]+\\](?::\\d{1,5})?|(?<![\\w:.])[0-9a-fA-F]{0,4}(?::[0-9a-fA-F]{0,4}){2,7}(?![\\w:.])"
            )
        private val FINGERPRINT = Regex("\\$?\\b[0-9A-Fa-f]{40}\\b")
        private val SECRET =
            Regex("\\b(cert|url|front|fronts|targets|ice|sqscreds|fingerprint)=\\S+")

        fun redact(text: String): String =
            text
                .replace(SECRET) { "${it.groupValues[1]}=[redacted]" }
                .replace(FINGERPRINT, "[fingerprint]")
                .replace(IPV6, "[address]")
                .replace(IPV4, "[address]")
    }
}
