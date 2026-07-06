// outline.js — the collapsible document-outline sidebar (.sidebar/
// .sidebar__inner/.sidebar__heading/.sidebar__item from theme.css). Lists
// every H1/H2/H3 in the current document in order, indented by level, and
// refreshes ~300ms after the editor changes (debounced — see onChange in
// editor.js, which fires on every keystroke and every exec() call).
// Headings-only: images, tables, and other block types never appear here
// (see README "Known limitations").

import { getPage, onChange } from './editor.js';

const HEADING_SELECTOR = 'h1, h2, h3';
const DEBOUNCE_MS = 300;
const FLASH_MS = 650;

let sidebarEl = null;
let innerEl = null;
let debounceTimer = null;
let headingCounter = 0;

function scheduleRefresh() {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(refreshOutline, DEBOUNCE_MS);
}

function jumpTo(headingEl) {
  headingEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
  headingEl.classList.remove('outline-flash');
  // Force a reflow so re-adding the class restarts the animation if the
  // same heading is clicked twice in a row.
  void headingEl.offsetWidth;
  headingEl.classList.add('outline-flash');
  setTimeout(() => headingEl.classList.remove('outline-flash'), FLASH_MS);
}

export function refreshOutline() {
  if (!innerEl) return;
  innerEl.innerHTML = '';

  const heading = document.createElement('div');
  heading.className = 'sidebar__heading';
  heading.textContent = 'Outline';
  innerEl.appendChild(heading);

  const page = getPage();
  const headings = page ? Array.from(page.querySelectorAll(HEADING_SELECTOR)) : [];

  if (!headings.length) {
    const empty = document.createElement('div');
    empty.className = 'sidebar__item sidebar__item--empty';
    empty.textContent = 'No headings yet';
    innerEl.appendChild(empty);
    return;
  }

  for (const h of headings) {
    if (!h.dataset.lwOutlineId) h.dataset.lwOutlineId = `lw-heading-${headingCounter++}`;
    const level = Number(h.tagName.slice(1)) || 1;
    const item = document.createElement('div');
    item.className = 'sidebar__item';
    item.style.paddingLeft = `${8 + (level - 1) * 14}px`;
    item.textContent = h.textContent.trim() || '(untitled heading)';
    item.addEventListener('click', () => jumpTo(h));
    innerEl.appendChild(item);
  }
}

export function toggleSidebar() {
  if (!sidebarEl) return;
  sidebarEl.classList.toggle('is-collapsed');
}

export function initOutline() {
  sidebarEl = document.getElementById('outline-sidebar');
  innerEl = document.getElementById('outline-sidebar-inner');
  onChange(scheduleRefresh);
  refreshOutline();
}
