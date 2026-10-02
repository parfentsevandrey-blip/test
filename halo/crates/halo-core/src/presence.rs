//! Where a device can be reached from anywhere: its public addresses, signed by
//! the device and passed between members.
//!
//! A laptop at home asks its router for a port that forwards to its node (UPnP,
//! NAT-PMP, PCP) and tells the other members the public address, together with
//! any global IPv6 address it has. A phone that met it at home dials those
//! addresses later from the mobile network. Addresses change, so presences are
//! not journal entries: only the latest one of each device counts, plus the
//! latest one with addresses, so a laptop that went from home to a café can
//! still be reached at home once it is back.

use std::{
    collections::BTreeMap,
    net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr},
    time::{SystemTime, UNIX_EPOCH},
};

use anyhow::{Context, Result, ensure};
use iroh::{EndpointId, SecretKey, Signature};

use crate::{
    addr::is_overlay,
    wire::{Reader, put_addrs},
};

const VERSION: u8 = 1;
/// Signatures cover this prefix too, so they mean nothing anywhere else.
const SIGNING_CONTEXT: &[u8] = b"halo presence v1";
const HEADER: &str = "# Halo presences: where devices can be reached, signed. Do not edit.\n";

/// The public addresses of one device, signed by it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Presence {
    device: EndpointId,
    /// Grows with every new presence of the device: the highest one counts.
    version: u64,
    addrs: Vec<SocketAddr>,
    signature: Signature,
}

impl Presence {
    pub fn device(&self) -> EndpointId {
        self.device
    }

    pub fn version(&self) -> u64 {
        self.version
    }

    pub fn addrs(&self) -> &[SocketAddr] {
        &self.addrs
    }

    fn sign(key: &SecretKey, version: u64, addrs: Vec<SocketAddr>) -> Self {
        let mut presence = Self {
            device: key.public(),
            version,
            addrs,
            signature: Signature::from_bytes(&[0; Signature::LENGTH]),
        };
        presence.signature = key.sign(&[SIGNING_CONTEXT, &presence.body()].concat());
        presence
    }

    fn body(&self) -> Vec<u8> {
        let mut bytes = vec![VERSION];
        bytes.extend_from_slice(self.device.as_bytes());
        bytes.extend_from_slice(&self.version.to_be_bytes());
        put_addrs(&mut bytes, &self.addrs);
        bytes
    }

    pub fn to_bytes(&self) -> Vec<u8> {
        let mut bytes = self.body();
        bytes.extend_from_slice(&self.signature.to_bytes());
        bytes
    }

    /// Parses a presence and checks its signature.
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        Self::parse_bytes(bytes).context("invalid presence")
    }

    fn parse_bytes(bytes: &[u8]) -> Result<Self> {
        ensure!(bytes.len() > Signature::LENGTH, "too short");
        let (body, signature) = bytes.split_at(bytes.len() - Signature::LENGTH);
        let signature = Signature::from_bytes(signature.try_into().expect("split at the length"));
        let mut reader = Reader(body);
        ensure!(reader.u8()? == VERSION, "unknown version");
        let device = reader.key()?;
        let version = reader.u64()?;
        let addrs = reader.addrs()?;
        reader.finish()?;
        device
            .verify(&[SIGNING_CONTEXT, body].concat(), &signature)
            .context("bad signature")?;
        Ok(Self {
            device,
            version,
            addrs,
            signature,
        })
    }
}

/// What is known about one device.
#[derive(Debug, Clone, PartialEq, Eq)]
struct Known {
    latest: Presence,
    /// The latest presence with addresses, if older than `latest`.
    reachable: Option<Presence>,
}

/// The latest presences of each device.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Presences(BTreeMap<EndpointId, Known>);

impl Presences {
    /// Reads the presence file: one presence per line, base64. Lines that do not
    /// check out are skipped: presences are only hints.
    pub fn parse(text: &str) -> Self {
        let mut presences = Self::default();
        for line in text.lines().map(str::trim) {
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            match data_encoding::BASE64URL_NOPAD
                .decode(line.as_bytes())
                .context("not base64")
                .and_then(|bytes| Presence::from_bytes(&bytes))
            {
                Ok(presence) => {
                    presences.insert(presence);
                }
                Err(err) => tracing::warn!("presence skipped: {err:#}"),
            }
        }
        presences
    }

