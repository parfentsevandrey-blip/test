/* =========================================================
   КУТУЗОВСКИЙ 12 — realistic WebGL stage
   A faithful, procedurally-modelled reconstruction of the
   real club house at Кутузовский проспект, 12 (arch. bureau
   Tsimailo, Lyashenko & Partners): an 11-storey limestone
   palazzo whose signature is full-height clusters of polished
   glass/steel fluted columns banded with brass, set against
   Moscow-City and the Moskva River at twilight.
   Built entirely from Three.js — no external 3D assets.
   ========================================================= */

import * as THREE from "three";
import { RGBELoader } from "three/addons/loaders/RGBELoader.js";
import { EffectComposer } from "three/addons/postprocessing/EffectComposer.js";
import { RenderPass } from "three/addons/postprocessing/RenderPass.js";
import { UnrealBloomPass } from "three/addons/postprocessing/UnrealBloomPass.js";
import { OutputPass } from "three/addons/postprocessing/OutputPass.js";

const ENV_URL = "img/env_dusk.hdr"; // real HDRI for image-based lighting

const canvas = document.getElementById("scene");
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (t) => t * t * (3 - 2 * t);
const clamp01 = (t) => Math.min(1, Math.max(0, t));

// Building dimensions (metres)
const W = 58, D = 22, H = 46, FLOORS = 11;
const FH = H / FLOORS;            // floor height
const FRONT = D / 2;              // +z front face
const NBAYS = 13;
const BAYW = W / NBAYS;

try {
  init();
} catch (err) {
  console.warn("КУТУЗОВСКИЙ 12 scene disabled (gradient fallback):", err);
  window.dispatchEvent(new Event("scene:ready"));
}

