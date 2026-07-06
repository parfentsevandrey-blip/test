// renderer.js — app entry point. Wires up the editor, toolbar, menu bar,
// theme toggle, status bar, and file I/O, then installs global keyboard
// shortcuts. Runs as a native ES module (loaded via <script type="module">).

import { initEditor, exec, setContentHTML } from './editor.js';
import { initToolbar } from './toolbar.js';
import { initMenubar } from './menubar.js';
import { initTheme } from './theme.js';
import { initStatusbar } from './statusbar.js';
import { initFileIO, newDocument, openDocument, saveDocument, saveDocumentAs } from './fileio.js';
import { openFindReplace } from './findreplace.js';

const page = document.getElementById('page');
initEditor(page);
setContentHTML('<p><br></p>');

initTheme();
initToolbar();
initMenubar();
initStatusbar();
initFileIO();

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
