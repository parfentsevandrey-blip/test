# Ravenmoor — a walkable dark-fantasy village

A first-person, atmospheric 3D scene built with **Three.js + WebGL**: a decrepit medieval
village at night, lit only by torchlight and a cold moon, with a brooding castle looming to
the north. Walk the torchlit road, wander between the shuttered cottages, pass through the
graveyard, and enter the castle gate.

Everything is **procedural and self-contained** — no external models, textures, or audio.
Three.js is vendored under `vendor/`, so the game runs fully offline.

## Run it

Because the game uses native ES modules, it must be served over HTTP (opening
`index.html` from the filesystem will be blocked by the browser). From this folder:

```bash
# Python (any 3.x)
python3 -m http.server 8000

# …or Node
npx serve -l 8000 .
```

Then open **http://localhost:8000/** and click **Enter the Village**.

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
