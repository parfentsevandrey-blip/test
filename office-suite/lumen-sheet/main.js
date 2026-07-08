// main.js — Electron main process. CommonJS only (no import/export here).
// Owns: BrowserWindow creation, native file dialogs, and all npm-package
// file conversions (xlsx / csv) — the renderer cannot require() npm
// packages, so every conversion happens here and is exposed via IPC.
//
// ---------------------------------------------------------------------------
// Trust boundary (security-critical — read before touching any handler below)
// ---------------------------------------------------------------------------
// main.js is the SOLE authority on which on-disk path is legitimate for the
// current read/write operation. No IPC handler that reads or writes a file
// may take a filePath argument straight from the renderer and trust it —
// every renderer is one XSS bug / compromised dependency / devtools session
// away from being fully attacker-controlled, and an arbitrary-path read or
// write is a full filesystem compromise.
//
// Every path used below comes from exactly one of:
//   1. A dialog.showOpenDialog/showSaveDialog result WE just requested and
//      awaited, inside the same handler invocation.
//   2. `currentFilePath`, a variable scoped to this module (one window/
//      session) that WE set — only ever assigned from (1), or from a
//      recent-list lookup (3), never copied from a renderer-supplied value.
//   3. A recent-files entry looked up by id in OUR OWN recent.json (which we
//      wrote), never a raw path the renderer hands us.
//   4. The one deliberate, narrowly-scoped exception: a genuine OS-level
//      drag-and-drop, whose path is resolved in preload.js via
//      webUtils.getPathForFile() from a real `File` object — never a bare
//      string accepted at face value (see the comment on
//      file:open-dropped-lsheet below for exactly why that one is safe).
//   5. An autosave recovery snapshot's `filePath` field (see the "Autosave /
//      crash recovery" section) — that field is only ever a value WE
//      previously wrote into the snapshot from an already-trusted
//      `currentFilePath`, so reading it back on recovery isn't a new
//      renderer-supplied-path case; the renderer never has a way to
//      influence which snapshot gets consumed or what path it names.
// ---------------------------------------------------------------------------

