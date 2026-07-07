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
  literalValue,
  DEFAULT_COL_WIDTH,
  DEFAULT_ROW_HEIGHT,
} from './grid.js';
import {
  colToLetter,
  letterToCol,
  cellKeyFromRC,
  parseCellRefStr,
  parseRangeStr,
  iterRangeKeys,
  shiftRefString,
} from './refUtils.js';
import { isError } from './formulaEngine.js';
import { showToast } from './toast.js';

const ROW_HEADER_WIDTH = 46;
const HEADER_HEIGHT = 26; // matches `.lumen-grid thead th { height: 26px }` in app.css

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
  { name: 'VLOOKUP', args: '(value, range, colIndex, [rangeLookup])', desc: 'Looks up a value in the first column of a range.' },
  { name: 'INDEX', args: '(range, row, [col])', desc: 'Returns the value at a row/column within a range.' },
  { name: 'MATCH', args: '(value, range, [matchType])', desc: 'Position of a value within a range.' },
  { name: 'SUMIF', args: '(range, criteria, [sumRange])', desc: 'Sums cells that meet a criteria.' },
  { name: 'COUNTIF', args: '(range, criteria)', desc: 'Counts cells that meet a criteria.' },
  { name: 'AVERAGEIF', args: '(range, criteria, [avgRange])', desc: 'Averages cells that meet a criteria.' },
  { name: 'DATE', args: '(year, month, day)', desc: 'Builds a date serial from parts.' },
  { name: 'YEAR', args: '(serial)', desc: 'Year of a date serial.' },
  { name: 'MONTH', args: '(serial)', desc: 'Month of a date serial.' },
  { name: 'DAY', args: '(serial)', desc: 'Day of a date serial.' },
  { name: 'WEEKDAY', args: '(serial, [type])', desc: 'Day of week of a date serial.' },
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

let recentCache = []; // [{ path, openedAt }], refreshed on boot and after every open/save
let fillHandleState = null; // active fill-handle drag, see "Fill handle" section
let openSubmenuEls = []; // tracked so closeAllMenus() can tear them down too

// Declared here (rather than next to their feature sections further down)
// because boot runs at the top of this module and calls functions that
// reference them immediately — `let` bindings are in the temporal dead zone
// until their declaration line executes, so these must come first.
let startScreenEl = null; // Start screen (template gallery + recent files)
let fillHandleEl = null; // Fill handle
let lastFillPreviewCells = [];
let chartElements = new Map(); // chart id -> { el, titleEl, bodyEl } (Charts)
let validationChevronEl = null; // Data validation dropdown affordance
let validationChevronKey = null;
let validationChevronRule = null;

const TEMPLATES = {
  blank: { label: 'Blank', build: () => buildBlankTemplate() },
  budget: { label: 'Budget Tracker', build: () => buildBudgetTemplate() },
  invoice: { label: 'Simple Invoice', build: () => buildInvoiceTemplate() },
  todo: { label: 'To-Do List', build: () => buildTodoTemplate() },
};

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
refreshRecentCache().then(() => {
  if (startScreenEl) renderStartScreenRecent();
});
showStartScreen();

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
      <div class="menubar__item" data-menu="data">Data</div>
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

    <div class="sheet-area" id="sheet-area">
      <div class="formula-bar">
        <span class="formula-bar__fx">fx</span>
        <span class="formula-bar__ref" id="formula-ref">A1</span>
        <input class="formula-bar__input" id="formula-input" spellcheck="false" autocomplete="off" />
      </div>

      <div class="sheet-viewport" id="sheet-viewport">
        <div class="sheet-canvas" id="sheet-canvas"></div>
      </div>

      <div class="sheet-tabs" id="sheet-tabs"></div>
    </div>

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
  els.sheetArea = document.getElementById('sheet-area');
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
  for (const el of openSubmenuEls) el.remove();
  openSubmenuEls = [];
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
    if (item.submenu) {
      el.classList.add('menu__item--submenu');
      const chevron = document.createElement('span');
      chevron.className = 'menu__chevron';
      chevron.textContent = '▸';
      el.appendChild(chevron);
      let submenuEl = null;
      let closeTimer = null;
      const openSub = () => {
        clearTimeout(closeTimer);
        if (submenuEl) return;
        submenuEl = buildMenuEl(item.submenu);
        submenuEl.classList.add('menu--submenu');
        document.body.appendChild(submenuEl);
        openSubmenuEls.push(submenuEl);
        const rect = el.getBoundingClientRect();
        submenuEl.style.left = rect.right + 'px';
        submenuEl.style.top = rect.top + 'px';
        const subRect = submenuEl.getBoundingClientRect();
        if (subRect.right > window.innerWidth) {
          submenuEl.style.left = Math.max(4, rect.left - subRect.width) + 'px';
        }
        submenuEl.addEventListener('mouseenter', () => clearTimeout(closeTimer));
        submenuEl.addEventListener('mouseleave', scheduleClose);
      };
      const scheduleClose = () => {
        closeTimer = setTimeout(() => {
          if (submenuEl) {
            submenuEl.remove();
            openSubmenuEls = openSubmenuEls.filter((s) => s !== submenuEl);
            submenuEl = null;
          }
        }, 250);
      };
      el.addEventListener('mouseenter', openSub);
      el.addEventListener('mouseleave', scheduleClose);
      el.addEventListener('click', (e) => {
        e.stopPropagation();
        openSub();
      });
    } else if (!item.disabled) {
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
  const inSubmenu = openSubmenuEls.some((s) => s.contains(e.target));
  if (openMenuEl && !openMenuEl.contains(e.target) && !(openMenuAnchor && openMenuAnchor.contains(e.target)) && !inSubmenu) {
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
        { label: 'Open Recent', submenu: recentOpenRecentSubmenu() },
        { label: 'Save', shortcut: 'Ctrl+S', onClick: saveWorkbook },
        { label: 'Save As…', shortcut: 'Ctrl+Shift+S', onClick: saveWorkbookAs },
        { type: 'sep' },
        { label: 'Import CSV…', onClick: importCsv },
        { label: 'Import Excel (.xlsx)…', onClick: importXlsx },
        { type: 'sep' },
        { label: 'Export CSV…', onClick: exportCsv },
        { label: 'Export Excel (.xlsx)…', onClick: exportXlsx },
        { label: 'Export PDF…', onClick: exportPdf },
        { type: 'sep' },
        { label: 'Page Setup…', onClick: openPageSetupDialog },
        { label: 'Print…', shortcut: 'Ctrl+P', onClick: printSheet },
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
        { label: 'Freeze Panes', onClick: freezePanesAtActive },
        { label: 'Freeze Top Row', onClick: freezeTopRow },
        { label: 'Freeze First Column', onClick: freezeFirstColumn },
        {
          label: 'Unfreeze Panes',
          disabled: !workbook.activeSheet.freezeRow && !workbook.activeSheet.freezeCol,
          onClick: unfreezePanes,
        },
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
        { label: 'Chart…', onClick: openInsertChartDialog },
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
        { type: 'sep' },
        { label: 'Merge Cells', onClick: mergeSelection },
        { label: 'Unmerge Cells', onClick: unmergeSelection },
        { type: 'sep' },
        { label: 'Conditional Formatting…', onClick: openConditionalFormattingDialog },
      ];
    case 'data':
      return [
        { label: 'Sort Selection Ascending', onClick: () => sortSelection('asc') },
        { label: 'Sort Selection Descending', onClick: () => sortSelection('desc') },
        { type: 'sep' },
        { label: 'Data Validation…', onClick: openDataValidationDialog },
      ];
    case 'help':
      return [
        { label: 'Keyboard Shortcuts', onClick: showKeyboardShortcuts },
        { label: 'About Lumen Sheet', onClick: showAbout },
      ];
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
  updateFillHandlePosition();
  updateValidationChevronPosition();
}

// ---------------------------------------------------------------------------
// Start screen (template gallery + recent files)
// ---------------------------------------------------------------------------
// (startScreenEl is declared with the other early state, near the top of
// this file — see the comment there for why.)

function tplSetCell(sheet, ref, raw, formatPatch) {
  sheet.setRaw(ref, raw);
  if (formatPatch) sheet.setFormat(ref, formatPatch);
}

const TPL_HEADER_FORMAT = { bold: true, bg: '#f3e9d6' };

function buildBlankTemplate() {
  return new Workbook();
}

function buildBudgetTemplate() {
  const wb = new Workbook();
  wb.title = 'Budget Tracker';
  const sheet = wb.activeSheet;
  sheet.name = 'Budget';
  ['A1', 'B1', 'C1', 'D1'].forEach((ref, i) => {
    tplSetCell(sheet, ref, ['Category', 'Budgeted', 'Actual', 'Difference'][i], TPL_HEADER_FORMAT);
  });
  const rows = [
    ['Rent', 1200, 1200],
    ['Groceries', 400, 375],
    ['Utilities', 150, 162],
    ['Entertainment', 100, 130],
  ];
  rows.forEach((row, i) => {
    const r = i + 2;
    tplSetCell(sheet, `A${r}`, row[0]);
    tplSetCell(sheet, `B${r}`, String(row[1]), { numberFormat: 'currency' });
    tplSetCell(sheet, `C${r}`, String(row[2]), { numberFormat: 'currency' });
    tplSetCell(sheet, `D${r}`, `=B${r}-C${r}`, { numberFormat: 'currency' });
  });
  const total = rows.length + 2;
  tplSetCell(sheet, `A${total}`, 'Total', { bold: true });
  tplSetCell(sheet, `B${total}`, `=SUM(B2:B${total - 1})`, { bold: true, numberFormat: 'currency' });
  tplSetCell(sheet, `C${total}`, `=SUM(C2:C${total - 1})`, { bold: true, numberFormat: 'currency' });
  tplSetCell(sheet, `D${total}`, `=SUM(D2:D${total - 1})`, { bold: true, numberFormat: 'currency' });
  return wb;
}

function buildInvoiceTemplate() {
  const wb = new Workbook();
  wb.title = 'Simple Invoice';
  const sheet = wb.activeSheet;
  sheet.name = 'Invoice';
  tplSetCell(sheet, 'A1', 'Invoice #', { bold: true });
  tplSetCell(sheet, 'B1', 'INV-1001');
  tplSetCell(sheet, 'A2', 'Date', { bold: true });
  tplSetCell(sheet, 'B2', '=TODAY()', { numberFormat: 'date' });
  tplSetCell(sheet, 'A3', 'Bill To', { bold: true });
  tplSetCell(sheet, 'B3', 'Acme Co.');
  ['A5', 'B5', 'C5', 'D5'].forEach((ref, i) => {
    tplSetCell(sheet, ref, ['Description', 'Qty', 'Unit Price', 'Total'][i], TPL_HEADER_FORMAT);
  });
  const items = [
    ['Consulting hours', 6, 120],
    ['Design review', 2, 150],
  ];
  items.forEach((item, i) => {
    const r = i + 6;
    tplSetCell(sheet, `A${r}`, item[0]);
    tplSetCell(sheet, `B${r}`, String(item[1]));
    tplSetCell(sheet, `C${r}`, String(item[2]), { numberFormat: 'currency' });
    tplSetCell(sheet, `D${r}`, `=B${r}*C${r}`, { numberFormat: 'currency' });
  });
  const total = items.length + 6;
  tplSetCell(sheet, `C${total}`, 'Grand Total', { bold: true });
  tplSetCell(sheet, `D${total}`, `=SUM(D6:D${total - 1})`, { bold: true, numberFormat: 'currency' });
  return wb;
}

