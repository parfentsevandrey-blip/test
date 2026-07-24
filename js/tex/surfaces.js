/* =========================================================================
   Hard surfaces: honed grey-green marble, bookbinding buckram, aged brass.

   Shared machinery
   ----------------
   `N.vnoise`/`N.gnoise` hash with Math.sin, which is fine for a few thousand
   draws but not for the millions of samples these three need (a single
   3-octave field at 512² already costs ~90 ms that way). So the noises below
   are the same maths — same fade curves, same lattice wrap — on a cheap
   integer hash, with SEPARATE periods per axis. Independent periods are what
   let a field be stretched 60:1 along one axis (brush lines, weave slub)
   while still wrapping exactly on the unit tile.

   Anisotropy elsewhere comes from integer UNIMODULAR shears of the uv before
   scaling, e.g. (u + 2v, v). Those map the wrap lattice onto itself, so a
   marble vein family can be given a direction and a 2.4:1 stretch with no
   seam and no repeat inside the tile.

   Veins are extracted as ISOLINES of smooth fields, and the offset from the
   isoline is divided by the field's own gradient magnitude. That converts
   (s - level) into a SIGNED DISTANCE IN TEXELS, so a vein has a width you
   author in millimetres instead of a width that flickers with the local
   slope — and one field can then carry a 2-texel core and a 28-texel halo
   that stay registered with each other. Because the division happens on the
   coarse grid, the whole thing also collapses to a single interpolated
   channel per vein family in the hot loop.
   ========================================================================= */

const TAU = Math.PI * 2;

const cl = (v, a, b) => (v < a ? a : v > b ? b : v);
/** smoothstep with the edges in either order: ss(hi, lo, d) → 1 when d < lo */
function ss(a, b, x) {
  let t = (x - a) / (b - a);
  t = t < 0 ? 0 : t > 1 ? 1 : t;
  return t * t * (3 - 2 * t);
}

/* ------------------------------------------------------------------ hashing
   Integer hash, uint32 out. The float form divides once at the end; the
   gradient form never leaves the integer domain and takes its table index
   straight off the top byte. */
function ihashi(x, y, s) {
  let h = (Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263) + Math.imul(s | 0, 1274126177)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return (h ^ (h >>> 16)) >>> 0;
}
const I2F = 2.3283064365386963e-10;              // 1 / 2^32
const ihash = (x, y, s) => ihashi(x, y, s) * I2F;

/** 256 unit vectors, indexed by hash — avoids a cos/sin pair per lattice corner */
const GRAD = new Float32Array(512);
for (let i = 0; i < 256; i++) {
  const a = ((i + 0.5) / 256) * TAU;
  GRAD[i * 2] = Math.cos(a);
  GRAD[i * 2 + 1] = Math.sin(a);
}

/* The lattice wrap is the hot instruction in every one of these generators.
   `((v % p) + p) % p` costs two integer divisions per axis per sample, and at
   two million samples that alone was measuring ~60 ms. Both corners of an
   axis differ by exactly one cell, so only the LOW corner needs the division
   and the high one is a compare — halving the divides. */

/** tileable value noise, 0..1, with independent x/y periods */
function vnp(x, y, PX, PY, seed) {
  const x0 = Math.floor(x), y0 = Math.floor(y);
  let fx = x - x0, fy = y - y0;
  fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
  let xa = (x0 | 0) % PX; if (xa < 0) xa += PX;
  const xb = xa + 1 === PX ? 0 : xa + 1;
  let ya = (y0 | 0) % PY; if (ya < 0) ya += PY;
  const yb = ya + 1 === PY ? 0 : ya + 1;
  const a = ihashi(xa, ya, seed) * I2F, b = ihashi(xb, ya, seed) * I2F;
  const c = ihashi(xa, yb, seed) * I2F, d = ihashi(xb, yb, seed) * I2F;
  return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}

/** tileable gradient (Perlin) noise, 0..1, with independent x/y periods */
function gnp(x, y, PX, PY, seed) {
  const x0 = Math.floor(x), y0 = Math.floor(y);
  const fx = x - x0, fy = y - y0;
  const u = fx * fx * fx * (fx * (fx * 6 - 15) + 10);
  const v = fy * fy * fy * (fy * (fy * 6 - 15) + 10);
  let xa = (x0 | 0) % PX; if (xa < 0) xa += PX;
  const xb = xa + 1 === PX ? 0 : xa + 1;
  let ya = (y0 | 0) % PY; if (ya < 0) ya += PY;
  const yb = ya + 1 === PY ? 0 : ya + 1;
  let k = (ihashi(xa, ya, seed) >>> 23) & 510;
  const n00 = GRAD[k] * fx + GRAD[k + 1] * fy;
  k = (ihashi(xb, ya, seed) >>> 23) & 510;
  const n10 = GRAD[k] * (fx - 1) + GRAD[k + 1] * fy;
  k = (ihashi(xa, yb, seed) >>> 23) & 510;
  const n01 = GRAD[k] * fx + GRAD[k + 1] * (fy - 1);
  k = (ihashi(xb, yb, seed) >>> 23) & 510;
  const n11 = GRAD[k] * (fx - 1) + GRAD[k + 1] * (fy - 1);
  const a = n00 + u * (n10 - n00), b = n01 + u * (n11 - n01);
  return (a + v * (b - a)) * 0.7071 + 0.5;
}

/** sin(pi * f) for f in [0,1], to <1% — the weave interlace needs the shape,
    not the transcendental, and Math.cos was 12% of the bookCloth loop */
function sinpi01(f) {
  const q = 4 * f * (1 - f);
  return q * (0.775 + 0.225 * q);
}

