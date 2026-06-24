/* ============================================================
   Кутузовский XII — gallery3d.js   ·   2.5D photographs
   ------------------------------------------------------------
   On hover, the prominent interior frames gain real depth: the
   photo is resampled through a procedural depth field (centre /
   vanishing-point nearer than the edges, refined by luminance) so
   it parallaxes against itself as the cursor moves — a quiet
   "window into the room" instead of a flat picture.

   Uses the ONE shared renderer (glshared.js) and blits into a per-
   frame overlay <canvas>, so 20 photos cost 0 extra GL contexts and
   nothing renders unless a frame is actually hovered. Desktop / fine
   pointer only; reduced-motion and no-WebGL keep the CSS hover.
   ============================================================ */
(function(){
  const KXGL = window.KXGL, THREE = window.THREE;
  if(!KXGL || !THREE) return;
  const reduce = matchMedia('(prefers-reduced-motion:reduce)').matches;
  const fine   = matchMedia('(hover:hover) and (pointer:fine)').matches;
  if(reduce || !fine) return;

  const PR = KXGL.renderer.getPixelRatio();
  const texCache = new Map();
  function getTex(src){
    if(texCache.has(src)) return texCache.get(src);
    const p = new Promise(res=>{
      new THREE.TextureLoader().load(src, t=>{
        t.minFilter=THREE.LinearFilter; t.magFilter=THREE.LinearFilter;
        t.generateMipmaps=false; t.wrapS=t.wrapT=THREE.ClampToEdgeWrapping;
        if('encoding' in t) t.encoding = THREE.sRGBEncoding;
        res(t);
      }, undefined, ()=>res(null));
    });
    texCache.set(src, p); return p;
  }

  const FRAG = `
    precision highp float;
    varying vec2 vUv;
    uniform sampler2D uTex;
    uniform vec2 uMouse; uniform float uAmt, uAspect, uImgAspect, uZoom;
    vec2 coverUV(vec2 uv){
      vec2 c = uv - 0.5; float ar = uAspect / uImgAspect;
      if(ar > 1.0) c.y /= ar; else c.x *= ar;
      c /= uZoom; return c + 0.5;
    }
    void main(){
      vec2 uv = coverUV(vUv);
      vec3 base = texture2D(uTex, clamp(uv,0.001,0.999)).rgb;
      float lum = dot(base, vec3(0.299,0.587,0.114));
      float radial = 1.0 - clamp(length(vUv-0.5)*1.5, 0.0, 1.0); // centre nearer
      float depth = clamp(radial*0.7 + lum*0.3, 0.0, 1.0);
      vec2 suv = coverUV(vUv + uMouse * uAmt * (depth - 0.4));
      gl_FragColor = vec4(texture2D(uTex, clamp(suv,0.001,0.999)).rgb, 1.0);
    }`;
  const VERT = `varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy,0.0,1.0); }`;

  function enhance(frame){
    const img = frame.querySelector('img');
    if(!img || frame.dataset.gl3d) return;
    frame.dataset.gl3d = '1';

    const overlay = document.createElement('canvas');
    overlay.className = 'gl-depth';
    const ctx = overlay.getContext('2d');
    frame.appendChild(overlay);

    const cam = new THREE.OrthographicCamera(-1,1,1,-1,0,1);
    const scene = new THREE.Scene();
    const U = {
      uTex:{value:null}, uMouse:{value:new THREE.Vector2()},
      uAmt:{value:0.06}, uAspect:{value:1}, uImgAspect:{value:1}, uZoom:{value:1.05}
    };
    const mat = new THREE.ShaderMaterial({ uniforms:U, vertexShader:VERT, fragmentShader:FRAG });
    scene.add(new THREE.Mesh(new THREE.PlaneGeometry(2,2), mat));

    let cssW=0, cssH=0, ready=false, hov=false, fade=0;
    const m = { tx:0, ty:0 };

    function resize(){
      const r = frame.getBoundingClientRect();
      cssW = r.width; cssH = r.height;
      overlay.width = Math.max(1, Math.round(cssW*PR));
      overlay.height = Math.max(1, Math.round(cssH*PR));
      U.uAspect.value = cssW / Math.max(1, cssH);
    }

    const consumer = { frame: step };
    function step(dt){
      // ease pointer + fade
      U.uMouse.value.x += (m.tx - U.uMouse.value.x) * Math.min(1, dt*6);
      U.uMouse.value.y += (m.ty - U.uMouse.value.y) * Math.min(1, dt*6);
      const tf = hov ? 1 : 0;
      fade += (tf - fade) * Math.min(1, dt*7);
      KXGL.size(cssW, cssH);
      KXGL.render(scene, cam);
      KXGL.blit(ctx, overlay.width, overlay.height);
      overlay.style.opacity = fade.toFixed(3);
      if(!hov && fade < 0.01){ overlay.style.opacity='0'; KXGL.deactivate(consumer); }
    }

    frame.addEventListener('pointerenter', async ()=>{
      if(!ready){
        const tex = await getTex(img.currentSrc || img.src);
        if(!tex){ return; }
        U.uTex.value = tex;
        const iw=(tex.image&&tex.image.width)||16, ih=(tex.image&&tex.image.height)||9;
        U.uImgAspect.value = iw/ih; ready = true;
      }
      resize(); hov = true; KXGL.activate(consumer);
    });
    frame.addEventListener('pointermove', e=>{
      const r = frame.getBoundingClientRect();
      m.tx = ((e.clientX - r.left)/r.width)*2 - 1;
      m.ty = ((e.clientY - r.top)/r.height)*2 - 1;
    }, {passive:true});
    frame.addEventListener('pointerleave', ()=>{ hov = false; m.tx=0; m.ty=0; });
    window.addEventListener('resize', ()=>{ if(hov) resize(); }, {passive:true});
  }

  // prominent frames only (skip the technical floor-plan; plan3d handles it)
  const sel = '.split .col-media .frame:not(.plan), .showcase .frame:not(.plan), .frame.wide:not(.plan)';
  document.querySelectorAll(sel).forEach(enhance);
})();
