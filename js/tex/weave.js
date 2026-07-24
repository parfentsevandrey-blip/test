/* =========================================================================
   Upholstery fabrics: plain-weave linen, looped boucle, hand-knit wool.

   All three are built the same way, which is why they share one file:

     * a "layer" is a set of parallel per-pixel fields (height, per-yarn tone,
       cross-section crown, ply/aux, kind) that yarn is MAX-blended into, so
       whichever strand is physically highest at a texel also owns the colour
       and the sheen there.  That single rule is what gives real over/under
       occlusion instead of two patterns cross-faded together;

     * yarn is drawn as capsules (stampYarn), curls of capsules (stampCurl) or
       squashed ellipsoids (stampLoop), all with a round cross-section, so the
       normal map shows actual fibre cylinders rather than embossed noise;

     * everything is stamped with wrapped pixel indices and every noise
       source has an integer period, so the tiles are seamless.

   Three scales are present in each: a metre-scale tonal/sheen drift, the
   centimetre-scale weave or stitch, and a millimetre-scale fibre fuzz that
   perturbs height, albedo and roughness together.
   ========================================================================= */

const TAU = Math.PI * 2;

/* ------------------------------------------------------------------ hashing
   N.hash2 is Math.sin-based and fine for the few thousand per-thread draws,
   but the per-pixel fibre fields need millions of samples — hence a cheap
   deterministic integer hash for those.  Still seed-driven, still stable. */
