//! A Halo node: the TUN device on one side, encrypted links to member devices on the other.
//!
//! There are no servers: relays and internet address lookup are disabled. A node
//! talks directly to its member devices, using addresses it was given or finds
//! on the local network over mDNS.
//!
//! With a member journal, members come and go while the node runs: connected
//! members exchange journal entries, and the node follows the journal file that
//! commands like `halo pair` and `halo remove` write.
//!
//! The node also asks the router for a port that forwards to it (UPnP, NAT-PMP,
//! PCP) and tells the members where it can be reached from anywhere, so they
//! find it from other networks too.

use std::{
    borrow::Cow,
    collections::{HashMap, HashSet},
    future::Future,
    net::{Ipv4Addr, Ipv6Addr, SocketAddr, SocketAddrV4},
    num::NonZeroU16,
    sync::{
        Arc, Mutex, RwLock, Weak,
        atomic::{AtomicBool, AtomicU64, Ordering::Relaxed},
    },
    time::{Duration, Instant, SystemTime},
};

use anyhow::{Context, Result, anyhow};
use bytes::{Bytes, BytesMut};
use iroh::{
    Endpoint, EndpointAddr, EndpointId, RelayMode, SecretKey, TransportAddr, Watcher,
    address_lookup::AddrFilter,
    endpoint::{
        AfterHandshakeOutcome, BindOpts, Connection, EndpointHooks, Side, VarInt,
        WeakConnectionHandle, presets,
    },
};
use iroh_mdns_address_lookup::{DiscoveryEvent, MdnsAddressLookup};
use n0_future::{StreamExt, task::AbortOnDropHandle};
use tokio::{
    sync::{Notify, mpsc},
    task::JoinSet,
};
use tracing::{debug, info, warn};
use tun_rs::AsyncDevice;