    pub fn render(&self) -> String {
        let mut text = String::from(HEADER);
        for presence in self.iter() {
            text.push_str(&data_encoding::BASE64URL_NOPAD.encode(&presence.to_bytes()));
            text.push('\n');
        }
        text
    }

    /// Keeps `presence` if it is newer than what is known for its device.
    pub fn insert(&mut self, presence: Presence) -> bool {
        let Some(known) = self.0.get_mut(&presence.device) else {
            self.0.insert(
                presence.device,
                Known {
                    latest: presence,
                    reachable: None,
                },
            );
            return true;
        };
        if presence.version > known.latest.version {
            let previous = std::mem::replace(&mut known.latest, presence);
            if known.latest.addrs.is_empty() && !previous.addrs.is_empty() {
                known.reachable = Some(previous);
            } else if !known.latest.addrs.is_empty() {
                known.reachable = None;
            }
            return true;
        }
        // An older presence still helps if it has addresses and is the newest
        // such one.
        let newer_reachable = !presence.addrs.is_empty()
            && known.latest.addrs.is_empty()
            && presence.version < known.latest.version
            && known
                .reachable
                .as_ref()
                .is_none_or(|reachable| presence.version > reachable.version);
        if newer_reachable {
            known.reachable = Some(presence);
        }
        newer_reachable
    }

    /// The latest presence of `device`.
    pub fn get(&self, device: &EndpointId) -> Option<&Presence> {
        self.0.get(device).map(|known| &known.latest)
    }

    /// Where to try `device`: its latest addresses, and the last ones it had if
    /// it has none now.
    pub fn addrs(&self, device: &EndpointId) -> Vec<SocketAddr> {
        self.0
            .get(device)
            .map(|known| {
                let reachable = known.reachable.iter().flat_map(|presence| &presence.addrs);
                known
                    .latest
                    .addrs
                    .iter()
                    .chain(reachable)
                    .copied()
                    .collect()
            })
            .unwrap_or_default()
    }

    /// Every presence kept: what members pass on to each other.
    pub fn iter(&self) -> impl Iterator<Item = &Presence> {
        self.0
            .values()
            .flat_map(|known| std::iter::once(&known.latest).chain(&known.reachable))
    }

    /// Forgets the presences of devices `keep` rejects, e.g. removed ones.
    pub fn retain(&mut self, keep: impl Fn(&EndpointId) -> bool) {
        self.0.retain(|device, _| keep(device));
    }

    /// Signs a new presence of this device, unless its addresses did not change.
    pub fn announce(&mut self, key: &SecretKey, addrs: Vec<SocketAddr>) -> Option<Presence> {
        let me = key.public();
        let previous = self.get(&me);
        // Nothing to say: same addresses as before, or none ever.
        if previous.map_or(addrs.is_empty(), |previous| previous.addrs == addrs) {
            return None;
        }
        // The clock keeps versions growing across restarts; the previous version
        // keeps them growing when the clock jumps back.
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_or(0, |time| time.as_secs());
        let version = now.max(previous.map_or(0, |previous| previous.version + 1));
        let presence = Presence::sign(key, version, addrs);
        self.insert(presence.clone());
        Some(presence)
    }
}

/// Whether other devices could reach `ip` from anywhere on the internet.
///
/// Excludes private, shared (carrier-grade NAT, which the overlay also uses),
/// loopback, link-local and multicast ranges, and IPv6 outside global unicast.
pub fn is_public(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(ip) => is_public_v4(ip),
        IpAddr::V6(ip) => is_public_v6(ip),
    }
}

fn is_public_v4(ip: Ipv4Addr) -> bool {
    !(ip.is_private()
        || ip.is_loopback()
        || ip.is_link_local()
        || ip.is_multicast()
        || ip.is_broadcast()
        || ip.is_unspecified()
        || is_overlay(ip)
        || ip.octets()[0] == 0
        || ip.octets()[0] >= 240)
}

