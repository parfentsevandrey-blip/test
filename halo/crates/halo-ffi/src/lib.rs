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
    Node, NodeConfig, PeerConfig, StatusHandle,
    addr::overlay_ipv4,
    members::{Member, parse_addrs},
    state::State,
};
use iroh::EndpointId;
use tokio::{runtime::Runtime, sync::oneshot};

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

#[uniffi::export]
pub fn list_members(state_dir: String) -> Result<Vec<MemberInfo>, HaloError> {
    Ok(state(state_dir)?
        .members()?
        .0
        .iter()
        .map(MemberInfo::from)
        .collect())
}

/// Adds a device, or updates the one with the same id.
#[uniffi::export]
pub fn add_member(
    state_dir: String,
    id: String,
    name: String,
    addrs: Vec<String>,
) -> Result<MemberInfo, HaloError> {
    let state = state(state_dir)?;
    let id = EndpointId::from_str(id.trim())
        .map_err(|err| HaloError::Failed(format!("invalid device id: {err}")))?;
    if id == state.key()?.public() {
        return Err(HaloError::Failed("this is the id of this device".into()));
    }
    let addrs = addrs
        .iter()
        .filter(|addr| !addr.trim().is_empty())
        .map(|addr| parse_addrs(addr.trim()))
        .collect::<anyhow::Result<Vec<_>>>()?
        .into_iter()
        .flatten()
        .collect();
    let member = Member {
        id,
        name: name.trim().to_string(),
        addrs,
    };
    let info = MemberInfo::from(&member);
    let mut members = state.members()?;
    members.upsert(member)?;
    state.save_members(&members)?;
    Ok(info)
}

/// Removes a device by name or id.
#[uniffi::export]
pub fn remove_member(state_dir: String, device: String) -> Result<(), HaloError> {
    let state = state(state_dir)?;
    let mut members = state.members()?;
    members
        .remove(&device)
        .ok_or_else(|| HaloError::Failed(format!("no device {device}")))?;
    state.save_members(&members)?;
    Ok(())
}

fn state(dir: String) -> Result<State, HaloError> {
    Ok(State::new(Some(PathBuf::from(dir)))?)
}

/// A running node.
#[derive(uniffi::Object)]
pub struct HaloNode {
    status: StatusHandle,
    stop: Mutex<Option<oneshot::Sender<()>>>,
    done: Mutex<mpsc::Receiver<()>>,
    // Dropped last: it owns the tasks above.
    _runtime: Runtime,
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
        let tun_fd =
            unsafe { <std::os::fd::OwnedFd as std::os::fd::FromRawFd>::from_raw_fd(tun_fd) };
        let state = state(state_dir)?;
        let secret_key = state.key()?;
        let peers: Vec<PeerConfig> = state
            .members()?
            .0
            .into_iter()
            .map(PeerConfig::from)
            .collect();
        if peers.is_empty() {
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
            peers,
            lan_discovery: true,
            stats_interval: None,
        };
        let node = runtime.block_on(async {
            let fd = std::os::fd::IntoRawFd::into_raw_fd(tun_fd);
            // SAFETY: `fd` is open and owned; from_fd takes over closing it.
            let tun = unsafe { tun_rs::AsyncDevice::from_fd(fd) }?;
            Node::start(config, tun).await
        })?;
        let status = node.status();
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
            status,
            stop: Mutex::new(Some(stop_tx)),
            done: Mutex::new(done_rx),
            _runtime: runtime,
        }))
    }

    /// The member devices and how they are connected.
    pub fn peers(&self) -> Vec<PeerState> {
        self.status
            .snapshot()
            .peers
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
