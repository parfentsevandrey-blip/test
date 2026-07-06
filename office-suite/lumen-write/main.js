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
  const filePath = result.filePaths[0];
  const ext = path.extname(filePath).toLowerCase();

  try {
    if (ext === '.lwrite') {
      const { contentHTML, title } = readLwrite(filePath);
      return { filePath, title, contentHTML, format: 'lwrite', warnings: [] };
    }
    if (ext === '.html' || ext === '.htm') {
      const contentHTML = fs.readFileSync(filePath, 'utf8');
      return { filePath: null, title: titleFromPath(filePath), contentHTML, format: 'html', warnings: [] };
    }
    if (ext === '.txt') {
      const raw = fs.readFileSync(filePath, 'utf8');
      const escape = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      const contentHTML = raw
        .split(/\r?\n/)
        .map((line) => `<p>${escape(line) || '<br>'}</p>`)
        .join('');
      return { filePath: null, title: titleFromPath(filePath), contentHTML, format: 'txt', warnings: [] };
    }
    if (ext === '.docx') {
      const mammoth = require('mammoth');
      const out = await mammoth.convertToHtml({ path: filePath });
      const warnings = (out.messages || []).map((m) => m.message);
      return { filePath: null, title: titleFromPath(filePath), contentHTML: out.value, format: 'docx', warnings };
    }
    return { filePath: null, title: titleFromPath(filePath), contentHTML: fs.readFileSync(filePath, 'utf8'), format: 'html', warnings: [] };
  } catch (err) {
    return { error: String(err && err.message ? err.message : err) };
  }
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
  return { filePath: targetPath };
});

ipcMain.handle('file:saveAs', async (event, payload) => {
  const { title, contentHTML } = payload;
  const result = await dialog.showSaveDialog(win, {
    title: 'Save document as',
    defaultPath: `${title || 'Untitled document'}.lwrite`,
    filters: [{ name: 'Lumen Write document', extensions: ['lwrite'] }],
  });
  if (result.canceled || !result.filePath) return null;
  const now = new Date().toISOString();
  const data = { version: 1, title: title || 'Untitled document', contentHTML: contentHTML || '', createdAt: now, modifiedAt: now };
  fs.writeFileSync(result.filePath, JSON.stringify(data, null, 2), 'utf8');
  return { filePath: result.filePath };
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

ipcMain.handle('export:txt', async (event, payload) => {
  const { title, text } = payload;
  const result = await dialog.showSaveDialog(win, {
    title: 'Export as plain text',
    defaultPath: `${title || 'Untitled document'}.txt`,
    filters: [{ name: 'Plain text', extensions: ['txt'] }],
  });
  if (result.canceled || !result.filePath) return null;
  fs.writeFileSync(result.filePath, text || '', 'utf8');
  return { filePath: result.filePath };
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
