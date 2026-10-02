//! Where this device keeps its key, member journal and the presences of members.
//!
//! The key belongs to the device, not to a user, so it lives in a system-wide
//! directory that only root / administrators can read.

use std::{
    fs,
    io::{self, Write},
    path::{Path, PathBuf},
    str::FromStr,
};

use anyhow::{Context, Result};
use iroh::SecretKey;

use crate::{
    journal::{Action, Journal},
    members::Members,
    presence::Presences,
};

const KEY_FILE: &str = "secret.key";
const JOURNAL_FILE: &str = "journal";
/// Held while the journal file changes: the node and CLI commands both write it.
const JOURNAL_LOCK: &str = "journal.lock";
/// The member list before the journal, moved into the journal on first use.
const MEMBERS_FILE: &str = "members";
const PRESENCE_FILE: &str = "presence";
const PRESENCE_LOCK: &str = "presence.lock";
/// What the running node last said about itself, for `halo status`.
const STATUS_FILE: &str = "status";
/// The node's log when it runs as a service without a system journal.
const LOG_FILE: &str = "halo.log";

#[derive(Debug, Clone)]
pub struct State {
    dir: PathBuf,
}

impl State {
    pub fn new(dir: Option<PathBuf>) -> Result<Self> {
        let dir = match dir {
            Some(dir) => dir,
            None => default_dir()?,
        };
        Ok(Self { dir })
    }

    /// Loads the device key, creating it on first use.
    pub fn key(&self) -> Result<SecretKey> {
        let path = self.dir.join(KEY_FILE);
        match fs::read_to_string(&path) {
            Ok(text) => SecretKey::from_str(text.trim())
                .with_context(|| format!("invalid key in {}", path.display())),
            Err(err) if err.kind() == io::ErrorKind::NotFound => {
                self.ensure_dir()?;
                let key = SecretKey::generate();
                let hex: String = key.to_bytes().iter().map(|b| format!("{b:02x}")).collect();
                write_new(&path, &hex).map_err(|err| explain(err, &path))?;
                Ok(key)
            }
            Err(err) => Err(explain(err, &path)),
        }
    }

    /// The directory itself.
    pub fn dir(&self) -> &Path {
        &self.dir
    }

    /// Where a node running as a service writes its log, where the system
    /// keeps no journal of its own (macOS, Windows).
    pub fn log_path(&self) -> PathBuf {
        self.dir.join(LOG_FILE)
    }

    /// Replaces the running node's status report.
    pub fn write_status(&self, text: &str) -> Result<()> {
        self.ensure_dir()?;
        self.replace(STATUS_FILE, text)
    }

    /// The running node's status report and when it was written, if there is one.
    pub fn status(&self) -> Result<Option<(String, std::time::SystemTime)>> {
        let path = self.dir.join(STATUS_FILE);
        let read = fs::read_to_string(&path).and_then(|text| {
            let modified = fs::metadata(&path)?.modified()?;
            Ok((text, modified))
        });
        match read {
            Ok(status) => Ok(Some(status)),
            Err(err) if err.kind() == io::ErrorKind::NotFound => Ok(None),
            Err(err) => Err(explain(err, &path)),
        }
    }

    /// Removes the status report of a node that stopped.
    pub fn remove_status(&self) {
        let _ = fs::remove_file(self.dir.join(STATUS_FILE));
    }

    /// The device key, if it was created already.
    pub fn existing_key(&self) -> Result<Option<SecretKey>> {
        let path = self.dir.join(KEY_FILE);
        match fs::read_to_string(&path) {
            Ok(text) => SecretKey::from_str(text.trim())
                .map(Some)
                .with_context(|| format!("invalid key in {}", path.display())),
            Err(err) if err.kind() == io::ErrorKind::NotFound => Ok(None),
            Err(err) => Err(explain(err, &path)),
        }
    }

    /// Where the member journal lives.
    pub fn journal_path(&self) -> PathBuf {
        self.dir.join(JOURNAL_FILE)
    }

    /// The member journal, as last saved.
    pub fn journal(&self) -> Result<Journal> {
        let path = self.dir.join(JOURNAL_FILE);
        match fs::read_to_string(&path) {
            Ok(text) => Journal::parse(&text).with_context(|| format!("in {}", path.display())),
            // Creates it, moving an old member list in.
            Err(err) if err.kind() == io::ErrorKind::NotFound => {
                self.update_journal(|journal| Ok(journal.clone()))
            }
            Err(err) => Err(explain(err, &path)),
        }
    }

