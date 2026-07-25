/* =========================================================================
   Part 2 / 5 — the world beyond the glass: overcast sky, city, rain, lightning
   ========================================================================= */
import * as THREE from 'three';
import { GLSL_NOISE, U, ROOM, rnd, rrnd, clamp, outsideScene } from './room.js';

const FOG = /* glsl */`
uniform vec3  uFogColor;
uniform vec3  uFogGround;
uniform float uFogDens;
vec3 applyFog(vec3 col, vec3 worldPos, float dist){
  float f = 1.0 - exp(-uFogDens * uFogDens * dist * dist);
  // fog warms and thickens toward the street far below
  float low = smoothstep(40.0, -140.0, worldPos.y);
  vec3 fc = mix(uFogColor, uFogGround, low * 0.85);
  fc += uFogGround * 0.5 * low;
  return mix(col, fc, clamp(f, 0.0, 1.0));
}
`;

/* one shared set of fog uniforms so every outdoor material stays in sync */
export const FOG_U = {
  uFogColor:  { value: new THREE.Color(0x0b1018) },
  uFogGround: { value: new THREE.Color(0x160e09) },
  uFogDens:   { value: 0.0027 },
};
const fogUniforms = () => FOG_U;

/* ------------------------------------------------------------------- sky */
export function buildSky() {
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide,
    depthWrite: false,
    uniforms: {
      uTime:  U.time,
      uFlash: U.flash,
      uRain:  U.rain,
    },
    vertexShader: /* glsl */`
      varying vec3 vDir;
      void main(){
        vDir = position;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }`,
    fragmentShader: GLSL_NOISE + /* glsl */`
      varying vec3 vDir;
      uniform float uTime, uFlash, uRain;
      void main(){
        vec3 d = normalize(vDir);
        float h = d.y;

        vec3 zenith  = vec3(0.0060, 0.0085, 0.0150);
        vec3 horizon = vec3(0.0300, 0.0250, 0.0250);
        vec3 col = mix(horizon, zenith, smoothstep(-0.04, 0.62, h));

        // overcast deck — flattened fbm, drifting
        vec2 cp = d.xz / max(abs(h) + 0.20, 0.20);
        float cl = fbm2(cp * 1.15 + vec2(uTime * 0.0075, uTime * 0.0032));
        cl = smoothstep(0.30, 0.86, cl);
        float deck = smoothstep(-0.02, 0.30, h);
        vec3 cloudLit = mix(vec3(0.012,0.014,0.021), vec3(0.052,0.042,0.038), cl);
        col = mix(col, cloudLit, deck * 0.92);

        // sodium light-pollution glow smeared along the horizon
        float glow = exp(-pow(max(h, -0.05) / 0.085, 2.0));
        col += vec3(0.078, 0.034, 0.011) * glow * (0.62 + 0.38 * fbm2(d.xz * 2.2 + 11.0));

        // lightning: lights the cloud base from inside, hottest where cloud is dense
        float bolt = uFlash * (0.35 + 0.95 * cl) * deck;
        col += vec3(0.62, 0.70, 0.92) * bolt;
        col += vec3(0.30, 0.38, 0.55) * uFlash * 0.25;

        // below the horizon: haze over the city floor
        col = mix(col, vec3(0.022, 0.017, 0.015), smoothstep(0.0, -0.22, h));

        // rain veil desaturates everything a touch
        col = mix(col, vec3(dot(col, vec3(0.299,0.587,0.114))), uRain * 0.13);
        gl_FragColor = vec4(col, 1.0);
      }`,
  });
  const sky = new THREE.Mesh(new THREE.SphereGeometry(2400, 40, 26), mat);
  sky.frustumCulled = false;
  sky.renderOrder = -100;
  outsideScene.add(sky);
  return sky;
}

