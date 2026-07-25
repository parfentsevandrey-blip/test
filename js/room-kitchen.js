/* =========================================================================
   The kitchen and the dining end.

   This is not a room, it is the far half of one open volume: no wall divides
   it from the living room, only the column, the downstand beam and the floor
   turning from smoked oak to honed stone. So everything here has to read from
   the sofa, eight metres away, at night, by firelight — which means the room
   is composed as three masses and almost nothing else. A full-height walnut
   bank at the near end stops the eye where the beam does; a low run sits along
   the one solid wall; the island stands in front of them and takes the light.
   Wall units, the sink and the worktop clutter all live behind the island,
   where nothing is ever the subject of a shot.

   Two hard constraints come off the plan. z = -4 and x = 11 are glass for the
   full height, so nothing tall goes within reach of either — the tall joinery
   is against the bedroom wall and the dining table sits out in the corner with
   the city under it. And the kitchen carries a 0.42 soffit, so its ceiling is
   at 2.88, not 3.3: the tall units and the pendant drops are all set out from
   CEIL, never from APT.h.
   ========================================================================= */
import * as THREE from 'three';
import { rnd, rrnd, roundedBoxGeo, boxUv, faceTowards, normalizeUv } from './room.js';
import { MAT, shadowed } from './room-mat.js';
import { cushionGeo, weltGeo, curtainGeo } from './room-soft.js';
import { ROOMS, APT } from './room-plan.js';

const K = ROOMS.kitchen;
const CEIL = APT.h - K.soffit;              // 2.88 — the soffit, not the slab
const WALL = K.z1 - APT.wall / 2;           // 0.545 — inside face of the bedroom wall

const STONE_TILE = 2.0;                     // stone veins run across the joins
const WOOD_TILE = 1.6;

/* Setting out. Worktops 0.92, tables 0.74, seats 0.45 — every other number in
   this file is measured off one of those three. */
const WORK = 0.92;
const RUN = { x0: 5.50, x1: 10.76, d: 0.62 };
const TALL = { x0: 4.56, x1: 5.50, d: 0.65 };
const WALLU = { x0: 5.50, x1: 8.36, d: 0.35, y0: 1.62 };
const ISL = { x0: 5.05, x1: 7.65, zS: -2.15, zN: -1.15, d: 0.63 };
const TABLE = { x: 9.55, z: -2.55, w: 1.90, d: 0.95, h: 0.74 };

const RUN_F = WALL - RUN.d;                 // -0.075, the front plane of the run
const TALL_F = WALL - TALL.d;               // -0.105
const WALLU_F = WALL - WALLU.d;             //  0.195
const ISL_F = ISL.zN - 0.02;                // -1.17, worktop overhangs the doors

/* The shadow gaps have to read as voids. MAT.cabinet is a lacquer and picks up
   far too much of a raking pendant beam down inside a 4 mm joint, so the gaps
   get their own matt near-black liner — the one place in the room that is not
   allowed to catch anything. */
const GAP = new THREE.MeshStandardMaterial({
  color: 0x131409, roughness: 0.94, metalness: 0, envMapIntensity: 0.12,
});
/* the oven's glass, the hob's glass: dark, hard, and the only high-gloss
   surfaces in the room, which is what makes an appliance read as an appliance */
const APPL_GLASS = new THREE.MeshStandardMaterial({
  color: 0x0c0d0e, roughness: 0.09, metalness: 0.15, envMapIntensity: 1.3,
});
const BOTTLE = new THREE.MeshStandardMaterial({
  color: 0x1c2a1e, roughness: 0.12, metalness: 0, envMapIntensity: 1.1,
});
const FRUIT_A = new THREE.MeshStandardMaterial({ color: 0x8a5a1e, roughness: 0.62 });
const FRUIT_B = new THREE.MeshStandardMaterial({ color: 0x3a3326, roughness: 0.7 });

const UPV = new THREE.Vector3(0, 1, 0);

/* ------------------------------------------------------------- primitives */

/** A box given by its extents, which is how joinery is actually drawn, with
 *  world-scaled UVs so the stone and the walnut match across the joins. */
function slab(g, mat, x0, x1, y0, y1, z0, z1, tile = WOOD_TILE) {
  const w = x1 - x0, h = y1 - y0, d = z1 - z0;
  const c = [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2];
  const m = new THREE.Mesh(
    boxUv(new THREE.BoxGeometry(w, h, d), w, h, d, tile, c), mat);
  m.position.set(c[0], c[1], c[2]);
  g.add(m);
  return m;
}

