//! What a running node tells `halo status`: a short text file in the state
//! directory, rewritten every few seconds. A node running as a service has no
//! terminal, and this needs no socket or port.

use std::{
    collections::HashMap,
    fmt::Write as _,
    str::FromStr,
    time::{Duration, SystemTime},
};

use halo_core::NodeStatus;
use iroh::EndpointId;

/// How often a running node rewrites its report.
pub const INTERVAL: Duration = Duration::from_secs(5);
/// A report older than this belongs to a node that is gone.
const STALE: Duration = Duration::from_secs(30);

/// The report of a running node.
#[derive(Debug, Default)]
pub struct Report {
    pub pid: u32,
    pub removed: bool,
    pub reachable: Vec<String>,
    pub peers: HashMap<EndpointId, Link>,
}

/// How a member is connected.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Link {
    Direct {
        path: String,
        rtt_ms: Option<u64>,
    },
    /// Through another member, for want of a direct connection.
    Via(EndpointId),
    Down,
}

pub fn render(status: &NodeStatus) -> String {
    let mut text = String::from("# Halo node status, rewritten every few seconds while it runs\n");
    let _ = writeln!(text, "pid {}", std::process::id());
    if status.removed {
        text.push_str("removed\n");
    }
    for addr in &status.reachable {
        let _ = writeln!(text, "reachable {addr}");
    }
    for peer in &status.peers {
        let _ = match (peer.connected, peer.via) {
            (true, _) => writeln!(
                text,
                "peer {} direct {} {}",
                peer.id,
                peer.path.as_deref().unwrap_or("-").replace(' ', "_"),
                peer.rtt
                    .map_or_else(|| "-".to_string(), |rtt| rtt.as_millis().to_string())
            ),
            (false, Some(via)) => writeln!(text, "peer {} via {via}", peer.id),
            (false, None) => writeln!(text, "peer {} down", peer.id),
        };
    }
    text
}

/// Reads a report written at `written`; `None` when the node that wrote it is gone.
pub fn parse(text: &str, written: SystemTime) -> Option<Report> {
    let age = SystemTime::now()
        .duration_since(written)
        .unwrap_or(Duration::ZERO);
    if age > STALE {
        return None;
    }
    let mut report = Report::default();
    for line in text.lines() {
        let words: Vec<&str> = line.split_whitespace().collect();
        match words.as_slice() {
            ["pid", pid] => report.pid = pid.parse().unwrap_or(0),
            ["removed"] => report.removed = true,
            ["reachable", addr] => report.reachable.push((*addr).to_string()),
            ["peer", id, rest @ ..] => {
                let Ok(id) = EndpointId::from_str(id) else {
                    continue;
                };
                let link = match rest {
                    ["direct", path, rtt] => Link::Direct {
                        path: (*path).to_string(),
                        rtt_ms: rtt.parse().ok(),
                    },
                    ["via", via] => match EndpointId::from_str(via) {
                        Ok(via) => Link::Via(via),
                        Err(_) => Link::Down,
                    },
                    _ => Link::Down,
                };
                report.peers.insert(id, link);
            }
            _ => {}
        }
    }
    Some(report)
}

#[cfg(test)]
mod tests {
    use std::net::Ipv4Addr;

    use halo_core::PeerStatus;
    use iroh::SecretKey;

    use super::*;

    #[test]
    fn the_report_reads_back() {
        let [a, b, c] = [1u8, 2, 3].map(|seed| SecretKey::from_bytes(&[seed; 32]).public());
        let peer = |id, connected, via| PeerStatus {
            id,
            ip: Ipv4Addr::LOCALHOST,
            connected,
            path: connected.then(|| "192.168.1.20:7777".to_string()),
            rtt: connected.then(|| Duration::from_millis(12)),
            via,
        };
        let status = NodeStatus {
            id: a,
            ip: Ipv4Addr::LOCALHOST,
            peers: vec![
                peer(a, true, None),
                peer(b, false, Some(a)),
                peer(c, false, None),
            ],
            removed: false,
            reachable: vec!["203.0.113.7:7777".parse().unwrap()],
        };
        let report = parse(&render(&status), SystemTime::now()).unwrap();
        assert_eq!(report.pid, std::process::id());
        assert_eq!(report.reachable, ["203.0.113.7:7777"]);
        assert_eq!(
            report.peers[&a],
            Link::Direct {
                path: "192.168.1.20:7777".into(),
                rtt_ms: Some(12)
            }
        );
        assert_eq!(report.peers[&b], Link::Via(a));
        assert_eq!(report.peers[&c], Link::Down);
        // A report nobody rewrote for a while is from a node that is gone.
        let old = SystemTime::now() - Duration::from_secs(60);
        assert!(parse(&render(&status), old).is_none());
    }
}
