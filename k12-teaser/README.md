# Кутузовский 12 — a teaser in glyphs

A 14-second cinematic title sequence for the **«Кутузовский 12»** club house,
built with [Remotion](https://remotion.dev). It's a motion piece grown from the
soul of the landing page in the parent repo: a twilight, brass-accented palazzo
whose signature trick is **rendering imagery as a live field of characters** — a
"photograph made of characters."

## The idea

```
0.0–1.5 s   cold open — a single brass line draws itself across the dusk
1.3–5.2 s   the building precipitates out of noise: columns, floor
            string-courses and a warm ground-floor lobby, all as glyphs
5.0–7.0 s   the glyph field parts through the centre
6.8–12.6 s  КУТУЗОВСКИЙ 12 resolves letter by letter in Cormorant brass
12.4–14.0 s sign-off — «Известняк · стекло · латунь» — then a fade to black
```

The building is never a photo or a 3-D model. It's described as a **luminance
field** in [`src/glyph/field.ts`](src/glyph/field.ts) — a bay rhythm of bright
brass columns over dark bronze glazing, faint floor bands, twinkling interior
windows and a warm lobby glow — and drawn every frame as monospace characters on
a `<canvas>` by [`src/components/GlyphField.tsx`](src/components/GlyphField.tsx).
The field math is pure and deterministic, so renders are stable.

## Design system

Lifted verbatim from the landing page (`css/styles.css`): near-black `#06080d`,
brass `#c9a35e` / `#e7c98a`, twilight blues, **Cormorant Garamond** for display
and **Manrope** for supporting type (both with the Cyrillic subset, loaded via
`@remotion/google-fonts`). See [`src/theme.ts`](src/theme.ts).

## Commands

```console
npm i                 # install
npm run dev           # open Remotion Studio to scrub the timeline
npx remotion render k12-teaser out/kutuzovsky-12-teaser.mp4 --crf=16
```

The composition is `k12-teaser` — 1920×1080, 30 fps, 420 frames.

### Rendering in this environment

`remotion.config.ts` points Remotion at the pre-installed
`chrome-headless-shell` (the full Chromium build has removed old headless mode)
and allows the render browser to load Google Fonts through the agent proxy
(`setChromiumIgnoreCertificateErrors`). On a normal machine neither is needed —
Remotion downloads its own Chrome Headless Shell and fonts load directly.

## Structure

```
src/theme.ts               palette + font loading (brand tokens)
src/glyph/field.ts         the building as a pure luminance/colour field
src/components/GlyphField.tsx   draws the field as characters on a canvas
src/components/TwilightSky.tsx  gradient backdrop seen through the glyphs
src/components/TitleCard.tsx    letter-by-letter brass title
src/components/SignOff.tsx      closing line
src/components/HorizonLine.tsx  the cold-open brass line
src/components/Overlays.tsx     vignette, film grain, light leak, edge fades
src/Teaser.tsx             the timeline that composes it all
```
