# PROMPTFIELD — *type a world into being*

A cinematic, scroll-driven **WebGL** experience whose subject is the thing it's
made of: what you can build on the web today with AI. It's **100% procedural and
fully offline** — no images, no 3D models, no fonts shipped, no CDN. Every star,
letter and photon is math, generated live in the browser from vendored
**Three.js r160**.

> **Just want to see it?** Open **`promptfield.html`** — double-click, no server,
> no internet. It's the entire site (WebGL scene + UI + the whole Three.js
> module graph) inlined into one self-contained file. `index.html` is the
> multi-file source and **must be served over HTTP** (ES-module import maps don't
> load from `file://`), e.g. `python3 -m http.server`.

## The experience

One eased **scroll progress** `s∈[0,1]` (plus smoothed velocity) drives
everything as a single instrument — camera, particle morph, palette temperature
and the post-processing all move together across six acts:

1. **The Prompt** — a breathing sphere of ~120k luminous points in the dark.
2. **The Swarm** — the points advect through a curl-noise flow field; the crowd
   visibly energizes the harder you scroll.
3. **Words Become Shapes** — the swarm pours from chaos into clean geometry
   (sphere → galaxy → torus knot → lattice), scrubbable like a timeline.
4. **The Pipeline** — a twisting cyan/magenta light tunnel of instanced rings;
   chromatic aberration ramps with scroll velocity for a warp-streak feel.
5. **Genesis — the wow** — the swarm slams into a perfectly legible 3D word
   (`GENESIS`), then a thin-film heat wave sweeps the letters and the bloom
   blows them to searing light. **The word is just the opaque pixels of a glyph,
   rasterized to a hidden canvas and sampled into ~point targets — no asset.**
6. **Your Turn** — the points disperse into a born cosmos and hand you a live
   prompt: **type any word and the same engine re-rasterizes and re-forms the
   swarm into it, in real time.** Proof the engine is general, not a canned reel.

A live HUD reads out FPS, particle count and **`assets loaded: 0`** — the quiet
flex underneath the whole thing.

## How it's built

- **One `EffectComposer` chain**, in r160-correct order:
  `RenderPass → UnrealBloomPass → cinematic grade (ShaderPass) → OutputPass → SMAAPass`.
  Bloom runs on the **linear HDR** buffer (so additive points bloom correctly);
  `OutputPass` is the single place tone-mapping + sRGB happen.
- **GPU particle morphing** — one `THREE.Points` + custom `ShaderMaterial`.
  Each point carries five precomputed target positions (`aTarget0..4`) and morphs
  between any two via a `uMorph` uniform with per-particle eased stagger, plus
  curl-noise drift, a breathing heartbeat and screen-space mouse repulsion.
- **Text → particles** — `sampleText()` draws a glyph to an offscreen 2D canvas
  once, then scatters point targets across its opaque pixels (xy from the glyph,
  z noise-extruded). The same function powers the live re-forge.
- **Aurora background** — a full-screen domain-warped fbm/simplex shader (an
  iridescent oil-on-water nebula) whose palette tracks scroll temperature.
- **Cinematic post** — radial chromatic aberration, filmic grain, gentle
  contrast/saturation and a vignette, all velocity- and scroll-reactive.

## Robustness

- **Graceful degrade** — a load-time tier scales the swarm (120k / 80k / 45k) by
  device; a runtime watchdog drops quality if FPS sags.
- **`prefers-reduced-motion`** — freezes idle motion, grain and aberration; the
  scroll-driven content stays fully navigable.
- **No WebGL?** — a try/catch falls back to a CSS-gradient cosmos that still
  carries the type, the message and the prompt input.

## Run it

```bash
# from the project root — any static server works
python3 -m http.server 8000
# then open http://localhost:8000
```

Three.js r160 + its post-processing addons are **vendored** under
`js/vendor/three/`, so the scene works fully offline.

## Single-file build

`promptfield.html` is regenerated from the source with:

```bash
node build-standalone.js
```

Browsers won't load an ES-module graph from `file://`, so the build embeds every
module as text and reconstructs the graph at runtime with **Blob URLs** (each
module's imports rewritten to its dependencies' Blob URLs, in dependency order).
Same-origin Blob URLs import cleanly even from disk.

## Project structure

```
index.html            Markup, copy, import map, section overlays
css/app.css           Design system, layout, choreographed copy, fallbacks
js/scene.js           The WebGL stage: shaders, particles, tunnel, post, choreography
js/ui.js              Scroll instrument, preloader, cursor, HUD, nav, live prompt
js/vendor/three/      Vendored Three.js r160 + post-processing addons
build-standalone.js   Bundles everything into one self-contained HTML file
tools/rendertest.js   Headless render-test harness (console errors + screenshots)
promptfield.html      Self-contained single-file build (downloadable)
archive/              The previous «Кутузовский 12» concept, preserved
```

---

*Built with AI. The medium is the message.*
