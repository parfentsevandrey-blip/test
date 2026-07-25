/* =========================================================================
   Luminaires.

   The complaint was that the light reads as a primitive blob, and it did:
   every fixture in the flat was a cylinder with a glowing disc inside it, so
   what you actually saw was bloom with no object under it.

   What makes a lamp read as a lamp at night is not the bulb. It is the shade
   — a fabric or glass shade lit from the inside glows unevenly, brightest
   where the bulb sits behind it, and it is much brighter seen from inside
   than out. That gradient is the whole tell, and a uniform `emissive` cannot
   produce it. So every shade here gets a small shader injection that adds a
   glow profiled in the shade's own local space, with the back faces lifted,
   and the weave breaking it up.

   The second tell is that a shade throws light somewhere specific: a drum
   throws a disc up at the ceiling and a pool down, a cone throws a hard-edged
   circle on the worktop. Those come from the rig in room-lights.js; what this
   file guarantees is that there is a believable object where the light says
   it comes from.

   Every fixture returns { group, lightPos, setGlow }, so the app can dim the
   emissive parts with the same switch that dims the lights.
   ========================================================================= */
import * as THREE from 'three';
import { GLSL_NOISE, U } from './room.js';
import { MAT, shadowed } from './room-mat.js';

/* ------------------------------------------------------------ shade glow */
let shadeKey = 0;

/**
 * Fabric or alabaster lit from within.
 *   bulbY   height of the bulb in shade-local space
 *   spread  how far the hot patch reaches up and down the shade
 *   glow    strength; the app scales it with the light switch
 *   inside  how much brighter the inner face is than the outer
 */
export function shadeMaterial(o = {}) {
  const m = new THREE.MeshStandardMaterial({
    color: o.color ?? 0xe6d6b8,
    roughness: o.roughness ?? 0.92,
    metalness: 0,
    side: THREE.DoubleSide,
    envMapIntensity: o.env ?? 0.3,
  });
  const u = {
    uGlow: { value: o.glow ?? 1.0 },
    uGlowCol: { value: new THREE.Color(o.glowColor ?? 0xffb066) },
    uBulbY: { value: o.bulbY ?? 0.0 },
    uSpread: { value: o.spread ?? 0.13 },
    uInside: { value: o.inside ?? 2.4 },
    uWeave: { value: o.weave ?? 0.35 },
  };
  const key = 'shade' + (shadeKey++);
  m.onBeforeCompile = (sh) => {
    Object.assign(sh.uniforms, u);
    sh.vertexShader = sh.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vShadeP;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvShadeP = position;');
    sh.fragmentShader = GLSL_NOISE + sh.fragmentShader
      .replace('#include <common>', `#include <common>
        varying vec3 vShadeP;
        uniform float uGlow, uBulbY, uSpread, uInside, uWeave;
        uniform vec3 uGlowCol;`)
      .replace('#include <tonemapping_fragment>', `
        {
          // hottest level with the bulb, falling away up and down the shade
          float d = (vShadeP.y - uBulbY) / uSpread;
          float core = exp(-d * d);
          // and a little hotter round the back of a shade that is open at both
          // ends, because you are seeing the lit inside face through the cloth
          float face = gl_FrontFacing ? 1.0 : uInside;
          // the weave is what stops it looking like an airbrushed gradient
          float w = 1.0 - uWeave + uWeave * fbm2(vec2(atan(vShadeP.z, vShadeP.x) * 5.0,
                                                      vShadeP.y * 46.0));
          // Clamped: added straight onto the lit result, an unclamped glow runs
          // past 2.0 on the inner face, tone-maps to white and blooms into the
          // ball this file exists to get rid of.
          gl_FragColor.rgb += uGlowCol * min(uGlow * core * face * w, 0.95);
        }
        #include <tonemapping_fragment>`);
  };
  m.customProgramCacheKey = () => key;
  m.userData.glowUniforms = u;
  return m;
}

/** a small hot glass envelope; hidden inside most shades, visible in a globe */
function bulbMaterial(hex) {
  return new THREE.MeshBasicMaterial({ color: hex, toneMapped: false });
}

