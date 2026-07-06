// Bootstraps the scene: loads every content module, runs the render/interaction
// loop, and exposes the small set of cross-cutting helpers modules use
// (DF.showBanner, DF.flashTint, DF.onKonami). This file owns the renderer,
// camera and scene graph - content modules never touch those directly.
(function () {
  const THREE = window.THREE;
  const PA = window.DF.PixelArt;

  const canvas = document.getElementById('scene-canvas');
  const stage = document.getElementById('stage');
  const hintEl = document.getElementById('hint');
  const bannerEl = document.getElementById('banner');

  const PIXEL_SCALE = 3; // internal render pixels = CSS pixels / PIXEL_SCALE (chunky look)

  const renderer = new THREE.WebGLRenderer({ canvas: canvas, antialias: false, alpha: false });
  renderer.setPixelRatio(1);
  renderer.shadowMap.enabled = false;
  if ('outputColorSpace' in renderer) renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(PA.PALETTE.mortar);
  scene.fog = new THREE.FogExp2(PA.PALETTE.mortar, 0.05);

  const camera = new THREE.PerspectiveCamera(38, 560 / 366, 0.1, 100);
  const CAM_TARGET = new THREE.Vector3(0, 2.4, -0.8);
  const CAM_BASE = new THREE.Vector3(0, 3.0, 9.4);
  const CAM_RADIUS = CAM_BASE.distanceTo(CAM_TARGET);
  const CAM_BASE_ANGLE = Math.atan2(CAM_BASE.x - CAM_TARGET.x, CAM_BASE.z - CAM_TARGET.z);
  camera.position.copy(CAM_BASE);
  camera.lookAt(CAM_TARGET);

  scene.add(new THREE.AmbientLight(0x2a2440, 0.6));
  const moonFill = new THREE.DirectionalLight(0x8fa3ff, 0.22);
  moonFill.position.set(-6, 8, 3);
  scene.add(moonFill);

  // ---------------------------------------------------------------------
  // Module registry: each module factory is window.DF.modules.<name> and
  // returns { group, lights?, update?(dt, elapsed), interactables? }
  // ---------------------------------------------------------------------
  const updaters = [];
  const pickMeshToEntry = new Map();
  let pickList = [];

  function registerModule(name) {
    const factory = window.DF.modules && window.DF.modules[name];
    if (typeof factory !== 'function') {
      console.warn('[DF] module missing:', name);
      return;
    }
    let result;
    try {
      result = factory(THREE, PA);
    } catch (err) {
      console.error('[DF] module "' + name + '" threw during build:', err);
      return;
    }
    if (!result) return;
    if (result.group) scene.add(result.group);
    (result.lights || []).forEach(function (l) {
      scene.add(l);
    });
    if (typeof result.update === 'function') updaters.push(result.update);
    (result.interactables || []).forEach(function (entry) {
      if (!entry || !entry.object) return;
      pickMeshToEntry.set(entry.object, entry);
      entry.object.traverse(function (o) {
        pickMeshToEntry.set(o, entry);
      });
    });
    pickList = Array.from(pickMeshToEntry.keys());
  }

  ['room', 'fireplace', 'characters', 'props', 'easterEggs'].forEach(registerModule);

  // ---------------------------------------------------------------------
  // Cross-cutting helpers exposed to content modules
  // ---------------------------------------------------------------------
  window.DF.showBanner = function (html, ms) {
    bannerEl.innerHTML = html;
    bannerEl.classList.add('show');
    clearTimeout(window.DF._bannerTimer);
    window.DF._bannerTimer = setTimeout(function () {
      bannerEl.classList.remove('show');
    }, ms || 3200);
  };

  window.DF.flashTint = function (cssColor, ms) {
    const duration = ms || 900;
    const el = document.createElement('div');
    el.className = 'tint-flash go';
    el.style.background = cssColor;
    el.style.animationDuration = duration + 'ms';
    stage.appendChild(el);
    setTimeout(function () {
      el.remove();
    }, duration);
  };

  const KONAMI = ['ArrowUp', 'ArrowUp', 'ArrowDown', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'ArrowLeft', 'ArrowRight', 'b', 'a'];
  let konamiPos = 0;
  const konamiCallbacks = [];
  window.DF.onKonami = function (cb) {
    konamiCallbacks.push(cb);
  };

  window.addEventListener('keydown', function (e) {
    const key = e.key.length === 1 ? e.key.toLowerCase() : e.key;
    if (key === KONAMI[konamiPos]) {
      konamiPos++;
      if (konamiPos === KONAMI.length) {
        konamiPos = 0;
        konamiCallbacks.forEach(function (cb) {
          try {
            cb();
          } catch (err) {
            console.error('[DF] konami callback error', err);
          }
        });
      }
    } else {
      konamiPos = key === KONAMI[0] ? 1 : 0;
    }
    if (e.key === 'Escape' && window.dfHost) window.dfHost.hide();
  });

  const closeBtn = document.getElementById('closeBtn');
  if (closeBtn) {
    closeBtn.addEventListener('click', function () {
      if (window.dfHost) window.dfHost.hide();
    });
  }

  // ---------------------------------------------------------------------
  // Pointer interaction: hover + click raycasting, drag-orbit
  // ---------------------------------------------------------------------
  const raycaster = new THREE.Raycaster();
  const ndc = new THREE.Vector2(-10, -10);
  let hovered = null;
  let isDragging = false;
  let dragStartX = 0;
  let dragMoved = false;
  let azimuth = 0;
  const AZIMUTH_LIMIT = THREE.MathUtils.degToRad(18);

  function updatePointerNDC(e) {
    const rect = canvas.getBoundingClientRect();
    ndc.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
    ndc.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
  }

  canvas.addEventListener('pointermove', function (e) {
    updatePointerNDC(e);
    if (isDragging) {
      const dx = e.clientX - dragStartX;
      if (Math.abs(dx) > 3) dragMoved = true;
      azimuth = THREE.MathUtils.clamp(dx * 0.0015, -AZIMUTH_LIMIT, AZIMUTH_LIMIT);
    }
  });

  canvas.addEventListener('pointerdown', function (e) {
    isDragging = true;
    dragStartX = e.clientX;
    dragMoved = false;
    canvas.setPointerCapture(e.pointerId);
  });

  window.addEventListener('pointerup', function () {
    if (!isDragging) return;
    isDragging = false;
    if (!dragMoved) handleClick();
  });

  function handleClick() {
    raycaster.setFromCamera(ndc, camera);
    const hits = raycaster.intersectObjects(pickList, false);
    if (hits.length) {
      const entry = pickMeshToEntry.get(hits[0].object);
      if (entry && entry.onClick) entry.onClick(hits[0]);
    }
  }

  // ---------------------------------------------------------------------
  // Resize: fit the stage element, keep the internal buffer low-res so the
  // canvas upscales into chunky, nearest-filtered pixels (CSS handles that).
  // ---------------------------------------------------------------------
  function resize() {
    const w = Math.max(1, stage.clientWidth);
    const h = Math.max(1, stage.clientHeight);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    const iw = Math.max(64, Math.round(w / PIXEL_SCALE));
    const ih = Math.max(48, Math.round(h / PIXEL_SCALE));
    renderer.setSize(iw, ih, false);
  }
  window.addEventListener('resize', resize);
  resize();

  // ---------------------------------------------------------------------
  // Main loop
  // ---------------------------------------------------------------------
  const clock = new THREE.Clock();

  function frame() {
    requestAnimationFrame(frame);
    const dt = Math.min(clock.getDelta(), 0.05);
    const elapsed = clock.elapsedTime;

    const bob = Math.sin(elapsed * 0.6) * 0.05;
    const angle = CAM_BASE_ANGLE + azimuth + ndc.x * 0.12;
    camera.position.x = CAM_TARGET.x + Math.sin(angle) * CAM_RADIUS;
    camera.position.z = CAM_TARGET.z + Math.cos(angle) * CAM_RADIUS;
    camera.position.y = CAM_BASE.y + bob + ndc.y * 0.35;
    camera.lookAt(CAM_TARGET.x, CAM_TARGET.y + bob * 0.3, CAM_TARGET.z);

    if (!isDragging) {
      raycaster.setFromCamera(ndc, camera);
      const hits = raycaster.intersectObjects(pickList, false);
      const hitEntry = hits.length ? pickMeshToEntry.get(hits[0].object) : null;
      if (hitEntry !== hovered) {
        if (hovered && hovered.onHoverEnd) hovered.onHoverEnd();
        if (hitEntry && hitEntry.onHoverStart) hitEntry.onHoverStart();
        canvas.style.cursor = hitEntry ? hitEntry.cursor || 'pointer' : 'default';
        hintEl.textContent = hitEntry && hitEntry.hint ? hitEntry.hint : '';
        hovered = hitEntry;
      }
    }

    for (let i = 0; i < updaters.length; i++) {
      try {
        updaters[i](dt, elapsed);
      } catch (err) {
        console.error('[DF] update() error', err);
      }
    }

    renderer.render(scene, camera);
  }

  frame();
})();
