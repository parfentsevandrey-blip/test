/* =========================================================================
   The hall.

   Nine metres by three and a half, and not one window in it: this is the
   internal spine of the flat, the piece you cross rather than the piece you
   sit in. Two things follow from that. It is the darkest room in the
   apartment — a pair of sconces either side of a mirror, and whatever leaks
   round the cased opening from the fire — and everything in it is either flat
   against a wall or hanging on one, because the middle has to stay walkable.
   The bedroom door lands at x = 4, the opening to the living room at x = 2.1,
   and the line between them is the busiest metre of floor in the flat.

   So the room is composed as one long wall and one long runner. A floating
   walnut console with a dark mirror over it takes the north wall, two closed
   leaves either side of it stand for the front door and the bathroom, two
   tall canvases face the mirror across the hall, and the coat and the bag by
   the entrance are the only soft things in here. Nothing is bright, nothing
   is in the way, and the eye is carried straight through — which is what a
   threshold between a lit living room and a sleeping bedroom has to do.

   Everything is set out from the plan rather than typed in, so the room
   cannot drift away from the walls it is built against. The hall carries the
   same 0.42 soffit the kitchen does, which puts its ceiling at 2.88 — nothing
   in here reaches past the door heads at 2.33, so the drop is only ever felt
   rather than seen.
   ========================================================================= */
import * as THREE from 'three';
import { rnd, rrnd, roundedBoxGeo, boxUv } from './room.js';
import { MAT, shadowed } from './room-mat.js';
import { cushionGeo, weltGeo, drapeGeo } from './room-soft.js';
import { ROOMS, APT } from './room-plan.js';
import { sconce } from './room-lamps.js';

const R = ROOMS.hall;
const NORTH = R.z1 - APT.wall / 2;      // 6.545 — inside face of the entrance wall
const SOUTH = R.z0 + APT.wall / 2;      // 3.055 — the living-room wall, the long one
const WEST = R.x0 + APT.wall / 2;       // -4.945 — the blind end, where the coats go
const WOOD_TILE = 1.6;                  // the shell's doors are grained at this scale

/* Setting out. A console top is not a worktop: 0.85 is where a hall table
   lands, low enough that the mirror over it still has a metre of wall. */
const CON = { x0: -3.40, x1: -0.60, top: 0.85, h: 0.27, d: 0.36 };
const CX = (CON.x0 + CON.x1) / 2;       // -2.0, and with it the mirror and the pier
const CON_F = NORTH - CON.d;            // 6.185 — the front plane of the console

const MIR = { w: 1.44, h: 1.10, y: 1.57 };
const SC_Y = 1.58, SC_DX = 0.90;        // sconces level with the middle of the glass
const DOOR_H = 2.30;
const HOOK_Y = 1.72;

/* Openings that have to stay clear, restated from the plan so the numbers in
   this file can be read against them: the cased opening to the living room
   runs x 1.54 → 2.66 in the z = 3 wall, the bedroom door z 3.74 → 4.76 in the
   x = 4 wall. Nothing here goes east of x = 0.96 except the runner. */

/* A single sheet of cloth shows its reverse wherever it folds back on itself,
   so the coat and the scarf take doubled clones of the palette materials
   rather than a second, subtly different fabric. */
const twoSided = (m) => { const c = m.clone(); c.side = THREE.DoubleSide; return c; };
const COAT = twoSided(MAT.olive);
const SCARF = twoSided(MAT.rust);       // the hall's one warm note, and it is 300 mm of it

/* Shadow gaps have to read as voids. MAT.walnut catches far too much of a
   sconce washing straight down the front of the console, so the 24 mm finger
   reveal gets its own matt near-black liner — the same trick the kitchen uses
   on its joints, and the one surface in here not allowed to catch anything. */
const GAP = new THREE.MeshStandardMaterial({
  color: 0x121310, roughness: 0.95, metalness: 0, envMapIntensity: 0.1,
});

