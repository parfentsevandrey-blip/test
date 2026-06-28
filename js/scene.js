/* ============================================================================
   PROMPTFIELD — the procedural WebGL stage
   "Type a world into being."

   One eased scroll s∈[0,1] (+ smoothed velocity) drives EVERYTHING as a single
   instrument: ~100k GPU particles morph sphere → galaxy → torus → DNA → globe →
   heart → neural net → a legible 3D WORD (which ignites) → supershape → cosmos,
   over an iridescent aurora, through an immersive light tunnel, around a reactive
   energy core, graded by bloom + shockwaves + god-rays + a cinematic post chain.
   No models, no images, no fonts: the one sprite and the one glyph are drawn to a
   canvas at runtime. Built entirely from vendored Three.js r160 — fully offline.
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
  for (let i = 0; i < n - 1; i++) { if (s >= keys[i][0] && s <= keys[i + 1][0]) { a = keys[i]; b = keys[i + 1]; break; } }
  const t = smooth((s - a[0]) / (b[0] - a[0]));
  out.set(lerp(a[1], b[1], t), lerp(a[2], b[2], t), lerp(a[3], b[3], t));
  return out;
}
// sample an arbitrary column c of a keyframe row (for camera roll/fov)
function camCol(keys, s, c) {
  if (s <= keys[0][0]) return keys[0][c];
  const n = keys.length;
  if (s >= keys[n - 1][0]) return keys[n - 1][c];
  for (let i = 0; i < n - 1; i++) { const a = keys[i], b = keys[i + 1]; if (s >= a[0] && s <= b[0]) return lerp(a[c], b[c], smooth((s - a[0]) / (b[0] - a[0]))); }
  return keys[n - 1][c];
}

/* ============================================================================
   SHADERS
   ========================================================================== */
const BG_VERT = /* glsl */ `
  varying vec2 vUv;
  void main() { vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }`;

const BG_FRAG = /* glsl */ `
  precision highp float;
  varying vec2 vUv;
  uniform float uTime; uniform vec2 uRes; uniform vec2 uMouse; uniform float uScroll; uniform float uVelocity;
  const vec3 VOID=vec3(0.020,0.024,0.039); const vec3 INDIGO=vec3(0.043,0.063,0.149);
  const vec3 VIOLET=vec3(0.424,0.361,0.906); const vec3 CYAN=vec3(0.129,0.902,0.757);
  const vec3 MAGENTA=vec3(1.000,0.302,0.616); const vec3 GOLD=vec3(1.000,0.722,0.361);
  const vec3 STAR=vec3(0.918,0.941,1.000);
  vec3 iridescence(float t){ t=fract(t); return 0.5+0.5*cos(6.28318*(t+vec3(0.0,0.33,0.67))); }
  vec3 mod289(vec3 x){ return x-floor(x*(1.0/289.0))*289.0; }
  vec2 mod289(vec2 x){ return x-floor(x*(1.0/289.0))*289.0; }
  vec3 permute(vec3 x){ return mod289(((x*34.0)+1.0)*x); }
  float snoise(vec2 v){
    const vec4 C=vec4(0.211324865405187,0.366025403784439,-0.577350269189626,0.024390243902439);
    vec2 i=floor(v+dot(v,C.yy)); vec2 x0=v-i+dot(i,C.xx);
    vec2 i1=(x0.x>x0.y)?vec2(1.0,0.0):vec2(0.0,1.0);
    vec4 x12=x0.xyxy+C.xxzz; x12.xy-=i1; i=mod289(i);
    vec3 p=permute(permute(i.y+vec3(0.0,i1.y,1.0))+i.x+vec3(0.0,i1.x,1.0));
    vec3 m=max(0.5-vec3(dot(x0,x0),dot(x12.xy,x12.xy),dot(x12.zw,x12.zw)),0.0); m=m*m; m=m*m;
    vec3 x=2.0*fract(p*C.www)-1.0; vec3 h=abs(x)-0.5; vec3 ox=floor(x+0.5); vec3 a0=x-ox;
    m*=1.79284291400159-0.85373472095314*(a0*a0+h*h);
    vec3 g; g.x=a0.x*x0.x+h.x*x0.y; g.yz=a0.yz*x12.xz+h.yz*x12.yw; return 130.0*dot(m,g);
  }
  float fbm(vec2 p){ float f=0.0,a=0.5; mat2 rot=mat2(0.80,-0.60,0.60,0.80);
    for(int i=0;i<3;i++){ f+=a*snoise(p); p=rot*p*2.0+11.3; a*=0.5; } return f; }
  void main(){
    vec2 uv=vUv; vec2 p=(uv-0.5); float asp=uRes.x/max(uRes.y,1.0); p.x*=asp;
    float t=uTime*0.06; vec2 mouse=uMouse*0.30;
    vec2 q; q.x=fbm(p*0.85+vec2(0.0,t)+mouse); q.y=fbm(p*0.85+vec2(5.2,t*0.9)-mouse);
    vec2 r; r.x=fbm(p*1.05+1.4*q+vec2(1.7,9.2)-t*0.7); r.y=fbm(p*1.05+1.4*q+vec2(8.3,2.8)+t*0.5);
    float flow=fbm(p*0.95+1.7*r); flow=flow*0.5+0.5;
    float thickness=flow*1.6+length(r)*0.45+uScroll*0.6+t*0.25; vec3 iri=iridescence(thickness);
    float s=clamp(uScroll,0.0,1.0);
    vec3 base=mix(VOID,INDIGO,smoothstep(0.15,0.85,flow));
    vec3 coolAccent=mix(VIOLET,CYAN,smoothstep(0.10,0.46,s));
    vec3 hotAccent=mix(CYAN,MAGENTA,smoothstep(0.46,0.62,s)); hotAccent=mix(hotAccent,GOLD,smoothstep(0.66,0.80,s));
    float uHeat=smoothstep(0.70,0.77,s);
    vec3 accent=mix(coolAccent,hotAccent,smoothstep(0.40,0.66,s));
    vec3 settle=mix(GOLD,STAR,0.35); accent=mix(accent,settle,smoothstep(0.82,1.00,s));
    float ribbon=smoothstep(0.45,0.72,flow)-smoothstep(0.72,0.95,flow); ribbon=max(ribbon,0.0);
    vec3 col=base; vec3 sheen=mix(accent,iri,0.5);
    col=mix(col,sheen,ribbon*(0.32+0.30*flow));
    col+=coolAccent*smoothstep(0.0,0.35,flow)*0.05*(1.0-uHeat);
    float heatWave=smoothstep(0.0,1.0,flow+r.x*0.5); vec3 ignite=mix(MAGENTA,GOLD,heatWave);
    col=mix(col,ignite,uHeat*(0.35+0.4*ribbon)); col+=ignite*uHeat*0.25*smoothstep(0.5,1.0,flow);
    float starField=smoothstep(0.86,1.0,s); float spark=snoise(p*90.0+7.0); spark=smoothstep(0.92,1.0,spark);
    col+=STAR*spark*starField*(0.6+0.4*sin(uTime*2.0+p.x*40.0));
    float breath=0.5+0.5*sin(uTime*0.7); col+=coolAccent*breath*0.03*(1.0-smoothstep(0.0,0.30,s));
    float vig=smoothstep(1.25,0.30,length(uv-0.5)*1.6); col*=mix(0.55,1.0,vig);
    col+=accent*0.04*smoothstep(0.6,0.0,uv.y)*(1.0-uHeat*0.5); col+=iri*uVelocity*0.12*ribbon;
    float grain=fract(sin(dot(uv*uRes,vec2(12.9898,78.233)))*43758.5453); col+=(grain-0.5)*0.02;
    col*=0.28; col=clamp(col,0.0,1.05);
    gl_FragColor=vec4(col,1.0);
  }`;

