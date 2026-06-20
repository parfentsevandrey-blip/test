/* =========================================================
   ZENITH — Cinematic WebGL hero
   A full-screen, post-processed Moscow-City night scene:
   bloom, a scripted intro fly-in, a scroll-driven camera
   journey, planar reflections, searchlights, beacons,
   traffic light-trails, drifting clouds, stars and a moon.
   Built entirely from Three.js primitives — no 3D assets.
   ========================================================= */

import * as THREE from "three";
import { RoomEnvironment } from "three/addons/environments/RoomEnvironment.js";
import { EffectComposer } from "three/addons/postprocessing/EffectComposer.js";
import { RenderPass } from "three/addons/postprocessing/RenderPass.js";
import { UnrealBloomPass } from "three/addons/postprocessing/UnrealBloomPass.js";
import { OutputPass } from "three/addons/postprocessing/OutputPass.js";

const canvas = document.getElementById("scene");
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (t) => t * t * (3 - 2 * t);
const clamp01 = (t) => Math.min(1, Math.max(0, t));

try {
  init();
} catch (err) {
  console.warn("ZENITH cinematic scene disabled (gradient fallback):", err);
  window.dispatchEvent(new Event("scene:ready"));
}

function init() {
  const isSmall = window.innerWidth < 760;

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, isSmall ? 2 : 1.6));
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.1;

  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x070a14, 0.011);

  const camera = new THREE.PerspectiveCamera(42, window.innerWidth / window.innerHeight, 0.1, 1200);
  camera.position.set(0, 80, 120);

  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.03).texture;

  /* ---------- World ---------- */
  scene.add(makeSky());
  scene.add(makeStars(isSmall ? 800 : 1600));
  const moon = makeMoon();
  scene.add(moon);

  // lights
  scene.add(new THREE.HemisphereLight(0x33406e, 0x05060a, 0.6));
  const key = new THREE.DirectionalLight(0xbcd0ff, 0.9);
  key.position.set(-40, 60, 30);
  scene.add(key);
  const warm = new THREE.PointLight(0xffcaa0, 120, 120, 2);
  warm.position.set(0, 30, 8);
  scene.add(warm);

  const updaters = [];
  const reflectables = [];

  const city = new THREE.Group();
  scene.add(city);

  const tower = makeTower(reflectables, updaters);
  city.add(tower);
  city.add(makeSkyline(isSmall ? 30 : 48, reflectables, updaters));

  makeSearchlights(city, updaters, isSmall ? 2 : 3);
  makeTraffic(city, updaters);
  city.add(makeClouds(updaters, isSmall ? 5 : 9));

  // planar reflection: a mirrored, dimmed copy of the emissive city
  city.add(makeReflection(reflectables));
  city.add(makeFloor());

  const particles = makeParticles(isSmall ? 700 : 1500);
  scene.add(particles);
  updaters.push((dt) => {
    if (reduceMotion) return;
    particles.rotation.y += dt * 0.01;
    particles.material.uniforms.uTime.value += dt;
  });

  /* ---------- Post-processing ---------- */
  const composer = new EffectComposer(renderer);
  composer.addPass(new RenderPass(scene, camera));
  const bloom = new UnrealBloomPass(
    new THREE.Vector2(window.innerWidth, window.innerHeight),
    0.7,  // strength
    0.5,  // radius
    0.2   // threshold — only bright emissive (windows, lights, moon) bloom
  );
  composer.addPass(bloom);
  composer.addPass(new OutputPass());

  /* ---------- Cinematic camera ---------- */
  // Scroll keyframes: { stop, position, target, fov }
  // Every shot keeps the glowing city framed (never empty sky)
  const keys = [
    { s: 0.0,  p: [24, 9, 38],   t: [0, 15, 0],  f: 42 },
    { s: 0.26, p: [-30, 16, 28], t: [0, 22, 0],  f: 46 },
    { s: 0.5,  p: [22, 31, 31],  t: [0, 24, 0],  f: 43 },
    { s: 0.74, p: [-27, 22, -30],t: [0, 22, 0],  f: 50 },
    { s: 1.0,  p: [0, 46, 64],   t: [0, 18, 0],  f: 40 },
  ];
  const sPos = new THREE.Vector3(), sTar = new THREE.Vector3();
  const k0Pos = new THREE.Vector3(), k0Tar = new THREE.Vector3();
  const introPos = new THREE.Vector3(), introTar = new THREE.Vector3();
  const finalPos = new THREE.Vector3(), finalTar = new THREE.Vector3();
  const introFrom = new THREE.Vector3(12, 64, 132);
  const introFromTar = new THREE.Vector3(0, 18, 0);

  function sampleCam(prog, outP, outT) {
    let i = 0;
    while (i < keys.length - 1 && prog > keys[i + 1].s) i++;
    const a = keys[i], b = keys[Math.min(i + 1, keys.length - 1)];
    const span = (b.s - a.s) || 1;
    const k = smooth(clamp01((prog - a.s) / span));
    outP.set(lerp(a.p[0], b.p[0], k), lerp(a.p[1], b.p[1], k), lerp(a.p[2], b.p[2], k));
    outT.set(lerp(a.t[0], b.t[0], k), lerp(a.t[1], b.t[1], k), lerp(a.t[2], b.t[2], k));
    return lerp(a.f, b.f, k);
  }
  function scrollProgress() {
    const max = document.documentElement.scrollHeight - window.innerHeight;
    return max > 0 ? clamp01(window.scrollY / max) : 0;
  }

  // pointer parallax
  const ptr = { x: 0, y: 0, tx: 0, ty: 0 };
  window.addEventListener("pointermove", (e) => {
    ptr.tx = e.clientX / window.innerWidth - 0.5;
    ptr.ty = e.clientY / window.innerHeight - 0.5;
  });

  const introDur = 4.5; // seconds — wall-clock based, independent of frame rate

  /* ---------- Resize ---------- */
  window.addEventListener("resize", () => {
    const w = window.innerWidth, h = window.innerHeight;
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    renderer.setSize(w, h);
    composer.setSize(w, h);
    bloom.setSize(w, h);
  });

  /* ---------- Loop ---------- */
  const clock = new THREE.Clock();
  let first = true;

  function tick() {
    const dt = Math.min(clock.getDelta(), 0.05);
    const t = clock.elapsedTime;

    const introT = reduceMotion ? 1 : clamp01(t / introDur);
    const e = smooth(introT);

    const fovS = sampleCam(scrollProgress(), sPos, sTar);
    const fov0 = sampleCam(0, k0Pos, k0Tar);
    introPos.lerpVectors(introFrom, k0Pos, e);
    introTar.lerpVectors(introFromTar, k0Tar, e);
    finalPos.lerpVectors(introPos, sPos, e);
    finalTar.lerpVectors(introTar, sTar, e);
    const fov = lerp(lerp(introFrom.fov || 28, fov0, e), fovS, e);

    ptr.x += (ptr.tx - ptr.x) * 0.04;
    ptr.y += (ptr.ty - ptr.y) * 0.04;

    if (!reduceMotion) {
      finalPos.x += Math.sin(t * 0.23) * 0.7 + ptr.x * 6;
      finalPos.y += Math.sin(t * 0.31) * 0.5 - ptr.y * 3;
    }
    camera.position.copy(finalPos);
    camera.lookAt(finalTar);
    if (!reduceMotion) camera.rotateZ(Math.sin(t * 0.18) * 0.006);
    camera.fov = fov;
    camera.updateProjectionMatrix();

    warm.intensity = 100 + Math.sin(t * 1.4) * 25;
    for (let i = 0; i < updaters.length; i++) updaters[i](dt, t);

    composer.render();

    if (first) { first = false; window.dispatchEvent(new Event("scene:ready")); }
    requestAnimationFrame(tick);
  }
  tick();

  document.addEventListener("visibilitychange", () => { if (!document.hidden) clock.getDelta(); });
}

