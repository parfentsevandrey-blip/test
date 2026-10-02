//! `halo`: run a Halo node on a desktop (Linux, macOS, Windows).

use std::{io::IsTerminal, net::SocketAddr, path::PathBuf, str::FromStr, time::Duration};

use anyhow::{Context, Result, bail};
use clap::{Parser, Subcommand};
use halo_core::{
    DEFAULT_MTU, NodeConfig, PeerConfig,
    addr::overlay_ipv4,
    members::{Member, parse_addrs},
    pair::{self, Pending, Ticket, member_name},
    state::State,
};
use iroh::EndpointId;
use tracing_subscriber::EnvFilter;

#[derive(Parser)]
#[command(
    version,
    about = "A private network of your own devices, without servers"
)]
struct Cli {
    /// Directory with this device's key and member list.
    #[arg(long, global = true, env = "HALO_STATE_DIR")]
    state_dir: Option<PathBuf>,
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    /// Print this device's id and address in the network, creating a key on first use.
    Id,
    /// Add a device to the network of this device, or update it.
    Add {
        /// The device id, as printed by `halo id` on that device.
        id: EndpointId,
        /// A short name: lowercase latin letters, digits and dashes.
        #[arg(long)]
        name: String,
        /// Known address of the device, `ip:port`. Not needed on the same local network.
        #[arg(long = "addr", value_name = "ADDR", value_parser = parse_addrs)]
        addrs: Vec<Vec<SocketAddr>>,
    },
    /// Pair with another device: one QR scan adds the two devices to each other.
    ///
    /// Shows a QR code and a text code for the Halo app or another computer.
    Pair {
        /// Join the pairing another device shows (its text code) instead.
        #[arg(long, value_name = "CODE")]
        join: Option<String>,
        /// How the other device will call this one (default: the host name).
        #[arg(long)]
        name: Option<String>,
        /// The UDP port `halo up` listens on, so the other device can find it
        /// where mDNS does not reach.
        #[arg(long, default_value_t = 7777)]
        port: u16,
    },
    /// Remove a device by name or id.
    Remove { device: String },
    /// List the devices of this network.
    Members,
    /// Bring the network up and connect to the member devices.
    Up {
        /// Extra device for this run: `ID` or `ID@ADDR[,ADDR...]`.
        #[arg(long = "peer", value_name = "PEER")]
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
        /// Do not look for devices on the local network (mDNS).
        #[arg(long)]
        no_lan: bool,
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
            Some((id, addrs)) => (id, parse_addrs(addrs)?),
            None => (s, Vec::new()),
        };
        let id = EndpointId::from_str(id).context("invalid device id")?;
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
    let state = State::new(cli.state_dir)?;
    match cli.command {
        Command::Id => {
            let id = state.key()?.public();
            println!("id: {id}");
            println!("ip: {}", overlay_ipv4(&id));
            Ok(())
        }
        Command::Add { id, name, addrs } => {
            if id == state.key()?.public() {
                bail!("this is the id of this device; add it on the other device instead");
            }
            let mut members = state.members()?;
            let addrs = addrs.into_iter().flatten().collect();
            members.upsert(Member {
                id,
                name: name.clone(),
                addrs,
            })?;
            state.save_members(&members)?;
            println!("added {name}: {}", overlay_ipv4(&id));
            Ok(())
        }
        Command::Pair { join, name, port } => {
            let secret_key = state.key()?;
            let raw =
                name.unwrap_or_else(|| gethostname::gethostname().to_string_lossy().into_owned());
            let name = member_name(&raw);
            let paired = match join {
                Some(code) => {
                    let ticket: Ticket = code.parse()?;
                    println!("Connecting to the other device...");
                    let (endpoint, pending) = pair::join(secret_key, &name, port, &ticket).await?;
                    let paired = unless_interrupted(confirm(pending)).await;
                    endpoint.close().await;
                    paired?
                }
                None => {
                    let host = pair::Host::bind(secret_key, &name, port).await?;
                    let code = host.ticket().to_string();
                    print_qr(&code)?;
                    println!("Scan it in the Halo app (Add device), or on another computer run:");
                    println!("  halo pair --join {code}\n");
                    println!("Waiting for a device... (Ctrl+C to cancel)");
                    let paired = unless_interrupted(async {
                        let pending = host.accept().await?;
                        confirm(pending).await
                    })
                    .await;
                    host.close().await;
                    paired?
                }
            };
            let Some((peer, peer_name, peer_addrs)) = paired else {
                println!("Not paired.");
                return Ok(());
            };
            let mut members = state.members()?;
            let name = members.free_name(&member_name(&peer_name), &peer);
            // Where its node listens, so it is found even where mDNS does not reach.
            members.upsert(Member {
                id: peer,
                name: name.clone(),
                addrs: peer_addrs,
            })?;
            state.save_members(&members)?;
            println!(
                "Paired with {name} ({}). Restart `halo up` to connect.",
                overlay_ipv4(&peer)
            );
            Ok(())
        }
        Command::Remove { device } => {
            let mut members = state.members()?;
            let removed = members
                .remove(&device)
                .with_context(|| format!("no device {device}"))?;
            state.save_members(&members)?;
            println!("removed {}", removed.name);
            Ok(())
        }
        Command::Members => {
            let members = state.members()?;
            if members.0.is_empty() {
                println!("no devices yet: add one with `halo add <id> --name <name>`");
            }
            for member in &members.0 {
                let addrs: Vec<String> = member.addrs.iter().map(ToString::to_string).collect();
                println!(
                    "{:<16} {:<16} {} {}",
                    member.name,
                    overlay_ipv4(&member.id),
                    member.id.fmt_short(),
                    addrs.join(",")
                );
            }
            Ok(())
        }
        Command::Up {
            peers,
            port,
            tun,
            mtu,
            no_lan,
            stats,
        } => {
            let secret_key = state.key()?;
            let id = secret_key.public();
            let mut all: Vec<PeerConfig> = state
                .members()?
                .0
                .into_iter()
                .map(PeerConfig::from)
                .collect();
            for PeerSpec(peer) in peers {
                match all.iter_mut().find(|known| known.id == peer.id) {
                    Some(known) => known.addrs.extend(peer.addrs),
                    None => all.push(peer),
                }
            }
            if all.is_empty() {
                bail!("no devices to connect to: add one with `halo add <id> --name <name>`");
            }
            if all.iter().any(|peer| peer.id == id) {
                bail!("a device cannot be its own peer");
            }
            let name = tun.as_deref().or(default_tun_name());
            let tun = halo_core::create_tun(name, overlay_ipv4(&id), mtu)?;
            let config = NodeConfig {
                secret_key,
                port,
                mtu,
                peers: all,
                lan_discovery: !no_lan,
                stats_interval: stats.map(Duration::from_secs),
            };
            halo_core::run(config, tun, shutdown_signal()).await
        }
    }
}

