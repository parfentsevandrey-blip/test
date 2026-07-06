// toast.js — small floating notifications (bottom-right, above the status
// bar). See ../../DESIGN.md "Motion & feedback patterns" for the shared
// .toast-stack/.toast markup contract. ES module, renderer-only.

let stackEl = null;

function ensureStack() {
  if (stackEl && document.body.contains(stackEl)) return stackEl;
  stackEl = document.createElement('div');
  stackEl.className = 'toast-stack';
  document.body.appendChild(stackEl);
  return stackEl;
}

/**
 * Show a toast notification.
 * @param {string} message
 * @param {{type?: 'success'|'error'}} [opts]
 */
export function showToast(message, opts = {}) {
  const type = opts.type === 'error' ? 'error' : 'success';
  const stack = ensureStack();
  const toast = document.createElement('div');
  toast.className = `toast toast--${type}`;
  const icon = window.lumen.icons[type === 'error' ? 'x' : 'check'] || '';
  const text = document.createElement('span');
  text.textContent = message;
  toast.innerHTML = icon;
  toast.appendChild(text);
  stack.appendChild(toast);

  const AUTO_DISMISS_MS = 3000;
  const timer = setTimeout(() => dismiss(toast), AUTO_DISMISS_MS);

  function dismiss(el) {
    if (!el.isConnected) return;
    clearTimeout(timer);
    el.classList.add('is-leaving');
    el.addEventListener(
      'transitionend',
      () => el.remove(),
      { once: true }
    );
    // Fallback in case transitionend never fires (e.g. reduced-motion).
    setTimeout(() => el.remove(), 220);
  }

  toast.addEventListener('click', () => dismiss(toast));
}
