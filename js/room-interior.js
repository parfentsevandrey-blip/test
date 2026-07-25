/* =========================================================================
   Part 3 / 5 — the fireplace.

   The shell and the glazing moved to js/room-shell.js when the apartment
   stopped being one box; what is left here is the one piece of the building
   that is really a piece of furniture. The firebox itself is an opening in
   the west wall declared in room-plan.js, so this only has to dress it.
   ========================================================================= */
import * as THREE from 'three';
import {
  GLSL_NOISE, U, ROOM, FIREBOX, rnd, roomScene, MAX_ANISO, boxUv,
} from './room.js';
import { MAT } from './room-mat.js';
import { applyMaps } from './tex/index.js';
import { applyDetail } from './tex/detail.js';

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
