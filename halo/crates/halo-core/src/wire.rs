//! Byte encoding shared by the signed records: journal entries and presences.

use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};

use anyhow::{Context, Result, bail, ensure};
use iroh::EndpointId;

pub(crate) const MAX_NAME: usize = 32;
pub(crate) const MAX_ADDRS: usize = 16;

/// A name of up to [`MAX_NAME`] bytes, cut at a character boundary.
pub(crate) fn put_name(bytes: &mut Vec<u8>, name: &str) {
    let mut end = name.len().min(MAX_NAME);
    while !name.is_char_boundary(end) {
        end -= 1;
    }
    bytes.push(end as u8);
    bytes.extend_from_slice(&name.as_bytes()[..end]);
}

/// Up to [`MAX_ADDRS`] addresses: family (4 or 6), address, port.
pub(crate) fn put_addrs(bytes: &mut Vec<u8>, addrs: &[SocketAddr]) {
    let addrs = &addrs[..addrs.len().min(MAX_ADDRS)];
    bytes.push(addrs.len() as u8);
    for addr in addrs {
        match addr.ip() {
            IpAddr::V4(ip) => {
                bytes.push(4);
                bytes.extend_from_slice(&ip.octets());
            }
            IpAddr::V6(ip) => {
                bytes.push(6);
                bytes.extend_from_slice(&ip.octets());
            }
        }
        bytes.extend_from_slice(&addr.port().to_be_bytes());
    }
}

pub(crate) struct Reader<'a>(pub(crate) &'a [u8]);

impl Reader<'_> {
    pub(crate) fn take(&mut self, len: usize) -> Result<&[u8]> {
        ensure!(self.0.len() >= len, "truncated");
        let (head, rest) = self.0.split_at(len);
        self.0 = rest;
        Ok(head)
    }

    pub(crate) fn u8(&mut self) -> Result<u8> {
        Ok(self.take(1)?[0])
    }

    pub(crate) fn u16(&mut self) -> Result<u16> {
        Ok(u16::from_be_bytes(self.take(2)?.try_into()?))
    }

    pub(crate) fn u64(&mut self) -> Result<u64> {
        Ok(u64::from_be_bytes(self.take(8)?.try_into()?))
    }

    pub(crate) fn key(&mut self) -> Result<EndpointId> {
        let bytes: &[u8; 32] = self.take(32)?.try_into()?;
        EndpointId::from_bytes(bytes).context("invalid device key")
    }

    pub(crate) fn name(&mut self) -> Result<String> {
        let len = usize::from(self.u8()?);
        ensure!(len <= MAX_NAME, "name too long");
        Ok(std::str::from_utf8(self.take(len)?)
            .context("name is not UTF-8")?
            .to_string())
    }

    pub(crate) fn addrs(&mut self) -> Result<Vec<SocketAddr>> {
        let count = usize::from(self.u8()?);
        ensure!(count <= MAX_ADDRS, "too many addresses");
        (0..count)
            .map(|_| {
                let ip = match self.u8()? {
                    4 => IpAddr::V4(Ipv4Addr::from(<[u8; 4]>::try_from(self.take(4)?)?)),
                    6 => IpAddr::V6(Ipv6Addr::from(<[u8; 16]>::try_from(self.take(16)?)?)),
                    other => bail!("unknown address family {other}"),
                };
                Ok(SocketAddr::new(ip, self.u16()?))
            })
            .collect()
    }

    pub(crate) fn finish(&self) -> Result<()> {
        ensure!(self.0.is_empty(), "unexpected trailing bytes");
        Ok(())
    }
}
