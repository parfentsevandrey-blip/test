//! The member journal: who belongs to the network, as signed statements that
//! members keep and pass on to each other.
//!
//! Every device appends entries to its own feed, each signed with its key and
//! chained to the previous one: it added a device, it removed one, its name.
//! Members exchange the entries the other one lacks whenever they connect, so a
//! device paired with one member soon knows all of them, and a removal made on
//! any member reaches everyone.
//!
//! Seen from one device, the members are the devices it reaches over "added"
//! statements, starting from itself, minus the removed ones:
//!
//! - All members are equal: any member may add and remove devices.
//! - A removal is for good: the removed device needs a new key to come back.
//! - A removal says how far its author had seen the removed device's feed, and
//!   cuts the feed there. What the removed device signs later counts for
//!   nothing: it cannot strike back, and devices it adds later are not members.
//!   What it did before stays, including devices it added: remove those
//!   separately if they should go too.
//! - Two devices that remove each other, each before seeing the other's removal,
//!   are both out: when in doubt, the journal removes rather than keeps.

use std::{
    collections::{BTreeMap, BTreeSet},
    net::SocketAddr,
    time::{SystemTime, UNIX_EPOCH},
};

use anyhow::{Context, Result, bail, ensure};
use iroh::{EndpointId, SecretKey, Signature};

use crate::{
    members::{Member, Members},
    pair::member_name,
    wire::{Reader, put_addrs, put_name},
};

const VERSION: u8 = 1;
/// Signatures cover this prefix too, so they mean nothing anywhere else.
const SIGNING_CONTEXT: &[u8] = b"halo journal v1";
/// Rounds of working out which removals cut which feeds. Real journals settle in
/// two or three; one that does not settle loses every device ever removed in it.
const MAX_ROUNDS: usize = 16;
const HEADER: &str = "# Halo member journal: signed entries, one per line. Do not edit.\n";

const ADD: u8 = 1;
const REMOVE: u8 = 2;
const NAME: u8 = 3;

/// What an entry says.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Action {
    /// Adds a device to the network, or updates the name and addresses the
    /// author knows it by.
    Add {
        id: EndpointId,
        name: String,
        addrs: Vec<SocketAddr>,
    },
    /// Removes a device for good. `seen` is the last entry of its feed the author
    /// had seen; its later entries count for nothing.
    Remove { id: EndpointId, seen: u64 },
    /// The name the author goes by.
    Name { name: String },
}

/// One signed statement. Only exists with a valid signature.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Entry {
    author: EndpointId,
    seq: u64,
    /// Hash of the author's previous entry; zero for the first.
    prev: [u8; 32],
    time: u64,
    action: Action,
    signature: Signature,
}

impl Entry {
    /// The device that signed it.
    pub fn author(&self) -> EndpointId {
        self.author
    }

    /// Position in the author's feed, from 1.
    pub fn seq(&self) -> u64 {
        self.seq
    }

    /// When the author wrote it, in milliseconds since the Unix epoch. For display:
    /// nothing depends on clocks.
    pub fn time(&self) -> u64 {
        self.time
    }

    pub fn action(&self) -> &Action {
        &self.action
    }

    fn hash(&self) -> [u8; 32] {
        *blake3::hash(&self.to_bytes()).as_bytes()
    }

    pub fn to_bytes(&self) -> Vec<u8> {
        let mut bytes = self.body();
        bytes.extend_from_slice(&self.signature.to_bytes());
        bytes
    }