const PARTICLE_VERT = /* glsl */ `
  precision highp float;
  attribute vec3 aTarget0; attribute vec3 aTarget1; attribute vec3 aTarget2; attribute vec3 aTarget3;
  attribute vec3 aTarget4; attribute vec3 aTarget5; attribute vec3 aTarget6; attribute vec3 aTarget7;
  attribute vec3 aTarget8; attribute vec3 aRandom; attribute float aSeed;
  uniform float uTargetA; uniform float uTargetB; uniform float uMorph;
  uniform float uTime; uniform float uSize; uniform float uScale; uniform float uDrift;
  uniform float uProgress; uniform float uVelocity;
  uniform vec2 uMouse; uniform float uMouseStrength; uniform vec2 uResolution;
  varying float vColorMix; varying float vSeed; varying float vCore;
  vec3 pickTarget(float idx){
    vec3 p=aTarget0;
    p=mix(p,aTarget1,step(0.5,idx)); p=mix(p,aTarget2,step(1.5,idx)); p=mix(p,aTarget3,step(2.5,idx));
    p=mix(p,aTarget4,step(3.5,idx)); p=mix(p,aTarget5,step(4.5,idx)); p=mix(p,aTarget6,step(5.5,idx));
    p=mix(p,aTarget7,step(6.5,idx)); p=mix(p,aTarget8,step(7.5,idx));
    return p;
  }
  vec3 hash3(vec3 p){ p=vec3(dot(p,vec3(127.1,311.7,74.7)),dot(p,vec3(269.5,183.3,246.1)),dot(p,vec3(113.5,271.9,124.6))); return -1.0+2.0*fract(sin(p)*43758.5453123); }
  float vnoise(vec3 x){ vec3 i=floor(x); vec3 f=fract(x); vec3 u=f*f*(3.0-2.0*f);
    return mix(mix(mix(dot(hash3(i+vec3(0,0,0)),f-vec3(0,0,0)),dot(hash3(i+vec3(1,0,0)),f-vec3(1,0,0)),u.x),
                   mix(dot(hash3(i+vec3(0,1,0)),f-vec3(0,1,0)),dot(hash3(i+vec3(1,1,0)),f-vec3(1,1,0)),u.x),u.y),
               mix(mix(dot(hash3(i+vec3(0,0,1)),f-vec3(0,0,1)),dot(hash3(i+vec3(1,0,1)),f-vec3(1,0,1)),u.x),
                   mix(dot(hash3(i+vec3(0,1,1)),f-vec3(0,1,1)),dot(hash3(i+vec3(1,1,1)),f-vec3(1,1,1)),u.x),u.y),u.z); }
  vec3 curlNoise(vec3 p){ const float e=0.35; vec3 dx=vec3(e,0,0),dy=vec3(0,e,0),dz=vec3(0,0,e);
    float x=(vnoise(p+dy)-vnoise(p-dy))-(vnoise(p+dz)-vnoise(p-dz));
    float y=(vnoise(p+dz)-vnoise(p-dz))-(vnoise(p+dx)-vnoise(p-dx));
    float z=(vnoise(p+dx)-vnoise(p-dx))-(vnoise(p+dy)-vnoise(p-dy));
    return normalize(vec3(x,y,z)+1e-5)*(1.0/(2.0*e)); }
  float easeStagger(float t,float seed){ float lead=seed*0.35; float local=clamp((t-lead)/(1.0-0.35),0.0,1.0);
    float s=local*local*local*(local*(local*6.0-15.0)+10.0); float spring=sin(local*3.14159)*(1.0-local)*0.12; return s+spring*step(0.5,t); }
  void main(){
    vSeed=aSeed;
    vec3 a=pickTarget(uTargetA); vec3 b=pickTarget(uTargetB);
    float m=easeStagger(clamp(uMorph,0.0,1.0),aSeed);
    vec3 pos=mix(a,b,m);
    float dt=uTime*0.06; vec3 drift=curlNoise(pos*0.12+aRandom*0.7+vec3(0.0,dt,0.0));
    float beat=0.5+0.5*sin(uTime*1.2+aSeed*6.2831);
    pos+=drift*uDrift*(0.6+0.4*beat); pos+=aRandom*0.04*sin(uTime*0.7+aSeed*40.0); pos*=uScale;
    vec4 mvPosition=modelViewMatrix*vec4(pos,1.0);
    vec4 clip=projectionMatrix*mvPosition; vec2 ndc=clip.xy/max(clip.w,1e-4);
    vec2 toMouse=ndc-uMouse; float md=length(toMouse*vec2(uResolution.x/uResolution.y,1.0));
    float push=uMouseStrength*exp(-md*md*6.0);
    vec3 right=vec3(modelViewMatrix[0][0],modelViewMatrix[1][0],modelViewMatrix[2][0]);
    vec3 up=vec3(modelViewMatrix[0][1],modelViewMatrix[1][1],modelViewMatrix[2][1]);
    mvPosition.xyz+=(right*toMouse.x+up*toMouse.y)*push;
    gl_Position=projectionMatrix*mvPosition;
    float size=uSize/max(-mvPosition.z,0.1);
    size*=(0.7+0.6*aSeed); size*=(0.85+0.3*beat); size*=(1.0+uVelocity*1.5*aSeed);
    gl_PointSize=clamp(size,1.0,90.0);
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
  vec3 palette(float t){ t=clamp(t,0.0,1.0); vec3 c;
    if(t<0.25) c=mix(INDIGO,VIOLET,t/0.25); else if(t<0.50) c=mix(VIOLET,CYAN,(t-0.25)/0.25);
    else if(t<0.72) c=mix(CYAN,MAGENTA,(t-0.50)/0.22); else if(t<0.88) c=mix(MAGENTA,GOLD,(t-0.72)/0.16);
    else c=mix(GOLD,STAR,(t-0.88)/0.12); return c; }
  void main(){
    vec2 uv=gl_PointCoord-0.5; float d=length(uv); if(d>0.5) discard;
    float core=smoothstep(0.5,0.0,d); float halo=pow(core,1.6); float glow=pow(core,4.5);
    vec3 col=palette(vColorMix);
    float fres=pow(1.0-core,2.0); vec3 film=iridescence(fres*1.2+vSeed*0.3+uTime*0.02);
    vec3 hot=mix(MAGENTA,GOLD,clamp(fres+vSeed*0.4,0.0,1.0)); hot=mix(hot,film*GOLD*1.4,0.5);
    col=mix(col,hot,uHeat);
    col=mix(col,STAR,glow*(0.18+0.3*uHeat)); col+=STAR*vCore*0.12*(0.4+0.5*uHeat);
    float alpha=(halo*0.65+glow*0.45)*uOpacity; col*=(0.92+uHeat*0.25+vCore*0.3);
    gl_FragColor=vec4(col,alpha);
  }`;

