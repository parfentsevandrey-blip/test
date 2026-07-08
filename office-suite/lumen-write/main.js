// main.js — Electron main process (CommonJS). No import/export syntax here.
'use strict';

const { app, BrowserWindow, Menu, ipcMain, dialog, screen } = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

// ---------- Single instance lock ----------
// Launching a second instance used to spin up a fully independent
// window/process tree sharing the same userData profile — among other
// problems, this caused a real reproduced data-loss race in recent.json
// (two windows doing an unsynchronized read-modify-write against the same
// file). requestSingleInstanceLock() makes the first-launched instance the
// sole owner of the userData profile for the lifetime of the app: any
// later launch attempt fails to acquire the lock, fires 'second-instance'
// on the ORIGINAL instance (handled below, once `win` exists, to focus it)
// and must quit itself immediately without ever creating a window or
// touching any on-disk state. This must run before anything else touches
// userData (recent.json, autosave, window-state.json, etc. below).
const gotSingleInstanceLock = app.requestSingleInstanceLock();
if (!gotSingleInstanceLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!win) return;
    if (win.isMinimized()) win.restore();
    if (!win.isVisible()) win.show();
    win.focus();
  });
}

// ---------- Global error handling ----------
// A bug anywhere in the main process used to either crash the whole app
// with no explanation or (inside an async IPC handler without its own
// try/catch) vanish into an unhandled rejection with zero trace. These are
// last-resort safety nets, not the primary error handling strategy — every
// IPC handler below still has its own try/catch that reports a proper
// { error } back to the renderer, which shows it in the app's own toast/
// dialog UI. dialog.showErrorBox is a NATIVE dialog — the one deliberate
// exception to "always use the app's own .dialog-overlay" — because by the
// time an uncaughtException fires, the renderer's own UI may be unavailable
// or the very thing that's broken.
process.on('uncaughtException', (err) => {
  console.error('Uncaught exception in main process:', err);
  try {
    dialog.showErrorBox(
      'Lumen Write — unexpected error',
      `Something went wrong and this action could not complete.\n\n${err && err.message ? err.message : err}`
    );
  } catch (boxErr) {
    // The dialog subsystem itself may not be available (e.g. very early/
    // late in the app lifecycle) — console.error above already logged it.
  }
});

process.on('unhandledRejection', (reason) => {
  console.error('Unhandled promise rejection in main process:', reason);
});

let win = null;

// Set to true right before we actually want the window to close, so the
// 'close' handler below doesn't intercept it a second time.
let allowClose = false;

// ---------- Trust boundary for file writes/reads ----------
// main.js is the SOLE authority on which on-disk path is legitimate for the
// current document. This is process-side state — never derived from a
// renderer-supplied IPC payload — set to null for a new/untitled document
// and to a real path only when a file is genuinely opened (dialog result,
// a validated recent-list lookup, or a real OS drop event) or a Save-As
// dialog completes. file:save writes to THIS path (or falls back to the
// Save-As flow if it's null); it never accepts a path argument from the
// renderer. See file:save/file:saveAs/loadDocumentFromPath/doc:new below.
let currentFilePath = null;

// ---------- Autosave / crash recovery ----------
// A separate recovery mechanism from the real document file: main.js
// periodically (at the renderer's request, throttled there — see
// fileio.js) writes a snapshot of the CURRENT document's content to a
// dedicated folder under userData, keyed by `currentDocKey` below. This
// snapshot is NEVER the same file as the user's real document — it is
// only ever consulted at the next launch to offer recovering content that
// never made it into a real Save (e.g. after a crash/kill -9). It is
// deleted the moment there's no longer any reason to believe data could be
// stranded: a clean Save, a clean New/Open (switching away from a
// document without a crash), or a clean app close.
//
// `currentDocKey` identifies the document currently being edited, exactly
// in lockstep with `currentFilePath` (see comment above), but survives an
// untitled/never-saved document too (which has no real path to key off
// of): `saved:<sha256 of the real path>` once a document has a path, or
// `untitled:<random uuid>` for a document that has never been saved this
// "document instance" (a fresh id is minted every time the document
// becomes untitled — New, a template, or a non-.lwrite Open — so a
// leftover untitled-*.json found at the next launch is unambiguously
// orphaned: nothing currently running could still be using that id).
let currentDocKey = null;

