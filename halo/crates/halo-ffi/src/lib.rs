//! Kotlin (and later Swift) bindings for the Halo core, via UniFFI.
//!
//! The Android app owns the TUN device (`VpnService`) and hands its file
//! descriptor to [`HaloNode::start`]; everything else runs in Rust.

use std::{
    path::PathBuf,
    str::FromStr,
    sync::{Arc, Mutex, mpsc},
    time::Duration,
};

use halo_core::{
    Node, NodeConfig, NodeHandle,
    addr::overlay_ipv4,
    journal::Action,
    members::{Member, parse_addrs, validate_name},
    pair::{self, Pending, Ticket, member_name},
    state::State,
};
use iroh::{Endpoint, EndpointId};
use tokio::{runtime::Runtime, sync::oneshot};
use tokio_util::sync::CancellationToken;

uniffi::setup_scaffolding!();

const STOP_TIMEOUT: Duration = Duration::from_secs(3);

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum HaloError {
    #[error("{0}")]
    Failed(String),
}

impl From<anyhow::Error> for HaloError {
    fn from(err: anyhow::Error) -> Self {
        Self::Failed(format!("{err:#}"))
    }
}

impl From<std::io::Error> for HaloError {
    fn from(err: std::io::Error) -> Self {
        Self::Failed(err.to_string())
    }
}

#[derive(Debug, uniffi::Record)]
pub struct DeviceInfo {
    pub id: String,
    pub ip: String,
}

#[derive(Debug, uniffi::Record)]
pub struct MemberInfo {
    pub id: String,
    pub name: String,
    pub ip: String,
    pub addrs: Vec<String>,
    /// Where it said it can be reached from anywhere: away from home, the
    /// phone finds it there.
    pub public_addrs: Vec<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct PeerState {
    pub id: String,
    pub ip: String,
    pub connected: bool,
    /// The network path in use, e.g. `192.168.1.20:7777`.
    pub path: Option<String>,
    pub rtt_ms: Option<u32>,
}

impl From<&Member> for MemberInfo {
    fn from(member: &Member) -> Self {
        Self {
            id: member.id.to_string(),
            name: member.name.clone(),
            ip: overlay_ipv4(&member.id).to_string(),
            addrs: member.addrs.iter().map(ToString::to_string).collect(),
            public_addrs: Vec::new(),
        }
    }
}

/// Sends the core's logs to logcat. Safe to call more than once.
#[uniffi::export]
pub fn init_logging() {
    #[cfg(target_os = "android")]
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("halo")
            .with_max_level(log::LevelFilter::Info),
    );
}

/// This device's id and address, creating its key on first use.
#[uniffi::export]
pub fn device_info(state_dir: String) -> Result<DeviceInfo, HaloError> {
    let id = state(state_dir)?.key()?.public();
    Ok(DeviceInfo {
        id: id.to_string(),
        ip: overlay_ipv4(&id).to_string(),
    })
}

/// The member devices, as this device's journal sees them.
#[uniffi::export]
pub fn list_members(state_dir: String) -> Result<Vec<MemberInfo>, HaloError> {
    let state = state(state_dir)?;
    let me = state.key()?.public();
    let presences = state.presences()?;
    Ok(state
        .journal()?
        .view(me)
        .members
        .0
        .iter()
        .map(|member| MemberInfo {
            public_addrs: presences
                .addrs(&member.id)
                .iter()
                .map(ToString::to_string)
                .collect(),
            ..MemberInfo::from(member)
        })
        .collect())
}

/// Whether the member journal says this device was removed from the network.
#[uniffi::export]
pub fn is_removed(state_dir: String) -> Result<bool, HaloError> {
    let state = state(state_dir)?;
    let me = state.key()?.public();
    Ok(state.journal()?.view(me).removed.contains(&me))
}

