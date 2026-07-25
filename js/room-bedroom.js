/* =========================================================================
   The bedroom.

   Seven metres by six, one window in the far corner, and at night almost no
   light in it at all: two bedside lamps and whatever the city throws through
   the glass. That is the whole brief. A room lit this softly lives or dies on
   silhouette, so the money goes into the bed — a real duvet with folds and a
   turned-back edge, four pillows that have been slept on — and everything
   else is kept dark, flat and quiet: walnut joinery, a wool rug the colour of
   wet slate, one rust throw across the foot.

   Positions are derived from the plan rather than typed in, so the room
   cannot drift away from the walls it is built against.
   ========================================================================= */
import * as THREE from 'three';
import { rnd, rrnd, pick, roundedBoxGeo, planeUv } from './room.js';
import { MAT, bookMat, shadowed } from './room-mat.js';
import { cushionGeo, weltGeo, drapeGeo } from './room-soft.js';
import { ROOMS, APT, COLUMN } from './room-plan.js';
import { tableLamp } from './room-lamps.js';

const R = ROOMS.bedroom;
/* walls are centred on their plan line and carry a 12 mm skirting, so the
   face anything can actually stand against is half a wall plus the skirting */
const SKIRT = APT.wall / 2 + 0.012;
const WEST = R.x0 + SKIRT;          // 4.067 — the doorway wall
const SOUTH = R.z0 + SKIRT;         // 0.667 — the wall shared with the kitchen
const NORTH = R.z1 - SKIRT;         // 6.533 — the outside wall behind the wardrobe

/* --------------------------------------------------------------- the bed --
   The headboard goes on the west wall. That is the only wall in the room that
   points the bed at the glazing: the glass runs down x = 11 from z = 0.6 to
   3.0, so lying with your head at x = 4 you look straight along the bed and
   out at the city. Against the north or south wall you would be looking at a
   blank wall with the window somewhere off your shoulder.

   The catch is that the structural column lands at x = 4, z = 1.55 and stands
   220 mm proud of that wall, right where the pillows want to be. Rather than
   notch the headboard round a pier, the whole panel is packed out past the
   column face, so the upholstery reads as one plane and the column simply
   disappears into it. */
const HB_FACE = Math.max(R.x0 + 0.34, COLUMN.x + COLUMN.w / 2 + 0.20);   // 4.42
const HB_TOP = 1.32;

const BED_X = HB_FACE + 0.095 + 1.025;   // mattress head tucked under the tufting
const BED_Z = 2.05;                      // centred on the glazing, clear of the column
const MAT_TOP = 0.56;

/* The panel runs from just off the kitchen wall to the same distance the other
   side of the bed: wide enough to stand both nightstands on and to read as a
   wall finish rather than a piece of bed. */
const HB_W = 2 * (BED_Z - SOUTH - 0.02);   // 2.726

const NS_Z = [0.905, 3.195];             // nightstand centres, one either side
const NS_W = 0.44, NS_D = 0.42, NS_TOP = 0.52;

/* A single sheet of cloth shows its reverse wherever it hangs off an edge or
   folds back on itself. Cloning the palette material and flipping `side` keeps
   the same maps and tint instead of inventing a second, subtly different
   fabric for the same duvet. */
const twoSided = (m) => { const c = m.clone(); c.side = THREE.DoubleSide; return c; };
const DUVET = twoSided(MAT.olive);
const THROW = twoSided(MAT.rust);

/* ========================================================== headboard ==== */
/* Channel tufting is a stack of horizontal bolsters trapped behind a panel,
   not a quilted grid — each channel is its own stuffed cushion, so it bulges
   in the middle and tucks in hard at the seam above and below it. cushionGeo
   is built lying down, so each one is stood on its side: w becomes the
   channel's height, h its projection off the panel, d its length. */
