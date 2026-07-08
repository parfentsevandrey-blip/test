// preload.js — Electron preload script. CommonJS only. Runs with Node
// access (sandbox:false) but the renderer itself never gets Node access;
// everything below is the sole bridge, via contextBridge.

const { contextBridge, ipcRenderer, webUtils } = require('electron');
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

  // Help ▸ About's version string — see main.js's app:getVersion handler.
  getAppVersion: () => ipcRenderer.invoke('app:getVersion'),

  // Real OS clipboard access (plain text only) — see main.js's
  // clipboard:writeText/clipboard:readText handlers for why this exists
  // alongside the renderer's own in-memory rich clipboard.
  clipboard: {
    writeText: (text) => ipcRenderer.invoke('clipboard:writeText', text),
    readText: () => ipcRenderer.invoke('clipboard:readText'),
  },

  // Every method below that reads or writes a file takes NO filePath
  // argument (the one deliberate exception being openDroppedLsheet — see the
  // comment on its main.js handler). main.js is the sole authority on which
  // on-disk path is actually used: it shows the native dialog itself and
  // acts on that dialog's result, or looks a path up in its own recent.json.
  // The renderer only ever supplies content/options and gets back the path
  // main.js actually used (to update the title bar, dirty state, etc).
  file: {
    newDocument: () => ipcRenderer.invoke('file:new-document'),

    openLsheet: () => ipcRenderer.invoke('file:open-lsheet'),
    // Takes the real `File` object from a genuine 'drop' DOM event's
    // dataTransfer.files[0] — NOT a path string. We resolve the actual
    // on-disk path ourselves, here in the preload/isolated-world context,
    // via Electron's webUtils.getPathForFile(). That API is the load-bearing
    // safety property: it throws for anything that isn't a real File object,
    // and returns '' for a File constructed purely in page JS (`new
    // File([...], name)`) that isn't backed by a real file on disk — so page
    // script (including an XSS payload with full run-of-window access) has
    // no way to make this resolve to an attacker-chosen path. It can only
    // ever resolve to the path of a file the OS itself handed to a real
    // drop/file-picker event. A previous version of this bridge method took
    // a bare path string and forwarded it as-is, which let ANY caller
    // (attacker-controlled or not) request ANY path — the "only reachable
    // from a genuine drop" claim was true in intent but not enforced in
    // code. Do not revert to accepting a string here.
    openDroppedLsheet: (file) => {
      let filePath;
      try {
        filePath = webUtils.getPathForFile(file);
      } catch (e) {
        return Promise.resolve({ ok: false, error: 'Invalid file.' });
      }
      if (!filePath) return Promise.resolve({ ok: false, error: 'Invalid file.' });
      return ipcRenderer.invoke('file:open-dropped-lsheet', filePath);
    },

    save: (data, defaultName) => ipcRenderer.invoke('file:save', data, defaultName),
    saveAs: (data, defaultName) => ipcRenderer.invoke('file:save-as', data, defaultName),

    importCsv: () => ipcRenderer.invoke('file:import-csv'),
    importXlsx: () => ipcRenderer.invoke('file:import-xlsx'),

    exportCsv: (sheetData, defaultName) => ipcRenderer.invoke('file:export-csv', sheetData, defaultName),
    exportXlsx: (workbookData, defaultName) => ipcRenderer.invoke('file:export-xlsx', workbookData, defaultName),
    exportPdf: (options, defaultName) => ipcRenderer.invoke('file:export-pdf', options, defaultName),
  },

  recent: {
    get: () => ipcRenderer.invoke('recent:get'),
    openById: (id) => ipcRenderer.invoke('recent:open-by-id', id),
  },

  // Crash-recovery autosave. Content-only, like file.save/saveAs above —
  // main.js decides where the recovery snapshot actually lives (a dedicated
  // folder under userData, keyed to the current document) and the renderer
  // never sees that path either.
  autosave: {
    save: (data) => ipcRenderer.invoke('autosave:save', data),
    clear: () => ipcRenderer.invoke('autosave:clear'),
    checkRecovery: () => ipcRenderer.invoke('autosave:check-recovery'),
    recover: () => ipcRenderer.invoke('autosave:recover'),
    discardRecovery: () => ipcRenderer.invoke('autosave:discard-recovery'),
  },
});
