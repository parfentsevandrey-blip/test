/* =========================================================================
   The material palette — one place, shared by the shell and every room.

   The redesign moves the apartment from warm-rustic (honey oak, cream
   plaster, brass everywhere) to something darker and quieter: smoked oak,
   deep olive limewash, honed travertine, walnut joinery, blackened steel,
   with brass kept as an accent rather than a theme. At night, lit mostly by
   a fire and a few practicals, a dark room reads far better than a pale one
   — pale walls at 2% of daylight just look grey.

   Every texture is still generated procedurally at load; this file only
   decides tint, tiling, roughness and how much environment each surface
   picks up.
   ========================================================================= */
import * as THREE from 'three';
import { MAX_ANISO } from './room.js';
import { applyMaps } from './tex/index.js';
import { applyDetail } from './tex/detail.js';

const AN = { aniso: MAX_ANISO };

/** attach the generated PBR set, and optionally the shared micro-detail normal */
export const tex = (mat, name, opts) => {
  applyMaps(mat, name, { ...AN, ...opts });
  if (opts && opts.detail) applyDetail(mat, opts.detail);
  return mat;
};

/* fabric wants a fine fibre break-up; wood and stone a coarser, shallower one */
export const D_FABRIC = { scale: 0.035, strength: 0.55, fade: 3.5, rough: 0.05 };
export const D_WOOD = { scale: 0.07, strength: 0.40, fade: 4.5, rough: 0.04 };
export const D_STONE = { scale: 0.045, strength: 0.45, fade: 3.5, rough: 0.06 };

const std = (o) => new THREE.MeshStandardMaterial(o);