function ihash(x, y, s) {
  let h = (Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263) + Math.imul(s | 0, 1274126177)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
const wrapi = (v, p) => ((v % p) + p) % p;
/* stamps never reach further than one tile outside, so the inner loops can
   skip the modulo — it is measurably the most expensive thing in there */
const wrap1 = (v, p) => (v < 0 ? v + p : v >= p ? v - p : v);

/* cos(TAU * turns) from a table — the ply twist needs millions of these */
const COS_N = 2048;
const COS_LUT = new Float32Array(COS_N + 1);
for (let i = 0; i <= COS_N; i++) COS_LUT[i] = Math.cos((i / COS_N) * TAU);
const cosT = (turns) => COS_LUT[((turns - Math.floor(turns)) * COS_N) | 0];

/** tileable value noise on the fast hash; `period` in lattice cells */
function vfast(x, y, period, seed) {
  const x0 = Math.floor(x), y0 = Math.floor(y);
  let fx = x - x0, fy = y - y0;
  fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
  const xa = wrapi(x0, period), xb = wrapi(x0 + 1, period);
  const ya = wrapi(y0, period), yb = wrapi(y0 + 1, period);
  const a = ihash(xa, ya, seed), b = ihash(xb, ya, seed);
  const c = ihash(xa, yb, seed), d = ihash(xb, yb, seed);
  return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}

/** 1D tileable value noise — the along-a-single-thread variation */
function v1(t, period, seed) {
  const i0 = Math.floor(t); let f = t - i0;
  f = f * f * (3 - 2 * f);
  const a = ihash(wrapi(i0, period), seed, 913);
  const b = ihash(wrapi(i0 + 1, period), seed, 913);
  return a + (b - a) * f;
}
/** fractal 1D — slub: spun yarn thickens and thins irregularly along itself */
function f1(t, period, seed, oct = 3, gain = 0.55) {
  let v = 0, amp = 1, fr = 1, n = 0;
  for (let i = 0; i < oct; i++) {
    v += amp * v1(t * fr, period * fr, seed + i * 131);
    n += amp; fr *= 2; amp *= gain;
  }
  return v / n;
}

/* ------------------------------------------------------------------- fields */
/** broad (metre-scale) fbm, evaluated coarse and bilinearly upsampled —
    it is low frequency by construction so the interpolation costs nothing */
function lowField(size, N, cells, seed, oct = 4, lowRes = 64) {
  const lo = new Float32Array(lowRes * lowRes);
  for (let y = 0; y < lowRes; y++) {
    for (let x = 0; x < lowRes; x++) {
      lo[y * lowRes + x] = N.fbm((x / lowRes) * cells, (y / lowRes) * cells, cells, seed, oct);
    }
  }
  const out = new Float32Array(size * size);
  const s = lowRes / size;
  for (let y = 0; y < size; y++) {
    const fy = y * s, y0 = Math.floor(fy), ty = fy - y0, y1 = (y0 + 1) % lowRes;
    for (let x = 0; x < size; x++) {
      const fx = x * s, x0 = Math.floor(fx), tx = fx - x0, x1 = (x0 + 1) % lowRes;
      const a = lo[y0 * lowRes + x0], b = lo[y0 * lowRes + x1];
      const c = lo[y1 * lowRes + x0], d = lo[y1 * lowRes + x1];
      out[y * size + x] = a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty;
    }
  }
  return out;
}

/** two independent broad fields for the price of one upsample pass — every
    generator here wants at least a tonal drift and a wear/density field, and
    the bilinear expansion, not the fbm, is what the pass actually costs */
function lowField2(size, N, cells, seedA, seedB, oct = 4, lowRes = 64) {
  const la = new Float32Array(lowRes * lowRes), lb = new Float32Array(lowRes * lowRes);
  for (let y = 0; y < lowRes; y++) {
    for (let x = 0; x < lowRes; x++) {
      const u = (x / lowRes) * cells, v = (y / lowRes) * cells;
      la[y * lowRes + x] = N.fbm(u, v, cells, seedA, oct);
      lb[y * lowRes + x] = N.fbm(u, v, cells, seedB, oct);
    }
  }
  const A = new Float32Array(size * size), B = new Float32Array(size * size);
  const s = lowRes / size;
  for (let y = 0; y < size; y++) {
    const fy = y * s, y0 = Math.floor(fy), ty = fy - y0, y1 = (y0 + 1) % lowRes;
    const r0 = y0 * lowRes, r1 = y1 * lowRes, o = y * size;
    for (let x = 0; x < size; x++) {
      const fx = x * s, x0 = Math.floor(fx), tx = fx - x0, x1 = (x0 + 1) % lowRes;
      let a = la[r0 + x0], b = la[r0 + x1], c = la[r1 + x0], d = la[r1 + x1];
      A[o + x] = a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty;
      a = lb[r0 + x0]; b = lb[r0 + x1]; c = lb[r1 + x0]; d = lb[r1 + x1];
      B[o + x] = a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty;
    }
  }
  return [A, B];
}

/** millimetre-scale fibre field; `cells` must be a power of two ≤ size */
function microField(size, cells, seed, oct = 2) {
  const out = new Float32Array(size * size);
  const inv = 1 / size;
  for (let y = 0; y < size; y++) {
    const fy = y * inv;
    for (let x = 0; x < size; x++) {
      const fx = x * inv;
      let v = 0, amp = 1, f = 1, n = 0;
      for (let o = 0; o < oct; o++) {
        v += amp * vfast(fx * cells * f, fy * cells * f, cells * f, seed + o * 37);
        n += amp; f *= 2; amp *= 0.5;
      }
      out[y * size + x] = v / n;
    }
  }
  return out;
}

/* ----------------------------------------------------------------- cavity AO
   Same cavity model as N.aoFromHeight — how far below the local average a
   texel sits — but with a sliding-window box blur, so the cost no longer
   scales with the radius and there is no modulo in the inner loop.  All three
   fabrics are dominated by their AO pass otherwise; this buys back the budget
   that pays for the ply grooves and the extra loop layers. */
function cavityAO(h, size, r, strength) {
  const n = 2 * r + 1, inv = 1 / n, k = 6 * strength;
  const tmp = new Float32Array(size * size);
  const out = new Float32Array(size * size);
  for (let y = 0; y < size; y++) {
    const o = y * size;
    let s = 0;
    for (let j = -r; j <= r; j++) { let q = j; if (q < 0) q += size; s += h[o + q]; }
    for (let x = 0; x < size; x++) {
      tmp[o + x] = s * inv;
      let ad = x + r + 1; if (ad >= size) ad -= size;
      let rm = x - r; if (rm < 0) rm += size;
      s += h[o + ad] - h[o + rm];
    }
  }
  for (let x = 0; x < size; x++) {
    let s = 0;
    for (let j = -r; j <= r; j++) { let q = j; if (q < 0) q += size; s += tmp[q * size + x]; }
    for (let y = 0; y < size; y++) {
      const d = s * inv - h[y * size + x];
      const v = d > 0 ? 1 - d * k : 1;
      out[y * size + x] = v < 0 ? 0 : v > 1 ? 1 : v;
      let ad = y + r + 1; if (ad >= size) ad -= size;
      let rm = y - r; if (rm < 0) rm += size;
      s += tmp[ad * size + x] - tmp[rm * size + x];
    }
  }
  return out;
}

/* -------------------------------------------------------------------- layer */
/** parallel fields that yarn is max-blended into; `kind` 0 = exposed backing */
function newLayer(px, baseH) {
  const L = {
    h: new Float32Array(px),
    tone: new Float32Array(px),
    crown: new Float32Array(px),
    aux: new Float32Array(px),
    kind: new Uint8Array(px),
  };
  if (baseH) L.h.fill(baseH);
  return L;
}

/**
 * One short length of yarn, as a capsule with a round cross-section.
 * `za..zb` is the centreline height along the segment (this is what carries
 * over/under); `hr` is how far the cylinder bulges above that centreline.
 * `phA..phB` are the ply-twist phase in turns; `plyDepth` > 0 cuts the
 * helical grooves between plies into the surface.
 */
function stampYarn(L, size, ax, ay, bx, by, r, hr, za, zb, tone, kind, phA, phB, plyDepth, aux) {
  const dx = bx - ax, dy = by - ay;
  const len2 = dx * dx + dy * dy;
  const invL2 = len2 > 1e-9 ? 1 / len2 : 0;
  const invLen = len2 > 1e-9 ? 1 / Math.sqrt(len2) : 0;
  const r2 = r * r, invR2 = 1 / r2;
  const x0 = Math.floor(Math.min(ax, bx) - r), x1 = Math.ceil(Math.max(ax, bx) + r);
  const y0 = Math.floor(Math.min(ay, by) - r), y1 = Math.ceil(Math.max(ay, by) + r);
  const H = L.h, TO = L.tone, CR = L.crown, AX = L.aux, KI = L.kind;
  const a = aux === undefined ? 1 : aux;
  for (let y = y0; y <= y1; y++) {
    const row = wrap1(y, size) * size;
    const qy = y - ay;
    for (let x = x0; x <= x1; x++) {
      const qx = x - ax;
      let t = (qx * dx + qy * dy) * invL2;
      if (t < 0) t = 0; else if (t > 1) t = 1;
      const ox = qx - dx * t, oy = qy - dy * t;
      const d2 = ox * ox + oy * oy;
      if (d2 >= r2) continue;
      let prof = Math.sqrt(1 - d2 * invR2);
      let g = 1;
      if (plyDepth > 0) {
        const s = (ox * dy - oy * dx) * invLen;        // signed offset across the yarn
        const ph = phA + (phB - phA) * t + s * 0.030;  // grooves lean ≈45°
        g = 0.5 + 0.5 * cosT(ph);
        prof *= 1 - plyDepth * (1 - g);
      }
      const z = za + (zb - za) * t + hr * prof;
      const i = row + wrap1(x, size);
      if (z > H[i]) { H[i] = z; TO[i] = tone; CR[i] = prof; AX[i] = a * g; KI[i] = kind; }
    }
  }
}

/**
 * One boucle loop: a squashed torus. `ring` 0 = solid nub, 1 = open loop with
 * the backing visible through the middle. `lump` warps the outline so no two
 * loops are the same circle.
 */
function stampLoop(L, size, cx, cy, R, zBase, hCrest, ring, tone, kind, lump, lumpAmt, aux, rot, aspect) {
  /* exact AABB of the rotated ellipse, widened by the most the lump warp can
     push the outline out — a loose box costs real time, a tight one clips the
     ragged edge into a straight one */
  const co = Math.cos(rot), so = Math.sin(rot), Rv = R / aspect;
  const pad = 1 + lumpAmt * 0.5;
  const bx_ = Math.sqrt(R * R * co * co + Rv * Rv * so * so) * pad + 1;
  const by_ = Math.sqrt(R * R * so * so + Rv * Rv * co * co) * pad + 1;
  const x0 = Math.floor(cx - bx_), x1 = Math.ceil(cx + bx_);
  const y0 = Math.floor(cy - by_), y1 = Math.ceil(cy + by_);
  const invR = 1 / R, rr = 0.54, invW = 1 / 0.54;
  const ca = co * invR, sa = so * invR;
  const cb = ca * aspect, sb = sa * aspect;
  const H = L.h, TO = L.tone, CR = L.crown, AX = L.aux, KI = L.kind;
  for (let y = y0; y <= y1; y++) {
    const row = wrap1(y, size) * size;
    const dy = y - cy;
    for (let x = x0; x <= x1; x++) {
      const i = row + wrap1(x, size);
      const dx = x - cx;
      const rx = dx * ca + dy * sa;
      const ry = dy * cb - dx * sb;
      let d = Math.sqrt(rx * rx + ry * ry);
      if (lump) d += (lump[i] - 0.5) * lumpAmt;
      if (d >= 1) continue;
      if (d < 0) d = -d;
      const solid = Math.sqrt(1 - d * d);
      const t = (d - rr) * invW;
      const rp = t * t < 1 ? Math.sqrt(1 - t * t) : 0;
      const prof = solid * (1 - ring) + rp * ring;
      if (prof <= 0.001) continue;
      const z = zBase + hCrest * prof;
      if (z > H[i]) { H[i] = z; TO[i] = tone; CR[i] = prof; AX[i] = aux; KI[i] = kind; }
    }
  }
}

/**
 * One boucle curl: a near-closed ellipse of yarn, stamped as a short chain of
 * capsules.  Drawing the loop as actual yarn rather than as a torus blob is
 * what separates "nubby fabric" from "porridge" — you can see the strand go
 * round, and where two curls cross, one lies over the other.
 */
function stampCurl(L, size, cx, cy, Rl, ry, hz, tilt, tdir, hr, tone, kind, aux, rot, aspect, span, ph0, sd, plyD) {
  const SEGS = 6;                     // enough: the yarn is fat next to the arc
  const co = Math.cos(rot), so = Math.sin(rot);
  /* the strand tapers and its tone drifts as it goes round.  Both have to vary
     SMOOTHLY along the arc: a per-segment hash breaks the loop into a string of
     separately-shaded beads and the fabric reads as gravel, not as one yarn */
  const tf = 1.3 + 2.4 * ihash(sd, 7, 55), tp0 = ihash(sd, 3, 91) * TAU;
  let ax = 0, ay = 0, az = 0, ar = 0, ah = 0, ph = ph0 * 0.7;
  for (let s = 0; s <= SEGS; s++) {
    const a = ph0 + (s / SEGS) * span;
    const ex = Math.cos(a) * Rl, ey = Math.sin(a) * Rl * aspect;
    const X = cx + ex * co - ey * so;
    const Y = cy + ex * so + ey * co;
    const z = hz + tilt * Math.cos(a - tdir);     // the curl leans, as loops do
    const w = Math.cos(a * tf + tp0);
    const rr = ry * (1 + 0.22 * w);
    const hh = hr * (1 + 0.18 * w);
    let tn = tone + 0.19 * w;
    if (tn < 0) tn = 0; else if (tn > 1) tn = 1;
    if (s > 0) {
      const ph2 = ph + Math.hypot(X - ax, Y - ay) / (ry * 2.2);
      stampYarn(L, size, ax, ay, X, Y, (ar + rr) * 0.5, (ah + hh) * 0.5,
                az, z, tn, kind, ph, ph2, plyD, aux);
      ph = ph2;
    }
    ax = X; ay = Y; az = z; ar = rr; ah = hh;
  }
}

/**
 * One arm of a knit stitch, traced from the bottom apex (t=0) out and up to
 * the top corner of the cell (t=1).  The arm must keep travelling outwards all
 * the way to the top: a profile that comes back to the centre closes the loop
 * into a bulb and the fabric reads as chain-mail.  Stockinette's whole
 * signature is that the two arms diverge into a V and hand off to the arms of
 * the neighbouring stitches at the row boundary.  The exponent rounds the
 * apex — real yarn cannot turn a corner.
 */
const armX = (t) => Math.pow(t, 0.9);

/**
 * Centreline depth along that arm. The apex rides proud (zHi) and the arm tops
 * dive behind (zLo), so the apex of the row above lies over the crossing where
 * this stitch hands off to its neighbours. That single inversion is what makes
 * a field of V's read as interlocked loops instead of stamped chevrons.
 */
function zAt(N, t, zLo, zHi) {
  return zHi + (zLo - zHi) * N.smoothstep(0.12, 0.92, t);
}

/* ========================================================================= */
/*  LINEN — plain (tabby) weave upholstery                                   */
/* ========================================================================= */
/* 96 threads per tile each way; the tile is ~25 cm, so a thread is ~2.6 mm
   with its slub nubs swelling past 4 mm. Warp and weft interlace via a
   cosine undulation whose phase is the thread index, which is a proper
   over-one-under-one checkerboard rather than two crossed stripe patterns. */
export function linen(size, N) {
  const px = size * size;
  const { clamp } = N;
  const NT = 80;                       // even → the undulation phase wraps
  const pitch = size / NT;
  const rBase = pitch * 0.40;
  const STEP = 4;                      // must divide `size` so the thread wraps
  const steps = Math.round(size / STEP);
  const NP = 32;                       // slub samples per tile along a thread

  const L = newLayer(px, 0.05);        // shadowed backing seen through the weave

  for (let dir = 0; dir < 2; dir++) {
    const kind = dir === 0 ? 1 : 2;
    const sbase = dir * 9109;
    for (let i = 0; i < NT; i++) {
      const c0 = (i + 0.5) * pitch;
      const tone = N.hash2(i, dir * 31 + 7, 3.7);
      const rj = 0.86 + 0.28 * N.hash2(i + 5, dir * 17 + 2, 9.1);
      const sgn = i & 1 ? -1 : 1;
      let pX = 0, pY = 0, pZ = 0, pR = 0, pH = 0;
      for (let s = 0; s <= steps; s++) {
        const along = s * STEP;
        const p = (s / steps) * NP;
        const slub = f1(p, NP, i * 37 + sbase, 3);
        const nub = N.smoothstep(0.70, 0.96, v1(p * 2, NP * 2, i * 37 + sbase + 13));
        const wob = (f1(p * 0.5, NP >> 1, i * 37 + sbase + 401, 2) - 0.5) * pitch * 0.24;

        let rad = rBase * rj * (0.78 + 0.42 * slub + 0.60 * nub);
        if (rad > pitch * 0.62) rad = pitch * 0.62;

        const v = along / pitch;
        const cv = Math.cos(Math.PI * v) * sgn;         // cos(PI*(v+i)) with i integer
        const lift = dir === 0 ? 0.5 + 0.5 * cv : 0.5 - 0.5 * cv;

        /* tone drifts ALONG the thread as well as between threads, and it
           follows the slub: where the yarn is fat the fibre bundle is looser
           and paler.  A tone that is constant for a whole thread paints
           full-width stripes across the cloth and reads as woven-in ticking
           rather than as the hand-spun irregularity linen actually has. */
        let tn = tone * 0.52 + slub * 0.30 + nub * 0.18;
        if (tn > 1) tn = 1;

        const z = 0.26 + 0.20 * lift;
        const hr = 0.24 * (rad / rBase);
        const cx = c0 + wob;
        const X = dir === 0 ? cx : along;
        const Y = dir === 0 ? along : cx;
        if (s > 0) {
          stampYarn(L, size, pX, pY, X, Y, (pR + rad) * 0.5, (pH + hr) * 0.5,
                    pZ, z, tn, kind, 0, 0, 0, 1);
        }
        pX = X; pY = Y; pZ = z; pR = rad; pH = hr;
      }
    }
  }

  /* ---------------------------------------------------------- fine + broad */
  const micro = microField(size, 64, 771, 2);
  const [drift, sheen] = lowField2(size, N, 3, 12, 88, 4);

  const height = N.newF(px);
  for (let i = 0; i < px; i++) height[i] = clamp(L.h[i] + (micro[i] - 0.5) * 0.018, 0, 1);
  /* the wider, softer cavity radius replaces the old tight one, so the
     strength comes down to match — interstices in a 3 mm weave are shadowed,
     not black, and crushing them there costs the fabric its mid tones */
  const ao = cavityAO(height, size, 3, 0.58);

  /* ------------------------------------------------------------- shading */
  const albedo = N.newF(px * 3), rough = N.newF(px);
  const dR = 0.541, dG = 0.498, dB = 0.408;   // 0x8a7f68 shadowed flax
  const lR = 0.765, lG = 0.718, lB = 0.612;   // 0xc3b79c bleached flax
  const wR = 0.663, wG = 0.627, wB = 0.549;   // weft dye lot, a touch greyer
  const hR = 0.238, hG = 0.212, hB = 0.169;   // the pinholes through the cloth

  for (let i = 0; i < px; i++) {
    const k = L.kind[i], cr = L.crown[i], m = micro[i];
    let r, g, b;
    if (k === 0) {
      r = hR; g = hG; b = hB;
    } else {
      const t = L.tone[i];
      r = dR + (lR - dR) * t; g = dG + (lG - dG) * t; b = dB + (lB - dB) * t;
      if (k === 2) { r += (wR - r) * 0.30; g += (wG - g) * 0.30; b += (wB - b) * 0.30; }
      const sh = 0.84 + 0.28 * cr;            // flax fibres catch light on the crown
      r *= sh; g *= sh; b *= sh;
    }
    const f = (0.92 + 0.15 * drift[i]) * (0.95 + 0.10 * m);
    albedo[i * 3] = r * f; albedo[i * 3 + 1] = g * f; albedo[i * 3 + 2] = b * f;

    /* three scales of gloss, same as the relief: broad patches where the cloth
       has been sat on and burnished, the per-thread crown catching a sheen,
       and fibre-level scatter — a flat roughness is the loudest "CG" tell */
    let ro = k === 0 ? 0.98 : 0.93 - 0.30 * cr * cr;
    ro += (m - 0.5) * 0.08 - 0.15 * sheen[i] - 0.05 * drift[i];
    rough[i] = clamp(ro, 0.42, 0.99);
  }

  return { albedo, rough, height, ao };
}

/* ========================================================================= */
/*  BOUCLE — nubby looped yarn                                               */
/* ========================================================================= */
/* Three grids of jittered loops at different pitches, so loop size and
   height vary and the clusters clump; a low-frequency field decides where
   the pile is dense and where the flat ground weave shows through. */
export function boucle(size, N) {
  const px = size * size;
  const { clamp } = N;
  const L = newLayer(px, 0);

  /* ------------------------------------------- ground weave, seen in gaps */
  /* cos(PI*(v + iu)) collapses to (-1)^iu * cos(PI*v) because iu is an
     integer, and the thread cross-section only depends on one axis — so the
     whole tabby is two 1-D tables instead of per-pixel trigonometry */
  const pb = size / 64;
  const cosU = new Float32Array(size), profU = new Float32Array(size);
  const sgnU = new Int8Array(size);
  for (let x = 0; x < size; x++) {
    const u = x / pb, iu = Math.floor(u), fu = (u - iu - 0.5) * 2.35;
    cosU[x] = Math.cos(Math.PI * u);
    sgnU[x] = iu & 1 ? -1 : 1;
    profU[x] = fu * fu < 1 ? Math.sqrt(1 - fu * fu) : 0;
  }
  L.tone.fill(0.5);
  L.aux.fill(1);
  for (let y = 0; y < size; y++) {
    const cv = cosU[y], sv = sgnU[y], pf = profU[y];
    const row = y * size;
    for (let x = 0; x < size; x++) {
      const lw = 0.5 + 0.5 * cv * sgnU[x];
      const lf = 0.5 - 0.5 * cosU[x] * sv;
      const pw = profU[x];
      const hw = 0.170 + 0.032 * lw + 0.040 * pw * lw;
      const hf = 0.170 + 0.032 * lf + 0.040 * pf * lf;
      const i = row + x;
      L.h[i] = hw > hf ? hw : hf;
      L.crown[i] = hw > hf ? pw : pf;
    }
  }

  /* ---------------------------------------------------------- loop layers */
  const [clus, drift] = lowField2(size, N, 4, 21, 404, 3);
  /* one field does double duty: it warps the loop outlines while they are
     being stamped and then becomes the surface fuzz */
  const micro = microField(size, 56, 407, 1);
  const lump = micro;

  /* A tile is ~25 cm, so a curl 16–26 px across is the 8–13 mm loop the fabric
     is named for.  Three pitches of curls overlap so they crowd and clump
     rather than sit on a lattice, and a sparse pass of tight knots adds the
     fat slubs that stand proud of everything around them.  The clump field
     decides where the pile thins enough to show the ground weave. */
  const curls = [
    { g: 12, rl: 0.34, hz: 0.42, jit: 1.00, keep: 0.42, ply: 0.24, seed: 5 },
    { g: 17, rl: 0.36, hz: 0.33, jit: 1.00, keep: 0.86, ply: 0.20, seed: 61 },
    /* the finest curls are ~6 mm across: the ply groove there is sub-texel and
       only costs time, so it is switched off for that layer */
    { g: 24, rl: 0.38, hz: 0.25, jit: 1.00, keep: 0.66, ply: 0, seed: 133 },
  ];

  for (const ly of curls) {
    const cell = size / ly.g;
    for (let gy = 0; gy < ly.g; gy++) {
      for (let gx = 0; gx < ly.g; gx++) {
        const ha = N.hash2(gx, gy, ly.seed);
        const hb = N.hash2(gx + 13, gy + 7, ly.seed + 3);
        const hc = N.hash2(gx + 41, gy + 29, ly.seed + 11);
        const hd = N.hash2(gx + 5, gy + 61, ly.seed + 19);
        const he = N.hash2(gx + 77, gy + 3, ly.seed + 27);
        const hf = N.hash2(gx + 31, gy + 47, ly.seed + 35);
        const hg = N.hash2(gx + 19, gy + 83, ly.seed + 43);
        const hh = N.hash2(gx + 59, gy + 37, ly.seed + 51);

        const cx = (gx + 0.5 + (ha - 0.5) * ly.jit) * cell;
        const cy = (gy + 0.5 + (hb - 0.5) * ly.jit) * cell;
        const ci = wrapi(Math.round(cy), size) * size + wrapi(Math.round(cx), size);
        const dens = clus[ci];
        if (hc > ly.keep * (0.72 + 0.52 * dens)) continue;

        const Rl = cell * ly.rl * (0.72 + 0.62 * hd);
        /* the strand-to-loop radius ratio is the whole character knob: at 0.42
           the loop is open and you see through it, at 0.85 it closes into a
           solid nub.  Spreading it across that range is what turns a field of
           identical rings — the "breakfast cereal" failure — into the mix of
           open loops, half-shut curls and tight nubs a real boucle has. */
        const ry = Rl * (0.30 + 0.32 * he);
        const zB = ly.hz * (0.45 + 0.95 * dens) + (he - 0.5) * 0.10;
        const tone = hd * 0.72 + hb * 0.28;
        const nep = N.hash2(gx + 3, gy + 91, ly.seed + 55) < 0.020 ? 3 : 1;
        /* and the arc SPAN: most curls close, but a good third are hooks and
           three-quarter loops with a free end, which is what a plucked boucle
           yarn really does.  All-closed rings look like breakfast cereal */
        const span = 3.4 + 3.1 * hh;
        stampCurl(L, size, cx, cy, Rl, ry, zB, 0.07 + 0.10 * hg, hf * TAU,
                  0.26 + 0.12 * hd, tone, nep, 1,
                  hf * TAU, 0.42 + 0.58 * he, span, hg * TAU,
                  ly.seed + gx * 7 + gy * 131, ly.ply);
      }
    }
  }

  /* tight knots — the slub where the yarn balls up instead of looping */
  for (let gy = 0; gy < 18; gy++) {
    for (let gx = 0; gx < 18; gx++) {
      const ha = N.hash2(gx, gy, 907), hb = N.hash2(gx + 9, gy + 3, 911);
      const hc = N.hash2(gx + 51, gy + 17, 919), hd = N.hash2(gx + 7, gy + 71, 929);
      if (hc > 0.34) continue;
      const cell = size / 18;
      const cx = (gx + 0.5 + (ha - 0.5) * 0.95) * cell;
      const cy = (gy + 0.5 + (hb - 0.5) * 0.95) * cell;
      const ci = wrapi(Math.round(cy), size) * size + wrapi(Math.round(cx), size);
      stampLoop(L, size, cx, cy, cell * 0.34 * (0.7 + 0.7 * hd),
                0.20 + 0.20 * clus[ci], 0.34, 0.18, hd * 0.7 + hb * 0.3, 1,
                lump, 0.46, 1, ha * TAU, 0.72 + 0.24 * hb);
    }
  }

  /* --------------------------------------------- stray fibres over the top */
  for (let s = 0; s < 70; s++) {
    const a = N.hash2(s, 3, 771), b = N.hash2(s + 17, 9, 219), c = N.hash2(s + 5, 41, 63);
    const x0 = a * size, y0 = b * size;
    const ang = c * TAU, len = size * (0.012 + 0.030 * N.hash2(s, 7, 55));
    const x1 = x0 + Math.cos(ang) * len, y1 = y0 + Math.sin(ang) * len;
    const ci = wrapi(Math.round(y0), size) * size + wrapi(Math.round(x0), size);
    stampYarn(L, size, x0, y0, x1, y1, size * 0.006, 0.05,
              L.h[ci] + 0.02, L.h[ci] + 0.03, 0.85, 2, 0, 0, 0, 1);
  }

  /* ---------------------------------------------------------- fuzz + relief */
  const height = N.newF(px);
  for (let i = 0; i < px; i++) {
    height[i] = clamp(L.h[i] + (micro[i] - 0.5) * 0.045, 0, 1);
  }
  /* the pile is only ~5 mm deep, so the shade between loops is a soft cavity,
     not a canyon — driving AO to black there is what makes a boucle read as
     charred cork instead of wool */
  const ao = cavityAO(height, size, 3, 0.36);

  /* ------------------------------------------------------------- shading */
  const albedo = N.newF(px * 3), rough = N.newF(px);
  const dR = 0.588, dG = 0.545, dB = 0.470;   // 0x968b78 shaded wool
  const lR = 0.780, lG = 0.733, lB = 0.643;   // 0xc7bba4 cream crown
  const gR = 0.541, gG = 0.502, gB = 0.435;   // 0x8a806f ground weave
  const nR = 0.400, nG = 0.333, nB = 0.255;   // 0x665541 undyed nep fleck

  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const i = y * size + x;
    const k = L.kind[i], cr = L.crown[i], ply = L.aux[i];
    /* the single-texel half of the fuzz is pure white noise — generating it
       inline beats keeping a whole extra field around for it */
    const m = micro[i] * 0.6 + ihash(x, y, 9377) * 0.4;
    let r, g, b, ro;
    if (k === 0) {
      const s = 0.85 + 0.35 * cr;
      r = gR * s; g = gG * s; b = gB * s;
      ro = 0.955;
    } else {
      const t = L.tone[i];
      r = dR + (lR - dR) * t; g = dG + (lG - dG) * t; b = dB + (lB - dB) * t;
      if (k === 3) { r = r * 0.45 + nR * 0.55; g = g * 0.45 + nG * 0.55; b = b * 0.45 + nB * 0.55; }
      /* the ply spiral darkens the grooves as well as cutting them, so the
         strand still reads as twisted yarn once the normal map is minified */
      const sh = 0.70 + 0.42 * cr * (0.55 + 0.45 * ply);
      r *= sh; g *= sh; b *= sh;
      ro = 0.985 - 0.19 * cr * cr * ply;           // only the crown is burnished
    }
    /* broad wear: where the arm of a chair has been leaned on for years the
       pile is flattened and slightly polished, and it is a metre-scale patch */
    const wear = clus[i];
    const f = (0.90 + 0.19 * drift[i]) * (0.958 + 0.084 * m);
    albedo[i * 3] = r * f; albedo[i * 3 + 1] = g * f; albedo[i * 3 + 2] = b * f;
    rough[i] = clamp(ro + (m - 0.5) * 0.06 - 0.10 * drift[i] - 0.07 * wear, 0.66, 0.995);
  }

  return { albedo, rough, height, ao };
}

