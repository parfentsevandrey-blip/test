/* =========================================================================
   Hand-loomed wool rug — cut pile, natural undyed tones.

   Built by splatting leaning elliptical yarn tips, max-blended so adjacent
   tips meet in a crevice instead of a blur, on four scales:

     * an upper tuft layer  — the yarn ends you actually see;
     * a lower tuft layer at a different pitch and lower amplitude — tufts
       sitting further down in the pile, filling the gaps without flattening
       them (and destroying any trace of the splat lattice);
     * a filament layer — individual fibres inside a tuft, which is where the
       heathered colour speckle lives;
     * a sparse stray-fibre layer — loose filaments lying across the pile.

   Every tuft leans along a direction field that drifts in broad swathes, so
   the pile shows the "brush marks" a walked-on rug has. The same field bakes
   a gentle sheen into the albedo, because pile leaning toward you reads
   lighter than pile leaning away.

   The composited relief is band-limited before it becomes a normal map. The
   tuft-scale slope is the point — it is what raking firelight picks out — but
   the one-texel creases where max-blended domes meet are not, and left in they
   pin the normal near 90° across most of the surface, which is a saturated map
   carrying no shape rather than deep relief.

   Tileable: every splat grid has an integer number of cells per tile and is
   written with wrapped pixel indices; all noise uses integer periods.
   ========================================================================= */

