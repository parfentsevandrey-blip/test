# Cinematic Weather

A rich, cinematic 3D weather app for Windows. It renders a live, real-time
3D sky — dynamic sun/moon, drifting clouds, rain, snow, fog and lightning —
driven entirely by real weather data from free, open sources (no API key
required).

## Stack

- **Electron** — desktop shell, packaged into a native Windows installer/portable `.exe`
- **Three.js** — the cinematic 3D scene (sky, clouds, precipitation, lightning, terrain, bloom post-processing)
- **React + Zustand** — UI overlay and app state
- **Vite / electron-vite** — build tooling
- **electron-builder** — packaging (NSIS installer + portable exe)
- **[Open-Meteo](https://open-meteo.com)** — free, open, no-API-key weather + geocoding data

## Develop

```bash
npm install
npm run dev
```

## Build a Windows installer/portable exe locally (on Windows)

```bash
npm install
npm run build:win
```

Output lands in `dist/` as `Cinematic Weather-<version>-x64-setup.exe` (NSIS
installer) and `Cinematic Weather-<version>-x64-portable.exe` (no install
needed, just run it).

## Build via CI (no Windows machine needed)

Push to the `claude/3d-weather-app-windows-fj37kb` branch (or run the
workflow manually) and GitHub Actions builds the Windows installer on a
`windows-latest` runner — see `.github/workflows/build-windows.yml` at the
repo root. Download the resulting `.exe` files from the workflow run's
**Artifacts** section.

## How the weather drives the scene

Each Open-Meteo [WMO weather code](https://open-meteo.com/en/docs) is mapped
to a scene condition (`clear`, `partly-cloudy`, `cloudy`, `fog`, `drizzle`,
`rain`, `snow`, `thunderstorm`) in
`src/renderer/src/utils/weatherCondition.ts`. That condition, combined with
real sun position, cloud cover, wind and precipitation intensity, is fed
into `SceneManager` every frame as a single `SceneParams` object that each
visual effect module (sky, stars, clouds, precipitation, lightning, fog,
terrain) reacts to independently.
