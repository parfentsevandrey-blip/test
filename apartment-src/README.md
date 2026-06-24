# Кутузовский XII — cinematic WebGL layer (apartment design)

This folder holds the source for the **cinematic enhancement** of the
"apartment" offline page (`2d430556-…XII_offline_8.html`, the version with the
particle backdrop, reveal/hover/depth engines and theme/accent switches).

> Note: this is a **different design** from the building-model concept in
> `../index.html` / `../js/` / `../kutuzovsky-12.html`. The apartment page ships
> as a self-unpacking bundle (gzip+base64 assets in `__bundler/*` script tags);
> these are its decoded + enhanced modules.

## What was added

| Module | Tier | Role |
|---|---|---|
| `engine3d.js` | 4 | **Ambient field, upgraded** — curl-noise flow, cursor attraction, scroll-velocity energy, converge-at-contact, IntersectionObserver pause, no runtime `preserveDrawingBuffer`. (Replaces the original `engine3d.orig.js`.) |
| `postfx.js` | 2 | Self-contained post-processing on r128 core — bloom, radial god-rays, ACES-style grade, split-tone, vignette, film grain, chromatic aberration. No addon files (nothing to version-match). |
| `hero3d.js` | 1 | The hero becomes a live WebGL plate: 2.5D depth parallax of the façade, slow dolly, **coalesce/develop-in intro**, sun + god-rays, graded through `postfx`. Falls back to the photo on any failure. |
| `glshared.js` | — | **One** shared renderer + procedural PMREM environment for every on-demand widget below, so the page never nears the WebGL-context limit. |
| `gallery3d.js` | 3 | Hover gives prominent interior photos real **2.5D depth parallax** (procedural depth). |
| `materials3d.js` | 5a | Hovering an accent swatch floats a live **PBR sphere** (metal / stone / glass / lacquered wood) lit by the env map. |
| `plan3d.js` | 5b | Floor-plan **light-table gold scan-sweep** on reveal + hover (raster plans can't be reliably extruded, so we light it instead). |

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
