/* =========================================================================
   Part 5 / 5 — environment, planar reflections, post stack, controls, audio
   ========================================================================= */
import * as THREE from 'three';
import {
  CFG, QUALITY, SHADOW_SIZE, ROOM, U, clamp, lerp, damp, easeInOut, rnd, rrnd,
  renderer, camera, roomScene, outsideScene, canvas, blit, rt, VERT_QUAD,
} from './room.js';
import { buildOutside, Lightning, FOG_U } from './room-outside.js';
import { buildShell, buildWindows, glassMaterials, reflectiveFloors } from './room-shell.js';
import { buildFireplace } from './room-interior.js';
import { APT, ROOMS, roomAt } from './room-plan.js';
import { buildKitchen } from './room-kitchen.js';
import { buildBedroom } from './room-bedroom.js';
import { buildHall } from './room-hall.js';
import { buildProps, buildLights, updateLights } from './room-props.js';
import { REGISTRY, prewarm, texStats } from './tex/index.js';
import { AmbientOcclusion } from './post-ao.js';
import { DepthOfField } from './post-dof.js';
import { Walker, roomColliders } from './room-walk.js';

const boot = document.getElementById('boot');
const bootBar = document.getElementById('bootBar');
const bootLabel = document.getElementById('bootLabel');
const step = (pct, label) => {
  if (bootBar) bootBar.style.width = pct + '%';
  if (label && bootLabel) bootLabel.textContent = label;
  return new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
};

/* ==================================================== environment map ==== */
function buildEnvironment() {
  const cv = document.createElement('canvas');
  cv.width = 512; cv.height = 256;
  const g = cv.getContext('2d');

  // sky above the horizon
  const sky = g.createLinearGradient(0, 0, 0, 128);
  sky.addColorStop(0, '#0a0d14');
  sky.addColorStop(0.7, '#141a24');
  sky.addColorStop(1, '#2a2a2c');
  g.fillStyle = sky; g.fillRect(0, 0, 512, 128);

  // ground / city glow below
  const gnd = g.createLinearGradient(0, 128, 0, 256);
  gnd.addColorStop(0, '#4a2c16');
  gnd.addColorStop(0.35, '#20140c');
  gnd.addColorStop(1, '#0a0705');
  g.fillStyle = gnd; g.fillRect(0, 128, 512, 128);

  // sodium band right at the horizon
  const band = g.createLinearGradient(0, 108, 0, 152);
  band.addColorStop(0, 'rgba(255,150,60,0)');
  band.addColorStop(0.5, 'rgba(255,150,60,.55)');
  band.addColorStop(1, 'rgba(255,150,60,0)');
  g.fillStyle = band; g.fillRect(0, 108, 512, 44);

  // the fire, baked into the environment so metal and marble pick it up
  const fx = 512 * 0.5, fy = 150;
  const fire = g.createRadialGradient(fx, fy, 2, fx, fy, 92);
  fire.addColorStop(0, 'rgba(255,190,110,.95)');
  fire.addColorStop(0.35, 'rgba(255,110,30,.45)');
  fire.addColorStop(1, 'rgba(255,90,20,0)');
  g.fillStyle = fire; g.fillRect(fx - 100, fy - 100, 200, 200);

  // scattered window lights on the far towers
  for (let i = 0; i < 260; i++) {
    const x = rnd() * 512, y = 118 + rnd() * 44;
    g.fillStyle = rnd() < 0.7 ? 'rgba(255,170,90,.5)' : 'rgba(150,190,255,.4)';
    g.fillRect(x, y, rrnd(1, 3), rrnd(1, 2));
  }

  const tex = new THREE.CanvasTexture(cv);
  tex.mapping = THREE.EquirectangularReflectionMapping;
  tex.colorSpace = THREE.SRGBColorSpace;

  const pmrem = new THREE.PMREMGenerator(renderer);
  pmrem.compileEquirectangularShader();
  const env = pmrem.fromEquirectangular(tex).texture;
  pmrem.dispose();
  tex.dispose();

  roomScene.environment = env;
  return env;
}

/* ------------------------------------------------ capture the real room ---
   A painted gradient environment makes every material reflect a generic
   outdoors, which is exactly why they read as pasted into the room rather than
   standing in it. Rendering a cube map from inside the room gives genuine
   colour bleeding: the ceiling picks up the fire, the marble picks up the
   floor, the brass picks up the wall it is standing against.

   It is captured, not tracked — the environment is a low-frequency term and
   the flame's variation is carried by the punctual lights, so one capture
   (plus one when the lamps toggle) is plenty and costs nothing per frame. */
let cubeRT = null, cubeCam = null, pmremGen = null, capturedEnv = null;

function captureRoomEnvironment() {
  if (!cubeRT) {
    cubeRT = new THREE.WebGLCubeRenderTarget(128, { type: THREE.HalfFloatType });
    cubeCam = new THREE.CubeCamera(0.25, 60, cubeRT);
    pmremGen = new THREE.PMREMGenerator(renderer);
  }
  // Captured from the middle of the open plan rather than in front of the
  // hearth: from the old point the fire filled most of the cube, and every
  // material in the flat — including the ones in the bedroom two rooms away
  // — reflected an orange room.
  cubeCam.position.set(2.40, 1.60, -0.90);

  // the reflective materials must not sample render targets mid-capture, and
  // the stretched background quad would be wrong on every cube face
  const prevOn = U.reflOn.value;
  const prevBg = roomScene.background;
  U.reflOn.value = 0;
  roomScene.background = null;
  // NOTE: the environment is deliberately left in place. Setting it to null
  // flips the USE_ENVMAP define and forces every material in the room to
  // recompile, twice — which is a visible freeze on a real driver. One extra
  // bounce in the capture is a far cheaper price.

  renderer.setClearColor(0x05070a, 1);
  cubeCam.update(renderer, roomScene);

  U.reflOn.value = prevOn;
  roomScene.background = prevBg;

  const next = pmremGen.fromCubemap(cubeRT.texture).texture;
  if (capturedEnv) capturedEnv.dispose();
  capturedEnv = next;
  roomScene.environment = capturedEnv;
  renderer.setRenderTarget(null);
}

