//! Datagram framing: IP packets travel inside QUIC datagrams.
//!
//! Every datagram starts with a one-byte tag. A datagram carries either a whole
//! IP packet or one fragment of a packet that does not fit into a single
//! datagram on the current path (mobile paths can be narrower than the tunnel MTU).

use std::{
    collections::HashMap,
    net::Ipv4Addr,
    time::{Duration, Instant},
};

use bytes::{BufMut, Bytes, BytesMut};

/// Tag: the rest of the datagram is one whole IP packet.
pub const WHOLE: u8 = 0;
/// Tag: the rest is a fragment: id (u16, big endian), index (u8), count (u8), data.
pub const FRAGMENT: u8 = 1;

const FRAGMENT_HEADER: usize = 5;
const MAX_PACKET: usize = 65_535;
const REASSEMBLY_TIMEOUT: Duration = Duration::from_secs(2);
const MAX_PENDING: usize = 64;

/// Splits a whole-packet frame into datagrams of at most `max` bytes.
///
/// `frame` must start with the [`WHOLE`] tag. A frame that already fits is
/// returned as is. Returns `None` if the packet cannot be split for this path.
pub fn split(frame: Bytes, max: usize, id: u16) -> Option<Vec<Bytes>> {
    debug_assert_eq!(frame.first(), Some(&WHOLE));
    if frame.len() <= max {
        return Some(vec![frame]);
    }
    let chunk = max
        .checked_sub(FRAGMENT_HEADER)
        .filter(|chunk| *chunk > 0)?;
    let packet = &frame[1..];
    let count = u8::try_from(packet.len().div_ceil(chunk)).ok()?;
    let fragments = packet
        .chunks(chunk)
        .enumerate()
        .map(|(index, data)| {
            let mut buf = BytesMut::with_capacity(FRAGMENT_HEADER + data.len());
            buf.put_u8(FRAGMENT);
            buf.put_u16(id);
            buf.put_u8(index as u8);
            buf.put_u8(count);
            buf.put_slice(data);
            buf.freeze()
        })
        .collect();
    Some(fragments)
}

/// Rebuilds IP packets from received datagrams.
#[derive(Debug, Default)]
pub struct Reassembler {
    pending: HashMap<u16, Pending>,
}

#[derive(Debug)]
struct Pending {
    parts: Vec<Option<Bytes>>,
    received: usize,
    started: Instant,
}

impl Pending {
    fn new(count: usize, now: Instant) -> Self {
        Self {
            parts: vec![None; count],
            received: 0,
            started: now,
        }
    }
}

impl Reassembler {
    /// Feeds one datagram, returning a complete IP packet once one is available.
    ///
    /// Malformed datagrams are ignored.
    pub fn push(&mut self, datagram: Bytes) -> Option<Bytes> {
        match *datagram.first()? {
            WHOLE => Some(datagram.slice(1..)),
            FRAGMENT => self.push_fragment(datagram),
            _ => None,
        }
    }

    fn push_fragment(&mut self, datagram: Bytes) -> Option<Bytes> {
        if datagram.len() <= FRAGMENT_HEADER {
            return None;
        }
        let id = u16::from_be_bytes([datagram[1], datagram[2]]);
        let index = usize::from(datagram[3]);
        let count = usize::from(datagram[4]);
        if index >= count {
            return None;
        }

        let now = Instant::now();
        self.expire(now);
        let pending = self
            .pending
            .entry(id)
            .or_insert_with(|| Pending::new(count, now));
        if pending.parts.len() != count {
            // Leftovers of an older packet that reused this id: start over.
            *pending = Pending::new(count, now);
        }
        let slot = &mut pending.parts[index];
        if slot.is_none() {
            *slot = Some(datagram.slice(FRAGMENT_HEADER..));
            pending.received += 1;
        }
        if pending.received < count {
            return None;
        }

        let parts = self.pending.remove(&id)?.parts;
        let len: usize = parts.iter().flatten().map(Bytes::len).sum();
        if len > MAX_PACKET {
            return None;
        }
        let mut packet = BytesMut::with_capacity(len);
        for part in parts.into_iter().flatten() {
            packet.put_slice(&part);
        }
        Some(packet.freeze())
    }

