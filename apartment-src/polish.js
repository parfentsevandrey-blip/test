/* ============================================================
   Кутузовский XII — polish.js
   ------------------------------------------------------------
   Small dependency-free cinematic touches that don't warrant a
   WebGL context. Currently: trigger the floor-plan "light-table"
   gold sweep (pure CSS) once when the plan scrolls into view.
   Hover re-plays it via CSS :hover. Honours reduced-motion.
   ============================================================ */
(function(){
  if(matchMedia('(prefers-reduced-motion:reduce)').matches) return;
  const plans = document.querySelectorAll('.frame.plan');
  if(!plans.length || !('IntersectionObserver' in window)) return;

  const io = new IntersectionObserver((entries)=>{
    for(const e of entries){
      if(!e.isIntersecting) continue;
      const el = e.target;
      el.classList.add('sweeping');
      el.addEventListener('animationend', ()=> el.classList.remove('sweeping'), { once:true });
      io.unobserve(el);
    }
  }, { threshold:0.4 });

  plans.forEach(p => io.observe(p));
})();
