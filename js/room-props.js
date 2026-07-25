/* =========================================================================
   Part 4 / 5 — furniture, soft goods, plants, the cat, and the lighting rig
   ========================================================================= */
import * as THREE from 'three';
import {
  GLSL_NOISE, U, ROOM, rnd, rrnd, pick, roomScene, MAX_ANISO,
  roundedBoxGeo, faceTowards, normalizeUv,
} from './room.js';
import { applyMaps } from './tex/index.js';
import { applyDetail } from './tex/detail.js';
export { buildLights, updateLights } from './room-lights.js';

const X = ROOM.x, Z = ROOM.z, H = ROOM.h;

/* ------------------------------------------------------------ materials --
   roundedBoxGeo() is an ExtrudeGeometry, whose UVs are already in metres, so
   a repeat of 4 means "one texture tile every 25 cm". Box/cylinder primitives
   carry 0..1 UVs instead, so a few materials get their own repeat. */
const AN = { aniso: MAX_ANISO };
const tex = (mat, name, opts) => {
  applyMaps(mat, name, { ...AN, ...opts });
  if (opts && opts.detail) applyDetail(mat, opts.detail);
  return mat;
};
// fabric wants a fine fibre break-up; wood and stone a coarser, shallower one
const D_FABRIC = { scale: 0.035, strength: 0.55, fade: 3.5, rough: 0.05 };
const D_WOOD = { scale: 0.07, strength: 0.40, fade: 4.5, rough: 0.04 };

const M = {
  linen: tex(new THREE.MeshStandardMaterial({
    color: 0xd2bfa6, metalness: 0, envMapIntensity: 0.32,
  }), 'linen', { repeat: [4, 4], normalScale: 0.85, detail: D_FABRIC }),

  linenDark: tex(new THREE.MeshStandardMaterial({
    color: 0x9c8971, metalness: 0, envMapIntensity: 0.28,
  }), 'linen', { repeat: [4, 4], normalScale: 0.85, detail: D_FABRIC }),

  boucle: tex(new THREE.MeshStandardMaterial({
    color: 0xe8dac2, metalness: 0, envMapIntensity: 0.28,
  }), 'boucle', { repeat: [4, 4], normalScale: 0.9, detail: D_FABRIC }),

  boucleRound: tex(new THREE.MeshStandardMaterial({
    color: 0xe8dac2, metalness: 0, envMapIntensity: 0.28,
  }), 'boucle', { repeat: [9, 1.6], normalScale: 0.9 }),

  rust: tex(new THREE.MeshStandardMaterial({
    color: 0xc4653c, metalness: 0, envMapIntensity: 0.32,
  }), 'linen', { repeat: [4, 4], normalScale: 0.85, detail: D_FABRIC }),

  knit: tex(new THREE.MeshStandardMaterial({
    color: 0xd4ac79, metalness: 0, side: THREE.DoubleSide, envMapIntensity: 0.22,
  }), 'knit', { repeat: [3, 5], normalScale: 1.0, detail: D_FABRIC }),

  oak: tex(new THREE.MeshStandardMaterial({
    color: 0x9a7752, metalness: 0, envMapIntensity: 0.5,
  }), 'oakFloor', { repeat: [0.4, 0.4], normalScale: 0.5, detail: D_WOOD }),

  oakDark: tex(new THREE.MeshStandardMaterial({
    color: 0x5c412c, metalness: 0, envMapIntensity: 0.5,
  }), 'oakFloor', { repeat: [0.4, 0.4], normalScale: 0.5, detail: D_WOOD }),

  brass: tex(new THREE.MeshStandardMaterial({
    color: 0xc59a55, metalness: 0.95, envMapIntensity: 1.2,
  }), 'brushedMetal', { repeat: [3, 3], normalScale: 0.6 }),

  blackSteel: tex(new THREE.MeshStandardMaterial({
    color: 0x26221f, metalness: 0.85, envMapIntensity: 0.8,
  }), 'brushedMetal', { repeat: [3, 3], normalScale: 0.5 }),

  marble: tex(new THREE.MeshStandardMaterial({
    color: 0xa5a5a1, metalness: 0.05, envMapIntensity: 1.0,
  }), 'marble', { repeat: [1.5, 1.5], normalScale: 0.5 }),

  bookCloth: tex(new THREE.MeshStandardMaterial({
    color: 0xffffff, metalness: 0, envMapIntensity: 0.3,
  }), 'bookCloth', { repeat: [8, 8], normalScale: 0.8 }),

  ceramic: new THREE.MeshStandardMaterial({ color: 0xc9bda9, roughness: 0.35, envMapIntensity: 0.8 }),

  leaf: tex(new THREE.MeshStandardMaterial({
    color: 0xffffff, metalness: 0, side: THREE.DoubleSide, envMapIntensity: 0.4,
  }), 'leaf', { repeat: [1, 1], normalScale: 0.8 }),

  fur: tex(new THREE.MeshStandardMaterial({
    color: 0x6d635c, metalness: 0, envMapIntensity: 0.2,
  }), 'woolRug', { repeat: [5, 5], normalScale: 0.7 }),
};