function init() {
  const isSmall = window.innerWidth < 760;

  // The scene is rendered small and then redrawn as colored text characters
  // for the page background, so antialiasing/high-res are unnecessary here.
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: false, preserveDrawingBuffer: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(1);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 0.95;
  renderer.shadowMap.enabled = !isSmall;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;

  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x1a2236, 0.0042);

  const camera = new THREE.PerspectiveCamera(40, window.innerWidth / window.innerHeight, 0.5, 2000);
  camera.position.set(40, 40, 95);

  /* ---------- Twilight sky + environment ---------- */
  const sky = makeSky();
  scene.add(sky);

  const pmrem = new THREE.PMREMGenerator(renderer);
  const envScene = new THREE.Scene();
  envScene.add(makeSky());
  scene.environment = pmrem.fromScene(envScene, 0.04).texture; // instant fallback

  // Real HDRI image-based lighting — authentic sky reflections on glass & metal
  new RGBELoader().load(
    ENV_URL,
    (hdr) => {
      hdr.mapping = THREE.EquirectangularReflectionMapping;
      const env = pmrem.fromEquirectangular(hdr).texture;
      scene.environment = env;
      hdr.dispose();
    },
    undefined,
    () => { /* keep procedural env on failure */ }
  );

  /* ---------- Lighting ---------- */
  scene.add(new THREE.HemisphereLight(0x4a5e92, 0x2a2018, 0.6));

  const sun = new THREE.DirectionalLight(0xffc188, 1.5);
  sun.position.set(64, 40, 30);
  if (!isSmall) {
    sun.castShadow = true;
    sun.shadow.mapSize.set(1024, 1024);
    sun.shadow.camera.near = 10;
    sun.shadow.camera.far = 240;
    const s = 58;
    sun.shadow.camera.left = -s; sun.shadow.camera.right = s;
    sun.shadow.camera.top = s; sun.shadow.camera.bottom = -s;
    sun.shadow.bias = -0.00025;
    sun.shadow.normalBias = 0.5;
  }
  scene.add(sun);

  const bounce = new THREE.DirectionalLight(0x6a86d6, 0.7);
  bounce.position.set(-50, 20, -30);
  scene.add(bounce);

  const updaters = [];
  const reflectables = [];

  /* ---------- World ---------- */
  const building = buildResidence(reflectables, updaters);
  scene.add(building);

  const city = makeMoscowCity(reflectables);
  scene.add(city);

  scene.add(makeContextBlocks(reflectables));
  scene.add(makePlaza());
  scene.add(makeReflection(reflectables));
  scene.add(makeWater(updaters));
  scene.add(makeTrees(isSmall ? 14 : 26));

  const particles = makeParticles(isSmall ? 500 : 1100);
  scene.add(particles);
  updaters.push((dt, t) => {
    if (reduceMotion) return;
    particles.material.uniforms.uTime.value = t;
  });

  /* ---------- Post-processing (kept minimal — output becomes text) ---------- */
  const composer = new EffectComposer(renderer);
  composer.addPass(new RenderPass(scene, camera));
  const bloom = new UnrealBloomPass(new THREE.Vector2(2, 2), 0.5, 0.5, 0.75);
  composer.addPass(bloom);
  composer.addPass(new OutputPass());

  /* ---------- Render the 3D scene as a colored-text "video" background ----------
     The WebGL canvas is rendered at low resolution and redrawn every frame as a
     grid of colored characters into #asciiBg — "video from text", driven by the
     live 3D model. */
  const asciiEl = document.getElementById("asciiBg");
  const samp = document.createElement("canvas");
  const sctx = samp.getContext("2d", { willReadFrequently: true });
  const RAMP = " .'\`:,-~+=*coaehx%#WM@";
  let aCols = 150, aRows = 60;

  function setRenderSize() {
    const vw = window.innerWidth, vh = window.innerHeight, aspect = vw / vh;
    const h = isSmall ? 130 : 170, w = Math.round(h * aspect);
    renderer.setSize(w, h, false);             // low-res source; don't touch CSS size
    camera.aspect = aspect; camera.updateProjectionMatrix();
    composer.setSize(w, h); bloom.setSize(w, h);
    // character grid that fills the viewport (monospace cell ~0.6 wide)
    aCols = vw < 760 ? 82 : 156;
    const f = vw / aCols / 0.6;
    aRows = Math.max(20, Math.ceil(vh / f) + 1);
    samp.width = aCols; samp.height = aRows;
    if (asciiEl) { asciiEl.style.fontSize = f.toFixed(2) + "px"; asciiEl.style.lineHeight = f.toFixed(2) + "px"; }
  }

  function renderAscii() {
    if (!asciiEl) return;
    try {
      sctx.drawImage(renderer.domElement, 0, 0, aCols, aRows);
      const data = sctx.getImageData(0, 0, aCols, aRows).data;
      let out = "", run = "", cr = -1, cg = -1, cb = -1;
      for (let y = 0; y < aRows; y++) {
        for (let x = 0; x < aCols; x++) {
          const o = (y * aCols + x) * 4;
          let r = data[o], g = data[o + 1], b = data[o + 2];
          const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
          const ch = RAMP[Math.min(RAMP.length - 1, (Math.pow(lum, 0.85) * RAMP.length) | 0)];
          r = Math.min(255, r * 1.15 + 8) | 0; g = Math.min(255, g * 1.15 + 8) | 0; b = Math.min(255, b * 1.15 + 8) | 0;
          const qr = r & 0xF0, qg = g & 0xF0, qb = b & 0xF0;
          if (qr !== cr || qg !== cg || qb !== cb) {
            if (run) out += '<span style="color:rgb(' + cr + ',' + cg + ',' + cb + ')">' + run + "</span>";
            run = ""; cr = qr; cg = qg; cb = qb;
          }
          run += ch === "<" ? "&lt;" : ch;
        }
        run += "\n";
      }
      if (run) out += '<span style="color:rgb(' + cr + ',' + cg + ',' + cb + ')">' + run + "</span>";
      asciiEl.innerHTML = out;
    } catch (e) { /* drawing buffer not ready */ }
  }

  /* ---------- Cinematic camera (frames the building) ---------- */
  const keys = [
    { s: 0.0,  p: [40, 12, 60],  t: [-2, 18, 0],  f: 40 }, // three-quarter colonnade
    { s: 0.26, p: [-34, 9, 46],  t: [8, 14, 2],   f: 40 }, // track the other way
    { s: 0.5,  p: [10, 30, 30],  t: [0, 33, -4],  f: 36 }, // rise to the crown / penthouse
    { s: 0.74, p: [54, 24, 66],  t: [-6, 18, -8],  f: 42 }, // pull back: house + City + river
    { s: 1.0,  p: [0, 17, 62],   t: [0, 21, 0],   f: 40 }, // frontal elevation
  ];
  const sPos = new THREE.Vector3(), sTar = new THREE.Vector3();
  const k0Pos = new THREE.Vector3(), k0Tar = new THREE.Vector3();
  const introPos = new THREE.Vector3(), introTar = new THREE.Vector3();
  const finalPos = new THREE.Vector3(), finalTar = new THREE.Vector3();
  const introFrom = new THREE.Vector3(46, 44, 104);
  const introFromTar = new THREE.Vector3(-2, 16, -6);

  function sampleCam(prog, outP, outT) {
    let i = 0;
    while (i < keys.length - 1 && prog > keys[i + 1].s) i++;
    const a = keys[i], b = keys[Math.min(i + 1, keys.length - 1)];
    const k = smooth(clamp01((prog - a.s) / ((b.s - a.s) || 1)));
    outP.set(lerp(a.p[0], b.p[0], k), lerp(a.p[1], b.p[1], k), lerp(a.p[2], b.p[2], k));
    outT.set(lerp(a.t[0], b.t[0], k), lerp(a.t[1], b.t[1], k), lerp(a.t[2], b.t[2], k));
    return lerp(a.f, b.f, k);
  }
  const scrollProgress = () => {
    const max = document.documentElement.scrollHeight - window.innerHeight;
    return max > 0 ? clamp01(window.scrollY / max) : 0;
  };

  const ptr = { x: 0, y: 0, tx: 0, ty: 0 };
  window.addEventListener("pointermove", (e) => {
    ptr.tx = e.clientX / window.innerWidth - 0.5;
    ptr.ty = e.clientY / window.innerHeight - 0.5;
  });

  const introDur = 4.5;

  setRenderSize();
  window.addEventListener("resize", setRenderSize);

  const clock = new THREE.Clock();
  let first = true, acc = 0;
  const STEP = 1 / 30;   // the text "video" runs at ~30fps

  function tick() {
    requestAnimationFrame(tick);
    const dt = Math.min(clock.getDelta(), 0.05);
    acc += dt;
    if (acc < STEP) return;
    acc = 0;
    const t = clock.elapsedTime;
    const introT = reduceMotion ? 1 : clamp01(t / introDur);
    const e = smooth(introT);

    const fovS = sampleCam(scrollProgress(), sPos, sTar);
    const fov0 = sampleCam(0, k0Pos, k0Tar);
    introPos.lerpVectors(introFrom, k0Pos, e);
    introTar.lerpVectors(introFromTar, k0Tar, e);
    finalPos.lerpVectors(introPos, sPos, e);
    finalTar.lerpVectors(introTar, sTar, e);
    const fov = lerp(lerp(34, fov0, e), fovS, e);

    ptr.x += (ptr.tx - ptr.x) * 0.04;
    ptr.y += (ptr.ty - ptr.y) * 0.04;
    if (!reduceMotion) {
      finalPos.x += Math.sin(t * 0.19) * 0.5 + ptr.x * 7;
      finalPos.y += Math.sin(t * 0.27) * 0.35 - ptr.y * 3.5;
    }
    camera.position.copy(finalPos);
    camera.lookAt(finalTar);
    camera.fov = fov;
    camera.updateProjectionMatrix();

    for (let i = 0; i < updaters.length; i++) updaters[i](dt, t);
    composer.render();
    renderAscii();   // redraw the frame as colored text

    if (first) { first = false; window.dispatchEvent(new Event("scene:ready")); }
  }
  tick();

  document.addEventListener("visibilitychange", () => { if (!document.hidden) clock.getDelta(); });
}

