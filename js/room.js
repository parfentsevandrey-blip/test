/* =========================================================================
   Уютная комната в небоскрёбе — part 1 / 4: core, utils, textures, renderer
   ========================================================================= */
import * as THREE from 'three';

/* ------------------------------------------------------------------ state */
export const CFG = {
  fire: 0.80,          // 0..1.5   flame + firelight strength
  rain: 0.85,          // 0..1.5   drops on glass + falling streaks
  warm: 0.70,          // 0..1     grade: cool ↔ warm
  lamps: true,
  drift: true,
  quality: 1,          // 0 low / 1 medium / 2 high
  res: 0.85,           // internal render scale, 0.4 … 1.0
};

/* Tiers deliberately hold NO resolution: every buffer stays allocated at the
   same size for all three, so switching tiers is a handful of uniform writes
   and costs nothing. Resolution is its own control (CFG.res) because changing
   it does reallocate, and that should only happen when the user asks. */
/* `towers` counts massing boxes, not buildings — a tower is several. The city
   emits them tall-and-near first, so cutting the list off is a level of
   detail rather than a hole in the skyline. */
export const QUALITY = [
  { refl: 0, bloom: 3, rain: 1100, towers: 1100, streets: 55, cars: 170 },
  { refl: 1, bloom: 4, rain: 2600, towers: 2200, streets: 110, cars: 420 },
  { refl: 2, bloom: 5, rain: 4200, towers: 3600, streets: 175, cars: 760 },
];
export const SHADOW_SIZE = 1024;

/* room is 10 × 8 m, 3.3 m high; floor y = 0 sits 150 m above the street */
export const ROOM = { x: 5, z: 4, h: 3.3, alt: 150 };

/* the firebox opening — the left wall and the stone facing must both be cut around it */
export const FIREBOX = {
  z: -0.6,      // centre along z
  w: 1.75,      // opening width
  h: 0.98,      // opening height
  y: 0.83,      // opening centre height
  d: 0.60,      // recess depth behind the wall
  panelW: 4.0,  // stone facing width
  panelT: 0.14, // stone facing thickness
};

/* ------------------------------------------------------------------ maths */
export const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
export const lerp = (a, b, t) => a + (b - a) * t;
export const smoothstep = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
export const easeInOut = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
/** frame-rate independent exponential approach */
export const damp = (a, b, lambda, dt) => lerp(a, b, 1 - Math.exp(-lambda * dt));

let _seed = 1337;
export const rnd = () => { _seed = (_seed * 1664525 + 1013904223) >>> 0; return _seed / 4294967296; };
export const rrnd = (a, b) => a + (b - a) * rnd();
export const pick = (arr) => arr[(rnd() * arr.length) | 0];

/* ----------------------------------------------------------- GLSL helpers */
export const GLSL_NOISE = /* glsl */`
float hash11(float p){ p = fract(p*0.1031); p *= p+33.33; p *= p+p; return fract(p); }
float hash12(vec2 p){ vec3 p3 = fract(vec3(p.xyx)*0.1031); p3 += dot(p3,p3.yzx+33.33); return fract((p3.x+p3.y)*p3.z); }
vec2  hash22(vec2 p){ vec3 p3 = fract(vec3(p.xyx)*vec3(.1031,.1030,.0973)); p3 += dot(p3,p3.yzx+33.33); return fract((p3.xx+p3.yz)*p3.zy); }
float hash13(vec3 p3){ p3 = fract(p3*0.1031); p3 += dot(p3,p3.zyx+31.32); return fract((p3.x+p3.y)*p3.z); }
float vnoise(vec2 p){ vec2 i=floor(p), f=fract(p); f=f*f*(3.-2.*f);
  return mix(mix(hash12(i),hash12(i+vec2(1,0)),f.x), mix(hash12(i+vec2(0,1)),hash12(i+vec2(1,1)),f.x), f.y); }
float vnoise3(vec3 p){ vec3 i=floor(p), f=fract(p); f=f*f*(3.-2.*f);
  float a=mix(mix(hash13(i),hash13(i+vec3(1,0,0)),f.x), mix(hash13(i+vec3(0,1,0)),hash13(i+vec3(1,1,0)),f.x), f.y);
  float b=mix(mix(hash13(i+vec3(0,0,1)),hash13(i+vec3(1,0,1)),f.x), mix(hash13(i+vec3(0,1,1)),hash13(i+vec3(1,1,1)),f.x), f.y);
  return mix(a,b,f.z); }
float fbm2(vec2 p){ float v=0.,a=.5; for(int i=0;i<5;i++){ v+=a*vnoise(p); p*=2.03; a*=.5; } return v; }
float fbm3(vec3 p){ float v=0.,a=.5; for(int i=0;i<4;i++){ v+=a*vnoise3(p); p*=2.05; a*=.5; } return v; }
`;