/// Adds a device to the network, or updates its name and addresses.
#[uniffi::export]
pub fn add_member(
    state_dir: String,
    id: String,
    name: String,
    addrs: Vec<String>,
) -> Result<MemberInfo, HaloError> {
    let state = state(state_dir)?;
    let key = state.key()?;
    let id = EndpointId::from_str(id.trim())
        .map_err(|err| HaloError::Failed(format!("invalid device id: {err}")))?;
    if id == key.public() {
        return Err(HaloError::Failed("this is the id of this device".into()));
    }
    let name = name.trim().to_string();
    validate_name(&name)?;
    let addrs: Vec<std::net::SocketAddr> = addrs
        .iter()
        .filter(|addr| !addr.trim().is_empty())
        .map(|addr| parse_addrs(addr.trim()))
        .collect::<anyhow::Result<Vec<_>>>()?
        .into_iter()
        .flatten()
        .collect();
    let view = state.update_journal(|journal| {
        anyhow::ensure!(
            !journal.view(key.public()).removed.contains(&id),
            "this device was removed from the network for good"
        );
        journal.append(
            &key,
            Action::Add {
                id,
                name: name.clone(),
                addrs: addrs.clone(),
            },
        );
        Ok(journal.view(key.public()))
    })?;
    let member = view
        .members
        .0
        .iter()
        .find(|member| member.id == id)
        .cloned()
        .unwrap_or(Member { id, name, addrs });
    Ok(MemberInfo::from(&member))
}

/// Removes a device from the network for good, by name or id.
#[uniffi::export]
pub fn remove_member(state_dir: String, device: String) -> Result<(), HaloError> {
    let state = state(state_dir)?;
    let key = state.key()?;
    state.update_journal(|journal| {
        let id = journal
            .view(key.public())
            .find(&device)
            .map(|member| member.id)
            .ok_or_else(|| anyhow::anyhow!("no device {device}"))?;
        journal.remove(&key, id);
        Ok(())
    })?;
    Ok(())
}

fn state(dir: String) -> Result<State, HaloError> {
    Ok(State::new(Some(PathBuf::from(dir)))?)
}

/// A running node.
#[derive(uniffi::Object)]
pub struct HaloNode {
    handle: NodeHandle,
    stop: Mutex<Option<oneshot::Sender<()>>>,
    done: Mutex<mpsc::Receiver<()>>,
    // Dropped last: it owns the tasks above.
    runtime: Runtime,
}

#[uniffi::export]
impl HaloNode {
    /// Starts the node on a TUN device, taking ownership of its file descriptor.
    ///
    /// The descriptor is closed on every error path, so the VPN interface never
    /// outlives a failed start.
    #[uniffi::constructor]
    pub fn start(state_dir: String, tun_fd: i32, mtu: u16) -> Result<Arc<Self>, HaloError> {
        // SAFETY: the app hands over a valid, open fd from VpnService and gives up
        // ownership of it (ParcelFileDescriptor.detachFd()).
        let tun_fd = unsafe { TunFd::adopt(tun_fd) };
        let state = state(state_dir)?;
        let secret_key = state.key()?;
        let view = state.journal()?.view(secret_key.public());
        if view.removed.contains(&secret_key.public()) {
            return Err(HaloError::Failed(
                "this device was removed from the network".into(),
            ));
        }
        if view.members.0.is_empty() {
            return Err(HaloError::Failed("no devices to connect to".into()));
        }
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .thread_name("halo")
            .enable_all()
            .build()?;
        let config = NodeConfig {
            secret_key,
            // Phones mostly dial out; peers learn the port over mDNS.
            port: 0,
            mtu,
            peers: Vec::new(),
            // Members come and go while it runs: the journal says who they are.
            journal: Some(state),
            lan_discovery: true,
            stats_interval: None,
        };
        let node = runtime.block_on(async {
            let tun = tun_fd.into_device()?;
            Node::start(config, tun).await
        })?;
        let handle = node.handle();
        let (stop_tx, stop_rx) = oneshot::channel();
        let (done_tx, done_rx) = mpsc::channel();
        runtime.spawn(async move {
            let stopped = async {
                let _ = stop_rx.await;
            };
            if let Err(err) = node.run_until(stopped).await {
                tracing::error!("node stopped: {err:#}");
            }
            let _ = done_tx.send(());
        });
        Ok(Arc::new(Self {
            handle,
            stop: Mutex::new(Some(stop_tx)),
            done: Mutex::new(done_rx),
            runtime,
        }))
    }

