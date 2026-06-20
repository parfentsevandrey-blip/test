/* =========================================================
   cinemagraph.js
   Turns the still courtyard photograph of «Кутузовский 12»
   into a looping, generative cinematic "video" — entirely in
   real-time WebGL via Three.js. There is no <video> file: every
   frame is synthesised on the GPU from the single photo.

   The image is treated as a 2.5D scene. A procedural depth map
   (derived from the composition + foliage detection) drives:
     · a slow breathing camera dolly + drift (loops seamlessly)
     · depth parallax (near foliage and far colonnade separate)
     · mouse "look-around" parallax
     · wind that sways only the trees/bushes (domain-warp)
     · flickering golden-hour glow on the lit colonnade windows
     · a slow sun-warmth cycle and a moving light shaft
     · drifting atmospheric haze
   On top, a particle field of glowing dust motes adds air, and
   a film grade (contrast, vignette, chromatic aberration, grain)
   finishes the cinematic look.
   ========================================================= */
import * as THREE from "three";

const canvas = document.getElementById("cg-canvas");
const srcImg = document.getElementById("cg-src");
const imageURL =
  (srcImg && (srcImg.currentSrc || srcImg.getAttribute("src"))) ||
  "img/k12-courtyard.jpg";

const reducedMotion =
  window.matchMedia &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/* ---------- renderer ---------- */
let renderer;
try {
  renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: false,
    powerPreference: "high-performance",
  });
} catch (e) {
  // No WebGL — reveal the plain <img> fallback and bail out.
  document.body.classList.add("cg-nowebgl");
  document.body.classList.add("cg-ready");
  console.warn("[cinemagraph] WebGL unavailable:", e);
  throw e;
}
const DPR_CAP = 2;
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, DPR_CAP));
renderer.setClearColor(0x05070b, 1);

const scene = new THREE.Scene();
const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);

/* ---------- shared uniforms ---------- */
const uniforms = {
  uTex: { value: null },
  uTime: { value: 0 },
  uMouse: { value: new THREE.Vector2(0, 0) },
  uImgAspect: { value: 16 / 9 },
  uScrAspect: { value: 16 / 9 },
  uResolution: { value: new THREE.Vector2(1, 1) },
  uIntro: { value: 0 },
  uMotion: { value: reducedMotion ? 0.0 : 1.0 },
};

// 1×1 placeholder so the sampler is always valid before the photo loads
const placeholder = new THREE.DataTexture(
  new Uint8Array([8, 10, 14, 255]),
  1,
  1,
  THREE.RGBAFormat
);
placeholder.needsUpdate = true;
uniforms.uTex.value = placeholder;

/* =========================================================
   Full-screen image quad — the "living photo" shader
   ========================================================= */
const quadVert = /* glsl */ `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 0.0, 1.0);
  }
`;