/* The mirror. There is no reflection in it and there does not need to be one:
   a real one is a second render of the whole flat, taken from behind a panel
   you see at a grazing angle, to show a dark hall at night. A near-black
   metal at 0.08
   roughness picks up the captured room environment instead — the sconce faces
   smear down it, the doorway behind you shows as a soft grey block, and that
   is the whole of what a dark mirror ever gives you by lamplight. It costs
   one material. */
const MIRROR = new THREE.MeshStandardMaterial({
  // The captured environment is dominated by the fire, so a mirror-smooth
  // metal at 2x intensity puts a blown-out sun in the middle of the hall.
  // Softer and dimmer reads as dark glass; sharper reads as a bug.
  // Smooth metal this size takes a specular lobe half a metre across from a
  // sconce standing next to it — the mirror was a white hole in the wall.
  // Dark glass rather than polished metal: a little sheen, no lobe.
  color: 0x171a1e, roughness: 0.46, metalness: 0.18, envMapIntensity: 0.65,
});

/* ============================================================== runner === */
/* Down the length rather than across it: the runner is the only thing telling
   you the hall is a route, and it stops short of the bedroom door so the leaf
   swings over stone. */
function buildRunner(g) {
  const rug = new THREE.Mesh(roundedBoxGeo(6.70, 0.022, 1.05, 0.05, 1), MAT.rugDark);
  rug.position.set(-1.15, 0.011, 4.95);
  rug.receiveShadow = true;
  g.add(rug);
  return rug;
}

/* ============================================================= console === */
/* Wall-hung, not on legs. Two reasons: the skirting runs across this wall at
   110 mm and a floating carcass never has to be scribed round it, and 600 mm
   of clear floor under a 2.8 m box is what stops the hall reading as a
   corridor with furniture jammed into it. The dark reveal above the drawers
   is the only handle. */
function buildConsole(g) {
  const c = new THREE.Group();
  const W = CON.x1 - CON.x0;
  const y0 = CON.top - CON.h;                    // 0.58 — the shadow gap starts here

  const body = shadowed(new THREE.Mesh(
    roundedBoxGeo(W, CON.h, CON.d - 0.015, 0.006, 1), MAT.walnut));
  body.position.set(CX, y0 + CON.h / 2, NORTH - (CON.d - 0.015) / 2);
  c.add(body);

  // two fronts, proud of the carcass, with a gap between them and a reveal over
  const FW = (W - 0.018) / 2, FH = CON.h - 0.028;
  [-1, 1].forEach((s) => {
    const f = shadowed(new THREE.Mesh(roundedBoxGeo(FW, FH, 0.015, 0.004, 1), MAT.walnut));
    f.position.set(CX + s * (FW / 2 + 0.009), y0 + FH / 2 + 0.004, CON_F + 0.0075);
    c.add(f);
  });
  /* The reveal is filled rather than left open: an empty slot shows the
     carcass face 15 mm behind it, coplanar with the drawer fronts, and the two
     walnut planes flicker against each other from across the hall. */
  const reveal = new THREE.Mesh(new THREE.BoxGeometry(W - 0.01, 0.024, 0.012), GAP);
  reveal.position.set(CX, CON.top - 0.012, CON_F + 0.006);
  c.add(reveal);

  g.add(c);
  return c;
}