    /// Changes the journal: reads the latest file, applies `change` and saves the
    /// result, all under a lock, so concurrent writers keep each other's entries.
    pub fn update_journal<T>(&self, change: impl FnOnce(&mut Journal) -> Result<T>) -> Result<T> {
        self.locked(JOURNAL_LOCK, || {
            let path = self.dir.join(JOURNAL_FILE);
            let (mut journal, exists) = match fs::read_to_string(&path) {
                Ok(text) => (
                    Journal::parse(&text).with_context(|| format!("in {}", path.display()))?,
                    true,
                ),
                Err(err) if err.kind() == io::ErrorKind::NotFound => (self.migrate()?, false),
                Err(err) => return Err(explain(err, &path)),
            };
            let before = journal.clone();
            let result = change(&mut journal)?;
            if !exists || journal != before {
                self.replace(JOURNAL_FILE, &journal.render())?;
            }
            if !exists {
                // The journal holds the old member list now.
                let members = self.dir.join(MEMBERS_FILE);
                let _ = fs::rename(&members, self.dir.join(format!("{MEMBERS_FILE}.old")));
            }
            Ok(result)
        })
    }

    /// The latest known presences of members, this device's own included.
    pub fn presences(&self) -> Result<Presences> {
        let path = self.dir.join(PRESENCE_FILE);
        match fs::read_to_string(&path) {
            Ok(text) => Ok(Presences::parse(&text)),
            Err(err) if err.kind() == io::ErrorKind::NotFound => Ok(Presences::default()),
            Err(err) => Err(explain(err, &path)),
        }
    }

    /// Changes the presences under a lock, like [`State::update_journal`].
    pub fn update_presences<T>(
        &self,
        change: impl FnOnce(&mut Presences) -> Result<T>,
    ) -> Result<T> {
        self.locked(PRESENCE_LOCK, || {
            let mut presences = self.presences()?;
            let before = presences.clone();
            let result = change(&mut presences)?;
            if presences != before {
                self.replace(PRESENCE_FILE, &presences.render())?;
            }
            Ok(result)
        })
    }

    /// Runs `body` holding the lock file `lock`: other processes wait.
    fn locked<T>(&self, lock: &str, body: impl FnOnce() -> Result<T>) -> Result<T> {
        self.ensure_dir()?;
        let path = self.dir.join(lock);
        let lock = fs::OpenOptions::new()
            .create(true)
            .truncate(false)
            .write(true)
            .open(&path)
            .map_err(|err| explain(err, &path))?;
        lock.lock().map_err(|err| explain(err, &path))?;
        body()
    }

    /// Replaces `file` at once: readers see the old or the new text, never half.
    fn replace(&self, file: &str, text: &str) -> Result<()> {
        let path = self.dir.join(file);
        let tmp = self.dir.join(format!("{file}.tmp"));
        let _ = fs::remove_file(&tmp);
        write_new(&tmp, text).map_err(|err| explain(err, &tmp))?;
        fs::rename(&tmp, &path).map_err(|err| explain(err, &path))
    }

    /// A journal holding the member list from before the journal, if any.
    fn migrate(&self) -> Result<Journal> {
        let mut journal = Journal::default();
        let members = self.members()?;
        if !members.0.is_empty() {
            let key = self.key()?;
            for member in members.0 {
                journal.append(
                    &key,
                    Action::Add {
                        id: member.id,
                        name: member.name,
                        addrs: member.addrs,
                    },
                );
            }
        }
        Ok(journal)
    }

    /// The member list from before the journal.
    fn members(&self) -> Result<Members> {
        let path = self.dir.join(MEMBERS_FILE);
        match fs::read_to_string(&path) {
            Ok(text) => Members::parse(&text).with_context(|| format!("in {}", path.display())),
            Err(err) if err.kind() == io::ErrorKind::NotFound => Ok(Members::default()),
            Err(err) => Err(explain(err, &path)),
        }
    }

    fn ensure_dir(&self) -> Result<()> {
        if self.dir.is_dir() {
            return Ok(());
        }
        create_private_dir(&self.dir).map_err(|err| explain(err, &self.dir))
    }
}