/* =========================================================
   The building
   ========================================================= */

function buildResidence(reflectables, updaters) {
  const g = new THREE.Group();

  const stone = new THREE.MeshStandardMaterial({ map: limestoneTexture(), color: 0xcabfa6, roughness: 0.82, metalness: 0.0, envMapIntensity: 0.7 });
  const stonePale = new THREE.MeshStandardMaterial({ color: 0xd8cfb8, roughness: 0.7, metalness: 0.0, envMapIntensity: 0.7 });
  const brass = new THREE.MeshStandardMaterial({ color: 0xcf9a44, roughness: 0.3, metalness: 1.0, envMapIntensity: 1.4, emissive: 0x3a2207, emissiveIntensity: 0.55 });
  // brushed glass/steel columns: reflective but not a blown-out mirror
  const colMat = new THREE.MeshStandardMaterial({ color: 0xccd6e0, roughness: 0.17, metalness: 0.95, envMapIntensity: 1.3, emissive: 0xffd9a0, emissiveIntensity: 0.05 });
  const railing = new THREE.MeshStandardMaterial({ color: 0x2a2c30, roughness: 0.4, metalness: 0.9 });
  // realistic glazing: bronze frame + mullions + sky-reflection, with clearcoat
  const glassMat = new THREE.MeshPhysicalMaterial({ map: windowTexture(), roughness: 0.14, metalness: 0.0, clearcoat: 0.6, clearcoatRoughness: 0.18, envMapIntensity: 1.7 });

  // ---- solid mass (cast/receive shadows) ----
  const mass = new THREE.Mesh(new THREE.BoxGeometry(W, H, D), stone);
  mass.position.y = H / 2;
  mass.castShadow = true; mass.receiveShadow = true;
  g.add(mass);
  reflectables.push(mass);

  // top cornice + base plinth
  const cornice = new THREE.Mesh(new THREE.BoxGeometry(W + 1.2, 1.2, D + 1.2), stonePale);
  cornice.position.y = H + 0.2; cornice.castShadow = true;
  g.add(cornice); reflectables.push(cornice);
  const plinth = new THREE.Mesh(new THREE.BoxGeometry(W + 1.4, 1.6, D + 1.4), stonePale);
  plinth.position.y = 0.8;
  g.add(plinth);

  // floor string-courses across the front
  for (let f = 1; f < FLOORS; f++) {
    const band = new THREE.Mesh(new THREE.BoxGeometry(W + 0.2, 0.22, 0.3), stonePale);
    band.position.set(0, f * FH + 1.6, FRONT + 0.18);
    g.add(band);
  }

  // ---- windows (front + right side) as instanced dark glass ----
  const winFront = [];
  for (let b = 0; b < NBAYS; b++) {
    const x = -W / 2 + BAYW * (b + 0.5);
    for (let f = 0; f < FLOORS; f++) {
      const groundFloor = f === 0;
      const y = f * FH + (groundFloor ? FH * 0.55 : FH * 0.5 + 1.6);
      winFront.push({ x, y, w: BAYW * 0.6, h: (groundFloor ? FH * 0.82 : FH * 0.62) });
    }
  }
  addWindows(g, winFront, FRONT + 0.06, glassMat);

  // right side windows
  const NDEPTH = 5, DBAY = D / NDEPTH;
  const winSide = [];
  for (let b = 0; b < NDEPTH; b++) {
    const z = -D / 2 + DBAY * (b + 0.5);
    for (let f = 0; f < FLOORS; f++) {
      winSide.push({ z, y: f * FH + FH * 0.5 + 1.6, w: DBAY * 0.55, h: FH * 0.6 });
    }
  }
  addWindowsSide(g, winSide, W / 2 + 0.06, glassMat);

  // ---- signature columns: clusters of fluted rods banded with brass ----
  // rods
  const RODS = 6, rodR = 0.16, clusterR = 0.72;
  const rodGeo = new THREE.CylinderGeometry(rodR, rodR, H - 1, 8);
  const colXs = [];
  for (let c = 0; c <= NBAYS; c++) colXs.push(-W / 2 + BAYW * c);
  const sideZs = [];
  for (let c = 1; c < NDEPTH; c++) sideZs.push(-D / 2 + DBAY * c);

  const rodCount = (colXs.length + sideZs.length) * RODS;
  const rods = new THREE.InstancedMesh(rodGeo, colMat, rodCount);
  rods.castShadow = true;
  const dummy = new THREE.Object3D();
  let ri = 0;
  const placeCluster = (cx, cz, faceZ) => {
    for (let r = 0; r < RODS; r++) {
      const a = (r / (RODS - 1) - 0.5) * Math.PI * 0.9;
      let ox = Math.sin(a) * clusterR, oz = Math.cos(a) * clusterR * 0.5 + 0.45;
      if (faceZ) dummy.position.set(cx + ox, H / 2, FRONT + oz);
      else dummy.position.set(W / 2 + oz, H / 2, cz + ox);
      dummy.rotation.set(0, 0, 0);
      dummy.scale.set(1, 1, 1);
      dummy.updateMatrix();
      rods.setMatrixAt(ri++, dummy.matrix);
    }
  };
  colXs.forEach((x) => placeCluster(x, 0, true));
  sideZs.forEach((z) => placeCluster(0, z, false));
  rods.instanceMatrix.needsUpdate = true;
  g.add(rods);
  reflectables.push(rods);

  // brass bands wrapping each cluster at every floor line
  const ringGeo = new THREE.CylinderGeometry(clusterR + 0.12, clusterR + 0.12, 0.3, 16, 1, true);
  const rings = new THREE.InstancedMesh(ringGeo, brass, (colXs.length + sideZs.length) * (FLOORS + 1));
  const ringState = { i: 0 };
  colXs.forEach((x) => placeRingsTo(rings, ringState, dummy, x, 0, true));
  sideZs.forEach((z) => placeRingsTo(rings, ringState, dummy, 0, z, false));
  rings.instanceMatrix.needsUpdate = true;
  rings.castShadow = true;
  g.add(rings);
  reflectables.push(rings);

  // ---- warm lobby glow at the base ----
  const lobby = new THREE.Mesh(
    new THREE.PlaneGeometry(W - 2, FH * 0.9),
    new THREE.MeshBasicMaterial({ color: 0xffd9a0, transparent: true, opacity: 0.9 })
  );
  lobby.position.set(0, FH * 0.5, FRONT + 0.02);
  g.add(lobby);

  // ---- penthouse setback + roof ----
  const ph = new THREE.Mesh(new THREE.BoxGeometry(W * 0.74, FH * 1.3, D * 0.7), stone);
  ph.position.y = H + 0.8 + FH * 0.65; ph.castShadow = true;
  g.add(ph); reflectables.push(ph);
  const phGlass = new THREE.Mesh(
    new THREE.PlaneGeometry(W * 0.7, FH * 0.9),
    new THREE.MeshStandardMaterial({ color: 0x10151c, roughness: 0.08, metalness: 0.2, envMapIntensity: 1.4, emissive: 0x40340f, emissiveIntensity: 0.4 })
  );
  phGlass.position.set(0, H + 0.8 + FH * 0.6, D * 0.35 + 0.05);
  g.add(phGlass);
  // roof terrace railing
  for (let i = 0; i <= 16; i++) {
    const post = new THREE.Mesh(new THREE.BoxGeometry(0.06, 1, 0.06), railing);
    post.position.set(-W / 2 + (W / 16) * i, H + 1.2, FRONT - 0.4);
    g.add(post);
  }
  const rail = new THREE.Mesh(new THREE.BoxGeometry(W, 0.06, 0.06), railing);
  rail.position.set(0, H + 1.7, FRONT - 0.4);
  g.add(rail);

  // subtle twilight shimmer on the columns
  updaters.push((_, t) => { colMat.emissiveIntensity = 0.05 + Math.sin(t * 0.7) * 0.03; });

  return g;
}

