//! Where this device keeps its key and member list.
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

use crate::members::Members;

const KEY_FILE: &str = "secret.key";
const MEMBERS_FILE: &str = "members";

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

    pub fn members(&self) -> Result<Members> {
        let path = self.dir.join(MEMBERS_FILE);
        match fs::read_to_string(&path) {
            Ok(text) => Members::parse(&text).with_context(|| format!("in {}", path.display())),
            Err(err) if err.kind() == io::ErrorKind::NotFound => Ok(Members::default()),
            Err(err) => Err(explain(err, &path)),
        }
    }

    pub fn save_members(&self, members: &Members) -> Result<()> {
        self.ensure_dir()?;
        let path = self.dir.join(MEMBERS_FILE);
        let tmp = self.dir.join(format!("{MEMBERS_FILE}.tmp"));
        let _ = fs::remove_file(&tmp);
        write_new(&tmp, &members.render()).map_err(|err| explain(err, &tmp))?;
        fs::rename(&tmp, &path).map_err(|err| explain(err, &path))
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
