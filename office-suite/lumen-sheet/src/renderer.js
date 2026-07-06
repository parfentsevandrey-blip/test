// renderer.js — Lumen Sheet application entry point (ES module, loaded by
// index.html). Builds the whole UI, wires interaction, and talks to main.js
// only through window.lumen (exposed by preload.js).

import {
  Workbook,
  Sheet,
  defaultFormat,
  formatValue,
  isNumericValue,
  shiftFormulaRefsByOffset,
  DEFAULT_COL_WIDTH,
  DEFAULT_ROW_HEIGHT,
} from './grid.js';
import { colToLetter, cellKeyFromRC, shiftRefString } from './refUtils.js';
import { isError } from './formulaEngine.js';

const ROW_HEADER_WIDTH = 46;

const FUNCTION_DOCS = [
  { name: 'SUM', args: '(number1, [number2, ...])', desc: 'Adds up numeric values.' },
  { name: 'AVERAGE', args: '(number1, [number2, ...])', desc: 'Arithmetic mean of numeric values.' },
  { name: 'MIN', args: '(number1, [number2, ...])', desc: 'Smallest numeric value.' },
  { name: 'MAX', args: '(number1, [number2, ...])', desc: 'Largest numeric value.' },
  { name: 'COUNT', args: '(value1, [value2, ...])', desc: 'Counts numeric values.' },
  { name: 'COUNTA', args: '(value1, [value2, ...])', desc: 'Counts non-empty values.' },
  { name: 'IF', args: '(test, then, [else])', desc: 'Conditional value.' },
  { name: 'AND', args: '(logical1, [logical2, ...])', desc: 'TRUE if all args are TRUE.' },
  { name: 'OR', args: '(logical1, [logical2, ...])', desc: 'TRUE if any arg is TRUE.' },
  { name: 'NOT', args: '(logical)', desc: 'Inverts a boolean.' },
  { name: 'CONCAT', args: '(text1, [text2, ...])', desc: 'Joins text together.' },
  { name: 'ROUND', args: '(number, digits)', desc: 'Rounds to a number of digits.' },
  { name: 'ABS', args: '(number)', desc: 'Absolute value.' },
  { name: 'POWER', args: '(base, exponent)', desc: 'Base raised to exponent.' },
  { name: 'SQRT', args: '(number)', desc: 'Square root.' },
  { name: 'LEN', args: '(text)', desc: 'Length of text.' },
  { name: 'UPPER', args: '(text)', desc: 'Converts to upper case.' },
  { name: 'LOWER', args: '(text)', desc: 'Converts to lower case.' },
  { name: 'TRIM', args: '(text)', desc: 'Removes extra whitespace.' },
  { name: 'MOD', args: '(number, divisor)', desc: 'Remainder after division.' },
  { name: 'INT', args: '(number)', desc: 'Rounds down to an integer.' },
  { name: 'TODAY', args: '()', desc: 'Current date serial.' },
  { name: 'NOW', args: '()', desc: 'Current date+time serial.' },
  { name: 'PI', args: '()', desc: 'The constant pi.' },
];

const SWATCHES = [
  '#1b1a17', '#57534a', '#8b867a', '#c7c2b6', '#ffffff',
  '#9c7a3c', '#b3432e', '#c9622f', '#c98a2f', '#8a8f3c',
  '#4c7a5c', '#3c8f86', '#3c6f8f', '#4a5b9c', '#7a4a9c',
  '#9c4a7a',
];

// ---------------------------------------------------------------------------
// Global state
// ---------------------------------------------------------------------------

let workbook = new Workbook();
let currentFilePath = null;
let zoom = 1;

const state = {
  anchor: { col: 0, row: 0 },
  focus: { col: 0, row: 0 },
  dragging: false,
  editing: null, // { key, input }
  formulaEditing: false,
};

let clipboard = null; // { rows, width, height, sourceRange, cut }
let undoStack = [];
let redoStack = [];

let cellElements = new Map(); // key -> td
let colElements = []; // <col> elements, index 0 = row-header col
let lastHighlighted = [];
let findMatchesCache = [];
let findIndex = -1;

// DOM refs populated by buildShell()
let els = {};

// ---------------------------------------------------------------------------
// Boot
// ---------------------------------------------------------------------------

buildShell();
initTheme();
switchSheet(0, true);
setDirty(false);
window.lumen.onBeforeClose(handleBeforeClose);

// ---------------------------------------------------------------------------
// Shell construction
// ---------------------------------------------------------------------------

function buildShell() {
  const app = document.getElementById('app');
  app.innerHTML = `
    <div class="titlebar">
      <div class="titlebar__brand">
        <span class="brand-mark" aria-hidden="true">
          <svg class="brand-glyph" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <line x1="3" y1="9.6" x2="21" y2="9.6"/>
            <line x1="3" y1="14.4" x2="21" y2="14.4"/>
            <line x1="9.6" y1="3" x2="9.6" y2="21"/>
            <line x1="14.4" y1="3" x2="14.4" y2="21"/>
          </svg>
        </span>
        <span>Lumen Sheet</span>
      </div>
      <input class="titlebar__title titlebar__title-input" id="title-input" spellcheck="false" />
      <button class="btn-icon" id="btn-theme" data-tooltip="Toggle theme"></button>
    </div>

    <div class="menubar" id="menubar">
      <div class="menubar__item" data-menu="file">File</div>
      <div class="menubar__item" data-menu="edit">Edit</div>
      <div class="menubar__item" data-menu="view">View</div>
      <div class="menubar__item" data-menu="insert">Insert</div>
      <div class="menubar__item" data-menu="format">Format</div>
      <div class="menubar__item" data-menu="help">Help</div>
    </div>

    <div class="toolbar" id="toolbar">
      <div class="toolbar__group">
        <button class="btn-icon" id="btn-undo" data-icon="undo" data-tooltip="Undo (Ctrl+Z)"></button>
        <button class="btn-icon" id="btn-redo" data-icon="redo" data-tooltip="Redo (Ctrl+Y)"></button>
      </div>
      <div class="toolbar__sep"></div>
      <div class="toolbar__group">
        <button class="btn-icon" id="btn-bold" data-icon="bold" data-tooltip="Bold (Ctrl+B)"></button>
        <button class="btn-icon" id="btn-italic" data-icon="italic" data-tooltip="Italic (Ctrl+I)"></button>
        <button class="btn-icon" id="btn-underline" data-icon="underline" data-tooltip="Underline (Ctrl+U)"></button>
      </div>
      <div class="toolbar__sep"></div>
      <div class="toolbar__group">
        <button class="btn-icon swatch-btn" id="btn-text-color" data-icon="type" data-tooltip="Text color"><span class="swatch-btn__bar" id="text-color-bar"></span></button>
        <button class="btn-icon swatch-btn" id="btn-fill-color" data-icon="paint-bucket" data-tooltip="Fill color"><span class="swatch-btn__bar" id="fill-color-bar"></span></button>
      </div>
      <div class="toolbar__sep"></div>
      <div class="toolbar__group">
        <button class="btn-icon" id="btn-align-left" data-icon="align-left" data-tooltip="Align left"></button>
        <button class="btn-icon" id="btn-align-center" data-icon="align-center" data-tooltip="Align center"></button>
        <button class="btn-icon" id="btn-align-right" data-icon="align-right" data-tooltip="Align right"></button>
      </div>
      <div class="toolbar__sep"></div>
      <div class="toolbar__group">
        <button class="btn-icon" id="btn-borders" data-icon="grid-3x3" data-tooltip="Toggle borders"></button>
      </div>
      <div class="toolbar__sep"></div>
      <div class="toolbar__group">
        <button class="btn-icon" id="btn-currency" data-icon="dollar-sign" data-tooltip="Currency format"></button>
        <button class="btn-icon" id="btn-percent" data-icon="percent" data-tooltip="Percent format"></button>
        <select class="select number-format-select" id="number-format-select" data-tooltip="Number format">
          <option value="general">General</option>
          <option value="number">Number</option>
          <option value="currency">Currency</option>
          <option value="percent">Percent</option>
          <option value="date">Date</option>
        </select>
      </div>
      <div class="toolbar__sep"></div>
      <div class="toolbar__group">
        <button class="btn-icon" id="btn-function" data-icon="sigma" data-tooltip="Insert function"></button>
      </div>
    </div>

    <div class="formula-bar">
      <span class="formula-bar__fx">fx</span>
      <span class="formula-bar__ref" id="formula-ref">A1</span>
      <input class="formula-bar__input" id="formula-input" spellcheck="false" autocomplete="off" />
    </div>

    <div class="sheet-viewport" id="sheet-viewport">
      <div class="sheet-canvas" id="sheet-canvas"></div>
    </div>

    <div class="sheet-tabs" id="sheet-tabs"></div>

    <div class="statusbar">
      <span class="statusbar__stat" id="stat-sum">Sum: —</span>
      <span class="statusbar__stat" id="stat-avg">Average: —</span>
      <span class="statusbar__stat" id="stat-count">Count: 0</span>
      <span class="statusbar__spacer"></span>
      <div class="statusbar__zoom">
        <span id="zoom-label">100%</span>
        <input type="range" id="zoom-range" min="50" max="150" step="10" value="100" />
      </div>
    </div>
  `;

  els.titleInput = document.getElementById('title-input');
  els.themeBtn = document.getElementById('btn-theme');
  els.menubar = document.getElementById('menubar');
  els.toolbar = document.getElementById('toolbar');
  els.formulaRef = document.getElementById('formula-ref');
  els.formulaInput = document.getElementById('formula-input');
  els.sheetViewport = document.getElementById('sheet-viewport');
  els.sheetCanvas = document.getElementById('sheet-canvas');
  els.sheetTabs = document.getElementById('sheet-tabs');
  els.statSum = document.getElementById('stat-sum');
  els.statAvg = document.getElementById('stat-avg');
  els.statCount = document.getElementById('stat-count');
  els.zoomLabel = document.getElementById('zoom-label');
  els.zoomRange = document.getElementById('zoom-range');
  els.numberFormatSelect = document.getElementById('number-format-select');

  applyIcons(app);
  wireTitlebar();
  wireMenubar();
  wireToolbar();
  wireFormulaBar();
  wireZoom();
}

