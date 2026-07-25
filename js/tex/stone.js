/* =========================================================================
   honedStone — dark honed limestone / soapstone slab for the fireplace
   surround. Seen at close range with firelight raking across it, so it is
   built for micro-relief and roughness break-up rather than big geometry.

   Structure:
     · matrix   — warm mid-dark grey; sedimentary bedding drift (metres),
                  warped mottled clouding (centimetres), micro grain (mm)
     · veins    — TRUE DISTANCE isolines of domain-warped fractal fields.
                  For a smooth field s, the set {s = level} is a long,
                  continuous, branching curve, but |s - level| alone is NOT a
                  distance: it collapses to zero slope near saddles and
                  extrema, so a fixed threshold there balloons into a lens-
                  shaped blob (the classic "rice grain" artefact). Dividing
                  by |∇s| converts it to a first-order distance in TEXELS, so
                  a vein keeps the width you ask for everywhere along its
                  length and the blobs cannot happen. Width and opacity are
                  then modulated deliberately, in texels, so strands swell,
                  pinch, taper out and reappear on purpose rather than by
                  accident.
                  Because t = (s-level)/|∇s| is a signed distance, the curves
                  t = ±offset are veins running PARALLEL to the main strand at
                  a spatially varying separation — free anastomosing
                  companions that converge, touch and split apart again, which
                  is exactly what calcite veining does. A second family, at a
                  higher frequency and sheared on the other axis, crosses the
                  first at a wide angle as a fainter web.
     · defects  — scattered dark inclusions (pitted, rougher) and tiny pale
                  calcite grains (proud, glossier)
     · finish   — honed: roughness 0.35..0.62 with broad uneven hone patches;
                  height amplitude stays inside ±0.05 so the slab reads flat

   PERFORMANCE. The per-pixel cost of N.gnoise/N.ridge is dominated by four
   Math.sin hashes per octave, which blows the 150 ms budget at 512² with
   this many fields (a single 3-octave ridged field alone measures ~260 ms).
   So the lattices are baked ONCE from N.hash2 and interpolated here. The
   interpolation is the same maths as N.gnoise / N.vnoise — same hash, same
   quintic/smoothstep fade, same wrap — just hoisted out of the inner loop,
   so the fields are identical to the toolkit's and still tile at `period`.

   The smooth fields are evaluated on a coarse grid and reconstructed
   bilinearly. Note what is stored there: not the raw noise but the signed
   vein DISTANCE, which is very nearly linear across a strand, so bilinear
   reconstruction is essentially exact where it matters and the isolines stay
   razor sharp at full resolution. Storing |s-level| instead would round off
   the crease at the vein centre and beat against the sample grid.

   TILING. Direction comes from two things, both integer and therefore seam-
   free: RECTANGULAR lattices (a PX×PY lattice sampled at u*PX, v*PY elongates
   its features PY/PX to one) and UNIMODULAR shears such as [[1,1],[0,1]]
   applied to uv before scaling, which map the wrap lattice onto itself. The
   elongation is what makes an isoline read as a strand; the shear only leans
   it. Getting direction from the shear alone, pushed hard, combs every strand
   of a family parallel and reads as scratches on slate rather than veining —
   so the shears here stay at 1:1 and the rectangle does the work.
   ========================================================================= */

/* --------------------------------------------------- baked lattice noise */

/** Gradient lattice with INDEPENDENT x and y periods. The rectangle matters:
    sampling a PX×PY lattice at u*PX, v*PY makes the field's features PY/PX
    times longer in u than in v, so the isolines that become veins come out as
    stretched strands instead of the round doodling loops an isotropic field
    gives. Both periods are integers, so it still wraps on the unit tile. */
function gradLattice(PX, PY, seed, hash) {
  const gx = new Float32Array(PX * PY), gy = new Float32Array(PX * PY);
  for (let y = 0; y < PY; y++) {
    for (let x = 0; x < PX; x++) {
      const a = hash(x, y, seed) * 6.283185307;
      gx[y * PX + x] = Math.cos(a);
      gy[y * PX + x] = Math.sin(a);
    }
  }
  return { PX, PY, gx, gy };
}

function valLattice(P, seed, hash) {
  const a = new Float32Array(P * P);
  for (let y = 0; y < P; y++) for (let x = 0; x < P; x++) a[y * P + x] = hash(x, y, seed);
  return { P, a };
}

