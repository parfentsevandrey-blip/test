'use strict';
// The one window of the app: it shows the node's own web interface (the same one a browser
// would show), signed in with a one-time link, and keeps that interface inside the app:
// links to other sites open in the browser, downloads go to the Downloads folder.
const fs = require('node:fs');
const path = require('node:path');
const { BrowserWindow, Menu, Notification, app, clipboard, nativeTheme, screen, session, shell } = require('electron');
const log = require('./log');
const { safeName, uniquePath } = require('./files');

const PARTITION = 'persist:themesh';

function visibleBounds(b) {
  if (!b || !Number.isFinite(b.x) || !Number.isFinite(b.y)) return false;
  return screen.getAllDisplays().some((d) => {
    const a = d.workArea;
    return b.x + 60 < a.x + a.width && b.x + b.width - 60 > a.x && b.y + 30 < a.y + a.height && b.y > a.y - 30;
  });
}

const VIBRANCY = 'under-window';
const TRANSPARENT = '#00000000';

/**
 * How the window looks on this system.
 *
 * On a Mac the interface is drawn in its "Liquid Glass" skin (internal/web/ui/css/glass.css): the window has no
 * title bar (the traffic lights sit inside the interface's sidebar) and is see-through to the system's own
 * blurred material (Electron `vibrancy`), which the interface lets shine through. Elsewhere it is an ordinary
 * window with the classic skin. THEMESH_DESKTOP_GLASS=0 gives a Mac the ordinary window too, and macOS
 * "Reduce transparency" keeps the title bar hidden but drops the see-through material.
 *
 * The interface learns all this from the user agent (see js/boot.js): "TheMeshDesktop/<v> (mac; skin=glass;
 * vibrancy; inset)" - or "reduced-transparency" in place of "vibrancy" when macOS says so, so that the first
 * picture is already the solid one.
 */
function windowLook({ platform = process.platform, env = process.env, dark = false, reducedTransparency = false, version = '0.0.0' } = {}) {
  const mac = platform === 'darwin';
  const glass = mac && env.THEMESH_DESKTOP_GLASS !== '0';
  const vibrant = glass && !reducedTransparency;
  const solid = dark ? '#0d1012' : '#f4f2ee';
  const options = { backgroundColor: solid };
  if (glass) {
    options.titleBarStyle = 'hiddenInset';
    options.trafficLightPosition = { x: 26, y: 22 };
  }
  if (vibrant) {
    options.vibrancy = VIBRANCY;
    options.visualEffectState = 'followWindow';
    options.backgroundColor = TRANSPARENT;
  }
  const facts = [mac ? 'mac' : platform === 'win32' ? 'win' : 'linux'];
  if (glass) facts.push('skin=glass');
  if (vibrant) facts.push('vibrancy');
  else if (glass) facts.push('reduced-transparency');
  if (glass) facts.push('inset');
  return { glass, vibrant, solid, options, userAgentToken: `TheMeshDesktop/${version} (${facts.join('; ')})` };
}

class MainWindow {
  /**
   * @param {object} o
   * @param {object} o.t
   * @param {import('./core').Core} o.core
   * @param {import('./settings').Settings} o.settings
   * @param {string} o.root     the app's directory (for pages and icons)
   * @param {string} o.locale
   */
  constructor(o) {
    Object.assign(this, o);
    this.win = null;
    this.quitting = false;
    this.hintShown = false;
    this.reloginTimes = [];
    this.onFirstHide = null;
    this.onSaved = null;
    this.devTools = !app.isPackaged || !!process.env.THEMESH_DEVTOOLS;
  }

  create({ show }) {
    const saved = this.settings.get('window', null);
    this.look = windowLook({ dark: nativeTheme.shouldUseDarkColors, reducedTransparency: !!nativeTheme.prefersReducedTransparency, version: app.getVersion() });
    this.reduced = !!nativeTheme.prefersReducedTransparency;
    const opts = {
      width: 1120,
      height: 760,
      minWidth: 420,
      minHeight: 600,
      show: false,
      title: 'The Mesh',
      icon: path.join(this.root, 'assets', 'icon.png'),
      autoHideMenuBar: true,
      ...this.look.options,
      webPreferences: {
        preload: path.join(this.root, 'src', 'preload.js'),
        contextIsolation: true,
        sandbox: true,
        nodeIntegration: false,
        partition: PARTITION,
        spellcheck: false,
        devTools: this.devTools,
      },
    };
    if (saved && saved.width >= 420 && saved.height >= 600) {
      opts.width = saved.width;
      opts.height = saved.height;
      if (visibleBounds(saved)) {
        opts.x = saved.x;
        opts.y = saved.y;
      }
    }
    const win = new BrowserWindow(opts);
    this.win = win;
    // Tells the interface what this window is and can do (see windowLook): it picks its look from that.
    const ua = win.webContents.getUserAgent();
    if (!ua.includes('TheMeshDesktop/')) win.webContents.setUserAgent(`${ua} ${this.look.userAgentToken}`);
    if (saved && saved.maximized) win.maximize();
    win.setMenuBarVisibility(false);

    win.once('ready-to-show', () => {
      if (show) win.show();
    });
    win.on('page-title-updated', (e) => e.preventDefault()); // always "The Mesh"
    win.on('close', (e) => {
      this.saveBounds();
      if (this.quitting) return;
      e.preventDefault(); // closing the window keeps the node running: it hides
      win.hide();
      if (!this.hintShown) {
        this.hintShown = true;
        if (this.onFirstHide) this.onFirstHide();
      }
    });
    win.on('closed', () => (this.win = null));

    this.guardNavigation(win);
    this.contextMenu(win);
    this.handleSession(win);
    this.watchAppearance(win);
    return win;
  }