function applyIcons(root) {
  root.querySelectorAll('[data-icon]').forEach((btn) => {
    const name = btn.dataset.icon;
    const svg = window.lumen.icons[name];
    if (svg) {
      // Preserve any existing child markup (e.g. the swatch color bar) by
      // inserting the icon before it.
      const extra = Array.from(btn.children);
      btn.innerHTML = svg;
      extra.forEach((el) => btn.appendChild(el));
    }
  });
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// ---------------------------------------------------------------------------
// Titlebar / theme
// ---------------------------------------------------------------------------

function wireTitlebar() {
  els.titleInput.addEventListener('input', () => {
    workbook.title = els.titleInput.value;
    setDirty(true);
  });
  els.titleInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') els.titleInput.blur();
  });
  els.themeBtn.addEventListener('click', toggleTheme);
}

function initTheme() {
  const saved = localStorage.getItem('lumen-theme');
  if (saved === 'dark' || saved === 'light') document.documentElement.dataset.theme = saved;
  updateThemeIcon();
}

function toggleTheme() {
  const isDark = currentIsDark();
  const next = isDark ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  localStorage.setItem('lumen-theme', next);
  updateThemeIcon();
}

function currentIsDark() {
  const t = document.documentElement.dataset.theme;
  if (t === 'dark') return true;
  if (t === 'light') return false;
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function updateThemeIcon() {
  els.themeBtn.innerHTML = window.lumen.icons[currentIsDark() ? 'sun' : 'moon'];
}

// ---------------------------------------------------------------------------
// Generic menu / popover / dialog helpers
// ---------------------------------------------------------------------------

let openMenuEl = null;
let openMenuAnchor = null;

function closeAllMenus() {
  if (openMenuEl) {
    openMenuEl.remove();
    openMenuEl = null;
  }
  if (openMenuAnchor) {
    openMenuAnchor.classList.remove('is-open');
    openMenuAnchor = null;
  }
}

function buildMenuEl(items) {
  const menu = document.createElement('div');
  menu.className = 'menu';
  for (const item of items) {
    if (item.type === 'sep') {
      const sep = document.createElement('div');
      sep.className = 'menu__sep';
      menu.appendChild(sep);
      continue;
    }
    const el = document.createElement('div');
    el.className = 'menu__item';
    if (item.disabled) el.setAttribute('aria-disabled', 'true');
    const label = document.createElement('span');
    label.textContent = item.label;
    el.appendChild(label);
    if (item.shortcut) {
      const sc = document.createElement('span');
      sc.className = 'menu__shortcut';
      sc.textContent = item.shortcut;
      el.appendChild(sc);
    }
    if (!item.disabled) {
      el.addEventListener('click', (e) => {
        e.stopPropagation();
        closeAllMenus();
        item.onClick && item.onClick();
      });
    }
    menu.appendChild(el);
  }
  return menu;
}

function toggleMenu(anchorEl, items) {
  if (openMenuAnchor === anchorEl) {
    closeAllMenus();
    return;
  }
  closeAllMenus();
  const menu = buildMenuEl(items);
  document.body.appendChild(menu);
  const rect = anchorEl.getBoundingClientRect();
  menu.style.left = rect.left + 'px';
  menu.style.top = rect.bottom + 'px';
  const menuRect = menu.getBoundingClientRect();
  if (menuRect.right > window.innerWidth) menu.style.left = Math.max(4, window.innerWidth - menuRect.width - 8) + 'px';
  anchorEl.classList.add('is-open');
  openMenuEl = menu;
  openMenuAnchor = anchorEl;
}

function showContextMenu(x, y, items) {
  closeAllMenus();
  const menu = buildMenuEl(items);
  menu.classList.add('context-menu');
  document.body.appendChild(menu);
  menu.style.left = x + 'px';
  menu.style.top = y + 'px';
  const rect = menu.getBoundingClientRect();
  if (rect.right > window.innerWidth) menu.style.left = Math.max(4, window.innerWidth - rect.width - 8) + 'px';
  if (rect.bottom > window.innerHeight) menu.style.top = Math.max(4, window.innerHeight - rect.height - 8) + 'px';
  openMenuEl = menu;
  openMenuAnchor = null;
}

document.addEventListener('mousedown', (e) => {
  if (openMenuEl && !openMenuEl.contains(e.target) && !(openMenuAnchor && openMenuAnchor.contains(e.target))) {
    closeAllMenus();
  }
  if (openPopoverEl && !openPopoverEl.contains(e.target) && !(openPopoverAnchor && openPopoverAnchor.contains(e.target))) {
    closePopover();
  }
});

let openPopoverEl = null;
let openPopoverAnchor = null;

function closePopover() {
  if (openPopoverEl) {
    openPopoverEl.remove();
    openPopoverEl = null;
    openPopoverAnchor = null;
  }
}

function showPopover(anchorEl, contentEl) {
  if (openPopoverAnchor === anchorEl) {
    closePopover();
    return;
  }
  closePopover();
  const pop = document.createElement('div');
  pop.className = 'popover';
  pop.appendChild(contentEl);
  document.body.appendChild(pop);
  const rect = anchorEl.getBoundingClientRect();
  pop.style.left = rect.left + 'px';
  pop.style.top = rect.bottom + 4 + 'px';
  const popRect = pop.getBoundingClientRect();
  if (popRect.right > window.innerWidth) pop.style.left = Math.max(4, window.innerWidth - popRect.width - 8) + 'px';
  openPopoverEl = pop;
  openPopoverAnchor = anchorEl;
}

function showDialog({ title, bodyHTML, buttons, onClose }) {
  const overlay = document.createElement('div');
  overlay.className = 'dialog-overlay';
  const dialog = document.createElement('div');
  dialog.className = 'dialog';
  dialog.innerHTML = `<h2>${escapeHtml(title)}</h2><div class="dialog__body">${bodyHTML || ''}</div>`;
  const actions = document.createElement('div');
  actions.className = 'dialog__actions';
  for (const b of buttons || [{ label: 'OK' }]) {
    const btn = document.createElement('button');
    btn.className = 'btn' + (b.variant === 'primary' ? ' btn--primary' : '');
    btn.textContent = b.label;
    btn.addEventListener('click', () => {
      if (!b.keepOpen) overlay.remove();
      b.onClick && b.onClick();
    });
    actions.appendChild(btn);
  }
  dialog.appendChild(actions);
  overlay.appendChild(dialog);
  document.body.appendChild(overlay);
  overlay.addEventListener('mousedown', (e) => {
    if (e.target === overlay) {
      overlay.remove();
      onClose && onClose();
    }
  });
  return { overlay, dialog, bodyEl: dialog.querySelector('.dialog__body') };
}

// ---------------------------------------------------------------------------
// Menubar
// ---------------------------------------------------------------------------

function wireMenubar() {
  els.menubar.querySelectorAll('.menubar__item').forEach((item) => {
    item.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleMenu(item, menuItemsFor(item.dataset.menu));
    });
  });
}