function placeRingsTo(mesh, state, dummy, cx, cz, faceZ) {
  for (let f = 0; f <= FLOORS; f++) {
    const y = Math.min(H - 0.6, f * FH + 0.9);
    if (faceZ) dummy.position.set(cx, y, FRONT + 0.6);
    else dummy.position.set(W / 2 + 0.6, y, cz);
    dummy.rotation.set(0, 0, 0); dummy.scale.set(1, 1, 1); dummy.updateMatrix();
    mesh.setMatrixAt(state.i++, dummy.matrix);
  }
}

function addWindows(group, list, z, mat) {
  const geo = new THREE.PlaneGeometry(1, 1);
  const d = new THREE.Object3D();

  const mesh = new THREE.InstancedMesh(geo, mat, list.length);
  list.forEach((w, i) => {
    d.position.set(w.x, w.y, z); d.rotation.set(0, 0, 0); d.scale.set(w.w, w.h, 1); d.updateMatrix();
    mesh.setMatrixAt(i, d.matrix);
  });
  mesh.instanceMatrix.needsUpdate = true;
  group.add(mesh);

  // a fraction of windows glow warm
  const litList = list.filter(() => Math.random() < 0.36);
  const litMat = new THREE.MeshBasicMaterial({ color: 0xffcc92, transparent: true, opacity: 0.85, blending: THREE.AdditiveBlending, depthWrite: false });
  const litMesh = new THREE.InstancedMesh(geo, litMat, litList.length);
  litList.forEach((w, i) => {
    d.position.set(w.x, w.y, z + 0.03); d.rotation.set(0, 0, 0); d.scale.set(w.w * 0.92, w.h * 0.9, 1); d.updateMatrix();
    litMesh.setMatrixAt(i, d.matrix);
  });
  litMesh.instanceMatrix.needsUpdate = true;
  group.add(litMesh);
}

