package app.veil.vpn.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One entry in the split-tunnelling list. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val hasInternet: Boolean,
)

/**
 * The installed applications a VPN could meaningfully route.
 *
 * Apps without the INTERNET permission are filtered out: including them would
 * pad the list with dozens of entries that could never send a packet either way.
 */
object InstalledApps {

    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val manager = context.packageManager
        val self = context.packageName
        val flags = PackageManager.GET_META_DATA

        manager.getInstalledApplications(flags)
            .asSequence()
            .filter { it.packageName != self }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { manager.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                    icon = runCatching { manager.getApplicationIcon(info) }.getOrNull(),
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    hasInternet = manager.checkPermission(
                        android.Manifest.permission.INTERNET,
                        info.packageName,
                    ) == PackageManager.PERMISSION_GRANTED,
                )
            }
            .filter { it.hasInternet }
            .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
            .toList()
    }
}