/** A round strut between two points. Legs splay, so they cannot be boxes. */
function strut(g, mat, a, b, r, seg = 6) {
  const A = new THREE.Vector3(a[0], a[1], a[2]);
  const d = new THREE.Vector3(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
  const m = new THREE.Mesh(new THREE.CylinderGeometry(r, r, d.length(), seg), mat);
  m.position.copy(A).addScaledVector(d, 0.5);
  m.quaternion.setFromUnitVectors(UPV, d.clone().normalize());
  m.castShadow = true;
  g.add(m);
  return m;
}

/* --------------------------------------------------------------- fronts --
   There are no handles anywhere in this flat. Every door and drawer is a slab
   standing 18 mm proud of a dark liner, so the 4 mm joints between fronts and
   the 20 mm recess under the worktop are real gaps you could put your fingers
   in — which is exactly how the grip works on the real thing, and the only
   reason the run reads as joinery rather than as a painted wall. */
const T_FRONT = 0.018;
const DRAWERS2 = [0.42, 0.58];              // a deep pan drawer under a shallow one
const DRAWERS3 = [0.22, 0.37, 0.41];

function frontBank(g, o) {
  const { x0, y0, y1, plane, dir, mat, cabs, grip = 0.020, gap = 0.004 } = o;
  const zc = plane - dir * T_FRONT / 2;
  const hAvail = (y1 - grip) - y0;
  let x = x0;
  for (const c of cabs) {
    const rows = c.rows || [1];
    const cols = c.cols || 1;
    const cw = c.w / cols;
    let y = y0;
    for (const f of rows) {
      const h = hAvail * f;
      for (let i = 0; i < cols; i++) {
        const m = shadowed(new THREE.Mesh(
          roundedBoxGeo(cw - gap, h - gap, T_FRONT, 0.003, 1), mat));
        m.position.set(x + (i + 0.5) * cw, y + h / 2, zc);
        g.add(m);
      }
      y += h;
    }
    x += c.w;
  }
}

/** the dark face the fronts stand off — only ever seen down a 4 mm joint */
function liner(g, x0, x1, y0, y1, plane, dir) {
  const z = plane - dir * T_FRONT;
  return slab(g, GAP, x0, x1, y0, y1,
    Math.min(z, z + dir * 0.010), Math.max(z, z + dir * 0.010));
}

/* ================================================== the run and its wall == */
function buildRun(g) {
  /* Base units. The plinth is set back 100 mm so the whole run reads as
     floating clear of the stone floor — with a flush plinth the joinery and
     the floor merge into one dark mass the moment the pendants come down. */
  slab(g, MAT.cabinet, RUN.x0, RUN.x1 - 0.06, 0, 0.10, RUN_F + 0.10, WALL).receiveShadow = true;
  shadowed(slab(g, MAT.cabinet, RUN.x0, RUN.x1, 0.10, WORK - 0.02, RUN_F + T_FRONT, WALL));
  liner(g, RUN.x0, RUN.x1, 0.10, WORK - 0.02, RUN_F, -1);

  const cabs = [
    { w: 0.60, rows: DRAWERS3 },
    { w: 0.90, rows: DRAWERS2 },            // pan drawers, under the hob
    { w: 0.60 },
    { w: 0.76, rows: DRAWERS3 },
    { w: 0.90, cols: 2 },                   // sink base: doors either side of the trap
    { w: 0.60 },
    { w: 0.90, rows: DRAWERS2 },
  ];
  frontBank(g, {
    x0: RUN.x0, y0: 0.10, y1: WORK - 0.02, plane: RUN_F, dir: -1,
    mat: MAT.cabinet, cabs,
  });

  /* --- worktop, cut round the sink and the hob ---
     Built as seven slabs rather than one, for the same reason the fireplace
     facing is built as four: an aperture you can see the stone edge of beats
     an appliance stuck on top of an unbroken slab. */
  const zf = RUN_F - 0.02, zb = WALL;       // 20 mm overhang past the doors
  const SINK = { x0: 8.49, x1: 9.13, z0: 0.03, z1: 0.45 };
  const HOB = { x0: 6.20, x1: 6.90, z0: 0.06, z1: 0.46 };
  const top = (x0, x1, z0, z1) =>
    shadowed(slab(g, MAT.travertine, x0, x1, WORK - 0.02, WORK, z0, z1, STONE_TILE));
  top(RUN.x0, HOB.x0, zf, zb);
  top(HOB.x0, HOB.x1, zf, HOB.z0);
  top(HOB.x0, HOB.x1, HOB.z1, zb);
  top(HOB.x1, SINK.x0, zf, zb);
  top(SINK.x0, SINK.x1, zf, SINK.z0);
  top(SINK.x0, SINK.x1, SINK.z1, zb);
  top(SINK.x1, RUN.x1 + 0.02, zf, zb);

  /* splashback in the same stone, run to the underside of the wall units. It
     never casts — it is flat against a wall — but it is the surface the strip
     light washes, so it has to receive. */
  slab(g, MAT.travertine, RUN.x0, RUN.x1 + 0.02, WORK, WALLU.y0, WALL - 0.02, WALL,
    STONE_TILE).receiveShadow = true;

  /* --- undermount sink: five steel plates, so the stone edge stays the rim */
  const bw = 0.012, bY = 0.71;
  slab(g, MAT.steel, SINK.x0 - 0.01, SINK.x1 + 0.01, bY, bY + bw, SINK.z0 - 0.01, SINK.z1 + 0.01);
  slab(g, MAT.steel, SINK.x0 - 0.01, SINK.x0, bY, WORK - 0.02, SINK.z0 - 0.01, SINK.z1 + 0.01);
  slab(g, MAT.steel, SINK.x1, SINK.x1 + 0.01, bY, WORK - 0.02, SINK.z0 - 0.01, SINK.z1 + 0.01);
  slab(g, MAT.steel, SINK.x0, SINK.x1, bY, WORK - 0.02, SINK.z0 - 0.01, SINK.z0);
  slab(g, MAT.steel, SINK.x0, SINK.x1, bY, WORK - 0.02, SINK.z1, SINK.z1 + 0.01);
  const drain = new THREE.Mesh(new THREE.CylinderGeometry(0.038, 0.038, 0.006, 14), MAT.steel);
  drain.position.set(8.81, bY + bw + 0.003, 0.24);
  g.add(drain);

  /* --- tap: one bent tube and a lever, nothing else */
  const tx = 8.81, tz = 0.485;
  const body = shadowed(new THREE.Mesh(
    new THREE.CylinderGeometry(0.021, 0.024, 0.30, 12), MAT.steel));
  body.position.set(tx, WORK + 0.15, tz);
  g.add(body);
  const spout = new THREE.CatmullRomCurve3([
    new THREE.Vector3(tx, WORK + 0.28, tz),
    new THREE.Vector3(tx, WORK + 0.35, tz - 0.02),
    new THREE.Vector3(tx, WORK + 0.375, tz - 0.09),
    new THREE.Vector3(tx, WORK + 0.35, tz - 0.155),
    new THREE.Vector3(tx, WORK + 0.26, tz - 0.175),
  ]);
  const sp = shadowed(new THREE.Mesh(new THREE.TubeGeometry(spout, 12, 0.019, 6, false), MAT.steel));
  g.add(sp);
  strut(g, MAT.steel, [tx + 0.02, WORK + 0.26, tz], [tx + 0.075, WORK + 0.30, tz - 0.01], 0.010);

  /* --- hob: four dark discs flush in the stone, and nothing sticking up */
  slab(g, APPL_GLASS, HOB.x0, HOB.x1, WORK - 0.008, WORK, HOB.z0, HOB.z1);
  [[6.37, 0.15], [6.37, 0.37], [6.73, 0.15], [6.73, 0.37]].forEach(([hx, hz], i) => {
    const r = i % 3 === 0 ? 0.085 : 0.072;
    const d = new THREE.Mesh(new THREE.CylinderGeometry(r, r, 0.003, 16),
      new THREE.MeshStandardMaterial({ color: 0x191a1a, roughness: 0.35, metalness: 0.2 }));
    d.position.set(hx, WORK + 0.0015, hz);
    g.add(d);
  });
}

/* ---------------------------------------------------------- wall units == */
function buildWallUnits(g) {
  const y1 = CEIL - 0.02;                   // a shadow gap under the soffit
  shadowed(slab(g, MAT.cabinet, WALLU.x0, WALLU.x1, WALLU.y0, y1,
    WALLU_F + T_FRONT, WALL));
  liner(g, WALLU.x0, WALLU.x1, WALLU.y0, y1, WALLU_F, -1);
  /* The grip on a wall unit is at the bottom, so the fronts start 20 mm up
     and the recess runs the whole length of the units under them. */
  frontBank(g, {
    x0: WALLU.x0, y0: WALLU.y0 + 0.020, y1, plane: WALLU_F, dir: -1,
    mat: MAT.cabinet, grip: 0, cabs: [{ w: WALLU.x1 - WALLU.x0, cols: 4 }],
  });

  /* The extractor is a slot in the underside of the unit above the hob. A
     canopy would be the honest thing, but it would hang straight across the
     one sightline from the sofa to the island and cut the room in half. */
  slab(g, MAT.steel, 6.16, 6.94, WALLU.y0 - 0.002, WALLU.y0 + 0.02, WALLU_F + 0.03, WALL - 0.06);

  /* A single walnut shelf where the wall units stop, so the east end of the
     wall stays open toward the glazing instead of running dark to the corner. */
  const sy = WALLU.y0;
  shadowed(slab(g, MAT.walnut, 8.42, RUN.x1 + 0.02, sy, sy + 0.036, WALL - 0.26, WALL));
  const bowlY = sy + 0.036;
  [[0.052, MAT.clayPale], [0.046, MAT.clay], [0.042, MAT.clayPale]].forEach(([h, m], i) => {
    const b = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.098 - i * 0.006, 0.082, h, 16), m));
    b.position.set(8.86, bowlY + h / 2 + i * 0.046, WALL - 0.14);
    g.add(b);
  });
  const jug = shadowed(new THREE.Mesh(new THREE.LatheGeometry(
    [[0.0, 0], [0.052, 0], [0.062, 0.05], [0.055, 0.14], [0.036, 0.19], [0.040, 0.215], [0.0, 0.218]]
      .map(([r, y]) => new THREE.Vector2(r, y)), 14), MAT.clay));
  jug.position.set(9.34, bowlY, WALL - 0.13);
  g.add(jug);

  /* --- the strip under the wall units --------------------------------- */
  const stripMat = new THREE.MeshBasicMaterial({ color: 0xc07a35, toneMapped: false });
  const strip = new THREE.Mesh(new THREE.BoxGeometry(WALLU.x1 - WALLU.x0 - 0.14, 0.006, 0.022), stripMat);
  strip.position.set((WALLU.x0 + WALLU.x1) / 2, WALLU.y0 - 0.006, WALLU_F + 0.05);
  g.add(strip);
  const stripBase = stripMat.color.clone();
  return {
    lightPos: new THREE.Vector3((WALLU.x0 + WALLU.x1) / 2, WALLU.y0 - 0.05, WALLU_F + 0.14),
    setGlow: (k) => { stripMat.color.copy(stripBase).multiplyScalar(k); },
  };
}

