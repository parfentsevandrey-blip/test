/* =========================================================================
   Upholstery.

   A cushion is not a rounded box. It is two panels of cloth sewn together
   around a seam and stuffed, so it bulges in the middle, tucks in sharply at
   the seam, carries a piped welt along that seam, and wrinkles toward the
   corners. A rounded box gets none of that, and no amount of texture work
   fixes it — the silhouette is wrong before the shading starts.

   Everything here is a parametric surface built once at load. The cushion is
   sphere topology with the seam at the equator, which is exactly how the real
   thing is cut; the welt is a tube swept along the same outline, so the two
   can never disagree.
   ========================================================================= */
import * as THREE from 'three';

const UP = new THREE.Vector3(0, 1, 0);

/** Superellipse: 2 is an ellipse, large is a rectangle, 4ish is a cushion. */
function outline(t, n) {
  const a = t * Math.PI * 2;
  const c = Math.cos(a), s = Math.sin(a), p = 2 / n;
  return [Math.sign(c) * Math.pow(Math.abs(c), p), Math.sign(s) * Math.pow(Math.abs(s), p)];
}

/**
 * The outline resampled to equal steps of real arc length, and a segment count
 * picked from the actual perimeter. Sampling the angle uniformly instead puts
 * the same number of points on a 15 cm end as on a 70 cm side, and an arm cap
 * comes out visibly faceted along its length.
 */
function ring(n, hw, hd, wanted) {
  const DENSE = 512;
  const px = new Float64Array(DENSE + 1), pz = new Float64Array(DENSE + 1);
  const cum = new Float64Array(DENSE + 1);
  for (let i = 0; i <= DENSE; i++) {
    const [ox, oz] = outline(i / DENSE, n);
    px[i] = ox; pz[i] = oz;
    if (i) cum[i] = cum[i - 1] + Math.hypot((ox - px[i - 1]) * hw, (oz - pz[i - 1]) * hd);
  }
  const total = cum[DENSE];
  const seg = wanted || Math.max(20, Math.min(48, Math.round(total / 0.045)));
  const out = [];
  let j = 0;
  for (let i = 0; i <= seg; i++) {
    const target = (i / seg) * total;
    while (j < DENSE - 1 && cum[j + 1] < target) j++;
    const span = cum[j + 1] - cum[j];
    const f = span > 1e-9 ? (target - cum[j]) / span : 0;
    out.push([px[j] + (px[j + 1] - px[j]) * f, pz[j] + (pz[j + 1] - pz[j]) * f]);
  }
  out.seg = seg;
  out.length_ = total;
  return out;
}

/**
 * A stuffed cushion of overall size w × h × d.
 *   corner  plan roundness (2 round … 8 boxy)
 *   flat    how fast the height is reached, so the top reads as a face
 *   wide    how long the outline stays at full width before the rim turns
 *   edge    rim softness
 *   pinch   how far the seam is tucked in
 *   wrinkle fold depth near the seam and the corners
 *   sag     downward squash toward the middle, for a seat someone has used
 */
export function cushionGeo(w, h, d, o = {}) {
  const JN = o.rings ?? 15;
  const n = o.corner ?? 4.2, flat = o.flat ?? 2.3, wide = o.wide ?? 4.0;
  const edge = o.edge ?? 0.34, pinch = o.pinch ?? 0.05;
  const wrinkle = o.wrinkle ?? 1.0, sag = o.sag ?? 0;
  const hw = w / 2, hh = h / 2, hd = d / 2;
  const R = ring(n, hw, hd, o.seg);
  const SEG = R.seg;

  const pos = [], uv = [], idx = [];
  for (let j = 0; j <= JN; j++) {
    // The last tenth of v carries the whole turn from the rim onto the flat
    // top. Spaced evenly it gets one ring and the cushion bands; this bunches
    // the rings there instead.
    const e = -1 + (2 * j) / JN;
    const v = Math.sign(e) * (1 - Math.pow(1 - Math.abs(e), 2.0));
    const av = Math.abs(v);
    let rr = Math.pow(Math.max(0, 1 - Math.pow(av, wide)), edge);
    rr *= 1 - pinch * Math.exp(-Math.pow(v / 0.13, 2));
    const yy = Math.sign(v) * (1 - Math.pow(1 - av, flat));

    for (let i = 0; i <= SEG; i++) {
      const [ox, oz] = R[i];
      const a = Math.atan2(oz, ox);

      // Creases run radially out of the seam and die away toward the middle of
      // a face. They must not vary with v: a fold that changes ring to ring
      // draws concentric contours across the cushion, which is corduroy, not
      // upholstery.
      const nearSeam = Math.exp(-Math.pow(v / 0.55, 2));
      const fold = (Math.sin(a * 3.0 + 0.7) * 0.5 +
                    Math.sin(a * 7.0 - 1.3) * 0.32 +
                    Math.sin(a * 13.0 + 1.7) * 0.18) * wrinkle * 0.016 * nearSeam;

      const r = rr + fold;
      const x = hw * ox * r;
      const z = hd * oz * r;
      // the middle of a used seat settles; the edges keep their loft
      const dip = sag * (1 - Math.min(1, Math.pow(Math.abs(ox), 2) + Math.pow(Math.abs(oz), 2))) * Math.max(0, v);
      const y = hh * yy - dip;

      pos.push(x, y, z);
      uv.push(x, z);            // planar from above; the rim is a few cm
    }
  }
  for (let j = 0; j < JN; j++) {
    for (let i = 0; i < SEG; i++) {
      const a = j * (SEG + 1) + i, b = a + 1, c = a + SEG + 1, e = c + 1;
      if (j !== 0) idx.push(a, c, b);
      if (j !== JN - 1) idx.push(b, c, e);
    }
  }

  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  g.setIndex(idx);
  g.computeVertexNormals();
  return g;
}