function buildHeadboard(b) {
  const panelD = HB_FACE - WEST;
  const panel = shadowed(new THREE.Mesh(
    roundedBoxGeo(panelD, HB_TOP - 0.12, HB_W, 0.02, 1), MAT.linenDark));
  panel.position.set(WEST + panelD / 2 - BED_X, (HB_TOP + 0.12) / 2, 0);
  b.add(panel);

  const CH_H = 0.162, GAP = 0.012, PROJ = 0.115;
  for (let i = 0; i < 5; i++) {
    // the bottom channel starts below the mattress line so no gap ever shows
    const y = 0.42 + CH_H / 2 + i * (CH_H + GAP);
    const c = shadowed(new THREE.Mesh(
      cushionGeo(CH_H, PROJ, 1.94, {
        corner: 7.0, wide: 5.0, edge: 0.30, pinch: 0.06, wrinkle: 0.7,
        seg: 40, rings: 8,
      }), MAT.linenDark));
    c.position.set(HB_FACE + PROJ / 2 - BED_X, y, 0);
    c.rotation.z = -Math.PI / 2;
    // no two channels are stuffed alike; a stack of identical ones reads as moulding
    c.rotation.y = rrnd(-0.006, 0.006);
    b.add(c);
  }
}

/* ================================================================ bed ==== */
function buildBed(g) {
  const b = new THREE.Group();
  b.position.set(BED_X, 0, BED_Z);

  buildHeadboard(b);

  /* A low walnut platform on slim steel feet, the mattress dropped into it so
     the rail hides its bottom third. */
  const frame = shadowed(new THREE.Mesh(roundedBoxGeo(2.14, 0.23, 1.94, 0.015, 1), MAT.walnut));
  frame.position.y = 0.205;
  b.add(frame);
  [[-0.92, -0.80], [-0.92, 0.80], [0.92, -0.80], [0.92, 0.80]].forEach(([x, z]) => {
    const f = new THREE.Mesh(new THREE.CylinderGeometry(0.021, 0.024, 0.09, 8), MAT.steel);
    f.position.set(x, 0.045, z);
    f.castShadow = true;
    b.add(f);
  });

  const mattress = shadowed(new THREE.Mesh(roundedBoxGeo(2.05, 0.26, 1.80, 0.05, 3), MAT.linen));
  mattress.position.y = MAT_TOP - 0.13;
  b.add(mattress);

  /* The fitted sheet. Only the corner the duvet has been pulled off ever
     shows, but it has to be there, and it has to be softer than a plane —
     two long, shallow wrinkles are enough to catch the lamp. */
  const sg = planeUv(new THREE.PlaneGeometry(2.03, 1.78, 8, 8), 2.03, 1.78, 1, 1);
  const sp = sg.attributes.position;
  for (let i = 0; i < sp.count; i++) {
    const x = sp.getX(i), y = sp.getY(i);
    sp.setZ(i, Math.sin(x * 2.7 + 0.6) * 0.005 + Math.sin(y * 3.4 - 1.1) * 0.004);
  }
  sp.needsUpdate = true;
  sg.computeVertexNormals();
  const sheet = new THREE.Mesh(sg, MAT.linen);
  sheet.rotation.x = -Math.PI / 2;
  sheet.position.y = MAT_TOP + 0.006;
  sheet.receiveShadow = true;
  b.add(sheet);

  buildDuvet(b);
  buildPillows(b);
  buildFootThrow(b);

  g.add(b);
  return b;
}

/* ============================================================== duvet ==== */
/* The hero. A duvet is swept ACROSS the bed, not along it: the cloth comes up
   off the floor on one side, over the mattress, and back down the other, which
   is the only way the two long overhangs come out as real hanging cloth with
   vertical pleats in them.

   The path's x drifts as it crosses, and the cloth gathers narrower with it,
   so the duvet ends up skewed on the bed: its top edge is right up against the
   pillows on the kitchen side and dragged 360 mm down the bed on the other,
   where somebody got out. That single drift is what opens up a wedge of bare
   sheet without having to cut a hole in anything. */
