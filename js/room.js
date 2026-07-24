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
};

export const QUALITY = [
  { scale: 0.70, dpr: 1.25, refl: 0,    bloom: 3, rain: 1100, towers: 220, shadow: 512  },
  { scale: 0.88, dpr: 1.60, refl: 1,    bloom: 4, rain: 2600, towers: 380, shadow: 1024 },
  { scale: 1.00, dpr: 2.00, refl: 2,    bloom: 5, rain: 4200, towers: 540, shadow: 2048 },
];

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

/* value noise (JS) — periodic so canvas textures tile seamlessly */
const _hash = (x, y, s) => {
  const n = Math.sin(x * 127.1 + y * 311.7 + s * 74.7) * 43758.5453123;
  return n - Math.floor(n);
};
export function vnoise(x, y, period, s) {
  const p = period | 0;
  let ix = Math.floor(x), iy = Math.floor(y);
  let fx = x - ix, fy = y - iy;
  fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
  const wx = (v) => (p > 0 ? ((v % p) + p) % p : v);
  const a = _hash(wx(ix), wx(iy), s), b = _hash(wx(ix + 1), wx(iy), s);
  const c = _hash(wx(ix), wx(iy + 1), s), d = _hash(wx(ix + 1), wx(iy + 1), s);
  return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}
export function fbm(x, y, period, s, oct = 5, gain = 0.5) {
  let v = 0, amp = 0.5, f = 1, norm = 0;
  for (let i = 0; i < oct; i++) {
    v += amp * vnoise(x * f, y * f, period * f, s + i * 19);
    norm += amp; f *= 2; amp *= gain;
  }
  return v / norm;
}

/* ------------------------------------------------- procedural canvas maps */
const _texCache = new Map();
function canvasTex(key, size, draw, { srgb = true, repeat = [1, 1], aniso = 8 } = {}) {
  if (_texCache.has(key)) return _texCache.get(key);
  const c = document.createElement('canvas');
  c.width = c.height = size;
  draw(c.getContext('2d', { willReadFrequently: true }), size);
  const t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.repeat.set(repeat[0], repeat[1]);
  t.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace;
  t.anisotropy = aniso;
  t.needsUpdate = true;
  _texCache.set(key, t);
  return t;
}
const px = (r, g, b) => `rgb(${r | 0},${g | 0},${b | 0})`;

/** oak floor: 8 planks across the tile, grain + per-plank tone jitter */
export function makeFloorMaps(aniso) {
  const S = 1024, PLANKS = 6;
  const color = canvasTex('floorC', S, (g, s) => {
    const ph = s / PLANKS;
    for (let p = 0; p < PLANKS; p++) {
      const tone = 0.78 + fbm(p * 3.1, 0, 0, 5, 2) * 0.44;
      const seg = 2 + ((rnd() * 2) | 0);                       // butt joints per plank
      for (let k = 0; k < seg; k++) {
        const y0 = (k / seg) * s, y1 = ((k + 1) / seg) * s;
        const jit = 0.9 + rnd() * 0.22;
        const base = [96 * tone * jit, 60 * tone * jit, 36 * tone * jit];
        g.fillStyle = px(base[0], base[1], base[2]);
        g.fillRect(p * ph, y0, ph, y1 - y0);
        // grain
        const img = g.getImageData(p * ph, y0, Math.ceil(ph), Math.ceil(y1 - y0));
        const d = img.data, w = img.width, hgt = img.height;
        for (let y = 0; y < hgt; y++) for (let x = 0; x < w; x++) {
          const n = fbm(x * 0.30, y * 0.016, 0, p * 37 + k * 7, 4, 0.55);
          const ring = Math.abs(Math.sin((x * 0.24 + n * 5.5) * 1.7));
          const v = 0.80 + n * 0.34 - Math.pow(ring, 6) * 0.30;
          const i = (y * w + x) * 4;
          d[i] = clamp(d[i] * v, 0, 255);
          d[i + 1] = clamp(d[i + 1] * v * 0.99, 0, 255);
          d[i + 2] = clamp(d[i + 2] * v * 0.97, 0, 255);
        }
        g.putImageData(img, p * ph, y0);
        // joint shadow
        g.fillStyle = 'rgba(18,10,6,.55)'; g.fillRect(p * ph, y1 - 1.5, ph, 1.5);
      }
      g.fillStyle = 'rgba(14,8,5,.6)'; g.fillRect(p * ph, 0, 1.6, s);
    }
  }, { repeat: [7, 4], aniso });

  const rough = canvasTex('floorR', 512, (g, s) => {
    const img = g.createImageData(s, s), d = img.data;
    for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
      const n = fbm(x * 0.05, y * 0.012, 26, 11, 4);
      const v = 92 + n * 90;                                    // fairly glossy oiled oak
      const i = (y * s + x) * 4;
      d[i] = d[i + 1] = d[i + 2] = v; d[i + 3] = 255;
    }
    g.putImageData(img, 0, 0);
  }, { srgb: false, repeat: [7, 4], aniso });

  return { color, rough };
}