function buildTodoTemplate() {
  const wb = new Workbook();
  wb.title = 'To-Do List';
  const sheet = wb.activeSheet;
  sheet.name = 'To-Do';
  ['A1', 'B1', 'C1'].forEach((ref, i) => {
    tplSetCell(sheet, ref, ['Task', 'Done', 'Priority'][i], TPL_HEADER_FORMAT);
  });
  const rows = [
    ['Draft project outline', 'TRUE', 'High'],
    ['Review budget numbers', 'FALSE', 'Medium'],
    ['Schedule kickoff meeting', 'FALSE', 'High'],
    ['Clean up shared drive', 'FALSE', 'Low'],
  ];
  rows.forEach((row, i) => {
    const r = i + 2;
    tplSetCell(sheet, `A${r}`, row[0]);
    tplSetCell(sheet, `B${r}`, row[1], { align: 'center' });
    tplSetCell(sheet, `C${r}`, row[2]);
  });
  return wb;
}

// (TEMPLATES is declared with the other early state, near the top of this
// file, since showStartScreen() reads it during boot — function
// declarations like buildBlankTemplate are hoisted, so referencing them
// here from an earlier line is safe.)

// Small abstract grid previews (a few colored rectangles suggesting a header
// row + data) — not literal screenshots. Inline SVG, theme-token colored.
function previewSVG(kind) {
  const bodies = {
    blank: `
      <rect x="4" y="4" width="112" height="62" rx="3" fill="var(--surface-0)" stroke="var(--border-subtle)"/>
      <line x1="4" y1="26" x2="116" y2="26" stroke="var(--border-subtle)"/>
      <line x1="4" y1="48" x2="116" y2="48" stroke="var(--border-subtle)"/>
      <line x1="42" y1="4" x2="42" y2="66" stroke="var(--border-subtle)"/>
      <line x1="80" y1="4" x2="80" y2="66" stroke="var(--border-subtle)"/>`,
    budget: `
      <rect x="4" y="4" width="112" height="62" rx="3" fill="var(--surface-0)" stroke="var(--border-subtle)"/>
      <rect x="4" y="4" width="112" height="12" fill="var(--accent-600)"/>
      <rect x="8" y="22" width="40" height="7" fill="var(--ink-200)"/>
      <rect x="56" y="22" width="22" height="7" fill="var(--accent-500)" opacity="0.55"/>
      <rect x="84" y="22" width="22" height="7" fill="var(--accent-500)" opacity="0.3"/>
      <rect x="8" y="34" width="40" height="7" fill="var(--ink-200)"/>
      <rect x="56" y="34" width="22" height="7" fill="var(--accent-500)" opacity="0.55"/>
      <rect x="84" y="34" width="22" height="7" fill="var(--accent-500)" opacity="0.3"/>
      <rect x="8" y="46" width="40" height="7" fill="var(--ink-200)"/>
      <rect x="56" y="46" width="22" height="7" fill="var(--accent-500)" opacity="0.55"/>
      <rect x="84" y="46" width="22" height="7" fill="var(--accent-500)" opacity="0.3"/>
      <rect x="8" y="58" width="98" height="6" fill="var(--ink-400)" opacity="0.6"/>`,
    invoice: `
      <rect x="4" y="4" width="112" height="62" rx="3" fill="var(--surface-0)" stroke="var(--border-subtle)"/>
      <rect x="8" y="8" width="34" height="6" fill="var(--ink-200)"/>
      <rect x="8" y="18" width="50" height="6" fill="var(--ink-200)"/>
      <rect x="4" y="30" width="112" height="10" fill="var(--accent-600)"/>
      <rect x="8" y="44" width="46" height="6" fill="var(--ink-200)"/>
      <rect x="90" y="44" width="18" height="6" fill="var(--accent-500)" opacity="0.5"/>
      <rect x="8" y="54" width="46" height="6" fill="var(--ink-200)"/>
      <rect x="90" y="54" width="18" height="6" fill="var(--accent-500)" opacity="0.5"/>
      <rect x="70" y="62" width="38" height="4" fill="var(--ink-400)" opacity="0.7"/>`,
    todo: `
      <rect x="4" y="4" width="112" height="62" rx="3" fill="var(--surface-0)" stroke="var(--border-subtle)"/>
      <rect x="4" y="4" width="112" height="12" fill="var(--accent-600)"/>
      <rect x="8" y="22" width="10" height="10" rx="2" fill="none" stroke="var(--ink-400)"/>
      <rect x="24" y="25" width="60" height="6" fill="var(--ink-200)"/>
      <rect x="8" y="38" width="10" height="10" rx="2" fill="var(--accent-500)" opacity="0.6"/>
      <rect x="24" y="41" width="60" height="6" fill="var(--ink-200)"/>
      <rect x="8" y="54" width="10" height="10" rx="2" fill="none" stroke="var(--ink-400)"/>
      <rect x="24" y="57" width="44" height="6" fill="var(--ink-200)"/>`,
  };
  return `<svg viewBox="0 0 120 70" width="100%" height="64" xmlns="http://www.w3.org/2000/svg">${bodies[kind] || bodies.blank}</svg>`;
}

function showStartScreen() {
  if (!startScreenEl) {
    startScreenEl = document.createElement('div');
    startScreenEl.className = 'start-screen';
    startScreenEl.innerHTML = `
      <div class="start-screen__title">Lumen Sheet</div>
      <div class="start-screen__subtitle">Choose a template to get started, or open a recent workbook.</div>
      <div class="start-screen__section-label">Templates</div>
      <div class="start-screen__grid" id="start-template-grid"></div>
      <div class="start-screen__section-label">Recent</div>
      <div class="start-screen__recent" id="start-recent-list"></div>
    `;
    els.sheetArea.appendChild(startScreenEl);
    const grid = startScreenEl.querySelector('#start-template-grid');
    for (const key of Object.keys(TEMPLATES)) {
      const tpl = TEMPLATES[key];
      const card = document.createElement('div');
      card.className = 'start-card';
      card.innerHTML = `
        <div class="start-card__preview">${previewSVG(key)}</div>
        <div class="start-card__label">${escapeHtml(tpl.label)}</div>
      `;
      card.addEventListener('click', () => applyTemplate(key));
      grid.appendChild(card);
    }
  }
  startScreenEl.classList.remove('is-hidden');
  renderStartScreenRecent();
}

function hideStartScreen() {
  if (startScreenEl) startScreenEl.classList.add('is-hidden');
}

function renderStartScreenRecent() {
  if (!startScreenEl) return;
  const list = startScreenEl.querySelector('#start-recent-list');
  list.innerHTML = '';
  if (!recentCache.length) {
    const empty = document.createElement('div');
    empty.className = 'start-recent-item__meta';
    empty.style.padding = '6px 12px';
    empty.textContent = 'No recent files yet.';
    list.appendChild(empty);
    return;
  }
  for (const entry of recentCache) {
    const item = document.createElement('div');
    item.className = 'start-recent-item';
    const icon = document.createElement('span');
    icon.className = 'start-recent-item__icon';
    icon.innerHTML = window.lumen.icons['folder-open'] || '';
    const textWrap = document.createElement('div');
    const name = document.createElement('div');
    name.className = 'start-recent-item__name';
    name.textContent = recentLabel(entry.path);
    const meta = document.createElement('div');
    meta.className = 'start-recent-item__meta';
    meta.textContent = new Date(entry.openedAt).toLocaleString();
    textWrap.appendChild(name);
    textWrap.appendChild(meta);
    item.appendChild(icon);
    item.appendChild(textWrap);
    item.addEventListener('click', () => openFileAtPath(entry.path));
    list.appendChild(item);
  }
}

function applyTemplate(key) {
  const tpl = TEMPLATES[key];
  if (!tpl) return;
  workbook = tpl.build();
  currentFilePath = null;
  resetUndoRedo();
  renderSheetTabs();
  switchSheet(0, true);
  setDirty(false);
  hideStartScreen();
  showToast(key === 'blank' ? 'New blank workbook' : `Created from "${tpl.label}" template`);
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
  // Keep the sliding active-tab indicator node across rebuilds — only the
  // tab pills and the add button are torn down and recreated, so the
  // indicator's left/width transition has a previous value to animate from
  // instead of jumping in as a freshly created element would.
  ensureSheetTabIndicator();
  els.sheetTabs.querySelectorAll('.sheet-tab, .sheet-tabs__add').forEach((el) => el.remove());
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
  updateSheetTabIndicator();
}

function ensureSheetTabIndicator() {
  if (els.sheetTabIndicator && els.sheetTabIndicator.isConnected) return;
  els.sheetTabIndicator = document.createElement('div');
  els.sheetTabIndicator.className = 'sheet-tab-indicator';
  els.sheetTabs.appendChild(els.sheetTabIndicator);
}

