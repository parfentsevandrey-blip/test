// dialogs.js — the app's own .dialog-overlay/.dialog modal component and a
// handful of concrete dialogs built on top of it. Never uses Electron's
// native dialog.showMessageBox for in-app UI.

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