/** tileable gradient noise, 0..1 — same maths as N.gnoise, rectangular wrap */
function gradAt(L, x, y) {
  const PX = L.PX, PY = L.PY;
  const x0 = Math.floor(x), y0 = Math.floor(y);
  const fx = x - x0, fy = y - y0;
  const u = fx * fx * fx * (fx * (fx * 6 - 15) + 10);
  const v = fy * fy * fy * (fy * (fy * 6 - 15) + 10);
  let X0 = x0 % PX; if (X0 < 0) X0 += PX;
  let Y0 = y0 % PY; if (Y0 < 0) Y0 += PY;
  const X1 = X0 + 1 === PX ? 0 : X0 + 1, Y1 = Y0 + 1 === PY ? 0 : Y0 + 1;
  const gx = L.gx, gy = L.gy;
  const i00 = Y0 * PX + X0, i10 = Y0 * PX + X1, i01 = Y1 * PX + X0, i11 = Y1 * PX + X1;
  const n00 = gx[i00] * fx + gy[i00] * fy;
  const n10 = gx[i10] * (fx - 1) + gy[i10] * fy;
  const n01 = gx[i01] * fx + gy[i01] * (fy - 1);
  const n11 = gx[i11] * (fx - 1) + gy[i11] * (fy - 1);
  const a = n00 + u * (n10 - n00), b = n01 + u * (n11 - n01);
  return (a + v * (b - a)) * 0.7071 + 0.5;
}

/** tileable value noise, 0..1 — same result as N.vnoise(x, y, L.P, seed) */
function valAt(L, x, y) {
  const P = L.P;
  const x0 = Math.floor(x), y0 = Math.floor(y);
  let fx = x - x0, fy = y - y0;
  fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
  let X0 = x0 % P; if (X0 < 0) X0 += P;
  let Y0 = y0 % P; if (Y0 < 0) Y0 += P;
  const X1 = X0 + 1 === P ? 0 : X0 + 1, Y1 = Y0 + 1 === P ? 0 : Y0 + 1;
  const a = L.a;
  const p = a[Y0 * P + X0], q = a[Y0 * P + X1];
  const r = a[Y1 * P + X0], s = a[Y1 * P + X1];
  const t = p + (q - p) * fx, w = r + (s - r) * fx;
  return t + (w - t) * fy;
}

function gradStack(PX0, PY0, oct, seed, hash) {
  const L = []; let PX = PX0, PY = PY0;
  for (let i = 0; i < oct; i++) { L.push(gradLattice(PX, PY, seed + i * 23, hash)); PX *= 2; PY *= 2; }
  return L;
}
function valStack(P0, oct, seed, hash) {
  const L = []; let P = P0;
  for (let i = 0; i < oct; i++) { L.push(valLattice(P, seed + i * 19, hash)); P *= 2; }
  return L;
}

/** fractal sum of value noise over a baked stack, 0..1 */
function vfbm(L, x, y, gain) {
  let v = 0, amp = 1, f = 1, norm = 0;
  for (let i = 0; i < L.length; i++) {
    v += amp * valAt(L[i], x * f, y * f); norm += amp; f *= 2; amp *= gain;
  }
  return v / norm;
}
/** fractal sum of gradient noise — less quilted than value noise, so the
    mid-scale mottle does not show its lattice as a crocodile-skin grid */
function gfbm(L, x, y, gain) {
  let v = 0, amp = 1, f = 1, norm = 0;
  for (let i = 0; i < L.length; i++) {
    v += amp * gradAt(L[i], x * f, y * f); norm += amp; f *= 2; amp *= gain;
  }
  return v / norm;
}

/** branch-free clamp + smoothstep, local so the hot loop avoids the
    cross-module call and the `|| 1e-9` guard in N.smoothstep */
const cl = (v, a, b) => (v < a ? a : v > b ? b : v);
function ss(a, b, x) {
  let t = (x - a) / (b - a);
  t = t < 0 ? 0 : t > 1 ? 1 : t;
  return t * t * (3 - 2 * t);
}

/** fill a W×W tileable field from a unit-square function */
function lrField(W, fn) {
  const a = new Float32Array(W * W);
  for (let y = 0; y < W; y++) {
    for (let x = 0; x < W; x++) a[y * W + x] = fn((x + 0.5) / W, (y + 0.5) / W);
  }
  return a;
}


/** Rasterise one irregular blob into a tileable field, max-combined.
    Hoisted to module scope and called once per blob so it tiers up — the
    same code inlined in the placement loop stays in the interpreter. */
