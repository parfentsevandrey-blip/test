import { contextBridge, ipcRenderer } from 'electron'

const api = {
  platform: process.platform,
  /** Pushes a freshly-rendered tray icon (PNG data URL) + tooltip to the main process. */
  updateTray: (dataUrl: string, tooltip: string): void => {
    ipcRenderer.send('tray:update', dataUrl, tooltip)
  },
  /** Whether the app is currently registered to launch when Windows starts. */
  getLaunchAtLogin: (): Promise<boolean> => ipcRenderer.invoke('app:getLaunchAtLogin'),
  setLaunchAtLogin: (enabled: boolean): void => {
    ipcRenderer.send('app:setLaunchAtLogin', enabled)
  }
}

try {
  contextBridge.exposeInMainWorld('api', api)
} catch (error) {
  console.error(error)
}

export type Api = typeof api