function menuItemsFor(name) {
  switch (name) {
    case 'file':
      return [
        { label: 'New', shortcut: 'Ctrl+N', onClick: newWorkbook },
        { label: 'Open…', shortcut: 'Ctrl+O', onClick: openWorkbookFlow },
        { label: 'Save', shortcut: 'Ctrl+S', onClick: saveWorkbook },
        { label: 'Save As…', shortcut: 'Ctrl+Shift+S', onClick: saveWorkbookAs },
        { type: 'sep' },
        { label: 'Import CSV…', onClick: importCsv },
        { label: 'Import Excel (.xlsx)…', onClick: importXlsx },
        { type: 'sep' },
        { label: 'Export CSV…', onClick: exportCsv },
        { label: 'Export Excel (.xlsx)…', onClick: exportXlsx },
      ];
    case 'edit':
      return [
        { label: 'Undo', shortcut: 'Ctrl+Z', onClick: undo, disabled: undoStack.length === 0 },
        { label: 'Redo', shortcut: 'Ctrl+Y', onClick: redo, disabled: redoStack.length === 0 },
        { type: 'sep' },
        { label: 'Cut', shortcut: 'Ctrl+X', onClick: cutSelection },
        { label: 'Copy', shortcut: 'Ctrl+C', onClick: copySelection },
        { label: 'Paste', shortcut: 'Ctrl+V', onClick: pasteAtActive },
        { type: 'sep' },
        { label: 'Find…', shortcut: 'Ctrl+F', onClick: openFindDialog },
      ];
    case 'view':
      return [
        { label: 'Zoom In', onClick: () => setZoom(zoom + 0.1) },
        { label: 'Zoom Out', onClick: () => setZoom(zoom - 0.1) },
        { label: 'Reset Zoom', onClick: () => setZoom(1) },
        { type: 'sep' },
        { label: 'Toggle Theme', onClick: toggleTheme },
      ];
    case 'insert':
      return [
        { label: 'Row Above', onClick: () => performStructuralChange((s) => s.insertRow(state.anchor.row)) },
        { label: 'Row Below', onClick: () => performStructuralChange((s) => s.insertRow(state.anchor.row + 1)) },
        { label: 'Column Left', onClick: () => performStructuralChange((s) => s.insertCol(state.anchor.col)) },
        { label: 'Column Right', onClick: () => performStructuralChange((s) => s.insertCol(state.anchor.col + 1)) },
        { type: 'sep' },
        { label: 'Sheet', onClick: addSheet },
        { type: 'sep' },
        { label: 'Function…', onClick: () => showPopover(els.toolbar.querySelector('#btn-function'), buildFunctionListEl()) },
      ];
    case 'format':
      return [
        { label: 'Bold', shortcut: 'Ctrl+B', onClick: toggleBold },
        { label: 'Italic', shortcut: 'Ctrl+I', onClick: toggleItalic },
        { label: 'Underline', shortcut: 'Ctrl+U', onClick: toggleUnderline },
        { type: 'sep' },
        { label: 'Align Left', onClick: () => setAlign('left') },
        { label: 'Align Center', onClick: () => setAlign('center') },
        { label: 'Align Right', onClick: () => setAlign('right') },
        { type: 'sep' },
        { label: 'Toggle Borders', onClick: toggleBorders },
        { label: 'Clear Formatting', onClick: clearFormatting },
      ];
    case 'help':
      return [{ label: 'About Lumen Sheet', onClick: showAbout }];
    default:
      return [];
  }
}

function showAbout() {
  showDialog({
    title: 'About Lumen Sheet',
    bodyHTML: `
      <div class="dialog__about-brand">
        <span class="brand-mark" aria-hidden="true" style="width:40px;height:40px;border-radius:10px;">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <line x1="3" y1="9.6" x2="21" y2="9.6"/>
            <line x1="3" y1="14.4" x2="21" y2="14.4"/>
            <line x1="9.6" y1="3" x2="9.6" y2="21"/>
            <line x1="14.4" y1="3" x2="14.4" y2="21"/>
          </svg>
        </span>
        <div>
          <div style="font-weight:600;">Lumen Sheet</div>
          <div style="font-size:12px;color:var(--ink-400);">Version 1.0.0</div>
        </div>
      </div>
      <p>A premium, minimalist spreadsheet for Windows desktop. Built with plain
      HTML, CSS and JavaScript on Electron — no bundler, no framework.</p>
    `,
    buttons: [{ label: 'Close', variant: 'primary' }],
  });
}

// ---------------------------------------------------------------------------
// Toolbar
// ---------------------------------------------------------------------------

function wireToolbar() {
  document.getElementById('btn-undo').addEventListener('click', undo);
  document.getElementById('btn-redo').addEventListener('click', redo);
  document.getElementById('btn-bold').addEventListener('click', toggleBold);
  document.getElementById('btn-italic').addEventListener('click', toggleItalic);
  document.getElementById('btn-underline').addEventListener('click', toggleUnderline);
  document.getElementById('btn-align-left').addEventListener('click', () => setAlign('left'));
  document.getElementById('btn-align-center').addEventListener('click', () => setAlign('center'));
  document.getElementById('btn-align-right').addEventListener('click', () => setAlign('right'));
  document.getElementById('btn-borders').addEventListener('click', toggleBorders);
  document.getElementById('btn-currency').addEventListener('click', () => setNumberFormat('currency'));
  document.getElementById('btn-percent').addEventListener('click', () => setNumberFormat('percent'));
  els.numberFormatSelect.addEventListener('change', () => setNumberFormat(els.numberFormatSelect.value));
  document.getElementById('btn-function').addEventListener('click', (e) => {
    showPopover(e.currentTarget, buildFunctionListEl());
  });
  document.getElementById('btn-text-color').addEventListener('click', (e) => {
    showPopover(e.currentTarget, buildColorSwatchEl('color'));
  });
  document.getElementById('btn-fill-color').addEventListener('click', (e) => {
    showPopover(e.currentTarget, buildColorSwatchEl('bg'));
  });
}

function buildFunctionListEl() {
  const wrap = document.createElement('div');
  wrap.className = 'fn-list';
  for (const fn of FUNCTION_DOCS) {
    const item = document.createElement('div');
    item.className = 'fn-list__item';
    item.innerHTML = `<b>${fn.name}</b>${escapeHtml(fn.args)}<div>${escapeHtml(fn.desc)}</div>`;
    item.addEventListener('click', () => {
      closePopover();
      insertFunctionSnippet(fn.name);
    });
    wrap.appendChild(item);
  }
  return wrap;
}

