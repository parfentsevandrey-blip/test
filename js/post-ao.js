/* =========================================================================
   Screen-space ambient occlusion.

   The room has exactly one shadow-casting light, so every surface outside the
   fire's cone is lit by flat ambient and reads as pasted-on: a table leg meets
   the rug with no darkening, a cushion meets the sofa with no seam, a corner
   is as bright as the middle of a wall. Contact darkening is what makes a
   rendered room feel like a photographed one.

   Contract
   --------
     const ao = new AmbientOcclusion(deps);
     ao.resize(w, h);
     ao.render(depthTexture, camera);   // once per frame, after the room pass
     ao.texture                          // r = 1 unoccluded … 0 fully occluded

   `deps` = { THREE, renderer, blit, rt, VERT_QUAD } — everything the pass
   needs, injected so this file imports nothing but stays testable.

   Only depth is available, so view-space normals are reconstructed from it.
   The scene is a 10 × 8 × 3.3 m room, so the sample radius lives in the
   0.15–0.5 m range rather than the usual outdoor scale.
   ========================================================================= */

/* Shared by the AO and blur passes: unproject a depth sample back to view
   space. Works for any projection because it goes through the inverse matrix
   rather than assuming a perspective near/far split. */
const DEPTH_LIB = /* glsl */`
uniform sampler2D tDepth;
uniform mat4 uProjInv;
uniform vec2 uTexel;

float rawDepth(vec2 uv){ return texture2D(tDepth, uv).x; }

vec3 viewPos(vec2 uv, float d){
  vec4 clip = vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
  vec4 v = uProjInv * clip;
  return v.xyz / v.w;
}
vec3 viewPos(vec2 uv){ return viewPos(uv, rawDepth(uv)); }
`;

