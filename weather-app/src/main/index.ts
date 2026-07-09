import { app, shell, BrowserWindow, Tray, Menu, nativeImage, ipcMain } from 'electron'
import { join } from 'path'

const isDev = !app.isPackaged

let mainWindow: BrowserWindow | null = null
let tray: Tray | null = null
/** Only true once the user picks "Quit" from the tray menu (or another real
    quit path) — lets the window's own 'close' handler tell a real quit from
    a click on the native close button, which should hide to tray instead. */
let isQuitting = false
/** Shown once per session so the close-to-tray behavior is discoverable. */
let hasShownTrayHint = false

function createWindow(): void {
  mainWindow = new BrowserWindow({
    title: 'Cinematic Weather',
    width: 1440,
    height: 900,
    minWidth: 1040,
    minHeight: 680,
    show: false,
    backgroundColor: '#04070d',
    autoHideMenuBar: true,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      sandbox: false,
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  mainWindow.on('ready-to-show', () => {
    mainWindow?.show()
  })

  // The tray exists specifically so the live temperature stays glanceable
  // without the window open — closing (rather than minimizing) used to quit
  // the whole process and kill the tray with it, defeating that purpose.
  mainWindow.on('close', (event) => {
    if (isQuitting) return
    event.preventDefault()
    mainWindow?.hide()
    if (!hasShownTrayHint && tray) {
      hasShownTrayHint = true
      tray.displayBalloon({
        title: 'Cinematic Weather',
        content: 'Still running in the background — click the tray icon to reopen.'
      })
    }
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  mainWindow.webContents.setWindowOpenHandler((details) => {
    shell.openExternal(details.url)
    return { action: 'deny' }
  })

  if (isDev && process.env['ELECTRON_RENDERER_URL']) {
    mainWindow.loadURL(process.env['ELECTRON_RENDERER_URL'])
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

/** Brings the main window to the front, restoring it first if minimized. */
function showMainWindow(): void {
  if (!mainWindow) return
  if (mainWindow.isMinimized()) mainWindow.restore()
  mainWindow.show()
  mainWindow.focus()
}

/** Rebuilds the tray's context menu with the given glance line at the top,
    so right-clicking the tray is itself a read, not just a launcher. */
function buildTrayMenu(glanceLabel: string): Menu {
  return Menu.buildFromTemplate([
    { label: glanceLabel, enabled: false },
    { type: 'separator' },
    { label: 'Open Cinematic Weather', click: showMainWindow },
    { type: 'separator' },
    {
      label: 'Quit',
      click: () => {
        isQuitting = true
        app.quit()
      }
    }
  ])
}

/**
 * A tray icon that shows the current temperature as a numeral (updated by the
 * renderer via the `tray:update` IPC message once weather data loads) rather
 * than a static app glyph. Starts out showing the ordinary app icon for the
 * brief window before the first fetch resolves.
 */
function createTray(): void {
  const iconPath = app.isPackaged
    ? join(process.resourcesPath, 'icon.png')
    : join(__dirname, '../../build/icon.png')
  const icon = nativeImage.createFromPath(iconPath)
  tray = new Tray(icon.isEmpty() ? icon : icon.resize({ width: 32, height: 32 }))
  tray.setToolTip('Cinematic Weather')
  tray.setContextMenu(buildTrayMenu('Cinematic Weather'))

  // Toggle rather than always-show: clicking the tray while the window is
  // already focused and visible hides it again, like a taskbar icon would.
  tray.on('click', () => {
    if (mainWindow && mainWindow.isVisible() && mainWindow.isFocused()) {
      mainWindow.hide()
    } else {
      showMainWindow()
    }
  })

  let lastTrayLabel: string | null = null

  ipcMain.on('tray:update', (_event, dataUrl: string, tooltip: string) => {
    if (!tray) return
    try {
      const image = nativeImage.createFromDataURL(dataUrl)
      if (image.isEmpty()) {
        console.error('[tray:update] nativeImage.createFromDataURL produced an empty image')
      } else {
        tray.setImage(image)
      }
    } catch (err) {
      console.error('[tray:update] failed to apply tray image', err)
    }
    tray.setToolTip(tooltip)
    // Rebuilding the menu is otherwise cheap, but there's no reason to touch
    // native Shell_NotifyIcon state every ~10 minutes if the label is unchanged.
    if (tooltip !== lastTrayLabel) {
      lastTrayLabel = tooltip
      tray.setContextMenu(buildTrayMenu(tooltip))
    }
  })
}

/** Settings-panel IPC: launch-at-Windows-startup is a main-process-only API. */
function registerSettingsIpc(): void {
  ipcMain.handle('app:getLaunchAtLogin', () => app.getLoginItemSettings().openAtLogin)

  ipcMain.on('app:setLaunchAtLogin', (_event, enabled: boolean) => {
    app.setLoginItemSettings({ openAtLogin: enabled })
  })
}

app.whenReady().then(() => {
  app.setAppUserModelId('com.cinematicweather.app')

  createWindow()
  createTray()
  registerSettingsIpc()

  app.on('activate', function () {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('before-quit', () => {
  isQuitting = true
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
