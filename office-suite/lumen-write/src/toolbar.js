// toolbar.js — builds the icon/select toolbar and wires it to the editor
// engine. Font family/size and text/highlight color use wrapSelection()
// (custom <span style> wrapping); everything else uses execCommand.

import { exec, wrapSelection, getSelectionState, restoreSelection, focusPage, insertHTML } from './editor.js';
import { promptLinkDialog, promptTableDialog } from './dialogs.js';

const FONT_FAMILIES = [
  { label: 'Georgia (default)', value: 'Georgia, "Iowan Old Style", "Palatino Linotype", serif' },
  { label: 'Segoe UI', value: '"Segoe UI", -apple-system, sans-serif' },
  { label: 'Arial', value: 'Arial, Helvetica, sans-serif' },
  { label: 'Calibri', value: 'Calibri, Candara, sans-serif' },
  { label: 'Cambria', value: 'Cambria, serif' },
  { label: 'Times New Roman', value: '"Times New Roman", Times, serif' },
  { label: 'Verdana', value: 'Verdana, sans-serif' },
  { label: 'Courier New', value: '"Courier New", Courier, monospace' },
];

const FONT_SIZES = [8, 9, 10, 10.5, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72];

const FORMAT_BLOCKS = [
  { label: 'Paragraph', value: 'p' },
  { label: 'Heading 1', value: 'h1' },
  { label: 'Heading 2', value: 'h2' },
  { label: 'Heading 3', value: 'h3' },
];

// Curated text-color swatches: theme ink/accent/semantic tokens, resolved
// to their literal computed color at click time (never stored as raw
// var(...) strings, so exported HTML/.docx stay portable).
const TEXT_COLOR_SWATCHES = [
  '--ink-900', '--ink-700', '--ink-600', '--ink-400', '--ink-200',
  '--accent-700', '--accent-600', '--accent-500', '--danger', '--success',
];

// Curated highlight swatches: three theme "soft" tokens plus a small set
// of standard highlighter tones (deliberate content-level exception —
// these mark document text, not app chrome, so they don't need to trace
// back to theme.css the way UI colors must).
const HIGHLIGHT_SWATCHES = [
  { color: '#fef08a', label: 'Yellow' },
  { color: '#bbf7d0', label: 'Green' },
  { color: '#bfdbfe', label: 'Blue' },
  { color: '#fbcfe8', label: 'Pink' },
  { color: '#fed7aa', label: 'Orange' },
  { color: '#e9d5ff', label: 'Purple' },
  { color: '#e5e7eb', label: 'Gray' },
  { varName: '--accent-soft', label: 'Accent' },
  { varName: '--success-soft', label: 'Success' },
  { varName: '--danger-soft', label: 'Danger' },
];

let activeButtons = {};

export function initToolbar() {
  const toolbar = document.getElementById('toolbar');
  toolbar.innerHTML = '';

  toolbar.appendChild(group([
    iconButton('undo', 'Undo (Ctrl+Z)', () => exec('undo')),
    iconButton('redo', 'Redo (Ctrl+Y)', () => exec('redo')),
  ]));
  toolbar.appendChild(sep());

  toolbar.appendChild(group([
    fontFamilySelect(),
    fontSizeSelect(),
  ]));
  toolbar.appendChild(sep());

  toolbar.appendChild(group([
    formatBlockSelect(),
  ]));
  toolbar.appendChild(sep());

  toolbar.appendChild(group([
    iconButton('bold', 'Bold (Ctrl+B)', () => exec('bold'), 'bold'),
    iconButton('italic', 'Italic (Ctrl+I)', () => exec('italic'), 'italic'),
    iconButton('underline', 'Underline (Ctrl+U)', () => exec('underline'), 'underline'),
    iconButton('strikethrough', 'Strikethrough', () => exec('strikeThrough'), 'strikeThrough'),
  ]));
  toolbar.appendChild(sep());

  toolbar.appendChild(group([
    colorSwatchButton('palette', 'Text color', 'color', TEXT_COLOR_SWATCHES),
    colorSwatchButton('highlighter', 'Highlight color', 'backgroundColor', HIGHLIGHT_SWATCHES),
  ]));
  toolbar.appendChild(sep());

  toolbar.appendChild(group([
    iconButton('align-left', 'Align left', () => exec('justifyLeft'), 'justifyLeft'),
    iconButton('align-center', 'Align center', () => exec('justifyCenter'), 'justifyCenter'),
    iconButton('align-right', 'Align right', () => exec('justifyRight'), 'justifyRight'),
    iconButton('align-justify', 'Justify', () => exec('justifyFull'), 'justifyFull'),
  ]));
  toolbar.appendChild(sep());

  toolbar.appendChild(group([
    iconButton('list', 'Bulleted list', () => exec('insertUnorderedList'), 'insertUnorderedList'),
    iconButton('list-ordered', 'Numbered list', () => exec('insertOrderedList'), 'insertOrderedList'),
    iconButton('outdent', 'Decrease indent', () => exec('outdent')),
    iconButton('indent', 'Increase indent', () => exec('indent')),
  ]));
  toolbar.appendChild(sep());

  toolbar.appendChild(group([
    iconButton('link', 'Insert link', insertLink),
    iconButton('image', 'Insert image', insertImage),
    iconButton('table', 'Insert table', insertTable),
    iconButton('minus', 'Insert horizontal rule', () => exec('insertHorizontalRule')),
  ]));

  document.addEventListener('selectionchange', refreshActiveStates);
  refreshActiveStates();
}

