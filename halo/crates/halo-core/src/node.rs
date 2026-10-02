//! A Halo node: the TUN device on one side, encrypted links to member devices on the other.
//!
//! There are no servers: relays and address lookup services are disabled, so a
//! node only talks to the member devices it is configured with, directly.

use std::{
    collections::{HashMap, HashSet},
    future::Future,
    net::{Ipv4Addr, Ipv6Addr, SocketAddr},
    sync::{
        Arc,
        atomic::{AtomicBool, AtomicU64, Ordering::Relaxed},
    },
    time::{Duration, Instant},
};

use anyhow::{Context, Result, anyhow};
use bytes::{Bytes, BytesMut};
use iroh::{
    Endpoint, EndpointAddr, EndpointId, RelayMode, SecretKey,
    endpoint::{AfterHandshakeOutcome, BindOpts, Connection, EndpointHooks, Side, VarInt, presets},
};
use tokio::{sync::mpsc, task::JoinSet};
use tracing::{debug, info, warn};
use tun_rs::AsyncDevice;

use crate::{
    ALPN,
    addr::{OVERLAY_PREFIX, overlay_ipv4},
    frame,
};

/// Default tunnel MTU: the IPv6 minimum, so the same tunnel can carry IPv6 later.
pub const DEFAULT_MTU: u16 = 1280;

/// Packets queued per peer while the link is busy; beyond that they are dropped,
/// like on a router with a full queue.
const QUEUE_LEN: usize = 1024;
const DIAL_TIMEOUT: Duration = Duration::from_secs(10);
const MIN_BACKOFF: Duration = Duration::from_secs(1);
const MAX_BACKOFF: Duration = Duration::from_secs(30);
/// Two connections to the same peer created this close together are a simultaneous dial.
const RACE_WINDOW: Duration = Duration::from_secs(5);

const CLOSE_NOT_MEMBER: VarInt = VarInt::from_u32(1);
const CLOSE_DUPLICATE: VarInt = VarInt::from_u32(2);

/// A member device this node talks to.
#[derive(Debug, Clone)]
pub struct PeerConfig {
    /// The device key.
    pub id: EndpointId,
    /// Known direct addresses. Empty means the peer is expected to dial us.
    pub addrs: Vec<SocketAddr>,
}

/// Node configuration.
#[derive(Debug, Clone)]
pub struct NodeConfig {
    /// The key of this device.
    pub secret_key: SecretKey,
    /// UDP port to listen on; 0 picks a random port.
    pub port: u16,
    /// MTU of the TUN device.
    pub mtu: u16,
    /// Member devices. Connections from any other key are rejected during the handshake.
    pub peers: Vec<PeerConfig>,
    /// Log traffic counters at this interval.
    pub stats_interval: Option<Duration>,
}

/// Creates the TUN device for a node on desktop platforms.
///
/// On Android the device comes from `VpnService` instead.
pub fn create_tun(name: Option<&str>, ip: Ipv4Addr, mtu: u16) -> Result<AsyncDevice> {
    let mut builder = tun_rs::DeviceBuilder::new()
        .ipv4(ip, OVERLAY_PREFIX, None)
        .mtu(mtu);
    if let Some(name) = name {
        builder = builder.name(name);
    }
    builder
        .build_async()
        .context("failed to create the TUN device (root / administrator rights are required)")
}