/* ------------------------------------------------------------- city floor */
export function buildGround() {
  const mat = new THREE.ShaderMaterial({
    uniforms: { uTime: U.time, uFlash: U.flash, ...fogUniforms() },
    vertexShader: /* glsl */`
      varying vec3 vW;
      void main(){
        vec4 w = modelMatrix * vec4(position, 1.0);
        vW = w.xyz;
        gl_Position = projectionMatrix * viewMatrix * w;
      }`,
    fragmentShader: GLSL_NOISE + FOG + /* glsl */`
      varying vec3 vW;
      uniform float uTime, uFlash;
      void main(){
        vec2 p = vW.xz;
        // wet asphalt base
        float n = fbm2(p * 0.004);
        vec3 col = vec3(0.020, 0.021, 0.026) * (0.6 + n * 0.9);

        // street grid, glowing sodium
        vec2 g = abs(fract(p / 78.0) - 0.5);
        float street = smoothstep(0.5, 0.47, max(g.x, g.y));
        float lamps  = smoothstep(0.6, 1.0, fbm2(p * 0.05));
        col += vec3(0.55, 0.26, 0.09) * street * (0.35 + lamps * 0.9);

        // a few brighter arterials
        vec2 g2 = abs(fract(p / 312.0) - 0.5);
        col += vec3(0.42, 0.30, 0.16) * smoothstep(0.5, 0.485, max(g2.x, g2.y)) * 0.9;

        col += vec3(0.30, 0.36, 0.50) * uFlash * 0.25;
        float dist = length(vW - cameraPosition);
        gl_FragColor = vec4(applyFog(col, vW, dist), 1.0);
      }`,
  });
  const g = new THREE.Mesh(new THREE.CircleGeometry(2100, 48), mat);
  g.rotation.x = -Math.PI / 2;
  g.position.y = -ROOM.alt;
  outsideScene.add(g);
  return g;
}