/* =========================================================
   Builders
   ========================================================= */

function makeFacadeTexture({ cols = 16, rows = 44, lit = 0.4 } = {}) {
  const cell = 16;
  const c = document.createElement("canvas");
  c.width = cols * cell; c.height = rows * cell;
  const ctx = c.getContext("2d");
  ctx.fillStyle = "#05070b";
  ctx.fillRect(0, 0, c.width, c.height);
  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) {
      const isLit = Math.random() < lit;
      const px = x * cell + 3, py = y * cell + 3, w = cell - 6, h = cell - 6;
      if (isLit) {
        const g = ctx.createLinearGradient(px, py, px, py + h);
        if (Math.random() > 0.22) { g.addColorStop(0, "#ffe6b0"); g.addColorStop(1, "#d59a4e"); }
        else { g.addColorStop(0, "#cfe2ff"); g.addColorStop(1, "#7f97c4"); }
        ctx.fillStyle = g;
      } else ctx.fillStyle = "#0a0f18";
      ctx.fillRect(px, py, w, h);
    }
  }
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.anisotropy = 4;
  return tex;
}

function makeTower(reflectables, updaters) {
  const group = new THREE.Group();
  const COLS = 18, ROWS = 50;
  const facade = makeFacadeTexture({ cols: COLS, rows: ROWS, lit: 0.42 });

  const glass = (w, h) => {
    const map = facade.clone(); map.needsUpdate = true;
    map.repeat.set((w * 1.7) / COLS, (h * 2.2) / ROWS);
    return new THREE.MeshPhysicalMaterial({
      color: 0x0a0e15, metalness: 0.2, roughness: 0.32, envMapIntensity: 0.5,
      emissive: 0xffffff, emissiveMap: map, emissiveIntensity: 1.8,
      clearcoat: 0.4, clearcoatRoughness: 0.3,
    });
  };

  const tiers = [
    { w: 9, d: 9, h: 22, y: 11 },
    { w: 7.4, d: 7.4, h: 12, y: 28 },
    { w: 5.8, d: 5.8, h: 9, y: 38.5 },
  ];
  tiers.forEach((tt) => {
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(tt.w, tt.h, tt.d), glass(tt.w, tt.h));
    mesh.position.y = tt.y;
    group.add(mesh);
    reflectables.push(mesh);
    const cap = new THREE.Mesh(
      new THREE.BoxGeometry(tt.w + 0.3, 0.4, tt.d + 0.3),
      new THREE.MeshStandardMaterial({ color: 0xc9a35e, metalness: 1, roughness: 0.35, envMapIntensity: 1.4 })
    );
    cap.position.y = tt.y + tt.h / 2;
    group.add(cap);
    reflectables.push(cap);
  });

  // spire + glowing crown
  const spire = new THREE.Mesh(
    new THREE.CylinderGeometry(0.08, 0.32, 8, 12),
    new THREE.MeshStandardMaterial({ color: 0xc9a35e, metalness: 1, roughness: 0.3 })
  );
  spire.position.y = 47; group.add(spire); reflectables.push(spire);

  const orb = new THREE.Mesh(
    new THREE.SphereGeometry(0.6, 24, 24),
    new THREE.MeshStandardMaterial({ color: 0xffe9c2, emissive: 0xe7c98a, emissiveIntensity: 4 })
  );
  orb.position.y = 51.4; group.add(orb); reflectables.push(orb);
  const orbLight = new THREE.PointLight(0xe7c98a, 40, 60, 2);
  orbLight.position.y = 51.4; group.add(orbLight);

  // aircraft beacon on the crown (with a real light)
  const beacon = makeBeacon(0, 52.4, 0, updaters, true);
  group.add(beacon);
  reflectables.push(beacon.children[0]);

  return group;
}