/// Runs a node until `shutdown` completes or a fatal error occurs.
pub async fn run(
    config: NodeConfig,
    tun: AsyncDevice,
    shutdown: impl Future<Output = ()>,
) -> Result<()> {
    let me = config.secret_key.public();
    let my_ip = overlay_ipv4(&me);
    let members: HashSet<EndpointId> = config.peers.iter().map(|peer| peer.id).collect();
    let endpoint = bind_endpoint(&config, members).await?;
    info!(id = %me, ip = %my_ip, "node up");
    for addr in endpoint.addr().ip_addrs() {
        info!(%addr, "listening");
    }

    let tun = Arc::new(tun);
    let stats = Arc::new(Stats::default());
    let mut by_ip = HashMap::new();
    let mut by_id = HashMap::new();
    let mut tasks = JoinSet::new();
    for peer in config.peers {
        let (outbound_tx, outbound_rx) = mpsc::channel(QUEUE_LEN);
        let (incoming_tx, incoming_rx) = mpsc::channel(4);
        let ip = overlay_ipv4(&peer.id);
        let state = Arc::new(Peer {
            id: peer.id,
            ip,
            addrs: peer.addrs,
            outbound: outbound_tx,
            incoming: incoming_tx,
            connected: AtomicBool::new(false),
        });
        info!(peer = %peer.id.fmt_short(), %ip, "member");
        by_ip.insert(ip, state.clone());
        by_id.insert(peer.id, state.clone());
        let link = Link {
            me,
            my_ip,
            peer: state,
            endpoint: endpoint.clone(),
            tun: tun.clone(),
            stats: stats.clone(),
        };
        tasks.spawn(link.run(outbound_rx, incoming_rx));
    }
    let transport_ports = endpoint
        .bound_sockets()
        .iter()
        .map(SocketAddr::port)
        .collect();
    tasks.spawn(accept_loop(endpoint.clone(), Arc::new(by_id)));
    tasks.spawn(tun_loop(
        tun,
        Arc::new(by_ip),
        transport_ports,
        stats.clone(),
        config.mtu,
    ));
    if let Some(interval) = config.stats_interval {
        tasks.spawn(log_stats(stats, interval));
    }

    let result = tokio::select! {
        Some(done) = tasks.join_next() => match done {
            Ok(Ok(())) => Err(anyhow!("a node task stopped unexpectedly")),
            Ok(Err(err)) => Err(err),
            Err(err) => Err(err.into()),
        },
        () = shutdown => Ok(()),
    };
    tasks.shutdown().await;
    endpoint.close().await;
    result
}

async fn bind_endpoint(config: &NodeConfig, members: HashSet<EndpointId>) -> Result<Endpoint> {
    let v4 = SocketAddr::from((Ipv4Addr::UNSPECIFIED, config.port));
    let v6 = SocketAddr::from((Ipv6Addr::UNSPECIFIED, config.port));
    let endpoint = Endpoint::builder(presets::Minimal)
        .secret_key(config.secret_key.clone())
        .alpns(vec![ALPN.to_vec()])
        // No servers: no relays and no address lookup services.
        .relay_mode(RelayMode::Disabled)
        .clear_address_lookup()
        .hooks(MembersOnly(members))
        .clear_ip_transports()
        .bind_addr(v4)?
        .bind_addr_with_opts(v6, BindOpts::default().set_is_required(false))?
        .bind()
        .await
        .context("failed to bind the endpoint")?;
    Ok(endpoint)
}

/// Rejects every key that is not a member, right after the TLS handshake.
#[derive(Debug)]
struct MembersOnly(HashSet<EndpointId>);

impl EndpointHooks for MembersOnly {
    fn after_handshake<'a>(
        &'a self,
        conn: &'a Connection,
    ) -> impl Future<Output = AfterHandshakeOutcome> + Send + 'a {
        let remote = conn.remote_id();
        let outcome = if self.0.contains(&remote) {
            AfterHandshakeOutcome::accept()
        } else {
            warn!(remote = %remote.fmt_short(), "rejected a device that is not a member");
            AfterHandshakeOutcome::Reject {
                error_code: CLOSE_NOT_MEMBER,
                reason: b"not a member".to_vec(),
            }
        };
        async move { outcome }
    }
}

struct Peer {
    id: EndpointId,
    ip: Ipv4Addr,
    addrs: Vec<SocketAddr>,
    /// Frames from the TUN device waiting to be sent to this peer.
    outbound: mpsc::Sender<Bytes>,
    /// Connections from this peer, handed over by the accept loop.
    incoming: mpsc::Sender<Connection>,
    connected: AtomicBool,
}

#[derive(Debug, Default)]
struct Stats {
    from_tun: AtomicU64,
    to_tun: AtomicU64,
    fragmented: AtomicU64,
    dropped_no_route: AtomicU64,
    dropped_queue_full: AtomicU64,
    dropped_spoofed: AtomicU64,
    dropped_loop: AtomicU64,
}

