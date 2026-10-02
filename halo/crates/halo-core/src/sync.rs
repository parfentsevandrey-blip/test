//! Exchanges between two members, over their tunnel connection: the member
//! journal and the presences. Each one runs on its own bidirectional QUIC
//! stream, next to the datagrams with the tunnel's packets, and starts with four
//! bytes that say what it is.
//!
//! Journal: the side that starts says how far each feed goes on its side. The
//! other side answers with the entries the first one lacks and how far its own
//! feeds go; the first side then sends what the other one lacks.
//!
//! Presences: both sides send the presences they know, and the address they see
//! the other side at; each keeps the newer presences. A device behind a NAT
//! learns its public address that way, and once others know it, two devices
//! behind NATs can dial each other at the same time and get through.
//!
//! Everything is checked as it arrives: signatures, chains, and limits on size
//! and count, so a peer cannot make this side do unbounded work.

use std::{net::SocketAddr, time::Duration};

use anyhow::{Context, Result, ensure};
use iroh::{
    EndpointId,
    endpoint::{Connection, RecvStream, SendStream},
};

use crate::{
    journal::{Entry, Heads, Journal},
    presence::Presence,
    wire::{Reader, put_addrs},
};

const JOURNAL: &[u8; 4] = b"HJS1";
const PRESENCES: &[u8; 4] = b"HPR1";
const LINKS: &[u8; 4] = b"HLK1";
const MAX_FEEDS: usize = 4096;
const MAX_ENTRIES: usize = 65_536;
const MAX_ENTRY: usize = 2048;
const MAX_PRESENCES: usize = 4096;
const MAX_PRESENCE: usize = 512;
const TIMEOUT: Duration = Duration::from_secs(30);

/// What an incoming stream carries.
pub enum Kind {
    Journal,
    Presences,
    /// The members the other side is connected to right now.
    Links,
}

/// Reads the first bytes of a stream the other side opened.
pub async fn kind(recv: &mut RecvStream) -> Result<Kind> {
    let mut magic = [0u8; 4];
    tokio::time::timeout(TIMEOUT, recv.read_exact(&mut magic))
        .await
        .context("timed out")??;
    match &magic {
        JOURNAL => Ok(Kind::Journal),
        PRESENCES => Ok(Kind::Presences),
        LINKS => Ok(Kind::Links),
        _ => anyhow::bail!("unknown exchange"),
    }
}

/// Starts a journal exchange on `conn`. Returns the entries the other side sent.
pub async fn initiate(conn: &Connection, journal: &Journal) -> Result<Vec<Entry>> {
    tokio::time::timeout(TIMEOUT, async {
        let (mut send, mut recv) = conn.open_bi().await?;
        send.write_all(JOURNAL).await?;
        write_heads(&mut send, &journal.heads()).await?;
        let entries = read_entries(&mut recv).await?;
        let theirs = read_heads(&mut recv).await?;
        write_entries(&mut send, &journal.missing(&theirs)).await?;
        send.finish()?;
        Ok(entries)
    })
    .await
    .context("journal exchange timed out")?
}

/// Answers a journal exchange the other side started, once [`kind`] said so.
/// Returns the entries it sent.
pub async fn respond(
    mut send: SendStream,
    mut recv: RecvStream,
    journal: &Journal,
) -> Result<Vec<Entry>> {
    tokio::time::timeout(TIMEOUT, async {
        let theirs = read_heads(&mut recv).await?;
        write_entries(&mut send, &journal.missing(&theirs)).await?;
        write_heads(&mut send, &journal.heads()).await?;
        let entries = read_entries(&mut recv).await?;
        send.finish()?;
        Ok(entries)
    })
    .await
    .context("journal exchange timed out")?
}

/// What the other side of a presence exchange said.
pub struct Heard {
    pub presences: Vec<Presence>,
    /// Where it sees this side, if it could tell.
    pub seen_at: Option<SocketAddr>,
}

/// Starts a presence exchange on `conn`. `seen_at` is where this side sees the
/// other one.
pub async fn initiate_presences(
    conn: &Connection,
    presences: &[Presence],
    seen_at: Option<SocketAddr>,
) -> Result<Heard> {
    tokio::time::timeout(TIMEOUT, async {
        let (mut send, mut recv) = conn.open_bi().await?;
        send.write_all(PRESENCES).await?;
        write_presences(&mut send, presences, seen_at).await?;
        send.finish()?;
        read_presences(&mut recv).await
    })
    .await
    .context("presence exchange timed out")?
}

/// Answers a presence exchange the other side started, once [`kind`] said so.
pub async fn respond_presences(
    mut send: SendStream,
    mut recv: RecvStream,
    presences: &[Presence],
    seen_at: Option<SocketAddr>,
) -> Result<Heard> {
    tokio::time::timeout(TIMEOUT, async {
        let heard = read_presences(&mut recv).await?;
        write_presences(&mut send, presences, seen_at).await?;
        send.finish()?;
        Ok(heard)
    })
    .await
    .context("presence exchange timed out")?
}

async fn write_presences(
    send: &mut SendStream,
    presences: &[Presence],
    seen_at: Option<SocketAddr>,
) -> Result<()> {
    let presences = &presences[..presences.len().min(MAX_PRESENCES)];
    let mut bytes = (presences.len() as u16).to_be_bytes().to_vec();
    for presence in presences {
        let presence = presence.to_bytes();
        bytes.extend_from_slice(&(presence.len() as u16).to_be_bytes());
        bytes.extend_from_slice(&presence);
    }
    put_addrs(&mut bytes, seen_at.as_slice());
    send.write_all(&bytes).await?;
    Ok(())
}

