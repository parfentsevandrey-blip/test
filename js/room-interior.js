/* =========================================================================
   Part 3 / 5 — the room shell, the floor-to-ceiling glass, the fireplace
   ========================================================================= */
import * as THREE from 'three';
import {
  GLSL_NOISE, U, ROOM, FIREBOX, rnd, roomScene, MAX_ANISO,
  roundedBoxGeo, planeUv, boxUv,
} from './room.js';
import { applyMaps } from './tex/index.js';
import { applyDetail } from './tex/detail.js';

const X = ROOM.x, Z = ROOM.z, H = ROOM.h;

/* materials that need textures wired in by the app after the render targets exist */
export const glassMaterials = [];
export const reflectiveFloor = { material: null, uniforms: null };

/* ============================================================== shell ==== */
export function buildShell() {
  const g = new THREE.Group();
  g.name = 'shell';

  /* ---- floor: oiled oak, glossy enough to catch the fire ---- */
  const floorMat = new THREE.MeshStandardMaterial({
    color: 0xffffff, metalness: 0.0, envMapIntensity: 0.55,
  });
  applyMaps(floorMat, 'oakFloor', { aniso: MAX_ANISO, normalScale: 0.9 });

  /* planar reflection injected into the standard lighting result */
  const fu = {
    tRefl:      { value: null },
    uReflMat:   { value: new THREE.Matrix4() },
    uReflAmt:   { value: 0.30 },
    uReflOn:    U.reflOn,
    uTime:      U.time,
  };
  floorMat.onBeforeCompile = (sh) => {
    Object.assign(sh.uniforms, fu);
    sh.vertexShader = sh.vertexShader
      .replace('#include <common>', `#include <common>
        uniform mat4 uReflMat; varying vec4 vReflUv; varying vec3 vWpos;`)
      .replace('#include <project_vertex>', `#include <project_vertex>
        vec4 _wp = modelMatrix * vec4(position, 1.0);
        vWpos = _wp.xyz;
        vReflUv = uReflMat * _wp;`);
    sh.fragmentShader = sh.fragmentShader
      .replace('#include <common>', `#include <common>
        uniform sampler2D tRefl; uniform float uReflAmt, uReflOn, uTime;
        varying vec4 vReflUv; varying vec3 vWpos;`)
      .replace('#include <tonemapping_fragment>', `
        if(uReflOn > 0.5 && uReflAmt > 0.001){
          vec3 V = normalize(cameraPosition - vWpos);
          float fres = pow(1.0 - clamp(V.y, 0.0, 1.0), 4.0);
          // rougher planks scatter the reflection more
          float rgh = roughnessFactor;
          vec2 base = vReflUv.xy / max(vReflUv.w, 1e-4);
          float blur = 0.004 + rgh * 0.022;
          vec3 r = vec3(0.0);
          r += texture2D(tRefl, base).rgb * 0.4;
          r += texture2D(tRefl, base + vec2( blur, 0.0)).rgb * 0.15;
          r += texture2D(tRefl, base + vec2(-blur, 0.0)).rgb * 0.15;
          r += texture2D(tRefl, base + vec2(0.0,  blur * 1.6)).rgb * 0.15;
          r += texture2D(tRefl, base + vec2(0.0, -blur * 1.6)).rgb * 0.15;
          float inside = step(0.0, base.x) * step(base.x, 1.0) * step(0.0, base.y) * step(base.y, 1.0);
          // clamp: an unclamped grazing reflection of the city blew out to white
          gl_FragColor.rgb += min(r, vec3(1.6)) * uReflAmt * fres * inside * (1.0 - rgh * 0.75);
        }
        #include <tonemapping_fragment>`);
  };
  floorMat.customProgramCacheKey = () => 'floorRefl';
  applyDetail(floorMat, { scale: 0.06, strength: 0.45, fade: 5.0, rough: 0.05 });
  reflectiveFloor.material = floorMat;
  reflectiveFloor.uniforms = fu;

  // one oak tile is 1.45 m across the planks × 2.0 m along them
  const floor = new THREE.Mesh(planeUv(new THREE.PlaneGeometry(X * 2, Z * 2), X * 2, Z * 2, 1.45, 2.0), floorMat);
  floor.rotation.x = -Math.PI / 2;
  floor.receiveShadow = true;
  floor.name = 'floor';
  g.add(floor);

  /* ---- walls & ceiling: warm limewash plaster ---- */
  const PLASTER_TILE = 3.3;
  const wallMat = new THREE.MeshStandardMaterial({
    color: 0xc6b39c, metalness: 0, envMapIntensity: 0.8,
  });
  applyMaps(wallMat, 'plasterWall', { aniso: MAX_ANISO, normalScale: 0.40 });
  applyDetail(wallMat, { scale: 0.05, strength: 0.5, fade: 4.0, rough: 0.05 });
  const ceilMat = new THREE.MeshStandardMaterial({
    color: 0xb0a496, metalness: 0, envMapIntensity: 0.65,
  });
  applyMaps(ceilMat, 'plasterCeiling', { aniso: MAX_ANISO, normalScale: 0.4 });

  // UVs in metres so every panel keeps the same plaster grain, whatever its
  // size; cu/cv place the panel within one continuous wall surface
  const wall = (w, h, mat, cu = 0, cv = 0) =>
    new THREE.Mesh(planeUv(new THREE.PlaneGeometry(w, h), w, h, PLASTER_TILE, PLASTER_TILE, cu, cv), mat);

  // back wall (z = +Z), facing -z
  const back = wall(X * 2, H, wallMat);
  back.position.set(0, H / 2, Z); back.rotation.y = Math.PI;
  back.receiveShadow = true; g.add(back);

  // left wall (x = -X) — built in four panels so the firebox is a real hole in it
  {
    const F = FIREBOX;
    const fy0 = F.y - F.h / 2, fy1 = F.y + F.h / 2;
    const fz0 = F.z - F.w / 2, fz1 = F.z + F.w / 2;
    // rotated +90° about Y, the plane's local +x runs along world -z, so the
    // surface coordinate u is -z and v is y
    const panel = (w, h, cz, cy) => {
      const m = wall(w, h, wallMat, -cz, cy);
      m.position.set(-X, cy, cz); m.rotation.y = Math.PI / 2;
      m.receiveShadow = true; g.add(m); return m;
    };
    panel(Z * 2, fy0, 0, fy0 / 2);                             // below the opening
    panel(Z * 2, H - fy1, 0, (H + fy1) / 2);                   // above
    panel(fz0 + Z, F.h, (-Z + fz0) / 2, F.y);                  // toward -z
    panel(Z - fz1, F.h, (fz1 + Z) / 2, F.y);                   // toward +z
  }

  // right wall (x = +X) only from z = -0.4 back; the front part is glass
  const rightW = Z - (-0.4);
  const right = wall(rightW, H, wallMat);
  right.position.set(X, H / 2, (-0.4 + Z) / 2); right.rotation.y = -Math.PI / 2;
  right.receiveShadow = true; g.add(right);

  // ceiling
  const ceil = wall(X * 2, Z * 2, ceilMat);
  ceil.position.set(0, H, 0); ceil.rotation.x = Math.PI / 2;
  ceil.receiveShadow = true; g.add(ceil);

  /* ---- perimeter cove: a shadow gap with a warm strip hidden inside ---- */
  const coveMat = new THREE.MeshStandardMaterial({ color: 0x4a4038, roughness: 0.95 });
  const strip = new THREE.MeshBasicMaterial({ color: 0x4e2d13, toneMapped: false });
  const coveDrop = 0.12, coveIn = 0.26, coveGap = 0.09;
  /* A ledge held clear of the ceiling with the strip lying in the trough on
     top of it, facing up. Hung tight to the ceiling with the strip underneath
     — which is what this was — you see the strip itself as a bright bar
     across the wall instead of a wash on the ceiling.
     `along` is 'x' or 'z'; `push` moves the strip toward the wall so the
     ledge's own lip hides it from anywhere in the room. */
  const addCove = (len, x, z, along, push) => {
    const dim = along === 'x' ? [len, coveDrop, coveIn] : [coveIn, coveDrop, len];
    const shell = new THREE.Mesh(new THREE.BoxGeometry(...dim), coveMat);
    shell.position.set(x, H - coveGap - coveDrop / 2, z);
    g.add(shell);
    const ldim = along === 'x' ? [len * 0.985, 0.010, coveIn * 0.40] : [coveIn * 0.40, 0.010, len * 0.985];
    const lp = new THREE.Mesh(new THREE.BoxGeometry(...ldim), strip);
    lp.position.set(
      x + (along === 'z' ? push : 0),
      H - coveGap + 0.006,
      z + (along === 'x' ? push : 0));
    g.add(lp);
  };
  // cove along the two solid walls only (the glass walls get a slim shadow gap)
  addCove(Z * 2, -X + coveIn / 2, 0, 'z', -coveIn * 0.24);
  addCove(X * 2, 0, Z - coveIn / 2, 'x', coveIn * 0.24);

  /* ---- skirting / floor edge trim on the solid walls ---- */
  const trimMat = new THREE.MeshStandardMaterial({ color: 0x3a322b, roughness: 0.7 });
  const t1 = new THREE.Mesh(new THREE.BoxGeometry(0.04, 0.10, Z * 2), trimMat);
  t1.position.set(-X + 0.02, 0.05, 0); g.add(t1);
  const t2 = new THREE.Mesh(new THREE.BoxGeometry(X * 2, 0.10, 0.04), trimMat);
  t2.position.set(0, 0.05, Z - 0.02); g.add(t2);

  /* ---- doorway on the back wall: a warm slice of the hallway ---- */
  const doorW = 1.05, doorH = 2.25;
  const jamb = new THREE.MeshStandardMaterial({ color: 0x2e2823, roughness: 0.6, metalness: 0.2 });
  const dGroup = new THREE.Group();
  dGroup.position.set(3.35, 0, Z - 0.02);
  const dGlow = new THREE.Mesh(
    new THREE.PlaneGeometry(doorW, doorH),
    new THREE.MeshBasicMaterial({ color: 0x6b431f, toneMapped: false }),
  );
  dGlow.position.set(0, doorH / 2, -0.06); dGlow.rotation.y = Math.PI;
  dGroup.add(dGlow);
  const dTop = new THREE.Mesh(new THREE.BoxGeometry(doorW + 0.14, 0.07, 0.14), jamb);
  dTop.position.set(0, doorH + 0.03, -0.06); dGroup.add(dTop);
  [-1, 1].forEach((s) => {
    const p = new THREE.Mesh(new THREE.BoxGeometry(0.07, doorH, 0.14), jamb);
    p.position.set(s * (doorW / 2 + 0.035), doorH / 2, -0.06); dGroup.add(p);
  });
  dGroup.rotation.y = Math.PI;
  g.add(dGroup);

  roomScene.add(g);
  return { group: g, floor, floorMat, wallMat };
}

