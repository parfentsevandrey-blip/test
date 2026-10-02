//! systemd: a unit that starts `halo up` at boot and restarts it on failure.
//! The log goes to the system journal.

use std::{fs, path::Path, process::Command};

use anyhow::{Context, Result, bail};

use super::{Installed, Setup, copy_self, require_root, run};

const UNIT: &str = "/etc/systemd/system/halo.service";
const PROGRAM: &str = "/usr/local/bin/halo";

pub fn install(setup: &Setup) -> Result<()> {
    require_root()?;
    if !Path::new("/run/systemd/system").is_dir() {
        bail!(
            "this system does not run systemd: have its init system start `{PROGRAM} up` instead"
        );
    }
    fs::create_dir_all("/usr/local/bin")?;
    copy_self(Path::new(PROGRAM))?;
    let args: Vec<String> = setup
        .args("up", false)
        .iter()
        .map(|arg| quote(arg))
        .collect();
    let unit = format!(
        "[Unit]\n\
         Description=Halo: a private network of your own devices\n\
         Wants=network-online.target\n\
         After=network-online.target\n\
         \n\
         [Service]\n\
         ExecStart={PROGRAM} {}\n\
         Restart=on-failure\n\
         RestartSec=5\n\
         \n\
         [Install]\n\
         WantedBy=multi-user.target\n",
        args.join(" ")
    );
    fs::write(UNIT, unit).with_context(|| format!("cannot write {UNIT}"))?;
    run("systemctl", &["daemon-reload"])?;
    run("systemctl", &["enable", "--quiet", "halo.service"])?;
    run("systemctl", &["restart", "halo.service"])?;
    Ok(())
}

/// Stops and removes the service; false if there was none.
pub fn uninstall() -> Result<bool> {
    require_root()?;
    let installed = Path::new(UNIT).exists();
    if installed {
        // Stopped already, or never started: either way it is going.
        let _ = run(
            "systemctl",
            &["disable", "--now", "--quiet", "halo.service"],
        );
        fs::remove_file(UNIT).with_context(|| format!("cannot remove {UNIT}"))?;
        run("systemctl", &["daemon-reload"])?;
    }
    if Path::new(PROGRAM).exists() {
        fs::remove_file(PROGRAM).with_context(|| format!("cannot remove {PROGRAM}"))?;
    }
    Ok(installed)
}

pub fn state() -> Result<Installed> {
    if !Path::new(UNIT).exists() {
        return Ok(Installed::No);
    }
    let active = Command::new("systemctl")
        .args(["is-active", "--quiet", "halo.service"])
        .status()
        .is_ok_and(|status| status.success());
    Ok(if active {
        Installed::Running
    } else {
        Installed::Stopped
    })
}

/// Where to read the service's log.
pub fn log_hint(_log_file: &Path) -> String {
    "journalctl -u halo".to_string()
}

/// One argument of `ExecStart=`, quoted for systemd.
fn quote(arg: &str) -> String {
    let escaped = arg
        .replace('\\', "\\\\")
        .replace('"', "\\\"")
        .replace('%', "%%")
        .replace('$', "$$");
    format!("\"{escaped}\"")
}
