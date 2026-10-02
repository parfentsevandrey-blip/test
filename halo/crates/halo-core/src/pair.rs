//! Pairing: one scan adds two devices to each other's network.
//!
//! The host (a laptop running `halo pair`) shows a ticket as a QR code: its id, a
//! one-time secret and its local addresses. The scanner (the phone) connects to
//! the host over iroh and proves it knows the secret. Both then show the same
//! four emoji derived from the TLS session; once the user confirms on both
//! devices that they match, each adds the other as a member.
//!
//! The ticket keeps strangers on the network from pairing; the emoji catch a
//! leaked ticket, because the host would then show another device's emoji.

use std::{
    fmt,
    net::{IpAddr, SocketAddr},
    str::FromStr,
    time::Duration,
};

use anyhow::{Context, Result, anyhow, bail, ensure};
use iroh::{
    Endpoint, EndpointAddr, EndpointId, RelayMode, SecretKey, TransportAddr,
    endpoint::{Connection, RecvStream, SendStream, presets},
};

use crate::addr::is_overlay;

/// ALPN of the pairing protocol.
pub const ALPN: &[u8] = b"halo/pair/0";

const TICKET_PREFIX: &str = "HALO/1/";
const SECRET_LEN: usize = 16;
const MAX_NAME: usize = 32;
/// How long either side waits for the other one, including for a human to confirm.
const STEP_TIMEOUT: Duration = Duration::from_secs(120);
const CLOSE_GRACE: Duration = Duration::from_secs(2);

/// What the QR code carries.
///
/// Text form: `HALO/1/<ID>/<SECRET>/<IP:PORT>+<IP:PORT>`, upper-case base32, so it
/// fits the compact alphanumeric mode of QR codes.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Ticket {
    pub id: EndpointId,
    pub secret: [u8; SECRET_LEN],
    pub addrs: Vec<SocketAddr>,
}

impl fmt::Display for Ticket {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let addrs: Vec<String> = self.addrs.iter().map(ToString::to_string).collect();
        write!(
            f,
            "{TICKET_PREFIX}{}/{}/{}",
            data_encoding::BASE32_NOPAD.encode(self.id.as_bytes()),
            data_encoding::BASE32_NOPAD.encode(&self.secret),
            addrs.join("+")
        )
    }
}

impl FromStr for Ticket {
    type Err = anyhow::Error;

    fn from_str(text: &str) -> Result<Self> {
        let text = text.trim().to_ascii_uppercase();
        let rest = text
            .strip_prefix(TICKET_PREFIX)
            .context("not a Halo pairing code")?;
        let mut parts = rest.split('/');
        let (Some(id), Some(secret), addrs, None) =
            (parts.next(), parts.next(), parts.next(), parts.next())
        else {
            bail!("malformed pairing code");
        };
        let id = data_encoding::BASE32_NOPAD
            .decode(id.as_bytes())
            .ok()
            .and_then(|bytes| <[u8; 32]>::try_from(bytes).ok())
            .context("malformed device id in the pairing code")?;
        let id = EndpointId::from_bytes(&id).context("malformed device id in the pairing code")?;
        let secret = data_encoding::BASE32_NOPAD
            .decode(secret.as_bytes())
            .ok()
            .and_then(|bytes| <[u8; SECRET_LEN]>::try_from(bytes).ok())
            .context("malformed secret in the pairing code")?;
        let addrs = addrs
            .filter(|addrs| !addrs.is_empty())
            .map(|addrs| {
                addrs
                    .split('+')
                    .map(|addr| {
                        SocketAddr::from_str(addr).context("malformed address in the pairing code")
                    })
                    .collect::<Result<Vec<_>>>()
            })
            .transpose()?
            .unwrap_or_default();
        Ok(Self { id, secret, addrs })
    }
}

