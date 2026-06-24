# Кутузовский XII — cinematic WebGL layer (apartment design)

This folder holds the source for the **cinematic enhancement** of the
"apartment" offline page (`2d430556-…XII_offline_8.html`, the version with the
particle backdrop, reveal/hover/depth engines and theme/accent switches).

> Note: this is a **different design** from the building-model concept in
> `../index.html` / `../js/` / `../kutuzovsky-12.html`. The apartment page ships
> as a self-unpacking bundle (gzip+base64 assets in `__bundler/*` script tags);
> these are its decoded + enhanced modules.

## Currently shipped

| Module | Tier | Role |
|---|---|---|
| `engine3d.js` | 4 | **Ambient field, upgraded** — curl-noise flow, cursor attraction, scroll-velocity energy, converge-at-contact, IntersectionObserver pause, no runtime `preserveDrawingBuffer`. (Replaces the original `engine3d.orig.js`.) |
| `glshared.js` | — | **One** shared renderer + procedural PMREM environment, so the page never nears the WebGL-context limit. |
| `plan3d.js` | 5b | Floor-plan **light-table gold scan-sweep** on reveal + hover (raster plans can't be reliably extruded, so we light it instead). |

## Reverted at user request → `disabled/` (kept for reference, not bundled)

These were removed because the motion was unwelcome: the hero was to return to
the original photo, the photo-hover depth-warp felt nauseating, and the material
popover was unwanted.

| Module | Tier | Was |
|---|---|---|
| `disabled/hero3d.js` | 1 | Hero as a WebGL plate (depth parallax, dolly, coalesce intro, god-rays). |
| `disabled/gallery3d.js` | 3 | Hover 2.5D depth parallax on interior photos. |
| `disabled/materials3d.js` | 5a | Live PBR sphere preview + name on swatch hover. |
| `disabled/postfx.js` | 2 | Post-processing (bloom/grade/god-rays) — only fed `hero3d`. |

To re-enable one, move it back into `apartment-src/` and add it (plus `postfx.js`
for the hero) back to `NEW_MODULES` and the `<script>` wiring in `build-apartment.js`.

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
