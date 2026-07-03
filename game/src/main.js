// Ravenmoor — a walkable dark-fantasy village at night.
// Integration core: renderer, atmosphere, pointer-lock controls, collision,
// torch-flicker, post-processing and procedural ambient audio.
// Scene content lives in ./modules/* and conforms to a fixed build(ctx) contract.

import * as THREE from 'three';
import { PointerLockControls } from 'three/addons/controls/PointerLockControls.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { ShaderPass } from 'three/addons/postprocessing/ShaderPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';

import { build as buildSky } from './modules/skybox.js';
import { build as buildEnvironment } from './modules/environment.js';
import { build as buildVillage } from './modules/village.js';
import { build as buildCastle } from './modules/castle.js';
import { build as buildProps } from './modules/props.js';

// ------------------------------------------------------------------ utils
// Deterministic seeded PRNG (mulberry32) so the world is identical each load.
function makeRng(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

// ------------------------------------------------------------------ renderer
const container = document.getElementById('app');
const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.06;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
container.appendChild(renderer.domElement);

// ------------------------------------------------------------------ scene
const scene = new THREE.Scene();
const FOG_COLOR = new THREE.Color(0x0a0c16);
scene.background = FOG_COLOR.clone();
scene.fog = new THREE.FogExp2(FOG_COLOR.getHex(), 0.0072);

const camera = new THREE.PerspectiveCamera(72, window.innerWidth / window.innerHeight, 0.1, 900);
camera.position.set(0, 1.7, 58);

// A very dim ambient bed so shadowed geometry never reads as pure black.
// Hemisphere is kept low so the up-facing ground doesn't wash out; the moon
// (directional) does the work of rim-lighting vertical stone into silhouette.
scene.add(new THREE.HemisphereLight(0x1c2340, 0x05060a, 0.085));
const ambient = new THREE.AmbientLight(0x0c1224, 0.13);
scene.add(ambient);

// ------------------------------------------------------------------ build world
const flickers = [];      // { light, base, amp, speed, phase }
const colliders = [];     // { minX, maxX, minZ, maxZ }
const updaters = [];      // (dt, elapsed) => void
let moonLight = null;

function mountModule(name, buildFn, seed) {
  let result;
  try {
    result = buildFn({ rng: makeRng(seed) });
  } catch (err) {
    console.error(`[Ravenmoor] module "${name}" failed to build:`, err);
    return;
  }
  if (!result || !result.group) { console.warn(`[Ravenmoor] module "${name}" returned no group`); return; }
  scene.add(result.group);
  if (Array.isArray(result.colliders)) colliders.push(...result.colliders);
  if (Array.isArray(result.flickers)) flickers.push(...result.flickers);
  if (typeof result.update === 'function') updaters.push(result.update);
  if (Array.isArray(result.lights)) {
    for (const l of result.lights) if (l && l.isDirectionalLight && !moonLight) moonLight = l;
  }
  return result;
}

setLoaderMsg('raising the sky…');
const sky = mountModule('skybox', buildSky, 0x51ce77);
// The sky, stars and moon must ignore scene fog or they vanish into the murk.
if (sky && sky.group) {
  sky.group.traverse((o) => {
    if (o.material) {
      const mats = Array.isArray(o.material) ? o.material : [o.material];
      for (const m of mats) { m.fog = false; }
    }
  });
}
setLoaderMsg('shaping the moor…');
mountModule('environment', buildEnvironment, 0x1a2b3c);
setLoaderMsg('waking the village…');
mountModule('village', buildVillage, 0x9e3f77);
setLoaderMsg('rousing the castle…');
mountModule('castle', buildCastle, 0x2c1a44);
setLoaderMsg('lighting the torches…');
mountModule('props', buildProps, 0x77aa22);

// Configure the moon as the single shadow-caster over the play area.
if (moonLight) {
  moonLight.intensity = 0.3; // a little more silhouette definition on the stone
  moonLight.castShadow = true;
  moonLight.shadow.mapSize.set(2048, 2048);
  const c = moonLight.shadow.camera;
  c.near = 1; c.far = 500; c.left = -160; c.right = 160; c.top = 160; c.bottom = -160;
  moonLight.shadow.bias = -0.0006;
  moonLight.shadow.normalBias = 0.6;
  c.updateProjectionMatrix();
} else {
  // Fallback moonlight if the sky module didn't provide one.
  moonLight = new THREE.DirectionalLight(0x8098c8, 0.22);
  moonLight.position.set(-120, 150, -170);
  scene.add(moonLight);
}

// World-boundary colliders so the player can't wander off the map into the void.
const WB = 150;
colliders.push({ minX: -WB - 4, maxX: -WB, minZ: -WB - 4, maxZ: WB + 4 });
colliders.push({ minX: WB, maxX: WB + 4, minZ: -WB - 4, maxZ: WB + 4 });
colliders.push({ minX: -WB - 4, maxX: WB + 4, minZ: -WB - 4, maxZ: -WB });
colliders.push({ minX: -WB - 4, maxX: WB + 4, minZ: WB, maxZ: WB + 4 });

// ------------------------------------------------------------------ controls & movement
const controls = new PointerLockControls(camera, renderer.domElement);
scene.add(controls.getObject());

const keys = Object.create(null);
const PLAYER_RADIUS = 0.55;
const EYE_HEIGHT = 1.7;
const WALK_SPEED = 4.6;
const RUN_SPEED = 8.2;
const velocity = new THREE.Vector3();
const forward = new THREE.Vector3();
const right = new THREE.Vector3();
let bobPhase = 0;

window.addEventListener('keydown', (e) => {
  keys[e.code] = true;
  if (e.code === 'KeyM') toggleAudio();
});
window.addEventListener('keyup', (e) => { keys[e.code] = false; });

function resolveCollisions(pos) {
  const r = PLAYER_RADIUS;
  for (let pass = 0; pass < 2; pass++) {
    for (const c of colliders) {
      const minX = c.minX - r, maxX = c.maxX + r, minZ = c.minZ - r, maxZ = c.maxZ + r;
      if (pos.x > minX && pos.x < maxX && pos.z > minZ && pos.z < maxZ) {
        const penL = pos.x - minX, penR = maxX - pos.x;
        const penD = pos.z - minZ, penU = maxZ - pos.z;
        const m = Math.min(penL, penR, penD, penU);
        if (m === penL) pos.x = minX;
        else if (m === penR) pos.x = maxX;
        else if (m === penD) pos.z = minZ;
        else pos.z = maxZ;
      }
    }
  }
  pos.x = clamp(pos.x, -WB + 1, WB - 1);
  pos.z = clamp(pos.z, -WB + 1, WB - 1);
}

function updateMovement(dt) {
  const obj = controls.getObject();
  const running = keys['ShiftLeft'] || keys['ShiftRight'];
  const speed = running ? RUN_SPEED : WALK_SPEED;

  camera.getWorldDirection(forward);
  forward.y = 0;
  if (forward.lengthSq() < 1e-6) forward.set(0, 0, -1);
  forward.normalize();
  right.crossVectors(forward, camera.up).normalize();

  let ix = 0, iz = 0;
  if (keys['KeyW'] || keys['ArrowUp']) iz += 1;
  if (keys['KeyS'] || keys['ArrowDown']) iz -= 1;
  if (keys['KeyD'] || keys['ArrowRight']) ix += 1;
  if (keys['KeyA'] || keys['ArrowLeft']) ix -= 1;

  const wish = new THREE.Vector3();
  wish.addScaledVector(forward, iz).addScaledVector(right, ix);
  if (wish.lengthSq() > 1e-6) wish.normalize().multiplyScalar(speed);

  // Smooth acceleration / damping.
  const k = 1 - Math.exp(-12 * dt);
  velocity.x += (wish.x - velocity.x) * k;
  velocity.z += (wish.z - velocity.z) * k;

  obj.position.x += velocity.x * dt;
  obj.position.z += velocity.z * dt;
  resolveCollisions(obj.position);

  // Subtle head-bob proportional to horizontal speed.
  const hspeed = Math.hypot(velocity.x, velocity.z);
  bobPhase += dt * hspeed * 1.9;
  const bob = Math.sin(bobPhase) * 0.045 * clamp(hspeed / WALK_SPEED, 0, 1.3);
  obj.position.y = EYE_HEIGHT + bob;
}

// ------------------------------------------------------------------ post-processing
const composer = new EffectComposer(renderer);
composer.addPass(new RenderPass(scene, camera));

const bloom = new UnrealBloomPass(
  new THREE.Vector2(window.innerWidth, window.innerHeight),
  0.68,   // strength
  0.6,    // radius
  0.17    // threshold — only bright emissive/fire blooms
);
composer.addPass(bloom);

// Vignette + film grain + cool shadow tint (operates in linear space, pre-output).
const AtmosphereShader = {
  uniforms: {
    tDiffuse: { value: null },
    uTime: { value: 0 },
    uVignette: { value: 1.12 },
    uGrain: { value: 0.022 },
  },
  vertexShader: /* glsl */`
    varying vec2 vUv;
    void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
  fragmentShader: /* glsl */`
    uniform sampler2D tDiffuse; uniform float uTime; uniform float uVignette; uniform float uGrain;
    varying vec2 vUv;
    float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
    void main() {
      vec4 c = texture2D(tDiffuse, vUv);
      vec2 d = vUv - 0.5;
      float vig = smoothstep(0.88, 0.22, length(d) * uVignette);
      c.rgb *= mix(0.32, 1.0, vig);
      c.rgb = mix(c.rgb, c.rgb * vec3(0.88, 0.94, 1.12), 0.18); // cool the shadows
      float g = hash(vUv * vec2(1287.0, 731.0) + fract(uTime)) * 2.0 - 1.0;
      c.rgb += g * uGrain;
      gl_FragColor = c;
    }`,
};
const atmospherePass = new ShaderPass(AtmosphereShader);
composer.addPass(atmospherePass);
composer.addPass(new OutputPass());

// ------------------------------------------------------------------ procedural ambient audio
class AudioManager {
  constructor() { this.ctx = null; this.master = null; this.started = false; this.muted = false; this._cawTimer = null; }
  start() {
    if (this.started) return;
    try {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      const ctx = new AC();
      this.ctx = ctx;
      this.started = true;
      const master = ctx.createGain();
      master.gain.value = this.muted ? 0 : 0.5;
      master.connect(ctx.destination);
      this.master = master;

      // Wind: looping filtered noise with a slow-gusting filter + gain LFO.
      const noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 4, ctx.sampleRate);
      const nd = noiseBuf.getChannelData(0);
      let last = 0;
      for (let i = 0; i < nd.length; i++) { // brownish noise
        const w = Math.random() * 2 - 1;
        last = (last + 0.02 * w) / 1.02; nd[i] = last * 3.2;
      }
      const wind = ctx.createBufferSource(); wind.buffer = noiseBuf; wind.loop = true;
      const windFilt = ctx.createBiquadFilter(); windFilt.type = 'lowpass'; windFilt.frequency.value = 420; windFilt.Q.value = 0.7;
      const windGain = ctx.createGain(); windGain.gain.value = 0.22;
      wind.connect(windFilt).connect(windGain).connect(master); wind.start();

      const gust = ctx.createOscillator(); gust.type = 'sine'; gust.frequency.value = 0.06;
      const gustDepth = ctx.createGain(); gustDepth.gain.value = 0.14;
      gust.connect(gustDepth).connect(windGain.gain); gust.start();
      const filtLfo = ctx.createOscillator(); filtLfo.type = 'sine'; filtLfo.frequency.value = 0.09;
      const filtDepth = ctx.createGain(); filtDepth.gain.value = 220;
      filtLfo.connect(filtDepth).connect(windFilt.frequency); filtLfo.start();

      // Ominous low drone: two detuned oscillators through a lowpass.
      const droneGain = ctx.createGain(); droneGain.gain.value = 0.05;
      const droneFilt = ctx.createBiquadFilter(); droneFilt.type = 'lowpass'; droneFilt.frequency.value = 180;
      droneGain.connect(droneFilt).connect(master);
      for (const f of [55, 55.4, 82.5]) {
        const o = ctx.createOscillator(); o.type = 'sawtooth'; o.frequency.value = f;
        const g = ctx.createGain(); g.gain.value = f > 80 ? 0.3 : 0.6;
        o.connect(g).connect(droneGain); o.start();
      }

      this._scheduleCaw();
    } catch (e) { /* audio is a nicety; never fatal */ }
  }
  _scheduleCaw() {
    const delay = 6000 + Math.random() * 14000;
    this._cawTimer = setTimeout(() => { this._caw(); this._scheduleCaw(); }, delay);
  }
  _caw() {
    const ctx = this.ctx; if (!ctx) return;
    const t = ctx.currentTime;
    const bell = Math.random() < 0.35;
    if (bell) { // distant tolling bell
      const o = ctx.createOscillator(); o.type = 'sine'; o.frequency.value = 140 + Math.random() * 30;
      const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(0.16, t + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t + 3.5);
      o.connect(g).connect(this.master); o.start(t); o.stop(t + 3.6);
    } else { // a raven's caw
      for (let k = 0; k < 2; k++) {
        const s = t + k * 0.22;
        const o = ctx.createOscillator(); o.type = 'sawtooth';
        o.frequency.setValueAtTime(560, s); o.frequency.exponentialRampToValueAtTime(320, s + 0.16);
        const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, s);
        g.gain.exponentialRampToValueAtTime(0.05, s + 0.03);
        g.gain.exponentialRampToValueAtTime(0.0001, s + 0.22);
        const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = 1200; bp.Q.value = 3;
        o.connect(bp).connect(g).connect(this.master); o.start(s); o.stop(s + 0.24);
      }
    }
  }
  resume() { if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume(); }
  setMuted(m) { this.muted = m; if (this.master) this.master.gain.value = m ? 0 : 0.5; }
}
const audio = new AudioManager();
function toggleAudio() {
  audio.setMuted(!audio.muted);
  const el = document.getElementById('audioState');
  if (el) el.textContent = audio.muted ? 'off' : 'on';
}

// ------------------------------------------------------------------ UI wiring
const overlay = document.getElementById('overlay');
const loader = document.getElementById('loader');
const hud = document.getElementById('hud');
const enterBtn = document.getElementById('enterBtn');
function setLoaderMsg(m) { const el = document.getElementById('loaderMsg'); if (el) el.textContent = m; }

function beginPlay() {
  controls.lock();
  audio.start(); audio.resume();
}
enterBtn.addEventListener('click', beginPlay);
overlay.addEventListener('click', (e) => { if (e.target === overlay || overlay.classList.contains('paused')) beginPlay(); });

controls.addEventListener('lock', () => {
  overlay.classList.add('hidden');
  overlay.classList.remove('paused');
  hud.classList.add('active');
});
controls.addEventListener('unlock', () => {
  overlay.classList.remove('hidden');
  overlay.classList.add('paused');
  hud.classList.remove('active');
});

// Reveal the world once everything is built.
requestAnimationFrame(() => {
  loader.style.opacity = '0';
  setTimeout(() => { loader.style.display = 'none'; }, 850);
});

// ------------------------------------------------------------------ animation loop
const clock = new THREE.Clock();
let elapsed = 0;

// Layered pseudo-noise for flame flicker (cheap, no allocations).
function flameNoise(t, phase, speed) {
  return 0.55 * Math.sin(t * speed + phase)
       + 0.28 * Math.sin(t * speed * 2.37 + phase * 1.7)
       + 0.17 * Math.sin(t * speed * 4.11 + phase * 0.9);
}

function animate() {
  requestAnimationFrame(animate);
  const dt = Math.min(clock.getDelta(), 0.05);
  elapsed += dt;

  if (controls.isLocked) updateMovement(dt);

  // Torch / fire flicker.
  for (const f of flickers) {
    const n = flameNoise(elapsed, f.phase || 0, f.speed || 8);
    const amp = f.amp == null ? 0.35 : f.amp;
    f.light.intensity = Math.max(0.05, f.base * (1 + amp * n));
  }

  for (const u of updaters) { try { u(dt, elapsed); } catch (e) { /* keep the loop alive */ } }

  atmospherePass.uniforms.uTime.value = elapsed;
  composer.render();
}
animate();

// ------------------------------------------------------------------ resize
window.addEventListener('resize', () => {
  const w = window.innerWidth, h = window.innerHeight;
  camera.aspect = w / h; camera.updateProjectionMatrix();
  renderer.setSize(w, h);
  composer.setSize(w, h);
  bloom.setSize(w, h);
});

// Expose a little state for debugging / verification.
window.__ravenmoor = { scene, camera, renderer, colliders, flickers, get pos() { return camera.position.toArray(); } };
