// dialogs.js — the app's own .dialog-overlay/.dialog modal component and a
// handful of concrete dialogs built on top of it. Never uses Electron's
// native dialog.showMessageBox for in-app UI.

import { PAGE_SIZES, MARGIN_PRESETS, MARGIN_MIN_IN, MARGIN_MAX_IN } from './pagination.js';

const root = () => document.getElementById('dialog-root');

/**
 * Open a modal dialog. `render(container)` receives the .dialog element and
 * should populate it; return value is ignored. Returns a `close()` function.
 */
function openDialog(extraClass, render) {
  const overlay = document.createElement('div');
  overlay.className = 'dialog-overlay';

  const box = document.createElement('div');
  box.className = extraClass ? `dialog ${extraClass}` : 'dialog';
  overlay.appendChild(box);

  function close() {
    overlay.remove();
    document.removeEventListener('keydown', onKeydown, true);
  }

  function onKeydown(e) {
    if (e.key === 'Escape') {
      e.preventDefault();
      close();
    }
  }
  document.addEventListener('keydown', onKeydown, true);

  render(box, close);
  root().appendChild(overlay);

  const firstInput = box.querySelector('input, textarea, button');
  if (firstInput) firstInput.focus();

  return close;
}

function buildActions(box, buttons) {
  const actions = document.createElement('div');
  actions.className = 'dialog__actions';
  for (const btn of buttons) {
    const el = document.createElement('button');
    el.className = btn.variant === 'primary' ? 'btn btn--primary' : 'btn';
    el.textContent = btn.label;
    el.addEventListener('click', btn.onClick);
    actions.appendChild(el);
  }
  box.appendChild(actions);
}

/** Simple OK/Cancel confirmation. Resolves true if confirmed. */
export function confirmDialog({ title, message, confirmLabel = 'OK', cancelLabel = 'Cancel' }) {
  return new Promise((resolve) => {
    openDialog('', (box, close) => {
      box.innerHTML = `<h2>${escapeHtml(title)}</h2><div class="dialog__body">${escapeHtml(message)}</div>`;
      buildActions(box, [
        { label: cancelLabel, onClick: () => { close(); resolve(false); } },
        { label: confirmLabel, variant: 'primary', onClick: () => { close(); resolve(true); } },
      ]);
    });
  });
}

/** Three-way "unsaved changes" prompt. Resolves 'save' | 'discard' | 'cancel'. */
export function unsavedChangesDialog(docTitle) {
  return new Promise((resolve) => {
    openDialog('', (box, close) => {
      box.innerHTML = `<h2>Save changes?</h2><div class="dialog__body">"${escapeHtml(docTitle)}" has unsaved changes. Do you want to save them before continuing?</div>`;
      buildActions(box, [
        { label: 'Cancel', onClick: () => { close(); resolve('cancel'); } },
        { label: "Don't Save", onClick: () => { close(); resolve('discard'); } },
        { label: 'Save', variant: 'primary', onClick: () => { close(); resolve('save'); } },
      ]);
    });
  });
}

/** Crash-recovery prompt shown at launch when main.js found an autosave
 * snapshot that's newer than its real document (or an orphaned snapshot
 * for a never-saved document) — see fileio.js's checkForRecovery().
 * Resolves 'recover' | 'discard'. */
export function recoveryDialog(docTitle, timeLabel) {
  return new Promise((resolve) => {
    openDialog('', (box, close) => {
      box.innerHTML = `<h2>Recover unsaved changes?</h2><div class="dialog__body">Lumen Write found unsaved changes to "${escapeHtml(
        docTitle
      )}" from ${escapeHtml(timeLabel)} that weren't saved before the app closed unexpectedly.</div>`;
      buildActions(box, [
        { label: 'Discard', onClick: () => { close(); resolve('discard'); } },
        { label: 'Recover', variant: 'primary', onClick: () => { close(); resolve('recover'); } },
      ]);
    });
  });
}

/** Prompt for a link URL. Resolves the URL string, or null if cancelled. */
export function promptLinkDialog() {
  return new Promise((resolve) => {
    openDialog('', (box, close) => {
      box.innerHTML = `
        <h2>Insert link</h2>
        <div class="field">
          <label for="lw-link-url">URL</label>
          <input type="url" id="lw-link-url" placeholder="https://example.com" />
        </div>`;
      const input = box.querySelector('#lw-link-url');
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') { e.preventDefault(); submit(); }
      });
      function submit() {
        const url = input.value.trim();
        close();
        resolve(url || null);
      }
      buildActions(box, [
        { label: 'Cancel', onClick: () => { close(); resolve(null); } },
        { label: 'Insert', variant: 'primary', onClick: submit },
      ]);
    });
  });
}