    fn expire(&mut self, now: Instant) {
        self.pending
            .retain(|_, pending| now.duration_since(pending.started) < REASSEMBLY_TIMEOUT);
        if self.pending.len() >= MAX_PENDING {
            // A flood of incomplete packets: keep memory bounded.
            self.pending.clear();
        }
    }
}

/// Returns the source and destination addresses of an IPv4 packet.
pub fn ipv4_endpoints(packet: &[u8]) -> Option<(Ipv4Addr, Ipv4Addr)> {
    if packet.len() < 20 || packet[0] >> 4 != 4 {
        return None;
    }
    let src = Ipv4Addr::new(packet[12], packet[13], packet[14], packet[15]);
    let dst = Ipv4Addr::new(packet[16], packet[17], packet[18], packet[19]);
    Some((src, dst))
}

/// Returns the UDP source port of an IPv4 packet (first fragment only).
pub fn ipv4_udp_src_port(packet: &[u8]) -> Option<u16> {
    const UDP: u8 = 17;
    if packet.len() < 20 || packet[0] >> 4 != 4 || packet[9] != UDP {
        return None;
    }
    let fragment_offset = u16::from_be_bytes([packet[6], packet[7]]) & 0x1fff;
    let header_len = usize::from(packet[0] & 0x0f) * 4;
    if fragment_offset != 0 || header_len < 20 || packet.len() < header_len + 2 {
        return None;
    }
    Some(u16::from_be_bytes([
        packet[header_len],
        packet[header_len + 1],
    ]))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn frame(packet: &[u8]) -> Bytes {
        let mut buf = BytesMut::with_capacity(packet.len() + 1);
        buf.put_u8(WHOLE);
        buf.put_slice(packet);
        buf.freeze()
    }

    fn packet(len: usize) -> Vec<u8> {
        (0..len).map(|i| (i % 251) as u8).collect()
    }

    #[test]
    fn whole_packet_roundtrip() {
        let data = packet(1200);
        let datagrams = split(frame(&data), 1300, 0).unwrap();
        assert_eq!(datagrams.len(), 1);
        let mut reassembler = Reassembler::default();
        assert_eq!(reassembler.push(datagrams[0].clone()).unwrap(), data);
    }

    #[test]
    fn exact_fit_is_not_fragmented() {
        let data = packet(1199);
        assert_eq!(split(frame(&data), 1200, 0).unwrap().len(), 1);
        assert_eq!(split(frame(&data), 1199, 0).unwrap().len(), 2);
    }

    #[test]
    fn fragments_reassemble_in_any_order_with_duplicates() {
        let data = packet(3000);
        let datagrams = split(frame(&data), 1100, 42).unwrap();
        assert_eq!(datagrams.len(), 3);
        assert!(datagrams.iter().all(|d| d.len() <= 1100));

        let mut reassembler = Reassembler::default();
        assert!(reassembler.push(datagrams[2].clone()).is_none());
        assert!(reassembler.push(datagrams[0].clone()).is_none());
        assert!(reassembler.push(datagrams[0].clone()).is_none());
        assert_eq!(reassembler.push(datagrams[1].clone()).unwrap(), data);
        assert!(reassembler.pending.is_empty());
    }

    #[test]
    fn interleaved_packets_do_not_mix() {
        let a = packet(2000);
        let b: Vec<u8> = packet(2500).into_iter().rev().collect();
        let fa = split(frame(&a), 1000, 1).unwrap();
        let fb = split(frame(&b), 1000, 2).unwrap();
        let mut reassembler = Reassembler::default();
        let mut done = Vec::new();
        for datagram in fa.iter().zip(fb.iter()).flat_map(|(x, y)| [x, y]) {
            done.extend(reassembler.push(datagram.clone()));
        }
        for datagram in fb.iter().skip(fa.len()) {
            done.extend(reassembler.push(datagram.clone()));
        }
        assert_eq!(done, vec![Bytes::from(a), Bytes::from(b)]);
    }

    #[test]
    fn unsplittable_paths_are_rejected() {
        assert!(split(frame(&packet(100)), FRAGMENT_HEADER, 0).is_none());
        // More than 255 fragments.
        assert!(split(frame(&packet(3000)), FRAGMENT_HEADER + 10, 0).is_none());
    }

    #[test]
    fn malformed_datagrams_are_ignored() {
        let mut reassembler = Reassembler::default();
        assert!(reassembler.push(Bytes::new()).is_none());
        assert!(reassembler.push(Bytes::from_static(&[9, 1, 2])).is_none());
        // Fragment header too short.
        assert!(
            reassembler
                .push(Bytes::from_static(&[FRAGMENT, 0, 1, 0]))
                .is_none()
        );
        // Index out of range.
        assert!(
            reassembler
                .push(Bytes::from_static(&[FRAGMENT, 0, 1, 2, 2, 7]))
                .is_none()
        );
        // Zero fragments.
        assert!(
            reassembler
                .push(Bytes::from_static(&[FRAGMENT, 0, 1, 0, 0, 7]))
                .is_none()
        );
        assert!(reassembler.pending.is_empty());
    }

    #[test]
    fn mismatched_count_restarts_the_packet() {
        let mut reassembler = Reassembler::default();
        assert!(
            reassembler
                .push(Bytes::from_static(&[FRAGMENT, 0, 5, 0, 3, 1]))
                .is_none()
        );
        // Same id, different packet with two fragments.
        assert!(
            reassembler
                .push(Bytes::from_static(&[FRAGMENT, 0, 5, 0, 2, 1]))
                .is_none()
        );
        let packet = reassembler.push(Bytes::from_static(&[FRAGMENT, 0, 5, 1, 2, 2]));
        assert_eq!(packet.unwrap(), Bytes::from_static(&[1, 2]));
    }

    #[test]
    fn pending_packets_are_bounded() {
        let mut reassembler = Reassembler::default();
        for id in 0..1000u16 {
            let [hi, lo] = id.to_be_bytes();
            reassembler.push(Bytes::copy_from_slice(&[FRAGMENT, hi, lo, 0, 2, 1]));
        }
        assert!(reassembler.pending.len() <= MAX_PENDING);
    }

    #[test]
    fn ipv4_header_addresses() {
        let mut header = [0u8; 20];
        header[0] = 0x45;
        header[12..16].copy_from_slice(&[100, 64, 0, 1]);
        header[16..20].copy_from_slice(&[100, 64, 0, 2]);
        assert_eq!(
            ipv4_endpoints(&header),
            Some((Ipv4Addr::new(100, 64, 0, 1), Ipv4Addr::new(100, 64, 0, 2)))
        );
        header[0] = 0x60;
        assert_eq!(ipv4_endpoints(&header), None);
        assert_eq!(ipv4_endpoints(&header[..19]), None);
    }

    #[test]
    fn udp_source_port() {
        let mut packet = [0u8; 28];
        packet[0] = 0x45;
        packet[9] = 17;
        packet[20..22].copy_from_slice(&7777u16.to_be_bytes());
        assert_eq!(ipv4_udp_src_port(&packet), Some(7777));
        // Not the first fragment: no UDP header.
        packet[7] = 1;
        assert_eq!(ipv4_udp_src_port(&packet), None);
        packet[7] = 0;
        // TCP.
        packet[9] = 6;
        assert_eq!(ipv4_udp_src_port(&packet), None);
        packet[9] = 17;
        assert_eq!(ipv4_udp_src_port(&packet[..21]), None);
    }
}
