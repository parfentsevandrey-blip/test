/* =========================================================================
   The lighting rig.

   The room is lit almost entirely by the fireplace on the left wall, plus a
   couple of dim practicals and cool spill from the rainy city through the
   floor-to-ceiling glass.

   Two things this rig has to get right, because the previous one got both
   wrong. First, a real fire in a real room does not only throw light — it
   fills the room with light that has bounced off the floor, the ceiling and
   the facing wall. Without those bounces every surface out of direct throw
   falls to near-black and its material becomes invisible: the sofa measured
   0.048 mean luminance, about 2%. Second, a spot light wide enough to cover a
   room draws a hard cone boundary across the wall, which reads instantly as
   CG. The fix is not a wider spot — that only makes the shadow camera
   degenerate — but a narrow shadow-caster carrying the shaped light, with an
   unshadowed omni underneath it carrying the throw, so no edge exists.

   Everything is driven per frame from updateLights(), so the fire's flicker
   reaches the direct light and its bounces together.
   ========================================================================= */
import * as THREE from 'three';
import { ROOM, roomScene } from './room.js';
import { ROOMS, APT } from './room-plan.js';

const X = ROOM.x, Z = ROOM.z, H = ROOM.h;

/* CONTAINMENT. A point light does not know about walls: without a shadow map
   covering it, it lights straight through them. In one room that never came
   up. Measured across the finished flat, all eighteen sources reached all
   four rooms — the fire lit the bedroom two walls away, the bedside lamps lit
   the sofa, and a sconce in the windowless hall lit the kitchen. Everything
   summed everywhere, so no room had its own light and the whole flat took the
   fire's orange cast.
   Every `distance` below is therefore set to roughly the reach of the room
   the fixture stands in, and no further. The falloff is windowed, so a light
   contributes essentially nothing near its limit — the numbers are chosen so
   that limit lands on the far wall of its own room.

   Every light here is evaluated by every fragment of every material, so the
   count is a budget, not a wish list. Four rooms get seven extra sources
   between them: the pendants over the island read as one cluster, the two
   bedside lamps are genuinely two, the pair of hall sconces is one, and the
   kitchen and bedroom each get a ceiling bounce.

   That bounce is not optional. The living room has had three of them since
   the very first pass, for the reason written at the top of this file, and
   the three new rooms shipped without any: measured, the bedroom came out at
   0.066 mean luminance with 23% of the frame crushed to pure black. A lamp on
   a nightstand does not light a room — the ceiling above it does. */
