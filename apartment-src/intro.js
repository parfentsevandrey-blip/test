/* ============================================================
   Кутузовский XII — intro.js   ·   "the open"
   ------------------------------------------------------------
   A one-time cinematic title card: the wordmark + a gold hairline
   resolve (the title focus-pulls in), hold a beat, then the card
   dissolves to reveal the page. The overlay lives in the markup
   and is OPAQUE, so there is no flash and a CSS keyframe dismisses
   it even if this script never runs (no-JS / error safe). Any
   scroll / key / pointer skips it. Honours reduced-motion.
   ============================================================ */
(function(){
  const intro = document.getElementById('kx-intro');
  if(!intro) return;

  if(matchMedia('(prefers-reduced-motion:reduce)').matches){ try{ intro.remove(); }catch(e){} return; }

  let done = false;
  function dismiss(){
    if(done) return; done = true;
    intro.classList.add('kx-intro-leaving');
    const gone = ()=>{ try{ intro.remove(); }catch(e){} };
    intro.addEventListener('animationend', gone, { once:true });
    setTimeout(gone, 1100);                 // failsafe if animationend is missed
  }

  // skip on the first real interaction
  ['wheel','touchstart','keydown','pointerdown'].forEach(ev=>
    window.addEventListener(ev, dismiss, { once:true, passive:true }));

  // otherwise dismiss just after the title beat (before the CSS no-JS failsafe)
  setTimeout(dismiss, 2200);
})();