const quadFrag = /* glsl */ `
  precision highp float;
  varying vec2 vUv;

  uniform sampler2D uTex;
  uniform float uTime;
  uniform vec2  uMouse;
  uniform float uImgAspect;
  uniform float uScrAspect;
  uniform vec2  uResolution;
  uniform float uIntro;
  uniform float uMotion;

  // ---- value noise + fbm ----
  float hash(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
  }
  float vnoise(vec2 p){
    vec2 i = floor(p), f = fract(p);
    float a = hash(i), b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0)), d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
  }
  float fbm(vec2 p){
    float s = 0.0, a = 0.5;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 5; i++){ s += a * vnoise(p); p = m * p; a *= 0.5; }
    return s;
  }
  float luma(vec3 c){ return dot(c, vec3(0.299, 0.587, 0.114)); }

  void main(){
    float t = uTime;
    float m = uMotion;
    vec2 uv = vUv;
    vec2 q  = uv - 0.5;

    // ---- cover-fit the photo to the viewport (no stretch) ----
    vec2 fit = (uScrAspect > uImgAspect)
      ? vec2(1.0, uImgAspect / uScrAspect)
      : vec2(uScrAspect / uImgAspect, 1.0);

    // ---- breathing dolly + gentle drift (looping, eased by intro) ----
    float zoom  = (1.18 + 0.04 * sin(t * 0.05) * m) * mix(1.06, 1.0, uIntro);
    vec2  drift = vec2(sin(t * 0.043), sin(t * 0.031 + 1.7)) * 0.010 * m;

    vec2 sampUv = q * (fit / zoom) + 0.5 + drift + uMouse * 0.012 * m;

    // ---- procedural depth (0 near .. 1 far) from composition ----
    vec3 ref = texture2D(uTex, sampUv).rgb;
    float depth = clamp(0.12 + sampUv.y * 0.62 + (sampUv.x - 0.5) * 0.22, 0.0, 1.0);
    // foliage detection: green-dominant pixels
    float green   = ref.g - max(ref.r, ref.b);
    float foliage = smoothstep(0.02, 0.16, green);
    // foreground bushes sit low/left → pull them nearer
    float fg = foliage * smoothstep(0.78, 0.12, sampUv.y);
    depth = mix(depth, depth * 0.30, fg);

    // ---- depth parallax (auto + mouse look-around) ----
    float focus = 0.40;
    vec2 look = (uMouse + vec2(sin(t * 0.05), sin(t * 0.063 + 1.0)) * 0.55) * m;
    vec2 parallax = look * (depth - focus) * 0.045;
    vec2 finalUv = sampUv + parallax;

    // ---- wind: domain-warp that sways ONLY the foliage ----
    vec2 wq = sampUv * vec2(uScrAspect, 1.0) * 7.0;
    vec2 wind = vec2(
      fbm(wq + vec2(t * 0.25,  t * 0.18)),
      fbm(wq + vec2(13.1 - t * 0.21, 5.7))
    ) - 0.5;
    finalUv += wind * 0.010 * fg * m;

    finalUv = clamp(finalUv, vec2(0.0015), vec2(0.9985));

    // ---- sample with subtle lens chromatic aberration ----
    float caAmt = 0.0014 + 0.004 * dot(q, q);
    vec2 dir = (length(q) > 1e-4) ? normalize(q) : vec2(0.0);
    vec3 col;
    col.r = texture2D(uTex, clamp(finalUv + dir * caAmt, 0.0015, 0.9985)).r;
    col.g = texture2D(uTex, finalUv).g;
    col.b = texture2D(uTex, clamp(finalUv - dir * caAmt, 0.0015, 0.9985)).b;

    float L = luma(col);

    // ---- golden-hour glow: warm, bright areas (lit colonnade/lobby) ----
    float warm  = smoothstep(0.04, 0.35, col.r - col.b) * smoothstep(0.22, 0.85, L);
    float flick = 0.85 + 0.15 * sin(t * 2.3 + finalUv.y * 40.0 + sin(t * 1.1) * 2.0);
    float pulse = 0.9 + 0.1 * sin(t * 0.5);
    col += warm * vec3(0.55, 0.32, 0.08) * (0.35 * flick * pulse) * (0.55 + 0.45 * m);

    // ---- slow sun-warmth cycle (golden hour breathing) ----
    float warmth = 0.5 + 0.5 * sin(t * 0.04);
    col *= mix(vec3(0.97, 0.98, 1.03), vec3(1.06, 1.0, 0.93), warmth * 0.6);

    // ---- moving light shaft sweeping across the facade ----
    float sweep = sin((finalUv.x * 0.8 + finalUv.y * 0.6) * 3.0 - t * 0.15);
    sweep = smoothstep(0.6, 1.0, sweep);
    col += sweep * vec3(1.0, 0.85, 0.6) * 0.045 * (0.5 + 0.5 * m);

    // ---- drifting atmospheric haze (mid-band, behind colonnade) ----
    float haze = fbm(sampUv * vec2(uScrAspect, 1.0) * 3.0 + vec2(t * 0.05, t * 0.02));
    float hazeBand = smoothstep(0.12, 0.55, sampUv.y) * smoothstep(0.98, 0.5, sampUv.y);
    col += vec3(0.16, 0.13, 0.10) * haze * 0.11 * hazeBand * (0.5 + 0.5 * m);

    // ---- film grade ----
    col = (col - 0.5) * 1.07 + 0.5;            // contrast
    float lum = luma(col);
    col = mix(vec3(lum), col, 1.12);           // saturation
    col = pow(max(col, 0.0), vec3(0.97));       // slight lift

    // vignette
    float vig = clamp(1.0 - dot(q, q) * 0.95, 0.0, 1.0);
    col *= mix(0.74, 1.0, vig);

    // animated film grain
    float grain = hash(vUv * uResolution + fract(t) * vec2(91.7, 73.3));
    col += (grain - 0.5) * 0.045;

    // intro reveal (fade up from black)
    col *= smoothstep(0.0, 1.0, uIntro);

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
  }
`;