function insertFunctionSnippet(name) {
  const snippet = `${name}()`;
  if (state.editing) {
    const input = state.editing.input;
    const start = input.selectionStart ?? input.value.length;
    const end = input.selectionEnd ?? input.value.length;
    const val = input.value;
    input.value = val.slice(0, start) + snippet + val.slice(end);
    const cursor = start + name.length + 1;
    input.focus();
    input.setSelectionRange(cursor, cursor);
  } else {
    const key = activeKey();
    startEditing(key, 'insert-fn', { text: '=' + snippet, cursor: 1 + name.length + 1 });
  }
}

function buildColorSwatchEl(kind) {
  const wrap = document.createElement('div');
  const grid = document.createElement('div');
  grid.className = 'swatch-grid';
  for (const hex of SWATCHES) {
    const sw = document.createElement('div');
    sw.className = 'swatch';
    sw.style.background = hex;
    sw.addEventListener('click', () => {
      closePopover();
      applyFormatPatch(() => ({ [kind]: hex }));
    });
    grid.appendChild(sw);
  }
  wrap.appendChild(grid);
  const row = document.createElement('div');
  row.className = 'popover__custom-row';
  const input = document.createElement('input');
  input.type = 'color';
  input.value = '#1b1a17';
  input.addEventListener('input', () => {
    applyFormatPatch(() => ({ [kind]: input.value }));
  });
  const clear = document.createElement('button');
  clear.className = 'btn-icon popover__clear';
  clear.textContent = 'Clear';
  clear.style.width = 'auto';
  clear.style.padding = '0 8px';
  clear.addEventListener('click', () => {
    closePopover();
    applyFormatPatch(() => ({ [kind]: null }));
  });
  row.appendChild(input);
  row.appendChild(clear);
  wrap.appendChild(row);
  return wrap;
}

// ---------------------------------------------------------------------------
// Formula bar
// ---------------------------------------------------------------------------

function wireFormulaBar() {
  els.formulaInput.addEventListener('focus', () => {
    state.formulaEditing = true;
  });
  els.formulaInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      commitEdit(activeKey(), els.formulaInput.value, { advance: 'down' });
      state.formulaEditing = false;
      els.formulaInput.blur();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      state.formulaEditing = false;
      updateFormulaBarFromActive();
      els.formulaInput.blur();
    }
  });
  els.formulaInput.addEventListener('blur', () => {
    if (state.formulaEditing) {
      commitEdit(activeKey(), els.formulaInput.value, { advance: null });
      state.formulaEditing = false;
    }
  });
}

function updateFormulaBarFromActive() {
  const key = activeKey();
  const cell = workbook.activeSheet.getCell(key);
  els.formulaRef.textContent = key;
  els.formulaInput.value = cell ? cell.raw : '';
}

// ---------------------------------------------------------------------------
// Zoom
// ---------------------------------------------------------------------------

function wireZoom() {
  els.zoomRange.addEventListener('input', () => {
    setZoom(parseInt(els.zoomRange.value, 10) / 100);
  });
}

function setZoom(value) {
  zoom = Math.min(1.5, Math.max(0.5, Math.round(value * 10) / 10));
  document.documentElement.style.setProperty('--grid-zoom', zoom);
  els.zoomLabel.textContent = Math.round(zoom * 100) + '%';
  els.zoomRange.value = String(Math.round(zoom * 100));
}

// ---------------------------------------------------------------------------
// Sheet switching / tabs
// ---------------------------------------------------------------------------

function switchSheet(idx, full) {
  workbook.activeSheetIndex = idx;
  state.anchor = { col: 0, row: 0 };
  state.focus = { col: 0, row: 0 };
  renderSheetTabs();
  renderGrid();
}

function renderSheetTabs() {
  els.sheetTabs.innerHTML = '';
  workbook.sheets.forEach((sheet, idx) => {
    const tab = document.createElement('div');
    tab.className = 'sheet-tab' + (idx === workbook.activeSheetIndex ? ' is-active' : '');
    tab.textContent = sheet.name;
    tab.addEventListener('click', () => {
      if (idx !== workbook.activeSheetIndex) switchSheet(idx);
    });
    tab.addEventListener('dblclick', () => startRenameTab(tab, idx));
    tab.addEventListener('contextmenu', (e) => {
      e.preventDefault();
      showContextMenu(e.clientX, e.clientY, [
        { label: 'Rename', onClick: () => startRenameTab(tab, idx) },
        { label: 'Delete', disabled: workbook.sheets.length <= 1, onClick: () => deleteSheetTab(idx) },
      ]);
    });
    els.sheetTabs.appendChild(tab);
  });
  const addBtn = document.createElement('button');
  addBtn.className = 'btn-icon sheet-tabs__add';
  addBtn.dataset.icon = 'plus';
  addBtn.dataset.tooltip = 'Add sheet';
  addBtn.addEventListener('click', addSheet);
  els.sheetTabs.appendChild(addBtn);
  applyIcons(els.sheetTabs);
}

function addSheet() {
  workbook.addSheet();
  switchSheet(workbook.sheets.length - 1);
  setDirty(true);
}

function startRenameTab(tabEl, idx) {
  const sheet = workbook.sheets[idx];
  tabEl.textContent = '';
  const input = document.createElement('input');
  input.className = 'sheet-tab__input';
  input.value = sheet.name;
  tabEl.appendChild(input);
  input.focus();
  input.select();
  function commit() {
    const name = input.value.trim();
    if (name && !workbook.sheets.some((s, i) => i !== idx && s.name === name)) {
      sheet.name = name;
      setDirty(true);
    }
    renderSheetTabs();
  }
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') input.blur();
    if (e.key === 'Escape') {
      input.value = sheet.name;
      input.blur();
    }
    e.stopPropagation();
  });
  input.addEventListener('blur', commit);
}

function deleteSheetTab(idx) {
  if (workbook.sheets.length <= 1) return;
  const removed = workbook.removeSheet(idx);
  if (removed) {
    setDirty(true);
    switchSheet(workbook.activeSheetIndex);
  }
}

// ---------------------------------------------------------------------------
// Grid rendering
// ---------------------------------------------------------------------------

function renderGrid() {
  const sheet = workbook.activeSheet;
  cellElements = new Map();
  colElements = [];

  const table = document.createElement('table');
  table.className = 'lumen-grid';

  const colgroup = document.createElement('colgroup');
  const rowHeaderCol = document.createElement('col');
  rowHeaderCol.style.width = ROW_HEADER_WIDTH + 'px';
  colgroup.appendChild(rowHeaderCol);
  colElements.push(rowHeaderCol);
  for (let c = 0; c < sheet.colCount; c++) {
    const col = document.createElement('col');
    col.style.width = (sheet.colWidths[c] || DEFAULT_COL_WIDTH) + 'px';
    colgroup.appendChild(col);
    colElements.push(col);
  }
  table.appendChild(colgroup);

  const thead = document.createElement('thead');
  const headRow = document.createElement('tr');
  const corner = document.createElement('th');
  corner.className = 'corner-cell';
  headRow.appendChild(corner);
  for (let c = 0; c < sheet.colCount; c++) {
    const th = document.createElement('th');
    th.className = 'col-th';
    th.dataset.col = String(c);
    const inner = document.createElement('div');
    inner.className = 'th-inner';
    inner.textContent = colToLetter(c);
    const handle = document.createElement('div');
    handle.className = 'col-resize-handle';
    attachColResize(handle, c);
    inner.appendChild(handle);
    th.appendChild(inner);
    th.addEventListener('click', () => selectWholeColumn(c));
    headRow.appendChild(th);
  }
  thead.appendChild(headRow);
  table.appendChild(thead);

  const tbody = document.createElement('tbody');
  for (let r = 0; r < sheet.rowCount; r++) {
    const tr = document.createElement('tr');
    tr.style.height = (sheet.rowHeights[r] || DEFAULT_ROW_HEIGHT) + 'px';
    const rh = document.createElement('td');
    rh.className = 'row-header';
    rh.dataset.row = String(r);
    const rhInner = document.createElement('div');
    rhInner.className = 'rh-inner';
    rhInner.textContent = String(r + 1);
    const rHandle = document.createElement('div');
    rHandle.className = 'row-resize-handle';
    attachRowResize(rHandle, r);
    rhInner.appendChild(rHandle);
    rh.appendChild(rhInner);
    rh.addEventListener('click', () => selectWholeRow(r));
    tr.appendChild(rh);
    for (let c = 0; c < sheet.colCount; c++) {
      const key = cellKeyFromRC(c, r);
      const td = document.createElement('td');
      td.className = 'cell';
      td.dataset.col = String(c);
      td.dataset.row = String(r);
      td.dataset.key = key;
      tr.appendChild(td);
      cellElements.set(key, td);
    }
    tbody.appendChild(tr);
  }
  table.appendChild(tbody);

  els.sheetCanvas.innerHTML = '';
  els.sheetCanvas.appendChild(table);

  attachGridEvents(tbody, thead);
  renderAllCellContents();
  updateSelectionUI();
}