/* --------------------------------------------------- tall units and oven = */
function buildTallUnits(g) {
  const y1 = CEIL - 0.02;
  slab(g, MAT.walnut, TALL.x0 + 0.06, TALL.x1, 0, 0.10, TALL_F + 0.10, WALL).receiveShadow = true;
  /* This carcass is the one that shows: its west return is what the living
     room actually sees of the kitchen, so it is walnut all the way round and
     the dark gap liner is only a facing behind the doors. */
  shadowed(slab(g, MAT.walnut, TALL.x0, TALL.x1, 0.10, y1, TALL_F + T_FRONT, WALL));
  liner(g, TALL.x0, TALL.x1, 0.10, y1, TALL_F, -1);

  const W = TALL.x1 - TALL.x0;
  frontBank(g, {                            // larder base
    x0: TALL.x0, y0: 0.10, y1: 0.88, plane: TALL_F, dir: -1,
    mat: MAT.walnut, cabs: [{ w: W }],
  });
  frontBank(g, {                            // two doors above the oven
    x0: TALL.x0, y0: 1.56, y1, plane: TALL_F, dir: -1,
    mat: MAT.walnut, grip: 0, cabs: [{ w: W, rows: [0.5, 0.5] }],
  });

  /* --- the oven: a dark glass panel in a steel surround, and a rail --- */
  const oy0 = 0.90, oy1 = 1.50;
  shadowed(slab(g, MAT.steel, TALL.x0 + 0.02, TALL.x1 - 0.02, oy0, oy1, TALL_F, TALL_F + 0.08));
  slab(g, APPL_GLASS, TALL.x0 + 0.07, TALL.x1 - 0.07, oy0 + 0.05, oy1 - 0.09,
    TALL_F - 0.002, TALL_F);
  const ry = oy1 - 0.045, rz = TALL_F - 0.042;
  strut(g, MAT.steel, [TALL.x0 + 0.10, ry, rz], [TALL.x1 - 0.10, ry, rz], 0.011, 8);
  [TALL.x0 + 0.11, TALL.x1 - 0.11].forEach((sx) =>
    strut(g, MAT.steel, [sx, ry, TALL_F], [sx, ry, rz], 0.009));

  /* The one hot colour in this room: a rust linen cloth over the oven rail.
     It is small, it is the first thing the firelight reaches coming through
     from the living room, and it is the only soft thing on this side. */
  const clothMat = MAT.rust.clone();
  clothMat.side = THREE.DoubleSide;         // a hung cloth is seen from both faces
  const cloth = new THREE.Mesh(curtainGeo(0.25, 0.30, {
    nu: 12, nv: 8, folds: 3, depth: 0.011, lead: 0.02, pool: 0,
  }), clothMat);
  cloth.position.set((TALL.x0 + TALL.x1) / 2 + 0.06, ry - 0.30, rz - 0.014);
  cloth.receiveShadow = true;
  g.add(cloth);
}