export function buildLights(opts) {
  const { firePos, lampPos, chandPos, shadowSize,
          pendants = [], lamps = [], sconces = [] } = opts;
  const L = {};

  /* --- the constant floor of the room -------------------------------- */
  // never truly black: a night room still has sky through the glass and the
  // last of the city bouncing around it
  L.ambient = new THREE.AmbientLight(0x46495c, 0.60);
  roomScene.add(L.ambient);

  // sky above (cool, from the window and ceiling), ground below (warm, from
  // the oak floor) — cheap directionality that flat ambient cannot give
  // The ground half used to be strongly orange, which was fine when the only
  // room was the one with the fire in it. Turned up to light four rooms it
  // put a sunset on every wall in the flat.
  L.hemi = new THREE.HemisphereLight(0x5c74a0, 0x6a5a4a, 1.55);
  L.hemi.position.set(0, H, 0);
  roomScene.add(L.hemi);

  /* --- the fire ------------------------------------------------------- */
  // Narrow enough that the shadow camera stays well-conditioned; this one
  // carries the shaped light and the moving shadows.
  L.fireSpot = new THREE.SpotLight(0xff8f4a, 18, 9.0, 0.95, 1.0, 1.75);
  L.fireSpot.position.copy(firePos).add(new THREE.Vector3(0.25, 0.02, 0));
  L.fireSpot.target.position.set(0.40, 0.40, 0.20);
  L.fireSpot.castShadow = true;
  L.fireSpot.shadow.mapSize.set(shadowSize, shadowSize);
  L.fireSpot.shadow.camera.near = 0.30;
  L.fireSpot.shadow.camera.far = 11;
  L.fireSpot.shadow.bias = -0.0016;
  L.fireSpot.shadow.normalBias = 0.03;
  L.fireSpot.shadow.radius = 3;
  roomScene.add(L.fireSpot, L.fireSpot.target);

  // No cone, so the spot's boundary never appears as an edge — this is what
  // removes the hard black line the fire used to draw across the wall.
  L.fireWide = new THREE.PointLight(0xff7d33, 9.0, 8.0, 1.6);
  L.fireWide.position.copy(firePos).add(new THREE.Vector3(0.30, 0.10, 0));
  roomScene.add(L.fireWide);

  // tight and dim, just enough to model the logs inside the firebox
  L.fireCore = new THREE.PointLight(0xff8134, 2.2, 1.9, 2.0);
  L.fireCore.position.copy(firePos).add(new THREE.Vector3(-0.30, -0.18, 0));
  roomScene.add(L.fireCore);

  /* --- bounce -------------------------------------------------------- */
  // light returning off the oiled oak in front of the hearth: warm, low, and
  // aimed back up into the room, which is what actually reaches the sofa
  L.bounceFloor = new THREE.PointLight(0xff9048, 5.2, 7.0, 1.5);
  L.bounceFloor.position.set(-2.0, 0.30, -0.30);
  roomScene.add(L.bounceFloor);

  // and off the ceiling above the fire, spreading much further than the fire
  // itself and lifting the whole back half of the room
  L.bounceCeil = new THREE.PointLight(0xf0955a, 3.2, 7.5, 1.5);
  L.bounceCeil.position.set(-1.2, H - 0.35, 0.30);
  roomScene.add(L.bounceCeil);

  // and off the back wall, which is the only thing lighting the back of the
  // sofa — the darkest surface in the room and the foreground of every wide shot
  L.bounceBack = new THREE.PointLight(0xe08a52, 3.4, 6.0, 1.5);
  L.bounceBack.position.set(0.9, 1.9, ROOMS.living.z1 - 0.6);
  roomScene.add(L.bounceBack);

  /* --- practicals ----------------------------------------------------- */
  /* The glass cluster over the seating. A SpotLight with a wide penumbra
     throws the soft-edged pool a real fixture makes; a point light in the
     same place lights the walls and the ceiling equally and reads as a glow
     with nothing above it. A little up-spill comes back from bounceCeil. */
  L.chandelier = new THREE.SpotLight(0xffbe86, 17, 7.0, 1.22, 0.95, 1.40);
  L.chandelier.position.copy(chandPos || new THREE.Vector3(-0.85, 2.35, 0.30));
  L.chandelier.target.position.set(
    (chandPos ? chandPos.x : -0.85), 0.35, (chandPos ? chandPos.z : 0.30));
  roomScene.add(L.chandelier, L.chandelier.target);

  L.lamp = new THREE.PointLight(0xffab5e, 13, 6.0, 1.65);
  L.lamp.position.copy(lampPos);
  roomScene.add(L.lamp);

  // follows the bookshelf, which moved to the west wall when the east one
  // became the kitchen
  L.shelf = new THREE.PointLight(0xffa055, 3.0, 4.0, 1.9);
  L.shelf.position.set(ROOMS.living.x0 + 0.55, 1.55, 2.15);
  roomScene.add(L.shelf);

  L.cove = new THREE.PointLight(0xffb877, 4.0, 7.0, 1.55);
  L.cove.position.set(ROOMS.living.x0 + 1.4, H - 0.25, ROOMS.living.z1 - 1.0);
  roomScene.add(L.cove);

  /* --- the other rooms ------------------------------------------------ */
  // Over the island. Three pendants a metre apart are one source at any
  // distance you actually see them from, and a third of the shader cost.
  const K = ROOMS.kitchen;
  const kp = pendants.length
    ? pendants.reduce((a, p) => a.add(p.lightPos), new THREE.Vector3()).multiplyScalar(1 / pendants.length)
    : new THREE.Vector3((K.x0 + K.x1) / 2, 1.55, -1.4);
  // A cone alone lights the island and leaves the room round it black —
  // measured, the kitchen lost a third of its mean when the pendants stopped
  // being point lights. The cone is the pool you see; kBounce below is the
  // light the same shades throw up at the ceiling and back down.
  L.pendant = new THREE.SpotLight(0xffbe86, 24, 7.5, 1.16, 0.92, 1.30);
  L.pendant.position.copy(kp);
  L.pendant.target.position.set(kp.x, 0.92, kp.z);
  roomScene.add(L.pendant, L.pendant.target);

  // the worktop run, washed from under the wall units
  L.worktop = new THREE.PointLight(0xffc490, 8.0, 4.8, 1.55);
  L.worktop.position.set((K.x0 + K.x1) / 2 + 0.6, 1.42, K.z1 - 0.55);
  roomScene.add(L.worktop);

  L.bedside = lamps.slice(0, 2).map((lp, i) => {
    const pl = new THREE.PointLight(0xffa663, 13, 5.5, 1.42);
    pl.position.copy(lp.lightPos);
    roomScene.add(pl);
    return pl;
  });
  if (!L.bedside.length) {
    const B = ROOMS.bedroom;
    const pl = new THREE.PointLight(0xffa663, 13, 5.5, 1.42);
    pl.position.set(B.x1 - 1.1, 0.66, B.z1 - 1.4);
    roomScene.add(pl);
    L.bedside = [pl];
  }

  // both hall sconces as one source, midway between them
  const H_ = ROOMS.hall;
  const sp = sconces.length
    ? sconces.reduce((a, s2) => a.add(s2.lightPos), new THREE.Vector3()).multiplyScalar(1 / sconces.length)
    : new THREE.Vector3(-2.0, 1.75, H_.z1 - 0.3);
  L.sconce = new THREE.PointLight(0xffb473, 10.5, 6.5, 1.28);
  L.sconce.position.copy(sp);
  roomScene.add(L.sconce);

  // ceiling bounce: what a pendant and a pair of bedside lamps actually put
  // into a room, as opposed to what they put on the surface right under them
  const B = ROOMS.bedroom;
  L.kBounce = new THREE.PointLight(0xe8c9a4, 9.5, 8.0, 1.20);
  L.kBounce.position.set((K.x0 + K.x1) / 2, H - 0.45, (K.z0 + K.z1) / 2 - 0.3);
  roomScene.add(L.kBounce);

  // the bedroom has a window as well as a lamp, so its bounce is not fire-warm
  L.bBounce = new THREE.PointLight(0xd9c3a8, 4.4, 7.5, 1.25);
  L.bBounce.position.set((B.x0 + B.x1) / 2 + 0.6, H - 0.45, (B.z0 + B.z1) / 2);
  roomScene.add(L.bBounce);

  /* --- the storm outside ---------------------------------------------- */
  // A directional has no range, so this one reaches the hall, which has no
  // windows. Turned down and let the local fills do the work of the glass.
  L.window = new THREE.DirectionalLight(0x8fb0e0, 0.62);
  L.window.position.set(-2, 6, -18);
  L.window.target.position.set(0, 0.6, 2);
  roomScene.add(L.window, L.window.target);

  // the glass is a broad cool source in its own right; a soft fill just inside
  // it keeps the floor and the near end of the sofa from going flat
  L.windowFill = new THREE.PointLight(0x86a8d8, 6.0, 8.0, 1.45);
  L.windowFill.position.set(0.0, 1.5, ROOMS.living.z0 + 0.7);
  roomScene.add(L.windowFill);

  return L;
}