    /// Parses an entry and checks its signature.
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        Self::parse_bytes(bytes).context("invalid journal entry")
    }

    fn parse_bytes(bytes: &[u8]) -> Result<Self> {
        ensure!(
            bytes.len() > Signature::LENGTH,
            "journal entry is too short"
        );
        let (body, signature) = bytes.split_at(bytes.len() - Signature::LENGTH);
        let signature = Signature::from_bytes(signature.try_into().expect("split at the length"));
        let mut reader = Reader(body);
        ensure!(reader.u8()? == VERSION, "unknown journal entry version");
        let author = reader.key()?;
        let seq = reader.u64()?;
        ensure!(seq > 0, "journal entries count from 1");
        let prev = reader.take(32)?.try_into()?;
        let time = reader.u64()?;
        let action = match reader.u8()? {
            ADD => Action::Add {
                id: reader.key()?,
                name: reader.name()?,
                addrs: reader.addrs()?,
            },
            REMOVE => Action::Remove {
                id: reader.key()?,
                seen: reader.u64()?,
            },
            NAME => Action::Name {
                name: reader.name()?,
            },
            other => bail!("unknown journal action {other}"),
        };
        reader.finish()?;
        author
            .verify(&[SIGNING_CONTEXT, body].concat(), &signature)
            .context("bad signature on a journal entry")?;
        Ok(Self {
            author,
            seq,
            prev,
            time,
            action,
            signature,
        })
    }

    fn sign(key: &SecretKey, seq: u64, prev: [u8; 32], action: Action) -> Self {
        let time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_or(0, |time| time.as_millis() as u64);
        let mut entry = Self {
            author: key.public(),
            seq,
            prev,
            time,
            action,
            signature: Signature::from_bytes(&[0; Signature::LENGTH]),
        };
        entry.signature = key.sign(&[SIGNING_CONTEXT, &entry.body()].concat());
        entry
    }

    fn body(&self) -> Vec<u8> {
        let mut bytes = vec![VERSION];
        bytes.extend_from_slice(self.author.as_bytes());
        bytes.extend_from_slice(&self.seq.to_be_bytes());
        bytes.extend_from_slice(&self.prev);
        bytes.extend_from_slice(&self.time.to_be_bytes());
        match &self.action {
            Action::Add { id, name, addrs } => {
                bytes.push(ADD);
                bytes.extend_from_slice(id.as_bytes());
                put_name(&mut bytes, name);
                put_addrs(&mut bytes, addrs);
            }
            Action::Remove { id, seen } => {
                bytes.push(REMOVE);
                bytes.extend_from_slice(id.as_bytes());
                bytes.extend_from_slice(&seen.to_be_bytes());
            }
            Action::Name { name } => {
                bytes.push(NAME);
                put_name(&mut bytes, name);
            }
        }
        bytes
    }
}

/// How far each feed goes: author -> number of entries.
pub type Heads = BTreeMap<EndpointId, u64>;

/// Every feed this device knows, each complete from its first entry.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Journal {
    feeds: BTreeMap<EndpointId, Vec<Entry>>,
}