function attachGridEvents(tbody, thead) {
  tbody.addEventListener('mousedown', (e) => {
    const td = e.target.closest('td.cell');
    if (!td) return;
    if (state.editing) commitEdit(state.editing.key, state.editing.input.value, { advance: null });
    const col = parseInt(td.dataset.col, 10);
    const row = parseInt(td.dataset.row, 10);
    if (e.shiftKey) {
      state.focus = { col, row };
    } else {
      state.anchor = { col, row };
      state.focus = { col, row };
    }
    state.dragging = true;
    updateSelectionUI();
  });

  tbody.addEventListener('dblclick', (e) => {
    const td = e.target.closest('td.cell');
    if (!td) return;
    startEditing(td.dataset.key, 'existing');
  });

  tbody.addEventListener('contextmenu', (e) => {
    const rh = e.target.closest('td.row-header');
    if (!rh) return;
    e.preventDefault();
    const row = parseInt(rh.dataset.row, 10);
    showContextMenu(e.clientX, e.clientY, [
      { label: 'Insert row above', onClick: () => performStructuralChange((s) => s.insertRow(row)) },
      { label: 'Insert row below', onClick: () => performStructuralChange((s) => s.insertRow(row + 1)) },
      { type: 'sep' },
      { label: 'Delete row', onClick: () => performStructuralChange((s) => s.deleteRow(row)) },
    ]);
  });

  thead.addEventListener('contextmenu', (e) => {
    const th = e.target.closest('th.col-th');
    if (!th) return;
    e.preventDefault();
    const col = parseInt(th.dataset.col, 10);
    showContextMenu(e.clientX, e.clientY, [
      { label: 'Insert column left', onClick: () => performStructuralChange((s) => s.insertCol(col)) },
      { label: 'Insert column right', onClick: () => performStructuralChange((s) => s.insertCol(col + 1)) },
      { type: 'sep' },
      { label: 'Delete column', onClick: () => performStructuralChange((s) => s.deleteCol(col)) },
    ]);
  });
}

document.addEventListener('mousemove', (e) => {
  if (!state.dragging) return;
  const el = document.elementFromPoint(e.clientX, e.clientY);
  const td = el && el.closest && el.closest('td.cell');
  if (!td) return;
  const col = parseInt(td.dataset.col, 10);
  const row = parseInt(td.dataset.row, 10);
  if (state.focus.col === col && state.focus.row === row) return;
  state.focus = { col, row };
  updateSelectionUI();
});

document.addEventListener('mouseup', () => {
  state.dragging = false;
});

function selectWholeColumn(col) {
  const sheet = workbook.activeSheet;
  state.anchor = { col, row: 0 };
  state.focus = { col, row: sheet.rowCount - 1 };
  updateSelectionUI();
}

function selectWholeRow(row) {
  const sheet = workbook.activeSheet;
  state.anchor = { col: 0, row };
  state.focus = { col: sheet.colCount - 1, row };
  updateSelectionUI();
}

function attachColResize(handle, col) {
  handle.addEventListener('mousedown', (e) => {
    e.preventDefault();
    e.stopPropagation();
    const sheet = workbook.activeSheet;
    const startX = e.clientX;
    const startWidth = sheet.colWidths[col] || DEFAULT_COL_WIDTH;
    function onMove(ev) {
      const width = Math.max(32, startWidth + (ev.clientX - startX));
      sheet.colWidths[col] = width;
      colElements[col + 1].style.width = width + 'px';
    }
    function onUp() {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      setDirty(true);
    }
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  });
}

function attachRowResize(handle, row) {
  handle.addEventListener('mousedown', (e) => {
    e.preventDefault();
    e.stopPropagation();
    const sheet = workbook.activeSheet;
    const startY = e.clientY;
    const startHeight = sheet.rowHeights[row] || DEFAULT_ROW_HEIGHT;
    const tr = handle.closest('tr');
    function onMove(ev) {
      const height = Math.max(18, startHeight + (ev.clientY - startY));
      sheet.rowHeights[row] = height;
      tr.style.height = height + 'px';
    }
    function onUp() {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      setDirty(true);
    }
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  });
}

// ---------------------------------------------------------------------------
// Cell display
// ---------------------------------------------------------------------------

function renderAllCellContents() {
  const sheet = workbook.activeSheet;
  for (const [key, td] of cellElements) {
    applyCellDisplay(td, sheet.getCell(key));
  }
  updateStatusBar();
}

function applyCellDisplay(td, cell) {
  const fmt = cell ? cell.format : defaultFormat();
  const value = cell ? cell.computed : '';
  const errored = isError(value);
  td.textContent = formatValue(value, fmt.numberFormat);
  const numeric = isNumericValue(value);
  td.classList.remove('align-left', 'align-center', 'align-right');
  td.classList.toggle('is-numeric', numeric && !fmt.align);
  if (fmt.align) td.classList.add('align-' + fmt.align);
  td.classList.toggle('is-bold', !!fmt.bold);
  td.classList.toggle('is-italic', !!fmt.italic);
  td.classList.toggle('is-underline', !!fmt.underline);
  td.classList.toggle('is-error', errored);
  td.classList.toggle('has-border', !!fmt.border);
  td.style.color = fmt.color || '';
  td.style.background = fmt.bg || '';
}

function refreshCell(key) {
  const td = cellElements.get(key);
  if (!td) return;
  applyCellDisplay(td, workbook.activeSheet.getCell(key));
}

// ---------------------------------------------------------------------------
// Selection
// ---------------------------------------------------------------------------

function activeKey() {
  return cellKeyFromRC(state.anchor.col, state.anchor.row);
}

function normalizedSelection() {
  const a = state.anchor;
  const f = state.focus;
  return {
    minCol: Math.min(a.col, f.col),
    maxCol: Math.max(a.col, f.col),
    minRow: Math.min(a.row, f.row),
    maxRow: Math.max(a.row, f.row),
  };
}

function updateSelectionUI() {
  for (const td of lastHighlighted) td.classList.remove('is-selected', 'is-in-range');
  lastHighlighted = [];
  const range = normalizedSelection();
  const activeK = activeKey();
  for (let r = range.minRow; r <= range.maxRow; r++) {
    for (let c = range.minCol; c <= range.maxCol; c++) {
      const key = cellKeyFromRC(c, r);
      const td = cellElements.get(key);
      if (!td) continue;
      td.classList.add(key === activeK ? 'is-selected' : 'is-in-range');
      lastHighlighted.push(td);
    }
  }
  if (!state.formulaEditing) updateFormulaBarFromActive();
  updateStatusBar();
}

function scrollActiveIntoView() {
  const td = cellElements.get(activeKey());
  if (td) td.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}

function moveActive(direction, extend) {
  const sheet = workbook.activeSheet;
  const base = extend ? state.focus : state.anchor;
  let { col, row } = base;
  if (direction === 'up') row = Math.max(0, row - 1);
  if (direction === 'down') row = Math.min(sheet.rowCount - 1, row + 1);
  if (direction === 'left') col = Math.max(0, col - 1);
  if (direction === 'right') col = Math.min(sheet.colCount - 1, col + 1);
  if (extend) {
    state.focus = { col, row };
  } else {
    state.anchor = { col, row };
    state.focus = { col, row };
  }
  updateSelectionUI();
  scrollActiveIntoView();
}

