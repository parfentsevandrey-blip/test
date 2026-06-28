/* ============================================================================
   PROMPTFIELD — the procedural WebGL stage
   "Type a world into being."

   A scroll is a single instrument. One eased progress s∈[0,1] (+ smoothed
   velocity) drives EVERYTHING: a 120k-particle organism that morphs sphere →
   galaxy → torus → lattice, slams into a legible 3D word, ignites, then
   disperses into a born cosmos — over an iridescent aurora, through a light
   tunnel, graded by bloom + a cinematic post chain. No models, no images:
   the one point-sprite and the one glyph are drawn to a canvas at runtime.

   Built entirely from vendored Three.js r160 — fully offline.
   ========================================================================== */

import * as THREE from "three";
import { EffectComposer } from "three/addons/postprocessing/EffectComposer.js";
import { RenderPass } from "three/addons/postprocessing/RenderPass.js";
import { UnrealBloomPass } from "three/addons/postprocessing/UnrealBloomPass.js";
import { ShaderPass } from "three/addons/postprocessing/ShaderPass.js";
import { OutputPass } from "three/addons/postprocessing/OutputPass.js";
import { SMAAPass } from "three/addons/postprocessing/SMAAPass.js";
import { SimplexNoise } from "three/addons/math/SimplexNoise.js";

/* ---- shared state (also owned/written by js/ui.js) ------------------------ */
const PF = (window.PF = window.PF || {});
if (PF.progress == null) PF.progress = 0;
if (PF.velocity == null) PF.velocity = 0;
if (PF.mouse == null) PF.mouse = { x: 0, y: 0 };
if (PF.reduceMotion == null)
  PF.reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/* ---- tiny math ------------------------------------------------------------ */
const TAU = Math.PI * 2;
const clamp01 = (t) => (t < 0 ? 0 : t > 1 ? 1 : t);
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (t) => { t = clamp01(t); return t * t * t * (t * (t * 6 - 15) + 10); };
const _simplex = new SimplexNoise();

/* keyframe samplers: keys = [[s, v], ...] sorted by s ----------------------- */
function sampleScalar(keys, s) {
  if (s <= keys[0][0]) return keys[0][1];
  const n = keys.length;
  if (s >= keys[n - 1][0]) return keys[n - 1][1];
  for (let i = 0; i < n - 1; i++) {
    const a = keys[i], b = keys[i + 1];
    if (s >= a[0] && s <= b[0]) return lerp(a[1], b[1], smooth((s - a[0]) / (b[0] - a[0])));
  }
  return keys[n - 1][1];
}
function sampleVec3(keys, s, out) {
  let a = keys[0], b = keys[0];
  if (s <= keys[0][0]) { out.set(keys[0][1], keys[0][2], keys[0][3]); return out; }
  const n = keys.length;
  if (s >= keys[n - 1][0]) { const k = keys[n - 1]; out.set(k[1], k[2], k[3]); return out; }
  for (let i = 0; i < n - 1; i++) {
    if (s >= keys[i][0] && s <= keys[i + 1][0]) { a = keys[i]; b = keys[i + 1]; break; }
  }
  const t = smooth((s - a[0]) / (b[0] - a[0]));
  out.set(lerp(a[1], b[1], t), lerp(a[2], b[2], t), lerp(a[3], b[3], t));
  return out;
}

/* ============================================================================
   SHADERS
   ========================================================================== */
const BG_VERT = /* glsl */ `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 0.0, 1.0); // fullscreen clip-space quad
  }`;