function group(children) {
  const g = document.createElement('div');
  g.className = 'toolbar__group';
  for (const child of children) g.appendChild(child);
  return g;
}

function sep() {
  const s = document.createElement('div');
  s.className = 'toolbar__sep';
  return s;
}

function iconButton(iconName, tooltip, onClick, stateKey) {
  const btn = document.createElement('button');
  btn.className = 'btn-icon';
  btn.dataset.tooltip = tooltip;
  btn.innerHTML = window.lumen.icons[iconName] || '';
  btn.addEventListener('mousedown', (e) => e.preventDefault());
  btn.addEventListener('click', onClick);
  if (stateKey) {
    if (!activeButtons[stateKey]) activeButtons[stateKey] = [];
    activeButtons[stateKey].push(btn);
  }
  return btn;
}

function fontFamilySelect() {
  const select = document.createElement('select');
  select.className = 'select select--font-family';
  select.dataset.tooltip = 'Font family';
  for (const f of FONT_FAMILIES) {
    const opt = document.createElement('option');
    opt.value = f.value;
    opt.textContent = f.label;
    select.appendChild(opt);
  }
  select.addEventListener('mousedown', () => restoreSelection());
  select.addEventListener('change', () => {
    wrapSelection('fontFamily', select.value);
    focusPage();
  });
  return select;
}

function fontSizeSelect() {
  const select = document.createElement('select');
  select.className = 'select select--font-size';
  select.dataset.tooltip = 'Font size';
  for (const size of FONT_SIZES) {
    const opt = document.createElement('option');
    opt.value = `${size}pt`;
    opt.textContent = String(size);
    if (size === 12) opt.selected = true;
    select.appendChild(opt);
  }
  select.addEventListener('mousedown', () => restoreSelection());
  select.addEventListener('change', () => {
    wrapSelection('fontSize', select.value);
    focusPage();
  });
  return select;
}

function formatBlockSelect() {
  const select = document.createElement('select');
  select.className = 'select select--format-block';
  select.dataset.tooltip = 'Paragraph style';
  for (const f of FORMAT_BLOCKS) {
    const opt = document.createElement('option');
    opt.value = f.value;
    opt.textContent = f.label;
    select.appendChild(opt);
  }
  select.addEventListener('mousedown', () => restoreSelection());
  select.addEventListener('change', () => {
    exec('formatBlock', `<${select.value}>`);
  });
  activeButtons.__formatBlockSelect = select;
  return select;
}

function resolveVar(varName) {
  return getComputedStyle(document.documentElement).getPropertyValue(varName).trim();
}

