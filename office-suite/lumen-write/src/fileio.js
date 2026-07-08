// fileio.js — document lifecycle: New/Open/Save/Save As/Export/Print,
// dirty-flag tracking, and the "unsaved changes" confirmation flow shared
// by New, Open, and window-close. All actual disk I/O happens in main.js;
// this module only ever sends/receives strings over the preload bridge.

import { getContentHTML, setContentHTML, getPlainText, focusPage, onChange as onEditorChange } from './editor.js';
import { unsavedChangesDialog, alertDialog, recoveryDialog } from './dialogs.js';
import { showToast } from './toast.js';
import { showStartScreen, hideStartScreen } from './startscreen.js';
import { sanitizeHTML } from './sanitize.js';
import {
  getHeaderRaw,
  getFooterRaw,
  setHeaderFooterRaw,
  onHeaderFooterChange,
  getPageSetup,
  restorePageSetup,
  onPageSetupChange,
} from './pagination.js';

// This is a local, purely informational cache of "the path main.js last
// told us it used" — for reference only (nothing here is ever sent back
// to main.js to decide where a write goes; main.js tracks that itself).
// See main.js's currentFilePath for the actual trust boundary.
let filePath = null;
let dirty = false;

// ---------- Autosave / crash recovery ----------
// A recovery-only snapshot, entirely separate from the real document file
// (see the comments above main.js's currentDocKey/AUTOSAVE_DIR for the
// on-disk side of this). Every AUTOSAVE_INTERVAL_MS, if the document is
// dirty AND its content actually differs from what was last autosaved
// (lastAutosavePayloadStr), main.js is asked to write a fresh snapshot —
// skipping unchanged dirty documents avoids pointless disk churn from a
// document that's been dirty but untouched for a while (e.g. the user
// stepped away).
const AUTOSAVE_INTERVAL_MS = 30000;
let lastAutosavePayloadStr = null;

function buildSavePayload() {
  return {
    title: getTitle(),
    contentHTML: getContentHTML(),
    headerHTML: getHeaderRaw(),
    footerHTML: getFooterRaw(),
    pageSetup: getPageSetup(),
  };
}

async function maybeAutosave() {
  if (!dirty) return;
  const payload = buildSavePayload();
  const serialized = JSON.stringify(payload);
  if (serialized === lastAutosavePayloadStr) return;
  try {
    const ok = await window.lumen.autosaveWrite(payload);
    if (ok) lastAutosavePayloadStr = serialized;
  } catch (err) {
    console.error('Autosave failed', err);
  }
}

function formatRecoveryTime(iso) {
  try {
    return new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
  } catch (err) {
    return 'a previous session';
  }
}

/** Checked once at launch (see renderer.js). Offers to recover any
 * crash-orphaned autosave snapshot main.js finds (newest first) via the
 * app's own Recover/Discard dialog. Recovering loads that snapshot's
 * content straight into the editor — marked dirty, NOT written to the
 * real file yet, the user still has to Save — and stops there (a
 * single-document-canvas app can only show one recovered document at a
 * time); any candidates the user discards along the way are deleted and
 * the loop moves on. Returns true if a document was recovered (so the
 * caller can skip showing the start screen). */
export async function checkForRecovery() {
  let candidates;
  try {
    candidates = await window.lumen.findRecoverableAutosaves();
  } catch (err) {
    return false;
  }
  if (!candidates || !candidates.length) return false;

  for (const c of candidates) {
    const choice = await recoveryDialog(c.title || 'Untitled document', formatRecoveryTime(c.savedAt));
    if (choice === 'recover') {
      const snap = await window.lumen.loadAutosaveSnapshot(c.key);
      if (!snap) {
        showToast('Could not recover that snapshot — it may be corrupted.', { type: 'error' });
        continue;
      }
      setContentHTML(sanitizeHTML(snap.contentHTML));
      setHeaderFooterRaw(snap.headerHTML || '', snap.footerHTML || '');
      restorePageSetup(snap.pageSetup || null);
      setTitle(snap.title);
      filePath = snap.filePath || null;
      markDirty();
      lastAutosavePayloadStr = null;
      hideStartScreen();
      showToast('Recovered unsaved changes — remember to Save.', { type: 'success' });
      return true;
    }
    await window.lumen.discardAutosave(c.key);
  }
  return false;
}

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
  lastAutosavePayloadStr = null;
  // Tell main.js the document is untitled again so it stops tracking the
  // old path as the Save target (and clears any autosave snapshot for the
  // document being left behind — see main.js's switchDocKey).
  await window.lumen.newDocument();
  await showStartScreen();
}

