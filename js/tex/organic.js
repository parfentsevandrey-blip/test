/* =========================================================================
   Organic surfaces: a burning log for the firebox, and a single glossy
   fiddle-leaf-fig leaf for the houseplants.

   Both sit very close to the camera and are lit almost entirely by raking
   firelight, so the height/roughness relief carries the read far more than
   the (deliberately near-black) albedo does.

   Perf notes -------------------------------------------------------------
   These are 512² surfaces on a 150 ms budget, i.e. ~570 ns per texel. The
   toolkit's noises are sin-hashed, which is lovely and slow (one gnoise is
   twelve trig calls); a single full-resolution ridged fbm would eat the
   whole budget. So, following the pattern already established in oak.js:

     - an integer hash drives the dense full-resolution fields,
     - broad metre-scale fields are baked at 128² and bilinearly upsampled,
     - small lattices (the log's crack cells, the leaf's vein parameters,
       the log's grain profile) are tabulated once rather than re-hashed
       per texel,
     - the cavity AO compares against a decimated copy of the height rather
       than a full-resolution box blur,
     - the toolkit's colour helpers are used as they are.

   The local noises take SEPARATE x and y periods, which the shared ones
   cannot. That matters twice over here: bark fissures want to be several
   times longer along the log than they are wide, and char plates want to be
   elongated along the grain. With one shared period the only way to buy
   that aspect ratio is to repeat the pattern around U; independent periods
   give the anisotropy *and* an exact wrap at the tile edge.
   ========================================================================= */

/* ---------------------------------------------------------------- toolkit */

/** integer hash -> 0..1. ~20x cheaper than a sin hash, just as deterministic. */
function ihash(x, y, s) {
  let h = Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(s | 0, 1274126177);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) * 2.3283064365386963e-10;
}

/** smoothstep with the range inverse precomputed by the caller */
const ss = (x, a, inv) => {
  let t = (x - a) * inv;
  t = t < 0 ? 0 : t > 1 ? 1 : t;
  return t * t * (3 - 2 * t);
};
const sat = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
const lerp = (a, b, t) => a + (b - a) * t;

/** tileable value noise, independent x/y periods (0 = do not wrap).
    The wrap tests before it takes a modulo: the sampling coordinate is
    almost always already inside [0, period), and the fmod is not cheap. */
function ivn(x, y, px, py, seed) {
  const x0 = Math.floor(x), y0 = Math.floor(y);
  let fx = x - x0, fy = y - y0;
  fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
  let xa = x0, ya = y0;
  if (px > 0 && (xa < 0 || xa >= px)) { xa %= px; if (xa < 0) xa += px; }
  if (py > 0 && (ya < 0 || ya >= py)) { ya %= py; if (ya < 0) ya += py; }
  let xb = xa + 1, yb = ya + 1;
  if (xb === px) xb = 0;
  if (yb === py) yb = 0;
  const a = ihash(xa, ya, seed), b = ihash(xb, ya, seed);
  const c = ihash(xa, yb, seed), d = ihash(xb, yb, seed);
  return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}

/** fractal sum of ivn; both periods double with each octave */
function ifbm(x, y, px, py, seed, oct = 4, gain = 0.5) {
  let v = 0, amp = 1, f = 1, norm = 0;
  for (let i = 0; i < oct; i++) {
    v += amp * ivn(x * f, y * f, px * f, py * f, seed + i * 37);
    norm += amp; f *= 2; amp *= gain;
  }
  return v / norm;
}

/** ridged fractal — approaches 1 along the creases. Fissures and cracks. */
function iridge(x, y, px, py, seed, oct = 2, gain = 0.5) {
  let v = 0, amp = 1, f = 1, norm = 0;
  for (let i = 0; i < oct; i++) {
    const n = 1 - Math.abs(ivn(x * f, y * f, px * f, py * f, seed + i * 53) * 2 - 1);
    v += amp * n * n; norm += amp; f *= 2; amp *= gain;
  }
  return v / norm;
}

/* ------------------------------------------------------------- cellular */
/* The log's crack lattice is only a few dozen cells, so its jitter offsets
   and per-cell ids are tabulated up front and the per-texel loop is pure
   array reads. Squared distances are compared and the two square roots
   taken once, at the end. Results land in module scratch so the inner loop
   never allocates. */
let W1 = 0, W2 = 0, WID = 0;

