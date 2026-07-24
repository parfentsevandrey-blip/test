/* =========================================================================
   Wide-plank oiled European oak floor.

   One tile covers ~1.43 m x 2.0 m in the scene (repeat [7,4] on a 10x8 m
   floor), so at 1024 px a texel is ~1.4 mm across and ~2.0 mm along the
   plank. Planks run along V (vertical in texture space); eight of them fit
   across a tile at ~0.18 m each.

   Structure, largest to smallest:
     - metre scale : oil blotching, tonal drift, broad wear patches
     - plank scale : per-board tone / ring pitch / grain tilt / sheen,
                     staggered butt joints, eased edge grooves
     - cm scale    : growth rings from a wandering "pith" distance, which
                     produces straight rift/quarter figure when the pith is
                     far away and cathedral arcs where it swings close
     - mm scale    : ring-porous open pores, medullary ray flecks,
                     sanding micro-streaks

   Everything is seeded; the only per-pixel randomness is an integer hash.
   ========================================================================= */

const SEED = 1701;

/* integer hash -> 0..1, used for per-texel micro detail (no Math.sin cost) */
function ihash(x, y, s) {
  let h = Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(s | 0, 1274126177);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

/* low-resolution tileable fbm field, bilinearly upsampled at lookup time */
function lowField(N, S, freq, oct, seed, gain) {
  const f = N.newF(S * S);
  const k = freq / S;
  for (let j = 0; j < S; j++) {
    for (let i = 0; i < S; i++) f[j * S + i] = N.fbm(i * k, j * k, freq, seed, oct, gain === undefined ? 0.5 : gain);
  }
  return f;
}

/* precomputed bilinear taps for sampling an S^2 field over a size^2 image */
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

/* smoothstep-interpolated 1-D random walk, control points every `cp` samples */
function smooth1D(N, n, cp, seed) {
  const nc = Math.ceil(n / cp) + 2;
  const ctl = new Float32Array(nc);
  for (let i = 0; i < nc; i++) ctl[i] = N.hash2(i * 3.77 + 1.3, seed * 1.91 + 0.7, seed + 17);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / cp, k = t | 0, f = t - k;
    const s = f * f * (3 - 2 * f);
    out[i] = ctl[k] + (ctl[k + 1] - ctl[k]) * s;
  }
  return out;
}

const sat = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);

/* wrapping box blur with running sums — O(n) in the radius */
function boxBlur(a, size, r, N) {
  const n = r * 2 + 1, tmp = N.newF(a.length), out = N.newF(a.length);
  for (let y = 0; y < size; y++) {
    const o = y * size;
    let s = 0;
    for (let k = -r; k <= r; k++) s += a[o + ((k + size) % size)];
    tmp[o] = s / n;
    for (let x = 1; x < size; x++) {
      s += a[o + ((x + r) % size)] - a[o + ((x - r - 1 + size) % size)];
      tmp[o + x] = s / n;
    }
  }
  for (let x = 0; x < size; x++) {
    let s = 0;
    for (let k = -r; k <= r; k++) s += tmp[((k + size) % size) * size + x];
    out[x] = s / n;
    for (let y = 1; y < size; y++) {
      s += tmp[((y + r) % size) * size + x] - tmp[((y - r - 1 + size) % size) * size + x];
      out[y * size + x] = s / n;
    }
  }
  return out;
}