/* ================================================================ island = */
function buildIsland(g) {
  /* The island is joinery, not a run of units, so it is wrapped in walnut on
     the two ends and the leg side and only wears cabinet fronts on the face
     the cook stands at. From the living room you see a solid walnut block; the
     working half never shows. */
  shadowed(slab(g, MAT.walnut, ISL.x0 + 0.02, ISL.x1 - 0.02, 0.10, WORK - 0.04,
    ISL.zS + 0.35, ISL_F - T_FRONT));
  // the plinth is set back on all four sides here, so the block floats
  slab(g, MAT.walnut, ISL.x0 + 0.07, ISL.x1 - 0.07, 0, 0.10,
    ISL.zS + 0.40, ISL_F - 0.06).receiveShadow = true;
  liner(g, ISL.x0 + 0.02, ISL.x1 - 0.02, 0.10, WORK - 0.04, ISL_F, 1);
  frontBank(g, {
    x0: ISL.x0 + 0.02, y0: 0.10, y1: WORK - 0.04, plane: ISL_F, dir: 1,
    mat: MAT.cabinet,
    cabs: [{ w: 0.86, rows: DRAWERS2 }, { w: 0.84 }, { w: 0.86, rows: DRAWERS2 }],
  });

  /* 40 mm rather than the run's 20: this slab cantilevers 350 mm over the
     stools and a thin edge there reads as laminate. */
  shadowed(slab(g, MAT.travertine, ISL.x0, ISL.x1, WORK - 0.04, WORK, ISL.zS, ISL.zN,
    STONE_TILE));
}

