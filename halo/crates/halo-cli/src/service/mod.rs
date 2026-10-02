//! Halo as a system service: the network comes up with the computer, needs no
//! terminal, and comes back if the node stops. systemd on Linux, launchd on
//! macOS, the service manager on Windows.

use std::{
    fs,
    path::{Path, PathBuf},
    process::Command,
};

use anyhow::{Context, Result, bail};

#[cfg(target_os = "linux")]
mod linux;
#[cfg(target_os = "linux")]
use linux as platform;

#[cfg(target_os = "macos")]
mod macos;
#[cfg(target_os = "macos")]
use macos as platform;

#[cfg(windows)]
pub mod windows;
#[cfg(windows)]
use windows as platform;

#[cfg(not(any(target_os = "linux", target_os = "macos", windows)))]
mod unsupported;
#[cfg(not(any(target_os = "linux", target_os = "macos", windows)))]
use unsupported as platform;

pub use platform::{install, log_hint, state, uninstall};

/// Whether the service is set up, and running.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Installed {
    No,
    Stopped,
    Running,
}

/// How the service runs the node.
pub struct Setup {
    pub port: u16,
    pub no_lan: bool,
    /// A state directory other than the default one.
    pub state_dir: Option<PathBuf>,
    /// Where the node logs, where the system keeps no journal of its own.
    pub log_file: PathBuf,
}

impl Setup {
    /// The arguments of the program the service starts.
    fn args(&self, command: &str, log_file: bool) -> Vec<String> {
        let mut args = vec![command.to_string(), "--port".into(), self.port.to_string()];
        if self.no_lan {
            args.push("--no-lan".into());
        }
        if let Some(dir) = &self.state_dir {
            args.push("--state-dir".into());
            args.push(dir.to_string_lossy().into_owned());
        }
        if log_file {
            args.push("--log-file".into());
            args.push(self.log_file.to_string_lossy().into_owned());
        }
        args
    }
}

/// Copies this program to `to`, replacing an older copy even while it runs.
fn copy_self(to: &Path) -> Result<()> {
    let me = std::env::current_exe().context("cannot find this program's file")?;
    if same_file(&me, to) {
        return Ok(());
    }
    let new = to.with_extension("new");
    fs::copy(&me, &new).with_context(|| format!("cannot copy halo to {}", new.display()))?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&new, fs::Permissions::from_mode(0o755))?;
    }
    // A rename: a running copy keeps its file until it exits.
    fs::rename(&new, to).with_context(|| format!("cannot replace {}", to.display()))
}

fn same_file(a: &Path, b: &Path) -> bool {
    match (a.canonicalize(), b.canonicalize()) {
        (Ok(a), Ok(b)) => a == b,
        _ => false,
    }
}

/// Runs a system tool, failing with what it said if it fails.
fn run(program: &str, args: &[&str]) -> Result<String> {
    let output = Command::new(program)
        .args(args)
        .output()
        .with_context(|| format!("failed to run {program}"))?;
    if !output.status.success() {
        let said = String::from_utf8_lossy(&output.stderr);
        let said = if said.trim().is_empty() {
            String::from_utf8_lossy(&output.stdout)
        } else {
            said
        };
        bail!("{program} {} failed: {}", args.join(" "), said.trim());
    }
    Ok(String::from_utf8_lossy(&output.stdout).into_owned())
}

#[cfg(unix)]
fn require_root() -> Result<()> {
    // SAFETY: geteuid has no preconditions and cannot fail.
    if unsafe { libc::geteuid() } != 0 {
        bail!("this changes system settings: run it with sudo");
    }
    Ok(())
}
