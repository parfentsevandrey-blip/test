//! The member list: devices this device talks to, kept in a small text file.
//!
//! One device per line: `<id> <name> [addr,addr...]`. Lines starting with `#` are comments.

use std::{net::SocketAddr, str::FromStr};

use anyhow::{Context, Result, bail, ensure};
use iroh::EndpointId;

use crate::node::PeerConfig;

const HEADER: &str = "# Halo members: <id> <name> [addr,addr...]\n";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Member {
    pub id: EndpointId,
    pub name: String,
    pub addrs: Vec<SocketAddr>,
}

impl From<Member> for PeerConfig {
    fn from(member: Member) -> Self {
        Self {
            id: member.id,
            addrs: member.addrs,
        }
    }
}

#[derive(Debug, Default, PartialEq, Eq)]
pub struct Members(pub Vec<Member>);

impl Members {
    pub fn parse(text: &str) -> Result<Self> {
        let mut members = Self::default();
        for (number, line) in text.lines().enumerate() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            let member = parse_line(line).with_context(|| format!("line {}", number + 1))?;
            members
                .upsert(member)
                .with_context(|| format!("line {}", number + 1))?;
        }
        Ok(members)
    }

    pub fn render(&self) -> String {
        let mut text = String::from(HEADER);
        for member in &self.0 {
            text.push_str(&format!("{} {}", member.id, member.name));
            if !member.addrs.is_empty() {
                let addrs: Vec<String> = member.addrs.iter().map(ToString::to_string).collect();
                text.push_str(&format!(" {}", addrs.join(",")));
            }
            text.push('\n');
        }
        text
    }

    /// Adds a member, or updates the one with the same id.
    pub fn upsert(&mut self, member: Member) -> Result<()> {
        validate_name(&member.name)?;
        if let Some(other) = self
            .0
            .iter()
            .find(|other| other.name == member.name && other.id != member.id)
        {
            bail!(
                "the name {} is already taken by {}",
                member.name,
                other.id.fmt_short()
            );
        }
        match self.0.iter_mut().find(|other| other.id == member.id) {
            Some(existing) => *existing = member,
            None => self.0.push(member),
        }
        Ok(())
    }

    /// A name based on `base` that no other device has: `base`, `base-2`, `base-3`…
    pub fn free_name(&self, base: &str, id: &EndpointId) -> String {
        let taken = |name: &str| self.0.iter().any(|m| m.name == name && m.id != *id);
        if !taken(base) {
            return base.to_string();
        }
        // Leave room for the suffix within the 32-character limit.
        let base = base[..base.len().min(28)].trim_end_matches('-');
        (2..)
            .map(|n| format!("{base}-{n}"))
            .find(|name| !taken(name))
            .expect("some suffix is free")
    }

    /// Removes a member by name or id.
    pub fn remove(&mut self, key: &str) -> Option<Member> {
        let position = self
            .0
            .iter()
            .position(|member| member.name == key || member.id.to_string() == key)?;
        Some(self.0.remove(position))
    }
}

fn parse_line(line: &str) -> Result<Member> {
    let mut fields = line.split_whitespace();
    let id = fields.next().context("missing device id")?;
    let id = EndpointId::from_str(id).context("invalid device id")?;
    let name = fields.next().context("missing name")?.to_string();
    let addrs = match fields.next() {
        Some(addrs) => parse_addrs(addrs)?,
        None => Vec::new(),
    };
    ensure!(
        fields.next().is_none(),
        "unexpected text after the addresses"
    );
    Ok(Member { id, name, addrs })
}

/// Parses a comma-separated list of `ip:port` addresses.
pub fn parse_addrs(text: &str) -> Result<Vec<SocketAddr>> {
    text.split(',')
        .map(|addr| SocketAddr::from_str(addr).with_context(|| format!("invalid address {addr}")))
        .collect()
}