/// The other device, once the secret checked out and before the user confirms.
pub struct Pending {
    conn: Connection,
    send: SendStream,
    recv: RecvStream,
    /// The other device's key.
    pub peer: EndpointId,
    /// The name the other device suggests for itself.
    pub peer_name: String,
    /// Where the other device's node listens, if it said: its addresses with the
    /// node's port. Lets it be found where mDNS does not reach.
    pub peer_addrs: Vec<SocketAddr>,
    /// The emoji both screens show; the user compares them.
    pub emoji: [&'static str; 4],
}

impl Pending {
    /// Sends this side's answer and, after a yes, waits for the other side's.
    ///
    /// Returns true when both sides confirmed: then each adds the other. A device
    /// that declined or went away means false, not an error.
    pub async fn confirm(self, accept: bool) -> Result<bool> {
        self.confirm_unless(accept, std::future::pending()).await
    }

    /// Like [`Pending::confirm`], but stops waiting for the other side's answer
    /// once `cancel` resolves, and then does not pair.
    ///
    /// Both sides come to the same result, unless one of them goes away in the
    /// moment its answer arrives.
    pub async fn confirm_unless(
        mut self,
        accept: bool,
        cancel: impl Future<Output = ()>,
    ) -> Result<bool> {
        // A device that is gone cannot learn our answer, so it cannot pair: then
        // sending fails, and this side does not pair either.
        let sent =
            self.send.write_all(&[u8::from(accept)]).await.is_ok() && self.send.finish().is_ok();
        let answer = if accept && sent {
            let answered = tokio::select! {
                answer = tokio::time::timeout(STEP_TIMEOUT, self.their_answer()) => Some(answer),
                () = cancel => None,
            };
            let Some(answer) = answered else {
                // Our yes is out already: leave at once, so that a yes from the
                // other side can no longer arrive and make it pair alone.
                self.conn.close(0u32.into(), b"cancelled");
                return Ok(false);
            };
            answer.ok()
        } else {
            Some(false)
        };
        // A QUIC close discards whatever the other side has not read yet, so give
        // it time to read our answer, unless it closes first.
        let _ = tokio::time::timeout(CLOSE_GRACE, self.conn.closed()).await;
        self.conn.close(0u32.into(), b"done");
        answer.context("the other device did not answer")
    }

    /// Resolves when the other device ends the pairing: it declined, cancelled
    /// or went away. Lets a side that waits for its user stop waiting.
    pub async fn closed(&self) {
        self.conn.closed().await;
    }

    /// Whether the other side said yes. It counts once our own answer has
    /// arrived there too: otherwise the other side cannot pair.
    async fn their_answer(&mut self) -> bool {
        let mut answer = [0u8; 1];
        self.recv.read_exact(&mut answer).await.is_ok()
            && answer[0] == 1
            && matches!(self.send.stopped().await, Ok(None))
    }
}

/// The side that shows the ticket.
pub struct Host {
    endpoint: Endpoint,
    ticket: Ticket,
    intro: Intro,
}

impl Host {
    /// Opens a pairing endpoint on a random port with this device's key.
    ///
    /// `node_port` is the UDP port this device's node listens on, 0 if unknown.
    pub async fn bind(secret_key: SecretKey, name: &str, node_port: u16) -> Result<Self> {
        let endpoint = pairing_endpoint(secret_key).await?;
        let mut secret = [0u8; SECRET_LEN];
        getrandom::fill(&mut secret).map_err(|err| anyhow!("no randomness: {err}"))?;
        // Local IPv4 addresses only: they keep the QR code compact, and pairing
        // happens with both devices on the same network.
        let addrs = endpoint
            .addr()
            .ip_addrs()
            .copied()
            .filter(|addr| match addr.ip() {
                IpAddr::V4(ip) => !is_overlay(ip),
                IpAddr::V6(_) => false,
            })
            .collect();
        let ticket = Ticket {
            id: endpoint.id(),
            secret,
            addrs,
        };
        Ok(Self {
            endpoint,
            ticket,
            intro: Intro::new(name, node_port),
        })
    }

    pub fn ticket(&self) -> &Ticket {
        &self.ticket
    }