export function oakFloor(size, N) {
  const S = size, px = S * S, sc = S / 1024;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px), ao = N.newF(px);
  const phaseT = N.newF(px);          // ring phase fraction, reused by the pore pass
  const radF = N.newF(px);            // radial ring coordinate — gives the pore pass a grain tangent
  const poreF = N.newF(px);           // 0..1 pore mask
  const fleckF = N.newF(px);          // 0..1 medullary ray mask
  const grooveF = N.newF(px);         // 0..1 joint / edge groove profile

  /* ---------------------------------------------------------- palette */
  const C_HONEY = N.hex(0xb2814b);    // warm honey oak, oil-darkened
  const C_GREY = N.hex(0x8a7154);     // greyish-brown oak
  const C_LATE = N.hex(0x4e3420);     // dark latewood band
  const C_PORE = N.hex(0x462f1f);     // open pore
  const C_WEAR = N.hex(0xc4a781);     // sun-bleached / worn high spots
  const C_RAY = N.hex(0xd3b78e);      // medullary ray fleck

  /* --------------------------------------------------- broad-scale fields */
  const FS = Math.max(64, Math.round(128 * sc));
  const WS = Math.max(96, Math.round(192 * sc));
  const drift = lowField(N, FS, 3, 4, SEED + 3);        // metre-scale tone
  const oilB = lowField(N, FS, 6, 4, SEED + 21);        // oil soak blotches
  const warpA = lowField(N, FS, 6, 4, SEED + 61);       // ring wander, coarse
  const warpB = lowField(N, WS, 24, 3, SEED + 77);      // ring wander, fine
  const smF = sampler(FS, S), smW = sampler(WS, S);

  /* tonal streaks that run WITH the grain: an isotropic field smeared along V */
  const streak = (() => {
    const f = lowField(N, WS, 20, 3, SEED + 91);
    const out = N.newF(WS * WS);
    const R = Math.max(2, Math.round(WS / 26));
    const n = R * 2 + 1;
    for (let x = 0; x < WS; x++) {
      let s = 0;
      for (let k = -R; k <= R; k++) s += f[(((k + WS) % WS) * WS) + x];
      out[x] = s / n;
      for (let y = 1; y < WS; y++) {
        s += f[(((y + R) % WS) * WS) + x] - f[(((y - R - 1 + WS) % WS) * WS) + x];
        out[y * WS + x] = s / n;
      }
    }
    return out;
  })();

  /* micro streaks along the grain: white noise smeared vertically */
  const micro = N.newF(px);
  {
    const R = Math.max(1, Math.round(2 * sc));
    const n = R * 2 + 1, inv = 1 / n;
    const raw = N.newF(px);
    for (let y = 0; y < S; y++) {
      const o = y * S;
      for (let x = 0; x < S; x++) raw[o + x] = ihash(x, y, SEED + 5);
    }
    /* running column sums, advanced one row at a time — the streak blur is
       along V, so this stays row-major and touches each texel once */
    const cs = N.newF(S);
    for (let k = -R; k <= R; k++) {
      const o = ((k + S) % S) * S;
      for (let x = 0; x < S; x++) cs[x] += raw[o + x];
    }
    for (let y = 0; y < S; y++) {
      const o = y * S;
      for (let x = 0; x < S; x++) micro[o + x] = cs[x] * inv;
      const oa = ((y + R + 1) % S) * S, os = ((y - R + S) % S) * S;
      for (let x = 0; x < S; x++) cs[x] += raw[oa + x] - raw[os + x];
    }
  }

  /* ------------------------------------------------------- plank columns */
  const NP = Math.max(4, Math.round(8 * sc));
  const colStart = new Int32Array(NP + 1), colW = new Int32Array(NP);
  {
    const raw = new Float64Array(NP);
    let tot = 0;
    for (let i = 0; i < NP; i++) { raw[i] = 1 + (N.hash2(i * 5.31, 11.1, SEED) - 0.5) * 0.34; tot += raw[i]; }
    let acc = 0;
    for (let i = 0; i < NP; i++) { acc += raw[i] / tot * S; colStart[i + 1] = Math.min(S, Math.round(acc)); }
    colStart[0] = 0; colStart[NP] = S;
    for (let i = 0; i < NP; i++) colW[i] = colStart[i + 1] - colStart[i];
  }
  const colOf = new Int32Array(S);
  for (let c = 0; c < NP; c++) for (let x = colStart[c]; x < colStart[c + 1]; x++) colOf[x] = c;

  /* ------------------------------------- butt joints + per-board parameters */
  const segOf = [], pvOf = [], boards = [], jointsOf = [];
  for (let c = 0; c < NP; c++) {
    /* one or two butt joints per column, at jittered heights */
    const js = [];
    if (N.hash2(c * 7.13, 3.31, SEED + 13) < 0.40) {
      js.push(Math.round(N.hash2(c * 2.71, 9.41, SEED + 31) * S));
    } else {
      const a = 0.05 + N.hash2(c * 2.71, 9.41, SEED + 31) * 0.34;
      const b = a + 0.36 + N.hash2(c * 4.17, 1.77, SEED + 47) * 0.28;
      js.push(Math.round(a * S), Math.round(b * S));
    }
    js.sort((p, q) => p - q);
    jointsOf.push(js);
    const nj = js.length;

    const seg = new Int32Array(S), pv = new Int32Array(S);
    for (let k = 0; k < nj; k++) {
      const y0 = js[k], y1 = k + 1 < nj ? js[k + 1] : S;
      for (let y = y0; y < y1; y++) { seg[y] = k; pv[y] = y - y0; }
    }
    const last = nj - 1, st = js[last] - S;      // the board that wraps the tile
    for (let y = 0; y < js[0]; y++) { seg[y] = last; pv[y] = y - st; }
    segOf.push(seg); pvOf.push(pv);

    const bs = [];
    for (let k = 0; k < nj; k++) {
      const h = (n) => N.hash2(c * 13.77 + k * 5.93, n, SEED + 101);
      /* Rings are cylinders around the pith. How the cut meets them decides
         the figure: a pith far off to the SIDE gives the straight parallel
         lines of quarter/rift sawn stock; a pith close to (or under) the
         board gives flat-sawn cathedrals. The pith also drifts away from the
         face along the plank — the log's taper — and that drift is what
         stretches a cathedral into a long pointed arc. */
      const quarter = h(2.3) < 0.50;              // quartered vs flat sawn
      const tone = h(1.1);
      const W = colW[c];
      const side = h(1.9) < 0.5 ? -1 : 1;
      const pu0 = quarter
        ? side * (1.7 + 6.5 * h(4.4) * h(4.4)) * W          // pith far to one side
        : (h(4.4) * 1.7 - 0.35) * W;                        // pith at/under the board
      const spacing = ((quarter ? 7 : 8.5) + (quarter ? 6 : 10) * h(6.6)) * sc;
      const tilt = (h(5.5) - 0.5) * 0.075;
      const dFloor = (quarter ? 6 + 30 * h(3.7) : 1.5 + 9 * h(3.7)) * sc;
      const kUp = (0.035 + 0.20 * h(7.1)) * (quarter ? 0.4 : 1);
      const kDn = (0.035 + 0.20 * h(7.9)) * (quarter ? 0.4 : 1);
      const vTip = (h(8.5) * 2.2 - 0.6) * S;
      const soft = 22 * sc;
      const L = S + 4;
      const wu = smooth1D(N, L, Math.max(8, Math.round(120 * sc)), c * 31 + k * 7 + 5);
      const wd = smooth1D(N, L, Math.max(8, Math.round(260 * sc)), c * 17 + k * 11 + 91);
      const pithU = new Float32Array(L), pithD = new Float32Array(L);
      for (let i = 0; i < L; i++) {
        pithU[i] = pu0 + tilt * i + (wu[i] - 0.5) * 20 * sc;
        const dd = (i - vTip) * (i > vTip ? kUp : kDn);
        pithD[i] = (dFloor + Math.sqrt(dd * dd + soft * soft) - soft) * (0.86 + 0.28 * wd[i]);
      }
      /* irregular ring spacing: a slow remap of the radial coordinate so the
         rings crowd and spread the way real growth does */
      const RJ = new Float32Array(2400);
      {
        const t2 = smooth1D(N, 2400, Math.max(6, Math.round(spacing * 3.5)), c * 53 + k * 29 + 7);
        for (let i = 0; i < 2400; i++) RJ[i] = (t2[i] - 0.5) * spacing * 0.55;
      }
      const col = N.mixRGB(C_HONEY, C_GREY, tone * tone * 0.85);
      const val = 0.78 + 0.40 * h(7.7);
      const hue = (h(6.1) - 0.5) * 0.05;          // some boards pinker, some greener
      bs.push({
        quarter,
        rays: quarter ? h(2.9) < 0.80 : h(2.9) < 0.18,
        /* how open-pored this board is — sapwood-side stock runs coarser */
        poro: 0.62 + 1.05 * h(2.1) * h(2.1),
        pithU, pithD, RJ,
        cup: (0.35 + 0.65 * h(3.3)) * (h(4.9) < 0.75 ? 1 : -0.4),
        lift: (h(8.1) - 0.5) * 0.022,
        invSp: 1 / spacing,
        sp: spacing,
        warp: quarter ? 0.45 : 1,
        invW: 1 / colW[c],
        contrast: 0.56 + 0.46 * h(8.8) - (quarter ? 0.04 : 0),
        salt: (c * 977 + k * 613 + 31) | 0,
        r: col[0] * val * (1 + hue), g: col[1] * val, b: col[2] * val * (1 - hue * 1.4),
        sheen: (h(9.9) - 0.5) * 0.09,
        tilt,
        y0: k === nj - 1 ? js[last] - S : js[k],
        len: k + 1 < nj ? js[k + 1] - js[k] : S - js[last] + js[0],
        c, k,
      });
    }
    boards.push(bs);
  }

  /* per-ring jitter tables (width, darkness, overall tone) */
  const TB = 1024;
  const RW = N.newF(TB), RD = N.newF(TB), RT = N.newF(TB);
  for (let i = 0; i < TB; i++) {
    RW[i] = N.hash2(i * 1.7, 4.2, SEED + 201);
    RD[i] = N.hash2(i * 2.3, 8.1, SEED + 211);
    RT[i] = N.hash2(i * 3.1, 1.9, SEED + 221);
  }

  /* =================================================== main per-pixel pass */
  const warpAmpA = 9 * sc, warpAmpB = 1.7 * sc;
  for (let y = 0; y < S; y++) {
    const fr0 = smF.i0[y] * FS, fr1 = smF.i1[y] * FS, ffy = smF.fr[y];
    const wr0 = smW.i0[y] * WS, wr1 = smW.i1[y] * WS, wfy = smW.fr[y];
    for (let c = 0; c < NP; c++) {
      const b = boards[c][segOf[c][y]];
      const v = pvOf[c][y];
      const pU = b.pithU[v], pD = b.pithD[v], pD2 = pD * pD, RJ = b.RJ;
      const ampA = warpAmpA * b.warp, ampB = warpAmpB * b.warp;
      const x0 = colStart[c], x1 = colStart[c + 1];
      for (let x = x0; x < x1; x++) {
        const i = y * S + x;
        const fc0 = smF.i0[x], fc1 = smF.i1[x], ffx = smF.fr[x];
        const wc0 = smW.i0[x], wc1 = smW.i1[x], wfx = smW.fr[x];

        const wa = bl(warpA, fr0, fr1, ffy, fc0, fc1, ffx) - 0.5;
        const wb = bl(warpB, wr0, wr1, wfy, wc0, wc1, wfx) - 0.5;
        const st = bl(streak, wr0, wr1, wfy, wc0, wc1, wfx) - 0.5;
        const dr = bl(drift, fr0, fr1, ffy, fc0, fc1, ffx);
        const ob = bl(oilB, fr0, fr1, ffy, fc0, fc1, ffx);
        const mi = micro[i];

        /* ------------------------------------------------- growth rings */
        const du = x - x0 - pU;
        let R = Math.sqrt(du * du + pD2) + wa * ampA + wb * ampB;
        R += RJ[R > 0 ? (R < 2399 ? R | 0 : 2399) : 0];
        radF[i] = R;
        const phase = R * b.invSp;
        const ri = Math.floor(phase);
        const t = phase - ri;
        phaseT[i] = t;
        const kk = (Math.imul(ri, 2654435761) ^ b.salt) & (TB - 1);
        /* Latewood is a thin dark band, but never a hairline: a sub-texel
           line reads as inked vector art and aliases badly at floor
           distance, so the band gets a soft shoulder instead of a plateau. */
        const lw = 0.15 + 0.22 * RW[kk];
        let late = t > 1 - lw ? (t - (1 - lw)) / (lw * 0.66) : 0;
        if (late > 1) late = 1;
        late = late * late * (3 - 2 * late);
        /* wood darkens gradually into the band, then cuts sharply back to
           the pale earlywood of the next ring. The broad ramp matters as much
           as the crisp line: without it the rings read as inked contours
           rather than as bands of denser late-season wood. */
        const ramp = sat((t - (1 - 3.0 * lw)) / (2.2 * lw));
        late += 0.26 * ramp * ramp;
        /* a faint secondary line inside the earlywood on some rings */
        const sec = RT[kk] > 0.55 ? sat(1 - Math.abs(t - 0.52) * 13) * 0.22 : 0;
        /* where the cut runs nearly along a ring (the flat of a cathedral)
           the figure smears out and pales, exactly as it does in real stock */
        const gsl = 0.54 + 0.46 * sat(Math.abs(du) / R * 6);
        /* strength wanders along the line so it never reads as printed-on */
        const gmod = (0.62 + 0.76 * (wb + 0.5)) * gsl;
        const grain = sat((late * (0.25 + 0.75 * RD[kk]) + sec) * b.contrast * gmod);
        const ringTone = 0.975 + 0.05 * RT[kk];    // ring-to-ring tone drift

        /* ------------------------------------------------------- albedo */
        const gm = grain * 0.97;
        let r = b.r + (C_LATE[0] - b.r) * gm;
        let g = b.g + (C_LATE[1] - b.g) * gm;
        let bb = b.b + (C_LATE[2] - b.b) * gm;

        /* the oil has worn thin where traffic passes — lighter and duller */
        const wear = sat((ob - 0.62) * 3.4) * (0.4 + 1.2 * dr);
        r += (C_WEAR[0] - r) * wear * 0.30;
        g += (C_WEAR[1] - g) * wear * 0.30;
        bb += (C_WEAR[2] - bb) * wear * 0.30;

        /* oil soaks unevenly: darker + a touch warmer where it pooled */
        const oil = ob - 0.5;
        /* mm-scale vessel speckle: the short dark flecks between the pore
           bands that keep the flat field from reading as painted */
        const spk = mi < 0.28 ? (0.28 - mi) * 0.42 : 0;
        const shade = ringTone * (0.83 + 0.34 * dr) * (1 - oil * 0.22)
          * (1 + st * 0.15) * (1 + wb * 0.09) * (0.985 + 0.03 * mi) * (1 - spk);
        r *= shade * (1 - oil * 0.02);
        g *= shade;
        bb *= shade * (1 + oil * 0.03);

        albedo[i * 3] = r; albedo[i * 3 + 1] = g; albedo[i * 3 + 2] = bb;

        /* ------------------------------------------------------- height */
        /* soft earlywood dishes slightly below the denser latewood, and each
           board sits at its own height with a touch of cup across its width */
        const uu = (x - x0) * b.invW * 2 - 1;
        height[i] = 0.60 + b.lift + b.cup * (1 - uu * uu) * 0.026
          + grain * 0.018 + (dr - 0.5) * 0.02 + (mi - 0.5) * 0.012 - spk * 0.05;

        /* ---------------------------------------------------- roughness */
        let rr = 0.455 + b.sheen + (ob - 0.5) * 0.22 + (dr - 0.5) * 0.06;
        rr -= grain * 0.055;                       // dense latewood takes a shine
        rr += wear * 0.16;                         // worn patches lose the oil
        rr += spk * 0.55 + (ihash(x, y, SEED + 9) - 0.5) * 0.045;
        rough[i] = rr;

        ao[i] = 1;
      }
    }
  }

  /* ========================================================== open pores */
  {
    const cand = Math.round(72000 * sc * sc);
    for (let p = 0; p < cand; p++) {
      const rx = ihash(p, 7, SEED + 301);
      const ry = ihash(p, 29, SEED + 307);
      const r3 = ihash(p, 53, SEED + 311);
      const cx = rx * S, cy = ry * S;
      const ix = cx | 0, iy = cy | 0;
      const i0 = iy * S + ix;
      const t = phaseT[i0];
      const b = boards[colOf[ix]][segOf[colOf[ix]][iy]];
      /* Ring-porous: the coarse earlywood vessels crowd into a narrow band
         immediately past each ring line and thin out fast after it. Letting
         them scatter evenly over the board is what makes procedural oak read
         as "peppered with ticks" instead of as oak. */
      const inBand = t < 0.20;
      /* Pore density also varies ring to ring — a wet season leaves a wide
         open band, a dry one barely any. Without this every grain line gets
         an identical dotted edge and the board reads as line art. */
      const ring = Math.floor(radF[i0] * b.invSp);
      const rp = 0.28 + 1.45 * ihash(ring, b.salt, SEED + 331);
      const openBand = (inBand ? 1 : t < 0.50 ? 0.10 : 0.02) * b.poro * rp;
      if (r3 > openBand) continue;
      const r4 = ihash(p, 101, SEED + 317);
      const r5 = ihash(p, 173, SEED + 323);
      /* dashes scale with the ring pitch so they read as the pore band that
         hugs each grain line rather than as random speckle */
      let len = (0.30 + 0.85 * r4 * r4) * b.sp * (inBand ? 1 : 0.45);
      if (len > 11 * sc) len = 11 * sc;
      const wid = (0.20 + 0.34 * r5) * sc;
      /* most vessels are barely-there; a few are properly open. A skewed
         amplitude keeps the band from looking rubber-stamped. */
      const amp = (0.14 + 0.86 * r5 * r5) * (inBand ? 1 : 0.6);
      /* Run the dash along the local growth ring rather than straight down
         the tile: on a cathedral arc the pore band curves with the figure. */
      let dxd = b.tilt + (r4 - 0.5) * 0.10, dyd = 1;
      {
        const gx = radF[i0 + (ix + 1 < S ? 1 : 1 - S)] - radF[i0 + (ix > 0 ? -1 : S - 1)];
        const gy = radF[(iy + 1 < S ? i0 + S : ix)] - radF[iy > 0 ? i0 - S : (S - 1) * S + ix];
        if (Math.abs(gx) < 2.6 && Math.abs(gy) < 2.6) {
          let tx = -gy, ty = gx;
          if (ty < 0) { tx = -tx; ty = -ty; }
          const m = Math.hypot(tx, ty);
          if (m > 0.12) {
            const j = (r4 - 0.5) * 0.14;
            const c_ = Math.cos(j), s_ = Math.sin(j);
            const ux = tx / m, uy = ty / m;
            dxd = ux * c_ - uy * s_; dyd = ux * s_ + uy * c_;
          }
        }
      }
      const steps = Math.max(2, Math.ceil(len * 1.3));
      const wr = Math.max(1, Math.ceil(wid + 0.6));
      for (let s = 0; s <= steps; s++) {
        const tt = s / steps;
        const e = tt * 2 - 1;
        const fade = 1 - e * e * e * e;
        const px_ = cx + dxd * (tt - 0.5) * len, py_ = cy + dyd * (tt - 0.5) * len;
        const bx = Math.round(px_), by = Math.round(py_);
        for (let oy = -wr; oy <= wr; oy++) {
          for (let ox = -wr; ox <= wr; ox++) {
            const ddx = bx + ox - px_, ddy = by + oy - py_;
            const d = Math.sqrt(ddx * ddx + ddy * ddy);
            const w = amp * fade * sat((wid + 0.40 - d) / 0.90);
            if (w <= 0) continue;
            const j = ((by + oy + S) % S) * S + ((bx + ox + S) % S);
            if (w > poreF[j]) poreF[j] = w;
          }
        }
      }
    }
  }

  /* ================================================== medullary ray flecks */
  for (let c = 0; c < NP; c++) {
    for (const b of boards[c]) {
      if (!b.rays) continue;
      const n = Math.round((45 + 130 * N.hash2(b.c * 3.7, b.k * 9.1, SEED + 401)) * sc * sc * (b.len / S) * (colW[c] / (S / NP)));
      for (let p = 0; p < n; p++) {
        const h = (q) => N.hash2(p * 1.37 + q, b.c * 6.1 + b.k * 2.3, SEED + 409 + q * 13);
        const cx = colStart[c] + h(0.5) * colW[c];
        const cy = b.y0 + h(1.5) * b.len;
        const len = (6 + 34 * h(2.5) * h(2.5)) * sc;
        const wid = (0.35 + 1.0 * h(3.5) * h(3.5)) * sc;
        const ang = (h(4.5) - 0.5) * 0.95;         // roughly across the grain
        const amp = (0.18 + 0.62 * h(5.5)) * (h(6.5) < 0.3 ? 1 : 0.6);
        const dxd = Math.cos(ang), dyd = Math.sin(ang) - b.tilt;
        const steps = Math.max(3, Math.ceil(len));
        const wr = Math.max(1, Math.ceil(wid + 1));
        for (let s = 0; s <= steps; s++) {
          const tt = s / steps;
          const e = 1 - Math.abs(tt * 2 - 1);
          const fade = e * e * (3 - 2 * e);
          const px_ = cx + dxd * (tt - 0.5) * len, py_ = cy + dyd * (tt - 0.5) * len;
          const bx = Math.round(px_), by = Math.round(py_);
          for (let oy = -wr; oy <= wr; oy++) {
            for (let ox = -wr; ox <= wr; ox++) {
              const ddx = bx + ox - px_, ddy = by + oy - py_;
              const d = Math.sqrt(ddx * ddx + ddy * ddy);
              const w = amp * fade * sat((wid + 0.5 - d) / 1.1);
              if (w <= 0) continue;
              const j = ((by + oy + S) % S) * S + ((bx + ox + S) % S);
              if (w > fleckF[j]) fleckF[j] = w;
            }
          }
        }
      }
    }
  }

  /* ============================================ eased edges + butt joints */
  {
    const wobX = smooth1D(N, S, Math.max(6, Math.round(60 * sc)), 555);
    const wobY = smooth1D(N, S, Math.max(6, Math.round(60 * sc)), 777);
    const halfW = 3.4 * sc;
    /* a real eased edge is a shallow chamfer, not a knife cut: the inner
       ramp spans ~2 texels so the normal map keeps a gradient instead of
       saturating into a hard line that aliases at floor distance */
    const prof = (d) => sat(N.smoothstep(halfW, 1.1 * sc, d) * 0.52 + N.smoothstep(2.1 * sc, 0.15 * sc, d) * 0.52);
    const rad = Math.ceil(halfW) + 1;

    /* plank edges: full-height lines at every column boundary */
    for (let c = 0; c < NP; c++) {
      const xb = colStart[c];
      for (let y = 0; y < S; y++) {
        const cxp = xb + (wobX[y] - 0.5) * 1.4 * sc;
        for (let o = -rad; o <= rad; o++) {
          const xx = Math.round(cxp) + o;
          const w = prof(Math.abs(xx - cxp));
          if (w <= 0) continue;
          const j = y * S + ((xx + S) % S);
          if (w > grooveF[j]) grooveF[j] = w;
        }
      }
    }
    /* butt joints: only across their own plank */
    for (let c = 0; c < NP; c++) {
      for (const jy of jointsOf[c]) {
        for (let x = colStart[c]; x < colStart[c + 1]; x++) {
          const cyp = jy + (wobY[x] - 0.5) * 1.4 * sc;
          for (let o = -rad; o <= rad; o++) {
            const yy = Math.round(cyp) + o;
            const w = prof(Math.abs(yy - cyp)) * 0.92;
            if (w <= 0) continue;
            const j = ((yy + S) % S) * S + x;
            if (w > grooveF[j]) grooveF[j] = w;
          }
        }
      }
    }
  }

  /* ==================================================== composite the masks */
  const gSoft = boxBlur(grooveF, S, Math.max(1, Math.round(2.5 * sc)), N);
  for (let i = 0; i < px; i++) {
    const po = poreF[i], fl = fleckF[i], gv = grooveF[i], gs = gSoft[i];

    let r = albedo[i * 3], g = albedo[i * 3 + 1], b = albedo[i * 3 + 2];

    /* ray flecks: pale, silky, slightly proud of the sanded field */
    if (fl > 0) {
      /* silver grain: the ray tissue is paler than its board and takes a
         different sheen, which is what makes it flash under raking light */
      const f = fl * 0.30, k = 1 + fl * 0.26;
      r = (r + (C_RAY[0] - r) * f) * k;
      g = (g + (C_RAY[1] - g) * f) * k;
      b = (b + (C_RAY[2] - b) * f) * k;
      rough[i] -= fl * 0.15;
      height[i] += fl * 0.006;
    }
    /* open pores: darker, deeper, rougher than the sanded surface */
    if (po > 0) {
      const f = po * 0.42;
      r += (C_PORE[0] - r) * f; g += (C_PORE[1] - g) * f; b += (C_PORE[2] - b) * f;
      rough[i] += po * 0.15;
      height[i] -= po * 0.030;
    }
    /* grooves: a real chamfer dip with dirt/oil shadow in the bottom */
    if (gv > 0 || gs > 0) {
      const f = gv;
      const dk = 1 - f * 0.34 - gs * 0.14;
      r *= dk; g *= dk; b *= dk;
      rough[i] += f * 0.20;
      height[i] -= f * 0.155;
    }

    albedo[i * 3] = sat(r); albedo[i * 3 + 1] = sat(g); albedo[i * 3 + 2] = sat(b);
    rough[i] = rough[i] < 0.30 ? 0.30 : rough[i] > 0.70 ? 0.70 : rough[i];
    height[i] = sat(height[i]);
    ao[i] = sat(1 - gs * 0.42 - gv * 0.20 - po * 0.30);
  }

  return { albedo, rough, height, ao };
}