function stamp(field, size, cxp, cyp, rad, cr, sr, sq, cp, sp, cq, sq2, amp) {
  const ix = Math.round(cxp), iy = Math.round(cyp);
  const ex0 = cxp - ix, ey0 = cyp - iy;
  const R = Math.ceil(rad * 1.5) + 1, rr2 = rad * rad * 2.25;
  const lobed = rad > 2.5;
  for (let dy = -R; dy <= R; dy++) {
    const yy = ((iy + dy) % size + size) % size;
    const ey = ey0 + dy;
    for (let dx = -R; dx <= R; dx++) {
      const ex = ex0 + dx;
      const qx = ex * cr + ey * sr, qy = (-ex * sr + ey * cr) / sq;
      const d2 = qx * qx + qy * qy;
      if (d2 > rr2) continue;
      const d = Math.sqrt(d2);
      // irregular outline without atan2/sin: 3rd and 6th angular harmonics
      // straight from the direction cosines (Chebyshev)
      let rr = rad;
      if (lobed && d > 1e-4) {
        const c = qx / d, s = qy / d;
        const c3 = c * (4 * c * c - 3), s3 = s * (3 - 4 * s * s);
        rr = rad * (1 + 0.30 * (s3 * cp + c3 * sp)
                      + 0.16 * (s3 * c3 * cq - (c3 * c3 - s3 * s3) * sq2));
      }
      const t = 1 - d / rr;
      if (t <= 0) continue;
      const xx = ((ix + dx) % size + size) % size;
      const j = yy * size + xx;
      const w = t * t * (3 - 2 * t) * amp;
      if (w > field[j]) field[j] = w;
    }
  }
}

/* ------------------------------------------------------------ generator */