/* ====================================================== planar mirror ==== */
class PlanarReflector {
  constructor(normal, point, scale = 0.5) {
    this.normal = normal.clone().normalize();
    this.point = point.clone();
    this.scale = scale;
    this.target = rt(2, 2);
    this.matrix = new THREE.Matrix4();
    this.cam = new THREE.PerspectiveCamera();
    this._v = new THREE.Vector3();
    this._look = new THREE.Vector3();
    this._rot = new THREE.Matrix4();
  }
  resize(w, h) {
    const tw = Math.max(2, (w * this.scale) | 0), th = Math.max(2, (h * this.scale) | 0);
    if (this.target.width === tw && this.target.height === th) return;
    this.target.setSize(tw, th);
  }
  update(mainCam, scene, hide = []) {
    const n = this.normal, p = this.point, vc = this.cam;

    // mirror the camera through the plane:  p' = p - 2n·dot(p - point, n)
    this._v.copy(mainCam.position).sub(p);
    const d = this._v.dot(n);
    if (d < 0) return false;                            // camera is behind the mirror
    vc.position.copy(mainCam.position).addScaledVector(n, -2 * d);

    this._rot.extractRotation(mainCam.matrixWorld);
    this._look.set(0, 0, -1).applyMatrix4(this._rot).add(mainCam.position);
    this._look.sub(p);
    const dl = this._look.dot(n);
    this._look.addScaledVector(n, -2 * dl).add(p);

    vc.up.set(0, 1, 0).applyMatrix4(this._rot);
    vc.up.addScaledVector(n, -2 * vc.up.dot(n));
    vc.lookAt(this._look);
    vc.near = mainCam.near; vc.far = mainCam.far;
    vc.fov = mainCam.fov; vc.aspect = mainCam.aspect;
    vc.updateProjectionMatrix();
    vc.updateMatrixWorld(true);

    this.matrix.set(0.5, 0, 0, 0.5, 0, 0.5, 0, 0.5, 0, 0, 0.5, 0.5, 0, 0, 0, 1);
    this.matrix.multiply(vc.projectionMatrix);
    this.matrix.multiply(vc.matrixWorldInverse);

    const bg = scene.background;
    scene.background = null;
    const vis = hide.map((o) => o.visible);
    hide.forEach((o) => { o.visible = false; });
    U.reflOn.value = 0;

    setGlassRes(this.target.width, this.target.height);
    renderer.setRenderTarget(this.target);
    renderer.setClearColor(0x05070a, 1);
    renderer.clear();
    renderer.render(scene, vc);

    U.reflOn.value = 1;
    hide.forEach((o, i) => { o.visible = vis[i]; });
    scene.background = bg;
    return true;
  }
}

function setGlassRes(w, h) {
  for (const m of glassMaterials) m.uniforms.uRes.value.set(w, h);
}

/* =============================================================== post ==== */
const KAWASE_DOWN = /* glsl */`
uniform sampler2D tDiffuse; uniform vec2 uTexel; varying vec2 vUv;
void main(){
  vec2 h = uTexel * 0.5;
  vec4 s = texture2D(tDiffuse, vUv) * 4.0;
  s += texture2D(tDiffuse, vUv + vec2( h.x,  h.y));
  s += texture2D(tDiffuse, vUv + vec2(-h.x,  h.y));
  s += texture2D(tDiffuse, vUv + vec2( h.x, -h.y));
  s += texture2D(tDiffuse, vUv + vec2(-h.x, -h.y));
  gl_FragColor = s / 8.0;
}`;

const KAWASE_UP = /* glsl */`
uniform sampler2D tDiffuse; uniform vec2 uTexel; varying vec2 vUv;
void main(){
  vec2 h = uTexel;
  vec4 s = texture2D(tDiffuse, vUv + vec2(-h.x * 2.0, 0.0));
  s += texture2D(tDiffuse, vUv + vec2(-h.x,  h.y)) * 2.0;
  s += texture2D(tDiffuse, vUv + vec2(0.0,  h.y * 2.0));
  s += texture2D(tDiffuse, vUv + vec2( h.x,  h.y)) * 2.0;
  s += texture2D(tDiffuse, vUv + vec2( h.x * 2.0, 0.0));
  s += texture2D(tDiffuse, vUv + vec2( h.x, -h.y)) * 2.0;
  s += texture2D(tDiffuse, vUv + vec2(0.0, -h.y * 2.0));
  s += texture2D(tDiffuse, vUv + vec2(-h.x, -h.y)) * 2.0;
  gl_FragColor = s / 12.0;
}`;

const mkPass = (frag, uniforms, blending) => new THREE.ShaderMaterial({
  uniforms, vertexShader: VERT_QUAD, fragmentShader: frag,
  depthTest: false, depthWrite: false,
  blending: blending || THREE.NoBlending,
  transparent: !!blending,
});