/// Names become host names later (`laptop.internal`), so they follow DNS label rules.
pub fn validate_name(name: &str) -> Result<()> {
    let valid = !name.is_empty()
        && name.len() <= 32
        && !name.starts_with('-')
        && !name.ends_with('-')
        && name
            .bytes()
            .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-');
    ensure!(
        valid,
        "invalid name {name:?}: use 1-32 lowercase latin letters, digits and dashes"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use iroh::SecretKey;

    use super::*;

    fn id(seed: u8) -> EndpointId {
        SecretKey::from_bytes(&[seed; 32]).public()
    }

    fn member(seed: u8, name: &str) -> Member {
        Member {
            id: id(seed),
            name: name.to_string(),
            addrs: Vec::new(),
        }
    }

    #[test]
    fn render_and_parse_roundtrip() {
        let mut members = Members::default();
        members.upsert(member(1, "macbook")).unwrap();
        let mut windows = member(2, "windows");
        windows.addrs = parse_addrs("192.168.1.20:7777,[fe80::1]:7777").unwrap();
        members.upsert(windows).unwrap();
        assert_eq!(Members::parse(&members.render()).unwrap(), members);
    }

    #[test]
    fn comments_and_blank_lines_are_ignored() {
        let text = format!("# hello\n\n   {} phone  \n", id(3));
        assert_eq!(Members::parse(&text).unwrap().0, vec![member(3, "phone")]);
    }

    #[test]
    fn invalid_lines_report_their_number() {
        let err = Members::parse(&format!("{} ok\nnot-an-id phone\n", id(1))).unwrap_err();
        assert!(format!("{err:#}").starts_with("line 2"), "{err:#}");
        assert!(Members::parse(&id(1).to_string()).is_err());
        assert!(Members::parse(&format!("{} phone 1.2.3.4", id(1))).is_err());
        assert!(Members::parse(&format!("{} phone 1.2.3.4:5 extra", id(1))).is_err());
    }

    #[test]
    fn upsert_updates_by_id_and_keeps_names_unique() {
        let mut members = Members::default();
        members.upsert(member(1, "laptop")).unwrap();
        members.upsert(member(1, "macbook")).unwrap();
        assert_eq!(members.0, vec![member(1, "macbook")]);
        assert!(members.upsert(member(2, "macbook")).is_err());
    }

    #[test]
    fn remove_by_name_or_id() {
        let mut members = Members::default();
        members.upsert(member(1, "macbook")).unwrap();
        members.upsert(member(2, "windows")).unwrap();
        assert_eq!(members.remove("macbook"), Some(member(1, "macbook")));
        assert_eq!(
            members.remove(&id(2).to_string()),
            Some(member(2, "windows"))
        );
        assert_eq!(members.remove("nobody"), None);
    }

    #[test]
    fn free_names_get_a_suffix() {
        let mut members = Members::default();
        assert_eq!(members.free_name("phone", &id(1)), "phone");
        members.upsert(member(1, "phone")).unwrap();
        // Same device keeps its name; another one gets a suffix.
        assert_eq!(members.free_name("phone", &id(1)), "phone");
        assert_eq!(members.free_name("phone", &id(2)), "phone-2");
        members.upsert(member(2, "phone-2")).unwrap();
        assert_eq!(members.free_name("phone", &id(3)), "phone-3");
        let long = "a".repeat(32);
        members.upsert(member(4, &long)).unwrap();
        let name = members.free_name(&long, &id(5));
        assert!(validate_name(&name).is_ok(), "{name}");
    }

    #[test]
    fn names_follow_dns_label_rules() {
        for name in ["macbook", "win-11", "a", "phone2"] {
            assert!(validate_name(name).is_ok(), "{name}");
        }
        for name in ["", "MacBook", "-x", "x-", "мак", "a.b", &"a".repeat(33)] {
            assert!(validate_name(name).is_err(), "{name}");
        }
    }
}
