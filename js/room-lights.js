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

const X = ROOM.x, Z = ROOM.z, H = ROOM.h;

export function buildLights(firePos, lampPos, shadowSize) {
  const L = {};

  /* --- the constant floor of the room -------------------------------- */
  // never truly black: a night room still has sky through the glass and the
  // last of the city bouncing around it
  L.ambient = new THREE.AmbientLight(0x46495c, 0.42);
  roomScene.add(L.ambient);

  // sky above (cool, from the window and ceiling), ground below (warm, from
  // the oak floor) — cheap directionality that flat ambient cannot give
  L.hemi = new THREE.HemisphereLight(0x5c74a0, 0x6b4526, 1.15);
  L.hemi.position.set(0, H, 0);
  roomScene.add(L.hemi);

  /* --- the fire ------------------------------------------------------- */
  // Narrow enough that the shadow camera stays well-conditioned; this one
  // carries the shaped light and the moving shadows.
  L.fireSpot = new THREE.SpotLight(0xff8f4a, 18, 26, 0.95, 1.0, 1.75);
  L.fireSpot.position.copy(firePos).add(new THREE.Vector3(0.25, 0.02, 0));
  L.fireSpot.target.position.set(0.40, 0.40, 0.20);
  L.fireSpot.castShadow = true;
  L.fireSpot.shadow.mapSize.set(shadowSize, shadowSize);
  L.fireSpot.shadow.camera.near = 0.30;
  L.fireSpot.shadow.camera.far = 16;
  L.fireSpot.shadow.bias = -0.0016;
  L.fireSpot.shadow.normalBias = 0.03;
  L.fireSpot.shadow.radius = 3;
  roomScene.add(L.fireSpot, L.fireSpot.target);

  // No cone, so the spot's boundary never appears as an edge — this is what
  // removes the hard black line the fire used to draw across the wall.
  L.fireWide = new THREE.PointLight(0xff7d33, 9.0, 18, 1.6);
  L.fireWide.position.copy(firePos).add(new THREE.Vector3(0.30, 0.10, 0));
  roomScene.add(L.fireWide);

  // tight and dim, just enough to model the logs inside the firebox
  L.fireCore = new THREE.PointLight(0xff8134, 2.2, 1.9, 2.0);
  L.fireCore.position.copy(firePos).add(new THREE.Vector3(-0.30, -0.18, 0));
  roomScene.add(L.fireCore);

  /* --- bounce -------------------------------------------------------- */
  // light returning off the oiled oak in front of the hearth: warm, low, and
  // aimed back up into the room, which is what actually reaches the sofa
  L.bounceFloor = new THREE.PointLight(0xff9048, 5.2, 11, 1.5);
  L.bounceFloor.position.set(-2.0, 0.30, -0.30);
  roomScene.add(L.bounceFloor);

  // and off the ceiling above the fire, spreading much further than the fire
  // itself and lifting the whole back half of the room
  L.bounceCeil = new THREE.PointLight(0xf0955a, 3.0, 14, 1.5);
  L.bounceCeil.position.set(-1.2, H - 0.35, 0.30);
  roomScene.add(L.bounceCeil);

  // and off the back wall, which is the only thing lighting the back of the
  // sofa — the darkest surface in the room and the foreground of every wide shot
  L.bounceBack = new THREE.PointLight(0xe08a52, 3.4, 9, 1.5);
  L.bounceBack.position.set(0.9, 1.9, Z - 0.9);
  roomScene.add(L.bounceBack);

  /* --- practicals ----------------------------------------------------- */
  L.lamp = new THREE.PointLight(0xffab5e, 13, 8, 1.9);
  L.lamp.position.copy(lampPos);
  roomScene.add(L.lamp);

  L.shelf = new THREE.PointLight(0xffa055, 3.0, 5.5, 2.0);
  L.shelf.position.set(X - 0.55, 1.55, 1.9);
  roomScene.add(L.shelf);

  L.cove = new THREE.PointLight(0xffb877, 4.0, 12, 1.8);
  L.cove.position.set(-X + 1.2, H - 0.25, Z - 1.2);
  roomScene.add(L.cove);

  L.door = new THREE.PointLight(0xd07d38, 1.8, 4.5, 2.0);
  L.door.position.set(3.35, 1.5, Z - 0.5);
  roomScene.add(L.door);

  /* --- the storm outside ---------------------------------------------- */
  L.window = new THREE.DirectionalLight(0x8fb0e0, 0.95);
  L.window.position.set(-2, 6, -18);
  L.window.target.position.set(0, 0.6, 2);
  roomScene.add(L.window, L.window.target);

  // the glass is a broad cool source in its own right; a soft fill just inside
  // it keeps the floor and the near end of the sofa from going flat
  L.windowFill = new THREE.PointLight(0x86a8d8, 5.0, 13, 1.5);
  L.windowFill.position.set(0.6, 1.5, -3.3);
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
  L.bounceCeil.intensity = 3.0 * soft;

  L.lamp.intensity = 13 * lampLevel;
  L.shelf.intensity = 3.0 * lampLevel;
  L.cove.intensity = 4.0 * lampLevel;

  L.bounceBack.intensity = 3.4 * soft;
  L.window.intensity = 0.95 + flash * 2.6;
  L.windowFill.intensity = 5.0 + flash * 6.0;
}