/** fractal value noise */
function vfbm(x, y, PX, PY, seed, oct = 4, gain = 0.5) {
  let v = 0, amp = 1, f = 1, n = 0;
  for (let i = 0; i < oct; i++) {
    v += amp * vnp(x * f, y * f, PX * f, PY * f, seed + i * 97);
    n += amp; f *= 2; amp *= gain;
  }
  return v / n;
}
/** fractal gradient noise */
function gfbm(x, y, PX, PY, seed, oct = 3, gain = 0.5) {
  let v = 0, amp = 1, f = 1, n = 0;
  for (let i = 0; i < oct; i++) {
    v += amp * gnp(x * f, y * f, PX * f, PY * f, seed + i * 131);
    n += amp; f *= 2; amp *= gain;
  }
  return v / n;
}

/** per-axis resample tables: cell indices + a C1 (smoothstepped) weight */
function axis(n, P) {
  const i0 = new Int32Array(n), i1 = new Int32Array(n), fr = new Float32Array(n);
  for (let k = 0; k < n; k++) {
    const t = ((k + 0.5) / n) * P - 0.5;
    const f0 = Math.floor(t);
    let a = f0 % P; if (a < 0) a += P;
    i0[k] = a; i1[k] = (a + 1) % P;
    const f = t - f0;
    fr[k] = f * f * (3 - 2 * f);
  }
  return { i0, i1, fr };
}

/* ========================================================================= */
/*  MARBLE — dark grey-green honed marble, coffee-table top                  */
/* ========================================================================= */
/* Three vein families of steeply decreasing weight, each an isoline set of a
   warped fractal field sheared onto a DIFFERENT axis so they cross at wide
   angles instead of combing together:

     A  a handful of dominant strands over about a quarter of the slab,
        1-5 texel core, 28-texel halo
     B  a subordinate web, ~1 texel core, short halo, mostly where A is absent
     C  hairline capillaries — a tint, not a line — and gated so that they
        clear entirely over part of the field

   Two things stop this reading as a contour map, which is the failure mode of
   naive isolines: presence gates each family's WIDTH (not its opacity), so a
   strand tapers to nothing and returns rather than running edge to edge; and
   the halo carries most of the visual weight. A hard core with a soft
   coloured bleed either side is what separates translucent stone from a
   painted stripe, and it is the halo the eye reads as depth.

   Finish is honed: roughness 0.10..0.32 driven by broad warped hone swirls,
   with a sparse scatter of polishing scratches that are also the only real
   relief in the height map — polished stone is flat. */