impl Journal {
    /// Reads the journal file: one entry per line, base64. Checks every signature
    /// and every chain.
    pub fn parse(text: &str) -> Result<Self> {
        let mut journal = Self::default();
        for (number, line) in text.lines().enumerate() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            data_encoding::BASE64URL_NOPAD
                .decode(line.as_bytes())
                .context("not base64")
                .and_then(|bytes| Entry::from_bytes(&bytes))
                .and_then(|entry| journal.insert(entry))
                .with_context(|| format!("journal line {}", number + 1))?;
        }
        Ok(journal)
    }

    pub fn render(&self) -> String {
        let mut text = String::from(HEADER);
        for entry in self.feeds.values().flatten() {
            text.push_str(&data_encoding::BASE64URL_NOPAD.encode(&entry.to_bytes()));
            text.push('\n');
        }
        text
    }

    /// Adds an entry from another device. Returns false if it was known already.
    ///
    /// Fails when the entry would leave a gap in its feed, or does not follow
    /// the entry before it: its author then signed two different histories.
    pub fn insert(&mut self, entry: Entry) -> Result<bool> {
        let feed = self.feeds.entry(entry.author).or_default();
        let next = feed.len() as u64 + 1;
        if entry.seq < next {
            ensure!(
                feed[(entry.seq - 1) as usize] == entry,
                "{} signed two different journal entries number {}",
                entry.author.fmt_short(),
                entry.seq
            );
            return Ok(false);
        }
        ensure!(
            entry.seq == next,
            "journal entry {} of {} arrived before entry {next}",
            entry.seq,
            entry.author.fmt_short()
        );
        let prev = feed.last().map_or([0; 32], Entry::hash);
        ensure!(
            entry.prev == prev,
            "{} signed two different journal histories",
            entry.author.fmt_short()
        );
        feed.push(entry);
        Ok(true)
    }

    /// Adds every entry of `other`. Returns how many were new.
    ///
    /// A feed that conflicts with the known one keeps the known part: the first
    /// history seen stays.
    pub fn merge(&mut self, other: &Journal) -> usize {
        let mut added = 0;
        for feed in other.feeds.values() {
            for entry in feed {
                match self.insert(entry.clone()) {
                    Ok(true) => added += 1,
                    Ok(false) => {}
                    Err(err) => {
                        // The rest of this feed builds on the conflicting entry.
                        tracing::warn!("journal entries skipped: {err:#}");
                        break;
                    }
                }
            }
        }
        added
    }

    /// Signs and appends an entry to this device's own feed.
    pub fn append(&mut self, key: &SecretKey, action: Action) -> Entry {
        let feed = self.feeds.entry(key.public()).or_default();
        let prev = feed.last().map_or([0; 32], Entry::hash);
        let entry = Entry::sign(key, feed.len() as u64 + 1, prev, action);
        feed.push(entry.clone());
        entry
    }

    /// Removes a device: everything it signs from now on counts for nothing.
    pub fn remove(&mut self, key: &SecretKey, id: EndpointId) -> Entry {
        let seen = self.feed(&id).len() as u64;
        self.append(key, Action::Remove { id, seen })
    }

    /// Sets the name this device goes by, unless it already does.
    pub fn set_name(&mut self, key: &SecretKey, name: &str) -> Option<Entry> {
        let current = self
            .feed(&key.public())
            .iter()
            .rev()
            .find_map(|entry| match &entry.action {
                Action::Name { name } => Some(name.as_str()),
                _ => None,
            });
        (current != Some(name)).then(|| self.append(key, Action::Name { name: name.into() }))
    }

    /// Drops the feeds of devices that no "added" statement leads to from `me`, so
    /// entries from strangers are kept nowhere.
    pub fn retain_reachable(&mut self, me: EndpointId) {
        let reached = self.reach(me, &BTreeMap::new());
        self.feeds.retain(|author, _| reached.contains(author));
    }

    pub fn heads(&self) -> Heads {
        self.feeds
            .iter()
            .map(|(author, feed)| (*author, feed.len() as u64))
            .collect()
    }

    /// The entries a device with `theirs` lacks, in an order it can insert them.
    pub fn missing(&self, theirs: &Heads) -> Vec<Entry> {
        self.feeds
            .iter()
            .flat_map(|(author, feed)| {
                let known = theirs.get(author).copied().unwrap_or(0);
                feed.iter().skip(known as usize).cloned()
            })
            .collect()
    }

    pub fn len(&self) -> usize {
        self.feeds.values().map(Vec::len).sum()
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// Membership as `me` sees it.
    pub fn view(&self, me: EndpointId) -> View {
        // Which removals stand decides where feeds are cut, and the cuts decide
        // whose removals count: devices added after a cut do not. Repeat until
        // nothing changes.
        let mut cuts = BTreeMap::new();
        let mut ever_removed = BTreeSet::new();
        let mut settled = None;
        for _ in 0..MAX_ROUNDS {
            let known = self.reach(me, &cuts);
            let removals: Vec<Removal> = known
                .iter()
                .flat_map(|author| self.feed(author))
                .filter_map(|entry| match entry.action {
                    Action::Remove { id, seen } => Some(Removal {
                        by: entry.author,
                        at: entry.seq,
                        id,
                        seen,
                    }),
                    _ => None,
                })
                .collect();
            let mut next: BTreeMap<EndpointId, u64> = BTreeMap::new();
            for removal in standing(&removals) {
                let cut = next.entry(removal.id).or_insert(removal.seen);
                *cut = (*cut).min(removal.seen);
            }
            ever_removed.extend(next.keys().copied());
            if next == cuts {
                settled = Some(known);
                break;
            }
            cuts = next;
        }
        let (known, removed) = match settled {
            Some(known) => (known, cuts.keys().copied().collect()),
            None => {
                tracing::warn!("the member journal does not settle; every removal stands");
                (self.reach(me, &cuts), ever_removed)
            }
        };
        let mut view = View {
            removed,
            ..View::default()
        };
        if view.removed.contains(&me) {
            return view;
        }
        for id in known
            .iter()
            .filter(|id| **id != me && !view.removed.contains(*id))
        {
            let added_by: Vec<EndpointId> = known
                .iter()
                .filter(|author| self.added(author, id, &cuts).is_some())
                .copied()
                .collect();
            let base = member_name(&self.name_of(me, id, &added_by, &cuts));
            let member = Member {
                id: *id,
                name: view.members.free_name(&base, id),
                addrs: self.addrs_of(id, &added_by, &cuts),
            };
            view.members
                .upsert(member)
                .expect("free_name gives a valid, unique name");
            view.added_by.insert(*id, added_by);
        }
        view
    }

    fn feed(&self, author: &EndpointId) -> &[Entry] {
        self.feeds.get(author).map_or(&[], Vec::as_slice)
    }

    /// The entries of `author` that count: all of them, or up to its cut.
    fn valid<'a>(&'a self, author: &EndpointId, cuts: &BTreeMap<EndpointId, u64>) -> &'a [Entry] {
        let feed = self.feed(author);
        let end = cuts
            .get(author)
            .map_or(feed.len(), |&cut| feed.len().min(cut as usize));
        &feed[..end]
    }

    /// The devices reachable from `me` over "added" statements that count.
    fn reach(&self, me: EndpointId, cuts: &BTreeMap<EndpointId, u64>) -> BTreeSet<EndpointId> {
        let mut reached = BTreeSet::from([me]);
        let mut queue = vec![me];
        while let Some(author) = queue.pop() {
            for entry in self.valid(&author, cuts) {
                if let Action::Add { id, .. } = &entry.action
                    && reached.insert(*id)
                {
                    queue.push(*id);
                }
            }
        }
        reached
    }

    /// The latest name and addresses `author` gave `id`, if it added it.
    fn added(
        &self,
        author: &EndpointId,
        id: &EndpointId,
        cuts: &BTreeMap<EndpointId, u64>,
    ) -> Option<(&str, &[SocketAddr])> {
        self.valid(author, cuts)
            .iter()
            .rev()
            .find_map(|entry| match &entry.action {
                Action::Add {
                    id: added,
                    name,
                    addrs,
                } if added == id => Some((name.as_str(), addrs.as_slice())),
                _ => None,
            })
    }

    /// What to call `id`: what this device called it, else its own name, else
    /// what another device called it.
    fn name_of(
        &self,
        me: EndpointId,
        id: &EndpointId,
        added_by: &[EndpointId],
        cuts: &BTreeMap<EndpointId, u64>,
    ) -> String {
        let own = || {
            self.valid(id, cuts)
                .iter()
                .rev()
                .find_map(|entry| match &entry.action {
                    Action::Name { name } => Some(name.as_str()),
                    _ => None,
                })
        };
        self.added(&me, id, cuts)
            .map(|(name, _)| name)
            .filter(|name| !name.is_empty())
            .or_else(own)
            .or_else(|| {
                added_by
                    .iter()
                    .filter_map(|author| self.added(author, id, cuts))
                    .map(|(name, _)| name)
                    .find(|name| !name.is_empty())
            })
            .unwrap_or_default()
            .to_string()
    }

    /// Every address the devices that added `id` know it at.
    fn addrs_of(
        &self,
        id: &EndpointId,
        added_by: &[EndpointId],
        cuts: &BTreeMap<EndpointId, u64>,
    ) -> Vec<SocketAddr> {
        let mut addrs = Vec::new();
        for author in added_by {
            for addr in self
                .added(author, id, cuts)
                .map_or(&[][..], |(_, addrs)| addrs)
            {
                if !addrs.contains(addr) {
                    addrs.push(*addr);
                }
            }
        }
        addrs
    }
}

