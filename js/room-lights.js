/* =========================================================================
   The lighting rig.

   The room is lit almost entirely by the fireplace on the left wall, with a
   couple of dim practicals and cool spill from the rainy city through the
   floor-to-ceiling glass. Everything here is driven per-frame from
   updateLights() below, so the fire's flicker reaches every light at once.
   ========================================================================= */
import * as THREE from 'three';
import { ROOM, roomScene } from './room.js';

const X = ROOM.x, Z = ROOM.z, H = ROOM.h;

export function buildLights(firePos, lampPos, shadowSize) {
  const L = {};

  L.ambient = new THREE.AmbientLight(0x3a4762, 0.68);
  roomScene.add(L.ambient);

  L.hemi = new THREE.HemisphereLight(0x51688e, 0x442a18, 1.05);
  roomScene.add(L.hemi);

  /* the fire — one wide shadow-casting spot pointed into the room */
  L.fireSpot = new THREE.SpotLight(0xff8f4a, 20, 26, 0.88, 1.0, 1.85);
  L.fireSpot.position.copy(firePos).add(new THREE.Vector3(0.25, 0.02, 0));
  L.fireSpot.target.position.set(1.20, 0.50, 0.05);
  L.fireSpot.castShadow = true;
  L.fireSpot.shadow.mapSize.set(shadowSize, shadowSize);
  L.fireSpot.shadow.camera.near = 0.30;
  L.fireSpot.shadow.camera.far = 16;
  L.fireSpot.shadow.bias = -0.0016;
  L.fireSpot.shadow.normalBias = 0.03;
  L.fireSpot.shadow.radius = 3;
  roomScene.add(L.fireSpot, L.fireSpot.target);

  /* close-range fill so the stone around the opening glows */
  L.fireFill = new THREE.PointLight(0xff7028, 3.0, 11.0, 1.85);
  L.fireFill.position.copy(firePos).add(new THREE.Vector3(0.22, 0.18, 0));
  roomScene.add(L.fireFill);

  /* a small light inside the firebox so the logs read as logs */
  L.fireCore = new THREE.PointLight(0xff8134, 2.2, 1.9, 2.0);
  L.fireCore.position.copy(firePos).add(new THREE.Vector3(-0.30, -0.18, 0));
  roomScene.add(L.fireCore);

  /* practicals */
  L.lamp = new THREE.PointLight(0xffab5e, 9.0, 9.0, 2.0);
  L.lamp.position.copy(lampPos);
  roomScene.add(L.lamp);

  L.shelf = new THREE.PointLight(0xffa055, 2.4, 5.0, 2.0);
  L.shelf.position.set(X - 0.55, 1.55, 1.9);
  roomScene.add(L.shelf);

  L.cove = new THREE.PointLight(0xffb877, 3.2, 11.0, 2.0);
  L.cove.position.set(-X + 1.2, H - 0.25, Z - 1.2);
  roomScene.add(L.cove);

  L.door = new THREE.PointLight(0xd07d38, 1.6, 4.5, 2.0);
  L.door.position.set(3.35, 1.5, Z - 0.5);
  roomScene.add(L.door);

  /* cool spill from the storm outside */
  L.window = new THREE.DirectionalLight(0x8fb0e0, 0.95);
  L.window.position.set(-2, 6, -18);
  L.window.target.position.set(0, 0.6, 2);
  roomScene.add(L.window, L.window.target);

  return L;
}


/* ------------------------------------------------------------ per frame --
   ctx = { fire, flick, lampLevel, flash, t, firePos } */
export function updateLights(L, ctx) {
  const { fire, flick, lampLevel, flash, t, firePos } = ctx;
  L.fireSpot.intensity = 20 * fire * flick;
  L.fireFill.intensity = 3.1 * fire * flick;
  L.fireCore.intensity = 2.4 * fire * flick;
  L.fireSpot.position.x = firePos.x + 0.25 + Math.sin(t * 3.1) * 0.03;
  L.fireSpot.position.z = firePos.z + Math.sin(t * 2.3) * 0.06;
  L.lamp.intensity = 7.5 * lampLevel;
  L.shelf.intensity = 2.4 * lampLevel;
  L.cove.intensity = 2.0 * lampLevel;
  L.window.intensity = 0.32 + flash * 2.6;
}
