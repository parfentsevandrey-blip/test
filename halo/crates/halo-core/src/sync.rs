//! Journal exchange between two members, over their tunnel connection.
//!
//! The side that starts says how far each feed goes on its side. The other side
//! answers with the entries the first one lacks and how far its own feeds go;
//! the first side then sends what the other one lacks. A bidirectional QUIC
//! stream carries the exchange, next to the datagrams with the tunnel's packets.
//!
//! Entries are checked as they arrive: signature, chain, and limits on size and
//! count, so a peer cannot make this side do unbounded work.

use std::time::Duration;

use anyhow::{Context, Result, ensure};
use iroh::{
    EndpointId,
    endpoint::{Connection, RecvStream, SendStream},
};

use crate::journal::{Entry, Heads, Journal};

/// Opens every exchange, so other kinds of streams can come later.
const MAGIC: &[u8; 4] = b"HJS1";
const MAX_FEEDS: usize = 4096;
const MAX_ENTRIES: usize = 65_536;
const MAX_ENTRY: usize = 2048;
const TIMEOUT: Duration = Duration::from_secs(30);

/// Starts an exchange on `conn`. Returns the entries the other side sent.
pub async fn initiate(conn: &Connection, journal: &Journal) -> Result<Vec<Entry>> {
    tokio::time::timeout(TIMEOUT, async {
        let (mut send, mut recv) = conn.open_bi().await?;
        send.write_all(MAGIC).await?;
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

/// Answers an exchange the other side started. Returns the entries it sent.
pub async fn respond(
    mut send: SendStream,
    mut recv: RecvStream,
    journal: &Journal,
) -> Result<Vec<Entry>> {
    tokio::time::timeout(TIMEOUT, async {
        let mut magic = [0u8; 4];
        recv.read_exact(&mut magic).await?;
        ensure!(&magic == MAGIC, "not a journal exchange");
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
