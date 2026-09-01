package app.veil.vpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import app.veil.vpn.VeilApp
import app.veil.vpn.core.VeilLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reconnects after a reboot, but only if the user asked for that and the VPN
 * consent Android stores for us is still valid. If consent has lapsed there is
 * nothing useful to do from a receiver, so we stay quiet rather than throwing a
 * dialog at someone who just turned their phone on.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val app = context.applicationContext as? VeilApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (!app.settings.settings.first().autoStartOnBoot) return@launch
                if (VpnService.prepare(context) != null) {
                    VeilLog.w("boot", "VPN consent is not granted; not starting")
                    return@launch
                }
                context.startForegroundService(Intent(context, VeilVpnService::class.java))
                VeilLog.i("boot", "auto-start requested")
            } finally {
                pending.finish()
            }
        }
    }
}
