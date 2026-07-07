// fileio.js — document lifecycle: New/Open/Save/Save As/Export/Print,
// dirty-flag tracking, and the "unsaved changes" confirmation flow shared
// by New, Open, and window-close. All actual disk I/O happens in main.js;
// this module only ever sends/receives strings over the preload bridge.

import { getContentHTML, setContentHTML, getPlainText, focusPage, onChange as onEditorChange } from './editor.js';
import { unsavedChangesDialog, alertDialog } from './dialogs.js';
import { showToast } from './toast.js';
import { showStartScreen, hideStartScreen } from './startscreen.js';
import {
  getHeaderRaw,
  getFooterRaw,
  setHeaderFooterRaw,
  onHeaderFooterChange,
  getPageSetup,
  restorePageSetup,
  onPageSetupChange,
} from './pagination.js';

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
  setHeaderFooterRaw('', '');
  restorePageSetup(null);
  setTitle('Untitled document');
  filePath = null;
  clearDirty();
  await showStartScreen();
}

/** Applies a result returned by openFile()/openPath() to the editor —
 * shared by openDocument() and openDocumentAtPath(). */
async function applyOpenedResult(result) {
  setContentHTML(result.contentHTML);
  setHeaderFooterRaw(result.headerHTML || '', result.footerHTML || '');
  restorePageSetup(result.pageSetup || null);
  setTitle(result.title);
  filePath = result.format === 'lwrite' ? result.filePath : null;
  clearDirty();
  hideStartScreen();
  showToast('Opened', { type: 'success' });
  if (result.warnings && result.warnings.length) {
    await alertDialog({
      title: 'Imported with formatting notes',
      message:
        `This document was converted on import and may not perfectly preserve all formatting ` +
        `(especially tables or advanced styles):\n\n${result.warnings.slice(0, 6).join('\n')}`,
    });
  }
}

export async function openDocument() {
  const proceed = await confirmProceedIfDirty();
  if (!proceed) return;
  const result = await window.lumen.openFile();
  if (!result) return;
  if (result.error) {
    showToast(result.error, { type: 'error' });
    return;
  }
  await applyOpenedResult(result);
}

/** Opens a document at a known path — used by File ▸ Open Recent and
 * drag-and-drop, both of which go through the same file:openPath IPC
 * channel (and thus the same main-process loading code) as the regular
 * Open dialog. */
export async function openDocumentAtPath(path) {
  const proceed = await confirmProceedIfDirty();
  if (!proceed) return;
  const result = await window.lumen.openPath(path);
  if (!result || result.error) {
    showToast((result && result.error) || 'Could not open file.', { type: 'error' });
    return;
  }
  await applyOpenedResult(result);
}

/** Loads starter content from the start screen's template grid. */
export function loadTemplateDocument(html, title) {
  setContentHTML(html);
  setHeaderFooterRaw('', '');
  restorePageSetup(null);
  setTitle(title);
  filePath = null;
  clearDirty();
  hideStartScreen();
  focusPage();
}

export async function saveDocument() {
  const payload = {
    filePath,
    title: getTitle(),
    contentHTML: getContentHTML(),
    headerHTML: getHeaderRaw(),
    footerHTML: getFooterRaw(),
    pageSetup: getPageSetup(),
  };
  const result = await window.lumen.saveFile(payload);
  if (!result) return false;
  if (result.error) {
    showToast(result.error, { type: 'error' });
    return false;
  }
  filePath = result.filePath;
  clearDirty();
  showToast('Saved', { type: 'success' });
  return true;
}

export async function saveDocumentAs() {
  const payload = {
    title: getTitle(),
    contentHTML: getContentHTML(),
    headerHTML: getHeaderRaw(),
    footerHTML: getFooterRaw(),
    pageSetup: getPageSetup(),
  };
  const result = await window.lumen.saveFileAs(payload);
  if (!result) return false;
  if (result.error) {
    showToast(result.error, { type: 'error' });
    return false;
  }
  filePath = result.filePath;
  clearDirty();
  showToast('Saved', { type: 'success' });
  return true;
}

export async function exportPdf() {
  const result = await window.lumen.exportPdf({
    title: getTitle(),
    headerHTML: getHeaderRaw(),
    footerHTML: getFooterRaw(),
    pageSetup: getPageSetup(),
  });
  if (!result) return;
  if (result.error) {
    showToast(result.error, { type: 'error' });
    return;
  }
  showToast('Exported as PDF', { type: 'success' });
}

export async function exportDocx() {
  const result = await window.lumen.exportDocx({ title: getTitle(), contentHTML: getContentHTML(), pageSetup: getPageSetup() });
  if (!result) return;
  if (result.error) {
    showToast(result.error, { type: 'error' });
    return;
  }
  showToast('Exported as Word document', { type: 'success' });
}

export async function exportMarkdown() {
  const result = await window.lumen.exportMarkdown({ title: getTitle(), contentHTML: getContentHTML() });
  if (!result) return;
  if (result.error) {
    showToast(result.error, { type: 'error' });
    return;
  }
  showToast('Exported as Markdown', { type: 'success' });
}

export async function exportTxt() {
  const result = await window.lumen.exportTxt({ title: getTitle(), text: getPlainText() });
  if (!result) return;
  if (result.error) {
    showToast(result.error, { type: 'error' });
    return;
  }
  showToast('Exported as plain text', { type: 'success' });
}

export async function printDocument() {
  await window.lumen.print({ pageSetup: getPageSetup() });
}

export function initFileIO() {
  titleInput().addEventListener('input', markDirty);
  onEditorChange(markDirty);
  onHeaderFooterChange(markDirty);
  onPageSetupChange(markDirty);

  window.lumen.onRequestCloseCheck(async () => {
    const proceed = await confirmProceedIfDirty();
    window.lumen.sendCloseResponse(proceed);
  });
}
