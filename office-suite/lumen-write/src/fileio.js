// fileio.js — document lifecycle: New/Open/Save/Save As/Export/Print,
// dirty-flag tracking, and the "unsaved changes" confirmation flow shared
// by New, Open, and window-close. All actual disk I/O happens in main.js;
// this module only ever sends/receives strings over the preload bridge.

import { getContentHTML, setContentHTML, getPlainText, onChange as onEditorChange } from './editor.js';
import { unsavedChangesDialog, alertDialog } from './dialogs.js';

let filePath = null;
let dirty = false;

function titleInput() {
  return document.getElementById('doc-title');
}

function dirtyDot() {
  return document.getElementById('dirty-dot');
}

export function getTitle() {
  return titleInput().value.trim() || 'Untitled document';
}

function setTitle(t) {
  titleInput().value = t || 'Untitled document';
}

function markDirty() {
  dirty = true;
  dirtyDot().hidden = false;
}

function clearDirty() {
  dirty = false;
  dirtyDot().hidden = true;
}

export function isDirty() {
  return dirty;
}

/** Shows the unsaved-changes dialog if needed. Resolves true if it is safe
 * to proceed (document was saved, discarded, or was already clean),
 * false if the caller should abort (user hit Cancel, or Save failed/was
 * cancelled itself). */
async function confirmProceedIfDirty() {
  if (!dirty) return true;
  const choice = await unsavedChangesDialog(getTitle());
  if (choice === 'cancel') return false;
  if (choice === 'discard') return true;
  return saveDocument();
}

export async function newDocument() {
  const proceed = await confirmProceedIfDirty();
  if (!proceed) return;
  setContentHTML('');
  setTitle('Untitled document');
  filePath = null;
  clearDirty();
}

export async function openDocument() {
  const proceed = await confirmProceedIfDirty();
  if (!proceed) return;
  const result = await window.lumen.openFile();
  if (!result) return;
  if (result.error) {
    await alertDialog({ title: 'Could not open file', message: result.error });
    return;
  }
  setContentHTML(result.contentHTML);
  setTitle(result.title);
  filePath = result.format === 'lwrite' ? result.filePath : null;
  clearDirty();
  if (result.warnings && result.warnings.length) {
    await alertDialog({
      title: 'Imported with formatting notes',
      message:
        `This document was converted on import and may not perfectly preserve all formatting ` +
        `(especially tables or advanced styles):\n\n${result.warnings.slice(0, 6).join('\n')}`,
    });
  }
}

export async function saveDocument() {
  const payload = { filePath, title: getTitle(), contentHTML: getContentHTML() };
  const result = await window.lumen.saveFile(payload);
  if (!result) return false;
  filePath = result.filePath;
  clearDirty();
  return true;
}

export async function saveDocumentAs() {
  const payload = { title: getTitle(), contentHTML: getContentHTML() };
  const result = await window.lumen.saveFileAs(payload);
  if (!result) return false;
  filePath = result.filePath;
  clearDirty();
  return true;
}

export async function exportPdf() {
  const result = await window.lumen.exportPdf({ title: getTitle() });
  if (result && result.error) {
    await alertDialog({ title: 'Could not export PDF', message: result.error });
  }
}

export async function exportDocx() {
  const result = await window.lumen.exportDocx({ title: getTitle(), contentHTML: getContentHTML() });
  if (result && result.error) {
    await alertDialog({ title: 'Could not export Word document', message: result.error });
  }
}

export async function exportTxt() {
  const result = await window.lumen.exportTxt({ title: getTitle(), text: getPlainText() });
  if (result && result.error) {
    await alertDialog({ title: 'Could not export text file', message: result.error });
  }
}

export async function printDocument() {
  await window.lumen.print();
}

export function initFileIO() {
  titleInput().addEventListener('input', markDirty);
  onEditorChange(markDirty);

  window.lumen.onRequestCloseCheck(async () => {
    const proceed = await confirmProceedIfDirty();
    window.lumen.sendCloseResponse(proceed);
  });
}
