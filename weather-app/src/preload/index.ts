import { contextBridge, ipcRenderer } from 'electron'

const api = {
  platform: process.platform,
  /** Pushes a freshly-rendered tray icon (PNG data URL) + tooltip to the main process. */
  updateTray: (dataUrl: string, tooltip: string): void => {
    ipcRenderer.send('tray:update', dataUrl, tooltip)
  }
}

try {
  contextBridge.exposeInMainWorld('api', api)
} catch (error) {
  console.error(error)
}

export type Api = typeof api