/* ------------------------------------------------------------ bar stool == */
function buildStool(g, x, z, spin) {
  const s = new THREE.Group();
  s.position.set(x, 0, z);
  const SH = 0.64;                          // 280 below the worktop, which is right
  const legs = [[-1, -1], [1, -1], [-1, 1], [1, 1]];
  legs.forEach(([sx, sz]) => {
    strut(s, MAT.steel, [sx * 0.185, 0, sz * 0.185], [sx * 0.125, SH - 0.025, sz * 0.125], 0.014);
  });
  // one footrail ring, at the height a foot actually finds it
  const fy = 0.22, fr = 0.185 - (0.06 * fy) / (SH - 0.025);
  [[-1, -1, 1, -1], [1, -1, 1, 1], [1, 1, -1, 1], [-1, 1, -1, -1]].forEach(([ax, az, bx, bz]) => {
    strut(s, MAT.steel, [ax * fr, fy, az * fr], [bx * fr, fy, bz * fr], 0.010);
  });
  const seat = shadowed(new THREE.Mesh(
    new THREE.CylinderGeometry(0.185, 0.172, 0.045, 20), MAT.walnut));
  seat.position.y = SH - 0.0225;
  s.add(seat);
  s.rotation.y = spin;
  g.add(s);
  return s;
}

/* ========================================================== dining table = */
function buildTable(g) {
  const t = new THREE.Group();
  t.position.set(TABLE.x, 0, TABLE.z);
  const top = shadowed(new THREE.Mesh(
    roundedBoxGeo(TABLE.w, 0.045, TABLE.d, 0.008, 1), MAT.walnut));
  top.position.y = TABLE.h - 0.0225;
  t.add(top);
  // blade legs in blackened steel, the same detail as the coffee table next door
  [-1, 1].forEach((sx) => {
    const l = shadowed(new THREE.Mesh(
      roundedBoxGeo(0.05, TABLE.h - 0.045, 0.62, 0.01, 1), MAT.steel));
    l.position.set(sx * 0.62, (TABLE.h - 0.045) / 2, 0);
    t.add(l);
  });
  const str = new THREE.Mesh(new THREE.BoxGeometry(1.24, 0.035, 0.035), MAT.steel);
  str.position.y = 0.19; str.castShadow = true;
  t.add(str);
  g.add(t);
  return t;
}

/* ------------------------------------------------------------ chair ----- */
/* Built looking down its own +z, so faceTowards() aims the seat at the table.
   The seat is a real stuffed cushion with a welt sewn into the seam — a
   rounded box at this size reads as a plastic garden chair immediately. */