class Post {
  constructor() {
    this.hdr = rt(2, 2, { depth: true });
    this.hdr.depthTexture = new THREE.DepthTexture(2, 2);
    this.hdr.depthTexture.type = THREE.UnsignedIntType;
    this.hdr.depthTexture.minFilter = THREE.NearestFilter;
    this.hdr.depthTexture.magFilter = THREE.NearestFilter;
    this.levels = [];
    for (let i = 0; i < 5; i++) this.levels.push(rt(2, 2, { depth: false }));

    // soft-knee bright pass: everything above the threshold, easing in
    this.bright = mkPass(/* glsl */`
      uniform sampler2D tDiffuse; uniform float uThresh, uKnee; varying vec2 vUv;
      void main(){
        vec3 c = texture2D(tDiffuse, vUv).rgb;
        float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
        float soft = clamp(l - uThresh + uKnee, 0.0, 2.0 * uKnee);
        soft = soft * soft / (4.0 * uKnee + 1e-4);
        float w = max(soft, l - uThresh) / max(l, 1e-4);
        gl_FragColor = vec4(c * clamp(w, 0.0, 1.0), 1.0);
      }`, {
      tDiffuse: { value: null }, uThresh: { value: 0.90 }, uKnee: { value: 0.45 },
    });

    this.down = mkPass(KAWASE_DOWN, { tDiffuse: { value: null }, uTexel: { value: new THREE.Vector2() } });
    // progressive upsample accumulates into the level below, so it must add
    this.up = mkPass(KAWASE_UP, { tDiffuse: { value: null }, uTexel: { value: new THREE.Vector2() } },
      THREE.AdditiveBlending);

    this.composite = mkPass(/* glsl */`
      uniform sampler2D tHDR, tBloom, tAO;
      uniform vec2  uRes;
      uniform float uTime, uExposure, uBloom, uWarm, uVignette, uGrain, uFlash, uAO, uDebugAO;
      varying vec2 vUv;

      vec3 aces(vec3 x){
        const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
        return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
      }
      vec3 lin2srgb(vec3 c){
        c = max(c, vec3(0.0));
        return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
      }
      float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

      void main(){
        vec2 uv = vUv;
        if(uDebugAO > 0.5){ gl_FragColor = vec4(vec3(texture2D(tAO, uv).r), 1.0); return; }
        vec2 d  = uv - 0.5;
        float r2 = dot(d, d);

        // lens: a whisper of chromatic aberration at the corners
        float ca = 0.0022 * r2;
        vec3 col;
        col.r = texture2D(tHDR, uv + d * ca).r;
        col.g = texture2D(tHDR, uv).g;
        col.b = texture2D(tHDR, uv - d * ca).b;

        // Contact darkening, before exposure so it reads as light that never
        // arrived rather than as a grey wash on top. Occlusion belongs to the
        // ambient term, so fade it out where a pixel is already brightly lit —
        // otherwise the firelit side of everything gets muddied too.
        if (uAO > 0.001) {
          float aoRaw = texture2D(tAO, uv).r;
          float lit = dot(col, vec3(0.2126, 0.7152, 0.0722));
          float w = uAO * (1.0 - smoothstep(0.10, 0.85, lit));
          col *= mix(1.0, aoRaw, w);
        }
        col += texture2D(tBloom, uv).rgb * uBloom;
        col *= uExposure * (1.0 + uFlash * 0.35);
        col = aces(col);

        // grade — cool in the shadows, warm in the light, like firelight in a dark room
        float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
        vec3 shadowTint = mix(vec3(1.0), vec3(0.84, 0.93, 1.13), 0.85);
        vec3 highTint   = mix(vec3(1.0), vec3(1.12, 1.00, 0.85), 0.85);
        col *= mix(shadowTint, highTint, smoothstep(0.04, 0.72, lum));
        col  = mix(col, col * vec3(1.10, 0.985, 0.85), uWarm * 0.55);
        col  = mix(vec3(lum), col, 1.07);
        col  = clamp(col, 0.0, 1.0);

        float vig = smoothstep(1.02, 0.22, length(d) * 1.32);
        col *= mix(1.0, vig, uVignette);

        float g = hash(uv * uRes + fract(uTime) * 977.0);
        col += (g - 0.5) * uGrain * (1.15 - lum * 0.8);
        col += (hash(uv * uRes + 31.7) - 0.5) * (1.0 / 255.0);   // dither out the banding

        gl_FragColor = vec4(lin2srgb(col), 1.0);
      }`, {
      tHDR: { value: null }, tBloom: { value: null },
      uRes: { value: new THREE.Vector2() },
      uTime: U.time,
      uExposure: { value: 1.24 },
      uBloom: { value: 0.28 },
      uWarm: { value: CFG.warm },
      uVignette: { value: 0.85 },
      uGrain: { value: 0.014 },
      uFlash: U.flash,
      tAO: { value: null },
      uAO: { value: 0.0 },
      uDebugAO: { value: 0.0 },
    });
  }

  resize(w, h) {
    if (this.hdr.width !== w || this.hdr.height !== h) this.hdr.setSize(w, h);
    for (let i = 0; i < this.levels.length; i++) {
      const lw = Math.max(2, w >> (i + 1)), lh = Math.max(2, h >> (i + 1));
      if (this.levels[i].width !== lw || this.levels[i].height !== lh) this.levels[i].setSize(lw, lh);
    }
    this.composite.uniforms.uRes.value.set(w, h);
  }

  render(nLevels, source) {
    const src = source || this.hdr.texture;
    const L = this.levels, n = Math.min(nLevels, L.length);
    this.bright.uniforms.tDiffuse.value = src;
    blit(this.bright, L[0]);
    for (let i = 1; i < n; i++) {
      this.down.uniforms.tDiffuse.value = L[i - 1].texture;
      this.down.uniforms.uTexel.value.set(1 / L[i - 1].width, 1 / L[i - 1].height);
      blit(this.down, L[i]);
    }
    for (let i = n - 1; i > 0; i--) {
      this.up.uniforms.tDiffuse.value = L[i].texture;
      this.up.uniforms.uTexel.value.set(1 / L[i].width, 1 / L[i].height);
      blit(this.up, L[i - 1], false);          // add on top of this level's own content
    }
    this.composite.uniforms.tHDR.value = src;
    this.composite.uniforms.tBloom.value = L[0].texture;
    blit(this.composite, null);
  }
}

/* ============================================================== build ==== */
let outside, shell, windows, fire, props, lights, post, lightning, ao, dof;
let rtOut, rtOutSmall, rtOutBlur, outBlurDown, outBlurUp;
let floorRefl, winReflS, winReflE;
let env;

async function build() {
  await step(4, 'Разжигаем камин');
  env = buildEnvironment();

  /* Paint every procedural surface up front, yielding between each one so the
     loader keeps animating instead of freezing on a long synchronous block. */
  {
    const names = Object.keys(REGISTRY);
    for (let i = 0; i < names.length; i++) {
      prewarm(names[i]);
      await step(4 + Math.round((i + 1) / names.length * 26), 'Ткём текстуры');
    }
    console.info(`textures: ${texStats.count} surfaces in ${texStats.ms.toFixed(0)} ms`, texStats.byName);
  }

  await step(34, 'Строим комнату');
  shell = buildShell();

  await step(46, 'Стеклим панорамные окна');
  windows = buildWindows();

  await step(58, 'Кладём камин');
  fire = buildFireplace();

  await step(70, 'Расставляем мебель');
  props = buildProps();
  const rooms = { kitchen: buildKitchen(), bedroom: buildBedroom(), hall: buildHall() };
  for (const r of Object.values(rooms)) roomScene.add(r.group);
  lights = buildLights({
    firePos: fire.firePos, lampPos: props.lamp.lightPos, chandPos: props.chand.lightPos,
    shadowSize: SHADOW_SIZE,
    pendants: rooms.kitchen?.pendants, lamps: rooms.bedroom?.lamps, sconces: rooms.hall?.sconces,
  });

  await step(84, 'Зажигаем город за окном');
  outside = buildOutside(QUALITY[2]);
  lightning = new Lightning();

  await step(94, 'Впускаем дождь');
  post = new Post();
  ao = new AmbientOcclusion({ THREE, renderer, blit, rt, VERT_QUAD });
  dof = new DepthOfField({ THREE, blit, rt, VERT_QUAD });
  rtOut = rt(2, 2, { depth: true });
  rtOutSmall = rt(2, 2, { depth: false });
  rtOutBlur = rt(2, 2, { depth: false });
  outBlurDown = mkPass(KAWASE_DOWN, { tDiffuse: { value: null }, uTexel: { value: new THREE.Vector2() } });
  outBlurUp = mkPass(KAWASE_UP, { tDiffuse: { value: null }, uTexel: { value: new THREE.Vector2() } });

  floorRefl = new PlanarReflector(new THREE.Vector3(0, 1, 0), new THREE.Vector3(0, 0, 0), 0.5);
  winReflS = new PlanarReflector(new THREE.Vector3(0, 0, 1), new THREE.Vector3(0, 0, APT.z0), 0.5);
  winReflE = new PlanarReflector(new THREE.Vector3(-1, 0, 0), new THREE.Vector3(APT.x1, 0, 0), 0.5);

  roomScene.background = rtOut.texture;

  applyQuality(CFG.quality);
  onResize();

  // one outside pass first, so the glass has a city to refract into the capture
  renderer.setRenderTarget(rtOut);
  renderer.clear();
  renderer.render(outsideScene, camera);
  for (const m of glassMaterials) {
    m.uniforms.tBack.value = rtOut.texture;
    m.uniforms.tBackBlur.value = rtOut.texture;
  }
  await step(97, 'Снимаем отражения комнаты');
  captureRoomEnvironment();

  // Compile every program up front. Otherwise the driver links a shader the
  // first time an object becomes visible, and moving the camera to a new view
  // links several at once — which is exactly what a freeze on a button press
  // looks like.
  await step(99, 'Прогреваем шейдеры');
  renderer.compile(roomScene, camera);
  renderer.compile(outsideScene, camera);

  await step(100, 'Готово');
  document.body.classList.add('ready');
  setTimeout(() => boot?.classList.add('done'), 260);
}