/** wires the emissive parts of a fixture to one dimmer */
function dimmer(shades, basics) {
  const base = basics.map((m) => m.color.clone());
  const g0 = shades.map((m) => m.userData.glowUniforms.uGlow.value);
  return (k) => {
    shades.forEach((m, i) => { m.userData.glowUniforms.uGlow.value = g0[i] * k; });
    basics.forEach((m, i) => { m.color.copy(base[i]).multiplyScalar(k); });
  };
}

/* ============================================================ chandelier == */
/* Five hand-blown globes on cables of different lengths from one plate. A
   cluster reads as a designed object from any angle, which a single pendant
   over a sofa never does, and each globe is its own small highlight in the
   dark glass and the oiled floor. */
export function chandelier(x, z, ceilY, o = {}) {
  const g = new THREE.Group();
  g.position.set(x, 0, z);

  const canopy = shadowed(new THREE.Mesh(
    new THREE.CylinderGeometry(0.19, 0.21, 0.035, 20), MAT.steel));
  canopy.position.y = ceilY - 0.018;
  g.add(canopy);

  const glass = new THREE.MeshStandardMaterial({
    color: 0xe0cdaa, roughness: 0.22, metalness: 0,
    transparent: true, opacity: 0.42, envMapIntensity: 1.3, side: THREE.DoubleSide,
  });
  // small and warm, not white: the nearest globe is the one that blooms
  const filMat = bulbMaterial(o.bulbColor ?? 0x6d4a28);

  const drops = o.drops ?? [
    [0.00, 0.00, 0.86, 0.105],
    [0.26, 0.13, 1.14, 0.086],
    [-0.24, 0.10, 1.30, 0.078],
    [0.13, -0.25, 1.02, 0.092],
    [-0.16, -0.22, 1.42, 0.070],
  ];
  let cy = 0;
  for (const [dx, dz, drop, r] of drops) {
    const y = ceilY - drop;
    const cord = new THREE.Mesh(
      new THREE.CylinderGeometry(0.0035, 0.0035, drop - r, 5), MAT.steel);
    cord.position.set(dx, y + r + (drop - r) / 2, dz);
    g.add(cord);

    // a brass collar where the cord enters the glass reads as a fitting
    const collar = new THREE.Mesh(new THREE.CylinderGeometry(r * 0.30, r * 0.36, 0.026, 12), MAT.brass);
    collar.position.set(dx, y + r * 0.86, dz);
    collar.castShadow = true;
    g.add(collar);

    const globe = new THREE.Mesh(new THREE.SphereGeometry(r, 18, 12), glass);
    globe.position.set(dx, y, dz);
    g.add(globe);

    // the filament: small, and the only thing in the fixture that is truly hot
    const fil = new THREE.Mesh(new THREE.CapsuleGeometry(r * 0.12, r * 0.42, 3, 6), filMat);
    fil.position.set(dx, y + r * 0.06, dz);
    g.add(fil);
    cy += y;
  }
  cy /= drops.length;

  return {
    group: g,
    lightPos: new THREE.Vector3(x, cy, z),
    setGlow: dimmer([], [filMat]),
  };
}

/* =============================================================== pendant == */
/* A cone over a worktop. The inside is brass, not the shade colour: that
   bounce is most of what you see of a pendant from below, and painting it the
   same matt tone as the outside is what made these read as paper cups. */