function addWindowsSide(group, list, x, mat) {
  const geo = new THREE.PlaneGeometry(1, 1);
  const mesh = new THREE.InstancedMesh(geo, mat, list.length);
  const d = new THREE.Object3D();
  list.forEach((w, i) => {
    d.position.set(x, w.y, w.z); d.rotation.y = Math.PI / 2; d.scale.set(w.w, w.h, 1); d.updateMatrix();
    mesh.setMatrixAt(i, d.matrix); d.rotation.y = 0;
  });
  mesh.instanceMatrix.needsUpdate = true;
  group.add(mesh);
}

/* =========================================================
   Context: Moscow-City, neighbours, plaza, water, trees
   ========================================================= */

function makeMoscowCity(reflectables) {
  const g = new THREE.Group();
  const glass = new THREE.MeshStandardMaterial({ color: 0x2b3850, roughness: 0.12, metalness: 0.7, envMapIntensity: 1.6, emissive: 0x101626, emissiveIntensity: 0.5 });
  const towers = [
    { x: -120, z: -150, w: 26, d: 26, h: 240 },
    { x: -150, z: -170, w: 22, d: 22, h: 300 },
    { x: -95, z: -175, w: 20, d: 20, h: 200 },
    { x: -176, z: -150, w: 18, d: 18, h: 170 },
    { x: -70, z: -185, w: 24, d: 16, h: 150 },
    { x: -200, z: -165, w: 20, d: 20, h: 210 },
  ];
  towers.forEach((t) => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(t.w, t.h, t.d), glass);
    m.position.set(t.x, t.h / 2, t.z);
    m.rotation.y = Math.random() * 0.6;
    g.add(m);
    reflectables.push(m);
  });
  return g;
}