function cellTable(px, py, seed, jitter) {
  const t = new Float32Array(px * py * 3);
  for (let j = 0; j < py; j++) {
    for (let i = 0; i < px; i++) {
      const k = (j * px + i) * 3;
      t[k] = (ihash(i, j, seed) - 0.5) * jitter;
      t[k + 1] = (ihash(i, j, seed + 7717) - 0.5) * jitter;
      t[k + 2] = ihash(i, j, seed + 9137);
    }
  }
  return t;
}
function tworley(x, y, T, px, py) {
  const ix = Math.floor(x), iy = Math.floor(y);
  let ax = (ix - 1) % px; if (ax < 0) ax += px;
  let bx = ax + 1; if (bx === px) bx = 0;
  let cx = bx + 1; if (cx === px) cx = 0;
  let f1 = 1e9, f2 = 1e9, bid = 0;
  for (let oy = -1; oy <= 1; oy++) {
    const cy = iy + oy;
    let wy = cy % py; if (wy < 0) wy += py;
    const row = wy * px, oy0 = cy + 0.5 - y;
    for (let ox = 0; ox < 3; ox++) {
      const wx = ox === 0 ? ax : ox === 1 ? bx : cx;
      const k = (row + wx) * 3;
      const dx = ix + ox - 1 + 0.5 + T[k] - x, dy = oy0 + T[k + 1];
      const d = dx * dx + dy * dy;
      if (d < f1) { f2 = f1; f1 = d; bid = T[k + 2]; }
      else if (d < f2) f2 = d;
    }
  }
  W1 = Math.sqrt(f1); W2 = Math.sqrt(f2); WID = bid;
}
/** precomputed bilinear taps for reading an S² field over a size² image */
function sampler(S, size) {
  const i0 = new Int32Array(size), i1 = new Int32Array(size), fr = new Float32Array(size);
  for (let x = 0; x < size; x++) {
    const u = (x + 0.5) * S / size - 0.5;
    const a = Math.floor(u);
    i0[x] = ((a % S) + S) % S;
    i1[x] = (i0[x] + 1) % S;
    fr[x] = u - a;
  }
  return { i0, i1, fr, S };
}
function bl(F, r0, r1, fy, c0, c1, fx) {
  const a = F[r0 + c0], b = F[r0 + c1], c = F[r1 + c0], d = F[r1 + c1];
  const t0 = a + (b - a) * fx, t1 = c + (d - c) * fx;
  return t0 + (t1 - t0) * fy;
}

/* Several broad fields sampled at the same texel are packed into one
   interleaved array: the four corner indices are then computed once for all
   of them instead of once each. */
function lowFieldInto(dst, K, o, S, fx, fy, seed, oct, gain) {
  for (let j = 0; j < S; j++) {
    const yy = ((j + 0.5) / S) * fy;
    for (let i = 0; i < S; i++) {
      dst[(j * S + i) * K + o] = ifbm(((i + 0.5) / S) * fx, yy, fx, fy, seed, oct, gain);
    }
  }
}
function blK(F, b00, b01, b10, b11, o, fx, fy) {
  const a = F[b00 + o], b = F[b01 + o], c = F[b10 + o], d = F[b11 + o];
  const t0 = a + (b - a) * fx, t1 = c + (d - c) * fx;
  return t0 + (t1 - t0) * fy;
}

/* Cavity AO. Same idea as the toolkit's aoFromHeight — how far below its
   local average a texel sits — but the local average is a block-decimated,
   3x3-blurred copy of the height instead of a full-resolution box blur.
   Visually indistinguishable at this radius and about four times cheaper,
   which on these two surfaces is the difference between fitting the frame
   budget and not. */
function cavityAO(h, size, strength, S = 64) {
  const step = size / S, inv = 1 / (step * step);
  const lo = new Float32Array(S * S), lb = new Float32Array(S * S);
  for (let j = 0; j < S; j++) {
    for (let i = 0; i < S; i++) {
      let acc = 0;
      for (let dy = 0; dy < step; dy++) {
        const row = (j * step + dy) * size + i * step;
        for (let dx = 0; dx < step; dx++) acc += h[row + dx];
      }
      lo[j * S + i] = acc * inv;
    }
  }
  for (let j = 0; j < S; j++) {
    const jm = ((j + S - 1) % S) * S, jc = j * S, jp = ((j + 1) % S) * S;
    for (let i = 0; i < S; i++) {
      const im = (i + S - 1) % S, ip = (i + 1) % S;
      lb[jc + i] = (lo[jm + im] + lo[jm + i] + lo[jm + ip]
                  + lo[jc + im] + lo[jc + i] + lo[jc + ip]
                  + lo[jp + im] + lo[jp + i] + lo[jp + ip]) * 0.1111111;
    }
  }
  const smp = sampler(S, size), out = new Float32Array(size * size);
  const I0 = smp.i0, I1 = smp.i1, FR = smp.fr;
  for (let y = 0; y < size; y++) {
    const r0 = I0[y] * S, r1 = I1[y] * S, fy = FR[y], row = y * size;
    for (let x = 0; x < size; x++) {
      const d = bl(lb, r0, r1, fy, I0[x], I1[x], FR[x]) - h[row + x];
      out[row + x] = d > 0 ? sat(1 - d * 6 * strength) : 1;
    }
  }
  return out;
}