const BG_FRAG = /* glsl */ `
  precision highp float;
  varying vec2 vUv;
  uniform float uTime;
  uniform vec2  uRes;
  uniform vec2  uMouse;
  uniform float uScroll;
  uniform float uVelocity;

  const vec3 VOID    = vec3(0.020, 0.024, 0.039);
  const vec3 INDIGO  = vec3(0.043, 0.063, 0.149);
  const vec3 VIOLET  = vec3(0.424, 0.361, 0.906);
  const vec3 CYAN    = vec3(0.129, 0.902, 0.757);
  const vec3 MAGENTA = vec3(1.000, 0.302, 0.616);
  const vec3 GOLD    = vec3(1.000, 0.722, 0.361);
  const vec3 STAR    = vec3(0.918, 0.941, 1.000);

  vec3 iridescence(float t){ t = fract(t); return 0.5 + 0.5*cos(6.28318*(t+vec3(0.0,0.33,0.67))); }

  vec3 mod289(vec3 x){ return x - floor(x*(1.0/289.0))*289.0; }
  vec2 mod289(vec2 x){ return x - floor(x*(1.0/289.0))*289.0; }
  vec3 permute(vec3 x){ return mod289(((x*34.0)+1.0)*x); }
  float snoise(vec2 v){
    const vec4 C = vec4(0.211324865405187,0.366025403784439,-0.577350269189626,0.024390243902439);
    vec2 i=floor(v+dot(v,C.yy)); vec2 x0=v-i+dot(i,C.xx);
    vec2 i1=(x0.x>x0.y)?vec2(1.0,0.0):vec2(0.0,1.0);
    vec4 x12=x0.xyxy+C.xxzz; x12.xy-=i1; i=mod289(i);
    vec3 p=permute(permute(i.y+vec3(0.0,i1.y,1.0))+i.x+vec3(0.0,i1.x,1.0));
    vec3 m=max(0.5-vec3(dot(x0,x0),dot(x12.xy,x12.xy),dot(x12.zw,x12.zw)),0.0);
    m=m*m; m=m*m;
    vec3 x=2.0*fract(p*C.www)-1.0; vec3 h=abs(x)-0.5; vec3 ox=floor(x+0.5); vec3 a0=x-ox;
    m*=1.79284291400159-0.85373472095314*(a0*a0+h*h);
    vec3 g; g.x=a0.x*x0.x+h.x*x0.y; g.yz=a0.yz*x12.xz+h.yz*x12.yw;
    return 130.0*dot(m,g);
  }
  float fbm(vec2 p){
    float f=0.0, a=0.5; mat2 rot=mat2(0.80,-0.60,0.60,0.80);
    for(int i=0;i<3;i++){ f+=a*snoise(p); p=rot*p*2.0+11.3; a*=0.5; }
    return f;
  }

  void main(){
    vec2 uv=vUv; vec2 p=(uv-0.5); float asp=uRes.x/max(uRes.y,1.0); p.x*=asp;
    float t=uTime*0.06;
    vec2 mouse=uMouse*0.30;
    vec2 q; q.x=fbm(p*0.85+vec2(0.0,t)+mouse); q.y=fbm(p*0.85+vec2(5.2,t*0.9)-mouse);
    vec2 r; r.x=fbm(p*1.05+1.4*q+vec2(1.7,9.2)-t*0.7); r.y=fbm(p*1.05+1.4*q+vec2(8.3,2.8)+t*0.5);
    float flow=fbm(p*0.95+1.7*r); flow=flow*0.5+0.5;

    float thickness=flow*1.6+length(r)*0.45+uScroll*0.6+t*0.25;
    vec3 iri=iridescence(thickness);

    float s=clamp(uScroll,0.0,1.0);
    vec3 base=mix(VOID,INDIGO,smoothstep(0.15,0.85,flow));
    vec3 coolAccent=mix(VIOLET,CYAN,smoothstep(0.10,0.46,s));
    vec3 hotAccent=mix(CYAN,MAGENTA,smoothstep(0.46,0.62,s));
    hotAccent=mix(hotAccent,GOLD,smoothstep(0.66,0.80,s));
    float uHeat=smoothstep(0.68,0.74,s);
    vec3 accent=mix(coolAccent,hotAccent,smoothstep(0.40,0.66,s));
    vec3 settle=mix(GOLD,STAR,0.35);
    accent=mix(accent,settle,smoothstep(0.82,1.00,s));

    float ribbon=smoothstep(0.45,0.72,flow)-smoothstep(0.72,0.95,flow); ribbon=max(ribbon,0.0);
    vec3 col=base;
    vec3 sheen=mix(accent,iri,0.5);
    col=mix(col,sheen,ribbon*(0.32+0.30*flow));
    col+=coolAccent*smoothstep(0.0,0.35,flow)*0.05*(1.0-uHeat);

    float heatWave=smoothstep(0.0,1.0,flow+r.x*0.5);
    vec3 ignite=mix(MAGENTA,GOLD,heatWave);
    col=mix(col,ignite,uHeat*(0.35+0.4*ribbon));
    col+=ignite*uHeat*0.25*smoothstep(0.5,1.0,flow);

    float starField=smoothstep(0.86,1.0,s);
    float spark=snoise(p*90.0+7.0); spark=smoothstep(0.92,1.0,spark);
    col+=STAR*spark*starField*(0.6+0.4*sin(uTime*2.0+p.x*40.0));

    float breath=0.5+0.5*sin(uTime*0.7);
    col+=coolAccent*breath*0.03*(1.0-smoothstep(0.0,0.30,s));

    float vig=smoothstep(1.25,0.30,length(uv-0.5)*1.6); col*=mix(0.55,1.0,vig);
    col+=accent*0.04*smoothstep(0.6,0.0,uv.y)*(1.0-uHeat*0.5);
    col+=iri*uVelocity*0.12*ribbon;

    float grain=fract(sin(dot(uv*uRes,vec2(12.9898,78.233)))*43758.5453); col+=(grain-0.5)*0.02;
    col*=0.45; col=clamp(col,0.0,1.05);
    gl_FragColor=vec4(col,1.0);
  }`;