    /// The member devices and how they are connected. Changes when devices join
    /// or leave the network.
    pub fn peers(&self) -> Vec<PeerState> {
        self.handle
            .snapshot()
            .map(|status| status.peers)
            .unwrap_or_default()
            .into_iter()
            .map(|peer| PeerState {
                id: peer.id.to_string(),
                ip: peer.ip.to_string(),
                connected: peer.connected,
                path: peer.path,
                rtt_ms: peer
                    .rtt
                    .map(|rtt| u32::try_from(rtt.as_millis()).unwrap_or(u32::MAX)),
            })
            .collect()
    }

    /// Call when Android reports a network change; iroh cannot see them there.
    pub fn network_changed(&self) {
        let handle = self.handle.clone();
        self.runtime
            .spawn(async move { handle.network_changed().await });
    }

    /// Whether the node is still running: it stops on a fatal error.
    pub fn is_running(&self) -> bool {
        matches!(
            self.done.lock().expect("poisoned").try_recv(),
            Err(mpsc::TryRecvError::Empty)
        )
    }

    /// Stops the node and closes the TUN device. Waits briefly for a clean shutdown.
    pub fn stop(&self) {
        if let Some(stop) = self.stop.lock().expect("poisoned").take() {
            let _ = stop.send(());
            let _ = self
                .done
                .lock()
                .expect("poisoned")
                .recv_timeout(STOP_TIMEOUT);
        }
    }
}

/// A TUN file descriptor handed over by the platform (Android, iOS).
#[cfg(unix)]
struct TunFd(std::os::fd::OwnedFd);

#[cfg(unix)]
impl TunFd {
    /// # Safety
    /// `fd` must be open, and the caller must give up ownership of it.
    unsafe fn adopt(fd: i32) -> Self {
        Self(unsafe { std::os::fd::FromRawFd::from_raw_fd(fd) })
    }

    /// Must run inside the tokio runtime: the device registers with its reactor.
    fn into_device(self) -> std::io::Result<tun_rs::AsyncDevice> {
        let fd = std::os::fd::IntoRawFd::into_raw_fd(self.0);
        // SAFETY: `fd` is open and owned; from_fd takes over closing it.
        unsafe { tun_rs::AsyncDevice::from_fd(fd) }
    }
}

/// Elsewhere the desktop CLI creates its own TUN device.
#[cfg(not(unix))]
struct TunFd;

#[cfg(not(unix))]
impl TunFd {
    unsafe fn adopt(_fd: i32) -> Self {
        Self
    }

    fn into_device(self) -> std::io::Result<tun_rs::AsyncDevice> {
        Err(std::io::Error::new(
            std::io::ErrorKind::Unsupported,
            "a TUN file descriptor from the platform is only supported on Android and iOS",
        ))
    }
}

/// Pairing with a device that shows a code (`halo pair`): one scan, both devices
/// add each other.
///
/// Creating one only reads the code. [`Pairing::connect`] and
/// [`Pairing::confirm`] block; [`Pairing::cancel`] ends either one from another
/// thread. Dropping the object tells the other device that the pairing is over,
/// so drop it off the main thread.
#[derive(uniffi::Object)]
pub struct Pairing {
    state_dir: String,
    name: String,
    ticket: Ticket,
    cancel: CancellationToken,
    session: Mutex<Option<Session>>,
    // Dropped last: it owns the tasks above.
    runtime: Runtime,
}

/// A connection to the other device, waiting for the user's answer.
struct Session {
    endpoint: Endpoint,
    pending: Pending,
}

/// The other device, as the user should see it before confirming.
#[derive(Debug, uniffi::Record)]
pub struct PairingPeer {
    /// How the other device calls itself.
    pub name: String,
    /// The four emoji to compare with the other device's screen.
    pub emoji: Vec<String>,
}

#[uniffi::export]
impl Pairing {
    /// Prepares to join the device showing `code`; fails on a malformed code.
    /// Touches neither the disk nor the network.
    ///
    /// `name` is how the other device will call this one, e.g. the phone model.
    #[uniffi::constructor]
    pub fn new(state_dir: String, code: String, name: String) -> Result<Arc<Self>, HaloError> {
        let ticket: Ticket = code.parse()?;
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(1)
            .thread_name("halo-pair")
            .enable_all()
            .build()?;
        Ok(Arc::new(Self {
            state_dir,
            name: member_name(&name),
            ticket,
            cancel: CancellationToken::new(),
            session: Mutex::new(None),
            runtime,
        }))
    }