// Cinematic grade + spectacle: chromatic aberration, shockwave ripple, god-ray
// zoom-streak, full-screen flash, barrel lens-warp, grain, vignette, filmic.
const GRADE_FRAG = /* glsl */ `
  uniform sampler2D tDiffuse;
  uniform float uTime, uAberration, uVignette, uGrain;
  uniform vec2 uResolution;
  uniform float uShock, uStreak, uFlash, uLensWarp;
  varying vec2 vUv;
  float hash21(vec2 p){ p=fract(p*vec2(123.34,345.45)); p+=dot(p,p+34.345); return fract(p.x*p.y); }
  float grainNoise(vec2 uv,float t){ vec2 s=uv*uResolution+vec2(t*53.17,t*71.31); return (hash21(s)+hash21(s+17.0))-1.0; }
  const vec3 LUMA=vec3(0.2126,0.7152,0.0722);
  void main(){
    vec2 uv=vUv; vec2 toC=uv-0.5;
    float r2warp=dot(toC,toC); uv+=toC*r2warp*(uLensWarp*0.18); toC=uv-0.5;
    float dist=length(toC);
    float phase=(1.0-uShock)*1.15;
    float ring=exp(-pow((dist-phase)*7.0,2.0));
    float shockEnv=uShock*uShock;
    vec2 rippleDir=toC/max(dist,1e-4);
    float ripple=ring*sin((dist-phase)*42.0);
    uv+=rippleDir*ripple*0.020*shockEnv; toC=uv-0.5; dist=length(toC);
    vec2 abDir=toC*(dot(toC,toC)*2.0);
    float ab=uAberration*0.012+ring*0.010*shockEnv; vec2 off=abDir*ab;
    float cr=texture2D(tDiffuse,uv+off).r; float cg=texture2D(tDiffuse,uv).g; float cb=texture2D(tDiffuse,uv-off).b;
    vec3 color=vec3(cr,cg,cb);
    if(uStreak>0.001){
      vec2 toCenter=(vec2(0.5)-uv); vec3 streak=vec3(0.0); float wsum=0.0;
      for(int i=0;i<6;i++){ float fi=float(i)/5.0; float w=1.0-fi;
        streak+=texture2D(tDiffuse, uv+toCenter*fi*(0.22*uStreak)).rgb*w; wsum+=w; }
      streak/=max(wsum,1e-4); float sl=dot(streak,LUMA); streak*=smoothstep(0.35,1.1,sl);
      color+=streak*uStreak*1.15;
    }
    color=(color-0.5)*1.06+0.5; color=max(color,0.0);
    float luma=dot(color,LUMA); color=mix(vec3(luma),color,1.12); color=max(color,0.0);
    float scan=sin((uv.y*uResolution.y-uTime*8.0)*3.14159265); color*=1.0-0.02*(0.5+0.5*scan);
    color+=vec3(0.55,0.78,1.0)*ring*shockEnv*0.6;
    float g=grainNoise(uv,uTime); float gm=smoothstep(0.0,0.25,luma)*(1.0-smoothstep(0.7,1.0,luma));
    color+=g*uGrain*0.08*gm;
    float vig=smoothstep(0.95,0.25,length(toC)*1.41421356); vig=mix(1.0,vig,clamp(uVignette,0.0,1.0)); color*=vig;
    float flash=uFlash*uFlash; color=mix(color,vec3(1.0,0.96,0.90),flash*0.32); color+=vec3(1.0,0.92,0.78)*flash*0.10;
    gl_FragColor=vec4(max(color,0.0),1.0);
  }`;

const CORE_FRAG = /* glsl */ `
  precision highp float;
  varying vec2 vUv;
  uniform float uTime, uPulse, uHeat, uOpacity;
  const vec3 CYAN=vec3(0.129,0.902,0.757); const vec3 VIOLET=vec3(0.424,0.361,0.906);
  const vec3 MAGENTA=vec3(1.000,0.302,0.616); const vec3 GOLD=vec3(1.000,0.722,0.361);
  const vec3 STAR=vec3(0.965,0.980,1.000);
  void main(){
    vec2 p=vUv-0.5; float r=length(p)*2.0; if(r>1.0) discard;
    float pulse=0.5+0.5*sin(uTime*2.2); float energy=clamp(uPulse,0.0,1.6);
    float nucleus=smoothstep(0.34+0.10*pulse,0.0,r); float corona=pow(1.0-r,2.4);
    float rings=0.5+0.5*sin(r*26.0-uTime*5.0-energy*4.0); rings=pow(rings,6.0)*smoothstep(1.0,0.18,r)*(0.35+0.65*energy);
    vec3 cool=mix(CYAN,VIOLET,r); vec3 hot=mix(MAGENTA,GOLD,clamp(r*1.3,0.0,1.0));
    vec3 col=mix(cool,hot,clamp(uHeat+energy*0.4,0.0,1.0)); col=mix(col,STAR,nucleus);
    float a=(corona*(0.4+0.5*energy)+nucleus*1.0+rings*0.7); a*=uOpacity; col*=(0.5+0.6*energy+nucleus*0.7);
    gl_FragColor=vec4(col,clamp(a,0.0,1.0));
  }`;
const CORE_VERT = "varying vec2 vUv; void main(){ vUv=uv; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }";

/* ============================================================================
   GEOMETRY — particle targets, glyph sampler, and procedural shape builders
   ========================================================================== */
const TEXT_OPTS = { font: '900 240px "Times New Roman", Georgia, serif', fitWidth: 17, depth: 1.3, jitter: 0.9 };

function sampleText(str, count, opts = {}) {
  const { font = TEXT_OPTS.font, fitWidth = 18, depth = 2.0, jitter = 1.0, canvasW = 2048, canvasH = 512 } = opts;
  const out = new Float32Array(count * 3);
  const disc = () => { for (let i = 0; i < count; i++) { const r = Math.sqrt(Math.random()) * (fitWidth * 0.5), a = Math.random() * TAU; out[i * 3] = Math.cos(a) * r; out[i * 3 + 1] = Math.sin(a) * r * 0.4; out[i * 3 + 2] = (Math.random() - 0.5) * depth; } return out; };
  if (typeof document === "undefined" || !document.createElement) return disc();
  const cvs = document.createElement("canvas"); cvs.width = canvasW; cvs.height = canvasH;
  const ctx = cvs.getContext("2d", { willReadFrequently: true });
  ctx.clearRect(0, 0, canvasW, canvasH); ctx.fillStyle = "#fff"; ctx.textAlign = "center"; ctx.textBaseline = "middle"; ctx.font = font;
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
    const px = filled[p * 2] + (Math.random() - 0.5) * 2 * jitter, py = filled[p * 2 + 1] + (Math.random() - 0.5) * 2 * jitter;
    const wx = (px - cx) * scale, wy = -(py - cy) * scale, wz = _simplex.noise3d(wx * 0.35, wy * 0.35, 0.0) * depth;
    out[i * 3] = wx; out[i * 3 + 1] = wy; out[i * 3 + 2] = wz;
  }
  return out;
}

