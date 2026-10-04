'use strict';
// The application menu: on macOS the usual one at the top of the screen; on Windows and Linux a
// short one that appears when Alt is pressed (the window itself has no menu bar).
const { Menu, app } = require('electron');

function buildMenu({ t, onAbout, onShowLog, onQuit, onAddDevice }) {
  const isMac = process.platform === 'darwin';
  const view = {
    label: t.menuView,
    submenu: [
      { label: t.menuReload, role: 'reload' },
      { type: 'separator' },
      { label: t.menuZoomIn, role: 'zoomIn' },
      { label: t.menuZoomOut, role: 'zoomOut' },
      { label: t.menuZoomReset, role: 'resetZoom' },
      { type: 'separator' },
      { label: t.menuFullscreen, role: 'togglefullscreen' },
    ],
  };
  const edit = {
    label: 'Edit',
    submenu: [
      { label: t.undo, role: 'undo' },
      { label: t.redo, role: 'redo' },
      { type: 'separator' },
      { label: t.cut, role: 'cut' },
      { label: t.copy, role: 'copy' },
      { label: t.paste, role: 'paste' },
      { label: t.selectAll, role: 'selectAll' },
    ],
  };
  const help = { label: t.menuHelp, submenu: [{ label: t.showLog, click: onShowLog }, ...(isMac ? [] : [{ label: t.about, click: onAbout }])] };
  const template = [];
  if (isMac) {
    template.push({
      label: app.name,
      submenu: [{ label: t.about, click: onAbout }, { type: 'separator' }, { role: 'hide' }, { role: 'hideOthers' }, { role: 'unhide' }, { type: 'separator' }, { label: t.quit, accelerator: 'Cmd+Q', click: onQuit }],
    });
  }
  template.push(edit, { label: t.menuNetwork, submenu: [{ label: t.addDevice, click: onAddDevice }] }, view);
  template.push({ label: t.menuWindow, submenu: isMac ? [{ role: 'minimize' }, { role: 'close' }] : [{ role: 'minimize' }, { role: 'close' }, { type: 'separator' }, { label: t.quit, accelerator: 'Ctrl+Q', click: onQuit }] });
  template.push(help);
  return Menu.buildFromTemplate(template);
}

module.exports = { buildMenu };
