/* =========================================================================
   The city outside the glass.

   The old skyline was a radial scatter of identical boxes, which from the
   47th floor reads as a bar chart: no streets, no districts, nothing moving.
   This builds an actual plan instead — a rotated street grid, a river with
   bridges, parks, two clusters of height — and then hangs massing, facades,
   traffic and air movement off it.

   Everything is instanced. A whole building is several stacked boxes (that
   is what gives setbacks), and roof plant, crowns and masts are more boxes
   in the same mesh, told apart by a per-instance `kind`. So the entire city
   is three draw calls: boxes, round towers, street glow — plus the cars and
   the aircraft lights.

   Instances are emitted in importance order (tall and near first) because
   the quality tiers cut the list off at a count; truncating an unsorted list
   would delete downtown and keep the suburbs.
   ========================================================================= */
import * as THREE from 'three';
import { GLSL_NOISE, U, ROOM, rnd, rrnd, smoothstep, outsideScene } from './room.js';
import { FOG, fogUniforms } from './room-fog.js';

/* ------------------------------------------------------------------ plan */
/* One grid for the whole city, turned off the room's axes so we never look
   straight down a street — that alignment is the thing that makes a
   procedural city look procedural. */
const GRID = { rot: 0.19, u: 104, v: 86, street: 21 };

/* The river runs across the view about 600 m out. Same constants drive the
   JS layout and the ground shader, so the water and the gap in the buildings
   are the same shape. */
const RIVER = { rot: 0.42, z0: -600, amp: 150, freq: 0.0016, hw: 62, wob: 22, wobF: 0.0031 };

/* dark voids, which is what actually gives a night skyline its structure */
const PARKS = [
  [-560, -330, 165],
  [430, -880, 205],
  [-150, 640, 150],
];

/* We are on the 47th floor at the edge of downtown, not in the middle of it.
   Nothing stands within HOLE of us, and for a couple of hundred metres past
   that the fabric is low — otherwise the neighbours fill the glass and there
   is no view, which is the whole point of the room. */
const HOLE = 170;
const NEAR_LOW = 430;   // heights ramp in from HOLE to here
const OUTER = 1520;     // instanced buildings out to here; ground fakes the rest

export const CITY = { GRID, RIVER, PARKS, OUTER };

/** signed distance to the water's edge; negative means in the river */
function riverSdf(x, z) {
  const c = Math.cos(RIVER.rot), s = Math.sin(RIVER.rot);
  const rx = x * c + z * s;
  const rz = -x * s + z * c;
  const centre = RIVER.z0 + Math.sin(rx * RIVER.freq) * RIVER.amp;
  const hw = RIVER.hw + Math.sin(rx * RIVER.wobF + 1.7) * RIVER.wob;
  return Math.abs(rz - centre) - hw;
}

/** which bank: positive is our side, negative is the far one with the towers */
function riverSide(x, z) {
  const c = Math.cos(RIVER.rot), s = Math.sin(RIVER.rot);
  const rx = x * c + z * s;
  const rz = -x * s + z * c;
  return rz - (RIVER.z0 + Math.sin(rx * RIVER.freq) * RIVER.amp);
}

/* The centre of gravity of the lit city, used to aim the light pollution in
   the sky and the reflections in the river. It sits out beyond the water and
   a little to the right, which is where the front glass looks. */
export const CBD = [280, -1000];

/* where the height lives: [x, z, sigma, weight] */
const CLUSTERS = [
  [CBD[0], CBD[1], 380, 1.0],
  [-830, -560, 280, 0.80],
  [-160, 620, 240, 0.35],
];