const shadowed = (m) => { m.castShadow = true; m.receiveShadow = true; return m; };

/* every book shares one cloth texture and differs only by tint */
const _bookMats = new Map();
const bookMat = (hex) => {
  if (!_bookMats.has(hex)) {
    _bookMats.set(hex, tex(new THREE.MeshStandardMaterial({
      color: hex, metalness: 0, envMapIntensity: 0.25,
    }), 'bookCloth', { repeat: [8, 8], normalScale: 0.8 }));
  }
  return _bookMats.get(hex);
};

/* ================================================================= rug === */
function buildRug(g) {
  const mat = tex(new THREE.MeshStandardMaterial({
    color: 0xe4cfb2, metalness: 0, envMapIntensity: 0.28,
  }), 'woolRug', { repeat: [2.2, 2.2], normalScale: 0.75 });
  const rug = new THREE.Mesh(roundedBoxGeo(4.6, 0.022, 3.4, 0.05, 1), mat);
  rug.position.set(-0.5, 0.011, 0.35);
  rug.receiveShadow = true;
  g.add(rug);
  return rug;
}

/* ================================================================ sofa === */
function buildSofa(g) {
  const s = new THREE.Group();
  s.position.set(0.15, 0, 1.95);          // back to the room, facing the window

  const W = 2.95, D = 1.02, SEAT = 0.40;
  // plinth
  const plinth = shadowed(new THREE.Mesh(roundedBoxGeo(W, 0.12, D, 0.03), M.oakDark));
  plinth.position.y = 0.06; s.add(plinth);
  // seat cushions
  for (let i = -1; i <= 1; i++) {
    const c = shadowed(new THREE.Mesh(roundedBoxGeo(W / 3 - 0.03, 0.20, D - 0.16, 0.055), M.linen));
    c.position.set(i * (W / 3), SEAT - 0.10 + 0.12, 0.02);
    s.add(c);
  }
  // back cushions, tilted
  for (let i = -1; i <= 1; i++) {
    const c = shadowed(new THREE.Mesh(roundedBoxGeo(W / 3 - 0.04, 0.44, 0.20, 0.06), M.linen));
    c.position.set(i * (W / 3), 0.74, D / 2 - 0.14);
    c.rotation.x = -0.16;
    s.add(c);
  }
  // arms
  [-1, 1].forEach((sgn) => {
    const a = shadowed(new THREE.Mesh(roundedBoxGeo(0.20, 0.40, D, 0.07), M.linenDark));
    a.position.set(sgn * (W / 2 - 0.10), 0.36, 0);
    s.add(a);
  });
  // back panel
  const bp = shadowed(new THREE.Mesh(roundedBoxGeo(W, 0.62, 0.14, 0.05), M.linenDark));
  bp.position.set(0, 0.49, D / 2 - 0.04);
  s.add(bp);

  // throw pillows
  const pillow = (x, z, rot, mat, sz = 0.42) => {
    const p = shadowed(new THREE.Mesh(roundedBoxGeo(sz, sz, 0.15, 0.07, 4), mat));
    p.position.set(x, SEAT + 0.30, z);
    p.rotation.set(-0.42, rot, rot * 0.5);
    s.add(p);
  };
  pillow(-1.02, 0.24, 0.22, M.rust, 0.44);
  pillow(-0.66, 0.30, -0.14, M.boucle, 0.38);
  pillow(1.00, 0.26, -0.26, M.boucle, 0.42);
  pillow(0.68, 0.32, 0.18, M.rust, 0.36);

  // knitted throw draped over the left arm
  const throwGeo = new THREE.PlaneGeometry(0.75, 1.25, 12, 18);
  const tp = throwGeo.attributes.position;
  for (let i = 0; i < tp.count; i++) {
    const u = tp.getX(i) / 0.75 + 0.5, v = tp.getY(i) / 1.25 + 0.5;
    tp.setZ(i, Math.sin(u * Math.PI * 3.2) * 0.028 + Math.sin(v * Math.PI * 2.1 + 1.0) * 0.02);
  }
  throwGeo.computeVertexNormals();
  const thr = shadowed(new THREE.Mesh(throwGeo, M.knit));
  thr.position.set(-1.26, 0.44, -0.06);
  thr.rotation.set(-Math.PI / 2 + 0.35, 0, 0.06);
  s.add(thr);

  g.add(s);
  return s;
}

