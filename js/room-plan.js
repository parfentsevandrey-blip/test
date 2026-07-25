/* =========================================================================
   The floor plan.

   One apartment on the 47th floor, four spaces, described once here: rooms as
   rectangles, walls as segments with openings cut in them, glazing as runs
   along the façade. The shell, the skirting, the coves, the collision boxes
   the walker uses and the camera presets are all generated from this, so the
   plan cannot drift out of step with any of them.

        z = -4  ┌──────────────────────────┬──────────────────┐
                │                          │                  │
                │        ГОСТИНАЯ          │  КУХНЯ-СТОЛОВАЯ  │  glazed south
                │      fireplace west      │                  │  and east
        z = 0.6 │                          ├──────────────────┤
                │                          │                  │
        z = 3   ├────────────┬─────────────┤     СПАЛЬНЯ      │
                │            │             │                  │
                │    ХОЛЛ    │   (bath)    │                  │
        z = 6.6 └────────────┴─────────────┴──────────────────┘
              x = -5                    x = 4              x = 11

   Living and kitchen are one open volume divided by a column and a downstand
   beam; everything else is walled with doorways.
   ========================================================================= */

export const APT = {
  h: 3.3,                 // ceiling
  wall: 0.11,             // interior partition thickness
  x0: -5, x1: 11,
  z0: -4, z1: 6.6,
};

/** Rooms, in the order the dock lists them. `floor` picks the finish. */
export const ROOMS = {
  living:  { x0: -5, x1: 4,  z0: -4,  z1: 3,   floor: 'oak',   soffit: 0,    name: 'Гостиная' },
  kitchen: { x0: 4,  x1: 11, z0: -4,  z1: 0.6, floor: 'stone', soffit: 0.42, name: 'Кухня' },
  bedroom: { x0: 4,  x1: 11, z0: 0.6, z1: 6.6, floor: 'oak',   soffit: 0,    name: 'Спальня' },
  hall:    { x0: -5, x1: 4,  z0: 3,   z1: 6.6, floor: 'stone', soffit: 0.42, name: 'Холл' },
};

export const roomAt = (x, z) => {
  for (const k of Object.keys(ROOMS)) {
    const r = ROOMS[k];
    if (x >= r.x0 && x <= r.x1 && z >= r.z0 && z <= r.z1) return k;
  }
  return null;
};

export const centre = (r) => [(r.x0 + r.x1) / 2, (r.z0 + r.z1) / 2];

/* ------------------------------------------------------------------ walls */
/* Segments run a → b. `openings` are measured from a along the segment:
   `at` is the centre, `w` the width, `h` the head height, `sill` the bottom
   (0 for a doorway, higher for a hatch). `door` fits a leaf and a lining. */
export const WALLS = [
  // living | hall, with the doorway through to the back of the apartment
  { a: [-5, 3], b: [4, 3], openings: [{ at: 7.1, w: 1.12, h: 2.42, door: 'open' }] },
  // hall | bedroom
  { a: [4, 3], b: [4, 6.6], openings: [{ at: 1.25, w: 1.02, h: 2.32, door: 'leaf' }] },
  // kitchen | bedroom
  { a: [4, 0.6], b: [11, 0.6], openings: [] },
];

/* Exterior solid walls (the rest of the perimeter is glass). The firebox is
   an opening in the west wall like any other, so the wall builder cuts round
   it instead of the fireplace having to rebuild that wall in four pieces. */
export const EXT_WALLS = [
  { a: [-5, -4], b: [-5, 6.6], openings: [{ at: 3.4, w: 1.75, h: 1.32, sill: 0.34 }] },
  { a: [-5, 6.6], b: [11, 6.6], openings: [] },   // north
  { a: [11, 3], b: [11, 6.6], openings: [] },     // east, past the glazing
];

/* ---------------------------------------------------------------- glazing */
/* Runs a → b along the façade. The inward normal is (-dz, dx), so the runs
   are wound so that "inward" really is into the apartment. `bays` sets the
   mullion spacing. */
export const GLAZING = [
  { a: [-5, -4], b: [4, -4], room: 'living', bays: 5 },
  { a: [4, -4], b: [11, -4], room: 'kitchen', bays: 4 },
  { a: [11, -4], b: [11, 0.6], room: 'kitchen', bays: 3 },
  { a: [11, 0.6], b: [11, 3], room: 'bedroom', bays: 2 },
];

/** unit vector into the apartment for a run */
export function inward(seg) {
  const dx = seg.b[0] - seg.a[0], dz = seg.b[1] - seg.a[1];
  const L = Math.hypot(dx, dz);
  return [-dz / L, dx / L];
}
export const segLen = (s) => Math.hypot(s.b[0] - s.a[0], s.b[1] - s.a[1]);

/* -------------------------------------------------------------- structure */
/* Living and kitchen are one volume; a column and a downstand beam are what
   tell you where one ends and the other begins. */
export const COLUMN = { x: 4, z: 1.55, w: 0.44, d: 0.44 };
export const BEAM = { x: 4, z0: -4, z1: 1.33, w: 0.34, drop: 0.42 };

/* ----------------------------------------------------------- collision ---
   Wall slabs and the column, as world AABBs. Furniture adds its own. */
export function planColliders() {
  const out = [];
  const t = APT.wall / 2 + 0.02;
  const push = (seg, openings) => {
    const [ax, az] = seg.a, [bx, bz] = seg.b;
    const L = Math.hypot(bx - ax, bz - az);
    const ux = (bx - ax) / L, uz = (bz - az) / L;
    // walk the segment, skipping the doorways
    const gaps = (openings || []).slice().sort((p, q) => p.at - q.at);
    let s = 0;
    const emit = (s0, s1) => {
      if (s1 - s0 < 0.05) return;
      const x0 = ax + ux * s0, z0 = az + uz * s0;
      const x1 = ax + ux * s1, z1 = az + uz * s1;
      out.push([Math.min(x0, x1) - t, 0, Math.min(z0, z1) - t,
                Math.max(x0, x1) + t, APT.h, Math.max(z0, z1) + t]);
    };
    // a firebox opening starts above the floor: you still cannot walk into it
    for (const g of gaps) {
      if ((g.sill || 0) > 0.05) continue;
      emit(s, g.at - g.w / 2); s = g.at + g.w / 2;
    }
    emit(s, L);
  };
  for (const w of WALLS) push(w, w.openings);
  for (const w of EXT_WALLS) push(w, w.openings);
  out.push([COLUMN.x - COLUMN.w / 2 - 0.02, 0, COLUMN.z - COLUMN.d / 2 - 0.02,
            COLUMN.x + COLUMN.w / 2 + 0.02, APT.h, COLUMN.z + COLUMN.d / 2 + 0.02]);
  return out;
}
