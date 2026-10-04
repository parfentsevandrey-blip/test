'use strict';
// The Mesh — the desktop app. It starts the `themesh` node in the background (no terminal), shows its
// interface in a window of its own, stays in the tray when the window is closed, and tells you
// about incoming files and messages with system notifications.
const fs = require('node:fs');
const path = require('node:path');
const { app, dialog, ipcMain, Notification, Menu, powerMonitor, shell } = require('electron');
const log = require('./log');
const autostart = require('./autostart');
const { Core, DEFAULT_PORT } = require('./core');
const { Watcher } = require('./events');
const { texts } = require('./i18n');
const { buildMenu } = require('./menu');
const { LocalNetworkWatch, SETTINGS_URL } = require('./localnet');
const { Notifier } = require('./notify');
const { Settings } = require('./settings');
const { statusText } = require('./status');
const { AppTray } = require('./tray');
const { MainWindow } = require('./window');

const root = path.resolve(__dirname, '..');
const testMode = !!process.env.THEMESH_DESKTOP_TEST;

// The Chromium profile of the app is kept apart from the node's data (keys, mail, settings):
// the profile can be thrown away, the data is the identity of this device.
const appData = app.getPath('appData');
const userData = process.env.THEMESH_DESKTOP_USERDATA || path.join(appData, 'themesh-desktop');
app.setPath('userData', userData);
const dataDir = process.env.THEMESH_DIR || path.join(appData, 'themesh');

app.setName('The Mesh');

if (!app.requestSingleInstanceLock()) {
  app.quit();
  return;
}

let t;
let settings;
let core;
let watcher;
let notifier;
let tray;
let mainWin;
let quitting = false;
let shuttingDown = false;
let lastError = '';
const restarts = [];
const localNet = new LocalNetworkWatch();
let localNetTimer = null;

function coreBinary() {
  if (process.env.THEMESH_CORE) return process.env.THEMESH_CORE;
  const exe = process.platform === 'win32' ? 'themesh.exe' : 'themesh';
  if (app.isPackaged) return path.join(process.resourcesPath, 'bin', exe);
  const key = `${process.platform === 'win32' ? 'win' : process.platform === 'darwin' ? 'mac' : 'linux'}-${process.arch}`;
  for (const p of [path.join(root, 'bin', key, exe), path.join(root, '..', exe)]) if (fs.existsSync(p)) return p;
  return path.join(root, 'bin', key, exe);
}

function coreArgs() {
  if (!testMode || !process.env.THEMESH_CORE_ARGS) return [];
  try {
    return JSON.parse(process.env.THEMESH_CORE_ARGS);
  } catch {
    return [];
  }
}

function logFile() {
  return path.join(userData, 'logs', 'themesh.log');
}

function updateTray() {
  if (tray) tray.update(statusText(t, core ? core.status : 'idle', watcher ? watcher.state : null));
}

/** «Добавить устройство»: the window opens the dialog that shows the QR code of a new invitation. */
function showAddDevice() {
  showWindow('#/devices?add=1');
}

function showWindow(route) {
  if (!mainWin || !mainWin.win) return;
  mainWin.show(route);
}

function toast(title, body, onClick) {
  if (!Notification.isSupported()) return;
  const n = new Notification({ title, body, icon: path.join(root, 'assets', 'icon.png') });
  if (onClick) n.on('click', onClick);
  n.show();
}

async function confirmQuit() {
  if (testMode) return true;
  showWindow();
  const r = await dialog.showMessageBox(mainWin.win, { type: 'question', buttons: [t.quitConfirm, t.cancel], defaultId: 1, cancelId: 1, title: t.quitTitle, message: t.quitTitle, detail: t.quitText });
  return r.response === 0;
}

async function quitFromUser() {
  if (await confirmQuit()) app.quit();
}

function showAbout() {
  const coreVersion = (watcher && watcher.state.self && watcher.state.self.version) || '—';
  dialog.showMessageBox(mainWin && mainWin.win ? mainWin.win : undefined, { type: 'info', title: t.aboutTitle, message: t.aboutTitle, detail: t.aboutText(app.getVersion(), coreVersion), buttons: ['OK'] });
}

function showLog() {
  const f = fs.existsSync(logFile()) ? logFile() : path.join(userData, 'logs');
  shell.showItemInFolder(f);
}