function buildChair(g, x, z, seed) {
  const c = new THREE.Group();
  c.position.set(x, 0, z);
  const SEAT = 0.385;                       // frame top; the cushion adds 75 mm

  [[-1, 1], [1, 1]].forEach(([sx]) => {     // front legs
    strut(c, MAT.walnut, [sx * 0.19, 0, 0.185], [sx * 0.175, SEAT, 0.165], 0.019);
  });
  [-1, 1].forEach((sx) => {                 // rear legs run on up as the back
    strut(c, MAT.walnut, [sx * 0.19, 0, -0.185], [sx * 0.172, 0.855, -0.238], 0.019);
  });
  const frame = new THREE.Mesh(new THREE.BoxGeometry(0.42, 0.03, 0.40), MAT.walnut);
  frame.position.y = SEAT - 0.015; frame.castShadow = true;
  c.add(frame);
  strut(c, MAT.walnut, [-0.171, 0.828, -0.234], [0.171, 0.828, -0.234], 0.016, 8);

  const cus = shadowed(new THREE.Mesh(cushionGeo(0.44, 0.075, 0.42, {
    corner: 4.6, wide: 5.0, edge: 0.26, wrinkle: 0.9,
    sag: 0.008 + seed * 0.004, seg: 22, rings: 9,
  }), MAT.olive));
  cus.position.set(0, SEAT + 0.0375, 0.005);
  cus.rotation.y = seed * 0.02;
  c.add(cus);
  const welt = new THREE.Mesh(weltGeo(0.435, 0.415, { corner: 4.6, radius: 0.0075, seg: 22 }), MAT.olive);
  welt.castShadow = false;
  cus.add(welt);

  /* The pad leans back: rotation.x a touch under π/2 tips the cushion's far
     end up and away, which is the difference between a chair and a bench. */
  const pad = shadowed(new THREE.Mesh(cushionGeo(0.40, 0.085, 0.30, {
    corner: 3.6, wide: 4.0, edge: 0.32, wrinkle: 1.2, pinch: 0.05, seg: 20, rings: 9,
  }), MAT.olive));
  pad.position.set(0, 0.655, -0.206);
  pad.rotation.x = Math.PI / 2 - 0.14;
  c.add(pad);

  g.add(c);
  return c;
}

/* ============================================================= pendants == */
/* Three of them, on the island's centre line. The shade is small and dark on
   purpose: what should be visible from the living room is the pool of light on
   the stone, not three bright objects hanging in the middle of the volume. */
function buildPendant(g, x, z) {
  const p = new THREE.Group();
  p.position.set(x, 0, z);
  const bot = 1.62, top = bot + 0.22;       // 700 above the worktop

  const canopy = new THREE.Mesh(new THREE.CylinderGeometry(0.032, 0.032, 0.012, 12), MAT.steel);
  canopy.position.y = CEIL - 0.006;
  p.add(canopy);
  const cord = new THREE.Mesh(
    new THREE.CylinderGeometry(0.005, 0.005, CEIL - top, 6), MAT.steel);
  cord.position.y = (CEIL + top) / 2;       // a 10 mm cord casting a shadow is noise
  p.add(cord);

  const shade = shadowed(new THREE.Mesh(
    new THREE.CylinderGeometry(0.055, 0.115, 0.22, 16, 1, true), MAT.steel));
  shade.position.y = (bot + top) / 2;
  p.add(shade);
  /* The whole flat gets one brass accent per room and this is the kitchen's:
     a 12 mm band at the lip of each shade. It is the only warm metal in here,
     it is 30 mm of it, and it sits exactly where the light leaves the shade. */
  const band = new THREE.Mesh(
    new THREE.CylinderGeometry(0.1165, 0.1165, 0.014, 16, 1, true), MAT.brass);
  band.position.y = bot + 0.007;
  p.add(band);

  const innerMat = new THREE.MeshBasicMaterial({ color: 0xb26a2c, toneMapped: false });
  const inner = new THREE.Mesh(new THREE.CircleGeometry(0.108, 16), innerMat);
  inner.rotation.x = Math.PI / 2; inner.position.y = bot + 0.012;
  p.add(inner);
  const bulbMat = new THREE.MeshBasicMaterial({ color: 0xffc987, toneMapped: false });
  const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.026, 10, 8), bulbMat);
  bulb.position.y = bot + 0.055;
  p.add(bulb);

  // fade the emitters with the lamp level instead of popping them off
  const iBase = innerMat.color.clone(), bBase = bulbMat.color.clone();
  g.add(p);
  return {
    group: p,
    lightPos: new THREE.Vector3(x, bot - 0.03, z),
    setGlow: (k) => {
      innerMat.color.copy(iBase).multiplyScalar(k);
      bulbMat.color.copy(bBase).multiplyScalar(k);
    },
  };
}

