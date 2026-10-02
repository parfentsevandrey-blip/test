//! Systems without a known service manager: `halo up` runs by hand.

use std::path::Path;

use anyhow::{Result, bail};

use super::{Installed, Setup};

pub fn install(_setup: &Setup) -> Result<()> {
    bail!("installing a service is not supported on this system: run `halo up` instead")
}

pub fn uninstall() -> Result<bool> {
    Ok(false)
}

pub fn state() -> Result<Installed> {
    Ok(Installed::No)
}

pub fn log_hint(log_file: &Path) -> String {
    log_file.display().to_string()
}