    /// The UDP port the pairing endpoint listens on, for IPv4 if it can.
    pub fn port(&self) -> u16 {
        let sockets = self.endpoint.bound_sockets();
        sockets
            .iter()
            .find(|addr| addr.is_ipv4())
            .or(sockets.first())
            .map_or(0, SocketAddr::port)
    }

    /// Waits for a device that knows the secret. Devices that do not are dropped.
    pub async fn accept(&self) -> Result<Pending> {
        loop {
            let incoming = self
                .endpoint
                .accept()
                .await
                .context("pairing endpoint closed")?;
            let Ok(accepting) = incoming.accept() else {
                continue;
            };
            let Ok(conn) = accepting.await else { continue };
            match tokio::time::timeout(STEP_TIMEOUT, self.handshake(conn)).await {
                Ok(Ok(pending)) => return Ok(pending),
                Ok(Err(err)) => tracing::warn!("pairing attempt rejected: {err:#}"),
                Err(_) => tracing::warn!("pairing attempt timed out"),
            }
        }
    }

    async fn handshake(&self, conn: Connection) -> Result<Pending> {
        let (mut send, mut recv) = conn.accept_bi().await?;
        let mut proof = [0u8; 32];
        recv.read_exact(&mut proof).await?;
        let peer = conn.remote_id();
        let expected = proof_of(&self.ticket.secret, &self.ticket.id, &peer);
        if blake3::Hash::from_bytes(proof) != expected {
            conn.close(1u32.into(), b"wrong pairing code");
            bail!("{} does not know the pairing code", peer.fmt_short());
        }
        let intro = Intro::read(&mut recv).await?;
        self.intro.write(&mut send).await?;
        let emoji = emoji_of(&conn)?;
        // The scanner's address as this connection sees it.
        let seen = conn
            .paths()
            .iter()
            .find_map(|path| match path.remote_addr() {
                TransportAddr::Ip(addr) => Some(addr.ip()),
                _ => None,
            });
        let peer_addrs = intro.addrs(seen);
        Ok(Pending {
            conn,
            send,
            recv,
            peer,
            peer_name: intro.name,
            peer_addrs,
            emoji,
        })
    }

    pub async fn close(self) {
        self.endpoint.close().await;
    }
}

/// The scanning side: connects to the host from the ticket.
///
/// `node_port` is the UDP port this device's node listens on, 0 if unknown.
/// Returns the endpoint too: it has to stay open until the pairing is confirmed.
pub async fn join(
    secret_key: SecretKey,
    name: &str,
    node_port: u16,
    ticket: &Ticket,
) -> Result<(Endpoint, Pending)> {
    let endpoint = pairing_endpoint(secret_key).await?;
    let me = endpoint.id();
    let addr = ticket
        .addrs
        .iter()
        .fold(EndpointAddr::new(ticket.id), |addr, ip| {
            addr.with_ip_addr(*ip)
        });
    let conn = tokio::time::timeout(STEP_TIMEOUT, endpoint.connect(addr, ALPN))
        .await
        .context("the other device did not answer")??;
    let (mut send, mut recv) = conn.open_bi().await?;
    let proof = proof_of(&ticket.secret, &ticket.id, &me);
    send.write_all(proof.as_bytes()).await?;
    Intro::new(name, node_port).write(&mut send).await?;
    let intro = tokio::time::timeout(STEP_TIMEOUT, Intro::read(&mut recv))
        .await
        .context("the other device did not answer")?
        .context("the other device refused the pairing code")?;
    let emoji = emoji_of(&conn)?;
    let mut peer_addrs = Vec::new();
    for addr in &ticket.addrs {
        peer_addrs.extend(intro.addrs(Some(addr.ip())));
    }
    let pending = Pending {
        conn,
        send,
        recv,
        peer: ticket.id,
        peer_name: intro.name,
        peer_addrs,
        emoji,
    };
    Ok((endpoint, pending))
}

async fn pairing_endpoint(secret_key: SecretKey) -> Result<Endpoint> {
    Endpoint::builder(presets::Minimal)
        .secret_key(secret_key)
        .alpns(vec![ALPN.to_vec()])
        .relay_mode(RelayMode::Disabled)
        .clear_address_lookup()
        .bind()
        .await
        .context("failed to open the pairing endpoint")
}

/// Proves knowledge of the secret, bound to both device keys.
fn proof_of(secret: &[u8; SECRET_LEN], host: &EndpointId, scanner: &EndpointId) -> blake3::Hash {
    let key = blake3::derive_key("halo pair v1 proof", secret);
    let mut hasher = blake3::Hasher::new_keyed(&key);
    hasher.update(host.as_bytes());
    hasher.update(scanner.as_bytes());
    hasher.finalize()
}

/// Four emoji from the TLS session: both ends of this very connection get the
/// same ones, a man in the middle would not.
fn emoji_of(conn: &Connection) -> Result<[&'static str; 4]> {
    let mut bytes = [0u8; 3];
    conn.export_keying_material(&mut bytes, b"halo pair v1 emoji", b"")
        .map_err(|err| anyhow!("TLS exporter failed: {err:?}"))?;
    let bits = u32::from_be_bytes([0, bytes[0], bytes[1], bytes[2]]);
    Ok(std::array::from_fn(|i| {
        EMOJI[(bits >> (18 - 6 * i)) as usize & 63]
    }))
}

/// What each side tells the other about itself: a name and its node's port.
struct Intro {
    name: String,
    node_port: u16,
}

impl Intro {
    fn new(name: &str, node_port: u16) -> Self {
        let mut end = name.len().min(MAX_NAME);
        while !name.is_char_boundary(end) {
            end -= 1;
        }
        Self {
            name: name[..end].to_string(),
            node_port,
        }
    }