/* ========================================================= island props = */
function buildIslandProps(g) {
  const y = WORK;

  /* board and knife, left where someone was working */
  const board = shadowed(new THREE.Mesh(roundedBoxGeo(0.38, 0.026, 0.26, 0.008, 1), MAT.walnut));
  board.position.set(5.74, y + 0.013, -1.60);
  board.rotation.y = 0.13;
  g.add(board);
  const blade = new THREE.Mesh(roundedBoxGeo(0.17, 0.003, 0.030, 0.001, 1), MAT.chrome);
  blade.position.set(5.72, y + 0.028, -1.64);
  blade.rotation.y = 0.42;
  g.add(blade);
  // the handle has to lie on the blade's own line, so it carries the blade's
  // plan angle as well as the roll that lays the cylinder down
  const grip = new THREE.Mesh(new THREE.CylinderGeometry(0.013, 0.011, 0.10, 8), MAT.walnut);
  grip.position.set(5.845, y + 0.038, -1.695);
  grip.rotation.set(0, 0.42, Math.PI / 2);
  grip.castShadow = true;
  g.add(grip);

  /* a shallow stoneware bowl — turned as a closed shell so the inside is
     really there when you look down into it from the walker's eye height */
  const bowl = shadowed(new THREE.Mesh(new THREE.LatheGeometry(
    [[0.05, 0], [0.135, 0.045], [0.163, 0.082], [0.166, 0.090],
     [0.150, 0.086], [0.124, 0.050], [0.044, 0.012], [0, 0.012]]
      .map(([r, h]) => new THREE.Vector2(r, h)), 16), MAT.clayPale));
  bowl.position.set(6.46, y, -1.56);
  g.add(bowl);
  const fruit = [[-0.05, -0.03, 0.052], [0.045, -0.05, 0.048], [0.0, 0.045, 0.05], [0.02, -0.005, 0.046]];
  fruit.forEach(([dx, dz, r], i) => {
    const f = shadowed(new THREE.Mesh(new THREE.SphereGeometry(r, 10, 7),
      i % 2 ? FRUIT_B : FRUIT_A));
    f.position.set(6.46 + dx, y + 0.06 + rrnd(0, 0.012), -1.56 + dz);
    f.rotation.set(rrnd(0, 1), rrnd(0, 1), rrnd(0, 1));
    g.add(f);
  });

  /* a bottle and two glasses, at the end where the stools are */
  const bot = shadowed(new THREE.Mesh(new THREE.LatheGeometry(
    [[0, 0], [0.038, 0], [0.040, 0.02], [0.040, 0.17], [0.030, 0.212],
     [0.0135, 0.245], [0.013, 0.30], [0.017, 0.306], [0, 0.308]]
      .map(([r, h]) => new THREE.Vector2(r, h)), 14), BOTTLE));
  bot.position.set(7.21, y, -1.70);
  g.add(bot);
  [[7.02, -1.88, 0.0], [7.26, -1.94, 0.4]].forEach(([gx, gz, spin]) => {
    const cup = new THREE.Mesh(
      new THREE.CylinderGeometry(0.033, 0.026, 0.105, 14, 1, true), MAT.glassware);
    cup.position.set(gx, y + 0.053, gz);
    cup.rotation.y = spin;
    cup.castShadow = true;
    g.add(cup);
    const base = new THREE.Mesh(new THREE.CircleGeometry(0.026, 14), MAT.glassware);
    base.rotation.x = -Math.PI / 2;
    base.position.set(gx, y + 0.002, gz);
    g.add(base);
  });
}

/* ================================================================ plant == */
/* One plant, in the corner where the walnut bank meets the threshold from the
   living room — the only place in the kitchen with no glass behind it and no
   worktop in front of it. */