function makeBeacon(x, y, z, updaters, withLight) {
  const g = new THREE.Group();
  const bulb = new THREE.Mesh(
    new THREE.SphereGeometry(0.28, 12, 12),
    new THREE.MeshStandardMaterial({ color: 0xff5544, emissive: 0xff2218, emissiveIntensity: 5 })
  );
  bulb.position.set(x, y, z);
  g.add(bulb);
  const light = withLight ? new THREE.PointLight(0xff3322, 0, 30, 2) : null;
  if (light) { light.position.set(x, y, z); g.add(light); }
  let ph = Math.random() * Math.PI * 2;
  updaters.push((dt) => {
    ph += dt * 2.2;
    const b = Math.max(0, Math.sin(ph));
    bulb.material.emissiveIntensity = 1.5 + b * 7;
    if (light) light.intensity = b * 8;
  });
  return g;
}

function makeSkyline(count, reflectables, updaters) {
  const group = new THREE.Group();
  const facade = makeFacadeTexture({ cols: 12, rows: 34, lit: 0.34 });
  const geo = new THREE.BoxGeometry(1, 1, 1);
  const map = facade.clone(); map.needsUpdate = true;
  const mat = new THREE.MeshStandardMaterial({
    color: 0x070b12, metalness: 0.3, roughness: 0.42,
    emissive: 0xffffff, emissiveMap: map, emissiveIntensity: 1.0, envMapIntensity: 0.8,
  });
  const mesh = new THREE.InstancedMesh(geo, mat, count);
  const dummy = new THREE.Object3D();
  const tall = [];
  for (let i = 0; i < count; i++) {
    const ang = (i / count) * Math.PI * 2 + Math.random() * 0.25;
    const dist = 26 + Math.random() * 46;
    const w = 3 + Math.random() * 5, h = 10 + Math.random() * 44, d = 3 + Math.random() * 5;
    const x = Math.cos(ang) * dist, z = Math.sin(ang) * dist;
    dummy.position.set(x, h / 2, z);
    dummy.scale.set(w, h, d);
    dummy.rotation.y = Math.random() * Math.PI;
    dummy.updateMatrix();
    mesh.setMatrixAt(i, dummy.matrix);
    if (h > 40) tall.push([x, h, z]);
  }
  mesh.instanceMatrix.needsUpdate = true;
  group.add(mesh);
  reflectables.push(mesh);

  // gentle window flicker
  updaters.push((_, t) => { mat.emissiveIntensity = 1.0 + Math.sin(t * 0.8) * 0.06; });

  // beacons on the tallest neighbours (emissive only — bloom makes them glow)
  tall.slice(0, 5).forEach(([x, h, z]) => group.add(makeBeacon(x, h + 1, z, updaters, false)));
  return group;
}