/// Reads packets from the TUN device and queues them for the peer that owns the destination.
///
/// iroh advertises every local address to peers, the tunnel's own included, so it
/// may try to reach a peer through the tunnel itself. Such packets come from our
/// transport's UDP port and are dropped: a path inside the tunnel never validates.
async fn tun_loop(
    tun: Arc<AsyncDevice>,
    peers: Arc<HashMap<Ipv4Addr, Arc<Peer>>>,
    transport_ports: Vec<u16>,
    stats: Arc<Stats>,
    mtu: u16,
) -> Result<()> {
    loop {
        // One byte of headroom for the frame tag, so whole packets go out without a copy.
        let mut buf = BytesMut::zeroed(usize::from(mtu) + 1);
        let len = tun.recv(&mut buf[1..]).await.context("TUN read failed")?;
        buf.truncate(len + 1);
        buf[0] = frame::WHOLE;
        stats.from_tun.fetch_add(1, Relaxed);
        let Some((_, dst)) = frame::ipv4_endpoints(&buf[1..]) else {
            continue;
        };
        if frame::ipv4_udp_src_port(&buf[1..]).is_some_and(|port| transport_ports.contains(&port)) {
            stats.dropped_loop.fetch_add(1, Relaxed);
            continue;
        }
        match peers.get(&dst) {
            Some(peer) if peer.connected.load(Relaxed) => {
                if peer.outbound.try_send(buf.freeze()).is_err() {
                    stats.dropped_queue_full.fetch_add(1, Relaxed);
                }
            }
            _ => {
                stats.dropped_no_route.fetch_add(1, Relaxed);
            }
        }
    }
}

/// Routes incoming connections to the link of the peer they come from.
async fn accept_loop(endpoint: Endpoint, peers: Arc<HashMap<EndpointId, Arc<Peer>>>) -> Result<()> {
    while let Some(incoming) = endpoint.accept().await {
        let peers = peers.clone();
        tokio::spawn(async move {
            let accepting = match incoming.accept() {
                Ok(accepting) => accepting,
                Err(err) => return debug!(%err, "incoming connection failed"),
            };
            let conn = match accepting.await {
                Ok(conn) => conn,
                Err(err) => return debug!(%err, "handshake failed"),
            };
            match peers.get(&conn.remote_id()) {
                Some(peer) => {
                    // The link is gone only during shutdown.
                    let _ = peer.incoming.send(conn).await;
                }
                None => conn.close(CLOSE_NOT_MEMBER, b"not a member"),
            }
        });
    }
    Err(anyhow!("endpoint closed"))
}

/// Keeps one connection to one peer alive and pumps packets over it.
struct Link {
    me: EndpointId,
    my_ip: Ipv4Addr,
    peer: Arc<Peer>,
    endpoint: Endpoint,
    tun: Arc<AsyncDevice>,
    stats: Arc<Stats>,
}

impl Link {
    async fn run(
        self,
        mut outbound: mpsc::Receiver<Bytes>,
        mut incoming: mpsc::Receiver<Connection>,
    ) -> Result<()> {
        let mut delay = Duration::ZERO;
        let mut next = None;
        loop {
            let conn = match next.take() {
                Some(conn) => conn,
                None => {
                    let dial = async {
                        if self.peer.addrs.is_empty() {
                            std::future::pending::<()>().await;
                        }
                        tokio::time::sleep(delay).await;
                        self.dial().await
                    };
                    tokio::select! {
                        Some(conn) = incoming.recv() => conn,
                        dialed = dial => match dialed {
                            Ok(conn) => conn,
                            Err(err) => {
                                debug!(peer = %self.peer.id.fmt_short(), "dial failed: {err:#}");
                                delay = (delay * 2).clamp(MIN_BACKOFF, MAX_BACKOFF);
                                continue;
                            }
                        },
                    }
                }
            };
            next = self.serve(conn, &mut outbound, &mut incoming).await;
            delay = MIN_BACKOFF;
        }
    }

    async fn dial(&self) -> Result<Connection> {
        let addr = self
            .peer
            .addrs
            .iter()
            .fold(EndpointAddr::new(self.peer.id), |addr, ip| {
                addr.with_ip_addr(*ip)
            });
        let conn = tokio::time::timeout(DIAL_TIMEOUT, self.endpoint.connect(addr, ALPN))
            .await
            .context("timed out")??;
        Ok(conn)
    }