function clampSelectionToSheet() {
  const sheet = workbook.activeSheet;
  const clamp = (p) => ({ col: Math.min(p.col, sheet.colCount - 1), row: Math.min(p.row, sheet.rowCount - 1) });
  state.anchor = clamp(state.anchor);
  state.focus = clamp(state.focus);
}

// ---------------------------------------------------------------------------
// Editing
// ---------------------------------------------------------------------------

function startEditing(key, mode, payload) {
  if (state.editing) commitEdit(state.editing.key, state.editing.input.value, { advance: null });
  const sheet = workbook.activeSheet;
  const cell = sheet.getCell(key);
  const raw = cell ? cell.raw : '';
  const td = cellElements.get(key);
  if (!td) return;
  const rect = td.getBoundingClientRect();
  const input = document.createElement('input');
  input.className = 'cell-editor';
  input.style.left = rect.left + 'px';
  input.style.top = rect.top + 'px';
  input.style.width = rect.width + 'px';
  input.style.height = rect.height + 'px';
  document.body.appendChild(input);

  let value = raw;
  let cursor = String(raw).length;
  if (mode === 'char') {
    value = payload;
    cursor = value.length;
  } else if (mode === 'insert-fn') {
    value = payload.text;
    cursor = payload.cursor;
  }
  input.value = value;
  input.focus();
  input.setSelectionRange(cursor, cursor);

  state.editing = { key, input };
  els.formulaRef.textContent = key;
  els.formulaInput.value = value;

  input.addEventListener('keydown', onEditorKeydown);
  input.addEventListener('input', () => {
    els.formulaInput.value = input.value;
  });
}

function onEditorKeydown(e) {
  if (!state.editing) return;
  const key = state.editing.key;
  if (e.key === 'Enter') {
    e.preventDefault();
    e.stopPropagation();
    commitEdit(key, e.target.value, { advance: e.shiftKey ? 'up' : 'down' });
  } else if (e.key === 'Tab') {
    e.preventDefault();
    e.stopPropagation();
    commitEdit(key, e.target.value, { advance: e.shiftKey ? 'left' : 'right' });
  } else if (e.key === 'Escape') {
    e.preventDefault();
    e.stopPropagation();
    removeEditor();
    updateFormulaBarFromActive();
  } else {
    e.stopPropagation();
  }
}

function removeEditor() {
  if (state.editing) {
    state.editing.input.remove();
    state.editing = null;
  }
}

function commitEdit(key, rawValue, { advance }) {
  const sheet = workbook.activeSheet;
  removeEditor();
  const before = sheet.snapshotCell(key);
  if (before.raw !== rawValue) {
    applyChanges([{ key, before, after: { raw: rawValue, format: before.format } }]);
  }
  if (advance) moveActive(advance, false);
  else updateFormulaBarFromActive();
}

// ---------------------------------------------------------------------------
// Undo / redo
// ---------------------------------------------------------------------------

function updateUndoRedoButtons() {
  const undoBtn = document.getElementById('btn-undo');
  const redoBtn = document.getElementById('btn-redo');
  if (undoBtn) undoBtn.disabled = undoStack.length === 0;
  if (redoBtn) redoBtn.disabled = redoStack.length === 0;
}

function resetUndoRedo() {
  undoStack = [];
  redoStack = [];
  updateUndoRedoButtons();
}

function applyChanges(changes) {
  if (!changes.length) return;
  const sheet = workbook.activeSheet;
  for (const c of changes) sheet.restoreCell(c.key, c.after);
  undoStack.push({ type: 'cells', sheetIndex: workbook.activeSheetIndex, changes });
  redoStack = [];
  setDirty(true);
  renderAllCellContents();
  updateFormulaBarFromActive();
  updateUndoRedoButtons();
}

function performStructuralChange(mutateFn) {
  const sheet = workbook.activeSheet;
  const before = sheet.toJSON();
  mutateFn(sheet);
  const after = sheet.toJSON();
  undoStack.push({ type: 'structural', sheetIndex: workbook.activeSheetIndex, before, after });
  redoStack = [];
  setDirty(true);
  clampSelectionToSheet();
  renderGrid();
  updateUndoRedoButtons();
}

function undo() {
  const entry = undoStack.pop();
  if (!entry) return;
  redoStack.push(entry);
  applyUndoRedoEntry(entry, true);
  updateUndoRedoButtons();
}

function redo() {
  const entry = redoStack.pop();
  if (!entry) return;
  undoStack.push(entry);
  applyUndoRedoEntry(entry, false);
  updateUndoRedoButtons();
}

function applyUndoRedoEntry(entry, useBefore) {
  if (workbook.activeSheetIndex !== entry.sheetIndex) {
    workbook.activeSheetIndex = entry.sheetIndex;
  }
  if (entry.type === 'cells') {
    const sheet = workbook.sheets[entry.sheetIndex];
    for (const c of entry.changes) sheet.restoreCell(c.key, useBefore ? c.before : c.after);
    renderSheetTabs();
    renderGrid();
  } else if (entry.type === 'structural') {
    workbook.sheets[entry.sheetIndex] = Sheet.fromJSON(useBefore ? entry.before : entry.after);
    clampSelectionToSheet();
    renderSheetTabs();
    renderGrid();
  }
  setDirty(true);
}

// ---------------------------------------------------------------------------
// Clipboard
// ---------------------------------------------------------------------------

function copySelection() {
  const sheet = workbook.activeSheet;
  const range = normalizedSelection();
  const rows = [];
  for (let r = range.minRow; r <= range.maxRow; r++) {
    const row = [];
    for (let c = range.minCol; c <= range.maxCol; c++) {
      const snap = sheet.snapshotCell(cellKeyFromRC(c, r));
      row.push(snap);
    }
    rows.push(row);
  }
  clipboard = {
    rows,
    width: range.maxCol - range.minCol + 1,
    height: range.maxRow - range.minRow + 1,
    sourceRange: range,
    cut: false,
  };
}

function cutSelection() {
  copySelection();
  clipboard.cut = true;
}

function pasteAtActive() {
  if (!clipboard) return;
  const sheet = workbook.activeSheet;
  const dest = state.anchor;
  const changes = [];
  for (let dr = 0; dr < clipboard.height; dr++) {
    for (let dc = 0; dc < clipboard.width; dc++) {
      const destCol = dest.col + dc;
      const destRow = dest.row + dr;
      if (destCol >= sheet.colCount || destRow >= sheet.rowCount) continue;
      const key = cellKeyFromRC(destCol, destRow);
      const before = sheet.snapshotCell(key);
      const src = clipboard.rows[dr][dc];
      let raw = src.raw;
      if (typeof raw === 'string' && raw.startsWith('=')) {
        const colOffset = destCol - (clipboard.sourceRange.minCol + dc);
        const rowOffset = destRow - (clipboard.sourceRange.minRow + dr);
        raw = shiftFormulaRefsByOffset(raw, colOffset, rowOffset);
      }
      changes.push({ key, before, after: { raw, format: { ...src.format } } });
    }
  }
  if (clipboard.cut) {
    for (let r = clipboard.sourceRange.minRow; r <= clipboard.sourceRange.maxRow; r++) {
      for (let c = clipboard.sourceRange.minCol; c <= clipboard.sourceRange.maxCol; c++) {
        const key = cellKeyFromRC(c, r);
        // Skip if this source cell is also a destination cell in this same paste.
        const isDest =
          r >= dest.row && r < dest.row + clipboard.height && c >= dest.col && c < dest.col + clipboard.width;
        if (isDest) continue;
        const before = sheet.snapshotCell(key);
        changes.push({ key, before, after: { raw: '', format: before.format } });
      }
    }
    clipboard = null;
  }
  applyChanges(changes);
}

// ---------------------------------------------------------------------------
// Formatting actions
// ---------------------------------------------------------------------------

