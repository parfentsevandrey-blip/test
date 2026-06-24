/* ============================================================
   Кутузовский XII — reveal-fx.js   ·   cinematic reveals
   ------------------------------------------------------------
   Two camera-grade touches layered on top of the existing reveal
   engine WITHOUT fighting it (it owns opacity/transform/filter; we
   only touch letter-spacing + clip-path, which it never sets):

     · HEADINGS focus-pull — section titles settle from a wide,
       "out-of-focus" tracking to their design tracking as they
       enter the reading zone (reads as a lens pulling focus).
     · PHOTOS aperture — framed images open from a centre slit
       (clip-path) with a thin gold "light seam" that fades as the
       aperture widens.

   Closed/wide states are added by JS, so if this never runs the
   page is simply the original reveal. A failsafe opens everything
   after load so an image can never be trapped shut.
   ============================================================ */
(function(){
  if(matchMedia('(prefers-reduced-motion:reduce)').matches) return;
  if(!('IntersectionObserver' in window)) return;

  const heads  = [...document.querySelectorAll('.sec-head h2')];
  const frames = [...document.querySelectorAll('.frame:not(.plan)')];

  heads.forEach(h => h.classList.add('kx-focus'));
  frames.forEach(f => f.classList.add('kx-aperture'));

  const hio = new IntersectionObserver(es=>{
    for(const e of es) if(e.isIntersecting){ e.target.classList.add('kx-focus-in'); hio.unobserve(e.target); }
  }, { threshold:0.6 });
  heads.forEach(h => hio.observe(h));

  const fio = new IntersectionObserver(es=>{
    for(const e of es) if(e.isIntersecting){ e.target.classList.add('open'); fio.unobserve(e.target); }
  }, { threshold:0.22, rootMargin:'0px 0px -8% 0px' });
  frames.forEach(f => fio.observe(f));

  // failsafe — never leave a title wide or an image clipped shut
  function openAll(){
    heads.forEach(h => h.classList.add('kx-focus-in'));
    frames.forEach(f => f.classList.add('open'));
  }
  window.addEventListener('load', ()=> setTimeout(openAll, 1600), { once:true });
  setTimeout(openAll, 4000);
})();