/* ============================================================== mirror === */
function buildMirror(g) {
  const m = new THREE.Group();

  const glass = new THREE.Mesh(
    new THREE.BoxGeometry(MIR.w - 0.04, MIR.h - 0.04, 0.012), MIRROR);
  glass.position.set(CX, MIR.y, NORTH - 0.006);
  glass.receiveShadow = true;                    // a 12 mm panel casts nothing worth having
  m.add(glass);

  /* The surround: 24 mm of brass on edge, and the only brass in the room apart
     from the sconces and the two levers. Anything wider turns the wall into a
     picture of a mirror. */
  const BAR = 0.024, PROUD = 0.030;
  const bar = (w, h, x, y) => {
    const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, PROUD), MAT.brass);
    b.position.set(x, y, NORTH - PROUD / 2);
    b.castShadow = true; b.receiveShadow = true;
    m.add(b);
  };
  bar(MIR.w, BAR, CX, MIR.y + MIR.h / 2 - BAR / 2);
  bar(MIR.w, BAR, CX, MIR.y - MIR.h / 2 + BAR / 2);
  bar(BAR, MIR.h - BAR * 2, CX - MIR.w / 2 + BAR / 2, MIR.y);
  bar(BAR, MIR.h - BAR * 2, CX + MIR.w / 2 - BAR / 2, MIR.y);

  g.add(m);
  return m;
}

/* ============================================================= sconce ==== */
/* An open brass tube on a plate, throwing up the wall and down onto the
   console. The tube's two ends are the light: a basic material with tone
   mapping off, so they stay a clean warm disc however hard the grade pushes
   the rest of the room down. The lamp itself is one point light for the pair,
   built in room-lights.js from the positions returned here. */
/* The sconces were a brass tube capped top and bottom with flat emissive
   discs, so what you saw was two white dots and a hot patch on the plaster.
   The shared fixture is an alabaster half-shade that glows unevenly from
   inside and washes the wall in both directions — it has a shape, and the
   light has somewhere to have come from. */
function buildSconce(g, x) {
  const f = sconce(x, SC_Y, NORTH - 0.055, Math.PI);
  g.add(f.group);
  return f;
}

/* ==================================================== what is on the top = */
/* The archaeology of a front door: keys in a dish, the post nobody has opened,
   a scarf dropped on the way past. It is arranged either side of the mirror
   rather than under it — the centre of a hall console is always empty, because
   that is where you put things down. */
