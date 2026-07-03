# Ravenmoor — a walkable dark-fantasy village

A first-person, atmospheric 3D scene built with **Three.js + WebGL**: a decrepit medieval
village at night, lit only by torchlight and a cold moon, with a brooding castle looming to
the north. Walk the torchlit road, wander between the shuttered cottages, pass through the
graveyard, and enter the castle gate.

Everything is **procedural and self-contained** — no external models, textures, or audio.
Three.js is vendored under `vendor/`, so the game runs fully offline.

## Run it

### Easiest — no terminal

Just open **`Ravenmoor.html`** in any browser (double-click it, or drag it onto a
browser window). It's a single self-contained file with Three.js and every module
bundled in, so it works straight from `file://` — no server, no internet.

### Modular version (for development)

`index.html` loads the game as separate ES modules, which browsers only allow over
HTTP. Serve the folder and open it:

```bash
python3 -m http.server 8000   # then open http://localhost:8000/
# …or: npx serve -l 8000 .
```

Either way, click **Enter the Village** to begin.

> `Ravenmoor.html` is generated from the modular sources. To rebuild it after editing
> anything under `src/`, re-run the bundler (esbuild) that inlines `src/main.js` +
> `vendor/three` into one file.

## Controls

| Key | Action |
| --- | --- |
| `W` `A` `S` `D` / arrows | Walk |
| Mouse | Look (click to capture the pointer) |
| `Shift` | Run |
| `M` | Toggle ambient sound |
| `Esc` | Pause / release the pointer |

## Atmosphere

- **Exponential night fog** + a gradient sky dome, drifting clouds, moon, and ~1600 stars.
- **Torchlight everywhere** — path torches, cottage lanterns, and castle braziers, each with
  layered procedural flicker, driving warm pools of light through the fog.
- **Post-processing**: ACES tone mapping → Unreal-style bloom (so fire and windows glow) →
  vignette, cool shadow-tint, and film grain.
- **Procedural audio** (Web Audio): gusting wind, an ominous low drone, and the occasional
  distant raven or tolling bell.
- **Living particles**: floating embers/fireflies, drifting mist, circling ravens, and
  will-o'-the-wisps out by the graveyard.

## How it's built

| File | Role |
| --- | --- |
| `index.html` | Import map, HUD, title/pause overlays |
| `src/main.js` | Renderer, fog, pointer-lock controls, collision, flicker, post-processing, audio, game loop |
| `src/modules/skybox.js` | Sky dome, moon + moonlight, stars, clouds, horizon silhouette |
| `src/modules/environment.js` | Ground, road, ground-fog, dead woods, boulders, graveyard |
| `src/modules/village.js` | Half-timbered cottages, well, market clutter, lanterns |
| `src/modules/castle.js` | Curtain walls, towers, gatehouse, keep, braziers, banners |
| `src/modules/props.js` | Path torches, embers, mist, ravens, will-o'-the-wisps |

Each module exports a single `build(ctx)` returning `{ group, colliders, flickers, update }`,
which `main.js` mounts uniformly into the scene.
