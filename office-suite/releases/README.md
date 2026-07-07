# Prebuilt binaries

Portable, no-install Windows executables, tracked via Git LFS (see the
root `.gitattributes`). These are built from the source in `../lumen-write`
and `../lumen-sheet` — see each app's own README for what's inside.

- `LumenWrite-portable-1.0.0.exe` — double-click to run, no installation.
- `LumenSheet-portable-1.0.0.exe` — double-click to run, no installation.

To rebuild these yourself instead of trusting a binary download:

```bash
cd lumen-write   # or lumen-sheet
npm install
npm run build:win
```