/* =========================================================== quality ==== */
/* Switching tiers used to resize eleven render targets inside the click
   handler and froze the page for over a second. Tiers now change nothing that
   allocates — only uniforms, instance counts and which passes run. */
function paintQualityUi(q) {
  document.querySelectorAll('#segQual button').forEach((b) => {
    b.setAttribute('aria-pressed', String(+b.dataset.q === q));
  });
  const oq = document.getElementById('oQual');
  if (oq) oq.textContent = ['низкое', 'среднее', 'высокое'][q];
}

let lastCommitMs = 0;
function applyQuality(q) {
  const t0 = performance.now();
  CFG.quality = q = clamp(q | 0, 0, 2);
  const Q = QUALITY[q];
  outside.city.count = Math.min(Q.towers, outside.plan.boxes.length);
  outside.streets.count = Math.min(Q.streets, outside.plan.streets.length);
  outside.traffic.geometry.instanceCount = Math.min(Q.cars, outside.traffic.maxCars);
  outside.rain.geometry.instanceCount = Q.rain;
  for (const f of reflectiveFloors) f.uniforms.uReflAmt.value = Q.refl >= 1 ? 0.30 : 0.0;
  for (const m of glassMaterials) m.uniforms.uReflAmt.value = Q.refl >= 2 ? 0.9 : 0.0;
  paintQualityUi(q);
  lastCommitMs = performance.now() - t0;
}

/* ============================================================ resize ==== */
let W = 1, Hh = 1, cssW = 0, cssH = 0;
/* the canvas backing store is fixed at load; quality only scales the offscreen
   buffers, so switching tiers never resizes the canvas or the GL drawing buffer */
const CANVAS_DPR = Math.min(window.devicePixelRatio || 1, 2);

function onResize() {
  const cw = canvas.clientWidth || window.innerWidth;
  const ch = canvas.clientHeight || window.innerHeight;

  if (cw !== cssW || ch !== cssH) {
    cssW = cw; cssH = ch;
    renderer.setPixelRatio(CANVAS_DPR);
    renderer.setSize(cw, ch, false);
    camera.aspect = cw / ch;
    camera.updateProjectionMatrix();
  }

  const w = Math.max(2, Math.round(cw * CANVAS_DPR * CFG.res));
  const h = Math.max(2, Math.round(ch * CANVAS_DPR * CFG.res));
  if (!post || (w === W && h === Hh)) return;   // nothing to reallocate
  W = w; Hh = h;

  post.resize(W, Hh);
  post.hdr.depthTexture.image.width = W;
  post.hdr.depthTexture.image.height = Hh;
  post.hdr.depthTexture.needsUpdate = true;
  ao.resize(W, Hh);
  dof.resize(W, Hh);
  rtOut.setSize(W, Hh);
  const bw = Math.max(2, W >> 2), bh = Math.max(2, Hh >> 2);
  rtOutSmall.setSize(bw, bh);
  rtOutBlur.setSize(bw, bh);
  floorRefl.resize(W, Hh);
  winReflS.resize(W, Hh);
  winReflE.resize(W, Hh);

  const pix = Hh / 900;
  fire.embers.material.uniforms.uPix.value = pix;
  props.dust.material.uniforms.uPix.value = pix;
}

/* window resizing fires a storm of events; reallocating buffers on each one
   is what made dragging the window edge unusable */
let resizeTimer = 0;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(onResize, 140);
});

/* =========================================================== controls === */
/* Each preset has to leave the camera inside its own room — the orbit rig
   puts the eye at target + dist·(sinθ·sinφ, cosφ, cosθ·sinφ), and it is very
   easy to end up standing in the hall looking at the living room through a
   wall. */
const VIEWS = [
  { name: 'Гостиная', target: new THREE.Vector3(-1.20, 1.05, -0.80), dist: 4.60, theta: 0.78, phi: 1.500 },
  { name: 'У камина', target: new THREE.Vector3(-4.55, 1.00, -0.60), dist: 3.55, theta: 1.24, phi: 1.520 },
  { name: 'У окна',   target: new THREE.Vector3(-1.40, 1.05, -4.20), dist: 2.60, theta: 0.10, phi: 1.505 },
  { name: 'Кухня',    target: new THREE.Vector3(8.20, 1.05, -1.60), dist: 3.60, theta: -1.15, phi: 1.505 },
  // east of the bed looking back at the headboard — the old one pointed at
  //  an empty corner, which read as a lighting problem and was not one
  { name: 'Спальня',  target: new THREE.Vector3(5.60, 0.95, 2.05), dist: 3.55, theta: 1.14, phi: 1.430 },
];

const cam = {
  target: VIEWS[0].target.clone(),
  dist: VIEWS[0].dist, theta: VIEWS[0].theta, phi: VIEWS[0].phi,
  gTarget: VIEWS[0].target.clone(), gDist: VIEWS[0].dist, gTheta: VIEWS[0].theta, gPhi: VIEWS[0].phi,
  tween: null, idle: 0, drift: 0,
};