export function marble(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const { hex } = N;

  /* ---- palette. Matrix mid luma ~0.23 sRGB → ~0.045 linear, which is where
     real dark marble actually sits; the darkness in the room comes from the
     lighting, not from a black albedo. */
  const [MDr, MDg, MDb] = hex(0x2a3128);   // matrix, dark charcoal-green
  const [MLr, MLg, MLb] = hex(0x515a4c);   // matrix, lit grain
  const [WMr, WMg, WMb] = hex(0x584f3b);   // warm mineral drift
  const [VCr, VCg, VCb] = hex(0xdcd5be);   // vein core, warm off-white
  const [VPr, VPg, VPb] = hex(0xafbda0);   // vein core, pale green
  const [VHr, VHg, VHb] = hex(0x757f6a);   // translucent halo

  /* ================================================= W² smooth fields (64²)
     0 warpX  1 warpY  2 presence  3 hone  4 mottle  5 tint */
  const W = 64, WC = 6;
  const wf = N.newF(W * W * WC);
  for (let y = 0; y < W; y++) {
    const v = (y + 0.5) / W;
    for (let x = 0; x < W; x++) {
      const u = (x + 0.5) / W;
      const o = (y * W + x) * WC;
      // two-scale warp: the coarse term bends whole strands, the finer one
      // gives them the organic kinks that keep them off a smooth loop
      const wx = (vfbm(u * 3, v * 3, 3, 3, 811, 3) - 0.5)
               + (vfbm(u * 9, v * 9, 9, 9, 823, 2) - 0.5) * 0.42;
      const wy = (vfbm(u * 3, v * 3, 3, 3, 947, 3) - 0.5)
               + (vfbm(u * 9, v * 9, 9, 9, 953, 2) - 0.5) * 0.42;
      wf[o] = wx; wf[o + 1] = wy;
      wf[o + 2] = vfbm(u * 4, v * 4, 4, 4, 1231, 4);
      // the hone swirl is itself warped, so the duller patches curl the way a
      // polishing head actually leaves them
      wf[o + 3] = vfbm(u * 3 + wx * 2.2, v * 3 + wy * 2.2, 3, 3, 1607, 3);
      wf[o + 4] = vfbm(u * 7, v * 7, 7, 7, 1901, 4);
      wf[o + 5] = vfbm(u * 2, v * 2, 2, 2, 2203, 3);
    }
  }

  /* ============================================ H² vein signed-distance pass
     Just under a third resolution: the coarsest vein wavelength is ~230 texels
     and the finest ~20, so even family C keeps ~6 samples per wavelength here,
     which is all a level set of a band-limited field can use — at 3/8 the
     output was indistinguishable and the pass cost a quarter more. The
     level offset is folded in BEFORE the gradient, so what ships to the hot
     loop is one number per family: how many texels this texel sits from the
     vein centreline. The isoline is still thresholded at full res, so cores
     stay one texel crisp. */
  const H = Math.max(96, (size * 5) >> 4);
  const HC = 7;                        // dA dB dC | presence hone mottle tint
  const hf = N.newF(H * H * HC);
  {
    const tA = N.newF(H * H), tB = N.newF(H * H), tC = N.newF(H * H);
    const ax = axis(H, W), ay = axis(H, W);
    for (let y = 0; y < H; y++) {
      const v = (y + 0.5) / H;
      const r0 = ay.i0[y] * W, r1 = ay.i1[y] * W, fy = ay.fr[y];
      for (let x = 0; x < H; x++) {
        const u = (x + 0.5) / H, fx = ax.fr[x];
        const j00 = (r0 + ax.i0[x]) * WC, j10 = (r0 + ax.i1[x]) * WC;
        const j01 = (r1 + ax.i0[x]) * WC, j11 = (r1 + ax.i1[x]) * WC;
        const k00 = (1 - fx) * (1 - fy), k10 = fx * (1 - fy);
        const k01 = (1 - fx) * fy, k11 = fx * fy;
        const wx = wf[j00] * k00 + wf[j10] * k10 + wf[j01] * k01 + wf[j11] * k11;
        const wy = wf[j00 + 1] * k00 + wf[j10 + 1] * k10 + wf[j01 + 1] * k01 + wf[j11 + 1] * k11;
        const pr = wf[j00 + 2] * k00 + wf[j10 + 2] * k10 + wf[j01 + 2] * k01 + wf[j11 + 2] * k11;
        const hn = wf[j00 + 3] * k00 + wf[j10 + 3] * k10 + wf[j01 + 3] * k01 + wf[j11 + 3] * k11;
        const mo = wf[j00 + 4] * k00 + wf[j10 + 4] * k10 + wf[j01 + 4] * k01 + wf[j11 + 4] * k11;
        const ti = wf[j00 + 5] * k00 + wf[j10 + 5] * k10 + wf[j01 + 5] * k01 + wf[j11 + 5] * k11;

        const i = y * H + x;
        /* The broad fields ride along into the same array as the veins. One
           interpolation setup in the hot loop instead of two is worth more
           than the memory: the index and weight arithmetic was costing about
           as much as the noise. */
        const o = i * HC;
        hf[o + 3] = pr; hf[o + 4] = hn; hf[o + 5] = mo; hf[o + 6] = ti;
        /* Each family is sampled ANISOTROPICALLY — a low frequency along the
           vein and a high one across it. That is what turns isolines from the
           round closed loops of an isotropic field into long directional
           strands; the shear then aims them. Independent x/y periods make it
           tile anyway. A runs up-right, B crosses it at right angles, C is a
           third, flatter direction. */
        /* The three families are deliberately a WHOLE OCTAVE apart in scale
           and in octave count. Fields of similar frequency give an even mat
           of squiggles — the eye reads that as hair or wood, not stone. What
           marble actually has is a few bold seams, a subordinate web at half
           the scale, and hairlines below that.

           A gets only two octaves at low gain so its strands stay smooth and
           long; all of A's wander comes from the domain warp, which bends
           whole seams instead of adding high-curvature wiggle. */
        // A — bold seams, ~2.5:1 elongation, running up-right
        // the level sits off the field's mean: the median isoline of a
        // Gaussian field is its longest and most evenly spaced, which is
        // exactly the even network that reads as a contour map
        tA[i] = gfbm(u * 2 + wx * 2.3, (v - u) * 5 + wy * 2.3, 2, 5, 3301, 2, 0.40)
              - (0.575 + (ti - 0.5) * 0.19 + (mo - 0.5) * 0.11);
        // B — subordinate web crossing A at right angles. Its warp is rotated
        // 90° so it does not bend in sympathy with A.
        tB[i] = gfbm(u * 4 + wy * 1.3, (v + u) * 9 - wx * 1.3, 4, 9, 4507, 3, 0.45)
              - (0.5 + (mo - 0.5) * 0.26 + (hn - 0.5) * 0.17);
        // C — hairlines on a third axis, only mildly elongated: an extreme
        // aspect down here combs the whole matrix into corduroy
        tC[i] = gfbm((u + v) * 8 + wx * 0.7, (v - 2 * u) * 13 + wy * 0.7, 8, 13, 5701, 2, 0.38)
              - (0.5 + (ti - 0.5) * 0.30);
      }
    }
    /* offset ÷ |gradient| → signed distance in full-res texels */
    const k = (H / size) * 0.5;
    for (let y = 0; y < H; y++) {
      const yp = ((y + 1) % H) * H, ym = ((y - 1 + H) % H) * H, yr = y * H;
      for (let x = 0; x < H; x++) {
        const xp = (x + 1) % H, xm = (x - 1 + H) % H;
        const o = (yr + x) * HC;
        let dx = (tA[yr + xp] - tA[yr + xm]) * k, dy = (tA[yp + x] - tA[ym + x]) * k;
        hf[o] = cl(tA[yr + x] / (Math.sqrt(dx * dx + dy * dy) + 1e-6), -60, 60);
        dx = (tB[yr + xp] - tB[yr + xm]) * k; dy = (tB[yp + x] - tB[ym + x]) * k;
        hf[o + 1] = cl(tB[yr + x] / (Math.sqrt(dx * dx + dy * dy) + 1e-6), -30, 30);
        dx = (tC[yr + xp] - tC[yr + xm]) * k; dy = (tC[yp + x] - tC[ym + x]) * k;
        hf[o + 2] = cl(tC[yr + x] / (Math.sqrt(dx * dx + dy * dy) + 1e-6), -12, 12);
      }
    }
  }

  /* ================================================================ main pass */
  const hx_ = axis(size, H), hy_ = axis(size, H);
  const inv = 1 / size;

  for (let y = 0; y < size; y++) {
    const v = (y + 0.5) * inv;
    const hr0 = hy_.i0[y] * H, hr1 = hy_.i1[y] * H, hfy = hy_.fr[y];

    for (let x = 0; x < size; x++) {
      const u = (x + 0.5) * inv;
      const i = y * size + x;

      const hfx = hx_.fr[x];
      const b00 = (hr0 + hx_.i0[x]) * HC, b10 = (hr0 + hx_.i1[x]) * HC;
      const b01 = (hr1 + hx_.i0[x]) * HC, b11 = (hr1 + hx_.i1[x]) * HC;
      const q00 = (1 - hfx) * (1 - hfy), q10 = hfx * (1 - hfy);
      const q01 = (1 - hfx) * hfy, q11 = hfx * hfy;

      /* ---- vein distances (texels) + the broad fields, one fetch ---- */
      // A stays signed: the side of the seam decides which way the halo bleeds
      const dAs = hf[b00] * q00 + hf[b10] * q10 + hf[b01] * q01 + hf[b11] * q11;
      const dA = dAs < 0 ? -dAs : dAs;
      const dB = Math.abs(hf[b00 + 1] * q00 + hf[b10 + 1] * q10 + hf[b01 + 1] * q01 + hf[b11 + 1] * q11);
      const dC = Math.abs(hf[b00 + 2] * q00 + hf[b10 + 2] * q10 + hf[b01 + 2] * q01 + hf[b11 + 2] * q11);
      const pres = hf[b00 + 3] * q00 + hf[b10 + 3] * q10 + hf[b01 + 3] * q01 + hf[b11 + 3] * q11;
      const hone = hf[b00 + 4] * q00 + hf[b10 + 4] * q10 + hf[b01 + 4] * q01 + hf[b11 + 4] * q11;
      const mot = hf[b00 + 5] * q00 + hf[b10 + 5] * q10 + hf[b01 + 5] * q01 + hf[b11 + 5] * q11;
      const tint = hf[b00 + 6] * q00 + hf[b10 + 6] * q10 + hf[b01 + 6] * q01 + hf[b11 + 6] * q11;

      /* ---- micro grain: the crystal sparkle of the stone.
         ISOTROPIC. Shearing this field (sampling it at (u+v, v)) correlates
         every lattice cell with its diagonal neighbour, and at three texels
         per cell the eye integrates that into a fine diagonal hatch laid over
         the whole slab — the matrix reads as brushed or woven rather than
         crystalline. The two periods are kept coprime instead so the grain has
         no axis of its own. ---- */
      const mg = vnp(u * 179, v * 173, 179, 173, 6101);

      /* ---- polishing scratches: sparse, short, clustered in the hone swirls */
      const smask = ss(0.60, 0.86, hone * 0.62 + mot * 0.38);
      let scr = 0;
      if (smask > 0.03) {
        const s1 = ss(0.855, 0.965, vnp(v * 205, u * 17, 205, 17, 7001));
        const s2 = ss(0.870, 0.975, vnp((u + v) * 168, (u - v) * 13, 168, 13, 7103));
        scr = (s1 > s2 ? s1 : s2) * smask;
      }

      /* ---- family A: the bold seams. Presence gates the WIDTH, so a seam
         swells to 4 texels, pinches to a hairline and dies out along its run
         rather than being chopped off. `fat` is put through a contrast curve
         first: raw fbm piles up near its mean, which would give every seam
         the same middling width — the single strongest tell that a vein was
         drawn by an algorithm. ---- */
      /* A gradient-normalised isoline is perfectly smooth, and a perfectly
         smooth line reads as ink. Displacing the DISTANCE by the millimetre
         grain before thresholding feathers the edge: the seam picks up the
         ragged, crystal-by-crystal boundary real calcite has against the
         matrix, and it costs nothing because the grain is already in hand.
         The halo keeps the unfeathered distance — a diffusion halo really is
         smooth. */
      const feath = (mg - 0.5) * 1.9;
      /* …but only in proportion to the seam's own width. Families B and C run
         about a texel wide, and displacing a line that thin by a ±1-texel
         grain does not feather its edge, it chops the line into a dotted
         trail, which reads as sensor noise rather than stone. Below a texel
         the honest modulation is of BRIGHTNESS along the run, and the width
         itself has to be resolved with a one-texel coverage ramp
         (ss(w + ½, w − ½, d)) or the line lands on whichever texel centres
         happen to fall inside it and dashes itself. */
      const gwl = cl(0.42 + 1.16 * mg, 0, 1.12);  // along-run brightness, fine veins

      /* A covers about a quarter of the slab, not a half. What a marble top
         actually looks like from two metres is a FEW bold seams sweeping
         across a mostly quiet field — spread the same amount of vein over
         twice the area and every strand comes out the same middling weight,
         which is the reading that says "generated". The width below is opened
         up to spend the saved coverage on boldness. */
      const gate = cl((pres - 0.545) * 4.0, 0, 1.25);
      const fat = ss(0.26, 0.80, mot);
      let coreA = 0, haloA = 0, selv = 0;
      const hAw = 22 * (0.16 + 0.95 * gate);
      if (dA < hAw) {
        // A bleed, not a cloud: cube the falloff so the colour is dense right
        // against the core and gone a centimetre out. It is also lopsided —
        // the fluid that deposited the calcite soaked further into the rock
        // on one side of the fracture than the other, and a halo that is
        // perfectly symmetric about its vein is the giveaway that it was
        // generated from a distance field.
        const t = ss(hAw, 0, dA) * (0.55 + 0.45 * ss(-9, 9, dAs));
        haloA = t * t * t;
        const wA = gate * (0.85 + 5.0 * fat) * (0.62 + 0.76 * hone);
        // a fat seam can take the full grain displacement; a pinched one only
        // as much as it is wide, or it dissolves into dashes
        const dAf = dA + feath * cl(wA * 0.42, 0.22, 1);
        if (dAf < wA * 4.6) {
          coreA = ss(wA + 0.45, wA * 0.18 - 0.35, dAf) * (0.44 + 0.56 * ss(0.20, 0.74, mot));
          selv = ss(wA * 4.6, wA * 1.2, dAf) * (1 - coreA);
        }
      }

      /* ---- family B: the subordinate web, mostly where A is absent ---- */
      const gateB = cl((0.76 - pres) * 2.6, 0, 1) * ss(0.32, 0.66, mot);
      let coreB = 0, haloB = 0;
      const hBw = 8 * (0.25 + 0.85 * gateB);
      if (dB < hBw) {
        haloB = ss(hBw, 0, dB);
        const wB = 1.05 * gateB;
        if (dB < wB + 0.6) coreB = ss(wB + 0.5, wB * 0.26 - 0.4, dB) * gwl;
      }

      /* ---- family C: hairlines. Everywhere, faint — this is the layer that
         stops the matrix between the seams reading as empty. ---- */
      /* Gated so it CLEARS: a capillary net of even density everywhere reads
         as craquelure on a glaze, not as stone. Real marble keeps whole hands
         of clean matrix between the busy zones, and it is those quiet areas
         that make the busy ones look like mineral. */
      const gateC = ss(0.28, 0.58, mot * 0.55 + (1 - hone) * 0.45) * (0.5 + 0.6 * ss(0.34, 0.80, tint));
      const wC = 0.62 * gateC;
      const coreC = dC < wC + 0.6 ? ss(wC + 0.5, wC * 0.2 - 0.4, dC) * gwl : 0;

      /* ---- albedo ------------------------------------------------------- */
      const tone = cl(0.5 + (mot - 0.5) * 0.94 + (tint - 0.5) * 0.70
                          + (mg - 0.5) * 0.36, 0, 1);
      let cr = MDr + (MLr - MDr) * tone;
      let cg = MDg + (MLg - MDg) * tone;
      let cb = MDb + (MLb - MDb) * tone;

      const warm = ss(0.56, 0.86, tint) * 0.22;
      cr += (WMr - cr) * warm; cg += (WMg - cg) * warm; cb += (WMb - cb) * warm;

      // the matrix right beside a vein is a shade darker — stylolite selvage
      const sm = 1 - selv * 0.11;
      cr *= sm; cg *= sm; cb *= sm;

      // the translucent bleed. It must sit UNDER the core so the core still
      // reads as a hard edge.
      const halo = haloA * (0.30 + 0.34 * gate) + haloB * haloB * 0.14;
      cr += (VHr - cr) * halo; cg += (VHg - cg) * halo; cb += (VHb - cb) * halo;

      // vein colour drifts between pale green and warm off-white along the run
      const tt = cl((tint - 0.40) * 2.3 + (mot - 0.5) * 0.5, 0, 1);
      const vr = VPr + (VCr - VPr) * tt;
      const vg = VPg + (VCg - VPg) * tt;
      const vb = VPb + (VCb - VPb) * tt;
      // cores granulate along their length instead of reading as a drawn line
      const core = cl((coreA * (0.66 + 0.34 * gate) + coreB * 0.50 + coreC * 0.30)
                      * (0.86 + 0.24 * mg), 0, 0.96);
      cr += (vr - cr) * core; cg += (vg - cg) * core; cb += (vb - cb) * core;

      const o = i * 3;
      albedo[o] = cr; albedo[o + 1] = cg; albedo[o + 2] = cb;

      /* ---- roughness: honed, and never constant ------------------------- */
      const r = 0.196
        + (hone - 0.5) * 0.155        // broad swirls of duller finish
        + (mot - 0.5) * 0.048
        + (mg - 0.5) * 0.030          // crystal micro-facets
        - core * 0.055                // calcite takes a higher polish
        + halo * 0.026                // the halo is softer stone, duller
        + scr * 0.155;                // scratches scatter
      rough[i] = cl(r, 0.10, 0.32);

      /* ---- height: polished stone is FLAT. Only the scratches and the
         faintest proud vein relief survive the hone. ---------------------- */
      const h = 0.5
        + core * 0.018 + halo * 0.004
        + (mg - 0.5) * 0.018
        + (mot - 0.5) * 0.006
        - scr * 0.050;
      height[i] = cl(h, 0, 1);
    }
  }

  return { albedo, rough, height };
}