/* ---- DNA double helix (index 3) ---- */
function sampleDNA(count) {
  const radius = 2.5, height = 13.5, turns = 4.0, rungEvery = 0.5, thickness = 0.5;
  const out = new Float32Array(count * 3), halfH = height * 0.5;
  for (let i = 0; i < count; i++) {
    const i3 = i * 3, t = Math.random(), ang = t * turns * TAU, y = (t - 0.5) * height;
    if (Math.random() >= 0.20) {
      const a = ang + (Math.random() < 0.5 ? 0 : Math.PI), ja = Math.random() * TAU, jr = Math.cbrt(Math.random()) * thickness;
      out[i3] = Math.cos(a) * radius + Math.cos(ja) * jr; out[i3 + 1] = y + (Math.random() - 0.5) * thickness; out[i3 + 2] = Math.sin(a) * radius + Math.sin(ja) * jr;
    } else {
      const step = Math.round(t * (height / rungEvery)) * rungEvery, ry = Math.min(halfH, Math.max(-halfH, step - halfH));
      const rang = (ry + halfH) / height * turns * TAU, u = Math.random();
      const x0 = Math.cos(rang) * radius, z0 = Math.sin(rang) * radius, x1 = Math.cos(rang + Math.PI) * radius, z1 = Math.sin(rang + Math.PI) * radius;
      out[i3] = x0 + (x1 - x0) * u + (Math.random() - 0.5) * 0.25; out[i3 + 1] = ry + (Math.random() - 0.5) * 0.25; out[i3 + 2] = z0 + (z1 - z0) * u + (Math.random() - 0.5) * 0.25;
    }
  }
  return out;
}
/* ---- Globe / planet shell with noise continents (index 4) ---- */
function sampleGlobe(count) {
  const radius = 7.0, relief = 0.45, freq = 1.35, shell = 0.05, out = new Float32Array(count * 3);
  const GA = Math.PI * (3.0 - Math.sqrt(5.0));
  for (let i = 0; i < count; i++) {
    const i3 = i * 3, tt = (i + 0.5) / count, y = 1.0 - 2.0 * tt, rr0 = Math.sqrt(Math.max(0, 1 - y * y)), phi = i * GA;
    const nx = Math.cos(phi) * rr0, ny = y, nz = Math.sin(phi) * rr0;
    let n = _simplex.noise3d(nx * freq, ny * freq, nz * freq);
    n += 0.5 * _simplex.noise3d(nx * freq * 2.1 + 5.2, ny * freq * 2.1, nz * freq * 2.1 - 3.7);
    n += 0.25 * _simplex.noise3d(nx * freq * 4.3, ny * freq * 4.3 + 1.9, nz * freq * 4.3); n /= 1.75;
    const land = Math.max(0, n), disp = radius + land * relief, rr = disp + (Math.random() - 0.5) * shell;
    out[i3] = nx * rr; out[i3 + 1] = ny * rr; out[i3 + 2] = nz * rr;
  }
  return out;
}
/* ---- 3D heart (index 5) ---- */
function sampleHeart(count) {
  const scale = 0.5, depth = 3.6, fill = 0.55, out = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    const i3 = i * 3, u = Math.random() * TAU;
    const hx = 16 * Math.pow(Math.sin(u), 3), hy = 13 * Math.cos(u) - 5 * Math.cos(2 * u) - 2 * Math.cos(3 * u) - Math.cos(4 * u);
    const rho = Math.sqrt(Math.random()), x = hx * rho * scale, y = (hy + 4.0) * rho * scale;
    const zr = depth * 0.5 * Math.sqrt(Math.max(0, 1 - rho * rho)) * (0.6 + 0.4 * Math.random());
    out[i3] = x; out[i3 + 1] = y; out[i3 + 2] = (Math.random() * 2 - 1) * zr * fill;
  }
  return out;
}
/* ---- Neural / brain connectome (index 6) ---- */
function sampleNeural(count) {
  const rx = 7.4, ry = 5.7, rz = 6.2, HN = 240, edgeFrac = 0.7, warp = 1.4;
  const out = new Float32Array(count * 3), node = new Float32Array(HN * 3), GA = Math.PI * (3.0 - Math.sqrt(5.0));
  for (let h = 0; h < HN; h++) {
    const t = (h + 0.5) / HN, y = 1 - 2 * t, r = Math.sqrt(Math.max(0, 1 - y * y)), phi = h * GA;
    let dx = Math.cos(phi) * r, dy = y, dz = Math.sin(phi) * r;
    const bulge = 1 + warp * 0.12 * _simplex.noise3d(dx * 1.6, dy * 1.6, dz * 1.6), groove = 1 - 0.18 * Math.exp(-(dx * dx) * 6);
    node[h * 3] = dx * rx * bulge * groove; node[h * 3 + 1] = dy * ry * bulge; node[h * 3 + 2] = dz * rz * bulge;
  }
  const K = 3, nn = new Int32Array(HN * K);
  for (let a = 0; a < HN; a++) {
    const ax = node[a * 3], ay = node[a * 3 + 1], az = node[a * 3 + 2], best = [Infinity, Infinity, Infinity], bi = [-1, -1, -1];
    for (let b = 0; b < HN; b++) { if (b === a) continue; const dx = node[b * 3] - ax, dy = node[b * 3 + 1] - ay, dz = node[b * 3 + 2] - az, d = dx * dx + dy * dy + dz * dz;
      if (d < best[0]) { best[2] = best[1]; bi[2] = bi[1]; best[1] = best[0]; bi[1] = bi[0]; best[0] = d; bi[0] = b; }
      else if (d < best[1]) { best[2] = best[1]; bi[2] = bi[1]; best[1] = d; bi[1] = b; } else if (d < best[2]) { best[2] = d; bi[2] = b; } }
    nn[a * K] = bi[0]; nn[a * K + 1] = bi[1]; nn[a * K + 2] = bi[2];
  }
  const nEdge = Math.floor(count * edgeFrac);
  for (let i = 0; i < count; i++) {
    const i3 = i * 3;
    if (i < nEdge) {
      const a = (Math.random() * HN) | 0, nbi = nn[a * K + ((Math.random() * K) | 0)], b = nbi >= 0 ? nbi : (Math.random() * HN) | 0, u = Math.random();
      const bow = Math.sin(u * Math.PI) * 0.6, nxv = _simplex.noise3d(a * 0.7, b * 0.7, u * 3.0);
      out[i3] = node[a * 3] + (node[b * 3] - node[a * 3]) * u + nxv * bow;
      out[i3 + 1] = node[a * 3 + 1] + (node[b * 3 + 1] - node[a * 3 + 1]) * u + bow * 0.5;
      out[i3 + 2] = node[a * 3 + 2] + (node[b * 3 + 2] - node[a * 3 + 2]) * u + (1 - nxv) * bow;
    } else {
      const a = (Math.random() * HN) | 0, blob = 0.5;
      out[i3] = node[a * 3] + (Math.random() - 0.5) * blob; out[i3 + 1] = node[a * 3 + 1] + (Math.random() - 0.5) * blob; out[i3 + 2] = node[a * 3 + 2] + (Math.random() - 0.5) * blob;
    }
  }
  return out;
}
/* ---- Supershape / 3D spirograph (index 7) ---- */
function sampleSupershape(count) {
  const scale = 6.6, m1 = 7, n11 = 0.2, n12 = 1.7, n13 = 1.7, m2 = 7, n21 = 0.2, n22 = 1.7, n23 = 1.7, jitter = 0.05;
  const out = new Float32Array(count * 3), GA = Math.PI * (3.0 - Math.sqrt(5.0));
  const superR = (th, m, n1, n2, n3) => { const t = m * th * 0.25, t1 = Math.pow(Math.abs(Math.cos(t)), n2), t2 = Math.pow(Math.abs(Math.sin(t)), n3), r = Math.pow(t1 + t2, -1 / n1); return isFinite(r) ? r : 0; };
  for (let i = 0; i < count; i++) {
    const i3 = i * 3, k = (i + 0.5) / count, phi = Math.acos(1 - 2 * k) - Math.PI / 2, theta = i * GA;
    const r1 = superR(theta, m1, n11, n12, n13), r2 = superR(phi, m2, n21, n22, n23), cp = Math.cos(phi), j = 1 + (Math.random() - 0.5) * jitter;
    out[i3] = scale * r1 * Math.cos(theta) * r2 * cp * j; out[i3 + 1] = scale * r2 * Math.sin(phi) * j; out[i3 + 2] = scale * r1 * Math.sin(theta) * r2 * cp * j;
  }
  return out;
}