function buildPlant(g, x, z) {
  const p = new THREE.Group();
  p.position.set(x, 0, z);

  const pot = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.175, 0.135, 0.38, 16), MAT.clay));
  pot.position.y = 0.19;
  p.add(pot);
  const soil = new THREE.Mesh(new THREE.CircleGeometry(0.163, 14), MAT.bark);
  soil.rotation.x = -Math.PI / 2; soil.position.y = 0.373;
  p.add(soil);

  const leafGeo = (() => {
    const s = new THREE.Shape();
    s.moveTo(0, 0);
    s.bezierCurveTo(0.075, 0.07, 0.088, 0.23, 0, 0.34);
    s.bezierCurveTo(-0.088, 0.23, -0.075, 0.07, 0, 0);
    return normalizeUv(new THREE.ShapeGeometry(s, 8));
  })();

  const stems = 4;
  for (let i = 0; i < stems; i++) {
    const a = (i / stems) * Math.PI * 2 + rnd();
    const lean = rrnd(0.10, 0.26);
    const hgt = rrnd(0.52, 0.82);
    const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.009, 0.014, hgt, 6), MAT.leaf);
    stem.position.set(Math.cos(a) * 0.045, 0.37 + hgt / 2, Math.sin(a) * 0.045);
    stem.rotation.z = -Math.cos(a) * lean;
    stem.rotation.x = Math.sin(a) * lean;
    stem.castShadow = true;
    p.add(stem);

    const n = 3 + ((rnd() * 2) | 0);
    for (let k = 0; k < n; k++) {
      const t = 0.42 + (k / n) * 0.62;
      const lf = new THREE.Mesh(leafGeo, MAT.leaf);
      lf.scale.setScalar(rrnd(0.7, 1.15));
      lf.position.set(
        Math.cos(a) * (0.045 + lean * hgt * t * 0.9),
        0.37 + hgt * t,
        Math.sin(a) * (0.045 + lean * hgt * t * 0.9),
      );
      lf.rotation.set(rrnd(-0.5, 0.2), a + rrnd(-1.3, 1.3), rrnd(-0.6, 0.6));
      lf.castShadow = true;
      lf.userData.sway = { base: lf.rotation.z, amp: rrnd(0.01, 0.03), ph: rnd() * 6.28 };
      p.add(lf);
    }
  }
  g.add(p);
  return p;
}

/* ============================================================= assemble == */
export function buildKitchen() {
  const g = new THREE.Group();
  g.name = 'kitchen';

  buildRun(g);
  const strip = buildWallUnits(g);
  buildTallUnits(g);
  buildIsland(g);
  buildIslandProps(g);

  /* Three stools tucked into the 350 overhang, facing the run — sit here and
     you are looking at whoever is cooking, which is the whole point of an
     island. The middle one has been pushed out and left turned. */
  const isC = (ISL.x0 + ISL.x1) / 2;
  buildStool(g, isC - 0.70, ISL.zS - 0.15, 0.06);
  buildStool(g, isC, ISL.zS - 0.20, -0.34);
  buildStool(g, isC + 0.70, ISL.zS - 0.15, 0.11);

  buildTable(g);
  /* Chairs square to the long sides, not aimed at the table's centre, then
     nudged: four chairs at identical angles is the tell of a showroom. */
  const seats = [[9.10, -1.80], [10.00, -1.80], [9.10, -3.30], [10.00, -3.30]];
  seats.forEach(([cx, cz], i) => {
    const c = buildChair(g, cx, cz, i);
    faceTowards(c, cx, TABLE.z);
    c.rotation.y += rrnd(-0.07, 0.07);
    c.position.x += rrnd(-0.02, 0.02);
    c.position.z += rrnd(-0.03, 0.03);
  });

  const pendants = [
    buildPendant(g, isC - 0.70, (ISL.zS + ISL.zN) / 2),
    buildPendant(g, isC, (ISL.zS + ISL.zN) / 2),
    buildPendant(g, isC + 0.70, (ISL.zS + ISL.zN) / 2),
    /* The strip under the wall units is a practical like any other and wants
       to dim with them, and the only thing the app asks of one of these is a
       position and a dimmer — so it rides in the same list. */
    strip,
  ];

  buildPlant(g, 4.38, 0.16);

  return { group: g, pendants };
}

/* ----------------------------------------------------------- collision ---
   Only the four masses. The island box is stretched 400 south of the worktop
   so that it swallows the stools: walking through a bar stool is worse than
   the island feeling 400 deeper than it looks. */
export const KITCHEN_COLLIDERS = [
  [ISL.x0 - 0.02, 0, ISL.zS - 0.40, ISL.x1 + 0.02, WORK, ISL.zN + 0.02],
  [RUN.x0 - 0.02, 0, RUN_F - 0.04, RUN.x1 + 0.04, WORK, WALL],
  [TALL.x0 - 0.02, 0, TALL_F - 0.04, TALL.x1 + 0.02, CEIL, WALL],
  [TABLE.x - TABLE.w / 2 - 0.02, 0, TABLE.z - TABLE.d / 2 - 0.02,
   TABLE.x + TABLE.w / 2 + 0.02, TABLE.h, TABLE.z + TABLE.d / 2 + 0.02],
];