function makeContextBlocks(reflectables) {
  // low Stalin-era neighbours around the plot
  const g = new THREE.Group();
  const mat = new THREE.MeshStandardMaterial({ color: 0x6d6354, roughness: 0.9, metalness: 0, envMapIntensity: 0.5 });
  const blocks = [
    { x: -70, z: 10, w: 40, d: 22, h: 26 },
    { x: 78, z: -6, w: 44, d: 22, h: 28 },
    { x: 60, z: 40, w: 30, d: 20, h: 22 },
    { x: -64, z: 46, w: 34, d: 20, h: 24 },
  ];
  blocks.forEach((b) => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(b.w, b.h, b.d), mat);
    m.position.set(b.x, b.h / 2, b.z);
    m.castShadow = true; m.receiveShadow = true;
    g.add(m); reflectables.push(m);
  });
  return g;
}

function makePlaza() {
  const tex = graniteTexture();
  const mat = new THREE.MeshStandardMaterial({ map: tex, color: 0x8d8a86, roughness: 0.55, metalness: 0.1, envMapIntensity: 0.6 });
  const plane = new THREE.Mesh(new THREE.PlaneGeometry(260, 120), mat);
  plane.rotation.x = -Math.PI / 2;
  plane.position.set(0, 0.02, FRONT + 40);
  plane.receiveShadow = true;
  return plane;
}

