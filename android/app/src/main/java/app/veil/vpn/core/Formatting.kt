package app.veil.vpn.core

import java.util.Locale
import kotlin.math.abs

/** Human-sized byte counts, biased towards short strings that fit a stat tile. */
fun formatBytes(bytes: Long): String {
    val value = abs(bytes)
    return when {
        value < 1_024 -> "$bytes B"
        value < 1_024 * 1_024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        value < 1_024L * 1_024 * 1_024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

fun formatRate(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