/* ------------------------------------------------------- shared uniforms */
export const U = {
  time:     { value: 0 },
  fire:     { value: CFG.fire },
  rain:     { value: CFG.rain },
  flicker:  { value: 1 },        // fire light modulation, 0.7 … 1.3
  flash:    { value: 0 },        // lightning 0 … 1
  reflOn:   { value: 1 },        // 0 while rendering a reflection pass
  fogColor: { value: new THREE.Color(0x121a26) },
  fogDens:  { value: 0.0019 },
  glow:     { value: new THREE.Color(0xff8a3a) },
};

/* ------------------------------------------------------------- geometry aid */
export function roundedBoxGeo(w, h, d, r, seg = 3) {
  const b = Math.min(r, w / 2 - 1e-3, h / 2 - 1e-3, d / 2 - 1e-3);
  const sw = Math.max(1e-3, w - 2 * b), sh = Math.max(1e-3, h - 2 * b), sd = Math.max(1e-3, d - 2 * b);
  const rr = Math.max(1e-4, Math.min(r - b, sw / 2 - 1e-3, sh / 2 - 1e-3));
  const s = new THREE.Shape();
  const x0 = -sw / 2, y0 = -sh / 2, x1 = sw / 2, y1 = sh / 2;
  s.moveTo(x0 + rr, y0);
  s.lineTo(x1 - rr, y0); s.quadraticCurveTo(x1, y0, x1, y0 + rr);
  s.lineTo(x1, y1 - rr); s.quadraticCurveTo(x1, y1, x1 - rr, y1);
  s.lineTo(x0 + rr, y1); s.quadraticCurveTo(x0, y1, x0, y1 - rr);
  s.lineTo(x0, y0 + rr); s.quadraticCurveTo(x0, y0, x0 + rr, y0);
  const g = new THREE.ExtrudeGeometry(s, {
    depth: sd, bevelEnabled: true, bevelThickness: b, bevelSize: b, bevelSegments: seg, curveSegments: 6,
  });
  g.translate(0, 0, -sd / 2);
  g.computeVertexNormals();
  return g;
}

export function faceTowards(obj, x, z) {
  obj.rotation.y = Math.atan2(x - obj.position.x, z - obj.position.z);
}

/* ---------------------------------------------------------- UV in metres --
   Texture tiles are authored at a physical size. Rescaling UVs to world units
   keeps texel density constant no matter how big the piece of geometry is, so
   the plaster on a 4 m wall matches the plaster on a 0.7 m panel beside it. */

/** PlaneGeometry spanning w × h metres, tiled every `tu` × `tv` metres.
 *  `cu`/`cv` are the panel's centre in the surface's own coordinates — pass
 *  them when one surface is built from several panels, so the pattern runs
 *  continuously across the joins instead of restarting on each piece. */
export function planeUv(geo, w, h, tu, tv = tu, cu = 0, cv = 0) {
  const uv = geo.attributes.uv;
  for (let i = 0; i < uv.count; i++) {
    uv.setXY(i, (cu + (uv.getX(i) - 0.5) * w) / tu, (cv + (uv.getY(i) - 0.5) * h) / tv);
  }
  uv.needsUpdate = true;
  return geo;
}