const PARTICLE_VERT = /* glsl */ `
  precision highp float;
  attribute vec3 aTarget0; attribute vec3 aTarget1; attribute vec3 aTarget2;
  attribute vec3 aTarget3; attribute vec3 aTarget4; attribute vec3 aRandom; attribute float aSeed;
  uniform float uTargetA; uniform float uTargetB; uniform float uMorph;
  uniform float uTime; uniform float uSize; uniform float uScale; uniform float uDrift;
  uniform float uProgress; uniform float uVelocity;
  uniform vec2 uMouse; uniform float uMouseStrength; uniform vec2 uResolution;
  varying float vColorMix; varying float vSeed; varying float vCore;

  vec3 pickTarget(float idx){
    vec3 p=aTarget0;
    p=mix(p,aTarget1,step(0.5,idx)); p=mix(p,aTarget2,step(1.5,idx));
    p=mix(p,aTarget3,step(2.5,idx)); p=mix(p,aTarget4,step(3.5,idx));
    return p;
  }
  vec3 hash3(vec3 p){
    p=vec3(dot(p,vec3(127.1,311.7,74.7)),dot(p,vec3(269.5,183.3,246.1)),dot(p,vec3(113.5,271.9,124.6)));
    return -1.0+2.0*fract(sin(p)*43758.5453123);
  }
  float vnoise(vec3 x){
    vec3 i=floor(x); vec3 f=fract(x); vec3 u=f*f*(3.0-2.0*f);
    return mix(mix(mix(dot(hash3(i+vec3(0,0,0)),f-vec3(0,0,0)),dot(hash3(i+vec3(1,0,0)),f-vec3(1,0,0)),u.x),
                   mix(dot(hash3(i+vec3(0,1,0)),f-vec3(0,1,0)),dot(hash3(i+vec3(1,1,0)),f-vec3(1,1,0)),u.x),u.y),
               mix(mix(dot(hash3(i+vec3(0,0,1)),f-vec3(0,0,1)),dot(hash3(i+vec3(1,0,1)),f-vec3(1,0,1)),u.x),
                   mix(dot(hash3(i+vec3(0,1,1)),f-vec3(0,1,1)),dot(hash3(i+vec3(1,1,1)),f-vec3(1,1,1)),u.x),u.y),u.z);
  }
  vec3 curlNoise(vec3 p){
    const float e=0.35; vec3 dx=vec3(e,0,0),dy=vec3(0,e,0),dz=vec3(0,0,e);
    float x=(vnoise(p+dy)-vnoise(p-dy))-(vnoise(p+dz)-vnoise(p-dz));
    float y=(vnoise(p+dz)-vnoise(p-dz))-(vnoise(p+dx)-vnoise(p-dx));
    float z=(vnoise(p+dx)-vnoise(p-dx))-(vnoise(p+dy)-vnoise(p-dy));
    return normalize(vec3(x,y,z)+1e-5)*(1.0/(2.0*e));
  }
  float easeStagger(float t,float seed){
    float lead=seed*0.35; float local=clamp((t-lead)/(1.0-0.35),0.0,1.0);
    float s=local*local*local*(local*(local*6.0-15.0)+10.0);
    float spring=sin(local*3.14159)*(1.0-local)*0.12;
    return s+spring*step(0.5,t);
  }
  void main(){
    vSeed=aSeed;
    vec3 a=pickTarget(uTargetA); vec3 b=pickTarget(uTargetB);
    float m=easeStagger(clamp(uMorph,0.0,1.0),aSeed);
    vec3 pos=mix(a,b,m);
    float dt=uTime*0.06;
    vec3 drift=curlNoise(pos*0.12+aRandom*0.7+vec3(0.0,dt,0.0));
    float beat=0.5+0.5*sin(uTime*1.2+aSeed*6.2831);
    pos+=drift*uDrift*(0.6+0.4*beat);
    pos+=aRandom*0.04*sin(uTime*0.7+aSeed*40.0);
    pos*=uScale;

    vec4 mvPosition=modelViewMatrix*vec4(pos,1.0);
    vec4 clip=projectionMatrix*mvPosition; vec2 ndc=clip.xy/max(clip.w,1e-4);
    vec2 toMouse=ndc-uMouse;
    float md=length(toMouse*vec2(uResolution.x/uResolution.y,1.0));
    float push=uMouseStrength*exp(-md*md*6.0);
    vec3 right=vec3(modelViewMatrix[0][0],modelViewMatrix[1][0],modelViewMatrix[2][0]);
    vec3 up=vec3(modelViewMatrix[0][1],modelViewMatrix[1][1],modelViewMatrix[2][1]);
    mvPosition.xyz+=(right*toMouse.x+up*toMouse.y)*push;

    gl_Position=projectionMatrix*mvPosition;
    float size=uSize/max(-mvPosition.z,0.1);
    size*=(0.7+0.6*aSeed); size*=(0.85+0.3*beat); size*=(1.0+uVelocity*1.5*aSeed);
    gl_PointSize=clamp(size,1.0,64.0);

    float radial=clamp(length(pos)/12.0,0.0,1.0);
    vColorMix=clamp(0.25*radial+0.5*aSeed+0.45*uProgress+0.15*beat,0.0,1.0);
    vCore=smoothstep(0.55,0.0,radial)*(0.5+0.5*beat);
  }`;

const PARTICLE_FRAG = /* glsl */ `
  precision highp float;
  varying float vColorMix; varying float vSeed; varying float vCore;
  uniform float uHeat; uniform float uOpacity; uniform float uTime;
  const vec3 VOID=vec3(0.020,0.024,0.039); const vec3 INDIGO=vec3(0.043,0.063,0.149);
  const vec3 VIOLET=vec3(0.424,0.361,0.906); const vec3 CYAN=vec3(0.129,0.902,0.757);
  const vec3 MAGENTA=vec3(1.000,0.302,0.616); const vec3 GOLD=vec3(1.000,0.722,0.361);
  const vec3 STAR=vec3(0.918,0.941,1.000);
  vec3 iridescence(float t){ t=fract(t); return 0.5+0.5*cos(6.28318*(t+vec3(0.0,0.33,0.67))); }
  vec3 palette(float t){
    t=clamp(t,0.0,1.0); vec3 c;
    if(t<0.25) c=mix(INDIGO,VIOLET,t/0.25);
    else if(t<0.50) c=mix(VIOLET,CYAN,(t-0.25)/0.25);
    else if(t<0.72) c=mix(CYAN,MAGENTA,(t-0.50)/0.22);
    else if(t<0.88) c=mix(MAGENTA,GOLD,(t-0.72)/0.16);
    else c=mix(GOLD,STAR,(t-0.88)/0.12);
    return c;
  }
  void main(){
    vec2 uv=gl_PointCoord-0.5; float d=length(uv); if(d>0.5) discard;
    float core=smoothstep(0.5,0.0,d); float halo=pow(core,1.6); float glow=pow(core,4.5);
    vec3 col=palette(vColorMix);
    float fres=pow(1.0-core,2.0);
    vec3 film=iridescence(fres*1.2+vSeed*0.3+uTime*0.02);
    vec3 hot=mix(MAGENTA,GOLD,clamp(fres+vSeed*0.4,0.0,1.0)); hot=mix(hot,film*GOLD*1.4,0.5);
    col=mix(col,hot,uHeat);
    col=mix(col,STAR,glow*(0.18+0.5*uHeat)); col+=STAR*vCore*0.12*(0.4+0.6*uHeat);
    float alpha=(halo*0.5+glow*0.38)*uOpacity;
    col*=(0.62+uHeat*1.0+vCore*0.3);
    gl_FragColor=vec4(col,alpha);
  }`;