/* ========================================================================= */
/*  BOOK CLOTH — bookbinding buckram                                         */
/* ========================================================================= */
/* Plain (tabby) weave, 64 threads per tile each way — one notch coarser and
   a lot more matte than the linen upholstery. The interlace is analytic: the
   warp centreline rides cos(pi*(v - 1/2 + iu)), which with integer iu is a
   proper over-one-under-one checkerboard rather than two crossed stripe
   patterns, and whichever of warp/weft is physically higher at a texel owns
   the colour, crown and sheen there.

   On top of the weave sit the three things that say "book" rather than
   "fabric": a gentle horizontal banding from the cloth being pressed over
   board, two faint raised bands across the spine, and broad rubbed patches
   where the nap has been polished flat by handling. The albedo stays a
   near-neutral mid grey (~0.47) so each book's material.color can tint it;
   all the character lives in roughness and height. */
export function bookCloth(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);

  const NT = 64;                       // even → the interlace phase wraps
  const inv = 1 / size;

  /* ---------------------------------------- broad fields (W², upsampled)
     0 wear   1 tonal drift   2 press banding */
  const W = 48, WC = 3;
  const wf = N.newF(W * W * WC);
  for (let y = 0; y < W; y++) {
    const v = (y + 0.5) / W;
    for (let x = 0; x < W; x++) {
      const u = (x + 0.5) / W;
      const o = (y * W + x) * WC;
      // Wear is stretched 3:1 across the tile: a book is rubbed by being slid
      // in and out and by fingers running down the spine, so it wears in long
      // soft streaks. Isotropic blobs read as damp stains.
      wf[o] = vfbm(u * 2, v * 7, 2, 7, 8101, 4);
      wf[o + 1] = vfbm(u * 2, v * 2, 2, 2, 8209, 3);
      // pressing bands: near-1D, very stretched horizontally
      wf[o + 2] = vfbm(u * 1, v * 6, 1, 6, 8317, 3);
    }
  }
  const wx_ = axis(size, W), wy_ = axis(size, W);

  /* -------------------------------------------------------------- palette */
  const ZM = 0.480;                    // mean height of the weave surface
  const D = 0.390, L = 0.570;          // shadowed / lit thread, neutral grey
  const GAP = 0.190;                   // pinhole through the cloth
  const WEAR = 0.670;                  // rubbed, polished-flat fibre

  const hRaw = N.newF(px);
  const crownA = new Float32Array(px);
  const kindA = new Uint8Array(px);
  const toneA = new Float32Array(px);
  const wearA = new Float32Array(px);
  const microA = new Float32Array(px);
  const pressA = new Float32Array(px);

  for (let y = 0; y < size; y++) {
    const vy = (y + 0.5) * inv;
    const wr0 = wy_.i0[y] * W, wr1 = wy_.i1[y] * W, wfy = wy_.fr[y];

    for (let x = 0; x < size; x++) {
      const ux = (x + 0.5) * inv;
      const i = y * size + x;

      const wfx = wx_.fr[x];
      const a00 = (wr0 + wx_.i0[x]) * WC, a10 = (wr0 + wx_.i1[x]) * WC;
      const a01 = (wr1 + wx_.i0[x]) * WC, a11 = (wr1 + wx_.i1[x]) * WC;
      const p00 = (1 - wfx) * (1 - wfy), p10 = wfx * (1 - wfy);
      const p01 = (1 - wfx) * wfy, p11 = wfx * wfy;
      const wearF = wf[a00] * p00 + wf[a10] * p10 + wf[a01] * p01 + wf[a11] * p11;
      const drift = wf[a00 + 1] * p00 + wf[a10 + 1] * p10 + wf[a01 + 1] * p01 + wf[a11 + 1] * p11;
      const press = wf[a00 + 2] * p00 + wf[a10 + 2] * p10 + wf[a01 + 2] * p01 + wf[a11 + 2] * p11;

      /* ---- the cloth wanders: whole groups of threads drift together.
         Buckram is a tight, sized, machine-woven cloth — the grid has to stay
         legible, so this is a sixth of a thread pitch, just enough to kill the
         perfect lattice. ---- */
      const du = (vnp(ux * 24, vy * 24, 24, 24, 9001) - 0.5) * 0.17;
      const dv = (vnp(ux * 24, vy * 24, 24, 24, 9103) - 0.5) * 0.17;
      const u = ux * NT + du, v = vy * NT + dv;
      const iu = Math.floor(u), iv = Math.floor(v);
      const fu = u - iu, fv = v - iv;

      /* ---- per-thread slub: thickness varies ALONG each thread ---- */
      const slW = vnp(iu * 3, v * 0.5, NT * 3, NT >> 1, 9209);
      const slF = vnp(u * 0.5, iv * 3, NT >> 1, NT * 3, 9311);

      // buckram is dense and sized: threads nearly touch, so the pinholes are
      // small dark dots at the crossing corners rather than open mesh
      const hwW = 0.585 * (0.90 + 0.20 * slW);
      const hwF = 0.585 * (0.90 + 0.20 * slF);

      const tw = Math.abs(fu - 0.5) / hwW;
      const tf = Math.abs(fv - 0.5) / hwF;
      const profW = tw < 1 ? Math.sqrt(1 - tw * tw) : 0;
      const profF = tf < 1 ? Math.sqrt(1 - tf * tf) : 0;

      /* interlace. cos(pi*(v - 1/2 + iu)) factors into (-1)^(iu+iv)*sin(pi*fv),
         so warp and weft share one sign and one cheap curve: the extreme lands
         exactly on the crossing centre and flips on every neighbouring
         crossing — over-one-under-one, in four operations. */
      const sgn = ((iu + iv) & 1) ? -1 : 1;
      const liftW = 0.5 + 0.5 * sgn * sinpi01(fv);
      const liftF = 0.5 - 0.5 * sgn * sinpi01(fu);

      const zW = profW > 0 ? 0.30 + 0.115 * liftW + 0.185 * profW * (0.72 + 0.36 * slW) : -1;
      const zF = profF > 0 ? 0.30 + 0.115 * liftF + 0.185 * profF * (0.72 + 0.36 * slF) : -1;

      let z, crown, kind, tone;
      if (zW < 0 && zF < 0) {
        z = 0.115; crown = 0; kind = 0; tone = 0;
      } else if (zW >= zF) {
        z = zW; crown = profW; kind = 1;
        tone = ihash(iu, 3, 9403) * 0.55 + slW * 0.45;
      } else {
        z = zF; crown = profF; kind = 2;
        tone = ihash(5, iv, 9511) * 0.55 + slF * 0.45;
      }

      /* ---- pressed over board: gentle horizontal banding, plus two broad
         raised bands across the spine. Wide and shallow — a binder's band is
         a swell under the cloth, not a wire; anything narrow enough to have a
         visible edge reads as a scanline. ---- */
      const band = (press - 0.5) * 0.075;
      let ridge = 0;
      for (let k = 0; k < 2; k++) {
        const c = k === 0 ? 0.215 : 0.685;
        const halfW = k === 0 ? 0.052 : 0.042;
        let d = vy - c; d -= Math.round(d);           // wrapped distance
        const t = Math.abs(d) / halfW;
        if (t < 1) {
          const q = 1 - t * t;
          ridge += q * q * (k === 0 ? 0.062 : 0.048);
        }
      }

      /* ---- micro fibre fuzz ---- */
      const m = vnp((ux + vy) * 128, vy * 128, 128, 128, 9601);

      /* Rubbing crushes the weave flat. It pools in broad soft patches and
         rides the crest of each band, where the cloth stands proud and takes
         the friction first — that correlation is what makes it read as
         handling rather than as a stain. */
      const wear = cl(ss(0.44, 0.82, wearF) * 0.88 + ridge * 4.5, 0, 1);

      /* Rubbing flattens the weave ABOUT ITS OWN MEAN. Scaling z toward zero
         instead sinks the whole worn patch, and since wear rides the crest of
         each binder's band the band came out as a trough — lighter, smoother
         and RECESSED, three signals that contradict each other. A swell under
         the cloth has to be a swell. */
      const flat = 1 - wear * 0.42;
      hRaw[i] = cl(ZM + (z - ZM) * flat + band + ridge + (m - 0.5) * 0.030, 0, 1);
      crownA[i] = crown; kindA[i] = kind;
      toneA[i] = cl(tone + (drift - 0.5) * 0.30, 0, 1);
      wearA[i] = wear; microA[i] = m; pressA[i] = press;
    }
  }

  /* soften a touch: buckram is sized, the thread edges are not razor sharp */
  const soft = N.blur(hRaw, size, 1);
  for (let i = 0; i < px; i++) height[i] = hRaw[i] * 0.66 + soft[i] * 0.34;
  const ao = N.aoFromHeight(height, size, 3, 0.85);

  /* ------------------------------------------------------------- shading */
  for (let i = 0; i < px; i++) {
    const k = kindA[i], crown = crownA[i], m = microA[i], wear = wearA[i];
    let g, ro;
    if (k === 0) {
      g = GAP + (D - GAP) * 0.35;
      ro = 0.97;
    } else {
      g = D + (L - D) * toneA[i];
      // the weft dye lot is a hair different from the warp — real cloth never
      // has both directions identical
      if (k === 2) g *= 0.955;
      g *= 0.87 + 0.24 * crown;                 // fibres catch light on the crown
      ro = 0.935 - 0.105 * crown * crown;       // matte, crowns a touch less so
    }
    // rubbed patches: lighter AND markedly less rough — that contrast is what
    // reads as "handled" rather than "dirty"
    g += (WEAR - g) * wear * 0.50;
    ro -= wear * 0.34;

    // where the cloth was pressed hardest onto the board the nap is laid down
    // and takes a faint sheen — the banding has to show in the light response,
    // not only in the relief, or it disappears at two metres
    ro -= (pressA[i] - 0.5) * 0.075;

    ro += (m - 0.5) * 0.085;
    const f = 0.96 + 0.08 * m;
    // a whisper of warmth so the grey is not dead neutral; small enough that
    // material.color still drives the hue
    albedo[i * 3] = g * f * 1.020;
    albedo[i * 3 + 1] = g * f * 1.000;
    albedo[i * 3 + 2] = g * f * 0.962;
    rough[i] = cl(ro, 0.42, 0.99);
  }

  return { albedo, rough, height, ao };
}

