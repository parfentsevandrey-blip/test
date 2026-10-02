//! `halo`: run a Halo node on a desktop (Linux, macOS, Windows).

use std::{
    fs,
    io::IsTerminal,
    net::SocketAddr,
    path::{Path, PathBuf},
    str::FromStr,
    time::Duration,
};

use anyhow::{Context, Result, bail};
use clap::{Parser, Subcommand};
use halo_core::{DEFAULT_MTU, NodeConfig, PeerConfig, addr::overlay_ipv4};
use iroh::{EndpointId, SecretKey};
use tracing_subscriber::EnvFilter;

const KEY_FILE: &str = "secret.key";

#[derive(Parser)]
#[command(
    version,
    about = "A private network of your own devices, without servers"
)]
struct Cli {
    /// Directory with this device's key (default: the platform config dir).
    #[arg(long, global = true, env = "HALO_STATE_DIR")]
    state_dir: Option<PathBuf>,
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    /// Print this device's id and overlay address, creating a key on first use.
    Id,
    /// Bring the tunnel up and connect to member devices.
    Up {
        /// Member device: `ID` or `ID@ADDR[,ADDR...]`, e.g. `3f2a…@192.168.1.20:7777`.
        #[arg(long = "peer", value_name = "PEER", required = true)]
        peers: Vec<PeerSpec>,
        /// UDP port to listen on (0 = random).
        #[arg(long, default_value_t = 7777)]
        port: u16,
        /// TUN device name.
        #[arg(long)]
        tun: Option<String>,
        /// MTU of the TUN device.
        #[arg(long, default_value_t = DEFAULT_MTU)]
        mtu: u16,
        /// Log traffic counters every N seconds.
        #[arg(long, value_name = "SECONDS")]
        stats: Option<u64>,
    },
}

#[derive(Clone, Debug)]
struct PeerSpec(PeerConfig);

impl FromStr for PeerSpec {
    type Err = anyhow::Error;

    fn from_str(s: &str) -> Result<Self> {
        let (id, addrs) = match s.split_once('@') {
            Some((id, addrs)) => (id, Some(addrs)),
            None => (s, None),
        };
        let id = EndpointId::from_str(id).context("invalid device id")?;
        let addrs = addrs
            .into_iter()
            .flat_map(|addrs| addrs.split(','))
            .map(|addr| {
                SocketAddr::from_str(addr).with_context(|| format!("invalid address {addr}"))
            })
            .collect::<Result<_>>()?;
        Ok(Self(PeerConfig { id, addrs }))
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| EnvFilter::new("halo=info,halo_core=info")),
        )
        .with_writer(std::io::stderr)
        .with_ansi(std::io::stderr().is_terminal())
        .init();

    let cli = Cli::parse();
    let state_dir = match cli.state_dir {
        Some(dir) => dir,
        None => dirs::config_dir()
            .context("no config directory on this platform; pass --state-dir")?
            .join("halo"),
    };
    let secret_key = load_or_create_key(&state_dir)?;
    let id = secret_key.public();

    match cli.command {
        Command::Id => {
            println!("id: {id}");
            println!("ip: {}", overlay_ipv4(&id));
            Ok(())
        }
        Command::Up {
            peers,
            port,
            tun,
            mtu,
            stats,
        } => {
            let peers: Vec<PeerConfig> = peers.into_iter().map(|spec| spec.0).collect();
            if peers.iter().any(|peer| peer.id == id) {
                bail!("a device cannot be its own peer");
            }
            let tun = halo_core::create_tun(tun.as_deref(), overlay_ipv4(&id), mtu)?;
            let config = NodeConfig {
                secret_key,
                port,
                mtu,
                peers,
                stats_interval: stats.map(Duration::from_secs),
            };
            halo_core::run(config, tun, shutdown_signal()).await
        }
    }
}

fn load_or_create_key(dir: &Path) -> Result<SecretKey> {
    let path = dir.join(KEY_FILE);
    match fs::read_to_string(&path) {
        Ok(contents) => SecretKey::from_str(contents.trim())
            .with_context(|| format!("invalid key in {}", path.display())),
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => {
            fs::create_dir_all(dir).with_context(|| format!("cannot create {}", dir.display()))?;
            let key = SecretKey::generate();
            let hex: String = key.to_bytes().iter().map(|b| format!("{b:02x}")).collect();
            write_private(&path, &hex)
                .with_context(|| format!("cannot write {}", path.display()))?;
            Ok(key)
        }
        Err(err) => Err(err).with_context(|| format!("cannot read {}", path.display())),
    }
}

/// Writes a file only the current user can read.
fn write_private(path: &Path, contents: &str) -> std::io::Result<()> {
    use std::io::Write;
    let mut options = fs::OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    std::os::unix::fs::OpenOptionsExt::mode(&mut options, 0o600);
    options.open(path)?.write_all(contents.as_bytes())
}

async fn shutdown_signal() {
    #[cfg(unix)]
    {
        let mut term = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("failed to install the SIGTERM handler");
        tokio::select! {
            _ = tokio::signal::ctrl_c() => {}
            _ = term.recv() => {}
        }
    }
    #[cfg(not(unix))]
    let _ = tokio::signal::ctrl_c().await;
}