const GRADE_FRAG = /* glsl */ `
  uniform sampler2D tDiffuse; uniform float uTime; uniform float uAberration;
  uniform float uVignette; uniform float uGrain; uniform vec2 uResolution;
  varying vec2 vUv;
  float hash21(vec2 p){ p=fract(p*vec2(123.34,345.45)); p+=dot(p,p+34.345); return fract(p.x*p.y); }
  float grainNoise(vec2 uv,float t){ vec2 s=uv*uResolution+vec2(t*53.17,t*71.31); return (hash21(s)+hash21(s+17.0))-1.0; }
  const vec3 LUMA=vec3(0.2126,0.7152,0.0722);
  void main(){
    vec2 uv=vUv; vec2 toC=uv-0.5; float r2=dot(toC,toC);
    vec2 dir=toC*(r2*2.0); float ab=uAberration*0.012; vec2 off=dir*ab;
    float cr=texture2D(tDiffuse,uv+off).r; float cg=texture2D(tDiffuse,uv).g; float cb=texture2D(tDiffuse,uv-off).b;
    vec3 color=vec3(cr,cg,cb);
    color=(color-0.5)*1.06+0.5; color=max(color,0.0);
    float luma=dot(color,LUMA); color=mix(vec3(luma),color,1.12); color=max(color,0.0);
    float scan=sin((uv.y*uResolution.y-uTime*8.0)*3.14159265); color*=1.0-0.02*(0.5+0.5*scan);
    float g=grainNoise(uv,uTime); float gm=smoothstep(0.0,0.25,luma)*(1.0-smoothstep(0.7,1.0,luma));
    color+=g*uGrain*0.08*gm;
    float vig=smoothstep(0.95,0.25,length(toC)*1.41421356); vig=mix(1.0,vig,clamp(uVignette,0.0,1.0)); color*=vig;
    gl_FragColor=vec4(max(color,0.0),1.0);
  }`;

/* ============================================================================
   GEOMETRY — particle targets + the runtime glyph sampler
   ========================================================================== */
const TEXT_OPTS = { font: '900 230px "Times New Roman", Georgia, serif', fitWidth: 15, depth: 1.0, jitter: 1.0 };

function sampleText(str, count, opts = {}) {
  const { font = TEXT_OPTS.font, fitWidth = 15, depth = 1.0, jitter = 1.0, canvasW = 2048, canvasH = 512 } = opts;
  const out = new Float32Array(count * 3);
  const disc = () => {
    for (let i = 0; i < count; i++) {
      const r = Math.sqrt(Math.random()) * (fitWidth * 0.5), a = Math.random() * TAU;
      out[i * 3] = Math.cos(a) * r; out[i * 3 + 1] = Math.sin(a) * r * 0.4; out[i * 3 + 2] = (Math.random() - 0.5) * depth;
    }
    return out;
  };
  if (typeof document === "undefined" || !document.createElement) return disc();
  const cvs = document.createElement("canvas"); cvs.width = canvasW; cvs.height = canvasH;
  const ctx = cvs.getContext("2d", { willReadFrequently: true });
  ctx.clearRect(0, 0, canvasW, canvasH);
  ctx.fillStyle = "#fff"; ctx.textAlign = "center"; ctx.textBaseline = "middle"; ctx.font = font;
  let f = parseInt(font.match(/(\d+)px/)[1], 10); const padX = canvasW * 0.08;
  while (f > 8) { ctx.font = font.replace(/\d+px/, f + "px"); if (ctx.measureText(str).width <= canvasW - padX * 2) break; f -= 6; }
  ctx.fillText(str, canvasW / 2, canvasH / 2);
  const img = ctx.getImageData(0, 0, canvasW, canvasH).data;
  const filled = []; let minX = canvasW, maxX = 0, minY = canvasH, maxY = 0;
  for (let y = 0; y < canvasH; y += 2) for (let x = 0; x < canvasW; x += 2)
    if (img[(y * canvasW + x) * 4 + 3] > 90) { filled.push(x, y); if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y; }
  const nFilled = filled.length / 2; if (nFilled === 0) return disc();
  const glyphW = (maxX - minX) || 1; const cx = (minX + maxX) * 0.5, cy = (minY + maxY) * 0.5; const scale = fitWidth / glyphW;
  for (let i = 0; i < count; i++) {
    const p = (Math.random() * nFilled) | 0;
    const px = filled[p * 2] + (Math.random() - 0.5) * 2 * jitter;
    const py = filled[p * 2 + 1] + (Math.random() - 0.5) * 2 * jitter;
    const wx = (px - cx) * scale, wy = -(py - cy) * scale;
    const wz = _simplex.noise3d(wx * 0.35, wy * 0.35, 0.0) * depth;
    out[i * 3] = wx; out[i * 3 + 1] = wy; out[i * 3 + 2] = wz;
  }
  return out;
}

