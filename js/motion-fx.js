/* =========================================================
   Motion FX — cinematic UI layer, powered by Motion
   (motion.dev), the framework-free animation engine vendored
   into this site's import map (see index.html).

   Loaded as its own ES module so it can `import` from "motion"
   without touching the classic main.js script. Everything here
   is purely additive and gated behind prefers-reduced-motion;
   the page looks and behaves correctly (main.js + CSS provide
   the fallback reveals) when Motion is unavailable or the
   visitor prefers reduced motion.

   Design-engineering principles applied (from the "make
   interfaces feel better" / "ui-ux-pro-max" playbooks):
     · entrances: spring, short stagger (~70ms), scale 0.985→1,
       blur 6px→0  — interruptible, never linear-ease keyframes
     · cinematic scroll: the hero performs a camera-style push
       (rise + fade + scale) as it leaves; gallery imagery glides
       inside its frame on an overscanned plane (internal parallax)
     · magnetic primary CTAs (spring toward the cursor)
   ========================================================= */
import { animate, inView, scroll, stagger } from "motion";

(function cinematic() {
  "use strict";

  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)").matches;

  // Reduced motion → do nothing. The CSS `.reveal` + main.js observer still
  // show every element; we simply don't add the cinematic layer on top.
  if (reduceMotion) return;

  // Tell the stylesheet that Motion now owns the reveals, so the CSS
  // transition on `.reveal` stands down and the two never double-animate.
  document.documentElement.classList.add("motion");

  /* ---------- 1. Entrance reveals — spring + stagger + de-blur ---------- */
  // Replaces the binary CSS fade with an interruptible spring. Items are
  // staggered by their order within the nearest section, capped so a long
  // list never accumulates a huge delay.
  try {
    const items = Array.from(document.querySelectorAll("[data-reveal]"));
    const order = new Map();
    const seen = new Map();
    for (const el of items) {
      const group = el.closest("section, footer, header") || document.body;
      const n = seen.get(group) || 0;
      order.set(el, n);
      seen.set(group, n + 1);
      // arm: hidden start state, with CSS transitions silenced
      el.style.transition = "none";
      el.style.opacity = "0";
      el.style.transform = "translateY(28px) scale(0.985)";
      el.style.filter = "blur(6px)";
      el.style.willChange = "transform, opacity, filter";
    }

    items.forEach((el) => {
      const stop = inView(
        el,
        () => {
          const delay = Math.min(order.get(el) || 0, 6) * 0.07;
          const controls = animate(
            el,
            { opacity: [0, 1], y: [28, 0], scale: [0.985, 1], filter: ["blur(6px)", "blur(0px)"] },
            { type: "spring", stiffness: 240, damping: 30, mass: 1, delay }
          );
          // drop the heavy hints + blur once it settles (keeps text crisp)
          controls.finished
            .then(() => { el.style.willChange = "auto"; el.style.filter = ""; })
            .catch(() => {});
          stop(); // reveal once
        },
        { amount: 0.2, margin: "0px 0px -8% 0px" }
      );
    });
  } catch (e) { /* reveals are enhancement-only */ }

  /* ---------- 2. Hero — cinematic exit (camera push) ---------- */
  // As the hero scrolls away, its content rises, fades and eases back in
  // scale, reading like a slow dolly-out over the live 3D backdrop.
  try {
    const hero = document.querySelector(".hero");
    const heroContent = document.querySelector(".hero__content");
    if (hero && heroContent) {
      scroll(
        animate(heroContent, { opacity: [1, 0], y: [0, -70], scale: [1, 0.965] }, { ease: "linear" }),
        { target: hero, offset: ["start start", "end start"] }
      );
    }
  } catch (e) { /* hero choreography is enhancement-only */ }

  /* ---------- 3. Gallery — internal parallax (overscanned plane) ---------- */
  // The image glides vertically inside its overflow-hidden frame as the card
  // travels through the viewport. The glide drives a dedicated wrapper, so the
  // image's own CSS hover-zoom is never overwritten (they nest and compose).
  try {
    document.querySelectorAll(".gallery__item").forEach((item) => {
      const img = item.querySelector("img");
      if (!img) return;
      let plane = img.closest(".par3d");
      if (!plane) {
        plane = document.createElement("div");
        plane.className = "par3d";
        img.parentNode.insertBefore(plane, img);
        plane.appendChild(img);
      }
      scroll(
        // constant overscan (scale) + scroll-linked vertical glide
        animate(plane, { y: ["-6%", "6%"], scale: [1.16, 1.16] }, { ease: "linear" }),
        { target: item, offset: ["start end", "end start"] }
      );
    });
  } catch (e) { /* parallax is enhancement-only */ }

  /* ---------- 4. Magnetic primary CTAs ---------- */
  // The buttons spring toward the cursor while hovered, then ease back on
  // leave — Motion's spring engine. Fine pointer only.
  if (finePointer) {
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
          animate(btn, { x: dx, y: dy, scale: 1.04 },
            { type: "spring", stiffness: 320, damping: 22, mass: 0.6 });
        });
      };
      const release = () => {
        if (raf) { cancelAnimationFrame(raf); raf = 0; }
        animate(btn, { x: 0, y: 0, scale: 1 }, { type: "spring", stiffness: 260, damping: 18 });
      };
      btn.addEventListener("pointermove", pull);
      btn.addEventListener("pointerleave", release);
      btn.addEventListener("blur", release);
    });
  }
})();