    async fn write(&self, send: &mut SendStream) -> Result<()> {
        send.write_all(&[self.name.len() as u8]).await?;
        send.write_all(self.name.as_bytes()).await?;
        send.write_all(&self.node_port.to_be_bytes()).await?;
        Ok(())
    }

    async fn read(recv: &mut RecvStream) -> Result<Self> {
        let mut len = [0u8; 1];
        recv.read_exact(&mut len).await?;
        ensure!(usize::from(len[0]) <= MAX_NAME, "name too long");
        let mut name = vec![0u8; len[0].into()];
        recv.read_exact(&mut name).await?;
        let mut port = [0u8; 2];
        recv.read_exact(&mut port).await?;
        Ok(Self {
            name: String::from_utf8_lossy(&name).into_owned(),
            node_port: u16::from_be_bytes(port),
        })
    }

    /// The node's address at `ip`, if both are known.
    fn addrs(&self, ip: Option<IpAddr>) -> Vec<SocketAddr> {
        match (ip, self.node_port) {
            (Some(ip), port) if port != 0 => vec![SocketAddr::new(ip, port)],
            _ => Vec::new(),
        }
    }
}

/// Turns a device or host name into a member name: `MacBook Pro.local` -> `macbook-pro`.
pub fn member_name(raw: &str) -> String {
    let base = raw.split('.').next().unwrap_or(raw);
    let mut name = String::new();
    for c in base.chars() {
        let c = c.to_ascii_lowercase();
        if c.is_ascii_lowercase() || c.is_ascii_digit() {
            name.push(c);
        } else if !name.is_empty() && !name.ends_with('-') {
            name.push('-');
        }
    }
    let name = name.trim_end_matches('-');
    let name = &name[..name.len().min(MAX_NAME)];
    let name = name.trim_end_matches('-');
    if name.is_empty() {
        "device".to_string()
    } else {
        name.to_string()
    }
}

/// The emoji table of Matrix device verification: chosen to be easy to tell apart.
const EMOJI: [&str; 64] = [
    "🐶", "🐱", "🦁", "🐎", "🦄", "🐷", "🐘", "🐰", "🐼", "🐓", "🐧", "🐢", "🐟", "🐙", "🦋", "🌷",
    "🌳", "🌵", "🍄", "🌏", "🌙", "☁️", "🔥", "🍌", "🍎", "🍓", "🌽", "🍕", "🎂", "❤️", "😀", "🤖",
    "🎩", "👓", "🔧", "🎅", "👍", "☂️", "⌛", "⏰", "🎁", "💡", "📕", "✏️", "📎", "✂️", "🔒", "🔑",
    "🔨", "☎️", "🏁", "🚂", "🚲", "✈️", "🚀", "🏆", "⚽", "🎸", "🎺", "🔔", "⚓", "🎧", "📁", "📌",
];

#[cfg(test)]
mod tests {
    use super::*;

