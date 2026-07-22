// ─────────────────────────────────────────────────────────────────────────
//  The glyph field
//
//  A pure, deterministic description of «Кутузовский 12» seen as a field of
//  characters at twilight — a limestone-and-brass palazzo whose colonnade,
//  floor string-courses and warm ground-floor lobby are encoded as a
//  luminance field. The <GlyphField> component samples this to draw glyphs.
//  No React, no canvas here — just maths, so it can be reasoned about and
//  unit-checked on its own.
// ─────────────────────────────────────────────────────────────────────────

export const clamp01 = (x: number): number => (x < 0 ? 0 : x > 1 ? 1 : x);

export const mix = (a: number, b: number, t: number): number => a + (b - a) * t;

export const smoothstep = (edge0: number, edge1: number, x: number): number => {
  const t = clamp01((x - edge0) / (edge1 - edge0));
  return t * t * (3 - 2 * t);
};

// Cheap deterministic hash → [0, 1). Frame-independent, so renders are stable.
export const hash = (n: number): number => {
  const s = Math.sin(n * 127.1) * 43758.5453;
  return s - Math.floor(s);
};

export const hash2 = (x: number, y: number): number => {
  const s = Math.sin(x * 127.1 + y * 311.7) * 43758.5453;
  return s - Math.floor(s);
};

// Smooth value noise in [0, 1] built from the hash lattice.
export const valueNoise = (x: number, y: number): number => {
  const xi = Math.floor(x);
  const yi = Math.floor(y);
  const xf = x - xi;
  const yf = y - yi;
  const tl = hash2(xi, yi);
  const tr = hash2(xi + 1, yi);
  const bl = hash2(xi, yi + 1);
  const br = hash2(xi + 1, yi + 1);
  const u = xf * xf * (3 - 2 * xf);
  const v = yf * yf * (3 - 2 * yf);
  return mix(mix(tl, tr, u), mix(bl, br, u), v);
};

export type FieldOpts = {
  /** 0 → primordial noise, 1 → fully-resolved building. */
  form: number;
  /** 0 → field intact, 1 → central swath dimmed to let the title read. */
  part: number;
  /** Horizontal camera pan, in facade-widths. */
  panX: number;
  /** Seconds — drives shimmer, twinkle and drift. */
  t: number;
};

export type Sample = {
  /** Brightness 0..1. */
  lum: number;
  /** 0 → cool indigo sky, 1 → warm brass/lobby. */
  warm: number;
};

const BAYS = 9; // bays of clustered columns across the facade
const ROOF = 0.24; // v of the roofline; above is sky
const LOBBY_V = 0.9; // v centre of the warm ground-floor lobby glow