export function pendant(x, y, z, ceilY, o = {}) {
  const g = new THREE.Group();
  const rTop = o.rTop ?? 0.055, rBot = o.rBot ?? 0.16, h = o.height ?? 0.20;

  const canopy = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.055, 0.02, 14), MAT.steel);
  canopy.position.set(x, ceilY - 0.01, z);
  g.add(canopy);
  const cord = new THREE.Mesh(
    new THREE.CylinderGeometry(0.0035, 0.0035, ceilY - y - h / 2, 5), MAT.steel);
  cord.position.set(x, (y + h / 2 + ceilY) / 2, z);
  g.add(cord);

  const outer = shadowed(new THREE.Mesh(
    new THREE.CylinderGeometry(rTop, rBot, h, 24, 1, true),
    o.material ?? MAT.cabinet));
  outer.position.set(x, y, z);
  g.add(outer);

  // the lit brass liner, a hair inside the shell
  const liner = new THREE.Mesh(
    new THREE.CylinderGeometry(rTop * 0.94, rBot * 0.97, h * 0.98, 24, 1, true), MAT.brass);
  liner.position.set(x, y, z);
  liner.material = MAT.brass;
  g.add(liner);

  // a frosted diffuser across the mouth: without it you see straight up into
  // an empty cone and the fixture reads as hollow
  const diff = shadeMaterial({
    color: 0xe8d6b4, glow: o.glow ?? 0.70, glowColor: 0xffb877,
    bulbY: 0.0, spread: 0.09, inside: 1.2, weave: 0.18, roughness: 0.75,
  });
  const disc = new THREE.Mesh(new THREE.CircleGeometry(rBot * 0.94, 22), diff);
  disc.rotation.x = Math.PI / 2;
  disc.position.set(x, y - h / 2 + 0.012, z);
  g.add(disc);

  const filMat = bulbMaterial(o.bulbColor ?? 0x936a3e);
  const fil = new THREE.Mesh(new THREE.SphereGeometry(0.026, 10, 8), filMat);
  fil.position.set(x, y + 0.01, z);
  g.add(fil);

  return {
    group: g,
    lightPos: new THREE.Vector3(x, y - h / 2, z),
    setGlow: dimmer([diff], [filMat]),
  };
}

/* ============================================================ floor lamp == */
/* A torchère with a fabric drum: the shade is the light source you actually
   see, so it carries the gradient, and the brass rings at its rim are what
   give it an edge instead of dissolving into bloom. */
export function floorLamp(x, z, o = {}) {
  const g = new THREE.Group();
  g.position.set(x, 0, z);
  const shadeY = o.shadeY ?? 1.46, rt = 0.185, rb = 0.235, sh = 0.27;

  const base = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.13, 0.165, 0.024, 22), MAT.steel));
  base.position.y = 0.012; g.add(base);
  const collar = new THREE.Mesh(new THREE.CylinderGeometry(0.028, 0.040, 0.05, 12), MAT.brass);
  collar.position.y = 0.045; g.add(collar);
  const stem = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.013, 0.015, shadeY - 0.08, 12), MAT.brass));
  stem.position.y = (shadeY - 0.08) / 2 + 0.05; g.add(stem);

  const shadeMat = shadeMaterial({
    color: 0xe9d9b8, glow: o.glow ?? 0.55, glowColor: 0xffab5e,
    bulbY: 0.0, spread: 0.135, inside: 1.7, weave: 0.4,
  });
  const shade = new THREE.Mesh(new THREE.CylinderGeometry(rt, rb, sh, 26, 1, true), shadeMat);
  shade.position.y = shadeY; g.add(shade);

  // rims: a shade without them has no silhouette once it is glowing
  [[rt, shadeY + sh / 2], [rb, shadeY - sh / 2]].forEach(([r, yy]) => {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(r, 0.005, 5, 26), MAT.brass);
    ring.position.y = yy; ring.rotation.x = Math.PI / 2; g.add(ring);
  });

  const filMat = bulbMaterial(o.bulbColor ?? 0x9c7550);
  const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.033, 10, 8), filMat);
  bulb.position.y = shadeY; g.add(bulb);

  return {
    group: g,
    lightPos: new THREE.Vector3(x, shadeY, z),
    setGlow: dimmer([shadeMat], [filMat]),
  };
}

