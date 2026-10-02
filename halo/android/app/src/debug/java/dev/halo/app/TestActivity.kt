package dev.halo.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log

/**
 * Debug-only automation for the emulator end-to-end test, driven over adb:
 *
 *   adb shell am start -W -n dev.halo.app/.TestActivity --es cmd info
 *   adb shell am start -W -n dev.halo.app/.TestActivity --es cmd add --es id <ID> --es name pc --es addr 10.0.2.2:7777
 *   adb shell am start -W -n dev.halo.app/.TestActivity --es cmd connect
 *   adb shell am start -W -n dev.halo.app/.TestActivity --es cmd status
 *
 * Results go to logcat under the `halo-test` tag. Grant the VPN first:
 * `adb shell appops set dev.halo.app ACTIVATE_VPN allow`.
 */
class TestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cmd = intent.getStringExtra("cmd")
        try {
            when (cmd) {
                "info" -> {
                    val device = Halo.device()
                    Log.i(TAG, "HALO_INFO id=${device.id} ip=${device.ip}")
                }
                "add" -> {
                    val member = Halo.addDevice(
                        requireNotNull(intent.getStringExtra("id")),
                        requireNotNull(intent.getStringExtra("name")),
                        intent.getStringExtra("addr"),
                    )
                    Log.i(TAG, "HALO_ADDED ${member.name} ${member.ip}")
                }
                "connect" -> {
                    check(VpnService.prepare(this) == null) { "VPN is not allowed for the app" }
                    startService(Intent(this, HaloVpnService::class.java))
                    Log.i(TAG, "HALO_CONNECTING")
                }
                "disconnect" -> {
                    startService(Intent(this, HaloVpnService::class.java).setAction(HaloVpnService.ACTION_STOP))
                    Log.i(TAG, "HALO_DISCONNECTING")
                }
                "status" -> {
                    val peers = Halo.peers.value.joinToString { "${it.ip}=${if (it.connected) it.path else "down"}" }
                    Log.i(TAG, "HALO_STATUS running=${Halo.running.value} error=${Halo.error.value} peers=[$peers]")
                }
                else -> Log.e(TAG, "HALO_ERROR unknown command $cmd")
            }
        } catch (e: Exception) {
            Log.e(TAG, "HALO_ERROR ${e.message}", e)
        }
        finish()
    }

    private companion object {
        const val TAG = "halo-test"
    }
}
