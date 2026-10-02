package dev.halo.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.halo.app.ui.HaloScreen
import dev.halo.app.ui.HaloTheme

class MainActivity : ComponentActivity() {
    private val askVpn =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startHalo()
            } else {
                Halo.reportError(getString(R.string.error_vpn_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloTheme {
                HaloScreen(
                    onConnect = ::connect,
                    onDisconnect = { send(HaloVpnService.ACTION_STOP) },
                    onMembersChanged = { if (Halo.running.value) send(HaloVpnService.ACTION_RESTART) },
                )
            }
        }
    }

    private fun connect() {
        // The system asks the user once to allow this app to set up a VPN.
        val consent = VpnService.prepare(this)
        if (consent != null) askVpn.launch(consent) else startHalo()
    }

    private fun startHalo() {
        startService(Intent(this, HaloVpnService::class.java))
    }

    private fun send(action: String) {
        startService(Intent(this, HaloVpnService::class.java).setAction(action))
    }
}
