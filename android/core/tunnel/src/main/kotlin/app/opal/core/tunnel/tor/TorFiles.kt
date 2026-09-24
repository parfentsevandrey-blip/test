package app.opal.core.tunnel.tor

import android.content.Context
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-disk layout for Tor. DataDirectory (state, guards) and CacheDirectory (consensus, descriptors)
 * are both under `filesDir`, never `cacheDir`: the system may purge the cache, and a live consensus
 * is what makes the next start take seconds instead of minutes (the "warm cache").
 */
internal class TorFiles(private val context: Context) {
    val root = File(context.filesDir, "tor")
    val dataDir = File(root, "data")
    val cacheDir = File(root, "cache")
    val torrc = File(root, "torrc")
    val defaultsTorrc = File(root, "torrc-defaults")
    val geoIp = File(root, "geoip")
    val geoIp6 = File(root, "geoip6")
    val transportState = File(context.noBackupFilesDir, "pt")

    fun prepare() {
        for (dir in listOf(root, dataDir, cacheDir, transportState)) {
            dir.mkdirs()
            // Tor refuses group/world-accessible data directories.
            dir.setReadable(false, false)
            dir.setReadable(true, true)
            dir.setWritable(false, false)
            dir.setWritable(true, true)
            dir.setExecutable(false, false)
            dir.setExecutable(true, true)
        }
        if (!defaultsTorrc.exists()) defaultsTorrc.writeText("")
    }

    /** True when a cached consensus exists (bootstrap can skip downloading the whole directory). */
    fun hasCachedConsensus(): Boolean =
        File(cacheDir, "cached-microdesc-consensus").let { it.exists() && it.length() > 0 }

    /**
     * Unpacks the bundled GeoIP databases once per app version. Returns false if assets are
     * missing. Done lazily, after the first successful bootstrap, so it never delays connecting.
     */
    suspend fun ensureGeoIp(versionCode: Long): Boolean =
        withContext(Dispatchers.IO) {
            val marker = File(root, "geoip.version")
            if (
                geoIp.exists() &&
                    geoIp6.exists() &&
                    marker.takeIf { it.exists() }?.readText() == versionCode.toString()
            ) {
                return@withContext true
            }
            try {
                unpack("geoip.gz", geoIp)
                unpack("geoip6.gz", geoIp6)
                marker.writeText(versionCode.toString())
                true
            } catch (_: java.io.IOException) {
                false
            }
        }

    private fun unpack(asset: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        context.assets.open(asset).use { input ->
            GZIPInputStream(input, 64 * 1024).use { gz ->
                tmp.outputStream().use { gz.copyTo(it, 64 * 1024) }
            }
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw java.io.IOException("Cannot move $tmp to $target")
        }
    }
}