/* ============================================================ windows ==== */

const GLASS_FRAG = GLSL_NOISE + /* glsl */`
uniform sampler2D tBack, tBackBlur, tRefl;
uniform vec2  uRes;
uniform float uTime, uRainAmt, uReflAmt, uReflOn, uFlicker, uFire, uFlash;
uniform vec2  uUvScale;
uniform vec3  uFireDir, uTint;
varying vec2  vUv;
varying vec4  vReflUv;
varying vec3  vWpos, vNrm;

/* one layer of sliding droplets + their trails.
   returns xy = surface slope (drives refraction), z = wetness mask       */
vec3 dropLayer(vec2 uv, float t, float seed){
  vec2 UV = uv;
  uv.y += t * 0.62;
  vec2 grid = vec2(6.0, 1.0) * 2.0;
  vec2 id = floor(uv * grid);
  uv.y += hash11(id.x * 31.7 + seed) * 0.55;      // per-column phase offset
  id = floor(uv * grid);
  vec3 n = vec3(hash12(id + seed), hash12(id + seed + 7.7), hash12(id + seed + 3.3));
  vec2 st = fract(uv * grid) - vec2(0.5, 0.0);

  float x = n.x - 0.5;
  float wig = sin(UV.y * 20.0 + sin(UV.y * 20.0));
  x += wig * (0.5 - abs(x)) * (n.z - 0.5);
  x *= 0.72;

  float ti = fract(t * (1.0 + n.z * 0.35) + n.y);
  float y = (smoothstep(0.0, 0.85, ti) + smoothstep(0.85, 1.0, ti)) * 0.9 - 0.5;

  vec2 p = vec2(x, y);
  float d  = length((st - p) * vec2(1.0, 6.0));
  float head = smoothstep(0.4, 0.0, d);

  float r  = sqrt(smoothstep(1.0, y, st.y));
  float cd = abs(st.x - x);
  float trail = smoothstep(0.22 * r, 0.14 * r * r, cd) * smoothstep(-0.02, 0.02, st.y - y) * r * r;

  float yy = fract(UV.y * 10.0) + (st.y - 0.5);
  float drops = smoothstep(0.3, 0.0, length(st - vec2(x, yy))) * r * trail;

  float m = head + drops * 0.55 + trail * 0.12;
  return vec3((st - p) * m * vec2(1.0, 0.35), clamp(m, 0.0, 1.0));
}

void main(){
  vec2 suv = gl_FragCoord.xy / uRes;
  float t  = uTime;
  float ra = clamp(uRainAmt, 0.0, 1.5);

  /* --- droplet field, three scales; uUvScale keeps drops round in world space --- */
  vec2 auv = vUv * uUvScale;
  vec3 d0 = dropLayer(auv * 1.35, t * 0.32, 0.0);
  vec3 d1 = dropLayer(auv * 2.30, t * 0.44, 5.7);
  vec3 d2 = dropLayer(auv * 4.10, t * 0.58, 13.1);
  vec3 drop = d0 * 1.0 + d1 * 0.75 + d2 * 0.5;
  drop *= ra;

  /* static condensation film — cleared where water has run */
  float haze = fbm2(vUv * vec2(26.0, 9.0) + 3.0) * 0.6 + fbm2(vUv * vec2(7.0, 3.0)) * 0.4;
  haze = smoothstep(0.42, 0.98, haze) * (0.20 + 0.26 * ra);
  haze *= smoothstep(0.85, 0.1, vUv.y * 0.6 + 0.2);              // heavier low on the pane
  haze = clamp(haze - clamp(drop.z, 0.0, 1.0) * 1.6, 0.0, 1.0);

  vec2 off = drop.xy * vec2(0.16, 0.16);
  vec3 sharp = texture2D(tBack, clamp(suv + off, vec2(0.001), vec2(0.999))).rgb;
  vec3 soft  = texture2D(tBackBlur, clamp(suv + off * 0.35, vec2(0.002), vec2(0.998))).rgb;

  float wet = clamp(drop.z * 2.4, 0.0, 1.0);
  float clearGlass = clamp(1.0 - haze, 0.0, 1.0);
  vec3 outside = mix(soft, sharp, max(clearGlass, wet));
  outside += vec3(0.055, 0.062, 0.075) * haze * 0.9;             // milky condensation

  outside *= uTint;

  /* --- droplets behave like little lenses --- */
  vec3 nrm = normalize(vec3(-drop.x * 9.0, -drop.y * 9.0, 1.0));
  float rim = pow(clamp(length(drop.xy) * 3.4, 0.0, 1.0), 1.4);

  vec3 col = outside;
  // each bead concentrates the city behind it and picks up a cold rim
  col += outside * wet * 0.55;
  col += vec3(0.34, 0.44, 0.62) * rim * wet * 0.30;
  // and catches the firelight from inside the room
  float spec = pow(max(dot(nrm, normalize(uFireDir)), 0.0), 14.0);
  col += vec3(1.0, 0.50, 0.19) * spec * wet * uFire * uFlicker * 1.35;

  /* --- the room mirrored in the dark glass --- */
  float fres = pow(1.0 - abs(dot(normalize(cameraPosition - vWpos), normalize(vNrm))), 3.4);
  fres = 0.06 + fres * 0.94;
  if(uReflOn > 0.5 && uReflAmt > 0.001){
    vec2 rb = vReflUv.xy / max(vReflUv.w, 1e-4);
    vec2 rd = rb + drop.xy * 0.05;
    float inside = step(0.0, rd.x) * step(rd.x, 1.0) * step(0.0, rd.y) * step(rd.y, 1.0);
    vec3 refl = texture2D(tRefl, clamp(rd, vec2(0.001), vec2(0.999))).rgb;
    col += refl * uReflAmt * fres * inside * (0.65 + 0.5 * wet);
  }

  /* faint sheen so the pane still reads as glass where nothing is behind it */
  col += vec3(0.10, 0.12, 0.16) * fres * 0.16;
  col += vec3(0.35, 0.42, 0.58) * uFlash * 0.35;

  gl_FragColor = vec4(col, 1.0);
}
`;