const { app, BrowserWindow, Menu, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

// ---------------------------------------------------------------------------
// Global error handling — an uncaught error anywhere in the main process
// used to either be silently swallowed or hard-crash the whole app with no
// user-facing message at all. Always log it; if it's bad enough to reach
// here uncaught, also fall back to a *native* dialog.showErrorBox — the one
// deliberate exception to "everything user-facing is the app's own
// .dialog-overlay component", because by the time we're in a process-level
// crash handler the renderer's own UI may not be available (or may be the
// very thing that's broken) to show anything at all.
// ---------------------------------------------------------------------------
process.on('uncaughtException', (err) => {
  console.error('[main] uncaughtException:', err);
  try {
    dialog.showErrorBox(
      'Lumen Sheet ran into a problem',
      'An unexpected error occurred. If you have unsaved changes, an autosave copy may be recoverable next time you open this document.\n\n' +
        String((err && err.stack) || err)
    );
  } catch (e) {
    // If even the native dialog fails, there's nothing left to try — the
    // console.error above is the last line of defense.
  }
});
process.on('unhandledRejection', (reason) => {
  // A rejected promise nobody awaited is recoverable far more often than a
  // genuinely fatal uncaughtException — log it, but don't put a native
  // dialog in front of the user for every stray rejection.
  console.error('[main] unhandledRejection:', reason);
});

// Chromium's native-window-occlusion tracking can misdetect an occluded/
// backgrounded window on some Linux compositors (and headless/virtual
// displays) and suspend page timers as a result — same underlying concern as
// `backgroundThrottling: false` below: the renderer's periodic autosave
// timer must keep running even if the OS thinks the window isn't visible,
// or a crash-recovery "safety net" that quietly stops working the moment
// the window is minimized isn't much of one.
app.commandLine.appendSwitch('disable-features', 'CalculateNativeWinOcclusion');

let XLSX = null;
function getXlsx() {
  if (!XLSX) XLSX = require('xlsx');
  return XLSX;
}

let mainWindow = null;

// The current document's on-disk path, tracked entirely on the main-process
// side. null means "new/untitled document — Save must behave like Save As".
// Set to a real path only when a file is genuinely opened (dialog result,
// recent-list lookup, or a real drop) or a Save-As dialog completes. Never
// set from a bare renderer-supplied argument.
let currentFilePath = null;

// Identifies the current *never-saved* document for autosave purposes (see
// the "Autosave / crash recovery" section below). Regenerated every time we
// start a fresh untitled document (New, or an Excel/CSV import), so an
// abandoned untitled document's stray autosave snapshot doesn't get
// silently reused/overwritten by the next one.
let untitledSessionId = crypto.randomUUID();

function startNewUntitledSession() {
  currentFilePath = null;
  untitledSessionId = crypto.randomUUID();
}

// ---------------------------------------------------------------------------
// Recent files (last 8, de-duped by path) — main.js owns this file end to
// end. The renderer can only ever *read* the list (recent:get) or ask to
// open one of ITS entries *by id* (recent:open-by-id); it can never hand us
// a path to add, since that would let a compromised renderer plant an
// arbitrary path (e.g. an SSH key) into the list and then "open" it back out.
// ---------------------------------------------------------------------------

function recentFilePath() {
  return path.join(app.getPath('userData'), 'recent.json');
}

function readRecent() {
  try {
    const text = fs.readFileSync(recentFilePath(), 'utf8');
    const list = JSON.parse(text);
    if (!Array.isArray(list)) return [];
    let changed = false;
    const withIds = list
      .map((entry) => {
        if (!entry || typeof entry.path !== 'string') return null;
        if (typeof entry.id !== 'string') {
          changed = true;
          return { ...entry, id: crypto.randomUUID() };
        }
        return entry;
      })
      .filter(Boolean);
    if (changed) writeRecent(withIds);
    return withIds;
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
  list.unshift({ id: crypto.randomUUID(), path: filePath, openedAt: Date.now() });
  list = list.slice(0, 8);
  writeRecent(list);
  return list;
}

ipcMain.handle('recent:get', () => readRecent());

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
      // The renderer's periodic autosave (crash-recovery snapshot, see
      // preload.js's `autosave` bridge) relies on a plain setInterval. Chromium
      // throttles/suspends timers in a backgrounded (minimized/occluded) page
      // to save power — fine for most apps, but here it would mean autosave
      // silently stops protecting the user's work the moment the window loses
      // visibility, which defeats the point of a crash-recovery safety net.
      backgroundThrottling: false,
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
// New document — renderer calls this whenever it starts editing a workbook
// that isn't backed by any on-disk file yet (File > New / applying a start
// screen template), so main.js's notion of "current path" stays in sync and
// the next Save correctly falls through to Save As.
// ---------------------------------------------------------------------------

ipcMain.handle('file:new-document', () => {
  startNewUntitledSession();
  return { ok: true };
});

// ---------------------------------------------------------------------------
// Autosave / crash recovery
// ---------------------------------------------------------------------------
// A periodic recovery *copy*, entirely separate from the user's real
// document file — written only here, under
// app.getPath('userData')/autosave/, and never read/written by the normal
// Open/Save flows above. Keyed to the current document: its real on-disk
// path if it has one, or `untitledSessionId` (regenerated per fresh
// untitled document, see startNewUntitledSession()) if it doesn't yet.
//
// Lifecycle:
//   - The renderer asks us to write a snapshot periodically while dirty
//     (autosave:save) — this NEVER touches currentFilePath's real file.
//   - On a clean Save/Save As, we delete the snapshot for whatever key was
//     current *before* that save (autosave:save handlers below).
//   - On app launch, autosave:check-recovery scans this directory for any
//     snapshot that's either orphaned (an untitled document's, which only
//     survives if nothing ever cleaned it up) or newer than its real
//     document's on-disk mtime (a path-keyed one written after the last
//     real save) and offers exactly one — the most recent — for recovery.
//   - autosave:recover consumes (and deletes) that snapshot; autosave:clear
//     / autosave:discard-recovery just delete it.
// So an autosave file surviving to the next launch really does only mean
// "the app didn't shut down cleanly last time" — every clean path removes it.

function autosaveDir() {
  const dir = path.join(app.getPath('userData'), 'autosave');
  try {
    fs.mkdirSync(dir, { recursive: true });
  } catch (e) {
    // Fall through — the write itself will fail below and report an error.
  }
  return dir;
}

function autosaveKind() {
  return currentFilePath ? 'path' : 'untitled';
}
function autosaveKeyValue() {
  return currentFilePath || untitledSessionId;
}
function autosaveFileFor(kind, keyValue) {
  const hash = crypto.createHash('sha1').update(`${kind}:${keyValue}`).digest('hex');
  return path.join(autosaveDir(), `${hash}.json`);
}
function currentAutosaveFile() {
  return autosaveFileFor(autosaveKind(), autosaveKeyValue());
}
function deleteAutosaveFile(file) {
  try {
    if (fs.existsSync(file)) fs.unlinkSync(file);
  } catch (e) {
    // Non-fatal — autosave cleanup is a hygiene step, not a correctness one.
  }
}

// Scans every stored snapshot and returns the single most-recently-written
// one that qualifies as "recoverable" (see the lifecycle comment above), or
// null. Cached on `pendingRecoveryCandidate` between check-recovery and
// whichever of recover/discard-recovery the user picks, so both act on
// exactly the file that was actually offered even if the directory changes
// in between (it shouldn't, but this is a single-shot boot-time flow).
let pendingRecoveryCandidate = null;

function findRecoveryCandidate() {
  const dir = autosaveDir();
  let entries;
  try {
    entries = fs.readdirSync(dir);
  } catch (e) {
    return null;
  }
  let best = null;
  for (const name of entries) {
    if (!name.endsWith('.json')) continue;
    const full = path.join(dir, name);
    let snap;
    try {
      snap = JSON.parse(fs.readFileSync(full, 'utf8'));
    } catch (e) {
      continue; // a half-written autosave file itself — ignore, don't crash the scan
    }
    if (!snap || typeof snap !== 'object' || typeof snap.savedAt !== 'number') continue;

    let eligible = false;
    if (snap.kind === 'untitled') {
      // A leftover untitled-document snapshot is orphaned by construction —
      // every clean path (Save, non-dirty close) deletes it.
      eligible = true;
    } else if (snap.kind === 'path' && typeof snap.filePath === 'string') {
      try {
        const stat = fs.statSync(snap.filePath);
        eligible = snap.savedAt > stat.mtimeMs;
      } catch (e) {
        // Real file missing/unreadable now — the autosave is still the only
        // record of that content, so still offer it.
        eligible = true;
      }
    }
    if (eligible && (!best || snap.savedAt > best.savedAt)) {
      best = { ...snap, file: full };
    }
  }
  return best;
}

ipcMain.handle('autosave:check-recovery', () => {
  pendingRecoveryCandidate = findRecoveryCandidate();
  if (!pendingRecoveryCandidate) return { found: false };
  return { found: true, filePath: pendingRecoveryCandidate.filePath || null, savedAt: pendingRecoveryCandidate.savedAt };
});

ipcMain.handle('autosave:recover', () => {
  const candidate = pendingRecoveryCandidate;
  pendingRecoveryCandidate = null;
  if (!candidate) return { ok: false, error: 'No recovery snapshot available.' };
  if (candidate.kind === 'path' && typeof candidate.filePath === 'string') {
    currentFilePath = candidate.filePath;
  } else {
    startNewUntitledSession();
  }
  deleteAutosaveFile(candidate.file);
  return { ok: true, data: candidate.data, filePath: currentFilePath };
});

ipcMain.handle('autosave:discard-recovery', () => {
  const candidate = pendingRecoveryCandidate;
  pendingRecoveryCandidate = null;
  if (candidate) deleteAutosaveFile(candidate.file);
  return { ok: true };
});

// Periodic write from the renderer — content only, no path. We decide where
// it goes (see currentAutosaveFile()); the renderer never sees or supplies
// the recovery-file path itself.
ipcMain.handle('autosave:save', (event, data) => {
  try {
    const snap = { kind: autosaveKind(), filePath: currentFilePath, savedAt: Date.now(), data };
    fs.writeFileSync(currentAutosaveFile(), JSON.stringify(snap), 'utf8');
    return { ok: true, savedAt: snap.savedAt };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

// Called by the renderer on a clean (non-dirty) window close — see
// app:before-close / app:confirm-close below and handleBeforeClose() in
// renderer.js.
ipcMain.handle('autosave:clear', () => {
  deleteAutosaveFile(currentAutosaveFile());
  return { ok: true };
});

// ---------------------------------------------------------------------------
// Native .lsheet read/write (plain JSON)
// ---------------------------------------------------------------------------

function readLsheetFromDisk(filePath) {
  let text;
  try {
    text = fs.readFileSync(filePath, 'utf8');
  } catch (e) {
    // fs errors (ENOENT/EACCES/EISDIR/...) only describe metadata, never
    // file contents, so it's fine to pass e.message through as-is.
    return { ok: false, error: String(e.message || e) };
  }
  let data;
  try {
    data = JSON.parse(text);
  } catch (e) {
    // Deliberately NOT e.message here: V8's JSON.parse SyntaxError embeds a
    // snippet of the offending input text. For the one path in this file
    // that reads a renderer-supplied path (the drag-and-drop exception,
    // below) that would leak a fragment of an arbitrary file's contents
    // back into the renderer even on the failure path. Keep this generic.
    return { ok: false, error: "This file couldn't be opened — it may be corrupted or in an unrecognized format." };
  }
  currentFilePath = filePath;
  addRecent(filePath);
  // We now have fresh, authoritative content straight from disk for this
  // exact path — any older autosave snapshot for it (e.g. left over from a
  // crash that happened *before* this successful open) is superseded.
  deleteAutosaveFile(autosaveFileFor('path', filePath));
  return { ok: true, data, filePath };
}

// File > Open: show the picker ourselves and read the result of THAT SAME
// dialog call — the renderer never gets a chance to supply a path in between.
ipcMain.handle('file:open-lsheet', async () => {
  const res = await dialog.showOpenDialog(mainWindow, {
    title: 'Open Spreadsheet',
    filters: [{ name: 'Lumen Sheet', extensions: ['lsheet'] }],
    properties: ['openFile'],
  });
  if (res.canceled || res.filePaths.length === 0) return { ok: false, canceled: true };
  return readLsheetFromDisk(res.filePaths[0]);
});

// File > Open Recent: the renderer sends only a stable id, never a path. We
// look the real path up ourselves in our own recent.json.
ipcMain.handle('recent:open-by-id', async (event, id) => {
  if (typeof id !== 'string' || !id) return { ok: false, error: 'Invalid recent-file reference.' };
  const entry = readRecent().find((e) => e.id === id);
  if (!entry) return { ok: false, error: 'That recent file is no longer available.' };
  return readLsheetFromDisk(entry.path);
});

// ---------------------------------------------------------------------------
// Drag-and-drop open — the ONE deliberate exception to "never trust a
// renderer-supplied path". By the time a filePath reaches THIS handler it is
// just a string like any other IPC argument — main.js itself cannot tell
// whether it came from a real OS-level drop or was typed by an attacker. The
// actual enforcement lives one layer up, in preload.js's openDroppedLsheet:
// it accepts only a real `File` object and resolves the path via Electron's
// webUtils.getPathForFile(), which throws for a non-File and returns '' for
// a File fabricated in page JS that isn't backed by a real on-disk file. The
// renderer (page world) never gets a function it can call with a bare string
// to reach this handler — contextIsolation + nodeIntegration:false also mean
// it has no other way to reach ipcRenderer.invoke directly. Do not add a
// second bridge/IPC path that accepts a filePath string for this feature,
// and do not weaken the preload-side File check — that check is the whole
// reason this handler is safe to keep taking a string.
// ---------------------------------------------------------------------------
ipcMain.handle('file:open-dropped-lsheet', async (event, filePath) => {
  if (typeof filePath !== 'string' || !filePath) return { ok: false, error: 'Invalid file.' };
  return readLsheetFromDisk(filePath);
});

async function saveAsFlow(data, defaultName) {
  // Capture the key any pending autosave snapshot is currently filed under
  // (very likely the untitled-session key, since Save As on an
  // already-saved document is rare) BEFORE currentFilePath changes below.
  const priorAutosaveFile = currentAutosaveFile();
  const res = await dialog.showSaveDialog(mainWindow, {
    title: 'Save Spreadsheet',
    defaultPath: defaultName || 'Untitled spreadsheet.lsheet',
    filters: [{ name: 'Lumen Sheet', extensions: ['lsheet'] }],
  });
  if (res.canceled || !res.filePath) return { ok: false, canceled: true };
  try {
    fs.writeFileSync(res.filePath, JSON.stringify(data, null, 2), 'utf8');
    currentFilePath = res.filePath;
    addRecent(res.filePath);
    // A clean, explicit save means there's no longer anything to recover —
    // clear both the old (pre-save) key's snapshot and any stale snapshot
    // that might already exist under the newly-saved path.
    deleteAutosaveFile(priorAutosaveFile);
    deleteAutosaveFile(currentAutosaveFile());
    return { ok: true, filePath: res.filePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
}

// Save: content only, no filePath. Writes to the path WE are tracking; if
// there isn't one yet (never-saved document) it runs the exact same flow as
// Save As. This is what makes repeated Ctrl+S on an already-saved file a
// silent, dialog-free write — the normal-use case the fix must preserve.
ipcMain.handle('file:save', async (event, data, defaultName) => {
  if (!currentFilePath) return saveAsFlow(data, defaultName);
  try {
    fs.writeFileSync(currentFilePath, JSON.stringify(data, null, 2), 'utf8');
    addRecent(currentFilePath);
    // Clean save — no reason to keep a recovery copy for this document.
    deleteAutosaveFile(currentAutosaveFile());
    return { ok: true, filePath: currentFilePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

// Save As: ALWAYS shows its own dialog and writes only to that dialog's
// result — never a renderer-supplied path.
ipcMain.handle('file:save-as', async (event, data, defaultName) => saveAsFlow(data, defaultName));

// ---------------------------------------------------------------------------
// XLSX export / import (SheetJS Community Edition, "xlsx" package)
// ---------------------------------------------------------------------------

// workbookData: { sheets: [{ name, cells: { ref: { raw, computed } }, rowCount, colCount }] }
ipcMain.handle('file:export-xlsx', async (event, workbookData, defaultName) => {
  const res = await dialog.showSaveDialog(mainWindow, {
    title: 'Export',
    defaultPath: defaultName,
    filters: [{ name: 'Excel Workbook', extensions: ['xlsx'] }],
  });
  if (res.canceled || !res.filePath) return { ok: false, canceled: true };
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
    xlsx.writeFile(wb, res.filePath);
    return { ok: true, filePath: res.filePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

// Import Excel: show the picker ourselves and parse the result of THAT SAME
// dialog call. A fresh import always starts a new, not-yet-saved document —
// so we also reset the tracked current path, matching the renderer's own
// "imported content is untitled" bookkeeping.
ipcMain.handle('file:import-xlsx', async () => {
  const dlg = await dialog.showOpenDialog(mainWindow, {
    title: 'Import',
    filters: [{ name: 'Excel Workbook', extensions: ['xlsx'] }],
    properties: ['openFile'],
  });
  if (dlg.canceled || dlg.filePaths.length === 0) return { ok: false, canceled: true };
  const filePath = dlg.filePaths[0];
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
    startNewUntitledSession();
    return { ok: true, sheets, filePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

// ---------------------------------------------------------------------------
// CSV export / import (via the same "xlsx" package's csv helpers)
// ---------------------------------------------------------------------------

ipcMain.handle('file:export-csv', async (event, sheetData, defaultName) => {
  const res = await dialog.showSaveDialog(mainWindow, {
    title: 'Export',
    defaultPath: defaultName,
    filters: [{ name: 'CSV', extensions: ['csv'] }],
  });
  if (res.canceled || !res.filePath) return { ok: false, canceled: true };
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
    fs.writeFileSync(res.filePath, csv, 'utf8');
    return { ok: true, filePath: res.filePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});

ipcMain.handle('file:import-csv', async () => {
  const dlg = await dialog.showOpenDialog(mainWindow, {
    title: 'Import',
    filters: [{ name: 'CSV', extensions: ['csv'] }],
    properties: ['openFile'],
  });
  if (dlg.canceled || dlg.filePaths.length === 0) return { ok: false, canceled: true };
  const filePath = dlg.filePaths[0];
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
    startNewUntitledSession();
    return {
      ok: true,
      cells,
      rowCount: Math.max(100, range.e.r + 2),
      colCount: Math.max(26, range.e.c + 2),
      filePath,
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
ipcMain.handle('file:export-pdf', async (event, options, defaultName) => {
  const res = await dialog.showSaveDialog(mainWindow, {
    title: 'Export',
    defaultPath: defaultName,
    filters: [{ name: 'PDF Document', extensions: ['pdf'] }],
  });
  if (res.canceled || !res.filePath) return { ok: false, canceled: true };
  try {
    if (!mainWindow) return { ok: false, error: 'No window' };
    const data = await mainWindow.webContents.printToPDF({ printBackground: true, preferCSSPageSize: true, ...options });
    fs.writeFileSync(res.filePath, data);
    return { ok: true, filePath: res.filePath };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
});