    /// Connects to the other device. Blocks until both show the emoji.
    pub fn connect(&self) -> Result<PairingPeer, HaloError> {
        let secret_key = state(self.state_dir.clone())?.key()?;
        // A phone's node has no fixed port to announce.
        let join = pair::join(secret_key, &self.name, 0, &self.ticket);
        let (endpoint, pending) = self
            .runtime
            .block_on(self.cancel.run_until_cancelled(join))
            .ok_or_else(cancelled)??;
        let peer = PairingPeer {
            name: pending.peer_name.clone(),
            emoji: pending.emoji.iter().map(ToString::to_string).collect(),
        };
        *self.session.lock().expect("poisoned") = Some(Session { endpoint, pending });
        Ok(peer)
    }

    /// Answers whether the emoji match; after a yes, waits for the other side's
    /// answer.
    ///
    /// Returns the added device when both said yes, and nothing when either
    /// said no, went away or cancelled.
    pub fn confirm(&self, accept: bool) -> Result<Option<MemberInfo>, HaloError> {
        let Session { endpoint, pending } = self
            .session
            .lock()
            .expect("poisoned")
            .take()
            .ok_or_else(|| HaloError::Failed("not connected".into()))?;
        let peer = (
            pending.peer,
            pending.peer_name.clone(),
            pending.peer_addrs.clone(),
        );
        let paired = self.runtime.block_on(async {
            let paired = pending
                .confirm_unless(accept, self.cancel.cancelled())
                .await;
            close(endpoint).await;
            paired
        })?;
        if !paired {
            return Ok(None);
        }
        let (id, name, addrs) = peer;
        let state = state(self.state_dir.clone())?;
        let key = state.key()?;
        let view = state.update_journal(|journal| {
            journal.set_name(&key, &self.name);
            journal.append(
                &key,
                Action::Add {
                    id,
                    name: member_name(&name),
                    addrs: addrs.clone(),
                },
            );
            Ok(journal.view(key.public()))
        })?;
        // Not a member if it was removed from the network before.
        Ok(view
            .members
            .0
            .iter()
            .find(|member| member.id == id)
            .map(MemberInfo::from))
    }

    /// Ends the pairing from any thread and returns at once: a blocked
    /// [`Pairing::connect`] fails, a blocked [`Pairing::confirm`] adds nothing.
    pub fn cancel(&self) {
        self.cancel.cancel();
    }
}

impl Drop for Pairing {
    fn drop(&mut self) {
        // Left before answering: tell the other device instead of letting it
        // wait for a timeout.
        if let Some(Session { endpoint, pending }) =
            self.session.get_mut().expect("poisoned").take()
        {
            drop(pending);
            self.runtime.block_on(close(endpoint));
        }
    }
}

fn cancelled() -> HaloError {
    HaloError::Failed("cancelled".into())
}

async fn close(endpoint: Endpoint) {
    let _ = tokio::time::timeout(STOP_TIMEOUT, endpoint.close()).await;
}

#[cfg(test)]
mod tests {
    use std::{net::UdpSocket, time::Instant};

    use iroh::SecretKey;

    use super::*;

