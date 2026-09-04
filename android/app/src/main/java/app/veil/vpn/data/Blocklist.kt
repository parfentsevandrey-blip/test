package app.veil.vpn.data

import android.content.Context
import app.veil.tun.veiltun.Veiltun
import app.veil.vpn.core.VeilLog
import java.io.File

/**
 * The ad blocker's list of names, shipped inside the APK.
 *
 * Nothing here may depend on a server, and a list fetched at runtime on a
 * censored network is a list that is usually not there — so the list is an
 * asset: a snapshot of Steven Black's unified hosts list (MIT), reduced to one
 * name per line and compressed. It is unpacked into the app's files on first
 * use and again whenever the APK ships a newer snapshot, which the stamp file
 * records. The native tunnel reads the unpacked file when it starts.
 */
object Blocklist {

    private const val ASSET = "blocklist/hosts.xz"
    private const val STAMP_ASSET = "blocklist/STAMP"

    fun file(context: Context): File = File(context.filesDir, "blocklist/hosts.txt")

    private fun stampFile(context: Context): File = File(context.filesDir, "blocklist/STAMP")

    /** Whether the list is unpacked and current, and how many names it holds. */
    fun describe(context: Context): String {
        val target = file(context)
        if (!target.exists() || target.length() == 0L) return "not planted"
        val stamp = stampFile(context).takeIf { it.exists() }?.readText()?.trim() ?: "?"
        return "snapshot $stamp, ${target.length() / 1024} KB"
    }

    /**
     * Makes sure the unpacked list matches the shipped one. Cheap when it
     * already does: a stat and a tiny read.
     */
    fun plant(context: Context): File? = runCatching {
        val target = file(context)
        val stamp = context.assets.open(STAMP_ASSET).bufferedReader().use { it.readText().trim() }
        val planted = stampFile(context).takeIf { it.exists() }?.readText()?.trim()
        if (target.exists() && target.length() > 0 && planted == stamp) return target

        target.parentFile?.mkdirs()
        val packed = File(context.cacheDir, "hosts.xz")
        context.assets.open(ASSET).use { input ->
            packed.outputStream().use { output -> input.copyTo(output) }
        }
        Veiltun.extractXz(packed.absolutePath, target.absolutePath)
        packed.delete()
        stampFile(context).writeText(stamp)
        VeilLog.i("adblock", "planted the blocklist: snapshot $stamp, ${target.length() / 1024} KB")
        target
    }.onFailure { VeilLog.w("adblock", "could not plant the blocklist: $it") }.getOrNull()
}
