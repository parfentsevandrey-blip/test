/* =========================================================================
   Part 4 / 5 — furniture, soft goods, plants, the cat, and the lighting rig
   ========================================================================= */
import * as THREE from 'three';
import {
  GLSL_NOISE, U, ROOM, rnd, rrnd, pick, roomScene, MAX_ANISO,
  roundedBoxGeo, faceTowards, normalizeUv,
} from './room.js';
import { MAT, bookMat, shadowed } from './room-mat.js';
import { ROOMS } from './room-plan.js';
import { cushionGeo, weltGeo, drapeGeo, curtainGeo } from './room-soft.js';
import { chandelier, floorLamp } from './room-lamps.js';
export { buildLights, updateLights } from './room-lights.js';

/* The living room is no longer the whole flat: it runs x -5..4, z -4..3,
   open to the kitchen across x=4. Everything here is placed against those
   bounds rather than the old symmetric box. */
const LR = ROOMS.living;
const X = ROOM.x, Z = ROOM.z, H = ROOM.h;

/* The palette lives in room-mat.js now that four rooms share it. The aliases
   keep this file's old names working against the redesigned set. */
const M = {
  ...MAT,
  oak: MAT.oakPale,
  oakDark: MAT.walnut,
  blackSteel: MAT.steel,
  bookCloth: MAT.linen,
};

/* ================================================================= rug === */
function buildRug(g) {
  const rug = new THREE.Mesh(roundedBoxGeo(4.4, 0.022, 3.3, 0.05, 1), M.rug);
  rug.position.set(-0.75, 0.011, 0.30);
  rug.receiveShadow = true;
  g.add(rug);
  return rug;
}

/* ================================================================ sofa === */
/* One seat cushion, its welt, and the shadow flags — used by the sofa and the
   armchair so the two are upholstered the same way. */
function upholster(s, geoOpts, mat, w, h, d, pos, rot) {
  const c = shadowed(new THREE.Mesh(cushionGeo(w, h, d, geoOpts), mat));
  c.position.set(pos[0], pos[1], pos[2]);
  if (rot) c.rotation.set(rot[0] || 0, rot[1] || 0, rot[2] || 0);
  s.add(c);
  // the piping is sewn into the seam, so it follows the same outline
  const welt = new THREE.Mesh(
    weltGeo(w * 0.995, d * 0.995, { corner: geoOpts.corner ?? 4.2, radius: geoOpts.welt ?? 0.0085 }),
    mat);
  welt.castShadow = false;
  c.add(welt);
  return c;
}

