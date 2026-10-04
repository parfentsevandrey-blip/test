'use strict';
// Not part of the app (the packager does not take tools/): a probe of the macOS look, run on a real Mac by
// .github/workflows/themesh-mac-probe.yml.
//
// A page screenshot cannot show what makes the Mac window what it is: the system material behind it, the traffic
// lights, the shadow. So this opens the real interface (web-dev/mock-server.mjs serves it with made-up devices)
// in windows made the way the app makes its window (src/window.js, windowLook), in a few variants of the
// options, over a colourful "wallpaper" window, and photographs the screen with the system's own tools.
//
//   PROBE_URL=http://127.0.0.1:8791 PROBE_OUT=./out npx electron tools/mac-probe/main.js
const { app, BrowserWindow, nativeTheme, screen } = require('electron');
const { execFile } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const { windowLook } = require('../../src/window.js');

const URL_BASE = process.env.PROBE_URL || 'http://127.0.0.1:8791';
const OUT = path.resolve(process.env.PROBE_OUT || path.join(__dirname, 'out'));
fs.mkdirSync(OUT, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (...a) => console.log('[probe]', ...a);

function capture(file, rect) {
  return new Promise((resolve) => {
    const args = ['-x'];
    if (rect) args.push('-R', `${Math.round(rect.x)},${Math.round(rect.y)},${Math.round(rect.width)},${Math.round(rect.height)}`);
    args.push(file);
    execFile('screencapture', args, (err) => {
      const size = fs.existsSync(file) ? fs.statSync(file).size : 0;
      log('screencapture', path.basename(file), err ? `FAILED: ${err.message}` : `${size} bytes`);
      resolve(!err && size > 0);
    });
  });
}

// a stand-in for the desktop picture: big soft colour fields, like the default wallpapers
const WALLPAPER = 'data:text/html;charset=utf-8,' + encodeURIComponent(`<!doctype html><meta charset="utf-8"><style>
html,body{margin:0;height:100%;overflow:hidden}
body{background:
  radial-gradient(60vw 55vh at 14% 18%, #ff7a59, transparent 62%),
  radial-gradient(55vw 50vh at 88% 14%, #3f86ff, transparent 62%),
  radial-gradient(62vw 56vh at 82% 92%, #b061ff, transparent 62%),
  radial-gradient(52vw 48vh at 8% 92%, #12d3a0, transparent 62%),
  #1c2a3a}
.b{position:absolute;border-radius:50%;filter:blur(2px)}
</style><div class="b" style="left:46%;top:30%;width:260px;height:260px;background:#ffd166"></div><div class="b" style="left:30%;top:62%;width:180px;height:180px;background:#ffffff"></div>`);

const VARIANTS = [
  { name: 'A-as-the-app-makes-it', extra: {}, themes: ['light', 'dark'] },
  { name: 'B-plus-transparent', extra: { transparent: true }, themes: ['light'] },
  { name: 'C-effect-always-active', extra: { visualEffectState: 'active' }, themes: ['light'] },
  { name: 'D-default-background', extra: { backgroundColor: undefined }, themes: ['light'] },
  { name: 'E-sidebar-material', extra: { vibrancy: 'sidebar' }, themes: ['light'] },
  { name: 'F-no-vibrancy', extra: { vibrancy: undefined, visualEffectState: undefined, backgroundColor: '#f4f2ee' }, themes: ['light'] },
];

async function main() {
  log('electron', process.versions.electron, 'chrome', process.versions.chrome, 'macOS', process.getSystemVersion());
  log('reduce transparency', nativeTheme.prefersReducedTransparency, 'dark', nativeTheme.shouldUseDarkColors);
  const display = screen.getPrimaryDisplay();
  log('display', JSON.stringify({ bounds: display.bounds, scale: display.scaleFactor, workArea: display.workArea }));

  const wallpaper = new BrowserWindow({
    x: display.bounds.x, y: display.bounds.y, width: display.bounds.width, height: display.bounds.height,
    frame: false, hasShadow: false, resizable: false, movable: false, focusable: false, show: false, backgroundColor: '#1c2a3a',
  });
  await wallpaper.loadURL(WALLPAPER);
  wallpaper.showInactive();
  await sleep(800);
  await capture(path.join(OUT, '00-wallpaper-only.png'));

  let shot = 0;
  for (const v of VARIANTS) {
    for (const theme of v.themes) {
      nativeTheme.themeSource = theme;
      const look = windowLook({ platform: 'darwin', env: {}, dark: theme === 'dark', reducedTransparency: false, version: app.getVersion() });
      const options = { ...look.options };
      for (const [k, val] of Object.entries(v.extra)) {
        if (val === undefined) delete options[k];
        else options[k] = val;
      }
      const win = new BrowserWindow({
        width: 1180, height: 780, x: Math.max(40, display.bounds.x + 90), y: display.bounds.y + 70, show: false, title: 'The Mesh probe',
        ...options,
        webPreferences: { partition: `probe-${v.name}-${theme}`, sandbox: true, contextIsolation: true, nodeIntegration: false },
      });
      const ses = win.webContents.session;
      ses.setUserAgent(`${ses.getUserAgent()} ${look.userAgentToken}`);
      try {
        await win.loadURL(`${URL_BASE}/#/home`);
        await win.webContents.executeJavaScript(`localStorage.setItem('themesh.theme', ${JSON.stringify(theme)}); localStorage.setItem('themesh.lang', 'ru');`);
        await win.loadURL(`${URL_BASE}/#/devices`);
        await win.loadURL(`${URL_BASE}/#/home`);
      } catch (e) {
        log('cannot load the interface', e.message);
      }
      win.show();
      win.focus();
      app.focus({ steal: true });
      await sleep(3000);
      const info = await win.webContents.executeJavaScript(`(() => { const r = document.documentElement; const s = document.querySelector('.sidebar'); const cs = s && getComputedStyle(s); return { ua: (/TheMeshDesktop\\S* \\([^)]*\\)/.exec(navigator.userAgent) || [''])[0], skin: r.dataset.skin, vibrancy: r.dataset.vibrancy, titlebar: r.dataset.titlebar, theme: r.dataset.theme, sidebar: cs ? { backdrop: cs.backdropFilter, padTop: cs.paddingTop, bg: cs.backgroundColor } : null, bodyBg: getComputedStyle(document.body).backgroundColor, htmlBg: getComputedStyle(r).backgroundColor }; })()`).catch((e) => ({ error: e.message }));
      log(v.name, theme, JSON.stringify(info));
      const b = win.getBounds();
      const base = `${String(++shot).padStart(2, '0')}-${v.name}-${theme}`;
      await capture(path.join(OUT, `${base}-screen.png`), { x: b.x - 40, y: b.y - 30, width: b.width + 80, height: b.height + 90 });
      try {
        const img = await win.webContents.capturePage();
        fs.writeFileSync(path.join(OUT, `${base}-page.png`), img.toPNG());
      } catch (e) {
        log('capturePage failed', e.message);
      }
      win.destroy();
      await sleep(400);
    }
  }
  await capture(path.join(OUT, '99-whole-screen.png'));
  wallpaper.destroy();
}

app.whenReady().then(main).then(() => app.quit(), (e) => {
  console.error('[probe] failed:', e);
  app.exit(1);
});
app.on('window-all-closed', () => {});