function applyFormatPatch(patchFn) {
  const sheet = workbook.activeSheet;
  const range = normalizedSelection();
  const changes = [];
  for (let r = range.minRow; r <= range.maxRow; r++) {
    for (let c = range.minCol; c <= range.maxCol; c++) {
      const key = cellKeyFromRC(c, r);
      const before = sheet.snapshotCell(key);
      const patch = patchFn(before.format, key);
      changes.push({ key, before, after: { raw: before.raw, format: { ...before.format, ...patch } } });
    }
  }
  applyChanges(changes);
}

function anchorFormat() {
  const sheet = workbook.activeSheet;
  const cell = sheet.getCell(activeKey());
  return cell ? cell.format : defaultFormat();
}

function toggleBold() {
  const target = !anchorFormat().bold;
  applyFormatPatch(() => ({ bold: target }));
}
function toggleItalic() {
  const target = !anchorFormat().italic;
  applyFormatPatch(() => ({ italic: target }));
}
function toggleUnderline() {
  const target = !anchorFormat().underline;
  applyFormatPatch(() => ({ underline: target }));
}
function setAlign(align) {
  applyFormatPatch(() => ({ align }));
}
function toggleBorders() {
  const target = !anchorFormat().border;
  applyFormatPatch(() => ({ border: target }));
}
function setNumberFormat(fmt) {
  applyFormatPatch(() => ({ numberFormat: fmt }));
  els.numberFormatSelect.value = fmt;
}
function clearFormatting() {
  applyFormatPatch(() => defaultFormat());
}

// ---------------------------------------------------------------------------
// Clear contents
// ---------------------------------------------------------------------------

function clearSelectionContents() {
  const sheet = workbook.activeSheet;
  const range = normalizedSelection();
  const changes = [];
  for (let r = range.minRow; r <= range.maxRow; r++) {
    for (let c = range.minCol; c <= range.maxCol; c++) {
      const key = cellKeyFromRC(c, r);
      const cell = sheet.getCell(key);
      if (!cell || cell.raw === '') continue;
      const before = sheet.snapshotCell(key);
      changes.push({ key, before, after: { raw: '', format: before.format } });
    }
  }
  applyChanges(changes);
}

// ---------------------------------------------------------------------------
// Status bar
// ---------------------------------------------------------------------------

function updateStatusBar() {
  const sheet = workbook.activeSheet;
  const range = normalizedSelection();
  let sum = 0;
  let numCount = 0;
  let count = 0;
  for (let r = range.minRow; r <= range.maxRow; r++) {
    for (let c = range.minCol; c <= range.maxCol; c++) {
      const cell = sheet.getCell(cellKeyFromRC(c, r));
      if (!cell || cell.raw === '') continue;
      count++;
      if (isNumericValue(cell.computed)) {
        sum += cell.computed;
        numCount++;
      }
    }
  }
  els.statSum.textContent = 'Sum: ' + (numCount ? formatValue(sum, 'general') : '—');
  els.statAvg.textContent = 'Average: ' + (numCount ? formatValue(sum / numCount, 'general') : '—');
  els.statCount.textContent = 'Count: ' + count;
}

// ---------------------------------------------------------------------------
// Find
// ---------------------------------------------------------------------------

function openFindDialog() {
  function jumpToFindMatch() {
    if (findIndex < 0 || findIndex >= findMatchesCache.length) return;
    const key = findMatchesCache[findIndex];
    const td = cellElements.get(key);
    if (!td) return;
    const parsed = { col: parseInt(td.dataset.col, 10), row: parseInt(td.dataset.row, 10) };
    state.anchor = parsed;
    state.focus = parsed;
    updateSelectionUI();
    scrollActiveIntoView();
  }

  let resultsEl = null;

  function runSearch(query) {
    clearFindHighlights();
    findMatchesCache = findMatches(query);
    findIndex = findMatchesCache.length ? 0 : -1;
    for (const key of findMatchesCache) {
      const td = cellElements.get(key);
      if (td) td.classList.add('is-find-match');
    }
    if (resultsEl) resultsEl.textContent = findMatchesCache.length ? `${findMatchesCache.length} match(es)` : 'No matches';
    if (findIndex >= 0) jumpToFindMatch();
  }

  function goNext() {
    if (!findMatchesCache.length) return;
    findIndex = (findIndex + 1) % findMatchesCache.length;
    jumpToFindMatch();
  }
  function goPrev() {
    if (!findMatchesCache.length) return;
    findIndex = (findIndex - 1 + findMatchesCache.length) % findMatchesCache.length;
    jumpToFindMatch();
  }

  const { bodyEl } = showDialog({
    title: 'Find',
    bodyHTML: `
      <label>Search text or values</label>
      <input type="text" id="find-input" autocomplete="off" />
      <div class="dialog__find-results" id="find-results">Type to search…</div>
    `,
    buttons: [
      { label: 'Previous', keepOpen: true, onClick: goPrev },
      { label: 'Next', variant: 'primary', keepOpen: true, onClick: goNext },
      { label: 'Close', onClick: clearFindHighlights },
    ],
    onClose: clearFindHighlights,
  });
  const input = bodyEl.querySelector('#find-input');
  resultsEl = bodyEl.querySelector('#find-results');

  input.addEventListener('input', () => runSearch(input.value));
  input.addEventListener('keydown', (e) => {
    e.stopPropagation();
    if (e.key === 'Enter') goNext();
  });
  input.focus();
}

function findMatches(query) {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const sheet = workbook.activeSheet;
  const matches = [];
  for (const [key, cell] of sheet.cells) {
    const raw = String(cell.raw || '').toLowerCase();
    const display = String(formatValue(cell.computed, cell.format.numberFormat)).toLowerCase();
    if (raw.includes(q) || display.includes(q)) matches.push(key);
  }
  return matches;
}

function clearFindHighlights() {
  for (const key of findMatchesCache) {
    const td = cellElements.get(key);
    if (td) td.classList.remove('is-find-match');
  }
  findMatchesCache = [];
  findIndex = -1;
}

// ---------------------------------------------------------------------------
// File I/O
// ---------------------------------------------------------------------------

function setDirty(v) {
  workbook.dirty = v;
  updateTitle();
}

function updateTitle() {
  document.title = `${workbook.title}${workbook.dirty ? ' •' : ''} — Lumen Sheet`;
  if (document.activeElement !== els.titleInput) els.titleInput.value = workbook.title;
}

function confirmUnsaved() {
  return new Promise((resolve) => {
    showDialog({
      title: 'Unsaved changes',
      bodyHTML: `<p>&ldquo;${escapeHtml(workbook.title)}&rdquo; has unsaved changes. Do you want to save them?</p>`,
      buttons: [
        { label: 'Cancel', onClick: () => resolve('cancel') },
        { label: "Don't Save", onClick: () => resolve('discard') },
        { label: 'Save', variant: 'primary', onClick: () => resolve('save') },
      ],
      onClose: () => resolve('cancel'),
    });
  });
}

async function newWorkbook() {
  if (workbook.dirty) {
    const choice = await confirmUnsaved();
    if (choice === 'cancel') return;
    if (choice === 'save') {
      const ok = await saveWorkbook();
      if (!ok) return;
    }
  }
  workbook = new Workbook();
  currentFilePath = null;
  resetUndoRedo();
  renderSheetTabs();
  switchSheet(0, true);
  setDirty(false);
}

