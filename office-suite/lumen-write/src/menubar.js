// menubar.js — the fully custom in-page menu bar (File/Edit/View/Insert/
// Format/Help). No native OS menu is used (Menu.setApplicationMenu(null)
// in main.js); these are plain absolutely-positioned .menu dropdowns.

import { exec } from './editor.js';
import { insertLink, insertImage, insertTable } from './toolbar.js';
import { openFindReplace } from './findreplace.js';
import { zoomBy, zoomReset } from './statusbar.js';
import { aboutDialog } from './dialogs.js';
import {
  newDocument,
  openDocument,
  saveDocument,
  saveDocumentAs,
  exportPdf,
  exportDocx,
  exportTxt,
  printDocument,
} from './fileio.js';

function menuDefs() {
  return [
    {
      label: 'File',
      items: [
        { label: 'New', shortcut: 'Ctrl+N', action: newDocument },
        { label: 'Open…', shortcut: 'Ctrl+O', action: openDocument },
        { label: 'Save', shortcut: 'Ctrl+S', action: saveDocument },
        { label: 'Save As…', shortcut: 'Ctrl+Shift+S', action: saveDocumentAs },
        { sep: true },
        { label: 'Export as PDF…', action: exportPdf },
        { label: 'Export as Word (.docx)…', action: exportDocx },
        { label: 'Export as Plain Text (.txt)…', action: exportTxt },
        { sep: true },
        { label: 'Print…', shortcut: 'Ctrl+P', action: printDocument },
        { sep: true },
        { label: 'Exit', action: () => window.close() },
      ],
    },
    {
      label: 'Edit',
      items: [
        { label: 'Undo', shortcut: 'Ctrl+Z', action: () => exec('undo') },
        { label: 'Redo', shortcut: 'Ctrl+Y', action: () => exec('redo') },
        { sep: true },
        { label: 'Cut', shortcut: 'Ctrl+X', action: () => exec('cut') },
        { label: 'Copy', shortcut: 'Ctrl+C', action: () => exec('copy') },
        { label: 'Paste', shortcut: 'Ctrl+V', action: () => exec('paste') },
        { sep: true },
        { label: 'Find & Replace…', shortcut: 'Ctrl+F', action: openFindReplace },
      ],
    },
    {
      label: 'View',
      items: [
        { label: 'Zoom In', shortcut: 'Ctrl+=', action: () => zoomBy(10) },
        { label: 'Zoom Out', shortcut: 'Ctrl+-', action: () => zoomBy(-10) },
        { label: 'Reset Zoom', action: zoomReset },
        { sep: true },
        { label: 'Toggle Theme', action: () => document.getElementById('theme-toggle').click() },
      ],
    },
    {
      label: 'Insert',
      items: [
        { label: 'Image…', action: insertImage },
        { label: 'Link…', action: insertLink },
        { label: 'Table…', action: insertTable },
        { label: 'Horizontal Rule', action: () => exec('insertHorizontalRule') },
      ],
    },
    {
      label: 'Format',
      items: [
        { label: 'Bold', shortcut: 'Ctrl+B', action: () => exec('bold') },
        { label: 'Italic', shortcut: 'Ctrl+I', action: () => exec('italic') },
        { label: 'Underline', shortcut: 'Ctrl+U', action: () => exec('underline') },
        { label: 'Strikethrough', action: () => exec('strikeThrough') },
        { sep: true },
        { label: 'Paragraph', action: () => exec('formatBlock', '<p>') },
        { label: 'Heading 1', action: () => exec('formatBlock', '<h1>') },
        { label: 'Heading 2', action: () => exec('formatBlock', '<h2>') },
        { label: 'Heading 3', action: () => exec('formatBlock', '<h3>') },
        { sep: true },
        { label: 'Align Left', action: () => exec('justifyLeft') },
        { label: 'Align Center', action: () => exec('justifyCenter') },
        { label: 'Align Right', action: () => exec('justifyRight') },
        { label: 'Justify', action: () => exec('justifyFull') },
        { sep: true },
        { label: 'Bulleted List', action: () => exec('insertUnorderedList') },
        { label: 'Numbered List', action: () => exec('insertOrderedList') },
        { label: 'Increase Indent', action: () => exec('indent') },
        { label: 'Decrease Indent', action: () => exec('outdent') },
      ],
    },
    {
      label: 'Help',
      items: [{ label: 'About Lumen Write', action: aboutDialog }],
    },
  ];
}

export function initMenubar() {
  const menubar = document.getElementById('menubar');
  menubar.innerHTML = '';
  let openMenu = null;

  function closeMenu() {
    if (!openMenu) return;
    openMenu.itemEl.classList.remove('is-open');
    openMenu.menuEl.remove();
    openMenu = null;
  }

  for (const menuDef of menuDefs()) {
    const container = document.createElement('div');
    container.className = 'menubar__container';

    const itemEl = document.createElement('div');
    itemEl.className = 'menubar__item';
    itemEl.textContent = menuDef.label;
    container.appendChild(itemEl);

    itemEl.addEventListener('click', (e) => {
      e.stopPropagation();
      const wasOpen = openMenu && openMenu.itemEl === itemEl;
      closeMenu();
      if (wasOpen) return;

      const menuEl = document.createElement('div');
      menuEl.className = 'menu';
      for (const item of menuDef.items) {
        if (item.sep) {
          const sepEl = document.createElement('div');
          sepEl.className = 'menu__sep';
          menuEl.appendChild(sepEl);
          continue;
        }
        const row = document.createElement('div');
        row.className = 'menu__item';
        const label = document.createElement('span');
        label.textContent = item.label;
        row.appendChild(label);
        if (item.shortcut) {
          const sc = document.createElement('span');
          sc.className = 'menu__shortcut';
          sc.textContent = item.shortcut;
          row.appendChild(sc);
        }
        row.addEventListener('click', () => {
          closeMenu();
          item.action();
        });
        menuEl.appendChild(row);
      }
      container.appendChild(menuEl);
      itemEl.classList.add('is-open');
      openMenu = { itemEl, menuEl };
    });

    menubar.appendChild(container);
  }

  document.addEventListener('click', closeMenu);
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeMenu();
  });
}
