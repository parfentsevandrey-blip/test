//! The Windows service manager: a service that starts with Windows, runs the
//! node as `halo service` and is restarted if it fails. The node writes its
//! own log.

use std::{
    ffi::OsString,
    fs,
    path::{Path, PathBuf},
    process::Command,
    sync::Mutex,
    thread,
    time::Duration,
};

use anyhow::{Context, Result, bail};
use halo_core::{addr::overlay_ipv4, state::State};
use tracing::error;
use windows_service::{
    define_windows_service,
    service::{
        ServiceAccess, ServiceAction, ServiceActionType, ServiceControl, ServiceControlAccept,
        ServiceErrorControl, ServiceExitCode, ServiceFailureActions, ServiceFailureResetPeriod,
        ServiceInfo, ServiceStartType, ServiceState, ServiceStatus, ServiceType,
    },
    service_control_handler::{self, ServiceControlHandlerResult},
    service_dispatcher,
    service_manager::{ServiceManager, ServiceManagerAccess},
};

use super::{Installed, Setup, copy_self, run};
use crate::UpArgs;

const NAME: &str = "Halo";
const DESCRIPTION: &str = "A private network of your own devices, without servers";
/// Windows says this when there is no such service.
const ERROR_SERVICE_DOES_NOT_EXIST: i32 = 1060;
const ERROR_ACCESS_DENIED: i32 = 5;

fn install_dir() -> PathBuf {
    let base = std::env::var_os("ProgramFiles").unwrap_or_else(|| r"C:\Program Files".into());
    PathBuf::from(base).join("Halo")
}

fn manager(access: ServiceManagerAccess) -> Result<ServiceManager> {
    ServiceManager::local_computer(None::<&str>, access).map_err(|err| match os_error(&err) {
        Some(ERROR_ACCESS_DENIED) => {
            anyhow::anyhow!("run it in a terminal opened as administrator")
        }
        _ => anyhow::Error::new(err).context("cannot reach the Windows service manager"),
    })
}

fn os_error(err: &windows_service::Error) -> Option<i32> {
    match err {
        windows_service::Error::Winapi(err) => err.raw_os_error(),
        _ => None,
    }
}

pub fn install(setup: &Setup) -> Result<()> {
    let manager = manager(ServiceManagerAccess::CONNECT | ServiceManagerAccess::CREATE_SERVICE)?;
    let access = ServiceAccess::QUERY_STATUS
        | ServiceAccess::START
        | ServiceAccess::STOP
        | ServiceAccess::CHANGE_CONFIG;
    let existing = match manager.open_service(NAME, access) {
        Ok(service) => Some(service),
        Err(err) if os_error(&err) == Some(ERROR_SERVICE_DOES_NOT_EXIST) => None,
        Err(err) => return Err(err).context("cannot open the Halo service"),
    };
    // A running older version holds its files.
    if let Some(service) = &existing {
        stop(service)?;
    }

    let dir = install_dir();
    fs::create_dir_all(&dir).with_context(|| format!("cannot create {}", dir.display()))?;
    let here = std::env::current_exe()
        .ok()
        .and_then(|exe| exe.parent().map(Path::to_path_buf))
        .context("cannot find this program's folder")?;
    let wintun = here.join("wintun.dll");
    if !wintun.is_file() {
        bail!(
            "wintun.dll is not next to halo.exe in {}: keep the two files together",
            here.display()
        );
    }
    let exe = dir.join("halo.exe");
    retry(|| copy_self(&exe))?;
    if !super::same_file(&wintun, &dir.join("wintun.dll")) {
        retry(|| {
            fs::copy(&wintun, dir.join("wintun.dll"))
                .map(drop)
                .context("cannot copy wintun.dll")
        })?;
    }

    let info = ServiceInfo {
        name: NAME.into(),
        display_name: NAME.into(),
        service_type: ServiceType::OWN_PROCESS,
        start_type: ServiceStartType::AutoStart,
        error_control: ServiceErrorControl::Normal,
        executable_path: exe.clone(),
        launch_arguments: setup
            .args("service", false)
            .into_iter()
            .map(OsString::from)
            .collect(),
        dependencies: Vec::new(),
        account_name: None,
        account_password: None,
    };
    let service = match existing {
        Some(service) => {
            service
                .change_config(&info)
                .context("cannot update the Halo service")?;
            service
        }
        None => manager
            .create_service(&info, access)
            .context("cannot create the Halo service")?,
    };
    service.set_description(DESCRIPTION)?;
    let restart = |seconds| ServiceAction {
        action_type: ServiceActionType::Restart,
        delay: Duration::from_secs(seconds),
    };
    service.update_failure_actions(ServiceFailureActions {
        reset_period: ServiceFailureResetPeriod::After(Duration::from_secs(24 * 3600)),
        reboot_msg: None,
        command: None,
        actions: Some(vec![restart(5), restart(5), restart(30)]),
    })?;
    // A node that stops with an error counts as a failure too, not only a crash.
    service.set_failure_actions_on_non_crash_failures(true)?;

    let state = State::new(setup.state_dir.clone())?;
    allow_through_firewall(&exe, &state)?;
    service
        .start::<&str>(&[])
        .context("cannot start the Halo service")?;
    Ok(())
}