  /**
   * Mac, glass look: keeps the window in step with the interface. The system material takes its light or dark
   * tone from the app's appearance, so the app follows the interface's own Auto / Light / Dark choice (only the
   * page knows it: it is read from the page), and the see-through material goes away when macOS
   * "Reduce transparency" is on (the interface then draws solid panels, see css/glass.css).
   */
  watchAppearance(win) {
    if (!this.look.glass) return;
    const wc = win.webContents;
    const sync = async () => {
      if (win.isDestroyed() || wc.isDestroyed()) return;
      let pref = 'auto';
      try {
        pref = await wc.executeJavaScript("(() => { try { return localStorage.getItem('themesh.theme') || 'auto'; } catch (e) { return 'auto'; } })()");
      } catch {
        /* the page is still loading */
      }
      if (win.isDestroyed()) return;
      const source = pref === 'light' || pref === 'dark' ? pref : 'system';
      if (nativeTheme.themeSource !== source) nativeTheme.themeSource = source;
      const reduced = !!nativeTheme.prefersReducedTransparency;
      if (reduced !== this.reduced) {
        this.reduced = reduced;
        win.setVibrancy(reduced ? null : VIBRANCY);
        win.setBackgroundColor(reduced ? this.look.solid : TRANSPARENT);
      }
      const see = !reduced;
      wc.executeJavaScript(`(() => { const r = document.documentElement; r.toggleAttribute('data-reduce-transparency', ${!see}); if (${see}) r.setAttribute('data-vibrancy', 'on'); else r.removeAttribute('data-vibrancy'); })()`).catch(() => {});
    };
    wc.on('did-finish-load', sync);
    win.on('focus', sync);
    nativeTheme.on('updated', sync);
    const timer = setInterval(() => { if (win.isVisible() && !win.isMinimized()) sync(); }, 2500);
    win.on('closed', () => {
      clearInterval(timer);
      nativeTheme.removeListener('updated', sync);
    });
  }

  saveBounds() {
    const w = this.win;
    if (!w || w.isDestroyed() || w.isMinimized() || w.isFullScreen()) return;
    const maximized = w.isMaximized();
    const b = maximized ? (this.settings.get('window', {}) || {}) : w.getBounds();
    this.settings.set('window', { x: b.x, y: b.y, width: b.width, height: b.height, maximized });
  }

  /** The interface stays in the window; everything else goes to the browser. */
  guardNavigation(win) {
    const sameOrigin = (url) => {
      try {
        return !!this.core.origin && new URL(url).origin === this.core.origin;
      } catch {
        return false;
      }
    };
    win.webContents.on('will-navigate', (e, url) => {
      if (url.startsWith('file://') || sameOrigin(url)) return;
      e.preventDefault();
      this.openExternal(url);
    });
    win.webContents.setWindowOpenHandler(({ url }) => {
      if (sameOrigin(url)) {
        // e.g. the licence texts: a small window of the same app, same sign-in
        return { action: 'allow', overrideBrowserWindowOptions: { width: 760, height: 640, autoHideMenuBar: true, title: 'The Mesh', webPreferences: { partition: PARTITION, sandbox: true, contextIsolation: true } } };
      }
      this.openExternal(url);
      return { action: 'deny' };
    });
  }

  openExternal(url) {
    try {
      const u = new URL(url);
      if (u.protocol === 'http:' || u.protocol === 'https:' || u.protocol === 'mailto:') shell.openExternal(u.toString());
    } catch {
      /* not a link */
    }
  }

  contextMenu(win) {
    win.webContents.on('context-menu', (_e, p) => {
      const t = this.t;
      const items = [];
      if (p.isEditable) {
        items.push({ label: t.undo, role: 'undo', enabled: p.editFlags.canUndo }, { label: t.redo, role: 'redo', enabled: p.editFlags.canRedo }, { type: 'separator' });
        items.push({ label: t.cut, role: 'cut', enabled: p.editFlags.canCut }, { label: t.copy, role: 'copy', enabled: p.editFlags.canCopy }, { label: t.paste, role: 'paste', enabled: p.editFlags.canPaste }, { label: t.selectAll, role: 'selectAll' });
      } else {
        if (p.selectionText) items.push({ label: t.copy, role: 'copy' });
        if (p.linkURL) {
          items.push({ label: t.openLink, click: () => this.openExternal(p.linkURL) }, { label: t.copyLink, click: () => clipboard.writeText(p.linkURL) });
        }
      }
      if (items.length) Menu.buildFromTemplate(items).popup({ window: win });
    });
  }

