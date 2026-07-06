// renderer.js — app entry point. Wires up the editor, toolbar, menu bar,
// theme toggle, status bar, and file I/O, then installs global keyboard
// shortcuts. Runs as a native ES module (loaded via <script type="module">).

import { initEditor, exec, setContentHTML } from './editor.js';
import { initToolbar } from './toolbar.js';
import { initMenubar } from './menubar.js';
import { initTheme } from './theme.js';
import { initStatusbar } from './statusbar.js';
import {
  initFileIO,
  newDocument,
  openDocument,
  saveDocument,
  saveDocumentAs,
  openDocumentAtPath,
  loadTemplateDocument,
} from './fileio.js';
import { openFindReplace } from './findreplace.js';
import { initOutline } from './outline.js';
import { initStartScreen, showStartScreen } from './startscreen.js';

const page = document.getElementById('page');
initEditor(page);
setContentHTML('');

initTheme();
initToolbar();
initMenubar();
initStatusbar();
initFileIO();
initOutline();
initStartScreen({
  onTemplate: (html, title) => loadTemplateDocument(html, title),
  onRecent: (path) => openDocumentAtPath(path),
});

// Fresh launch: show the start screen (template gallery + recent files)
// in place of the editor canvas until the user picks something.
showStartScreen();

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
  if (!file || !file.path) return;
  const ext = file.path.split('.').pop().toLowerCase();
  if (!SUPPORTED_DROP_EXTENSIONS.includes(ext)) return;
  openDocumentAtPath(file.path);
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
