/* =========================================================================
   Depth of field.

   Nothing marks an image as computer-generated more reliably than being sharp
   everywhere. A real photograph of a room taken at f/2 in near-darkness has a
   focal plane a few centimetres deep and everything else falls away; the eye
   reads that instantly as "photographed". This is a gather-based bokeh at half
   resolution, driven by the same depth buffer the AO pass uses.

       const dof = new DepthOfField(deps);
       dof.resize(w, h);
       dof.render(colorTexture, depthTexture, camera, focusDistance);
       dof.texture   // the defocused image, same size as the input

   It runs BEFORE bloom, so out-of-focus highlights bloom as discs rather than
   as points — which is what makes bokeh look like glass and not like a blur.
   ========================================================================= */

export class DepthOfField {
  constructor({ THREE, blit, rt, VERT_QUAD }) {
    this.THREE = THREE;
    this.blit = blit;
    this.enabled = true;
    this.aperture = 1.0;        // scales the whole effect; 0 disables it
    this.focusRange = 1.7;      // metres either side of focus that stay sharp
    this.maxBlur = 11.0;        // pixels of blur radius at full defocus

    this.half = rt(2, 2, { depth: false });
    this.out = rt(2, 2, { depth: false });

    const common = /* glsl */`
      uniform sampler2D tDepth;
      uniform mat4  uProjInv;
      uniform float uFocus, uRange, uAperture;
      varying vec2  vUv;

      float viewZ(vec2 uv){
        float d = texture2D(tDepth, uv).x;
        vec4 c = vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
        vec4 v = uProjInv * c;
        return -(v.z / v.w);                       // positive distance from eye
      }
      /* signed circle of confusion: negative in front of focus, positive
         behind. Sign matters — foreground bokeh must be allowed to spill over
         a sharp background, while a sharp subject must not be eaten by the
         blurred wall behind it. */
      float coc(vec2 uv){
        float z = viewZ(uv);
        float d = z - uFocus;
        // soft-limited so the far field never runs away — an interior has no
        // true far plane, and a hard 1/z curve blurs the whole back wall
        float c = d / (abs(d) + uRange * 4.0);
        c = sign(c) * max(abs(c) - 0.18, 0.0) / 0.82;   // a real sharp band
        return clamp(c * uAperture, -1.0, 1.0);
      }`;

    this.uniforms = {
      tColor: { value: null },
      tDepth: { value: null },
      tHalf: { value: null },
      uProjInv: { value: new THREE.Matrix4() },
      uFocus: { value: 3.0 },
      uRange: { value: this.focusRange },
      uAperture: { value: this.aperture },
      uTexel: { value: new THREE.Vector2() },
      uMaxBlur: { value: this.maxBlur },
    };

    /* ------------------------------------------------- gather at half res */
    this.blurMaterial = new THREE.ShaderMaterial({
      uniforms: this.uniforms,
      vertexShader: VERT_QUAD,
      depthTest: false, depthWrite: false,
      fragmentShader: common + /* glsl */`
        uniform sampler2D tColor;
        uniform vec2 uTexel;
        uniform float uMaxBlur;

        const int RINGS = 3;
        const int PER_RING = 7;
        const float GOLDEN = 2.39996323;

        void main(){
          float c0 = coc(vUv);
          float r0 = abs(c0);

          vec3 sum = texture2D(tColor, vUv).rgb;
          float wsum = 1.0;

          // a hexagonal-ish spiral: cheap, and the ring structure gives the
          // highlight discs an edge instead of a gaussian mush
          for(int ring = 1; ring <= RINGS; ring++){
            float fr = float(ring) / float(RINGS);
            for(int i = 0; i < PER_RING; i++){
              float a = float(i) * (6.2831853 / float(PER_RING)) + float(ring) * GOLDEN;
              // scaling by the texel size keeps the bokeh circular on screen
              vec2 uv = vUv + vec2(cos(a), sin(a)) * fr * r0 * uMaxBlur * uTexel;
              vec3 s = texture2D(tColor, uv).rgb;
              float cs = coc(uv);

              // a sample may contribute only if it is itself blurred enough to
              // reach here; otherwise a sharp foreground bleeds into the plate
              float w = clamp(abs(cs) * 1.4 - fr * 0.4 + 0.35, 0.0, 1.0);
              // and foreground blur is allowed to spill forward
              if(cs < 0.0 && c0 > cs) w = max(w, abs(cs));
              sum += s * w; wsum += w;
            }
          }
          gl_FragColor = vec4(sum / wsum, r0);
        }`,
    });

    /* ------------------------------------------------ composite back up */
    this.compositeMaterial = new THREE.ShaderMaterial({
      uniforms: this.uniforms,
      vertexShader: VERT_QUAD,
      depthTest: false, depthWrite: false,
      fragmentShader: common + /* glsl */`
        uniform sampler2D tColor, tHalf;
        void main(){
          vec3 sharp = texture2D(tColor, vUv).rgb;
          vec4 blur = texture2D(tHalf, vUv);
          float k = smoothstep(0.03, 0.30, abs(coc(vUv)));
          // the half-res buffer carries its own reach in .a, so a strongly
          // defocused neighbour can still dominate a nominally sharp pixel
          k = max(k, smoothstep(0.08, 0.40, blur.a));
          gl_FragColor = vec4(mix(sharp, blur.rgb, k), 1.0);
        }`,
    });
  }

  resize(w, h) {
    const hw = Math.max(2, w >> 1), hh = Math.max(2, h >> 1);
    if (this.half.width !== hw || this.half.height !== hh) this.half.setSize(hw, hh);
    if (this.out.width !== w || this.out.height !== h) this.out.setSize(w, h);
    this.uniforms.uTexel.value.set(1 / w, 1 / h);
  }

  /** @returns the texture to use downstream — the input itself when disabled */
  render(colorTexture, depthTexture, camera, focusDistance) {
    if (!this.enabled || this.aperture <= 0.001) return colorTexture;
    const u = this.uniforms;
    u.tDepth.value = depthTexture;
    u.uProjInv.value.copy(camera.projectionMatrixInverse);
    u.uFocus.value = focusDistance;
    u.uRange.value = this.focusRange;
    u.uAperture.value = this.aperture;
    u.uMaxBlur.value = this.maxBlur;

    u.tColor.value = colorTexture;
    this.blit(this.blurMaterial, this.half);

    u.tHalf.value = this.half.texture;
    this.blit(this.compositeMaterial, this.out);
    return this.out.texture;
  }

  get texture() { return this.out.texture; }
}