export class AmbientOcclusion {
  constructor({ THREE, renderer, blit, rt, VERT_QUAD }) {
    this.THREE = THREE;
    this.renderer = renderer;
    this.blit = blit;
    this.scale = 0.6;              // AO is low-frequency, but contact wants some resolution
    this.strength = 0.85;
    this.radius = 0.42;            // metres
    this.bias = 0.018;             // metres, against self-occlusion acne
    this.target = rt(2, 2, { depth: false });
    this.blurTarget = rt(2, 2, { depth: false });

    const shared = {
      tDepth: { value: null },
      uProjInv: { value: new THREE.Matrix4() },
      uTexel: { value: new THREE.Vector2() },
    };
    this.shared = shared;

    /* ---------------------------------------------------------- AO pass -- */
    this.aoUniforms = {
      ...shared,
      uProj: { value: new THREE.Matrix4() },
      uRadius: { value: this.radius },
      uBias: { value: this.bias },
      uIntensity: { value: 1.7 },
    };

    this.aoMaterial = new THREE.ShaderMaterial({
      uniforms: this.aoUniforms,
      vertexShader: VERT_QUAD,
      depthTest: false, depthWrite: false,
      fragmentShader: DEPTH_LIB + /* glsl */`
        uniform mat4  uProj;
        uniform float uRadius, uBias, uIntensity;
        varying vec2  vUv;

        const int SAMPLES = 12;
        const float GOLDEN = 2.39996323;

        /* Reconstructing the normal from naive dFdx/dFdy puts a one-pixel band
           of garbage on every silhouette, because the difference straddles two
           surfaces. Taking whichever of the forward and backward difference is
           the smaller step in depth keeps the estimate on the near surface. */
        vec3 viewNormal(vec2 uv, vec3 P){
          vec3 ddxF = viewPos(uv + vec2(uTexel.x, 0.0)) - P;
          vec3 ddxB = P - viewPos(uv - vec2(uTexel.x, 0.0));
          vec3 ddyF = viewPos(uv + vec2(0.0, uTexel.y)) - P;
          vec3 ddyB = P - viewPos(uv - vec2(0.0, uTexel.y));
          vec3 dx = abs(ddxF.z) < abs(ddxB.z) ? ddxF : ddxB;
          vec3 dy = abs(ddyF.z) < abs(ddyB.z) ? ddyF : ddyB;
          return normalize(cross(dx, dy));
        }

        float hash12(vec2 p){
          vec3 p3 = fract(vec3(p.xyx) * 0.1031);
          p3 += dot(p3, p3.yzx + 33.33);
          return fract((p3.x + p3.y) * p3.z);
        }

        void main(){
          float d = rawDepth(vUv);
          if(d >= 0.9999){ gl_FragColor = vec4(1.0); return; }   // sky / background

          vec3 P = viewPos(vUv, d);
          vec3 N = viewNormal(vUv, P);

          // per-pixel rotation turns the sampling pattern's banding into dither
          float ang = hash12(gl_FragCoord.xy) * 6.2831853;
          vec3 rvec = vec3(cos(ang), sin(ang), 0.0);
          vec3 T = normalize(rvec - N * dot(rvec, N));
          vec3 B = cross(N, T);
          mat3 TBN = mat3(T, B, N);

          float occ = 0.0;
          for(int i = 0; i < SAMPLES; i++){
            float fi = float(i);
            // cosine-ish hemisphere spiral, pushed away from the very centre
            float a = fi * GOLDEN + ang;
            float r = sqrt((fi + 0.5) / float(SAMPLES));
            float z = sqrt(1.0 - r * r);
            vec3 dir = TBN * vec3(cos(a) * r, sin(a) * r, z);

            // vary the reach so a few samples probe close contact and a few
            // probe the wider cavity, instead of all landing on one shell
            float scl = mix(0.10, 1.0, pow((fi + 0.5) / float(SAMPLES), 1.6));
            vec3 sp = P + dir * uRadius * scl;

            vec4 clip = uProj * vec4(sp, 1.0);
            vec2 suv = clip.xy / clip.w * 0.5 + 0.5;
            if(suv.x < 0.0 || suv.x > 1.0 || suv.y < 0.0 || suv.y > 1.0) continue;

            float sd = rawDepth(suv);
            if(sd >= 0.9999) continue;
            vec3 sampleP = viewPos(suv, sd);

            // in view space -z runs away from the eye, so a larger z means the
            // real surface sits in front of the sample point: it occludes
            float dz = sampleP.z - sp.z;
            float occluded = step(uBias, dz);

            // ignore geometry far outside the radius, or a distant wall would
            // "occlude" a near object it merely sits behind
            float range = smoothstep(0.0, 1.0, uRadius / max(abs(P.z - sampleP.z), 1e-4));
            occ += occluded * range;
          }

          float ao = 1.0 - clamp((occ / float(SAMPLES)) * uIntensity, 0.0, 1.0);
          ao = pow(ao, 1.9);           // deepen the crevices, keep open areas open
          gl_FragColor = vec4(clamp(ao, 0.0, 1.0), 0.0, 0.0, 1.0);
        }`,
    });

    /* -------------------------------------------------- bilateral blur -- */
    const blurFrag = DEPTH_LIB + /* glsl */`
      uniform sampler2D tAO;
      uniform vec2 uDir;
      varying vec2 vUv;

      void main(){
        float d0 = rawDepth(vUv);
        if(d0 >= 0.9999){ gl_FragColor = vec4(1.0); return; }
        float z0 = viewPos(vUv, d0).z;

        float sum = 0.0, wsum = 0.0;
        for(int i = -4; i <= 4; i++){
          float fi = float(i);
          vec2 uv = vUv + uDir * uTexel * fi;
          float w = exp(-fi * fi / 8.0);
          // depth-aware: a plain blur bleeds occlusion across silhouettes and
          // leaves a dark halo, which is the classic cheap-SSAO tell
          float z = viewPos(uv).z;
          w *= exp(-abs(z - z0) * 12.0);
          sum += texture2D(tAO, uv).r * w;
          wsum += w;
        }
        gl_FragColor = vec4(sum / max(wsum, 1e-4), 0.0, 0.0, 1.0);
      }`;

    this.blurUniforms = { ...shared, tAO: { value: null }, uDir: { value: new THREE.Vector2(1, 0) } };
    this.blurMaterial = new THREE.ShaderMaterial({
      uniforms: this.blurUniforms,
      vertexShader: VERT_QUAD,
      fragmentShader: blurFrag,
      depthTest: false, depthWrite: false,
    });
  }

  resize(w, h) {
    const tw = Math.max(2, (w * this.scale) | 0), th = Math.max(2, (h * this.scale) | 0);
    if (this.target.width !== tw || this.target.height !== th) {
      this.target.setSize(tw, th);
      this.blurTarget.setSize(tw, th);
    }
    this.shared.uTexel.value.set(1 / tw, 1 / th);
  }

  render(depthTexture, camera) {
    this.shared.tDepth.value = depthTexture;
    this.shared.uProjInv.value.copy(camera.projectionMatrixInverse);
    this.aoUniforms.uProj.value.copy(camera.projectionMatrix);
    this.aoUniforms.uRadius.value = this.radius;
    this.aoUniforms.uBias.value = this.bias;

    this.blit(this.aoMaterial, this.target);

    // separable, so two cheap passes rather than one square kernel
    this.blurUniforms.tAO.value = this.target.texture;
    this.blurUniforms.uDir.value.set(1, 0);
    this.blit(this.blurMaterial, this.blurTarget);

    this.blurUniforms.tAO.value = this.blurTarget.texture;
    this.blurUniforms.uDir.value.set(0, 1);
    this.blit(this.blurMaterial, this.target);
  }

  get texture() { return this.target.texture; }
}