function buildParticles(count, opts = {}) {
  const { text = "GENESIS", sphereR = 6.0, galaxyR = 8.0, torusR = 5.0, torusTube = 1.6, torusP = 2, torusQ = 3, boxR = 6.0 } = opts;
  const geo = new THREE.BufferGeometry();
  const position = new Float32Array(count * 3);
  const aTarget0 = new Float32Array(count * 3), aTarget1 = new Float32Array(count * 3);
  const aTarget2 = new Float32Array(count * 3), aTarget3 = new Float32Array(count * 3);
  const aRandom = new Float32Array(count * 3), aSeed = new Float32Array(count);
  const aTarget4 = sampleText(text, count, TEXT_OPTS);
  const ARMS = 4, SPIN = 1.0, SPREAD = 0.55, GOLDEN = 1.6180339887;
  for (let i = 0; i < count; i++) {
    const i3 = i * 3; aSeed[i] = (i + 0.5) / count;
    const rx = Math.random() * 2 - 1, ry = Math.random() * 2 - 1, rz = Math.random() * 2 - 1;
    aRandom[i3] = rx; aRandom[i3 + 1] = ry; aRandom[i3 + 2] = rz;
    { const t = (i + 0.5) / count, phi = Math.acos(1 - 2 * t), theta = TAU * GOLDEN * i, r = sphereR * (0.97 + 0.03 * Math.random());
      aTarget0[i3] = r * Math.sin(phi) * Math.cos(theta); aTarget0[i3 + 1] = r * Math.cos(phi); aTarget0[i3 + 2] = r * Math.sin(phi) * Math.sin(theta); }
    { const rad = Math.pow(Math.random(), 0.5) * galaxyR, arm = (i % ARMS) / ARMS * TAU, ang = arm + rad * SPIN;
      const sx = (Math.random() - 0.5) * SPREAD * (rad * 0.4 + 0.6), sy = (Math.random() - 0.5) * SPREAD * (rad * 0.4 + 0.6);
      const thin = (Math.random() - 0.5) * 0.6 * (1.0 - rad / galaxyR * 0.7);
      aTarget1[i3] = Math.cos(ang) * rad + sx; aTarget1[i3 + 1] = thin; aTarget1[i3 + 2] = Math.sin(ang) * rad + sy; }
    { const u = (i / count) * TAU * torusP, qu = u * (torusQ / torusP), cu = Math.cos(u), su = Math.sin(u), cq = Math.cos(qu);
      const baseR = torusR + torusTube * cq, tr = Math.cbrt(Math.random()) * 0.9;
      aTarget2[i3] = baseR * cu + rx * tr; aTarget2[i3 + 1] = torusTube * Math.sin(qu) + ry * tr; aTarget2[i3 + 2] = baseR * su + rz * tr; }
    { const face = i % 6, GRID = 28, s = boxR;
      const a = (Math.floor(Math.random() * GRID) / (GRID - 1) - 0.5) * 2 * boxR, b = (Math.floor(Math.random() * GRID) / (GRID - 1) - 0.5) * 2 * boxR;
      let X, Y, Z;
      switch (face) { case 0: X = s; Y = a; Z = b; break; case 1: X = -s; Y = a; Z = b; break; case 2: X = a; Y = s; Z = b; break; case 3: X = a; Y = -s; Z = b; break; case 4: X = a; Y = b; Z = s; break; default: X = a; Y = b; Z = -s; }
      const jw = 0.06 * boxR;
      aTarget3[i3] = X + (Math.random() - 0.5) * jw; aTarget3[i3 + 1] = Y + (Math.random() - 0.5) * jw; aTarget3[i3 + 2] = Z + (Math.random() - 0.5) * jw; }
    position[i3] = aTarget0[i3]; position[i3 + 1] = aTarget0[i3 + 1]; position[i3 + 2] = aTarget0[i3 + 2];
  }
  geo.setAttribute("position", new THREE.BufferAttribute(position, 3));
  geo.setAttribute("aTarget0", new THREE.BufferAttribute(aTarget0, 3));
  geo.setAttribute("aTarget1", new THREE.BufferAttribute(aTarget1, 3));
  geo.setAttribute("aTarget2", new THREE.BufferAttribute(aTarget2, 3));
  geo.setAttribute("aTarget3", new THREE.BufferAttribute(aTarget3, 3));
  geo.setAttribute("aTarget4", new THREE.BufferAttribute(aTarget4, 3));
  geo.setAttribute("aRandom", new THREE.BufferAttribute(aRandom, 3));
  geo.setAttribute("aSeed", new THREE.BufferAttribute(aSeed, 1));
  geo.boundingSphere = new THREE.Sphere(new THREE.Vector3(0, 0, 0), 80);
  return geo;
}

/* a soft radial point sprite, drawn once to a canvas (no asset) -------------- */
function makeSprite() {
  const s = 64, c = document.createElement("canvas"); c.width = c.height = s;
  const g = c.getContext("2d"); const grad = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  grad.addColorStop(0, "rgba(255,255,255,1)"); grad.addColorStop(0.35, "rgba(255,255,255,0.55)");
  grad.addColorStop(1, "rgba(255,255,255,0)"); g.fillStyle = grad; g.fillRect(0, 0, s, s);
  const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.NoColorSpace; return tex;
}