fn is_public_v6(ip: Ipv6Addr) -> bool {
    // Global unicast is 2000::/3; it excludes unique local (fc00::/7) and
    // link-local (fe80::/10) addresses.
    (ip.segments()[0] & 0xe000) == 0x2000
        && ip.to_ipv4_mapped().is_none()
        // Documentation (2001:db8::/32).
        && !(ip.segments()[0] == 0x2001 && ip.segments()[1] == 0x0db8)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn key(seed: u8) -> SecretKey {
        SecretKey::from_bytes(&[seed; 32])
    }

    fn addrs(list: &[&str]) -> Vec<SocketAddr> {
        list.iter().map(|addr| addr.parse().unwrap()).collect()
    }

    #[test]
    fn presences_roundtrip_and_reject_tampering() {
        let mut presences = Presences::default();
        let presence = presences
            .announce(&key(1), addrs(&["203.0.113.7:7777", "[2a01:4f8::1]:7777"]))
            .unwrap();
        let bytes = presence.to_bytes();
        assert_eq!(Presence::from_bytes(&bytes).unwrap(), presence);
        for i in [0, 1, 33, 40, bytes.len() / 2, bytes.len() - 1] {
            let mut tampered = bytes.clone();
            tampered[i] ^= 1;
            assert!(Presence::from_bytes(&tampered).is_err(), "byte {i}");
        }
        assert_eq!(Presences::parse(&presences.render()), presences);
        // A broken line costs that presence only.
        let text = format!("{}garbage\n", presences.render());
        assert_eq!(Presences::parse(&text), presences);
    }

    #[test]
    fn the_newest_presence_wins() {
        let mut mine = Presences::default();
        let first = mine
            .announce(&key(1), addrs(&["203.0.113.7:7777"]))
            .unwrap();
        assert!(
            mine.announce(&key(1), addrs(&["203.0.113.7:7777"]))
                .is_none(),
            "unchanged"
        );
        let second = mine
            .announce(&key(1), addrs(&["203.0.113.8:7777"]))
            .unwrap();
        assert!(second.version() > first.version());

        let mut theirs = Presences::default();
        assert!(theirs.insert(second.clone()));
        assert!(!theirs.insert(first), "older");
        assert!(!theirs.insert(second.clone()), "known");
        assert_eq!(
            theirs.get(&key(1).public()).unwrap().addrs(),
            second.addrs()
        );
        theirs.retain(|device| *device != key(1).public());
        assert!(theirs.get(&key(1).public()).is_none());
    }

    /// A laptop that left home keeps its home address on the others' side.
    #[test]
    fn the_last_reachable_addresses_stay_until_new_ones_come() {
        let mut laptop = Presences::default();
        let home = laptop
            .announce(&key(1), addrs(&["203.0.113.7:7777"]))
            .unwrap();
        let cafe = laptop.announce(&key(1), vec![]).unwrap();

        let mut phone = Presences::default();
        assert!(phone.insert(home.clone()));
        assert!(phone.insert(cafe.clone()));
        let id = key(1).public();
        assert_eq!(phone.get(&id).unwrap(), &cafe);
        assert_eq!(phone.addrs(&id), addrs(&["203.0.113.7:7777"]));
        assert_eq!(phone.iter().count(), 2, "both are passed on");

        // Learned in the other order, from someone who knew both.
        let mut other = Presences::default();
        assert!(other.insert(cafe.clone()));
        assert!(other.insert(home.clone()));
        assert!(!other.insert(home), "known");
        assert_eq!(other, phone);
        assert_eq!(Presences::parse(&phone.render()), phone);

        // Back at home with a new address: the old one goes.
        let back = laptop
            .announce(&key(1), addrs(&["203.0.113.9:7777"]))
            .unwrap();
        assert!(phone.insert(back));
        assert_eq!(phone.addrs(&id), addrs(&["203.0.113.9:7777"]));
        assert_eq!(phone.iter().count(), 1);
    }

    #[test]
    fn only_public_addresses_count() {
        for ip in ["203.0.113.7", "8.8.8.8", "2a01:4f8::1", "2001:470::1"] {
            assert!(is_public(ip.parse().unwrap()), "{ip}");
        }
        for ip in [
            "10.0.0.1",
            "172.16.5.4",
            "192.168.1.20",
            "100.64.0.1",
            "100.127.255.254",
            "127.0.0.1",
            "169.254.1.1",
            "224.0.0.251",
            "255.255.255.255",
            "0.0.0.0",
            "::1",
            "fe80::1",
            "fd00::1",
            "::ffff:8.8.8.8",
            "2001:db8::1",
            "ff02::fb",
        ] {
            assert!(!is_public(ip.parse().unwrap()), "{ip}");
        }
    }
}