/** the plan in GLSL, so the ground and the sky agree with the buildings */
export const GLSL_PLAN = /* glsl */`
const float RIV_C = ${Math.cos(RIVER.rot).toFixed(6)};
const float RIV_S = ${Math.sin(RIVER.rot).toFixed(6)};
/** world → the city's grid frame, so the far fabric runs with the near streets */
vec2 toGrid(vec2 p){
  const float c = ${Math.cos(GRID.rot).toFixed(6)}, s = ${Math.sin(GRID.rot).toFixed(6)};
  return vec2(p.x * c + p.y * s, -p.x * s + p.y * c);
}
float riverSdf(vec2 p){
  vec2 r = vec2(p.x * RIV_C + p.y * RIV_S, -p.x * RIV_S + p.y * RIV_C);
  float centre = ${RIVER.z0.toFixed(1)} + sin(r.x * ${RIVER.freq}) * ${RIVER.amp.toFixed(1)};
  float hw = ${RIVER.hw.toFixed(1)} + sin(r.x * ${RIVER.wobF} + 1.7) * ${RIVER.wob.toFixed(1)};
  return abs(r.y - centre) - hw;
}
float parkMask(vec2 p){
  float m = 0.0;
${PARKS.map(([x, z, r]) => `  m = max(m, smoothstep(${r.toFixed(1)}, ${(r * 0.55).toFixed(1)}, distance(p, vec2(${x.toFixed(1)}, ${z.toFixed(1)}))));`).join('\n')}
  return m;
}
${CLUSTERS.map(([cx, cz, s, w], i) => `#define CL${i} vec2(${cx.toFixed(1)}, ${cz.toFixed(1)})`).join('\n')}
float cityDensity(vec2 p){
  float t = 0.0;
${CLUSTERS.map(([cx, cz, s, w], i) => `  t += exp(-dot(p - CL${i}, p - CL${i}) / ${(2 * s * s).toFixed(1)}) * ${w.toFixed(2)};`).join('\n')}
  return t * (1.0 - smoothstep(1020.0, ${OUTER.toFixed(1)}, length(p)) * 0.65);
}`;

const parkMask = (x, z) => {
  let m = 0;
  for (const [px, pz, pr] of PARKS) m = Math.max(m, smoothstep(pr, pr * 0.55, Math.hypot(x - px, z - pz)));
  return m;
};

/* Height field: two clusters of towers, falling away at the edge of town, and
   held down hard in the near ring so we can see over the neighbours.
   Returns roughly 0 … 1.3. */
function density(x, z) {
  const r = Math.hypot(x, z);
  let t = 0;
  for (const [cx, cz, s, w] of CLUSTERS) t += Math.exp(-((x - cx) ** 2 + (z - cz) ** 2) / (2 * s * s)) * w;
  t *= 1 - smoothstep(1020, OUTER, r) * 0.65;
  t *= 0.24 + 0.76 * smoothstep(HOLE, NEAR_LOW, r);
  return t;
}

/* ---------------------------------------------------------------- massing */
/* A building is a list of boxes. kind 0 = facade, 1 = plant/mast, 2 = lit crown. */
function massing(b, out) {
  const { x, z, w, d, h, rot, style, id, glass } = b;
  const base = -ROOM.alt;
  const push = (cy, bw, bh, bd, kind, crown) =>
    out.push({ x, z, y: base + cy, w: bw, h: bh, d: bd, rot, id, style, kind, crown,
               baseY: base, topY: base + h, glass, occ: b.occ, score: 0 });

  const form = h < 60 ? 0 : rnd();
  if (form < 0.42) {                                   // straight slab
    push(h / 2, w, h, d, 0, b.crown);
  } else if (form < 0.78) {                            // one setback
    const h1 = h * rrnd(0.55, 0.70);
    push(h1 / 2, w, h1, d, 0, -1);
    push(h1 + (h - h1) / 2, w * 0.79, h - h1, d * 0.79, 0, b.crown);
  } else {                                             // tapered, three stacks
    const h1 = h * 0.48, h2 = h * 0.30, h3 = h - h1 - h2;
    push(h1 / 2, w, h1, d, 0, -1);
    push(h1 + h2 / 2, w * 0.80, h2, d * 0.80, 0, -1);
    push(h1 + h2 + h3 / 2, w * 0.60, h3, d * 0.60, 0, b.crown);
  }

  const topW = out[out.length - 1].w, topD = out[out.length - 1].d;

  // an architectural crown on the tall ones, sometimes with a mast above it
  if (h > 150 && b.crown >= 0) {
    const ch = rrnd(7, 17);
    push(h + ch / 2, topW * 0.5, ch, topD * 0.5, 2, b.crown);
    if (rnd() < 0.45) push(h + ch + rrnd(9, 24), 1.1, rrnd(18, 48), 1.1, 1, -1);
  }

  // roof plant: the lumpy silhouette that stops a roof reading as a lid
  if (h > 42) {
    const n = 1 + ((rnd() * 3) | 0);
    for (let i = 0; i < n; i++) {
      const pw = topW * rrnd(0.16, 0.34), pd = topD * rrnd(0.16, 0.34), ph = rrnd(2.4, 6.5);
      const ox = rrnd(-1, 1) * (topW / 2 - pw / 2) * 0.8;
      const oz = rrnd(-1, 1) * (topD / 2 - pd / 2) * 0.8;
      const c = Math.cos(rot), s = Math.sin(rot);
      out.push({ x: x + ox * c - oz * s, z: z + ox * s + oz * c, y: base + h + ph / 2,
                 w: pw, h: ph, d: pd, rot, id, style, kind: 1, crown: -1,
                 baseY: base, topY: base + h, glass, occ: b.occ, score: 0 });
    }
  }
}

/* ------------------------------------------------------------------ plan */
export function cityPlan() {
  const buildings = [], boxes = [], round = [];
  const c = Math.cos(GRID.rot), s = Math.sin(GRID.rot);
  const toWorld = (u, v) => [u * c - v * s, u * s + v * c];

  const IU = Math.ceil(OUTER / GRID.u) + 1, IV = Math.ceil(OUTER / GRID.v) + 1;
  for (let i = -IU; i <= IU; i++) {
    for (let j = -IV; j <= IV; j++) {
      const u0 = i * GRID.u + GRID.street / 2, u1 = (i + 1) * GRID.u - GRID.street / 2;
      const v0 = j * GRID.v + GRID.street / 2, v1 = (j + 1) * GRID.v - GRID.street / 2;
      const [cx, cz] = toWorld((u0 + u1) / 2, (v0 + v1) / 2);
      const r = Math.hypot(cx, cz);
      if (r < HOLE || r > OUTER) continue;
      if (riverSdf(cx, cz) < 34) continue;
      if (parkMask(cx, cz) > 0.35) continue;

      const t = density(cx, cz);
      // Our own bank is the old low one. That is what makes the view work:
      // roofs below us, then the water, then the towers standing behind it.
      const nearBank = riverSide(cx, cz) > 0;
      const scale = nearBank ? 0.62 : 1;

      // big plots downtown, subdivided into smaller parcels out in the fabric
      const parcels = t > 0.62 ? [[0, 0, 1, 1]]
        : t > 0.22 ? (rnd() < 0.5 ? [[0, 0, 1, 0.5], [0, 0.5, 1, 0.5]] : [[0, 0, 0.5, 1], [0.5, 0, 0.5, 1]])
        : [[0, 0, 0.5, 0.5], [0.5, 0, 0.5, 0.5], [0, 0.5, 0.5, 0.5], [0.5, 0.5, 0.5, 0.5]];

      for (const [fu, fv, fw, fd] of parcels) {
        if (parcels.length > 1 && rnd() < 0.22) continue;   // gap sites, yards
        const inset = rrnd(1.5, 5.0);
        const pw = (u1 - u0) * fw - inset, pd = (v1 - v0) * fd - inset;
        if (pw < 9 || pd < 9) continue;
        const [px, pz] = toWorld(u0 + (u1 - u0) * (fu + fw / 2), v0 + (v1 - v0) * (fv + fd / 2));
        const pr = Math.hypot(px, pz);
        if (pr < HOLE || pr > OUTER) continue;

        const tall = density(px, pz) * (0.55 + rnd() * 0.95);
        let h = (16 + tall * rrnd(120, 300)) * scale;
        if (parcels.length === 4) h = Math.min(h, rrnd(14, 46));
        h = Math.max(11, Math.min(h, 26 + (pr - HOLE) * 0.55));   // nothing tall right on top of us
        // out past the modelled ring the ground shader already draws low-rise
        // fabric; a 15 m box a kilometre away is a wasted instance
        if (h < 22 && pr > 900) continue;

        // style follows use: glass towers downtown, masonry and panel out in
        // the fabric and across the river
        const rs = rnd();
        const style = h > 110 ? (rs < 0.72 ? 0 : 2)
          : h > 45 ? (rs < 0.34 ? 0 : rs < 0.66 ? 2 : rs < 0.86 ? 3 : 1)
          : (rs < 0.42 ? 1 : rs < 0.78 ? 3 : 2);

        const b = {
          x: px, z: pz, w: pw, d: pd, h, rot: GRID.rot + rrnd(-0.02, 0.02),
          id: buildings.length, style, glass: style === 0 ? 1 : style === 2 ? 0.55 : 0.2,
          occ: style === 0 ? rrnd(0.22, 0.46) : style === 1 ? rrnd(0.10, 0.26) : rrnd(0.14, 0.34),
          crown: h > 150 && rnd() < 0.62 ? rnd() : -1,
          // a handful of the downtown towers are cylinders, for the silhouette
          roundTower: h > 150 && t > 0.55 && rnd() < 0.16,
        };
        buildings.push(b);
        if (b.roundTower) round.push(b); else massing(b, boxes);
      }
    }
  }

  /* Our own tower. Without it, looking straight down through the glass shows
     150 m of nothing and then the street — the room appears to be floating.
     It is exactly the room's footprint, so the glazing sits flush with the
     facade, and it is only ever seen from inside, edge-on and downward. */
  boxes.push({
    x: 0, z: 0, y: -ROOM.alt / 2 - 0.01, w: ROOM.x * 2 - 0.04, h: ROOM.alt - 0.02,
    d: ROOM.z * 2 - 0.04, rot: 0, id: 7717, style: 0, kind: 0, crown: -1,
    baseY: -ROOM.alt, topY: -0.02, glass: 1, occ: 0.38, score: Infinity,
  });

  /* Sort by what a viewer would miss first: tall things, near things. The
     quality tiers slice this list, so the order is the level of detail. */
  for (const o of boxes) {
    if (o.score === Infinity) continue;
    const r = Math.hypot(o.x, o.z);
    o.score = (o.topY + ROOM.alt) / (1 + r / 260) - (o.kind === 1 ? 40 : 0);
  }
  boxes.sort((a, b) => b.score - a.score);

  return { buildings, boxes, round, streets: streetRuns() };
}

/* -------------------------------------------------------------- streets */
/* Walk each grid line and keep the stretches that are in town and out of the
   water. Every fourth line is an arterial and carries a bridge across. */
function streetRuns() {
  const runs = [];
  const c = Math.cos(GRID.rot), s = Math.sin(GRID.rot);
  const R = 980, STEP = 24;

  const scan = (fixed, axis, arterial) => {
    let run = null;
    for (let t = -R; t <= R; t += STEP) {
      const u = axis === 0 ? t : fixed, v = axis === 0 ? fixed : t;
      const x = u * c - v * s, z = u * s + v * c;
      const sd = riverSdf(x, z);
      const bridge = arterial && sd < 0;
      const ok = Math.hypot(x, z) < R && Math.hypot(x, z) > 42 && (sd > 5 || bridge);
      if (ok) {
        if (!run) run = { ax: x, az: z, bx: x, bz: z, arterial, bridge: false };
        run.bx = x; run.bz = z;
        if (bridge) run.bridge = true;
      } else if (run) {
        if (Math.hypot(run.bx - run.ax, run.bz - run.az) > 60) runs.push(run);
        run = null;
      }
    }
    if (run && Math.hypot(run.bx - run.ax, run.bz - run.az) > 60) runs.push(run);
  };

  // the lines are the gaps the blocks were inset from, not the block centres
  const IU = Math.ceil(R / GRID.u), IV = Math.ceil(R / GRID.v);
  for (let j = -IV; j <= IV; j++) scan(j * GRID.v, 0, j % 4 === 0);
  for (let i = -IU; i <= IU; i++) scan(i * GRID.u, 1, i % 3 === 0);

  // nearest first, so a low tier keeps the streets we can actually see into
  runs.sort((a, b) => Math.hypot(a.ax, a.az) - Math.hypot(b.ax, b.az));
  return runs;
}

/* ================================================================ facades */
/* Shared between the box towers and the round ones; ROUND swaps how the
   horizontal facade coordinate is found. */
const FACADE = /* glsl */`
uniform float uTime, uFlash, uRain;
varying vec3 vLocal, vNrm, vW, vScale;
varying vec4 vA, vB;