/** Prompt for table rows/cols. Resolves {rows, cols} or null. */
export function promptTableDialog() {
  return new Promise((resolve) => {
    openDialog('', (box, close) => {
      box.innerHTML = `
        <h2>Insert table</h2>
        <div class="field-row">
          <div class="field">
            <label for="lw-table-rows">Rows</label>
            <input type="number" id="lw-table-rows" min="1" max="30" value="3" />
          </div>
          <div class="field">
            <label for="lw-table-cols">Columns</label>
            <input type="number" id="lw-table-cols" min="1" max="15" value="3" />
          </div>
        </div>`;
      function submit() {
        const rows = clamp(parseInt(box.querySelector('#lw-table-rows').value, 10) || 3, 1, 30);
        const cols = clamp(parseInt(box.querySelector('#lw-table-cols').value, 10) || 3, 1, 15);
        close();
        resolve({ rows, cols });
      }
      buildActions(box, [
        { label: 'Cancel', onClick: () => { close(); resolve(null); } },
        { label: 'Insert', variant: 'primary', onClick: submit },
      ]);
    });
  });
}

/** File ▸ Page Setup — page size + margins. Resolves
 * {sizeKey, marginKey, marginsIn} (already resolved to concrete inch
 * values, including for a preset) or null if cancelled. `current` is the
 * shape pagination.js's getPageSetup() returns, used to pre-select the
 * dialog's controls. */
export function pageSetupDialog(current) {
  return new Promise((resolve) => {
    openDialog('dialog--page-setup', (box, close) => {
      const sizeOptions = Object.entries(PAGE_SIZES)
        .map(([key, s]) => `<option value="${key}">${escapeHtml(s.label)}</option>`)
        .join('');
      const marginOptions =
        Object.entries(MARGIN_PRESETS)
          .map(([key, m]) => `<option value="${key}">${escapeHtml(m.label)}</option>`)
          .join('') + '<option value="custom">Custom</option>';

      box.innerHTML = `
        <h2>Page Setup</h2>
        <div class="field">
          <label for="lw-page-size">Page size</label>
          <select id="lw-page-size">${sizeOptions}</select>
        </div>
        <div class="field">
          <label for="lw-margin-preset">Margins</label>
          <select id="lw-margin-preset">${marginOptions}</select>
        </div>
        <div id="lw-margin-custom" hidden>
          <div class="field-row">
            <div class="field">
              <label for="lw-margin-top">Top (in)</label>
              <input type="number" id="lw-margin-top" min="${MARGIN_MIN_IN}" max="${MARGIN_MAX_IN}" step="0.05" />
            </div>
            <div class="field">
              <label for="lw-margin-bottom">Bottom (in)</label>
              <input type="number" id="lw-margin-bottom" min="${MARGIN_MIN_IN}" max="${MARGIN_MAX_IN}" step="0.05" />
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <label for="lw-margin-left">Left (in)</label>
              <input type="number" id="lw-margin-left" min="${MARGIN_MIN_IN}" max="${MARGIN_MAX_IN}" step="0.05" />
            </div>
            <div class="field">
              <label for="lw-margin-right">Right (in)</label>
              <input type="number" id="lw-margin-right" min="${MARGIN_MIN_IN}" max="${MARGIN_MAX_IN}" step="0.05" />
            </div>
          </div>
        </div>`;

      const sizeSelect = box.querySelector('#lw-page-size');
      const marginSelect = box.querySelector('#lw-margin-preset');
      const customWrap = box.querySelector('#lw-margin-custom');
      const topInput = box.querySelector('#lw-margin-top');
      const bottomInput = box.querySelector('#lw-margin-bottom');
      const leftInput = box.querySelector('#lw-margin-left');
      const rightInput = box.querySelector('#lw-margin-right');

      sizeSelect.value = current.sizeKey;
      marginSelect.value = current.marginKey;
      topInput.value = current.marginsIn.top;
      bottomInput.value = current.marginsIn.bottom;
      leftInput.value = current.marginsIn.left;
      rightInput.value = current.marginsIn.right;

      function syncCustomVisibility() {
        customWrap.hidden = marginSelect.value !== 'custom';
      }
      syncCustomVisibility();
      marginSelect.addEventListener('change', syncCustomVisibility);

      function submit() {
        const sizeKey = sizeSelect.value;
        const marginKey = marginSelect.value;
        const marginsIn =
          marginKey === 'custom'
            ? {
                top: clamp(parseFloat(topInput.value) || 1, MARGIN_MIN_IN, MARGIN_MAX_IN),
                bottom: clamp(parseFloat(bottomInput.value) || 1, MARGIN_MIN_IN, MARGIN_MAX_IN),
                left: clamp(parseFloat(leftInput.value) || 1, MARGIN_MIN_IN, MARGIN_MAX_IN),
                right: clamp(parseFloat(rightInput.value) || 1, MARGIN_MIN_IN, MARGIN_MAX_IN),
              }
            : { ...MARGIN_PRESETS[marginKey].values };
        close();
        resolve({ sizeKey, marginKey, marginsIn });
      }

      buildActions(box, [
        { label: 'Cancel', onClick: () => { close(); resolve(null); } },
        { label: 'Apply', variant: 'primary', onClick: submit },
      ]);
    });
  });
}