/* ----------------------------------------------------------------- towers */
export function buildCity(maxCount) {
  const geo = new THREE.BoxGeometry(1, 1, 1);
  const mat = new THREE.ShaderMaterial({
    uniforms: { uTime: U.time, uFlash: U.flash, uRain: U.rain, ...fogUniforms() },
    vertexShader: /* glsl */`
      attribute float aId;
      varying vec3 vLocal, vNrm, vW, vScale;
      varying float vId;
      void main(){
        vScale = vec3(length(instanceMatrix[0].xyz), length(instanceMatrix[1].xyz), length(instanceMatrix[2].xyz));
        vLocal = position;
        vNrm   = normal;
        vId    = aId;
        vec4 w = modelMatrix * instanceMatrix * vec4(position, 1.0);
        vW = w.xyz;
        gl_Position = projectionMatrix * viewMatrix * w;
      }`,
    fragmentShader: GLSL_NOISE + FOG + /* glsl */`
      varying vec3 vLocal, vNrm, vW, vScale;
      varying float vId;
      uniform float uTime, uFlash, uRain;

      void main(){
        vec3 n = normalize(vNrm);
        bool roof = abs(n.y) > 0.5;

        // concrete / glass shell, faintly lit by the overcast sky from above
        float up = clamp(n.y * 0.5 + 0.5, 0.0, 1.0);
        vec3 col = mix(vec3(0.016,0.019,0.026), vec3(0.055,0.060,0.075), up);
        col *= 0.75 + 0.5 * hash11(vId * 3.1);

        if(!roof){
          // metres along the facade → constant-size windows regardless of tower size
          vec2 uvw = (abs(n.x) > 0.5)
            ? vec2(vLocal.z * vScale.z, vLocal.y * vScale.y)
            : vec2(vLocal.x * vScale.x, vLocal.y * vScale.y);
          vec2 CELL = vec2(3.4, 3.8);
          vec2 cid  = floor(uvw / CELL);
          vec2 f    = fract(uvw / CELL);

          float pane = step(0.13, f.x) * step(f.x, 0.87) * step(0.20, f.y) * step(f.y, 0.82);
          float r    = hash12(cid + vId * 41.7);
          float r2   = hash12(cid.yx + vId * 13.3);

          // ~36% of panes lit; a slow cycle turns a few on and off
          float slow = step(0.985, hash11(floor(uTime * 0.07 + r2 * 40.0) + r * 97.0));
          float lit  = step(0.70, r) * (1.0 - slow * 0.85);

          vec3 warm = vec3(1.00, 0.63, 0.28);
          vec3 cool = vec3(0.62, 0.80, 1.00);
          vec3 tint = mix(warm, cool, step(0.72, r2));
          float energy = (0.45 + 0.85 * r2);
          // faint per-window flicker (fluorescent hum / people moving)
          energy *= 0.90 + 0.10 * sin(uTime * (1.7 + r * 5.0) + r2 * 30.0);

          col += tint * pane * lit * energy * 0.62;
          // window recess shadow
          col *= 1.0 - (1.0 - pane) * 0.35;
        } else {
          col *= 0.5;
        }

        col += vec3(0.34, 0.40, 0.55) * uFlash * (0.25 + up * 0.5);

        float dist = length(vW - cameraPosition);
        col = applyFog(col, vW, dist);
        gl_FragColor = vec4(col, 1.0);
      }`,
  });

  const mesh = new THREE.InstancedMesh(geo, mat, maxCount);
  mesh.frustumCulled = false;
  const ids = new Float32Array(maxCount);
  const m = new THREE.Matrix4(), q = new THREE.Quaternion(), s = new THREE.Vector3(), p = new THREE.Vector3();

  for (let i = 0; i < maxCount; i++) {
    // denser ring near the tower we are standing in, thinning outward
    const t = Math.pow(rnd(), 0.55);
    const r = 95 + t * 1100;
    const a = rnd() * Math.PI * 2;
    const w = rrnd(16, 46) * (1 + t * 0.7);
    const d = w * rrnd(0.7, 1.35);
    // the closer towers are the tall ones, so we get neighbours at eye level
    const hMax = r < 330 ? rrnd(110, 300) : rrnd(30, 190);
    const h = Math.max(24, hMax * (0.5 + rnd() * 0.5));
    p.set(Math.cos(a) * r, -ROOM.alt + h / 2, Math.sin(a) * r);
    s.set(w, h, d);
    q.setFromAxisAngle(new THREE.Vector3(0, 1, 0), rnd() * Math.PI);
    m.compose(p, q, s);
    mesh.setMatrixAt(i, m);
    ids[i] = i;
  }
  geo.setAttribute('aId', new THREE.InstancedBufferAttribute(ids, 1));
  mesh.instanceMatrix.needsUpdate = true;
  outsideScene.add(mesh);
  return mesh;
}

/* --------------------------------------------- rooftop aviation beacons */
export function buildBeacons(city, count = 46) {
  const geo = new THREE.PlaneGeometry(1, 1);
  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: { uTime: U.time },
    vertexShader: /* glsl */`
      attribute vec4 aPos;   // xyz = world position, w = phase
      varying vec2 vUv; varying float vPhase;
      void main(){
        vUv = uv; vPhase = aPos.w;
        vec3 c = aPos.xyz;
        float d = length(c - cameraPosition);
        float size = clamp(d * 0.0032, 0.6, 5.0);
        vec3 right = normalize(vec3(viewMatrix[0][0], viewMatrix[1][0], viewMatrix[2][0]));
        vec3 up    = normalize(vec3(viewMatrix[0][1], viewMatrix[1][1], viewMatrix[2][1]));
        vec3 w = c + (right * position.x + up * position.y) * size;
        gl_Position = projectionMatrix * viewMatrix * vec4(w, 1.0);
      }`,
    fragmentShader: /* glsl */`
      varying vec2 vUv; varying float vPhase;
      uniform float uTime;
      void main(){
        float d = length(vUv - 0.5) * 2.0;
        float a = smoothstep(1.0, 0.0, d);
        a = pow(a, 2.4);
        float blink = smoothstep(0.55, 0.95, sin(uTime * 1.5 + vPhase * 6.283) * 0.5 + 0.5);
        gl_FragColor = vec4(vec3(1.0, 0.13, 0.07) * (0.25 + blink * 2.4), a);
      }`,
  });

  const g = new THREE.InstancedBufferGeometry();
  g.index = geo.index;
  g.setAttribute('position', geo.attributes.position);
  g.setAttribute('uv', geo.attributes.uv);
  const arr = new Float32Array(count * 4);
  const m = new THREE.Matrix4(), p = new THREE.Vector3(), q = new THREE.Quaternion(), s = new THREE.Vector3();
  let n = 0;
  for (let i = 0; i < city.count && n < count; i += Math.max(1, (city.count / count) | 0)) {
    city.getMatrixAt(i, m); m.decompose(p, q, s);
    if (s.y < 90) continue;
    arr[n * 4 + 0] = p.x; arr[n * 4 + 1] = p.y + s.y / 2 + 1.5; arr[n * 4 + 2] = p.z; arr[n * 4 + 3] = rnd();
    n++;
  }
  g.setAttribute('aPos', new THREE.InstancedBufferAttribute(arr, 4));
  g.instanceCount = n;
  const mesh = new THREE.Mesh(g, mat);
  mesh.frustumCulled = false;
  mesh.renderOrder = 5;
  outsideScene.add(mesh);
  return mesh;
}

