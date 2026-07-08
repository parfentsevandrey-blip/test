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

/** Walks up from `node` to the nearest direct child of #page — the same
 * "top-level block" resolution pagination.js's getPageNumberForElement
 * uses to turn an arbitrary descendant into the paragraph/heading/list/
 * etc. block it lives in. */
function topLevelBlockFor(node) {
  let n = node;
  while (n && n.parentNode !== pageEl) n = n.parentNode;
  return n && n.nodeType === Node.ELEMENT_NODE ? n : null;
}

/** All top-level blocks the current selection touches, from the block
 * containing its start to the block containing its end (inclusive). */
function topLevelBlocksInRange(range) {
  const start = topLevelBlockFor(range.startContainer);
  const end = topLevelBlockFor(range.endContainer);
  if (!start) return [];
  if (!end || start === end) return [start];
  const blocks = [];
  let node = start;
  while (node) {
    blocks.push(node);
    if (node === end) break;
    node = node.nextElementSibling;
  }
  // If `end` was never reached walking forward (shouldn't normally
  // happen for a real selection range), fall back to just the start
  // block rather than silently applying to nothing.
  return blocks.includes(end) ? blocks : [start];
}

/** Sets a CSS property directly on every top-level block (paragraph,
 * heading, list, etc. — a direct child of #page) the current selection
 * touches. This is the same paragraph-level granularity formatBlock and
 * justifyLeft/Center/Right/Full already apply at (see toolbar.js/
 * menubar.js) — but since there is no execCommand for line spacing, this
 * sets the style property directly rather than going through execCommand,
 * the same way wrapSelection() sets inline styles execCommand can't for
 * font/color (that one wraps a <span>; this one targets the enclosing
 * block itself, since line spacing is paragraph-level, not
 * character-level). */
export function applyBlockStyle(styleProp, styleValue) {
  restoreSelection();
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return false;
  const range = sel.getRangeAt(0);
  if (!pageEl.contains(range.commonAncestorContainer) && range.commonAncestorContainer !== pageEl) return false;

  const blocks = topLevelBlocksInRange(range);
  if (!blocks.length) return false;
  for (const block of blocks) block.style[styleProp] = styleValue;

  focusPage();
  notifyChange();
  return true;
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
    lineHeight: currentLineHeight(),
  };
  return state;
}

/** Reads the inline line-height (set by applyBlockStyle above) off the
 * top-level block the selection starts in, for reflecting the current
 * paragraph's line spacing on the toolbar/menu. Empty string means "no
 * explicit override" (the document's default line-height applies). */
function currentLineHeight() {
  try {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return '';
    const range = sel.getRangeAt(0);
    if (!pageEl.contains(range.commonAncestorContainer) && range.commonAncestorContainer !== pageEl) return '';
    const block = topLevelBlockFor(range.startContainer);
    return (block && block.style && block.style.lineHeight) || '';
  } catch (e) {
    return '';
  }
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