/// Shows the emoji, asks the user and returns the other device when both sides said yes.
/// Runs a pairing step until Ctrl+C. Then the pairing ends as if the user said
/// no, and the caller still closes the connection: the other device learns at
/// once instead of after a timeout.
async fn unless_interrupted<T>(step: impl Future<Output = Result<Option<T>>>) -> Result<Option<T>> {
    tokio::select! {
        result = step => result,
        _ = tokio::signal::ctrl_c() => {
            println!("\nCancelled.");
            Ok(None)
        }
    }
}

async fn confirm(pending: Pending) -> Result<Option<(EndpointId, String, Vec<SocketAddr>)>> {
    println!(
        "\n{} wants to pair. Check that its screen shows:\n\n    {}\n",
        pending.peer_name,
        pending.emoji.join("  ")
    );
    print!("Do they match? [y/N] ");
    std::io::Write::flush(&mut std::io::stdout())?;
    // A plain thread rather than spawn_blocking: when the other device leaves,
    // the exit must not wait for this read.
    let (line_tx, line_rx) = tokio::sync::oneshot::channel();
    std::thread::spawn(move || {
        let mut line = String::new();
        let _ = line_tx.send(std::io::stdin().read_line(&mut line).map(|_| line));
    });
    let line = tokio::select! {
        line = line_rx => line??,
        () = pending.closed() => {
            println!("\nThe other device ended the pairing.");
            return Ok(None);
        }
    };
    let accept = matches!(
        line.trim().to_lowercase().as_str(),
        "y" | "yes" | "д" | "да"
    );
    let peer = (
        pending.peer,
        pending.peer_name.clone(),
        pending.peer_addrs.clone(),
    );
    if !accept {
        pending.confirm(false).await?;
        return Ok(None);
    }
    println!("Waiting for the other device to confirm...");
    Ok(pending.confirm(true).await?.then_some(peer))
}

/// Prints a QR code black on white, readable on dark and light terminals alike.
fn print_qr(text: &str) -> Result<()> {
    use qrcode::render::unicode::Dense1x2;
    let code = qrcode::QrCode::new(text.as_bytes())?;
    let image = code.render::<Dense1x2>().quiet_zone(true).build();
    for line in image.lines() {
        anstream::println!("\x1b[30;47m{line}\x1b[0m");
    }
    Ok(())
}

/// macOS names TUN devices `utunN` itself.
fn default_tun_name() -> Option<&'static str> {
    if cfg!(target_os = "macos") {
        None
    } else if cfg!(windows) {
        Some("Halo")
    } else {
        Some("halo0")
    }
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