/* ------------------------------------------------------------ per frame --
   ctx = { fire, flick, lampLevel, flash, t, firePos }
   The bounces flicker with the fire too — light that has bounced once still
   carries the flame's modulation, and holding them steady while the direct
   light dances is a giveaway. */
export function updateLights(L, ctx) {
  const { fire, flick, lampLevel, flash, t, firePos } = ctx;
  const f = fire * flick;

  L.fireSpot.intensity = 18 * f;
  L.fireWide.intensity = 9.0 * f;
  L.fireCore.intensity = 2.2 * f;
  L.fireSpot.position.x = firePos.x + 0.25 + Math.sin(t * 3.1) * 0.03;
  L.fireSpot.position.z = firePos.z + Math.sin(t * 2.3) * 0.06;

  // bounce lags the flame very slightly and varies less — it is an average of
  // the flame over a whole wall, not the flame itself
  const soft = fire * (0.72 + flick * 0.28);
  L.bounceFloor.intensity = 5.2 * soft;
  L.bounceCeil.intensity = 3.2 * soft;

  L.lamp.intensity = 13 * lampLevel;
  L.chandelier.intensity = 17 * lampLevel;
  L.shelf.intensity = 3.0 * lampLevel;
  L.cove.intensity = 4.0 * lampLevel;

  // The other rooms are on the same switch, but never go fully dark — a flat
  // with the lamps off still has the city coming in and light spilling round
  // a doorway, and rooms that hit pure black just read as holes.
  const k = 0.16 + 0.84 * lampLevel;
  L.pendant.intensity = 24 * k;
  L.worktop.intensity = 8.0 * k;
  L.sconce.intensity = 10.5 * k;
  L.kBounce.intensity = 9.5 * k;
  L.bBounce.intensity = 4.4 * (0.14 + 0.86 * lampLevel);
  for (const b of L.bedside) b.intensity = 13 * (0.14 + 0.86 * lampLevel);

  L.bounceBack.intensity = 3.4 * soft;
  L.window.intensity = 0.62 + flash * 2.6;
  L.windowFill.intensity = 6.0 + flash * 6.0;
}
