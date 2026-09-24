package app.opal.core.model.tunnel

import kotlinx.serialization.Serializable

/**
 * User-facing grouping of Tor's bootstrap tags (control-spec "Bootstrap phases"). Several tags map
 * to one phase so the label does not flicker.
 */
@Serializable
enum class BootstrapPhase {
    Starting,
    ConnectingToBridge,
    Handshake,
    LoadingDirectory,
    LoadingRelays,
    ConnectingToNetwork,
    BuildingCircuit,
    Done;

    companion object {
        fun fromTag(tag: String): BootstrapPhase =
            when (tag) {
                "starting" -> Starting
                "conn_pt",
                "conn_done_pt",
                "conn_proxy",
                "conn_done_proxy",
                "conn",
                "conn_done" -> ConnectingToBridge
                "handshake",
                "handshake_done",
                "onehop_create" -> Handshake
                "requesting_status",
                "loading_status",
                "loading_keys" -> LoadingDirectory
                "requesting_descriptors",
                "loading_descriptors",
                "enough_dirinfo" -> LoadingRelays
                "ap_conn_pt",
                "ap_conn_done_pt",
                "ap_conn_proxy",
                "ap_conn_done_proxy",
                "ap_conn",
                "ap_conn_done",
                "ap_handshake",
                "ap_handshake_done" -> ConnectingToNetwork
                "circuit_create" -> BuildingCircuit
                "done" -> Done
                else -> Starting
            }
    }
}