use crate::{
    ALPN,
    addr::{is_overlay, overlay_ipv4},
    frame,
    journal::{Entry, Journal, View},
    presence::{Presence, Presences, is_public},
    state::State,
    sync::{self, Kind},
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
/// How often the node looks for changes other programs made to the journal file.
const JOURNAL_POLL: Duration = Duration::from_secs(2);
/// How long a new node waits for a port mapping before saying where it can be reached.
const MAPPING_GRACE: Duration = Duration::from_secs(10);
/// How often the node checks where it can be reached, besides on every change.
const REACH_RECHECK: Duration = Duration::from_secs(300);

/// mDNS service name, so Halo devices do not mix with other iroh apps on the network.
const MDNS_SERVICE: &str = "halo";

const CLOSE_NOT_MEMBER: VarInt = VarInt::from_u32(1);
const CLOSE_DUPLICATE: VarInt = VarInt::from_u32(2);
const CLOSE_MOVED: VarInt = VarInt::from_u32(3);

/// A member device this node talks to.
#[derive(Debug, Clone)]
pub struct PeerConfig {
    /// The device key.
    pub id: EndpointId,
    /// Known direct addresses. May be empty: the peer is then found on the local
    /// network, or dials us itself.
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
    /// Devices to connect to besides the members in the journal, or all of them
    /// without a journal. Connections from any other key are rejected during
    /// the handshake.
    pub peers: Vec<PeerConfig>,
    /// The state directory whose member journal the node follows and shares
    /// with the other members. Without one, the members stay as given.
    pub journal: Option<State>,
    /// Find member devices on the local network over mDNS.
    pub lan_discovery: bool,
    /// Log traffic counters at this interval.
    pub stats_interval: Option<Duration>,
}

/// Creates the TUN device for a node on desktop platforms.
///
/// On Android the device comes from `VpnService` instead.
#[cfg(not(any(target_os = "android", target_os = "ios")))]
pub fn create_tun(name: Option<&str>, ip: Ipv4Addr, mtu: u16) -> Result<AsyncDevice> {
    let mut builder = tun_rs::DeviceBuilder::new()
        .ipv4(ip, crate::addr::OVERLAY_PREFIX, None)
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
    Node::start(config, tun).await?.run_until(shutdown).await
}

/// A running node.
pub struct Node {
    shared: Arc<Shared>,
    tasks: JoinSet<Result<()>>,
}

impl Node {
    /// Binds the endpoint and starts connecting to the member devices.
    pub async fn start(config: NodeConfig, tun: AsyncDevice) -> Result<Self> {
        let me = config.secret_key.public();
        let my_ip = overlay_ipv4(&me);
        let (journal, presences) = match &config.journal {
            Some(state) => {
                let state = state.clone();
                let (journal, presences) = tokio::task::spawn_blocking(move || {
                    anyhow::Ok((state.journal()?, state.presences()?))
                })
                .await??;
                (Some(journal), presences)
            }
            None => (None, Presences::default()),
        };
        let view = journal.as_ref().map(|journal| journal.view(me));
        let members = members_of(view.as_ref(), &presences, &config.peers);
        let allowed = Arc::new(RwLock::new(members.iter().map(|peer| peer.id).collect()));
        let endpoint = bind_endpoint(&config, allowed.clone()).await?;
        info!(id = %me, ip = %my_ip, "node up");
        for addr in endpoint.addr().ip_addrs() {
            info!(%addr, "listening");
        }
        let lan = config
            .lan_discovery
            .then(|| start_lan_discovery(&endpoint))
            .flatten();

        let v4_port = endpoint
            .bound_sockets()
            .iter()
            .find(|addr| addr.is_ipv4())
            .map_or(0, SocketAddr::port);
        let shared = Arc::new(Shared {
            key: config.secret_key.clone(),
            me,
            my_ip,
            v4_port,
            endpoint: endpoint.clone(),
            tun: Arc::new(tun),
            stats: Arc::new(Stats::default()),
            peers: RwLock::new(HashMap::new()),
            by_ip: RwLock::new(HashMap::new()),
            links: Mutex::new(HashMap::new()),
            allowed,
            extra_peers: config.peers,
            membership: config.journal.map(|state| Membership {
                state,
                journal: Mutex::new(journal.unwrap_or_default()),
                presences: Mutex::new(presences),
                mapper: portmapper::Client::new(portmapper::Config::default()),
            }),
            removed: AtomicBool::new(false),
        });
        if let Some(view) = &view {
            shared.note_removal(view);
        }
        shared.set_members(members);

        let mut tasks = JoinSet::new();
        if let Some(lan) = lan {
            tasks.spawn(watch_lan(lan, shared.clone()));
        }
        tasks.spawn(accept_loop(shared.clone()));
        let transport_ports = endpoint
            .bound_sockets()
            .iter()
            .map(SocketAddr::port)
            .collect();
        tasks.spawn(tun_loop(shared.clone(), transport_ports, config.mtu));
        if shared.membership.is_some() {
            tasks.spawn(follow_journal(shared.clone()));
            tasks.spawn(announce_reach(shared.clone()));
        }
        if let Some(interval) = config.stats_interval {
            tasks.spawn(log_stats(shared.stats.clone(), interval));
        }
        Ok(Self { shared, tasks })
    }

    /// A handle for user interfaces and the platform, valid while the node runs.
    pub fn handle(&self) -> NodeHandle {
        NodeHandle {
            shared: Arc::downgrade(&self.shared),
        }
    }

    /// Runs until `shutdown` completes or a node task fails, then closes the endpoint.
    pub async fn run_until(mut self, shutdown: impl Future<Output = ()>) -> Result<()> {
        let result = tokio::select! {
            Some(done) = self.tasks.join_next() => match done {
                Ok(Ok(())) => Err(anyhow!("a node task stopped unexpectedly")),
                Ok(Err(err)) => Err(err),
                Err(err) => Err(err.into()),
            },
            () = shutdown => Ok(()),
        };
        self.tasks.shutdown().await;
        // Handles may outlive the node: stop the links now.
        self.shared.links.lock().expect("poisoned").clear();
        self.shared.endpoint.close().await;
        result
    }
}

/// The member devices to connect to: the journal's members, at the addresses
/// the journal and their presences give, and the extra peers, except removed
/// devices.
fn members_of(view: Option<&View>, presences: &Presences, extra: &[PeerConfig]) -> Vec<PeerConfig> {
    let mut peers: Vec<PeerConfig> = view
        .map(|view| {
            view.members
                .0
                .iter()
                .map(|member| {
                    let mut addrs = member.addrs.clone();
                    for addr in presences.addrs(&member.id) {
                        if !addrs.contains(&addr) {
                            addrs.push(addr);
                        }
                    }
                    PeerConfig {
                        id: member.id,
                        addrs,
                    }
                })
                .collect()
        })
        .unwrap_or_default();
    for peer in extra {
        if view.is_some_and(|view| view.removed.contains(&peer.id)) {
            continue;
        }
        match peers.iter_mut().find(|known| known.id == peer.id) {
            Some(known) => known.addrs.extend(peer.addrs.iter().copied()),
            None => peers.push(peer.clone()),
        }
    }
    peers
}

/// State shared by the node's tasks and handles.
struct Shared {
    key: SecretKey,
    me: EndpointId,
    my_ip: Ipv4Addr,
    /// The UDP port of the IPv4 socket: the one to map on the router.
    v4_port: u16,
    endpoint: Endpoint,
    tun: Arc<AsyncDevice>,
    stats: Arc<Stats>,
    peers: RwLock<HashMap<EndpointId, Arc<Peer>>>,
    by_ip: RwLock<HashMap<Ipv4Addr, Arc<Peer>>>,
    /// The task of each peer's link; dropping one stops it.
    links: Mutex<HashMap<EndpointId, AbortOnDropHandle<Result<()>>>>,
    /// Who may connect: read by the handshake hook.
    allowed: Arc<RwLock<HashSet<EndpointId>>>,
    extra_peers: Vec<PeerConfig>,
    membership: Option<Membership>,
    /// The journal says this device was removed from the network.
    removed: AtomicBool,
}

/// The member journal and presences as the node keeps them.
struct Membership {
    state: State,
    /// What the journal file said last.
    journal: Mutex<Journal>,
    presences: Mutex<Presences>,
    /// Maps a port on the router to this node, where the router lets it.
    mapper: portmapper::Client,
}

impl Shared {
    /// Makes `wanted` the member devices: links to new ones start, links to
    /// devices that are no longer members end.
    fn set_members(self: &Arc<Self>, wanted: Vec<PeerConfig>) {
        let ids: HashSet<EndpointId> = wanted.iter().map(|peer| peer.id).collect();
        *self.allowed.write().expect("poisoned") = ids.clone();
        let mut peers = self.peers.write().expect("poisoned");
        let mut by_ip = self.by_ip.write().expect("poisoned");
        let mut links = self.links.lock().expect("poisoned");
        peers.retain(|id, peer| {
            if ids.contains(id) {
                return true;
            }
            info!(peer = %id.fmt_short(), ip = %peer.ip, "no longer a member");
            by_ip.remove(&peer.ip);
            links.remove(id);
            if let Some(conn) = peer.connection() {
                conn.close(CLOSE_NOT_MEMBER, b"not a member");
            }
            false
        });
        for config in wanted {
            if let Some(peer) = peers.get(&config.id) {
                let mut addrs = peer.addrs.lock().expect("poisoned");
                if *addrs != config.addrs {
                    *addrs = config.addrs;
                    peer.wake.notify_one();
                }
                continue;
            }
            let (outbound_tx, outbound_rx) = mpsc::channel(QUEUE_LEN);
            let (incoming_tx, incoming_rx) = mpsc::channel(4);
            let ip = overlay_ipv4(&config.id);
            let peer = Arc::new(Peer {
                id: config.id,
                ip,
                addrs: Mutex::new(config.addrs),
                outbound: outbound_tx,
                incoming: incoming_tx,
                connected: AtomicBool::new(false),
                conn: Mutex::new(None),
                wake: Notify::new(),
            });
            info!(peer = %config.id.fmt_short(), %ip, "member");
            let link = Link {
                shared: self.clone(),
                peer: peer.clone(),
            };
            links.insert(
                config.id,
                AbortOnDropHandle::new(tokio::spawn(link.run(outbound_rx, incoming_rx))),
            );
            by_ip.insert(ip, peer.clone());
            peers.insert(config.id, peer);
        }
    }

    fn note_removal(&self, view: &View) {
        let removed = view.removed.contains(&self.me);
        if removed && !self.removed.swap(true, Relaxed) {
            warn!("this device was removed from the network");
        }
    }

    /// Switches to `journal` if it says something new: updates the members and
    /// tells the connected members.
    fn adopt(self: &Arc<Self>, journal: Journal) {
        let Some(membership) = &self.membership else {
            return;
        };
        {
            let mut current = membership.journal.lock().expect("poisoned");
            if *current == journal {
                return;
            }
            *current = journal.clone();
        }
        self.refresh();
        self.push();
        // New members may have presences the peers can tell.
        self.push_presences();
    }

    /// Works out the members and their addresses again, from the journal and
    /// the presences.
    fn refresh(self: &Arc<Self>) {
        let Some(membership) = &self.membership else {
            return;
        };
        let view = membership.journal.lock().expect("poisoned").view(self.me);
        self.note_removal(&view);
        let members = {
            let presences = membership.presences.lock().expect("poisoned");
            members_of(Some(&view), &presences, &self.extra_peers)
        };
        self.set_members(members);
    }

    /// Keeps the presences from another member that are new here.
    async fn learn_presences(self: &Arc<Self>, presences: Vec<Presence>) {
        let Some(membership) = &self.membership else {
            return;
        };
        // Only members' presences matter, and this device's own.
        let presences: Vec<Presence> = {
            let allowed = self.allowed.read().expect("poisoned");
            presences
                .into_iter()
                .filter(|presence| {
                    presence.device() == self.me || allowed.contains(&presence.device())
                })
                .collect()
        };
        let changed = {
            let mut known = membership.presences.lock().expect("poisoned");
            let mut changed = false;
            for presence in &presences {
                changed |= known.insert(presence.clone());
            }
            changed
        };
        if !changed {
            return;
        }
        let state = membership.state.clone();
        let saved = tokio::task::spawn_blocking(move || {
            state.update_presences(|known| {
                for presence in presences {
                    known.insert(presence);
                }
                Ok(())
            })
        })
        .await;
        if !matches!(saved, Ok(Ok(()))) {
            warn!("failed to save the presences of members");
        }
        self.refresh();
        self.push_presences();
    }

    /// Signs and spreads where this device can be reached, if that changed.
    async fn announce(self: &Arc<Self>, reach: Vec<SocketAddr>) {
        let Some(membership) = &self.membership else {
            return;
        };
        let unchanged = membership
            .presences
            .lock()
            .expect("poisoned")
            .get(&self.me)
            .map_or(reach.is_empty(), |presence| presence.addrs() == reach);
        if unchanged {
            return;
        }
        let state = membership.state.clone();
        let key = self.key.clone();
        let addrs = reach.clone();
        let signed = tokio::task::spawn_blocking(move || {
            state.update_presences(|presences| Ok(presences.announce(&key, addrs)))
        })
        .await;
        let presence = match signed {
            Ok(Ok(Some(presence))) => presence,
            Ok(Ok(None)) => return,
            Ok(Err(err)) => return warn!("failed to save this device's presence: {err:#}"),
            Err(err) => return warn!("failed to save this device's presence: {err}"),
        };
        membership
            .presences
            .lock()
            .expect("poisoned")
            .insert(presence);
        if reach.is_empty() {
            info!("not reachable from the internet: no port mapping on this network");
        } else {
            let addrs: Vec<String> = reach.iter().map(ToString::to_string).collect();
            info!(addrs = %addrs.join(","), "reachable from the internet");
        }
        self.push_presences();
    }

    /// Offers the presences to every connected member.
    fn push_presences(self: &Arc<Self>) {
        for peer in self.peers.read().expect("poisoned").values() {
            if let Some(conn) = peer.connection() {
                tokio::spawn(self.clone().exchange_presences(conn));
            }
        }
    }

    /// One presence exchange with the member at the other end of `conn`.
    async fn exchange_presences(self: Arc<Self>, conn: Connection) {
        let Some(membership) = &self.membership else {
            return;
        };
        let ours: Vec<Presence> = membership
            .presences
            .lock()
            .expect("poisoned")
            .iter()
            .cloned()
            .collect();
        match sync::initiate_presences(&conn, &ours).await {
            Ok(theirs) => self.learn_presences(theirs).await,
            Err(err) => {
                debug!(peer = %conn.remote_id().fmt_short(), "presence exchange failed: {err:#}")
            }
        }
    }

    /// Connects to `peer` at every address known for it.
    async fn dial(&self, peer: &Peer) -> Result<Connection> {
        let addrs = peer.addrs.lock().expect("poisoned").clone();
        let addr = addrs.iter().fold(EndpointAddr::new(peer.id), |addr, ip| {
            addr.with_ip_addr(*ip)
        });
        let conn = tokio::time::timeout(DIAL_TIMEOUT, self.endpoint.connect(addr, ALPN))
            .await
            .context("timed out")??;
        Ok(conn)
    }

    /// This device moved to another network. Connections over the old one may
    /// be dead without anyone noticing for half a minute, and while one lives,
    /// iroh keeps sending a new connection's first packets along its path only.
    /// So they are closed, and every link dials again at once, at all the
    /// addresses it knows: the public ones from presences reach home from
    /// anywhere.
    fn moved(self: &Arc<Self>) {
        info!("the network changed: reconnecting");
        for peer in self.peers.read().expect("poisoned").values() {
            if let Some(conn) = peer.connection() {
                conn.close(CLOSE_MOVED, b"network changed");
            }
            peer.wake.notify_one();
        }
    }

    /// Asks the router for a mapping again: after a network change the old one
    /// belongs to another network. Finds out first which of UPnP, NAT-PMP and
    /// PCP the router speaks; without that, only UPnP would be tried.
    async fn remap(&self) {
        let Some(membership) = &self.membership else {
            return;
        };
        let Some(port) = NonZeroU16::new(self.v4_port) else {
            return;
        };
        match membership.mapper.probe().await {
            Ok(Ok(found)) if found.upnp || found.pcp || found.nat_pmp => {
                debug!(%found, "port mapping protocols")
            }
            Ok(Ok(_)) => info!(
                "the router maps no ports (UPnP, NAT-PMP, PCP): other networks reach this \
                 device only through a port forwarded by hand"
            ),
            Ok(Err(err)) => debug!("no port mapping here: {err}"),
            Err(_) => return,
        }
        membership.mapper.deactivate();
        membership.mapper.update_local_port(port);
    }

    /// Where this device can be reached from anywhere: the port the router maps
    /// to it, and public addresses of its own (global IPv6, mostly).
    fn reachable(&self, mapped: Option<SocketAddrV4>) -> Vec<SocketAddr> {
        let mut addrs: Vec<SocketAddr> = mapped
            .map(SocketAddr::V4)
            .into_iter()
            .filter(|addr| is_public(addr.ip()))
            .collect();
        for addr in self.endpoint.addr().ip_addrs() {
            if is_public(addr.ip()) && !addrs.contains(addr) {
                addrs.push(*addr);
            }
        }
        addrs.sort();
        addrs
    }

    /// Keeps the entries from another member that are new here, and acts on them.
    async fn learn(self: &Arc<Self>, entries: Vec<Entry>) {
        let Some(membership) = &self.membership else {
            return;
        };
        let new: Vec<Entry> = {
            let heads = membership.journal.lock().expect("poisoned").heads();
            entries
                .into_iter()
                .filter(|entry| heads.get(&entry.author()).copied().unwrap_or(0) < entry.seq())
                .collect()
        };
        if new.is_empty() {
            return;
        }
        let state = membership.state.clone();
        let me = self.me;
        let merged = tokio::task::spawn_blocking(move || {
            state.update_journal(|journal| {
                for entry in new {
                    if let Err(err) = journal.insert(entry) {
                        warn!("journal entry skipped: {err:#}");
                    }
                }
                journal.retain_reachable(me);
                Ok(journal.clone())
            })
        })
        .await;
        match merged {
            Ok(Ok(journal)) => self.adopt(journal),
            Ok(Err(err)) => warn!("failed to save the member journal: {err:#}"),
            Err(err) => warn!("failed to save the member journal: {err}"),
        }
    }

    /// Offers the journal to every connected member.
    fn push(self: &Arc<Self>) {
        for peer in self.peers.read().expect("poisoned").values() {
            if let Some(conn) = peer.connection() {
                tokio::spawn(self.clone().exchange(conn));
            }
        }
    }

    /// One journal exchange with the member at the other end of `conn`.
    async fn exchange(self: Arc<Self>, conn: Connection) {
        let Some(membership) = &self.membership else {
            return;
        };
        let journal = membership.journal.lock().expect("poisoned").clone();
        match sync::initiate(&conn, &journal).await {
            Ok(entries) => self.learn(entries).await,
            Err(err) => {
                debug!(peer = %conn.remote_id().fmt_short(), "journal exchange failed: {err:#}")
            }
        }
    }

    /// Answers the journal exchanges a member starts, for as long as its
    /// connection lives.
    async fn answer_exchanges(self: Arc<Self>, conn: Connection) {
        while let Ok((send, mut recv)) = conn.accept_bi().await {
            let shared = self.clone();
            let remote = conn.remote_id();
            tokio::spawn(async move {
                let Some(membership) = &shared.membership else {
                    return;
                };
                match sync::kind(&mut recv).await {
                    Ok(Kind::Journal) => {
                        let journal = membership.journal.lock().expect("poisoned").clone();
                        match sync::respond(send, recv, &journal).await {
                            Ok(entries) => shared.learn(entries).await,
                            Err(err) => {
                                debug!(peer = %remote.fmt_short(), "journal exchange failed: {err:#}")
                            }
                        }
                    }
                    Ok(Kind::Presences) => {
                        let ours: Vec<Presence> = membership
                            .presences
                            .lock()
                            .expect("poisoned")
                            .iter()
                            .cloned()
                            .collect();
                        match sync::respond_presences(send, recv, &ours).await {
                            Ok(theirs) => shared.learn_presences(theirs).await,
                            Err(err) => {
                                debug!(peer = %remote.fmt_short(), "presence exchange failed: {err:#}")
                            }
                        }
                    }
                    Err(err) => debug!(peer = %remote.fmt_short(), "unknown exchange: {err:#}"),
                }
            });
        }
    }
}

/// Follows the journal file: `halo pair`, `halo remove` and the app change it
/// while the node runs.
async fn follow_journal(shared: Arc<Shared>) -> Result<()> {
    let Some(membership) = &shared.membership else {
        return std::future::pending().await;
    };
    let path = membership.state.journal_path();
    let stamp = |path: &std::path::Path| {
        std::fs::metadata(path).ok().map(|meta| {
            (
                meta.modified().unwrap_or(SystemTime::UNIX_EPOCH),
                meta.len(),
            )
        })
    };
    let mut seen = stamp(&path);
    loop {
        tokio::time::sleep(JOURNAL_POLL).await;
        let now = stamp(&path);
        if now == seen {
            continue;
        }
        seen = now;
        let state = membership.state.clone();
        match tokio::task::spawn_blocking(move || state.journal()).await? {
            Ok(journal) => shared.adopt(journal),
            Err(err) => warn!("failed to read the member journal: {err:#}"),
        }
    }
}

/// Asks the router for a port that forwards to this node, and tells the members
/// where the node can be reached from anywhere whenever that changes.
async fn announce_reach(shared: Arc<Shared>) -> Result<()> {
    let Some(membership) = &shared.membership else {
        return std::future::pending().await;
    };
    let mut mapped = membership.mapper.watch_external_address();
    let mut addrs = shared.endpoint.watch_addr();
    let mut local: Vec<SocketAddr> = shared.endpoint.addr().ip_addrs().copied().collect();
    shared.remap().await;
    // Give the router a moment, so a restart does not first announce nothing.
    let _ = tokio::time::timeout(MAPPING_GRACE, mapped.changed()).await;
    loop {
        let reach = shared.reachable(*mapped.borrow());
        shared.announce(reach).await;
        tokio::select! {
            changed = mapped.changed() => {
                if changed.is_err() {
                    warn!("port mapping stopped");
                    return std::future::pending().await;
                }
            }
            updated = addrs.updated() => {
                let Ok(addr) = updated else {
                    return std::future::pending().await;
                };
                let now: Vec<SocketAddr> = addr.ip_addrs().copied().collect();
                if now != local {
                    // An address gone means another network: connections over
                    // it need renewing. A new address alone (another IPv6
                    // address, say) leaves the old paths working.
                    if local.iter().any(|addr| !now.contains(addr)) {
                        shared.moved();
                    }
                    local = now;
                    shared.remap().await;
                }
            }
            () = tokio::time::sleep(REACH_RECHECK) => {
                // The router may have come round, or rebooted and lost the mapping.
                if mapped.borrow().is_none() {
                    shared.remap().await;
                }
            }
        }
    }
}

/// A cheap, cloneable handle to a running node, for user interfaces and the platform.
///
/// Weak: once the node stops, its TUN device closes whatever handles remain.
#[derive(Clone)]
pub struct NodeHandle {
    shared: Weak<Shared>,
}

/// A snapshot of a node's state.
#[derive(Debug, Clone)]
pub struct NodeStatus {
    pub id: EndpointId,
    pub ip: Ipv4Addr,
    pub peers: Vec<PeerStatus>,
    /// The member journal says this device was removed from the network.
    pub removed: bool,
    /// Where other devices can reach this one from anywhere, as last announced.
    pub reachable: Vec<SocketAddr>,
}

/// A snapshot of one member device.
#[derive(Debug, Clone)]
pub struct PeerStatus {
    pub id: EndpointId,
    pub ip: Ipv4Addr,
    pub connected: bool,
    /// The network path in use, e.g. `192.168.1.20:7777`.
    pub path: Option<String>,
    pub rtt: Option<Duration>,
}

impl NodeHandle {
    /// Tells the node the network changed: Wi-Fi to mobile, a new Wi-Fi, back online.
    ///
    /// iroh watches interfaces itself on desktops, but not on Android, where the app
    /// has to pass on what `ConnectivityManager` reports. Every link redials at once
    /// instead of waiting out its backoff.
    pub async fn network_changed(&self) {
        let Some(shared) = self.shared.upgrade() else {
            return;
        };
        shared.endpoint.network_change().await;
        shared.moved();
        shared.remap().await;
    }

    /// The node's state, while it runs.
    pub fn snapshot(&self) -> Option<NodeStatus> {
        let shared = self.shared.upgrade()?;
        let mut peers: Vec<PeerStatus> = shared
            .peers
            .read()
            .expect("poisoned")
            .values()
            .map(|peer| {
                let conn = peer.connection();
                let selected = conn.as_ref().and_then(|conn| {
                    let paths = conn.paths();
                    let path = paths.iter().find(|path| path.is_selected())?;
                    Some((describe(path.remote_addr()), conn.rtt(path.id())))
                });
                let (path, rtt) = selected.unzip();
                PeerStatus {
                    id: peer.id,
                    ip: peer.ip,
                    connected: peer.connected.load(Relaxed),
                    path,
                    rtt: rtt.flatten(),
                }
            })
            .collect();
        peers.sort_by_key(|peer| peer.ip);
        let reachable = shared
            .membership
            .as_ref()
            .and_then(|membership| {
                let presences = membership.presences.lock().expect("poisoned");
                presences
                    .get(&shared.me)
                    .map(|presence| presence.addrs().to_vec())
            })
            .unwrap_or_default();
        Some(NodeStatus {
            id: shared.me,
            ip: shared.my_ip,
            peers,
            removed: shared.removed.load(Relaxed),
            reachable,
        })
    }
}

fn describe(addr: &TransportAddr) -> String {
    match addr {
        TransportAddr::Ip(addr) => addr.to_string(),
        other => format!("{other:?}"),
    }
}

async fn bind_endpoint(
    config: &NodeConfig,
    allowed: Arc<RwLock<HashSet<EndpointId>>>,
) -> Result<Endpoint> {
    let v4 = SocketAddr::from((Ipv4Addr::UNSPECIFIED, config.port));
    let v6 = SocketAddr::from((Ipv6Addr::UNSPECIFIED, config.port));
    let endpoint = Endpoint::builder(presets::Minimal)
        .secret_key(config.secret_key.clone())
        .alpns(vec![ALPN.to_vec()])
        // No servers: no relays and no internet address lookup.
        .relay_mode(RelayMode::Disabled)
        .clear_address_lookup()
        // Never advertise the tunnel's own address as a way to reach this device.
        .addr_filter(without_overlay())
        .hooks(MembersOnly(allowed))
        .clear_ip_transports()
        .bind_addr(v4)?
        .bind_addr_with_opts(v6, BindOpts::default().set_is_required(false))?
        .bind()
        .await
        .context("failed to bind the endpoint")?;
    Ok(endpoint)
}

fn without_overlay() -> AddrFilter {
    AddrFilter::new(|addrs| {
        Cow::Owned(
            addrs
                .iter()
                .filter(|addr| match addr {
                    TransportAddr::Ip(SocketAddr::V4(addr)) => !is_overlay(*addr.ip()),
                    TransportAddr::Ip(SocketAddr::V6(_)) => true,
                    _ => false,
                })
                .cloned()
                .collect(),
        )
    })
}

/// Publishes this device on the local network and finds members there.
///
/// Optional: without multicast the node still works with known addresses.
fn start_lan_discovery(endpoint: &Endpoint) -> Option<MdnsAddressLookup> {
    let lan = MdnsAddressLookup::builder()
        .service_name(MDNS_SERVICE)
        .build(endpoint.id())
        .map_err(anyhow::Error::from)
        .and_then(|lan| {
            endpoint.address_lookup()?.add(lan.clone());
            Ok(lan)
        });
    match lan {
        Ok(lan) => Some(lan),
        Err(err) => {
            warn!("local network discovery is unavailable: {err:#}");
            None
        }
    }
}

/// Dials a member as soon as it shows up on the local network.
async fn watch_lan(lan: MdnsAddressLookup, shared: Arc<Shared>) -> Result<()> {
    let mut events = lan.subscribe().await;
    while let Some(event) = events.next().await {
        if let DiscoveryEvent::Discovered { endpoint_info, .. } = event
            && let Some(peer) = shared
                .peers
                .read()
                .expect("poisoned")
                .get(&endpoint_info.endpoint_id)
        {
            debug!(peer = %peer.id.fmt_short(), "seen on the local network");
            peer.wake.notify_one();
        }
    }
    warn!("local network discovery stopped");
    std::future::pending().await
}

/// Rejects every key that is not a member, right after the TLS handshake.
#[derive(Debug)]
struct MembersOnly(Arc<RwLock<HashSet<EndpointId>>>);

impl EndpointHooks for MembersOnly {
    fn after_handshake<'a>(
        &'a self,
        conn: &'a Connection,
    ) -> impl Future<Output = AfterHandshakeOutcome> + Send + 'a {
        let remote = conn.remote_id();
        let outcome = if self.0.read().expect("poisoned").contains(&remote) {
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
    addrs: Mutex<Vec<SocketAddr>>,
    /// Frames from the TUN device waiting to be sent to this peer.
    outbound: mpsc::Sender<Bytes>,
    /// Connections from this peer, handed over by the accept loop.
    incoming: mpsc::Sender<Connection>,
    connected: AtomicBool,
    /// The connection in use, for status snapshots. Weak, so it never keeps one alive.
    conn: Mutex<Option<WeakConnectionHandle>>,
    /// Cuts the redial backoff short, e.g. when the peer appears on the local network.
    wake: Notify,
}

impl Peer {
    fn connection(&self) -> Option<Connection> {
        self.conn
            .lock()
            .expect("poisoned")
            .as_ref()
            .and_then(WeakConnectionHandle::upgrade)
    }
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
async fn tun_loop(shared: Arc<Shared>, transport_ports: Vec<u16>, mtu: u16) -> Result<()> {
    let stats = &shared.stats;
    loop {
        // One byte of headroom for the frame tag, so whole packets go out without a copy.
        let mut buf = BytesMut::zeroed(usize::from(mtu) + 1);
        let len = shared
            .tun
            .recv(&mut buf[1..])
            .await
            .context("TUN read failed")?;
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
        let peer = shared.by_ip.read().expect("poisoned").get(&dst).cloned();
        match peer {
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
async fn accept_loop(shared: Arc<Shared>) -> Result<()> {
    while let Some(incoming) = shared.endpoint.accept().await {
        let shared = shared.clone();
        tokio::spawn(async move {
            let accepting = match incoming.accept() {
                Ok(accepting) => accepting,
                Err(err) => return debug!(%err, "incoming connection failed"),
            };
            let conn = match accepting.await {
                Ok(conn) => conn,
                Err(err) => return debug!(%err, "handshake failed"),
            };
            let peer = shared
                .peers
                .read()
                .expect("poisoned")
                .get(&conn.remote_id())
                .cloned();
            match peer {
                Some(peer) => {
                    // The link is gone only during shutdown or a removal.
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
    shared: Arc<Shared>,
    peer: Arc<Peer>,
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
                    // Without known addresses iroh asks local network discovery.
                    let dial = async {
                        tokio::select! {
                            () = tokio::time::sleep(delay) => {}
                            () = self.peer.wake.notified() => {}
                        }
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
        self.shared.dial(&self.peer).await
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
        *self.peer.conn.lock().expect("poisoned") = Some(conn.weak_handle());
        let established = Instant::now();
        if self.shared.membership.is_some() {
            tokio::spawn(self.shared.clone().answer_exchanges(conn.clone()));
            tokio::spawn(self.shared.clone().exchange(conn.clone()));
            tokio::spawn(self.shared.clone().exchange_presences(conn.clone()));
        }

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
        *self.peer.conn.lock().expect("poisoned") = None;
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
        let me = self.shared.me;
        let dialer = |conn: &Connection| match conn.side() {
            Side::Client => me,
            Side::Server => self.peer.id,
        };
        let (candidate, current) = (dialer(candidate), dialer(current));
        candidate == current || candidate == me.min(self.peer.id)
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
                Some((src, dst)) if src == self.peer.ip && dst == self.shared.my_ip => {
                    match self.shared.tun.send(&packet).await {
                        Ok(_) => {
                            self.shared.stats.to_tun.fetch_add(1, Relaxed);
                        }
                        // The kernel rejects malformed packets; that is the sender's problem.
                        Err(err) => debug!(%err, "TUN write failed"),
                    }
                }
                _ => {
                    self.shared.stats.dropped_spoofed.fetch_add(1, Relaxed);
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
                self.shared.stats.fragmented.fetch_add(1, Relaxed);
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
