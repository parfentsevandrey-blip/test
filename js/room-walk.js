/* =========================================================================
   First-person walking.

   WASD (and the arrow keys) move; the mouse looks around once the canvas has
   pointer lock; Shift walks faster, Ctrl crouches. Escape or the dock button
   returns to the orbiting preset views.

   Collision is a capsule against a small list of axis-aligned boxes, resolved
   one axis at a time so you slide along a wall instead of sticking to it. The
   room is a shoebox with a dozen pieces of furniture, so a broadphase would
   cost more than it saves.
   ========================================================================= */
import * as THREE from 'three';
import { ROOM, clamp, damp } from './room.js';

const EYE = 1.62;          // standing eye height, metres
const CROUCH = 1.05;
const RADIUS = 0.30;       // body radius for collision
const STEP = 0.28;         // obstacles lower than this are stepped over

export class Walker {
  constructor(camera, canvas) {
    this.camera = camera;
    this.canvas = canvas;
    this.active = false;

    this.pos = new THREE.Vector3(1.6, EYE, 2.2);
    this.vel = new THREE.Vector3();
    this.yaw = Math.PI;                 // facing the window
    this.pitch = -0.05;
    this.eye = EYE;
    this.bob = 0;
    this.keys = new Set();
    this.colliders = [];
    this.onExit = null;

    this._tmp = new THREE.Vector3();
    this._fwd = new THREE.Vector3();
    this._right = new THREE.Vector3();

    this._onKeyDown = (e) => {
      if (e.code === 'Escape') { this.stop(); return; }
      this.keys.add(e.code);
      if (this.active && MOVE_KEYS.has(e.code)) e.preventDefault();
    };
    this._onKeyUp = (e) => this.keys.delete(e.code);
    this._onMouse = (e) => {
      if (!this.active) return;
      this.yaw -= e.movementX * 0.0022;
      this.pitch = clamp(this.pitch - e.movementY * 0.0020, -1.15, 1.05);
    };
    this._onLockChange = () => {
      if (document.pointerLockElement !== this.canvas && this.active) this.stop();
    };

    window.addEventListener('keydown', this._onKeyDown);
    window.addEventListener('keyup', this._onKeyUp);
    document.addEventListener('mousemove', this._onMouse);
    document.addEventListener('pointerlockchange', this._onLockChange);
  }

  /** boxes are [minX, minY, minZ, maxX, maxY, maxZ] in world space */
  setColliders(boxes) { this.colliders = boxes; }

  start(from) {
    if (this.active) return;
    this.active = true;
    if (from) {
      this.pos.set(from.x, this.eye, from.z);
      // keep looking where the orbit camera was looking
      this.yaw = from.yaw ?? this.yaw;
      this.pitch = from.pitch ?? this.pitch;
    }
    this.clampInside();
    this.canvas.requestPointerLock?.();
    document.body.classList.add('walking');
  }

  stop() {
    if (!this.active) return;
    this.active = false;
    this.keys.clear();
    this.vel.set(0, 0, 0);
    if (document.pointerLockElement === this.canvas) document.exitPointerLock?.();
    document.body.classList.remove('walking');
    if (this.onExit) this.onExit();
  }

  /** true if a point at (x, z) with the body radius is inside something solid */
  blocked(x, y, z) {
    for (let i = 0; i < this.colliders.length; i++) {
      const b = this.colliders[i];
      if (b[4] <= y - this.eye + STEP) continue;              // low enough to step over
      if (b[1] >= y + 0.10) continue;                          // above the head
      if (x + RADIUS > b[0] && x - RADIUS < b[3] &&
          z + RADIUS > b[2] && z - RADIUS < b[5]) return true;
    }
    return false;
  }

  clampInside() {
    const m = RADIUS + 0.06;
    this.pos.x = clamp(this.pos.x, -ROOM.x + m, ROOM.x - m);
    this.pos.z = clamp(this.pos.z, -ROOM.z + m, ROOM.z - m);
  }

