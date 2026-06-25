/* ============================================================
   Кутузовский XII — depth3d.js
   A standalone 3D engine. It owns the spatial depth of the readable
   site (NOT the WebGL backdrop, NOT per-card hover-tilt):

     · SCROLL DEPTH  — the IMAGE inside each media frame glides
       vertically within its (overflow-hidden) frame as you scroll,
       on an overscanned plane, for true internal parallax. Copy and
       backdrops drift on their own slower planes.
     · POINTER DEPTH — the page reacts to the cursor like a diorama:
       every plane tilts / shifts by its assigned depth, so layers
       feel suspended above the surface.

   Layering is deliberate so nothing overwrites anything else:
     reveal-engine + hover-engine own each .frame element;
     this engine drives the <img> WITHIN the frame (scroll parallax)
     and the OUTER [data-px] wrapper (pointer tilt) — they nest and
     compose. Wrappers that are themselves hover-tilt targets are
     skipped for tilt so the hover engine stays in charge there.

   One inertial rAF loop, frame-rate independent, reconciled from
   ground truth (scroll + pointer) every tick. Geometry via the
   offsetTop chain (immune to the transforms it writes). Honours
   reduced-motion; pointer-depth only on a fine pointer.
   ============================================================ */
(function(){
  const reduce = matchMedia('(prefers-reduced-motion:reduce)').matches;
  if(reduce) return;
  const fine = matchMedia('(hover:hover) and (pointer:fine)').matches;

  /* depth presets per kind.  scroll* drives the scroll plane,
     ptiltX/Y + pshift drive the pointer diorama. */
  const PRESET = {
    media: { overscan:1.24, scrollY:58, tiltX:3.4, tiltY:4.2, shiftX:13, shiftY:9, persp:1400 },
    text:  { overscan:1,    scrollY:20, tiltX:0,   tiltY:0,   shiftX:7,  shiftY:5, persp:0 },
    bg:    { overscan:1.20, scrollY:46, tiltX:0,   tiltY:0,   shiftX:-16,shiftY:-10,persp:0, bgScale:1.2 }
  };

  const planes = [...document.querySelectorAll('[data-px]')].map(el=>{
    const kind = PRESET[el.dataset.px] ? el.dataset.px : 'media';
    const p = PRESET[kind];
    let img=null, layer=null, plan=false;
    if(kind==='media'){
      img = el.querySelector('img');
      plan = !!(img && img.closest('.frame.plan'));
      if(img && !plan){
        // wrap the image in a dedicated parallax layer the engine owns
        // exclusively (no CSS transition) so the hover-zoom on <img> and the
        // scroll parallax never fight over the same transform.
        if(!img.parentElement.classList.contains('par3d')){
          const par = document.createElement('div'); par.className='par3d';
          img.parentNode.insertBefore(par, img); par.appendChild(img);
        }
        layer = img.parentElement;            // .par3d
      } else if(img && plan){
        layer = img;                          // plan img has transition:none — drive directly
        img.style.willChange='transform';
      }
    }
    // a wrapper that is itself a hover-tilt target must NOT also get pointer tilt
    const ownsTilt = el.classList.contains('frame') || el.classList.contains('tilt');
    return { el, kind, p, layer, plan, ownsTilt, top:0, h:0, cur:0 };   // cur = eased scroll pos
  });
  if(!planes.length) return;

  /* ---- geometry (transform-immune) ---- */
  function docTop(el){ let y=0,n=el; while(n){ y += n.offsetTop||0; n = n.offsetParent; } return y; }
  function measure(){ for(const o of planes){ o.top = docTop(o.el); o.h = o.el.offsetHeight; } }

  /* ---- smoothed pointer (−1..1 from screen centre) ---- */
  let ptx=0, pty=0, px=0, py=0;
  if(fine){
    window.addEventListener('pointermove', e=>{
      ptx = (e.clientX/window.innerWidth)*2 - 1;
      pty = (e.clientY/window.innerHeight)*2 - 1;
    }, {passive:true});
    window.addEventListener('pointerleave', ()=>{ ptx=0; pty=0; }, {passive:true});
  }

  let lastT = performance.now();
  function frame(){
    const now = performance.now();
    let dt = (now-lastT)/1000; lastT = now; if(dt>0.05) dt=0.05; if(dt<=0) dt=0.016;
    const ks = 1 - Math.exp(-dt/0.15);    // scroll-trail easing (weighty)
    const kp = 1 - Math.exp(-dt/0.10);    // pointer easing
    px += (ptx-px)*kp; py += (pty-py)*kp;

    const sy = window.scrollY || window.pageYOffset || 0;
    const vh = window.innerHeight;
    const damp = window.innerWidth < 860 ? 0.5 : 1;     // calmer on stacked layout

    for(const o of planes){
      const top = o.top - sy;
      if(top + o.h < -260 || top > vh+260) continue;
      const off = ((top + o.h/2) - vh/2)/vh;            // −1..1 scroll position
      o.cur += (off - o.cur) * ks;
      const s = Math.max(-1, Math.min(1, o.cur)), P = o.p;

      /* --- scroll plane --- */
      if(o.kind==='media' && o.layer){
        if(o.plan){
          // technical drawing: gentle vertical drift, no overscan
          o.layer.style.transform = 'translate3d(0,'+(s * -P.scrollY*0.5).toFixed(1)+'px,0)';
        } else {
          // image glides within its overscanned frame → visible internal parallax
          o.layer.style.transform =
            'scale('+P.overscan+') translate3d(0,'+(s * -P.scrollY).toFixed(1)+'px,0)';
        }
      } else if(o.kind==='bg'){
        o.el.style.transform =
          'scale('+P.bgScale+') translate3d(0,'+(s * P.scrollY).toFixed(1)+'px,0)';
      } else if(o.kind==='text'){
        // copy drifts + leans toward the cursor (no tilt)
        o.el.style.transform =
          'translate3d('+(px*P.shiftX*damp).toFixed(1)+'px,'+
          (s*P.scrollY*damp + py*P.shiftY*damp).toFixed(1)+'px,0)';
      }

      /* --- pointer diorama tilt on the media wrapper --- */
      if(o.kind==='media' && !o.ownsTilt){
        const rx = (-py * P.tiltX) * damp;
        const ry = ( px * P.tiltY) * damp;
        o.el.style.transform =
          'perspective('+P.persp+'px) translate3d('+
          (px*P.shiftX*damp).toFixed(1)+'px,'+(py*P.shiftY*damp).toFixed(1)+'px,0) '+
          'rotateX('+rx.toFixed(2)+'deg) rotateY('+ry.toFixed(2)+'deg)';
      }
    }
    requestAnimationFrame(frame);
  }

  measure();
  window.addEventListener('resize', measure, {passive:true});
  window.addEventListener('load', measure);
  document.querySelectorAll('img').forEach(im=>{ if(!im.complete) im.addEventListener('load', measure, {once:true}); });
  setTimeout(measure, 400); setTimeout(measure, 1200);
  frame();
})();