    fn key(seed: u8) -> SecretKey {
        SecretKey::from_bytes(&[seed; 32])
    }

    /// A host whose ticket points at loopback, which works in every test environment.
    async fn loopback_host() -> (Host, Ticket) {
        let host = Host::bind(key(1), "laptop", 7777).await.unwrap();
        let mut ticket = host.ticket().clone();
        ticket.addrs = vec![SocketAddr::from(([127, 0, 0, 1], host.port()))];
        (host, ticket)
    }

    #[test]
    fn ticket_roundtrip_is_qr_alphanumeric() {
        let ticket = Ticket {
            id: key(1).public(),
            secret: [7; SECRET_LEN],
            addrs: vec![
                "192.168.1.20:7777".parse().unwrap(),
                "10.0.0.5:41000".parse().unwrap(),
            ],
        };
        let text = ticket.to_string();
        // Upper case, digits and `/ : . +` only: the dense alphanumeric QR mode.
        assert!(
            text.chars()
                .all(|c| c.is_ascii_uppercase() || c.is_ascii_digit() || "/:.+".contains(c)),
            "{text}"
        );
        assert_eq!(text.parse::<Ticket>().unwrap(), ticket);
        // Pasted from a chat: lower case and stray whitespace are fine.
        assert_eq!(
            format!("  {}\n", text.to_lowercase())
                .parse::<Ticket>()
                .unwrap(),
            ticket
        );
    }

    #[test]
    fn ticket_without_addresses() {
        let ticket = Ticket {
            id: key(2).public(),
            secret: [9; SECRET_LEN],
            addrs: vec![],
        };
        assert_eq!(ticket.to_string().parse::<Ticket>().unwrap(), ticket);
    }

    #[test]
    fn malformed_tickets_are_rejected() {
        for text in [
            "",
            "hello",
            "HALO/1/",
            "HALO/1/AAAA/BBBB/",
            "HALO/2/X/Y/Z",
            "HALO/1/A/B/C/D",
        ] {
            assert!(text.parse::<Ticket>().is_err(), "{text}");
        }
    }

    #[test]
    fn member_names_from_device_names() {
        assert_eq!(member_name("MacBook-Pro.local"), "macbook-pro");
        assert_eq!(member_name("Pixel 8 Pro"), "pixel-8-pro");
        assert_eq!(member_name("DESKTOP-4F2K9L"), "desktop-4f2k9l");
        assert_eq!(member_name("Андрей’s iPhone"), "s-iphone");
        assert_eq!(member_name("---"), "device");
        assert!(crate::members::validate_name(&member_name(&"x".repeat(80))).is_ok());
    }

    #[test]
    fn proof_binds_both_keys() {
        let secret = [3; SECRET_LEN];
        let (host, scanner, other) = (key(1).public(), key(2).public(), key(3).public());
        assert_eq!(
            proof_of(&secret, &host, &scanner),
            proof_of(&secret, &host, &scanner)
        );
        assert_ne!(
            proof_of(&secret, &host, &scanner),
            proof_of(&secret, &host, &other)
        );
        assert_ne!(
            proof_of(&secret, &host, &scanner),
            proof_of(&[4; SECRET_LEN], &host, &scanner)
        );
    }