function buildSofa(g) {
  const s = new THREE.Group();
  s.position.set(0.15, 0, 1.95);          // back to the room, facing the window

  const W = 2.52, D = 0.98, SEAT = 0.40;
  const CW = W / 3 - 0.02;

  // An upholstered base on low wooden feet, inset from the body — the old one
  // was a dark slab wider than the sofa and read as a pallet.
  const base = shadowed(new THREE.Mesh(roundedBoxGeo(W - 0.10, 0.20, D - 0.10, 0.02), M.linenDark));
  base.position.y = 0.20; s.add(base);
  [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(([sx, sz]) => {
    const f = new THREE.Mesh(new THREE.CylinderGeometry(0.026, 0.019, 0.10, 8), M.oakDark);
    f.position.set(sx * (W / 2 - 0.20), 0.05, sz * (D / 2 - 0.16));
    f.castShadow = true; s.add(f);
  });

  /* seat cushions — the middle one has been sat in, the outer two are proud,
     and none of the three is quite square to the frame */
  const seatSag = [0.006, 0.020, 0.010];
  const seatTilt = [-0.012, 0.004, 0.014];
  for (let i = -1; i <= 1; i++) {
    const k = i + 1;
    upholster(s, { corner: 4.8, wide: 5.4, edge: 0.24, sag: seatSag[k], wrinkle: 1.0 },
      M.linen, CW, 0.185, D - 0.20,
      [i * (W / 3) + seatTilt[k] * 0.6, SEAT + 0.005 - seatSag[k], 0.015],
      [0, seatTilt[k], 0]);
  }

  /* Back cushions. Their seam runs round the perimeter as you look at them,
     so they are built lying down and stood up — build one the other way and
     its front face is a pole, which shows as rings across the linen. */
  const backLean = [-0.20, -0.15, -0.23];
  for (let i = -1; i <= 1; i++) {
    const k = i + 1;
    upholster(s, { corner: 3.4, wide: 3.2, edge: 0.42, wrinkle: 1.5, pinch: 0.07 },
      M.linen, CW - 0.01, 0.25, 0.50,
      [i * (W / 3), 0.75, D / 2 - 0.19],
      [Math.PI / 2 + backLean[k], (k - 1) * 0.02, 0]);
  }

  // arms: tight-upholstered, so a rounded box with a welt run over its top
  [-1, 1].forEach((sgn) => {
    const a = shadowed(new THREE.Mesh(roundedBoxGeo(0.19, 0.42, D, 0.075), M.linenDark));
    a.position.set(sgn * (W / 2 - 0.095), 0.38, 0);
    s.add(a);
    const cap = shadowed(new THREE.Mesh(
      cushionGeo(0.19, 0.09, D - 0.02, { corner: 5.5, wide: 5.0, edge: 0.26, wrinkle: 0.5 }),
      M.linenDark));
    cap.position.set(sgn * (W / 2 - 0.095), 0.605, 0);
    s.add(cap);
  });

  const bp = shadowed(new THREE.Mesh(roundedBoxGeo(W, 0.66, 0.13, 0.05), M.linenDark));
  bp.position.set(0, 0.52, D / 2 - 0.035);
  s.add(bp);

  /* Throw pillows. Real ones are stuffed square and land on their corners,
     leaning into whatever is behind them — never standing up on edge. */
  const pillow = (x, z, sz, mat, tilt, spin) => {
    const p = shadowed(new THREE.Mesh(
      cushionGeo(sz, 0.16, sz, { corner: 2.9, wide: 2.6, edge: 0.46, wrinkle: 1.8, pinch: 0.09 }),
      mat));
    p.position.set(x, SEAT + 0.10 + sz * 0.46, z);
    p.rotation.set(Math.PI / 2 - tilt, spin, spin * 0.7);
    s.add(p);
    const welt = new THREE.Mesh(weltGeo(sz * 0.99, sz * 0.99, { corner: 2.9, radius: 0.007 }), mat);
    welt.castShadow = false; p.add(welt);
  };
  pillow(-0.86, 0.19, 0.44, M.rust, 0.30, 0.20);
  pillow(-0.53, 0.26, 0.38, M.boucle, 0.20, -0.13);
  pillow(0.90, 0.21, 0.42, M.boucle, 0.26, -0.24);
  pillow(0.58, 0.27, 0.36, M.rust, 0.16, 0.17);

  /* A knitted throw thrown over the left arm: up the outside, over the top,
     down onto the seat, with the tail crumpled where it lands. */
  const ax = -(W / 2 - 0.095);
  const thr = shadowed(new THREE.Mesh(drapeGeo([
    [ax - 0.105, 0.30, -0.26],
    [ax - 0.115, 0.44, -0.14],
    [ax - 0.10, 0.57, -0.01],
    [ax - 0.02, 0.645, 0.09],
    [ax + 0.11, 0.615, 0.15],
    [ax + 0.24, 0.545, 0.16],
    [ax + 0.37, 0.515, 0.11],
  ], 0.46, { folds: 5, amp: 0.024, taper: 0.06,
             freeAt: (u) => Math.max(0, 1 - Math.abs(u - 0.10) * 3.2) }), M.knit));
  s.add(thr);

  g.add(s);
  return s;
}

/* ============================================================ armchair === */
function buildArmchair(g, x, z, lookX, lookZ, mat, seed = 0) {
  const c = new THREE.Group();
  c.position.set(x, 0, z);

  const W = 0.86, D = 0.84;
  /* An upholstered skirt on short legs. The chair used to stand on a dark
     slab wider than its own seat, which read as a shipping pallet. */
  const skirt = shadowed(new THREE.Mesh(roundedBoxGeo(W - 0.09, 0.14, D - 0.09, 0.02), mat));
  skirt.position.y = 0.19; c.add(skirt);
  [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(([sx, sz]) => {
    const l = new THREE.Mesh(new THREE.CylinderGeometry(0.024, 0.017, 0.13, 8), M.oakDark);
    l.position.set(sx * (W / 2 - 0.10), 0.062, sz * (D / 2 - 0.10));
    l.castShadow = true; c.add(l);
  });

  upholster(c, { corner: 4.8, wide: 5.4, edge: 0.24, sag: 0.014 + seed * 0.006, wrinkle: 1.1 },
    mat, W - 0.09, 0.18, D - 0.14, [0, 0.335, 0.015], [0, 0.015 - seed * 0.03, 0]);

  const back = shadowed(new THREE.Mesh(
    cushionGeo(W - 0.09, 0.20, 0.54, { corner: 4.0, wide: 4.2, edge: 0.32, wrinkle: 1.4, pinch: 0.06 }),
    mat));
  back.position.set(0, 0.66, D / 2 - 0.14);
  back.rotation.x = Math.PI / 2 - 0.21 - seed * 0.02;
  c.add(back);
  const bWelt = new THREE.Mesh(weltGeo(W - 0.10, 0.53, { corner: 4.0, radius: 0.008 }), mat);
  bWelt.castShadow = false; back.add(bWelt);

  [-1, 1].forEach((s) => {
    const a = shadowed(new THREE.Mesh(roundedBoxGeo(0.155, 0.34, D - 0.10, 0.07), mat));
    a.position.set(s * (W / 2 - 0.065), 0.44, 0.0); c.add(a);
    const cap = shadowed(new THREE.Mesh(
      cushionGeo(0.155, 0.075, D - 0.12, { corner: 5.2, wide: 4.8, edge: 0.26, wrinkle: 0.5 }), mat));
    cap.position.set(s * (W / 2 - 0.065), 0.625, 0); c.add(cap);
  });

  const p = shadowed(new THREE.Mesh(
    cushionGeo(0.36, 0.15, 0.36, { corner: 2.9, wide: 2.6, edge: 0.46, wrinkle: 1.8, pinch: 0.09 }),
    M.rust));
  p.position.set(0.05, 0.55, 0.03);
  p.rotation.set(Math.PI / 2 - 0.40, 0.22, 0.14);
  c.add(p);
  const pWelt = new THREE.Mesh(weltGeo(0.355, 0.355, { corner: 2.9, radius: 0.007 }), M.rust);
  pWelt.castShadow = false; p.add(pWelt);

  g.add(c);
  faceTowards(c, lookX, lookZ);
  return c;
}

/* ======================================================== coffee table === */
function buildCoffeeTable(g) {
  const t = new THREE.Group();
  t.position.set(-0.55, 0, 0.30);

  const top = shadowed(new THREE.Mesh(roundedBoxGeo(1.30, 0.05, 0.72, 0.012, 2), M.marble));
  top.position.y = 0.38; t.add(top);
  [[-1, 0], [1, 0]].forEach(([sx]) => {
    const leg = shadowed(new THREE.Mesh(roundedBoxGeo(0.045, 0.36, 0.60, 0.01, 1), M.blackSteel));
    leg.position.set(sx * 0.52, 0.18, 0); t.add(leg);
  });
  const stretch = new THREE.Mesh(new THREE.BoxGeometry(1.0, 0.03, 0.03), M.blackSteel);
  stretch.position.set(0, 0.10, 0); stretch.castShadow = true; t.add(stretch);

  /* --- things on the table --- */
  const bookCols = [0x7a3b2c, 0x2f4038, 0x8a6a3a, 0x33303a, 0xa8563c];
  let by = 0.405;
  for (let i = 0; i < 3; i++) {
    const w = 0.30 - i * 0.018, d = 0.22 - i * 0.012, h = 0.026 + rnd() * 0.016;
    const b = shadowed(new THREE.Mesh(roundedBoxGeo(w, h, d, 0.004, 1), bookMat(pick(bookCols))));
    b.position.set(-0.36, by + h / 2, 0.06);
    b.rotation.y = rrnd(-0.12, 0.12);
    by += h; t.add(b);
  }

  // mug + saucer
  const mug = new THREE.Group();
  mug.position.set(0.24, 0.405, -0.07);
  const saucer = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.075, 0.07, 0.012, 20), M.ceramic));
  mug.add(saucer);
  const cup = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.042, 0.034, 0.085, 20, 1, true), M.ceramic));
  cup.position.y = 0.05; mug.add(cup);
  const cupBase = new THREE.Mesh(new THREE.CircleGeometry(0.034, 20), M.ceramic);
  cupBase.rotation.x = -Math.PI / 2; cupBase.position.y = 0.0085; mug.add(cupBase);
  const tea = new THREE.Mesh(new THREE.CircleGeometry(0.038, 20),
    new THREE.MeshStandardMaterial({ color: 0x2a160c, roughness: 0.18, metalness: 0.1 }));
  tea.rotation.x = -Math.PI / 2; tea.position.y = 0.075; mug.add(tea);
  const handle = shadowed(new THREE.Mesh(new THREE.TorusGeometry(0.026, 0.006, 8, 16, Math.PI * 1.3), M.ceramic));
  handle.position.set(0.046, 0.052, 0); handle.rotation.set(0, Math.PI / 2, -0.2); mug.add(handle);
  t.add(mug);

  // steam
  const steam = new THREE.Mesh(
    new THREE.PlaneGeometry(0.20, 0.34),
    new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide,
      uniforms: { uTime: U.time },
      vertexShader: `varying vec2 vUv; void main(){ vUv=uv; gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); }`,
      fragmentShader: GLSL_NOISE + /* glsl */`
        varying vec2 vUv; uniform float uTime;
        void main(){
          vec2 p = vUv;
          float rise = uTime * 0.35;
          float n = fbm2(vec2(p.x * 5.0 + sin(p.y * 6.0 + rise) * 0.6, p.y * 3.0 - rise));
          float shape = smoothstep(0.5, 0.0, abs(p.x - 0.5 + sin(p.y * 5.0 + uTime * 0.8) * 0.10 * p.y) / (0.10 + p.y * 0.34));
          float a = shape * smoothstep(0.0, 0.18, p.y) * smoothstep(1.0, 0.35, p.y) * smoothstep(0.35, 0.75, n);
          gl_FragColor = vec4(vec3(0.95, 0.86, 0.76) * 0.5, a * 0.30);
        }`,
    }),
  );
  steam.position.set(0.24, 0.62, -0.07);
  steam.userData.billboard = true;
  steam.renderOrder = 9;
  t.add(steam);

  // small tray + candle
  const tray = shadowed(new THREE.Mesh(roundedBoxGeo(0.26, 0.014, 0.18, 0.01, 1), M.brass));
  tray.position.set(0.02, 0.412, 0.20); t.add(tray);
  const candle = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.032, 0.032, 0.10, 16), M.ceramic));
  candle.position.set(0.02, 0.47, 0.20); t.add(candle);

  g.add(t);
  return { group: t, steam, candleTop: new THREE.Vector3(-0.53, 0.52, 0.50) };
}