function makeSearchlights(parent, updaters, n) {
  for (let i = 0; i < n; i++) {
    const ang = (i / n) * Math.PI * 2;
    const r = 30 + i * 6;
    const base = new THREE.Vector3(Math.cos(ang) * r, 1, Math.sin(ang) * r);
    const color = 0xbfd4ff;
    const spot = new THREE.SpotLight(color, 600, 200, Math.PI / 9, 0.4, 1.4);
    spot.position.copy(base);
    const target = new THREE.Object3D();
    target.position.set(base.x * 0.4, 60, base.z * 0.4);
    parent.add(target);
    spot.target = target;
    parent.add(spot);

    // visible additive beam cone
    const cone = new THREE.Mesh(
      new THREE.ConeGeometry(7, 70, 24, 1, true),
      new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.05, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, depthWrite: false })
    );
    cone.position.set(base.x, 35, base.z);
    parent.add(cone);

    let ph = Math.random() * Math.PI * 2;
    const speed = 0.12 + Math.random() * 0.1;
    updaters.push((dt, t) => {
      ph += dt * speed;
      const tx = Math.cos(ph) * 22, tz = Math.sin(ph * 0.7) * 22;
      target.position.set(tx, 64, tz);
      cone.lookAt(tx, 64, tz);
      cone.rotateX(Math.PI / 2);
    });
  }
}

