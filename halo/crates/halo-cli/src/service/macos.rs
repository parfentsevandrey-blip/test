//! launchd: a daemon that starts `halo up` at boot and restarts it if it fails.
//! launchd keeps no log, so the node writes its own.

use std::{fs, path::Path, process::Command, thread, time::Duration};

use anyhow::{Context, Result};

use super::{Installed, Setup, copy_self, require_root, run};

const LABEL: &str = "dev.halo.node";
const PLIST: &str = "/Library/LaunchDaemons/dev.halo.node.plist";
const PROGRAM: &str = "/usr/local/bin/halo";
const FIREWALL: &str = "/usr/libexec/ApplicationFirewall/socketfilterfw";

pub fn install(setup: &Setup) -> Result<()> {
    require_root()?;
    fs::create_dir_all("/usr/local/bin")?;
    copy_self(Path::new(PROGRAM))?;
    // A copy of a downloaded file is still "downloaded": Gatekeeper would stop
    // the daemon, with no window to ask in.
    let _ = Command::new("xattr")
        .args(["-d", "com.apple.quarantine", PROGRAM])
        .output();
    let mut args = vec![PROGRAM.to_string()];
    args.extend(setup.args("up", true));
    let args: String = args
        .iter()
        .map(|arg| format!("\n\t\t<string>{}</string>", escape(arg)))
        .collect();
    let plist = format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key>
	<string>{LABEL}</string>
	<key>ProgramArguments</key>
	<array>{args}
	</array>
	<key>RunAtLoad</key>
	<true/>
	<key>KeepAlive</key>
	<dict>
		<key>SuccessfulExit</key>
		<false/>
	</dict>
	<key>ThrottleInterval</key>
	<integer>5</integer>
</dict>
</plist>
"#
    );
    fs::write(PLIST, plist).with_context(|| format!("cannot write {PLIST}"))?;
    // An older version may be running: launchd starts the new one in its place.
    let _ = run("launchctl", &["bootout", &format!("system/{LABEL}")]);
    let mut tries = 0;
    loop {
        match run("launchctl", &["bootstrap", "system", PLIST]) {
            Ok(_) => break,
            // The old one takes a moment to go.
            Err(_) if tries < 10 => {
                tries += 1;
                thread::sleep(Duration::from_millis(500));
            }
            Err(err) => return Err(err),
        }
    }
    allow_through_firewall();
    Ok(())
}

/// Stops and removes the daemon; false if there was none.
pub fn uninstall() -> Result<bool> {
    require_root()?;
    let installed = Path::new(PLIST).exists();
    let _ = run("launchctl", &["bootout", &format!("system/{LABEL}")]);
    if installed {
        fs::remove_file(PLIST).with_context(|| format!("cannot remove {PLIST}"))?;
    }
    if Path::new(PROGRAM).exists() {
        let _ = Command::new(FIREWALL).args(["--remove", PROGRAM]).output();
        fs::remove_file(PROGRAM).with_context(|| format!("cannot remove {PROGRAM}"))?;
    }
    Ok(installed)
}

pub fn state() -> Result<Installed> {
    if !Path::new(PLIST).exists() {
        return Ok(Installed::No);
    }
    let output = Command::new("launchctl")
        .args(["print", &format!("system/{LABEL}")])
        .output()
        .context("failed to run launchctl")?;
    let running = output.status.success()
        && String::from_utf8_lossy(&output.stdout)
            .lines()
            .any(|line| line.trim() == "state = running");
    Ok(if running {
        Installed::Running
    } else {
        Installed::Stopped
    })
}

/// Where to read the service's log.
pub fn log_hint(log_file: &Path) -> String {
    format!("sudo tail -f '{}'", log_file.display())
}

/// With the application firewall on, lets other devices reach the node: the
/// daemon has no window to ask in.
fn allow_through_firewall() {
    let on = Command::new(FIREWALL)
        .arg("--getglobalstate")
        .output()
        .is_ok_and(|output| String::from_utf8_lossy(&output.stdout).contains("enabled"));
    if on {
        let _ = Command::new(FIREWALL).args(["--add", PROGRAM]).output();
        let _ = Command::new(FIREWALL)
            .args(["--unblockapp", PROGRAM])
            .output();
    }
}

fn escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}
