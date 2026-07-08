// theme.js — light/dark theme toggle, persisted to localStorage.

const STORAGE_KEY = 'lumen-theme';

export function initTheme() {
  const button = document.getElementById('theme-toggle');
  const icons = window.lumen.icons;

  function apply(theme) {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(STORAGE_KEY, theme);
    button.innerHTML = theme === 'dark' ? icons.sun : icons.moon;
    // Keep the accessible name in lockstep with the hover tooltip so they
    // can't drift — both describe the action the click performs (switch TO
    // the other theme), not the icon currently shown.
    const label = theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme';
    button.setAttribute('data-tooltip', label);
    button.setAttribute('aria-label', label);
  }

  const stored = localStorage.getItem(STORAGE_KEY);
  const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  apply(stored || (prefersDark ? 'dark' : 'light'));

  button.addEventListener('click', () => {
    const current = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
    apply(current === 'dark' ? 'light' : 'dark');
  });
}

export function toggleTheme() {
  const current = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
  document.getElementById('theme-toggle').click();
  return current;
}