function buildDuvet(b) {
  const body = shadowed(new THREE.Mesh(drapeGeo([
    [0.39, 0.175, -1.055],
    [0.40, 0.365, -1.025],
    [0.42, 0.530, -0.950],
    [0.44, 0.598, -0.840],
    [0.48, 0.607, -0.520],
    [0.52, 0.600, -0.150],
    [0.55, 0.606, 0.230],
    [0.58, 0.601, 0.600],
    [0.59, 0.590, 0.845],
    [0.60, 0.520, 0.960],
    [0.61, 0.330, 1.045],
    [0.61, 0.150, 1.080],
  ], 1.26, {
    nu: 36, nv: 22, folds: 7, amp: 0.028, taper: 0.222,
    // pressed flat where it lies on the mattress, loose where it hangs
    freeAt: (u) => 0.18 + 0.82 * Math.max(0, Math.max(1 - u / 0.20, (u - 0.80) / 0.20)),
  }), DUVET));
  b.add(body);

  /* The turned-back cuff along that skewed top edge: the duvet folded over
     onto itself, so the fold is the head-most point and the raw hem lies back
     down the bed. Rotating the whole piece by the same angle the edge runs at
     keeps the two aligned however the drift is tuned. */
  const cuff = shadowed(new THREE.Mesh(drapeGeo([
    [-0.02, 0.572, 0],
    [0.02, 0.628, 0],
    [0.10, 0.668, 0],
    [0.22, 0.662, 0],
    [0.34, 0.636, 0],
    [0.44, 0.610, 0],
  ], 1.58, {
    nu: 14, nv: 26, folds: 5, amp: 0.020, taper: 0.05,
    freeAt: (u) => 0.25 + 0.55 * Math.max(0, 1 - u / 0.30),
  }), DUVET));
  cuff.position.set(-0.08, 0, 0);
  cuff.rotation.y = 0.167;
  b.add(cuff);
}

/* ============================================================ pillows ==== */
/* Two standing against the headboard and two dropped flat in front of them.
   A standing pillow is rotated about Z, not X: that keeps the cushion's
   thickness axis pointing down the bed and its length across it, which is the
   only orientation where the seam runs round the pillow the way it is sewn.

   No welts — piped bed pillows are a hotel thing, and the seam here should
   read as a soft edge, not a cord. */
function buildPillows(b) {
  const prop = (h, thick, len, z, lean, spin, mat) => {
    const p = shadowed(new THREE.Mesh(
      cushionGeo(h, thick, len, {
        corner: 3.0, wide: 3.0, edge: 0.42, pinch: 0.07, wrinkle: 1.6,
        seg: 28, rings: 9,
      }), mat));
    // the pillow's foot rests on the mattress and the head tips back
    p.position.set(-0.76 - Math.sin(lean) * h / 2, MAT_TOP + Math.cos(lean) * h / 2, z);
    p.rotation.set(rrnd(-0.03, 0.03), spin, -Math.PI / 2 + lean);
    b.add(p);
    return p;
  };
  prop(0.46, 0.17, 0.68, -0.40, 0.34, 0.10, MAT.linen);
  prop(0.44, 0.185, 0.70, 0.41, 0.27, -0.14, MAT.linenDark);

  const flat = (along, thick, across, x, z, spin, mat) => {
    const p = shadowed(new THREE.Mesh(
      cushionGeo(along, thick, across, {
        corner: 3.0, wide: 2.8, edge: 0.44, pinch: 0.07, wrinkle: 1.9,
        seg: 28, rings: 9,
      }), mat));
    p.position.set(x, MAT_TOP + thick / 2 - 0.012, z);
    p.rotation.set(rrnd(-0.02, 0.02), spin, rrnd(-0.03, 0.03));
    b.add(p);
    return p;
  };
  // dropped just clear of the standing pair, so the two only squash together
  flat(0.44, 0.155, 0.66, -0.49, -0.34, 0.13, MAT.linen);
  flat(0.46, 0.145, 0.64, -0.47, 0.40, -0.20, MAT.linenDark);
}

/* ======================================================== foot throw ==== */
/* Folded in two and laid across the foot, so it hangs outside the duvet on
   both sides. The one warm colour anyone gets to see in here. */