function makeTraffic(parent, updaters) {
  // long-exposure light-trails: additive planes with scrolling dash textures
  const tex = (() => {
    const c = document.createElement("canvas"); c.width = 256; c.height = 16;
    const ctx = c.getContext("2d");
    ctx.fillStyle = "#000"; ctx.fillRect(0, 0, 256, 16);
    for (let x = 0; x < 256; x += 18) {
      const g = ctx.createLinearGradient(x, 0, x + 12, 0);
      g.addColorStop(0, "rgba(255,210,140,0)");
      g.addColorStop(0.5, "rgba(255,225,170,0.9)");
      g.addColorStop(1, "rgba(255,210,140,0)");
      ctx.fillStyle = g; ctx.fillRect(x, 4, 12, 3);
      const g2 = ctx.createLinearGradient(x + 6, 0, x + 18, 0);
      g2.addColorStop(0, "rgba(255,80,70,0)");
      g2.addColorStop(0.5, "rgba(255,90,80,0.8)");
      g2.addColorStop(1, "rgba(255,80,70,0)");
      ctx.fillStyle = g2; ctx.fillRect(x + 6, 9, 12, 3);
    }
    const t = new THREE.CanvasTexture(c);
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.colorSpace = THREE.SRGBColorSpace;
    return t;
  })();

  const roads = [
    { x: 0, z: 14, len: 120, rot: 0 },
    { x: 16, z: 0, len: 120, rot: Math.PI / 2 },
    { x: -18, z: -6, len: 110, rot: Math.PI / 3 },
    { x: 6, z: -20, len: 130, rot: -Math.PI / 5 },
  ];
  roads.forEach((rd, i) => {
    const m = tex.clone(); m.needsUpdate = true; m.repeat.set(rd.len / 10, 1);
    const mat = new THREE.MeshBasicMaterial({ map: m, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false });
    const plane = new THREE.Mesh(new THREE.PlaneGeometry(rd.len, 2.4), mat);
    plane.rotation.x = -Math.PI / 2;
    plane.rotation.z = rd.rot;
    plane.position.set(rd.x, 0.25, rd.z);
    parent.add(plane);
    const dir = i % 2 ? -1 : 1;
    updaters.push((dt) => { m.offset.x += dt * 0.6 * dir; });
  });
}

function makeClouds(updaters, n) {
  const group = new THREE.Group();
  const c = document.createElement("canvas"); c.width = c.height = 128;
  const ctx = c.getContext("2d");
  const g = ctx.createRadialGradient(64, 64, 4, 64, 64, 64);
  g.addColorStop(0, "rgba(120,140,180,0.5)");
  g.addColorStop(1, "rgba(120,140,180,0)");
  ctx.fillStyle = g; ctx.fillRect(0, 0, 128, 128);
  const tex = new THREE.CanvasTexture(c);
  for (let i = 0; i < n; i++) {
    const mat = new THREE.MeshBasicMaterial({ map: tex, transparent: true, opacity: 0.16, depthWrite: false, blending: THREE.AdditiveBlending });
    const s = 40 + Math.random() * 60;
    const m = new THREE.Mesh(new THREE.PlaneGeometry(s, s * 0.6), mat);
    m.position.set((Math.random() - 0.5) * 160, 60 + Math.random() * 50, (Math.random() - 0.5) * 160);
    m.rotation.x = -Math.PI / 2.3;
    group.add(m);
    const drift = 0.4 + Math.random() * 0.5;
    updaters.push((dt) => {
      m.position.x += dt * drift;
      if (m.position.x > 110) m.position.x = -110;
    });
  }
  return group;
}

function makeReflection(reflectables) {
  const group = new THREE.Group();
  reflectables.forEach((src) => {
    let clone;
    if (src.isInstancedMesh) {
      clone = new THREE.InstancedMesh(src.geometry, src.material.clone(), src.count);
      clone.instanceMatrix.copy(src.instanceMatrix);
      clone.instanceMatrix.needsUpdate = true;
    } else {
      clone = new THREE.Mesh(src.geometry, src.material.clone());
      clone.position.copy(src.position);
      clone.rotation.copy(src.rotation);
      clone.scale.copy(src.scale);
    }
    if (clone.material) {
      if ("emissiveIntensity" in clone.material) clone.material.emissiveIntensity *= 0.5;
      clone.material.transparent = true;
      clone.material.opacity = 0.5;
    }
    group.add(clone);
  });
  group.scale.y = -1;
  group.position.y = -0.1;
  return group;
}

function makeFloor() {
  const mat = new THREE.MeshPhysicalMaterial({
    color: 0x05080e, metalness: 0.4, roughness: 0.18,
    transparent: true, opacity: 0.62, envMapIntensity: 0.8, depthWrite: false,
  });
  const plane = new THREE.Mesh(new THREE.PlaneGeometry(700, 700), mat);
  plane.rotation.x = -Math.PI / 2;
  return plane;
}

