// findreplace.js — Find & Replace logic. Find Next uses the (Chromium-only,
// legacy but functional) window.find() for highlighting. Replace All uses a
// TreeWalker over the page's text nodes for precise, structure-preserving
// substitution instead of relying on window.find()'s side effects.

import { getPage, notifyChange } from './editor.js';
import { openFindReplaceDialog } from './dialogs.js';

let dialogOpen = false;

export function openFindReplace() {
  if (dialogOpen) return;
  dialogOpen = true;
  const close = openFindReplaceDialog({
    findNext,
    replaceOne,
    replaceAll,
  });
  // Wrap close to reset our guard flag (dialogs.js already removes the
  // overlay on Escape/Close button; we just need to know when that happens).
  const root = document.getElementById('dialog-root');
  const observer = new MutationObserver(() => {
    if (!root.hasChildNodes()) {
      dialogOpen = false;
      observer.disconnect();
    }
  });
  observer.observe(root, { childList: true });
  return close;
}

function findNext(query) {
  if (!query) return false;
  getPage().focus();
  return window.find(query, false, false, true, false, false, false);
}

function replaceOne(query, replacement) {
  if (!query) return false;
  const page = getPage();
  const sel = window.getSelection();
  const hasMatchSelected =
    sel &&
    sel.rangeCount > 0 &&
    !sel.isCollapsed &&
    page.contains(sel.anchorNode) &&
    sel.toString().toLowerCase() === query.toLowerCase();

  if (!hasMatchSelected) {
    const found = findNext(query);
    if (!found) return false;
  }
  document.execCommand('insertText', false, replacement);
  notifyChange();
  return true;
}

function replaceAll(query, replacement) {
  if (!query) return 0;
  const page = getPage();
  const walker = document.createTreeWalker(page, NodeFilter.SHOW_TEXT, null);
  const nodes = [];
  let node;
  while ((node = walker.nextNode())) nodes.push(node);

  let count = 0;
  for (const textNode of nodes) {
    const value = textNode.nodeValue;
    if (!value || !value.includes(query)) continue;
    const parts = value.split(query);
    count += parts.length - 1;
    textNode.nodeValue = parts.join(replacement);
  }
  if (count > 0) notifyChange();
  return count;
}