/* ------------------------------------------------------------ falling rain */
export function buildRain(maxCount) {
  const plane = new THREE.PlaneGeometry(1, 1);
  const g = new THREE.InstancedBufferGeometry();
  g.index = plane.index;
  g.setAttribute('position', plane.attributes.position);
  g.setAttribute('uv', plane.attributes.uv);

  const seeds = new Float32Array(maxCount * 4);
  for (let i = 0; i < maxCount; i++) {
    // annulus around the building so no drop is ever inside the room
    const a = rnd() * Math.PI * 2;
    const r = 7.5 + Math.pow(rnd(), 0.7) * 46;
    seeds[i * 4 + 0] = Math.cos(a) * r;
    seeds[i * 4 + 1] = rnd();                 // phase
    seeds[i * 4 + 2] = Math.sin(a) * r;
    seeds[i * 4 + 3] = rnd();                 // size / speed jitter
  }
  g.setAttribute('aSeed', new THREE.InstancedBufferAttribute(seeds, 4));
  g.instanceCount = maxCount;

  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: {
      uTime: U.time, uRain: U.rain, uFlash: U.flash,
      uFall: { value: new THREE.Vector3(0.24, -1.0, 0.07).normalize() },
      uSpan: { value: 52.0 },
    },
    vertexShader: /* glsl */`
      attribute vec4 aSeed;
      uniform float uTime, uRain, uSpan;
      uniform vec3  uFall;
      varying vec2 vUv; varying float vFade;
      void main(){
        float speed = 26.0 * (0.7 + 0.55 * aSeed.w) * (0.55 + 0.75 * uRain);
        float y = fract(aSeed.y - uTime * speed / uSpan) * uSpan - uSpan * 0.42;
        vec3 c = vec3(aSeed.x, y, aSeed.z) + uFall * (fract(aSeed.w * 7.3) * 4.0);

        vec3 axis  = normalize(uFall);
        vec3 toCam = normalize(cameraPosition - c);
        vec3 side  = normalize(cross(axis, toCam) + vec3(1e-5));

        float dist = length(cameraPosition - c);
        float len  = (0.55 + 1.35 * aSeed.w) * (0.55 + 0.9 * uRain) * clamp(dist * 0.055, 0.6, 3.4);
        float wid  = clamp(dist * 0.0016, 0.006, 0.05);

        vec3 w = c + side * position.x * wid + axis * position.y * len;
        vFade = smoothstep(70.0, 12.0, dist) * smoothstep(1.5, 4.0, dist);
        vUv = uv;
        gl_Position = projectionMatrix * viewMatrix * vec4(w, 1.0);
      }`,
    fragmentShader: /* glsl */`
      varying vec2 vUv; varying float vFade;
      uniform float uRain, uFlash;
      void main(){
        float x = abs(vUv.x - 0.5) * 2.0;
        float a = smoothstep(1.0, 0.0, x);
        a *= smoothstep(0.0, 0.32, vUv.y) * smoothstep(1.0, 0.5, vUv.y);
        a *= vFade * (0.16 + 0.20 * uRain);
        vec3 col = mix(vec3(0.52,0.60,0.74), vec3(1.0,0.72,0.42), 0.22);
        col += vec3(0.4,0.5,0.7) * uFlash * 2.0;
        gl_FragColor = vec4(col, a);
      }`,
  });

  const mesh = new THREE.Mesh(g, mat);
  mesh.frustumCulled = false;
  mesh.renderOrder = 4;
  outsideScene.add(mesh);
  return mesh;
}

