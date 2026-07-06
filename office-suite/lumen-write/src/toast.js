// toast.js — small floating notifications (bottom-right, above the status
// bar). Uses the shared .toast-stack/.toast/.toast--success/.toast--error
// components already defined in theme.css (see DESIGN.md's "Motion &
// feedback patterns" section). The stack container is created once and
// appended to the app shell on first use.

let stackEl = null;

const TYPE_ICONS = {
  success: 'check',
  error: 'x',
  info: 'info',
};

function ensureStack() {
  if (stackEl && stackEl.isConnected) return stackEl;
  stackEl = document.createElement('div');
  stackEl.className = 'toast-stack';
  (document.querySelector('.app-shell') || document.body).appendChild(stackEl);
  return stackEl;
}

/**
 * Show a toast notification.
 * @param {string} message
 * @param {{type?: 'success'|'error'|'info'}} [options]
 * @returns {{ dismiss: () => void }}
 */
export function showToast(message, options = {}) {
  const type = options.type === 'success' || options.type === 'error' ? options.type : 'info';
  const iconName = TYPE_ICONS[type] || TYPE_ICONS.info;
  const iconMarkup = (window.lumen && window.lumen.icons && window.lumen.icons[iconName]) || '';

  const el = document.createElement('div');
  el.className = `toast toast--${type}`;
  el.innerHTML = `${iconMarkup}<span class="toast__message"></span>`;
  el.querySelector('.toast__message').textContent = message == null ? '' : String(message);

  ensureStack().appendChild(el);

  let removed = false;
  function remove() {
    if (removed) return;
    removed = true;
    clearTimeout(autoTimer);
    el.classList.add('is-leaving');
    // Fall back to a hard removal in case transitionend never fires
    // (e.g. prefers-reduced-motion collapses the duration to ~0).
    const fallback = setTimeout(() => el.remove(), 250);
    el.addEventListener(
      'transitionend',
      () => {
        clearTimeout(fallback);
        el.remove();
      },
      { once: true }
    );
  }

  const autoTimer = setTimeout(remove, 3000);

  return { dismiss: remove };
}