function makeWater(updaters) {
  const mat = new THREE.MeshStandardMaterial({ color: 0x0c1622, roughness: 0.08, metalness: 0.6, envMapIntensity: 1.1, transparent: true, opacity: 0.92 });
  const plane = new THREE.Mesh(new THREE.PlaneGeometry(600, 300, 1, 1), mat);
  plane.rotation.x = -Math.PI / 2;
  plane.position.set(-40, -0.05, -120);
  return plane;
}

function makeReflection(reflectables) {
  const group = new THREE.Group();
  reflectables.forEach((src) => {
    let clone;
    if (src.isInstancedMesh) {
      clone = new THREE.InstancedMesh(src.geometry, src.material, src.count);
      clone.instanceMatrix.copy(src.instanceMatrix);
      clone.instanceMatrix.needsUpdate = true;
    } else {
      clone = new THREE.Mesh(src.geometry, src.material);
      clone.position.copy(src.position);
      clone.rotation.copy(src.rotation);
      clone.scale.copy(src.scale);
    }
    group.add(clone);
  });
  group.scale.y = -1;
  group.position.y = -0.1;
  return group;
}

function makeTrees(n) {
  const g = new THREE.Group();
  const trunkMat = new THREE.MeshStandardMaterial({ color: 0x3a2c20, roughness: 0.9 });
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x35502f, roughness: 0.9 });
  for (let i = 0; i < n; i++) {
    const tree = new THREE.Group();
    const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.26, 3, 6), trunkMat);
    trunk.position.y = 1.5;
    const crown = new THREE.Mesh(new THREE.IcosahedronGeometry(1.7 + Math.random(), 1), leafMat);
    crown.position.y = 4; crown.castShadow = true;
    tree.add(trunk); tree.add(crown);
    const side = Math.random() > 0.5 ? 1 : -1;
    tree.position.set((Math.random() - 0.5) * 120, 0, FRONT + 16 + Math.random() * 40);
    tree.scale.setScalar(0.8 + Math.random() * 0.7);
    g.add(tree);
  }
  return g;
}

/* =========================================================
   Procedural textures
   ========================================================= */

function limestoneTexture() {
  const c = document.createElement("canvas"); c.width = 256; c.height = 256;
  const ctx = c.getContext("2d");
  ctx.fillStyle = "#cabfa6"; ctx.fillRect(0, 0, 256, 256);
  for (let i = 0; i < 4000; i++) {
    const v = 200 + Math.random() * 40;
    ctx.fillStyle = `rgba(${v},${v - 8},${v - 26},0.05)`;
    ctx.fillRect(Math.random() * 256, Math.random() * 256, 2, 2);
  }
  // faint vertical fluting + horizontal courses
  ctx.strokeStyle = "rgba(120,110,92,0.18)"; ctx.lineWidth = 1;
  for (let x = 8; x < 256; x += 16) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, 256); ctx.stroke(); }
  ctx.strokeStyle = "rgba(120,110,92,0.12)";
  for (let y = 0; y < 256; y += 64) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(256, y); ctx.stroke(); }
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(W / 6, H / 6);
  return tex;
}