function gotoView(i) {
  const v = VIEWS[i];
  cam.tween = {
    t: 0, dur: 1.7,
    from: { target: cam.gTarget.clone(), dist: cam.gDist, theta: cam.gTheta, phi: cam.gPhi },
    to: { target: v.target.clone(), dist: v.dist, theta: v.theta, phi: v.phi },
  };
  cam.drift = 0;
  document.querySelectorAll('#dock button[data-view]').forEach((b) => {
    b.setAttribute('aria-pressed', String(+b.dataset.view === i));
  });
}

function snapView(i) {
  const v = VIEWS[i];
  cam.tween = null;
  cam.gTarget.copy(v.target); cam.target.copy(v.target);
  cam.gDist = cam.dist = v.dist;
  cam.gTheta = cam.theta = v.theta;
  cam.gPhi = cam.phi = v.phi;
  document.querySelectorAll('#dock button[data-view]').forEach((b) => {
    b.setAttribute('aria-pressed', String(+b.dataset.view === i));
  });
}

/* ------------------------------------------------------------ walking --- */
const walker = new Walker(camera, canvas);
walker.setColliders(roomColliders());

function setWalking(on) {
  const btn = document.getElementById('btnWalk');
  if (on) {
    // start from where the orbit camera is, looking the same way
    walker.start({
      x: camera.position.x, z: camera.position.z,
      yaw: Math.atan2(camera.position.x - cam.target.x, camera.position.z - cam.target.z) + Math.PI,
      pitch: Math.asin(clamp((cam.target.y - camera.position.y) /
        Math.max(camera.position.distanceTo(cam.target), 1e-3), -1, 1)),
    });
    hint?.classList.add('gone');
  } else {
    walker.stop();
  }
  btn?.setAttribute('aria-pressed', String(on));
  document.getElementById('walkHint')?.classList.toggle('show', on);
}
walker.onExit = () => {
  document.getElementById('btnWalk')?.setAttribute('aria-pressed', 'false');
  document.getElementById('walkHint')?.classList.remove('show');
  // hand control back to the orbit rig from where the walker left off
  cam.gTarget.set(
    walker.pos.x - Math.sin(walker.yaw) * 2.4,
    walker.eye + Math.sin(walker.pitch) * 2.4,
    walker.pos.z - Math.cos(walker.yaw) * 2.4,
  );
  cam.target.copy(cam.gTarget);
  cam.gDist = cam.dist = 2.4;
  cam.gTheta = cam.theta = walker.yaw + Math.PI;
  cam.gPhi = cam.phi = clamp(Math.PI / 2 - walker.pitch, 1.16, 1.86);
  cam.tween = null;
};

/* pointer look */
let dragging = false, lastX = 0, lastY = 0, pinch = 0;
const hint = document.getElementById('hint');
const nudge = () => { cam.idle = 0; cam.tween = null; hint?.classList.add('gone'); };

canvas.addEventListener('pointerdown', (e) => {
  if (walker.active) return;
  if (e.pointerType === 'mouse' && e.button !== 0) return;
  dragging = true; lastX = e.clientX; lastY = e.clientY;
  canvas.classList.add('dragging');
  canvas.setPointerCapture?.(e.pointerId);
  nudge();
});
canvas.addEventListener('pointermove', (e) => {
  if (!dragging) return;
  const dx = e.clientX - lastX, dy = e.clientY - lastY;
  lastX = e.clientX; lastY = e.clientY;
  cam.gTheta -= dx * 0.0042;
  cam.gPhi = clamp(cam.gPhi - dy * 0.0034, 1.16, 1.86);
  cam.idle = 0;
});
const endDrag = (e) => {
  dragging = false; canvas.classList.remove('dragging');
  if (e && e.pointerId != null) canvas.releasePointerCapture?.(e.pointerId);
};
canvas.addEventListener('pointerup', endDrag);
canvas.addEventListener('pointercancel', endDrag);
canvas.addEventListener('pointerleave', endDrag);

canvas.addEventListener('wheel', (e) => {
  if (walker.active) return;
  e.preventDefault();
  cam.gDist = clamp(cam.gDist * (1 + Math.sign(e.deltaY) * 0.08), 1.5, 8.5);
  nudge();
}, { passive: false });

canvas.addEventListener('touchmove', (e) => {
  if (e.touches.length !== 2) return;
  const dx = e.touches[0].clientX - e.touches[1].clientX;
  const dy = e.touches[0].clientY - e.touches[1].clientY;
  const d = Math.hypot(dx, dy);
  if (pinch) cam.gDist = clamp(cam.gDist * (pinch / d), 1.5, 8.5);
  pinch = d; nudge();
}, { passive: true });
canvas.addEventListener('touchend', () => { pinch = 0; });

window.addEventListener('keydown', (e) => {
  if (!walker.active && ['KeyW', 'KeyA', 'KeyS', 'KeyD'].includes(e.code)) { setWalking(true); return; }
  if (walker.active) return;                       // the walker owns the keys
  const n = '12345'.indexOf(e.key);
  if (n >= 0) gotoView(n);
});

