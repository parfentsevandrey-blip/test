// renderer.js — app entry point. Wires up the editor, toolbar, menu bar,
// theme toggle, status bar, and file I/O, then installs global keyboard
// shortcuts. Runs as a native ES module (loaded via <script type="module">).

import { initEditor, exec, setContentHTML } from './editor.js';
import { initToolbar } from './toolbar.js';
import { initMenubar } from './menubar.js';
import { initTheme } from './theme.js';
import { initStatusbar } from './statusbar.js';
import { initPagination } from './pagination.js';
import {
  initFileIO,
  newDocument,
  openDocument,
  saveDocument,
  saveDocumentAs,
  openRecentEntry,
  openDroppedFile,
  loadTemplateDocument,
  checkForRecovery,
} from './fileio.js';
import { openFindReplace } from './findreplace.js';
import { initOutline } from './outline.js';
import { initTOC } from './toc.js';
import { initStartScreen, showStartScreen } from './startscreen.js';
import { showToast } from './toast.js';

// Global error handling: a bug that reaches here would otherwise leave the
// user staring at an app that silently did nothing (an uncaught exception)
// or that quietly ate a rejected promise. This is a last-resort safety net,
// not the primary error handling strategy — normal operations (file I/O,
// exports, etc.) already report their own errors via toasts at the call
// site. Uses the app's own toast component, never a native alert/dialog.
let lastErrorToastAt = 0;
function surfaceUnexpectedError(err) {
  console.error('Unhandled error:', err);
  const now = Date.now();
  if (now - lastErrorToastAt < 2000) return; // avoid flooding on an error storm
  lastErrorToastAt = now;
  showToast('Something unexpected went wrong. Your edits are still in the editor — consider saving now.', { type: 'error' });
}
window.addEventListener('error', (e) => {
  surfaceUnexpectedError((e && e.error) || (e && e.message) || e);
});
window.addEventListener('unhandledrejection', (e) => {
  surfaceUnexpectedError(e && e.reason);
});

const page = document.getElementById('page');
initEditor(page);
setContentHTML('');

initTheme();
initToolbar();
initMenubar();
initStatusbar();
initPagination();
initFileIO();
initOutline();
initTOC();
initStartScreen({
  onTemplate: (html, title) => loadTemplateDocument(html, title),
  onRecent: (id) => openRecentEntry(id),
});

// Fresh launch: first check for a crash-recovery autosave snapshot (see
// fileio.js's checkForRecovery()/main.js's autosave:findRecoverable). If
// the user recovers one, its content loads straight into the editor and
// the start screen is skipped entirely; otherwise fall through to the
// normal start screen (template gallery + recent files).
const recovered = await checkForRecovery();
if (!recovered) showStartScreen();

// Drag-and-drop open: dropping a supported file onto the window opens it
// through the same code path as File ▸ Open, instead of letting Chromium
// navigate the window to the dropped file's file:// URL.
const SUPPORTED_DROP_EXTENSIONS = ['lwrite', 'html', 'htm', 'txt', 'docx'];
window.addEventListener('dragover', (e) => {
  e.preventDefault();
});
window.addEventListener('drop', (e) => {
  e.preventDefault();
  const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
  if (!file || !file.name) return;
  const ext = file.name.split('.').pop().toLowerCase();
  if (!SUPPORTED_DROP_EXTENSIONS.includes(ext)) return;
  // Forward the real File object itself, NOT a path string — the bridge
  // (preload.js's openDroppedFile) resolves the on-disk path via
  // webUtils.getPathForFile(), which only succeeds for a File Chromium
  // itself created with a genuine filesystem backing (an actual OS-level
  // drop or file-picker selection). A script cannot fabricate a File with
  // that backing, so this is the one place a "path" effectively reaches
  // main.js's file-read code without going through a save/open dialog or a
  // validated recent-list id — but it can only ever be a path the user
  // themselves just dragged in, never one script chooses. Every other read
  // path (Open Recent, direct Open) goes through main.js's own dialog
  // result or a validated recent-list id instead; don't copy this pattern
  // anywhere a path might originate from script rather than a genuine File.
  openDroppedFile(file);
});

window.addEventListener('keydown', (e) => {
  const mod = e.ctrlKey || e.metaKey;
  if (!mod) return;
  const key = e.key.toLowerCase();

  if (key === 'b') { e.preventDefault(); exec('bold'); }
  else if (key === 'i') { e.preventDefault(); exec('italic'); }
  else if (key === 'u') { e.preventDefault(); exec('underline'); }
  else if (key === 'z') { e.preventDefault(); exec('undo'); }
  else if (key === 'y') { e.preventDefault(); exec('redo'); }
  else if (key === 'f') { e.preventDefault(); openFindReplace(); }
  else if (key === 's' && e.shiftKey) { e.preventDefault(); saveDocumentAs(); }
  else if (key === 's') { e.preventDefault(); saveDocument(); }
  else if (key === 'n') { e.preventDefault(); newDocument(); }
  else if (key === 'o') { e.preventDefault(); openDocument(); }
});