function buildConsoleThings(g) {
  const y = CON.top;

  /* --- a shallow travertine dish, with the keys thrown into it --- */
  const dish = shadowed(new THREE.Mesh(new THREE.LatheGeometry([
    [0.000, 0.000], [0.055, 0.000], [0.090, 0.008], [0.104, 0.026], [0.104, 0.030],
    [0.096, 0.028], [0.082, 0.014], [0.050, 0.006], [0.000, 0.006],
  ].map(([r, h]) => new THREE.Vector2(r, h)), 16), MAT.travertine));
  dish.position.set(-1.34, y, 6.34);
  g.add(dish);

  const keys = new THREE.Group();
  keys.position.set(-1.34, y + 0.008, 6.34);
  keys.rotation.y = 0.42;
  [[-0.012, 0.0, 0.30], [0.010, 0.004, -0.18]].forEach(([kx, ky, ka]) => {
    const k = new THREE.Mesh(new THREE.BoxGeometry(0.017, 0.002, 0.055), MAT.steel);
    k.position.set(kx, ky, 0.008);
    k.rotation.y = ka;
    k.receiveShadow = true;              // 2 mm of key casts nothing but noise
    keys.add(k);
  });
  const ring = new THREE.Mesh(new THREE.TorusGeometry(0.016, 0.0022, 6, 14), MAT.brass);
  ring.rotation.x = Math.PI / 2 - 0.25;
  ring.position.set(0.004, 0.006, -0.028);
  ring.receiveShadow = true;
  keys.add(ring);
  g.add(keys);

  /* --- the post, squared up by nobody --- */
  /* MAT.clayPale and MAT.clay are the palette's only untextured matts, which
     makes them the closest thing it has to paper and kraft. */
  let py = y;
  for (let i = 0; i < 4; i++) {
    const th = 0.005;
    const e = new THREE.Mesh(
      roundedBoxGeo(0.235 - i * 0.006, th, 0.162 - i * 0.004, 0.002, 1),
      i === 1 ? MAT.clay : MAT.clayPale);
    e.position.set(-2.56, py + th / 2, 6.33);
    e.rotation.y = rrnd(-0.13, 0.13);
    e.receiveShadow = true;              // a 20 mm stack: it takes light, it does not throw it
    py += th;
    g.add(e);
  }

  /* --- a bud vase, and the dry stems left in it since the autumn --- */
  /* The profile turns over the rim and runs back down inside to a floor. A
     lathe stopped at the lip is a shell: look down into the neck and you see
     straight through the pot to the walnut, because the inside face is
     backfacing and gets culled. */
  const vase = shadowed(new THREE.Mesh(new THREE.LatheGeometry([
    [0.000, 0.000], [0.045, 0.000], [0.053, 0.035], [0.046, 0.105],
    [0.030, 0.170], [0.027, 0.200], [0.031, 0.215],
    [0.026, 0.212], [0.022, 0.170], [0.024, 0.100], [0.019, 0.048], [0.000, 0.042],
  ].map(([r, h]) => new THREE.Vector2(r, h)), 14), MAT.clay));
  vase.position.set(-3.20, y, 6.38);
  g.add(vase);

  /* The stems lean west, away from the sconce at x = -2.90: a stem through a
     light fitting is the sort of thing you only ever see once it is rendered. */
  for (let i = 0; i < 3; i++) {
    const len = rrnd(0.40, 0.50);
    const lean = -rrnd(0.10, 0.26), drift = rrnd(-0.10, 0.06);
    const p0 = new THREE.Vector3(-3.20, y + 0.17, 6.38);
    const curve = new THREE.CatmullRomCurve3([
      p0,
      p0.clone().add(new THREE.Vector3(lean * 0.25, len * 0.45, drift * 0.35)),
      p0.clone().add(new THREE.Vector3(lean * 0.70, len * 0.80, drift * 0.75)),
      p0.clone().add(new THREE.Vector3(lean, len, drift)),
    ]);
    const stem = new THREE.Mesh(new THREE.TubeGeometry(curve, 8, 0.0035, 4), MAT.wicker);
    stem.castShadow = true;
    g.add(stem);
    // three dry seed heads up the top third, and nothing else
    for (let k = 0; k < 3; k++) {
      const t = 0.62 + k * 0.16;
      const bud = new THREE.Mesh(new THREE.ConeGeometry(0.006, 0.028, 5), MAT.wicker);
      bud.position.copy(curve.getPointAt(t));
      bud.rotation.set(rrnd(-0.5, 0.5), rnd() * 6.28, rrnd(-0.6, 0.6));
      g.add(bud);
    }
  }

  /* --- the scarf, folded once and left over the front edge --- */
  const scarf = shadowed(new THREE.Mesh(drapeGeo([
    [-0.98, 0.864, 6.470],
    [-0.99, 0.862, 6.370],
    [-1.00, 0.859, 6.265],
    [-1.005, 0.846, 6.196],
    [-1.010, 0.790, 6.166],
    [-1.014, 0.710, 6.158],
    [-1.018, 0.638, 6.166],
  ], 0.30, {
    nu: 22, nv: 10, folds: 4, amp: 0.018, taper: 0.06,
    // pressed flat where it lies on the walnut, loose where it falls
    freeAt: (u) => Math.max(0.12, (u - 0.48) * 2.1),
  }), SCARF));
  g.add(scarf);

  // the upper leaf of the fold, set back so the lower one shows past it
  const fold = shadowed(new THREE.Mesh(drapeGeo([
    [-0.94, 0.876, 6.430],
    [-0.95, 0.874, 6.340],
    [-0.96, 0.870, 6.255],
    [-0.965, 0.858, 6.205],
    [-0.970, 0.822, 6.186],
  ], 0.26, { nu: 14, nv: 8, folds: 3, amp: 0.014, taper: 0.05, freeAt: () => 0.30 }), SCARF));
  g.add(fold);
}

