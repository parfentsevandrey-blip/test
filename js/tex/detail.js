/* =========================================================================
   A shared micro-detail layer.

   Base textures are authored at a tile of tens of centimetres. That is right
   for the pattern, but it means two things go wrong at the ends of the
   viewing range: up close there is no structure finer than the base texture's
   own texel, so surfaces look soft; and at four or five metres the base normal
   map mips toward flat and the surface goes dead just when the whole room is
   in shot.

   This adds a second, much finer normal sampled at a few centimetres, faded
   out with view distance so it never survives to become aliasing shimmer.

       applyDetail(material, { scale: 0.05, strength: 0.6, fade: 4.0 })

   It composes with whatever onBeforeCompile a material already has (the
   reflective floor has one), and folds its options into the program cache key,
   without which Three.js hands two materials the same compiled program and the
   second one silently gets the first one's settings.
   ========================================================================= */
import * as THREE from 'three';
import * as N from './noise.js';

let _detailTex = null;

/** one shared tileable detail normal — fine grain, no directional bias */
function detailTexture() {
  if (_detailTex) return _detailTex;
  const S = 256;
  const h = N.newF(S * S);
  for (let y = 0; y < S; y++) {
    for (let x = 0; x < S; x++) {
      // two octaves of fine grain plus a sparse scatter of pits, so it reads
      // as surface irregularity rather than as uniform sandpaper
      const a = N.fbm(x * 0.28, y * 0.28, S * 0.28, 7, 3, 0.55, 2, N.gnoise);
      const b = N.vnoise(x * 0.9, y * 0.9, S * 0.9, 31);
      const w = N.worley(x * 0.055, y * 0.055, S * 0.055, 13, 1);
      const pit = N.smoothstep(0.30, 0.0, w.f1) * 0.35;
      h[y * S + x] = N.clamp(a * 0.72 + b * 0.28 - pit, 0, 1);
    }
  }
  const t = new THREE.CanvasTexture(N.normalCanvas(h, S, 1.6));
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.colorSpace = THREE.NoColorSpace;
  t.needsUpdate = true;
  _detailTex = t;
  return t;
}

/**
 * @param {THREE.MeshStandardMaterial} material
 * @param {object} opts
 *   scale     detail tile size in metres (default 0.06)
 *   strength  0..1 blend weight (default 0.55)
 *   fade      distance in metres by which the detail is gone (default 4.5)
 *   rough     how much the detail breaks up roughness (default 0.06)
 */
export function applyDetail(material, opts = {}) {
  const scale = opts.scale ?? 0.06;
  const strength = opts.strength ?? 0.55;
  const fade = opts.fade ?? 4.5;
  const rough = opts.rough ?? 0.06;

  const uniforms = {
    tDetail: { value: detailTexture() },
    uDetail: { value: new THREE.Vector4(1 / scale, strength, fade, rough) },
  };

  const prev = material.onBeforeCompile;
  material.onBeforeCompile = (shader, renderer) => {
    if (prev) prev(shader, renderer);          // compose, never clobber
    Object.assign(shader.uniforms, uniforms);

    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', `#include <common>
        varying vec3 vDetailPos; varying vec3 vDetailNrm;`)
      .replace('#include <project_vertex>', `#include <project_vertex>
        vDetailPos = (modelMatrix * vec4(position, 1.0)).xyz;
        vDetailNrm = normalize(mat3(modelMatrix) * normal);`);

    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', `#include <common>
        uniform sampler2D tDetail; uniform vec4 uDetail;
        varying vec3 vDetailPos; varying vec3 vDetailNrm;

        /* Whiteout blending: adding or replacing tangent-space normals flattens
           whichever layer is weaker. This keeps both. */
        vec3 blendNormals(vec3 base, vec3 det){
          vec3 a = base + vec3(0.0, 0.0, 1.0);
          vec3 b = det * vec3(-1.0, -1.0, 1.0);
          return normalize(a * dot(a, b) / max(a.z, 1e-4) - b);
        }`)
      // triplanar so it needs no UVs of its own and never stretches
      .replace('#include <normal_fragment_maps>', `#include <normal_fragment_maps>
        {
          float dist = length(vDetailPos - cameraPosition);
          float amt = uDetail.y * (1.0 - smoothstep(uDetail.z * 0.35, uDetail.z, dist));
          if(amt > 0.002){
            vec3 aw = abs(normalize(vDetailNrm));
            aw /= (aw.x + aw.y + aw.z);
            vec3 p = vDetailPos * uDetail.x;
            vec3 dn = texture2D(tDetail, p.yz).xyz * aw.x
                    + texture2D(tDetail, p.xz).xyz * aw.y
                    + texture2D(tDetail, p.xy).xyz * aw.z;
            dn = normalize(dn * 2.0 - 1.0);
            dn = normalize(mix(vec3(0.0, 0.0, 1.0), dn, amt));
            normal = normalize(blendNormals(normal, dn));
            roughnessFactor = clamp(roughnessFactor + (dn.z - 0.9) * uDetail.w * amt, 0.04, 1.0);
          }
        }`);
  };

  // without this two materials share one compiled program and the second
  // silently inherits the first one's detail settings
  const basis = material.customProgramCacheKey ? material.customProgramCacheKey() : '';
  material.customProgramCacheKey = () => `${basis}|det${scale},${strength},${fade},${rough}`;
  material.needsUpdate = true;
  return material;
}