/* ============================================================ table lamp == */
export function tableLamp(x, y, z, o = {}) {
  const g = new THREE.Group();
  const rt = o.rTop ?? 0.105, rb = o.rBot ?? 0.145, sh = 0.185;
  const shadeY = y + 0.30;

  const foot = shadowed(new THREE.Mesh(new THREE.CylinderGeometry(0.058, 0.072, 0.021, 18), MAT.brass));
  foot.position.set(x, y + 0.011, z); g.add(foot);
  // a turned ceramic body, so the lamp is an object even when it is switched off
  const body = shadowed(new THREE.Mesh(new THREE.LatheGeometry(
    [[0.052, 0], [0.070, 0.03], [0.078, 0.09], [0.060, 0.16], [0.030, 0.21], [0.020, 0.23]]
      .map(([r, yy]) => new THREE.Vector2(r, yy)), 20), MAT.ceramic));
  body.position.set(x, y + 0.02, z); g.add(body);
  const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.008, 0.008, 0.10, 8), MAT.brass);
  stem.position.set(x, y + 0.27, z); g.add(stem);

  const shadeMat = shadeMaterial({
    color: 0xe9dcc0, glow: o.glow ?? 0.48, glowColor: 0xffa663,
    bulbY: 0.0, spread: 0.10, inside: 1.7, weave: 0.4,
  });
  const shade = new THREE.Mesh(new THREE.CylinderGeometry(rt, rb, sh, 22, 1, true), shadeMat);
  shade.position.set(x, shadeY, z); g.add(shade);
  [[rt, shadeY + sh / 2], [rb, shadeY - sh / 2]].forEach(([r, yy]) => {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(r, 0.004, 5, 22), MAT.brass);
    ring.position.set(x, yy, z); ring.rotation.x = Math.PI / 2; g.add(ring);
  });

  const filMat = bulbMaterial(o.bulbColor ?? 0x8a6844);
  const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.026, 10, 8), filMat);
  bulb.position.set(x, shadeY, z); g.add(bulb);

  return {
    group: g,
    lightPos: new THREE.Vector3(x, shadeY, z),
    setGlow: dimmer([shadeMat], [filMat]),
  };
}

/* ================================================================ sconce == */
/* Half a cylinder of alabaster on a brass backplate: open top and bottom, so
   it washes the wall in two directions and reads as a fitting rather than a
   bright spot on the plaster. `ry` turns it to face out of its wall. */
export function sconce(x, y, z, ry, o = {}) {
  const g = new THREE.Group();
  g.position.set(x, y, z);
  g.rotation.y = ry;

  const plate = new THREE.Mesh(new THREE.BoxGeometry(0.015, 0.30, 0.10), MAT.brass);
  plate.position.set(-0.008, 0, 0);
  plate.castShadow = true; g.add(plate);

  const shadeMat = shadeMaterial({
    color: 0xeadfc6, glow: o.glow ?? 0.50, glowColor: 0xffb473,
    bulbY: 0.0, spread: 0.085, inside: 1.5, weave: 0.28, roughness: 0.7,
  });
  // half a cylinder: thetaStart puts the open side against the wall
  const shade = new THREE.Mesh(
    new THREE.CylinderGeometry(0.062, 0.075, 0.20, 16, 1, true, -Math.PI / 2, Math.PI), shadeMat);
  shade.position.set(0.055, 0, 0);
  g.add(shade);
  [[0.062, 0.10], [0.075, -0.10]].forEach(([r, yy]) => {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(r, 0.0038, 5, 16, Math.PI), MAT.brass);
    ring.position.set(0.055, yy, 0);
    ring.rotation.set(Math.PI / 2, 0, Math.PI / 2);
    g.add(ring);
  });

  const filMat = bulbMaterial(o.bulbColor ?? 0x8f6a42);
  const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.022, 10, 8), filMat);
  bulb.position.set(0.055, 0, 0); g.add(bulb);

  return {
    group: g,
    // Stood well off the wall. Two sconces average to one source in the rig,
    // and that average landed 17 cm in front of the mirror between them —
    // close enough that its specular lobe was a hole burned in the glass.
    lightPos: new THREE.Vector3(x + Math.sin(ry) * 0.42, y, z + Math.cos(ry) * 0.42),
    setGlow: dimmer([shadeMat], [filMat]),
  };
}