void facade(out vec3 col){
  vec3 n  = normalize(vNrm);
  bool roof = abs(n.y) > 0.5;
  float id = vA.x, style = vA.y, baseY = vA.z, topY = vA.w;
  float kind = vB.x, occ = vB.y, crown = vB.z, glass = vB.w;
  float dist = length(vW - cameraPosition);

  // Concrete and stone under an overcast night sky are very nearly black.
  // Keeping the shell dark is what lets the windows read as light sources
  // instead of pale spots on a grey wall — the difference between a night
  // skyline and a daytime one with the exposure pulled down.
  float up = clamp(n.y * 0.5 + 0.5, 0.0, 1.0);
  vec3 shell = mix(vec3(0.0055,0.0065,0.0095), vec3(0.021,0.023,0.029), up);
  shell *= 0.72 + 0.55 * hash11(id * 3.1);
  col = shell;

  if (kind > 1.5) {                       // ---- lit architectural crown
    // Fins, not a flat wall: uplighting a crown picks out its ribs, and a
    // featureless bright slab on top of a tower reads as a missing texture.
    float upl = clamp(vLocal.y + 0.5, 0.0, 1.0);
    float u = (abs(n.x) > 0.5) ? vLocal.z * vScale.z : vLocal.x * vScale.x;
    float fin = 0.28 + 0.72 * smoothstep(0.32, 0.52, fract(u / 1.7));
    vec3 cc = mix(vec3(0.44,0.58,0.92), vec3(1.00,0.62,0.26), crown);
    col += cc * (0.018 + 0.105 * upl) * fin * (roof ? 0.25 : 1.0);
    return;
  }
  if (kind > 0.5) { col *= 0.55; return; } // ---- roof plant and masts

  if (roof) {
    // wet gravel, standing water, and a parapet catching the sky
    vec2 rp = vW.xz;
    float g = fbm2(rp * 0.6);
    col = vec3(0.015,0.015,0.017) * (0.55 + g * 0.95);
    float puddle = smoothstep(0.54, 0.70, fbm2(rp * 0.42 + 3.0)) * (0.35 + 0.65 * uRain);
    col = mix(col, vec3(0.026, 0.019, 0.014), puddle * 0.7);
    float edge = 1.0 - max(abs(vLocal.x), abs(vLocal.z)) * 2.0;
    col += vec3(0.030,0.028,0.030) * smoothstep(0.055, 0.0, edge);
    col += vec3(0.34,0.40,0.55) * uFlash * 0.5;
    return;
  }

  // ---- windows.  metres along the facade, so a cell is the same size on a
  // 20 m infill block and a 250 m tower.
  #ifdef ROUND
    float around = atan(vLocal.z, vLocal.x) * 0.5 * vScale.x;
  #else
    float around = (abs(n.x) > 0.5) ? vLocal.z * vScale.z : vLocal.x * vScale.x;
  #endif
  float yUp = vW.y - baseY;

  float floorH = 3.15 + 1.25 * hash11(id * 1.7 + 5.0);
  float cellW, padX, padY, coher, warmth;
  if (style < 0.5)      { cellW = 1.55; padX = 0.055; padY = 0.16; coher = 0.78; warmth = 0.50; }
  else if (style < 1.5) { cellW = 3.05; padX = 0.30;  padY = 0.27; coher = 0.12; warmth = 0.93; }
  else if (style < 2.5) { cellW = 2.20; padX = 0.21;  padY = 0.10; coher = 0.45; warmth = 0.72; }
  else                  { cellW = 3.40; padX = 0.17;  padY = 0.21; coher = 0.18; warmth = 0.88; }
  // one building tends to have one kind of lamp in it
  warmth = clamp(warmth + (hash11(id * 5.1 + 9.0) - 0.5) * 0.5, 0.0, 1.0);

  float fy = yUp / floorH, cx = around / cellW;
  float fi = floor(fy), ff = fract(fy);
  float ci = floor(cx), cf = fract(cx);

  // analytic antialiasing: a window an eighth of a pixel wide must not flicker
  float wx = fwidth(cx) * 0.9 + 0.0015;
  float wy = fwidth(fy) * 0.9 + 0.0015;
  float sx = smoothstep(padX - wx, padX + wx, cf) * smoothstep(padX - wx, padX + wx, 1.0 - cf);
  float sy = smoothstep(padY - wy, padY + wy, ff) * smoothstep(padY * 0.6 - wy, padY * 0.6 + wy, 1.0 - ff);
  float pane = sx * sy;

  // occupancy.  Offices go dark a floor at a time; flats go dark a flat at a
  // time — that difference is most of what tells the two apart from here.
  float rFloor = hash12(vec2(fi, id * 7.3));
  float rBay   = hash12(vec2(ci, fi + id * 3.1));
  float rHue   = hash12(vec2(ci * 1.7, fi * 2.3 + id));
  float slow   = step(0.988, hash11(floor(uTime * 0.06 + rBay * 40.0) + rFloor * 97.0));
  float floorOn = step(0.58, rFloor) * step(0.34, rBay);
  float bayOn   = step(1.0 - occ, rBay);
  // Whole buildings are dark at this hour — an empty office block, a tower
  // still being fitted out. Without this every roofline is equally busy and
  // the skyline has no rhythm.
  float alive = mix(0.08, 1.0, pow(hash11(id * 13.7 + 2.0), 1.35));
  float lit = mix(bayOn, floorOn, coher) * (1.0 - slow * 0.8) * alive;

  vec3 warm = vec3(1.00, 0.60, 0.26), cool = vec3(0.70, 0.82, 1.00);
  vec3 tint = mix(cool, warm, step(rHue, warmth));
  float energy = 0.30 + 0.95 * rHue;
  energy *= 0.90 + 0.10 * sin(uTime * (1.6 + rBay * 5.0) + rHue * 30.0);
  // half-drawn blinds and curtains: plenty of lit windows are not bright
  energy *= mix(0.30, 1.0, step(0.42, hash12(vec2(ci, fi) + id)));

  // Once a bay is smaller than a pixel the pattern has to cross-fade into its
  // own average or the whole skyline crawls with moiré. Drive that off the
  // on-screen size of a cell — fwidth is exactly "cells per pixel" — not off
  // distance, so it holds at any resolution or field of view.
  //
  // Crucially the two axes fade separately. A bay is 1.5 m and goes first; a
  // floor is nearly 4 m and survives another kilometre. That is why a distant
  // tower reads as horizontal bands of lit and dark storeys rather than as a
  // blank slab — average both axes at once and every glass tower downtown
  // turns into a grey monolith.
  float lodX = smoothstep(0.85, 0.30, fwidth(cx));
  float lodY = smoothstep(0.85, 0.30, fwidth(fy));
  vec3 meanTint = mix(cool, warm, warmth);

  float rowLit = mix(occ, 0.30 * step(0.42, rFloor), coher) * alive;
  float litM  = mix(rowLit, lit, lodX);
  float maskM = mix(0.70, sx, lodX) * mix(0.80, sy, lodY);
  vec3  tintM = mix(meanTint, tint, lodX);

  col += tintM * litM * mix(0.55, energy, lodX) * maskM * 0.95;
  col *= 1.0 - (1.0 - pane) * 0.28 * lodX * lodY;            // recess shadow
  col += tintM * litM * 0.045 * lodX;                        // spill onto the wall

  // wet glass picks up the sodium under the clouds at a grazing angle
  vec3 V = normalize(cameraPosition - vW);
  float fres = pow(1.0 - clamp(abs(dot(vec3(n.x, 0.0, n.z), V)), 0.0, 1.0), 4.0);
  col += vec3(0.048, 0.026, 0.011) * fres * (0.35 + glass) * (0.6 + 0.5 * uRain);

  // the top few floors of a crowned tower are washed from below
  if (crown >= 0.0) {
    vec3 cc = mix(vec3(0.55,0.72,1.00), vec3(1.00,0.58,0.22), crown);
    col += cc * smoothstep(topY - 26.0, topY - 2.0, vW.y) * 0.09;
  }
  col += vec3(0.34, 0.40, 0.55) * uFlash * (0.25 + up * 0.5);
}
`;

const CITY_VERT = /* glsl */`
attribute vec4 aA, aB;
varying vec3 vLocal, vNrm, vW, vScale;
varying vec4 vA, vB;
void main(){
  vScale = vec3(length(instanceMatrix[0].xyz), length(instanceMatrix[1].xyz), length(instanceMatrix[2].xyz));
  vLocal = position;
  vNrm   = normal;
  vA = aA; vB = aB;
  vec4 w = modelMatrix * instanceMatrix * vec4(position, 1.0);
  vW = w.xyz;
  gl_Position = projectionMatrix * viewMatrix * w;
}`;

const CITY_FRAG = GLSL_NOISE + FOG + FACADE + /* glsl */`
void main(){
  vec3 col;
  facade(col);
  gl_FragColor = vec4(applyFog(col, vW, length(vW - cameraPosition)), 1.0);
}`;

function cityMaterial(round) {
  return new THREE.ShaderMaterial({
    defines: round ? { ROUND: '' } : {},
    // the facade antialiases its window grid with fwidth()
    extensions: { derivatives: true },
    uniforms: { uTime: U.time, uFlash: U.flash, uRain: U.rain, ...fogUniforms() },
    vertexShader: CITY_VERT,
    fragmentShader: CITY_FRAG,
  });
}

/* -------------------------------------------------------- the box towers */
function packInstances(geo, mesh, list) {
  const n = list.length;
  const A = new Float32Array(n * 4), B = new Float32Array(n * 4);
  const m = new THREE.Matrix4(), q = new THREE.Quaternion();
  const p = new THREE.Vector3(), s = new THREE.Vector3(), up = new THREE.Vector3(0, 1, 0);
  for (let i = 0; i < n; i++) {
    const o = list[i];
    p.set(o.x, o.y, o.z);
    s.set(o.w, o.h, o.d);
    q.setFromAxisAngle(up, o.rot);
    m.compose(p, q, s);
    mesh.setMatrixAt(i, m);
    A[i * 4] = o.id; A[i * 4 + 1] = o.style; A[i * 4 + 2] = o.baseY; A[i * 4 + 3] = o.topY;
    B[i * 4] = o.kind; B[i * 4 + 1] = o.occ; B[i * 4 + 2] = o.crown; B[i * 4 + 3] = o.glass;
  }
  geo.setAttribute('aA', new THREE.InstancedBufferAttribute(A, 4));
  geo.setAttribute('aB', new THREE.InstancedBufferAttribute(B, 4));
  mesh.instanceMatrix.needsUpdate = true;
  mesh.frustumCulled = false;
  outsideScene.add(mesh);
}

export function buildCity(plan, maxCount) {
  const list = plan.boxes.slice(0, maxCount);
  const geo = new THREE.BoxGeometry(1, 1, 1);
  const mesh = new THREE.InstancedMesh(geo, cityMaterial(false), list.length);
  packInstances(geo, mesh, list);
  return mesh;
}

export function buildRoundTowers(plan) {
  const list = [];
  for (const b of plan.round) {
    const base = -ROOM.alt, w = Math.min(b.w, b.d);
    list.push({ x: b.x, z: b.z, y: base + b.h / 2, w, h: b.h, d: w, rot: b.rot,
                id: b.id, style: b.style, kind: 0, crown: b.crown,
                baseY: base, topY: base + b.h, glass: b.glass, occ: b.occ });
    if (b.crown >= 0) {
      const ch = rrnd(8, 16);
      list.push({ x: b.x, z: b.z, y: base + b.h + ch / 2, w: w * 0.55, h: ch, d: w * 0.55, rot: b.rot,
                  id: b.id, style: b.style, kind: 2, crown: b.crown,
                  baseY: base, topY: base + b.h, glass: b.glass, occ: b.occ });
    }
  }
  const geo = new THREE.CylinderGeometry(0.5, 0.5, 1, 22, 1);
  const mesh = new THREE.InstancedMesh(geo, cityMaterial(true), Math.max(1, list.length));
  packInstances(geo, mesh, list);
  mesh.count = list.length;
  return mesh;
}

/* ------------------------------------------------------------ street glow */
/* One flat additive quad per stretch of street. The lamps are a periodic
   function of arc length inside the shader, so a 900 m avenue costs the same
   as a 60 m one and there is no such thing as a lamp instance. */
export function buildStreets(plan, maxCount) {
  const runs = plan.streets.slice(0, maxCount);
  const geo = new THREE.PlaneGeometry(1, 1);
  geo.rotateX(-Math.PI / 2);
  const flags = new Float32Array(runs.length * 2);

  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: { uTime: U.time, uRain: U.rain, uFlash: U.flash, ...fogUniforms() },
    vertexShader: /* glsl */`
      attribute vec2 aFlag;      // x = arterial, y = has a bridge
      varying vec2 vUv; varying vec3 vW; varying float vLen, vArt;
      void main(){
        vUv = uv;
        vLen = length(instanceMatrix[2].xyz);
        vArt = aFlag.x;
        vec4 w = modelMatrix * instanceMatrix * vec4(position, 1.0);
        vW = w.xyz;
        gl_Position = projectionMatrix * viewMatrix * w;
      }`,
    fragmentShader: GLSL_NOISE + FOG + /* glsl */`
      varying vec2 vUv; varying vec3 vW; varying float vLen, vArt;
      uniform float uTime, uRain, uFlash;
      void main(){
        float across = abs(vUv.x - 0.5) * 2.0;
        float s = vUv.y * vLen;

        // Lamps every 28 m down both kerbs. The light has to run ALONG the
        // road, not across it — a pool that spans the full width turns every
        // lamp into a rung and the street into a ladder.
        float lp = fract(s / 23.0);
        float d = min(lp, 1.0 - lp);
        float head = exp(-pow(d * 30.0, 2.0)) * exp(-pow((across - 0.68) * 5.2, 2.0));
        float pool = exp(-pow(d * 3.6, 2.0)) * exp(-pow(across * 2.4, 2.0));
        float wet  = exp(-pow(across * 1.8, 2.0)) * (0.30 + 0.55 * uRain);

        vec3 sodium = vec3(1.00, 0.47, 0.13);
        float a = (head * 0.30 + pool * 0.18 + wet * 0.20) * (0.65 + 0.55 * vArt);
        a *= smoothstep(1.0, 0.70, across);

        float dist = length(vW - cameraPosition);
        a *= smoothstep(1500.0, 500.0, dist);
        vec3 col = sodium + vec3(0.25, 0.30, 0.45) * uFlash;
        gl_FragColor = vec4(col * a, a * 0.9);
      }`,
  });

  const mesh = new THREE.InstancedMesh(geo, mat, Math.max(1, runs.length));
  const m = new THREE.Matrix4(), q = new THREE.Quaternion(), p = new THREE.Vector3(),
        sc = new THREE.Vector3(), up = new THREE.Vector3(0, 1, 0);
  runs.forEach((r, i) => {
    const dx = r.bx - r.ax, dz = r.bz - r.az;
    const len = Math.hypot(dx, dz);
    p.set((r.ax + r.bx) / 2, -ROOM.alt + 0.35, (r.az + r.bz) / 2);
    sc.set(r.arterial ? 20 : 13, 1, len);
    q.setFromAxisAngle(up, Math.atan2(dx, dz));
    m.compose(p, q, sc);
    mesh.setMatrixAt(i, m);
    flags[i * 2] = r.arterial ? 1 : 0;
    flags[i * 2 + 1] = r.bridge ? 1 : 0;
  });
  geo.setAttribute('aFlag', new THREE.InstancedBufferAttribute(flags, 2));
  mesh.instanceMatrix.needsUpdate = true;
  mesh.count = runs.length;
  mesh.frustumCulled = false;
  mesh.renderOrder = 2;
  outsideScene.add(mesh);
  return mesh;
}

/* ---------------------------------------------------------------- traffic */
/* Nothing says "this city is switched on" like moving light. Each car is one
   camera-facing streak that walks a street run; the position is computed in
   the vertex shader from time, so the CPU never touches it. */
export function buildTraffic(plan, maxCount) {
  const runs = plan.streets.filter((r) => r.arterial || Math.hypot(r.ax, r.az) < 620);
  const plane = new THREE.PlaneGeometry(1, 1);
  const g = new THREE.InstancedBufferGeometry();
  g.index = plane.index;
  g.setAttribute('position', plane.attributes.position);
  g.setAttribute('uv', plane.attributes.uv);

  const seg = new Float32Array(maxCount * 4), car = new Float32Array(maxCount * 4);
  let n = 0;
  for (let pass = 0; pass < 40 && n < maxCount; pass++) {
    for (const r of runs) {
      if (n >= maxCount) break;
      const dx = r.bx - r.ax, dz = r.bz - r.az, len = Math.hypot(dx, dz);
      if (len < 80) continue;
      const dir = rnd() < 0.5 ? 1 : -1;
      // offset into the correct lane, perpendicular to the run
      const px = -dz / len, pz = dx / len;
      const lane = dir * (r.arterial ? rrnd(3.5, 9) : rrnd(2.5, 5));
      seg[n * 4] = r.ax + px * lane; seg[n * 4 + 1] = r.az + pz * lane;
      seg[n * 4 + 2] = (dx / len) * dir; seg[n * 4 + 3] = (dz / len) * dir;
      car[n * 4] = len;
      car[n * 4 + 1] = rrnd(9, 17) * (r.arterial ? 1.35 : 1.0);   // m/s
      car[n * 4 + 2] = rnd();
      car[n * 4 + 3] = dir > 0 ? 1 : 0;
      n++;
    }
  }
  g.setAttribute('aSeg', new THREE.InstancedBufferAttribute(seg, 4));
  g.setAttribute('aCar', new THREE.InstancedBufferAttribute(car, 4));
  g.instanceCount = n;

  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: { uTime: U.time, uRain: U.rain, uY: { value: -ROOM.alt + 0.75 } },
    vertexShader: /* glsl */`
      attribute vec4 aSeg, aCar;
      uniform float uTime, uY;
      varying vec2 vUv; varying float vKind, vFade;
      void main(){
        float len = aCar.x, speed = aCar.y;
        // a whole run is one lap; the phase spreads the cars along it
        float t = fract(aCar.z + uTime * speed / len);
        vec3 dir = vec3(aSeg.z, 0.0, aSeg.w);
        vec3 c = vec3(aSeg.x, uY, aSeg.y) + dir * (t * len);

        float dist = length(cameraPosition - c);
        // a car is a couple of metres, but its wet smear is much longer
        float along = 5.5 + 4.0 * aCar.z;
        float wide  = clamp(dist * 0.0022, 1.1, 4.5);
        vec3 side = normalize(cross(dir, vec3(0.0, 1.0, 0.0)));

        vec3 w = c + dir * position.y * along + side * position.x * wide;
        vUv = uv; vKind = aCar.w;
        vFade = smoothstep(1150.0, 480.0, dist) * 0.65 + smoothstep(620.0, 200.0, dist) * 0.45;
        gl_Position = projectionMatrix * viewMatrix * vec4(w, 1.0);
      }`,
    fragmentShader: /* glsl */`
      varying vec2 vUv; varying float vKind, vFade;
      uniform float uRain;
      void main(){
        vec2 d = (vUv - 0.5) * 2.0;
        float core = exp(-dot(d * vec2(2.6, 1.5), d * vec2(2.6, 1.5)) * 2.2);
        float smear = exp(-abs(d.y) * 1.6) * exp(-abs(d.x) * 3.4) * (0.25 + 0.45 * uRain);
        vec3 head = vec3(1.00, 0.94, 0.82);
        vec3 tail = vec3(1.00, 0.16, 0.06);
        vec3 col = mix(tail, head, vKind);
        float a = (core * 0.85 + smear * 0.5) * vFade;
        gl_FragColor = vec4(col * a, a);
      }`,
  });

  const mesh = new THREE.Mesh(g, mat);
  mesh.maxCars = n;
  mesh.frustumCulled = false;
  mesh.renderOrder = 3;
  outsideScene.add(mesh);
  return mesh;
}

/* --------------------------------------------- rooftop aviation beacons */
export function buildBeacons(plan, count = 70) {
  const tall = plan.buildings.filter((b) => b.h > 95).sort((a, b) => b.h - a.h).slice(0, count);
  const geo = new THREE.PlaneGeometry(1, 1);
  const g = new THREE.InstancedBufferGeometry();
  g.index = geo.index;
  g.setAttribute('position', geo.attributes.position);
  g.setAttribute('uv', geo.attributes.uv);
  const arr = new Float32Array(Math.max(1, tall.length) * 4);
  tall.forEach((b, i) => {
    arr[i * 4] = b.x; arr[i * 4 + 1] = -ROOM.alt + b.h + rrnd(2, 26);
    arr[i * 4 + 2] = b.z; arr[i * 4 + 3] = rnd();
  });
  g.setAttribute('aPos', new THREE.InstancedBufferAttribute(arr, 4));
  g.instanceCount = tall.length;

  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: { uTime: U.time },
    vertexShader: /* glsl */`
      attribute vec4 aPos;
      varying vec2 vUv; varying float vPhase;
      void main(){
        vUv = uv; vPhase = aPos.w;
        vec3 c = aPos.xyz;
        float d = length(c - cameraPosition);
        float size = clamp(d * 0.0032, 0.6, 5.0);
        vec3 right = normalize(vec3(viewMatrix[0][0], viewMatrix[1][0], viewMatrix[2][0]));
        vec3 up    = normalize(vec3(viewMatrix[0][1], viewMatrix[1][1], viewMatrix[2][1]));
        vec3 w = c + (right * position.x + up * position.y) * size;
        gl_Position = projectionMatrix * viewMatrix * vec4(w, 1.0);
      }`,
    fragmentShader: /* glsl */`
      varying vec2 vUv; varying float vPhase;
      uniform float uTime;
      void main(){
        float d = length(vUv - 0.5) * 2.0;
        float a = pow(smoothstep(1.0, 0.0, d), 2.4);
        float blink = smoothstep(0.55, 0.95, sin(uTime * 1.5 + vPhase * 6.283) * 0.5 + 0.5);
        gl_FragColor = vec4(vec3(1.0, 0.13, 0.07) * (0.25 + blink * 2.4), a);
      }`,
  });
  const mesh = new THREE.Mesh(g, mat);
  mesh.frustumCulled = false;
  mesh.renderOrder = 5;
  outsideScene.add(mesh);
  return mesh;
}

/* --------------------------------------------------------------- aircraft */
/* Two airliners on long approaches. Four lights each — red to port, green to
   starboard, a steady white tail and an anti-collision strobe. It costs
   eight quads and it is the cheapest realism in the whole scene. */
export function buildAircraft() {
  const PLANES = [
    { a: [-1900, 430, -1500], b: [1700, 300, -2100], period: 190 },
    { a: [1800, 620, 900], b: [-1500, 540, -1700], period: 260 },
  ];
  const LIGHTS = [
    { off: [-1, 0, 0], kind: 0 },   // port, red
    { off: [1, 0, 0], kind: 1 },    // starboard, green
    { off: [0, 0, -1], kind: 2 },   // tail, steady white
    { off: [0, -0.2, 0.4], kind: 3 }, // strobe
  ];
  const plane = new THREE.PlaneGeometry(1, 1);
  const g = new THREE.InstancedBufferGeometry();
  g.index = plane.index;
  g.setAttribute('position', plane.attributes.position);
  g.setAttribute('uv', plane.attributes.uv);

  const n = PLANES.length * LIGHTS.length;
  const A = new Float32Array(n * 4), B = new Float32Array(n * 4), C = new Float32Array(n * 4);
  let i = 0;
  PLANES.forEach((p, pi) => {
    for (const l of LIGHTS) {
      A[i * 4] = p.a[0]; A[i * 4 + 1] = p.a[1]; A[i * 4 + 2] = p.a[2]; A[i * 4 + 3] = pi * 0.37;
      B[i * 4] = p.b[0]; B[i * 4 + 1] = p.b[1]; B[i * 4 + 2] = p.b[2]; B[i * 4 + 3] = p.period;
      C[i * 4] = l.off[0] * 16; C[i * 4 + 1] = l.off[1] * 16; C[i * 4 + 2] = l.off[2] * 16;
      C[i * 4 + 3] = l.kind;
      i++;
    }
  });
  g.setAttribute('aFrom', new THREE.InstancedBufferAttribute(A, 4));
  g.setAttribute('aTo', new THREE.InstancedBufferAttribute(B, 4));
  g.setAttribute('aOff', new THREE.InstancedBufferAttribute(C, 4));
  g.instanceCount = n;

  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: { uTime: U.time },
    vertexShader: /* glsl */`
      attribute vec4 aFrom, aTo, aOff;
      uniform float uTime;
      varying vec2 vUv; varying float vKind, vT;
      void main(){
        float t = fract(aFrom.w + uTime / aTo.w);
        vec3 dir = normalize(aTo.xyz - aFrom.xyz);
        vec3 side = normalize(cross(dir, vec3(0.0, 1.0, 0.0)));
        vec3 c = mix(aFrom.xyz, aTo.xyz, t) + side * aOff.x + vec3(0.0, aOff.y, 0.0) + dir * aOff.z;
        float d = length(c - cameraPosition);
        float size = clamp(d * 0.0028, 1.0, 9.0);
        vec3 right = normalize(vec3(viewMatrix[0][0], viewMatrix[1][0], viewMatrix[2][0]));
        vec3 up    = normalize(vec3(viewMatrix[0][1], viewMatrix[1][1], viewMatrix[2][1]));
        vec3 w = c + (right * position.x + up * position.y) * size;
        vUv = uv; vKind = aOff.w; vT = t;
        gl_Position = projectionMatrix * viewMatrix * vec4(w, 1.0);
      }`,
    fragmentShader: /* glsl */`
      varying vec2 vUv; varying float vKind, vT;
      uniform float uTime;
      void main(){
        float d = length(vUv - 0.5) * 2.0;
        float a = pow(smoothstep(1.0, 0.0, d), 2.6);
        vec3 col = vec3(1.0, 0.15, 0.10);
        float amp = 0.55;
        if (vKind > 2.5) {                       // anti-collision strobe
          col = vec3(1.0, 0.98, 0.95);
          float ph = fract(uTime * 0.85);
          amp = 6.0 * (exp(-pow(ph * 26.0, 2.0)) + exp(-pow((ph - 0.09) * 26.0, 2.0)));
        } else if (vKind > 1.5) { col = vec3(1.0, 0.96, 0.88); amp = 0.45; }
        else if (vKind > 0.5)   { col = vec3(0.15, 1.0, 0.25); }
        // fade out while it is turning away at the ends of the leg
        a *= smoothstep(0.0, 0.06, vT) * smoothstep(1.0, 0.94, vT);
        gl_FragColor = vec4(col * amp, a * min(amp, 1.0));
      }`,
  });
  const mesh = new THREE.Mesh(g, mat);
  mesh.frustumCulled = false;
  mesh.renderOrder = 5;
  outsideScene.add(mesh);
  return mesh;
}
