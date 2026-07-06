// editor.js — the contentEditable engine: execCommand wrappers, custom
// selection-wrapping for font/color styling, word/char counting, and
// selection-state inspection used to reflect active formatting on the UI.

let pageEl = null;
let savedRange = null;
const changeListeners = [];

export function initEditor(page) {
  pageEl = page;

  document.addEventListener('selectionchange', () => {
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0 && pageEl.contains(sel.anchorNode)) {
      savedRange = sel.getRangeAt(0).cloneRange();
    }
  });

  pageEl.addEventListener('input', () => {
    notifyChange();
  });
}

export function getPage() {
  return pageEl;
}

export function onChange(fn) {
  changeListeners.push(fn);
}

export function notifyChange() {
  for (const fn of changeListeners) fn();
}

/** Restore the last selection range that lived inside the page — used
 * after interacting with a <select> or popover, which steals focus and
 * would otherwise collapse the in-page text selection. */
export function restoreSelection() {
  if (!savedRange) return false;
  const sel = window.getSelection();
  sel.removeAllRanges();
  sel.addRange(savedRange);
  return true;
}

export function focusPage() {
  pageEl.focus();
}

/** Run a document.execCommand-based editing action, keeping focus/selection
 * inside the page. */
export function exec(command, value) {
  restoreSelection();
  focusPage();
  document.execCommand(command, false, value);
  notifyChange();
}

/** Wrap the current (non-collapsed) selection in a <span style="..."> for
 * properties execCommand can't set cleanly (font family/size, colors).
 * Falls back to extractContents()+insert when the selection spans
 * partial/multiple nodes and surroundContents() would throw. */
export function wrapSelection(styleProp, styleValue) {
  restoreSelection();
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0 || sel.isCollapsed) return false;
  const range = sel.getRangeAt(0);
  if (!pageEl.contains(range.commonAncestorContainer)) return false;

  const span = document.createElement('span');
  span.style[styleProp] = styleValue;
  try {
    range.surroundContents(span);
  } catch (err) {
    const contents = range.extractContents();
    span.appendChild(contents);
    range.insertNode(span);
  }

  sel.removeAllRanges();
  const newRange = document.createRange();
  newRange.selectNodeContents(span);
  sel.addRange(newRange);
  savedRange = newRange.cloneRange();
  focusPage();
  notifyChange();
  return true;
}

export function insertHTML(html) {
  restoreSelection();
  focusPage();
  document.execCommand('insertHTML', false, html);
  notifyChange();
}

/** Inspect execCommand state plus ancestor styling to reflect the current
 * selection's formatting on the toolbar. */
export function getSelectionState() {
  const state = {
    bold: safeState('bold'),
    italic: safeState('italic'),
    underline: safeState('underline'),
    strikeThrough: safeState('strikeThrough'),
    justifyLeft: safeState('justifyLeft'),
    justifyCenter: safeState('justifyCenter'),
    justifyRight: safeState('justifyRight'),
    justifyFull: safeState('justifyFull'),
    insertUnorderedList: safeState('insertUnorderedList'),
    insertOrderedList: safeState('insertOrderedList'),
    formatBlock: (safeValue('formatBlock') || 'p').toLowerCase(),
  };
  return state;
}

function safeState(cmd) {
  try {
    return document.queryCommandState(cmd);
  } catch (e) {
    return false;
  }
}

function safeValue(cmd) {
  try {
    return document.queryCommandValue(cmd);
  } catch (e) {
    return '';
  }
}

export function getWordCharCounts() {
  const text = pageEl.innerText || '';
  const trimmed = text.trim();
  const words = trimmed.length ? trimmed.split(/\s+/).length : 0;
  const chars = text.replace(/\n/g, '').length;
  return { words, chars };
}

export function getContentHTML() {
  return pageEl.innerHTML;
}

export function setContentHTML(html) {
  pageEl.innerHTML = html || '';
  notifyChange();
}

export function getPlainText() {
  return pageEl.innerText || '';
}
