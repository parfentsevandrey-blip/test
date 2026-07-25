/* =========================================================================
   Part 2 / 5 — the weather and the ground the city stands on.

   The buildings, streets, traffic and aircraft live in room-city.js; this
   file is the envelope around them: the overcast, the ground plane with its
   river and parks, the falling rain, the mist and the lightning. It also
   composes the whole outdoor scene.
   ========================================================================= */
import * as THREE from 'three';
import { GLSL_NOISE, U, ROOM, rnd, rrnd, clamp, outsideScene } from './room.js';
import { FOG, FOG_U, fogUniforms, groundHaze } from './room-fog.js';
import {
  CITY, CBD, GLSL_PLAN, cityPlan,
  buildCity, buildRoundTowers, buildStreets, buildTraffic, buildBeacons, buildAircraft,
} from './room-city.js';

export { FOG_U };

/* ------------------------------------------------------------------- sky */
export function buildSky() {
  const az = Math.hypot(CBD[0], CBD[1]);
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide,
    depthWrite: false,
    uniforms: {
      uTime: U.time,
      uFlash: U.flash,
      uRain: U.rain,
      // the glow under the clouds is brightest over downtown, not everywhere
      uCbd: { value: new THREE.Vector2(CBD[0] / az, CBD[1] / az) },
      // what the ground plane fades to, so its far edge cannot draw a line
      uHaze: { value: groundHaze() },
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
      uniform vec2 uCbd;
      uniform vec3 uHaze;
      void main(){
        vec3 d = normalize(vDir);
        float h = d.y;

        vec3 zenith  = vec3(0.0060, 0.0085, 0.0150);
        vec3 horizon = vec3(0.0300, 0.0250, 0.0250);
        vec3 col = mix(horizon, zenith, smoothstep(-0.04, 0.62, h));

        // overcast deck — two scales, the coarse one drifting faster
        vec2 cp = d.xz / max(abs(h) + 0.20, 0.20);
        float cl = fbm2(cp * 1.15 + vec2(uTime * 0.0075, uTime * 0.0032));
        cl = smoothstep(0.30, 0.86, cl);
        float shred = fbm2(cp * 3.6 - vec2(uTime * 0.019, uTime * 0.006));
        cl = clamp(cl * (0.72 + 0.55 * shred), 0.0, 1.0);
        float deck = smoothstep(-0.02, 0.30, h);
        vec3 cloudLit = mix(vec3(0.012,0.014,0.021), vec3(0.052,0.042,0.038), cl);
        col = mix(col, cloudLit, deck * 0.92);

        // sodium light-pollution, aimed: strongest where the city is densest
        float toward = dot(normalize(d.xz + 1e-5), uCbd) * 0.5 + 0.5;
        float glow = exp(-pow(max(h, -0.05) / 0.075, 2.0));
        col += vec3(0.060, 0.026, 0.008) * glow
             * (0.40 + 1.00 * pow(toward, 2.4))
             * (0.62 + 0.38 * fbm2(d.xz * 2.2 + 11.0));
        // and it keeps bouncing off the underside of the deck well up the sky
        col += vec3(0.017, 0.007, 0.003) * cl * deck * pow(toward, 2.2)
             * smoothstep(0.50, 0.0, h);

        // a break in the weather, with the moon behind it
        float moon = exp(-pow(distance(d, normalize(vec3(-0.62, 0.55, 0.56))) / 0.30, 2.0));
        col += vec3(0.055, 0.062, 0.080) * moon * (1.0 - cl * 0.75);

        // lightning: lights the cloud base from inside, hottest where dense
        float bolt = uFlash * (0.35 + 0.95 * cl) * deck;
        col += vec3(0.62, 0.70, 0.92) * bolt;
        col += vec3(0.30, 0.38, 0.55) * uFlash * 0.25;

        // Below the horizon this has to become exactly what the ground plane
        // fades to at distance — the disc runs out at about 1.7 degrees down,
        // and any mismatch there is a hard line straight across the view.
        col = mix(col, uHaze, smoothstep(0.010, -0.012, h));

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
/* Inside the modelled radius this is mostly dark — real buildings and real
   street lights are standing on it and you barely see the ground at all.
   Its job is the river, the parks, and the low-rise fabric that has to carry
   on past where the instanced towers stop, so the city does not end in a
   visible circle. */
export function buildGround() {
  const mat = new THREE.ShaderMaterial({
    extensions: { derivatives: true },
    uniforms: { uTime: U.time, uFlash: U.flash, uRain: U.rain, ...fogUniforms() },
    vertexShader: /* glsl */`
      varying vec3 vW;
      void main(){
        vec4 w = modelMatrix * vec4(position, 1.0);
        vW = w.xyz;
        gl_Position = projectionMatrix * viewMatrix * w;
      }`,
    fragmentShader: GLSL_NOISE + FOG + GLSL_PLAN + /* glsl */`
      varying vec3 vW;
      uniform float uTime, uFlash, uRain;

      void main(){
        vec2 p = vW.xz;
        float r = length(p);
        float dist = length(vW - cameraPosition);

        // Districts of differing brightness at 1.5 km scale, applied after the
        // fog so the far haze is not a flat slab of colour.
        float haze = 0.70 + 0.62 * fbm2(p * 0.00065);

        // The disc runs out to 5 km, well past the point where the haze has
        // closed over it, so the ground never ends in a visible edge with sky
        // underneath. Out there nothing is left to draw but the haze itself,
        // and skipping the rest saves the whole horizon band.
        if (dist > 3000.0) {
          gl_FragColor = vec4(applyFog(vec3(0.012, 0.012, 0.014), vW, dist) * haze, 1.0);
          return;
        }

        // wet ground between the buildings: nearly black, but not black —
        // there is always some sodium bouncing around down there
        float n = fbm2(p * 0.004);
        vec3 col = vec3(0.011, 0.012, 0.015) * (0.5 + n * 0.9);
        col += vec3(0.0075, 0.0035, 0.0012) * (0.30 + 0.70 * cityDensity(p))
             * (0.5 + 0.5 * fbm2(p * 0.05));

        // ---- low-rise fabric taking over where the instanced towers stop, so
        // the modelled city does not end in a visible circle. A hard grid here
        // reads as a checkerboard from 150 m up — this is all smooth terms,
        // aligned to the same street grid, fading out toward the horizon
        // where the ground plane is too foreshortened to carry any detail.
        float fabric = smoothstep(${(CITY.OUTER * 0.42).toFixed(1)}, ${(CITY.OUTER * 1.05).toFixed(1)}, r);
        if (fabric > 0.001) {
          vec2 q = toGrid(p);
          float roofs = fbm2(q * 0.011) * 0.6 + fbm2(q * 0.042) * 0.4;
          float detail = smoothstep(3100.0, 1100.0, dist);
          vec3 fab = vec3(0.013, 0.014, 0.017) * (0.35 + roofs * 1.4);
          float lanes = 0.5 + 0.5 * sin(q.x * 0.0605) * sin(q.y * 0.0731);
          fab += vec3(0.052, 0.024, 0.008) * (0.35 + 0.65 * lanes * detail) * (0.4 + roofs);
          fab += vec3(0.058, 0.034, 0.018) * smoothstep(0.66, 0.95, fbm2(q * 0.085 + 7.0)) * detail;
          col = mix(col, fab, fabric);
        }

        // ---- parks: dark voids with a lit path or two through them
        float park = parkMask(p);
        if (park > 0.001) {
          vec3 gr = vec3(0.008, 0.012, 0.009) * (0.6 + fbm2(p * 0.06) * 0.9);
          float path = smoothstep(0.46, 0.50, fbm2(p * 0.028 + 21.0));
          path *= 1.0 - smoothstep(0.50, 0.545, fbm2(p * 0.028 + 21.0));
          gr += vec3(0.24, 0.13, 0.05) * path * 0.55;
          col = mix(col, gr, park);
        }

        // ---- the river
        float riv = riverSdf(p);
        float water = smoothstep(1.0, -7.0, riv);
        if (water > 0.001) {
          // A reflection on water streaks toward the eye, not along the
          // river — so stretch the ripple noise along the line from here to
          // the camera and squeeze it across, and the lights on the far bank
          // draw the long vertical smears that say "wet" from any angle.
          vec2 toCam = normalize(p - cameraPosition.xz + 1e-4);
          vec2 perp = vec2(-toCam.y, toCam.x);
          float ripple = fbm2(vec2(dot(p, toCam) * 0.020 + uTime * 0.05,
                                   dot(p, perp) * 0.20));
          float chop = fbm2(p * 0.55 + uTime * 0.4);
          vec3 w = vec3(0.0035, 0.0045, 0.0070);
          float refl = cityDensity(p) * 1.35 + 0.10;
          w += vec3(0.26, 0.115, 0.033) * refl * pow(ripple, 1.6) * 1.5;
          w += vec3(0.12, 0.15, 0.22) * refl * 0.20 * ripple;
          // rain stipples the surface and kills the mirror
          w = mix(w, w * 0.62 + vec3(0.008, 0.009, 0.012), uRain * chop * 0.55);
          w += vec3(0.40, 0.48, 0.66) * uFlash * 0.7;
          col = mix(col, w, water);
          // lit quays along both banks
          col += vec3(0.13, 0.060, 0.019) * exp(-pow((riv - 6.0) / 9.0, 2.0)) * (1.0 - water);
        }

        col += vec3(0.30, 0.36, 0.50) * uFlash * 0.25;
        gl_FragColor = vec4(applyFog(col, vW, dist) * haze, 1.0);
      }`,
  });
  const g = new THREE.Mesh(new THREE.CircleGeometry(5000, 72), mat);
  g.rotation.x = -Math.PI / 2;
  g.position.y = -ROOM.alt;
  outsideScene.add(g);
  return g;
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
        float n = fbm2(vUv * vec2(3.4, 1.5) + vec2(uTime * 0.02, -uTime * 0.06));
        float a = smoothstep(0.42, 0.90, n) * uRain * 0.11;
        a *= smoothstep(0.0, 0.25, vUv.y) * smoothstep(1.0, 0.55, vUv.y);
        // Barely there. These sheets sit between us and the whole skyline, so
        // anything you can actually see is a grey veil over the entire city.
        vec3 col = vec3(0.040, 0.038, 0.046) + vec3(0.3,0.36,0.5) * uFlash;
        gl_FragColor = vec4(col, a);
      }`,
  });
  const group = new THREE.Group();
  for (let i = 0; i < 3; i++) {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(520, 190), mat);
    m.position.set(rrnd(-130, 130), rrnd(-130, -40), -110 - i * 150);
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

export function buildOutside(Q) {
  const plan = cityPlan();
  const sky = buildSky();
  const ground = buildGround();
  const city = buildCity(plan, Q.towers);
  const roundTowers = buildRoundTowers(plan);
  const streets = buildStreets(plan, Q.streets);
  const traffic = buildTraffic(plan, Q.cars);
  const beacons = buildBeacons(plan);
  const aircraft = buildAircraft();
  const rain = buildRain(Q.rain);
  const mist = buildMist();
  return { plan, sky, ground, city, roundTowers, streets, traffic, beacons, aircraft, rain, mist };
}