/** Find & Replace — persistent dialog, wired up by findreplace.js. */
export function openFindReplaceDialog(handlers) {
  return openDialog('dialog--find', (box, close) => {
    box.innerHTML = `
      <h2>Find &amp; Replace</h2>
      <div class="field">
        <label for="lw-find-query">Find</label>
        <input type="text" id="lw-find-query" placeholder="Search this document" />
      </div>
      <div class="field">
        <label for="lw-find-replacement">Replace with</label>
        <input type="text" id="lw-find-replacement" placeholder="Replacement text" />
      </div>
      <div class="dialog__hint" id="lw-find-status"></div>
      <div class="dialog__actions">
        <button class="btn" id="lw-find-close">Close</button>
        <button class="btn" id="lw-find-replace-all">Replace All</button>
        <button class="btn" id="lw-find-replace">Replace</button>
        <button class="btn btn--primary" id="lw-find-next">Find Next</button>
      </div>`;

    const queryInput = box.querySelector('#lw-find-query');
    const replInput = box.querySelector('#lw-find-replacement');
    const status = box.querySelector('#lw-find-status');

    box.querySelector('#lw-find-close').addEventListener('click', close);
    box.querySelector('#lw-find-next').addEventListener('click', () => {
      const ok = handlers.findNext(queryInput.value);
      status.textContent = ok ? '' : 'No more matches.';
    });
    box.querySelector('#lw-find-replace').addEventListener('click', () => {
      const ok = handlers.replaceOne(queryInput.value, replInput.value);
      status.textContent = ok ? '' : 'No match selected — try Find Next first.';
    });
    box.querySelector('#lw-find-replace-all').addEventListener('click', () => {
      const count = handlers.replaceAll(queryInput.value, replInput.value);
      status.textContent = `Replaced ${count} occurrence${count === 1 ? '' : 's'}.`;
    });
    queryInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); box.querySelector('#lw-find-next').click(); }
    });
  });
}

/** Simple single-button notice (errors, import warnings). */
export function alertDialog({ title, message }) {
  return new Promise((resolve) => {
    openDialog('', (box, close) => {
      const body = document.createElement('div');
      body.className = 'dialog__body';
      body.style.whiteSpace = 'pre-line';
      body.textContent = message;
      box.innerHTML = `<h2>${escapeHtml(title)}</h2>`;
      box.appendChild(body);
      buildActions(box, [{ label: 'OK', variant: 'primary', onClick: () => { close(); resolve(); } }]);
    });
  });
}

const SHORTCUTS = [
  ['New document', 'Ctrl+N'],
  ['Open…', 'Ctrl+O'],
  ['Save', 'Ctrl+S'],
  ['Save As…', 'Ctrl+Shift+S'],
  ['Print…', 'Ctrl+P'],
  ['Undo', 'Ctrl+Z'],
  ['Redo', 'Ctrl+Y'],
  ['Bold', 'Ctrl+B'],
  ['Italic', 'Ctrl+I'],
  ['Underline', 'Ctrl+U'],
  ['Find & Replace', 'Ctrl+F'],
  ['Cut', 'Ctrl+X'],
  ['Copy', 'Ctrl+C'],
  ['Paste', 'Ctrl+V'],
  ['Zoom in', 'Ctrl+='],
  ['Zoom out', 'Ctrl+-'],
  ['Close dialog / menu', 'Esc'],
];

/** Help ▸ Keyboard Shortcuts — a two-column reference of every shortcut
 * the app supports. */
export function shortcutsDialog() {
  openDialog('dialog--shortcuts', (box, close) => {
    const rows = SHORTCUTS.map(
      ([label, combo]) =>
        `<span class="shortcuts-grid__label">${escapeHtml(label)}</span><span class="shortcuts-grid__combo">${escapeHtml(combo)}</span>`
    ).join('');
    box.innerHTML = `<h2>Keyboard Shortcuts</h2><div class="shortcuts-grid">${rows}</div>`;
    buildActions(box, [{ label: 'Close', variant: 'primary', onClick: close }]);
  });
}

export function aboutDialog() {
  openDialog('dialog--about', (box, close) => {
    box.innerHTML = `
      <span class="brand-mark">W</span>
      <h2>Lumen Write</h2>
      <div class="about-version">Version 1.0.0</div>
      <p>A premium, minimalist word processor for everyday document editing.</p>
      <p>Part of the Lumen office suite.</p>`;
    buildActions(box, [{ label: 'Close', variant: 'primary', onClick: close }]);
  });
}

function clamp(n, min, max) {
  return Math.min(max, Math.max(min, n));
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}
