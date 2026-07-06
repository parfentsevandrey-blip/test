# Lumen — design system

Shared visual language for **Lumen Write** and **Lumen Sheet**, a two-app
office suite in the spirit of Word and Excel, built for Windows desktop with
a premium, minimalist aesthetic. Read this before touching any UI code in
either app.

## Principles

1. **Quiet chrome, confident content.** Toolbars and menus are low-contrast
   and recede; the document page / spreadsheet canvas is the brightest,
   highest-contrast surface in the window.
2. **One accent, used sparingly.** The muted antique-brass accent
   (`--accent-600`) marks selection, active tool state, and primary actions
   only. It never fills large areas.
3. **Native, not skeuomorphic.** Frameless window with a custom `titleBarOverlay`
   (real Windows caption buttons, tinted to match). No fake traffic lights,
   no drop shadows pretending to be paper — except the one deliberate
   paper-elevation shadow under the document/sheet canvas itself.
4. **Restrained motion.** 120–180ms ease-out transitions on hover/active
   states only. Nothing animates just to prove it can.
5. **System fonts, not web fonts.** `--font-ui` (Segoe UI Variable stack) for
   all chrome; `--font-doc` (Georgia stack) for document body text in Lumen
   Write; `--font-mono` (Cascadia Code stack) for numeric cells in Lumen
   Sheet. No network font loading — the apps must work fully offline.

## Tokens

All colors, spacing, radii, shadows, and type live in
`src/styles/theme.css` (identical copy in both apps — see the root
`shared/theme.css`). Never hardcode a hex value or px spacing in
component CSS; reference the custom property.

- Surfaces: `--surface-0` (canvas white) → `--surface-3` (dividers)
- Ink: `--ink-900` (primary text) → `--ink-200` (faint borders/disabled)
- Accent: `--accent-600` default, `--accent-700` hover/pressed, `--accent-soft`
  for selection/active backgrounds
- Both light and dark themes are defined (`:root` / `:root[data-theme="dark"]`).
  Every app needs a theme toggle (sun/moon icon) that flips
  `document.documentElement.dataset.theme` and persists to
  `localStorage['lumen-theme']`.

## Layout shell (both apps)

```
.app-shell
  .titlebar        40px  — brand mark + app name (left), editable doc title (center), theme toggle (right)
  .menubar         30px  — File / Edit / View / (Format|Insert) / Help, custom dropdown menus
  .toolbar         46px  — icon buttons, grouped with .toolbar__sep dividers
  <main content>   flex:1, scrollable, holds the canvas
  .statusbar       28px  — contextual info (left), zoom slider (right)
```

Use the exact classes already defined in `theme.css` (`.titlebar`,
`.menubar`, `.menu`, `.toolbar`, `.btn-icon`, `.statusbar`, `.dialog*`) rather
than inventing parallel ones.

## Icons

Icons live in `src/assets/icons/*.svg` (sourced from Lucide, stroke-based,
24×24, `stroke="currentColor"`). Load them at startup through the preload
script (`fs.readFileSync` on a fixed manifest, exposed via
`contextBridge` as `window.lumen.icons.<name>` — a map of raw SVG markup
strings) and set `button.innerHTML = icons[name]` so `currentColor` picks up
`.btn-icon`'s `color`. Do not reference icons via `<img src>` (loses
currentColor tinting) and do not fetch them over `fetch()`/XHR at runtime.

## Window chrome (Electron `BrowserWindow`, both apps' `main.js`)

```js
{
  width: 1280, height: 820, minWidth: 900, minHeight: 600,
  backgroundColor: '#faf9f7', // must match --surface-1 (light theme)
  titleBarStyle: 'hidden',
  titleBarOverlay: { color: '#faf9f7', symbolColor: '#1b1a17', height: 40 },
  webPreferences: {
    preload: path.join(__dirname, 'preload.js'),
    contextIsolation: true,
    nodeIntegration: false,
    sandbox: false,
  },
}
```

Call `Menu.setApplicationMenu(null)` — the in-window `.menubar` fully
replaces the native menu. Native `dialog.showOpenDialog` /
`showSaveDialog` are still used for file pickers (invoked via IPC from the
renderer through `preload.js`), since those are expected OS surfaces.

## Motion & feedback patterns (v1.1 addition)

`theme.css` now also defines shared keyframes and components for the
interactive patterns below. Reuse these rather than inventing new ones —
consistency of *feel* matters as much as consistency of color.

- **Toasts** (`.toast-stack` / `.toast` / `.toast--success` / `.toast--error`):
  a small floating notification, bottom-right, above the status bar. Use
  for confirmations that don't need a modal — saved, exported, imported,
  file errors. Auto-dismiss after ~3s (add `.is-leaving` for the exit
  transition, then remove the node). Toasts use fixed dark colors
  (`--scrim-dark`/`--scrim-dark-ink`) that don't flip with the theme —
  they're a floating overlay, not part of the surface hierarchy.
- **Dialogs & menus** now animate in (`lumen-pop-in` / `lumen-menu-in` /
  `lumen-scrim-in`, already wired onto `.dialog` / `.dialog-overlay` /
  `.menu`) — nothing extra to do for existing ones.
- **Sidebar** (`.sidebar` / `.sidebar__inner` / `.sidebar__heading` /
  `.sidebar__item`): a collapsible 240px left panel (e.g. Lumen Write's
  document outline). Toggle the `.is-collapsed` class; width/opacity
  transition over `--dur-slow`.
- **Start screen** (`.start-screen` / `.start-screen__grid` / `.start-card`
  / `.start-screen__recent` / `.start-recent-item`): a template gallery +
  recent-files view shown in place of the canvas on a fresh launch (and
  from File ▸ New). Cards lift slightly on hover; clicking one loads a
  starter document/workbook and reveals the normal editor.
- Motion stays restrained (principle 4): every new transition uses
  `--ease-out` and one of the three duration tokens (`--dur-fast` for
  hover/press, `--dur-med` for dialogs/menus/toasts, `--dur-slow` for
  panel-sized things like the sidebar or start screen). No spring/bounce
  easing, no looping/idle animation. `theme.css` already disables all of
  this under `prefers-reduced-motion: reduce` — don't fight that.

## Brand

- Suite name: **Lumen**. App names: **Lumen Write**, **Lumen Sheet**.
- App icons: `build/icon.ico` (already generated, Windows multi-res icon)
  and `src/assets/icon-256.png` (for in-app About dialogs) — already present
  in both app directories, do not regenerate.
- Titlebar brand mark: a small 18×18 rounded-square swatch
  (`.brand-mark` class, already in `theme.css`) with a single glyph —
  `W` (serif) for Write, a 3×3 grid glyph (inline SVG, see
  `shared/brand/lumen-sheet-icon.svg` for the motif) for Sheet — on
  `--accent-600` background.