async function openWorkbookFlow() {
  if (workbook.dirty) {
    const choice = await confirmUnsaved();
    if (choice === 'cancel') return;
    if (choice === 'save') {
      const ok = await saveWorkbook();
      if (!ok) return;
    }
  }
  const filePath = await window.lumen.dialogs.openLsheet();
  if (!filePath) return;
  const res = await window.lumen.file.readLsheet(filePath);
  if (!res.ok) {
    showDialog({ title: 'Open failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
    return;
  }
  workbook = Workbook.fromJSON(res.data);
  currentFilePath = filePath;
  resetUndoRedo();
  clampSelectionToSheet();
  renderSheetTabs();
  switchSheet(workbook.activeSheetIndex, true);
  setDirty(false);
}

async function saveWorkbook() {
  if (!currentFilePath) return saveWorkbookAs();
  const data = workbook.toJSON();
  const res = await window.lumen.file.writeLsheet(currentFilePath, data);
  if (res.ok) {
    setDirty(false);
    return true;
  }
  showDialog({ title: 'Save failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
  return false;
}

async function saveWorkbookAs() {
  const defaultName = (workbook.title || 'Untitled spreadsheet') + '.lsheet';
  const filePath = await window.lumen.dialogs.saveLsheet(defaultName);
  if (!filePath) return false;
  currentFilePath = filePath;
  const base = filePath
    .split(/[\\/]/)
    .pop()
    .replace(/\.lsheet$/i, '');
  workbook.title = base;
  const data = workbook.toJSON();
  const res = await window.lumen.file.writeLsheet(filePath, data);
  if (res.ok) {
    setDirty(false);
    return true;
  }
  showDialog({ title: 'Save failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
  return false;
}

async function importCsv() {
  const filePath = await window.lumen.dialogs.openImport('csv');
  if (!filePath) return;
  const res = await window.lumen.file.importCsv(filePath);
  if (!res.ok) {
    showDialog({ title: 'Import failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
    return;
  }
  const sheet = new Sheet('Imported');
  sheet.rowCount = res.rowCount;
  sheet.colCount = res.colCount;
  for (const ref of Object.keys(res.cells)) {
    if (res.cells[ref] !== '') sheet.setRaw(ref, res.cells[ref]);
  }
  workbook = new Workbook();
  workbook.sheets = [sheet];
  workbook.activeSheetIndex = 0;
  workbook.title = filePath
    .split(/[\\/]/)
    .pop()
    .replace(/\.csv$/i, '');
  currentFilePath = null;
  resetUndoRedo();
  renderSheetTabs();
  switchSheet(0, true);
  setDirty(true);
}

async function importXlsx() {
  const filePath = await window.lumen.dialogs.openImport('xlsx');
  if (!filePath) return;
  const res = await window.lumen.file.importXlsx(filePath);
  if (!res.ok) {
    showDialog({ title: 'Import failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
    return;
  }
  const wb = new Workbook();
  wb.sheets = res.sheets.map((s) => {
    const sheet = new Sheet(s.name);
    sheet.rowCount = s.rowCount;
    sheet.colCount = s.colCount;
    for (const ref of Object.keys(s.cells)) {
      if (s.cells[ref] !== '') sheet.setRaw(ref, s.cells[ref]);
    }
    return sheet;
  });
  wb.activeSheetIndex = 0;
  wb.title = filePath
    .split(/[\\/]/)
    .pop()
    .replace(/\.xlsx$/i, '');
  workbook = wb;
  currentFilePath = null;
  resetUndoRedo();
  renderSheetTabs();
  switchSheet(0, true);
  setDirty(true);
}

async function exportCsv() {
  const sheet = workbook.activeSheet;
  const defaultName = (workbook.title || 'sheet') + '.csv';
  const filePath = await window.lumen.dialogs.saveExport('csv', defaultName);
  if (!filePath) return;
  const cells = {};
  for (const [key, cell] of sheet.cells) {
    if (cell.raw === '') continue;
    cells[key] = { raw: cell.raw, display: formatValue(cell.computed, cell.format.numberFormat) };
  }
  const res = await window.lumen.file.exportCsv(filePath, { cells, rowCount: sheet.rowCount, colCount: sheet.colCount });
  if (!res.ok) showDialog({ title: 'Export failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
}

async function exportXlsx() {
  const defaultName = (workbook.title || 'workbook') + '.xlsx';
  const filePath = await window.lumen.dialogs.saveExport('xlsx', defaultName);
  if (!filePath) return;
  const sheetsData = workbook.sheets.map((sheet) => {
    const cells = {};
    for (const [key, cell] of sheet.cells) {
      if (cell.raw === '') continue;
      cells[key] = { raw: cell.raw, computed: isError(cell.computed) ? cell.computed.error : cell.computed };
    }
    return { name: sheet.name, cells, rowCount: sheet.rowCount, colCount: sheet.colCount };
  });
  const res = await window.lumen.file.exportXlsx(filePath, { sheets: sheetsData });
  if (!res.ok) showDialog({ title: 'Export failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
}

async function handleBeforeClose() {
  if (workbook.dirty) {
    const choice = await confirmUnsaved();
    if (choice === 'cancel') return;
    if (choice === 'save') {
      const ok = await saveWorkbook();
      if (!ok) return;
    }
  }
  window.lumen.confirmClose();
}

// ---------------------------------------------------------------------------
// Keyboard shortcuts
// ---------------------------------------------------------------------------

const ARROW_DIRS = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right' };

document.addEventListener('keydown', (e) => {
  if (state.editing) return; // editor input owns its own keys
  const ae = document.activeElement;
  if (ae && (ae.tagName === 'INPUT' || ae.tagName === 'TEXTAREA')) return;

  const mod = e.ctrlKey || e.metaKey;

  if (mod && !e.shiftKey && e.key.toLowerCase() === 'b') {
    e.preventDefault();
    toggleBold();
    return;
  }
  if (mod && !e.shiftKey && e.key.toLowerCase() === 'i') {
    e.preventDefault();
    toggleItalic();
    return;
  }
  if (mod && !e.shiftKey && e.key.toLowerCase() === 'u') {
    e.preventDefault();
    toggleUnderline();
    return;
  }
  if (mod && !e.shiftKey && e.key.toLowerCase() === 'n') {
    e.preventDefault();
    newWorkbook();
    return;
  }
  if (mod && !e.shiftKey && e.key.toLowerCase() === 'o') {
    e.preventDefault();
    openWorkbookFlow();
    return;
  }
  if (mod && e.shiftKey && e.key.toLowerCase() === 's') {
    e.preventDefault();
    saveWorkbookAs();
    return;
  }
  if (mod && !e.shiftKey && e.key.toLowerCase() === 's') {
    e.preventDefault();
    saveWorkbook();
    return;
  }
  if (mod && !e.shiftKey && e.key.toLowerCase() === 'z') {
    e.preventDefault();
    undo();
    return;
  }
  if ((mod && e.shiftKey && e.key.toLowerCase() === 'z') || (mod && e.key.toLowerCase() === 'y')) {
    e.preventDefault();
    redo();
    return;
  }
  if (mod && !e.shiftKey && e.key.toLowerCase() === 'c') {
    e.preventDefault();
    copySelection();
    return;
  }
  if (mod && !e.shiftKey && e.key.toLowerCase() === 'x') {
    e.preventDefault();
    cutSelection();
    return;
  }
  if (mod && !e.shiftKey && e.key.toLowerCase() === 'v') {
    e.preventDefault();
    pasteAtActive();
    return;
  }
  if (mod && !e.shiftKey && e.key.toLowerCase() === 'f') {
    e.preventDefault();
    openFindDialog();
    return;
  }
  if (!mod && (e.key === 'Delete' || e.key === 'Backspace')) {
    e.preventDefault();
    clearSelectionContents();
    return;
  }
  if (!mod && ARROW_DIRS[e.key]) {
    e.preventDefault();
    moveActive(ARROW_DIRS[e.key], e.shiftKey);
    return;
  }
  if (!mod && e.key === 'F2') {
    e.preventDefault();
    startEditing(activeKey(), 'existing');
    return;
  }
  if (!mod && e.key === 'Enter') {
    e.preventDefault();
    moveActive(e.shiftKey ? 'up' : 'down', false);
    return;
  }
  if (!mod && e.key === 'Tab') {
    e.preventDefault();
    moveActive(e.shiftKey ? 'left' : 'right', false);
    return;
  }
  if (!mod && e.key.length === 1 && /^[ -~]$/.test(e.key)) {
    e.preventDefault();
    startEditing(activeKey(), 'char', e.key);
    return;
  }
});