export const MAT = {
  /* ---------------------------------------------------------- surfaces */
  // smoked oak, wide plank — cooler and several stops darker than before
  oakFloor: tex(std({ color: 0x7d6549, metalness: 0, envMapIntensity: 0.5 }),
    'oakFloor', { normalScale: 0.9 }),

  // honed travertine, large format; the grout is injected by the shell
  stoneFloor: tex(std({ color: 0x8d8474, metalness: 0.03, envMapIntensity: 0.5 }),
    'honedStone', { repeat: [0.55, 0.55], normalScale: 0.55, detail: D_STONE }),

  // limewash: the everyday wall
  plaster: tex(std({ color: 0x9d9184, metalness: 0, envMapIntensity: 0.7 }),
    'plasterWall', { normalScale: 0.40, detail: { scale: 0.05, strength: 0.5, fade: 4.0, rough: 0.05 } }),

  // and the deep olive it goes to on a feature wall
  plasterDark: tex(std({ color: 0x3f453c, metalness: 0, envMapIntensity: 0.55 }),
    'plasterWall', { normalScale: 0.48, detail: { scale: 0.05, strength: 0.55, fade: 4.0, rough: 0.05 } }),

  ceiling: tex(std({ color: 0x8d857a, metalness: 0, envMapIntensity: 0.6 }),
    'plasterCeiling', { normalScale: 0.4 }),

  /* ------------------------------------------------------------ joinery */
  walnut: tex(std({ color: 0x5a422d, metalness: 0, envMapIntensity: 0.45 }),
    'oakFloor', { repeat: [0.55, 0.55], normalScale: 0.6, detail: D_WOOD }),

  oakPale: tex(std({ color: 0x9c7f5b, metalness: 0, envMapIntensity: 0.5 }),
    'oakFloor', { repeat: [0.45, 0.45], normalScale: 0.5, detail: D_WOOD }),

  // matt lacquered cabinet fronts — no texture, all shape and sheen
  cabinet: std({ color: 0x383d38, roughness: 0.52, metalness: 0.04, envMapIntensity: 0.55 }),
  cabinetPale: std({ color: 0x7d7566, roughness: 0.58, metalness: 0.03, envMapIntensity: 0.5 }),

  /* -------------------------------------------------------------- stone */
  travertine: tex(std({ color: 0xb0a58f, metalness: 0.03, envMapIntensity: 0.7 }),
    'honedStone', { repeat: [1.1, 1.1], normalScale: 0.5, detail: D_STONE }),

  marble: tex(std({ color: 0x9d9c98, metalness: 0.05, envMapIntensity: 1.0 }),
    'marble', { repeat: [1.5, 1.5], normalScale: 0.5 }),

  // the fireplace surround: a darker, denser stone than the floor
  fireStone: tex(std({ color: 0xcfc6ba, metalness: 0.04, envMapIntensity: 0.5 }),
    'honedStone', { normalScale: 0.7, detail: D_STONE }),

  /* -------------------------------------------------------------- metal */
  steel: tex(std({ color: 0x22201d, metalness: 0.82, roughness: 0.42, envMapIntensity: 0.8 }),
    'brushedMetal', { repeat: [3, 3], normalScale: 0.5 }),

  brass: tex(std({ color: 0xb08f4e, metalness: 0.95, envMapIntensity: 1.1 }),
    'brushedMetal', { repeat: [3, 3], normalScale: 0.6 }),

  chrome: std({ color: 0xcfd2d4, roughness: 0.12, metalness: 1.0, envMapIntensity: 1.2 }),

  /* ------------------------------------------------------------- fabric */
  linen: tex(std({ color: 0xb3a68f, metalness: 0, envMapIntensity: 0.32 }),
    'linen', { repeat: [4, 4], normalScale: 0.85, detail: D_FABRIC }),

  linenDark: tex(std({ color: 0x796d5c, metalness: 0, envMapIntensity: 0.28 }),
    'linen', { repeat: [4, 4], normalScale: 0.85, detail: D_FABRIC }),

  boucle: tex(std({ color: 0xcdbfa6, metalness: 0, envMapIntensity: 0.28 }),
    'boucle', { repeat: [4, 4], normalScale: 0.9, detail: D_FABRIC }),

  boucleRound: tex(std({ color: 0xcdbfa6, metalness: 0, envMapIntensity: 0.28 }),
    'boucle', { repeat: [9, 1.6], normalScale: 0.9 }),

  // the one hot colour in the flat, used sparingly
  rust: tex(std({ color: 0xa8542f, metalness: 0, envMapIntensity: 0.32 }),
    'linen', { repeat: [4, 4], normalScale: 0.85, detail: D_FABRIC }),

  olive: tex(std({ color: 0x556052, metalness: 0, envMapIntensity: 0.3 }),
    'linen', { repeat: [4, 4], normalScale: 0.85, detail: D_FABRIC }),

  knit: tex(std({ color: 0xb59a72, metalness: 0, side: THREE.DoubleSide, envMapIntensity: 0.22 }),
    'knit', { repeat: [3, 5], normalScale: 1.0, detail: D_FABRIC }),

  drape: tex(std({ color: 0x9d9384, metalness: 0, side: THREE.DoubleSide, envMapIntensity: 0.3 }),
    'linen', { repeat: [3, 3], normalScale: 1.0, detail: D_FABRIC }),

  rug: tex(std({ color: 0xbaa88c, metalness: 0, envMapIntensity: 0.28 }),
    'woolRug', { repeat: [2.2, 2.2], normalScale: 0.75 }),

  rugDark: tex(std({ color: 0x6c6a5e, metalness: 0, envMapIntensity: 0.24 }),
    'woolRug', { repeat: [2.4, 2.4], normalScale: 0.75 }),

  leather: tex(std({ color: 0x5a3a29, roughness: 0.55, metalness: 0, envMapIntensity: 0.55 }),
    'linen', { repeat: [7, 7], normalScale: 0.35, detail: D_FABRIC }),

  /* --------------------------------------------------------------- misc */
  wicker: tex(std({ color: 0x8d6a41, metalness: 0, envMapIntensity: 0.3 }),
    'knit', { repeat: [3.2, 2.2], normalScale: 1.15 }),

  bark: tex(std({ color: 0x7a6249, metalness: 0, envMapIntensity: 0.25 }),
    'charredLog', { repeat: [2.5, 2.5], normalScale: 0.9 }),

  leaf: tex(std({ color: 0xffffff, metalness: 0, side: THREE.DoubleSide, envMapIntensity: 0.4 }),
    'leaf', { repeat: [1, 1], normalScale: 0.8 }),

  fur: tex(std({ color: 0x635a53, metalness: 0, envMapIntensity: 0.2 }),
    'woolRug', { repeat: [5, 5], normalScale: 0.7 }),

  ceramic: std({ color: 0xa89b87, roughness: 0.4, envMapIntensity: 0.7 }),
  clay: std({ color: 0x6d5c49, roughness: 0.82, envMapIntensity: 0.35 }),
  clayPale: std({ color: 0x9a8f7c, roughness: 0.68, envMapIntensity: 0.4 }),
  glassware: std({ color: 0xdfe6e6, roughness: 0.06, metalness: 0, envMapIntensity: 1.4,
                  transparent: true, opacity: 0.28 }),
};

/** every book shares one cloth texture and differs only by tint */
const _bookMats = new Map();
export const bookMat = (hex) => {
  if (!_bookMats.has(hex)) {
    _bookMats.set(hex, tex(std({ color: hex, metalness: 0, envMapIntensity: 0.25 }),
      'bookCloth', { repeat: [8, 8], normalScale: 0.8 }));
  }
  return _bookMats.get(hex);
};

export const shadowed = (m) => { m.castShadow = true; m.receiveShadow = true; return m; };