/** generic fbm bump map */
export function makeBump(key, size, freq, seed, oct = 5, contrast = 1) {
  return canvasTex(key, size, (g, s) => {
    const img = g.createImageData(s, s), d = img.data;
    for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
      let n = fbm(x * freq, y * freq, s * freq, seed, oct);
      n = clamp(0.5 + (n - 0.5) * contrast, 0, 1);
      const i = (y * s + x) * 4;
      d[i] = d[i + 1] = d[i + 2] = n * 255; d[i + 3] = 255;
    }
    g.putImageData(img, 0, 0);
  }, { srgb: false });
}

/** honed dark stone with soft veining, for the fireplace surround */
export function makeStoneMaps() {
  const color = canvasTex('stoneC', 512, (g, s) => {
    const img = g.createImageData(s, s), d = img.data;
    for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
      const warp = fbm(x * 0.008, y * 0.008, 4, 3, 4) * 26;
      const vein = Math.abs(Math.sin((x * 0.012 + y * 0.004 + warp * 0.05) * 3.1));
      const grain = fbm(x * 0.09, y * 0.09, 46, 8, 4);
      const v = 0.30 + grain * 0.24 + Math.pow(1 - vein, 14) * 0.34;
      const i = (y * s + x) * 4;
      d[i] = 62 * v * 2.1; d[i + 1] = 58 * v * 2.0; d[i + 2] = 56 * v * 1.95; d[i + 3] = 255;
    }
    g.putImageData(img, 0, 0);
  }, { repeat: [2, 2] });
  const bump = makeBump('stoneB', 512, 0.09, 8, 4, 0.7);
  bump.repeat.set(2, 2);
  return { color, bump };
}

/** wool rug: soft mottled pile with a faint border */
export function makeRugMaps() {
  const color = canvasTex('rugC', 512, (g, s) => {
    const img = g.createImageData(s, s), d = img.data;
    for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
      const n = fbm(x * 0.035, y * 0.035, 18, 21, 5);
      const fibre = fbm(x * 0.5, y * 0.5, 256, 5, 2);
      const edge = smoothstep(0.0, 0.055, Math.min(x, y, s - 1 - x, s - 1 - y) / s);
      const band = 1 - 0.16 * (1 - smoothstep(0.06, 0.085, Math.min(x, y, s - 1 - x, s - 1 - y) / s));
      const v = (0.62 + n * 0.5) * (0.86 + fibre * 0.28) * band * (0.55 + edge * 0.45);
      const i = (y * s + x) * 4;
      d[i] = 196 * v; d[i + 1] = 170 * v; d[i + 2] = 143 * v; d[i + 3] = 255;
    }
    g.putImageData(img, 0, 0);
  });
  const bump = makeBump('rugB', 512, 0.42, 33, 3, 1.4);
  return { color, bump };
}

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