function makeStars(count) {
  const pos = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    const r = 300 + Math.random() * 200;
    const th = Math.random() * Math.PI * 2;
    const ph = Math.acos(Math.random() * 0.7 + 0.1);
    pos[i * 3] = r * Math.sin(ph) * Math.cos(th);
    pos[i * 3 + 1] = r * Math.cos(ph) + 60;
    pos[i * 3 + 2] = r * Math.sin(ph) * Math.sin(th);
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute("position", new THREE.BufferAttribute(pos, 3));
  const mat = new THREE.PointsMaterial({ color: 0xcfd8ff, size: 1.1, sizeAttenuation: false, transparent: true, opacity: 0.9 });
  return new THREE.Points(geo, mat);
}

function makeMoon() {
  const g = new THREE.Group();
  const moon = new THREE.Mesh(
    new THREE.SphereGeometry(7, 32, 32),
    new THREE.MeshStandardMaterial({ color: 0xfff4e0, emissive: 0xead9b6, emissiveIntensity: 2.2 })
  );
  const halo = new THREE.Mesh(
    new THREE.PlaneGeometry(46, 46),
    new THREE.MeshBasicMaterial({ map: radialSprite(), transparent: true, opacity: 0.5, blending: THREE.AdditiveBlending, depthWrite: false })
  );
  g.add(moon); g.add(halo);
  g.position.set(-150, 120, -180);
  halo.lookAt(0, 0, 0);
  return g;
}

function radialSprite() {
  const c = document.createElement("canvas"); c.width = c.height = 128;
  const ctx = c.getContext("2d");
  const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64);
  g.addColorStop(0, "rgba(255,244,224,0.9)");
  g.addColorStop(0.3, "rgba(255,240,210,0.35)");
  g.addColorStop(1, "rgba(255,240,210,0)");
  ctx.fillStyle = g; ctx.fillRect(0, 0, 128, 128);
  return new THREE.CanvasTexture(c);
}

function makeParticles(count) {
  const positions = new Float32Array(count * 3);
  const seeds = new Float32Array(count);
  for (let i = 0; i < count; i++) {
    positions[i * 3] = (Math.random() - 0.5) * 140;
    positions[i * 3 + 1] = Math.random() * 80;
    positions[i * 3 + 2] = (Math.random() - 0.5) * 140;
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
        p.y += sin(uTime*0.3 + aSeed)*1.6;
        p.x += cos(uTime*0.2 + aSeed)*1.3;
        vec4 mv = modelViewMatrix * vec4(p,1.0);
        gl_Position = projectionMatrix * mv;
        gl_PointSize = (30.0 / -mv.z) * (0.6 + 0.4*sin(aSeed));
        vA = 0.35 + 0.65*abs(sin(uTime*0.6 + aSeed));
      }`,
    fragmentShader: `
      varying float vA;
      void main(){
        float d = smoothstep(0.5, 0.0, length(gl_PointCoord - 0.5));
        gl_FragColor = vec4(0.85, 0.68, 0.4, d*vA);
      }`,
  });
  return new THREE.Points(geo, mat);
}

function makeSky() {
  const geo = new THREE.SphereGeometry(560, 32, 16);
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false,
    uniforms: {
      uTop: { value: new THREE.Color(0x04060f) },
      uMid: { value: new THREE.Color(0x0a1325) },
      uBot: { value: new THREE.Color(0x1c1612) },
    },
    vertexShader: `varying vec3 vP; void main(){ vP = position; gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); }`,
    fragmentShader: `
      varying vec3 vP; uniform vec3 uTop,uMid,uBot;
      void main(){
        float h = normalize(vP).y;
        vec3 col = mix(uMid, uTop, smoothstep(0.0,0.7,h));
        col = mix(uBot, col, smoothstep(-0.3,0.1,h));
        gl_FragColor = vec4(col,1.0);
      }`,
  });
  return new THREE.Mesh(geo, mat);
}