const _cp = new THREE.Vector3();
function updateCamera(dt) {
  if (cam.tween) {
    const tw = cam.tween;
    tw.t += dt;
    const k = easeInOut(clamp(tw.t / tw.dur, 0, 1));
    cam.gTarget.lerpVectors(tw.from.target, tw.to.target, k);
    cam.gDist = lerp(tw.from.dist, tw.to.dist, k);
    cam.gTheta = lerp(tw.from.theta, tw.to.theta, k);
    cam.gPhi = lerp(tw.from.phi, tw.to.phi, k);
    if (tw.t >= tw.dur) cam.tween = null;
  } else {
    cam.idle += dt;
    if (CFG.drift && cam.idle > 3.0 && !dragging) {
      cam.drift = damp(cam.drift, 1, 0.7, dt);
      cam.gTheta += dt * 0.016 * cam.drift;
      cam.gPhi += Math.sin(U.time.value * 0.11) * dt * 0.006 * cam.drift;
    } else {
      cam.drift = damp(cam.drift, 0, 3, dt);
    }
  }

  cam.target.lerp(cam.gTarget, 1 - Math.exp(-6 * dt));
  cam.dist = damp(cam.dist, cam.gDist, 6, dt);
  cam.theta = damp(cam.theta, cam.gTheta, 7, dt);
  cam.phi = damp(cam.phi, cam.gPhi, 7, dt);

  const sp = Math.sin(cam.phi);
  _cp.set(
    cam.target.x + cam.dist * sp * Math.sin(cam.theta),
    cam.target.y + cam.dist * Math.cos(cam.phi),
    cam.target.z + cam.dist * sp * Math.cos(cam.theta),
  );
  /* Stay inside the room the preset is looking at. This used to clamp to the
     old single-room box, so every camera aimed at the kitchen or the bedroom
     was quietly dragged back across the flat and ended up staring at a wall —
     which looked like the light was broken and was not. */
  let rk = roomAt(cam.target.x, cam.target.z);
  // living and kitchen are one open volume, so a camera aimed at one may
  // legitimately stand in the other
  const open = rk === 'living' || rk === 'kitchen';
  const rb = open ? { x0: ROOMS.living.x0, x1: ROOMS.kitchen.x1,
                     z0: ROOMS.kitchen.z0, z1: ROOMS.living.z1 }
                  : (rk ? ROOMS[rk] : null);
  const m = 0.45;
  _cp.x = clamp(_cp.x, (rb ? rb.x0 : APT.x0) + m, (rb ? rb.x1 : APT.x1) - m);
  _cp.z = clamp(_cp.z, (rb ? rb.z0 : APT.z0) + m, (rb ? rb.z1 : APT.z1) - m);
  _cp.y = clamp(_cp.y, 0.55, APT.h - 0.35);
  // breathing, so it never feels like a locked tripod
  const t = U.time.value;
  _cp.y += Math.sin(t * 0.42) * 0.012 + Math.sin(t * 0.29 + 1.7) * 0.008;

  camera.position.copy(_cp);
  camera.lookAt(cam.target);
}

/* ============================================================== audio === */
const Audio_ = {
  ctx: null, master: null, on: false, rainGain: null, fireGain: null, nextCrackle: 0,
  init() {
    if (this.ctx) return;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    const ctx = this.ctx = new AC();
    const master = this.master = ctx.createGain();
    master.gain.value = 0;
    master.connect(ctx.destination);

    const noise = (len, brown) => {
      const buf = ctx.createBuffer(1, ctx.sampleRate * len, ctx.sampleRate);
      const d = buf.getChannelData(0);
      let last = 0;
      for (let i = 0; i < d.length; i++) {
        const w = Math.random() * 2 - 1;
        if (brown) { last = (last + 0.02 * w) / 1.02; d[i] = last * 3.5; }
        else d[i] = w;
      }
      return buf;
    };

    // rain: a wide hiss plus a lower body
    const white = noise(2.5, false), brown = noise(2.5, true);
    const rs = ctx.createBufferSource(); rs.buffer = white; rs.loop = true;
    const rlp = ctx.createBiquadFilter(); rlp.type = 'lowpass'; rlp.frequency.value = 2600;
    const rhp = ctx.createBiquadFilter(); rhp.type = 'highpass'; rhp.frequency.value = 380;
    this.rainGain = ctx.createGain(); this.rainGain.gain.value = 0.30;
    rs.connect(rhp).connect(rlp).connect(this.rainGain).connect(master);
    rs.start();

    const rs2 = ctx.createBufferSource(); rs2.buffer = brown; rs2.loop = true;
    const r2lp = ctx.createBiquadFilter(); r2lp.type = 'lowpass'; r2lp.frequency.value = 500;
    const r2g = ctx.createGain(); r2g.gain.value = 0.35;
    rs2.connect(r2lp).connect(r2g).connect(this.rainGain);
    rs2.start();

    // fire: low roar; crackles are scheduled on the fly
    const fs = ctx.createBufferSource(); fs.buffer = brown; fs.loop = true;
    const flp = ctx.createBiquadFilter(); flp.type = 'lowpass'; flp.frequency.value = 420;
    this.fireGain = ctx.createGain(); this.fireGain.gain.value = 0.22;
    fs.connect(flp).connect(this.fireGain).connect(master);
    fs.start();

    this.noiseBuf = white;
  },
  crackle() {
    const ctx = this.ctx; if (!ctx || !this.on) return;
    const s = ctx.createBufferSource(); s.buffer = this.noiseBuf;
    s.playbackRate.value = 0.6 + Math.random() * 1.4;
    const bp = ctx.createBiquadFilter(); bp.type = 'bandpass';
    bp.frequency.value = 700 + Math.random() * 2600; bp.Q.value = 3 + Math.random() * 6;
    const g = ctx.createGain();
    const t = ctx.currentTime;
    const amp = 0.06 + Math.random() * 0.16;
    g.gain.setValueAtTime(0, t);
    g.gain.linearRampToValueAtTime(amp, t + 0.004);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.06 + Math.random() * 0.14);
    s.connect(bp).connect(g).connect(this.master);
    s.start(t); s.stop(t + 0.35);
  },
  thunder(power) {
    const ctx = this.ctx; if (!ctx || !this.on) return;
    const delay = 0.6 + (1 - power) * 4.5;
    const s = ctx.createBufferSource(); s.buffer = this.noiseBuf;
    s.loop = true; s.playbackRate.value = 0.25;
    const lp = ctx.createBiquadFilter(); lp.type = 'lowpass';
    lp.frequency.value = 90 + power * 180;
    const g = ctx.createGain();
    const t = ctx.currentTime + delay;
    const dur = 1.8 + power * 2.6;
    g.gain.setValueAtTime(0, t);
    g.gain.linearRampToValueAtTime(0.10 + power * 0.42, t + 0.10 + Math.random() * 0.3);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    s.connect(lp).connect(g).connect(this.master);
    s.start(t); s.stop(t + dur + 0.2);
  },
  toggle(on) {
    this.init();
    if (!this.ctx) return false;
    if (this.ctx.state === 'suspended') this.ctx.resume();
    this.on = on;
    const t = this.ctx.currentTime;
    this.master.gain.cancelScheduledValues(t);
    this.master.gain.setValueAtTime(this.master.gain.value, t);
    this.master.gain.linearRampToValueAtTime(on ? 0.5 : 0, t + (on ? 1.6 : 0.5));
    return true;
  },
  sync() {
    if (!this.ctx || !this.on) return;
    const t = this.ctx.currentTime;
    this.rainGain.gain.setTargetAtTime(0.06 + CFG.rain * 0.30, t, 0.4);
    this.fireGain.gain.setTargetAtTime(CFG.fire * 0.22, t, 0.4);
  },
};

/* ================================================================= UI === */
const fpsOut = document.getElementById('oFps');
const qualOut = document.getElementById('oQual');