/**
 * Start the node and show the interface; on failure show the error page. One start at a time: a node that
 * dies while an earlier start is still loading the window must not make two navigations fight for it.
 */
let startQueue = Promise.resolve();
function startCore() {
  const run = startQueue.then(doStartCore, doStartCore);
  startQueue = run.catch(() => {});
  return run;
}

async function doStartCore() {
  mainWin.showSplash();
  updateTray();
  try {
    await core.start();
  } catch (e) {
    lastError = e.message || String(e);
    log.error('start: the core did not start:', lastError);
    mainWin.showError();
    mainWin.show();
    updateTray();
    return false;
  }
  lastError = '';
  log.info('core is up at', core.origin, core.attached ? '(attached)' : '(started by the app)');
  const port = Number(new URL(core.origin).port);
  if (port) settings.set('port', port);
  if (!watcher) {
    watcher = new Watcher(core);
    notifier = new Notifier({ t, core, watcher, windowActive: () => mainWin.isActive(), onClick: (route) => showWindow(route), Notification, icon: path.join(root, 'assets', 'icon.png') });
    notifier.attach();
    if (testMode) notifier.onNotify = (n) => global.__themeshTest.notes.push(n);
    watcher.on('state', () => {
      updateTray();
      maybeAskAutostart();
      maybeExplainLocalNetwork();
    });
    watcher.start();
  }
  updateTray();
  try {
    await mainWin.loadUI();
  } catch (e) {
    lastError = e.message || String(e);
    log.error('start: cannot show the interface:', lastError);
    mainWin.showError();
    mainWin.show();
    return false;
  }
  log.info('interface loaded');
  return true;
}

/** Once the network exists, ask (once) whether this device should come back after a restart. */
async function maybeAskAutostart() {
  if (testMode || settings.get('autostartAsked', false)) return;
  const self = watcher && watcher.state.self;
  if (!self || self.configured === false || !mainWin.isVisible()) return;
  settings.set('autostartAsked', true);
  const r = await dialog.showMessageBox(mainWin.win, { type: 'question', buttons: [t.yes, t.no], defaultId: 0, cancelId: 1, title: t.askAutostartTitle, message: t.askAutostartTitle, detail: t.askAutostartText });
  if (r.response === 0) {
    autostart.setEnabled(true);
    updateTray();
  }
}

/**
 * On a Mac the person has to allow the app to use the local network; without it the node cannot be found by the
 * other devices at home. The node reports it (self.lan.problem === 'blocked'): explain it, once in a while.
 */
async function maybeExplainLocalNetwork() {
  if (testMode || !mainWin.isVisible()) return;
  const r = localNet.check(watcher && watcher.state, settings.get('localNetworkAskedAt', 0), Date.now());
  if (r.action === 'wait') {
    clearTimeout(localNetTimer);
    localNetTimer = setTimeout(maybeExplainLocalNetwork, r.ms + 500);
    localNetTimer.unref();
    return;
  }
  if (r.action !== 'ask') return;
  localNet.asked();
  settings.set('localNetworkAskedAt', Date.now());
  const answer = await dialog.showMessageBox(mainWin.win, { type: 'warning', buttons: [t.localNetOpen, t.localNetLater], defaultId: 0, cancelId: 1, title: t.localNetTitle, message: t.localNetTitle, detail: t.localNetText });
  if (answer.response === 0) shell.openExternal(SETTINGS_URL).catch((e) => log.warn('cannot open the settings:', e.message));
}

/** The node died by itself: start it again (a few times), then give up with a readable page. */
async function onCrashed() {
  if (quitting) return;
  const now = Date.now();
  while (restarts.length && now - restarts[0] > 120_000) restarts.shift();
  if (restarts.length >= 3) {
    lastError = core.lastError || t.statusStopped;
    mainWin.showError();
    mainWin.show();
    updateTray();
    return;
  }
  restarts.push(now);
  log.warn('the core stopped, starting it again');
  await startCore();
}