/* ============================================================ armchair === */
function buildArmchair(g, x, z, lookX, lookZ, mat) {
  const c = new THREE.Group();
  c.position.set(x, 0, z);

  const W = 0.86, D = 0.84;
  const cPlinth = shadowed(new THREE.Mesh(roundedBoxGeo(W, 0.10, D, 0.03), M.oakDark));
  cPlinth.position.y = 0.05; c.add(cPlinth);
  const seat = shadowed(new THREE.Mesh(roundedBoxGeo(W - 0.06, 0.20, D - 0.10, 0.07), mat));
  seat.position.set(0, 0.30, 0.02); c.add(seat);
  const back = shadowed(new THREE.Mesh(roundedBoxGeo(W - 0.04, 0.58, 0.19, 0.08), mat));
  back.position.set(0, 0.66, D / 2 - 0.11); back.rotation.x = -0.19; c.add(back);
  [-1, 1].forEach((s) => {
    const a = shadowed(new THREE.Mesh(roundedBoxGeo(0.16, 0.32, D - 0.10, 0.07), mat));
    a.position.set(s * (W / 2 - 0.07), 0.42, 0.0); c.add(a);
  });
  // tapered legs
  [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(([sx, sz]) => {
    const l = new THREE.Mesh(new THREE.CylinderGeometry(0.018, 0.012, 0.10, 6), M.oakDark);
    l.position.set(sx * (W / 2 - 0.09), 0.05, sz * (D / 2 - 0.09));
    l.castShadow = true; c.add(l);
  });
  const p = shadowed(new THREE.Mesh(roundedBoxGeo(0.34, 0.34, 0.13, 0.06, 4), M.rust));
  p.position.set(0.06, 0.55, 0.16); p.rotation.set(-0.5, 0.2, 0.1); c.add(p);

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
  const p = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.34, 0.36, 0.36, 24, 1), M.boucleRound));
  p.position.set(x, 0.18, z);
  g.add(p);
  const seam = new THREE.Mesh(new THREE.TorusGeometry(0.345, 0.012, 8, 28), M.boucleRound);
  seam.position.set(x, 0.30, z); seam.rotation.x = Math.PI / 2;
  seam.castShadow = true; g.add(seam);
  return p;
}

