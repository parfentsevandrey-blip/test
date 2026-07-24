/* =========================================================================
   Texture registry + the bridge from generator output to Three.js textures.
   ========================================================================= */
import * as THREE from 'three';
import * as N from './noise.js';

import { oakFloor } from './oak.js';
import { plasterWall, plasterCeiling } from './plaster.js';
import { honedStone } from './stone.js';
import { woolRug } from './rug.js';
import { linen, boucle, knit } from './weave.js';
import { marble, bookCloth, brushedMetal } from './surfaces.js';
import { charredLog, leaf } from './organic.js';

/* name → { gen, size, normal } ; `normal` is the height→normal slope scale */
export const REGISTRY = {
  oakFloor:       { gen: oakFloor,       size: 1024, normal: 2.2 },
  plasterWall:    { gen: plasterWall,    size: 512,  normal: 1.4 },
  plasterCeiling: { gen: plasterCeiling, size: 512,  normal: 1.1 },
  honedStone:     { gen: honedStone,     size: 512,  normal: 1.6 },
  woolRug:        { gen: woolRug,        size: 512,  normal: 3.0 },
  linen:          { gen: linen,          size: 512,  normal: 2.6 },
  boucle:         { gen: boucle,         size: 512,  normal: 3.4 },
  knit:           { gen: knit,           size: 512,  normal: 3.6 },
  marble:         { gen: marble,         size: 512,  normal: 0.8 },
  bookCloth:      { gen: bookCloth,      size: 512,  normal: 2.0 },
  brushedMetal:   { gen: brushedMetal,   size: 256,  normal: 1.2 },
  charredLog:     { gen: charredLog,     size: 512,  normal: 3.2 },
  leaf:           { gen: leaf,           size: 512,  normal: 2.0 },
};

/* Painting the canvases is the expensive part and depends only on the
   generator; tiling is a property of the THREE.Texture wrapper. Caching them
   separately means a surface used at three different tile scales is still
   only generated once — and lets us pre-warm everything up front. */
const canvasCache = new Map();
export const texStats = { ms: 0, count: 0, byName: {} };

function canvases(name, opts = {}) {
  const entry = REGISTRY[name];
  if (!entry) throw new Error('unknown texture: ' + name);
  const size = opts.size || entry.size;
  const key = name + '@' + size;
  if (canvasCache.has(key)) return canvasCache.get(key);

  const now = () => (typeof performance !== 'undefined' ? performance.now() : 0);
  const t0 = now();
  const out = entry.gen(size, N);
  const res = {
    size,
    albedo: N.rgbCanvas(out.albedo, size, out.ao || null, opts.aoAmount ?? 1),
    rough: N.grayCanvas(out.rough, size),
    normal: N.normalCanvas(out.height, size, opts.normal ?? entry.normal),
    metal: out.metal ? N.grayCanvas(out.metal, size) : null,
  };
  res.ms = now() - t0;

  texStats.ms += res.ms; texStats.count++; texStats.byName[name] = Math.round(res.ms);
  canvasCache.set(key, res);
  return res;
}

/** paint a surface's canvases without building textures — used by the loader */
export function prewarm(name, opts) { return canvases(name, opts).ms; }

/**
 * Build the Three.js texture set for a registered generator.
 * @param {string} name    key in REGISTRY
 * @param {object} opts    { repeat:[u,v], aniso, aoAmount, size, normal }
 * @returns {{ map, roughnessMap, normalMap, metalnessMap|null, ms:number }}
 */
export function buildMaps(name, opts = {}) {
  const c = canvases(name, opts);
  const rep = opts.repeat || [1, 1];
  const aniso = opts.aniso ?? 8;
  const finish = (canvas, srgb) => {
    if (!canvas) return null;
    const t = new THREE.CanvasTexture(canvas);
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(rep[0], rep[1]);
    t.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace;
    t.anisotropy = aniso;
    t.needsUpdate = true;
    return t;
  };
  return {
    map: finish(c.albedo, true),
    roughnessMap: finish(c.rough, false),
    normalMap: finish(c.normal, false),
    metalnessMap: finish(c.metal, false),
    ms: c.ms,
  };
}

/** apply a generated set onto a MeshStandardMaterial */
export function applyMaps(material, name, opts = {}) {
  const m = buildMaps(name, opts);
  material.map = m.map;
  material.roughnessMap = m.roughnessMap;
  material.normalMap = m.normalMap;
  material.normalScale = new THREE.Vector2(opts.normalScale ?? 1, opts.normalScale ?? 1);
  if (m.metalnessMap) material.metalnessMap = m.metalnessMap;
  material.roughness = 1;                 // roughnessMap multiplies this
  material.needsUpdate = true;
  return m;
}