const GLASS_VERT = /* glsl */`
uniform mat4 uReflMat;
varying vec2 vUv;
varying vec4 vReflUv;
varying vec3 vWpos, vNrm;
void main(){
  vUv = uv;
  vec4 wp = modelMatrix * vec4(position, 1.0);
  vWpos = wp.xyz;
  vNrm  = normalize(mat3(modelMatrix) * normal);
  vReflUv = uReflMat * wp;
  gl_Position = projectionMatrix * viewMatrix * wp;
}
`;

function makeGlassMaterial(widthM, heightM) {
  const uvScale = new THREE.Vector2(widthM * 2.0, heightM * 0.91);
  const m = new THREE.ShaderMaterial({
    uniforms: {
      tBack:     { value: null },
      tBackBlur: { value: null },
      tRefl:     { value: null },
      uReflMat:  { value: new THREE.Matrix4() },
      uRes:      { value: new THREE.Vector2(1, 1) },
      uTime:     U.time,
      uRainAmt:  U.rain,
      uFire:     U.fire,
      uFlicker:  U.flicker,
      uFlash:    U.flash,
      uReflOn:   U.reflOn,
      uReflAmt:  { value: 0.85 },
      uFireDir:  { value: new THREE.Vector3(-0.6, 0.25, 0.4).normalize() },
      uTint:     { value: new THREE.Color(0.90, 0.94, 1.02) },
      uUvScale:  { value: uvScale },
    },
    vertexShader: GLASS_VERT,
    fragmentShader: GLASS_FRAG,
  });
  glassMaterials.push(m);
  return m;
}

