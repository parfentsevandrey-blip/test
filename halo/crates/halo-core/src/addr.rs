//! Overlay addressing: every device gets a stable IPv4 address derived from its key.

use std::net::Ipv4Addr;

use iroh::EndpointId;

/// Overlay network: 100.64.0.0/10 (RFC 6598 shared address space).
pub const OVERLAY_NET: Ipv4Addr = Ipv4Addr::new(100, 64, 0, 0);
/// Prefix length of [`OVERLAY_NET`].
pub const OVERLAY_PREFIX: u8 = 10;

const HOST_MASK: u32 = (1 << (32 - OVERLAY_PREFIX)) - 1;

/// Derives the overlay IPv4 address of a device from its endpoint id.
///
/// The address is a pure function of the key, so every member computes the same
/// address for a device without any coordination.
pub fn overlay_ipv4(id: &EndpointId) -> Ipv4Addr {
    let hash = blake3::derive_key("halo overlay ipv4 v0", id.as_bytes());
    let host = u32::from_be_bytes([hash[0], hash[1], hash[2], hash[3]]) & HOST_MASK;
    // Skip the all-zeros and all-ones host parts.
    let host = host.clamp(1, HOST_MASK - 1);
    Ipv4Addr::from(u32::from(OVERLAY_NET) | host)
}

/// Returns true if `ip` belongs to the overlay network.
pub fn is_overlay(ip: Ipv4Addr) -> bool {
    u32::from(ip) & !HOST_MASK == u32::from(OVERLAY_NET)
}

#[cfg(test)]
mod tests {
    use iroh::SecretKey;

    use super::*;

    #[test]
    fn derived_address_is_stable_and_inside_overlay() {
        let id = SecretKey::from_bytes(&[7; 32]).public();
        let ip = overlay_ipv4(&id);
        assert_eq!(ip, overlay_ipv4(&id));
        assert!(is_overlay(ip));
        assert_ne!(ip, OVERLAY_NET);
        assert_ne!(u32::from(ip), u32::from(OVERLAY_NET) | HOST_MASK);
    }

    #[test]
    fn different_keys_get_different_addresses() {
        let a = overlay_ipv4(&SecretKey::from_bytes(&[1; 32]).public());
        let b = overlay_ipv4(&SecretKey::from_bytes(&[2; 32]).public());
        assert_ne!(a, b);
    }

    #[test]
    fn overlay_membership() {
        assert!(is_overlay(Ipv4Addr::new(100, 64, 0, 1)));
        assert!(is_overlay(Ipv4Addr::new(100, 127, 255, 254)));
        assert!(!is_overlay(Ipv4Addr::new(100, 128, 0, 1)));
        assert!(!is_overlay(Ipv4Addr::new(192, 168, 1, 1)));
    }
}
