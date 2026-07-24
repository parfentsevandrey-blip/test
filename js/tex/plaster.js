/* =========================================================================
   Hand-troweled limewash plaster — walls and ceiling.

   One tile is authored for a 3.3 m x 3.3 m patch of wall (see PLASTER_TILE in
   room-interior.js), so at 512² a texel is ~6.4 mm. Everything below is sized
   against that: trowel sweeps are 10-30 cm bands, aggregate grain is ~1 cm,
   pinholes are 1-3 texels across.

   The whole material is deliberately quiet. Limewash on a wall is a few
   percent of tonal drift, a millimetre of trowel relief and a lot of very
   fine roughness variation; push any of it and it turns into stucco. The
   realism has to come from the three scales agreeing with each other:

     - metres : broad cloudiness of the wash + a wall that is not dead flat
     - decimetres : overlapping float passes, burnished on the high side,
                    slightly starved and darker in the hollows
     - millimetres : lime aggregate, pinholes, the odd nib

   Cost discipline: the sin-based hashes in noise.js are expensive, so all the
   smooth fields are evaluated on a 96² grid and smoothly upsampled, the fine
   grain is band-passed white noise (blur is cheap), and pinholes/nibs are
   splatted per-element instead of sampled per-pixel.
   ========================================================================= */

const TAU = Math.PI * 2;
const LOW = 96;          // resolution of the smooth field grid
const PROF = 1024;       // resolution of the 1-D trowel profiles

/* ---------------------------------------------------------------- helpers */

/** evaluate fn(u,v) on a LOW² tileable grid */
function lowField(N, fn) {
  const a = N.newF(LOW * LOW);
  for (let y = 0; y < LOW; y++) {
    const v = y / LOW;
    for (let x = 0; x < LOW; x++) a[y * LOW + x] = fn(x / LOW, v);
  }
  return a;
}

/** LOW² -> size², wrapping, smoothstep-interpolated (C1, so no quad creases) */
function upsample(N, low, size) {
  const out = N.newF(size * size);
  const s = LOW / size;
  // per-column tables so the inner loop is pure arithmetic
  const xi0 = new Int32Array(size), xi1 = new Int32Array(size), xtf = new Float32Array(size);
  for (let x = 0; x < size; x++) {
    const fx = (x + 0.5) * s - 0.5;
    const i0 = Math.floor(fx);
    let t = fx - i0;
    t = t * t * (3 - 2 * t);
    xi0[x] = ((i0 % LOW) + LOW) % LOW;
    xi1[x] = (xi0[x] + 1) % LOW;
    xtf[x] = t;
  }
  for (let y = 0; y < size; y++) {
    const fy = (y + 0.5) * s - 0.5;
    const j0 = Math.floor(fy);
    let ty = fy - j0;
    ty = ty * ty * (3 - 2 * ty);
    const r0 = (((j0 % LOW) + LOW) % LOW) * LOW;
    const r1 = ((((j0 + 1) % LOW) + LOW) % LOW) * LOW;
    const o = y * size;
    for (let x = 0; x < size; x++) {
      const a0 = xi0[x], a1 = xi1[x], tx = xtf[x];
      const t0 = low[r0 + a0] + (low[r0 + a1] - low[r0 + a0]) * tx;
      const t1 = low[r1 + a0] + (low[r1 + a1] - low[r1 + a0]) * tx;
      out[o + x] = t0 + (t1 - t0) * ty;
    }
  }
  return out;
}

/**
 * A 1-periodic pink-ish 1-D signal in -1..1. Used as the cross-section of a
 * float pass: bands of uneven width and depth rather than a sine wave.
 */
