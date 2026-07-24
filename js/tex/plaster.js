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
   smooth fields are evaluated on an 80² grid and smoothly upsampled, the fine
   grain is band-passed white noise (blur is cheap), and pinholes/nibs are
   splatted per-element instead of sampled per-pixel.
   ========================================================================= */

const TAU = Math.PI * 2;
const LOW = 80;          // resolution of the smooth field grid
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

/**
 * LOW² -> size² for several fields at once, wrapping, smoothstep-interpolated
 * (C1, so the upsample leaves no quad creases in the normal map). Doing the
 * whole set in one pass amortises the index arithmetic, which dominates.
 */
function upsampleAll(N, lows, size) {
  const n = lows.length;
  const outs = [];
  for (let k = 0; k < n; k++) outs.push(N.newF(size * size));
  const s = LOW / size;
  const xi0 = new Int32Array(size), xi1 = new Int32Array(size), xtf = new Float32Array(size);
  for (let x = 0; x < size; x++) {
    const fx = (x + 0.5) * s - 0.5;
    const i0 = Math.floor(fx);
    let t = fx - i0;
    xtf[x] = t * t * (3 - 2 * t);
    xi0[x] = ((i0 % LOW) + LOW) % LOW;
    xi1[x] = (xi0[x] + 1) % LOW;
  }
  for (let y = 0; y < size; y++) {
    const fy = (y + 0.5) * s - 0.5;
    const j0 = Math.floor(fy);
    let ty = fy - j0;
    ty = ty * ty * (3 - 2 * ty);
    const r0 = (((j0 % LOW) + LOW) % LOW) * LOW;
    const r1 = ((((j0 + 1) % LOW) + LOW) % LOW) * LOW;
    const o = y * size;
    for (let k = 0; k < n; k++) {
      const lo = lows[k], out = outs[k];
      for (let x = 0; x < size; x++) {
        const a0 = xi0[x], a1 = xi1[x], tx = xtf[x];
        const p0 = lo[r0 + a0], p1 = lo[r0 + a1], q0 = lo[r1 + a0], q1 = lo[r1 + a1];
        const t0 = p0 + (p1 - p0) * tx;
        const t1 = q0 + (q1 - q0) * tx;
        out[o + x] = t0 + (t1 - t0) * ty;
      }
    }
  }
  return outs;
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
 * A float pass does not corrugate the wall — it leaves mostly flat ground
 * with a few pronounced ridges and hollows in it. Squashing everything below
 * `knee` toward zero turns the wavy profile into exactly that, and is what
 * stops the sweeps reading as corduroy.
 */
function sparsify(t, knee, top) {
  const o = new Float32Array(t.length);
  for (let i = 0; i < t.length; i++) {
    const v = t[i];
    const a = v < 0 ? -v : v;
    let s = (a - knee) / (top - knee);
    s = s < 0 ? 0 : s > 1 ? 1 : s;
    o[i] = v * s * s * (3 - 2 * s);
  }
  return o;
}

/**
 * A stroke is not infinitely long. Gating each pass along its own direction
 * (integer coefficients, so it still tiles) makes the ridges fade in and out
 * over a metre or so instead of running the full width of the wall, which is
 * what stopped the four passes reading as a woven lattice.
 */
function gateProfile(N, seed, harm) {
  const t = bandProfile(N, seed, harm);
  const g = new Float32Array(PROF + 1);
  for (let i = 0; i <= PROF; i++) {
    let s = (t[i] + 0.62) * 1.15;
    s = s < 0 ? 0 : s > 1 ? 1 : s;
    g[i] = 0.14 + 0.86 * (s * s * (3 - 2 * s));
  }
  return g;
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

/**
 * White noise per texel. Tileable for free (adjacent texels are uncorrelated
 * wherever you cut it) and, on an integer hash rather than the toolkit's
 * sin-based one, cheap enough to blur into a whole grain pyramid.
 */
function whiteNoise(N, size, seed) {
  const a = N.newF(size * size);
  const sh = Math.imul(seed | 0, 374761393) | 0;
  for (let y = 0; y < size; y++) {
    const yh = (Math.imul(y, 668265263) ^ sh) | 0;
    const o = y * size;
    for (let x = 0; x < size; x++) {
      let h = (Math.imul(x, 2246822519) ^ yh) | 0;
      h = Math.imul(h ^ (h >>> 15), 2654435761);
      h ^= h >>> 13;
      a[o + x] = (h >>> 8) * (1 / 16777216);
    }
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

/**
 * Separable wrapping box blur with a sliding window — O(1) per texel whatever
 * the radius, where the toolkit's blur() is O(r) with a modulo per tap. The
 * grain pyramid needs several of these at 512², so it is worth the 20 lines.
 */
function fastBlur(N, src, size, r) {
  if (r < 1) return src;
  const n = 2 * r + 1, inv = 1 / n;
  const tmp = N.newF(size * size), out = N.newF(size * size);
  for (let y = 0; y < size; y++) {
    const o = y * size;
    let s = 0;
    for (let k = -r; k <= r; k++) s += src[o + ((k % size) + size) % size];
    for (let x = 0; x < size; x++) {
      tmp[o + x] = s * inv;
      let add = x + r + 1; if (add >= size) add -= size;
      let sub = x - r; if (sub < 0) sub += size;
      s += src[o + add] - src[o + sub];
    }
  }
  for (let x = 0; x < size; x++) {
    let s = 0;
    for (let k = -r; k <= r; k++) s += tmp[(((k % size) + size) % size) * size + x];
    for (let y = 0; y < size; y++) {
      out[y * size + x] = s * inv;
      let add = y + r + 1; if (add >= size) add -= size;
      let sub = y - r; if (sub < 0) sub += size;
      s += tmp[add * size + x] - tmp[sub * size + x];
    }
  }
  return out;
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
  const warpA = lowField(N, (u, v) => N.fbm(u * 3, v * 3, 3, seed + 11, 2) - 0.5);
  const warpB = lowField(N, (u, v) => N.fbm(u * 3, v * 3, 3, seed + 47, 2) - 0.5);

  // lowField walks the grid in order, so (u,v) lands exactly on a cell
  const cell = (u, v) => (v * LOW | 0) * LOW + (u * LOW | 0);

  /* The wash itself: broad, soft, domain-warped so the patches are lobed the
     way brushed-on lime is rather than the round blobs raw fbm gives. */
  const cloud = lowField(N, (u, v) => {
    const i = cell(u, v);
    return N.fbm(u * cloudFreq + warpA[i] * 1.6, v * cloudFreq + warpB[i] * 1.6,
                 cloudFreq, seed + 23, cloudOct, 0.56) - 0.5;
  });

  /* Where the tool pressed / burnished — independent of the wash colour.
     Warped as well, because an unwarped low-octave value noise shows its own
     lattice as a grid of blobs, which reads instantly as CG. */
  const press = lowField(N, (u, v) => {
    const i = cell(u, v);
    return N.fbm(u * 5 + warpA[i] * 1.1, v * 5 + warpB[i] * 1.1,
                 5, seed + 91, 3, 0.55) - 0.5;
  });

  /* The float only bends its arcs gently, over a metre or more. Blurring the
     warp fields on the low grid (which is nearly free) gives that; the raw
     fbm is far too wiggly and folds the sweeps into burl-wood swirls. */
  const up = upsampleAll(N, [
    fastBlur(N, warpA, LOW, 6), fastBlur(N, warpB, LOW, 6), cloud, press,
  ], size);
  return {
    bendA: normField(up[0]), bendB: normField(up[1]),
    cloud: normField(up[2]), press: normField(up[3]),
  };
}

/* ------------------------------------------------------------------- walls */

export function plasterWall(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px), ao = N.newF(px);

  const B = limeBase(N, size, 1700, 4, 3);

  /* --- trowel passes -------------------------------------------------- */
  /* Each pass is a 1-D band profile read along a sheared, warped coordinate.
     ax/ay are integers so the whole thing still tiles; the warp bends the
     bands into the long shallow arcs a float actually leaves. Few bands per
     pass and a warp worth a fifth of a band, so the arcs wander instead of
     marching. Coverage masks come from the bend fields that already exist —
     free, and correlated with the wash, which is how a real pass and the wash
     it carries actually relate. */
  const PASS = [
    // ax, ay, warp, amp, harmonics, profSeed, maskAngle, maskBias
    [1, 3, 0.20, 1.00, 5, 5, 0.00, 0.05],
    [-1, 4, 0.17, 0.85, 6, 19, 2.10, -0.05],
    [3, 2, 0.22, 0.66, 4, 41, 4.20, -0.12],
    [2, -3, 0.19, 0.58, 5, 63, 5.40, -0.02],
  ];
  const NP = PASS.length;
  const profs = PASS.map((p) => sparsify(bandProfile(N, p[5], p[4]), 0.44, 0.96));
  const edges = PASS.map((p) => edgeProfile(bandProfile(N, p[5], p[4])));
  const gates = PASS.map((p) => gateProfile(N, p[5] + 137, 3));
  // flat typed params — the inner loop runs 262 k x 4 times, so avoid the
  // array-of-arrays lookups in there
  const pAx = new Float64Array(NP), pAy = new Float64Array(NP);
  const pWarp = new Float64Array(NP), pAmp = new Float64Array(NP);
  const mCos = new Float64Array(NP), mSin = new Float64Array(NP), mBias = new Float64Array(NP);
  for (let k = 0; k < NP; k++) {
    pAx[k] = PASS[k][0]; pAy[k] = PASS[k][1];
    pWarp[k] = PASS[k][2]; pAmp[k] = PASS[k][3];
    mCos[k] = Math.cos(PASS[k][6]); mSin[k] = Math.sin(PASS[k][6]);
    mBias[k] = PASS[k][7] + 0.55;
  }
  const mRaw = new Float64Array(NP);
  const rowY = new Float64Array(NP), rowGx = new Float64Array(NP);

  /* --- fine scales ---------------------------------------------------- */
  const wn = whiteNoise(N, size, 4211);
  const b1 = fastBlur(N, wn, size, 2);
  const b2 = fastBlur(N, b1, size, 6);
  const b3 = fastBlur(N, b2, size, 14);
  const fine = bandOf(N, wn, b1, 1.0);                   // ~1 texel, 6 mm sand
  const agg = normField(bandOf(N, b1, b2, 1.0), 0.5);    // ~2 cm aggregate clumps
  const mot = normField(bandOf(N, b2, b3, 1.0), 0.5);    // ~10 cm skin mottle

  const pin = N.newF(px);                                // pinholes / bubble craters
  scatterDiscs(N, pin, size, 64, 8801, 0.13, 0.8, 2.2, 0.5, 1.0);
  const nib = N.newF(px);                                // nibs, lime lumps, grit
  scatterDiscs(N, nib, size, 26, 3307, 0.09, 1.1, 3.0, 0.4, 1.0);

  /* --- composite ------------------------------------------------------ */
  /* Lime does drift warm/cool with thickness, but only just — a wall that
     swings between beige and blue-grey reads as a watercolour wash, not as
     plaster, and the scene tints this with material.color anyway. Keep the
     total R-B swing inside a few percent. */
  const TINT_W = [1.012, 1.000, 0.982];                  // warm ochre-ish lime
  const TINT_C = [0.995, 0.999, 1.008];                  // cool grey lime
  const BASE_V = 0.645;

  for (let y = 0; y < size; y++) {
    const v = y / size;
    for (let k = 0; k < NP; k++) { rowY[k] = v * pAy[k]; rowGx[k] = v * pAx[k]; }
    for (let x = 0; x < size; x++) {
      const i = y * size + x;
      const u = x / size;

      const wa = B.bendA[i], wb = B.bendB[i];

      // ---- trowel relief
      const bend = wa * 1.6 + wb * 0.9;
      const bendG = bend * 0.11;
      /* A plasterer works one direction at a time. Letting all four passes
         blanket the wall produced a woven lattice — the clearest CG tell in
         the whole material. Keeping only the leading pass plus whatever is
         within `DOM` of it leaves one dominant sweep with a faint ghost of an
         earlier one, which is what a floated wall actually looks like. */
      let mmax = -1e9;
      for (let k = 0; k < NP; k++) {
        const m = (wa * mCos[k] + wb * mSin[k]) * 3.0 + mBias[k];
        mRaw[k] = m;
        if (m > mmax) mmax = m;
      }
      let sweep = 0, edge = 0, wsum = 0;
      for (let k = 0; k < NP; k++) {
        let m = (mRaw[k] - mmax + 0.42) * 2.38;
        if (m <= 0.02) continue;
        if (m > 1) m = 1;
        m = m * m * (3 - 2 * m);
        // gate along the stroke, so a ridge is a stroke and not a wire
        let gc = rowGx[k] - u * pAy[k] + bendG;
        gc = (gc - Math.floor(gc)) * PROF;
        const gi = gc | 0, gf = gc - gi, ga = gates[k];
        const w = m * (ga[gi] + (ga[gi + 1] - ga[gi]) * gf) * pAmp[k];
        // one index shared by the ridge profile and its lift-off line
        let Y = u * pAx[k] + rowY[k] + bend * pWarp[k];
        Y = (Y - Math.floor(Y)) * PROF;
        const yi = Y | 0, yf = Y - yi, pa = profs[k], ea = edges[k];
        sweep += (pa[yi] + (pa[yi + 1] - pa[yi]) * yf) * w;
        edge += (ea[yi] + (ea[yi + 1] - ea[yi]) * yf) * w;
        wsum += w;
      }
      if (wsum > 1e-4) { sweep /= Math.max(1, wsum * 0.55); edge /= Math.max(1, wsum); }

      const cl0 = B.cloud[i];                // -0.5..0.5 wash cloudiness
      const pr = B.press[i];                 // -0.5..0.5 tool pressure
      const ag = agg[i], fi = fine[i], mo = mot[i];
      const ph = pin[i], nb = nib[i];

      /* Brush-applied lime does not fade smoothly — each loaded pass leaves a
         soft-edged lap. Steepening the cloud around zero gives those laps
         without needing a second noise field. */
      let s = (cl0 + 0.30) * 1.9;
      s = s < 0 ? 0 : s > 1 ? 1 : s;
      const lap = s * s * (3 - 2 * s) - 0.5;
      const cl = cl0 * 0.72 + lap * 0.30;

      // ---- height: metres of gentle undulation, decimetres of float pass,
      //      millimetres of grain, then the pits and nibs on top
      let h = 0.5
        + wa * 0.090                          // the wall itself is not flat
        + cl * 0.040
        + sweep * 0.046
        - edge * 0.021
        + mo * 0.034
        + ag * 0.040
        + fi * 0.012
        + nb * 0.050
        - ph * 0.070;
      h = h < 0 ? 0 : h > 1 ? 1 : h;
      height[i] = h;

      // ---- how proud of the local surface this texel sits: burnish happens
      //      on the high side of a pass, starvation in the hollows
      const prom = sweep * 0.55 + pr * 0.9;

      // ---- ambient occlusion, from the things that actually occlude
      const occ = 1 - ph * 0.11 - edge * 0.05 - Math.max(0, -sweep) * 0.035
                    - Math.max(0, -ag) * 0.05;
      ao[i] = occ < 0.78 ? 0.78 : occ;

      // ---- albedo. Lime is thicker/whiter where it pooled and where the
      //      tool burnished it, thinner and greyer where it was dragged off.
      let val = BASE_V
        + cl * 0.062                          // the main limewash cloudiness
        + wa * 0.030                          // very broad tonal drift
        + pr * 0.024                          // wash density under the tool
        + mo * 0.022                          // 10 cm skin mottle
        + sweep * 0.009
        + prom * 0.010
        - edge * 0.006                        // a lift-off line, not a scratch
        + ag * 0.028
        + fi * 0.014
        + nb * 0.030
        - ph * 0.015;                         // pits read by shading, not paint
      if (val < 0.36) val = 0.36; else if (val > 0.80) val = 0.80;

      // hue drifts very slightly warm/cool with the wash, never far
      let t = 0.5 + (cl * 0.7 + wb * 0.5 + pr * 0.3);
      t = t < 0 ? 0 : t > 1 ? 1 : t;
      const r = (TINT_C[0] + (TINT_W[0] - TINT_C[0]) * t) * val;
      const g = (TINT_C[1] + (TINT_W[1] - TINT_C[1]) * t) * val;
      const b = (TINT_C[2] + (TINT_W[2] - TINT_C[2]) * t) * val;
      albedo[i * 3] = r; albedo[i * 3 + 1] = g; albedo[i * 3 + 2] = b;

      // ---- roughness. This is what sells it: burnished high points get a
      //      touch of sheen, hollows and pinholes stay chalk.
      let ro = 0.902
        - pr * 0.058                          // burnished where the tool worked
        - sweep * 0.020                       // ridge crests take the polish
        + Math.max(0, -sweep) * 0.022         // starved hollows stay chalk
        + ph * 0.070                          // pinhole interiors
        + edge * 0.026
        - ag * 0.026                          // proud grains get flattened
        - mo * 0.024
        + fi * 0.034                          // micro tooth
        - nb * 0.020
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
  const B = limeBase(N, size, 5300, 3, 3);

  const wn = whiteNoise(N, size, 9137);
  const c1 = fastBlur(N, wn, size, 1);
  const c2 = fastBlur(N, c1, size, 5);
  const c3 = fastBlur(N, c2, size, 13);
  const fine = bandOf(N, wn, c1, 1.0);
  const agg = normField(bandOf(N, c1, c2, 1.0), 0.5);
  const mot = normField(bandOf(N, c2, c3, 1.0), 0.5);

  /* Spray stipple: a dense field of very small, very shallow blobs, then
     centred so it reads as texture rather than a bias. Two grades, because a
     sprayed then knocked-back skim has both a fine peppering and a slightly
     coarser one. Kept well under the wall's trowel relief, or the ceiling
     turns into orange peel. */
  const stip = N.newF(px);
  scatterDiscs(N, stip, size, 190, 6421, 0.50, 0.9, 2.2, 0.45, 1.0);
  scatterDiscs(N, stip, size, 78, 1553, 0.22, 1.4, 3.6, 0.30, 0.70);
  normField(stip, 0.5);
  // a few pinholes where the skim bridged a bubble
  const pin = N.newF(px);
  scatterDiscs(N, pin, size, 60, 2711, 0.10, 0.8, 2.0, 0.5, 1.0);

  // long, very shallow sanding sweeps from the pole sander — almost invisible,
  // sparse, and masked to the patches the sander actually touched
  const sandProf = sparsify(bandProfile(N, 77, 11), 0.40, 0.95);

  // same discipline as the wall: a couple of percent of hue drift, no more
  const TINT_W = [1.008, 1.000, 0.988];
  const TINT_C = [0.993, 0.999, 1.010];
  const BASE_V = 0.598;

  for (let y = 0; y < size; y++) {
    const v = y / size;
    for (let x = 0; x < size; x++) {
      const i = y * size + x;
      const u = x / size;

      const wa = B.bendA[i], wb = B.bendB[i];
      const cl = B.cloud[i], pr = B.press[i];
      const ag = agg[i], fi = fine[i], mo = mot[i], st = stip[i], ph = pin[i];

      /* The sander touched patches, not the whole ceiling. Biased well below
         zero and smoothstepped so it clears most of the tile — left blanket it
         gave the whole ceiling one coherent diagonal grain, which reads as a
         brushed surface rather than a skim. */
      let sm = (wa * 0.7 + wb * 2.4) * 1.6 - 0.16;
      sm = sm < 0 ? 0 : sm > 1 ? 1 : sm;
      sm = sm * sm * (3 - 2 * sm);
      const sand = sm > 0.01
        ? prof(sandProf, u * 2 + v * 7 + (wa * 1.4 + wb * 0.8) * 0.22) * sm
        : 0;

      let h = 0.5
        + wa * 0.045
        + cl * 0.020
        + st * 0.026
        + mo * 0.024
        + sand * 0.017
        + ag * 0.015
        + fi * 0.009
        - ph * 0.055;
      h = h < 0 ? 0 : h > 1 ? 1 : h;
      height[i] = h;

      const occ = 1 - ph * 0.12 - Math.max(0, -st) * 0.03;
      ao[i] = occ < 0.84 ? 0.84 : occ;

      let val = BASE_V
        + cl * 0.028
        + wa * 0.017
        + pr * 0.018
        + st * 0.013
        + mo * 0.020
        + ag * 0.017
        + fi * 0.011
        - ph * 0.014;
      if (val < 0.34) val = 0.34; else if (val > 0.78) val = 0.78;

      // sits a touch cooler than the wall on average
      let t = 0.42 + (cl * 0.6 + wb * 0.45);
      t = t < 0 ? 0 : t > 1 ? 1 : t;
      albedo[i * 3] = (TINT_C[0] + (TINT_W[0] - TINT_C[0]) * t) * val;
      albedo[i * 3 + 1] = (TINT_C[1] + (TINT_W[1] - TINT_C[1]) * t) * val;
      albedo[i * 3 + 2] = (TINT_C[2] + (TINT_W[2] - TINT_C[2]) * t) * val;

      let ro = 0.935
        - pr * 0.045
        - st * 0.030
        + ph * 0.055
        + ag * 0.024
        - mo * 0.020
        + fi * 0.018
        + Math.abs(sand) * 0.018
        - cl * 0.014;
      rough[i] = ro < 0.86 ? 0.86 : ro > 0.985 ? 0.985 : ro;
    }
  }

  return { albedo, rough, height, ao };
}
