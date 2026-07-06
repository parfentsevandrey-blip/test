# Lumen — a small, premium office suite

Two native Windows desktop apps, built with Electron, sharing one design
system:

- **Lumen Write** — a minimalist word processor (Word-like: rich text
  formatting, pages, DOCX/PDF/TXT export).
- **Lumen Sheet** — a minimalist spreadsheet (Excel-like: a real formula
  engine, cell formatting, multiple sheets, XLSX/CSV export).

See `DESIGN.md` for the shared visual language (both apps read from the same
`theme.css` design tokens). Each app has its own `README.md` with its
specific feature list, keyboard shortcuts, and known scope limits.

## Repository layout

```
office-suite/
  DESIGN.md              shared design system spec
  shared/
    theme.css             design tokens + primitive component CSS (source of truth)
    brand/                 source SVGs for both app icons
  lumen-write/            Word-like app (Electron project)
  lumen-sheet/            Excel-like app (Electron project)
```

Each app directory is a self-contained Electron + electron-builder project
(its own `package.json`, `main.js`, `preload.js`, `index.html`, `src/`).

## Running in development

```bash
cd lumen-write   # or lumen-sheet
npm install
npm start
```

## Building the Windows .exe

```bash
cd lumen-write   # or lumen-sheet
npm run build:win
```

This produces, under `<app>/dist/`:

- `Lumen Write Setup 1.0.0.exe` (or `Lumen Sheet Setup 1.0.0.exe`) — a real
  NSIS installer (per-user, no admin rights required, with Desktop/Start
  Menu shortcuts).
- `LumenWrite-portable-1.0.0.exe` (or `LumenSheet-portable-1.0.0.exe`) — a
  single portable exe, no installation needed.

`npm run build:win` already sets the two environment variables below for
you (see the `scripts.build:win` line in each `package.json`). They point
Electron's own binary download and electron-builder's NSIS/7zip download at
an npmmirror.com CDN mirror instead of `github.com` — needed in network
environments (like the sandbox this was built in) where `github.com` isn't
reachable for arbitrary binary downloads, but harmless and unnecessary
elsewhere:

```bash
ELECTRON_MIRROR=https://cdn.npmmirror.com/binaries/electron/
ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/
```

`node_modules/` and `dist/` are gitignored — built executables are not
committed to source control.

## Design language, in one paragraph

Quiet, low-contrast chrome (toolbars, menus, title bar) around a bright,
high-contrast document/sheet canvas. One muted antique-brass accent color,
used only for selection, active tool state, and primary actions — never as
a large fill. System fonts only (Segoe UI Variable for UI chrome, Georgia
for document body text, Cascadia Code for tabular numbers) so both apps
work fully offline with no bundled web fonts. A real custom title bar via
Electron's `titleBarOverlay` (native Windows caption buttons, tinted to
match) rather than a fake, skeuomorphic one. Light and dark themes are both
first-class.