    /// A fresh state directory. Created up front, so no platform ACL step runs.
    fn state_dir(name: &str) -> String {
        let dir = std::env::temp_dir().join(format!("halo-ffi-{name}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir.to_string_lossy().into_owned()
    }

    /// A laptop running `halo pair`, with a code that points at loopback.
    fn laptop(runtime: &Runtime, key: &SecretKey) -> (pair::Host, String) {
        let host = runtime
            .block_on(pair::Host::bind(key.clone(), "laptop", 7777))
            .unwrap();
        let mut ticket = host.ticket().clone();
        ticket.addrs = vec![std::net::SocketAddr::from(([127, 0, 0, 1], host.port()))];
        (host, ticket.to_string())
    }

    fn assert_cancelled<T: std::fmt::Debug>(result: Result<T, HaloError>) {
        assert!(
            matches!(&result, Err(HaloError::Failed(message)) if message == "cancelled"),
            "{result:?}"
        );
    }

    /// The phone's side: one code, the same emoji, and the laptop is added with
    /// the address of its node.
    #[test]
    fn pairing_adds_the_other_device() {
        let runtime = Runtime::new().unwrap();
        let key = SecretKey::generate();
        let (host, code) = laptop(&runtime, &key);
        let laptop_side = runtime.spawn(async move {
            let pending = host.accept().await.unwrap();
            let emoji = pending.emoji.map(String::from).to_vec();
            let paired = pending.confirm(true).await.unwrap();
            host.close().await;
            (emoji, paired)
        });

        let dir = state_dir("pair");
        let pairing = Pairing::new(dir.clone(), code, "Pixel 9 Pro".into()).unwrap();
        let peer = pairing.connect().unwrap();
        assert_eq!(peer.name, "laptop");
        let member = pairing.confirm(true).unwrap().expect("not paired");
        let (emoji, paired) = runtime.block_on(laptop_side).unwrap();
        assert!(paired);
        assert_eq!(peer.emoji, emoji);
        assert_eq!(member.id, key.public().to_string());
        assert_eq!(member.name, "laptop");
        assert_eq!(member.addrs, vec!["127.0.0.1:7777".to_string()]);
        assert_eq!(list_members(dir).unwrap().len(), 1);
    }

    /// The code points at a device that never answers: cancel ends the wait.
    #[test]
    fn cancel_ends_a_stuck_connect() {
        let silent = UdpSocket::bind("127.0.0.1:0").unwrap();
        let code = Ticket {
            id: SecretKey::generate().public(),
            secret: [0; 16],
            addrs: vec![silent.local_addr().unwrap()],
        }
        .to_string();
        let pairing = Pairing::new(state_dir("stuck"), code, "phone".into()).unwrap();
        let connecting = {
            let pairing = pairing.clone();
            std::thread::spawn(move || pairing.connect())
        };
        std::thread::sleep(Duration::from_millis(300));
        let cancelled_at = Instant::now();
        pairing.cancel();
        assert_cancelled(connecting.join().unwrap());
        assert!(cancelled_at.elapsed() < Duration::from_secs(2));
    }

    /// Cancelled while waiting for the laptop's answer: the laptop learns at once.
    #[test]
    fn cancel_while_waiting_ends_the_pairing_on_both_sides() {
        let runtime = Runtime::new().unwrap();
        let (host, code) = laptop(&runtime, &SecretKey::generate());
        let accepting = runtime.spawn(async move {
            let pending = host.accept().await.unwrap();
            (host, pending)
        });
        let pairing = Pairing::new(state_dir("waiting"), code, "phone".into()).unwrap();
        pairing.connect().unwrap();
        let (host, pending) = runtime.block_on(accepting).unwrap();

        // The laptop's user has not answered yet.
        let confirming = {
            let pairing = pairing.clone();
            std::thread::spawn(move || pairing.confirm(true))
        };
        std::thread::sleep(Duration::from_millis(300));
        pairing.cancel();
        assert!(confirming.join().unwrap().unwrap().is_none());
        runtime.block_on(async {
            tokio::time::timeout(Duration::from_secs(5), pending.closed())
                .await
                .expect("the laptop did not see the pairing end");
            assert!(!pending.confirm(true).await.unwrap());
            host.close().await;
        });
    }

    /// Leaving while the emoji are on screen tells the laptop at once.
    #[test]
    fn dropping_before_answering_ends_the_pairing() {
        let runtime = Runtime::new().unwrap();
        let (host, code) = laptop(&runtime, &SecretKey::generate());
        let accepting = runtime.spawn(async move {
            let pending = host.accept().await.unwrap();
            (host, pending)
        });
        let pairing = Pairing::new(state_dir("drop"), code, "phone".into()).unwrap();
        pairing.connect().unwrap();
        let (host, pending) = runtime.block_on(accepting).unwrap();

        drop(pairing);
        runtime.block_on(async {
            tokio::time::timeout(Duration::from_secs(5), pending.closed())
                .await
                .expect("the laptop did not see the pairing end");
            assert!(!pending.confirm(true).await.unwrap());
            host.close().await;
        });
    }
}
