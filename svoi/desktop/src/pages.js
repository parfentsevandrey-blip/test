'use strict';
// Script of the two local pages (splash and error). Texts come from the shell; nothing is injected as HTML.
(async () => {
  const shell = window.themeshShell;
  if (!shell) return;
  let info;
  try {
    info = await shell.info();
  } catch {
    return;
  }
  const t = info.texts;
  document.documentElement.lang = info.lang;
  const set = (id, text) => {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
  };
  set('text', t.starting);
  set('title', t.errorTitle);
  set('hint', t.errorHint);
  set('message', info.message || '');
  set('restart', t.restart);
  set('logs', t.logs);
  set('quit', t.closeApp);
  const on = (id, fn) => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('click', fn);
  };
  on('restart', () => shell.restart());
  on('logs', () => shell.showLog());
  on('quit', () => shell.quit());
})();