function bandProfile(N, seed, harm) {
  const amp = new Float64Array(harm), ph = new Float64Array(harm);
  for (let h = 1; h <= harm; h++) {
    amp[h - 1] = (0.4 + 0.6 * N.hash2(h * 3.1, 7.3, seed)) / Math.pow(h, 1.05);
    ph[h - 1] = N.hash2(h * 1.7, 9.1, seed + 31) * TAU;
  }
  const t = new Float32Array(PROF + 1);
  let mn = 1e9, mx = -1e9;
  for (let i = 0; i < PROF; i++) {
    const x = (i / PROF) * TAU;
    let s = 0;
    for (let h = 1; h <= harm; h++) s += amp[h - 1] * Math.sin(x * h + ph[h - 1]);
    t[i] = s;
    if (s < mn) mn = s;
    if (s > mx) mx = s;
  }
  const inv = 2 / (mx - mn || 1);
  for (let i = 0; i < PROF; i++) t[i] = (t[i] - mn) * inv - 1;
  t[PROF] = t[0];
  return t;
}

/**
 * Where a float pass lifts off you get a faint darker line on the trailing
 * side. That is the steepest falling part of the profile, so derive it.
 */
function edgeProfile(prof) {
  const e = new Float32Array(PROF + 1);
  let mx = 1e-9;
  for (let i = 0; i < PROF; i++) {
    const d = prof[i + 1] - prof[i];
    const v = d < 0 ? -d : 0;
    e[i] = v;
    if (v > mx) mx = v;
  }
  for (let i = 0; i < PROF; i++) {
    const u = e[i] / mx;
    const u2 = u * u;
    e[i] = u2 * u2;                       // only the very steepest survives
  }
  e[PROF] = e[0];
  return e;
}

/** table lookup at a real coordinate, 1-periodic */
function prof(t, x) {
  let f = (x - Math.floor(x)) * PROF;
  const i = f | 0;
  f -= i;
  return t[i] + (t[i + 1] - t[i]) * f;
}

/** white noise per texel — tileable for free, and 20x cheaper than gnoise */
function whiteNoise(N, size, seed) {
  const a = N.newF(size * size);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) a[y * size + x] = N.hash2(x, y, seed);
  }
  return a;
}

/**
 * Scatter soft discs into a field. Used for pinholes (negative amp) and for
 * nibs/lumps of unslaked lime (positive amp). Wrapping, so it tiles.
 */
function scatterDiscs(N, field, size, cells, seed, prob, rMin, rMax, aMin, aMax) {
  const cs = size / cells;
  for (let cy = 0; cy < cells; cy++) {
    for (let cx = 0; cx < cells; cx++) {
      if (N.hash2(cx, cy, seed) > prob) continue;
      const jx = N.hash2(cx + 0.3, cy + 4.7, seed + 11);
      const jy = N.hash2(cx + 8.1, cy + 1.9, seed + 11);
      const px = (cx + jx) * cs, py = (cy + jy) * cs;
      const rr = N.hash2(cx + 5.3, cy + 2.1, seed + 29);
      const r = rMin + (rMax - rMin) * rr * rr;      // mostly small, a few big
      const a = aMin + (aMax - aMin) * N.hash2(cx + 1.1, cy + 7.7, seed + 61);
      const x0 = Math.floor(px - r), x1 = Math.ceil(px + r);
      const y0 = Math.floor(py - r), y1 = Math.ceil(py + r);
      for (let y = y0; y <= y1; y++) {
        const wy = ((y % size) + size) % size;
        const dy = y + 0.5 - py;
        for (let x = x0; x <= x1; x++) {
          const dx = x + 0.5 - px;
          const d = Math.sqrt(dx * dx + dy * dy);
          if (d >= r) continue;
          let f = 1 - d / r;
          f = f * f * (3 - 2 * f);
          const i = wy * size + ((x % size) + size) % size;
          const nv = a * f;
          if (nv > field[i]) field[i] = nv;          // max, so overlaps stay flat
        }
      }
    }
  }
}

/** difference of two blur levels = one octave of band-limited grain */
function bandOf(N, a, b, gain) {
  const out = N.newF(a.length);
  for (let i = 0; i < out.length; i++) out[i] = (a[i] - b[i]) * gain;
  return out;
}

