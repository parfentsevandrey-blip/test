/* =========================================================
   Motion FX — premium micro-interactions powered by Motion
   (motion.dev), the framework-free animation engine vendored
   into this site's import map (see index.html).

   Loaded as its own ES module so it can `import` from "motion"
   without touching the classic main.js script. Everything here
   is purely additive and gated behind prefers-reduced-motion +
   a fine pointer, so the page looks and behaves identically when
   Motion is unavailable or the visitor prefers reduced motion.
   ========================================================= */
import { animate } from "motion";

(function motionFx() {
  "use strict";

  const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)").matches;
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (!finePointer || reduceMotion) return; // honour device + user preferences

  /* ---------- Magnetic CTAs ----------
     The primary buttons spring toward the cursor while hovered, then
     ease back to rest on leave — Motion's spring engine doing the work. */
  const MAGNETIC = ".btn--gold, .btn--line";
  const STRENGTH = 0.35; // how strongly the button follows the cursor (0–1)
  const MAX = 14;        // px cap so the pull stays subtle and tasteful
  const clamp = (v, m) => Math.max(-m, Math.min(m, v));

  document.querySelectorAll(MAGNETIC).forEach((btn) => {
    let raf = 0, lastX = 0, lastY = 0;

    const pull = (e) => {
      lastX = e.clientX;
      lastY = e.clientY;
      if (raf) return; // coalesce moves to one update per frame
      raf = requestAnimationFrame(() => {
        raf = 0;
        const r = btn.getBoundingClientRect();
        const dx = clamp((lastX - (r.left + r.width / 2)) * STRENGTH, MAX);
        const dy = clamp((lastY - (r.top + r.height / 2)) * STRENGTH, MAX);
        animate(
          btn,
          { x: dx, y: dy, scale: 1.04 },
          { type: "spring", stiffness: 320, damping: 22, mass: 0.6 }
        );
      });
    };

    const release = () => {
      if (raf) { cancelAnimationFrame(raf); raf = 0; }
      animate(
        btn,
        { x: 0, y: 0, scale: 1 },
        { type: "spring", stiffness: 260, damping: 18 }
      );
    };

    btn.addEventListener("pointermove", pull);
    btn.addEventListener("pointerleave", release);
    btn.addEventListener("blur", release);
  });
})();
