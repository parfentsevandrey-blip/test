/* =========================================================================
   The shell, generated from js/room-plan.js.

   Walls are solid boxes with the openings genuinely cut out of them, so a
   doorway has a reveal you can see the thickness of and the firebox is a
   hole rather than a picture on a flat plane. Floors, ceilings, skirtings,
   coves, linings and the glazing all come off the same plan, so moving a
   wall in room-plan.js moves everything that touches it — including the
   boxes the walker collides with.
   ========================================================================= */
import * as THREE from 'three';
import {
  GLSL_NOISE, U, roomScene, boxUv, planeUv,
} from './room.js';
import { MAT, shadowed } from './room-mat.js';
import { APT, ROOMS, WALLS, EXT_WALLS, GLAZING, COLUMN, BEAM, inward, segLen } from './room-plan.js';

const H = APT.h, T = APT.wall;

/* materials the app has to finish wiring once the render targets exist */
export const glassMaterials = [];
export const reflectiveFloors = [];

/* ------------------------------------------------------- reflective floor */
/* The planar reflection is injected into the standard lighting result rather
   than replacing the material, so the floor still takes shadows and the
   environment normally. `grout` cuts joints into a large-format stone floor
   from world position — cheaper and seamless compared with laying slabs. */
function reflective(base, key, grout) {
  const u = {
    tRefl: { value: null },
    uReflMat: { value: new THREE.Matrix4() },
    uReflAmt: { value: 0.30 },
    uReflOn: U.reflOn,
  };
  base.onBeforeCompile = (sh) => {
    Object.assign(sh.uniforms, u);
    sh.vertexShader = sh.vertexShader
      .replace('#include <common>', `#include <common>
        uniform mat4 uReflMat; varying vec4 vReflUv; varying vec3 vWpos;`)
      .replace('#include <project_vertex>', `#include <project_vertex>
        vec4 _wp = modelMatrix * vec4(position, 1.0);
        vWpos = _wp.xyz;
        vReflUv = uReflMat * _wp;`);
    sh.fragmentShader = sh.fragmentShader
      .replace('#include <common>', `#include <common>
        uniform sampler2D tRefl; uniform float uReflAmt, uReflOn;
        varying vec4 vReflUv; varying vec3 vWpos;`)
      .replace('#include <tonemapping_fragment>', `
        ${grout ? `
        {
          vec2 j = abs(fract(vWpos.xz / ${grout.toFixed(3)} + 0.5) - 0.5) * ${grout.toFixed(3)};
          float w = fwidth(vWpos.x) * 1.2 + 0.0015;
          float line = 1.0 - smoothstep(0.004, 0.004 + w, min(j.x, j.y));
          gl_FragColor.rgb *= 1.0 - line * 0.55;
        }` : ''}
        if(uReflOn > 0.5 && uReflAmt > 0.001){
          vec3 V = normalize(cameraPosition - vWpos);
          float fres = pow(1.0 - clamp(V.y, 0.0, 1.0), 4.0);
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
  base.customProgramCacheKey = () => key;
  base.extensions = { derivatives: true };
  reflectiveFloors.push({ material: base, uniforms: u });
  return base;
}

/* -------------------------------------------------------------- geometry */
/** the solid pieces a wall segment breaks into once its openings are cut */
function wallPieces(seg) {
  const L = segLen(seg);
  const [ax, az] = seg.a;
  const ux = (seg.b[0] - ax) / L, uz = (seg.b[1] - az) / L;
  const alongX = Math.abs(ux) > 0.5;
  const out = [];
  const addBox = (s0, s1, y0, y1) => {
    if (s1 - s0 < 1e-3 || y1 - y0 < 1e-3) return;
    const cs = (s0 + s1) / 2, len = s1 - s0;
    out.push({
      x: ax + ux * cs, y: (y0 + y1) / 2, z: az + uz * cs,
      w: alongX ? len : T, h: y1 - y0, d: alongX ? T : len,
    });
  };
  let s = 0;
  for (const o of (seg.openings || []).slice().sort((a, b) => a.at - b.at)) {
    addBox(s, o.at - o.w / 2, 0, H);
    if ((o.sill || 0) > 0.001) addBox(o.at - o.w / 2, o.at + o.w / 2, 0, o.sill);
    if (o.h < H - 0.001) addBox(o.at - o.w / 2, o.at + o.w / 2, o.h, H);
    s = o.at + o.w / 2;
  }
  addBox(s, L, 0, H);
  return { pieces: out, alongX, ux, uz, L };
}

const WALL_TILE = 3.3;

function addWall(g, seg, mat) {
  const { pieces, alongX } = wallPieces(seg);
  for (const p of pieces) {
    const m = new THREE.Mesh(
      boxUv(new THREE.BoxGeometry(p.w, p.h, p.d), p.w, p.h, p.d, WALL_TILE, [p.x, p.y, p.z]), mat);
    m.position.set(p.x, p.y, p.z);
    m.receiveShadow = true;
    g.add(m);
    // Skirting: one box wrapping the wall does both faces at once, which is
    // both fewer meshes and automatically correct at a doorway, because the
    // wall pieces already stop there.
    if (p.y - p.h / 2 < 0.001) {
      const sk = 0.012, hgt = 0.11;
      const b = new THREE.Mesh(new THREE.BoxGeometry(
        alongX ? p.w : T + sk * 2, hgt, alongX ? T + sk * 2 : p.d), MAT.walnut);
      b.position.set(p.x, hgt / 2, p.z);
      g.add(b);
    }
  }
  return pieces;
}

/* ------------------------------------------------------------------ shell */
export function buildShell() {
  const g = new THREE.Group();
  g.name = 'shell';

  /* ---- floors, one per room, with the reflection injected ---- */
  const oakFloorMat = reflective(MAT.oakFloor, 'floorOak', 0);
  const stoneFloorMat = reflective(MAT.stoneFloor, 'floorStone', 0.92);
  const floors = [];
  for (const key of Object.keys(ROOMS)) {
    const r = ROOMS[key];
    const w = r.x1 - r.x0, d = r.z1 - r.z0;
    const mat = r.floor === 'stone' ? stoneFloorMat : oakFloorMat;
    // one oak tile is 1.45 m across the planks × 2.0 m along them
    const tu = r.floor === 'stone' ? 2.4 : 1.45, tv = r.floor === 'stone' ? 2.4 : 2.0;
    const f = new THREE.Mesh(
      planeUv(new THREE.PlaneGeometry(w, d), w, d, tu, tv, (r.x0 + r.x1) / 2, (r.z0 + r.z1) / 2), mat);
    f.rotation.x = -Math.PI / 2;
    f.position.set((r.x0 + r.x1) / 2, 0, (r.z0 + r.z1) / 2);
    f.receiveShadow = true;
    f.name = 'floor-' + key;
    g.add(f);
    floors.push(f);

    // ceiling, dropped where the plan asks for a soffit
    const cy = H - (r.soffit || 0);
    const c = new THREE.Mesh(
      planeUv(new THREE.PlaneGeometry(w, d), w, d, WALL_TILE, WALL_TILE, (r.x0 + r.x1) / 2, (r.z0 + r.z1) / 2),
      MAT.ceiling);
    c.rotation.x = Math.PI / 2;
    c.position.set((r.x0 + r.x1) / 2, cy, (r.z0 + r.z1) / 2);
    c.receiveShadow = true;
    g.add(c);
  }

  /* ---- walls ---- */
  for (const seg of EXT_WALLS) addWall(g, seg, MAT.plaster);
  for (const seg of WALLS) addWall(g, seg, MAT.plaster);

  /* ---- the structure that separates living from kitchen without a wall --- */
  const col = shadowed(new THREE.Mesh(
    boxUv(new THREE.BoxGeometry(COLUMN.w, H, COLUMN.d), COLUMN.w, H, COLUMN.d, WALL_TILE,
      [COLUMN.x, H / 2, COLUMN.z]), MAT.plaster));
  col.position.set(COLUMN.x, H / 2, COLUMN.z);
  g.add(col);

  const beamLen = BEAM.z1 - BEAM.z0;
  const beam = new THREE.Mesh(
    boxUv(new THREE.BoxGeometry(BEAM.w, BEAM.drop, beamLen), BEAM.w, BEAM.drop, beamLen, WALL_TILE,
      [BEAM.x, H - BEAM.drop / 2, (BEAM.z0 + BEAM.z1) / 2]), MAT.plaster);
  beam.position.set(BEAM.x, H - BEAM.drop / 2, (BEAM.z0 + BEAM.z1) / 2);
  beam.receiveShadow = true;
  g.add(beam);

  /* ---- door linings and leaves ---- */
  for (const seg of WALLS) {
    const L = segLen(seg);
    const ux = (seg.b[0] - seg.a[0]) / L, uz = (seg.b[1] - seg.a[1]) / L;
    const alongX = Math.abs(ux) > 0.5;
    for (const o of seg.openings || []) {
      if ((o.sill || 0) > 0.001) continue;
      const cx = seg.a[0] + ux * o.at, cz = seg.a[1] + uz * o.at;
      const lin = 0.035;
      const jamb = (s) => {
        const b = new THREE.Mesh(new THREE.BoxGeometry(
          alongX ? lin : T + 0.01, o.h + lin, alongX ? T + 0.01 : lin), MAT.walnut);
        b.position.set(cx + (alongX ? s * o.w / 2 : 0), (o.h + lin) / 2, cz + (alongX ? 0 : s * o.w / 2));
        g.add(b);
      };
      jamb(-1); jamb(1);
      const head = new THREE.Mesh(new THREE.BoxGeometry(
        alongX ? o.w + lin * 2 : T + 0.01, lin, alongX ? T + 0.01 : o.w + lin * 2), MAT.walnut);
      head.position.set(cx, o.h + lin / 2, cz);
      g.add(head);

      if (o.door === 'leaf') {
        // hung on the near jamb and left ajar, so you can see through it
        const pivot = new THREE.Group();
        pivot.position.set(cx - (alongX ? o.w / 2 : 0), 0, cz - (alongX ? 0 : o.w / 2));
        const leaf = shadowed(new THREE.Mesh(
          boxUv(new THREE.BoxGeometry(o.w - 0.012, o.h - 0.02, 0.042),
            o.w, o.h, 0.042, 1.6), MAT.walnut));
        leaf.position.set((o.w - 0.012) / 2, (o.h - 0.02) / 2, 0);
        pivot.add(leaf);
        const handle = new THREE.Mesh(new THREE.CylinderGeometry(0.011, 0.011, 0.12, 8), MAT.brass);
        handle.rotation.z = Math.PI / 2;
        handle.position.set(o.w - 0.09, 1.04, 0.05);
        pivot.add(handle);
        pivot.rotation.y = (alongX ? 0 : -Math.PI / 2) + 0.62;
        g.add(pivot);
      }
    }
  }

  /* ---- feature wall: vertical walnut slats behind the living room ---- */
  const slatGeo = new THREE.BoxGeometry(0.042, H - 0.14, 0.032);
  const slatCount = 62;
  const slats = new THREE.InstancedMesh(slatGeo, MAT.walnut, slatCount);
  const m4 = new THREE.Matrix4();
  const z = ROOMS.living.z1 - T / 2 - 0.018;
  for (let i = 0; i < slatCount; i++) {
    const x = -4.82 + i * 0.098;
    m4.makeTranslation(x, (H - 0.14) / 2 + 0.055, z);
    slats.setMatrixAt(i, m4);
  }
  slats.instanceMatrix.needsUpdate = true;
  slats.castShadow = true; slats.receiveShadow = true;
  g.add(slats);

  roomScene.add(g);
  return { group: g, floors, floorMat: oakFloorMat, wallMat: MAT.plaster };
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

function makeGlassMaterial(widthM, heightM, plane) {
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
      uUvScale:  { value: new THREE.Vector2(widthM * 2.0, heightM * 0.91) },
    },
    vertexShader: GLASS_VERT,
    fragmentShader: GLASS_FRAG,
  });
  // which reflector feeds it: the façade and the return face are different planes
  m.userData.plane = plane;
  glassMaterials.push(m);
  return m;
}

/* Glazing generated from the plan. Each run is one pane with its own mullion
   grid; splitting per run also means frustum culling can drop the panes you
   are not looking at, which matters because the drop shader is not cheap. */
export function buildWindows() {
  const g = new THREE.Group();
  g.name = 'windows';
  const panes = [];

  const MW = 0.055, MD = 0.10;
  for (const run of GLAZING) {
    const L = segLen(run);
    const [nx, nz] = inward(run);
    const ux = (run.b[0] - run.a[0]) / L, uz = (run.b[1] - run.a[1]) / L;
    const ry = Math.atan2(nx, nz);
    const cx = (run.a[0] + run.b[0]) / 2, cz = (run.a[1] + run.b[1]) / 2;
    const plane = Math.abs(nz) > 0.5 ? 'south' : 'east';

    const pane = new THREE.Mesh(new THREE.PlaneGeometry(L, H), makeGlassMaterial(L, H, plane));
    pane.position.set(cx, H / 2, cz);
    pane.rotation.y = ry;
    pane.name = 'glass-' + run.room + '-' + plane;
    pane.userData.plane = plane;
    g.add(pane);
    panes.push(pane);

    /* mullions, sill and head, placed in run-local coordinates */
    const box = (s, y, along, h, thick) => {
      const m = new THREE.Mesh(new THREE.BoxGeometry(along, h, thick), MAT.steel);
      m.position.set(run.a[0] + ux * s + nx * (thick / 2 + 0.001), y,
                     run.a[1] + uz * s + nz * (thick / 2 + 0.001));
      m.rotation.y = ry;
      m.receiveShadow = true;
      g.add(m);
    };
    for (let i = 0; i <= run.bays; i++) box((i * L) / run.bays, H / 2, MW, H, MD);
    box(L / 2, 0.035, L, 0.07, MD + 0.03);
    box(L / 2, H - 0.045, L, 0.09, MD + 0.02);
  }

  roomScene.add(g);
  return { group: g, panes, frontGlass: panes[0] };
}