const quad = new THREE.Mesh(
  new THREE.PlaneGeometry(2, 2),
  new THREE.ShaderMaterial({
    uniforms,
    vertexShader: quadVert,
    fragmentShader: quadFrag,
    depthTest: false,
    depthWrite: false,
    toneMapped: false,
  })
);
quad.frustumCulled = false;
quad.renderOrder = 0;
scene.add(quad);

/* =========================================================
   Particle field — glowing dust motes catching the light
   ========================================================= */
const COUNT = reducedMotion ? 90 : 360;
const pPos = new Float32Array(COUNT * 3);
const pSize = new Float32Array(COUNT);
const pDepth = new Float32Array(COUNT);
const pSeed = new Float32Array(COUNT * 3);
for (let i = 0; i < COUNT; i++) {
  pPos[i * 3 + 0] = Math.random() * 2.4 - 1.2;
  pPos[i * 3 + 1] = Math.random() * 2.4 - 1.2;
  pPos[i * 3 + 2] = 0;
  pSize[i] = 1.5 + Math.random() * 5.5;
  pDepth[i] = Math.random();
  pSeed[i * 3 + 0] = Math.random();
  pSeed[i * 3 + 1] = Math.random();
  pSeed[i * 3 + 2] = Math.random();
}
const pGeo = new THREE.BufferGeometry();
pGeo.setAttribute("position", new THREE.BufferAttribute(pPos, 3));
pGeo.setAttribute("aSize", new THREE.BufferAttribute(pSize, 1));
pGeo.setAttribute("aDepth", new THREE.BufferAttribute(pDepth, 1));
pGeo.setAttribute("aSeed", new THREE.BufferAttribute(pSeed, 3));

const pMat = new THREE.ShaderMaterial({
  uniforms: {
    uTime: uniforms.uTime,
    uMouse: uniforms.uMouse,
    uMotion: uniforms.uMotion,
    uIntro: uniforms.uIntro,
    uDpr: { value: renderer.getPixelRatio() },
  },
  vertexShader: /* glsl */ `
    attribute float aSize;
    attribute float aDepth;
    attribute vec3  aSeed;
    uniform float uTime;
    uniform vec2  uMouse;
    uniform float uMotion;
    uniform float uDpr;
    varying float vTw;
    void main(){
      float t = uTime;
      vec2 p = position.xy;
      float spd = mix(0.02, 0.06, aDepth);
      p.x += sin(t * spd + aSeed.x * 6.2831) * 0.06 + t * 0.012 * spd * uMotion;
      p.y += cos(t * spd * 0.8 + aSeed.y * 6.2831) * 0.05 + t * 0.010 * (0.4 + aDepth) * uMotion;
      p = fract((p + 1.2) / 2.4) * 2.4 - 1.2;      // wrap
      p += uMouse * (0.02 + aDepth * 0.06) * uMotion;
      gl_Position = vec4(p, 0.0, 1.0);
      gl_PointSize = aSize * mix(0.5, 1.7, aDepth) * uDpr;
      vTw = 0.5 + 0.5 * sin(t * (1.0 + aSeed.z * 3.0) + aSeed.x * 10.0);
    }
  `,
  fragmentShader: /* glsl */ `
    precision mediump float;
    uniform float uIntro;
    varying float vTw;
    void main(){
      vec2 c = gl_PointCoord - 0.5;
      float d = length(c);
      float a = smoothstep(0.5, 0.0, d);
      a *= a;
      a *= (0.2 + 0.8 * vTw) * uIntro;
      vec3 col = mix(vec3(1.0, 0.93, 0.78), vec3(1.0, 0.78, 0.5), 0.4);
      gl_FragColor = vec4(col, a * 0.5);
    }
  `,
  transparent: true,
  depthTest: false,
  depthWrite: false,
  blending: THREE.AdditiveBlending,
  toneMapped: false,
});
const points = new THREE.Points(pGeo, pMat);
points.frustumCulled = false;
points.renderOrder = 1;
scene.add(points);

