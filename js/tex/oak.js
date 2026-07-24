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

export function oakFloor(size, N) {
  const S = size, px = S * S, sc = S / 1024;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px), ao = N.newF(px);
  const phaseT = N.newF(px);          // ring phase fraction, reused by the pore pass
  const poreF = N.newF(px);           // 0..1 pore mask
  const fleckF = N.newF(px);          // 0..1 medullary ray mask
  const grooveF = N.newF(px);         // 0..1 joint / edge groove profile

  /* ---------------------------------------------------------- palette */
  const C_HONEY = N.hex(0xc59560);    // warm honey oak
  const C_GREY = N.hex(0x8d7055);     // greyish-brown oak
  const C_LATE = N.hex(0x6d4b2e);     // dark latewood band
  const C_PORE = N.hex(0x422c1c);     // open pore
  const C_WEAR = N.hex(0xcdae86);     // sun-bleached / worn high spots
  const C_RAY = N.hex(0xdcbc90);      // medullary ray fleck

  /* --------------------------------------------------- broad-scale fields */
  const FS = Math.max(64, Math.round(128 * sc));
  const WS = Math.max(128, Math.round(256 * sc));
  const drift = lowField(N, FS, 3, 4, SEED + 3);        // metre-scale tone
  const oilB = lowField(N, FS, 6, 4, SEED + 21);        // oil soak blotches
  const wearF = lowField(N, FS, 2, 3, SEED + 44);       // broad wear patches
  const warpA = lowField(N, WS, 10, 4, SEED + 61);      // ring wander, coarse
  const warpB = lowField(N, WS, 34, 3, SEED + 77);      // ring wander, fine
  const smF = sampler(FS, S), smW = sampler(WS, S);

  /* micro streaks along the grain: white noise smeared vertically */
  const micro = N.newF(px);
  {
    const R = Math.max(1, Math.round(3 * sc));
    const n = R * 2 + 1;
    for (let y = 0; y < S; y++) {
      for (let x = 0; x < S; x++) {
        let s = 0;
        for (let k = -R; k <= R; k++) s += ihash(x, (y + k + S) % S, SEED + 5);
        micro[y * S + x] = s / n;
      }
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
      const quarter = h(2.3) < 0.42;             // quarter/rift sawn -> straight figure
      const tone = h(1.1);
      const dBase = quarter ? (150 + 320 * h(3.7)) * sc : (6 + 46 * h(3.7)) * sc;
      const spacing = ((quarter ? 5.5 : 7.5) + (quarter ? 5 : 10) * h(6.6)) * sc;
      const tilt = (h(5.5) - 0.5) * 0.085;
      const pu0 = (h(4.4) * 1.7 - 0.35) * colW[c];
      const L = S + 4;
      const wu = smooth1D(N, L, Math.max(8, Math.round(80 * sc)), c * 31 + k * 7 + 5);
      const wd = smooth1D(N, L, Math.max(8, Math.round(150 * sc)), c * 17 + k * 11 + 91);
      const pithU = new Float32Array(L), pithD = new Float32Array(L);
      for (let i = 0; i < L; i++) {
        pithU[i] = pu0 + tilt * i + (wu[i] - 0.5) * 34 * sc;
        const w = wd[i];
        pithD[i] = dBase * (0.12 + 1.7 * w * w);
      }
      const col = N.mixRGB(C_HONEY, C_GREY, tone * 0.9);
      const val = 0.86 + 0.28 * h(7.7);
      bs.push({
        quarter,
        pithU, pithD,
        invSp: 1 / spacing,
        contrast: 0.55 + 0.75 * h(8.8) + (quarter ? 0.1 : 0),
        salt: (c * 977 + k * 613 + 31) | 0,
        r: col[0] * val, g: col[1] * val, b: col[2] * val,
        sheen: (h(9.9) - 0.5) * 0.13,
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
  const warpAmpA = 26 * sc, warpAmpB = 5.5 * sc;
  for (let y = 0; y < S; y++) {
    const fr0 = smF.i0[y] * FS, fr1 = smF.i1[y] * FS, ffy = smF.fr[y];
    const wr0 = smW.i0[y] * WS, wr1 = smW.i1[y] * WS, wfy = smW.fr[y];
    for (let c = 0; c < NP; c++) {
      const b = boards[c][segOf[c][y]];
      const v = pvOf[c][y];
      const pU = b.pithU[v], pD = b.pithD[v], pD2 = pD * pD;
      const x0 = colStart[c], x1 = colStart[c + 1];
      for (let x = x0; x < x1; x++) {
        const i = y * S + x;
        const fc0 = smF.i0[x], fc1 = smF.i1[x], ffx = smF.fr[x];
        const wc0 = smW.i0[x], wc1 = smW.i1[x], wfx = smW.fr[x];

        const wa = bl(warpA, wr0, wr1, wfy, wc0, wc1, wfx) - 0.5;
        const wb = bl(warpB, wr0, wr1, wfy, wc0, wc1, wfx) - 0.5;
        const dr = bl(drift, fr0, fr1, ffy, fc0, fc1, ffx);
        const ob = bl(oilB, fr0, fr1, ffy, fc0, fc1, ffx);
        const we = bl(wearF, fr0, fr1, ffy, fc0, fc1, ffx);
        const mi = micro[i];

        /* ------------------------------------------------- growth rings */
        const du = x - x0 - pU;
        const R = Math.sqrt(du * du + pD2);
        const phase = (R + wa * warpAmpA + wb * warpAmpB) * b.invSp;
        const ri = Math.floor(phase);
        const t = phase - ri;
        phaseT[i] = t;
        const kk = (Math.imul(ri, 2654435761) ^ b.salt) & (TB - 1);
        const lw = 0.15 + 0.34 * RW[kk];
        let late = t > 1 - lw ? (t - (1 - lw)) / (lw * 0.55) : 0;
        if (late > 1) late = 1;
        late = late * late * (3 - 2 * late);
        const grain = sat(late * (0.30 + 0.70 * RD[kk]) * b.contrast);
        const ringTone = 0.972 + 0.056 * RT[kk];   // ring-to-ring tone drift

        /* ------------------------------------------------------- albedo */
        const gm = grain * 0.88;
        let r = b.r + (C_LATE[0] - b.r) * gm;
        let g = b.g + (C_LATE[1] - b.g) * gm;
        let bb = b.b + (C_LATE[2] - b.b) * gm;

        const wear = sat((we - 0.52) * 3.2);
        r += (C_WEAR[0] - r) * wear * 0.30;
        g += (C_WEAR[1] - g) * wear * 0.30;
        bb += (C_WEAR[2] - bb) * wear * 0.30;

        /* oil soaks unevenly: darker + a touch warmer where it pooled */
        const oil = ob - 0.5;
        const shade = ringTone * (0.90 + 0.21 * dr) * (1 - oil * 0.13) * (0.985 + 0.03 * mi);
        r *= shade * (1 - oil * 0.02);
        g *= shade;
        bb *= shade * (1 + oil * 0.03);

        albedo[i * 3] = r; albedo[i * 3 + 1] = g; albedo[i * 3 + 2] = bb;

        /* ------------------------------------------------------- height */
        /* soft earlywood dishes slightly below the denser latewood */
        height[i] = 0.60 + grain * 0.030 + (dr - 0.5) * 0.02 + (mi - 0.5) * 0.014;

        /* ---------------------------------------------------- roughness */
        let rr = 0.455 + b.sheen + (ob - 0.5) * 0.22 + (dr - 0.5) * 0.06;
        rr -= grain * 0.055;                       // dense latewood takes a shine
        rr += wear * 0.16;                         // worn patches lose the oil
        rr += (ihash(x, y, SEED + 9) - 0.5) * 0.045;
        rough[i] = rr;

        ao[i] = 1;
      }
    }
  }

  /* ========================================================== open pores */
  {
    const cand = Math.round(46000 * sc * sc);
    for (let p = 0; p < cand; p++) {
      const rx = N.hash2(p * 0.731 + 1.1, 7.3, SEED + 301);
      const ry = N.hash2(p * 1.117 + 5.7, 2.9, SEED + 307);
      const r3 = N.hash2(p * 0.417 + 3.3, 9.1, SEED + 311);
      const cx = rx * S, cy = ry * S;
      const ix = cx | 0, iy = cy | 0;
      const t = phaseT[iy * S + ix];
      /* ring-porous: big pores crowd the earlywood just past the ring line */
      const openBand = t < 0.20 ? 1 : t < 0.45 ? 0.28 : 0.10;
      if (r3 > openBand) continue;
      const b = boards[colOf[ix]][segOf[colOf[ix]][iy]];
      const r4 = N.hash2(p * 2.13 + 0.9, 4.4, SEED + 317);
      const r5 = N.hash2(p * 1.71 + 8.2, 6.6, SEED + 323);
      const len = (2.5 + 11 * r4 * r4) * sc * (t < 0.20 ? 1 : 0.6);
      const wid = (0.55 + 0.55 * r5) * sc;
      const amp = 0.45 + 0.55 * r5;
      const dxd = b.tilt + (r4 - 0.5) * 0.10, dyd = 1;
      const steps = Math.max(2, Math.ceil(len * 2));
      const wr = Math.max(1, Math.ceil(wid + 0.5));
      for (let s = 0; s <= steps; s++) {
        const tt = s / steps;
        const fade = 1 - Math.abs(tt * 2 - 1) * Math.abs(tt * 2 - 1);
        const px_ = cx + dxd * (tt - 0.5) * len, py_ = cy + dyd * (tt - 0.5) * len;
        const bx = Math.round(px_), by = Math.round(py_);
        for (let oy = -wr; oy <= wr; oy++) {
          for (let ox = -wr; ox <= wr; ox++) {
            const d = Math.hypot(bx + ox - px_, (by + oy - py_) * 0.9);
            const w = amp * fade * sat((wid + 0.45 - d) / 0.9);
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
      if (!b.quarter) continue;
      const n = Math.round((14 + 46 * N.hash2(b.c * 3.7, b.k * 9.1, SEED + 401)) * sc * sc * (b.len / S) * (colW[c] / (S / NP)));
      for (let p = 0; p < n; p++) {
        const h = (q) => N.hash2(p * 1.37 + q, b.c * 6.1 + b.k * 2.3, SEED + 409 + q * 13);
        const cx = colStart[c] + h(0.5) * colW[c];
        const cy = b.y0 + h(1.5) * b.len;
        const len = (7 + 30 * h(2.5) * h(2.5)) * sc;
        const wid = (0.6 + 1.5 * h(3.5)) * sc;
        const ang = (h(4.5) - 0.5) * 0.55;         // roughly across the grain
        const amp = 0.30 + 0.70 * h(5.5);
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
              const d = Math.hypot(bx + ox - px_, by + oy - py_);
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
    const halfW = 3.2 * sc;
    const prof = (d) => sat(N.smoothstep(halfW, 0.9 * sc, d) * 0.55 + N.smoothstep(1.15 * sc, 0.0, d) * 0.55);
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
  const gSoft = N.blur(grooveF, S, Math.max(1, Math.round(2 * sc)));
  for (let i = 0; i < px; i++) {
    const po = poreF[i], fl = fleckF[i], gv = grooveF[i], gs = gSoft[i];

    let r = albedo[i * 3], g = albedo[i * 3 + 1], b = albedo[i * 3 + 2];

    /* ray flecks: pale, silky, slightly proud of the sanded field */
    if (fl > 0) {
      const f = fl * 0.55;
      r += (C_RAY[0] - r) * f; g += (C_RAY[1] - g) * f; b += (C_RAY[2] - b) * f;
      rough[i] -= fl * 0.09;
      height[i] += fl * 0.006;
    }
    /* open pores: darker, deeper, rougher than the sanded surface */
    if (po > 0) {
      const f = po * 0.72;
      r += (C_PORE[0] - r) * f; g += (C_PORE[1] - g) * f; b += (C_PORE[2] - b) * f;
      rough[i] += po * 0.20;
      height[i] -= po * 0.075;
    }
    /* grooves: a real chamfer dip with dirt/oil shadow in the bottom */
    if (gv > 0 || gs > 0) {
      const f = gv;
      const dk = 1 - f * 0.42 - gs * 0.16;
      r *= dk; g *= dk; b *= dk;
      rough[i] += f * 0.20;
      height[i] -= f * 0.19;
    }

    albedo[i * 3] = sat(r); albedo[i * 3 + 1] = sat(g); albedo[i * 3 + 2] = sat(b);
    rough[i] = rough[i] < 0.27 ? 0.27 : rough[i] > 0.74 ? 0.74 : rough[i];
    height[i] = sat(height[i]);
    ao[i] = sat(1 - gs * 0.42 - gv * 0.20 - po * 0.30);
  }

  return { albedo, rough, height, ao };
}