/** normalise a zero-ish-mean field to roughly -0.5..0.5 by its own spread */
function normField(field, target = 0.5) {
  let mean = 0;
  for (let i = 0; i < field.length; i++) mean += field[i];
  mean /= field.length;
  let sd = 0;
  for (let i = 0; i < field.length; i++) { const d = field[i] - mean; sd += d * d; }
  sd = Math.sqrt(sd / field.length) || 1e-6;
  const k = target / (sd * 2.2);            // ~2.2 sigma maps to the target edge
  for (let i = 0; i < field.length; i++) {
    const v = (field[i] - mean) * k;
    field[i] = v < -target ? -target : v > target ? target : v;
  }
  return field;
}

/* -------------------------------------------------------- shared substrate */

/**
 * The parts every lime finish shares: a wall that is not dead flat, broad
 * wash cloudiness, a pigment/temperature drift, and a "pressure" field that
 * says where the tool worked the surface hardest.
 */
function limeBase(N, size, seed, cloudFreq, cloudOct) {
  // two cheap warp fields, reused by everything else
  const warpA = lowField(N, (u, v) => N.fbm(u * 3, v * 3, 3, seed + 11, 3) - 0.5);
  const warpB = lowField(N, (u, v) => N.fbm(u * 3, v * 3, 3, seed + 47, 3) - 0.5);

  const cloud = lowField(N, (u, v) => {
    const i = ((Math.floor(v * LOW) % LOW) + LOW) % LOW * LOW +
              ((Math.floor(u * LOW) % LOW) + LOW) % LOW;
    const wx = warpA[i], wy = warpB[i];
    return N.fbm(u * cloudFreq + wx * 1.6, v * cloudFreq + wy * 1.6,
                 cloudFreq, seed + 23, cloudOct, 0.56) - 0.5;
  });

  // where the tool pressed / burnished: independent of the wash colour
  const press = lowField(N, (u, v) => N.fbm(u * 5, v * 5, 5, seed + 91, 3, 0.5) - 0.5);

  return {
    warpA: normField(upsample(N, warpA, size)),
    warpB: normField(upsample(N, warpB, size)),
    cloud: normField(upsample(N, cloud, size)),
    press: normField(upsample(N, press, size)),
  };
}

/* ------------------------------------------------------------------- walls */