/// Stops and removes the service and its files; false if there was none.
pub fn uninstall() -> Result<bool> {
    let manager = manager(ServiceManagerAccess::CONNECT)?;
    let access = ServiceAccess::QUERY_STATUS | ServiceAccess::STOP | ServiceAccess::DELETE;
    let installed = match manager.open_service(NAME, access) {
        Ok(service) => {
            stop(&service)?;
            service.delete().context("cannot remove the Halo service")?;
            true
        }
        Err(err) if os_error(&err) == Some(ERROR_SERVICE_DOES_NOT_EXIST) => false,
        Err(err) => return Err(err).context("cannot open the Halo service"),
    };
    let _ = powershell("Remove-NetFirewallRule -Group 'Halo' -ErrorAction SilentlyContinue");
    let dir = install_dir();
    if dir.is_dir() {
        let running_from_it = std::env::current_exe()
            .ok()
            .is_some_and(|exe| exe.starts_with(&dir));
        if running_from_it {
            // A running program cannot delete itself: a moment after it exits.
            use std::os::windows::process::CommandExt;
            const CREATE_NO_WINDOW: u32 = 0x0800_0000;
            let _ = Command::new("cmd")
                .raw_arg(format!(
                    "/c \"ping -n 3 127.0.0.1 >nul & rmdir /s /q \"{}\"\"",
                    dir.display()
                ))
                .creation_flags(CREATE_NO_WINDOW)
                .spawn();
        } else {
            retry(|| {
                fs::remove_dir_all(&dir).with_context(|| format!("cannot remove {}", dir.display()))
            })?;
        }
    }
    Ok(installed)
}

pub fn state() -> Result<Installed> {
    let manager = manager(ServiceManagerAccess::CONNECT)?;
    match manager.open_service(NAME, ServiceAccess::QUERY_STATUS) {
        Ok(service) => Ok(match service.query_status()?.current_state {
            ServiceState::Running => Installed::Running,
            _ => Installed::Stopped,
        }),
        Err(err) if os_error(&err) == Some(ERROR_SERVICE_DOES_NOT_EXIST) => Ok(Installed::No),
        Err(err) => Err(err).context("cannot open the Halo service"),
    }
}

/// Where to read the service's log.
pub fn log_hint(log_file: &Path) -> String {
    format!("Get-Content -Wait '{}'", log_file.display())
}

fn stop(service: &windows_service::service::Service) -> Result<()> {
    if service.query_status()?.current_state != ServiceState::Stopped {
        let _ = service.stop();
    }
    for _ in 0..60 {
        if service.query_status()?.current_state == ServiceState::Stopped {
            return Ok(());
        }
        thread::sleep(Duration::from_millis(500));
    }
    bail!("the Halo service does not stop")
}

