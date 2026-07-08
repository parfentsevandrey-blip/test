// preload.js — Electron preload script (CommonJS). No import/export syntax here.
'use strict';

const { contextBridge, ipcRenderer, webUtils } = require('electron');
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
  // File ▸ Open Recent: pass only the stable id of a recent-list entry —
  // never a path. main.js resolves the id against its own recent.json.
  openRecent: (id) => ipcRenderer.invoke('recent:open', id),
  // Drag-and-drop open only: `file` must be the real File object handed to
  // the renderer's 'drop' DOM event handler (dataTransfer.files[0]) — NOT a
  // path string. A renderer script (including XSS/compromised-dependency
  // script, or a raw CDP Runtime.evaluate call) can trivially call this
  // bridge function with any string it likes, so a previous version of this
  // API that accepted a bare path string was a full arbitrary-file-read
  // primitive (any string in, loadDocumentFromPath() on it, contents back
  // out) — see git history. The fix: resolve the real on-disk path HERE,
  // inside preload (a context the renderer's own JS cannot reach into or
  // monkey-patch), via webUtils.getPathForFile(). That call only returns a
  // non-empty path for a File object Chromium itself created with a real
  // backing file — e.g. from a genuine OS-level drag-and-drop or a native
  // <input type=file> selection. A script-fabricated `new File([...], name)`
  // has no such backing and resolves to ''. We reject empty/invalid
  // resolutions before ever invoking the main-process IPC handler, so no
  // renderer-chosen string can reach the filesystem-read code path here.
  // Do not change this back to accepting a plain path string.
  openDroppedFile: (file) => {
    let filePath;
    try {
      filePath = webUtils.getPathForFile(file);
    } catch (err) {
      filePath = '';
    }
    if (!filePath) return Promise.resolve({ error: 'Invalid file.' });
    return ipcRenderer.invoke('file:openDropped', filePath);
  },
  // Save takes content/options only — no filePath. main.js writes to the
  // path it is itself tracking, prompting via Save As internally the first
  // time a document is saved. See main.js's file:save handler.
  saveFile: (payload) => ipcRenderer.invoke('file:save', payload),
  saveFileAs: (payload) => ipcRenderer.invoke('file:saveAs', payload),
  // Tells main.js the current document is now untitled (New / a template),
  // so it resets the path it would otherwise write Save to.
  newDocument: () => ipcRenderer.invoke('doc:new'),
  getRecentFiles: () => ipcRenderer.invoke('recent:list'),

  exportPdf: (payload) => ipcRenderer.invoke('export:pdf', payload),
  exportDocx: (payload) => ipcRenderer.invoke('export:docx', payload),
  exportMarkdown: (payload) => ipcRenderer.invoke('export:markdown', payload),
  exportTxt: (payload) => ipcRenderer.invoke('export:txt', payload),
  print: (payload) => ipcRenderer.invoke('app:print', payload),

  insertImage: () => ipcRenderer.invoke('dialog:insertImage'),

  // Help ▸ About's version string — see main.js's app:getVersion handler.
  getAppVersion: () => ipcRenderer.invoke('app:getVersion'),

  // Autosave / crash recovery — a separate recovery-only snapshot main.js
  // keeps under userData/autosave, never the user's real document file.
  // See the comments above main.js's currentDocKey/AUTOSAVE_DIR and
  // fileio.js's checkForRecovery()/initFileIO() for the full flow.
  autosaveWrite: (payload) => ipcRenderer.invoke('autosave:write', payload),
  findRecoverableAutosaves: () => ipcRenderer.invoke('autosave:findRecoverable'),
  loadAutosaveSnapshot: (key) => ipcRenderer.invoke('autosave:loadSnapshot', key),
  discardAutosave: (key) => ipcRenderer.invoke('autosave:discard', key),

  onRequestCloseCheck: (callback) => {
    ipcRenderer.on('app:request-close-check', () => callback());
  },
  sendCloseResponse: (shouldClose) => {
    ipcRenderer.send('app:close-response', shouldClose);
  },
});