  /** Downloads, permissions and expired sessions for the window's own session. */
  handleSession(win) {
    const ses = win.webContents.session;
    if (ses.__themeshHandled) return;
    ses.__themeshHandled = true;

    // Windows opened later from this one (the licence texts) are told what they are in too. (A session's user
    // agent does not reach web contents that already exist: the main window is told in create().)
    if (!ses.getUserAgent().includes('TheMeshDesktop/')) ses.setUserAgent(`${ses.getUserAgent()} ${this.look.userAgentToken}`);

    ses.on('will-download', (_e, item) => {
      const dir = app.getPath('downloads');
      const dest = uniquePath(dir, safeName(item.getFilename()));
      item.setSavePath(dest);
      item.once('done', (_ev, state) => {
        if (state !== 'completed') return;
        log.info('downloaded', dest);
        if (this.onSaved) this.onSaved(dest);
      });
    });

    // The interface registers a service worker for browsers that install it as a web app. Inside this
    // window it is useless, and a worker left over from an earlier run stalls the first page load of the
    // next start - so it is never allowed to register (the interface treats that as "not critical").
    ses.webRequest.onBeforeRequest({ urls: ['http://127.0.0.1:*/sw.js*', 'http://localhost:*/sw.js*'] }, (_d, cb) => cb({ cancel: true }));

    // Nothing but copying to the clipboard and full screen is ever needed.
    ses.setPermissionRequestHandler((_wc, permission, cb) => cb(permission === 'clipboard-sanitized-write' || permission === 'fullscreen'));
    ses.setPermissionCheckHandler((_wc, permission) => permission === 'clipboard-sanitized-write' || permission === 'fullscreen');

    // The sign-in lasts 14 days and survives restarts, but if the node ever says "unauthorized"
    // (its session list was reset, say) sign in again instead of showing a dead page.
    ses.webRequest.onCompleted({ urls: ['http://127.0.0.1/*', 'http://127.0.0.1:*/*'] }, (d) => {
      if (d.statusCode === 401 && this.core.origin && d.url.startsWith(this.core.origin + '/api/') && !d.url.includes('/api/handshake')) this.scheduleRelogin();
    });
  }

  scheduleRelogin() {
    clearTimeout(this.reloginTimer);
    this.reloginTimer = setTimeout(async () => {
      const now = Date.now();
      this.reloginTimes = this.reloginTimes.filter((t) => now - t < 60_000);
      if (this.reloginTimes.length >= 3) return log.warn('window: too many sign-ins in a minute, giving up');
      this.reloginTimes.push(now);
      try {
        log.info('window: the session ended, signing in again');
        await this.loadUI();
      } catch (e) {
        log.warn('window: cannot sign in again', e.message);
      }
    }, 800);
  }

  page(name, query = {}) {
    if (!this.win) return Promise.resolve();
    this.pageLoading = this.win.loadFile(path.join(this.root, 'src', name), { query: { lang: this.locale, ...query } }).catch((e) => log.warn('window: cannot load', name, e.message));
    return this.pageLoading;
  }

  showSplash() {
    this.page('splash.html');
  }

  showError() {
    this.page('error.html');
  }

  /** Sign in with a fresh one-time link and show the interface. */
  async loadUI(route = '') {
    for (let attempt = 0; ; attempt++) {
      const url = await this.core.loginURL();
      if (!this.win) return;
      // (a worker left over from an older version of this app could still be there: forget it)
      await this.win.webContents.session.clearStorageData({ storages: ['serviceworkers', 'cachestorage'] }).catch(() => {});
      // The start-up page may still be loading (slow disk, a node that answered at once): Electron fails the
      // new navigation with the old one's "aborted" error, and the window would end on the error page.
      await this.pageLoading;
      try {
        await this.win.loadURL(url);
        break;
      } catch (e) {
        // ERR_ABORTED (-3): another navigation took over; try again with a fresh one-time link
        if (attempt >= 2 || !/ERR_ABORTED|\(-3\)/.test(String((e && e.message) || e))) throw e;
        log.warn('window: the page load was interrupted, trying again');
        await new Promise((r) => setTimeout(r, 200));
      }
    }
    if (route) this.goto(route);
  }

  /** Go to a place inside the interface ("#/chat/<id>") without reloading it. */
  goto(route) {
    if (!this.win || !/^#\/[\w\-./?=&%]*$/.test(route)) return;
    this.win.webContents.executeJavaScript(`location.hash = ${JSON.stringify(route)}`).catch(() => {});
  }

  show(route) {
    const w = this.win;
    if (!w) return;
    if (w.isMinimized()) w.restore();
    w.show();
    w.focus();
    if (route) this.goto(route);
  }

  hide() {
    if (this.win) this.win.hide();
  }

  isActive() {
    const w = this.win;
    return !!w && !w.isDestroyed() && w.isVisible() && !w.isMinimized() && w.isFocused();
  }

  isVisible() {
    return !!this.win && this.win.isVisible();
  }
}

module.exports = { MainWindow, PARTITION, windowLook };