/** The piped welt sewn into that seam — a tube swept along the same outline. */
export function weltGeo(w, d, o = {}) {
  const n = o.corner ?? 4.2, rad = o.radius ?? 0.0085;
  const R = ring(n, w / 2, d / 2, o.seg);
  const pts = [];
  for (let i = 0; i < R.seg; i++) {
    pts.push(new THREE.Vector3((R[i][0] * w) / 2, o.y ?? 0, (R[i][1] * d) / 2));
  }
  const curve = new THREE.CatmullRomCurve3(pts, true, 'catmullrom', 0.5);
  return new THREE.TubeGeometry(curve, R.seg, rad, 5, true);
}

/**
 * A cloth drape swept along a path: a throw over an arm, a runner on a table.
 * `points` are world-space control points in the order the cloth travels.
 * The cloth ripples across its width, and the ripples deepen wherever it is
 * hanging free rather than lying on something.
 */
export function drapeGeo(points, width, o = {}) {
  const NU = o.nu ?? 34, NV = o.nv ?? 12;
  const folds = o.folds ?? 4, amp = o.amp ?? 0.022, taper = o.taper ?? 0.12;
  const curve = new THREE.CatmullRomCurve3(
    points.map((p) => new THREE.Vector3(p[0], p[1], p[2])), false, 'catmullrom', 0.35);

  const pos = [], uv = [], idx = [];
  const P = new THREE.Vector3(), T = new THREE.Vector3(),
        side = new THREE.Vector3(), nrm = new THREE.Vector3(), q = new THREE.Vector3();
  for (let i = 0; i <= NU; i++) {
    const u = i / NU;
    curve.getPointAt(u, P);
    curve.getTangentAt(u, T);
    side.copy(T).cross(UP);
    if (side.lengthSq() < 1e-6) side.set(1, 0, 0);
    side.normalize();
    nrm.copy(side).cross(T).normalize();

    // free-hanging stretches ripple; the part lying over the arm is pressed flat
    const free = o.freeAt ? o.freeAt(u) : Math.pow(Math.abs(u - 0.5) * 2, 1.4);
    const w = width * (1 - taper * u);

    for (let k = 0; k <= NV; k++) {
      const t = k / NV;
      const s = (t - 0.5) * w;
      const ripple = Math.sin(t * Math.PI * 2 * folds + u * 1.7) * amp * (0.35 + 0.65 * free)
                   + Math.sin(t * Math.PI * 2 * folds * 2.3 + 1.1) * amp * 0.3 * free;
      // the hem gathers in a little as the cloth falls
      q.copy(P).addScaledVector(side, s * (1 - 0.10 * free))
               .addScaledVector(nrm, ripple);
      pos.push(q.x, q.y, q.z);
      uv.push(s, u * curve.getLength());
    }
  }
  for (let i = 0; i < NU; i++) {
    for (let k = 0; k < NV; k++) {
      const a = i * (NV + 1) + k, b = a + 1, c = a + NV + 1, e = c + 1;
      idx.push(a, c, b, b, c, e);
    }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  g.setIndex(idx);
  g.computeVertexNormals();
  return g;
}

/**
 * A curtain panel hanging in the XY plane, folding in Z: gathered tight at the
 * heading, opening out down the drop, pooling a little on the floor. `lead` is
 * the edge that has been drawn back, so the panel sweeps away from the glass.
 */
export function curtainGeo(width, height, o = {}) {
  const NU = o.nu ?? 38, NV = o.nv ?? 20;
  const folds = o.folds ?? 6, depth = o.depth ?? 0.075;
  const lead = o.lead ?? 0.35, pool = o.pool ?? 0.05;

  const pos = [], uv = [], idx = [];
  for (let i = 0; i <= NU; i++) {
    const u = i / NU;
    for (let k = 0; k <= NV; k++) {
      const v = k / NV;                       // 0 at the heading, 1 at the hem
      // a gathered heading opens as it falls; the folds also wander
      const open = 0.42 + 0.58 * Math.pow(v, 0.7);
      const wave = Math.sin(u * Math.PI * 2 * folds + v * 0.9)
                 + 0.35 * Math.sin(u * Math.PI * 2 * folds * 1.7 - v * 1.4);
      const x = (u - 0.5) * width * (0.80 + 0.20 * v) + lead * u * u * v * 0.55;
      const z = wave * depth * open;
      let y = height * (1 - v);
      if (v > 0.94) y = height * (1 - v) - pool * (v - 0.94) / 0.06 * 0.2;   // hem breaks
      pos.push(x, y, z);
      uv.push(x + z, y);
    }
  }
  for (let i = 0; i < NU; i++) {
    for (let k = 0; k < NV; k++) {
      const a = i * (NV + 1) + k, b = a + 1, c = a + NV + 1, e = c + 1;
      idx.push(a, c, b, b, c, e);
    }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  g.setIndex(idx);
  g.computeVertexNormals();
  return g;
}