function colorSwatchButton(iconName, tooltip, styleProp, swatches) {
  const wrap = document.createElement('div');
  wrap.className = 'toolbar__group';
  wrap.style.position = 'relative';

  const btn = document.createElement('button');
  btn.className = 'btn-icon btn-swatch';
  btn.dataset.tooltip = tooltip;
  btn.innerHTML = window.lumen.icons[iconName] || '';
  const chip = document.createElement('span');
  chip.className = 'btn-swatch__chip';
  btn.appendChild(chip);
  btn.addEventListener('mousedown', (e) => e.preventDefault());

  const popover = document.createElement('div');
  popover.className = 'swatch-popover';
  popover.hidden = true;

  const grid = document.createElement('div');
  grid.className = 'swatch-grid';
  for (const s of swatches) {
    const sw = document.createElement('button');
    sw.type = 'button';
    sw.className = 'swatch';
    sw.addEventListener('mousedown', (e) => e.preventDefault());
    let literalColor;
    let label;
    if (typeof s === 'string') {
      literalColor = resolveVar(s);
      label = s.replace(/^--/, '');
    } else if (s.varName) {
      literalColor = resolveVar(s.varName);
      label = s.label;
    } else {
      literalColor = s.color;
      label = s.label;
    }
    sw.style.background = literalColor || (s.color || '#000000');
    sw.dataset.tooltip = label;
    sw.addEventListener('click', () => {
      const finalColor = typeof s === 'string' ? resolveVar(s) : s.varName ? resolveVar(s.varName) : s.color;
      wrapSelection(styleProp, finalColor);
      chip.style.background = finalColor;
      popover.hidden = true;
      focusPage();
    });
    grid.appendChild(sw);
  }
  popover.appendChild(grid);

  const custom = document.createElement('div');
  custom.className = 'swatch-popover__custom';
  const customLabel = document.createElement('span');
  customLabel.textContent = 'Custom';
  const customInput = document.createElement('input');
  customInput.type = 'color';
  customInput.value = '#000000';
  customInput.addEventListener('mousedown', () => restoreSelection());
  customInput.addEventListener('input', () => {
    wrapSelection(styleProp, customInput.value);
    chip.style.background = customInput.value;
  });
  custom.appendChild(customLabel);
  custom.appendChild(customInput);
  popover.appendChild(custom);

  btn.addEventListener('click', () => {
    const isOpen = !popover.hidden;
    closeAllPopovers();
    popover.hidden = isOpen;
  });

  wrap.appendChild(btn);
  wrap.appendChild(popover);
  return wrap;
}

function closeAllPopovers() {
  document.querySelectorAll('.swatch-popover').forEach((p) => { p.hidden = true; });
}

document.addEventListener('click', (e) => {
  if (!e.target.closest('.btn-swatch') && !e.target.closest('.swatch-popover')) {
    closeAllPopovers();
  }
});

export async function insertLink() {
  restoreSelection();
  const url = await promptLinkDialog();
  if (!url) return;
  restoreSelection();
  const sel = window.getSelection();
  if (sel && sel.rangeCount > 0 && !sel.isCollapsed) {
    exec('createLink', url);
  } else {
    insertHTML(`<a href="${url.replace(/"/g, '&quot;')}">${url}</a>`);
  }
}

export async function insertImage() {
  const result = await window.lumen.insertImage();
  if (!result || !result.dataUrl) return;
  insertHTML(`<img src="${result.dataUrl}" alt="" />`);
}

export async function insertTable() {
  restoreSelection();
  const dims = await promptTableDialog();
  if (!dims) return;
  restoreSelection();
  let html = '<table><tbody>';
  for (let r = 0; r < dims.rows; r++) {
    html += '<tr>';
    for (let c = 0; c < dims.cols; c++) html += '<td><br></td>';
    html += '</tr>';
  }
  html += '</tbody></table><p><br></p>';
  insertHTML(html);
}

function refreshActiveStates() {
  const state = getSelectionState();
  setActive('bold', state.bold);
  setActive('italic', state.italic);
  setActive('underline', state.underline);
  setActive('strikeThrough', state.strikeThrough);
  setActive('justifyLeft', state.justifyLeft);
  setActive('justifyCenter', state.justifyCenter);
  setActive('justifyRight', state.justifyRight);
  setActive('justifyFull', state.justifyFull);
  setActive('insertUnorderedList', state.insertUnorderedList);
  setActive('insertOrderedList', state.insertOrderedList);

  const select = activeButtons.__formatBlockSelect;
  if (select) {
    const known = FORMAT_BLOCKS.map((f) => f.value);
    select.value = known.includes(state.formatBlock) ? state.formatBlock : 'p';
  }
}

function setActive(key, isActive) {
  const btns = activeButtons[key];
  if (!btns) return;
  for (const btn of btns) btn.classList.toggle('is-active', !!isActive);
}