function buildFootThrow(b) {
  const main = shadowed(new THREE.Mesh(drapeGeo([
    [0.66, 0.300, -1.085],
    [0.67, 0.470, -1.065],
    [0.68, 0.580, -1.005],
    [0.69, 0.625, -0.900],
    [0.70, 0.634, -0.450],
    [0.71, 0.630, 0.020],
    [0.72, 0.636, 0.480],
    [0.73, 0.628, 0.850],
    [0.74, 0.590, 0.985],
    [0.75, 0.455, 1.070],
    [0.75, 0.300, 1.100],
  ], 0.46, {
    nu: 26, nv: 10, folds: 3, amp: 0.016, taper: 0.03,
    freeAt: (u) => 0.20 + 0.80 * Math.max(0, Math.max(1 - u / 0.22, (u - 0.78) / 0.22)),
  }), THROW));
  b.add(main);

  // the upper leaf of the fold, set back so the lower one shows past it
  const leaf = shadowed(new THREE.Mesh(drapeGeo([
    [0.78, 0.646, -0.880],
    [0.79, 0.652, -0.440],
    [0.80, 0.648, 0.020],
    [0.81, 0.654, 0.470],
    [0.82, 0.646, 0.870],
  ], 0.32, { nu: 16, nv: 8, folds: 3, amp: 0.013, taper: 0.02, freeAt: () => 0.35 }), THROW));
  b.add(leaf);
}

/* ======================================================== nightstand ==== */
/* Walnut box on blackened steel legs, two drawers, a 12 mm steel bar for a
   pull. Slim enough that it does not compete with the headboard behind it. */
function buildNightstand(g, z) {
  const n = new THREE.Group();
  n.position.set(HB_FACE + NS_D / 2, 0, z);

  const LEG = 0.10;
  const body = shadowed(new THREE.Mesh(
    roundedBoxGeo(NS_D, NS_TOP - LEG, NS_W, 0.006, 1), MAT.walnut));
  body.position.y = (NS_TOP + LEG) / 2;
  n.add(body);

  [[-1, -1], [-1, 1], [1, -1], [1, 1]].forEach(([sx, sz]) => {
    const l = new THREE.Mesh(new THREE.BoxGeometry(0.026, LEG, 0.026), MAT.steel);
    l.position.set(sx * (NS_D / 2 - 0.035), LEG / 2, sz * (NS_W / 2 - 0.035));
    l.castShadow = true;
    n.add(l);
  });

  // two drawer fronts, proud of the carcass with a shadow gap between them
  const DH = 0.19;
  [0.115, 0.315].forEach((y0) => {
    const d = shadowed(new THREE.Mesh(
      roundedBoxGeo(0.014, DH, NS_W - 0.022, 0.003, 1), MAT.walnut));
    d.position.set(NS_D / 2 + 0.006, y0 + DH / 2, 0);
    n.add(d);
    const pull = new THREE.Mesh(new THREE.BoxGeometry(0.022, 0.012, 0.17), MAT.steel);
    pull.position.set(NS_D / 2 + 0.023, y0 + DH / 2, 0);
    pull.receiveShadow = true;       // 12 mm of steel casts nothing worth the map
    n.add(pull);
  });

  g.add(n);
  return n;
}

/* ============================================================== lamp ==== */
/* Same construction as the floor lamp in the living room: a translucent shade
   that glows a little on its own, a basic-material disc under it doing the
   pool of light, and a bulb. All three fade together — a shade still lit with
   the lamp switched off is the one thing you always notice. */
/* A turned ceramic body under a fabric drum that glows from the inside,
   from the shared fixture library. The old one was a plain cylinder with a
   flat emissive disc under it: two of them a metre apart bloomed into one
   ball and neither read as a lamp. */
function buildLamp(g, x, z) {
  const f = tableLamp(x, NS_TOP, z);
  g.add(f.group);
  return f;
}

/* ========================================================== wardrobe ==== */
/* Full height, no handles, no frame: six walnut leaves floating in front of a
   dark carcass with an 18 mm shadow gap between them and a reveal top and
   bottom. Push-to-open, so the only thing that draws the eye is the run of
   vertical lines — which is the point in a room this dark. */
function buildWardrobe(g) {
  const w = new THREE.Group();
  const X0 = R.x0 + 0.10, X1 = X0 + 3.60;
  const BACK = NORTH, DEPTH = 0.57;

  const carcass = shadowed(new THREE.Mesh(
    new THREE.BoxGeometry(X1 - X0, APT.h, DEPTH), MAT.cabinet));
  carcass.position.set((X0 + X1) / 2, APT.h / 2, BACK - DEPTH / 2);
  w.add(carcass);

  const N = 6, GAP = 0.018;
  const dw = ((X1 - X0) - GAP * (N - 1)) / N;
  const dz = BACK - DEPTH - 0.014;
  for (let i = 0; i < N; i++) {
    const d = shadowed(new THREE.Mesh(roundedBoxGeo(dw, 3.10, 0.028, 0.004, 1), MAT.walnut));
    d.position.set(X0 + dw / 2 + i * (dw + GAP), 0.10 + 3.10 / 2, dz);
    w.add(d);
  }

  g.add(w);
  return w;
}

