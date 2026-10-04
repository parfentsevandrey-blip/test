'use strict';
// The icon by the clock (Windows, Linux) or in the menu bar (macOS): what is going on in one
// line, "open", start at login, "quit". Closing the window leaves the app here, still on the network.
const path = require('node:path');
const { Menu, Tray, nativeImage } = require('electron');
const log = require('./log');

class AppTray {
  /**
   * @param {object} o
   * @param {object} o.t
   * @param {string} o.root
   * @param {() => void} o.onOpen
   * @param {() => void} o.onAddDevice
   * @param {() => void} o.onQuit
   * @param {() => void} o.onAbout
   * @param {() => void} o.onShowLog
   * @param {() => boolean} o.getAutostart
   * @param {(on: boolean) => void} o.setAutostart
   */
  constructor(o) {
    Object.assign(this, o);
    this.tray = null;
    this.status = '';
  }

  icon() {
    const dir = path.join(this.root, 'assets');
    if (process.platform === 'darwin') {
      const img = nativeImage.createFromPath(path.join(dir, 'trayTemplate.png'));
      img.setTemplateImage(true);
      return img;
    }
    const img = nativeImage.createFromPath(path.join(dir, process.platform === 'win32' ? 'tray.ico' : 'tray.png'));
    return img.isEmpty() ? nativeImage.createFromPath(path.join(dir, 'tray.png')) : img;
  }

  create() {
    try {
      this.tray = new Tray(this.icon());
    } catch (e) {
      log.warn('tray: cannot create the icon', e.message);
      this.tray = null;
      return false;
    }
    this.tray.on('click', () => this.onOpen());
    this.update(this.status || this.t.statusStarting);
    return true;
  }

  update(status) {
    this.status = status;
    if (!this.tray) return;
    const t = this.t;
    this.tray.setToolTip(t.tooltip(status));
    this.tray.setContextMenu(
      Menu.buildFromTemplate([
        { label: t.open, click: () => this.onOpen() },
        { label: t.addDevice, click: () => this.onAddDevice() },
        { label: status, enabled: false },
        { type: 'separator' },
        { label: t.autostart, type: 'checkbox', checked: this.getAutostart(), click: (item) => this.setAutostart(item.checked) },
        { label: t.showLog, click: () => this.onShowLog() },
        { label: t.about, click: () => this.onAbout() },
        { type: 'separator' },
        { label: t.quit, click: () => this.onQuit() },
      ]),
    );
  }

  destroy() {
    if (this.tray) this.tray.destroy();
    this.tray = null;
  }
}

module.exports = { AppTray };