/** once the user opens the settings, nothing may move on its own again */
function userTookControl() {
  if (!autoQuality) return;
  autoQuality = false;
  if (qualOut) qualOut.textContent = ['низкое', 'среднее', 'высокое'][CFG.quality];
}

function wireUI() {
  document.querySelectorAll('#dock button[data-view]').forEach((b) => {
    b.addEventListener('click', () => { if (walker.active) setWalking(false); gotoView(+b.dataset.view); });
  });
  document.getElementById('btnWalk')?.addEventListener('click', () => setWalking(!walker.active));
  // clicking the scene while walking re-acquires pointer lock after an Escape
  canvas.addEventListener('click', () => {
    if (walker.active && document.pointerLockElement !== canvas) canvas.requestPointerLock?.();
  });

  const sheet = document.getElementById('sheet');
  const btnSheet = document.getElementById('btnSheet');
  btnSheet?.addEventListener('click', () => {
    const open = sheet.classList.toggle('open');
    btnSheet.setAttribute('aria-pressed', String(open));
    if (open) userTookControl();
  });

  const btnLamps = document.getElementById('btnLamps');
  btnLamps?.addEventListener('click', () => {
    CFG.lamps = !CFG.lamps;
    btnLamps.setAttribute('aria-pressed', String(CFG.lamps));
  });

  const btnSound = document.getElementById('btnSound');
  btnSound?.addEventListener('click', () => {
    const want = !Audio_.on;
    if (Audio_.toggle(want)) btnSound.setAttribute('aria-pressed', String(want));
  });

  const bind = (id, out, fmt, apply) => {
    const el = document.getElementById(id), o = document.getElementById(out);
    if (!el) return;
    const run = () => { const v = +el.value / 100; apply(v); if (o) o.textContent = fmt(v); };
    el.addEventListener('pointerdown', userTookControl);
    el.addEventListener('input', run); run();
  };
  bind('sFire', 'oFire', (v) => Math.round(v * 100) + '%', (v) => { CFG.fire = v; });
  bind('sRain', 'oRain', (v) => Math.round(v * 100) + '%', (v) => { CFG.rain = v; U.rain.value = v; });
  bind('sDof', 'oDof', (v) => Math.round(v * 100) + '%', (v) => { if (dof) dof.aperture = v; });
  bind('sWarm', 'oWarm', (v) => Math.round(v * 100) + '%', (v) => {
    CFG.warm = v;
    if (post) post.composite.uniforms.uWarm.value = v;
  });

  // Resolution is the only setting that reallocates buffers, so it is applied
  // when the user lets go of the slider, never on every pixel of the drag.
  const sRes = document.getElementById('sRes'), oRes = document.getElementById('oRes');
  if (sRes) {
    sRes.value = String(Math.round(CFG.res * 100));
    const label = () => { if (oRes) oRes.textContent = sRes.value + '%'; };
    label();
    sRes.addEventListener('pointerdown', userTookControl);
    sRes.addEventListener('input', label);
    sRes.addEventListener('change', () => { CFG.res = clamp(+sRes.value / 100, 0.4, 1); onResize(); });
  }

  document.querySelectorAll('#segQual button').forEach((b) => {
    b.addEventListener('click', () => { userTookControl(); applyQuality(+b.dataset.q); });
  });
  document.querySelectorAll('#segDrift button').forEach((b) => {
    b.addEventListener('click', () => {
      CFG.drift = b.dataset.drift === '1';
      document.querySelectorAll('#segDrift button').forEach((x) => {
        x.setAttribute('aria-pressed', String((x.dataset.drift === '1') === CFG.drift));
      });
    });
  });
}