/* ============================================================= bench ==== */
/* Leather, on a blackened steel frame, sitting just off the foot of the bed.
   The seat is a stuffed cushion with a welt, not a slab — it is the one thing
   in the room at eye level when you are standing in the doorway. */
function buildBench(g) {
  const bn = new THREE.Group();
  bn.position.set(6.94, 0, BED_Z);
  const W = 0.44, L = 1.50, SEAT = 0.45;

  [[-1, -1], [-1, 1], [1, -1], [1, 1]].forEach(([sx, sz]) => {
    const l = new THREE.Mesh(new THREE.BoxGeometry(0.028, 0.31, 0.028), MAT.steel);
    l.position.set(sx * (W / 2 - 0.045), 0.155, sz * (L / 2 - 0.075));
    l.castShadow = true;
    bn.add(l);
  });
  [-1, 1].forEach((sx) => {
    const r = new THREE.Mesh(new THREE.BoxGeometry(0.022, 0.022, L - 0.15), MAT.steel);
    r.position.set(sx * (W / 2 - 0.045), 0.285, 0);
    r.castShadow = true;
    bn.add(r);
  });

  const seat = shadowed(new THREE.Mesh(
    cushionGeo(W, 0.14, L, { corner: 5.0, wide: 5.0, edge: 0.26, sag: 0.008, wrinkle: 0.7, seg: 34, rings: 9 }),
    MAT.leather));
  seat.position.y = SEAT - 0.07;
  bn.add(seat);
  const welt = new THREE.Mesh(weltGeo(W * 0.99, L * 0.995, { corner: 5.0, radius: 0.008 }), MAT.leather);
  welt.castShadow = false;
  seat.add(welt);

  g.add(bn);
  return bn;
}

/* =============================================== what is on the tables == */
function buildBedsideThings(g) {
  /* Everything lives on the front half of the top; the lamps have the back
     half, which is what keeps the two from fighting over 42 cm of walnut. */
  const FRONT = HB_FACE + 0.33;

  /* North side: two books left where they were put down, and a glass. */
  const cols = [0x3d4a3f, 0x7a3b2c, 0x2f3038, 0x8a6a3a];
  let by = NS_TOP;
  for (let i = 0; i < 2; i++) {
    const h = 0.026 + rnd() * 0.012;
    const bk = new THREE.Mesh(
      roundedBoxGeo(0.145 - i * 0.01, h, 0.20 - i * 0.012, 0.004, 1), bookMat(pick(cols)));
    bk.position.set(FRONT, by + h / 2, NS_Z[1] - 0.10);
    bk.rotation.y = rrnd(-0.22, 0.22);
    bk.receiveShadow = true;                 // 30 mm of paperback casts nothing
    by += h;
    g.add(bk);
  }

  const glass = new THREE.Mesh(
    new THREE.CylinderGeometry(0.034, 0.029, 0.105, 14, 1, true), MAT.glassware);
  glass.position.set(FRONT, NS_TOP + 0.0525, NS_Z[1] + 0.11);
  g.add(glass);                              // glass throws a caustic, not a shadow
  const water = new THREE.Mesh(
    new THREE.CylinderGeometry(0.031, 0.0275, 0.055, 14),
    new THREE.MeshStandardMaterial({
      color: 0x8fa2a6, roughness: 0.04, metalness: 0, transparent: true, opacity: 0.35,
      envMapIntensity: 1.2,
    }));
  water.position.set(FRONT, NS_TOP + 0.028, NS_Z[1] + 0.11);
  g.add(water);

  /* South side: a small brass tray, and that is the whole of the brass in the
     room apart from the two lamp stems. */
  const tray = new THREE.Mesh(roundedBoxGeo(0.15, 0.012, 0.115, 0.005, 1), MAT.brass);
  tray.position.set(FRONT, NS_TOP + 0.006, NS_Z[0] + 0.10);
  tray.rotation.y = -0.12;
  tray.receiveShadow = true;
  g.add(tray);
}