export function plasterWall(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px), ao = N.newF(px);

  const B = limeBase(N, size, 1700, 4, 4);

  /* --- trowel passes -------------------------------------------------- */
  /* Each pass is a 1-D band profile read along a sheared, warped coordinate.
     ax/ay are integers so the whole thing still tiles; the warp bends the
     bands into the long shallow arcs a float actually leaves. Four passes at
     different angles, each masked to a region, so they overlap the way a
     plasterer's passes do rather than covering the wall uniformly. */
  const PASS = [
    // ax,  ay,  warp, amp,  maskSeed, maskBias, harmonics, profSeed
    [1, 3, 0.30, 1.00, 3.1, 0.15, 9, 5],
    [-1, 4, 0.26, 0.85, 7.7, 0.05, 11, 19],
    [2, 5, 0.20, 0.60, 2.3, -0.05, 13, 41],
    [1, -2, 0.34, 0.70, 5.9, 0.10, 7, 63],
  ];
  const profs = PASS.map((p) => bandProfile(N, p[7], p[6]));
  const edges = profs.map((p) => edgeProfile(p));
  // per-pass coverage masks, cheap low-res
  const masks = PASS.map((p) =>
    upsample(N, lowField(N, (u, v) =>
      N.clamp((N.fbm(u * 2, v * 2, 2, p[4] * 100, 2) - 0.5) * 2.1 + 0.55 + p[5], 0, 1)), size));

  /* --- fine scales ---------------------------------------------------- */
  const wn = whiteNoise(N, size, 4211);
  const fine = grainBand(N, wn, size, 0, 2, 2.1);        // ~1 texel sand
  const agg = grainBand(N, wn, size, 2, 6, 5.6);         // ~1.5 cm aggregate clumps

  const pin = N.newF(px);                                // pinholes / bubble craters
  scatterDiscs(N, pin, size, 72, 8801, 0.20, 0.9, 2.6, 0.55, 1.0);
  const nib = N.newF(px);                                // nibs, lime lumps, grit
  scatterDiscs(N, nib, size, 30, 3307, 0.10, 1.2, 3.4, 0.4, 1.0);

  /* --- composite ------------------------------------------------------ */
  const TINT_W = [1.048, 0.998, 0.921];                  // warm ochre-ish lime
  const TINT_C = [0.976, 0.996, 1.033];                  // cool grey lime
  const BASE_V = 0.645;

  for (let y = 0; y < size; y++) {
    const v = y / size;
    for (let x = 0; x < size; x++) {
      const i = y * size + x;
      const u = x / size;

      const wa = B.warpA[i], wb = B.warpB[i];

      // ---- trowel relief
      let sweep = 0, edge = 0, wsum = 0;
      for (let k = 0; k < 4; k++) {
        const p = PASS[k];
        const m = masks[k][i];
        if (m <= 0.001) continue;
        const Y = u * p[0] + v * p[1] + (wa * 1.5 + wb * 0.9) * p[2];
        const w = m * p[3];
        sweep += prof(profs[k], Y) * w;
        edge += prof(edges[k], Y) * w;
        wsum += w;
      }
      if (wsum > 1e-4) { sweep /= Math.max(1, wsum * 0.72); edge /= Math.max(1, wsum); }

      const cl = B.cloud[i];                 // -0.5..0.5 wash cloudiness
      const pr = B.press[i];                 // -0.5..0.5 tool pressure
      const ag = agg[i], fi = fine[i];
      const ph = pin[i], nb = nib[i];

      // ---- height: metres of gentle undulation, decimetres of float pass,
      //      millimetres of grain, then the pits and nibs on top
      let h = 0.5
        + wa * 0.070                          // the wall itself is not flat
        + cl * 0.035
        + sweep * 0.052
        - edge * 0.022
        + ag * 0.016
        + fi * 0.009
        + nb * 0.055
        - ph * 0.085;
      h = h < 0 ? 0 : h > 1 ? 1 : h;
      height[i] = h;

      // ---- how proud of the local surface this texel sits: burnish happens
      //      on the high side of a pass, starvation in the hollows
      const prom = sweep * 0.55 + pr * 0.9;

      // ---- ambient occlusion, from the things that actually occlude
      const occ = 1 - ph * 0.34 - edge * 0.10 - Math.max(0, -sweep) * 0.045;
      ao[i] = occ < 0.6 ? 0.6 : occ;

      // ---- albedo. Lime is thicker/whiter where it pooled and where the
      //      tool burnished it, thinner and greyer where it was dragged off.
      let val = BASE_V
        + cl * 0.075                          // the main limewash cloudiness
        + wa * 0.026                          // very broad tonal drift
        + sweep * 0.030
        + prom * 0.022
        - edge * 0.055
        + ag * 0.020
        + fi * 0.011
        + nb * 0.045
        - ph * 0.075;
      if (val < 0.34) val = 0.34; else if (val > 0.80) val = 0.80;

      // hue drifts very slightly warm/cool with the wash, never far
      let t = 0.5 + (cl * 1.1 + wb * 0.7);
      t = t < 0 ? 0 : t > 1 ? 1 : t;
      const r = (TINT_C[0] + (TINT_W[0] - TINT_C[0]) * t) * val;
      const g = (TINT_C[1] + (TINT_W[1] - TINT_C[1]) * t) * val;
      const b = (TINT_C[2] + (TINT_W[2] - TINT_C[2]) * t) * val;
      albedo[i * 3] = r; albedo[i * 3 + 1] = g; albedo[i * 3 + 2] = b;

      // ---- roughness. This is what sells it: burnished high points get a
      //      touch of sheen, hollows and pinholes stay chalk.
      let ro = 0.900
        - prom * 0.070                        // polished by the float
        + Math.max(0, -sweep) * 0.030         // starved hollows
        + ph * 0.075                          // pinhole interiors
        + edge * 0.030
        + ag * 0.028
        + fi * 0.020
        - nb * 0.015
        - cl * 0.020;                         // thicker wash = slightly tighter
      rough[i] = ro < 0.82 ? 0.82 : ro > 0.972 ? 0.972 : ro;
    }
  }

  return { albedo, rough, height, ao };
}

