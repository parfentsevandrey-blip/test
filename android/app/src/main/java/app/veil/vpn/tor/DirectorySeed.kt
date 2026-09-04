package app.veil.vpn.tor

import android.content.Context
import app.veil.tun.veiltun.Veiltun
import app.veil.vpn.core.VeilLog
import java.io.File

/**
 * Gives a fresh install the network directory before tor asks for it.
 *
 * A first connect over Snowflake is mostly not Snowflake. Once the bridge is
 * reached, tor downloads the consensus and a microdescriptor for every one of
 * some nine thousand relays, over a path that on one user's Wi-Fi managed 68
 * KB/s — and it will not build a circuit until it has enough of them. That is
 * the "15% … 80%" stretch of the bootstrap, and it is minutes.
 *
 * The APK ships those documents, gathered at build time from the Tor Project's
 * public archive and stored in the exact files tor keeps in its DataDirectory.
 * Planted before tor starts, they are read on its first run: measured locally,
 * a tor started on them reports "Reloaded microdescriptor cache. Found 9453
 * descriptors", accepts the consensus with seven good signatures, and goes
 * straight from "Starting" to "Connecting to a relay".
 *
 * Planted once. Tor maintains the files from then on — the consensus goes
 * stale within a day and it fetches a fresh one, as a diff where it can — and
 * a cache tor has been keeping is newer than anything the APK can carry, so an
 * existing one is never touched.
 */
object DirectorySeed {

    private val files = listOf("cached-microdesc-consensus", "cached-microdescs", "cached-certs")

    /** Where tor-android keeps its DataDirectory: the same call TorService makes. */
    fun dataDirectory(context: Context): File =
        File(context.getDir("TorService", Context.MODE_PRIVATE), "data")

    /** True when tor already has a directory cache of its own. */
    fun isPresent(context: Context): Boolean =
        File(dataDirectory(context), "cached-microdescs").let { it.exists() && it.length() > 0 }

    fun plant(context: Context) {
        if (isPresent(context)) {
            VeilLog.d("seed", "tor has its own directory cache; not planting")
            return
        }
        val started = System.currentTimeMillis()
        val target = dataDirectory(context).apply { mkdirs() }
        val staging = File(context.cacheDir, "seed").apply { mkdirs() }
        var planted = 0
        for (name in files) {
            val packed = File(staging, "$name.xz")
            val result = runCatching {
                context.assets.open("seed/$name.xz").use { input ->
                    packed.outputStream().use { output -> input.copyTo(output) }
                }
                Veiltun.extractXz(packed.absolutePath, File(target, name).absolutePath)
            }
            packed.delete()
            result.onSuccess { planted += 1 }
                .onFailure { VeilLog.w("seed", "could not plant $name: ${it.message}") }
        }
        if (planted == files.size) {
            VeilLog.i(
                "seed",
                "directory planted for tor's first run in ${System.currentTimeMillis() - started}ms " +
                    "(${File(target, "cached-microdescs").length() / 1024} KB of descriptors)",
            )
        } else {
            // Half a seed is worse than none: a consensus without certificates
            // is ignored, and descriptors without a consensus are unused. Tor
            // will download everything itself, as it always could.
            files.forEach { File(target, it).delete() }
            VeilLog.w("seed", "planted $planted/${files.size}; removed, tor will fetch the directory itself")
        }
    }

    /**
     * When the consensus tor has on disk was made, for the diagnostic.
     *
     * The first lines of the file say so. A day past valid-until tor stops
     * trusting it and fetches another, which is the cost of an old seed; the
     * descriptors, which are the bulk, outlive it by weeks.
     */
    fun consensusAge(context: Context): String? = runCatching {
        val file = File(dataDirectory(context), "cached-microdesc-consensus")
        if (!file.exists()) return null
        file.bufferedReader().useLines { lines ->
            lines.take(12).firstOrNull { it.startsWith("valid-after ") }?.removePrefix("valid-after ")
        }
    }.getOrNull()
}
