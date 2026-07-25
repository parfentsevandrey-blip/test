# Кутузовский 12 — Club House landing (concept)

A premium, single-page site for the real **«Кутузовский 12»** deluxe club house
on Kutuzovsky Prospekt, Moscow (architecture by Tsimailo, Lyashenko & Partners).
The hero is a full-screen **Three.js** reconstruction of the actual building —
an 11-storey limestone palazzo with its signature full-height glass/steel
colonnade banded in brass — rendered at twilight against Moscow-City and the
Moskva River. Copy is in Russian; the gallery uses real photography of the
building.

> **Just want to see it? Open `kutuzovsky-12.html` — double-click, no server, no
> internet.** It's the whole site (3D building + the "video-from-text" section +
> gallery) in one self-contained file. `index.html` is the multi-file source and
> **must be served over HTTP** (ES modules can't load from `file://`), e.g.
> `python3 -m http.server` — opening `index.html` directly will look blank.

![Кутузовский 12 against Moscow-City](img/k12-aerial-sunset.jpg)

> Modelled from reference photos of the completed building. This is a
> conceptual presentation, not affiliated with the developer.

## Highlights

- **Faithful 3D model** — the building is rebuilt procedurally from the real
  design: a beige-limestone mass with floor string-courses, a regular bay
  rhythm of **clustered fluted columns wrapped in brass rings**, dark bronze
  glazing, a setback penthouse, a 9-metre warm-lit lobby, and a granite
  courtyard — set against Moscow-City towers, low Stalin-era neighbours, the
  river and trees.
- **The background IS "running text".** The 3D model is rendered, then a **GPU
  shader turns every cell into a colored character glyph** (by luminance, tinted
  by the cell colour) — dense enough (~7px cells) to read like a *photograph made
  of characters*, at 60fps. There's no `<video>`; the moving backdrop is live 3D
  disguised as text. Real **HDRI image-based lighting** (a dusk sky) drives the
  reflections and a low warm sun the shadows, so the source looks right before it
  becomes glyphs.
- **Cinematic camera** — a timed intro fly-in, then a scroll-driven journey that
  tracks the colonnade, rises to the penthouse and pulls back over the river,
  with idle drift and mouse parallax.
- **Full-bleed + real imagery** — the text background is fixed full-viewport;
  content floats over it on translucent glass with a scroll-driven veil, and the
  gallery shows real photographs of the building.
- **Premium art direction** — dark editorial palette with brass accents,
  Cormorant Garamond + Manrope, custom cursor, scroll-rail, preloader, scroll
  reveals, animated counters and a working (front-end only) viewing-request form.
- **Robust & accessible** — graceful CSS fallback if WebGL is unavailable, full
  `prefers-reduced-motion` support, per-device quality scaling (shadows off on
  small screens), keyboard-friendly forms, responsive to mobile.

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

`kutuzovsky-12.html` is a **fully self-contained build** — CSS, the UI
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
index.html             Markup & content (Russian copy lives here)
css/styles.css         Design system, layout, animations, responsive rules
js/scene.js            WebGL scene: the building model, lighting, camera, bloom
js/main.js             UI: preloader, reveals, counters, nav, veil, cursor, form
js/vendor/three/       Vendored Three.js r160 + post-processing addons
img/                   Real photographs of Кутузовский 12 (gallery)
build-standalone.js    Bundles the module graph + images into one HTML file
kutuzovsky-12.html  Self-contained single-file build (downloadable)
build-text-video.js    Generates the "video-as-text" experiment
text-video.html        Plays footage with no <video>/<img> — pure colored text
docs/preview.svg       Static preview (earlier concept)
```

## Experiment: video without a video player

`text-video.html` (built by `node build-text-video.js`) plays the building
footage with **no `<video>` and no visible `<img>`** — every frame is rebuilt as
colored text characters in a `<pre>`. Real photos are animated (Ken-Burns +
crossfade), sampled to a grid, and each cell becomes a glyph chosen by luminance
and coloured by the pixel. It looks like video; technically it's text you can
select. A small "reveal" toggle enlarges the glyphs to show what it really is.

## Customising the listing

All listing details are plain HTML in `index.html` — price, area, floor,
amenities, floor-plan specs, location distances, and contact details. Brand
colours and fonts are CSS custom properties at the top of `css/styles.css`
(`--gold`, `--bg`, `--serif`, …). The tower's proportions, window density, and
skyline are parameters inside the builder functions in `js/scene.js`.

---

# Уютная комната в небоскрёбе (`cozy-room.html`)

A second, independent scene in this repo: a **cozy penthouse living room on the
47th floor** — floor-to-ceiling glass on two sides, a lit fireplace, and a
rainy night city outside. Same vendored Three.js r160, no external assets.

> **Just want to see it? Open `cozy-room.html` — double-click, no server, no
> internet.** `room.html` is the multi-file source and must be served over HTTP
> (ES modules can't load from `file://`), e.g. `python3 -m http.server`.

## What's in it

- **Rain on the glass.** The world outside is rendered to its own buffer first,
  then the window shader refracts it through a field of sliding droplets and
  runnels. Beads act as little lenses — they concentrate the city behind them,
  pick up a cold rim, and catch the firelight from inside the room. Dry areas
  fog over with condensation; the wet tracks stay clear.
- **A real fire.** A layered domain-warped flame shader over a bed of glowing
  coals and charred logs, with rising embers. Its light drives the room: a
  shadow-casting spot plus omni fill, both modulated by a two-rate flicker, so
  furniture throws shadows that breathe.
- **A city with a plan.** Not a scatter of boxes — a rotated street grid, a
  river with bridges, parks, and two clusters of height, all generated from
  one layout that the buildings, the streets, the traffic, the ground and the
  sky all read from. We are on the low old bank; the towers stand across the
  water, so the view layers into roofs, then streets, then the river, then the
  skyline. Buildings are stacked boxes, which is where the setbacks come from,
  with lit crowns, masts and roof plant on the tall ones and a couple of round
  towers to break the silhouette. Everything is instanced: the whole city is
  three draw calls.
- **Facades that tell you what a building is.** Four archetypes — curtain wall,
  punched masonry, vertical fin, panel slab — each with its own bay width,
  floor height and occupancy. Offices go dark a floor at a time, flats go dark
  a flat at a time, and about a third of the buildings are dark altogether,
  which is what gives a skyline its rhythm. Half-drawn blinds dim a share of
  the lit windows; a few switch over the course of an evening.
- **Traffic, and streets to put it on.** Each stretch of street is one flat
  additive quad whose lamps are a periodic function of arc length, so a 900 m
  avenue costs the same as a 60 m one and there is no such thing as a lamp
  instance. Cars are streaks that walk those same runs entirely in the vertex
  shader — white one way, red the other, smeared by the wet road. Two
  airliners cross on long approaches with red, green, white and a strobe.
- **An overcast sky** with a drifting, shredded cloud deck, a moon behind a
  break in the weather, sodium light-pollution aimed at downtown rather than
  smeared evenly along the horizon, falling rain streaks, drifting mist, and
  occasional lightning (with delayed thunder if sound is on).
- **Ambient occlusion.** A depth-only SSAO pass reconstructs view normals from
  the depth buffer, samples a contact-biased hemisphere with per-pixel rotation,
  and blurs the result depth-aware so occlusion never bleeds across a
  silhouette. It is weighted toward the ambient term, so it deepens crevices and
  contact without muddying anything the fire is directly lighting.
- **A rig that bounces.** The fire's shaped light and moving shadows come from
  a narrow shadow-casting spot; its throw comes from an unshadowed omni beneath
  it, so no cone boundary can be drawn across a wall. Three unshadowed fills
  stand in for light returning off the floor, the ceiling and the back wall —
  without them every surface out of direct throw fell to about 2% luminance and
  its material was invisible. The bounces flicker with the flame.
- **A micro-detail layer.** A shared fine normal is blended over every base
  material with whiteout blending, sampled triplanar in world space and faded
  out by view distance, so surfaces keep structure up close without aliasing
  into shimmer at range.
- **Depth of field.** Nothing marks an image as CG more reliably than being
  sharp from half a metre to infinity. A gather bokeh at half resolution, run
  before bloom so defocused highlights bloom as discs rather than points, with
  a soft-limited circle of confusion — a hard 1/z curve defocuses an interior's
  whole back wall. Focus tracks whatever the camera is looking at.
- **A real environment.** The cube map is captured from inside the room rather
  than painted, so materials reflect *this* room: the ceiling picks up the
  fire, the marble picks up the floor, the brass picks up the wall behind it.
  Re-captured when the lamps change; it is a low-frequency term, so the flame's
  variation is carried by the punctual lights instead.
- **Planar reflections.** Mirror cameras give the oiled-oak floor and the dark
  glass genuine reflections of the room — the signature look of a lit room at
  night. Guarded against feedback: reflective materials switch off while any
  reflection pass renders.
- **Everything is procedural** — every surface is painted into a canvas at
  load; the page ships no textures and no models. Thirteen generators produce
  full PBR sets (albedo + roughness + normal, some with AO or metalness):
  oiled oak with staggered butt joints, cathedral figure, ring-oriented open
  pores and eased plank edges; troweled limewash plaster; honed stone with a
  branching vein network; cut-pile wool; linen, bouclé and hand-knit wool;
  marble, bookbinding cloth, brushed brass; charred log bark and a veined
  leaf. UVs are rescaled to **metres**, so the plaster on a 4 m wall matches
  the plaster on the 0.7 m panel beside it, and patterns run continuously
  across surfaces built from several pieces. About 2.3 s of generation at
  load, split into chunks that yield so the loader keeps animating.
- **You can walk around it.** "Пройтись" (or just pressing `W`) drops you into
  the room at eye height with pointer lock. Collision is a capsule against a
  dozen axis-aligned boxes resolved one axis at a time, so you slide along the
  sofa instead of sticking to it; low obstacles are stepped over. Speed is a
  real 1.25 m/s — game speeds read as a game — with damped acceleration, head
  bob scaled by actual velocity, a little roll into a strafe, and focus that
  follows wherever you are looking.
- **Procedural audio** (off by default): filtered-noise rain, a fire roar with
  scheduled crackles, and low thunder — synthesised with WebAudio, no files.

## Controls

Drag to look, wheel/pinch to zoom, keys `1`–`4` for the camera presets
(Гостиная · У камина · У окна · На диване). The gear panel adjusts fire, rain,
colour warmth, depth of field, resolution, quality and camera drift, and shows
a live fps readout.

**Walking:** `W` `A` `S` `D` (or the arrows) to move — pressing any of them
enters walk mode, as does the "Пройтись" button. Mouse looks, `Shift` moves
faster, `Ctrl` crouches, `Esc` (or a preset key) returns to the orbiting views.

**Quality** and **resolution** are deliberately separate. A quality tier only
toggles which passes run — reflections, bloom levels, rain and tower counts —
so every buffer keeps its size and switching tiers costs no allocation and no
measurable time. Resolution is the one control that does reallocate the render
targets, so it is applied when you release the slider rather than during the
drag. Quality auto-selects on first run and adapts to the measured frame rate,
but the moment you open the settings panel it stops moving on its own.

Every shader is compiled at load, in the last step of the loader, rather than
the first time a light or a pass changes — otherwise the first click on a
button that flips a material define stalls the frame while Three relinks
programs. For the same reason the environment capture no longer clears
`scene.environment` while it renders: doing so toggled `USE_ENVMAP` and
recompiled every material in the room twice per capture.

`node tools/uicheck.js` audits every control: it actuates each one the way a
user would and asserts the state actually changed, and fails the build if a
quality switch costs more than a few ms of main-thread time.
`node tools/walkcheck.js` does the same for walk mode — that `W` moves you,
that walls and furniture stop you, that crouching lowers the eye, and that
`Esc` hands the camera back to the orbit rig.

`node tools/skyline.js` hides the room and the glass and photographs the world
on its own from six directions, printing the plan's statistics with it —
building and instance counts, tallest, median height, nearest neighbour. Judging
the city through rain-covered glass in a dim room is how the last version ended
up as a field of identical boxes.

Two things about the city are worth knowing before changing it. The quality
tiers cut the instance list off at a count, so the list is emitted **in
importance order** — tall and near first — and truncating it is a level of
detail rather than a hole in the skyline. And the facade fades its window grid
into its own average **per axis, driven by `fwidth`** rather than by distance:
a 1.5 m bay stops being resolvable long before a 4 m floor does, which is why a
distant tower reads as horizontal bands of lit and dark storeys. Average both
axes together and every glass tower downtown turns into a grey monolith.

## Files

```
room.html              Page shell + UI (Russian copy)
js/room.js             Core: config, maths, renderer, RT + world-UV helpers
js/room-outside.js     Sky, ground with the river and parks, rain, mist, lightning
js/room-city.js        The city plan: grid, massing, facades, streets, traffic
js/room-fog.js         Aerial perspective shared by everything outside the glass
js/room-interior.js    Room shell, floor-to-ceiling glazing + rain shader, fireplace
js/room-props.js       Furniture, soft goods, plants, the cat
js/room-lights.js      Lighting rig: fire spot + omni, bounces, practicals, window
js/room-walk.js        First-person controller: pointer lock, collision, head bob
js/post-ao.js          Depth-only SSAO with a depth-aware blur
js/post-dof.js         Depth of field: CoC prepass, half-res gather bokeh, composite
js/room-app.js         Env map, planar reflectors, post stack, controls, audio, loop
js/tex/noise.js        Texture toolkit: tileable noise, worley, warp, height→normal
js/tex/index.js        Registry of the 13 surfaces + the bridge to THREE textures
js/tex/*.js            One generator family per file (oak, plaster, stone, rug, …)
build-room.js          Bundles the module graph into one HTML file
cozy-room.html         Self-contained single-file build (double-click to open)
tools/shoot.js         Headless smoke test + screenshots of all four views
tools/filecheck.js     Verifies the single-file build runs from file://
tools/uicheck.js       Audits every control: does it work, and does it stall?
tools/walkcheck.js     Audits walk mode: movement, collision, crouch, exit
tools/skyline.js       Renders the world alone from six vantage points + plan stats
tools/closeup.js       Eight in-scene close-ups + per-surface luminance readout
tools/texlab.html      Texture lab: any surface on a lit panel/sphere/cylinder
tools/texshot.js       Renders one surface to a PNG contact sheet
```

`node build-room.js` rebuilds `cozy-room.html` after any source change.
The dev tools need `npm i playwright-core` and a local Chromium.

### Working on a texture

`js/tex/noise.js` documents the generator contract — a generator is
`(size, N) => { albedo, rough, height, ao?, metal? }` over flat Float32Arrays,
and the registry turns those into Three.js textures. To see one:

```
node tools/texshot.js oakFloor /tmp/oak.png '{"repeat":[4,3]}'
```

That renders the surface on a lit panel, sphere and cylinder under lighting
matched to the scene, with the albedo, roughness, normal and height maps tiled
2×2 underneath so seams are obvious. It exits non-zero if a generator breaks
the contract, so it doubles as a test.

---

*Conceptual presentation. Imagery, pricing, and contact details are
illustrative.*