const AUTOSAVE_DIR = path.join(app.getPath('userData'), 'autosave');

function ensureAutosaveDir() {
  try {
    fs.mkdirSync(AUTOSAVE_DIR, { recursive: true });
  } catch (err) {
    console.error('Failed to create autosave directory', err);
  }
}

function keyForRealPath(filePath) {
  return `saved:${crypto.createHash('sha256').update(filePath).digest('hex')}`;
}

function newUntitledKey() {
  return `untitled:${crypto.randomUUID()}`;
}

function autosaveFilePathFor(key) {
  // Autosave keys are already filesystem-safe (hex digest / uuid) apart
  // from the "saved:"/"untitled:" prefix's colon — which is illegal in a
  // Windows filename (this app ships Windows builds — see package.json's
  // "build" block), so it's swapped for a hyphen here rather than allowed
  // through. Anything else unexpected is stripped defensively too.
  const safe = String(key).replace(/:/g, '-').replace(/[^a-zA-Z0-9_-]/g, '');
  return path.join(AUTOSAVE_DIR, `${safe}.json`);
}

function clearAutosaveForKey(key) {
  if (!key) return;
  try {
    const p = autosaveFilePathFor(key);
    if (fs.existsSync(p)) fs.unlinkSync(p);
  } catch (err) {
    console.error('Failed to clear autosave snapshot', err);
  }
}

/** Switches `currentDocKey` to `newKey`, clearing both the outgoing key's
 * snapshot (no crash happened — the document is being cleanly navigated
 * away from, so any recovery copy for it is no longer meaningful) and any
 * stale snapshot that might already exist under the incoming key (e.g. a
 * leftover from a previous crash while editing the same real path, now
 * being superseded by a fresh, clean load of that same document). */
function switchDocKey(newKey) {
  clearAutosaveForKey(currentDocKey);
  currentDocKey = newKey;
  clearAutosaveForKey(currentDocKey);
}

// ---------- Window size/position persistence ----------
// A small JSON file in userData, independent of recent.json/autosave,
// remembering the last-known normal (non-maximized) bounds plus whether
// the window was maximized, so the next launch restores it instead of
// always resetting to the hardcoded default below.
const WINDOW_STATE_PATH = path.join(app.getPath('userData'), 'window-state.json');
const DEFAULT_WINDOW_BOUNDS = { width: 1280, height: 820, x: undefined, y: undefined };

function loadWindowState() {
  try {
    const raw = fs.readFileSync(WINDOW_STATE_PATH, 'utf8');
    const data = JSON.parse(raw);
    if (!data || typeof data !== 'object') return { ...DEFAULT_WINDOW_BOUNDS, maximized: false };
    const width = Number.isFinite(data.width) && data.width > 0 ? data.width : DEFAULT_WINDOW_BOUNDS.width;
    const height = Number.isFinite(data.height) && data.height > 0 ? data.height : DEFAULT_WINDOW_BOUNDS.height;
    const x = Number.isFinite(data.x) ? data.x : undefined;
    const y = Number.isFinite(data.y) ? data.y : undefined;
    return { width, height, x, y, maximized: !!data.maximized };
  } catch (err) {
    return { ...DEFAULT_WINDOW_BOUNDS, maximized: false };
  }
}

function saveWindowState(state) {
  try {
    fs.mkdirSync(path.dirname(WINDOW_STATE_PATH), { recursive: true });
    fs.writeFileSync(WINDOW_STATE_PATH, JSON.stringify(state), 'utf8');
  } catch (err) {
    console.error('Failed to write window-state.json', err);
  }
}