  update(dt) {
    if (!this.active) return false;

    const k = this.keys;
    let fx = 0, fz = 0;
    if (k.has('KeyW') || k.has('ArrowUp')) fz += 1;
    if (k.has('KeyS') || k.has('ArrowDown')) fz -= 1;
    if (k.has('KeyA') || k.has('ArrowLeft')) fx -= 1;
    if (k.has('KeyD') || k.has('ArrowRight')) fx += 1;
    const len = Math.hypot(fx, fz);
    if (len > 0) { fx /= len; fz /= len; }

    const crouching = k.has('ControlLeft') || k.has('ControlRight') || k.has('KeyC');
    const running = k.has('ShiftLeft') || k.has('ShiftRight');
    this.eye = damp(this.eye, crouching ? CROUCH : EYE, 9, dt);

    // a person walks about 1.3 m/s indoors; anything faster reads as a game
    const speed = (crouching ? 0.7 : running ? 2.1 : 1.25);
    const sin = Math.sin(this.yaw), cos = Math.cos(this.yaw);
    const wantX = (-sin * fz + cos * fx) * speed;
    const wantZ = (-cos * fz - sin * fx) * speed;

    // accelerate and decelerate rather than snapping, or it feels like a cursor
    this.vel.x = damp(this.vel.x, wantX, len > 0 ? 11 : 14, dt);
    this.vel.z = damp(this.vel.z, wantZ, len > 0 ? 11 : 14, dt);

    // resolve one axis at a time so a wall slides instead of stopping you dead
    const nx = this.pos.x + this.vel.x * dt;
    if (!this.blocked(nx, this.pos.y, this.pos.z)) this.pos.x = nx; else this.vel.x = 0;
    const nz = this.pos.z + this.vel.z * dt;
    if (!this.blocked(this.pos.x, this.pos.y, nz)) this.pos.z = nz; else this.vel.z = 0;
    this.clampInside();

    // head bob, scaled by how fast you are actually moving
    const moving = Math.hypot(this.vel.x, this.vel.z);
    this.bob += dt * moving * 6.4;
    const amp = Math.min(moving / speed, 1) * 0.022;
    const bobY = Math.sin(this.bob * 2) * amp;
    const bobX = Math.sin(this.bob) * amp * 0.6;

    this.camera.position.set(
      this.pos.x + Math.cos(this.yaw) * bobX,
      this.eye + bobY,
      this.pos.z - Math.sin(this.yaw) * bobX,
    );
    this.camera.rotation.set(0, 0, 0);
    this.camera.rotateY(this.yaw);
    this.camera.rotateX(this.pitch);
    // a touch of roll as you lean into a strafe
    this.camera.rotateZ(-this.vel.x * 0.006 * Math.cos(this.yaw) - this.vel.z * 0.006 * Math.sin(this.yaw));
    return true;
  }

  /** where the eye is looking, for depth-of-field focus */
  focusDistance() {
    // analytic ray-vs-room-box: cheap, and good enough for a shoebox
    const dx = -Math.sin(this.yaw) * Math.cos(this.pitch);
    const dz = -Math.cos(this.yaw) * Math.cos(this.pitch);
    const dy = Math.sin(this.pitch);
    let t = 12;
    const hit = (d, lo, hi, p) => {
      if (Math.abs(d) < 1e-4) return;
      const a = (lo - p) / d, b = (hi - p) / d;
      const m = Math.max(a, b);
      if (m > 0 && m < t) t = m;
    };
    hit(dx, -ROOM.x, ROOM.x, this.pos.x);
    hit(dz, -ROOM.z, ROOM.z, this.pos.z);
    hit(dy, 0, ROOM.h, this.eye);
    return clamp(t, 1.1, 9);
  }

  dispose() {
    window.removeEventListener('keydown', this._onKeyDown);
    window.removeEventListener('keyup', this._onKeyUp);
    document.removeEventListener('mousemove', this._onMouse);
    document.removeEventListener('pointerlockchange', this._onLockChange);
  }
}

const MOVE_KEYS = new Set([
  'KeyW', 'KeyA', 'KeyS', 'KeyD', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Space',
]);

/** the solid things in the room, as world-space AABBs */
export function roomColliders() {
  const X = ROOM.x, Z = ROOM.z;
  return [
    // the stone fireplace facing, which stands proud of the left wall
    [-X, 0, -2.6, -X + 0.72, 3.3, 1.4],
    // sofa
    [-1.40, 0, 1.40, 1.70, 0.90, 2.50],
    // armchairs
    [-3.05, 0, 0.80, -2.05, 0.95, 1.80],
    [2.45, 0, -1.35, 3.45, 0.95, -0.35],
    // coffee table
    [-1.25, 0, -0.10, 0.15, 0.45, 0.70],
    // pouf, side table, floor lamp base
    [-2.85, 0, -1.75, -2.05, 0.40, -0.95],
    [-2.20, 0, 2.35, -1.50, 0.60, 3.05],
    [-3.55, 0, 1.65, -3.05, 1.60, 2.15],
    // bookshelf along the right wall
    [X - 0.36, 0, 0.45, X, 2.4, 3.35],
    // side table by the window chair
    [3.64, 0, -2.01, 4.16, 0.60, -1.49],
    // log basket at the hearth
    [-4.66, 0, 0.74, -4.04, 0.40, 1.36],
    // plants
    [3.55, 0, -3.25, 4.15, 1.9, -2.65],
    [-3.85, 0, 2.65, -3.35, 1.3, 3.15],
  ];
}