export function buildWindows() {
  const g = new THREE.Group();
  g.name = 'windows';

  const frameMat = new THREE.MeshStandardMaterial({
    color: 0x2b2723, roughness: 0.42, metalness: 0.75, envMapIntensity: 0.9,
  });

  /* ---- front wall (z = -Z): five full-height bays ---- */
  // ~3 cm droplet cells across, ~40 cm runnels down — see makeGlassMaterial
  const frontGlass = new THREE.Mesh(new THREE.PlaneGeometry(X * 2, H), makeGlassMaterial(X * 2, H));
  frontGlass.position.set(0, H / 2, -Z);
  frontGlass.name = 'glassFront';
  g.add(frontGlass);

  /* ---- right wall front section (x = +X, z in [-Z, -0.4]) ---- */
  const sideW = -0.4 - (-Z);
  const sideGlass = new THREE.Mesh(new THREE.PlaneGeometry(sideW, H), makeGlassMaterial(sideW, H));
  sideGlass.position.set(X, H / 2, -Z + sideW / 2);
  sideGlass.rotation.y = -Math.PI / 2;
  sideGlass.name = 'glassSide';
  g.add(sideGlass);

  /* ---- mullions, head & sill rails ---- */
  const mull = (x, y, z, w, h, d, ry = 0) => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), frameMat);
    m.position.set(x, y, z); m.rotation.y = ry;
    m.castShadow = false; m.receiveShadow = true;
    g.add(m); return m;
  };
  const MW = 0.055, MD = 0.10;
  for (let i = 0; i <= 5; i++) {
    const x = -X + (i * (X * 2)) / 5;
    mull(x, H / 2, -Z + MD / 2, MW, H, MD);
  }
  mull(0, 0.035, -Z + MD / 2, X * 2, 0.07, MD + 0.03);              // sill
  mull(0, H - 0.045, -Z + MD / 2, X * 2, 0.09, MD + 0.02);          // head

  for (let i = 0; i <= 2; i++) {
    const z = -Z + (i * sideW) / 2;
    mull(X - MD / 2, H / 2, z, MD, H, MW);
  }
  mull(X - MD / 2, 0.035, -Z + sideW / 2, MD + 0.03, 0.07, sideW);
  mull(X - MD / 2, H - 0.045, -Z + sideW / 2, MD + 0.02, 0.09, sideW);

  // jamb where the side glazing meets the solid wall
  mull(X - MD / 2, H / 2, -0.4, MD, H, MW);

  /* ---- sheer curtains framing the composition ---- */
  const sheerMat = new THREE.MeshStandardMaterial({
    color: 0xe6dccd, roughness: 0.98, metalness: 0, envMapIntensity: 0.15,
    transparent: true, opacity: 0.13, side: THREE.DoubleSide, depthWrite: false,
  });
  const makeSheer = (w, h, folds) => {
    const geo = new THREE.PlaneGeometry(w, h, folds * 4, 10);
    const p = geo.attributes.position;
    for (let i = 0; i < p.count; i++) {
      const x = p.getX(i), y = p.getY(i);
      const t = (x / w + 0.5);
      const wave = Math.sin(t * Math.PI * 2 * folds) * 0.055;
      const sag = Math.sin(t * Math.PI * 2 * folds + 1.1) * 0.02 * (1 - (y / h + 0.5));
      p.setZ(i, wave + sag);
    }
    geo.computeVertexNormals();
    return geo;
  };
  const cur1 = new THREE.Mesh(makeSheer(1.05, H - 0.12, 4), sheerMat);
  cur1.position.set(-X + 0.62, (H - 0.12) / 2, -Z + 0.22);
  cur1.renderOrder = 2; g.add(cur1);
  const cur2 = new THREE.Mesh(makeSheer(0.92, H - 0.12, 3), sheerMat);
  cur2.position.set(X - 0.58, (H - 0.12) / 2, -Z + 0.22);
  cur2.renderOrder = 2; g.add(cur2);

  // curtain track
  mull(0, H - 0.13, -Z + 0.24, X * 2 - 0.1, 0.03, 0.03);

  roomScene.add(g);
  return { group: g, frontGlass, sideGlass, curtains: [cur1, cur2] };
}