fn default_dir() -> Result<PathBuf> {
    if cfg!(windows) {
        let base = std::env::var_os("ProgramData")
            .context("%ProgramData% is not set; pass --state-dir")?;
        Ok(PathBuf::from(base).join("Halo"))
    } else if cfg!(target_os = "macos") {
        Ok(PathBuf::from("/Library/Application Support/Halo"))
    } else {
        Ok(PathBuf::from("/var/lib/halo"))
    }
}

fn explain(err: io::Error, path: &Path) -> anyhow::Error {
    let hint = if err.kind() == io::ErrorKind::PermissionDenied {
        if cfg!(windows) {
            " (run halo in a terminal opened as administrator)"
        } else {
            " (run halo with sudo)"
        }
    } else {
        ""
    };
    anyhow::Error::new(err).context(format!("cannot access {}{hint}", path.display()))
}

/// Creates a file only root / administrators can read. Fails if it exists.
fn write_new(path: &Path, contents: &str) -> io::Result<()> {
    let mut options = fs::OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    std::os::unix::fs::OpenOptionsExt::mode(&mut options, 0o600);
    options.open(path)?.write_all(contents.as_bytes())
}

#[cfg(unix)]
fn create_private_dir(dir: &Path) -> io::Result<()> {
    use std::os::unix::fs::DirBuilderExt;
    fs::DirBuilder::new()
        .recursive(true)
        .mode(0o700)
        .create(dir)
}

/// `%ProgramData%` is readable by every user, so the directory gets its own ACL:
/// full control for Administrators and SYSTEM only. Well-known SIDs, because group
/// names are localized ("Администраторы").
#[cfg(windows)]
fn create_private_dir(dir: &Path) -> io::Result<()> {
    fs::create_dir_all(dir)?;
    let status = std::process::Command::new("icacls")
        .arg(dir)
        .args([
            "/inheritance:r",
            "/grant:r",
            "*S-1-5-32-544:(OI)(CI)F",
            "*S-1-5-18:(OI)(CI)F",
        ])
        .stdout(std::process::Stdio::null())
        .status()?;
    if status.success() {
        Ok(())
    } else {
        let _ = fs::remove_dir(dir);
        Err(io::Error::other(format!(
            "icacls failed to restrict {}",
            dir.display()
        )))
    }
}

#[cfg(test)]
mod tests {
    use iroh::EndpointId;

    use super::*;
    use crate::members::Member;

    /// A fresh directory, created up front so no platform ACL step runs.
    fn dir(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("halo-state-{name}-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn the_old_member_list_moves_into_the_journal() {
        let dir = dir("migrate");
        let state = State::new(Some(dir.clone())).unwrap();
        let me = state.key().unwrap().public();
        let phone = SecretKey::from_bytes(&[7; 32]).public();
        let old = Members(vec![Member {
            id: phone,
            name: "phone".into(),
            addrs: vec!["192.168.1.20:7777".parse().unwrap()],
        }]);
        fs::write(dir.join(MEMBERS_FILE), old.render()).unwrap();

        let view = state.journal().unwrap().view(me);
        assert_eq!(view.members, old);
        assert!(!dir.join(MEMBERS_FILE).exists());
        assert!(dir.join("members.old").exists());
        // Read back from the file this time.
        assert_eq!(state.journal().unwrap().view(me).members, old);
    }

    #[test]
    fn concurrent_writers_keep_each_others_entries() {
        let dir = dir("concurrent");
        let state = State::new(Some(dir.clone())).unwrap();
        let key = state.key().unwrap();
        let writers: Vec<_> = (0..4u8)
            .map(|writer| {
                let (dir, key) = (dir.clone(), key.clone());
                std::thread::spawn(move || {
                    let state = State::new(Some(dir)).unwrap();
                    for i in 0..25u8 {
                        let id: EndpointId =
                            SecretKey::from_bytes(&[writer * 25 + i + 1; 32]).public();
                        state
                            .update_journal(|journal| {
                                journal.append(
                                    &key,
                                    Action::Add {
                                        id,
                                        name: String::new(),
                                        addrs: vec![],
                                    },
                                );
                                Ok(())
                            })
                            .unwrap();
                    }
                })
            })
            .collect();
        for writer in writers {
            writer.join().unwrap();
        }
        let journal = state.journal().unwrap();
        assert_eq!(journal.len(), 100);
        assert_eq!(journal.view(key.public()).members.0.len(), 100);
    }
}