export function honedStone(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const { hash2, hash22, hex } = N;

  /* ---- palette. Warm mid-dark grey matrix (luma ~0.17..0.31) so the
     scene's material.color (0xd8d2c9) can tint it without going muddy. */
  const [MDr, MDg, MDb] = hex(0x2c2a26);      // matrix, dark
  const [MLr, MLg, MLb] = hex(0x5b5449);      // matrix, light
  const [BWr, BWg, BWb] = hex(0x4e4335);      // iron staining in the bedding
  const [VCr, VCg, VCb] = hex(0x74767a);      // vein core: lighter AND cooler
  const [VWr, VWg, VWb] = hex(0x776f62);      // some veins oxidised warm
  const [VHr, VHg, VHb] = hex(0x615f5c);      // diffuse halo round the core
  const [INr, INg, INb] = hex(0x232019);      // dark mineral inclusion
  const [PGr, PGg, PGb] = hex(0xb1aa9c);      // pale calcite grain

  /* ================================ low-frequency fields (W², upsampled) */
  const W = 72;
  const wpS = valStack(2, 3, 101, hash2);     // domain warp
  const preS = valStack(3, 3, 307, hash2);    // vein presence
  const honS = valStack(2, 3, 503, hash2);    // uneven hone patches
  const bedS = valStack(2, 4, 601, hash2);    // sedimentary bedding

  const warpX = lrField(W, (u, v) => vfbm(wpS, u * 2, v * 2, 0.55) - 0.5);
  const warpY = lrField(W, (u, v) => vfbm(wpS, u * 2 + 0.37, v * 2 + 0.71, 0.55) - 0.5);
  /* A fractal sum of N octaves piles up around 0.5 — its useful range is far
     narrower than 0..1. Left raw, any smoothstep gate driven by it sits on the
     shoulder for nearly every texel, so "presence" never reaches full strength
     and every vein comes out uniformly thin and faint. Expanding the contrast
     about 0.5 first is what gives long full-strength runs separated by clean
     gaps — and, on the hone field, a roughness map with real large-scale
     structure rather than a narrow band around the mean. */
  const expand = (v, k) => cl(0.5 + (v - 0.5) * k, 0, 1);
  const presF = lrField(W, (u, v) => expand(vfbm(preS, u * 3, v * 3, 0.55), 1.90));
  const honeF = lrField(W, (u, v) => expand(vfbm(honS, u * 2, v * 2, 0.6), 1.55));
  // bedding: unimodular shear [[1,4],[0,1]] → strata stretched ~4:1 and
  // near-horizontal, still exactly periodic on the unit tile. Bedding is the
  // one thing that SHOULD be a hard shear — sedimentary strata really are
  // parallel — but it is a soft tonal drift, never a line.
  const bedF = lrField(W, (u, v) => expand(vfbm(bedS, (u + 4 * v) * 2, v * 2, 0.55), 1.15));

  /* ======================================== scattered defects (splatted) */
  const incF = N.newF(px);      // dark mineral inclusions — small pits
  const palF = N.newF(px);      // tiny pale calcite grains — slightly proud

  const splat = (field, cells, chance, rMin, rMax, seed) => {
    const step = size / cells;
    for (let cy = 0; cy < cells; cy++) {
      for (let cx = 0; cx < cells; cx++) {
        if (hash2(cx, cy, seed) > chance) continue;
        const [ox, oy] = hash22(cx, cy, seed + 3);
        const h = hash2(cx, cy, seed + 7);
        const cxp = (cx + ox) * step, cyp = (cy + oy) * step;
        const rad = rMin + (rMax - rMin) * h * h;
        const ph = hash2(cx, cy, seed + 11) * 6.283185307;
        const rot = hash2(cx, cy, seed + 17) * 3.14159265;
        stamp(field, size, cxp, cyp, rad,
              Math.cos(rot), Math.sin(rot),
              0.55 + 0.8 * hash2(cx, cy, seed + 13),        // aspect
              Math.cos(ph), Math.sin(ph),
              Math.cos(ph * 1.7), Math.sin(ph * 1.7),
              0.5 + 0.5 * hash2(cx, cy, seed + 19));        // amplitude
    }
    }
  };
  splat(incF, 11, 0.26, 2.5, 7.0, 4001);    // inclusions
  splat(incF, 31, 0.22, 1.0, 2.8, 4301);    // fine dark flecks
  splat(palF, 51, 0.15, 0.6, 1.7, 5101);    // pale calcite grains
  splat(palF, 17, 0.13, 1.2, 3.2, 5501);    // occasional bigger grain

  /* ============================================ vein + mottle fields (H²) */
  // 2×5 and 9×6: rectangular, so the strands come out ~3:1 elongated
  const veA = gradStack(2, 6, 3, 1301, hash2);  // dominant veins
  const veB = gradStack(9, 6, 2, 1601, hash2);  // fine secondary web
  const motS = gradStack(13, 13, 2, 2609, hash2); // cm-scale mottled clouding
  const mic1 = gradLattice(128, 128, 2203, hash2); // ~4 px micro grain
  const mic2 = valLattice(56, 2311, hash2);       // ~9 px micro relief

  /* Coarse grid for everything smooth.
     Channels: 0 tA (signed vein distance, texels), 1 tB, 2 mottle,
     3 presence, 4 hone, 5 bed.
     Quarter res. Everything sampled here is band-limited to >=20 texels, so
     there are >=5 samples per wavelength, and the two channels that carry the
     veins are DISTANCES — locally linear across a strand, so bilinear pickup
     puts the strand centre back within a fraction of a texel. Finer mottle
     than this grid can carry is added at full resolution from the micro
     lattices instead of being aliased in here. */
  const H = Math.max(64, size >> 2);
  const HC = 6;
  const hf = N.newF(H * H * HC);
  const sAf = N.newF(H * H), sBf = N.newF(H * H);
  {
    const invH = 1 / H;
    for (let y = 0; y < H; y++) {
      const v = (y + 0.5) * invH;
      const ly = v * W - 0.5, ly0 = Math.floor(ly);
      let fy = ly - ly0; fy = fy * fy * (3 - 2 * fy);
      let LY0 = ly0 % W; if (LY0 < 0) LY0 += W;
      const LY1 = LY0 + 1 === W ? 0 : LY0 + 1;
      const r0 = LY0 * W, r1 = LY1 * W;

      for (let x = 0; x < H; x++) {
        const u = (x + 0.5) * invH;
        const lx = u * W - 0.5, lx0 = Math.floor(lx);
        let fx = lx - lx0; fx = fx * fx * (3 - 2 * fx);
        let LX0 = lx0 % W; if (LX0 < 0) LX0 += W;
        const LX1 = LX0 + 1 === W ? 0 : LX0 + 1;
        const j00 = r0 + LX0, j10 = r0 + LX1, j01 = r1 + LX0, j11 = r1 + LX1;
        const k00 = (1 - fx) * (1 - fy), k10 = fx * (1 - fy);
        const k01 = (1 - fx) * fy, k11 = fx * fy;

        const wx = warpX[j00] * k00 + warpX[j10] * k10 + warpX[j01] * k01 + warpX[j11] * k11;
        const wy = warpY[j00] * k00 + warpY[j10] * k10 + warpY[j01] * k01 + warpY[j11] * k11;

        // family A — 2×5 lattice (features ~3:1 long) plus a mild shear
        // [[1,1],[0,1]] for lean. Elongation is what stops isolines being
        // round doodles; the shear alone, pushed hard, only combs them into
        // parallel scratches.
        // Warp amplitude is deliberately well under one lattice cell. Past
        // that a domain warp stops meandering the isolines and starts curling
        // them into lassos and spirals, which no stone does.
        const aX = (u + v) * 2 + wx * 0.50, aY = v * 6 + wy * 0.85;
        // family B — sheared [[1,0],[-1,1]], i.e. leaning the other way, so it
        // crosses A at a wide angle; warped by the rotated warp so it does not
        // bend in sympathy with A
        const bX = u * 9 + wy * 0.55, bY = (v - u) * 6 - wx * 0.55;
        // mottle — warped so the clouding is organic rather than a lattice
        const mX = u * 13 + wx * 1.1, mY = v * 13 + wy * 1.1;

        const k = y * H + x, o = k * HC;
        sAf[k] = (gradAt(veA[0], aX, aY) + 0.56 * gradAt(veA[1], aX * 2, aY * 2)
                  + 0.30 * gradAt(veA[2], aX * 4, aY * 4)) / 1.86;
        sBf[k] = (gradAt(veB[0], bX, bY)
                  + 0.46 * gradAt(veB[1], bX * 2, bY * 2)) / 1.46;
        hf[o + 2] = gfbm(motS, mX, mY, 0.55);
        hf[o + 3] = presF[j00] * k00 + presF[j10] * k10 + presF[j01] * k01 + presF[j11] * k11;
        hf[o + 4] = honeF[j00] * k00 + honeF[j10] * k10 + honeF[j01] * k01 + honeF[j11] * k11;
        hf[o + 5] = bedF[j00] * k00 + bedF[j10] * k10 + bedF[j01] * k01 + bedF[j11] * k11;
      }
    }

    /* ---- turn the raw fields into SIGNED DISTANCES in texels ----
       t = (s - level) / |∇s|, gradient by central difference on the wrapping
       coarse grid. This is the step that makes veins keep their width: a bare
       |s - level| threshold widens without limit wherever the field flattens,
       which is what produced lens-shaped blobs instead of strands. */
    const gs = 0.5 * H;                    // d/du per grid step, in unit uv
    for (let y = 0; y < H; y++) {
      const ym = ((y - 1 + H) % H) * H, yp = ((y + 1) % H) * H, yc = y * H;
      for (let x = 0; x < H; x++) {
        const xm = (x - 1 + H) % H, xp = (x + 1) % H;
        const k = yc + x, o = k * HC;
        const bed = hf[o + 5], mot = hf[o + 2], hone = hf[o + 4];

        /* An isoline on a torus is a closed loop: it runs out, turns hard and
           comes back, and that U-turn is what reads as a doodle rather than as
           stone. Real veins in a slab are a joint set — they share a preferred
           orientation and simply stop rather than turning across it. The
           strand's tangent is perpendicular to the field gradient, so |gy|/|g|
           is 1 where the strand runs along the joint direction and 0 exactly
           at the hairpins. Dividing the distance by that weight makes the vein
           thin out through its own turns, so what survives is a set of
           roughly-aligned arcs with tapered ends. It costs nothing: the
           gradient is already computed here to normalise the distance. */
        let gx = (sAf[yc + xp] - sAf[yc + xm]) * gs;
        let gy = (sAf[yp + x] - sAf[ym + x]) * gs;
        let gm = Math.sqrt(gx * gx + gy * gy) + 1e-3;
        let ad = gy / gm; ad *= ad;
        // the isolevel drifts, so a strand wanders onto a neighbouring
        // contour: it merges, splits and dies out instead of running edge to
        // edge at even spacing
        const lvlA = 0.5 + (bed - 0.5) * 0.26 + (hone - 0.5) * 0.11;
        hf[o] = cl((sAf[k] - lvlA) * size / (gm * (0.15 + 0.85 * ad)), -160, 160);

        gx = (sBf[yc + xp] - sBf[yc + xm]) * gs;
        gy = (sBf[yp + x] - sBf[ym + x]) * gs;
        gm = Math.sqrt(gx * gx + gy * gy) + 1e-3;
        // family B is weighted on the OTHER axis, so the web that survives
        // crosses family A at a wide angle instead of combing with it
        let bd = gx / gm; bd *= bd;
        const lvlB = 0.5 + (mot - 0.5) * 0.30 + (bed - 0.5) * 0.16;
        hf[o + 1] = cl((sBf[k] - lvlB) * size / (gm * (0.20 + 0.80 * bd)), -60, 60);
      }
    }
  }

  /* =========================================================== main pass */
  const inv = 1 / size, hScale = H / size;
  const S = size / 512;                       // texel widths quoted at 512²

  /* Both micro lattices are sampled on the axes directly (no shear), so the
     lattice cell index and the interpolation fade depend on x alone and y
     alone. Tabulating them per axis takes the two Math.floor calls, the two
     modulos and the two fade polynomials out of the inner loop entirely —
     which is most of the cost of a full-resolution noise lookup. */
  const axisTab = (P, scale, quintic) => {
    const I0 = new Int32Array(size), I1 = new Int32Array(size);
    const F = new Float32Array(size), U = new Float32Array(size);
    for (let i = 0; i < size; i++) {
      const c = (i + 0.5) * inv * scale, c0 = Math.floor(c), f = c - c0;
      let a = c0 % P; if (a < 0) a += P;
      I0[i] = a; I1[i] = a + 1 === P ? 0 : a + 1; F[i] = f;
      U[i] = quintic ? f * f * f * (f * (f * 6 - 15) + 10) : f * f * (3 - 2 * f);
    }
    return { I0, I1, F, U };
  };
  const t1 = axisTab(128, 128, true), t2 = axisTab(56, 56, false);
  const g1x = mic1.gx, g1y = mic1.gy, m2a = mic2.a;

  /* smoothsteps whose edges are compile-time constants, as multiplies */
  const K_GATE = 1 / (0.62 - 0.40), K_HALO = 1 / (0.80 - 0.28), K_GRAN = 1 / (0.78 - 0.24);

  for (let y = 0; y < size; y++) {
    const v = (y + 0.5) * inv;
    const r1a = t1.I0[y] * 128, r1b = t1.I1[y] * 128, f1y = t1.F[y], u1y = t1.U[y];
    const r2a = t2.I0[y] * 56, r2b = t2.I1[y] * 56, u2y = t2.U[y];
    const hyf = (y + 0.5) * hScale - 0.5, hy0 = Math.floor(hyf);
    const by = hyf - hy0;
    let HY0 = hy0 % H; if (HY0 < 0) HY0 += H;
    const HY1 = HY0 + 1 === H ? 0 : HY0 + 1;
    const hr0 = HY0 * H, hr1 = HY1 * H;

    for (let x = 0; x < size; x++) {
      const u = (x + 0.5) * inv;
      const hxf = (x + 0.5) * hScale - 0.5, hx0 = Math.floor(hxf);
      const bx = hxf - hx0;
      let HX0 = hx0 % H; if (HX0 < 0) HX0 += H;
      const HX1 = HX0 + 1 === H ? 0 : HX0 + 1;
      const q00 = (hr0 + HX0) * HC, q10 = (hr0 + HX1) * HC;
      const q01 = (hr1 + HX0) * HC, q11 = (hr1 + HX1) * HC;
      const b00 = (1 - bx) * (1 - by), b10 = bx * (1 - by);
      const b01 = (1 - bx) * by, b11 = bx * by;

      const tA   = hf[q00]     * b00 + hf[q10]     * b10 + hf[q01]     * b01 + hf[q11]     * b11;
      const tB   = hf[q00 + 1] * b00 + hf[q10 + 1] * b10 + hf[q01 + 1] * b01 + hf[q11 + 1] * b11;
      const mot  = hf[q00 + 2] * b00 + hf[q10 + 2] * b10 + hf[q01 + 2] * b01 + hf[q11 + 2] * b11;
      const pres = hf[q00 + 3] * b00 + hf[q10 + 3] * b10 + hf[q01 + 3] * b01 + hf[q11 + 3] * b11;
      const hone = hf[q00 + 4] * b00 + hf[q10 + 4] * b10 + hf[q01 + 4] * b01 + hf[q11 + 4] * b11;
      const bed  = hf[q00 + 5] * b00 + hf[q10 + 5] * b10 + hf[q01 + 5] * b01 + hf[q11 + 5] * b11;

      const i = y * size + x;

      /* ---- micro detail: the only fields that must be full resolution ----
         mic1 (~4 texels) is fine speckle for albedo and roughness; mic2
         (~9 texels) carries most of the height, because a 3-texel height
         wiggle just aliases into static once the slab is a couple of metres
         away, whereas a 9-texel one still has a shape for raking firelight to
         catch. Same maths as gradAt/valAt, with the per-axis work tabulated. */
      const a0 = t1.I0[x], a1 = t1.I1[x], f1x = t1.F[x], u1x = t1.U[x];
      const i00 = r1a + a0, i10 = r1a + a1, i01 = r1b + a0, i11 = r1b + a1;
      const fx1 = f1x - 1, fy1 = f1y - 1;
      const n00 = g1x[i00] * f1x + g1y[i00] * f1y;
      const n10 = g1x[i10] * fx1 + g1y[i10] * f1y;
      const n01 = g1x[i01] * f1x + g1y[i01] * fy1;
      const n11 = g1x[i11] * fx1 + g1y[i11] * fy1;
      const nA = n00 + u1x * (n10 - n00), nB = n01 + u1x * (n11 - n01);
      const mgF = (nA + u1y * (nB - nA)) * 0.7071 + 0.5;

      const c0 = t2.I0[x], c1 = t2.I1[x], u2x = t2.U[x];
      const p2 = m2a[r2a + c0], q2 = m2a[r2a + c1];
      const s2 = m2a[r2b + c0], w2 = m2a[r2b + c1];
      const mA = p2 + (q2 - p2) * u2x, mB = s2 + (w2 - s2) * u2x;
      const mgM = mA + (mB - mA) * u2y;

      /* ---- veins ----------------------------------------------------
         tA is a signed distance in texels to the dominant isoline, so the
         curves tA = 0, tA = +off, tA = -off2 are three parallel strands whose
         separation drifts with the slow fields: they converge, kiss and pull
         apart, the way anastomosing calcite does. */
      /* The isolines of a smooth field on a torus are CLOSED loops, so left
         to itself every strand runs out, hairpins and comes back — the
         racetrack that instantly reads as a doodle. The presence gate has to
         pinch a strand to nothing well before it can close, which means the
         width must go to zero at gate = 0, not to some floor. A ragged
         cm-scale term on top stops all the terminations happening at the same
         contour of one smooth field. */
      let gt = (pres * 0.86 + mot * 0.14 - 0.40) * K_GATE;
      gt = gt < 0 ? 0 : gt > 1 ? 1 : gt;
      const gate = gt * gt * (3 - 2 * gt);
      const swell = 0.42 * hone + 0.38 * bed + 0.20 * mot;

      // half-widths, in texels at 512² and scaled with size
      const wA = (1.55 + 3.50 * swell) * gate * S;
      const hAw = (8 + 22 * swell) * (0.10 + 0.90 * gate) * S;
      /* Companion separations must drift SLOWLY. The curve tA = off is only a
         well-behaved offset of the strand while |∇off| stays well under one
         texel per texel; drive it from a cm-scale field and the "companion"
         degenerates into speckle instead of a vein. Hence hone/bed here, never
         the mottle. */
      const off1 = (18 + 42 * hone) * S;
      const off2 = (14 + 34 * (1 - bed)) * S;

      const dA0 = tA < 0 ? -tA : tA;
      const e1 = tA - off1, dA1 = e1 < 0 ? -e1 : e1;
      const e2 = tA + off2, dA2 = e2 < 0 ? -e2 : e2;
      let dA = dA0 < dA1 ? dA0 : dA1; if (dA2 < dA) dA = dA2;

      let coreA = 0, haloA = 0, selv = 0;
      if (dA < hAw) {
        const iw = 1 / (wA > 1e-6 ? wA : 1e-6);
        // the bleached halo is uneven along the strand, never a symmetric
        // glow — a symmetric one makes the vein read as a glowing rope
        let hg = (mot - 0.28) * K_HALO; hg = hg < 0 ? 0 : hg > 1 ? 1 : hg;
        haloA = ss(hAw, 0, dA) * gate * (0.45 + 0.70 * hg * hg * (3 - 2 * hg));
        // dominant strand full strength, companions thinner and fainter;
        // distances measured in units of wA so one reciprocal covers them all
        coreA = ss(1.00, 0.30, dA0 * iw);
        const k1 = ss(0.68, 0.20, dA1 * iw) * 0.74;  if (k1 > coreA) coreA = k1;
        const k2 = ss(0.50, 0.15, dA2 * iw) * 0.56;  if (k2 > coreA) coreA = k2;
        coreA *= gate;
        // dark selvage: the matrix immediately beside a vein is a touch darker
        selv = cl((4.5 - dA * iw) * (1 / 3.4), 0, 1) * (1 - coreA);
      }

      /* family B — the fine web. Subordinate, and it lives mostly where A is
         absent, so the two families do not pile up on each other. */
      const gateB = cl((0.92 - pres) * 2.4, 0, 1) * (0.40 + 0.85 * (1 - hone));
      const wB = (0.60 + 0.95 * swell) * (0.30 + 0.70 * gateB) * S;
      const hBw = (3.5 + 7 * swell) * S;
      const offB = (7 + 17 * bed) * S;
      const dB0 = tB < 0 ? -tB : tB;
      const eb = tB - offB, dB1 = eb < 0 ? -eb : eb;
      const dB = dB0 < dB1 ? dB0 : dB1;
      let coreB = 0, haloB = 0;
      if (dB < hBw) {
        const iwB = 1 / (wB > 1e-6 ? wB : 1e-6);
        haloB = ss(hBw, 0, dB) * gateB;
        coreB = ss(1.00, 0.25, dB0 * iwB);
        const kb1 = ss(0.70, 0.20, dB1 * iwB) * 0.70; if (kb1 > coreB) coreB = kb1;
        coreB *= gateB;
      }

      const inc = incF[i], pal = palF[i];

      /* ---- albedo ---- */
      const tone = cl(0.5 + (mot - 0.5) * 0.74 + (bed - 0.5) * 0.60
                             + (mgF - 0.5) * 0.50 + (mgM - 0.5) * 0.38, 0, 1);
      let cr = MDr + (MLr - MDr) * tone;
      let cg = MDg + (MLg - MDg) * tone;
      let cb = MDb + (MLb - MDb) * tone;

      const stain = cl((bed - 0.60) * 2.40, 0, 1) * 0.20;
      cr += (BWr - cr) * stain; cg += (BWg - cg) * stain; cb += (BWb - cb) * stain;

      // dark selvage first, then the diffuse halo, then the hard core
      const sm = selv * 0.17;
      cr *= 1 - sm; cg *= 1 - sm; cb *= 1 - sm;

      const halo = haloA * 0.58 + haloB * 0.16;
      const hm = halo * halo * 0.40;
      cr += (VHr - cr) * hm; cg += (VHg - cg) * hm; cb += (VHb - cb) * hm;

      // vein tint drifts between cool calcite and warm oxidised along the bed
      const tt = cl((bed - 0.42) * 2.2, 0, 1);
      const vr = VCr + (VWr - VCr) * tt;
      const vg = VCg + (VWg - VCg) * tt;
      const vb = VCb + (VWb - VCb) * tt;
      const core = coreA + coreB * 0.50;
      // Cores granulate ALONG their length — the single strongest cue that a
      // vein is a mineral fill and not a stroke drawn on the surface. Without
      // this the strands read as painted lines however good their shape is.
      let gr = (mot - 0.24) * K_GRAN; gr = gr < 0 ? 0 : gr > 1 ? 1 : gr;
      const cm = cl(core * (0.16 + 0.96 * (gr * gr * (3 - 2 * gr))) * (0.78 + 0.36 * mgF),
                    0, 0.44);
      cr += (vr - cr) * cm; cg += (vg - cg) * cm; cb += (vb - cb) * cm;

      const im = inc * 0.86;
      cr += (INr - cr) * im; cg += (INg - cg) * im; cb += (INb - cb) * im;
      const pm = pal * 0.58;
      cr += (PGr - cr) * pm; cg += (PGg - cg) * pm; cb += (PGb - cb) * pm;

      /* cavity: on a slab this flat only the pits genuinely occlude, so the
         AO is folded straight into the albedo rather than shipped as a map */
      const cav = 1 - inc * 0.34 - (0.5 - mgM) * 0.10;
      const o = i * 3;
      albedo[o] = cr * cav; albedo[o + 1] = cg * cav; albedo[o + 2] = cb * cav;

      /* ---- roughness: honed, never constant ---- */
      const r = 0.498
        + (hone - 0.5) * 0.195        // broad soft patches of uneven hone
        + (mot - 0.5) * 0.095         // clouding takes the hone differently
        + (bed - 0.5) * 0.042
        + (mgF - 0.5) * 0.055         // micro grain
        + (mgM - 0.5) * 0.038
        - cm * 0.155                  // calcite polishes smoother
        - halo * 0.038
        + inc * 0.145                 // inclusions are dull and pitted
        - pal * 0.085;                // pale grains glint
      rough[i] = cl(r, 0.35, 0.62);

      /* ---- height: a honed slab is nearly flat. Veins stand very slightly
         PROUD (harder calcite survives the hone), inclusions pit. The bulk of
         the relief is at ~9 texels so raking firelight has something with a
         real shape to catch, instead of per-texel static that just aliases. */
      const h = 0.5
        + cm * 0.030 + halo * 0.006
        + (mgM - 0.5) * 0.055
        + (mgF - 0.5) * 0.012
        + (mot - 0.5) * 0.014
        + (bed - 0.5) * 0.008
        - inc * 0.046
        + pal * 0.016;
      height[i] = cl(h, 0, 1);

    }
  }

  return { albedo, rough, height };
}