/* ============================================================== pouf ==== */
function buildPouf(g, x, z) {
  // a stuffed round pouf: soft top, seam round the middle, settled on the rug
  const p = shadowed(new THREE.Mesh(
    cushionGeo(0.70, 0.42, 0.70, { corner: 2.0, wide: 2.4, edge: 0.44, wrinkle: 1.1, pinch: 0.06 }),
    M.boucleRound));
  p.position.set(x, 0.21, z);
  p.rotation.y = 0.4;
  g.add(p);
  const seam = new THREE.Mesh(new THREE.TorusGeometry(0.348, 0.011, 8, 30), M.boucleRound);
  seam.position.set(x, 0.21, z); seam.rotation.x = Math.PI / 2;
  seam.castShadow = true; g.add(seam);
  return p;
}

/* ========================================================== side table == */
function buildSideTable(g, x, z) {
  const t = new THREE.Group();
  t.position.set(x, 0, z);
  const top = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.24, 0.24, 0.035, 24), M.oak));
  top.position.y = 0.52; t.add(top);
  const col = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.035, 0.045, 0.50, 12), M.oak));
  col.position.y = 0.26; t.add(col);
  const foot = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.17, 0.19, 0.024, 20), M.oak));
  foot.position.y = 0.012; t.add(foot);
  // stack of books + reading glasses stand-in
  const b1 = shadowed(new THREE.Mesh(roundedBoxGeo(0.19, 0.032, 0.14, 0.004, 1), bookMat(0x2f4038)));
  b1.position.set(0.02, 0.554, 0.01); t.add(b1);
  const b2 = shadowed(new THREE.Mesh(roundedBoxGeo(0.175, 0.028, 0.13, 0.004, 1), bookMat(0x8a6a3a)));
  b2.position.set(0.0, 0.584, 0.02); b2.rotation.y = 0.16; t.add(b2);
  g.add(t);
  return t;
}

