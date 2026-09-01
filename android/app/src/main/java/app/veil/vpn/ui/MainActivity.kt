package app.veil.vpn.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.veil.vpn.core.VeilLog
import app.veil.vpn.ui.theme.VeilTheme

/**
 * The only Activity.
 *
 * Its real job is the two pieces of ceremony Android puts between a user and a
 * working VPN: the system consent dialog, which can only be raised from an
 * Activity, and the notification permission, without which the foreground
 * service runs invisibly.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: VeilViewModel by viewModels()

    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.startTunnel()
        } else {
            VeilLog.w("ui", "VPN consent declined")
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            VeilLog.w("ui", "notification permission declined; the tunnel will run unannounced")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        setContent {
            VeilTheme {
                VeilScaffold(
                    viewModel = viewModel,
                    onConnectRequested = ::connect,
                )
            }
        }
    }

    /**
     * Android grants the VPN slot to one app at a time and asks the user first.
     * `prepare` returns an Intent when consent is still needed and null once it
     * has been given.
     */
    private fun connect() {
        val consent: Intent? = VpnService.prepare(this)
        if (consent != null) {
            vpnConsent.launch(consent)
        } else {
            viewModel.startTunnel()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