/* ========================================================= floor lamp === */
function buildFloorLamp(g, x, z) {
  const l = new THREE.Group();
  l.position.set(x, 0, z);
  const base = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.18, 0.028, 24), M.blackSteel));
  base.position.y = 0.014; l.add(base);
  const stem = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.014, 0.016, 1.42, 12), M.brass));
  stem.position.y = 0.72; l.add(stem);

  const shadeMat = new THREE.MeshStandardMaterial({
    color: 0xe8d3b0, roughness: 0.9, side: THREE.DoubleSide,
    transparent: true, opacity: 0.94, envMapIntensity: 0.4,
    emissive: 0xff9d4e, emissiveIntensity: 0.30,
  });
  const shade = new THREE.Mesh(new THREE.CylinderGeometry(0.19, 0.235, 0.26, 28, 1, true), shadeMat);
  shade.position.y = 1.50; l.add(shade);
  const innerMat = new THREE.MeshBasicMaterial({ color: 0xb9702f, toneMapped: false });
  const inner = new THREE.Mesh(new THREE.CircleGeometry(0.225, 24), innerMat);
  inner.rotation.x = Math.PI / 2; inner.position.y = 1.372; l.add(inner);
  const bulbMat = new THREE.MeshBasicMaterial({ color: 0xffcc8a, toneMapped: false });
  const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.032, 12, 10), bulbMat);
  bulb.position.y = 1.48; l.add(bulb);

  // fade the emitters with the light rather than popping them on/off
  const innerBase = innerMat.color.clone(), bulbBase = bulbMat.color.clone();
  const setGlow = (k) => {
    innerMat.color.copy(innerBase).multiplyScalar(k);
    bulbMat.color.copy(bulbBase).multiplyScalar(k);
  };

  g.add(l);
  return { group: l, shadeMat, inner, bulb, setGlow, lightPos: new THREE.Vector3(x, 1.44, z) };
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
  s.position.set(X - 0.17, 0, 1.9);

  const W = 0.34, HH = 2.35, L = 2.9;
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
      new THREE.MeshBasicMaterial({ color: 0x9a5a28, toneMapped: false }));
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
  vase.position.set(-0.02, 1.752, -1.0); s.add(vase);
  const bowl = shadowed(new THREE.Mesh(new THREE.SphereGeometry(0.075, 16, 10, 0, Math.PI * 2, Math.PI * 0.5, Math.PI * 0.5), M.brass));
  bowl.position.set(-0.02, 0.955, 0.95); s.add(bowl);

  s.rotation.y = -Math.PI / 2;
  s.position.set(X - 0.17, 0, 1.9);
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
  art.position.set(-0.9, 1.72, Z - 0.06);
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
    pos[i * 3] = rrnd(-X + 0.4, X - 0.4);
    pos[i * 3 + 1] = rrnd(0.15, H - 0.35);
    pos[i * 3 + 2] = rrnd(-Z + 0.4, Z - 0.4);
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

/* ============================================================ assemble == */
export function buildProps() {
  const g = new THREE.Group();
  g.name = 'props';

  const rug = buildRug(g);
  const sofa = buildSofa(g);
  const chair = buildArmchair(g, -2.55, 1.30, -X, -0.6, M.boucle);
  const chair2 = buildArmchair(g, 2.95, -0.85, -0.6, 0.3, M.linenDark);
  const table = buildCoffeeTable(g);
  buildPouf(g, -2.45, -1.35);
  const lamp = buildFloorLamp(g, -3.30, 1.90);
  buildSideTable(g, -1.85, 2.70);
  buildBookshelf(g);
  const plant = buildPlant(g, 3.85, -2.95, 1.0);
  const plant2 = buildPlant(g, -3.6, 2.9, 0.62);
  const cat = buildCat(g, -1.95, -0.55);
  buildArt(g);
  const dust = buildDust(g);

  roomScene.add(g);
  return { group: g, rug, sofa, chair, chair2, table, lamp, plant, plant2, cat, dust, M };
}