/* ============================================================================
   CHOREOGRAPHY KEYFRAMES (scroll s ∈ [0,1])
   ========================================================================== */
const CAM_KEYS = [
  [0.00, 0.0, 0.0, 13.0], [0.10, 3.5, 1.2, 19.5], [0.28, -7.5, 2.6, 20.5],
  [0.40, -3.0, 1.0, 16.5], [0.46, 0.0, 0.2, 15.5], [0.58, 0.0, 0.0, 12.5],
  [0.68, 0.0, 0.0, 10.5], [0.74, 0.0, 0.0, 8.0], [0.80, 0.0, 0.5, 5.6],
  [0.88, 0.0, 3.2, 22.0], [1.00, 0.0, 1.6, 24.0],
];
const DRIFT_KEYS = [[0.00, 0.25], [0.10, 0.30], [0.19, 1.25], [0.28, 0.55], [0.37, 0.32], [0.46, 0.55], [0.52, 0.9], [0.58, 0.45], [0.63, 0.12], [0.80, 0.12], [0.9, 0.5], [1.0, 0.55]];
const BSTR_KEYS = [[0.00, 0.52], [0.19, 0.72], [0.40, 0.66], [0.52, 0.82], [0.62, 0.82], [0.66, 0.95], [0.71, 1.15], [0.76, 0.92], [0.82, 0.7], [1.00, 0.66]];
const BTHR_KEYS = [[0.00, 0.62], [0.10, 0.56], [0.40, 0.50], [0.52, 0.46], [0.62, 0.40], [0.66, 0.30], [0.71, 0.24], [0.76, 0.32], [0.80, 0.46], [1.00, 0.56]];
const BRAD_KEYS = [[0.00, 0.55], [0.50, 0.68], [0.71, 0.82], [0.80, 0.68], [1.00, 0.64]];
const SCALE_KEYS = [[0.00, 1.10], [0.05, 1.18], [0.80, 1.18], [0.86, 1.6], [1.00, 2.1]];
const VIG_KEYS = [[0.00, 0.55], [0.66, 0.5], [0.71, 0.72], [0.80, 0.55], [1.00, 0.5]];
const ABER_KEYS = [[0.00, 0.0], [0.45, 0.0], [0.50, 0.6], [0.58, 0.2], [0.63, 0.05], [0.70, 0.25], [0.78, 0.65], [0.82, 0.12], [1.00, 0.0]];
const MOUSE_KEYS = [[0.00, 0.03], [0.80, 0.03], [0.86, 0.22], [1.00, 0.25]];

// morph stops: [startS, A, B] — mix is local progress to the next stop's startS
const MORPH_STOPS = [
  [0.00, 0, 0], [0.10, 0, 1], [0.28, 1, 2], [0.37, 2, 3],
  [0.46, 3, 3], [0.58, 3, 4], [0.68, 4, 4], [0.80, 4, 1], [1.0001, 1, 1],
];
function morphFromScroll(s) {
  for (let i = 0; i < MORPH_STOPS.length - 1; i++) {
    const a = MORPH_STOPS[i], b = MORPH_STOPS[i + 1];
    if (s >= a[0] && s < b[0]) return { A: a[1], B: a[2], mix: clamp01((s - a[0]) / (b[0] - a[0])) };
  }
  return { A: 4, B: 1, mix: 1 };
}

/* ============================================================================
   BOOT
   ========================================================================== */
const canvas = document.getElementById("stage");
let degraded = false;

try {
  init();
} catch (err) {
  console.warn("PROMPTFIELD scene disabled (fallback):", err);
  document.body.classList.add("no-webgl");
  window.__webglLost = true;
  window.dispatchEvent(new Event("scene:ready"));
}