    /// Pumps packets over `conn` until it closes or a better connection to the same
    /// peer shows up, which is then returned.
    async fn serve(
        &self,
        conn: Connection,
        outbound: &mut mpsc::Receiver<Bytes>,
        incoming: &mut mpsc::Receiver<Connection>,
    ) -> Option<Connection> {
        let path = conn
            .paths()
            .iter()
            .find(|path| path.is_selected())
            .map(|path| format!("{:?}", path.remote_addr()))
            .unwrap_or_default();
        info!(peer = %self.peer.id.fmt_short(), ip = %self.peer.ip, side = ?conn.side(), %path, "connected");
        // Whatever was queued before the connection came up is stale by now.
        while outbound.try_recv().is_ok() {}
        self.peer.connected.store(true, Relaxed);
        let established = Instant::now();

        let receive = self.receive(&conn);
        let send = self.send(&conn, outbound);
        tokio::pin!(receive, send);
        let next = loop {
            tokio::select! {
                err = &mut receive => {
                    info!(peer = %self.peer.id.fmt_short(), "disconnected: {err:#}");
                    break None;
                }
                err = &mut send => {
                    info!(peer = %self.peer.id.fmt_short(), "disconnected: {err:#}");
                    break None;
                }
                Some(other) = incoming.recv() => {
                    if self.prefer(&other, &conn, established.elapsed()) {
                        conn.close(CLOSE_DUPLICATE, b"duplicate");
                        break Some(other);
                    }
                    other.close(CLOSE_DUPLICATE, b"duplicate");
                }
            }
        };
        self.peer.connected.store(false, Relaxed);
        next
    }

    /// Decides which of two connections to the same peer to keep.
    ///
    /// A peer only dials when it has no connection to us. So a new connection
    /// long after the current one came up means the peer lost the old one
    /// (restart, network change) and the new one wins. Shortly after, it is a
    /// simultaneous dial from both ends: both sides then keep the connection
    /// dialed by the smaller key, so they converge on the same one.
    fn prefer(&self, candidate: &Connection, current: &Connection, current_age: Duration) -> bool {
        if current_age > RACE_WINDOW {
            return true;
        }
        let dialer = |conn: &Connection| match conn.side() {
            Side::Client => self.me,
            Side::Server => self.peer.id,
        };
        let (candidate, current) = (dialer(candidate), dialer(current));
        candidate == current || candidate == self.me.min(self.peer.id)
    }

    async fn receive(&self, conn: &Connection) -> anyhow::Error {
        let mut reassembler = frame::Reassembler::default();
        loop {
            let datagram = match conn.read_datagram().await {
                Ok(datagram) => datagram,
                Err(err) => return err.into(),
            };
            let Some(packet) = reassembler.push(datagram) else {
                continue;
            };
            // A peer may only send packets from its own address, and only to us.
            match frame::ipv4_endpoints(&packet) {
                Some((src, dst)) if src == self.peer.ip && dst == self.my_ip => {
                    match self.tun.send(&packet).await {
                        Ok(_) => {
                            self.stats.to_tun.fetch_add(1, Relaxed);
                        }
                        // The kernel rejects malformed packets; that is the sender's problem.
                        Err(err) => debug!(%err, "TUN write failed"),
                    }
                }
                _ => {
                    self.stats.dropped_spoofed.fetch_add(1, Relaxed);
                }
            }
        }
    }

    async fn send(&self, conn: &Connection, outbound: &mut mpsc::Receiver<Bytes>) -> anyhow::Error {
        let mut next_id = 0u16;
        loop {
            let Some(frame) = outbound.recv().await else {
                return anyhow!("node is shutting down");
            };
            let Some(max) = conn.max_datagram_size() else {
                return anyhow!("the peer does not accept datagrams");
            };
            let Some(datagrams) = frame::split(frame, max, next_id) else {
                continue;
            };
            if datagrams.len() > 1 {
                next_id = next_id.wrapping_add(1);
                self.stats.fragmented.fetch_add(1, Relaxed);
            }
            for datagram in datagrams {
                if let Err(err) = conn.send_datagram_wait(datagram).await {
                    if let Some(reason) = conn.close_reason() {
                        return reason.into();
                    }
                    // The path shrank between the size check and the send: drop the packet.
                    debug!(%err, "datagram dropped");
                    break;
                }
            }
        }
    }
}

async fn log_stats(stats: Arc<Stats>, interval: Duration) -> Result<()> {
    let mut ticker = tokio::time::interval(interval);
    ticker.tick().await;
    loop {
        ticker.tick().await;
        info!(
            from_tun = stats.from_tun.load(Relaxed),
            to_tun = stats.to_tun.load(Relaxed),
            fragmented = stats.fragmented.load(Relaxed),
            dropped_no_route = stats.dropped_no_route.load(Relaxed),
            dropped_queue_full = stats.dropped_queue_full.load(Relaxed),
            dropped_spoofed = stats.dropped_spoofed.load(Relaxed),
            dropped_loop = stats.dropped_loop.load(Relaxed),
            "stats"
        );
    }
}
