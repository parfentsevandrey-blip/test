package app.opal.core.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun PackageManager.queryLauncherActivities(intent: Intent) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION") queryIntentActivities(intent, 0)
    }

private fun PackageManager.applicationInfoOrNull(packageName: String): ApplicationInfo? =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") getApplicationInfo(packageName, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/**
 * Apps that can be chosen for split tunnelling. Visibility comes from the manifest `<queries>`
 * (LAUNCHER intent + a few named system packages) instead of QUERY_ALL_PACKAGES: only apps with a
 * launcher icon or listed explicitly can be seen, which covers everything a user would pick.
 */
class InstalledAppsRepository(context: Context) {
    private val appContext = context.applicationContext

    suspend fun load(): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = appContext.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val fromLauncher =
                pm.queryLauncherActivities(launcher).map { it.activityInfo.applicationInfo }
            val named = EXTRA_VISIBLE_PACKAGES.mapNotNull { pm.applicationInfoOrNull(it) }
            (fromLauncher + named)
                .distinctBy { it.packageName }
                .filter { it.packageName != appContext.packageName }
                .map { info ->
                    InstalledApp(
                        packageName = info.packageName,
                        label = info.loadLabel(pm).toString(),
                        isSystem =
                            info.flags and
                                (ApplicationInfo.FLAG_SYSTEM or
                                    ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }

    companion object {
        /**
         * System components without a launcher icon that users commonly want to route directly
         * (push notifications). Must stay in sync with `<queries>` in the manifest.
         */
        val EXTRA_VISIBLE_PACKAGES = listOf("com.google.android.gms", "com.google.android.gsf")
    }
}
