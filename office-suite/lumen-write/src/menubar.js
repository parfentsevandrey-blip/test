// menubar.js — the fully custom in-page menu bar (File/Edit/View/Insert/
// Format/Help). No native OS menu is used (Menu.setApplicationMenu(null)
// in main.js); these are plain absolutely-positioned .menu dropdowns.
//
// Keyboard support implements the standard ARIA menubar pattern
// (https://www.w3.org/WAI/ARIA/apg/patterns/menubar/): the bar itself uses
// roving tabindex (Tab reaches exactly one top-level item; Left/Right moves
// between top-level items without further Tab presses; typing a letter
// jumps to the next item starting with it); Down/Enter/Space opens a menu
// and moves focus into it; Up/Down move between items inside an open menu;
// Left/Right on an open menu switches to the adjacent top-level menu;
// Escape closes the open menu and returns focus to the triggering
// menubar item; Tab closes the whole menu system and moves focus to
// whatever's next/previous outside it entirely.

import { exec, applyBlockStyle } from './editor.js';
import { insertLink, insertImage, insertTable, LINE_SPACINGS } from './toolbar.js';
import { openFindReplace } from './findreplace.js';
import { zoomBy, zoomReset } from './statusbar.js';
import { aboutDialog, shortcutsDialog, pageSetupDialog, getFocusable } from './dialogs.js';
import { toggleSidebar } from './outline.js';
import { getPageSetup, setPageSetup } from './pagination.js';
import { insertTableOfContents } from './toc.js';
import {
  newDocument,
  openDocument,
  openRecentEntry,
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
        { sep: true },
        ...LINE_SPACINGS.map((s) => ({
          label: `Line Spacing: ${s.label}`,
          action: () => applyBlockStyle('lineHeight', s.value),
        })),
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
  menubar.setAttribute('role', 'menubar');
  menubar.setAttribute('aria-label', 'Application menu');

  const defs = menuDefs();
  const topItems = []; // itemEl per top-level menu, in order
  let openMenu = null; // { itemEl, menuEl, index, items: menuitemEl[] }

  // ---------- Roving tabindex across the top-level bar ----------
  // Exactly one top-level item is ever in the natural tab order at a time,
  // so a single Tab press reaches the bar (landing on the active item —
  // initially File) and a single further Tab press leaves it entirely.
  // Left/Right move the "active" item without any Tab press at all.
  function setActiveTopIndex(index, { focus = true } = {}) {
    topItems.forEach((el, i) => {
      el.tabIndex = i === index ? 0 : -1;
    });
    if (focus) topItems[index].focus();
  }

  function closeMenu({ returnFocus = false } = {}) {
    if (!openMenu) return;
    const { itemEl, menuEl } = openMenu;
    itemEl.classList.remove('is-open');
    itemEl.setAttribute('aria-expanded', 'false');
    menuEl.remove();
    openMenu = null;
    if (returnFocus) itemEl.focus();
  }

  // Moves focus out of the whole menu system entirely (Tab / Shift+Tab),
  // to whatever's next/previous focusable element in the document outside
  // the menubar — the toolbar going forward, the titlebar's theme toggle
  // going backward — reusing dialogs.js's own "what's focusable" query
  // rather than relying on default browser Tab behavior interacting with
  // elements we're about to remove from the DOM (the open dropdown).
  function focusOutsideMenubar(direction) {
    const focusable = getFocusable(document.body).filter((el) => !menubar.contains(el));
    if (!focusable.length) return;
    if (direction === 'forward') {
      const next = focusable.find((el) => menubar.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING);
      (next || focusable[0]).focus();
    } else {
      const prev = [...focusable].reverse().find((el) => menubar.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_PRECEDING);
      (prev || focusable[focusable.length - 1]).focus();
    }
  }

  function exitMenuSystem(direction) {
    closeMenu();
    focusOutsideMenubar(direction);
  }

  // ---------- Building an open dropdown (top-level or "recent" submenu) ----------
  // `getItems()` returns the current list of `.menu__item` element rows
  // that are direct children of `menuEl` (skips separators and, for the
  // top-level dropdown, the nested submenu element itself).
  function getItems(menuEl) {
    return Array.from(menuEl.children).filter(
      (el) => el.classList.contains('menu__item') && el.getAttribute('aria-disabled') !== 'true'
    );
  }

  function focusMenuItem(menuEl, index) {
    const items = getItems(menuEl);
    if (!items.length) return;
    const clamped = ((index % items.length) + items.length) % items.length;
    items[clamped].focus();
  }

  function buildDropdown(items, { onArrowLeft, onArrowRight, topIndex }) {
    const menuEl = document.createElement('div');
    menuEl.className = 'menu';
    menuEl.setAttribute('role', 'menu');

    for (const item of items) {
      if (item.sep) {
        const sepEl = document.createElement('div');
        sepEl.className = 'menu__sep';
        sepEl.setAttribute('role', 'separator');
        menuEl.appendChild(sepEl);
        continue;
      }
      const row = document.createElement('div');
      row.className = 'menu__item';
      row.setAttribute('role', 'menuitem');
      row.tabIndex = -1;
      const label = document.createElement('span');
      label.textContent = item.label;
      row.appendChild(label);

      if (item.submenu === 'recent') {
        row.classList.add('menu__item--has-submenu');
        row.setAttribute('aria-haspopup', 'menu');
        row.setAttribute('aria-expanded', 'false');
        const caret = document.createElement('span');
        caret.className = 'menu__submenu-caret';
        caret.innerHTML = window.lumen.icons['chevron-down'] || '';
        row.appendChild(caret);

        let submenuEl = null;
        const openSubmenu = async ({ focusIndex } = {}) => {
          if (submenuEl) {
            if (typeof focusIndex === 'number') focusMenuItem(submenuEl, focusIndex);
            return;
          }
          submenuEl = document.createElement('div');
          submenuEl.className = 'menu menu--submenu';
          submenuEl.setAttribute('role', 'menu');
          row.appendChild(submenuEl);
          row.setAttribute('aria-expanded', 'true');

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
            empty.setAttribute('role', 'menuitem');
            empty.setAttribute('aria-disabled', 'true');
            empty.textContent = 'No recent files';
            submenuEl.appendChild(empty);
            return;
          }
          for (const entry of recent) {
            const recentRow = document.createElement('div');
            recentRow.className = 'menu__item';
            recentRow.setAttribute('role', 'menuitem');
            recentRow.tabIndex = -1;
            recentRow.textContent = entry.title || entry.path;
            const activate = () => {
              // Return focus to the top-level menubar item (e.g. "File")
              // rather than leaving it nowhere: the recent-file row itself
              // is a transient element that's about to be removed from the
              // DOM, so it can't be the thing a subsequently-opened dialog
              // (e.g. an error toast is fine, but a corrupt-file alertDialog
              // is not) restores focus to when it closes.
              closeMenu({ returnFocus: true });
              openRecentEntry(entry.id);
            };
            recentRow.addEventListener('click', (e) => {
              e.stopPropagation();
              activate();
            });
            recentRow.addEventListener('keydown', (e) => {
              handleSubmenuItemKeydown(e, submenuEl, row, activate);
            });
            submenuEl.appendChild(recentRow);
          }
          if (typeof focusIndex === 'number') focusMenuItem(submenuEl, focusIndex);
        };
        const closeSubmenu = ({ returnFocus = false } = {}) => {
          if (submenuEl) {
            submenuEl.remove();
            submenuEl = null;
            row.setAttribute('aria-expanded', 'false');
          }
          if (returnFocus) row.focus();
        };
        row.addEventListener('mouseenter', () => openSubmenu());
        row.addEventListener('mouseleave', (e) => {
          if (submenuEl && e.relatedTarget && submenuEl.contains(e.relatedTarget)) return;
          closeSubmenu();
        });
        row.addEventListener('click', (e) => {
          e.stopPropagation();
          if (submenuEl) closeSubmenu();
          else openSubmenu({ focusIndex: 0 });
        });
        row.addEventListener('keydown', (e) => {
          if (e.key === 'ArrowRight' || e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            openSubmenu({ focusIndex: 0 });
            return;
          }
          if (e.key === 'ArrowLeft' && submenuEl) {
            e.preventDefault();
            closeSubmenu();
            return;
          }
          handleMenuItemKeydown(e, menuEl, row, { onArrowLeft, onArrowRight, onEnter: () => openSubmenu({ focusIndex: 0 }) });
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
      const activate = () => {
        // Same reasoning as the recent-file row above: many menu actions
        // (Page Setup…, Keyboard Shortcuts, About, an "unsaved changes?"
        // prompt from New/Open) open one of dialogs.js's own dialogs, which
        // restores focus to "whatever had focus when it opened" on close.
        // The menu__item row itself is torn down the instant the menu
        // closes, so it can never be that anchor — the persistent
        // top-level menubar item (e.g. "File") is.
        closeMenu({ returnFocus: true });
        item.action();
      };
      row.addEventListener('click', activate);
      row.addEventListener('keydown', (e) => {
        handleMenuItemKeydown(e, menuEl, row, { onArrowLeft, onArrowRight, onEnter: activate });
      });
      menuEl.appendChild(row);
    }
    return menuEl;
  }

  // Shared Up/Down/Home/End/Escape/Left/Right/typeahead handling for a
  // regular (non-submenu) menu item row.
  function handleMenuItemKeydown(e, menuEl, row, { onArrowLeft, onArrowRight, onEnter }) {
    const items = getItems(menuEl);
    const index = items.indexOf(row);
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        focusMenuItem(menuEl, index + 1);
        break;
      case 'ArrowUp':
        e.preventDefault();
        focusMenuItem(menuEl, index - 1);
        break;
      case 'Home':
        e.preventDefault();
        focusMenuItem(menuEl, 0);
        break;
      case 'End':
        e.preventDefault();
        focusMenuItem(menuEl, items.length - 1);
        break;
      case 'Enter':
      case ' ':
        e.preventDefault();
        onEnter();
        break;
      case 'Escape':
        e.preventDefault();
        closeMenu({ returnFocus: true });
        break;
      case 'ArrowLeft':
        e.preventDefault();
        onArrowLeft();
        break;
      case 'ArrowRight':
        e.preventDefault();
        onArrowRight();
        break;
      case 'Tab':
        e.preventDefault();
        exitMenuSystem(e.shiftKey ? 'backward' : 'forward');
        break;
      default:
        typeaheadWithin(menuEl, e.key);
    }
  }

  function handleSubmenuItemKeydown(e, submenuEl, parentRow, activate) {
    const items = getItems(submenuEl);
    const index = items.indexOf(document.activeElement);
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        focusMenuItem(submenuEl, index + 1);
        break;
      case 'ArrowUp':
        e.preventDefault();
        focusMenuItem(submenuEl, index - 1);
        break;
      case 'Enter':
      case ' ':
        e.preventDefault();
        activate();
        break;
      case 'ArrowLeft':
      case 'Escape':
        e.preventDefault();
        submenuEl.remove();
        parentRow.setAttribute('aria-expanded', 'false');
        parentRow.focus();
        break;
      case 'Tab':
        e.preventDefault();
        exitMenuSystem(e.shiftKey ? 'backward' : 'forward');
        break;
      default:
        break;
    }
  }

  function typeaheadWithin(menuEl, key) {
    if (key.length !== 1 || !/[a-z0-9]/i.test(key)) return;
    const items = getItems(menuEl);
    if (!items.length) return;
    const current = items.indexOf(document.activeElement);
    const ordered = [...items.slice(current + 1), ...items.slice(0, current + 1)];
    const match = ordered.find((el) => el.textContent.trim().toLowerCase().startsWith(key.toLowerCase()));
    if (match) match.focus();
  }

  function openMenuAt(index, { focusIndex = 0 } = {}) {
    const itemEl = topItems[index];
    const wasOpenSameItem = openMenu && openMenu.index === index;
    closeMenu();
    if (wasOpenSameItem) return;

    const menuEl = buildDropdown(defs[index].items, {
      topIndex: index,
      onArrowLeft: () => openMenuAt((index - 1 + topItems.length) % topItems.length, { focusIndex: 0 }),
      onArrowRight: () => openMenuAt((index + 1) % topItems.length, { focusIndex: 0 }),
    });
    itemEl.parentElement.appendChild(menuEl);
    itemEl.classList.add('is-open');
    itemEl.setAttribute('aria-expanded', 'true');
    openMenu = { itemEl, menuEl, index };
    setActiveTopIndex(index, { focus: false });

    const items = getItems(menuEl);
    if (items.length) {
      focusMenuItem(menuEl, focusIndex === 'last' ? items.length - 1 : focusIndex);
    }
  }

  defs.forEach((menuDef, index) => {
    const container = document.createElement('div');
    container.className = 'menubar__container';

    const itemEl = document.createElement('div');
    itemEl.className = 'menubar__item';
    itemEl.textContent = menuDef.label;
    itemEl.setAttribute('role', 'menuitem');
    itemEl.setAttribute('aria-haspopup', 'menu');
    itemEl.setAttribute('aria-expanded', 'false');
    itemEl.tabIndex = -1;
    container.appendChild(itemEl);
    topItems.push(itemEl);

    itemEl.addEventListener('click', (e) => {
      e.stopPropagation();
      openMenuAt(index, { focusIndex: 0 });
    });

    itemEl.addEventListener('focus', () => setActiveTopIndex(index, { focus: false }));

    itemEl.addEventListener('keydown', (e) => {
      switch (e.key) {
        case 'ArrowRight': {
          e.preventDefault();
          const next = (index + 1) % topItems.length;
          if (openMenu) openMenuAt(next, { focusIndex: 0 });
          else setActiveTopIndex(next);
          break;
        }
        case 'ArrowLeft': {
          e.preventDefault();
          const prev = (index - 1 + topItems.length) % topItems.length;
          if (openMenu) openMenuAt(prev, { focusIndex: 0 });
          else setActiveTopIndex(prev);
          break;
        }
        case 'ArrowDown':
        case 'Enter':
        case ' ':
          e.preventDefault();
          openMenuAt(index, { focusIndex: 0 });
          break;
        case 'ArrowUp':
          e.preventDefault();
          openMenuAt(index, { focusIndex: 'last' });
          break;
        case 'Escape':
          if (openMenu) {
            e.preventDefault();
            closeMenu({ returnFocus: true });
          }
          break;
        case 'Tab':
          if (openMenu) {
            e.preventDefault();
            exitMenuSystem(e.shiftKey ? 'backward' : 'forward');
          }
          // else: no menu open, let Tab leave the bar normally (roving
          // tabindex already means only this one item was reachable).
          break;
        default: {
          if (e.key.length === 1 && /[a-z0-9]/i.test(e.key)) {
            e.preventDefault();
            const ordered = [...topItems.slice(index + 1), ...topItems.slice(0, index + 1)];
            const match = ordered.find((el) => el.textContent.trim().toLowerCase().startsWith(e.key.toLowerCase()));
            if (match) setActiveTopIndex(topItems.indexOf(match));
          }
        }
      }
    });

    menubar.appendChild(container);
  });

  // Exactly one top-level item starts in the tab order.
  setActiveTopIndex(0, { focus: false });

  document.addEventListener('click', () => closeMenu());
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && openMenu) closeMenu({ returnFocus: true });
  });
}
