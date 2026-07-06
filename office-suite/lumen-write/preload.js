// preload.js — Electron preload script (CommonJS). No import/export syntax here.
'use strict';

const { contextBridge, ipcRenderer } = require('electron');
const fs = require('fs');
const path = require('path');

// ---------- Load icon set ----------

const ICONS_DIR = path.join(__dirname, 'src', 'assets', 'icons');
const icons = {};
try {
  const files = fs.readdirSync(ICONS_DIR).filter((f) => f.endsWith('.svg'));
  for (const file of files) {
    const name = path.basename(file, '.svg');
    icons[name] = fs.readFileSync(path.join(ICONS_DIR, file), 'utf8');
  }
} catch (err) {
  console.error('Failed to load icons', err);
}

// ---------- Bridge exposed to renderer ----------

contextBridge.exposeInMainWorld('lumen', {
  icons,

  openFile: () => ipcRenderer.invoke('file:open'),
  openPath: (filePath) => ipcRenderer.invoke('file:openPath', filePath),
  saveFile: (payload) => ipcRenderer.invoke('file:save', payload),
  saveFileAs: (payload) => ipcRenderer.invoke('file:saveAs', payload),
  getRecentFiles: () => ipcRenderer.invoke('recent:list'),

  exportPdf: (payload) => ipcRenderer.invoke('export:pdf', payload),
  exportDocx: (payload) => ipcRenderer.invoke('export:docx', payload),
  exportMarkdown: (payload) => ipcRenderer.invoke('export:markdown', payload),
  exportTxt: (payload) => ipcRenderer.invoke('export:txt', payload),
  print: () => ipcRenderer.invoke('app:print'),

  insertImage: () => ipcRenderer.invoke('dialog:insertImage'),

  onRequestCloseCheck: (callback) => {
    ipcRenderer.on('app:request-close-check', () => callback());
  },
  sendCloseResponse: (shouldClose) => {
    ipcRenderer.send('app:close-response', shouldClose);
  },
});
