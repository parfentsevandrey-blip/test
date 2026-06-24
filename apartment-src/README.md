# Кутузовский XII — cinematic WebGL layer (apartment design)

This folder holds the source for the **cinematic enhancement** of the
"apartment" offline page (`2d430556-…XII_offline_8.html`, the version with the
particle backdrop, reveal/hover/depth engines and theme/accent switches).

> Note: this is a **different design** from the building-model concept in
> `../index.html` / `../js/` / `../kutuzovsky-12.html`. The apartment page ships
> as a self-unpacking bundle (gzip+base64 assets in `__bundler/*` script tags);
> these are its decoded + enhanced modules.

## Currently shipped

**One** WebGL context (the ambient field) + small dependency-free modules.

| Module | Role |
|---|---|
| `engine3d.js` | **Cinematic atmosphere (v3)** — two layers (fine dust + soft out-of-focus *bokeh*), per-mote warm/cool colour grading, organic curl-noise drift, a slow **automatic "breathing" camera**, only a *whisper* of pointer parallax. Pauses off-screen; DPR-capped. Replaces `engine3d.orig.js`. |
| `intro.js` | **"The open"** — one-time cinematic title card (wordmark focus-pulls in over the dust, holds, dissolves to the page). Overlay lives in the markup and is opaque, with a CSS no-JS dismiss failsafe; any scroll/key/pointer skips it. |
| `reveal-fx.js` | **Cinematic reveals** — section titles *focus-pull* (letter-spacing settle) and framed photos open from a centre *aperture* (clip-path) with a gold light seam. Layered on the existing reveal engine without fighting it (only touches letter-spacing + clip-path); failsafe opens everything after load. |
| `lightstory.js` | **Colour story** — a full-scene soft-light grade eases toward a per-section mood as you scroll (warm→cool→…). Automatic; updates only while converging. |
| `audio.js` | **Generative ambient sound (opt-in)** — header toggle builds a synthesized warm pad + room tone on first click (WebAudio, zero payload); breathes, swells with scroll, re-tints with theme. Off by default, remembered. |
| `polish.js` | Triggers the floor-plan **CSS** light-table sweep on reveal (hover re-plays via CSS). |

Pure-CSS pieces added by the build: the floor-plan sweep, the richer filmic
vignette, the **drifting light shaft** (`.kx-shaft`), the per-section **grade**
(`.kx-grade`) + **reading spot** (`.kx-spot`). All honour `reduced-motion`.

## Calmer by request

Earlier motion was unwelcome, so it was removed and kept in `disabled/` for
reference (not bundled):

| Module | Was |
|---|---|
| `disabled/hero3d.js` | Hero as a WebGL plate (parallax, dolly, coalesce intro, god-rays) → **hero reverted to the original photo**. |
| `disabled/gallery3d.js` | Hover depth-warp on photos → removed (felt nauseating). |
| `disabled/materials3d.js` | PBR sphere + name popover on swatch hover → removed. |
| `disabled/postfx.js` | Post-processing (only fed the hero). |
| `disabled/glshared.js` | Shared renderer + PMREM env → no longer needed (plan sweep is CSS). |
| `disabled/plan3d.js` | WebGL plan sweep → replaced by the lighter CSS version. |

## Dead code removed

`canvas.ascii` (old text-video leftover), the unused `.statement` section styles,
and the unreferenced `--ease-grace` / `--scrim` CSS custom properties.

`page.html` is the decoded template (markup + CSS). `site.js` / `depth3d.js` are
the original, unchanged engines (kept for reference).

## Design rules honoured

- **Graceful degradation** — every effect is feature-detected and wrapped; on no-WebGL,
  shader-compile failure, or any throw, it reverts to the original (already-excellent) site.
- `prefers-reduced-motion` respected throughout; heavy widgets are fine-pointer only.
- All loops are frame-rate-independent and pause when hidden / off-screen.

## Build

```bash
node ../build-apartment.js [originalBundle.html] [out.html]
# default out → ../kutuzovsky-xii-cinematic.html
node ../validate-apartment.js     # full unpack simulation + structure checks
node ../smoke-test-modules.js     # runs the module JS against a mocked THREE/DOM
```

The build keeps the original bundle's images/fonts/three.js verbatim, swaps in the
enhanced `engine3d.js`, adds the new modules as gzipped assets, edits the template
(CSS + `<script>` wiring + cleanup), and re-emits the self-unpacking HTML.

> Not visually QA'd in CI: this sandbox has no GPU/WebGL, so GLSL is reviewed but
> not compile-tested here. Open `kutuzovsky-xii-cinematic.html` in a browser to see it.
