// menubar.js — the fully custom in-page menu bar (File/Edit/View/Insert/
// Format/Help). No native OS menu is used (Menu.setApplicationMenu(null)
// in main.js); these are plain absolutely-positioned .menu dropdowns.

import { exec } from './editor.js';
import { insertLink, insertImage, insertTable } from './toolbar.js';
import { openFindReplace } from './findreplace.js';
import { zoomBy, zoomReset } from './statusbar.js';
import { aboutDialog, shortcutsDialog, pageSetupDialog } from './dialogs.js';
import { toggleSidebar } from './outline.js';
import { getPageSetup, setPageSetup } from './pagination.js';
import { insertTableOfContents } from './toc.js';
import {
  newDocument,
  openDocument,
  openDocumentAtPath,
  saveDocument,
  saveDocumentAs,
  exportPdf,
  exportDocx,
  exportMarkdown,
  exportTxt,
  printDocument,
} from './fileio.js';

async function openPageSetup() {
  const result = await pageSetupDialog(getPageSetup());
  if (result) setPageSetup(result);
}

function menuDefs() {
  return [
    {
      label: 'File',
      items: [
        { label: 'New', shortcut: 'Ctrl+N', action: newDocument },
        { label: 'Open…', shortcut: 'Ctrl+O', action: openDocument },
        { label: 'Open Recent', submenu: 'recent' },
        { label: 'Save', shortcut: 'Ctrl+S', action: saveDocument },
        { label: 'Save As…', shortcut: 'Ctrl+Shift+S', action: saveDocumentAs },
        { sep: true },
        { label: 'Page Setup…', action: openPageSetup },
        { sep: true },
        { label: 'Export as PDF…', action: exportPdf },
        { label: 'Export as Word (.docx)…', action: exportDocx },
        { label: 'Export as Markdown (.md)…', action: exportMarkdown },
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
        { label: 'Toggle Outline', action: toggleSidebar },
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
        { sep: true },
        { label: 'Table of Contents', action: insertTableOfContents },
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
        { label: 'Quote', action: () => exec('formatBlock', '<blockquote>') },
        { label: 'Code', action: () => exec('formatBlock', '<pre>') },
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
      items: [
        { label: 'Keyboard Shortcuts', action: shortcutsDialog },
        { label: 'About Lumen Write', action: aboutDialog },
      ],
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

        if (item.submenu === 'recent') {
          row.classList.add('menu__item--has-submenu');
          const caret = document.createElement('span');
          caret.className = 'menu__submenu-caret';
          caret.innerHTML = window.lumen.icons['chevron-down'] || '';
          row.appendChild(caret);

          let submenuEl = null;
          const openSubmenu = async () => {
            if (submenuEl) return;
            submenuEl = document.createElement('div');
            submenuEl.className = 'menu menu--submenu';
            row.appendChild(submenuEl);

            let recent = [];
            try {
              recent = (await window.lumen.getRecentFiles()) || [];
            } catch (err) {
              recent = [];
            }
            // The row's mouseleave handler (or a whole-menu close) may have
            // already torn this submenu down while we were awaiting IPC.
            if (!submenuEl || !submenuEl.isConnected) return;

            if (!recent.length) {
              const empty = document.createElement('div');
              empty.className = 'menu__item';
              empty.setAttribute('aria-disabled', 'true');
              empty.textContent = 'No recent files';
              submenuEl.appendChild(empty);
              return;
            }
            for (const entry of recent) {
              const recentRow = document.createElement('div');
              recentRow.className = 'menu__item';
              recentRow.textContent = entry.title || entry.path;
              recentRow.addEventListener('click', (e) => {
                e.stopPropagation();
                closeMenu();
                openDocumentAtPath(entry.path);
              });
              submenuEl.appendChild(recentRow);
            }
          };
          const closeSubmenu = () => {
            if (submenuEl) {
              submenuEl.remove();
              submenuEl = null;
            }
          };
          row.addEventListener('mouseenter', openSubmenu);
          row.addEventListener('mouseleave', (e) => {
            if (submenuEl && e.relatedTarget && submenuEl.contains(e.relatedTarget)) return;
            closeSubmenu();
          });
          menuEl.appendChild(row);
          continue;
        }

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