/* ===================================================================== log */
/*
   A split hardwood log burning in the firebox. One tile wraps once around
   the log (~30 cm) and covers ~35 cm along it, so a texel is about 0.6 mm.
   U runs around the log, V along its axis.

   Layers, largest to smallest:
     m  : how far the burn has got, where the surface has swelled, and where
          ash has settled — all drawn out along the log
     cm : bark ridges and fissures where it has not burned through; a worley
          craquelure of flat blackened plates, elongated along the grain,
          where it has
     mm : crazing inside each plate, flaked plate shoulders, powdery ash on
          the raised faces, granular char micro-structure
*/
export function charredLog(size, N) {
  const { hex } = N;
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);

  /* Palette, sRGB-ish. Char stays inside 0.035..0.11 luminance so that the
     firelight — not the map — is what makes it read black. Charcoal is very
     NEUTRAL; only the unburnt bark keeps a brown cast. */
  const [bmR, bmG, bmB] = hex(0x1b1611);   // bark, scaled 0.38x .. 1.76x
  const [chR, chG, chB] = hex(0x1c1a18);   // char plate face, scaled down to
                                           //   0.2x on the crack floors
  const [gsR, gsG, gsB] = hex(0x161820);   // vitrified char, faintly cool
  const [spR, spG, spB] = hex(0x271b12);   // wood exposed where a plate fell
  const [alR, alG, alB] = hex(0x3b3b39);   // ash, shadowed
  const [ahR, ahG, ahB] = hex(0x757369);   // ash, bright — peaks at ~0.45 lum
  const [emR, emG, emB] = hex(0xa83c0a);   // ember, deep in a crack

  /* ---- broad fields, interleaved at 128² and read bilinearly --------- */
  const S = 128, smp = sampler(S, size), K = 4;
  const LF = new Float32Array(S * S * K);
  lowFieldInto(LF, K, 0, S, 5, 4, 6607, 3, 0.50);   // domain warp
  lowFieldInto(LF, K, 1, S, 3, 2, 4409, 4, 0.50);   // swelling / tonal drift
  lowFieldInto(LF, K, 2, S, 5, 3, 1101, 5, 0.55);   // bark -> char progression
  lowFieldInto(LF, K, 3, S, 7, 4, 2203, 5, 0.55);   // where ash has settled
  const SI0 = smp.i0, SI1 = smp.i1, SFR = smp.fr;

  /* bark: 22 fissure lanes around the log, 5 along it -> ~4.5:1 aspect, so a
     ridge runs most of the way up the tile instead of breaking every 7 cm */
  const BU = 22, BV = 5;
  /* char plates: 12 around, 5 along -> flat facets roughly 2.5 x 7 cm,
     which is the scale hardwood alligators at in a domestic firebox */
  const CU = 12, CV = 5;
  const CT = cellTable(CU, CV, 2311, 0.95);

  /* The fine grain of wood runs dead straight along the axis, so its noise
     is a one-dimensional profile across U, sampled at a V-dependent offset
     so the lines waver instead of ruling the tile. One table lookup and a
     lerp instead of a whole 2-D noise fetch. */
  const MASK = size - 1;
  const GRAIN = new Float32Array(size);
  for (let gx = 0; gx < size; gx++) {
    const t = (gx + 0.5) / size;
    GRAIN[gx] = ivn(t * 150, 0.37, 150, 26, 1907) * 0.62 + ivn(t * 44, 0.61, 44, 26, 1913) * 0.38;
  }
  /* A second, much coarser profile on the same trick. Bark ridges built from
     one ridged lane field come out evenly spaced — corduroy, not bark. Beating
     a ~30-lane and a ~17-lane profile against the 22-lane field makes ridges
     merge, split and vary in width, which is what real fissuring does, and it
     costs two array reads instead of another noise fetch. */
  const GRAIN2 = new Float32Array(size);
  for (let gx = 0; gx < size; gx++) {
    const t = (gx + 0.5) / size;
    GRAIN2[gx] = ivn(t * 30, 0.19, 30, 22, 1921) * 0.58 + ivn(t * 17, 0.83, 17, 22, 1927) * 0.42;
  }

  /* The pixel loop lives in a closure that is called for a short band
     first and the rest afterwards. A single 512-row loop spends its first
     ~15k iterations in the interpreter before the JIT tiers it up, which on
     a one-shot generator is 30% of the wall clock; priming it with a narrow
     band means the bulk of the image runs optimised from the first row. */
  const inv = 1 / size;
  const band = (yStart, yEnd) => {
  for (let y = yStart; y < yEnd; y++) {
    const r0 = SI0[y] * S, r1 = SI1[y] * S, fy = SFR[y];
    const v = (y + 0.5) * inv;
    const vB = v * BV, vC = v * CV, v46 = v * 46;
    let i = y * size - 1;
    for (let x = 0; x < size; x++) {
      const c0 = SI0[x], c1 = SI1[x], fx = SFR[x];
      i++;
      const u = (x + 0.5) * inv;

      const b00 = (r0 + c0) * K, b01 = (r0 + c1) * K, b10 = (r1 + c0) * K, b11 = (r1 + c1) * K;
      const wx = blK(LF, b00, b01, b10, b11, 0, fx, fy) - 0.5;
      const form = blK(LF, b00, b01, b10, b11, 1, fx, fy);
      const wy = form - 0.5;                           // a slower second axis
      const burn = blK(LF, b00, b01, b10, b11, 2, fx, fy);

      /* -------- mid / high frequency scratch shared by several layers --- */
      const n46 = ivn(u * 62 + wx * 2, v46 + wy * 2, 62, 46, 2609);
      const gxf = x + wy * 34 + (n46 - 0.5) * 13;
      const gi = Math.floor(gxf), gf = gxf - gi;
      const g0 = GRAIN[gi & MASK], g1 = GRAIN[(gi + 1) & MASK];
      const fibre = g0 + (g1 - g0) * gf;
      const cxf = x + wy * 62 + (n46 - 0.5) * 30;
      const ci = Math.floor(cxf), cf = cxf - ci;
      const q0 = GRAIN2[ci & MASK], q1 = GRAIN2[(ci + 1) & MASK];
      const coarse = q0 + (q1 - q0) * cf;
      const grain = ihash(x, y, 97) - 0.5;
      // stand-in for a dedicated fine field: mid detail plus texel grain
      const hf = sat(0.5 + (n46 - 0.5) * 1.15 + grain * 0.62);
      const cr = 1 - Math.abs(n46 * 2 - 1);            // creases of n46
      const fr = 1 - Math.abs(fibre * 2 - 1);          // creases along the grain
      const fr2 = 1 - Math.abs(coarse * 2 - 1);        // coarse axial creases

      /* -------- how far the burn has got ------------------------------- */
      /* Most of a log in a live fire is blackened, but bark survives in
         sizeable patches — the brief asks for them and at the old threshold
         they covered barely 10% of the tile and never read. ~20% bark plus a
         wide, noise-jittered transition band keeps the boundary blotchy. */
      const charM = ss(burn + (n46 - 0.5) * 0.20 + (fibre - 0.5) * 0.13, 0.365, 3.6);

      /* -------- axial fissures ------------------------------------------
         Three well-separated scales of crease — coarse ridge lanes, mid
         cross-checking, hairline splits along the grain — all stretched
         along V. This field does double duty: it is the bark's fissure
         pattern, and in the burnt areas it is the checking that opens up
         along the grain as the wood dries and splits. */
      const lane = 1 - Math.abs(ivn(u * BU + wx * 3.4, vB + wy * 0.9, BU, BV, 1301) * 2 - 1);
      const fis = lane * lane * 0.44 + fr2 * fr2 * 0.28 + fr * fr * 0.20 + cr * cr * 0.08;
      // the threshold drifts over the log, so ridge width varies at metre scale too
      const barkPlate = 1 - ss(fis, 0.19 + 0.13 * form, 3.4);   // 0 down in a fissure
      const check = ss(fis, 0.44, 3.0);                // axial split in the char
      const bH = 0.34 + 0.47 * barkPlate * barkPlate + 0.09 * fibre
        - 0.20 * check * check;                        // deepest fissure floors

      /* -------- char: worley craquelure of flat plates ------------------
         How far the craquelure has got varies over the log: gaping black
         fissures in one stretch, a smooth blistered skin in the next. */
      const gape = 0.30 + 1.55 * form * (0.45 + 0.75 * burn);
      const cgp = gape > 1 ? 1 : gape;
      let pid = 0.5, plate = 1, crackDeep = 0, shoulder = 0, craze = 0, spall = 0, cH = 0.5;
      if (charM > 0.006) {
        tworley(u * CU + wx * 1.6, vC + wy * 1.0, CT, CU, CV);
        pid = WID;
        // the crack net wobbles at the texel scale, so never a clean Voronoi
        const cd = (W2 - W1) + (n46 - 0.5) * 0.13 + grain * 0.005;
        crackDeep = 1 - ss(cd, 0.0, 10.0);             // wide black floor
        const cw = 1 - cd * 3.4; const crackWide = cw > 0 ? cw : 0;
        plate = ss(cd, 0.05, 7.0);                     // flat plate interior
        // the lip of char just back from a crack, curled proud of the wood
        const sd = 1 - (cd - 0.11) * 6.5; shoulder = plate * (sd > 1 ? 1 : sd > 0 ? sd : 0);
        // crazing inside a plate; some plates are far more broken than others
        craze = cr * cr * cr * (0.30 + 0.70 * pid);
        // a few plates have spalled clean off, exposing smoother wood beneath
        const sp = (form * 0.55 + pid * 0.45 + burn * 0.25 - 0.66) * 9;
        spall = sp > 1 ? 1 : sp > 0 ? sp * sp : 0;
        cH = 0.38 + 0.20 * plate + 0.075 * (pid - 0.5)
           - (0.42 * crackDeep + 0.09 * crackWide) * gape
           - 0.13 * check * (0.4 + 0.6 * plate) * (0.5 + 0.9 * form)
           - 0.022 * craze + 0.045 * shoulder - 0.14 * spall;
      }

      /* -------- combine, then ash on top ------------------------------- */
      let h = lerp(bH, cH, charM);
      h += (form - 0.5) * 0.17;                        // the log is not a tube

      /* Ash settles in patches, mostly on the raised faces (the upward ones
         on a log in a firebox). Powdery, so it fills the relief it covers. */
      /* the grain term streaks the ash along the log the way a real deposit
         clings to the fibre, and stops the blob field reading as paint */
      const ashBlob = blK(LF, b00, b01, b10, b11, 3, fx, fy)
        + (n46 - 0.5) * 0.24 + (fibre - 0.5) * 0.21 + (hf - 0.5) * 0.10;
      const ah = (h - 0.30) * 5; const ahc = ah > 1 ? 1 : ah > 0 ? ah : 0;
      let ash = ss(ashBlob, 0.545, 8.0) * ahc * (0.30 + 0.85 * hf);
      ash = sat(ash) * (0.25 + 0.75 * charM);
      h = lerp(h, h * 0.90 + 0.13, ash * 0.85);

      /* Micro structure. This was driven by `hf`, which is half single-texel
         hash, and it turned the normal map into RGB confetti that will alias
         into fizz the moment the log is more than a metre away. Granular char
         is millimetre-scale, not texel-scale: drive it from the mid fields and
         leave only a whisper of per-texel dither. */
      h += ((n46 - 0.5) * 0.020 + (fibre - 0.5) * 0.013) * (0.4 + 0.6 * charM)
         + grain * 0.0022;
      /* 4.4% of the tile was pinned flat at height 0 — the deepest crack
         floors were clipped, which throws away the relief down there and
         flat-bottoms the normal map. A C1 soft floor compresses the tail
         instead: identical above 0.10, asymptotic to 0 below it. */
      height[i] = h > 0.10 ? (h > 1 ? 1 : h) : 0.01 / (0.20 - h);

      /* -------- albedo --------------------------------------------------
         Bark and char are each essentially one hue over a range of
         luminances, so their ramps are scalar multiplies and the small
         chromatic departures (glassy char is cooler, exposed wood warmer)
         are additive tweaks. Only the mixes that really change hue —
         bark/char, ember, ash — are done as colour interpolations. */
      const bt = 0.36 + 1.66 * sat(barkPlate * 1.25 - 0.14 + (fibre - 0.5) * 0.36);
      const ct = (1 - cgp * 0.60 * (1 - plate)) * (0.80 + 0.40 * pid) * (1 - 0.34 * check * cgp);
      let R = lerp(bmR * bt, chR * ct, charM);
      let G = lerp(bmG * bt, chG * ct, charM);
      let B = lerp(bmB * bt * (1.06 - 0.06 * bt), chB * ct, charM);

      // vitrified char: glassy and faintly cool. Forms where ash has not settled.
      const glassM = ss(pid * 0.55 + (0.62 - ashBlob) * 0.45 + craze * 0.10, 0.575, 7.5) * plate * charM;
      // exposed wood where a plate has spalled off is warmer than the char
      const sw = spall * 0.8 * charM;
      const sh = shoulder * charM, cz = craze * charM;
      R += glassM * (gsR - chR) + sw * (spR - chR) + sh * 0.040 - cz * 0.024;
      G += glassM * (gsG - chG) + sw * (spG - chG) + sh * 0.038 - cz * 0.022;
      B += glassM * (gsB - chB) + sw * (spB - chB) + sh * 0.035 - cz * 0.020;

      /* embers glimpsed down inside the deepest cracks, where the burn is
         furthest along — rare, and never bright enough to look painted */
      if (crackDeep > 0.02 && burn > 0.6) {
        const em = ss(burn, 0.62, 3.2) * crackDeep * crackDeep * charM * ss(pid, 0.42, 3.0) * 0.55;
        R = lerp(R, emR, em); G = lerp(G, emG, em); B = lerp(B, emB, em);
      }

      // ash last, sitting on top of everything
      if (ash > 0.003) {
        const at = 0.20 + 0.80 * hf;
        R = lerp(R, lerp(alR, ahR, at), ash);
        G = lerp(G, lerp(alG, ahG, at), ash);
        B = lerp(B, lerp(alB, ahB, at), ash);
      }

      /* Nothing is allowed to hit zero: real charcoal reflects a few per
         cent, and the cavity AO below is what takes the crack floors down. */
      const dk = 0.80 + 0.30 * form + (hf - 0.5) * 0.16;
      const rr = R * dk, gg = G * dk, bb = B * dk;
      albedo[i * 3] = rr < 0.031 ? 0.031 : rr > 1 ? 1 : rr;
      albedo[i * 3 + 1] = gg < 0.030 ? 0.030 : gg > 1 ? 1 : gg;
      albedo[i * 3 + 2] = bb < 0.029 ? 0.029 : bb > 1 ? 1 : bb;

      /* -------- roughness ---------------------------------------------- */
      let rg = 0.965 - 0.030 * barkPlate;              // bark crests polish a hair
      rg = lerp(rg, 0.955 - 0.035 * plate, charM);     // char plate faces
      rg -= glassM * 0.34;                             // vitrified char is glassy
      rg -= sw * 0.10;                                 // exposed wood is smoother
      rg += crackDeep * 0.03 + craze * 0.015 + check * 0.02 * charM;
      rg = lerp(rg, 1.0, ash * 0.95);                  // ash is pure chalk
      rg += (hf - 0.5) * 0.07 + grain * 0.03;
      rough[i] = rg < 0.5 ? 0.5 : rg > 1 ? 1 : rg;
    }
  }
  };
  band(0, 24); band(24, size);

  const ao = cavityAO(height, size, 1.15, 64);
  return { albedo, rough, height, ao };
}