function buildParticles(count, opts = {}) {
  const { text = "GENESIS", sphereR = 6.8, galaxyR = 9.2, torusR = 5.8, torusTube = 1.9, torusP = 2, torusQ = 3 } = opts;
  const geo = new THREE.BufferGeometry();
  const position = new Float32Array(count * 3);
  const aTarget0 = new Float32Array(count * 3), aTarget1 = new Float32Array(count * 3), aTarget2 = new Float32Array(count * 3);
  const aRandom = new Float32Array(count * 3), aSeed = new Float32Array(count);
  const aTarget3 = sampleDNA(count), aTarget4 = sampleGlobe(count), aTarget5 = sampleHeart(count);
  const aTarget6 = sampleNeural(count), aTarget7 = sampleSupershape(count), aTarget8 = sampleText(text, count, TEXT_OPTS);
  const ARMS = 4, SPIN = 1.0, SPREAD = 0.6, GOLDEN = 1.6180339887;
  for (let i = 0; i < count; i++) {
    const i3 = i * 3; aSeed[i] = (i + 0.5) / count;
    const rx = Math.random() * 2 - 1, ry = Math.random() * 2 - 1, rz = Math.random() * 2 - 1;
    aRandom[i3] = rx; aRandom[i3 + 1] = ry; aRandom[i3 + 2] = rz;
    { const t = (i + 0.5) / count, phi = Math.acos(1 - 2 * t), theta = TAU * GOLDEN * i, r = sphereR * (0.08 + 0.92 * Math.pow(Math.random(), 1.7));
      aTarget0[i3] = r * Math.sin(phi) * Math.cos(theta); aTarget0[i3 + 1] = r * Math.cos(phi); aTarget0[i3 + 2] = r * Math.sin(phi) * Math.sin(theta); }
    { const rad = Math.pow(Math.random(), 0.5) * galaxyR, arm = (i % ARMS) / ARMS * TAU, ang = arm + rad * SPIN;
      const sx = (Math.random() - 0.5) * SPREAD * (rad * 0.4 + 0.6), sy = (Math.random() - 0.5) * SPREAD * (rad * 0.4 + 0.6), thin = (Math.random() - 0.5) * 0.6 * (1 - rad / galaxyR * 0.7);
      aTarget1[i3] = Math.cos(ang) * rad + sx; aTarget1[i3 + 1] = thin; aTarget1[i3 + 2] = Math.sin(ang) * rad + sy; }
    { const u = (i / count) * TAU * torusP, qu = u * (torusQ / torusP), cu = Math.cos(u), su = Math.sin(u), cq = Math.cos(qu), baseR = torusR + torusTube * cq, tr = Math.cbrt(Math.random()) * 1.0;
      aTarget2[i3] = baseR * cu + rx * tr; aTarget2[i3 + 1] = torusTube * Math.sin(qu) + ry * tr; aTarget2[i3 + 2] = baseR * su + rz * tr; }
    position[i3] = aTarget0[i3]; position[i3 + 1] = aTarget0[i3 + 1]; position[i3 + 2] = aTarget0[i3 + 2];
  }
  geo.setAttribute("position", new THREE.BufferAttribute(position, 3));
  geo.setAttribute("aTarget0", new THREE.BufferAttribute(aTarget0, 3));
  geo.setAttribute("aTarget1", new THREE.BufferAttribute(aTarget1, 3));
  geo.setAttribute("aTarget2", new THREE.BufferAttribute(aTarget2, 3));
  geo.setAttribute("aTarget3", new THREE.BufferAttribute(aTarget3, 3));
  geo.setAttribute("aTarget4", new THREE.BufferAttribute(aTarget4, 3));
  geo.setAttribute("aTarget5", new THREE.BufferAttribute(aTarget5, 3));
  geo.setAttribute("aTarget6", new THREE.BufferAttribute(aTarget6, 3));
  geo.setAttribute("aTarget7", new THREE.BufferAttribute(aTarget7, 3));
  geo.setAttribute("aTarget8", new THREE.BufferAttribute(aTarget8, 3));
  geo.setAttribute("aRandom", new THREE.BufferAttribute(aRandom, 3));
  geo.setAttribute("aSeed", new THREE.BufferAttribute(aSeed, 1));
  geo.boundingSphere = new THREE.Sphere(new THREE.Vector3(0, 0, 0), 90);
  return geo;
}

function makeSprite() {
  const s = 64, c = document.createElement("canvas"); c.width = c.height = s;
  const g = c.getContext("2d"); const grad = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  grad.addColorStop(0, "rgba(255,255,255,1)"); grad.addColorStop(0.35, "rgba(255,255,255,0.55)"); grad.addColorStop(1, "rgba(255,255,255,0)");
  g.fillStyle = grad; g.fillRect(0, 0, s, s);
  const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.NoColorSpace; return tex;
}
function makeStreakTexture() {
  const w = 16, h = 128, c = document.createElement("canvas"); c.width = w; c.height = h; const g = c.getContext("2d");
  const lg = g.createLinearGradient(0, 0, 0, h); lg.addColorStop(0, "rgba(255,255,255,0)"); lg.addColorStop(0.5, "rgba(255,255,255,1)"); lg.addColorStop(1, "rgba(255,255,255,0)");
  g.fillStyle = lg; g.fillRect(0, 0, w, h);
  const wg = g.createLinearGradient(0, 0, w, 0); wg.addColorStop(0, "rgba(0,0,0,0)"); wg.addColorStop(0.5, "rgba(0,0,0,1)"); wg.addColorStop(1, "rgba(0,0,0,0)");
  g.globalCompositeOperation = "destination-in"; g.fillStyle = wg; g.fillRect(0, 0, w, h);
  const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.NoColorSpace; return tex;
}

/* ============================================================================
   CHOREOGRAPHY KEYFRAMES (scroll s ∈ [0,1])
   ========================================================================== */
// [s, x, y, z, rollDeg, fov]
const CAM_KEYS = [
  [0.00, 0.0, 0.0, 11.0, 0, 58], [0.08, 3.0, 1.2, 11.2, -5, 58], [0.18, -5.0, -1.0, 11.8, 7, 62],
  [0.28, 4.6, 2.0, 11.4, -8, 58], [0.40, -2.4, 0.8, 11.6, 4, 55], [0.50, 0.0, 0.3, 11.8, 0, 54],
  [0.58, 0.0, 0.0, 11.5, 0, 53], [0.66, 0.0, 0.0, 12.5, 0, 52], [0.72, 0.0, 0.0, 12.5, 0, 52],
  [0.80, 0.0, 0.4, 7.5, 0, 50], [0.90, 0.0, 2.4, 15.0, 0, 62], [1.00, 0.0, 1.3, 18.0, 0, 64],
];
const DRIFT_KEYS = [[0.00, 0.30], [0.10, 0.5], [0.16, 1.4], [0.22, 0.45], [0.27, 0.16], [0.32, 0.13], [0.41, 0.13], [0.50, 0.15], [0.55, 0.5], [0.60, 0.2], [0.66, 0.1], [0.80, 0.1], [0.86, 0.6], [1.0, 0.7]];
const BSTR_KEYS = [[0.00, 0.50], [0.18, 0.70], [0.40, 0.60], [0.55, 0.66], [0.62, 0.62], [0.70, 0.58], [0.76, 0.56], [0.80, 0.58], [0.86, 0.60], [1.00, 0.66]];
const BTHR_KEYS = [[0.00, 0.58], [0.10, 0.55], [0.40, 0.54], [0.55, 0.54], [0.66, 0.56], [0.74, 0.54], [0.80, 0.52], [1.00, 0.56]];
const BRAD_KEYS = [[0.00, 0.50], [0.55, 0.60], [0.76, 0.62], [0.82, 0.58], [1.00, 0.60]];
const SCALE_KEYS = [[0.00, 0.95], [0.08, 1.02], [0.30, 0.92], [0.41, 0.90], [0.50, 0.92], [0.58, 0.96], [0.66, 1.0], [0.80, 1.0], [0.86, 1.2], [1.00, 1.45]];
const VIG_KEYS = [[0.00, 0.55], [0.72, 0.50], [0.77, 0.72], [0.85, 0.55], [1.00, 0.50]];
const ABER_KEYS = [[0.00, 0.0], [0.46, 0.0], [0.54, 0.85], [0.62, 0.25], [0.66, 0.08], [0.74, 0.40], [0.80, 0.95], [0.85, 0.18], [1.00, 0.0]];
const MOUSE_KEYS = [[0.00, 0.03], [0.80, 0.03], [0.86, 0.22], [1.00, 0.25]];

