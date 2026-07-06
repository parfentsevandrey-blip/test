import { app, shell, BrowserWindow, Tray, Menu, nativeImage, ipcMain } from 'electron'
import { join } from 'path'

const isDev = !app.isPackaged

let mainWindow: BrowserWindow | null = null
let tray: Tray | null = null

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

  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: 'Open Cinematic Weather', click: showMainWindow },
      { type: 'separator' },
      { label: 'Quit', click: () => app.quit() }
    ])
  )

  tray.on('click', showMainWindow)

  ipcMain.on('tray:update', (_event, dataUrl: string, tooltip: string) => {
    if (!tray) return
    try {
      const image = nativeImage.createFromDataURL(dataUrl)
      if (!image.isEmpty()) tray.setImage(image)
    } catch {
      // Malformed payload: keep whatever image the tray already has.
    }
    tray.setToolTip(tooltip)
  })
}

app.whenReady().then(() => {
  app.setAppUserModelId('com.cinematicweather.app')

  createWindow()
  createTray()

  app.on('activate', function () {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