/* =========================================================
   Texture load + sizing
   ========================================================= */
function resize() {
  const w = window.innerWidth;
  const h = window.innerHeight;
  const dpr = Math.min(window.devicePixelRatio || 1, DPR_CAP);
  renderer.setPixelRatio(dpr);
  renderer.setSize(w, h, false);
  uniforms.uResolution.value.set(w * dpr, h * dpr);
  uniforms.uScrAspect.value = w / h;
  pMat.uniforms.uDpr.value = dpr;
}
window.addEventListener("resize", resize);
resize();

new THREE.TextureLoader().load(
  imageURL,
  (tex) => {
    // Sample raw sRGB bytes (no GPU linearisation); we output verbatim
    // after grading, so on-screen colour matches the source photo.
    tex.colorSpace = THREE.NoColorSpace;
    tex.minFilter = THREE.LinearMipmapLinearFilter;
    tex.magFilter = THREE.LinearFilter;
    tex.wrapS = THREE.ClampToEdgeWrapping;
    tex.wrapT = THREE.ClampToEdgeWrapping;
    tex.generateMipmaps = true;
    tex.anisotropy = renderer.capabilities.getMaxAnisotropy();
    tex.needsUpdate = true;
    uniforms.uTex.value = tex;
    if (tex.image && tex.image.width) {
      uniforms.uImgAspect.value = tex.image.width / tex.image.height;
    }
    started = true;
    document.body.classList.add("cg-ready");
  },
  undefined,
  (err) => {
    document.body.classList.add("cg-nowebgl");
    document.body.classList.add("cg-ready");
    console.warn("[cinemagraph] texture failed to load:", err);
  }
);

/* ---------- interaction ---------- */
const targetMouse = new THREE.Vector2(0, 0);
function onPointer(x, y) {
  targetMouse.set((x / window.innerWidth) * 2 - 1, -((y / window.innerHeight) * 2 - 1));
}
window.addEventListener("pointermove", (e) => onPointer(e.clientX, e.clientY), { passive: true });
window.addEventListener(
  "touchmove",
  (e) => {
    if (e.touches[0]) onPointer(e.touches[0].clientX, e.touches[0].clientY);
  },
  { passive: true }
);

/* ---------- timecode overlay (it's a "video", after all) ---------- */
const tcEl = document.getElementById("cg-timecode");
function updateTimecode(elapsed) {
  if (!tcEl) return;
  const fps = 24;
  const total = Math.floor(elapsed * fps);
  const ff = total % fps;
  const s = Math.floor(elapsed) % 60;
  const mm = Math.floor(elapsed / 60) % 60;
  const hh = Math.floor(elapsed / 3600);
  const p = (n) => String(n).padStart(2, "0");
  tcEl.textContent = `${p(hh)}:${p(mm)}:${p(s)}:${p(ff)}`;
}

/* ---------- render loop ---------- */
let started = false;
let visible = true;
let startTime = 0;
const clock = new THREE.Clock();

document.addEventListener("visibilitychange", () => {
  visible = !document.hidden;
  if (visible) clock.getDelta(); // drop the paused interval
});

function tick() {
  requestAnimationFrame(tick);
  if (!visible) return;
  const dt = Math.min(clock.getDelta(), 0.05);

  if (started) {
    if (startTime === 0) startTime = performance.now();
    uniforms.uTime.value += dt;
    // ease mouse toward target
    uniforms.uMouse.value.x += (targetMouse.x - uniforms.uMouse.value.x) * 0.05;
    uniforms.uMouse.value.y += (targetMouse.y - uniforms.uMouse.value.y) * 0.05;
    // intro reveal over ~1.9s
    const introT = Math.min((performance.now() - startTime) / 1900, 1);
    uniforms.uIntro.value = introT * introT * (3 - 2 * introT); // smoothstep
    updateTimecode((performance.now() - startTime) / 1000);
  }

  renderer.render(scene, camera);
}
tick();