function updateSheetTabIndicator() {
  const activeTab = els.sheetTabs.querySelector('.sheet-tab.is-active');
  if (!activeTab) return;
  els.sheetTabIndicator.style.width = activeTab.offsetWidth + 'px';
  els.sheetTabIndicator.style.transform = `translateX(${activeTab.offsetLeft}px)`;
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

// Cumulative pixel offsets used to position the sticky frozen band (see
// "Freeze panes" below) — colLefts[c] / rowTops[r] are the left/top a cell in
// column c / row r would need for `position: sticky` to pin it exactly where
// it already sits in normal flow. Column widths and row heights aren't
// uniform, so these can't be computed from a single constant the way the
// row-header gutter / column-header height can.
function computeStickyOffsets(sheet) {
  const colLefts = [];
  let left = ROW_HEADER_WIDTH;
  for (let c = 0; c < sheet.colCount; c++) {
    colLefts.push(left);
    left += sheet.colWidths[c] || DEFAULT_COL_WIDTH;
  }
  const rowTops = [];
  let top = HEADER_HEIGHT;
  for (let r = 0; r < sheet.rowCount; r++) {
    rowTops.push(top);
    top += sheet.rowHeights[r] || DEFAULT_ROW_HEIGHT;
  }
  return { colLefts, rowTops };
}

function renderGrid() {
  const sheet = workbook.activeSheet;
  cellElements = new Map();
  colElements = [];
  const { colLefts, rowTops } = computeStickyOffsets(sheet);

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
    if (c < sheet.freezeCol) {
      th.classList.add('is-frozen-col');
      th.style.left = colLefts[c] + 'px';
      if (c === sheet.freezeCol - 1) th.classList.add('is-frozen-col-edge');
    }
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
    if (r < sheet.freezeRow) {
      rh.classList.add('is-frozen-row');
      rh.style.top = rowTops[r] + 'px';
      if (r === sheet.freezeRow - 1) rh.classList.add('is-frozen-row-edge');
    }
    tr.appendChild(rh);
    for (let c = 0; c < sheet.colCount; c++) {
      // A merged region renders as a single <td colspan rowspan> at its
      // top-left anchor; every other cell inside it is skipped entirely —
      // native table layout then makes that one <td> cover the whole visual
      // area, so clicks/drags anywhere in it naturally hit the same element
      // (see "Cell merge" in the README for why this is enough to satisfy
      // "click anywhere in the region selects it as one cell").
      const merge = sheet.getMergeContainingCell(c, r);
      if (merge && (merge.startCol !== c || merge.startRow !== r)) continue;

      const key = cellKeyFromRC(c, r);
      const td = document.createElement('td');
      td.className = 'cell';
      td.dataset.col = String(c);
      td.dataset.row = String(r);
      td.dataset.key = key;
      if (merge) {
        td.colSpan = merge.endCol - merge.startCol + 1;
        td.rowSpan = merge.endRow - merge.startRow + 1;
        td.classList.add('is-merged');
      }
      if (r < sheet.freezeRow) {
        td.classList.add('is-frozen-row');
        td.style.top = rowTops[r] + 'px';
        if (r === sheet.freezeRow - 1) td.classList.add('is-frozen-row-edge');
      }
      if (c < sheet.freezeCol) {
        td.classList.add('is-frozen-col');
        td.style.left = colLefts[c] + 'px';
        if (c === sheet.freezeCol - 1) td.classList.add('is-frozen-col-edge');
      }
      tr.appendChild(td);
      cellElements.set(key, td);
    }
    tbody.appendChild(tr);
  }
  table.appendChild(tbody);

  els.sheetCanvas.innerHTML = '';
  els.sheetCanvas.appendChild(table);
  ensureFillHandleEl();
  ensureValidationChevronEl();
  renderAllCharts();

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

// If (col,row) is the hidden (non-anchor) interior of a merged region, snap
// to that region's top-left anchor — same rule arrow navigation follows.
function snapToMergeAnchor(sheet, col, row) {
  const merge = sheet.getMergeContainingCell(col, row);
  return merge ? { col: merge.startCol, row: merge.startRow } : { col, row };
}

function selectWholeColumn(col) {
  const sheet = workbook.activeSheet;
  state.anchor = snapToMergeAnchor(sheet, col, 0);
  state.focus = { col, row: sheet.rowCount - 1 };
  updateSelectionUI();
}

function selectWholeRow(row) {
  const sheet = workbook.activeSheet;
  state.anchor = snapToMergeAnchor(sheet, 0, row);
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
    applyCellDisplay(td, sheet.getCell(key), key);
  }
  updateStatusBar();
  refreshAllCharts();
}

function applyCellDisplay(td, cell, key) {
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
  // Conditional-formatting rules are a computed overlay: they never override
  // the user's own manual fill color, only supply one when none is set.
  const cfBg = key ? getCondFormatBackground(workbook.activeSheet, key) : null;
  const bg = fmt.bg || cfBg || '';
  td.style.background = bg;
  if (!fmt.color && bg) {
    // Any literal fill — a manual cell fill (including template header
    // shading) or a CF overlay — is theme-independent, so pick readable ink
    // for it by luminance rather than always using the theme's --ink-900
    // (see relativeLuminance above), which is light in dark mode and goes
    // nearly invisible on a light literal fill.
    td.style.color = relativeLuminance(bg) > 0.5 ? '#1b1a17' : '#f3f1eb';
  } else {
    td.style.color = fmt.color || '';
  }
}

function refreshCell(key) {
  const td = cellElements.get(key);
  if (!td) return;
  applyCellDisplay(td, workbook.activeSheet.getCell(key), key);
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
  updateFillHandlePosition();
  updateValidationChevronPosition();
}

function scrollActiveIntoView() {
  const td = cellElements.get(activeKey());
  if (td) td.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}

function moveActive(direction, extend) {
  const sheet = workbook.activeSheet;
  const base = extend ? state.focus : state.anchor;
  let { col, row } = base;
  // If we're moving off a merged region's anchor, jump to its far edge first
  // so the plain +/-1 step below clears the whole region in one press,
  // skipping over its hidden interior cells rather than landing inside them.
  const atMerge = sheet.getMergeContainingCell(col, row);
  if (atMerge) {
    if (direction === 'right') col = atMerge.endCol;
    else if (direction === 'left') col = atMerge.startCol;
    else if (direction === 'down') row = atMerge.endRow;
    else if (direction === 'up') row = atMerge.startRow;
  }
  if (direction === 'up') row = Math.max(0, row - 1);
  if (direction === 'down') row = Math.min(sheet.rowCount - 1, row + 1);
  if (direction === 'left') col = Math.max(0, col - 1);
  if (direction === 'right') col = Math.min(sheet.colCount - 1, col + 1);
  // Landing inside another merged region (anchor or hidden interior) snaps
  // to that region's top-left anchor — arrow navigation always treats a
  // merged region as a single cell.
  const landed = sheet.getMergeContainingCell(col, row);
  if (landed) {
    col = landed.startCol;
    row = landed.startRow;
  }
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
  if (!isValueAllowedForCell(sheet, key, rawValue)) {
    // Reject and keep the editor open so the user can fix it — see "Data
    // validation" below. Deliberately does not call removeEditor()/advance.
    showToast(`"${rawValue}" is not an allowed value for this cell`, { type: 'error' });
    return;
  }
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
// Fill handle
// ---------------------------------------------------------------------------
// A small square handle at the bottom-right corner of the active
// selection. Dragging it down or right previews an extended range; on
// mouseup the whole fill is applied as a single undoable action:
//   (a) formula source cells: relative refs shifted via shiftFormulaRefsByOffset
//       (the same helper paste already uses in refUtils/grid.js).
//   (b) 2+ non-formula source cells forming a recognized arithmetic or
//       weekday/month sequence: the sequence continues (not cyclic repeat).
//   (c) otherwise: the literal value/format is copied as-is (cyclically
//       repeating the source block if it has more than one cell).
// Only downward/rightward drags extend the selection — matching the common
// simplified fill-handle behavior (documented scope cut in the README).

// (fillHandleEl / lastFillPreviewCells are declared with the other early
// state, near the top of this file.)

const WEEKDAY_FULL = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const WEEKDAY_ABBR = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTH_FULL = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];
const MONTH_ABBR = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function ensureFillHandleEl() {
  fillHandleEl = document.createElement('div');
  fillHandleEl.className = 'fill-handle';
  els.sheetCanvas.appendChild(fillHandleEl);
  fillHandleEl.addEventListener('mousedown', onFillHandleMousedown);
}

function updateFillHandlePosition() {
  if (!fillHandleEl) return;
  const range = normalizedSelection();
  const td = cellElements.get(cellKeyFromRC(range.maxCol, range.maxRow));
  if (!td) {
    fillHandleEl.style.display = 'none';
    return;
  }
  const canvasRect = els.sheetCanvas.getBoundingClientRect();
  const tdRect = td.getBoundingClientRect();
  fillHandleEl.style.display = '';
  fillHandleEl.style.left = tdRect.right - canvasRect.left + 'px';
  fillHandleEl.style.top = tdRect.bottom - canvasRect.top + 'px';
}

function onFillHandleMousedown(e) {
  e.preventDefault();
  e.stopPropagation();
  const sourceRange = normalizedSelection();
  if (rangeIntersectsAnyMerge(workbook.activeSheet, sourceRange)) {
    showToast("Can't fill: selection contains a merged cell", { type: 'error' });
    return;
  }
  fillHandleState = { sourceRange, previewRange: { ...sourceRange } };

  function onMove(ev) {
    const el = document.elementFromPoint(ev.clientX, ev.clientY);
    const td = el && el.closest && el.closest('td.cell');
    if (!td) return;
    const col = parseInt(td.dataset.col, 10);
    const row = parseInt(td.dataset.row, 10);
    const dRow = row - sourceRange.maxRow;
    const dCol = col - sourceRange.maxCol;
    const target = { ...sourceRange };
    if (dRow > 0 && dRow >= dCol) target.maxRow = row;
    else if (dCol > 0) target.maxCol = col;
    fillHandleState.previewRange = target;
    renderFillPreview();
  }

  function onUp() {
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    clearFillPreview();
    const { sourceRange: sr, previewRange: pr } = fillHandleState;
    fillHandleState = null;
    if (pr.maxRow > sr.maxRow || pr.maxCol > sr.maxCol) performFill(sr, pr);
  }

  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
}

function renderFillPreview() {
  clearFillPreview();
  if (!fillHandleState) return;
  const { sourceRange: sr, previewRange: pr } = fillHandleState;
  for (let r = pr.minRow; r <= pr.maxRow; r++) {
    for (let c = pr.minCol; c <= pr.maxCol; c++) {
      const inSource = r >= sr.minRow && r <= sr.maxRow && c >= sr.minCol && c <= sr.maxCol;
      if (inSource) continue;
      const td = cellElements.get(cellKeyFromRC(c, r));
      if (td) {
        td.classList.add('is-fill-preview');
        lastFillPreviewCells.push(td);
      }
    }
  }
}

function clearFillPreview() {
  for (const td of lastFillPreviewCells) td.classList.remove('is-fill-preview');
  lastFillPreviewCells = [];
}

function detectSequenceForLine(lineCells) {
  if (lineCells.some((c) => typeof c.raw === 'string' && c.raw.startsWith('='))) return null;
  if (lineCells.length < 2) return null;
  const raws = lineCells.map((c) => String(c.raw ?? '').trim());
  const nums = lineCells.map((c) => literalValue(c.raw));
  if (nums.every((v) => typeof v === 'number')) {
    const step = nums[1] - nums[0];
    let consistent = true;
    for (let k = 1; k < nums.length; k++) {
      if (Math.abs(nums[k] - nums[k - 1] - step) > 1e-9) {
        consistent = false;
        break;
      }
    }
    if (consistent && step !== 0) return { rawAt: (idx) => String(nums[0] + step * idx) };
  }
  return detectNameSequence(raws, WEEKDAY_FULL, WEEKDAY_ABBR) || detectNameSequence(raws, MONTH_FULL, MONTH_ABBR);
}

function detectNameSequence(raws, fullNames, abbrNames) {
  const tryList = (names) => {
    const idxs = raws.map((r) => names.findIndex((n) => n.toLowerCase() === r.toLowerCase()));
    if (idxs.some((i) => i === -1)) return null;
    const period = names.length;
    const baseStep = (((idxs[1] - idxs[0]) % period) + period) % period;
    if (baseStep === 0) return null;
    for (let k = 1; k < idxs.length; k++) {
      const diff = (((idxs[k] - idxs[k - 1]) % period) + period) % period;
      if (diff !== baseStep) return null;
    }
    return { rawAt: (absIdx) => names[(((idxs[0] + baseStep * absIdx) % period) + period) % period] };
  };
  return tryList(fullNames) || tryList(abbrNames);
}