/* ========================================================== fireplace ==== */
export function buildFireplace() {
  const g = new THREE.Group();
  g.name = 'fireplace';

  const STONE_TILE = 2.0;
  const stoneMat = new THREE.MeshStandardMaterial({
    color: 0xf0e9df, metalness: 0.04, envMapIntensity: 0.55,
  });
  applyMaps(stoneMat, 'honedStone', { aniso: MAX_ANISO, normalScale: 0.7 });
  applyDetail(stoneMat, { scale: 0.045, strength: 0.45, fade: 3.5, rough: 0.06 });
  const darkMat = new THREE.MeshStandardMaterial({ color: 0x120d0a, roughness: 0.95, metalness: 0 });
  const steelMat = new THREE.MeshStandardMaterial({ color: 0x1a1715, roughness: 0.35, metalness: 0.85 });

  const F = FIREBOX;
  const WALL_X = -ROOM.x;
  const FZ = F.z;                                  // fireplace centre along z
  const SW = F.panelW;                             // stone panel width
  const PT = F.panelT;                             // panel thickness (proud of the wall)
  const BW = F.w, BH = F.h, BD = F.d;              // firebox opening
  const BY = F.y;                                  // opening centre height
  const y0 = BY - BH / 2, y1 = BY + BH / 2;

  /* stone panel, built as four slabs so the firebox is a genuine opening */
  const slab = (h, d, y, z) => {
    const m = new THREE.Mesh(
      boxUv(new THREE.BoxGeometry(PT, h, d), PT, h, d, STONE_TILE, [WALL_X + PT / 2, y, z]), stoneMat);
    m.position.set(WALL_X + PT / 2, y, z);
    m.castShadow = true; m.receiveShadow = true;
    g.add(m); return m;
  };
  slab(y0, SW, y0 / 2, FZ);                                    // below
  slab(ROOM.h - y1, SW, (ROOM.h + y1) / 2, FZ);                // above
  slab(BH, (SW - BW) / 2, BY, FZ - BW / 2 - (SW - BW) / 4);    // left of opening
  slab(BH, (SW - BW) / 2, BY, FZ + BW / 2 + (SW - BW) / 4);    // right of opening

  /* the firebox recess behind the opening */
  const box = new THREE.Group();
  box.position.set(WALL_X, BY, FZ);
  const back = new THREE.Mesh(new THREE.PlaneGeometry(BW, BH), darkMat);
  back.position.set(-BD, 0, 0); back.rotation.y = Math.PI / 2; box.add(back);
  const topP = new THREE.Mesh(new THREE.PlaneGeometry(BD, BW), darkMat);
  topP.position.set(-BD / 2, BH / 2, 0); topP.rotation.set(Math.PI / 2, 0, 0); box.add(topP);
  const botP = new THREE.Mesh(new THREE.PlaneGeometry(BD, BW), darkMat);
  botP.position.set(-BD / 2, -BH / 2, 0); botP.rotation.set(-Math.PI / 2, 0, 0); box.add(botP);
  [-1, 1].forEach((s) => {
    const sp = new THREE.Mesh(new THREE.PlaneGeometry(BD, BH), darkMat);
    sp.position.set(-BD / 2, 0, (s * BW) / 2);
    sp.rotation.y = s > 0 ? Math.PI : 0;
    box.add(sp);
  });
  g.add(box);

  /* slim blackened-steel reveal framing the opening (four bars, not a slab) */
  const RB = 0.045;
  const revX = WALL_X + PT + 0.008;
  const bar = (w, h, d, y, z) => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), steelMat);
    m.position.set(revX, y, z); g.add(m);
  };
  bar(0.03, RB, BW + RB * 2, y0 - RB / 2, FZ);
  bar(0.03, RB, BW + RB * 2, y1 + RB / 2, FZ);
  bar(0.03, BH, RB, BY, FZ - BW / 2 - RB / 2);
  bar(0.03, BH, RB, BY, FZ + BW / 2 + RB / 2);

  /* hearth ledge */
  const hearth = new THREE.Mesh(boxUv(new THREE.BoxGeometry(0.62, 0.14, SW - 0.6), 0.62, 0.14, SW - 0.6, STONE_TILE, [WALL_X + 0.31, 0.07, FZ]), stoneMat);
  hearth.position.set(WALL_X + 0.31, 0.07, FZ);
  hearth.castShadow = true; hearth.receiveShadow = true;
  g.add(hearth);

  /* mantel shelf */
  const mantel = new THREE.Mesh(boxUv(new THREE.BoxGeometry(0.30, 0.08, 3.0), 0.30, 0.08, 3.0, STONE_TILE, [WALL_X + 0.15, 1.62, FZ]), stoneMat);
  mantel.position.set(WALL_X + 0.15, 1.62, FZ);
  mantel.castShadow = true; mantel.receiveShadow = true;
  g.add(mantel);

  /* Two floating oak shelves above the mantel, and things standing on them.
     Bare, they caught the firelight along their whole length and read as two
     neon bars across the chimney breast; a darker oak and something to break
     the run is all it takes. */
  const oak = new THREE.MeshStandardMaterial({ color: 0x584330, metalness: 0, roughness: 0.85, envMapIntensity: 0.22 });
  applyMaps(oak, 'oakFloor', { repeat: [0.4, 0.4], aniso: MAX_ANISO, normalScale: 0.5 });
  const shelfY = [2.16, 2.70];
  const shelfLen = [1.30, 1.00], shelfZ = [FZ + 0.22, FZ - 0.30];
  for (let i = 0; i < 2; i++) {
    const sh = new THREE.Mesh(new THREE.BoxGeometry(0.22, 0.036, shelfLen[i]), oak);
    sh.position.set(WALL_X + PT + 0.11, shelfY[i], shelfZ[i]);
    sh.castShadow = true; sh.receiveShadow = true; g.add(sh);
  }

  /* what lives on the shelves and the mantel */
  const clay = new THREE.MeshStandardMaterial({ color: 0x6d5c49, roughness: 0.82, envMapIntensity: 0.35 });
  const clayPale = new THREE.MeshStandardMaterial({ color: 0x9a8f7c, roughness: 0.68, envMapIntensity: 0.4 });
  const spine = (hex) => new THREE.MeshStandardMaterial({ color: hex, roughness: 0.85, envMapIntensity: 0.25 });

  const put = (mesh, x, y, z, ry = 0) => {
    mesh.position.set(x, y, z); mesh.rotation.y = ry;
    mesh.castShadow = true; mesh.receiveShadow = true; g.add(mesh);
    return mesh;
  };
  const SX = WALL_X + PT + 0.12;

  // a tall stoneware vase and a squat bowl on the lower shelf
  put(new THREE.Mesh(new THREE.CylinderGeometry(0.045, 0.062, 0.26, 14), clay),
      SX - 0.01, shelfY[0] + 0.148, shelfZ[0] - 0.44);
  put(new THREE.Mesh(new THREE.SphereGeometry(0.075, 14, 10, 0, Math.PI * 2, 0, Math.PI * 0.62), clayPale),
      SX, shelfY[0] + 0.062, shelfZ[0] + 0.42);
  // books stacked flat, with one leaning against them
  let by = shelfY[0] + 0.019;
  [[0.155, 0x3d4a3f], [0.145, 0x7a3b2c], [0.150, 0x2f3038]].forEach(([w, hex], i) => {
    const b = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.028, w), spine(hex));
    put(b, SX - 0.005, by + 0.014, shelfZ[0] + 0.06 + i * 0.008, 0.05 * i);
    by += 0.028;
  });
  const lean = new THREE.Mesh(new THREE.BoxGeometry(0.155, 0.026, 0.20), spine(0x8a6a3a));
  lean.rotation.set(0, 0.08, 0); lean.position.set(SX, by + 0.03, shelfZ[0] + 0.24);
  lean.rotateX(-1.15); lean.castShadow = true; g.add(lean);

  // upper shelf: a framed print leaning back, and a small brass-ish tin
  const frame = new THREE.Mesh(new THREE.BoxGeometry(0.022, 0.34, 0.26),
    new THREE.MeshStandardMaterial({ color: 0x4a3b2c, roughness: 0.6, envMapIntensity: 0.4 }));
  frame.position.set(SX + 0.045, shelfY[1] + 0.19, shelfZ[1] - 0.20);
  frame.rotation.z = 0.16;
  frame.castShadow = true; g.add(frame);
  put(new THREE.Mesh(new THREE.CylinderGeometry(0.052, 0.052, 0.10, 14), clay),
      SX - 0.01, shelfY[1] + 0.069, shelfZ[1] + 0.32);

  // mantel: a pair of candles and a shallow dish
  [[-0.44, 0.13], [-0.30, 0.085]].forEach(([dz, ch]) => {
    const c = new THREE.Mesh(new THREE.CylinderGeometry(0.026, 0.027, ch, 12), clayPale);
    put(c, WALL_X + 0.14, 1.66 + ch / 2, FZ + dz);
  });
  put(new THREE.Mesh(new THREE.CylinderGeometry(0.10, 0.085, 0.04, 18), clay),
      WALL_X + 0.15, 1.68, FZ + 0.55);

  /* ------------------------------------------------------- log & embers */
  const fireGroup = new THREE.Group();
  fireGroup.position.set(WALL_X + 0.02, BY - BH / 2 + 0.02, FZ);
  g.add(fireGroup);

  const charMat = new THREE.MeshStandardMaterial({ color: 0xffffff, metalness: 0 });
  applyMaps(charMat, 'charredLog', { repeat: [2, 4], aniso: MAX_ANISO, normalScale: 1.0 });
  // cylinder axis is local +Y; rotation.x = π/2 lays it along +Z (across the firebox)
  const logGeo = new THREE.CylinderGeometry(0.075, 0.062, 1.0, 9);
  const logs = [
    { p: [-0.30, 0.10, -0.30], r: [Math.PI / 2, 0, 0.10], s: [1.15, 1.35, 1.15] },
    { p: [-0.33, 0.10, 0.26], r: [Math.PI / 2 + 0.05, 0, -0.14], s: [1.1, 1.25, 1.1] },
    { p: [-0.30, 0.26, -0.02], r: [Math.PI / 2 - 0.07, 0, 0.06], s: [1.2, 1.45, 1.2] },
    { p: [-0.24, 0.25, 0.34], r: [Math.PI / 2 + 0.14, 0, -0.22], s: [1.0, 1.0, 1.0] },
    { p: [-0.36, 0.09, 0.58], r: [Math.PI / 2, 0, 0.30], s: [0.9, 0.85, 0.9] },
  ];
  logs.forEach((L) => {
    const m = new THREE.Mesh(logGeo, charMat);
    m.position.set(...L.p); m.rotation.set(...L.r); m.scale.set(...L.s);
    m.castShadow = true; m.receiveShadow = true; fireGroup.add(m);
  });

  /* glowing coal bed */
  const emberBed = new THREE.Mesh(
    new THREE.PlaneGeometry(BD * 0.82, BW * 0.80),
    new THREE.ShaderMaterial({
      transparent: true, depthWrite: false,
      uniforms: { uTime: U.time, uFire: U.fire, uFlicker: U.flicker },
      vertexShader: `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
      fragmentShader: GLSL_NOISE + /* glsl */`
        varying vec2 vUv; uniform float uTime, uFire, uFlicker;
        void main(){
          vec2 p = vUv * vec2(5.0, 14.0);
          float n = fbm2(p + vec2(0.0, uTime * 0.05));
          float n2 = fbm2(p * 2.3 - vec2(uTime * 0.09, 0.0));
          float glow = smoothstep(0.42, 0.82, n * 0.65 + n2 * 0.45);
          glow *= 0.55 + 0.45 * sin(uTime * 1.7 + n * 12.0);
          float edge = smoothstep(0.0, 0.28, vUv.y) * smoothstep(1.0, 0.72, vUv.y)
                     * smoothstep(0.0, 0.22, vUv.x) * smoothstep(1.0, 0.7, vUv.x);
          vec3 c = mix(vec3(0.35,0.03,0.0), vec3(1.0,0.42,0.06), glow);
          c = mix(c, vec3(1.0,0.80,0.36), smoothstep(0.72, 1.0, glow));
          float a = (0.25 + glow * 0.95) * edge;
          gl_FragColor = vec4(c * (0.09 + glow * 0.62) * uFire * uFlicker, a);
        }`,
      blending: THREE.AdditiveBlending,
    }),
  );
  emberBed.rotation.x = -Math.PI / 2;
  emberBed.position.set(-BD / 2 + 0.02, 0.012, 0);
  fireGroup.add(emberBed);

  /* ------------------------------------------------------------- flames */
  const flameMat = (seed, speed, intensity) => new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide,
    uniforms: {
      uTime: U.time, uFire: U.fire, uFlicker: U.flicker,
      uSeed: { value: seed }, uSpeed: { value: speed }, uInt: { value: intensity },
    },
    vertexShader: `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
    fragmentShader: GLSL_NOISE + /* glsl */`
      varying vec2 vUv;
      uniform float uTime, uSeed, uSpeed, uInt, uFire, uFlicker;
      void main(){
        float t = uTime * uSpeed;
        vec2 uv = vUv;
        float y = uv.y;

        vec3 q  = vec3(uv.x * 3.2, y * 1.7 - t * 0.95, uSeed + t * 0.16);
        float n  = fbm3(q);
        float n2 = fbm3(q * 2.35 + vec3(0.0, -t * 0.55, 3.7));

        float w = 0.40 * (1.0 - pow(y, 0.80)) * (0.85 + 0.28 * sin(t * 2.2 + uSeed * 7.0 + y * 3.2));
        float sway = (n2 - 0.5) * 0.34 * y + sin(t * 1.75 + uSeed * 11.0 + y * 4.6) * 0.06 * y;
        float body = smoothstep(w, w * 0.12, abs(uv.x - 0.5 + sway));

        float d = body * (1.16 - y * 0.30) - (n * 0.92 + n2 * 0.38) * (0.56 + y * 0.95) + 0.07;
        d  = clamp(d, 0.0, 1.0);
        d *= smoothstep(0.0, 0.09, y) * smoothstep(1.0, 0.52, y);

        vec3 c = mix(vec3(0.55,0.045,0.0), vec3(1.0,0.28,0.02), smoothstep(0.04, 0.34, d));
        c = mix(c, vec3(1.0,0.62,0.16), smoothstep(0.34, 0.62, d));
        c = mix(c, vec3(1.0,0.93,0.66), smoothstep(0.62, 0.90, d));

        float amt = d * uInt * uFire * (0.75 + 0.45 * uFlicker);
        if(amt < 0.002) discard;
        gl_FragColor = vec4(c * amt * 0.95, amt);
      }`,
  });

  const flames = new THREE.Group();
  flames.position.set(-0.20, 0.10, 0);
  fireGroup.add(flames);
  const flameDefs = [
    { w: 1.30, h: 0.84, x: 0.00, seed: 0.0, sp: 1.00, i: 1.00 },
    { w: 0.90, h: 0.70, x: -0.46, seed: 4.3, sp: 1.28, i: 0.82 },
    { w: 0.92, h: 0.74, x: 0.48, seed: 9.1, sp: 1.14, i: 0.86 },
    { w: 0.66, h: 0.58, x: 0.14, seed: 15.7, sp: 1.55, i: 0.70 },
  ];
  flameDefs.forEach((f) => {
    const geo = new THREE.PlaneGeometry(f.w, f.h, 1, 1);
    geo.translate(0, f.h / 2, 0);
    const m = new THREE.Mesh(geo, flameMat(f.seed, f.sp, f.i));
    m.position.set(0, 0, f.x);
    m.rotation.y = Math.PI / 2;             // face into the room (+x)
    m.renderOrder = 10;
    m.userData.billboard = true;            // softly tracked toward the camera in the app
    flames.add(m);
  });

  /* ---------------------------------------------------- rising embers */
  const EMB = 140;
  const eGeo = new THREE.BufferGeometry();
  const pos = new Float32Array(EMB * 3), seed = new Float32Array(EMB * 3);
  for (let i = 0; i < EMB; i++) {
    pos[i * 3] = 0; pos[i * 3 + 1] = 0; pos[i * 3 + 2] = 0;
    seed[i * 3] = rnd(); seed[i * 3 + 1] = rnd(); seed[i * 3 + 2] = rnd();
  }
  eGeo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  eGeo.setAttribute('aSeed', new THREE.BufferAttribute(seed, 3));
  const embers = new THREE.Points(eGeo, new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: { uTime: U.time, uFire: U.fire, uPix: { value: 1 } },
    vertexShader: GLSL_NOISE + /* glsl */`
      attribute vec3 aSeed;
      uniform float uTime, uFire, uPix;
      varying float vLife, vSeed;
      void main(){
        float sp = 0.30 + aSeed.z * 0.42;
        float life = fract(aSeed.x + uTime * sp * 0.22);
        vLife = life; vSeed = aSeed.y;
        float rise = life * (1.05 + aSeed.y * 0.85);
        float wob  = (vnoise3(vec3(aSeed.xy * 12.0, uTime * 0.55 + aSeed.z * 8.0)) - 0.5);
        vec3 p = vec3(
          (aSeed.y - 0.5) * 0.28 + wob * 0.22 * life + rise * 0.10,
          rise,
          (aSeed.z - 0.5) * 1.35 + wob * 0.30 * life
        );
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_Position = projectionMatrix * mv;
        gl_PointSize = clamp((0.55 + aSeed.z * 1.05) * uPix * (1.0 - life * 0.5) * 9.0 / max(-mv.z, 0.25), 0.0, 8.0);
      }`,
    fragmentShader: /* glsl */`
      varying float vLife, vSeed;
      uniform float uFire;
      void main(){
        float d = length(gl_PointCoord - 0.5) * 2.0;
        float a = smoothstep(1.0, 0.0, d);
        a *= smoothstep(0.0, 0.08, vLife) * smoothstep(1.0, 0.45, vLife);
        vec3 c = mix(vec3(1.0,0.72,0.28), vec3(1.0,0.25,0.05), vLife);
        gl_FragColor = vec4(c * (0.75 + vSeed * 0.6) * uFire, a * 0.8 * uFire);
      }`,
  }));
  embers.frustumCulled = false;
  embers.position.set(-0.26, 0.16, 0);
  fireGroup.add(embers);

  roomScene.add(g);
  return {
    group: g, flames, embers, emberBed,
    firePos: new THREE.Vector3(WALL_X + 0.15, BY, FZ),
    boxDepth: BD, mantelY: 1.62, panelZ: FZ, panelW: SW, wallX: WALL_X, panelT: PT,
  };
}
