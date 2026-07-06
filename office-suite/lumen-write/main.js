// main.js — Electron main process (CommonJS). No import/export syntax here.
'use strict';

const { app, BrowserWindow, Menu, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

let win = null;

// Set to true right before we actually want the window to close, so the
// 'close' handler below doesn't intercept it a second time.
let allowClose = false;

function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: '#faf9f7',
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#faf9f7', symbolColor: '#1b1a17', height: 40 },
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  Menu.setApplicationMenu(null);
  win.loadFile('index.html');

  win.on('close', (e) => {
    if (allowClose) return;
    e.preventDefault();
    win.webContents.send('app:request-close-check');
  });

  win.on('closed', () => {
    win = null;
  });
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

// Renderer tells us whether it's safe to actually close the window
// (after showing its own in-app "unsaved changes" dialog, if needed).
ipcMain.on('app:close-response', (event, shouldClose) => {
  if (shouldClose) {
    allowClose = true;
    if (win) win.close();
  }
});

// ---------- File I/O helpers ----------

function readLwrite(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8');
  const data = JSON.parse(raw);
  return {
    contentHTML: data.contentHTML || '',
    title: data.title || path.basename(filePath, path.extname(filePath)),
  };
}

function titleFromPath(filePath) {
  return path.basename(filePath, path.extname(filePath));
}

// ---------- Recent files ----------
// A small JSON file in userData: an array of the last RECENT_LIMIT
// {path, title, openedAt} entries, most-recent-first, de-duplicated by
// path. Kept entirely in the main process; the renderer only ever reads
// it through the recent:list IPC handler below.

const RECENT_LIMIT = 8;
const RECENT_PATH = path.join(app.getPath('userData'), 'recent.json');

function loadRecent() {
  try {
    const raw = fs.readFileSync(RECENT_PATH, 'utf8');
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch (err) {
    return [];
  }
}

function saveRecentList(list) {
  try {
    fs.mkdirSync(path.dirname(RECENT_PATH), { recursive: true });
    fs.writeFileSync(RECENT_PATH, JSON.stringify(list, null, 2), 'utf8');
  } catch (err) {
    console.error('Failed to write recent.json', err);
  }
}

function addRecent(filePath, title) {
  if (!filePath) return;
  let list = loadRecent().filter((entry) => entry && entry.path !== filePath);
  list.unshift({ path: filePath, title: title || titleFromPath(filePath), openedAt: new Date().toISOString() });
  list = list.slice(0, RECENT_LIMIT);
  saveRecentList(list);
}

function removeFromRecent(filePath) {
  const list = loadRecent().filter((entry) => entry && entry.path !== filePath);
  saveRecentList(list);
}

ipcMain.handle('recent:list', () => loadRecent());

// ---------- Open (dialog-picked path or a known path) ----------

/** Reads a document from an already-known path (used by the Open dialog,
 * File ▸ Open Recent, and drag-and-drop) and records it in recent.json.
 * Returns { error, missing? } if the file can't be read, in which case a
 * missing recent entry is pruned automatically. */
async function loadDocumentFromPath(filePath) {
  if (!fs.existsSync(filePath)) {
    removeFromRecent(filePath);
    return { error: `"${titleFromPath(filePath)}" no longer exists at its saved location.`, missing: true };
  }
  const ext = path.extname(filePath).toLowerCase();
  try {
    let contentHTML;
    let title = titleFromPath(filePath);
    let format;
    let warnings = [];

    if (ext === '.lwrite') {
      const loaded = readLwrite(filePath);
      contentHTML = loaded.contentHTML;
      title = loaded.title;
      format = 'lwrite';
    } else if (ext === '.html' || ext === '.htm') {
      contentHTML = fs.readFileSync(filePath, 'utf8');
      format = 'html';
    } else if (ext === '.txt') {
      const raw = fs.readFileSync(filePath, 'utf8');
      const escape = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      contentHTML = raw
        .split(/\r?\n/)
        .map((line) => `<p>${escape(line) || '<br>'}</p>`)
        .join('');
      format = 'txt';
    } else if (ext === '.docx') {
      const mammoth = require('mammoth');
      const out = await mammoth.convertToHtml({ path: filePath });
      warnings = (out.messages || []).map((m) => m.message);
      contentHTML = out.value;
      format = 'docx';
    } else {
      contentHTML = fs.readFileSync(filePath, 'utf8');
      format = 'html';
    }

    addRecent(filePath, title);
    return { filePath: format === 'lwrite' ? filePath : null, title, contentHTML, format, warnings };
  } catch (err) {
    return { error: String(err && err.message ? err.message : err) };
  }
}

ipcMain.handle('file:open', async () => {
  const result = await dialog.showOpenDialog(win, {
    title: 'Open document',
    properties: ['openFile'],
    filters: [
      { name: 'All supported documents', extensions: ['lwrite', 'html', 'htm', 'txt', 'docx'] },
      { name: 'Lumen Write document', extensions: ['lwrite'] },
      { name: 'HTML document', extensions: ['html', 'htm'] },
      { name: 'Plain text', extensions: ['txt'] },
      { name: 'Word document', extensions: ['docx'] },
      { name: 'All files', extensions: ['*'] },
    ],
  });
  if (result.canceled || !result.filePaths.length) return null;
  return loadDocumentFromPath(result.filePaths[0]);
});

// Used by File ▸ Open Recent and drag-and-drop — same loading code path as
// the dialog-based Open above, just given a path directly.
ipcMain.handle('file:openPath', async (event, filePath) => {
  return loadDocumentFromPath(filePath);
});

ipcMain.handle('file:save', async (event, payload) => {
  const { filePath, title, contentHTML } = payload;
  let targetPath = filePath;
  if (!targetPath) {
    const result = await dialog.showSaveDialog(win, {
      title: 'Save document',
      defaultPath: `${title || 'Untitled document'}.lwrite`,
      filters: [{ name: 'Lumen Write document', extensions: ['lwrite'] }],
    });
    if (result.canceled || !result.filePath) return null;
    targetPath = result.filePath;
  }
  try {
    const now = new Date().toISOString();
    let createdAt = now;
    try {
      if (fs.existsSync(targetPath)) {
        const prev = JSON.parse(fs.readFileSync(targetPath, 'utf8'));
        if (prev && prev.createdAt) createdAt = prev.createdAt;
      }
    } catch (e) {
      /* ignore malformed existing file, just overwrite */
    }
    const data = { version: 1, title: title || 'Untitled document', contentHTML: contentHTML || '', createdAt, modifiedAt: now };
    fs.writeFileSync(targetPath, JSON.stringify(data, null, 2), 'utf8');
    addRecent(targetPath, title || 'Untitled document');
    return { filePath: targetPath };
  } catch (err) {
    return { error: String(err && err.message ? err.message : err) };
  }
});

ipcMain.handle('file:saveAs', async (event, payload) => {
  const { title, contentHTML } = payload;
  const result = await dialog.showSaveDialog(win, {
    title: 'Save document as',
    defaultPath: `${title || 'Untitled document'}.lwrite`,
    filters: [{ name: 'Lumen Write document', extensions: ['lwrite'] }],
  });
  if (result.canceled || !result.filePath) return null;
  try {
    const now = new Date().toISOString();
    const data = { version: 1, title: title || 'Untitled document', contentHTML: contentHTML || '', createdAt: now, modifiedAt: now };
    fs.writeFileSync(result.filePath, JSON.stringify(data, null, 2), 'utf8');
    addRecent(result.filePath, title || 'Untitled document');
    return { filePath: result.filePath };
  } catch (err) {
    return { error: String(err && err.message ? err.message : err) };
  }
});

ipcMain.handle('export:pdf', async (event, payload) => {
  const { title } = payload || {};
  try {
    const buffer = await win.webContents.printToPDF({});
    const result = await dialog.showSaveDialog(win, {
      title: 'Export as PDF',
      defaultPath: `${title || 'Untitled document'}.pdf`,
      filters: [{ name: 'PDF document', extensions: ['pdf'] }],
    });
    if (result.canceled || !result.filePath) return null;
    fs.writeFileSync(result.filePath, buffer);
    return { filePath: result.filePath };
  } catch (err) {
    return { error: String(err && err.message ? err.message : err) };
  }
});

ipcMain.handle('export:docx', async (event, payload) => {
  const { title, contentHTML } = payload;
  try {
    const htmlToDocx = require('html-to-docx');
    const buffer = await htmlToDocx(contentHTML || '', null, {
      title: title || 'Untitled document',
      font: 'Georgia',
    });
    const result = await dialog.showSaveDialog(win, {
      title: 'Export as Word document',
      defaultPath: `${title || 'Untitled document'}.docx`,
      filters: [{ name: 'Word document', extensions: ['docx'] }],
    });
    if (result.canceled || !result.filePath) return null;
    fs.writeFileSync(result.filePath, buffer);
    return { filePath: result.filePath };
  } catch (err) {
    return { error: String(err && err.message ? err.message : err) };
  }
});

ipcMain.handle('export:markdown', async (event, payload) => {
  const { title, contentHTML } = payload;
  try {
    const TurndownService = require('turndown');
    const turndownService = new TurndownService({ headingStyle: 'atx' });
    const markdown = turndownService.turndown(contentHTML || '');
    const result = await dialog.showSaveDialog(win, {
      title: 'Export as Markdown',
      defaultPath: `${title || 'Untitled document'}.md`,
      filters: [{ name: 'Markdown document', extensions: ['md'] }],
    });
    if (result.canceled || !result.filePath) return null;
    fs.writeFileSync(result.filePath, markdown, 'utf8');
    return { filePath: result.filePath };
  } catch (err) {
    return { error: String(err && err.message ? err.message : err) };
  }
});

ipcMain.handle('export:txt', async (event, payload) => {
  const { title, text } = payload;
  const result = await dialog.showSaveDialog(win, {
    title: 'Export as plain text',
    defaultPath: `${title || 'Untitled document'}.txt`,
    filters: [{ name: 'Plain text', extensions: ['txt'] }],
  });
  if (result.canceled || !result.filePath) return null;
  try {
    fs.writeFileSync(result.filePath, text || '', 'utf8');
    return { filePath: result.filePath };
  } catch (err) {
    return { error: String(err && err.message ? err.message : err) };
  }
});

ipcMain.handle('app:print', async () => {
  win.webContents.print({});
  return true;
});

ipcMain.handle('dialog:insertImage', async () => {
  const result = await dialog.showOpenDialog(win, {
    title: 'Insert image',
    properties: ['openFile'],
    filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'gif', 'webp'] }],
  });
  if (result.canceled || !result.filePaths.length) return null;
  const filePath = result.filePaths[0];
  const ext = path.extname(filePath).slice(1).toLowerCase();
  const mime = ext === 'jpg' ? 'jpeg' : ext;
  const buffer = fs.readFileSync(filePath);
  const dataUrl = `data:image/${mime};base64,${buffer.toString('base64')}`;
  return { dataUrl };
});
