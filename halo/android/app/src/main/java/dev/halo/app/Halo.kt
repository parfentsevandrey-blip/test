package dev.halo.app

import android.content.Context
import android.os.Build
import dev.halo.core.DeviceInfo
import dev.halo.core.MemberInfo
import dev.halo.core.Pairing
import dev.halo.core.PeerState
import dev.halo.core.addMember
import dev.halo.core.deviceInfo
import dev.halo.core.listMembers
import dev.halo.core.removeMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

/**
 * Process-wide state shared by the VPN service and the UI.
 *
 * The functions touch the disk through the Rust core: call them off the main thread.
 */
object Halo {
    lateinit var stateDir: String
        private set

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _peers = MutableStateFlow<List<PeerState>>(emptyList())
    val peers: StateFlow<List<PeerState>> = _peers.asStateFlow()

    private val _members = MutableStateFlow<List<MemberInfo>>(emptyList())
    val members: StateFlow<List<MemberInfo>> = _members.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun init(context: Context) {
        stateDir = context.filesDir.absolutePath
    }

    fun device(): DeviceInfo = deviceInfo(stateDir)

    fun reloadMembers() {
        _members.value = listMembers(stateDir)
    }

    fun addDevice(id: String, name: String, address: String?): MemberInfo {
        val member = addMember(stateDir, id, name, listOfNotNull(address?.takeIf { it.isNotBlank() }))
        reloadMembers()
        return member
    }

    /** Prepares to join a device showing a pairing code. Instant; throws on a malformed code. */
    fun pairing(code: String): Pairing = Pairing(stateDir, code, Build.MODEL)

    /** Answers the emoji question; returns the added device when both sides said yes. */
    fun pairConfirm(pairing: Pairing, accept: Boolean): MemberInfo? {
        val member = pairing.confirm(accept)
        reloadMembers()
        return member
    }

    /**
     * Ends a pairing wherever it is and frees it. Returns at once: telling the
     * other device takes a moment, so that happens on a background thread.
     */
    fun release(pairing: Pairing) {
        pairing.cancel()
        thread(name = "halo-pair-close") { pairing.close() }
    }

    fun removeDevice(name: String) {
        removeMember(stateDir, name)
        reloadMembers()
    }

    fun reportError(message: String?) {
        _error.value = message
    }

    internal fun setRunning(running: Boolean) {
        _running.value = running
        if (!running) _peers.value = emptyList()
    }

    internal fun publish(peers: List<PeerState>) {
        _peers.value = peers
    }
}