export function woolRug(size, N) {
  const px = size * size;
  const { clamp, hex } = N;

  const albedo = N.newF(px * 3);
  const rough = N.newF(px);
  const height = N.newF(px);

  /* ---------------------------------------------------- palette (undyed) */
  const PAL = [
    hex(0xd6cbb5),          // warm cream
    hex(0xc0b39a),          // oatmeal
    hex(0x9d958a),          // greyish taupe
    hex(0xc0a985),          // pale ochre
  ];
  const DYE_WARM = hex(0xc8ac87);   // hand-dye lot drift, warm side
  const DYE_GREY = hex(0xa19a90);   // hand-dye lot drift, grey side
  const PILE_FLOOR = hex(0x7d7367); // pile floor seen down between the tufts
  const STRAY = hex(0xe2d9c8);      // bleached loose filament

  /* ------------------------------------------- broad fields, low-res grid
     Sampled bilinearly per pixel and per tuft, so the hot loops never call
     fbm.                                                                  */
  const LR = 48;
  const angLR = new Float32Array(LR * LR);   // pile lean angle, radians
  const sheLR = new Float32Array(LR * LR);   // directional sheen, ~-1..1
  const denLR = new Float32Array(LR * LR);   // knotting density / pile height
  const dyeLR = new Float32Array(LR * LR);   // yarn-lot colour drift
  const wearLR = new Float32Array(LR * LR);  // walked-flat / matted patches
  for (let y = 0; y < LR; y++) {
    for (let x = 0; x < LR; x++) {
      const u = x / LR, v = y / LR;
      const a1 = N.fbm(u * 2, v * 2, 2, 701, 3) - 0.5;   // metre-scale swathes
      const a2 = N.fbm(u * 6, v * 6, 6, 733, 2) - 0.5;   // brush marks
      const i = y * LR + x;
      angLR[i] = 0.36 + a1 * 1.55 + a2 * 0.62;
      sheLR[i] = a1 * 2.9 + a2 * 1.35;
      denLR[i] = N.fbm(u * 2, v * 2, 2, 811, 4);
      dyeLR[i] = N.fbm(u * 2, v * 2, 2, 877, 3);
      /* Where the pile has been walked on it lies down: slightly flatter and,
         because the fibres line up, slightly less chalky. Warped along the
         same swathe field that steers the pile, so the matted areas run in
         streaks the way footfall actually wears a rug — an unwarped fbm on a
         tight smoothstep gave hard-edged round "continents" that read as a
         map painted on rather than as wear. */
      const ww = N.fbm(u * 3 + a1 * 1.7, v * 3 + a2 * 1.7, 3, 619, 3);
      wearLR[i] = N.smoothstep(0.30, 0.80, ww);
    }
  }

  /* Faint weft ridging. It varies only across the loom, so it is a 1-D table
     at full resolution rather than a plane — cheaper than the 2-D fields and
     sharper than they could be. Its strength is modulated per pixel by the
     density field so the rows fade in and out instead of striping the rug. */
  const bandY = new Float32Array(size);
  for (let y = 0; y < size; y++) {
    bandY[y] = N.fbm(0.37, (y / size) * 12, 12, 953, 3) - 0.5;
  }

  /** bilinear lookup with wrap; (x,y) in pixels, grid `d` cells per tile */
  function grid(f, d, x, y) {
    const s = d / size;
    const fx = x * s, fy = y * s;
    const gx = Math.floor(fx), gy = Math.floor(fy);
    const tx = fx - gx, ty = fy - gy;
    const x0 = ((gx % d) + d) % d, y0 = ((gy % d) + d) % d;
    const x1 = (x0 + 1) % d, y1 = (y0 + 1) % d;
    const a = f[y0 * d + x0], b = f[y0 * d + x1];
    const c = f[y1 * d + x0], e = f[y1 * d + x1];
    return a + (b - a) * tx + (c - a) * ty + (a - b - c + e) * tx * ty;
  }

  /* micro fuzz: a small random table, bilerped — the fibre-scale surface
     noise. Far cheaper than calling gnoise 262k times. */
  const MD = 256;
  const micLR = new Float32Array(MD * MD);
  for (let i = 0, y = 0; y < MD; y++) {
    for (let x = 0; x < MD; x++, i++) {
      /* integer bit-mix rather than N.hash2 — this table is 65k entries and
         the sine in hash2 is pure cost for a value that only needs to be
         white and repeatable. Deterministic, no Math.random anywhere. */
      let h = Math.imul(x, 374761393) + Math.imul(y, 668265263) + 1301;
      h = Math.imul(h ^ (h >>> 13), 1274126177);
      micLR[i] = ((h ^ (h >>> 16)) >>> 0) / 4294967296;
    }
  }

  /* --------------------------------------------------------- splat buffers
     H* hold max-blended dome height; Cc the colour of whichever tuft won;
     Vf a signed tone shift for the filament that won (cheaper than a second
     RGB buffer, and enough to read as within-tuft heather).                */
  const Hc = new Float32Array(px), Cc = new Float32Array(px * 3);
  const Hf = new Float32Array(px), Vf = new Float32Array(px);
  const Hs = new Float32Array(px);

  /**
   * Splat one grid of leaning elliptical yarn tips.
   *   P        cells per tile (integer → tiles exactly)
   *   len/wid  half-length along the lean / half-width, in pixels
   *   H, C, V  target height / colour / tone-shift buffers
   */
  function splat(o) {
    const { P, seed, H, C, V } = o;
    const sc = size / P;
    for (let cy = 0; cy < P; cy++) {
      for (let cx = 0; cx < P; cx++) {
        /* Seven decorrelated randoms per tuft from one integer bit-mix chain.
           N.hash2 costs a Math.sin apiece and this loop runs ~13k times; the
           sines alone were a tenth of the generator. cx/cy are already the
           wrapped lattice coordinates, so this stays exactly as tileable. */
        let s = Math.imul(cx + 1, 0x27d4eb2d) ^ Math.imul(cy + 1, 0x165667b1)
              ^ Math.imul(seed + 1, 0x9e3779b1);
        s = Math.imul(s ^ (s >>> 15), 0x85ebca6b);
        s = Math.imul(s ^ (s >>> 13), 0xc2b2ae35);
        s ^= s >>> 16;
        const IN = 1 / 16777216;
        s = Math.imul(s ^ (s >>> 16), 0x7feb352d); const r0 = (s >>> 8) * IN;
        s = Math.imul(s ^ (s >>> 15), 0x846ca68b); const r1 = (s >>> 8) * IN;
        s = Math.imul(s ^ (s >>> 16), 0x7feb352d); const r2 = (s >>> 8) * IN;
        if (o.skip && r0 < o.skip) continue;
        s = Math.imul(s ^ (s >>> 15), 0x846ca68b); const r3 = (s >>> 8) * IN;
        s = Math.imul(s ^ (s >>> 16), 0x7feb352d); const r4 = (s >>> 8) * IN;
        s = Math.imul(s ^ (s >>> 15), 0x846ca68b); const r5 = (s >>> 8) * IN;
        s = Math.imul(s ^ (s >>> 16), 0x7feb352d); const r6 = (s >>> 8) * IN;

        const lx = (cx + 0.5 + (r0 - 0.5) * o.jit) * sc;
        const ly = (cy + 0.5 + (r1 - 0.5) * o.jit) * sc;

        const dens = grid(denLR, LR, lx, ly);
        const ang = grid(angLR, LR, lx, ly) + (r2 - 0.5) * o.angJit;
        const ca = Math.cos(ang), sa = Math.sin(ang);

        const bnd = bandY[((Math.round(ly) % size) + size) % size]
                  * (0.45 + 0.9 * dens);
        const a = o.len * (1 + (r3 - 0.5) * o.sizeJit) * (0.88 + 0.26 * dens);
        const b = o.wid * (1 + (r4 - 0.5) * o.sizeJit * 0.7);
        const amp = o.amp * (0.84 + 0.16 * r2) * (0.84 + 0.28 * dens)
                  * (1 + 0.5 * bnd);

        /* heathered yarn: a discrete tone out of the four-colour lot, nudged
           toward its neighbour, then drifted by the hand-dye field */
        let cr = 0, cg = 0, cb = 0;
        if (C) {
          /* Component-wise rather than via mixRGB: this runs once per tuft,
             ~13k times, and the two temporary triples it used to allocate were
             costing more in garbage collection than the whole filament pass. */
          if (o.pale) {
            const t = r5 * 0.5, A = STRAY, B = PAL[0];
            cr = A[0] + (B[0] - A[0]) * t;
            cg = A[1] + (B[1] - A[1]) * t;
            cb = A[2] + (B[2] - A[2]) * t;
          } else {
            /* weighted toward the two light tones — undyed fleece is mostly
               cream and oatmeal, with taupe and ochre as the minority spins */
            const k = r5 < 0.34 ? 0 : r5 < 0.66 ? 1 : r5 < 0.85 ? 3 : 2;
            const A = PAL[k], B = PAL[(k + 1) & 3], t = r6 * 0.45;
            cr = A[0] + (B[0] - A[0]) * t;
            cg = A[1] + (B[1] - A[1]) * t;
            cb = A[2] + (B[2] - A[2]) * t;
            const td = grid(dyeLR, LR, lx, ly) - 0.5;
            const D = td > 0 ? DYE_WARM : DYE_GREY;
            const w = (td < 0 ? -td : td) * 1.4;
            cr += (D[0] - cr) * w; cg += (D[1] - cg) * w; cb += (D[2] - cb) * w;
          }
          const vj = 0.88 + 0.24 * r4;          // per-yarn value speckle
          cr *= vj; cg *= vj; cb *= vj;
        }
        const tone = V ? (r5 - 0.5) * 2 : 0;    // per-filament tone shift

        const pxi = Math.round(lx), pyi = Math.round(ly);
        const ox = lx - pxi, oy = ly - pyi;
        const ia = 1 / (a * a), ib = 1 / (b * b);
        /* exact axis-aligned y-extent of the rotated ellipse */
        const ay = a * sa, by = b * ca;
        const RY = Math.ceil(Math.sqrt(ay * ay + by * by)) + 1;

        /* Solve the ellipse span per scanline instead of scanning its bounding
           box. Substituting the rotation into q < 1 leaves a quadratic in ex,
           so each row costs one sqrt and then touches only texels that are
           actually inside. For the filament layer, whose ellipses are 5:1, the
           box was ~90% misses — this is where the frame budget went. */
        const qa = ca * ca * ia + sa * sa * ib;
        const qc = sa * sa * ia + ca * ca * ib;
        const qb = 2 * ca * sa * (ia - ib);
        const inv2a = 0.5 / qa;
        let yw = (pyi - RY) % size; if (yw < 0) yw += size;
        for (let dy = -RY; dy <= RY; dy++) {
          const row = yw * size;
          if (++yw === size) yw = 0;
          const ey = dy - oy;
          const eyc = ey * sa, eys = ey * ca;
          const B = qb * ey, C2 = qc * ey * ey - 1;
          const disc = B * B - 4 * qa * C2;
          if (disc <= 0) continue;
          const sq = Math.sqrt(disc);
          const dxLo = Math.ceil((-B - sq) * inv2a + ox);
          const dxHi = Math.floor((-B + sq) * inv2a + ox);
          if (dxHi < dxLo) continue;
          let xw = (pxi + dxLo) % size; if (xw < 0) xw += size;
          for (let dx = dxLo; dx <= dxHi; dx++) {
            const i = row + xw;
            if (++xw === size) xw = 0;
            const ex = dx - ox;
            const u = ex * ca + eyc;
            const v = eys - ex * sa;
            const q = u * u * ia + v * v * ib;
            if (q >= 1) continue;
            /* flat-topped cut end whose rim also flattens out — the profile
               and its slope both reach zero at q=1, so tufts blend into the
               pile below them without the hard crease a linear falloff
               leaves. That crease is what made earlier passes shade like
               gravel rather than wool. */
            const t = 1 - q * q;
            const val = amp * t * t;
            if (val > H[i]) {
              H[i] = val;
              if (C) {
                /* shade the tuft along its own profile so a yarn end reads as
                   a rounded tip rather than a flat chip of colour */
                const g = 0.88 + 0.16 * t, b3 = i * 3;
                C[b3] = cr * g; C[b3 + 1] = cg * g; C[b3 + 2] = cb * g;
              } else if (V) V[i] = tone;
            }
          }
        }
      }
    }
  }

  /* Upper tufts, then a lower rank at another pitch filling between them.
     The ellipses are deliberately larger than their cell pitch: cut pile is
     dense, the tips touch, and the dark is a narrow slot between them. When
     the tufts were only just big enough to meet, every gap ran all the way to
     the pile floor and the surface read as gravel rather than wool. */
  splat({ P: 43, seed: 11, len: 11.6, wid: 5.0, amp: 1.00, jit: 1.0,
          angJit: 0.38, sizeJit: 0.5, H: Hc, C: Cc });
  splat({ P: 53, seed: 47, len: 9.4, wid: 4.1, amp: 0.82, jit: 1.05,
          angJit: 0.48, sizeJit: 0.55, H: Hc, C: Cc });
  /* filaments inside the tufts — fine directional streaks, colour speckle */
  splat({ P: 70, seed: 91, len: 7.2, wid: 1.55, amp: 1.0, jit: 1.15,
          angJit: 0.34, sizeJit: 0.55, H: Hf, V: Vf });
  /* stray loose fibres lying across the pile */
  splat({ P: 40, seed: 133, len: 15.0, wid: 1.05, amp: 1.0, jit: 1.2,
          angJit: 0.8, sizeJit: 0.5, skip: 0.9, pale: true, H: Hs, C: null });

  /* ----------------------------------------------------------- composite */
  const ao = N.newF(px);
  const hraw = N.newF(px);

  /* The three broad fields share one set of bilinear weights, walked
     incrementally, rather than three full-resolution upsample passes and
     three more 256k buffers. */
  const ls = LR / size;
  for (let y = 0; y < size; y++) {
   const by = bandY[y];
   const fy = y * ls, gy = Math.floor(fy), ty = fy - gy;
   const rA = (((gy % LR) + LR) % LR) * LR;
   const rB = ((((gy % LR) + LR) % LR + 1) % LR) * LR;
   const mrow = (y >> 1) * MD;
   let gx = 0, tx = 0;
   for (let x = 0; x < size; x++) {
    const gx1 = gx + 1 === LR ? 0 : gx + 1;
    const wB = tx * (1 - ty), wA = (1 - tx) * (1 - ty);
    const wD = tx * ty, wC = (1 - tx) * ty;
    const dens = denLR[rA + gx] * wA + denLR[rA + gx1] * wB
               + denLR[rB + gx] * wC + denLR[rB + gx1] * wD;
    const shev = sheLR[rA + gx] * wA + sheLR[rA + gx1] * wB
               + sheLR[rB + gx] * wC + sheLR[rB + gx1] * wD;
    const wear = wearLR[rA + gx] * wA + wearLR[rA + gx1] * wB
               + wearLR[rB + gx] * wC + wearLR[rB + gx1] * wD;
    tx += ls; while (tx >= 1) { tx -= 1; gx = gx + 1 === LR ? 0 : gx + 1; }

    const i = y * size + x;
    const hc = Hc[i], hf = Hf[i], hs = Hs[i];
    /* fibre grain, read straight out of the table at half resolution — it is a
       ±5% shading dither, so a full bilinear upsample pass and its 256k buffer
       bought nothing an eye could see */
    const micro = micLR[mrow + (x >> 1)] - 0.5;
    const band = by * (0.45 + 0.9 * dens);

    /* Filaments ride on the tuft body; the gaps between tufts stay deep.
       Fibre-scale noise deliberately does NOT go in here — at ~3 mm/texel it
       is below the size of anything that can cast a shadow, and feeding it to
       the normal map only buys single-texel slopes that alias into sparkle at
       room distance. It lives in the albedo and roughness instead. */
    hraw[i] = clamp(0.05 + 0.70 * hc + 0.085 * hf * (0.30 + 0.70 * hc)
                    + 0.045 * hs + 0.11 * dens + 0.035 * band
                    - 0.06 * wear * hc,
                    0, 1);

    /* the yarn tone that won this texel, or the pile floor if none did */
    let cr, cg, cb;
    if (hc > 0) { const b = i * 3; cr = Cc[b]; cg = Cc[b + 1]; cb = Cc[b + 2]; }
    else { cr = PILE_FLOOR[0]; cg = PILE_FLOOR[1]; cb = PILE_FLOOR[2]; }

    /* within-tuft heather: each filament is a slightly different spin */
    const s = Vf[i];
    if (s !== 0) {
      const w = (s < 0 ? -s : s) * 0.30, d = s < 0 ? DYE_GREY : DYE_WARM;
      const g = 1 + 0.18 * s;
      cr = (cr + (d[0] - cr) * w) * g;
      cg = (cg + (d[1] - cg) * w) * g;
      cb = (cb + (d[2] - cb) * w) * g;
    }

    /* pale stray fibres lie over everything */
    if (hs > 0.02) {
      const w = hs * 0.6;
      cr += (STRAY[0] - cr) * w; cg += (STRAY[1] - cg) * w; cb += (STRAY[2] - cb) * w;
    }

    /* light does not reach the bottom of the pile (smoothstep, inlined) */
    let g0 = (hc - 0.02) * 1.7241; g0 = g0 < 0 ? 0 : g0 > 1 ? 1 : g0;
    const gap = g0 * g0 * (3 - 2 * g0);
    /* the deep slots grey out toward the pile floor continuously, instead of
       snapping to it only on the texels no tuft reached */
    const fw = (1 - gap) * 0.45;
    cr += (PILE_FLOOR[0] - cr) * fw;
    cg += (PILE_FLOOR[1] - cg) * fw;
    cb += (PILE_FLOOR[2] - cb) * fw;
    const shade = 0.69 + 0.31 * gap;
    /* directional sheen — the brush-mark bands */
    let g1 = (shev + 0.55) * 0.9091; g1 = g1 < 0 ? 0 : g1 > 1 ? 1 : g1;
    const sheen = 0.90 + 0.20 * (g1 * g1 * (3 - 2 * g1));
    const k = shade * sheen * (1 + 0.10 * micro) * (0.88 + 0.22 * dens)
            * (1 + 0.13 * band);

    const j = i * 3;
    let ar = cr * k, ag = cg * k, ab = cb * k;
    albedo[j] = ar < 0.035 ? 0.035 : ar > 0.85 ? 0.85 : ar;
    albedo[j + 1] = ag < 0.035 ? 0.035 : ag > 0.85 ? 0.85 : ag;
    albedo[j + 2] = ab < 0.035 ? 0.035 : ab > 0.85 ? 0.85 : ab;

    /* Wool is chalky everywhere, but not uniformly — a constant roughness is
       the loudest fake tell there is. Three terms, at three scales: matted
       walked-on patches lie down and gain a faint sheen (metres), exposed tuft
       tips catch a little more light than the shaded slots (centimetres), and
       stray fibres and fibre grain break it up (millimetres). */
    let g2 = (hc - 0.14) * 1.55; g2 = g2 < 0 ? 0 : g2 > 1 ? 1 : g2;
    const tip = g2 * g2 * (3 - 2 * g2);
    const rv = 1.0 - 0.062 * wear - 0.055 * tip * (0.5 + 0.9 * wear)
             - 0.024 * hs - 0.020 * micro - 0.012 * band;
    rough[i] = rv < 0.88 ? 0.88 : rv > 1 ? 1 : rv;

    /* occlusion lives exactly where the pile does not: between the tufts */
    const av = 0.45 + 0.55 * gap * (0.92 + 0.08 * (hf < 1 ? hf : 1));
    ao[i] = av > 1 ? 1 : av;
   }
  }

  /* Band-limit the relief before it becomes a normal map. The max-blend of
     overlapping domes leaves a one-texel crease wherever two tufts meet; left
     alone those creases pin the normal to near-90° over most of the surface,
     which is not "deep relief", it is a saturated map carrying no shape at
     all. A 3×3 box softens exactly those creases and leaves the tuft-scale
     slope — the thing the raking firelight is supposed to pick out — intact.
     The remap then keeps the pile deep without driving the slope back up.
     Hand-rolled separable box, reusing the filament buffer as scratch —
     N.blur is general and allocates two more 256k arrays per call. */
  const tmp = Hf, th = 1 / 3;
  for (let y = 0; y < size; y++) {
    const o = y * size;
    let prev = hraw[o + size - 1], cur = hraw[o], next;
    for (let x = 0; x < size; x++) {
      next = hraw[o + (x + 1 === size ? 0 : x + 1)];
      tmp[o + x] = (prev + cur + next) * th;
      prev = cur; cur = next;
    }
  }
  for (let y = 0; y < size; y++) {
    const o = y * size;
    const a = (y === 0 ? size - 1 : y - 1) * size;
    const b = (y + 1 === size ? 0 : y + 1) * size;
    for (let x = 0; x < size; x++) {
      height[o + x] = 0.14 + 0.72 * ((tmp[a + x] + tmp[o + x] + tmp[b + x]) * th);
    }
  }

  return { albedo, rough, height, ao };
}