/* =========================================================== bookshelf == */
function buildBookshelf(g) {
  const s = new THREE.Group();
  // north of the fireplace on the west wall — its old wall is the kitchen now
  s.position.set(LR.x0 + 0.17, 0, 2.15);

  const W = 0.34, HH = 2.35, L = 1.45;
  const carcass = shadowed(new THREE.Mesh(new THREE.BoxGeometry(W, HH, L), M.oakDark));
  carcass.position.set(0, HH / 2, 0); s.add(carcass);
  const front = shadowed(new THREE.Mesh(new THREE.BoxGeometry(0.02, HH, L), M.oak));
  front.position.set(-W / 2 - 0.01, HH / 2, 0); s.add(front);

  const shelfY = [0.42, 0.86, 1.30, 1.74, 2.14];
  const bookCols = [0x7a3b2c, 0x2f4038, 0x8a6a3a, 0x33303a, 0xa8563c, 0x5b4a63, 0x3d5a63, 0x94734a];

  shelfY.forEach((y, si) => {
    const sh = shadowed(new THREE.Mesh(new THREE.BoxGeometry(W - 0.03, 0.022, L - 0.06), M.oak));
    sh.position.set(-0.01, y, 0); s.add(sh);
    // concealed strip light under each shelf
    const led = new THREE.Mesh(new THREE.BoxGeometry(0.10, 0.008, L - 0.30),
      new THREE.MeshBasicMaterial({ color: 0x6b3f1c, toneMapped: false }));
    led.position.set(-0.10, y - 0.02, 0); s.add(led);

    // books
    let z = -L / 2 + 0.10;
    while (z < L / 2 - 0.16) {
      if (rnd() < 0.14) { z += rrnd(0.06, 0.18); continue; }   // gaps and objects
      const bw = rrnd(0.022, 0.055);
      const bh = rrnd(0.20, 0.31);
      const bd = rrnd(0.20, 0.27);
      const b = new THREE.Mesh(new THREE.BoxGeometry(bd, bh, bw), bookMat(pick(bookCols)));
      b.position.set(-0.02, y + 0.011 + bh / 2, z + bw / 2);
      if (rnd() < 0.10) { b.rotation.x = 0.28; b.position.y -= 0.02; }
      b.castShadow = true; b.receiveShadow = true;
      s.add(b);
      z += bw + 0.004;
    }
  });

  // a couple of objects on the shelves
  const vase = shadowed(new THREE.Mesh(new THREE.LatheGeometry(
    [[0.0, 0], [0.05, 0], [0.065, 0.05], [0.055, 0.12], [0.035, 0.17], [0.042, 0.20]]
      .map(([r, y2]) => new THREE.Vector2(r, y2)), 18), M.ceramic));
  vase.position.set(-0.02, 1.752, -0.48); s.add(vase);
  const bowl = shadowed(new THREE.Mesh(new THREE.SphereGeometry(0.075, 16, 10, 0, Math.PI * 2, Math.PI * 0.5, Math.PI * 0.5), M.brass));
  bowl.position.set(-0.02, 0.955, 0.44); s.add(bowl);

  s.rotation.y = Math.PI / 2;          // it faces east now, off the west wall
  s.position.set(LR.x0 + 0.17, 0, 2.15);
  g.add(s);
  return s;
}

