const { app, BrowserWindow, Tray, Menu, screen, nativeImage, ipcMain } = require('electron');
const path = require('path');

const POPUP_W = 560;
const POPUP_H = 400;

let tray = null;
let popup = null;

function createPopup() {
  popup = new BrowserWindow({
    width: POPUP_W,
    height: POPUP_H,
    show: false,
    frame: false,
    resizable: false,
    movable: true,
    transparent: true,
    skipTaskbar: true,
    alwaysOnTop: true,
    fullscreenable: false,
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      backgroundThrottling: false,
    },
  });

  popup.setMenu(null);
  popup.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  popup.on('blur', () => {
    if (popup && !popup.webContents.isDevToolsOpened()) popup.hide();
  });

  popup.on('close', (e) => {
    if (!app.isQuiting) {
      e.preventDefault();
      popup.hide();
    }
  });

  popup.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
}

function positionPopupNearTray(bounds) {
  const display = screen.getDisplayNearestPoint({ x: bounds.x, y: bounds.y });
  const work = display.workArea;

  let x = Math.round(bounds.x + bounds.width / 2 - POPUP_W / 2);
  let y;

  const trayLooksLikeTopBar = bounds.y < work.y + work.height / 2;
  if (trayLooksLikeTopBar) {
    y = Math.round(bounds.y + bounds.height + 8);
  } else {
    y = Math.round(bounds.y - POPUP_H - 8);
  }

  x = Math.min(Math.max(x, work.x + 4), work.x + work.width - POPUP_W - 4);
  y = Math.min(Math.max(y, work.y + 4), work.y + work.height - POPUP_H - 4);

  popup.setPosition(x, y, false);
}

function positionPopupFallback() {
  const point = screen.getCursorScreenPoint();
  const display = screen.getDisplayNearestPoint(point);
  const work = display.workArea;
  popup.setPosition(work.x + work.width - POPUP_W - 12, work.y + work.height - POPUP_H - 12, false);
}

function togglePopup() {
  if (!popup) createPopup();

  if (popup.isVisible()) {
    popup.hide();
    return;
  }

  const bounds = tray.getBounds();
  if (bounds && bounds.width > 0) {
    positionPopupNearTray(bounds);
  } else {
    positionPopupFallback();
  }

  popup.show();
  popup.focus();
}

function createTray() {
  const iconPath = path.join(__dirname, 'assets', 'icon.png');
  let image = nativeImage.createFromPath(iconPath);
  if (!image.isEmpty() && process.platform === 'win32') {
    image = image.resize({ width: 16, height: 16, quality: 'good' });
  }

  tray = new Tray(image);
  tray.setToolTip('Забытая обитель — интерактивная pixel-art комната');
  tray.on('click', togglePopup);
  tray.on('double-click', togglePopup);

  const contextMenu = Menu.buildFromTemplate([
    { label: 'Открыть', click: togglePopup },
    { type: 'separator' },
    {
      label: 'Выход',
      click: () => {
        app.isQuiting = true;
        app.quit();
      },
    },
  ]);
  tray.on('right-click', () => tray.popUpContextMenu(contextMenu));
}

ipcMain.on('df:hide', () => {
  if (popup) popup.hide();
});

ipcMain.on('df:quit', () => {
  app.isQuiting = true;
  app.quit();
});

const gotSingleInstanceLock = app.requestSingleInstanceLock();
if (!gotSingleInstanceLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    togglePopup();
  });

  app.whenReady().then(() => {
    if (process.platform === 'darwin' && app.dock) app.dock.hide();
    createTray();
  });

  // Intentionally does nothing: a tray app has no "last window" to quit on -
  // the popup is only ever hidden, never destroyed, and the tray icon persists.
  app.on('window-all-closed', () => {});
}