// Sample the field at normalised (u, v) — u∈[0,1] left→right, v∈[0,1] top→bottom.
export const sampleField = (u: number, v: number, o: FieldOpts): Sample => {
  const uu = u + o.panX; // pan the camera across the facade

  // ── Sky (above the roofline) ──────────────────────────────────────────
  if (v < ROOF) {
    // Deep indigo at the top warming toward the roofline horizon.
    const grad = smoothstep(0.0, ROOF, v);
    let lum = mix(0.035, 0.12, grad);
    // Warm dusk bleed rising behind the building's centre.
    const glow = smoothstep(0.55, 0.0, Math.abs(u - 0.5)) * smoothstep(0.0, ROOF, v);
    lum += glow * 0.1;
    // Sparse cool stars / distant Moscow-City windows.
    const star = hash2(Math.floor(uu * 220), Math.floor(v * 160));
    if (star > 0.995) lum += 0.5 * (0.6 + 0.4 * Math.sin(o.t * 3 + star * 40));
    return { lum: clamp01(lum), warm: clamp01(glow * 0.5) };
  }

  // ── Building facade ───────────────────────────────────────────────────
  // Vertical bay rhythm: bright brass columns, dark bronze glazing between.
  const bay = uu * BAYS;
  const colStripe = Math.pow(0.5 + 0.5 * Math.cos(bay * Math.PI * 2), 5);
  // Fine fluting inside each column shaft.
  const flute = 0.5 + 0.5 * Math.cos(bay * Math.PI * 2 * 6);
  let lum = 0.06 + colStripe * (0.55 + 0.12 * flute);
  let warm = 0.35 + colStripe * 0.5;

  // Floor string-courses — faint horizontal bands marking each storey.
  const floors = 11;
  const band = Math.pow(0.5 + 0.5 * Math.cos((v - ROOF) * floors * Math.PI * 2), 8);
  lum += band * 0.14;

  // Brass rings wrapping the columns at two heights per storey.
  const ring = Math.pow(0.5 + 0.5 * Math.cos((v - ROOF) * floors * 2 * Math.PI * 2), 16);
  lum += ring * colStripe * 0.35;
  warm += ring * colStripe * 0.4;

  // Warm interior windows behind the glazing — a few twinkle.
  const winX = Math.floor(uu * BAYS * 3.0);
  const winY = Math.floor((v - ROOF) * floors);
  const win = hash2(winX, winY);
  if (win > 0.82 && colStripe < 0.25) {
    const flick = 0.55 + 0.45 * Math.sin(o.t * 2.2 + win * 50);
    lum += (win - 0.82) * 3.2 * flick;
    warm = Math.max(warm, 0.8);
  }

  // The 9-metre warm-lit lobby glowing at the base, centre-frame.
  const dx = (u - 0.5) / 0.42;
  const dy = (v - LOBBY_V) / 0.16;
  const lobby = Math.exp(-(dx * dx + dy * dy) * 1.6);
  lum = mix(lum, 1.05, lobby * 0.85);
  warm = mix(warm, 1.0, lobby * 0.9);

  // Granite courtyard foreground fading to dark at the very bottom.
  lum *= mix(1.0, 0.35, smoothstep(0.95, 1.0, v));

  // ── Forming: dissolve in from primordial noise ────────────────────────
  const n = valueNoise(uu * 26 + o.t * 0.6, v * 26 - o.t * 0.4);
  const noiseLum = 0.05 + n * 0.55;
  const noiseWarm = 0.3 + n * 0.4;
  // Per-cell birth threshold → glyphs precipitate out of the noise.
  const birth = hash2(Math.floor(uu * 300), Math.floor(v * 300));
  const settled = smoothstep(birth * 0.9, birth * 0.9 + 0.25, o.form);
  lum = mix(noiseLum, lum, settled);
  warm = mix(noiseWarm, warm, settled);
  // A little residual flicker while still forming.
  lum += (1 - o.form) * (n - 0.5) * 0.25;

  // ── Living shimmer ────────────────────────────────────────────────────
  lum *= 0.93 + 0.07 * Math.sin(o.t * 1.6 + u * 9 + v * 5);

  // ── Part the field for the title (dim a central horizontal swath) ─────
  if (o.part > 0) {
    const swath = smoothstep(0.28, 0.44, v) * smoothstep(0.72, 0.56, v);
    lum *= 1 - o.part * swath * 0.86;
  }

  return { lum: clamp01(lum), warm: clamp01(warm) };
};

// ── Glyph ramp: sparse dark → dense bright ────────────────────────────────
// A calm, architectural ramp — sparse dots → letters → dense blocks. Letters
// in the mid-tones keep it reading as *text*, echoing the site's text-video.
export const RAMP = " .·:-=+co*x#%@";

export const glyphForLum = (lum: number): string => {
  if (lum < 0.06) return " ";
  const g = Math.pow(lum, 0.82);
  const i = Math.min(RAMP.length - 1, Math.max(0, Math.round(g * (RAMP.length - 1))));
  return RAMP[i];
};

// ── Colour: cool indigo ↔ warm brass, brightened by luminance ─────────────
type RGB = [number, number, number];
const lerp3 = (a: RGB, b: RGB, t: number): RGB => [
  mix(a[0], b[0], t),
  mix(a[1], b[1], t),
  mix(a[2], b[2], t),
];

// Cool ramp (indigo sky → pale steel) and warm ramp (bronze → lobby cream).
const COOL_LO: RGB = [22, 32, 54]; // #16203... deep indigo
const COOL_HI: RGB = [122, 138, 168];
const WARM_LO: RGB = [120, 92, 48]; // bronze shadow
const WARM_MID: RGB = [201, 163, 94]; // --gold
const WARM_HI: RGB = [244, 226, 184]; // warm lobby cream

export const colorFor = (lum: number, warm: number): RGB => {
  const cool = lerp3(COOL_LO, COOL_HI, lum);
  const warmCol =
    lum < 0.6
      ? lerp3(WARM_LO, WARM_MID, lum / 0.6)
      : lerp3(WARM_MID, WARM_HI, (lum - 0.6) / 0.4);
  return lerp3(cool, warmCol, warm);
};