/* ============================================================== plant === */
function buildPlant(g, x, z, scale = 1) {
  const p = new THREE.Group();
  p.position.set(x, 0, z);
  p.scale.setScalar(scale);

  const pot = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.21, 0.17, 0.34, 22), M.ceramic));
  pot.position.y = 0.17; p.add(pot);
  const soil = new THREE.Mesh(new THREE.CircleGeometry(0.195, 20),
    new THREE.MeshStandardMaterial({ color: 0x241a12, roughness: 1 }));
  soil.rotation.x = -Math.PI / 2; soil.position.y = 0.335; p.add(soil);

  const leafGeo = (() => {
    const shape = new THREE.Shape();
    shape.moveTo(0, 0);
    shape.bezierCurveTo(0.11, 0.10, 0.13, 0.34, 0, 0.50);
    shape.bezierCurveTo(-0.13, 0.34, -0.11, 0.10, 0, 0);
    const gg = new THREE.ShapeGeometry(shape, 10);
    return normalizeUv(gg);
  })();

  const stems = 5;
  for (let i = 0; i < stems; i++) {
    const a = (i / stems) * Math.PI * 2 + rnd();
    const lean = rrnd(0.10, 0.28);
    const hgt = rrnd(0.85, 1.55);
    const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.018, hgt, 6), M.leaf);
    stem.position.set(Math.cos(a) * 0.05, 0.33 + hgt / 2, Math.sin(a) * 0.05);
    stem.rotation.z = -Math.cos(a) * lean;
    stem.rotation.x = Math.sin(a) * lean;
    stem.castShadow = true;
    p.add(stem);

    const nLeaf = 4 + ((rnd() * 3) | 0);
    for (let k = 0; k < nLeaf; k++) {
      const t = 0.35 + (k / nLeaf) * 0.68;
      const lf = new THREE.Mesh(leafGeo, M.leaf);
      const sc = rrnd(0.55, 1.05) * (0.6 + t * 0.6);
      lf.scale.setScalar(sc);
      lf.position.set(
        Math.cos(a) * (0.05 + lean * hgt * t * 0.9),
        0.33 + hgt * t,
        Math.sin(a) * (0.05 + lean * hgt * t * 0.9),
      );
      lf.rotation.set(rrnd(-0.5, 0.2), a + rrnd(-1.4, 1.4), rrnd(-0.6, 0.6));
      lf.castShadow = true;
      lf.userData.sway = { base: lf.rotation.z, amp: rrnd(0.01, 0.035), ph: rnd() * 6.28 };
      p.add(lf);
    }
  }
  g.add(p);
  return p;
}