function windowTexture() {
  const c = document.createElement("canvas"); c.width = 96; c.height = 128;
  const ctx = c.getContext("2d");
  // dark glass with a vertical sky-reflection gradient
  const g = ctx.createLinearGradient(0, 0, 0, 128);
  g.addColorStop(0, "#3a5676"); g.addColorStop(0.5, "#1a2a3c"); g.addColorStop(1, "#0a1018");
  ctx.fillStyle = g; ctx.fillRect(0, 0, 96, 128);
  // soft diagonal sky streak
  ctx.globalAlpha = 0.16; ctx.fillStyle = "#cfe2f7";
  ctx.beginPath(); ctx.moveTo(0, 28); ctx.lineTo(96, 66); ctx.lineTo(96, 84); ctx.lineTo(0, 46); ctx.closePath(); ctx.fill();
  ctx.globalAlpha = 1;
  // bronze frame, vertical mullion, transom
  ctx.strokeStyle = "#101418"; ctx.lineWidth = 10; ctx.strokeRect(0, 0, 96, 128);
  ctx.lineWidth = 4;
  ctx.beginPath(); ctx.moveTo(48, 0); ctx.lineTo(48, 128); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(0, 50); ctx.lineTo(96, 50); ctx.stroke();
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function graniteTexture() {
  const c = document.createElement("canvas"); c.width = 256; c.height = 256;
  const ctx = c.getContext("2d");
  ctx.fillStyle = "#83807c"; ctx.fillRect(0, 0, 256, 256);
  for (let i = 0; i < 6000; i++) {
    const v = 110 + Math.random() * 60;
    ctx.fillStyle = `rgba(${v},${v},${v},0.06)`;
    ctx.fillRect(Math.random() * 256, Math.random() * 256, 2, 2);
  }
  // chevron seams
  ctx.strokeStyle = "rgba(40,40,40,0.25)"; ctx.lineWidth = 1;
  for (let i = -256; i < 256; i += 22) {
    ctx.beginPath(); ctx.moveTo(i, 0); ctx.lineTo(i + 128, 256); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(i + 128, 0); ctx.lineTo(i, 256); ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(20, 10);
  return tex;
}

/* =========================================================
   Sky, particles
   ========================================================= */

function makeSky() {
  const geo = new THREE.SphereGeometry(900, 32, 16);
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false,
    uniforms: {
      uZenith: { value: new THREE.Color(0x0a1430) },
      uMid:    { value: new THREE.Color(0x33406e) },
      uHorizon:{ value: new THREE.Color(0xe08a4a) },
      uGlow:   { value: new THREE.Color(0xffb56a) },
    },
    vertexShader: `varying vec3 vP; void main(){ vP = position; gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); }`,
    fragmentShader: `
      varying vec3 vP; uniform vec3 uZenith,uMid,uHorizon,uGlow;
      void main(){
        vec3 d = normalize(vP);
        float h = d.y;
        vec3 col = mix(uMid, uZenith, smoothstep(0.15, 0.75, h));
        col = mix(uHorizon, col, smoothstep(-0.05, 0.22, h));
        // warm sun glow low on the horizon
        float sun = max(0.0, dot(d, normalize(vec3(0.8, 0.06, 0.5))));
        col += uGlow * pow(sun, 22.0) * 0.5;
        col = mix(col, uHorizon*0.6, smoothstep(0.0,-0.4,h));
        gl_FragColor = vec4(col,1.0);
      }`,
  });
  return new THREE.Mesh(geo, mat);
}

function makeParticles(count) {
  const positions = new Float32Array(count * 3);
  const seeds = new Float32Array(count);
  for (let i = 0; i < count; i++) {
    positions[i * 3] = (Math.random() - 0.5) * 160;
    positions[i * 3 + 1] = Math.random() * 70;
    positions[i * 3 + 2] = (Math.random() - 0.5) * 160 + 20;
    seeds[i] = Math.random() * Math.PI * 2;
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  geo.setAttribute("aSeed", new THREE.BufferAttribute(seeds, 1));
  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: { uTime: { value: 0 } },
    vertexShader: `
      uniform float uTime; attribute float aSeed; varying float vA;
      void main(){
        vec3 p = position;
        p.y += sin(uTime*0.25 + aSeed)*1.4;
        p.x += cos(uTime*0.18 + aSeed)*1.1;
        vec4 mv = modelViewMatrix * vec4(p,1.0);
        gl_Position = projectionMatrix * mv;
        gl_PointSize = (26.0 / -mv.z) * (0.6 + 0.4*sin(aSeed));
        vA = 0.25 + 0.5*abs(sin(uTime*0.5 + aSeed));
      }`,
    fragmentShader: `
      varying float vA;
      void main(){
        float d = smoothstep(0.5, 0.0, length(gl_PointCoord - 0.5));
        gl_FragColor = vec4(0.95, 0.82, 0.6, d*vA);
      }`,
  });
  return new THREE.Points(geo, mat);
}