async fn read_presences(recv: &mut RecvStream) -> Result<Heard> {
    let mut count = [0u8; 2];
    recv.read_exact(&mut count).await?;
    let count = usize::from(u16::from_be_bytes(count));
    ensure!(count <= MAX_PRESENCES, "too many presences");
    let mut presences = Vec::with_capacity(count);
    let mut buf = vec![0u8; MAX_PRESENCE];
    for _ in 0..count {
        let mut len = [0u8; 2];
        recv.read_exact(&mut len).await?;
        let len = usize::from(u16::from_be_bytes(len));
        ensure!(len <= MAX_PRESENCE, "presence too large");
        recv.read_exact(&mut buf[..len]).await?;
        presences.push(Presence::from_bytes(&buf[..len])?);
    }
    // Zero or one address: count, then family, address and port.
    let mut seen = [0u8; 1 + 1 + 16 + 2];
    recv.read_exact(&mut seen[..1]).await?;
    let len = match seen[0] {
        0 => 1,
        1 => {
            recv.read_exact(&mut seen[1..2]).await?;
            let len = if seen[1] == 4 { 2 + 4 + 2 } else { 2 + 16 + 2 };
            recv.read_exact(&mut seen[2..len]).await?;
            len
        }
        _ => anyhow::bail!("too many addresses"),
    };
    let seen_at = Reader(&seen[..len]).addrs()?.pop();
    Ok(Heard { presences, seen_at })
}

/// Tells the other side of `conn` which members this side is connected to, so
/// it can send packets for them through this side.
pub async fn send_links(conn: &Connection, links: &[EndpointId]) -> Result<()> {
    tokio::time::timeout(TIMEOUT, async {
        let (mut send, mut recv) = conn.open_bi().await?;
        let links = &links[..links.len().min(MAX_FEEDS)];
        let mut bytes = LINKS.to_vec();
        bytes.extend_from_slice(&(links.len() as u16).to_be_bytes());
        for id in links {
            bytes.extend_from_slice(id.as_bytes());
        }
        send.write_all(&bytes).await?;
        send.finish()?;
        // The other side only reads; its end of the stream closes empty.
        let _ = recv.read_to_end(0).await;
        Ok(())
    })
    .await
    .context("links report timed out")?
}

/// Reads a links report, once [`kind`] said so.
pub async fn read_links(mut send: SendStream, mut recv: RecvStream) -> Result<Vec<EndpointId>> {
    tokio::time::timeout(TIMEOUT, async {
        let mut count = [0u8; 2];
        recv.read_exact(&mut count).await?;
        let count = usize::from(u16::from_be_bytes(count));
        ensure!(count <= MAX_FEEDS, "too many links");
        let mut links = Vec::with_capacity(count);
        for _ in 0..count {
            let mut id = [0u8; 32];
            recv.read_exact(&mut id).await?;
            links.push(EndpointId::from_bytes(&id).context("invalid device key")?);
        }
        send.finish()?;
        Ok(links)
    })
    .await
    .context("links report timed out")?
}

async fn write_heads(send: &mut SendStream, heads: &Heads) -> Result<()> {
    let mut bytes = Vec::with_capacity(2 + heads.len() * 40);
    bytes.extend_from_slice(&(heads.len().min(MAX_FEEDS) as u16).to_be_bytes());
    for (author, len) in heads.iter().take(MAX_FEEDS) {
        bytes.extend_from_slice(author.as_bytes());
        bytes.extend_from_slice(&len.to_be_bytes());
    }
    send.write_all(&bytes).await?;
    Ok(())
}

async fn read_heads(recv: &mut RecvStream) -> Result<Heads> {
    let mut count = [0u8; 2];
    recv.read_exact(&mut count).await?;
    let count = usize::from(u16::from_be_bytes(count));
    ensure!(count <= MAX_FEEDS, "too many feeds");
    let mut heads = Heads::new();
    for _ in 0..count {
        let mut head = [0u8; 40];
        recv.read_exact(&mut head).await?;
        let author = EndpointId::from_bytes(head[..32].try_into().expect("32 bytes"))
            .context("invalid device key")?;
        heads.insert(
            author,
            u64::from_be_bytes(head[32..].try_into().expect("8 bytes")),
        );
    }
    Ok(heads)
}

async fn write_entries(send: &mut SendStream, entries: &[Entry]) -> Result<()> {
    let entries = &entries[..entries.len().min(MAX_ENTRIES)];
    send.write_all(&(entries.len() as u32).to_be_bytes())
        .await?;
    for entry in entries {
        let bytes = entry.to_bytes();
        send.write_all(&(bytes.len() as u16).to_be_bytes()).await?;
        send.write_all(&bytes).await?;
    }
    Ok(())
}

async fn read_entries(recv: &mut RecvStream) -> Result<Vec<Entry>> {
    let mut count = [0u8; 4];
    recv.read_exact(&mut count).await?;
    let count = u32::from_be_bytes(count) as usize;
    ensure!(count <= MAX_ENTRIES, "too many journal entries");
    let mut entries = Vec::with_capacity(count.min(1024));
    let mut buf = vec![0u8; MAX_ENTRY];
    for _ in 0..count {
        let mut len = [0u8; 2];
        recv.read_exact(&mut len).await?;
        let len = usize::from(u16::from_be_bytes(len));
        ensure!(len <= MAX_ENTRY, "journal entry too large");
        recv.read_exact(&mut buf[..len]).await?;
        entries.push(Entry::from_bytes(&buf[..len])?);
    }
    Ok(entries)
}