function computeFillCell(lineCells, seq, i, offsetForIndex) {
  const N = lineCells.length;
  const srcIdx = (i - 1) % N;
  const src = lineCells[srcIdx];
  const hasFormula = typeof src.raw === 'string' && src.raw.startsWith('=');
  if (hasFormula) {
    const { colOffset, rowOffset } = offsetForIndex(srcIdx);
    return { raw: shiftFormulaRefsByOffset(src.raw, colOffset, rowOffset), format: { ...src.format } };
  }
  if (seq) return { raw: seq.rawAt(N - 1 + i), format: { ...lineCells[0].format } };
  return { raw: src.raw, format: { ...src.format } };
}

function performFill(sourceRange, targetRange) {
  const sheet = workbook.activeSheet;
  if (rangeIntersectsAnyMerge(sheet, targetRange)) {
    showToast("Can't fill: target range contains a merged cell", { type: 'error' });
    return;
  }
  const rowExt = targetRange.maxRow > sourceRange.maxRow;
  const colExt = targetRange.maxCol > sourceRange.maxCol;
  const changes = [];
  if (rowExt) {
    const numNew = targetRange.maxRow - sourceRange.maxRow;
    for (let c = sourceRange.minCol; c <= sourceRange.maxCol; c++) {
      const lineCells = [];
      for (let r = sourceRange.minRow; r <= sourceRange.maxRow; r++) lineCells.push(sheet.snapshotCell(cellKeyFromRC(c, r)));
      const seq = detectSequenceForLine(lineCells);
      for (let i = 1; i <= numNew; i++) {
        const targetRow = sourceRange.maxRow + i;
        const key = cellKeyFromRC(c, targetRow);
        const before = sheet.snapshotCell(key);
        const after = computeFillCell(lineCells, seq, i, (srcIdx) => ({
          colOffset: 0,
          rowOffset: targetRow - (sourceRange.minRow + srcIdx),
        }));
        changes.push({ key, before, after });
      }
    }
  }
  if (colExt) {
    const numNew = targetRange.maxCol - sourceRange.maxCol;
    for (let r = sourceRange.minRow; r <= sourceRange.maxRow; r++) {
      const lineCells = [];
      for (let c = sourceRange.minCol; c <= sourceRange.maxCol; c++) lineCells.push(sheet.snapshotCell(cellKeyFromRC(c, r)));
      const seq = detectSequenceForLine(lineCells);
      for (let i = 1; i <= numNew; i++) {
        const targetCol = sourceRange.maxCol + i;
        const key = cellKeyFromRC(targetCol, r);
        const before = sheet.snapshotCell(key);
        const after = computeFillCell(lineCells, seq, i, (srcIdx) => ({
          colOffset: targetCol - (sourceRange.minCol + srcIdx),
          rowOffset: 0,
        }));
        changes.push({ key, before, after });
      }
    }
  }
  if (changes.length) applyChanges(changes);
}

// ---------------------------------------------------------------------------
// Cell merge
// ---------------------------------------------------------------------------
// Merged ranges are stored per-sheet as plain "A1:C3" strings (sheet.merges,
// see grid.js) and persisted in the .lsheet file. Rendering (colspan/rowspan
// on the anchor <td>, skipping the rest) lives in renderGrid(); formula
// resolution to the top-left anchor lives in grid.js's
// _resolveMergeAnchorKey(). This section owns the Merge/Unmerge actions
// themselves.
//
// Deliberate scope cut (documented in the README): merging/unmerging is not
// on the undo/redo stack, same precedent already established for
// conditional-formatting rule changes and sheet add/rename/delete — Ctrl+Z
// will skip right over a merge action to whatever came before it. Merging
// clears every cell in the range except the top-left one (their content is
// discarded immediately, matching the common "merge keeps only the anchor's
// content" spreadsheet convention) — that clearing is therefore also not
// undoable via Ctrl+Z. Unmerge only removes the merge boundary; it does not
// resurrect any content that was cleared when the cells were merged.
// Inserting/deleting rows or columns does not re-point existing merged
// ranges either, the same cut already applied to charts and
// conditional-formatting ranges.

function rangeIntersectsAnyMerge(sheet, sel) {
  return sheet.merges.some((m) => {
    const mp = parseRangeStr(m);
    return mp && mp.startCol <= sel.maxCol && mp.endCol >= sel.minCol && mp.startRow <= sel.maxRow && mp.endRow >= sel.minRow;
  });
}

function mergeFullyContainedInSelection(mp, sel) {
  return mp.startCol >= sel.minCol && mp.endCol <= sel.maxCol && mp.startRow >= sel.minRow && mp.endRow <= sel.maxRow;
}

function mergeSelection() {
  const sheet = workbook.activeSheet;
  const range = normalizedSelection();
  if (range.minCol === range.maxCol && range.minRow === range.maxRow) {
    showToast('Select more than one cell to merge', { type: 'error' });
    return;
  }
  const existing = sheet.merges.map((m) => ({ str: m, parsed: parseRangeStr(m) }));
  const overlaps = (parsed) =>
    parsed.startCol <= range.maxCol && parsed.endCol >= range.minCol && parsed.startRow <= range.maxRow && parsed.endRow >= range.minRow;
  const partialOverlap = existing.some(({ parsed }) => overlaps(parsed) && !mergeFullyContainedInSelection(parsed, range));
  if (partialOverlap) {
    showToast("Can't merge: overlaps an existing merged cell", { type: 'error' });
    return;
  }
  // Any existing merge fully inside the new selection is absorbed into it.
  sheet.merges = existing.filter(({ parsed }) => !mergeFullyContainedInSelection(parsed, range)).map(({ str }) => str);
  const rangeStr = `${cellKeyFromRC(range.minCol, range.minRow)}:${cellKeyFromRC(range.maxCol, range.maxRow)}`;
  sheet.merges.push(rangeStr);
  // Only the top-left cell's content survives — see scope-cut note above.
  for (let r = range.minRow; r <= range.maxRow; r++) {
    for (let c = range.minCol; c <= range.maxCol; c++) {
      if (c === range.minCol && r === range.minRow) continue;
      const key = cellKeyFromRC(c, r);
      const cell = sheet.getCell(key);
      if (cell && cell.raw !== '') sheet.setRaw(key, '');
    }
  }
  state.anchor = { col: range.minCol, row: range.minRow };
  state.focus = { col: range.minCol, row: range.minRow };
  setDirty(true);
  renderGrid();
  showToast('Cells merged');
}

function unmergeSelection() {
  const sheet = workbook.activeSheet;
  const range = normalizedSelection();
  const before = sheet.merges.length;
  sheet.merges = sheet.merges.filter((m) => {
    const mp = parseRangeStr(m);
    return !(mp && mp.startCol <= range.maxCol && mp.endCol >= range.minCol && mp.startRow <= range.maxRow && mp.endRow >= range.minRow);
  });
  if (sheet.merges.length === before) {
    showToast('No merged cells in selection', { type: 'error' });
    return;
  }
  setDirty(true);
  renderGrid();
  showToast('Cells unmerged');
}

// ---------------------------------------------------------------------------
// Freeze panes
// ---------------------------------------------------------------------------
// The real Excel mechanism: View ▸ Freeze Panes freezes every row above and
// every column left of the active cell (both sticky, scrolling
// independently — see the "is-frozen-row"/"is-frozen-col" cells in
// renderGrid, positioned via computeStickyOffsets since column widths/row
// heights aren't uniform). freezeRow/freezeCol on the Sheet are *counts* —
// "how many rows/columns are frozen" — not booleans.

function applyFreezePanes(rowCount, colCount) {
  const sheet = workbook.activeSheet;
  sheet.freezeRow = Math.max(0, rowCount);
  sheet.freezeCol = Math.max(0, colCount);
  setDirty(true);
  renderGrid();
}

function freezePanesAtActive() {
  const { col, row } = state.anchor;
  if (col === 0 && row === 0) {
    showToast('Select a cell other than A1 to freeze panes', { type: 'error' });
    return;
  }
  applyFreezePanes(row, col);
  showToast(`Froze ${row} row${row === 1 ? '' : 's'} and ${col} column${col === 1 ? '' : 's'}`);
}

// Thin wrappers over the general mechanism — Freeze Top Row is just
// freezePanes(row=1, col=0), Freeze First Column is freezePanes(row=0,
// col=1). Each replaces any existing freeze boundary rather than combining
// with it, matching how a single "current freeze state" is stored.
function freezeTopRow() {
  applyFreezePanes(1, 0);
  showToast('Froze top row');
}

function freezeFirstColumn() {
  applyFreezePanes(0, 1);
  showToast('Froze first column');
}

function unfreezePanes() {
  applyFreezePanes(0, 0);
  showToast('Unfroze panes');
}

// ---------------------------------------------------------------------------
// Sort
// ---------------------------------------------------------------------------
// Sorts the selected rows by the computed value of the selection's first
// column. Deliberate simplification (documented in the README): formulas do
// not survive the reorder with re-pointed relative refs — they are replaced
// by their last computed value, since a generic reorder can't preserve
// relative-formula semantics the way a plain row/column insert/delete can.

function sortSelection(direction) {
  const sheet = workbook.activeSheet;
  const range = normalizedSelection();
  if (range.maxRow - range.minRow + 1 < 2) return;
  if (rangeIntersectsAnyMerge(sheet, range)) {
    showToast("Can't sort: selection contains a merged cell", { type: 'error' });
    return;
  }
  const rows = [];
  for (let r = range.minRow; r <= range.maxRow; r++) {
    const rowCells = [];
    for (let c = range.minCol; c <= range.maxCol; c++) {
      const key = cellKeyFromRC(c, r);
      const cell = sheet.getCell(key);
      const snap = sheet.snapshotCell(key);
      let raw = snap.raw;
      if (typeof raw === 'string' && raw.startsWith('=')) {
        const computed = cell ? cell.computed : '';
        raw = isError(computed) ? '' : typeof computed === 'boolean' ? (computed ? 'TRUE' : 'FALSE') : String(computed ?? '');
      }
      rowCells.push({ raw, format: snap.format });
    }
    rows.push({ cells: rowCells, sortVal: literalValue(rowCells[0].raw) });
  }
  rows.sort((a, b) => {
    const av = a.sortVal;
    const bv = b.sortVal;
    const aBlank = av === '' || av === undefined || av === null;
    const bBlank = bv === '' || bv === undefined || bv === null;
    if (aBlank && bBlank) return 0;
    if (aBlank) return 1; // blanks always sort last
    if (bBlank) return -1;
    const cmp = typeof av === 'number' && typeof bv === 'number' ? av - bv : String(av).localeCompare(String(bv));
    return direction === 'asc' ? cmp : -cmp;
  });
  const changes = [];
  rows.forEach((row, i) => {
    const r = range.minRow + i;
    row.cells.forEach((cellData, ci) => {
      const key = cellKeyFromRC(range.minCol + ci, r);
      const before = sheet.snapshotCell(key);
      changes.push({ key, before, after: { raw: cellData.raw, format: cellData.format } });
    });
  });
  applyChanges(changes);
  showToast('Sorted selection');
}