    /// Two devices pair over loopback: same emoji on both sides, both confirm.
    #[tokio::test]
    async fn pairing_over_loopback() {
        let (host, ticket) = loopback_host().await;
        let scanner = tokio::spawn(async move {
            let (endpoint, pending) = join(key(2), "phone", 0, &ticket).await.unwrap();
            assert_eq!(
                pending.peer_addrs,
                vec![SocketAddr::from(([127, 0, 0, 1], 7777))]
            );
            let result = (
                pending.peer,
                pending.peer_name.clone(),
                pending.emoji,
                pending.confirm(true).await.unwrap(),
            );
            endpoint.close().await;
            result
        });
        let pending = host.accept().await.unwrap();
        assert_eq!(pending.peer, key(2).public());
        assert_eq!(pending.peer_name, "phone");
        // The phone's node has no fixed port, so there is no address to keep.
        assert!(pending.peer_addrs.is_empty());
        let host_emoji = pending.emoji;
        assert!(pending.confirm(true).await.unwrap());

        let (peer, peer_name, scanner_emoji, confirmed) = scanner.await.unwrap();
        assert_eq!(peer, key(1).public());
        assert_eq!(peer_name, "laptop");
        assert_eq!(scanner_emoji, host_emoji);
        assert!(confirmed);
        host.close().await;
    }

    /// A device without the secret never gets to the emoji.
    #[tokio::test]
    async fn wrong_secret_is_refused() {
        let (host, mut ticket) = loopback_host().await;
        ticket.secret = [0xAA; SECRET_LEN];
        let accept = tokio::spawn(async move {
            let _ = tokio::time::timeout(Duration::from_secs(5), host.accept()).await;
        });
        assert!(join(key(2), "intruder", 0, &ticket).await.is_err());
        accept.await.unwrap();
    }

    /// A refusal on one side means no pairing on both.
    #[tokio::test]
    async fn refusal_on_one_side_cancels() {
        let (host, ticket) = loopback_host().await;
        let scanner = tokio::spawn(async move {
            let (endpoint, pending) = join(key(2), "phone", 0, &ticket).await.unwrap();
            let confirmed = pending.confirm(true).await.unwrap();
            endpoint.close().await;
            confirmed
        });
        let pending = host.accept().await.unwrap();
        assert!(!pending.confirm(false).await.unwrap());
        assert!(!scanner.await.unwrap());
        host.close().await;
    }

    /// Declining does not wait for the other user: their side sees the pairing end.
    #[tokio::test]
    async fn declining_does_not_wait_for_the_other_side() {
        let (host, ticket) = loopback_host().await;
        let scanner = tokio::spawn(async move {
            let (endpoint, pending) = join(key(2), "phone", 0, &ticket).await.unwrap();
            // Its user is still comparing the emoji when the host declines.
            tokio::time::timeout(Duration::from_secs(10), pending.closed())
                .await
                .expect("the scanner did not see the pairing end");
            let confirmed = pending.confirm(true).await.unwrap();
            endpoint.close().await;
            confirmed
        });
        let pending = host.accept().await.unwrap();
        let declined = tokio::time::timeout(Duration::from_secs(5), pending.confirm(false))
            .await
            .expect("declining waited for the other side");
        assert!(!declined.unwrap());
        assert!(!scanner.await.unwrap());
        host.close().await;
    }

    /// A device that goes away without answering ends the pairing on the other
    /// side: no error, nothing added, and a waiting user hears about it.
    #[tokio::test]
    async fn leaving_ends_the_pairing() {
        let (host, ticket) = loopback_host().await;
        let scanner = tokio::spawn(async move {
            let (endpoint, pending) = join(key(2), "phone", 0, &ticket).await.unwrap();
            // The user gives up instead of answering.
            drop(pending);
            endpoint.close().await;
        });
        let pending = host.accept().await.unwrap();
        tokio::time::timeout(Duration::from_secs(5), pending.closed())
            .await
            .expect("the host did not see the scanner leave");
        assert!(!pending.confirm(true).await.unwrap());
        scanner.await.unwrap();
        host.close().await;
    }
}