/* ================================================================ cat === */
function buildCat(g, x, z) {
  const c = new THREE.Group();
  c.position.set(x, 0, z);
  c.rotation.y = -0.7;

  const body = shadowed(new THREE.Mesh(new THREE.SphereGeometry(0.17, 20, 14), M.fur));
  body.scale.set(1.5, 0.78, 1.05);
  body.position.set(0, 0.13, 0); c.add(body);

  const head = shadowed(new THREE.Mesh(new THREE.SphereGeometry(0.093, 18, 14), M.fur));
  head.scale.set(1.0, 0.92, 1.0);
  head.position.set(0.20, 0.135, 0.045); c.add(head);

  [[0.055, 0.075], [0.055, -0.02]].forEach(([dx, dz]) => {
    const ear = shadowed(new THREE.Mesh(new THREE.ConeGeometry(0.033, 0.055, 4), M.fur));
    ear.position.set(0.20 + dx * 0.2, 0.205, 0.045 + dz);
    ear.rotation.set(0.1, 0.6, 0.22); c.add(ear);
  });

  // tail curled around the body
  const curve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(-0.22, 0.12, 0.02),
    new THREE.Vector3(-0.30, 0.09, 0.16),
    new THREE.Vector3(-0.16, 0.07, 0.26),
    new THREE.Vector3(0.04, 0.06, 0.22),
    new THREE.Vector3(0.16, 0.06, 0.12),
  ]);
  const tail = shadowed(new THREE.Mesh(new THREE.TubeGeometry(curve, 24, 0.028, 8, false), M.fur));
  c.add(tail);

  c.userData.breathe = body;
  g.add(c);
  return c;
}