/* ==================================================================== leaf */
/*
   One fiddle-leaf-fig / rubber-plant leaf filling the UV square, tip at the
   top (V = 1), midrib vertical. Deliberately NOT tileable.

   The cue that sells a leaf is not the veins themselves but what happens
   between them: on the upper surface the vein network is *sunken* and the
   lamina bulges up into cushions between it. So the whole surface is built
   from one distance field — distance to the nearest vein, midrib, margin or
   tertiary strand — and the cushions are that distance domed upward.
*/

/** half-width of the leaf silhouette at along-leaf position a (0 base, 1 tip) */
function leafHalfWidth(a) {
  if (a <= 0 || a >= 1) return 0;
  const a3 = a * a * a;
  /* The 0.30 power gives the broad shoulders a fig has, but on its own it
     leaves the blade still ~0.48 wide at the top row — the leaf came out
     chopped flat instead of pointed. The final factor tapers the last third
     into an actual tip. */
  return 0.53 * Math.pow(Math.sin(Math.PI * Math.pow(a, 0.86)), 0.30) * (1 - a3 * a3);
}

export function leaf(size, N) {
  const { hex } = N;
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);

  const [dpR, dpG, dpB] = hex(0x1e3618);   // shaded tissue flanking a vein
  const [bsR, bsG, bsB] = hex(0x2f5424);   // lamina
  const [ltR, ltG, ltB] = hex(0x4a7331);   // puffed crowns, a touch yellower
  /* On the UPPER surface of a fig the secondaries are sunken but PALE — the
     tissue either side of the channel is what goes dark. Painting the whole
     vein dark (as before) turned them into black slots and the leaf read as a
     moulded rubber mat. */
  const [vpR, vpG, vpB] = hex(0x638141);   // the vein itself
  const [rbR, rbG, rbB] = hex(0x7d9155);   // midrib, pale
  const [mgR, mgG, mgB] = hex(0x6f8b4b);   // paler rim
  const [byR, byG, byB] = hex(0x6f6033);   // dry blemish
  const [obR, obG, obB] = hex(0x3a5c2b);   // outside the silhouette

  const NPAIR = 12;                        // secondary vein pairs
  const A0 = 0.045, ASP = 0.080, IASP = 1 / ASP;

  /* ---- vein parameters, tabulated once (they do not vary per texel) --- */
  const NV = NPAIR + 2;
  const VAI = new Float32Array(2 * NV), VKI = new Float32Array(2 * NV), VCI = new Float32Array(2 * NV);
  /* Spacing jitter alone was not enough: every secondary had the identical
     width and depth, which is what made the set read as a machined herringbone.
     Per-vein width and prominence break the rank. */
  const VWV = new Float32Array(2 * NV), VDI = new Float32Array(2 * NV);
  for (let s = 0; s < 2; s++) {
    for (let vi = 0; vi < NV; vi++) {
      const k = s * NV + vi;
      const ai = A0 + vi * ASP + (ihash(vi, s, 7001) - 0.5) * ASP * 0.62;
      VAI[k] = ai > 0.02 && ai < 1.0 ? ai : -1;
      VKI[k] = 0.44 + 0.60 * ai + (ihash(vi, s, 7103) - 0.5) * 0.24;
      VCI[k] = 0.42 + (ihash(vi, s, 7207) - 0.5) * 0.85;
      // base half-width folded in, so the texel loop is one read and no multiply
      VWV[k] = (0.0092 + 0.0058 * (1 - ai)) * (0.78 + ihash(vi, s, 7309) * 0.50);
      VDI[k] = 0.70 + ihash(vi, s, 7411) * 0.46;
    }
  }

  /* ---- sparse blemishes, splatted so they cost nothing per texel ------ */
  const blem = N.newF(px);
  for (let k = 0; k < 24; k++) {
    const h1 = ihash(k, 7, 5501), h2 = ihash(k, 13, 5501);
    const h3 = ihash(k, 19, 5501), h4 = ihash(k, 23, 5501);
    if (h4 > 0.62) continue;                       // not every slot is used
    const a = 0.08 + h1 * 0.86;
    const W = leafHalfWidth(a);
    const cxp = (0.5 + (h2 - 0.5) * 1.8 * W) * size;
    const cyp = (1 - a) * size;
    const r = (0.005 + h3 * h3 * 0.022) * size;
    const ri = Math.ceil(r) + 1;
    for (let dy = -ri; dy <= ri; dy++) {
      const yy = Math.round(cyp) + dy;
      if (yy < 0 || yy >= size) continue;
      for (let dx = -ri; dx <= ri; dx++) {
        const xx = Math.round(cxp) + dx;
        if (xx < 0 || xx >= size) continue;
        const d = Math.sqrt(dx * dx + dy * dy) / r;
        const wob = 0.70 + 0.55 * ivn(xx * 0.10, yy * 0.10, 0, 0, 5600 + k);
        const s = 1 - ss(d, wob * 0.5, 2 / wob);
        if (s > blem[yy * size + xx]) blem[yy * size + xx] = s;
      }
    }
  }

  /* Same JIT priming as the log: a narrow band first so the bulk of the
     image runs through optimised code. */
  const band = (yStart, yEnd) => {
  for (let y = yStart; y < yEnd; y++) {
    const py = (y + 0.5) / size;
    const a = 1 - py;                       // 0 at the base, 1 at the tip
    const W = leafHalfWidth(a), IW = 1 / (W > 1e-3 ? W : 1e-3);
    // the midrib is not perfectly straight
    const cxc = 0.5 + 0.016 * Math.sin(a * 3.4 + 0.9) + 0.008 * Math.sin(a * 8.1 + 2.2);
    const mw = 0.005 + 0.031 * Math.sqrt(a < 1 ? 1 - a : 0);
    const imw = 1 / mw;
    const Ka = 0.50 + 0.55 * a;             // veins angle harder toward the tip
    const py9 = py * 9, py23 = py * 23, py66 = py * 66;
    /* The midrib has to die out at the apex as well as at the base: it is
       ~5 texels wide up there while the blade has narrowed to nothing, so
       left running it put a bright spike above the tip. */
    const rowTip = ss(a, 0.0, 20) * (1 - ss(a, 0.86, 7.5));

    for (let x = 0; x < size; x++) {
      const i = y * size + x;
      const u = (x + 0.5) / size;
      const sx = u - cxc, ax = sx < 0 ? -sx : sx;
      const base = (sx < 0 ? 0 : 1) * NV;

      /* a little domain warp so nothing in here is mathematically clean */
      const wr = ivn(u * 9, py9, 0, 0, 6101) - 0.5;
      const wr2 = ivn(u * 23, py23, 0, 0, 6203) - 0.5;

      /* -------- silhouette / margin ------------------------------------ */
      const Wj = W + wr2 * 0.020 + wr * 0.010;
      const dEdgeS = Wj - ax;
      const inside = ss(dEdgeS, -0.006, 83.3);
      const insideH = ss(dEdgeS, -0.036, 21);
      const dEdge = dEdgeS > 0 ? dEdgeS : 0;

      /* -------- midrib -------------------------------------------------- */
      const mt = ax * imw < 1 ? ax * imw : 1;
      const md = 1 - mt * mt;
      const midDome = Math.sqrt(md) * (0.72 + 0.28 * md);
      const mg0 = 1 - ax * imw * 3.8;                 // shallow crown channel
      const midGroove = mg0 > 0 ? mg0 * mg0 : 0;

      /* -------- secondary veins ----------------------------------------
         Each vein is the curve  a = ai + Ki*ax + Ci*ax²  anchored on the
         midrib; solving for the index gives the two or three that could
         possibly be nearest, so this stays O(3) per texel. */
      let vein = 0, dv = 1e9, veinCore = 0;
      const qi = Math.round((a + wr * 0.012 - Ka * ax - 0.42 * ax * ax - A0) * IASP);
      // veins do not cross the midrib
      const ribGuard = ss(ax, mw * 0.55, 1 / (mw * 0.8));
      for (let k = -1; k <= 1; k++) {
        const vi = qi + k;
        if (vi < 0 || vi >= NV) continue;
        const ai = VAI[base + vi];
        if (ai < 0) continue;
        const Ki = VKI[base + vi], Ci = VCI[base + vi];
        const aV = ai + Ki * ax + Ci * ax * ax + wr * 0.010 + wr2 * 0.004;
        const slope = Ki + 2 * Ci * ax;
        const dd = a - aV;
        const d = (dd < 0 ? -dd : dd) / Math.sqrt(1 + slope * slope);
        // taper: thinner far from the midrib, thinner toward the tip
        const wv = VWV[base + vi] * (1 - 0.45 * (ax > 0.5 ? 1 : ax * 2));
        // veins arch and die out before the margin
        // fig secondaries arch and loop well short of the margin
        const fade = (1 - ss(ax * IW, 0.58, 2.8)) * ribGuard;
        const s = ss(-d, -wv, 1 / (wv * 0.85)) * fade * VDI[base + vi];
        if (s > vein) vein = s;
        if (s > 0.35) {
          const c = ss(-d, -wv * 0.42, 2.4 / wv) * fade;
          if (c > veinCore) veinCore = c;
        }
        const dn = d + (1 - fade) * 0.06;
        if (dn < dv) dv = dn;
      }

      /* -------- tertiary reticulum -------------------------------------
         The fine web between the secondaries. Ridged noise rather than a
         cell pattern: real areoles are irregular, branch and dead-end,
         where a Voronoi net reads as uniform bubble wrap. Barely any
         relief — just enough to break up the specular. */
      /* One octave, not two. The second octave sat at ~130 cells — under four
         texels — so it was fizz that would alias the moment the plant is more
         than a metre from camera, and it cost a whole extra noise fetch. */
      const tw = iridge(u * 66 + wr2 * 3.0, py66 + wr * 3.0, 0, 0, 7411, 1);
      /* Threshold set from the field's own quantiles (~p70 to the top few
         per cent), so the web is thin strands. Anything broader turns the
         lamina into brain coral rather than a reticulum. */
      const tert = ss(tw, 0.62, 2.8);

      /* -------- puffed cushions ---------------------------------------- */
      const dMid = ax - mw * 1.15 > 0 ? ax - mw * 1.15 : 0;
      let dCush = dv < dMid ? dv : dMid;
      const de = dEdge * 1.1; if (de < dCush) dCush = de;
      const puff = ss(dCush, 0.0, 22) * (0.96 - 0.075 * tert);

      /* -------- height -------------------------------------------------- */
      let h = 0.36;
      h += 0.40 * puff;
      /* was -0.19: at that depth the secondaries cut clean through the
         cushions and rendered as black slots. A fig's channels are shallow
         relative to the blistering, and the vein cord itself sits proud
         inside its own groove — that lift is most of what reads as "vein". */
      h -= 0.115 * vein;
      h += 0.030 * veinCore;
      h -= 0.0105 * tert;
      h += 0.30 * midDome * rowTip;
      h -= 0.055 * midGroove * midDome * rowTip;
      h += (ihash(x, y, 199) - 0.5) * 0.0035 + wr2 * 0.012;
      /* The silhouette is faded out over a much wider band than the colour
         is: a 6-texel height cliff at the edge put a hard embossed outline in
         the normal map that does not exist on the (already leaf-shaped) mesh. */
      h = lerp(0.345, h, insideH);
      height[i] = sat(h);

      /* -------- albedo -------------------------------------------------- */
      /* `puff` is ~1 over most of the lamina, so the old 0.88 weight pinned t
         at 1 and the whole blade came out the yellow-green crown colour. Halve
         it: the deep base green now dominates and the yellow is a crown tint. */
      const t = sat(0.10 + puff * 0.46 + wr * 0.50 + wr2 * 0.16);
      let R = lerp(bsR, ltR, t), G = lerp(bsG, ltG, t), B = lerp(bsB, ltB, t);
      // tissue flanking the channel darkens...
      const vt = sat(vein * 0.58 + tert * 0.062);
      R = lerp(R, dpR, vt); G = lerp(G, dpG, vt); B = lerp(B, dpB, vt);
      // ...and the vein cord running down the middle of it is pale
      const vc = veinCore * 0.54;
      R = lerp(R, vpR, vc); G = lerp(G, vpG, vc); B = lerp(B, vpB, vc);
      // midrib, pale and slightly yellow
      const mrb = midDome * rowTip;
      R = lerp(R, rbR, mrb * 0.72); G = lerp(G, rbG, mrb * 0.72); B = lerp(B, rbB, mrb * 0.72);
      // paler margin
      const mgn = 1 - ss(dEdge, 0.0, 13.3);
      R = lerp(R, mgR, mgn * 0.55); G = lerp(G, mgG, mgn * 0.55); B = lerp(B, mgB, mgn * 0.55);
      // blemishes
      const bm = blem[i] * inside;
      R = lerp(R, byR, bm * 0.72); G = lerp(G, byG, bm * 0.72); B = lerp(B, byB, bm * 0.72);

      const dk = 0.97 + wr * 0.16 + (ihash(x, y, 233) - 0.5) * 0.03;
      let Ro = R * dk, Go = G * dk, Bo = B * dk;
      // outside the silhouette, settle on a plain lamina green so the mesh
      // edge cannot fringe against a dark rim
      if (inside < 0.997) {
        const o = 1 - inside;
        Ro = lerp(Ro, obR, o); Go = lerp(Go, obG, o); Bo = lerp(Bo, obB, o);
      }
      albedo[i * 3] = sat(Ro);
      albedo[i * 3 + 1] = sat(Go);
      albedo[i * 3 + 2] = sat(Bo);

      /* -------- roughness ----------------------------------------------
         Waxy cuticle: the crowns of the cushions catch the sheen, the vein
         valleys hold dust and read matte. */
      let rg = 0.41 - 0.14 * puff + 0.11 * vein + 0.035 * tert;
      rg -= 0.04 * mrb;
      rg += 0.16 * bm;
      // house dust and dried spray settle unevenly; a constant gloss is the
      // single most plastic-looking thing a leaf can have
      rg += wr * 0.15 + wr2 * 0.06 + (ihash(x, y, 271) - 0.5) * 0.022;
      rough[i] = rg < 0.22 ? 0.22 : rg > 0.52 ? 0.52 : rg;
    }
  }
  };
  band(0, 24); band(24, size);

  const ao = cavityAO(height, size, 0.9, 128);
  return { albedo, rough, height, ao };
}
