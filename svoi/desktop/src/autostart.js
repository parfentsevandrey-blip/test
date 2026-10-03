'use strict';
// "Start when I sign in". Windows and macOS have it in Electron; on Linux it is a .desktop file in
// ~/.config/autostart (for an AppImage the file points at the AppImage itself).
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { app } = require('electron');

const HIDDEN = '--hidden';

function linuxFile() {
  const base = process.env.XDG_CONFIG_HOME || path.join(os.homedir(), '.config');
  return path.join(base, 'autostart', 'themesh.desktop');
}

function linuxExec() {
  const target = process.env.APPIMAGE || process.execPath;
  return `"${target.replace(/"/g, '\\"')}" ${HIDDEN}`;
}

function isEnabled() {
  if (process.platform === 'linux') return fs.existsSync(linuxFile());
  return app.getLoginItemSettings({ args: [HIDDEN] }).openAtLogin;
}

function setEnabled(on) {
  if (process.platform === 'linux') {
    const f = linuxFile();
    if (!on) {
      fs.rmSync(f, { force: true });
      return;
    }
    fs.mkdirSync(path.dirname(f), { recursive: true });
    fs.writeFileSync(
      f,
      ['[Desktop Entry]', 'Type=Application', 'Name=The Mesh', 'Name[en]=The Mesh', 'Comment=Private network of your own devices', `Exec=${linuxExec()}`, 'Terminal=false', 'X-GNOME-Autostart-enabled=true', ''].join('\n'),
    );
    return;
  }
  app.setLoginItemSettings({ openAtLogin: on, args: [HIDDEN] });
}

/** True when the system started us at login (so there is no point in showing a window). */
function startedHidden() {
  if (process.argv.includes(HIDDEN)) return true;
  return process.platform === 'darwin' ? app.getLoginItemSettings().wasOpenedAtLogin === true : false;
}

module.exports = { isEnabled, setEnabled, startedHidden, HIDDEN };