/* ============================================================ wall art == */
function buildArt(g) {
  const tex = (() => {
    const cv = document.createElement('canvas');
    cv.width = 512; cv.height = 340;
    const ctx = cv.getContext('2d');
    const grd = ctx.createLinearGradient(0, 0, 0, 340);
    grd.addColorStop(0, '#1d1a20'); grd.addColorStop(0.55, '#3b2b22'); grd.addColorStop(1, '#6b452a');
    ctx.fillStyle = grd; ctx.fillRect(0, 0, 512, 340);
    for (let i = 0; i < 220; i++) {
      ctx.globalAlpha = 0.02 + rnd() * 0.06;
      ctx.fillStyle = pick(['#c98a4b', '#8a5a34', '#2a2530', '#e0b070']);
      const w = rrnd(20, 220), h = rrnd(3, 26);
      ctx.fillRect(rrnd(-40, 512), rrnd(-20, 340), w, h);
    }
    ctx.globalAlpha = 1;
    const t = new THREE.CanvasTexture(cv);
    t.colorSpace = THREE.SRGBColorSpace;
    return t;
  })();

  const art = shadowed(new THREE.Mesh(new THREE.BoxGeometry(1.9, 1.25, 0.05),
    [M.oakDark, M.oakDark, M.oakDark, M.oakDark,
     new THREE.MeshStandardMaterial({ map: tex, roughness: 0.85, envMapIntensity: 0.3 }), M.oakDark]));
  art.position.set(-1.5, 1.78, LR.z1 - 0.14);
  art.rotation.y = Math.PI;
  g.add(art);
  return art;
}

/* ========================================================= dust motes === */
function buildDust(g) {
  const N = 250;
  const geo = new THREE.BufferGeometry();
  const pos = new Float32Array(N * 3), seed = new Float32Array(N * 3);
  for (let i = 0; i < N; i++) {
    pos[i * 3] = rrnd(LR.x0 + 0.4, LR.x1 - 0.4);
    pos[i * 3 + 1] = rrnd(0.15, H - 0.35);
    pos[i * 3 + 2] = rrnd(LR.z0 + 0.4, LR.z1 - 0.4);
    seed[i * 3] = rnd(); seed[i * 3 + 1] = rnd(); seed[i * 3 + 2] = rnd();
  }
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  geo.setAttribute('aSeed', new THREE.BufferAttribute(seed, 3));

  const pts = new THREE.Points(geo, new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: { uTime: U.time, uFire: U.fire, uPix: { value: 1 }, uFirePos: { value: new THREE.Vector3(-4.9, 0.9, -0.6) } },
    vertexShader: GLSL_NOISE + /* glsl */`
      attribute vec3 aSeed;
      uniform float uTime, uPix;
      uniform vec3 uFirePos;
      varying float vGlow, vTw;
      void main(){
        vec3 p = position;
        float t = uTime * (0.05 + aSeed.z * 0.06);
        p.x += sin(t * 2.1 + aSeed.x * 30.0) * 0.28;
        p.y += sin(t * 1.4 + aSeed.y * 30.0) * 0.20 + mod(uTime * 0.014 * (0.4 + aSeed.z), 1.0) * 0.4;
        p.z += cos(t * 1.7 + aSeed.z * 30.0) * 0.28;
        // motes glow when they drift through the firelight
        float d = length(p - uFirePos);
        vGlow = smoothstep(5.2, 0.9, d);
        vTw = 0.5 + 0.5 * sin(uTime * 2.4 + aSeed.x * 40.0);
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_Position = projectionMatrix * mv;
        gl_PointSize = clamp((0.6 + aSeed.y * 1.1) * uPix * 7.5 / max(-mv.z, 0.25), 0.0, 6.0);
      }`,
    fragmentShader: /* glsl */`
      varying float vGlow, vTw;
      uniform float uFire;
      void main(){
        float d = length(gl_PointCoord - 0.5) * 2.0;
        float a = smoothstep(1.0, 0.0, d);
        a = pow(a, 2.0) * (0.10 + vGlow * 0.85) * (0.4 + vTw * 0.6);
        vec3 c = mix(vec3(0.62,0.66,0.78), vec3(1.0,0.66,0.30), vGlow);
        gl_FragColor = vec4(c * (0.4 + vGlow * 1.5 * uFire), a * 0.15);
      }`,
  }));
  pts.frustumCulled = false;
  pts.renderOrder = 8;
  g.add(pts);
  return pts;
}

/* ============================================================ curtains == */
/* Floor-to-ceiling glass with nothing at the edges reads as an office. Two
   panels drawn back at the ends of the front window and one on the side
   glazing soften the corners and give the firelight something warm to catch
   between the room and the night. They sit inside the walker's wall clearance
   so you can never push through one. */