/* ========================================================================= */
/*  BRUSHED METAL — aged brass                                               */
/* ========================================================================= */
/* The brush lines are four superimposed noise fields stretched 20:1 to 60:1
   along u, at unrelated frequencies and with unrelated along-length
   modulation, so line spacing, length and depth are all irregular — a single
   layer, however fine, always reads as pinstripes. The whole bundle is then
   displaced by a slow wobble, because a hand-brushed part is never dead
   straight, and its amplitude is modulated by a broad field so some zones are
   deeply scored and others nearly smooth.

   Aged: broad lacquer-rot patina clouds and grime that settles in the groove
   bottoms. Both show up mostly in ROUGHNESS, a little in albedo, and pull the
   metalness down from 0.99 to ~0.74 where the surface has gone non-metallic. */
export function brushedMetal(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const metal = N.newF(px);
  const { hex } = N;

  const [BLr, BLg, BLb] = hex(0xdcbb7c);   // bright polished brass
  const [BDr, BDg, BDb] = hex(0x93763f);   // brass in the groove bottoms
  const [PTr, PTg, PTb] = hex(0x6d6844);   // lacquer-rot patina, olive
  const [GRr, GRg, GRb] = hex(0x4a4335);   // grime

  const inv = 1 / size;

  /* ------------------------------------------- broad fields (W², upsampled)
     0 brush wobble   1 brushing density   2 patina cloud   3 drag-scratch mask
     None of these has any energy above ~8 cycles across the tile, so paying
     for them per texel was pure waste. */
  const W = 48, WC = 4;
  const wf = N.newF(W * W * WC);
  for (let y = 0; y < W; y++) {
    const v = (y + 0.5) / W;
    for (let x = 0; x < W; x++) {
      const u = (x + 0.5) / W;
      const o = (y * W + x) * WC;
      wf[o] = (vnp(u * 3, v * 3, 3, 3, 1103) - 0.5) * 0.014
            + (vnp(u * 11, v * 6, 11, 6, 1201) - 0.5) * 0.005;
      wf[o + 1] = vfbm(u * 2, v * 3, 2, 3, 1301, 3);
      wf[o + 2] = 0.66 * vfbm(u * 3, v * 3, 3, 3, 2003, 4)
                + 0.34 * vfbm(u * 8, v * 8, 8, 8, 2111, 3);
      wf[o + 3] = ss(0.42, 0.70, vnp(u * 6, v * 4, 6, 4, 1907));
    }
  }
  const wx_ = axis(size, W), wy_ = axis(size, W);

  for (let y = 0; y < size; y++) {
    const v = (y + 0.5) * inv;
    const wr0 = wy_.i0[y] * W, wr1 = wy_.i1[y] * W, wfy = wy_.fr[y];
    for (let x = 0; x < size; x++) {
      const u = (x + 0.5) * inv;
      const i = y * size + x;

      const wfx = wx_.fr[x];
      const a00 = (wr0 + wx_.i0[x]) * WC, a10 = (wr0 + wx_.i1[x]) * WC;
      const a01 = (wr1 + wx_.i0[x]) * WC, a11 = (wr1 + wx_.i1[x]) * WC;
      const p00 = (1 - wfx) * (1 - wfy), p10 = wfx * (1 - wfy);
      const p01 = (1 - wfx) * wfy, p11 = wfx * wfy;
      const wob = wf[a00] * p00 + wf[a10] * p10 + wf[a01] * p01 + wf[a11] * p11;
      const dens = wf[a00 + 1] * p00 + wf[a10 + 1] * p10 + wf[a01 + 1] * p01 + wf[a11 + 1] * p11;
      const pat = wf[a00 + 2] * p00 + wf[a10 + 2] * p10 + wf[a01 + 2] * p01 + wf[a11 + 2] * p11;
      const smask = wf[a00 + 3] * p00 + wf[a10 + 3] * p10 + wf[a01 + 3] * p01 + wf[a11 + 3] * p11;

      /* ---- the brush wanders across the part ---- */
      const vv = v + wob;

      /* ---- four stretched layers. Each has its own along-length period, so
         a line that is deep here is shallow 3 cm along. ---- */
      const b1 = vnp(u * 7, vv * 116, 7, 116, 1409);
      const b2 = vnp(u * 13, vv * 73, 13, 73, 1511);
      const b3 = vnp(u * 4, vv * 37, 4, 37, 1613);
      // nothing finer than ~2 texels: a period of 179 across 256 puts the
      // lattice below the sampling grid and the "fine brushing" degenerates
      // into per-texel salt and pepper
      const b4 = vnp(u * 23, vv * 127, 23, 127, 1709);
      let brush = (0.32 + 0.16 * dens) * b1
                + (0.28 - 0.08 * dens) * b2
                + 0.14 * b3
                + (0.20 + 0.10 * dens) * b4;
      brush /= 0.94 + 0.18 * dens;
      // push toward the grooves: metal is abraded away, not piled up
      brush = cl(0.5 + (brush - 0.5) * (1.15 + 0.7 * dens), 0, 1);

      /* ---- a few deeper drag scratches ---- */
      const scr = smask > 0.02
        ? ss(0.74, 0.94, vnp(u * 2, vv * 141, 2, 141, 1811)) * smask : 0;

      /* ---- patina + grime. The cloud edge is broken by one of the brush
         layers, because lacquer lifts ALONG the grain — a patina whose
         outline ignores the brushing looks like camouflage painted on. ---- */
      const patina = ss(0.38, 0.80, pat + (b2 - 0.5) * 0.13);
      const grime = patina * (1 - brush) * 0.9 + (1 - brush) * (1 - brush) * 0.22;

      /* ---- per-texel tooth so the lines are not glassy ---- */
      const fine = vnp(u * 47, vv * 97, 47, 97, 2213);

      /* ---- albedo: metals take their colour from here ---- */
      const t = cl(0.26 + 0.72 * brush + 0.16 * (dens - 0.5) - 0.30 * scr, 0, 1);
      let cr = BDr + (BLr - BDr) * t;
      let cg = BDg + (BLg - BDg) * t;
      let cb = BDb + (BLb - BDb) * t;
      const pm = patina * 0.46;
      cr += (PTr - cr) * pm; cg += (PTg - cg) * pm; cb += (PTb - cb) * pm;
      const gm = cl(grime, 0, 1) * 0.30;
      cr += (GRr - cr) * gm; cg += (GRg - cg) * gm; cb += (GRb - cb) * gm;
      const o = i * 3;
      albedo[o] = cr; albedo[o + 1] = cg; albedo[o + 2] = cb;

      /* ---- roughness: this is where brushed metal lives or dies ---- */
      // the LINE-to-line spread has to out-weigh the patina clouds, or the
      // part reads as a dirty casting rather than a brushed one
      const r = 0.235
        + (0.55 - brush) * 0.40        // groove bottoms are torn, crests polished
        + patina * 0.12
        + grime * 0.09
        + scr * 0.15
        + (fine - 0.5) * 0.045
        - (dens - 0.5) * 0.05;
      rough[i] = cl(r, 0.14, 0.45);

      metal[i] = cl(0.995 - patina * 0.17 - grime * 0.13, 0.70, 1);

      /* Brush marks are microns deep. Give them real geometry and the part
         turns into a screw thread — the grain has to live in the ROUGHNESS
         map, with only just enough normal to catch a grazing highlight. */
      height[i] = cl(0.5 + (brush - 0.5) * 0.055 - scr * 0.045
                     + (fine - 0.5) * 0.018, 0, 1);
    }
  }

  return { albedo, rough, height, metal };
}