/* ============================================================== doors ==== */
/* Decoration: there is nothing behind either of these. They are closed leaves
   in a solid wall, lined and levered like the real one the shell hangs in the
   bedroom wall, because a hall with one door in it is a corridor and a hall
   with three is somewhere you arrive. */
function buildDoor(g, cx, w, handSide) {
  const d = new THREE.Group();
  const LIN = 0.032, PROUD = 0.055, TH = 0.042;

  /* The leaf is cut 3 mm short of its lining all round. That is the joint a
     real door has, and it also keeps the leaf's edges off the lining's faces —
     butt two boxes together on exactly the same plane and the pair flickers
     the moment the camera moves. */
  const LW = w - 0.006, LH = DOOR_H - 0.004;
  const leaf = shadowed(new THREE.Mesh(
    boxUv(new THREE.BoxGeometry(LW, LH, TH), LW, LH, TH, WOOD_TILE), MAT.walnut));
  leaf.position.set(cx, LH / 2, NORTH - TH / 2);
  d.add(leaf);

  const line = (bw, bh, x, y) => {
    const b = new THREE.Mesh(new THREE.BoxGeometry(bw, bh, PROUD), MAT.walnut);
    b.position.set(x, y, NORTH - PROUD / 2);
    b.castShadow = true; b.receiveShadow = true;
    d.add(b);
  };
  line(LIN, DOOR_H + LIN, cx - w / 2 - LIN / 2, (DOOR_H + LIN) / 2);
  line(LIN, DOOR_H + LIN, cx + w / 2 + LIN / 2, (DOOR_H + LIN) / 2);
  line(w + LIN * 2, LIN, cx, DOOR_H + LIN / 2);

  /* Lever at 1.05, which is where every door handle in the world is, and the
     lever pointing back toward its hinge — the other way round reads as a
     bathroom lock. */
  const hx = cx + handSide * (w / 2 - 0.075);
  const face = NORTH - TH;
  const rose = new THREE.Mesh(new THREE.CylinderGeometry(0.026, 0.026, 0.010, 10), MAT.brass);
  rose.rotation.x = Math.PI / 2;
  rose.position.set(hx, 1.05, face - 0.005);
  rose.receiveShadow = true;
  d.add(rose);
  const lever = new THREE.Mesh(new THREE.CylinderGeometry(0.010, 0.009, 0.10, 8), MAT.brass);
  lever.rotation.z = Math.PI / 2;
  lever.position.set(hx - handSide * 0.045, 1.05, face - 0.026);
  lever.castShadow = true;
  d.add(lever);

  g.add(d);
  return d;
}

/* ================================================================ art ==== */
/* Two tall narrow canvases on the long wall, hung to face the mirror. Painted
   in a canvas element at load like everything else here: a dark tonal column
   with one warmer band low in it, which at this light level is all that ever
   comes back off a picture anyway. */