function buildCurtains(g) {
  const c = new THREE.Group();
  c.name = 'curtains';
  const DROP = 3.14;

  const panel = (x, z, w, ry, lead) => {
    const m = new THREE.Mesh(curtainGeo(w, DROP, {
      folds: 6, depth: 0.072, lead, pool: 0.05,
    }), M.drape);
    m.position.set(x, 0.015, z);
    m.rotation.y = ry;
    // a curtain in front of the window has nothing behind it to shadow, and a
    // 3 m shadow caster is not what the fire's shadow map is for
    m.receiveShadow = true;
    c.add(m);
    return m;
  };

  panel(LR.x0 + 0.62, LR.z0 + 0.15, 1.08, 0, 0.22);
  panel(LR.x1 - 0.62, LR.z0 + 0.15, 1.08, 0, -0.22);

  // tracks, tight under the ceiling cove
  const rod = (x, y, z, len, ry) => {
    const r = new THREE.Mesh(new THREE.CylinderGeometry(0.011, 0.011, len, 8), M.blackSteel);
    r.position.set(x, y, z); r.rotation.set(0, ry, Math.PI / 2);
    c.add(r);
  };
  rod((LR.x0 + LR.x1) / 2, DROP + 0.035, LR.z0 + 0.15, LR.x1 - LR.x0 - 0.1, 0);

  g.add(c);
  return c;
}

/* ======================================================== log basket === */
function buildLogBasket(g, x, z) {
  const b = new THREE.Group();
  b.position.set(x, 0, z);

  const body = shadowed(new THREE.Mesh(
    new THREE.CylinderGeometry(0.27, 0.23, 0.34, 20, 1, true), M.wicker));
  body.position.y = 0.17; b.add(body);
  const floor_ = new THREE.Mesh(new THREE.CircleGeometry(0.23, 20), M.wicker);
  floor_.rotation.x = -Math.PI / 2; floor_.position.y = 0.012; b.add(floor_);
  const rim = shadowed(new THREE.Mesh(new THREE.TorusGeometry(0.268, 0.016, 6, 24), M.wicker));
  rim.position.y = 0.34; rim.rotation.x = Math.PI / 2; b.add(rim);

  // split logs, stacked in at whatever angle they landed
  const logs = [
    [0.00, 0.26, 0.02, 0.30, 1.25, 0.055],
    [-0.09, 0.30, -0.05, -0.42, 1.05, 0.048],
    [0.08, 0.33, 0.06, 0.62, 1.42, 0.050],
    [-0.02, 0.38, -0.02, 0.10, 1.15, 0.044],
    [0.06, 0.42, -0.08, -0.75, 1.30, 0.041],
  ];
  for (const [lx, ly, lz, ry, rz, r] of logs) {
    const l = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(r, r * 0.92, rrnd(0.46, 0.58), 7), M.bark));
    l.position.set(lx, ly, lz);
    l.rotation.set(rrnd(-0.12, 0.12), ry, rz);
    b.add(l);
  }

  g.add(b);
  return b;
}

/* ============================================================ assemble == */
export function buildProps() {
  const g = new THREE.Group();
  g.name = 'props';

  const rug = buildRug(g);
  const sofa = buildSofa(g);
  const chair = buildArmchair(g, -2.55, 1.30, -X, -0.6, M.boucle, 0);
  const chair2 = buildArmchair(g, 2.95, -0.85, -0.6, 0.3, M.linenDark, 1);
  const table = buildCoffeeTable(g);
  buildPouf(g, -2.45, -1.35);
  /* Light you can see the source of. The room used to be lit by a point
     light with nothing above it: a cluster of glass globes over the seating
     and a torchère by the armchair give the two pools in here something to
     come out of. */
  const lamp = floorLamp(-3.30, 1.90);
  g.add(lamp.group);
  const chand = chandelier(-0.85, 0.30, H - 0.02);
  g.add(chand.group);
  buildSideTable(g, -1.85, 2.52);
  buildSideTable(g, 3.55, -1.75);
  buildBookshelf(g);
  buildCurtains(g);
  buildLogBasket(g, -4.35, 1.05);
  const plant = buildPlant(g, 3.55, -3.05, 1.0);
  const plant2 = buildPlant(g, -3.6, 2.58, 0.62);
  const cat = buildCat(g, -1.95, -0.55);
  buildArt(g);
  const dust = buildDust(g);

  roomScene.add(g);
  return { group: g, rug, sofa, chair, chair2, table, lamp, chand, plant, plant2, cat, dust, M };
}