function init() {
  const small = Math.min(window.innerWidth, window.innerHeight) < 700 ||
    window.matchMedia("(pointer: coarse)").matches;
  const dm = navigator.deviceMemory || 4, hc = navigator.hardwareConcurrency || 4;
  let COUNT = 120000;
  if (small || dm <= 2) COUNT = 45000; else if (dm <= 4 || hc <= 4) COUNT = 80000;
  window.__particles = COUNT;

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: "high-performance", alpha: false });
  let dpr = Math.min(window.devicePixelRatio || 1, small ? 1.5 : 1.75);
  renderer.setPixelRatio(dpr);
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.setClearColor(0x05060a, 1);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.0;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(55, window.innerWidth / window.innerHeight, 0.1, 600);
  camera.position.set(0, 0, 13);

  /* --- background aurora (fullscreen, camera-independent) --- */
  const bgUniforms = {
    uTime: { value: 0 }, uRes: { value: new THREE.Vector2(window.innerWidth, window.innerHeight) },
    uMouse: { value: new THREE.Vector2(0, 0) }, uScroll: { value: 0 }, uVelocity: { value: 0 },
  };
  const bgMesh = new THREE.Mesh(new THREE.PlaneGeometry(2, 2),
    new THREE.ShaderMaterial({ vertexShader: BG_VERT, fragmentShader: BG_FRAG, uniforms: bgUniforms, depthTest: false, depthWrite: false, transparent: false }));
  bgMesh.frustumCulled = false; bgMesh.renderOrder = -10; scene.add(bgMesh);

  /* --- particle organism --- */
  const sprite = makeSprite();
  const pUniforms = {
    uTargetA: { value: 0 }, uTargetB: { value: 0 }, uMorph: { value: 0 },
    uTime: { value: 0 }, uSize: { value: 48 * (small ? 0.8 : 1) }, uScale: { value: 1 }, uDrift: { value: 0.25 },
    uProgress: { value: 0 }, uVelocity: { value: 0 }, uHeat: { value: 0 },
    uMouse: { value: new THREE.Vector2(0, 0) }, uMouseStrength: { value: 0.03 },
    uResolution: { value: new THREE.Vector2(window.innerWidth, window.innerHeight) }, uOpacity: { value: 1 },
    uSprite: { value: sprite },
  };
  const geometry = buildParticles(COUNT, { text: "GENESIS" });
  const pMat = new THREE.ShaderMaterial({
    uniforms: pUniforms, vertexShader: PARTICLE_VERT,
    // sprite-textured fragment: multiply soft sprite into the computed colour
    fragmentShader: PARTICLE_FRAG.replace(
      "vec2 uv=gl_PointCoord-0.5; float d=length(uv); if(d>0.5) discard;",
      "vec2 uv=gl_PointCoord-0.5; float d=length(uv); if(d>0.5) discard; float spr=texture2D(uSprite,gl_PointCoord).a;"
    ).replace("float alpha=(halo*0.85+glow*0.6)*uOpacity;", "float alpha=(halo*0.85+glow*0.6)*spr*uOpacity;")
      .replace("uniform float uHeat;", "uniform sampler2D uSprite; uniform float uHeat;"),
    blending: THREE.AdditiveBlending, depthWrite: false, depthTest: false, transparent: true,
  });
  const points = new THREE.Points(geometry, pMat);
  points.frustumCulled = false; scene.add(points);

  /* --- light tunnel rings (§03) --- */
  const RING_N = small ? 38 : 72;
  const ringGeo = new THREE.TorusGeometry(7.4, 0.05, 6, 96);
  const ringMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false, depthTest: false });
  const rings = new THREE.InstancedMesh(ringGeo, ringMat, RING_N);
  rings.frustumCulled = false; rings.renderOrder = -1;
  const cCyan = new THREE.Color(0x21e6c1), cMag = new THREE.Color(0xff4d9d);
  for (let i = 0; i < RING_N; i++) rings.setColorAt(i, i % 2 ? cMag : cCyan);
  if (rings.instanceColor) rings.instanceColor.needsUpdate = true;
  scene.add(rings);
  const _dummy = new THREE.Object3D();

  /* --- post chain: Render → Bloom → Grade → Output → SMAA --- */
  const composer = new EffectComposer(renderer);
  composer.addPass(new RenderPass(scene, camera));
  const bloom = new UnrealBloomPass(new THREE.Vector2(window.innerWidth, window.innerHeight), 0.62, 0.6, 0.08);
  composer.addPass(bloom);
  const gradePass = new ShaderPass({
    uniforms: {
      tDiffuse: { value: null }, uTime: { value: 0 }, uAberration: { value: 0 },
      uVignette: { value: 0.55 }, uGrain: { value: PF.reduceMotion ? 0 : 0.5 },
      uResolution: { value: new THREE.Vector2(window.innerWidth * dpr, window.innerHeight * dpr) },
    },
    vertexShader: "varying vec2 vUv; void main(){ vUv=uv; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }",
    fragmentShader: GRADE_FRAG,
  });
  composer.addPass(gradePass);
  composer.addPass(new OutputPass());
  const smaa = new SMAAPass(window.innerWidth, window.innerHeight);
  composer.addPass(smaa);

  /* --- interaction: smoothed pointer --- */
  const mouse = new THREE.Vector2(0, 0), mouseT = new THREE.Vector2(0, 0);
  window.addEventListener("pointermove", (e) => {
    mouseT.set((e.clientX / window.innerWidth) * 2 - 1, -(e.clientY / window.innerHeight) * 2 + 1);
  }, { passive: true });

  /* --- live re-forge (§05) --- */
  const reform = { active: false, t: 0, dur: 1.8, lock: false, lockProg: 0 };
  PF.scene = {
    formedWord: "GENESIS",
    submitWord(word) {
      word = (word || "").toString().trim().toUpperCase().slice(0, 14);
      if (!word) word = "AI";
      const pts = sampleText(word, COUNT, TEXT_OPTS);
      geometry.attributes.aTarget4.array.set(pts);
      geometry.attributes.aTarget4.needsUpdate = true;
      PF.scene.formedWord = word;
      reform.active = true; reform.t = 0; reform.lock = false;
    },
  };

  /* --- resize --- */
  function resize() {
    const w = window.innerWidth, h = window.innerHeight;
    dpr = Math.min(window.devicePixelRatio || 1, small ? 1.5 : 1.75);
    if (degraded) dpr = Math.min(dpr, 1.0);
    renderer.setPixelRatio(dpr);
    renderer.setSize(w, h);
    camera.aspect = w / h; camera.updateProjectionMatrix();
    composer.setSize(w, h); bloom.setSize(w, h);
    bgUniforms.uRes.value.set(w, h);
    pUniforms.uResolution.value.set(w, h);
    gradePass.uniforms.uResolution.value.set(w * dpr, h * dpr);
  }
  window.addEventListener("resize", resize, { passive: true });

  /* --- context loss safety --- */
  canvas.addEventListener("webglcontextlost", (e) => { e.preventDefault(); window.__webglLost = true; running = false; }, false);

  /* --- render loop --- */
  const clock = new THREE.Clock();
  const camPos = new THREE.Vector3(), camWord = new THREE.Vector3(0, 0.2, 12);
  let running = true, ready = false;
  let frames = 0, fpsAcc = 0, lowStreak = 0;

  function frame() {
    if (!running) return;
    requestAnimationFrame(frame);
    const dt = Math.min(clock.getDelta(), 0.05);
    const s = clamp01(PF.progress);
    const vel = clamp01(PF.velocity);
    const timeScale = PF.reduceMotion ? 0.15 : 1.0;
    pUniforms.uTime.value += dt * timeScale;
    bgUniforms.uTime.value += dt * timeScale;
    if (!PF.reduceMotion) gradePass.uniforms.uTime.value += dt;

    // smoothed pointer
    mouse.lerp(mouseT, 0.08);
    PF.mouse.x = mouse.x; PF.mouse.y = mouse.y;
    pUniforms.uMouse.value.copy(mouse);
    bgUniforms.uMouse.value.lerp(mouseT, 0.04);

    // shared scroll instrument
    bgUniforms.uScroll.value = s; bgUniforms.uVelocity.value = vel;
    pUniforms.uProgress.value = s; pUniforms.uVelocity.value = PF.reduceMotion ? 0 : vel;

    // morph (scroll, or live re-forge override)
    let A, B, mix, wordView = 0;
    if (reform.active) {
      reform.t += dt; const k = clamp01(reform.t / reform.dur);
      A = 1; B = 4; mix = smooth(k); wordView = 1;
      pUniforms.uHeat.value = lerp(pUniforms.uHeat.value, 0.35 * Math.sin(k * Math.PI), 0.2);
      if (k >= 1) { reform.active = false; reform.lock = true; reform.lockProg = s; }
    } else if (reform.lock) {
      A = 4; B = 4; mix = 1; wordView = 1;
      pUniforms.uHeat.value = lerp(pUniforms.uHeat.value, 0.0, 0.05);
      if (Math.abs(s - reform.lockProg) > 0.015) reform.lock = false;
    } else {
      const m = morphFromScroll(s); A = m.A; B = m.B; mix = m.mix;
      pUniforms.uHeat.value = smooth((s - 0.68) / 0.06);
    }
    PF.forging = reform.active || reform.lock;
    pUniforms.uTargetA.value = A; pUniforms.uTargetB.value = B; pUniforms.uMorph.value = mix;
    pUniforms.uDrift.value = PF.reduceMotion ? 0.06 : sampleScalar(DRIFT_KEYS, s);
    pUniforms.uScale.value = wordView ? lerp(pUniforms.uScale.value, 1.0, 0.1) : sampleScalar(SCALE_KEYS, s);
    pUniforms.uMouseStrength.value = sampleScalar(MOUSE_KEYS, s);

    // camera path (+ mouse parallax), blending toward a word-framing view on re-forge
    sampleVec3(CAM_KEYS, s, camPos);
    if (wordView) camPos.lerp(camWord, 0.85);
    if (!PF.reduceMotion) { camPos.x += mouse.x * 0.7; camPos.y += mouse.y * 0.5; }
    camera.position.lerp(camPos, ready ? 0.12 : 1.0);
    camera.lookAt(0, 0, 0);

    // light tunnel rings — active only across the pipeline window
    const ringAmt = Math.max(0, Math.min(
      smooth((s - 0.42) / 0.06), smooth((0.66 - s) / 0.06)));
    ringMat.opacity = ringAmt * 0.85; rings.visible = ringAmt > 0.002;
    if (rings.visible) {
      const spread = 4.2, range = RING_N * spread, time = pUniforms.uTime.value;
      const flow = (time * 16 + s * 240);
      for (let i = 0; i < RING_N; i++) {
        let z = 10 - (((i * spread - flow) % range) + range) % range;
        const ang = i * 0.5 + time * 0.4, off = 1.7;
        _dummy.position.set(Math.cos(ang) * off, Math.sin(ang) * off, z);
        _dummy.rotation.set(0, 0, ang); _dummy.scale.setScalar(1);
        _dummy.updateMatrix(); rings.setMatrixAt(i, _dummy.matrix);
      }
      rings.instanceMatrix.needsUpdate = true;
    }

    // bloom + grade per-frame
    bloom.strength = sampleScalar(BSTR_KEYS, s);
    bloom.threshold = sampleScalar(BTHR_KEYS, s);
    bloom.radius = sampleScalar(BRAD_KEYS, s);
    gradePass.uniforms.uVignette.value = sampleScalar(VIG_KEYS, s);
    gradePass.uniforms.uAberration.value = PF.reduceMotion ? 0 : clamp01(sampleScalar(ABER_KEYS, s) + vel * 0.7);

    composer.render(dt);

    if (!ready) { ready = true; PF.ready = true; window.dispatchEvent(new Event("scene:ready")); }

    // perf telemetry + one-time degrade
    frames++; fpsAcc += dt;
    if (fpsAcc >= 0.5) {
      const fps = Math.round(frames / fpsAcc); frames = 0; fpsAcc = 0;
      PF.fps = fps; window.__fps = fps; PF.drawCalls = renderer.info.render.calls;
      if (!degraded) {
        if (fps < 32) lowStreak++; else lowStreak = 0;
        if (lowStreak >= 3) { degraded = true; gradePass.uniforms.uGrain.value = 0; resize(); }
      }
    }
  }

  resize();
  frame();

  PF.scene.dispose = () => {
    running = false;
    geometry.dispose(); pMat.dispose(); sprite.dispose();
    ringGeo.dispose(); ringMat.dispose();
    bgMesh.geometry.dispose(); bgMesh.material.dispose();
    composer.dispose(); renderer.dispose();
  };
}