/* scene clock — a slow evening */
const clockEl = document.getElementById('clock');
let sceneMinutes = 21 * 60 + 40;
function updateClock(dt) {
  sceneMinutes += dt / 20;
  const m = Math.floor(sceneMinutes) % 1440;
  if (clockEl) clockEl.textContent = `${String((m / 60) | 0).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
}

/* ============================================================== loop ==== */
const clock = new THREE.Clock();
let flickA = 1, flickB = 1, lampLevel = 1, audioSyncAt = 0;
let frames = 0, fpsMark = 0, autoQuality = true, qCooldown = 5, badWin = 0, goodWin = 0;
const _v3 = new THREE.Vector3();
let lastFps = 0, frameNo = 0;

function tick() {
  requestAnimationFrame(tick);
  if (document.hidden) { clock.getDelta(); return; }
  const dt = Math.min(clock.getDelta(), 0.05);
  U.time.value += dt;
  frameNo++;
  updateClock(dt);


  /* --- firelight flicker: two smoothed random walks at different rates --- */
  flickA = damp(flickA, 0.55 + rnd() * 0.9, 9, dt);
  flickB = damp(flickB, 0.75 + rnd() * 0.5, 2.4, dt);
  const t = U.time.value;
  const flick = clamp(
    (flickA * 0.45 + flickB * 0.55) * (0.94 + 0.06 * Math.sin(t * 8.3) + 0.04 * Math.sin(t * 19.7 + 1.1)),
    0.35, 1.6,
  );
  U.flicker.value = flick;
  U.fire.value = CFG.fire;
  U.rain.value = CFG.rain;

  lightning.update(dt);

  /* --- lights --- */
  const f = CFG.fire;
  lampLevel = damp(lampLevel, CFG.lamps ? 1 : 0, 5, dt);
  updateLights(lights, {
    fire: f, flick, lampLevel, flash: U.flash.value, t, firePos: fire.firePos,
  });
  props.lamp.setGlow(lampLevel);
  props.chand.setGlow(lampLevel);

  /* --- billboards: flames turn toward the camera but stay in the firebox --- */
  fire.flames.children.forEach((m) => {
    m.getWorldPosition(_v3);
    const want = Math.atan2(camera.position.x - _v3.x, camera.position.z - _v3.z);
    const base = Math.PI / 2;
    let d = want - base;
    while (d > Math.PI) d -= Math.PI * 2;
    while (d < -Math.PI) d += Math.PI * 2;
    m.rotation.y = damp(m.rotation.y, base + clamp(d, -0.55, 0.55), 6, dt);
  });
  props.table.steam.lookAt(camera.position.x, props.table.steam.getWorldPosition(_v3).y, camera.position.z);

  /* --- soft life: breathing cat, swaying leaves --- */
  if (props.cat.userData.breathe) {
    const b = props.cat.userData.breathe;
    b.scale.y = 0.78 + Math.sin(t * 1.15) * 0.014;
  }
  props.plant.children.forEach((c) => {
    if (c.userData.sway) c.rotation.z = c.userData.sway.base + Math.sin(t * 0.6 + c.userData.sway.ph) * c.userData.sway.amp;
  });

  if (t > audioSyncAt) { audioSyncAt = t + 0.25; Audio_.sync(); }
  if (Audio_.on && t > Audio_.nextCrackle) {
    Audio_.nextCrackle = t + rrnd(0.12, 1.5) / Math.max(0.15, CFG.fire);
    Audio_.crackle();
  }

  // tools/skyline.js parks the camera to inspect the world on its own
  const lock = window.__room && window.__room.__lockCamera;
  if (lock) {
    camera.position.set(lock.eye[0], lock.eye[1], lock.eye[2]);
    camera.lookAt(lock.tgt[0], lock.tgt[1], lock.tgt[2]);
  } else if (!walker.update(dt)) updateCamera(dt);

  /* ========================= render ========================= */
  const Q = QUALITY[CFG.quality];

  // 1 — the world outside
  renderer.setClearColor(0x05070a, 1);
  renderer.setRenderTarget(rtOut);
  renderer.clear();
  renderer.render(outsideScene, camera);

  // 2 — a blurred copy for the fogged parts of the glass
  outBlurDown.uniforms.tDiffuse.value = rtOut.texture;
  outBlurDown.uniforms.uTexel.value.set(1 / rtOut.width, 1 / rtOut.height);
  blit(outBlurDown, rtOutSmall);
  outBlurUp.uniforms.tDiffuse.value = rtOutSmall.texture;
  outBlurUp.uniforms.uTexel.value.set(1 / rtOutSmall.width, 1 / rtOutSmall.height);
  blit(outBlurUp, rtOutBlur);

  for (const m of glassMaterials) {
    m.uniforms.tBack.value = rtOut.texture;
    m.uniforms.tBackBlur.value = rtOutBlur.texture;
  }

  // 3 — planar reflections
  if (Q.refl >= 2) {
    // one reflector per glazed plane, and each pane takes the matching one
    winReflS.update(camera, roomScene, windows.panes);
    winReflE.update(camera, roomScene, windows.panes);
    for (const m of glassMaterials) {
      const r = m.userData.plane === 'east' ? winReflE : winReflS;
      m.uniforms.tRefl.value = r.target.texture;
      m.uniforms.uReflMat.value.copy(r.matrix);
    }
  }
  if (Q.refl >= 1) {
    floorRefl.update(camera, roomScene, shell.floors);
    for (const f of reflectiveFloors) {
      f.uniforms.tRefl.value = floorRefl.target.texture;
      f.uniforms.uReflMat.value.copy(floorRefl.matrix);
    }
  }

  // 4 — the room itself
  setGlassRes(W, Hh);
  renderer.setRenderTarget(post.hdr);
  renderer.clear();
  renderer.render(roomScene, camera);

  // 5 — ambient occlusion from the room pass's depth buffer
  ao.render(post.hdr.depthTexture, camera);
  post.composite.uniforms.tAO.value = ao.texture;
  post.composite.uniforms.uAO.value = ao.strength;

  // 6 — defocus before bloom, so out-of-focus highlights bloom as discs
  const focus = walker.active ? walker.focusDistance() : camera.position.distanceTo(cam.target);
  const graded = dof.render(post.hdr.texture, post.hdr.depthTexture, camera, focus);

  // 7 — bloom + grade
  post.render(Q.bloom, graded);
  renderer.setRenderTarget(null);

  /* --------------------- adaptive quality --------------------- */
  frames++;
  const now = performance.now();
  if (!fpsMark) fpsMark = now;
  const span = (now - fpsMark) / 1000;
  qCooldown -= dt;
  if (span >= 1.5) {
    const fps = frames / span;
    lastFps = fps;
    // A single slow window is not evidence — a shader compile or a GC pause
    // looks identical. Requiring consecutive windows, with a wide dead zone
    // and a long cooldown after stepping up, stops the tier oscillating (and
    // every oscillation used to cost a full render-target reallocation).
    if (autoQuality && qCooldown <= 0) {
      if (fps < 30) { badWin++; goodWin = 0; }
      else if (fps > 55) { goodWin++; badWin = 0; }
      else { badWin = 0; goodWin = 0; }
      if (badWin >= 2 && CFG.quality > 0) { applyQuality(CFG.quality - 1); qCooldown = 8; badWin = 0; }
      else if (goodWin >= 4 && CFG.quality < 2) { applyQuality(CFG.quality + 1); qCooldown = 20; goodWin = 0; }
    }
    if (fpsOut) fpsOut.textContent = `${Math.round(fps)} fps`;
    if (qualOut) qualOut.textContent = ['низкое', 'среднее', 'высокое'][CFG.quality] + (autoQuality ? ' · авто' : '');
    frames = 0; fpsMark = now;
  }
}

/* ============================================================== start === */
(async function start() {
  wireUI();
  await build();

  lightning.onStrike = (power, far) => {
    Audio_.thunder(far ? power * 0.5 : power);
  };

  // pick a sensible starting tier before the adaptive loop takes over
  const cores = navigator.hardwareConcurrency || 4;
  const mobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
  CFG.res = mobile ? 0.6 : cores >= 8 ? 1.0 : 0.8;
  const sRes = document.getElementById('sRes'), oRes = document.getElementById('oRes');
  if (sRes) { sRes.value = String(Math.round(CFG.res * 100)); if (oRes) oRes.textContent = sRes.value + '%'; }
  onResize();
  applyQuality(mobile ? 0 : cores >= 8 ? 2 : 1);

  gotoView(0);
  cam.tween = null;
  cam.target.copy(VIEWS[0].target); cam.gTarget.copy(VIEWS[0].target);
  cam.dist = cam.gDist = VIEWS[0].dist;
  cam.theta = cam.gTheta = VIEWS[0].theta;
  cam.phi = cam.gPhi = VIEWS[0].phi;

  // debug handle: window.__room.snapView(0..3), .stats(), .CFG, .lights …
  window.__room = {
    camera, cam, CFG, QUALITY, VIEWS, lights, post, U, snapView, renderer,
    roomScene, outsideScene, shell, windows, fire, props, outside, THREE,
    glassMaterials, reflectiveFloors, ao, dof, walker, setWalking,
    texStats,
    stats: () => ({ frame: frameNo, fps: lastFps, q: CFG.quality, commitMs: Math.round(lastCommitMs) }),
  };
  tick();
})();
