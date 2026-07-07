// main.js — Electron main process. CommonJS only (no import/export here).
// Owns: BrowserWindow creation, native file dialogs, and all npm-package
// file conversions (xlsx / csv) — the renderer cannot require() npm
// packages, so every conversion happens here and is exposed via IPC.

const { app, BrowserWindow, Menu, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

let XLSX = null;
function getXlsx() {
  if (!XLSX) XLSX = require('xlsx');
  return XLSX;
}

let mainWindow = null;

// ---------------------------------------------------------------------------
// Recent files (last 8, de-duped by path)
// ---------------------------------------------------------------------------

function recentFilePath() {
  return path.join(app.getPath('userData'), 'recent.json');
}

function readRecent() {
  try {
    const text = fs.readFileSync(recentFilePath(), 'utf8');
    const list = JSON.parse(text);
    return Array.isArray(list) ? list : [];
  } catch (e) {
    return [];
  }
}

function writeRecent(list) {
  try {
    fs.writeFileSync(recentFilePath(), JSON.stringify(list, null, 2), 'utf8');
  } catch (e) {
    // Non-fatal — recent files is a convenience feature.
  }
}

function addRecent(filePath) {
  let list = readRecent().filter((entry) => entry.path !== filePath);
  list.unshift({ path: filePath, openedAt: Date.now() });
  list = list.slice(0, 8);
  writeRecent(list);
  return list;
}

ipcMain.handle('recent:get', () => readRecent());
ipcMain.handle('recent:add', (event, filePath) => addRecent(filePath));

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: '#faf9f7',
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#faf9f7', symbolColor: '#1b1a17', height: 40 },
    icon: path.join(__dirname, 'build', 'icon.ico'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  Menu.setApplicationMenu(null);
  mainWindow.loadFile(path.join(__dirname, 'index.html'));

  mainWindow.on('close', (e) => {
    // The renderer owns the dirty-flag confirm dialog; ask it to check before
    // we actually let the window close.
    if (mainWindow && !mainWindow.__forceClose) {
      e.preventDefault();
      mainWindow.webContents.send('app:before-close');
    }
  });
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

ipcMain.on('app:confirm-close', () => {
  if (mainWindow) {
    mainWindow.__forceClose = true;
    mainWindow.close();
  }
});

// ---------------------------------------------------------------------------
// Native file pickers
// ---------------------------------------------------------------------------

ipcMain.handle('dialog:open-lsheet', async () => {
  const res = await dialog.showOpenDialog(mainWindow, {
    title: 'Open Spreadsheet',
    filters: [{ name: 'Lumen Sheet', extensions: ['lsheet'] }],
    properties: ['openFile'],
  });
  if (res.canceled || res.filePaths.length === 0) return null;
  return res.filePaths[0];
});

ipcMain.handle('dialog:save-lsheet', async (event, defaultName) => {
  const res = await dialog.showSaveDialog(mainWindow, {
    title: 'Save Spreadsheet',
    defaultPath: defaultName || 'Untitled spreadsheet.lsheet',
    filters: [{ name: 'Lumen Sheet', extensions: ['lsheet'] }],
  });
  if (res.canceled || !res.filePath) return null;
  return res.filePath;
});

ipcMain.handle('dialog:open-import', async (event, kind) => {
  const filters =
    kind === 'csv' ? [{ name: 'CSV', extensions: ['csv'] }] : [{ name: 'Excel Workbook', extensions: ['xlsx'] }];
  const res = await dialog.showOpenDialog(mainWindow, { title: 'Import', filters, properties: ['openFile'] });
  if (res.canceled || res.filePaths.length === 0) return null;
  return res.filePaths[0];
});

ipcMain.handle('dialog:save-export', async (event, kind, defaultName) => {
  const filters =
    kind === 'csv'
      ? [{ name: 'CSV', extensions: ['csv'] }]
      : kind === 'pdf'
        ? [{ name: 'PDF Document', extensions: ['pdf'] }]
        : [{ name: 'Excel Workbook', extensions: ['xlsx'] }];
  const res = await dialog.showSaveDialog(mainWindow, {
    title: 'Export',
    defaultPath: defaultName,
    filters,
  });
  if (res.canceled || !res.filePath) return null;
  return res.filePath;
});

// ---------------------------------------------------------------------------
// Native .lsheet read/write (plain JSON)
// ---------------------------------------------------------------------------

ipcMain.handle('file:read-lsheet', async (event, filePath) => {
  try {
    const text = fs.readFileSync(filePath, 'utf8');
    return { ok: true, data: JSON.parse(text), filePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

ipcMain.handle('file:write-lsheet', async (event, filePath, data) => {
  try {
    fs.writeFileSync(filePath, JSON.stringify(data, null, 2), 'utf8');
    return { ok: true, filePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

// ---------------------------------------------------------------------------
// XLSX export / import (SheetJS Community Edition, "xlsx" package)
// ---------------------------------------------------------------------------

// workbookData: { sheets: [{ name, cells: { ref: { raw, computed } }, rowCount, colCount }] }
ipcMain.handle('file:export-xlsx', async (event, filePath, workbookData) => {
  try {
    const xlsx = getXlsx();
    const wb = xlsx.utils.book_new();
    for (const sheet of workbookData.sheets) {
      const aoa = [];
      for (let r = 0; r < sheet.rowCount; r++) {
        const row = [];
        for (let c = 0; c < sheet.colCount; c++) row.push(undefined);
        aoa.push(row);
      }
      const ws = xlsx.utils.aoa_to_sheet(aoa);
      for (const ref of Object.keys(sheet.cells)) {
        const cellData = sheet.cells[ref];
        if (cellData.raw === '' || cellData.raw === undefined || cellData.raw === null) continue;
        const cellObj = {};
        if (typeof cellData.raw === 'string' && cellData.raw.startsWith('=')) {
          cellObj.f = cellData.raw.slice(1);
          if (typeof cellData.computed === 'number') {
            cellObj.t = 'n';
            cellObj.v = cellData.computed;
          } else if (typeof cellData.computed === 'string') {
            cellObj.t = 's';
            cellObj.v = cellData.computed;
          }
        } else {
          const n = Number(cellData.raw);
          if (cellData.raw !== '' && !Number.isNaN(n) && /^[+-]?(\d+\.?\d*|\.\d+)$/.test(String(cellData.raw).trim())) {
            cellObj.t = 'n';
            cellObj.v = n;
          } else {
            cellObj.t = 's';
            cellObj.v = String(cellData.raw);
          }
        }
        ws[ref] = cellObj;
      }
      xlsx.utils.book_append_sheet(wb, ws, sheet.name.slice(0, 31));
    }
    xlsx.writeFile(wb, filePath);
    return { ok: true, filePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

ipcMain.handle('file:import-xlsx', async (event, filePath) => {
  try {
    const xlsx = getXlsx();
    const wb = xlsx.readFile(filePath);
    const sheets = wb.SheetNames.map((name) => {
      const ws = wb.Sheets[name];
      const cells = {};
      const range = xlsx.utils.decode_range(ws['!ref'] || 'A1:A1');
      for (const ref of Object.keys(ws)) {
        if (ref.startsWith('!')) continue;
        const cell = ws[ref];
        // We deliberately ignore SheetJS's cached computed value (cell.v for
        // formula cells) and recompute everything ourselves — see README.
        let raw = '';
        if (cell.f) raw = '=' + cell.f;
        else if (cell.v !== undefined) raw = String(cell.v);
        cells[ref] = raw;
      }
      return {
        name,
        cells,
        rowCount: Math.max(100, range.e.r + 2),
        colCount: Math.max(26, range.e.c + 2),
      };
    });
    return { ok: true, sheets };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

// ---------------------------------------------------------------------------
// CSV export / import (via the same "xlsx" package's csv helpers)
// ---------------------------------------------------------------------------

ipcMain.handle('file:export-csv', async (event, filePath, sheetData) => {
  try {
    const xlsx = getXlsx();
    const aoa = [];
    for (let r = 0; r < sheetData.rowCount; r++) {
      const row = [];
      for (let c = 0; c < sheetData.colCount; c++) row.push('');
      aoa.push(row);
    }
    const ws = xlsx.utils.aoa_to_sheet(aoa);
    for (const ref of Object.keys(sheetData.cells)) {
      const cellData = sheetData.cells[ref];
      if (cellData.raw === '' || cellData.raw === undefined) continue;
      const display = cellData.display !== undefined ? cellData.display : cellData.raw;
      ws[ref] = { t: 's', v: display };
    }
    const csv = xlsx.utils.sheet_to_csv(ws);
    fs.writeFileSync(filePath, csv, 'utf8');
    return { ok: true, filePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

ipcMain.handle('file:import-csv', async (event, filePath) => {
  try {
    const xlsx = getXlsx();
    const text = fs.readFileSync(filePath, 'utf8');
    const ws = xlsx.utils.csv_to_sheet(text);
    const cells = {};
    const range = xlsx.utils.decode_range(ws['!ref'] || 'A1:A1');
    for (const ref of Object.keys(ws)) {
      if (ref.startsWith('!')) continue;
      const cell = ws[ref];
      cells[ref] = cell.v !== undefined ? String(cell.v) : '';
    }
    return {
      ok: true,
      cells,
      rowCount: Math.max(100, range.e.r + 2),
      colCount: Math.max(26, range.e.c + 2),
    };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

// ---------------------------------------------------------------------------
// PDF export (File > Page Setup + Export > PDF / File > Print)
// ---------------------------------------------------------------------------
// The renderer builds an off-screen "print-root" DOM node reproducing just
// the print area (see src/renderer.js), scaled to fit one page, and a
// dynamic `@page` CSS rule for the chosen size/orientation. We then render
// *this already-loaded page* to PDF with `preferCSSPageSize: true` so
// Chromium's print pipeline follows that `@page` rule instead of the
// pageSize/landscape options — single-page scale-to-fit, not Excel's full
// page-break-preview/tiling system (documented scope cut in the README).
ipcMain.handle('file:export-pdf', async (event, filePath, options) => {
  try {
    if (!mainWindow) return { ok: false, error: 'No window' };
    const data = await mainWindow.webContents.printToPDF({ printBackground: true, preferCSSPageSize: true, ...options });
    fs.writeFileSync(filePath, data);
    return { ok: true, filePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});
