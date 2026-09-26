package app.opal.feature.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.opal.core.tunnel.TunnelTileService

/** Device state the settings screen shows; re-read whenever the screen resumes. */
@Immutable
data class SystemStatus(
    val ignoringBatteryOptimizations: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val canRequestTile: Boolean = false,
)

@Composable
fun rememberSystemStatus(): SystemStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(SystemIntents.read(context)) }
    LifecycleResumeEffect(context) {
        status = SystemIntents.read(context)
        onPauseOrDispose {}
    }
    return status
}

/** System screens the app sends the user to. Every call survives a missing activity. */
object SystemIntents {

    fun read(context: Context) =
        SystemStatus(
            ignoringBatteryOptimizations =
                context
                    .getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true,
            notificationsEnabled =
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
            canRequestTile = TunnelTileService.canRequestAdd,
        )

    fun openVpnSettings(context: Context) = start(context, Intent(Settings.ACTION_VPN_SETTINGS))

    /**
     * Direct exemption dialog (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS); when already exempt, the list
     * of optimisation settings instead.
     */
    @SuppressLint("BatteryLife") // A VPN that must survive Doze is an accepted use case.
    fun requestBatteryExemption(context: Context) {
        val pm = context.getSystemService(PowerManager::class.java)
        val intent =
            if (pm?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    "package:${context.packageName}".toUri(),
                )
            }
        if (!start(context, intent))
            start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openNotificationSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (!start(context, intent)) openAppDetails(context)
    }

    fun openAppDetails(context: Context) =
        start(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            ),
        )

    private fun start(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
