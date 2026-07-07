// toc.js — Insert ▸ Table of Contents. Builds a single block, inserted at
// the cursor, listing every H1/H2/H3 in the document (indented by level,
// dot-leader + right-aligned page number using pagination.js's real
// per-block page tracking). Deliberately *not* auto-diffed on every
// keystroke — like Word's own TOC, it's refreshed on demand via a small
// inline "Update" button (shown on hover/focus) or right-click ▸ Update
// Table of Contents. See the "Table of Contents block" section of
// src/styles/app.css for how it's styled apart from body text.

import { getPage, insertHTML, notifyChange } from './editor.js';
import { getPageNumberForElement, paginateNow } from './pagination.js';

const HEADING_SELECTOR = 'h1, h2, h3';

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/** Collects {level, text, page} for every heading currently in the
 * document, in document order. Assumes paginateNow() has already run (or
 * doesn't matter much — page numbers just reflect the last measured
 * layout) so data-lw-page markers are current. */
function collectEntries() {
  const page = getPage();
  if (!page) return [];
  const headings = Array.from(page.querySelectorAll(HEADING_SELECTOR));
  return headings.map((h) => ({
    level: Number(h.tagName.slice(1)) || 1,
    text: h.textContent.trim() || '(untitled heading)',
    page: getPageNumberForElement(h),
  }));
}

function entriesMarkup(entries) {
  if (!entries.length) {
    return '<div class="toc-block__empty">No headings yet — apply Heading 1–3 styles to see them listed here.</div>';
  }
  return entries
    .map(
      (e) => `<div class="toc-entry toc-entry--level-${e.level}">` +
        `<span class="toc-entry__text">${escapeHtml(e.text)}</span>` +
        `<span class="toc-entry__leader"></span>` +
        `<span class="toc-entry__page">${e.page}</span>` +
        `</div>`
    )
    .join('');
}

function blockMarkup(entries) {
  return (
    `<div class="toc-block" data-lw-toc="1" contenteditable="false" tabindex="0">` +
    `<div class="toc-block__header">` +
    `<span class="toc-block__title">Table of Contents</span>` +
    `<button type="button" class="toc-block__update" data-lw-toc-update>&#8635; Update</button>` +
    `</div>` +
    `<div class="toc-block__entries">${entriesMarkup(entries)}</div>` +
    `</div>`
  );
}

/** Insert ▸ Table of Contents — builds a snapshot from the document's
 * current headings/pages and inserts it at the caret. */
export function insertTableOfContents() {
  paginateNow();
  const entries = collectEntries();
  insertHTML(`${blockMarkup(entries)}<p><br></p>`);
}

/** Rebuilds one existing TOC block's entries in place (used by both the
 * inline "Update" button and the right-click menu). Mutates the DOM
 * directly rather than going through execCommand/insertHTML — this is a
 * targeted, deliberate refresh of a non-editable island, not user typing —
 * then tells the rest of the app (word count, dirty flag, outline, etc.)
 * that the document changed. */
function updateTocBlock(block) {
  if (!block) return;
  paginateNow();
  const entries = collectEntries();
  const entriesEl = block.querySelector('.toc-block__entries');
  if (entriesEl) entriesEl.innerHTML = entriesMarkup(entries);
  notifyChange();
}

// ---------- Right-click context menu ----------

let activeMenu = null;

function closeContextMenu() {
  if (!activeMenu) return;
  activeMenu.remove();
  activeMenu = null;
}

function openContextMenu(x, y, onUpdate) {
  closeContextMenu();
  const menu = document.createElement('div');
  menu.className = 'menu toc-context-menu';
  menu.style.left = `${x}px`;
  menu.style.top = `${y}px`;

  const item = document.createElement('div');
  item.className = 'menu__item';
  item.textContent = 'Update Table of Contents';
  item.addEventListener('click', (e) => {
    e.stopPropagation();
    closeContextMenu();
    onUpdate();
  });
  menu.appendChild(item);

  document.body.appendChild(menu);
  activeMenu = menu;

  // Deferred so the contextmenu event that opened this menu doesn't
  // immediately bubble into the listener and close it again.
  setTimeout(() => document.addEventListener('click', closeContextMenu, { once: true }), 0);
}

/** Wires event delegation for every TOC block's "Update" button and
 * right-click menu — delegated on #page once, rather than per-block,
 * since blocks are inserted/loaded as raw HTML (insertHTML, or restored
 * from a saved document) with no chance to attach per-instance listeners. */
export function initTOC() {
  const page = getPage();
  if (!page) return;

  page.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-lw-toc-update]');
    if (!btn) return;
    e.preventDefault();
    updateTocBlock(btn.closest('.toc-block'));
  });

  page.addEventListener('contextmenu', (e) => {
    const block = e.target.closest('.toc-block');
    if (!block) return;
    e.preventDefault();
    openContextMenu(e.clientX, e.clientY, () => updateTocBlock(block));
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeContextMenu();
  });
}
