// preload.js — Electron preload script. CommonJS only. Runs with Node
// access (sandbox:false) but the renderer itself never gets Node access;
// everything below is the sole bridge, via contextBridge.

const { contextBridge, ipcRenderer } = require('electron');
const fs = require('fs');
const path = require('path');

const iconsDir = path.join(__dirname, 'src', 'assets', 'icons');
const icons = {};
try {
  for (const file of fs.readdirSync(iconsDir)) {
    if (file.endsWith('.svg')) {
      const name = file.replace(/\.svg$/, '');
      icons[name] = fs.readFileSync(path.join(iconsDir, file), 'utf8');
    }
  }
} catch (e) {
  // icons directory should always exist in this project; fail quiet in prod.
}

contextBridge.exposeInMainWorld('lumen', {
  icons,
  platform: process.platform,

  onBeforeClose: (cb) => ipcRenderer.on('app:before-close', cb),
  confirmClose: () => ipcRenderer.send('app:confirm-close'),

  dialogs: {
    openLsheet: () => ipcRenderer.invoke('dialog:open-lsheet'),
    saveLsheet: (defaultName) => ipcRenderer.invoke('dialog:save-lsheet', defaultName),
    openImport: (kind) => ipcRenderer.invoke('dialog:open-import', kind),
    saveExport: (kind, defaultName) => ipcRenderer.invoke('dialog:save-export', kind, defaultName),
  },

  file: {
    readLsheet: (filePath) => ipcRenderer.invoke('file:read-lsheet', filePath),
    writeLsheet: (filePath, data) => ipcRenderer.invoke('file:write-lsheet', filePath, data),
    exportXlsx: (filePath, workbookData) => ipcRenderer.invoke('file:export-xlsx', filePath, workbookData),
    importXlsx: (filePath) => ipcRenderer.invoke('file:import-xlsx', filePath),
    exportCsv: (filePath, sheetData) => ipcRenderer.invoke('file:export-csv', filePath, sheetData),
    importCsv: (filePath) => ipcRenderer.invoke('file:import-csv', filePath),
  },

  recent: {
    get: () => ipcRenderer.invoke('recent:get'),
    add: (filePath) => ipcRenderer.invoke('recent:add', filePath),
  },
});
