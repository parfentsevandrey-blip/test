//! Halo: a private network of your own devices, peer to peer, without servers.
//!
//! Every device is identified by its key and gets a stable overlay address
//! derived from that key. IP packets from the TUN device travel to member
//! devices inside QUIC datagrams over direct iroh connections.

pub mod addr;
pub mod frame;
pub mod members;
pub mod node;
pub mod state;

#[cfg(not(any(target_os = "android", target_os = "ios")))]
pub use node::create_tun;
pub use node::{
    DEFAULT_MTU, Node, NodeConfig, NodeHandle, NodeStatus, PeerConfig, PeerStatus, run,
};

/// ALPN of the Halo tunnel protocol.
pub const ALPN: &[u8] = b"halo/0";