// morph stops: [startS, A, B] — mix is local progress to the next stop's startS
// targets: 0 sphere 1 galaxy 2 torus 3 DNA 4 globe 5 heart 6 neural 7 supershape 8 TEXT
const MORPH_STOPS = [
  [0.00, 0, 0], [0.07, 0, 1], [0.13, 1, 1], [0.18, 1, 2], [0.23, 2, 2], [0.27, 2, 3],
  [0.32, 3, 3], [0.36, 3, 4], [0.41, 4, 4], [0.45, 4, 5], [0.50, 5, 5], [0.53, 5, 6],
  [0.58, 6, 6], [0.62, 6, 8], [0.68, 8, 8], [0.80, 8, 7], [0.86, 7, 7], [0.90, 7, 1],
  [1.0001, 1, 1],
];
function morphFromScroll(s) {
  for (let i = 0; i < MORPH_STOPS.length - 1; i++) {
    const a = MORPH_STOPS[i], b = MORPH_STOPS[i + 1];
    if (s >= a[0] && s < b[0]) return { A: a[1], B: a[2], mix: clamp01((s - a[0]) / (b[0] - a[0])) };
  }
  return { A: 1, B: 1, mix: 1 };
}

/* ============================================================================
   BOOT
   ========================================================================== */
const canvas = document.getElementById("stage");
let degraded = false;

try { init(); }
catch (err) {
  console.warn("PROMPTFIELD scene disabled (fallback):", err);
  document.body.classList.add("no-webgl"); window.__webglLost = true;
  window.dispatchEvent(new Event("scene:ready"));
}