/** BoxGeometry w × h × d metres — each face gets its own world-scaled UVs.
 *  `centre` is the box's world position; passing it keeps the pattern
 *  continuous across separate boxes that form one surface. */
export function boxUv(geo, w, h, d, tile, centre = null) {
  const uv = geo.attributes.uv;
  const [cx, cy, cz] = centre || [0, 0, 0];
  // BoxGeometry emits faces +x, -x, +y, -y, +z, -z, 4 verts each.
  // Per face: [u span, v span, u centre, v centre] — u/v follow three's own
  // axis choice for each plane (px/nx: z,y · py/ny: x,z · pz/nz: x,y).
  const F = [
    [d, h, -cz, cy], [d, h, cz, cy],
    [w, d, cx, cz], [w, d, cx, -cz],
    [w, h, cx, cy], [w, h, -cx, cy],
  ];
  for (let f = 0; f < 6; f++) {
    const [su, sv, ou, ov] = F[f];
    for (let k = 0; k < 4; k++) {
      const i = f * 4 + k;
      if (i >= uv.count) break;
      uv.setXY(i, (ou + (uv.getX(i) - 0.5) * su) / tile, (ov + (uv.getY(i) - 0.5) * sv) / tile);
    }
  }
  uv.needsUpdate = true;
  return geo;
}

/** normalise UVs into 0..1 from their bounding box — for ShapeGeometry, whose
    generator emits raw local coordinates rather than a unit square */
export function normalizeUv(geo) {
  const uv = geo.attributes.uv;
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (let i = 0; i < uv.count; i++) {
    const x = uv.getX(i), y = uv.getY(i);
    if (x < x0) x0 = x; if (x > x1) x1 = x;
    if (y < y0) y0 = y; if (y > y1) y1 = y;
  }
  const dx = x1 - x0 || 1, dy = y1 - y0 || 1;
  for (let i = 0; i < uv.count; i++) uv.setXY(i, (uv.getX(i) - x0) / dx, (uv.getY(i) - y0) / dy);
  uv.needsUpdate = true;
  return geo;
}

/* --------------------------------------------------------------- renderer */
export const canvas = document.getElementById('scene');
let renderer;
try {
  renderer = new THREE.WebGLRenderer({
    canvas, antialias: false, alpha: false, powerPreference: 'high-performance', stencil: false,
  });
} catch (e) { renderer = null; }

if (!renderer || !renderer.capabilities.isWebGL2) {
  document.getElementById('fallback')?.classList.add('show');
  document.getElementById('boot')?.classList.add('done');
  throw new Error('WebGL2 unavailable');
}

renderer.setClearColor(0x000000, 1);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.NoToneMapping;      // we tone-map in the final pass
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
export { renderer };

export const MAX_ANISO = renderer.capabilities.getMaxAnisotropy();

export const camera = new THREE.PerspectiveCamera(48, 1, 0.05, 6000);
camera.position.set(2.8, 1.55, 3.05);

export const roomScene = new THREE.Scene();
export const outsideScene = new THREE.Scene();

/* fullscreen-quad helper used by every post pass */
export const quadCam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
const quadGeo = new THREE.PlaneGeometry(2, 2);
export const quadMesh = new THREE.Mesh(quadGeo, null);
export const quadScene = new THREE.Scene();
quadScene.add(quadMesh);
export function blit(material, target, clear = true) {
  quadMesh.material = material;
  renderer.setRenderTarget(target || null);
  const prev = renderer.autoClear;
  renderer.autoClear = clear;
  renderer.render(quadScene, quadCam);
  renderer.autoClear = prev;
}

export const VERT_QUAD = /* glsl */`
varying vec2 vUv;
void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }
`;

export function rt(w, h, opts = {}) {
  return new THREE.WebGLRenderTarget(Math.max(1, w | 0), Math.max(1, h | 0), {
    type: THREE.HalfFloatType,
    format: THREE.RGBAFormat,
    minFilter: THREE.LinearFilter,
    magFilter: THREE.LinearFilter,
    depthBuffer: opts.depth !== false,
    stencilBuffer: false,
    generateMipmaps: false,
    ...opts,
  });
}