/// Membership as one device sees it.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct View {
    /// The other member devices, under names unique in this view.
    pub members: Members,
    /// Who added each member.
    pub added_by: BTreeMap<EndpointId, Vec<EndpointId>>,
    /// Devices removed from the network, possibly this one.
    pub removed: BTreeSet<EndpointId>,
}

impl View {
    /// A member by name or id.
    pub fn find(&self, key: &str) -> Option<&Member> {
        self.members
            .0
            .iter()
            .find(|member| member.name == key || member.id.to_string() == key)
    }
}

/// A removal statement: `by` removed `id` in its entry number `at`, having seen
/// `id`'s feed up to entry `seen`.
#[derive(Debug, Clone, Copy)]
struct Removal {
    by: EndpointId,
    at: u64,
    id: EndpointId,
    seen: u64,
}

impl Removal {
    /// Whether this removal voids `other`: it removed `other`'s author before
    /// having seen `other`.
    fn voids(&self, other: &Removal) -> bool {
        self.id == other.by && other.at > self.seen
    }
}

/// The removals that stand.
///
/// A removal is void when a standing removal of its author came first, as far
/// as that one's author knew. Removals that void each other in a circle (two
/// devices removing each other at the same time) all stand: when in doubt,
/// remove.
fn standing(removals: &[Removal]) -> impl Iterator<Item = &Removal> {
    #[derive(Clone, Copy, PartialEq)]
    enum Label {
        Stands,
        Void,
        Undecided,
    }
    let mut labels = vec![Label::Undecided; removals.len()];
    loop {
        let mut changed = false;
        for i in 0..removals.len() {
            if labels[i] != Label::Undecided {
                continue;
            }
            let voiders = || (0..removals.len()).filter(|&j| removals[j].voids(&removals[i]));
            labels[i] = if voiders().any(|j| labels[j] == Label::Stands) {
                Label::Void
            } else if voiders().all(|j| labels[j] == Label::Void) {
                Label::Stands
            } else {
                continue;
            };
            changed = true;
        }
        if !changed {
            break;
        }
    }
    removals
        .iter()
        .zip(labels)
        .filter(|(_, label)| *label != Label::Void)
        .map(|(removal, _)| removal)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn key(seed: u8) -> SecretKey {
        SecretKey::from_bytes(&[seed; 32])
    }

    fn add(journal: &mut Journal, by: &SecretKey, who: &SecretKey, name: &str) {
        journal.append(
            by,
            Action::Add {
                id: who.public(),
                name: name.into(),
                addrs: vec![],
            },
        );
    }

    /// Two devices pair: each adds the other.
    fn pair(journal: &mut Journal, a: &SecretKey, b: &SecretKey) {
        add(journal, a, b, "");
        add(journal, b, a, "");
    }

    fn ids(view: &View) -> BTreeSet<EndpointId> {
        view.members.0.iter().map(|m| m.id).collect()
    }

    fn set<const N: usize>(keys: [&SecretKey; N]) -> BTreeSet<EndpointId> {
        keys.iter().map(|key| key.public()).collect()
    }

    #[test]
    fn entries_roundtrip_and_reject_tampering() {
        let mut journal = Journal::default();
        journal.append(
            &key(1),
            Action::Name {
                name: "laptop".into(),
            },
        );
        let entry = journal.append(
            &key(1),
            Action::Add {
                id: key(2).public(),
                name: "phone".into(),
                addrs: vec![
                    "192.168.1.20:7777".parse().unwrap(),
                    "[fe80::1]:7777".parse().unwrap(),
                ],
            },
        );
        let bytes = entry.to_bytes();
        assert_eq!(Entry::from_bytes(&bytes).unwrap(), entry);
        for i in [0, 1, 40, 50, bytes.len() / 2, bytes.len() - 1] {
            let mut tampered = bytes.clone();
            tampered[i] ^= 1;
            assert!(Entry::from_bytes(&tampered).is_err(), "byte {i}");
        }
        assert!(Entry::from_bytes(&bytes[..bytes.len() - 1]).is_err());
        assert_eq!(Journal::parse(&journal.render()).unwrap(), journal);
    }

    #[test]
    fn feeds_have_no_gaps_and_no_second_history() {
        let mut theirs = Journal::default();
        let first = theirs.append(&key(1), Action::Name { name: "a".into() });
        let second = theirs.append(&key(1), Action::Name { name: "b".into() });

        let mut mine = Journal::default();
        assert!(mine.insert(second.clone()).is_err(), "gap");
        assert!(mine.insert(first.clone()).unwrap());
        assert!(!mine.insert(first).unwrap(), "known");
        assert!(mine.insert(second).unwrap());

        // The same author signing another history: a different entry at a known
        // place, or a next entry that does not follow the known one.
        let mut forked = Journal::default();
        forked.append(&key(1), Action::Name { name: "x".into() });
        forked.append(&key(1), Action::Name { name: "y".into() });
        let third = forked.append(&key(1), Action::Name { name: "z".into() });
        let rewrite = forked.missing(&Heads::new()).remove(0);
        assert!(mine.insert(rewrite).is_err());
        assert!(mine.insert(third).is_err());
        assert_eq!(mine.len(), 2);
    }

    #[test]
    fn sync_sends_what_the_other_side_lacks() {
        let (a, b, c) = (key(1), key(2), key(3));
        let mut left = Journal::default();
        pair(&mut left, &a, &b);
        let mut right = left.clone();
        add(&mut left, &a, &c, "c");
        pair(&mut right, &b, &c);

        for entry in left.missing(&right.heads()) {
            right.insert(entry).unwrap();
        }
        for entry in right.missing(&left.heads()) {
            left.insert(entry).unwrap();
        }
        assert_eq!(left, right);
        assert!(left.missing(&right.heads()).is_empty());
    }

    /// Pair once: a device paired with one member knows the others.
    #[test]
    fn membership_is_transitive() {
        let (a, b, c, d) = (key(1), key(2), key(3), key(4));
        let mut journal = Journal::default();
        pair(&mut journal, &a, &b);
        pair(&mut journal, &a, &c);
        assert_eq!(ids(&journal.view(b.public())), set([&a, &c]));
        assert_eq!(ids(&journal.view(c.public())), set([&a, &b]));
        assert_eq!(
            journal.view(b.public()).added_by[&c.public()],
            vec![a.public()]
        );
        // A device nobody here added sees nothing.
        assert!(journal.view(d.public()).members.0.is_empty());
    }

    #[test]
    fn removal_reaches_everyone_and_is_final() {
        let (a, b, c) = (key(1), key(2), key(3));
        let mut journal = Journal::default();
        pair(&mut journal, &a, &b);
        pair(&mut journal, &a, &c);
        journal.remove(&a, c.public());
        assert_eq!(ids(&journal.view(b.public())), set([&a]));
        assert_eq!(journal.view(b.public()).removed, set([&c]));
        assert!(
            journal.view(c.public()).members.0.is_empty(),
            "c knows it is out"
        );
        // Adding it again does not bring it back.
        add(&mut journal, &b, &c, "c");
        assert_eq!(ids(&journal.view(b.public())), set([&a]));
    }

    /// Removing the device that introduced the others keeps them together.
    #[test]
    fn removing_the_first_device_keeps_the_rest() {
        let (mac, windows, phone) = (key(1), key(2), key(3));
        let mut journal = Journal::default();
        pair(&mut journal, &mac, &windows);
        pair(&mut journal, &mac, &phone);
        journal.remove(&phone, mac.public());
        assert_eq!(ids(&journal.view(windows.public())), set([&phone]));
        assert_eq!(ids(&journal.view(phone.public())), set([&windows]));
    }

    /// A stolen device cannot strike back after its removal.
    #[test]
    fn a_removed_device_cannot_remove_others() {
        let (a, b, c) = (key(1), key(2), key(3));
        let mut journal = Journal::default();
        pair(&mut journal, &a, &b);
        pair(&mut journal, &a, &c);
        journal.remove(&a, c.public());
        // c signs removals after a removed it; a had not seen them.
        journal.remove(&c, a.public());
        journal.remove(&c, b.public());
        let view = journal.view(b.public());
        assert_eq!(ids(&view), set([&a]));
        assert_eq!(view.removed, set([&c]));
    }

    /// Nor through a new key it adds after its removal.
    #[test]
    fn devices_added_after_a_removal_do_not_count() {
        let (a, b, c, e) = (key(1), key(2), key(3), key(5));
        let mut journal = Journal::default();
        pair(&mut journal, &a, &b);
        pair(&mut journal, &a, &c);
        journal.remove(&a, c.public());
        add(&mut journal, &c, &e, "e");
        journal.remove(&e, a.public());
        journal.remove(&e, b.public());
        let view = journal.view(b.public());
        assert_eq!(ids(&view), set([&a]));
        assert_eq!(view.removed, set([&c]));
    }

    /// What a device did before its removal stays.
    #[test]
    fn devices_added_before_a_removal_stay() {
        let (a, b, c, d) = (key(1), key(2), key(3), key(4));
        let mut journal = Journal::default();
        pair(&mut journal, &a, &b);
        pair(&mut journal, &b, &c);
        pair(&mut journal, &c, &d);
        journal.remove(&a, c.public());
        let view = journal.view(a.public());
        assert_eq!(ids(&view), set([&b, &d]));
        assert_eq!(view.added_by[&d.public()], vec![c.public()]);
    }

    /// Removals made before the other side's removal arrived both stand.
    #[test]
    fn mutual_removal_removes_both() {
        let (a, b, c) = (key(1), key(2), key(3));
        let mut journal = Journal::default();
        pair(&mut journal, &a, &b);
        pair(&mut journal, &a, &c);
        let mut other_side = journal.clone();
        journal.remove(&a, c.public());
        other_side.remove(&c, a.public());
        journal.merge(&other_side);
        let view = journal.view(b.public());
        assert_eq!(view.removed, set([&a, &c]));
        assert!(view.members.0.is_empty());
    }

    /// A removal its author made knowing it had been removed itself is void.
    #[test]
    fn removal_after_being_removed_is_void() {
        let (a, b, c) = (key(1), key(2), key(3));
        let mut journal = Journal::default();
        pair(&mut journal, &a, &b);
        pair(&mut journal, &a, &c);
        journal.remove(&c, a.public());
        journal.remove(&a, c.public());
        let view = journal.view(b.public());
        assert_eq!(view.removed, set([&a]));
        assert_eq!(ids(&view), set([&c]));
    }

    #[test]
    fn leaving_removes_yourself() {
        let (a, b) = (key(1), key(2));
        let mut journal = Journal::default();
        pair(&mut journal, &a, &b);
        journal.remove(&b, b.public());
        assert!(journal.view(a.public()).members.0.is_empty());
        assert!(journal.view(b.public()).removed.contains(&b.public()));
    }

    #[test]
    fn names_prefer_this_devices_choice_and_stay_unique() {
        let (a, b, c, d) = (key(1), key(2), key(3), key(4));
        let mut journal = Journal::default();
        journal.set_name(&b, "Pixel 9");
        assert!(journal.set_name(&b, "Pixel 9").is_none(), "unchanged");
        journal.set_name(&c, "Pixel 9");
        add(&mut journal, &a, &b, "phone");
        add(&mut journal, &a, &c, "");
        add(&mut journal, &b, &a, "laptop");
        add(&mut journal, &b, &d, "Work PC");
        let view = journal.view(a.public());
        let name = |key: &SecretKey| {
            view.members
                .0
                .iter()
                .find(|m| m.id == key.public())
                .unwrap()
                .name
                .clone()
        };
        assert_eq!(name(&b), "phone", "a's own choice");
        assert_eq!(name(&c), "pixel-9", "its own name, made valid");
        assert_eq!(name(&d), "work-pc", "what b called it");
        // d paired with b, which goes by the same name as c.
        add(&mut journal, &d, &b, "");
        let view = journal.view(d.public());
        assert!(view.find("pixel-9").is_some() && view.find("pixel-9-2").is_some());
        assert!(view.find(&a.public().to_string()).is_some());
    }

    #[test]
    fn addresses_come_from_every_device_that_added_it() {
        let (a, b, c) = (key(1), key(2), key(3));
        let mut journal = Journal::default();
        pair(&mut journal, &a, &b);
        let at = |addr: &str| vec![addr.parse::<SocketAddr>().unwrap()];
        let mut add_at = |by: &SecretKey, addr: &str| {
            journal.append(
                by,
                Action::Add {
                    id: c.public(),
                    name: "c".into(),
                    addrs: at(addr),
                },
            );
        };
        add_at(&a, "10.0.0.3:7777");
        add_at(&b, "10.0.0.3:7777");
        add_at(&a, "10.0.0.4:7777");
        let view = journal.view(b.public());
        let addrs: BTreeSet<SocketAddr> = view.find("c").unwrap().addrs.iter().copied().collect();
        assert_eq!(
            addrs,
            BTreeSet::from_iter([at("10.0.0.4:7777"), at("10.0.0.3:7777")].concat()),
            "the latest from a, and b's"
        );
    }
}