function artTexture(tint) {
  const cv = document.createElement('canvas');
  cv.width = 128; cv.height = 384;
  const c = cv.getContext('2d');
  const grd = c.createLinearGradient(0, 0, 0, 384);
  grd.addColorStop(0, '#0d0f12');
  grd.addColorStop(0.60, '#1a191b');
  grd.addColorStop(0.80, tint);
  grd.addColorStop(1, '#0b0a0a');
  c.fillStyle = grd; c.fillRect(0, 0, 128, 384);
  for (let i = 0; i < 90; i++) {
    c.globalAlpha = 0.02 + rnd() * 0.05;
    c.fillStyle = rnd() < 0.5 ? '#4a3b2a' : '#08080a';
    c.fillRect(rrnd(-24, 128), rrnd(0, 384), rrnd(24, 150), rrnd(2, 13));
  }
  c.globalAlpha = 1;
  const t = new THREE.CanvasTexture(cv);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

function buildArt(g, x, tint) {
  const W = 0.44, HGT = 1.34, Y = 1.60;
  const frame = shadowed(new THREE.Mesh(
    boxUv(new THREE.BoxGeometry(W, HGT, 0.038), W, HGT, 0.038, WOOD_TILE), MAT.walnut));
  frame.position.set(x, Y, SOUTH + 0.019);
  g.add(frame);

  const face = new THREE.Mesh(new THREE.PlaneGeometry(W - 0.05, HGT - 0.05),
    new THREE.MeshStandardMaterial({ map: artTexture(tint), roughness: 0.92, envMapIntensity: 0.22 }));
  face.position.set(x, Y, SOUTH + 0.039);
  face.receiveShadow = true;
  g.add(face);
  return frame;
}

/* ============================================================== coats ==== */
/* A steel rail on the blind west wall, one bay in from the entrance door. The
   coat is the only thing in the hall with any volume to it, so it is built the
   way the throw over the sofa arm is: a swept cloth with real folds, hung from
   the hem upward so the taper closes it into a gather at the hook. */
function buildHooks(g) {
  const h = new THREE.Group();
  const zs = [5.26, 5.48, 5.70, 5.92];

  const rail = new THREE.Mesh(new THREE.BoxGeometry(0.022, 0.032, 0.86), MAT.steel);
  rail.position.set(WEST + 0.011, HOOK_Y, 5.59);
  rail.castShadow = true; rail.receiveShadow = true;
  h.add(rail);

  for (const z of zs) {
    const peg = new THREE.Mesh(new THREE.CylinderGeometry(0.009, 0.009, 0.075, 8), MAT.steel);
    peg.rotation.z = Math.PI / 2;
    peg.position.set(WEST + 0.0595, HOOK_Y, z);
    peg.castShadow = true;
    h.add(peg);
    const knob = new THREE.Mesh(new THREE.SphereGeometry(0.013, 8, 6), MAT.steel);
    knob.position.set(WEST + 0.097, HOOK_Y, z);
    knob.castShadow = true;
    h.add(knob);
  }

  g.add(h);
  return h;
}

function buildCoat(g) {
  /* The body. The path leans in toward the wall as it climbs, which is what
     gives drapeGeo a stable side vector — a dead vertical sweep degenerates —
     and is also how a wool coat actually hangs off a peg. */
  const body = shadowed(new THREE.Mesh(drapeGeo([
    [-4.742, 0.600, 5.488],
    [-4.757, 0.850, 5.484],
    [-4.782, 1.110, 5.480],
    [-4.808, 1.350, 5.478],
    [-4.836, 1.550, 5.476],
    [-4.855, 1.655, 5.475],
  ], 0.50, {
    nu: 26, nv: 12, folds: 5, amp: 0.030, taper: 0.62,
    freeAt: (u) => 0.25 + 0.75 * Math.pow(1 - u, 0.8),
  }), COAT));
  g.add(body);

  // the front edges, hung a few millimetres proud so the coat reads as open
  const front = shadowed(new THREE.Mesh(drapeGeo([
    [-4.706, 0.760, 5.470],
    [-4.722, 1.000, 5.468],
    [-4.750, 1.240, 5.466],
    [-4.784, 1.440, 5.464],
    [-4.816, 1.570, 5.463],
  ], 0.30, { nu: 18, nv: 9, folds: 3, amp: 0.020, taper: 0.48, freeAt: (u) => 0.30 + 0.60 * (1 - u) }), COAT));
  g.add(front);

  // sleeves, hanging shorter and narrower off each shoulder
  [[5.300, 0.860, 1], [5.660, 0.900, -1]].forEach(([z, hem, s]) => {
    const sl = shadowed(new THREE.Mesh(drapeGeo([
      [-4.700, hem, z],
      [-4.722, hem + 0.20, z + s * 0.005],
      [-4.762, hem + 0.40, z + s * 0.020],
      [-4.802, hem + 0.58, z + s * 0.050],
      [-4.832, hem + 0.69, z + s * 0.100],
    ], 0.15, { nu: 16, nv: 6, folds: 3, amp: 0.013, taper: 0.30, freeAt: (u) => 0.35 + 0.55 * (1 - u) }), COAT));
    g.add(sl);
  });

  // the collar, arced over the peg — the piece that makes it hang rather than float
  const collar = shadowed(new THREE.Mesh(drapeGeo([
    [-4.866, 1.600, 5.398],
    [-4.882, 1.690, 5.437],
    [-4.890, 1.732, 5.480],
    [-4.882, 1.690, 5.523],
    [-4.866, 1.600, 5.562],
  ], 0.10, { nu: 14, nv: 5, folds: 2, amp: 0.007, taper: 0, freeAt: () => 0.25 }), COAT));
  g.add(collar);
}

function buildBag(g) {
  const Z = 5.92;
  /* A soft leather tote, so it is a stuffed cushion stood on its edge rather
     than a box: cushionGeo is built lying down, so rotating it about z turns
     its thickness axis out from the wall and its width into height. */
  const bag = shadowed(new THREE.Mesh(
    cushionGeo(0.30, 0.13, 0.34, {
      corner: 3.4, wide: 3.2, edge: 0.40, pinch: 0.05, wrinkle: 1.5, seg: 26, rings: 9,
    }), MAT.leather));
  bag.position.set(WEST + 0.078, 1.28, Z);
  bag.rotation.z = -Math.PI / 2;
  bag.rotation.x = 0.04;
  g.add(bag);
  const welt = new THREE.Mesh(weltGeo(0.30 * 0.99, 0.34 * 0.99, { corner: 3.4, radius: 0.006 }), MAT.leather);
  welt.castShadow = false;
  bag.add(welt);

  const strap = shadowed(new THREE.Mesh(new THREE.TubeGeometry(
    new THREE.CatmullRomCurve3([
      new THREE.Vector3(-4.868, 1.400, Z - 0.115),
      new THREE.Vector3(-4.884, 1.640, Z - 0.070),
      new THREE.Vector3(-4.890, 1.738, Z),
      new THREE.Vector3(-4.884, 1.640, Z + 0.070),
      new THREE.Vector3(-4.868, 1.400, Z + 0.115),
    ]), 16, 0.011, 6), MAT.leather));
  g.add(strap);
}

/* =========================================================== assemble ==== */
export function buildHall() {
  const g = new THREE.Group();
  g.name = 'hall';

  buildRunner(g);
  buildConsole(g);
  buildMirror(g);
  buildConsoleThings(g);

  /* The entrance at the blind end and the bathroom the other side of the
     console, both well clear of the 2.88 soffit. */
  buildDoor(g, -4.30, 0.94, 1);
  buildDoor(g, 0.55, 0.82, -1);

  // facing the mirror across the hall, on the only long stretch of blank wall
  buildArt(g, -2.30, '#463020');
  buildArt(g, -1.72, '#2d3830');

  buildHooks(g);
  buildCoat(g);
  buildBag(g);

  const sconces = [
    buildSconce(g, CX - SC_DX),
    buildSconce(g, CX + SC_DX),
  ];

  return { group: g, sconces };
}

/* ----------------------------------------------------------- collision ---
   World AABBs, [minX, minY, minZ, maxX, maxY, maxZ]. Only two: the console,
   run down to the floor because nobody walks under a floating carcass and a
   box that starts at 580 lets you stand inside it, and the strip of west wall
   the coat and the bag hang off. Everything else in here is flatter than the
   walker's own clearance. */
export const HALL_COLLIDERS = [
  [CON.x0 - 0.04, 0, CON_F - 0.05, CON.x1 + 0.04, CON.top + 0.02, NORTH],
  [WEST - 0.03, 0, 5.14, WEST + 0.28, 1.80, 6.12],
];