// Clamps a saved position to a currently-connected display's work area so
// a window last seen on a monitor that's since been unplugged (or a
// resolution that shrank) doesn't restore mostly/fully off-screen. Only
// the position is validated — width/height are already sane numbers from
// loadWindowState, and BrowserWindow's own min/max constraints handle the
// rest. Falls back to letting Electron pick a default position (x/y
// undefined) if the saved spot doesn't usefully overlap any display.
function clampToVisibleDisplay(state) {
  if (typeof state.x !== 'number' || typeof state.y !== 'number') {
    return { width: state.width, height: state.height, maximized: state.maximized };
  }
  const MIN_VISIBLE_PX = 100; // at least this much of the window must land on some display
  const onScreen = screen.getAllDisplays().some((d) => {
    const area = d.workArea;
    const visibleX = Math.min(state.x + state.width, area.x + area.width) - Math.max(state.x, area.x);
    const visibleY = Math.min(state.y + state.height, area.y + area.height) - Math.max(state.y, area.y);
    return visibleX >= MIN_VISIBLE_PX && visibleY >= MIN_VISIBLE_PX;
  });
  if (!onScreen) {
    return { width: state.width, height: state.height, maximized: state.maximized };
  }
  return state;
}

function createWindow() {
  const savedState = clampToVisibleDisplay(loadWindowState());

  win = new BrowserWindow({
    width: savedState.width,
    height: savedState.height,
    x: savedState.x,
    y: savedState.y,
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

  if (savedState.maximized) win.maximize();

  // Tracks the last-known NORMAL (non-maximized, non-minimized,
  // non-fullscreen) bounds, since win.getBounds() while maximized reports
  // the maximized bounds, not the restored size the user actually chose —
  // saving those would mean un-maximizing next launch lands on the wrong
  // size, unlike most native apps.
  let lastNormalBounds = { width: savedState.width, height: savedState.height, x: savedState.x, y: savedState.y };
  let boundsSaveTimer = null;

  function persistBounds() {
    if (!win || win.isDestroyed()) return;
    if (!win.isMaximized() && !win.isMinimized() && !win.isFullScreen()) {
      lastNormalBounds = win.getBounds();
    }
    saveWindowState({ ...lastNormalBounds, maximized: win.isMaximized() });
  }

  function scheduleBoundsSave() {
    clearTimeout(boundsSaveTimer);
    boundsSaveTimer = setTimeout(persistBounds, 400);
  }

  win.on('resize', scheduleBoundsSave);
  win.on('move', scheduleBoundsSave);
  win.on('maximize', scheduleBoundsSave);
  win.on('unmaximize', scheduleBoundsSave);

  Menu.setApplicationMenu(null);
  win.loadFile('index.html');

  win.on('close', (e) => {
    // Flush immediately (bypassing the debounce) so a clean quit always
    // persists the final bounds, even if it happens within the debounce
    // window of the last resize/move.
    clearTimeout(boundsSaveTimer);
    persistBounds();
    if (allowClose) return;
    e.preventDefault();
    win.webContents.send('app:request-close-check');
  });

  win.on('closed', () => {
    win = null;
  });
}

if (gotSingleInstanceLock) {
  app.whenReady().then(createWindow);
}

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

// Renderer tells us whether it's safe to actually close the window
// (after showing its own in-app "unsaved changes" dialog, if needed).
// Help ▸ About reads the real app version through here instead of a
// literal string hand-duplicated in the renderer — app.getVersion()
// resolves to package.json's "version" field both in dev and in a packaged
// build (electron-builder embeds it), so there is exactly one place this
// number is ever written.
ipcMain.handle('app:getVersion', () => app.getVersion());

ipcMain.on('app:close-response', (event, shouldClose) => {
  if (shouldClose) {
    // By the time we get here the renderer's own confirmProceedIfDirty()
    // flow has already run: the document is either clean, was just saved
    // (file:save/file:saveAs already cleared its autosave snapshot below),
    // or the user explicitly chose "Don't Save". In every one of those
    // cases this is a clean, deliberate close — not a crash — so any
    // leftover autosave snapshot for the current document no longer means
    // anything and should go too, otherwise the NEXT launch would wrongly
    // offer to "recover" changes the user just told us to discard.
    clearAutosaveForKey(currentDocKey);
    allowClose = true;
    if (win) win.close();
  }
});

// ---------- File I/O helpers ----------

// Thrown for any .lwrite file that parses as JSON but isn't a document
// this app could have written (or doesn't parse as JSON at all) — carries
// a message that's already safe to show the user as-is (see
// loadDocumentFromPath's catch, which forwards err.message verbatim to
// the renderer's toast), instead of a raw "Unexpected token X in JSON at
// position Y" or a null-dereference TypeError reaching the user.
class CorruptFileError extends Error {
  constructor() {
    super("This file couldn't be opened — it may be corrupted or in an unrecognized format.");
    this.name = 'CorruptFileError';
  }
}

function readLwrite(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8');
  let data;
  try {
    data = JSON.parse(raw);
  } catch (err) {
    // Truncated / partially-written / not-JSON-at-all.
    throw new CorruptFileError();
  }
  // Reject anything that isn't a plain object shaped like a document this
  // app actually wrote: `data` being null (a bare "null" is valid JSON),
  // an array, a primitive, or missing/mismatched the version field this
  // app has always stamped on save all indicate a corrupted or foreign
  // file rather than something safe to guess-parse.
  if (!data || typeof data !== 'object' || Array.isArray(data) || data.version !== 1) {
    throw new CorruptFileError();
  }
  return {
    contentHTML: typeof data.contentHTML === 'string' ? data.contentHTML : '',
    headerHTML: typeof data.headerHTML === 'string' ? data.headerHTML : '',
    footerHTML: typeof data.footerHTML === 'string' ? data.footerHTML : '',
    pageSetup: data.pageSetup && typeof data.pageSetup === 'object' ? data.pageSetup : null,
    title: typeof data.title === 'string' && data.title ? data.title : path.basename(filePath, path.extname(filePath)),
  };
}

// Standard page sizes Electron's printToPDF/print() accept as a bare
// string, keyed the same way as pagination.js's PAGE_SIZES so a document's
// on-screen Page Setup (size + margins) translates 1:1 into print/PDF
// output instead of the app silently exporting a different paper size.
const PDF_PAGE_SIZE = { letter: 'Letter', a4: 'A4', legal: 'Legal' };
const DEFAULT_MARGINS_IN = { top: 1, bottom: 1, left: 1, right: 1 };

// Same page sizes in px @96dpi as pagination.js's PAGE_SIZES (duplicated,
// not imported — main.js is CommonJS and src/pagination.js is a renderer
// ES module; keep these two in sync if page sizes ever change). Used for
// Word export's pageSize option below, which accepts a "<n>px" string.
const DOCX_PAGE_SIZE_PX = {
  letter: { w: 816, h: 1056 },
  a4: { w: 794, h: 1123 },
  legal: { w: 816, h: 1344 },
};

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

// Every entry carries a stable `id` (a random UUID, not derived from the
// path) so the renderer can ask to open a recent file BY ID instead of by
// sending main.js an arbitrary path string — see recent:open below. Older
// recent.json files from before this existed get ids backfilled on load.
function loadRecent() {
  try {
    const raw = fs.readFileSync(RECENT_PATH, 'utf8');
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    let backfilled = false;
    const list = arr.filter(Boolean).map((entry) => {
      if (entry.id) return entry;
      backfilled = true;
      return { ...entry, id: crypto.randomUUID() };
    });
    if (backfilled) saveRecentList(list);
    return list;
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
  list.unshift({
    id: crypto.randomUUID(),
    path: filePath,
    title: title || titleFromPath(filePath),
    openedAt: new Date().toISOString(),
  });
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
    let headerHTML = '';
    let footerHTML = '';
    let pageSetup = null;
    let title = titleFromPath(filePath);
    let format;
    let warnings = [];

    if (ext === '.lwrite') {
      const loaded = readLwrite(filePath);
      contentHTML = loaded.contentHTML;
      headerHTML = loaded.headerHTML;
      footerHTML = loaded.footerHTML;
      pageSetup = loaded.pageSetup;
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
    // OS-level recent-documents integration (Windows Jump List, macOS
    // "Open Recent"/Dock menu) — separate from, and in addition to, the
    // app's own in-app Recent list (recent.json) above. Cheap, one-shot;
    // no-op on platforms/desktop environments that don't support it.
    app.addRecentDocument(filePath);
    // This is the single place a successful open updates the main-tracked
    // "current document path" (see the currentFilePath comment up top) —
    // every caller below (dialog Open, Open Recent, drag-and-drop) funnels
    // through here, so there's exactly one spot to get this right. Non-
    // .lwrite opens (html/txt/docx) don't get a "current path" at all:
    // Save on one of those still needs to go through Save As to pick a
    // real .lwrite destination, same as before this fix.
    currentFilePath = format === 'lwrite' ? filePath : null;
    // See the currentDocKey comment up top: a .lwrite open now has a real
    // path to key its autosave snapshot off of; any other format is still
    // "untitled" as far as Save/autosave are concerned (Save on one of
    // these still goes through Save As), so it gets a fresh per-instance id.
    switchDocKey(format === 'lwrite' ? keyForRealPath(filePath) : newUntitledKey());
    return { filePath: format === 'lwrite' ? filePath : null, title, contentHTML, headerHTML, footerHTML, pageSetup, format, warnings };
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

// File ▸ Open Recent: the renderer sends only a stable `id` — never a raw
// path — and main.js looks the real path up itself in recent.json, which
// main.js itself wrote and fully controls. A renderer that sent an
// arbitrary path here (e.g. a compromised/XSS'd page) could otherwise read
// any file the OS user can read; an id that doesn't match a *current*
// entry is rejected outright rather than falling back to anything.
ipcMain.handle('recent:open', async (event, id) => {
  if (typeof id !== 'string' || !id) return { error: 'Invalid recent-file selection.' };
  const entry = loadRecent().find((e) => e && e.id === id);
  if (!entry) return { error: 'That recent file is no longer available.' };
  return loadDocumentFromPath(entry.path);
});

// Drag-and-drop open: by the time a filePath string reaches THIS handler
// it is just a string like any other IPC argument — main.js itself has no
// way to tell whether it originated from a real drop or was typed in by a
// malicious script, so this handler alone is NOT the security boundary.
// The actual boundary is upstream, in preload.js's exposed openDroppedFile:
// it takes a real File object (never a string) and resolves the path via
// Electron's webUtils.getPathForFile(), which only returns a non-empty
// path for a File Chromium itself created with a genuine on-disk backing —
// a script-fabricated File resolves to '' and preload rejects it before
// ever calling this IPC channel. (An earlier version of this bridge
// accepted a bare path string directly and was a live arbitrary-file-read
// vulnerability — any renderer script, e.g. via
// `window.lumen.openDroppedPath('/etc/passwd')`, could read any file the
// OS user could read. Do not revert preload.js's openDroppedFile to accept
// a plain path string; that reopens this hole even though this handler's
// code looks unchanged.) This handler still validates its input defensively
// since it must never assume its caller is well-behaved.
ipcMain.handle('file:openDropped', async (event, filePath) => {
  if (typeof filePath !== 'string' || !filePath) return { error: 'Invalid file.' };
  return loadDocumentFromPath(filePath);
});

// Renderer tells us the current document has become untitled (New, or a
// template picked from the start screen) so main.js's own notion of "the
// current path" — the thing file:save writes to — resets in lockstep with
// what's on screen, instead of a stale path lingering server-side.
ipcMain.handle('doc:new', () => {
  currentFilePath = null;
  switchDocKey(newUntitledKey());
  return true;
});

// Save: intentionally takes NO filePath from the renderer at all — the
// payload is document content/options only. It writes to the path main.js
// itself is tracking (currentFilePath); if the document has never been
// saved (currentFilePath is null), it runs the exact same
// dialog.showSaveDialog flow as Save As and adopts THAT result. This is
// what makes repeated Ctrl+S silent for an already-saved document while
// making it impossible for a compromised renderer to redirect a write
// anywhere it likes.
ipcMain.handle('file:save', async (event, payload) => {
  const { title, contentHTML, headerHTML, footerHTML, pageSetup } = payload || {};
  let targetPath = currentFilePath;
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
    const data = {
      version: 1,
      title: title || 'Untitled document',
      contentHTML: contentHTML || '',
      headerHTML: headerHTML || '',
      footerHTML: footerHTML || '',
      pageSetup: pageSetup || null,
      createdAt,
      modifiedAt: now,
    };
    fs.writeFileSync(targetPath, JSON.stringify(data, null, 2), 'utf8');
    currentFilePath = targetPath;
    // A clean, successful Save means there's no longer any unsaved work
    // for a crash to strand — clear this document's autosave snapshot
    // (see the currentDocKey/autosave comments up top).
    switchDocKey(keyForRealPath(targetPath));
    addRecent(targetPath, title || 'Untitled document');
    app.addRecentDocument(targetPath); // OS Jump List / Open Recent, see loadDocumentFromPath's comment
    return { filePath: targetPath };
  } catch (err) {
    return { error: String(err && err.message ? err.message : err) };
  }
});

// Save As: ALWAYS shows its own dialog.showSaveDialog and uses ONLY that
// result as the write target — the payload here never carried a filePath
// (renderer content/options only), so there is no fallback path to a
// renderer-supplied location, ever.
ipcMain.handle('file:saveAs', async (event, payload) => {
  const { title, contentHTML, headerHTML, footerHTML, pageSetup } = payload || {};
  const result = await dialog.showSaveDialog(win, {
    title: 'Save document as',
    defaultPath: `${title || 'Untitled document'}.lwrite`,
    filters: [{ name: 'Lumen Write document', extensions: ['lwrite'] }],
  });
  if (result.canceled || !result.filePath) return null;
  try {
    const now = new Date().toISOString();
    const data = {
      version: 1,
      title: title || 'Untitled document',
      contentHTML: contentHTML || '',
      headerHTML: headerHTML || '',
      footerHTML: footerHTML || '',
      pageSetup: pageSetup || null,
      createdAt: now,
      modifiedAt: now,
    };
    fs.writeFileSync(result.filePath, JSON.stringify(data, null, 2), 'utf8');
    currentFilePath = result.filePath;
    // Same reasoning as file:save above — a clean Save As clears the
    // autosave snapshot for the document being saved.
    switchDocKey(keyForRealPath(result.filePath));
    addRecent(result.filePath, title || 'Untitled document');
    app.addRecentDocument(result.filePath); // OS Jump List / Open Recent, see loadDocumentFromPath's comment
    return { filePath: result.filePath };
  } catch (err) {
    return { error: String(err && err.message ? err.message : err) };
  }
});

// ---------- Autosave IPC ----------
// Separate from file:save/file:saveAs above: these never touch the user's
// real document file, only the dedicated recovery snapshot described in
// the currentDocKey/AUTOSAVE_DIR comments near the top of this file.

// Periodic "the document is dirty" snapshot, throttled/deduped on the
// renderer side (fileio.js) so this only fires roughly every 30-45s and
// only when the content actually changed since the last autosave.
ipcMain.handle('autosave:write', (event, payload) => {
  if (!currentDocKey) return false;
  try {
    ensureAutosaveDir();
    const data = {
      version: 1,
      key: currentDocKey,
      filePath: currentFilePath,
      title: (payload && payload.title) || 'Untitled document',
      contentHTML: (payload && payload.contentHTML) || '',
      headerHTML: (payload && payload.headerHTML) || '',
      footerHTML: (payload && payload.footerHTML) || '',
      pageSetup: (payload && payload.pageSetup) || null,
      savedAt: new Date().toISOString(),
    };
    fs.writeFileSync(autosaveFilePathFor(currentDocKey), JSON.stringify(data), 'utf8');
    return true;
  } catch (err) {
    console.error('Autosave write failed', err);
    return false;
  }
});

// Called once at launch. Scans every snapshot under AUTOSAVE_DIR and
// returns the ones worth offering to recover: for a snapshot tied to a
// real path, only if it's newer than that file's own last-saved mtime (a
// snapshot that's older, or whose real file no longer exists but was
// re-saved since... see below, just means the user already saved normally
// after the snapshot was taken); for an untitled document, ANY leftover
// snapshot at all, since a clean New/Save/close always clears its own
// (see switchDocKey / the app:close-response handler above) — so one
// still on disk at the next launch can only mean the session that wrote
// it never shut down cleanly.
ipcMain.handle('autosave:findRecoverable', () => {
  ensureAutosaveDir();
  let files;
  try {
    files = fs.readdirSync(AUTOSAVE_DIR).filter((f) => f.endsWith('.json'));
  } catch (err) {
    return [];
  }
  const candidates = [];
  for (const file of files) {
    const full = path.join(AUTOSAVE_DIR, file);
    let data;
    try {
      data = JSON.parse(fs.readFileSync(full, 'utf8'));
    } catch (err) {
      // A corrupt/partially-written autosave snapshot itself (e.g. the
      // process died mid-write of the snapshot) isn't worth surfacing —
      // and definitely not worth crashing over. Clean it up and move on.
      try { fs.unlinkSync(full); } catch (e2) { /* ignore */ }
      continue;
    }
    if (!data || typeof data !== 'object' || !data.key) continue;
    // Never offer to "recover" the document that's currently open and
    // being actively autosaved this very session — that's a live copy of
    // the current session's progress, not an orphan from a crash.
    if (data.key === currentDocKey) continue;

    let recoverable = false;
    if (data.filePath) {
      try {
        const st = fs.statSync(data.filePath);
        recoverable = !data.savedAt || new Date(data.savedAt).getTime() > st.mtimeMs;
      } catch (err) {
        // The real file is missing/unreadable — still surface the
        // snapshot rather than silently dropping the only remaining copy
        // of that content.
        recoverable = true;
      }
    } else {
      recoverable = true;
    }
    if (recoverable) {
      candidates.push({ key: data.key, filePath: data.filePath || null, title: data.title || 'Untitled document', savedAt: data.savedAt || null });
    }
  }
  candidates.sort((a, b) => new Date(b.savedAt || 0) - new Date(a.savedAt || 0));
  return candidates;
});

// Applies a specific snapshot (by key) as the new "current document" —
// the renderer loads its content into the editor and marks it dirty; nothing
// is written to the real file here (the user still has to Save). Adopting
// `currentDocKey`/`currentFilePath` from the snapshot means a subsequent
// Save writes straight back to the original path (if any) instead of
// re-prompting Save As, and — deliberately — does NOT clear the snapshot
// itself yet: if the app is killed again before the user saves, the
// recovery copy needs to still be there next time too.
ipcMain.handle('autosave:loadSnapshot', (event, key) => {
  if (typeof key !== 'string' || !key) return null;
  let data;
  try {
    data = JSON.parse(fs.readFileSync(autosaveFilePathFor(key), 'utf8'));
  } catch (err) {
    return null;
  }
  if (!data || typeof data !== 'object') return null;
  currentFilePath = data.filePath || null;
  currentDocKey = data.key || key;
  return {
    filePath: data.filePath || null,
    title: typeof data.title === 'string' ? data.title : 'Untitled document',
    contentHTML: typeof data.contentHTML === 'string' ? data.contentHTML : '',
    headerHTML: typeof data.headerHTML === 'string' ? data.headerHTML : '',
    footerHTML: typeof data.footerHTML === 'string' ? data.footerHTML : '',
    pageSetup: data.pageSetup && typeof data.pageSetup === 'object' ? data.pageSetup : null,
  };
});

// User chose "Discard" in the recovery dialog for a given snapshot.
ipcMain.handle('autosave:discard', (event, key) => {
  clearAutosaveForKey(key);
  return true;
});

// Chromium's header/footer templates support a few special classes that
// get auto-populated (date/title/url/pageNumber/totalPages) — used here
// to translate our `{n}`/`{pages}` tokens into the real thing so the
// exported PDF's page numbers are correct per-page, not just a static
// string baked in once.
function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function buildHeaderFooterTemplate(raw) {
  const escaped = escapeHtml(raw || '')
    .replace(/\{n\}/g, '<span class="pageNumber"></span>')
    .replace(/\{pages\}/g, '<span class="totalPages"></span>');
  return `<div style="width:100%;font-size:9px;text-align:center;-webkit-print-color-adjust:exact;">${escaped}</div>`;
}

// html-to-docx has no equivalent of the PDF export's live Chromium
// pageNumber/totalPages template classes above — {n}/{pages} tokens are
// preserved as literal text here rather than silently dropped or guessed
// at, same treatment turndown (Markdown export) already gives them.
function buildDocxHeaderFooterHtml(raw) {
  return `<p>${escapeHtml(raw || '')}</p>`;
}

// Every export handler below (pdf/docx/markdown/txt) follows the same
// rule as Save As: it always calls dialog.showSaveDialog() itself and
// writes ONLY to that call's result. None of these payloads carry (or are
// read for) a filePath — the renderer cannot influence the write target.
ipcMain.handle('export:pdf', async (event, payload) => {
  const { title, headerHTML, footerHTML, pageSetup } = payload || {};
  try {
    const hasHeader = !!(headerHTML && headerHTML.trim());
    const hasFooter = !!(footerHTML && footerHTML.trim());
    // NB: despite Electron's own TypeScript comment saying "in pixels",
    // printToPDF's margins are actually in inches at runtime (verified —
    // passing pixel values throws "margins must be less than or equal to
    // pageSize"). pageSetup.marginsIn already carries the document's real
    // Page Setup margins in inches, so this now feeds the exact on-screen
    // geometry (size + margins) into the exported PDF instead of the
    // fixed Letter/~1in approximation this used to hardcode.
    const sizeKey = (pageSetup && pageSetup.sizeKey) || 'letter';
    const margins = (pageSetup && pageSetup.marginsIn) || DEFAULT_MARGINS_IN;
    const printOptions = {
      pageSize: PDF_PAGE_SIZE[sizeKey] || 'Letter',
      printBackground: true,
      margins: { marginType: 'custom', top: margins.top, bottom: margins.bottom, left: margins.left, right: margins.right },
    };
    if (hasHeader || hasFooter) {
      printOptions.displayHeaderFooter = true;
      printOptions.headerTemplate = hasHeader ? buildHeaderFooterTemplate(headerHTML) : '<div></div>';
      printOptions.footerTemplate = hasFooter ? buildHeaderFooterTemplate(footerHTML) : '<div></div>';
    }
    const buffer = await win.webContents.printToPDF(printOptions);
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
  const { title, contentHTML, headerHTML, footerHTML, pageSetup } = payload;
  try {
    const htmlToDocx = require('html-to-docx');
    const sizeKey = (pageSetup && pageSetup.sizeKey) || 'letter';
    const size = DOCX_PAGE_SIZE_PX[sizeKey] || DOCX_PAGE_SIZE_PX.letter;
    const margins = (pageSetup && pageSetup.marginsIn) || DEFAULT_MARGINS_IN;
    const hasHeader = !!(headerHTML && headerHTML.trim());
    const hasFooter = !!(footerHTML && footerHTML.trim());
    const buffer = await htmlToDocx(
      contentHTML || '',
      hasHeader ? buildDocxHeaderFooterHtml(headerHTML) : null,
      {
        title: title || 'Untitled document',
        font: 'Georgia',
        // Without these, the document's header/footer text (present in
        // the PDF export of the same document) never made it into the
        // .docx at all — html-to-docx only emits header/footer parts when
        // these flags are set.
        header: hasHeader,
        footer: hasFooter,
        // html-to-docx auto-detects the unit from a "<n>px"/"<n>in" string
        // suffix (see its normalizeDocumentOptions), so the same pageSetup
        // the screen/PDF/print paths use feeds Word export too.
        pageSize: { width: `${size.w}px`, height: `${size.h}px` },
        margins: {
          top: `${margins.top}in`,
          bottom: `${margins.bottom}in`,
          left: `${margins.left}in`,
          right: `${margins.right}in`,
          // html-to-docx's normalizer only fills in a default for a
          // margins key it never SEES at all (it walks Object.keys() of
          // whatever object we pass) — omitting header/footer/gutter
          // here, as before, meant they came back `undefined` from
          // html-to-docx's own shallow options merge and got serialized
          // as the literal string "undefined" into the docx's <w:pgMar>
          // XML. These are html-to-docx's own documented defaults (720
          // twips header/footer distance, 0 gutter), supplied explicitly
          // so nothing is ever left unset regardless of unit.
          header: 720,
          footer: 720,
          gutter: 0,
        },
      },
      hasFooter ? buildDocxHeaderFooterHtml(footerHTML) : null
    );
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

ipcMain.handle('app:print', async (event, payload) => {
  const sizeKey = (payload && payload.pageSetup && payload.pageSetup.sizeKey) || 'letter';
  win.webContents.print({ pageSize: PDF_PAGE_SIZE[sizeKey] || 'Letter' });
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