function init() {
  const small = Math.min(window.innerWidth, window.innerHeight) < 700 || window.matchMedia("(pointer: coarse)").matches;
  const dm = navigator.deviceMemory || 4, hc = navigator.hardwareConcurrency || 4;
  let COUNT = 110000;
  if (small || dm <= 2) COUNT = 42000; else if (dm <= 4 || hc <= 4) COUNT = 75000;
  window.__particles = COUNT;

  const probe = typeof location !== "undefined" && /[?&]probe/.test(location.search); // test-only: allows pixel readback
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: "high-performance", alpha: false, preserveDrawingBuffer: probe });
  let dpr = Math.min(window.devicePixelRatio || 1, small ? 1.5 : 1.75);
  renderer.setPixelRatio(dpr); renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.setClearColor(0x05060a, 1); renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.0;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 600);
  camera.position.set(0, 0, 12);

  /* --- background aurora --- */
  const bgUniforms = { uTime: { value: 0 }, uRes: { value: new THREE.Vector2(window.innerWidth, window.innerHeight) }, uMouse: { value: new THREE.Vector2(0, 0) }, uScroll: { value: 0 }, uVelocity: { value: 0 } };
  const bgMesh = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), new THREE.ShaderMaterial({ vertexShader: BG_VERT, fragmentShader: BG_FRAG, uniforms: bgUniforms, depthTest: false, depthWrite: false, transparent: false }));
  bgMesh.frustumCulled = false; bgMesh.renderOrder = -10; scene.add(bgMesh);

  /* --- particle organism --- */
  const sprite = makeSprite();
  const pUniforms = {
    uTargetA: { value: 0 }, uTargetB: { value: 0 }, uMorph: { value: 0 },
    uTime: { value: 0 }, uSize: { value: 48 * (small ? 0.8 : 1) }, uScale: { value: 1 }, uDrift: { value: 0.3 },
    uProgress: { value: 0 }, uVelocity: { value: 0 }, uHeat: { value: 0 },
    uMouse: { value: new THREE.Vector2(0, 0) }, uMouseStrength: { value: 0.03 },
    uResolution: { value: new THREE.Vector2(window.innerWidth, window.innerHeight) }, uOpacity: { value: 1 }, uSprite: { value: sprite },
  };
  const geometry = buildParticles(COUNT, { text: "GENESIS" });
  const pMat = new THREE.ShaderMaterial({
    uniforms: pUniforms, vertexShader: PARTICLE_VERT,
    fragmentShader: PARTICLE_FRAG
      .replace("vec2 uv=gl_PointCoord-0.5; float d=length(uv); if(d>0.5) discard;", "vec2 uv=gl_PointCoord-0.5; float d=length(uv); if(d>0.5) discard; float spr=texture2D(uSprite,gl_PointCoord).a;")
      .replace("float alpha=(halo*0.65+glow*0.45)*uOpacity;", "float alpha=(halo*0.65+glow*0.45)*spr*uOpacity;")
      .replace("uniform float uHeat;", "uniform sampler2D uSprite; uniform float uHeat;"),
    blending: THREE.AdditiveBlending, depthWrite: false, depthTest: false, transparent: true,
  });
  const points = new THREE.Points(geometry, pMat); points.frustumCulled = false; scene.add(points);

  /* --- immersive light tunnel: rings + velocity-stretched glow streaks --- */
  const TUN_N = small ? 64 : 140, TUN_SPREAD = 3.2, TUN_RANGE = TUN_N * TUN_SPREAD, TUN_R = 7.8, STREAK_N = small ? 36 : 80;
  const ringGeo = new THREE.TorusGeometry(TUN_R, 0.05, 6, 110);
  const ringMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false, depthTest: false });
  const rings = new THREE.InstancedMesh(ringGeo, ringMat, TUN_N); rings.frustumCulled = false; rings.renderOrder = -2;
  const cCyan = new THREE.Color(0x21e6c1), cMag = new THREE.Color(0xff4d9d), cViolet = new THREE.Color(0x6c5cff);
  for (let i = 0; i < TUN_N; i++) { const k = i % 3; rings.setColorAt(i, k === 0 ? cCyan : k === 1 ? cMag : cViolet); }
  if (rings.instanceColor) rings.instanceColor.needsUpdate = true; scene.add(rings);

  const streakTex = makeStreakTexture();
  const streakGeo = new THREE.PlaneGeometry(0.55, 5.0);
  const streakMat = new THREE.MeshBasicMaterial({ map: streakTex, color: 0xbfeaff, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false, depthTest: false });
  const streaks = new THREE.InstancedMesh(streakGeo, streakMat, STREAK_N); streaks.frustumCulled = false; streaks.renderOrder = -2; scene.add(streaks);
  const _streakAng = new Float32Array(STREAK_N), _streakRad = new Float32Array(STREAK_N), _streakZ0 = new Float32Array(STREAK_N);
  for (let i = 0; i < STREAK_N; i++) { _streakAng[i] = Math.random() * TAU; _streakRad[i] = TUN_R * (0.45 + 0.5 * Math.random()); _streakZ0[i] = Math.random() * TUN_RANGE; }
  const _dummy = new THREE.Object3D();

  /* --- reactive energy core --- */
  const coreUniforms = { uTime: { value: 0 }, uPulse: { value: 0 }, uHeat: { value: 0 }, uOpacity: { value: 0 } };
  const coreMat = new THREE.ShaderMaterial({ uniforms: coreUniforms, vertexShader: CORE_VERT, fragmentShader: CORE_FRAG, blending: THREE.AdditiveBlending, depthWrite: false, depthTest: false, transparent: true });
  const coreMesh = new THREE.Mesh(new THREE.PlaneGeometry(5, 5), coreMat); coreMesh.frustumCulled = false; coreMesh.renderOrder = -1; scene.add(coreMesh);
  let corePulse = 0;

  /* --- post chain: Render → Bloom → Grade → Output → SMAA --- */
  const composer = new EffectComposer(renderer);
  composer.addPass(new RenderPass(scene, camera));
  const bloom = new UnrealBloomPass(new THREE.Vector2(window.innerWidth, window.innerHeight), 0.6, 0.6, 0.4);
  composer.addPass(bloom);
  const gradePass = new ShaderPass({
    uniforms: {
      tDiffuse: { value: null }, uTime: { value: 0 }, uAberration: { value: 0 }, uVignette: { value: 0.55 },
      uGrain: { value: PF.reduceMotion ? 0 : 0.5 }, uResolution: { value: new THREE.Vector2(window.innerWidth * dpr, window.innerHeight * dpr) },
      uShock: { value: 0 }, uStreak: { value: 0 }, uFlash: { value: 0 }, uLensWarp: { value: 0 },
    },
    vertexShader: "varying vec2 vUv; void main(){ vUv=uv; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }",
    fragmentShader: GRADE_FRAG,
  });
  composer.addPass(gradePass);
  composer.addPass(new OutputPass());
  const smaa = new SMAAPass(window.innerWidth, window.innerHeight); composer.addPass(smaa);

  /* --- spectacle impulses (decaying envelopes + threshold crossings) --- */
  const fx = { shock: 0, flash: 0, shockHalf: 0.55, flashHalf: 0.28 };
  const fireShock = (st) => { fx.shock = Math.max(fx.shock, st == null ? 1 : st); };
  const fireFlash = (st) => { fx.flash = Math.max(fx.flash, st == null ? 1 : st); };
  const fireWow = () => { fireShock(1.0); fireFlash(1.0); };
  const SECTION_THRESHOLDS = [0.16, 0.24, 0.32, 0.41, 0.50, 0.58, 0.80];
  let _lastS = clamp01(PF.progress), _ignited = false;
  function detectCrossings(s) {
    for (let i = 0; i < SECTION_THRESHOLDS.length; i++) { const t = SECTION_THRESHOLDS[i]; if ((_lastS < t && s >= t) || (_lastS > t && s <= t)) fireShock(0.6); }
    _lastS = s;
  }

  /* --- interaction --- */
  const mouse = new THREE.Vector2(0, 0), mouseT = new THREE.Vector2(0, 0);
  window.addEventListener("pointermove", (e) => { mouseT.set((e.clientX / window.innerWidth) * 2 - 1, -(e.clientY / window.innerHeight) * 2 + 1); }, { passive: true });

  /* --- live re-forge (§ Your turn) --- */
  const reform = { active: false, t: 0, dur: 1.8, lock: false, lockProg: 0 };
  PF.scene = {
    formedWord: "GENESIS",
    submitWord(word) {
      word = (word || "").toString().trim().toUpperCase().slice(0, 14); if (!word) word = "AI";
      const pts = sampleText(word, COUNT, TEXT_OPTS);
      geometry.attributes.aTarget8.array.set(pts); geometry.attributes.aTarget8.needsUpdate = true;
      PF.scene.formedWord = word; reform.active = true; reform.t = 0; reform.lock = false; fireWow();
    },
  };

  /* --- resize --- */
  function resize() {
    const w = window.innerWidth, h = window.innerHeight;
    dpr = Math.min(window.devicePixelRatio || 1, small ? 1.5 : 1.75); if (degraded) dpr = Math.min(dpr, 1.0);
    renderer.setPixelRatio(dpr); renderer.setSize(w, h);
    camera.aspect = w / h; camera.updateProjectionMatrix();
    composer.setSize(w, h); bloom.setSize(w, h);
    bgUniforms.uRes.value.set(w, h); pUniforms.uResolution.value.set(w, h); gradePass.uniforms.uResolution.value.set(w * dpr, h * dpr);
  }
  window.addEventListener("resize", resize, { passive: true });
  canvas.addEventListener("webglcontextlost", (e) => { e.preventDefault(); window.__webglLost = true; running = false; }, false);

  /* --- render loop --- */
  const clock = new THREE.Clock();
  const camPos = new THREE.Vector3(), camWord = new THREE.Vector3(0, 0.3, 12.5);
  let running = true, ready = false, frames = 0, fpsAcc = 0, lowStreak = 0;

  function frame() {
    if (!running) return;
    requestAnimationFrame(frame);
    const dt = Math.min(clock.getDelta(), 0.05);
    const s = clamp01(PF.progress), vel = clamp01(PF.velocity);
    const timeScale = PF.reduceMotion ? 0.15 : 1.0;
    pUniforms.uTime.value += dt * timeScale; bgUniforms.uTime.value += dt * timeScale;
    if (!PF.reduceMotion) gradePass.uniforms.uTime.value += dt;

    mouse.lerp(mouseT, 0.08); PF.mouse.x = mouse.x; PF.mouse.y = mouse.y;
    pUniforms.uMouse.value.copy(mouse); bgUniforms.uMouse.value.lerp(mouseT, 0.04);
    bgUniforms.uScroll.value = s; bgUniforms.uVelocity.value = vel;
    pUniforms.uProgress.value = s; pUniforms.uVelocity.value = PF.reduceMotion ? 0 : vel;

    // morph (scroll, or live re-forge override)
    let A, B, mix, wordView = 0;
    if (reform.active) {
      reform.t += dt; const k = clamp01(reform.t / reform.dur); A = 1; B = 8; mix = smooth(k); wordView = 1;
      pUniforms.uHeat.value = lerp(pUniforms.uHeat.value, 0.35 * Math.sin(k * Math.PI), 0.2);
      if (k >= 1) { reform.active = false; reform.lock = true; reform.lockProg = s; }
    } else if (reform.lock) {
      A = 8; B = 8; mix = 1; wordView = 1; pUniforms.uHeat.value = lerp(pUniforms.uHeat.value, 0.0, 0.05);
      if (Math.abs(s - reform.lockProg) > 0.015) reform.lock = false;
    } else {
      const m = morphFromScroll(s); A = m.A; B = m.B; mix = m.mix; pUniforms.uHeat.value = smooth((s - 0.72) / 0.05);
    }
    PF.forging = reform.active || reform.lock;
    pUniforms.uTargetA.value = A; pUniforms.uTargetB.value = B; pUniforms.uMorph.value = mix;
    pUniforms.uDrift.value = PF.reduceMotion ? 0.06 : sampleScalar(DRIFT_KEYS, s);
    pUniforms.uScale.value = wordView ? lerp(pUniforms.uScale.value, 1.0, 0.1) : sampleScalar(SCALE_KEYS, s);
    // shrink points while the swarm is the WORD so dense glyph strokes read as crisp letters, not a blob
    const wAmt = wordView ? 1 : clamp01(Math.min(smooth((s - 0.62) / 0.04), smooth((0.82 - s) / 0.04)));
    pUniforms.uSize.value = (48 * (small ? 0.8 : 1)) * (1 - 0.18 * wAmt);
    pUniforms.uMouseStrength.value = sampleScalar(MOUSE_KEYS, s);

    // camera path + fov + roll + parallax
    sampleVec3(CAM_KEYS, s, camPos);
    if (wordView) camPos.lerp(camWord, 0.85);
    const targetFov = camCol(CAM_KEYS, s, 5); camera.fov += (targetFov - camera.fov) * 0.1; camera.updateProjectionMatrix();
    if (!PF.reduceMotion) { camPos.x += mouse.x * 1.2; camPos.y += mouse.y * 0.85; }
    camera.position.lerp(camPos, ready ? 0.12 : 1.0);
    camera.lookAt(0, 0, 0);
    const rollRad = camCol(CAM_KEYS, s, 4) * Math.PI / 180 + (PF.reduceMotion ? 0 : mouse.x * 0.05);
    camera.rotateZ(rollRad);

    // global volumetric rotation of the cloud (frozen while a word is formed)
    if (!PF.reduceMotion) {
      const spin = PF.forging ? 0.04 : 1.0, t = pUniforms.uTime.value;
      points.rotation.y = t * 0.045 * spin; points.rotation.x = Math.sin(t * 0.13) * 0.08 * spin; points.rotation.z = Math.cos(t * 0.09) * 0.05 * spin;
    } else points.rotation.set(0, 0, 0);

    // energy core — billboard, pulses with velocity + ignition
    coreMesh.quaternion.copy(camera.quaternion);
    const igniteAmt = Math.max(0, Math.min(smooth((s - 0.77) / 0.04), smooth((0.90 - s) / 0.05)));
    corePulse = lerp(corePulse, vel * 1.3 + pUniforms.uHeat.value * 0.9, 0.12);
    coreUniforms.uTime.value = pUniforms.uTime.value; coreUniforms.uPulse.value = corePulse; coreUniforms.uHeat.value = pUniforms.uHeat.value;
    coreUniforms.uOpacity.value = (0.02 + 0.24 * igniteAmt) * (PF.reduceMotion ? 0.4 : 1.0);
    coreMesh.scale.setScalar(1.0 + corePulse * 0.5 + igniteAmt * 0.4); coreMesh.visible = coreUniforms.uOpacity.value > 0.01;

    // light tunnel — flies through during the pipeline → slam window
    const ringAmt = Math.max(0, Math.min(smooth((s - 0.585) / 0.025), smooth((0.66 - s) / 0.025)));
    const tunOn = ringAmt > 0.002;
    ringMat.opacity = ringAmt * 0.9; streakMat.opacity = ringAmt * (0.25 + 0.75 * vel);
    rings.visible = tunOn; streaks.visible = tunOn;
    if (tunOn) {
      const time = pUniforms.uTime.value, flow = time * (24 + vel * 60) + s * 320, stretch = 1.0 + vel * 3.5;
      for (let i = 0; i < TUN_N; i++) {
        let z = 12 - (((i * TUN_SPREAD - flow) % TUN_RANGE) + TUN_RANGE) % TUN_RANGE;
        const ang = i * 0.5 + time * 0.5, off = 1.4 + 0.3 * Math.sin(time * 0.6 + i), brZ = 1.0 + 0.18 * Math.sin(time * 1.3 + i);
        _dummy.position.set(Math.cos(ang) * off, Math.sin(ang) * off, z); _dummy.rotation.set(0, 0, ang); _dummy.scale.set(brZ, brZ, 1);
        _dummy.updateMatrix(); rings.setMatrixAt(i, _dummy.matrix);
      }
      rings.instanceMatrix.needsUpdate = true;
      for (let i = 0; i < STREAK_N; i++) {
        let z = 12 - (((_streakZ0[i] - flow) % TUN_RANGE) + TUN_RANGE) % TUN_RANGE;
        const a = _streakAng[i] + time * 0.2, rad = _streakRad[i];
        _dummy.position.set(Math.cos(a) * rad, Math.sin(a) * rad, z); _dummy.rotation.set(0, 0, a + Math.PI * 0.5); _dummy.scale.set(1, stretch, 1);
        _dummy.updateMatrix(); streaks.setMatrixAt(i, _dummy.matrix);
      }
      streaks.instanceMatrix.needsUpdate = true;
    }

    // bloom + grade per-frame
    bloom.strength = sampleScalar(BSTR_KEYS, s) + (PF.reduceMotion ? 0 : vel * 0.15);
    bloom.threshold = sampleScalar(BTHR_KEYS, s); bloom.radius = sampleScalar(BRAD_KEYS, s);
    gradePass.uniforms.uVignette.value = sampleScalar(VIG_KEYS, s);

    // spectacle impulses
    fx.shock = Math.max(0, fx.shock - dt / fx.shockHalf); fx.flash = Math.max(0, fx.flash - dt / fx.flashHalf);
    if (!PF.reduceMotion) {
      detectCrossings(s);
      if (!_ignited && s >= 0.78) { fireWow(); _ignited = true; }
      if (_ignited && s < 0.72) _ignited = false;
    }
    const tunnelStreak = Math.max(0, Math.min(smooth((s - 0.585) / 0.025), smooth((0.66 - s) / 0.025)));
    const igniteStreak = smooth((s - 0.77) / 0.04) * (1.0 - smooth((s - 0.88) / 0.05));
    let streak = clamp01(Math.max(tunnelStreak * 0.22, igniteStreak * 0.18) + vel * 0.1);
    const fxOff = PF.reduceMotion ? 0 : 1;
    gradePass.uniforms.uShock.value = fx.shock * fxOff;
    gradePass.uniforms.uStreak.value = streak * fxOff;
    gradePass.uniforms.uFlash.value = fx.flash * fxOff;
    gradePass.uniforms.uLensWarp.value = clamp01(vel * 0.6 + fx.shock * 0.4) * fxOff;
    gradePass.uniforms.uAberration.value = PF.reduceMotion ? 0 : clamp01(sampleScalar(ABER_KEYS, s) + vel * 0.7);

    composer.render(dt);

    if (!ready) { ready = true; PF.ready = true; window.dispatchEvent(new Event("scene:ready")); }

    frames++; fpsAcc += dt;
    if (fpsAcc >= 0.5) {
      const fps = Math.round(frames / fpsAcc); frames = 0; fpsAcc = 0;
      PF.fps = fps; window.__fps = fps; PF.drawCalls = renderer.info.render.calls;
      if (!degraded) { if (fps < 32) lowStreak++; else lowStreak = 0; if (lowStreak >= 3) { degraded = true; gradePass.uniforms.uGrain.value = 0; resize(); } }
    }
  }

  resize(); frame();

  PF.scene.dispose = () => {
    running = false; geometry.dispose(); pMat.dispose(); sprite.dispose();
    ringGeo.dispose(); ringMat.dispose(); streakGeo.dispose(); streakMat.dispose(); streakTex.dispose();
    coreMesh.geometry.dispose(); coreMat.dispose(); bgMesh.geometry.dispose(); bgMesh.material.dispose();
    composer.dispose(); renderer.dispose();
  };
}
