# ZENITH · Sky Residence — Moscow-City

A premium, single-page **3D landing site** for the sale of a luxury penthouse
in Moscow-City. A full-screen, post-processed **Three.js** cityscape is the
stage for the whole page — no external 3D assets, images, or build step
required.

![Concept landing page for the ZENITH sky residence](docs/preview.svg)

## Highlights

- **Cinematic, full-screen WebGL stage** — a fixed, full-viewport Moscow-City
  at night sits behind the entire page. Content floats over the living scene on
  translucent glass, dimmed just enough to stay legible.
- **Bloom post-processing** — an `EffectComposer` pipeline (RenderPass →
  `UnrealBloomPass` → `OutputPass`) makes every lit window, beacon and the gold
  crown glow. This is what turns the scene from "nice" into cinematic.
- **Scripted camera** — a timed intro fly-in on load, then a scroll-driven
  camera journey that orbits and rises around the tower, with idle drift and
  mouse parallax.
- **A living city** — a procedural glass tower, a surrounding skyline, blinking
  aircraft beacons, sweeping searchlights, long-exposure traffic light-trails,
  planar reflections (a mirrored city under a glass floor, so the reflection
  blooms too), drifting clouds, stars and a hazy moon.
- **Premium art direction** — dark editorial palette with bronze/gold accents,
  Cormorant Garamond + Manrope typography, film-grain, custom cursor, a
  cinematic scroll-rail, preloader, scroll reveals, animated counters and a
  working (front-end only) enquiry form.
- **Robust & accessible** — graceful CSS gradient fallback if WebGL is
  unavailable, full `prefers-reduced-motion` support (intro/parallax disabled),
  per-device quality scaling, keyboard-friendly forms, responsive to mobile.

## Run it

It's a static site. Because it uses ES modules + an import map, serve it over
HTTP rather than opening the file directly:

```bash
# from the project root
python3 -m http.server 8000
# then open http://localhost:8000
```

Any static server works (`npx serve`, VS Code Live Server, etc.).

> Three.js (r160) and its post-processing addons are **vendored locally** under
> `js/vendor/three/`, so the scene works fully offline — no CDN. If WebGL is
> unavailable for any reason, the page degrades gracefully to a styled gradient
> sky and everything else still works. (Google Fonts are still loaded from a CDN
> and fall back to system serif/sans if blocked.)

## Single-file download

`zenith-residence.html` is a **fully self-contained build** — CSS, the UI
script, and the entire Three.js module graph (core + post-processing addons +
the scene) are inlined into one file. You can download it and **open it
directly by double-clicking** (no web server, no internet); the full cinematic
scene still renders. It's regenerated from the source files with:

```bash
node build-standalone.js
```

The 3D code is an ES-module graph, which browsers refuse to load from `file://`.
The build sidesteps this by embedding every module as text and reconstructing
the graph at runtime with **Blob URLs**: each module's import specifiers are
rewritten to the Blob URLs of its dependencies, created in dependency order.
Blob URLs are same-origin, so the graph imports cleanly even from disk.

## Structure

```
index.html             Markup & content (all copy lives here)
css/styles.css         Design system, layout, animations, responsive rules
js/scene.js            Cinematic WebGL scene (city, bloom, camera, effects)
js/main.js             UI: preloader, reveals, counters, nav, veil, cursor, form
js/vendor/three/       Vendored Three.js r160 + post-processing addons
build-standalone.js    Bundles the module graph into the single-file build
zenith-residence.html  Self-contained single-file build (downloadable)
docs/preview.svg       Static preview used in this README
```

## Customising the listing

All listing details are plain HTML in `index.html` — price, area, floor,
amenities, floor-plan specs, location distances, and contact details. Brand
colours and fonts are CSS custom properties at the top of `css/styles.css`
(`--gold`, `--bg`, `--serif`, …). The tower's proportions, window density, and
skyline are parameters inside the builder functions in `js/scene.js`.

---

*Conceptual presentation. Imagery, pricing, and contact details are
illustrative.*