/* ----------------------------------------------------------------- ceiling */

export function plasterCeiling(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px), ao = N.newF(px);

  // same family, but a sprayed + skimmed finish: flatter, finer, no float arcs
  const B = limeBase(N, size, 5300, 3, 4);

  const wn = whiteNoise(N, size, 9137);
  const fine = grainBand(N, wn, size, 0, 2, 2.3);
  const agg = grainBand(N, wn, size, 1, 4, 4.2);

  // spray stipple: a dense field of very small, very shallow blobs. Kept an
  // order of magnitude below the wall's trowel relief or it reads as orange peel.
  const stip = N.newF(px);
  scatterDiscs(N, stip, size, 150, 6421, 0.42, 1.0, 3.0, 0.35, 1.0);
  // a few faint sanding sweeps left by the pole sander, and pinholes
  const pin = N.newF(px);
  scatterDiscs(N, pin, size, 60, 2711, 0.10, 0.8, 2.0, 0.5, 1.0);

  // long, very shallow sanding scratches — one masked pass, almost invisible
  const sandProf = bandProfile(N, 77, 15);
  const sandMask = upsample(N, lowField(N, (u, v) =>
    N.clamp((N.fbm(u * 2, v * 2, 2, 611, 2) - 0.5) * 2.4 + 0.25, 0, 1)), size);

  const TINT_W = [1.024, 1.000, 0.960];
  const TINT_C = [0.972, 0.996, 1.040];
  const BASE_V = 0.598;

  for (let y = 0; y < size; y++) {
    const v = y / size;
    for (let x = 0; x < size; x++) {
      const i = y * size + x;
      const u = x / size;

      const wa = B.warpA[i], wb = B.warpB[i];
      const cl = B.cloud[i], pr = B.press[i];
      const ag = agg[i], fi = fine[i], st = stip[i], ph = pin[i];

      const sm = sandMask[i];
      const sand = sm > 0.001
        ? prof(sandProf, u * 3 + v * 8 + (wa * 1.1 + wb * 0.6) * 0.18) * sm
        : 0;

      let h = 0.5
        + wa * 0.045
        + cl * 0.024
        + st * 0.020
        + sand * 0.012
        + ag * 0.013
        + fi * 0.008
        - ph * 0.055;
      h = h < 0 ? 0 : h > 1 ? 1 : h;
      height[i] = h;

      const occ = 1 - ph * 0.26 - Math.max(0, -st) * 0.03;
      ao[i] = occ < 0.7 ? 0.7 : occ;

      let val = BASE_V
        + cl * 0.055
        + wa * 0.020
        + pr * 0.018
        + st * 0.016
        + ag * 0.016
        + fi * 0.010
        - ph * 0.055;
      if (val < 0.34) val = 0.34; else if (val > 0.78) val = 0.78;

      let t = 0.5 + (cl * 0.9 + wb * 0.6);
      t = t < 0 ? 0 : t > 1 ? 1 : t;
      albedo[i * 3] = (TINT_C[0] + (TINT_W[0] - TINT_C[0]) * t) * val;
      albedo[i * 3 + 1] = (TINT_C[1] + (TINT_W[1] - TINT_C[1]) * t) * val;
      albedo[i * 3 + 2] = (TINT_C[2] + (TINT_W[2] - TINT_C[2]) * t) * val;

      let ro = 0.935
        - pr * 0.045
        - st * 0.022
        + ph * 0.055
        + ag * 0.024
        + fi * 0.018
        + Math.abs(sand) * 0.012
        - cl * 0.014;
      rough[i] = ro < 0.86 ? 0.86 : ro > 0.985 ? 0.985 : ro;
    }
  }

  return { albedo, rough, height, ao };
}