/** Applies a result returned by openFile()/openRecent()/openDroppedFile()
 * to the editor — shared by openDocument(), openRecentEntry(), and
 * openDroppedFile(). `contentHTML` is sanitized here because this is the
 * single point where HTML from an external/file source (docx import, a
 * directly-opened .html file, or a possibly hand-edited .lwrite file's
 * contentHTML) becomes the live document — content the editor produces
 * itself never goes through this function. */
async function applyOpenedResult(result) {
  setContentHTML(sanitizeHTML(result.contentHTML));
  setHeaderFooterRaw(result.headerHTML || '', result.footerHTML || '');
  restorePageSetup(result.pageSetup || null);
  setTitle(result.title);
  filePath = result.format === 'lwrite' ? result.filePath : null;
  clearDirty();
  lastAutosavePayloadStr = null;
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

/** Opens a File ▸ Open Recent entry by its stable id — main.js resolves
 * the id against its own recent.json and reads the real path itself; the
 * renderer never sends a path for this. */
export async function openRecentEntry(id) {
  const proceed = await confirmProceedIfDirty();
  if (!proceed) return;
  const result = await window.lumen.openRecent(id);
  if (!result || result.error) {
    showToast((result && result.error) || 'Could not open file.', { type: 'error' });
    return;
  }
  await applyOpenedResult(result);
}

/** Opens a file dropped onto the window. `file` must be the real File
 * object from a 'drop' DOM event's dataTransfer.files[0] (see the comment
 * at the drop listener in renderer.js) — NOT a path string. The bridge
 * (preload.js's openDroppedFile) resolves the real on-disk path itself via
 * webUtils.getPathForFile(), which only succeeds for a genuine
 * browser-backed File; passing a path string here would defeat that
 * protection, so don't refactor this to take a path. */
export async function openDroppedFile(file) {
  const proceed = await confirmProceedIfDirty();
  if (!proceed) return;
  const result = await window.lumen.openDroppedFile(file);
  if (!result || result.error) {
    showToast((result && result.error) || 'Could not open file.', { type: 'error' });
    return;
  }
  await applyOpenedResult(result);
}

/** Loads starter content from the start screen's template grid. This html
 * is one of the app's own hardcoded template strings (src/startscreen.js),
 * not external/file-sourced, so it is intentionally not run through
 * sanitizeHTML() — same trust level as anything else typed/inserted in
 * the live editor. */
export async function loadTemplateDocument(html, title) {
  setContentHTML(html);
  setHeaderFooterRaw('', '');
  restorePageSetup(null);
  setTitle(title);
  filePath = null;
  clearDirty();
  lastAutosavePayloadStr = null;
  // Same as New: tell main.js this document is untitled.
  await window.lumen.newDocument();
  hideStartScreen();
  focusPage();
}

export async function saveDocument() {
  // No filePath in this payload — main.js writes to the path it is
  // itself tracking (or runs the Save As flow internally the first time).
  // See preload.js/main.js's file:save handler.
  const payload = buildSavePayload();
  const result = await window.lumen.saveFile(payload);
  if (!result) return false;
  if (result.error) {
    showToast(result.error, { type: 'error' });
    return false;
  }
  filePath = result.filePath;
  clearDirty();
  // main.js's file:save handler already clears the autosave snapshot for
  // this document on a clean save — reset the renderer's own "last
  // autosaved" baseline too so a later autosave doesn't wrongly skip
  // itself thinking nothing changed since a snapshot that no longer exists.
  lastAutosavePayloadStr = null;
  showToast('Saved', { type: 'success' });
  return true;
}

export async function saveDocumentAs() {
  const payload = buildSavePayload();
  const result = await window.lumen.saveFileAs(payload);
  if (!result) return false;
  if (result.error) {
    showToast(result.error, { type: 'error' });
    return false;
  }
  filePath = result.filePath;
  clearDirty();
  lastAutosavePayloadStr = null;
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

  // Periodic crash-recovery autosave — see the comments above
  // AUTOSAVE_INTERVAL_MS/maybeAutosave near the top of this file.
  setInterval(maybeAutosave, AUTOSAVE_INTERVAL_MS);

  window.lumen.onRequestCloseCheck(async () => {
    const proceed = await confirmProceedIfDirty();
    window.lumen.sendCloseResponse(proceed);
  });
}