/* ----------------------------------------------- drifting rain mist sheets */
export function buildMist() {
  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, side: THREE.DoubleSide,
    uniforms: { uTime: U.time, uRain: U.rain, uFlash: U.flash },
    vertexShader: /* glsl */`
      varying vec2 vUv; varying vec3 vW;
      void main(){ vUv = uv; vec4 w = modelMatrix * vec4(position,1.0); vW = w.xyz;
        gl_Position = projectionMatrix * viewMatrix * w; }`,
    fragmentShader: GLSL_NOISE + /* glsl */`
      varying vec2 vUv; varying vec3 vW;
      uniform float uTime, uRain, uFlash;
      void main(){
        float n = fbm2(vUv * vec2(3.0, 1.6) + vec2(uTime * 0.02, -uTime * 0.06));
        float a = smoothstep(0.34, 0.86, n) * uRain * 0.30;
        a *= smoothstep(0.0, 0.25, vUv.y) * smoothstep(1.0, 0.55, vUv.y);
        vec3 col = mix(vec3(0.10,0.12,0.16), vec3(0.30,0.20,0.14), 0.45) + vec3(0.3,0.36,0.5) * uFlash;
        gl_FragColor = vec4(col, a);
      }`,
  });
  const group = new THREE.Group();
  for (let i = 0; i < 3; i++) {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(600, 260), mat);
    m.position.set(rrnd(-120, 120), rrnd(-90, 20), -90 - i * 130);
    group.add(m);
  }
  group.renderOrder = 3;
  outsideScene.add(group);
  return group;
}

/* --------------------------------------------------------------- lightning */
export class Lightning {
  constructor() { this.next = 6 + rnd() * 12; this.t = 0; this.seq = []; this.onStrike = null; }
  update(dt) {
    this.t += dt;
    if (this.seq.length) {
      const s = this.seq[0];
      s.t -= dt;
      U.flash.value = Math.max(0, s.a * clamp(s.t / s.d, 0, 1));
      if (s.t <= 0) this.seq.shift();
      if (!this.seq.length) U.flash.value = 0;
      return;
    }
    U.flash.value = Math.max(0, U.flash.value - dt * 4);
    if (this.t > this.next) {
      this.t = 0;
      this.next = 9 + rnd() * 26;
      const far = rnd() < 0.55;
      const peak = far ? rrnd(0.10, 0.28) : rrnd(0.45, 0.95);
      this.seq = [
        { a: peak * 0.7, d: 0.05, t: 0.05 },
        { a: 0,          d: 0.04, t: 0.04 },
        { a: peak,       d: 0.14, t: 0.14 },
        { a: peak * 0.35, d: 0.30, t: 0.30 },
      ];
      if (this.onStrike) this.onStrike(peak, far);
    }
  }
}

export function buildOutside(maxTowers, maxRain) {
  const sky = buildSky();
  const ground = buildGround();
  const city = buildCity(maxTowers);
  const beacons = buildBeacons(city);
  const rain = buildRain(maxRain);
  const mist = buildMist();
  return { sky, ground, city, beacons, rain, mist };
}
