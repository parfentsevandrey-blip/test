/* ============================================================
   Кутузовский XII — postfx.js
   ------------------------------------------------------------
   A small, self-contained post-processing chain built directly on
   three.js r128 core (no addon files, so nothing to version-match).

   Pipeline:  scene → rtScene
              bright-pass (threshold/knee)          → rtBright
              progressive separable gaussian (×3)   → bloom
              radial light-shafts from a "sun"      → rtGod
              FINAL GRADE (to screen):
                composite scene + bloom + god-rays
                · ACES-style filmic tone curve
                · warm-highlight / cool-shadow split-tone
                · vignette · animated film grain
                · subtle chromatic aberration to the edges

   Exposes  window.KXPostFX.create(renderer, opts) → composer.
   Built for an OPAQUE scene (the hero), so the grade is clean.
   ============================================================ */
(function(){
  const THREE = window.THREE;
  if(!THREE) return;

  const VERT = `
    varying vec2 vUv;
    void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }`;

  function makeRT(w, h, depth){
    return new THREE.WebGLRenderTarget(Math.max(2, w|0), Math.max(2, h|0), {
      minFilter: THREE.LinearFilter, magFilter: THREE.LinearFilter,
      format: THREE.RGBAFormat, depthBuffer: !!depth, stencilBuffer: false
    });
  }

  function create(renderer, opts){
    opts = opts || {};

    const quadCam   = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
    const quadScene = new THREE.Scene();
    const quadGeo   = new THREE.PlaneGeometry(2, 2);
    const quadMesh  = new THREE.Mesh(quadGeo, null);
    quadMesh.frustumCulled = false;
    quadScene.add(quadMesh);

    /* ---------- materials ---------- */
    const brightMat = new THREE.ShaderMaterial({
      uniforms:{ tDiffuse:{value:null}, uThresh:{value: opts.threshold ?? 0.60}, uKnee:{value:0.28} },
      vertexShader: VERT,
      fragmentShader:`
        varying vec2 vUv; uniform sampler2D tDiffuse; uniform float uThresh, uKnee;
        void main(){
          vec3 c = texture2D(tDiffuse, vUv).rgb;
          float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
          float k = smoothstep(uThresh - uKnee, uThresh + uKnee, l);
          gl_FragColor = vec4(c * k, 1.0);
        }`
    });

    const blurMat = new THREE.ShaderMaterial({
      uniforms:{ tDiffuse:{value:null}, uDir:{value:new THREE.Vector2(1,0)}, uTexel:{value:new THREE.Vector2()} },
      vertexShader: VERT,
      fragmentShader:`
        varying vec2 vUv; uniform sampler2D tDiffuse; uniform vec2 uDir, uTexel;
        void main(){
          vec2 o = uDir * uTexel;
          vec3 s = texture2D(tDiffuse, vUv).rgb * 0.2270270270;
          s += texture2D(tDiffuse, vUv + o*1.3846153846).rgb * 0.3162162162;
          s += texture2D(tDiffuse, vUv - o*1.3846153846).rgb * 0.3162162162;
          s += texture2D(tDiffuse, vUv + o*3.2307692308).rgb * 0.0702702703;
          s += texture2D(tDiffuse, vUv - o*3.2307692308).rgb * 0.0702702703;
          gl_FragColor = vec4(s, 1.0);
        }`
    });

    const copyMat = new THREE.ShaderMaterial({
      uniforms:{ tDiffuse:{value:null} }, vertexShader: VERT,
      fragmentShader:`varying vec2 vUv; uniform sampler2D tDiffuse;
        void main(){ gl_FragColor = texture2D(tDiffuse, vUv); }`
    });

    const combineMat = new THREE.ShaderMaterial({
      uniforms:{ t0:{value:null}, t1:{value:null}, t2:{value:null} },
      vertexShader: VERT,
      fragmentShader:`
        varying vec2 vUv; uniform sampler2D t0, t1, t2;
        void main(){
          vec3 c = texture2D(t0,vUv).rgb*0.5 + texture2D(t1,vUv).rgb*0.85 + texture2D(t2,vUv).rgb*1.2;
          gl_FragColor = vec4(c, 1.0);
        }`
    });

    const godMat = new THREE.ShaderMaterial({
      uniforms:{ tDiffuse:{value:null}, uSun:{value:new THREE.Vector2(0.5,0.82)},
                 uDensity:{value:0.6}, uDecay:{value:0.94}, uWeight:{value:0.5} },
      vertexShader: VERT,
      fragmentShader:`
        varying vec2 vUv; uniform sampler2D tDiffuse;
        uniform vec2 uSun; uniform float uDensity, uDecay, uWeight;
        void main(){
          const int N = 24;
          vec2 dir = (vUv - uSun) * (uDensity / float(N));
          vec2 uv = vUv; float illum = 1.0; vec3 acc = vec3(0.0);
          for(int i=0;i<N;i++){ uv -= dir; acc += texture2D(tDiffuse, uv).rgb * illum * uWeight; illum *= uDecay; }
          gl_FragColor = vec4(acc / float(N), 1.0);
        }`
    });

    const finalMat = new THREE.ShaderMaterial({
      uniforms:{
        tScene:{value:null}, tBloom:{value:null}, tGod:{value:null},
        uBloom:{value: opts.bloomStrength ?? 0.9},
        uGod:{value: opts.godStrength ?? 0.5},
        uTime:{value:0}, uGrain:{value: opts.grain ?? 0.05},
        uVignette:{value: opts.vignette ?? 0.34},
        uCA:{value: opts.chroma ?? 1.0},
        uExposure:{value: opts.exposure ?? 1.06},
        uWarm:{value:new THREE.Color(opts.warm || '#ffd9a0')},
        uCool:{value:new THREE.Color(opts.cool || '#0e1726')},
        uTint:{value: opts.tint ?? 0.10},
        uResolution:{value:new THREE.Vector2()}
      },
      vertexShader: VERT,
      fragmentShader:`
        precision highp float;
        varying vec2 vUv;
        uniform sampler2D tScene, tBloom, tGod;
        uniform float uBloom, uGod, uTime, uGrain, uVignette, uCA, uExposure, uTint;
        uniform vec3 uWarm, uCool; uniform vec2 uResolution;
        vec3 aces(vec3 x){
          const float a=2.51,b=0.03,c=2.43,d=0.59,e=0.14;
          return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
        }
        float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
        vec3 toSRGB(vec3 c){ return pow(clamp(c,0.0,1.0), vec3(1.0/2.2)); }
        void main(){
          vec2 uv = vUv; vec2 d = uv - 0.5; float r2 = dot(d, d);
          float ca = uCA * (0.0016 + r2 * 0.004);
          vec3 col;
          col.r = texture2D(tScene, uv + d*ca).r;
          col.g = texture2D(tScene, uv).g;
          col.b = texture2D(tScene, uv - d*ca).b;
          vec3 bloom = texture2D(tBloom, uv).rgb;
          vec3 god   = texture2D(tGod,   uv).rgb;
          col += bloom * uBloom;
          col += god   * uGod * (uWarm * 0.6 + 0.4);
          col = aces(col * uExposure);
          float l = dot(col, vec3(0.2126,0.7152,0.0722));
          vec3 tone = mix(uCool, uWarm, smoothstep(0.15, 0.85, l));
          col = mix(col, col * tone * 1.9, uTint);
          col *= 1.0 - uVignette * smoothstep(0.25, 0.85, r2);
          float g = hash(uv * uResolution + fract(uTime) * 137.0) - 0.5;
          col += g * uGrain * (0.35 + 0.65 * l);
          gl_FragColor = vec4(toSRGB(col), 1.0);
        }`
    });

    /* ---------- render targets ---------- */
    let W = 2, H = 2, BW = 2, BH = 2;
    let rtScene, rtBright, rtA, rtB, cap0, cap1, cap2, rtBloom, rtGod;
    const allRT = ()=>[rtScene, rtBright, rtA, rtB, cap0, cap1, cap2, rtBloom, rtGod];

    function setSize(w, h){
      const PR = renderer.getPixelRatio();
      W = Math.max(2, Math.round(w * PR));
      H = Math.max(2, Math.round(h * PR));
      BW = Math.max(2, W >> 1); BH = Math.max(2, H >> 1);
      allRT().forEach(rt => rt && rt.dispose());
      rtScene  = makeRT(W, H, true);
      rtBright = makeRT(BW, BH);
      rtA = makeRT(BW, BH); rtB = makeRT(BW, BH);
      cap0 = makeRT(BW, BH); cap1 = makeRT(BW, BH); cap2 = makeRT(BW, BH);
      rtBloom = makeRT(BW, BH);
      rtGod   = makeRT(BW, BH);
      finalMat.uniforms.uResolution.value.set(W, H);
    }

    function drawQuad(mat, target){
      quadMesh.material = mat;
      renderer.setRenderTarget(target || null);
      renderer.render(quadScene, quadCam);
    }

    // one separable gaussian of srcTex (radius ~ scale) → returns rtB.texture
    function blur(srcTex, scale){
      const tx = 1 / BW, ty = 1 / BH;
      blurMat.uniforms.tDiffuse.value = srcTex;
      blurMat.uniforms.uTexel.value.set(tx * scale, ty * scale);
      blurMat.uniforms.uDir.value.set(1, 0); drawQuad(blurMat, rtA);
      blurMat.uniforms.tDiffuse.value = rtA.texture;
      blurMat.uniforms.uDir.value.set(0, 1); drawQuad(blurMat, rtB);
      return rtB.texture;
    }
    function capture(tex, target){ copyMat.uniforms.tDiffuse.value = tex; drawQuad(copyMat, target); }

    function render(scene, camera){
      const prevTarget = renderer.getRenderTarget();
      const prevAuto = renderer.autoClear;
      renderer.autoClear = true;

      // 1) scene → rtScene
      renderer.setRenderTarget(rtScene); renderer.clear(); renderer.render(scene, camera);

      // 2) bright pass
      brightMat.uniforms.tDiffuse.value = rtScene.texture;
      drawQuad(brightMat, rtBright);

      // 3) progressive blur — each scale blurs the previous (smooth, wide, no banding)
      capture(blur(rtBright.texture, 1.0), cap0);
      capture(blur(cap0.texture,    1.7), cap1);
      capture(blur(cap1.texture,    1.7), cap2);
      combineMat.uniforms.t0.value = cap0.texture;
      combineMat.uniforms.t1.value = cap1.texture;
      combineMat.uniforms.t2.value = cap2.texture;
      drawQuad(combineMat, rtBloom);

      // 4) god-rays from the bright buffer
      godMat.uniforms.tDiffuse.value = rtBright.texture;
      drawQuad(godMat, rtGod);

      // 5) grade to screen (or to a parent target if nested)
      finalMat.uniforms.tScene.value = rtScene.texture;
      finalMat.uniforms.tBloom.value = rtBloom.texture;
      finalMat.uniforms.tGod.value   = rtGod.texture;
      drawQuad(finalMat, prevTarget);

      renderer.setRenderTarget(prevTarget);
      renderer.autoClear = prevAuto;
    }

    setSize(opts.width || window.innerWidth, opts.height || window.innerHeight);

    return {
      setSize, render,
      final: finalMat, god: godMat, bright: brightMat,
      setTime(t){ finalMat.uniforms.uTime.value = t; },
      setSun(x, y){ godMat.uniforms.uSun.value.set(x, y); },
      uniforms: finalMat.uniforms,
      dispose(){
        allRT().forEach(rt => rt && rt.dispose());
        [brightMat, blurMat, copyMat, combineMat, godMat, finalMat].forEach(m => m.dispose());
        quadGeo.dispose();
      }
    };
  }

  window.KXPostFX = { create };
})();