/* ========================================================================= */
/*  KNIT — chunky hand-knit stockinette throw                                */
/* ========================================================================= */
/* Each stitch is one loop of yarn drawn as two mirrored cubic Beziers: out
   from the bottom apex, bulging sideways, up and over into the head arch at
   the top of the cell.  Centreline height falls from apex (in front) to head
   (behind), so the apex of the stitch above correctly covers the head of the
   stitch below — the chain that makes stockinette read as knitted. */
export function knit(size, N) {
  const px = size * size;
  const { clamp } = N;
  const COLS = 10, ROWS = 13;                 // ~2.5 cm × 1.9 cm per stitch
  const cw = size / COLS, ch = size / ROWS;
  const L = newLayer(px, 0.03);

  /* hand-made wander: whole rows and columns drift, not just single stitches */
  const rowShift = new Float32Array(ROWS), colShift = new Float32Array(COLS);
  for (let r = 0; r < ROWS; r++) rowShift[r] = (f1(r, ROWS, 71, 2) - 0.5) * cw * 0.24;
  for (let c = 0; c < COLS; c++) colShift[c] = (f1(c, COLS, 137, 2) - 0.5) * ch * 0.22;

  const SEG = 7;
  const plyPitch = cw * 0.22;                 // helix pitch of the ply twist
  const zLo = 0.20, zHi = 0.60;               // arm tops (behind) / apex (proud)

  for (let r = 0; r < ROWS; r++) {
    for (let c = 0; c < COLS; c++) {
      const h1 = N.hash2(c, r, 3), h2 = N.hash2(c + 11, r + 5, 8);
      const h3 = N.hash2(c + 23, r + 2, 17), h4 = N.hash2(c + 7, r + 31, 29);
      const h5 = N.hash2(c + 43, r + 13, 41), h6 = N.hash2(c + 3, r + 53, 59);
      const h7 = N.hash2(c + 67, r + 19, 71);

      const cx = (c + 0.5) * cw + (h1 - 0.5) * cw * 0.15 + rowShift[r];
      const cy = (r + 0.5) * ch + (h2 - 0.5) * ch * 0.15 + colShift[c];
      const sx = cw * (0.93 + 0.17 * h3);
      const sy = ch * (0.94 + 0.15 * h4);
      /* yarn diameter ≈ 0.40 of a stitch width. Fatter and the two arms of a V
         weld into a plateau; thinner and the backing shows through in a grid */
      const rad = 0.205 * cw * (0.88 + 0.22 * h5);
      const hr = 0.235 * (0.88 + 0.24 * h5);
      const lean = (h7 - 0.5) * 0.22;               // the stitch tips a little
      const tone = h6;
      const zOff = (h3 - 0.5) * 0.05;               // neighbours never tie in z

      for (let side = -1; side <= 1; side += 2) {
        /* One arm of the V, from this stitch's own apex out to the top corner
           of its cell, where it meets the arm of the neighbouring stitch and
           dives under the apex of the row above.  Carrying the arm a little
           PAST the cell edge (tEnd > 1) is what welds the zig-zag together —
           stop exactly on the boundary and a hairline of backing shows along
           every row. */
        /* the arm travels ~1.45 row pitches, so three rows of yarn overlap in
           every cell: the apex of the row above nests down into the wedge
           between these two arms, exactly as loops interlock in real knitting.
           Arms that stop at the row boundary leave a black chevron there and
           the fabric reads as stamped chevrons on a dark ground. */
        const A = 0.55 * sx, yA = cy + 0.72 * sy, yT = cy - 0.73 * sy;
        let ax = cx, ay = yA;
        let ph = h1 * 3.1 + (side < 0 ? 0 : 0.47);
        const tEnd = 1.0;
        for (let s = 1; s <= SEG; s++) {
          const t = (s / SEG) * tEnd, tp = ((s - 1) / SEG) * tEnd;
          let bx = cx + side * A * armX(t);
          const by = yA + (yT - yA) * t;
          bx += lean * sx * t;
          const seg = Math.hypot(bx - ax, by - ay);
          const ph2 = ph + seg / plyPitch;
          /* the arm thins as it turns away from the viewer, so the crossing at
             the row boundary reads as yarn going round rather than a butt joint */
          const taper = 1 - 0.16 * N.smoothstep(0.5, 1.0, t);
          const za = zAt(N, tp, zLo, zHi) + zOff;
          const zb = zAt(N, t, zLo, zHi) + zOff;
          stampYarn(L, size, ax, ay, bx, by, rad * taper, hr * taper,
                    za, zb, tone, 1, ph, ph2, 0.26, 1);
          ax = bx; ay = by; ph = ph2;
        }
      }
    }
  }

  /* -------------------------------------------------- fuzzy halo + relief */
  const micro = microField(size, 128, 5501, 1);
  const drift = lowField(size, N, 3, 909, 4);
  const soft = N.blur(L.h, size, 1);

  /* single-texel fibre speckle — white noise is right here, it only ever
     touches colour and roughness, never the relief */
  const fine = N.newF(px);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) fine[y * size + x] = ihash(x, y, 8123);
  }

  const height = N.newF(px);
  for (let i = 0; i < px; i++) {
    height[i] = clamp(L.h[i] * 0.62 + soft[i] * 0.38 + (micro[i] - 0.5) * 0.07, 0, 1);
  }
  const ao = cavityAO(height, size, 3, 0.62);

  /* ------------------------------------------------------------- shading */
  const albedo = N.newF(px * 3), rough = N.newF(px);
  const dR = 0.541, dG = 0.435, dB = 0.298;   // 0x8a6f4c shaded camel
  const lR = 0.784, lG = 0.663, lB = 0.478;   // 0xc8a97a lit crown
  const bR = 0.349, bG = 0.278, bB = 0.196;   // 0x594732 gap between stitches

  for (let i = 0; i < px; i++) {
    const k = L.kind[i], cr = L.crown[i], m = micro[i], fn = fine[i];
    let r, g, b, ro;
    if (k === 0) {
      /* wool haze: the blurred relief already knows how close the nearest
         yarn is, so it doubles as the fibre halo spilling into the gap */
      const hl = N.smoothstep(0.06, 0.34, soft[i]);
      r = bR + (dR - bR) * hl * 0.80;
      g = bG + (dG - bG) * hl * 0.80;
      b = bB + (dB - bB) * hl * 0.80;
      ro = 0.985;
    } else {
      const t = tone3(L.tone[i], fn);
      r = dR + (lR - dR) * t; g = dG + (lG - dG) * t; b = dB + (lB - dB) * t;
      /* the ply grooves have to show in COLOUR as well as relief — at 2–5 m
         the normal map is half-resolved but the dark spiral banding survives,
         and it is the cue that says "spun yarn" rather than "moulded rubber" */
      const sh = 0.72 + 0.40 * cr * (0.50 + 0.50 * L.aux[i]);
      r *= sh; g *= sh; b *= sh;
      /* the ply crests are the only part of a wool yarn that has ever been
         rubbed smooth; everything else is chalk */
      ro = 0.985 - 0.20 * cr * L.aux[i];
    }
    const f = (0.91 + 0.17 * drift[i]) * (0.94 + 0.12 * m);
    albedo[i * 3] = r * f; albedo[i * 3 + 1] = g * f; albedo[i * 3 + 2] = b * f;
    rough[i] = clamp(ro + (m - 0.5) * 0.05 + (fn - 0.5) * 0.04 - 0.07 * drift[i], 0.72, 0.998);
  }

  return { albedo, rough, height, ao };
}

/** heathered wool: the per-stitch tone plus fibre-level speckle */
function tone3(t, f) {
  const v = t * 0.72 + f * 0.28;
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