// ---------------------------------------------------------------------------
// Conditional formatting
// ---------------------------------------------------------------------------
// Rules are stored per-sheet and applied as a read-only visual overlay at
// render time (see applyCellDisplay) — they never overwrite the cell's own
// manual formatting (bold/italic/text color/manual fill), only supply a
// background color when the cell doesn't already have one of its own.

function keyInRangeStr(key, rangeStr) {
  const range = parseRangeStr(rangeStr);
  if (!range) return false;
  const p = parseCellRefStr(key);
  if (!p) return false;
  return p.col >= range.startCol && p.col <= range.endCol && p.row >= range.startRow && p.row <= range.endRow;
}

function hexToRgb(hex) {
  const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(String(hex || ''));
  return m ? [parseInt(m[1], 16), parseInt(m[2], 16), parseInt(m[3], 16)] : [255, 255, 255];
}

function rgbToHex([r, g, b]) {
  return '#' + [r, g, b].map((v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');
}

// Relative luminance (WCAG) of a hex color, used to pick a legible ink color
// for conditional-formatting fills. Cell text otherwise always uses the
// theme's --ink-900, which is light in dark mode — fine against the dark
// canvas, but conditional-formatting fills are literal user-chosen colors
// (independent of theme) and default to light pastels, so ink-900 text goes
// nearly invisible on them in dark mode. Bug found during motion polish;
// see applyCellDisplay.
function relativeLuminance(hex) {
  const [r, g, b] = hexToRgb(hex).map((v) => v / 255);
  const lin = (c) => (c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

function lerpColor(hexA, hexB, t) {
  const a = hexToRgb(hexA);
  const b = hexToRgb(hexB);
  return rgbToHex([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]);
}

function getCondFormatBackground(sheet, key) {
  const cell = sheet.getCell(key);
  const value = cell ? cell.computed : '';
  let bg = null;
  for (const rule of sheet.condFormats) {
    if (!keyInRangeStr(key, rule.range)) continue;
    if (typeof value !== 'number') continue;
    if (rule.kind === 'highlight') {
      const rv = typeof rule.value === 'number' ? rule.value : Number(rule.value);
      if (Number.isNaN(rv)) continue;
      let match = false;
      if (rule.op === '>') match = value > rv;
      else if (rule.op === '<') match = value < rv;
      else match = value === rv;
      if (match) bg = rule.color;
    } else if (rule.kind === 'scale') {
      const range = parseRangeStr(rule.range);
      let min = Infinity;
      let max = -Infinity;
      for (const k of iterRangeKeys(range)) {
        const c = sheet.getCell(k);
        const v = c ? c.computed : '';
        if (typeof v === 'number') {
          if (v < min) min = v;
          if (v > max) max = v;
        }
      }
      if (min === Infinity || max === -Infinity || max === min) {
        bg = rule.midColor;
      } else {
        const mid = (min + max) / 2;
        bg = value <= mid
          ? lerpColor(rule.minColor, rule.midColor, (value - min) / (mid - min || 1))
          : lerpColor(rule.midColor, rule.maxColor, (value - mid) / (max - mid || 1));
      }
    }
  }
  return bg;
}

function openConditionalFormattingDialog() {
  const range = normalizedSelection();
  const rangeStr = `${cellKeyFromRC(range.minCol, range.minRow)}:${cellKeyFromRC(range.maxCol, range.maxRow)}`;
  const sheet = workbook.activeSheet;
  const existing = sheet.condFormats.filter((r) => r.range === rangeStr);

  const { bodyEl } = showDialog({
    title: 'Conditional Formatting',
    bodyHTML: `
      <label>Applies to</label>
      <input type="text" value="${escapeHtml(rangeStr)}" disabled />
      <label>Rule type</label>
      <select id="cf-kind" class="select">
        <option value="highlight">Highlight cells</option>
        <option value="scale">Color scale</option>
      </select>
      <div id="cf-highlight-fields">
        <label>Condition</label>
        <select id="cf-op" class="select">
          <option value=">">Greater than</option>
          <option value="<">Less than</option>
          <option value="=">Equal to</option>
        </select>
        <label>Value</label>
        <input type="text" id="cf-value" placeholder="e.g. 100" />
        <label>Color</label>
        <input type="color" id="cf-color" value="#f0d8d2" />
      </div>
      <div id="cf-scale-fields" style="display:none;">
        <label>Min color</label>
        <input type="color" id="cf-min-color" value="#f0d8d2" />
        <label>Mid color</label>
        <input type="color" id="cf-mid-color" value="#faf9f7" />
        <label>Max color</label>
        <input type="color" id="cf-max-color" value="#dbe6dc" />
      </div>
      ${existing.length ? `<label style="margin-top:12px;">Existing rules on this range</label><div id="cf-existing"></div>` : ''}
    `,
    buttons: [{ label: 'Cancel' }, { label: 'Apply', variant: 'primary', onClick: () => applyRule() }],
  });

  const kindSel = bodyEl.querySelector('#cf-kind');
  const highlightFields = bodyEl.querySelector('#cf-highlight-fields');
  const scaleFields = bodyEl.querySelector('#cf-scale-fields');
  kindSel.addEventListener('change', () => {
    const isScale = kindSel.value === 'scale';
    highlightFields.style.display = isScale ? 'none' : '';
    scaleFields.style.display = isScale ? '' : 'none';
  });

  if (existing.length) {
    const list = bodyEl.querySelector('#cf-existing');
    for (const rule of existing) {
      const row = document.createElement('div');
      row.className = 'cf-existing-row';
      const desc = document.createElement('span');
      desc.textContent =
        rule.kind === 'scale' ? 'Color scale' : `Highlight ${rule.op === '>' ? '>' : rule.op === '<' ? '<' : '='} ${rule.value}`;
      const removeBtn = document.createElement('button');
      removeBtn.className = 'btn-icon';
      removeBtn.dataset.icon = 'x';
      removeBtn.dataset.tooltip = 'Remove rule';
      removeBtn.addEventListener('click', () => {
        sheet.condFormats = sheet.condFormats.filter((r) => r.id !== rule.id);
        setDirty(true);
        renderAllCellContents();
        row.remove();
      });
      row.appendChild(desc);
      row.appendChild(removeBtn);
      list.appendChild(row);
      applyIcons(row);
    }
  }

  function applyRule() {
    const kind = kindSel.value;
    let rule;
    if (kind === 'highlight') {
      const value = literalValue(bodyEl.querySelector('#cf-value').value);
      rule = {
        id: 'cf' + Date.now() + Math.random().toString(36).slice(2, 7),
        range: rangeStr,
        kind: 'highlight',
        op: bodyEl.querySelector('#cf-op').value,
        value,
        color: bodyEl.querySelector('#cf-color').value,
      };
    } else {
      rule = {
        id: 'cf' + Date.now() + Math.random().toString(36).slice(2, 7),
        range: rangeStr,
        kind: 'scale',
        minColor: bodyEl.querySelector('#cf-min-color').value,
        midColor: bodyEl.querySelector('#cf-mid-color').value,
        maxColor: bodyEl.querySelector('#cf-max-color').value,
      };
    }
    sheet.condFormats.push(rule);
    setDirty(true);
    renderAllCellContents();
    showToast('Conditional formatting rule added');
  }
}

// ---------------------------------------------------------------------------
// Data validation
// ---------------------------------------------------------------------------
// Rules are stored per-sheet (sheet.dataValidations, see grid.js) using the
// same "list of rules, applies-to range string" shape and dialog pattern as
// conditional formatting above. A validated cell shows a small chevron
// affordance (see ensureValidationChevronEl/updateValidationChevronPosition,
// wired from updateSelectionUI) once it's the sole active cell; clicking it
// offers the allowed values as a popover list. Typing a value outside the
// list is rejected at commit time (see commitEdit) with a toast — chosen
// over visually flagging so there's exactly one, consistent way invalid
// entries are handled everywhere in the app (matches the toast-based error
// pattern already used for invalid ranges, failed saves, etc).
//
// Deliberate scope cut (documented in the README): validation is enforced
// only on direct typed entry (cell editor / formula bar). Paste, fill, and
// formula results are not checked against the allowed list.

function findValidationRuleForKey(sheet, key) {
  let found = null;
  for (const rule of sheet.dataValidations) {
    if (keyInRangeStr(key, rule.range)) found = rule; // last match wins, same precedent as conditional formatting
  }
  return found;
}

function isValueAllowedForCell(sheet, key, rawValue) {
  if (rawValue === '' || rawValue === undefined || rawValue === null) return true; // clearing is always allowed
  if (typeof rawValue === 'string' && rawValue.startsWith('=')) return true; // formulas bypass validation (scope cut)
  const rule = findValidationRuleForKey(sheet, key);
  if (!rule) return true;
  const needle = String(rawValue).trim().toLowerCase();
  return rule.values.some((v) => v.toLowerCase() === needle);
}

function ensureValidationChevronEl() {
  validationChevronEl = document.createElement('div');
  validationChevronEl.className = 'dv-chevron';
  validationChevronEl.innerHTML = window.lumen.icons['chevron-down'] || '';
  validationChevronEl.style.display = 'none';
  els.sheetCanvas.appendChild(validationChevronEl);
  validationChevronEl.addEventListener('mousedown', (e) => {
    e.preventDefault();
    e.stopPropagation();
  });
  validationChevronEl.addEventListener('click', (e) => {
    e.stopPropagation();
    if (!validationChevronRule) return;
    showPopover(validationChevronEl, buildValidationValuesEl(validationChevronKey, validationChevronRule));
  });
}

function updateValidationChevronPosition() {
  if (!validationChevronEl) return;
  const range = normalizedSelection();
  const isSingleCell = range.minCol === range.maxCol && range.minRow === range.maxRow;
  const sheet = workbook.activeSheet;
  const key = activeKey();
  const rule = isSingleCell ? findValidationRuleForKey(sheet, key) : null;
  validationChevronKey = key;
  validationChevronRule = rule;
  const td = cellElements.get(key);
  if (!rule || !td) {
    validationChevronEl.style.display = 'none';
    return;
  }
  const canvasRect = els.sheetCanvas.getBoundingClientRect();
  const tdRect = td.getBoundingClientRect();
  validationChevronEl.style.display = '';
  validationChevronEl.style.left = tdRect.right - canvasRect.left - 15 + 'px';
  validationChevronEl.style.top = tdRect.top - canvasRect.top + (tdRect.height - 14) / 2 + 'px';
}

function buildValidationValuesEl(key, rule) {
  const wrap = document.createElement('div');
  wrap.className = 'dv-list';
  for (const v of rule.values) {
    const item = document.createElement('div');
    item.className = 'dv-list__item';
    item.textContent = v;
    item.addEventListener('click', () => {
      closePopover();
      const sheet = workbook.activeSheet;
      const before = sheet.snapshotCell(key);
      if (before.raw === v) return;
      applyChanges([{ key, before, after: { raw: v, format: before.format } }]);
    });
    wrap.appendChild(item);
  }
  return wrap;
}

function openDataValidationDialog() {
  const range = normalizedSelection();
  const rangeStr = `${cellKeyFromRC(range.minCol, range.minRow)}:${cellKeyFromRC(range.maxCol, range.maxRow)}`;
  const sheet = workbook.activeSheet;
  const existing = sheet.dataValidations.filter((r) => r.range === rangeStr);

  const { bodyEl } = showDialog({
    title: 'Data Validation',
    bodyHTML: `
      <label>Applies to</label>
      <input type="text" value="${escapeHtml(rangeStr)}" disabled />
      <label>Allowed values (comma-separated)</label>
      <input type="text" id="dv-values" placeholder="e.g. Yes, No, Maybe" />
      ${existing.length ? `<label style="margin-top:12px;">Existing rules on this range</label><div id="dv-existing"></div>` : ''}
    `,
    buttons: [{ label: 'Cancel' }, { label: 'Apply', variant: 'primary', onClick: () => applyRule() }],
  });

  if (existing.length) {
    const list = bodyEl.querySelector('#dv-existing');
    for (const rule of existing) {
      const row = document.createElement('div');
      row.className = 'cf-existing-row';
      const desc = document.createElement('span');
      desc.textContent = rule.values.join(', ');
      const removeBtn = document.createElement('button');
      removeBtn.className = 'btn-icon';
      removeBtn.dataset.icon = 'x';
      removeBtn.dataset.tooltip = 'Remove rule';
      removeBtn.addEventListener('click', () => {
        sheet.dataValidations = sheet.dataValidations.filter((r) => r.id !== rule.id);
        setDirty(true);
        updateValidationChevronPosition();
        row.remove();
      });
      row.appendChild(desc);
      row.appendChild(removeBtn);
      list.appendChild(row);
      applyIcons(row);
    }
  }

  function applyRule() {
    const values = bodyEl
      .querySelector('#dv-values')
      .value.split(',')
      .map((v) => v.trim())
      .filter((v) => v !== '');
    if (!values.length) {
      showToast('Enter at least one allowed value', { type: 'error' });
      return;
    }
    sheet.dataValidations.push({
      id: 'dv' + Date.now() + Math.random().toString(36).slice(2, 7),
      range: rangeStr,
      values,
    });
    setDirty(true);
    updateValidationChevronPosition();
    showToast('Data validation rule added');
  }
}

// ---------------------------------------------------------------------------
// Charts
// ---------------------------------------------------------------------------
// Hand-rolled inline SVG (no charting dependency, keeping the no-bundler
// renderer architecture intact). Single data series only — the multi-series
// case (2-D range with several data columns/rows) is a documented scope cut.
// Label-orientation heuristic: if the first row is mostly text and the first
// column is mostly numeric, the first row supplies category labels (one data
// row is used as the single series); otherwise labels come down the first
// column and values from the next column (skipping a text header row if the
// range's top row looks like one).

// (chartElements is declared with the other early state, near the top of
// this file.)

function extractChartData(sheet, rangeStr) {
  const range = parseRangeStr(rangeStr);
  if (!range) return null;
  const getVal = (c, r) => {
    const cell = sheet.getCell(cellKeyFromRC(c, r));
    return cell ? cell.computed : '';
  };
  const isNumeric = (v) => typeof v === 'number';
  const width = range.endCol - range.startCol + 1;

  let rowTextCount = 0;
  let rowTotal = 0;
  for (let c = range.startCol; c <= range.endCol; c++) {
    const v = getVal(c, range.startRow);
    if (v === '' || v === undefined) continue;
    rowTotal++;
    if (!isNumeric(v)) rowTextCount++;
  }
  let colNumCount = 0;
  let colTotal = 0;
  for (let r = range.startRow; r <= range.endRow; r++) {
    const v = getVal(range.startCol, r);
    if (v === '' || v === undefined) continue;
    colTotal++;
    if (isNumeric(v)) colNumCount++;
  }
  const useRowHeaders =
    width > 1 && rowTotal > 0 && rowTextCount / rowTotal > 0.5 && colTotal > 0 && colNumCount / colTotal > 0.5;

  const labels = [];
  const values = [];
  if (useRowHeaders) {
    const dataRow = Math.min(range.startRow + 1, range.endRow);
    for (let c = range.startCol; c <= range.endCol; c++) {
      labels.push(String(getVal(c, range.startRow)));
      const v = getVal(c, dataRow);
      values.push(isNumeric(v) ? v : 0);
    }
  } else {
    const valueCol = width > 1 ? range.startCol + 1 : range.startCol;
    let startRow = range.startRow;
    const headerCandidate = getVal(valueCol, range.startRow);
    if (width > 1 && headerCandidate !== '' && !isNumeric(headerCandidate)) startRow = range.startRow + 1;
    for (let r = startRow; r <= range.endRow; r++) {
      labels.push(String(getVal(range.startCol, r)));
      const v = getVal(valueCol, r);
      values.push(isNumeric(v) ? v : 0);
    }
  }
  return { labels, values };
}

const PIE_HUES = ['var(--accent-600)', 'var(--accent-500)', '#8a8f3c', '#3c8f86', '#7a4a9c', '#c9622f'];

// Compact axis-tick formatting: integers print bare, large magnitudes
// abbreviate to k/M so a $1,200 budget doesn't blow out the left margin.
function formatAxisValue(v) {
  const rounded = Math.round(v * 100) / 100;
  if (rounded === 0) return '0';
  const abs = Math.abs(rounded);
  if (abs >= 1000000) return (Math.round((rounded / 1000000) * 10) / 10) + 'M';
  if (abs >= 1000) return (Math.round((rounded / 1000) * 10) / 10) + 'k';
  return String(rounded);
}

function buildChartSVG(chart, data) {
  const w = chart.w;
  const h = chart.h - 34; // minus header height
  const values = data.values;
  let inner = '';

  if (chart.type === 'pie') {
    const total = values.reduce((a, b) => a + Math.abs(b), 0) || 1;
    // Blank/zero rows are common when a selection over-reaches the real data
    // (e.g. a range that includes trailing empty rows) — they'd otherwise
    // pad the legend with a wall of meaningless "0%" entries.
    const nonZero = values.map((v, i) => ({ v, label: data.labels[i], color: PIE_HUES[i % PIE_HUES.length] })).filter((d) => d.v !== 0);
    const showLegend = nonZero.length > 1;
    const legendW = showLegend ? Math.min(120, w * 0.4) : 0;
    const plotW = w - legendW;
    const cx = plotW / 2;
    const cy = h / 2;
    const r = Math.max(4, Math.min(plotW, h) / 2 - 10);
    let angleStart = -Math.PI / 2;
    values.forEach((v, i) => {
      const frac = Math.abs(v) / total;
      const angleEnd = angleStart + frac * Math.PI * 2;
      const x1 = cx + r * Math.cos(angleStart);
      const y1 = cy + r * Math.sin(angleStart);
      const x2 = cx + r * Math.cos(angleEnd);
      const y2 = cy + r * Math.sin(angleEnd);
      const largeArc = angleEnd - angleStart > Math.PI ? 1 : 0;
      const color = PIE_HUES[i % PIE_HUES.length];
      if (frac > 0) {
        inner += `<path class="chart-pie-slice" d="M ${cx} ${cy} L ${x1.toFixed(1)} ${y1.toFixed(1)} A ${r.toFixed(1)} ${r.toFixed(1)} 0 ${largeArc} 1 ${x2.toFixed(1)} ${y2.toFixed(1)} Z" fill="${color}" stroke="var(--surface-0)" stroke-width="1.5"/>`;
        // Percentage label directly on the wedge, but only when it's wide
        // enough not to clutter a sliver slice.
        if (frac >= 0.08) {
          const midAngle = (angleStart + angleEnd) / 2;
          const labelR = r * 0.62;
          const lx = cx + labelR * Math.cos(midAngle);
          const ly = cy + labelR * Math.sin(midAngle);
          inner += `<text x="${lx.toFixed(1)}" y="${ly.toFixed(1)}" font-size="var(--text-xs)" font-weight="600" fill="#fff" text-anchor="middle" dominant-baseline="middle" pointer-events="none">${Math.round(frac * 100)}%</text>`;
        }
      }
      angleStart = angleEnd;
    });
    if (showLegend) {
      const rowH = Math.min(20, (h - 12) / nonZero.length);
      const startY = h / 2 - (nonZero.length * rowH) / 2 + rowH / 2;
      nonZero.forEach((d, i) => {
        const y = startY + i * rowH;
        const pct = Math.round((Math.abs(d.v) / total) * 100);
        inner += `<rect x="${(plotW + 10).toFixed(1)}" y="${(y - 5).toFixed(1)}" width="9" height="9" rx="2" fill="${d.color}"/>`;
        inner += `<text x="${(plotW + 24).toFixed(1)}" y="${(y + 4).toFixed(1)}" font-size="var(--text-xs)" fill="var(--ink-600)">${escapeHtml(String(d.label).slice(0, 10))} <tspan fill="var(--ink-400)">${pct}%</tspan></text>`;
      });
    }
    return `<svg viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" xmlns="http://www.w3.org/2000/svg">${inner}</svg>`;
  }

  const maxV = Math.max(0, ...values);
  const minV = Math.min(0, ...values);
  const spread = maxV - minV || 1;

  // Three evenly-spaced ticks (min / mid / max), deduped for flat data —
  // drives both the gridlines and the left-axis value labels.
  const tickValues = minV === maxV ? [maxV] : [minV, (minV + maxV) / 2, maxV];
  const tickLabels = tickValues.map(formatAxisValue);
  const maxTickLen = Math.max(...tickLabels.map((s) => s.length));
  const padding = { top: 20, right: 12, bottom: 26, left: Math.max(28, 12 + maxTickLen * 6.5) };
  const plotW = Math.max(1, w - padding.left - padding.right);
  const plotH = Math.max(1, h - padding.top - padding.bottom);
  const scaleY = (v) => padding.top + plotH - ((v - minV) / spread) * plotH;

  // Gridlines + axis value labels first, underneath the marks.
  tickValues.forEach((tv, i) => {
    const y = scaleY(tv);
    inner += `<line x1="${padding.left}" y1="${y.toFixed(1)}" x2="${(padding.left + plotW).toFixed(1)}" y2="${y.toFixed(1)}" stroke="var(--ink-200)" stroke-width="1"/>`;
    inner += `<text x="${(padding.left - 8).toFixed(1)}" y="${(y + 3.5).toFixed(1)}" font-size="var(--text-xs)" fill="var(--ink-400)" text-anchor="end">${escapeHtml(tickLabels[i])}</text>`;
  });
  inner += `<line x1="${padding.left}" y1="${padding.top}" x2="${padding.left}" y2="${(padding.top + plotH).toFixed(1)}" stroke="var(--ink-400)" stroke-width="1"/>`;

  const n = Math.max(values.length, 1);
  const slot = plotW / n;
  const dense = n > 8; // beyond this, permanent value labels start to collide
  const valueLabelClass = dense ? 'chart-mark__value chart-mark__value--on-hover' : 'chart-mark__value';

  if (chart.type === 'line') {
    const points = values.map((v, i) => `${(padding.left + i * slot + slot / 2).toFixed(1)},${scaleY(v).toFixed(1)}`).join(' ');
    inner += `<polyline points="${points}" fill="none" stroke="var(--accent-600)" stroke-width="2"/>`;
    values.forEach((v, i) => {
      const x = padding.left + i * slot + slot / 2;
      const y = scaleY(v);
      const labelY = Math.max(10, y - 9);
      inner += `<g class="chart-mark">`;
      inner += `<circle class="chart-mark__point" cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="3"/>`;
      inner += `<circle class="chart-mark__hit" cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="8" fill="transparent"/>`;
      inner += `<text class="${valueLabelClass}" x="${x.toFixed(1)}" y="${labelY.toFixed(1)}" text-anchor="middle">${escapeHtml(formatAxisValue(v))}</text>`;
      inner += `</g>`;
    });
  } else {
    const barW = slot * 0.6;
    values.forEach((v, i) => {
      const x = padding.left + i * slot + (slot - barW) / 2;
      const y0 = scaleY(0);
      const y1 = scaleY(v);
      const barY = Math.min(y0, y1);
      const barH = Math.max(Math.abs(y1 - y0), 0.5);
      const labelY = v >= 0 ? Math.max(10, barY - 6) : Math.min(h - padding.bottom + 14, barY + barH + 12);
      inner += `<g class="chart-mark">`;
      inner += `<rect class="chart-mark__bar" x="${x.toFixed(1)}" y="${barY.toFixed(1)}" width="${barW.toFixed(1)}" height="${barH.toFixed(1)}" rx="2"/>`;
      inner += `<text class="${valueLabelClass}" x="${(x + barW / 2).toFixed(1)}" y="${labelY.toFixed(1)}" text-anchor="middle">${escapeHtml(formatAxisValue(v))}</text>`;
      inner += `</g>`;
    });
  }
  data.labels.forEach((label, i) => {
    const x = padding.left + i * slot + slot / 2;
    inner += `<text x="${x.toFixed(1)}" y="${h - 8}" font-size="var(--text-xs)" fill="var(--ink-400)" text-anchor="middle">${escapeHtml(String(label).slice(0, 8))}</text>`;
  });
  return `<svg viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" xmlns="http://www.w3.org/2000/svg">${inner}</svg>`;
}

function chartTypeLabel(type) {
  return type === 'bar' ? 'Bar chart' : type === 'line' ? 'Line chart' : 'Pie chart';
}

function redrawChart(chart, rec) {
  const sheet = workbook.activeSheet;
  const data = extractChartData(sheet, chart.range);
  rec.titleEl.textContent = `${chartTypeLabel(chart.type)} — ${chart.range}`;
  // Fade the body out, swap its content, then fade back in — so a refresh
  // (or the initial draw) settles in rather than popping straight to the
  // new SVG. Two rAFs: one to let the opacity:0 paint before the content
  // swap, one to let the new content paint before transitioning back to 1.
  rec.bodyEl.style.opacity = '0';
  requestAnimationFrame(() => {
    if (!data || !data.values.length) {
      rec.bodyEl.innerHTML = `<div class="chart-box__empty">No data in range</div>`;
    } else {
      rec.bodyEl.innerHTML = buildChartSVG(chart, data);
    }
    requestAnimationFrame(() => {
      rec.bodyEl.style.opacity = '1';
    });
  });
}

function createChartEl(chart, opts = {}) {
  const box = document.createElement('div');
  box.className = 'chart-box' + (opts.animate ? ' is-inserting' : '');
  box.style.left = chart.x + 'px';
  box.style.top = chart.y + 'px';
  box.style.width = chart.w + 'px';
  box.style.height = chart.h + 'px';
  box.innerHTML = `
    <div class="chart-box__header">
      <span class="chart-box__title"></span>
      <div class="chart-box__actions">
        <button class="btn-icon chart-box__refresh" data-icon="redo" data-tooltip="Refresh chart"></button>
        <button class="btn-icon chart-box__close" data-icon="x" data-tooltip="Remove chart"></button>
      </div>
    </div>
    <div class="chart-box__body"></div>
  `;
  els.sheetCanvas.appendChild(box);
  applyIcons(box);
  const rec = { el: box, titleEl: box.querySelector('.chart-box__title'), bodyEl: box.querySelector('.chart-box__body') };
  chartElements.set(chart.id, rec);
  redrawChart(chart, rec);

  box.addEventListener('mousedown', (e) => e.stopPropagation());
  box.querySelector('.chart-box__refresh').addEventListener('click', () => redrawChart(chart, rec));
  box.querySelector('.chart-box__close').addEventListener('click', () => {
    const sheet = workbook.activeSheet;
    sheet.charts = sheet.charts.filter((c) => c.id !== chart.id);
    setDirty(true);
    box.remove();
    chartElements.delete(chart.id);
  });

  const header = box.querySelector('.chart-box__header');
  header.addEventListener('mousedown', (e) => {
    if (e.target.closest('button')) return;
    e.preventDefault();
    e.stopPropagation();
    const startX = e.clientX;
    const startY = e.clientY;
    const startLeft = chart.x;
    const startTop = chart.y;
    const onMove = (ev) => {
      chart.x = Math.max(0, startLeft + (ev.clientX - startX));
      chart.y = Math.max(0, startTop + (ev.clientY - startY));
      box.style.left = chart.x + 'px';
      box.style.top = chart.y + 'px';
    };
    const onUp = () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      setDirty(true);
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  });
}

function renderAllCharts() {
  chartElements.forEach((rec) => rec.el.remove());
  chartElements = new Map();
  for (const chart of workbook.activeSheet.charts) createChartEl(chart);
}

function refreshAllCharts() {
  for (const chart of workbook.activeSheet.charts) {
    const rec = chartElements.get(chart.id);
    if (rec) redrawChart(chart, rec);
  }
}

function openInsertChartDialog() {
  const range = normalizedSelection();
  const defaultRange = `${cellKeyFromRC(range.minCol, range.minRow)}:${cellKeyFromRC(range.maxCol, range.maxRow)}`;
  const { bodyEl } = showDialog({
    title: 'Insert Chart',
    bodyHTML: `
      <label>Source range</label>
      <input type="text" id="chart-range" value="${escapeHtml(defaultRange)}" />
      <label>Chart type</label>
      <select id="chart-type" class="select">
        <option value="bar">Bar</option>
        <option value="line">Line</option>
        <option value="pie">Pie</option>
      </select>
    `,
    buttons: [{ label: 'Cancel' }, { label: 'Insert', variant: 'primary', onClick: () => insertChart() }],
  });

  function insertChart() {
    const rangeStr = bodyEl.querySelector('#chart-range').value.trim().toUpperCase();
    const type = bodyEl.querySelector('#chart-type').value;
    if (!parseRangeStr(rangeStr)) {
      showToast('Invalid range', { type: 'error' });
      return;
    }
    const sheet = workbook.activeSheet;
    const parsed = parseRangeStr(rangeStr);
    const cascade = sheet.charts.length * 24;
    let x = 40 + cascade;
    let y = 40 + cascade;
    // Default the chart next to (not on top of) the source range, so the data
    // it was built from stays visible. Fall back to the top-left cascade above
    // if the range's cells aren't currently in the rendered DOM.
    const topRightTd = parsed && cellElements.get(cellKeyFromRC(parsed.endCol, parsed.startRow));
    if (topRightTd) {
      const canvasRect = els.sheetCanvas.getBoundingClientRect();
      const tdRect = topRightTd.getBoundingClientRect();
      x = tdRect.right - canvasRect.left + els.sheetCanvas.scrollLeft + 16 + cascade;
      y = tdRect.top - canvasRect.top + els.sheetCanvas.scrollTop + cascade;
    }
    const chart = {
      id: 'chart' + Date.now() + Math.random().toString(36).slice(2, 7),
      type,
      range: rangeStr,
      x,
      y,
      w: 340,
      h: 260,
    };
    sheet.charts.push(chart);
    setDirty(true);
    createChartEl(chart, { animate: true });
    showToast('Chart inserted');
  }
}

// ---------------------------------------------------------------------------
// Keyboard shortcuts dialog
// ---------------------------------------------------------------------------

function showKeyboardShortcuts() {
  const rows = [
    ['Ctrl+N / Ctrl+O / Ctrl+S / Ctrl+Shift+S', 'New / Open / Save / Save As'],
    ['Ctrl+Z / Ctrl+Y (or Ctrl+Shift+Z)', 'Undo / Redo'],
    ['Ctrl+C / Ctrl+X / Ctrl+V', 'Copy / Cut / Paste'],
    ['Ctrl+B / Ctrl+I / Ctrl+U', 'Bold / Italic / Underline'],
    ['Ctrl+F', 'Find'],
    ['Ctrl+P', 'Print'],
    ['Arrow keys', 'Move active cell (Shift+Arrow extends selection; skips over merged cells)'],
    ['Enter / Shift+Enter', 'Move down / up (or commit an edit and move)'],
    ['Tab / Shift+Tab', 'Move right / left (or commit an edit and move)'],
    ['F2 / double-click', 'Start editing the active cell'],
    ['Delete / Backspace', "Clear selected cells' contents"],
    ['Escape', 'Cancel an in-progress edit'],
    ['Drag the fill handle', 'Fill / extend a series into adjacent cells'],
  ];
  const bodyHTML = `
    <div class="shortcut-list">
      ${rows
        .map(
          ([keys, desc]) => `
        <div class="shortcut-list__row">
          <span class="shortcut-list__keys">${escapeHtml(keys)}</span>
          <span class="shortcut-list__desc">${escapeHtml(desc)}</span>
        </div>`
        )
        .join('')}
    </div>
  `;
  showDialog({ title: 'Keyboard Shortcuts', bodyHTML, buttons: [{ label: 'Close', variant: 'primary' }] });
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
// Recent files
// ---------------------------------------------------------------------------

async function refreshRecentCache() {
  recentCache = await window.lumen.recent.get();
  return recentCache;
}

async function touchRecent(filePath) {
  recentCache = await window.lumen.recent.add(filePath);
}

function recentLabel(filePath) {
  return filePath
    .split(/[\\/]/)
    .pop()
    .replace(/\.lsheet$/i, '');
}

function recentOpenRecentSubmenu() {
  if (!recentCache.length) return [{ label: '(No recent files)', disabled: true }];
  return recentCache.map((entry) => ({
    label: recentLabel(entry.path),
    onClick: () => openFileAtPath(entry.path),
  }));
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
  // File > New opens the template gallery instead of jumping straight to a
  // blank workbook — see "Start screen" section.
  showStartScreen();
}

async function openWorkbookFlow() {
  const filePath = await window.lumen.dialogs.openLsheet();
  if (!filePath) return;
  await openFileAtPath(filePath);
}

async function openFileAtPath(filePath) {
  if (workbook.dirty) {
    const choice = await confirmUnsaved();
    if (choice === 'cancel') return;
    if (choice === 'save') {
      const ok = await saveWorkbook();
      if (!ok) return;
    }
  }
  const res = await window.lumen.file.readLsheet(filePath);
  if (!res.ok) {
    showToast('Could not open file', { type: 'error' });
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
  hideStartScreen();
  showToast(`Opened "${workbook.title}"`);
  await touchRecent(filePath);
}

async function saveWorkbook() {
  if (!currentFilePath) return saveWorkbookAs();
  const data = workbook.toJSON();
  const res = await window.lumen.file.writeLsheet(currentFilePath, data);
  if (res.ok) {
    setDirty(false);
    showToast('Saved');
    await touchRecent(currentFilePath);
    return true;
  }
  showToast('Save failed', { type: 'error' });
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
    showToast('Saved');
    await touchRecent(filePath);
    return true;
  }
  showToast('Save failed', { type: 'error' });
  showDialog({ title: 'Save failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
  return false;
}

async function importCsv() {
  const filePath = await window.lumen.dialogs.openImport('csv');
  if (!filePath) return;
  const res = await window.lumen.file.importCsv(filePath);
  if (!res.ok) {
    showToast('Import failed', { type: 'error' });
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
  hideStartScreen();
  showToast('Imported CSV');
}

async function importXlsx() {
  const filePath = await window.lumen.dialogs.openImport('xlsx');
  if (!filePath) return;
  const res = await window.lumen.file.importXlsx(filePath);
  if (!res.ok) {
    showToast('Import failed', { type: 'error' });
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
  hideStartScreen();
  showToast('Imported Excel workbook');
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
  if (res.ok) {
    showToast('Exported CSV');
  } else {
    showToast('Export failed', { type: 'error' });
    showDialog({ title: 'Export failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
  }
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
  if (res.ok) {
    showToast('Exported Excel workbook');
  } else {
    showToast('Export failed', { type: 'error' });
    showDialog({ title: 'Export failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
  }
}

// ---------------------------------------------------------------------------
// Print / Page Setup
// ---------------------------------------------------------------------------
// File ▸ Page Setup stores { pageSize, orientation, printArea } per sheet
// (sheet.pageSetup, see grid.js). File ▸ Print and Export ▸ PDF share one
// code path: build an off-screen "print root" — a plain <table> reproducing
// just the print area's computed values/formatting (merges included, clipped
// to the area), scaled with a CSS transform to fit a single page of the
// chosen size/orientation — inject a matching `@page` CSS rule, then either
// call window.print() (Electron wires this to the native print dialog with
// no IPC needed) or hand off to main.js's printToPDF (which honors that same
// `@page` rule via `preferCSSPageSize`). Scope cut, documented in the
// README: this is a single-page scale-to-fit, not Excel's full
// page-break-preview/multi-page-tiling system.

const PRINT_PAGE_SIZES_IN = { letter: [8.5, 11], a4: [8.27, 11.69], legal: [8.5, 14] };
const PRINT_MARGIN_IN = 0.4;

/** "A1:<last used cell>" — the default print area when none has been set explicitly. */
function usedRangeString(sheet) {
  let maxCol = -1;
  let maxRow = -1;
  for (const [key, cell] of sheet.cells) {
    if (cell.raw === '' || cell.raw === undefined || cell.raw === null) continue;
    const p = parseCellRefStr(key);
    if (!p) continue;
    if (p.col > maxCol) maxCol = p.col;
    if (p.row > maxRow) maxRow = p.row;
  }
  if (maxCol < 0) return 'A1:A1';
  return `A1:${cellKeyFromRC(maxCol, maxRow)}`;
}

function buildPrintTable(sheet, range) {
  const table = document.createElement('table');
  table.className = 'print-table';
  const tbody = document.createElement('tbody');
  for (let r = range.startRow; r <= range.endRow; r++) {
    const tr = document.createElement('tr');
    for (let c = range.startCol; c <= range.endCol; c++) {
      const merge = sheet.getMergeContainingCell(c, r);
      if (merge && (merge.startCol !== c || merge.startRow !== r)) continue;
      const key = cellKeyFromRC(c, r);
      const cell = sheet.getCell(key);
      const fmt = cell ? cell.format : defaultFormat();
      const value = cell ? cell.computed : '';
      const td = document.createElement('td');
      if (merge) {
        // Clip the span to the print area in case the merge hangs off its edge.
        td.colSpan = Math.min(merge.endCol, range.endCol) - c + 1;
        td.rowSpan = Math.min(merge.endRow, range.endRow) - r + 1;
      }
      td.textContent = formatValue(value, fmt.numberFormat);
      if (fmt.bold) td.style.fontWeight = '700';
      if (fmt.italic) td.style.fontStyle = 'italic';
      if (fmt.underline) td.style.textDecoration = 'underline';
      td.style.textAlign = fmt.align || (isNumericValue(value) ? 'right' : 'left');
      const bg = fmt.bg || getCondFormatBackground(sheet, key);
      if (bg) td.style.background = bg;
      if (fmt.color) td.style.color = fmt.color;
      tr.appendChild(td);
    }
    tbody.appendChild(tr);
  }
  table.appendChild(tbody);
  return table;
}

/** Build (and append to <body>) the off-screen print root for `sheet`. Returns { cleanup }. */
function buildPrintRoot(sheet) {
  const setup = sheet.pageSetup || { pageSize: 'letter', orientation: 'portrait', printArea: null };
  const areaStr = setup.printArea && parseRangeStr(setup.printArea) ? setup.printArea : usedRangeString(sheet);
  const range = parseRangeStr(areaStr) || parseRangeStr(usedRangeString(sheet));
  const [baseW, baseH] = PRINT_PAGE_SIZES_IN[setup.pageSize] || PRINT_PAGE_SIZES_IN.letter;
  const landscape = setup.orientation === 'landscape';
  const pageWIn = landscape ? baseH : baseW;
  const pageHIn = landscape ? baseW : baseH;

  const style = document.createElement('style');
  style.textContent = `@page { size: ${pageWIn}in ${pageHIn}in; margin: 0; }`;
  document.head.appendChild(style);

  const root = document.createElement('div');
  root.id = 'print-root';

  const page = document.createElement('div');
  page.className = 'print-page';
  page.style.width = pageWIn + 'in';
  page.style.height = pageHIn + 'in';
  page.style.padding = PRINT_MARGIN_IN + 'in';

  const scaleWrap = document.createElement('div');
  scaleWrap.className = 'print-scale';
  const table = buildPrintTable(sheet, range);
  scaleWrap.appendChild(table);
  page.appendChild(scaleWrap);
  root.appendChild(page);
  document.body.appendChild(root);

  // Measure the table's natural (unscaled) size, then shrink it to fit the
  // page's printable area — see the scope-cut note above the section header.
  const contentW = table.offsetWidth || 1;
  const contentH = table.offsetHeight || 1;
  const availW = (pageWIn - 2 * PRINT_MARGIN_IN) * 96;
  const availH = (pageHIn - 2 * PRINT_MARGIN_IN) * 96;
  const scale = Math.min(1, availW / contentW, availH / contentH);
  scaleWrap.style.transform = `scale(${scale})`;

  const cleanup = () => {
    if (root.isConnected) root.remove();
    if (style.isConnected) style.remove();
  };
  return { cleanup };
}

function printSheet() {
  const { cleanup } = buildPrintRoot(workbook.activeSheet);
  const onAfterPrint = () => {
    cleanup();
    window.removeEventListener('afterprint', onAfterPrint);
  };
  window.addEventListener('afterprint', onAfterPrint);
  // Fallback in case 'afterprint' never fires — same defensive pattern as
  // toast.js's transitionend fallback.
  setTimeout(onAfterPrint, 20000);
  window.print();
}

async function exportPdf() {
  const sheet = workbook.activeSheet;
  const defaultName = (workbook.title || 'workbook') + '.pdf';
  const filePath = await window.lumen.dialogs.saveExport('pdf', defaultName);
  if (!filePath) return;
  const { cleanup } = buildPrintRoot(sheet);
  try {
    const res = await window.lumen.file.exportPdf(filePath, {});
    if (res.ok) {
      showToast('Exported PDF');
    } else {
      showToast('Export failed', { type: 'error' });
      showDialog({ title: 'Export failed', bodyHTML: `<p>${escapeHtml(res.error)}</p>` });
    }
  } finally {
    cleanup();
  }
}

function openPageSetupDialog() {
  const sheet = workbook.activeSheet;
  const setup = sheet.pageSetup;
  const defaultArea = setup.printArea || usedRangeString(sheet);
  const { bodyEl } = showDialog({
    title: 'Page Setup',
    bodyHTML: `
      <label>Page size</label>
      <select id="ps-size" class="select">
        <option value="letter">Letter (8.5 × 11 in)</option>
        <option value="a4">A4 (210 × 297 mm)</option>
        <option value="legal">Legal (8.5 × 14 in)</option>
      </select>
      <label>Orientation</label>
      <select id="ps-orientation" class="select">
        <option value="portrait">Portrait</option>
        <option value="landscape">Landscape</option>
      </select>
      <label>Print area</label>
      <input type="text" id="ps-area" value="${escapeHtml(defaultArea)}" />
    `,
    buttons: [{ label: 'Cancel' }, { label: 'Apply', variant: 'primary', onClick: () => applySetup() }],
  });
  bodyEl.querySelector('#ps-size').value = setup.pageSize;
  bodyEl.querySelector('#ps-orientation').value = setup.orientation;

  function applySetup() {
    const areaRaw = bodyEl.querySelector('#ps-area').value.trim().toUpperCase();
    if (!parseRangeStr(areaRaw)) {
      showToast('Invalid print area range', { type: 'error' });
      return;
    }
    sheet.pageSetup = {
      pageSize: bodyEl.querySelector('#ps-size').value,
      orientation: bodyEl.querySelector('#ps-orientation').value,
      printArea: areaRaw,
    };
    setDirty(true);
    showToast('Page setup updated');
  }
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
  if (mod && !e.shiftKey && e.key.toLowerCase() === 'p') {
    e.preventDefault();
    printSheet();
    return;
  }

  // Bug fix (found during motion/focus-visibility polish): everything below
  // this point is grid navigation (Tab/Enter/arrows/Delete/typing-to-edit)
  // that assumes the sheet itself has "focus" — but the sheet has no real
  // focusable element, so that's really just document.body. If keyboard
  // focus is actually resting on a control (a toolbar button, menu item,
  // dialog field, link — anything reachable by Tab), these were hijacking
  // Tab/Enter/Space/typing away from that control instead of letting it
  // move focus or activate natively. That made it impossible to Tab off a
  // toolbar button, or to press Enter/Space to activate one.
  if (ae && ae !== document.body && ae.closest('button, select, a, [tabindex]')) return;

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