async function main() {
  log.init(path.join(userData, 'logs'));
  log.info('The Mesh', app.getVersion(), process.platform, process.arch, 'electron', process.versions.electron, 'packaged', app.isPackaged);
  if (process.platform === 'win32') app.setAppUserModelId('app.themesh.desktop');
  const locale = process.env.THEMESH_DESKTOP_LOCALE || app.getLocale();
  t = texts(locale);
  settings = new Settings(path.join(userData, 'settings.json'));
  core = new Core({ binary: coreBinary(), dataDir, logFile: logFile(), preferredPort: settings.get('port', DEFAULT_PORT), extraArgs: coreArgs() });
  core.on('crashed', () => onCrashed());

  const hidden = autostart.startedHidden() && !testMode;
  mainWin = new MainWindow({ t, core, settings, root, locale });
  mainWin.onFirstHide = () => toast(t.hiddenTitle, t.hiddenBody);
  mainWin.onSaved = (file) => toast(t.savedTitle, path.basename(file), () => shell.showItemInFolder(file));
  mainWin.create({ show: !hidden });
  mainWin.win.on('session-end', () => {
    quitting = true;
    mainWin.quitting = true;
    app.quit();
  });

  Menu.setApplicationMenu(buildMenu({ t, onAbout: showAbout, onShowLog: showLog, onQuit: quitFromUser, onAddDevice: showAddDevice }));
  tray = new AppTray({
    t,
    root,
    onOpen: () => showWindow(),
    onAddDevice: showAddDevice,
    onQuit: quitFromUser,
    onAbout: showAbout,
    onShowLog: showLog,
    getAutostart: () => autostart.isEnabled(),
    setAutostart: (on) => {
      try {
        autostart.setEnabled(on);
      } catch (e) {
        log.warn('autostart:', e.message);
      }
      updateTray();
    },
  });
  if (!testMode || process.env.THEMESH_DESKTOP_TRAY) tray.create();

  ipcMain.handle('shell:info', (e) => {
    if (!e.senderFrame || !String(e.senderFrame.url).startsWith('file://')) return null;
    return {
      lang: locale.toLowerCase().startsWith('ru') ? 'ru' : 'en',
      message: lastError,
      texts: { starting: t.starting, errorTitle: t.errorTitle, errorHint: t.errorHint, restart: t.restart, logs: t.logs, closeApp: t.closeApp },
    };
  });
  ipcMain.handle('shell:restart', (e) => {
    if (!e.senderFrame || !String(e.senderFrame.url).startsWith('file://')) return;
    restarts.length = 0;
    return startCore();
  });
  ipcMain.handle('shell:show-log', (e) => {
    if (e.senderFrame && String(e.senderFrame.url).startsWith('file://')) showLog();
  });
  ipcMain.handle('shell:quit', (e) => {
    if (e.senderFrame && String(e.senderFrame.url).startsWith('file://')) app.quit();
  });

  powerMonitor.on('shutdown', () => {
    quitting = true;
    mainWin.quitting = true;
  });

  if (testMode) {
    global.__themeshTest = {
      core,
      get tray() {
        return !!(tray && tray.tray);
      },
      get status() {
        return core.status;
      },
      window: () => mainWin.win,
      visible: () => mainWin.isVisible(),
      addDevice: showAddDevice,
      watcher: () => watcher,
      autostart,
      notes: [],
    };
  }

  await startCore();
  if (hidden && process.platform === 'linux') toast('The Mesh', t.startedHiddenBody, () => showWindow());
}

app.on('second-instance', () => showWindow());
app.on('activate', () => showWindow());
app.on('window-all-closed', () => {
  /* the app lives in the tray */
});
app.on('web-contents-created', (_e, contents) => {
  contents.on('will-attach-webview', (ev) => ev.preventDefault());
});
app.on('before-quit', (e) => {
  quitting = true;
  if (mainWin) mainWin.quitting = true;
  if (shuttingDown) return;
  shuttingDown = true;
  e.preventDefault();
  (async () => {
    try {
      if (watcher) watcher.stop();
      if (core) await core.stop();
    } catch (err) {
      log.error('shutdown:', err);
    }
    if (tray) tray.destroy();
    log.info('bye');
    app.exit(0);
  })();
});
for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => app.quit());

app.whenReady().then(main).catch((e) => {
  log.error('fatal', e);
  dialog.showErrorBox('The Mesh', String(e && e.message ? e.message : e));
  app.exit(1);
});