/* ============================================================= plant ==== */
/* A sansevieria in the corner where the glazing meets the solid wall: stiff
   upright blades, no canopy, so it silhouettes against the city instead of
   turning into a grey cloud in the dark. Blades are tapered and bent planes —
   the leaf material is double-sided, so one quad each is enough. */
function buildPlant(g, x, z) {
  const p = new THREE.Group();
  p.position.set(x, 0, z);

  const pot = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.17, 0.145, 0.38, 16), MAT.clay));
  pot.position.y = 0.19;
  p.add(pot);
  const soil = new THREE.Mesh(new THREE.CircleGeometry(0.162, 16),
    new THREE.MeshStandardMaterial({ color: 0x1d1710, roughness: 1 }));
  soil.rotation.x = -Math.PI / 2;
  soil.position.y = 0.372;
  p.add(soil);

  for (let i = 0; i < 11; i++) {
    const h = rrnd(0.52, 0.95);
    const geo = new THREE.PlaneGeometry(0.075, h, 1, 5);
    geo.translate(0, h / 2, 0);
    const pos = geo.attributes.position;
    const bend = rrnd(0.06, 0.20);
    for (let k = 0; k < pos.count; k++) {
      // translate() leaves the bottom row a hair below zero, and a negative
      // base under a fractional power is NaN — which quietly poisons the
      // whole blade's bounding sphere
      const t = Math.max(0, pos.getY(k) / h);
      pos.setX(k, pos.getX(k) * (1 - 0.72 * Math.pow(t, 1.5)));
      pos.setZ(k, bend * t * t);
    }
    pos.needsUpdate = true;
    geo.computeVertexNormals();
    const blade = new THREE.Mesh(geo, MAT.leaf);
    const a = (i / 11) * Math.PI * 2 + rrnd(-0.25, 0.25);
    blade.position.set(Math.cos(a) * rrnd(0.02, 0.07), 0.36, Math.sin(a) * rrnd(0.02, 0.07));
    blade.rotation.set(0, a, rrnd(-0.10, 0.10));
    blade.castShadow = true;
    p.add(blade);
  }

  g.add(p);
  return p;
}

/* ========================================================== assemble ==== */
export function buildBedroom() {
  const g = new THREE.Group();
  g.name = 'bedroom';

  /* Wool, laid so it runs out well past the foot and both sides of the bed but
     stops short of the nightstands — a rug that disappears under joinery reads
     as fitted carpet. */
  const rug = new THREE.Mesh(roundedBoxGeo(3.20, 0.022, 2.60, 0.05, 1), MAT.rugDark);
  rug.position.set(6.66, 0.011, BED_Z + 0.03);
  rug.receiveShadow = true;
  g.add(rug);

  buildBed(g);
  buildNightstand(g, NS_Z[0]);
  buildNightstand(g, NS_Z[1]);
  buildWardrobe(g);
  buildBench(g);
  buildBedsideThings(g);
  buildPlant(g, 10.28, 3.42);

  // set back on the top, so the shade sits behind the book rather than over it
  const lamps = [
    buildLamp(g, HB_FACE + 0.11, NS_Z[0] - 0.02),
    buildLamp(g, HB_FACE + 0.11, NS_Z[1] + 0.02),
  ];

  return { group: g, lamps };
}

/* ---------------------------------------------------------- collision ----
   World AABBs, [minX, minY, minZ, maxX, maxY, maxZ]. The nightstand boxes are
   run back to the wall so they also close off the strip of headboard panel
   that sticks out past the bed. */
export const BEDROOM_COLLIDERS = [
  [WEST - 0.01, 0, 1.06, 6.63, HB_TOP, 3.04],          // bed and headboard
  [WEST - 0.01, 0, 0.66, 4.89, NS_TOP + 0.02, 1.14],   // nightstand, south
  [WEST - 0.01, 0, 2.96, 4.89, NS_TOP + 0.02, 3.44],   // nightstand, north
  [R.x0 + 0.08, 0, NORTH - 0.60, R.x0 + 3.72, APT.h, NORTH + 0.02],   // wardrobe
  [6.70, 0, 1.26, 7.18, 0.48, 2.84],                   // bench at the foot
];
