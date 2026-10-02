//! A stand-in member device for smoke tests: it answers pings sent to its
//! address in the network, with no TUN device of its own. One machine can then
//! test its own tunnel end to end: the system's ping goes into the TUN device,
//! through the node to this peer and back.
//!
//!     echo-peer <state-dir> <port>
//!
//! Prints `id <id>` and `ip <address in the network>`, then answers until killed.
//! It talks to any device; only for tests.

use std::{
    net::{Ipv4Addr, SocketAddr},
    path::PathBuf,
};

use anyhow::{Context, Result};
use bytes::{BufMut, Bytes, BytesMut};
use halo_core::{ALPN, addr::overlay_ipv4, frame, state::State};
use iroh::{
    Endpoint, RelayMode,
    endpoint::{Connection, presets},
};

#[tokio::main]
async fn main() -> Result<()> {
    let mut args = std::env::args().skip(1);
    let (Some(dir), Some(port)) = (args.next(), args.next()) else {
        anyhow::bail!("usage: echo-peer <state-dir> <port>");
    };
    let port: u16 = port.parse().context("invalid port")?;
    let key = State::new(Some(PathBuf::from(dir)))?.key()?;
    let ip = overlay_ipv4(&key.public());
    let endpoint = Endpoint::builder(presets::Minimal)
        .secret_key(key.clone())
        .alpns(vec![ALPN.to_vec()])
        .relay_mode(RelayMode::Disabled)
        .clear_address_lookup()
        .clear_ip_transports()
        .bind_addr(SocketAddr::from((Ipv4Addr::LOCALHOST, port)))?
        .bind()
        .await?;
    println!("id {}", key.public());
    println!("ip {ip}");
    while let Some(incoming) = endpoint.accept().await {
        tokio::spawn(async move {
            let Ok(accepting) = incoming.accept() else {
                return;
            };
            if let Ok(conn) = accepting.await {
                eprintln!("connected: {}", conn.remote_id());
                let err = answer(&conn, ip).await;
                eprintln!("disconnected: {err:#}");
            }
        });
    }
    Ok(())
}

/// Answers every echo request for `ip` that comes over `conn`.
async fn answer(conn: &Connection, ip: Ipv4Addr) -> anyhow::Error {
    loop {
        let datagram = match conn.read_datagram().await {
            Ok(datagram) => datagram,
            Err(err) => return err.into(),
        };
        // Pings are small: whole packets only.
        if datagram.first() != Some(&frame::WHOLE) {
            continue;
        }
        let Some(reply) = echo_reply(&datagram[1..], ip) else {
            continue;
        };
        let mut out = BytesMut::with_capacity(reply.len() + 1);
        out.put_u8(frame::WHOLE);
        out.extend_from_slice(&reply);
        if let Err(err) = conn.send_datagram(out.freeze()) {
            return err.into();
        }
    }
}

/// The ICMP echo reply to an IPv4 echo request for `ip`.
fn echo_reply(packet: &[u8], ip: Ipv4Addr) -> Option<Bytes> {
    let header = usize::from(packet.first()? & 0x0f) * 4;
    let total = usize::from(u16::from_be_bytes([*packet.get(2)?, *packet.get(3)?]));
    if packet[0] >> 4 != 4 || header < 20 || total < header + 8 || packet.len() < total {
        return None;
    }
    let to: [u8; 4] = packet[16..20].try_into().ok()?;
    // ICMP, an echo request, for this peer.
    if packet[9] != 1 || packet[header] != 8 || Ipv4Addr::from(to) != ip {
        return None;
    }
    let mut reply = packet[..total].to_vec();
    reply.copy_within(12..16, 16);
    reply[12..16].copy_from_slice(&to);
    reply[8] = 64;
    reply[10..12].fill(0);
    let sum = checksum(&reply[..header]);
    reply[10..12].copy_from_slice(&sum.to_be_bytes());
    reply[header] = 0;
    reply[header + 2..header + 4].fill(0);
    let sum = checksum(&reply[header..]);
    reply[header + 2..header + 4].copy_from_slice(&sum.to_be_bytes());
    Some(reply.into())
}

/// The Internet checksum (RFC 1071).
fn checksum(bytes: &[u8]) -> u16 {
    let mut sum: u32 = bytes
        .chunks(2)
        .map(|pair| u32::from(u16::from_be_bytes([pair[0], *pair.get(1).unwrap_or(&0)])))
        .sum();
    while sum > 0xffff {
        sum = (sum & 0xffff) + (sum >> 16);
    }
    !(sum as u16)
}
