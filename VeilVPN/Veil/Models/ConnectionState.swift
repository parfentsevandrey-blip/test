import SwiftUI

/// High-level state of the Tor tunnel as shown in the UI.
enum ConnectionState: Equatable, Sendable {
    case disconnected
    case connecting
    case connected
    case disconnecting
    case failed

    var isConnected: Bool { self == .connected }
    var isConnecting: Bool { self == .connecting }
    var isActive: Bool { self == .connecting || self == .connected }
    var isBusy: Bool { self == .connecting || self == .disconnecting }
    var isFailed: Bool { self == .failed }

    var title: LocalizedStringKey {
        switch self {
        case .disconnected: "Not Connected"
        case .connecting: "Connecting"
        case .connected: "Connected"
        case .disconnecting: "Disconnecting"
        case .failed: "Connection Failed"
        }
    }

    var tint: Color {
        switch self {
        case .disconnected: .indigo
        case .connecting, .disconnecting: .orange
        case .connected: .mint
        case .failed: .red
        }
    }

    var symbolName: String {
        switch self {
        case .connected: "shield.lefthalf.filled"
        case .connecting, .disconnecting: "shield"
        case .failed: "exclamationmark.shield"
        case .disconnected: "shield.slash"
        }
    }
}

/// Progress of Tor's bootstrap as reported in its log (`Bootstrapped 45% (requesting_descriptors): ...`).
struct BootstrapProgress: Equatable, Sendable {
    var percent: Int = 0
    var tag: String = ""
    var summary: String = ""

    var isDone: Bool { percent >= 100 }

    /// A friendlier description of the current phase than Tor's raw summary.
    var phaseTitle: LocalizedStringKey {
        switch tag {
        case "starting": "Starting Tor"
        case "conn_pt", "conn_done_pt": "Reaching a Snowflake proxy"
        case "conn", "conn_done": "Connecting to the Tor network"
        case "handshake", "handshake_done": "Performing TLS handshake"
        case "onehop_create", "requesting_status", "loading_status": "Loading network status"
        case "loading_keys": "Loading authority certificates"
        case "requesting_descriptors", "loading_descriptors": "Loading relay descriptors"
        case "enough_dirinfo", "ap_conn", "ap_conn_done", "ap_handshake", "ap_handshake_done": "Building circuits"
        case "circuit_create": "Establishing a circuit"
        case "done": "Done"
        default: percent == 0 ? "Preparing" : "Bootstrapping"
        }
    }
}

struct ActivePorts: Equatable, Sendable {
    var socks: UInt16
    var http: UInt16
    var control: UInt16
    /// The lane pool's own SOCKS listener, which carries the isolation flags. 0 means the pool is
    /// off. The default keeps every existing `ActivePorts(socks:http:control:)` call compiling.
    var pool: UInt16 = 0
}

enum ProxyStatus: Equatable, Sendable {
    case off
    /// System proxy configured for these network services (e.g. "Wi-Fi").
    case configured([String])
    /// User disabled automatic configuration; proxies must be set manually.
    case manual
    /// Automatic configuration was attempted and failed (permission denied, etc.).
    case failed(String)
}