/// Tries for a few seconds: a program that just stopped may still hold its files.
fn retry(mut step: impl FnMut() -> Result<()>) -> Result<()> {
    let mut tries = 0;
    loop {
        match step() {
            Ok(()) => return Ok(()),
            Err(_) if tries < 20 => {
                tries += 1;
                thread::sleep(Duration::from_millis(500));
            }
            Err(err) => return Err(err),
        }
    }
}

/// Lets other devices reach the node, and the network's own traffic reach this
/// computer: only to this device's address in the network, which only the
/// Halo adapter has, and only from the network's addresses.
fn allow_through_firewall(exe: &Path, state: &State) -> Result<()> {
    let ip = overlay_ipv4(&state.key()?.public());
    let script = format!(
        "Remove-NetFirewallRule -Group 'Halo' -ErrorAction SilentlyContinue; \
         New-NetFirewallRule -Group 'Halo' -DisplayName 'Halo transport' -Direction Inbound \
         -Program '{exe}' -Action Allow -Profile Any | Out-Null; \
         New-NetFirewallRule -Group 'Halo' -DisplayName 'Halo network' -Direction Inbound \
         -LocalAddress {ip} -RemoteAddress 100.64.0.0/10 -Action Allow -Profile Any | Out-Null",
        exe = exe.display().to_string().replace('\'', "''"),
    );
    powershell(&script).context("cannot add the firewall rules")?;
    Ok(())
}

fn powershell(script: &str) -> Result<String> {
    run(
        "powershell",
        &["-NoProfile", "-NonInteractive", "-Command", script],
    )
}

/// What the service runs, handed from `main` to the service thread.
static NODE: Mutex<Option<(State, UpArgs)>> = Mutex::new(None);

define_windows_service!(ffi_service_main, service_main);

/// Runs the node under the service manager: `halo service`, which Windows starts.
pub fn run_service(state: State, args: UpArgs) -> Result<()> {
    *NODE.lock().expect("poisoned") = Some((state, args));
    service_dispatcher::start(NAME, ffi_service_main)
        .context("`halo service` is for Windows to start: use `halo install`")
}

fn service_main(_arguments: Vec<OsString>) {
    if let Err(err) = serve() {
        error!("the service failed: {err:#}");
    }
}

fn serve() -> Result<()> {
    let (state, args) = NODE
        .lock()
        .expect("poisoned")
        .take()
        .context("the service started twice")?;
    let (stop_tx, mut stop_rx) = tokio::sync::watch::channel(false);
    let handler = move |control| match control {
        ServiceControl::Stop | ServiceControl::Shutdown | ServiceControl::Preshutdown => {
            let _ = stop_tx.send(true);
            ServiceControlHandlerResult::NoError
        }
        ServiceControl::Interrogate => ServiceControlHandlerResult::NoError,
        _ => ServiceControlHandlerResult::NotImplemented,
    };
    let status = service_control_handler::register(NAME, handler)?;
    let report = |state, accept, exit_code| {
        status.set_service_status(ServiceStatus {
            service_type: ServiceType::OWN_PROCESS,
            current_state: state,
            controls_accepted: accept,
            exit_code,
            checkpoint: 0,
            wait_hint: Duration::default(),
            process_id: None,
        })
    };
    report(
        ServiceState::Running,
        ServiceControlAccept::STOP | ServiceControlAccept::SHUTDOWN,
        ServiceExitCode::NO_ERROR,
    )?;
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?;
    let stopped = async move {
        let _ = stop_rx.wait_for(|stop| *stop).await;
    };
    let result = runtime.block_on(crate::up(state, args, stopped));
    if let Err(err) = &result {
        error!("{err:#}");
    }
    let exit_code = match result {
        Ok(()) => ServiceExitCode::NO_ERROR,
        Err(_) => ServiceExitCode::ServiceSpecific(1),
    };
    report(
        ServiceState::Stopped,
        ServiceControlAccept::empty(),
        exit_code,
    )?;
    Ok(())
}
