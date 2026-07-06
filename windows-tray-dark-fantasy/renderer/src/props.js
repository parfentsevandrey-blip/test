// props.js - four independent, always-alive decorations scattered around the room:
// a bubbling cauldron, a pulsing spellbook on its pedestal, a ticking grandfather
// clock, and a sparse field of drifting dust motes.
// Module contract: window.DF.modules.props = function(THREE, PA) { ... }
(function () {
  window.DF = window.DF || {};
  window.DF.modules = window.DF.modules || {};

  window.DF.modules.props = function (THREE, PA) {
    var P = PA.PALETTE;

    function hexCss(n) {
      return '#' + ('000000' + n.toString(16)).slice(-6);
    }

    var group = new THREE.Group();

    // =======================================================================
    // 1) CAULDRON - x:[-3.4,-1.6] z:[-3.8,-2.6] y:[0,1.6]
    // =======================================================================
    function buildCauldron() {
      // Shifted right from the originally-spec'd x=-2.5: that sat squarely
      // behind characters.js's armchair/hooded-figure (x:[-3.2,-1.4]) and was
      // fully occluded by its backrest from the main camera. x=-0.8 sits in
      // the open floor gap between the chair and the cat, still against the
      // back wall.
      var cx = -0.8;
      var cz = -3.2;

      var cauldronGroup = new THREE.Group();

      var potTex = PA.createPixelTexture(8, 8, function (ctx, w, h) {
        ctx.fillStyle = hexCss(P.catBlack);
        ctx.fillRect(0, 0, w, h);
        ctx.fillStyle = hexCss(P.stoneDark);
        ctx.fillRect(0, 3, w, 1);
        ctx.fillRect(2, 0, 1, h);
        ctx.fillRect(5, 0, 1, h);
        ctx.fillStyle = hexCss(P.mortar);
        ctx.fillRect(0, 6, w, 1);
      }, { repeat: true, repeatX: 4, repeatY: 2 });

      var potMat = PA.toonMaterial(P.catBlack, { map: potTex, side: THREE.DoubleSide });
      var legMat = PA.toonMaterial(P.stoneDark);
      var liquidMatA = PA.glowMaterial(P.poison, { additive: true, depthWrite: false, opacity: 0.7, side: THREE.DoubleSide });
      var liquidMatB = PA.glowMaterial(P.poison, { additive: true, depthWrite: false, opacity: 0.32, side: THREE.DoubleSide });
      var bubbleMat = PA.glowMaterial(P.poison, { additive: true, depthWrite: false, opacity: 0.75 });
      var popMat = PA.glowMaterial(P.poison, { additive: true, depthWrite: false, opacity: 0.9 });

      // --- legs ---
      var legGeo = new THREE.CylinderGeometry(0.06, 0.08, 0.26, 6);
      var legAngles = [0, 2.094, 4.189];
      for (var li = 0; li < legAngles.length; li++) {
        var leg = new THREE.Mesh(legGeo, legMat);
        leg.position.set(cx + Math.cos(legAngles[li]) * 0.3, 0.13, cz + Math.sin(legAngles[li]) * 0.3);
        cauldronGroup.add(leg);
      }

      // --- pot body (hollow tube, open top AND bottom, with a bottom cap disc
      // so we can see down into it through the rim opening) ---
      var potBodyGeo = new THREE.CylinderGeometry(0.5, 0.3, 0.68, 8, 1, true);
      var potBody = new THREE.Mesh(potBodyGeo, potMat);
      potBody.position.set(cx, 0.26 + 0.34, cz);
      cauldronGroup.add(potBody);

      var bottomCapGeo = new THREE.CircleGeometry(0.3, 8);
      var bottomCap = new THREE.Mesh(bottomCapGeo, potMat);
      bottomCap.rotation.x = -Math.PI / 2;
      bottomCap.position.set(cx, 0.28, cz);
      cauldronGroup.add(bottomCap);

      var rimGeo = new THREE.CylinderGeometry(0.53, 0.49, 0.07, 8);
      var rim = new THREE.Mesh(rimGeo, potMat);
      rim.position.set(cx, 0.94, cz);
      cauldronGroup.add(rim);

      var liquidBaseY = 0.88;

      var liquidGeoA = new THREE.CircleGeometry(0.43, 10);
      var liquidA = new THREE.Mesh(liquidGeoA, liquidMatA);
      liquidA.rotation.x = -Math.PI / 2;
      liquidA.position.set(cx, liquidBaseY, cz);
      cauldronGroup.add(liquidA);

      var liquidGeoB = new THREE.CircleGeometry(0.38, 10);
      var liquidB = new THREE.Mesh(liquidGeoB, liquidMatB);
      liquidB.rotation.x = -Math.PI / 2;
      liquidB.position.set(cx, liquidBaseY + 0.01, cz);
      cauldronGroup.add(liquidB);

      // --- small pile of embers beside the pot ---
      var embers = [];
      var emberGeo = new THREE.BoxGeometry(0.05, 0.05, 0.05);
      var emberOffsets = [
        [0.66, 0.03], [0.78, -0.08], [0.72, 0.14], [0.6, -0.15], [0.85, 0.02]
      ];
      for (var ei = 0; ei < emberOffsets.length; ei++) {
        var emat = PA.glowMaterial(P.ember, { additive: true, depthWrite: false, opacity: 0.85 });
        var emesh = new THREE.Mesh(emberGeo, emat);
        var ex = cx + emberOffsets[ei][0];
        var ez = cz + emberOffsets[ei][1];
        var ey = 0.03;
        emesh.position.set(ex, ey, ez);
        cauldronGroup.add(emesh);
        embers.push({ mesh: emesh, mat: emat, baseY: ey, seed: PA.rand(0, 100) });
      }

      // --- bubble pool (reused, small) ---
      var bubbles = [];
      var bubbleGeo = new THREE.SphereGeometry(0.032, 6, 5);
      var numBubbles = 8;
      for (var bi = 0; bi < numBubbles; bi++) {
        var bmesh = new THREE.Mesh(bubbleGeo, bubbleMat);
        var angle = PA.rand(0, Math.PI * 2);
        var radius = PA.rand(0.04, 0.32);
        cauldronGroup.add(bmesh);
        bubbles.push({
          mesh: bmesh,
          bx: cx + Math.cos(angle) * radius,
          bz: cz + Math.sin(angle) * radius,
          life: PA.rand(0, 1),
          speed: PA.rand(0.3, 0.5),
          seed: PA.rand(0, 100)
        });
      }

      // --- click "pop" bubble ---
      var popGeo = new THREE.SphereGeometry(0.05, 8, 6);
      var popBubble = new THREE.Mesh(popGeo, popMat);
      popBubble.position.set(cx, liquidBaseY + 0.02, cz);
      popBubble.scale.setScalar(0.001);
      cauldronGroup.add(popBubble);

      var cauldronLight = new THREE.PointLight(P.poison, 0.5, 3.8, 2);
      cauldronLight.position.set(cx, 1.1, cz);

      var lastPoke = -999;
      var localElapsed = 0;

      function poke() {
        lastPoke = localElapsed;
      }

      function update(dt, elapsed) {
        localElapsed = elapsed;
        var pt = elapsed - lastPoke;
        var pokeAmt = (pt >= 0 && pt < 1.0) ? (1 - pt / 1.0) : 0;

        liquidA.rotation.z += dt * 0.16;
        liquidB.rotation.z -= dt * 0.11;

        var wobbleA = 1 + PA.flicker(elapsed, 11, 0.6, -0.04, 0.04);
        var wobbleB = 1 + PA.flicker(elapsed, 22, 0.7, -0.05, 0.05);
        liquidA.scale.setScalar(wobbleA);
        liquidB.scale.setScalar(wobbleB);
        liquidA.position.y = liquidBaseY + PA.flicker(elapsed, 33, 0.5, -0.01, 0.01);

        liquidMatA.opacity = PA.clamp(0.55 + PA.flicker(elapsed, 44, 0.8, -0.15, 0.15) + pokeAmt * 0.55, 0, 1.3);
        liquidMatB.opacity = PA.clamp(0.22 + PA.flicker(elapsed, 55, 0.9, -0.1, 0.12) + pokeAmt * 0.3, 0, 1.1);

        for (var j = 0; j < bubbles.length; j++) {
          var b = bubbles[j];
          b.life = (b.life + dt * b.speed) % 1;
          var phase = b.life;
          var y = liquidBaseY + phase * 0.2;
          var scale = 0.35 + phase * 1.05;
          if (phase > 0.82) {
            var shrink = 1 - (phase - 0.82) / 0.18;
            scale *= Math.max(shrink, 0);
          }
          var jitterX = PA.flicker(elapsed, b.seed, 1.4, -0.015, 0.015);
          var jitterZ = PA.flicker(elapsed, b.seed + 40, 1.2, -0.015, 0.015);
          b.mesh.position.set(b.bx + jitterX, y, b.bz + jitterZ);
          b.mesh.scale.setScalar(Math.max(scale, 0.001));
        }

        var popProgress = PA.clamp(pt / 1.0, 0, 1);
        var popScale = (pt >= 0 && pt < 1.0) ? Math.sin(popProgress * Math.PI) * 1.7 : 0;
        popBubble.scale.setScalar(Math.max(popScale, 0.001));
        popBubble.position.y = liquidBaseY + 0.02 + popScale * 0.12;

        for (var k = 0; k < embers.length; k++) {
          var e = embers[k];
          var flick = PA.flicker(elapsed, e.seed, 3.0, 0.55, 1.0);
          e.mat.opacity = 0.85 * flick;
          e.mesh.position.y = e.baseY + PA.flicker(elapsed, e.seed + 10, 1.5, 0, 0.015);
          e.mesh.scale.setScalar(0.85 + flick * 0.3);
        }

        var lightPulse = PA.flicker(elapsed, 66, 2.2, 0.75, 1.15) * (1 + pokeAmt * 0.6);
        cauldronLight.intensity = 0.5 * lightPulse;
      }

      return {
        group: cauldronGroup,
        lights: [cauldronLight],
        update: update,
        interactables: [
          {
            object: cauldronGroup,
            onClick: function () {
              poke();
            },
            cursor: 'pointer',
            hint: 'Заглянуть в котёл'
          }
        ]
      };
    }

    // =======================================================================
    // 2) SPELLBOOK - x:[3.4,4.8] z:[-0.8,1.2] y:[0,1.3]
    // =======================================================================
    function buildSpellbook() {
      var px = 4.1;
      var pz = 0.2;

      var spellbookGroup = new THREE.Group();

      var woodTex = PA.createPixelTexture(8, 8, function (ctx, w, h) {
        ctx.fillStyle = hexCss(P.wood);
        ctx.fillRect(0, 0, w, h);
        ctx.fillStyle = hexCss(P.woodDark);
        ctx.fillRect(0, 1, w, 1);
        ctx.fillRect(3, 0, 1, h);
        ctx.fillRect(0, 5, w, 1);
      }, { repeat: true, repeatX: 2, repeatY: 3 });

      var pedestalMat = PA.toonMaterial(P.wood, { map: woodTex });
      var pedestalDarkMat = PA.toonMaterial(P.woodDark);
      var boneMat = PA.toonMaterial(P.bone);
      var spineMat = PA.toonMaterial(P.woodDark);

      var base = PA.box(0.55, 0.14, 0.55, pedestalDarkMat);
      base.position.set(px, 0.07, pz);
      spellbookGroup.add(base);

      var shaft = PA.box(0.24, 0.74, 0.24, pedestalMat);
      shaft.position.set(px, 0.14 + 0.37, pz);
      spellbookGroup.add(shaft);

      var topPlate = PA.box(0.5, 0.08, 0.5, pedestalDarkMat);
      topPlate.position.set(px, 0.14 + 0.74 + 0.04, pz);
      spellbookGroup.add(topPlate);

      var topY = 0.14 + 0.74 + 0.08;

      var runeTex = PA.createPixelTexture(16, 16, function (ctx, w, h) {
        ctx.fillStyle = hexCss(P.purple);
        ctx.fillRect(6, 1, 4, 2);
        ctx.fillRect(6, 13, 4, 2);
        ctx.fillRect(1, 6, 2, 4);
        ctx.fillRect(13, 6, 2, 4);
        ctx.fillRect(4, 4, 2, 2);
        ctx.fillRect(10, 4, 2, 2);
        ctx.fillRect(4, 10, 2, 2);
        ctx.fillRect(10, 10, 2, 2);
        ctx.fillStyle = hexCss(P.gold);
        ctx.fillRect(7, 7, 2, 2);
      }, { repeat: false });

      var runeMat = PA.glowMaterial(0xffffff, { map: runeTex, additive: true, depthWrite: false, opacity: 0.6, side: THREE.DoubleSide });

      var bookGroup = new THREE.Group();
      bookGroup.position.set(px, topY + 0.01, pz);
      bookGroup.rotation.x = -0.28;
      spellbookGroup.add(bookGroup);

      var spine = PA.box(0.05, 0.05, 0.6, spineMat);
      spine.position.set(0, 0.01, 0);
      bookGroup.add(spine);

      var leftPage = PA.box(0.46, 0.03, 0.56, boneMat);
      leftPage.position.set(-0.24, 0.01, 0);
      leftPage.rotation.z = 0.05;
      bookGroup.add(leftPage);

      var rightPage = PA.box(0.46, 0.03, 0.56, boneMat);
      rightPage.position.set(0.24, 0.01, 0);
      rightPage.rotation.z = -0.05;
      bookGroup.add(rightPage);

      var runePlane = new THREE.Mesh(new THREE.PlaneGeometry(0.22, 0.22), runeMat);
      runePlane.rotation.x = -Math.PI / 2;
      runePlane.rotation.z = -0.05;
      runePlane.position.set(0.24, 0.05, 0);
      bookGroup.add(runePlane);

      // Flip pivot: hinge at the spine, carries one extra page-shaped mesh that
      // periodically sweeps from lying on the right page, up and over, to the
      // left page and back - a quick, purely time-driven "page flip".
      var flipPivot = new THREE.Group();
      flipPivot.position.set(0, 0.02, 0);
      bookGroup.add(flipPivot);

      var flipPage = PA.box(0.46, 0.02, 0.56, boneMat);
      flipPage.position.set(0.24, 0, 0);
      flipPage.rotation.z = -0.05;
      flipPivot.add(flipPage);

      var bookLight = new THREE.PointLight(P.purple, 0.32, 2.6, 2);
      bookLight.position.set(px, topY + 0.35, pz);

      var flipping = false;
      var flipStart = 0;
      var flipDuration = 0.9;
      var nextFlipAt = PA.rand(4, 7);
      var runeFlashAt = -999;
      var localElapsed = 0;

      function forceFlip() {
        flipping = true;
        flipStart = localElapsed;
        runeFlashAt = localElapsed;
      }

      function update(dt, elapsed) {
        localElapsed = elapsed;

        if (!flipping && elapsed >= nextFlipAt) {
          flipping = true;
          flipStart = elapsed;
        }

        if (flipping) {
          var p = (elapsed - flipStart) / flipDuration;
          if (p >= 1) {
            p = 1;
            flipping = false;
            nextFlipAt = elapsed + PA.rand(4, 7);
          }
          flipPivot.rotation.z = Math.sin(p * Math.PI) * Math.PI;
        }

        var breathe = 0.5 + 0.5 * Math.sin(elapsed * 1.6 + 2.0);
        var flashT = elapsed - runeFlashAt;
        var flashBoost = (flashT >= 0 && flashT < 1.0) ? (1 - flashT) : 0;
        runeMat.opacity = PA.clamp(0.3 + breathe * 0.35 + flashBoost * 0.7, 0, 1.3);
        runePlane.scale.setScalar(1 + flashBoost * 0.3);

        var lightPulse = PA.flicker(elapsed, 77, 1.6, 0.8, 1.15);
        bookLight.intensity = 0.3 * lightPulse + flashBoost * 0.4;
      }

      return {
        group: spellbookGroup,
        lights: [bookLight],
        update: update,
        interactables: [
          {
            object: bookGroup,
            onClick: function () {
              forceFlip();
            },
            cursor: 'pointer',
            hint: 'Полистать гримуар'
          }
        ]
      };
    }

    // =======================================================================
    // 3) GRANDFATHER CLOCK - x:[4.3,5.0] z:[0,2.4] y:[0,5]
    // =======================================================================
    function buildClock() {
      var cx = 4.65;
      var cz = 1.2;

      var clockGroup = new THREE.Group();

      var woodMat = PA.toonMaterial(P.wood);
      var woodDarkMat = PA.toonMaterial(P.woodDark);
      var boneMat = PA.toonMaterial(P.bone);
      var markMat = PA.toonMaterial(P.mortar);
      var goldMat = PA.toonMaterial(P.gold, { emissive: P.gold, emissiveIntensity: 0.15 });
      var glassMat = PA.toonMaterial(P.mortar, { transparent: true, opacity: 0.85 });

      var base = PA.box(0.58, 0.25, 0.95, woodDarkMat);
      base.position.set(cx, 0.125, cz);
      clockGroup.add(base);

      var body = PA.box(0.5, 3.7, 0.85, woodMat);
      body.position.set(cx, 0.25 + 1.85, cz);
      clockGroup.add(body);

      var hood = PA.box(0.56, 0.3, 0.9, woodDarkMat);
      hood.position.set(cx, 0.25 + 3.7 + 0.15, cz);
      clockGroup.add(hood);

      var finial = PA.box(0.16, 0.16, 0.16, woodDarkMat);
      finial.position.set(cx, 0.25 + 3.7 + 0.3 + 0.08, cz);
      clockGroup.add(finial);

      var faceY = 3.55;
      var faceX = cx - 0.255;

      var dial = new THREE.Mesh(new THREE.CircleGeometry(0.26, 10), boneMat);
      dial.rotation.y = -Math.PI / 2;
      dial.position.set(faceX, faceY, cz);
      clockGroup.add(dial);

      var markGeo = new THREE.BoxGeometry(0.035, 0.05, 0.035);
      var markOffsets = [[0, 0.2, 0], [0, -0.2, 0], [0, 0, 0.2], [0, 0, -0.2]];
      for (var mi = 0; mi < markOffsets.length; mi++) {
        var mark = new THREE.Mesh(markGeo, markMat);
        mark.position.set(faceX - 0.01, faceY + markOffsets[mi][1], cz + markOffsets[mi][2]);
        clockGroup.add(mark);
      }

      // Hands: a small pivot per hand; rotating pivot.rotation.x sweeps the hand
      // around within the dial's (Y,Z) plane.
      var hourPivot = new THREE.Group();
      hourPivot.position.set(faceX - 0.015, faceY, cz);
      clockGroup.add(hourPivot);
      var hourHand = PA.box(0.02, 0.14, 0.02, goldMat);
      hourHand.position.set(0, 0.07, 0);
      hourPivot.add(hourHand);

      var minutePivot = new THREE.Group();
      minutePivot.position.set(faceX - 0.02, faceY, cz);
      clockGroup.add(minutePivot);
      var minuteHand = PA.box(0.016, 0.19, 0.016, goldMat);
      minuteHand.position.set(0, 0.095, 0);
      minutePivot.add(minuteHand);

      // Lower "glass" window and the pendulum swinging behind it.
      var glassPanel = PA.box(0.42, 1.4, 0.04, glassMat);
      glassPanel.position.set(cx - 0.22, 1.55, cz);
      clockGroup.add(glassPanel);

      var pendulumPivot = new THREE.Group();
      pendulumPivot.position.set(faceX - 0.01, 3.2, cz);
      clockGroup.add(pendulumPivot);

      var rod = PA.box(0.025, 1.45, 0.025, goldMat);
      rod.position.set(0, -0.72, 0);
      pendulumPivot.add(rod);

      var weight = new THREE.Mesh(new THREE.CylinderGeometry(0.14, 0.14, 0.05, 10), goldMat);
      weight.rotation.z = Math.PI / 2;
      weight.position.set(0, -1.45, 0);
      pendulumPivot.add(weight);

      var swingFreq = (2 * Math.PI) / 1.7;
      var swingAmp = 0.32;
      var lastChime = -999;
      var localElapsed = 0;

      function chime() {
        lastChime = localElapsed;
      }

      function update(dt, elapsed) {
        localElapsed = elapsed;
        var ct = elapsed - lastChime;
        var chimeBoost = (ct >= 0 && ct < 2.0) ? (1 - ct / 2.0) : 0;

        pendulumPivot.rotation.x = Math.sin(elapsed * swingFreq) * swingAmp * (1 + chimeBoost * 0.5);
        hourPivot.rotation.x = elapsed * ((2 * Math.PI) / 90);
        minutePivot.rotation.x = elapsed * ((2 * Math.PI) / 18);
      }

      return {
        group: clockGroup,
        lights: [],
        update: update,
        interactables: [
          {
            object: clockGroup,
            onClick: function () {
              chime();
            },
            cursor: 'pointer',
            hint: 'Подтолкнуть маятник'
          }
        ]
      };
    }

    // =======================================================================
    // 4) DUST MOTES - roughly x:[-4.5,4.5] y:[0.5,5] z:[-3.5,3]
    // =======================================================================
    function buildDustMotes() {
      var dustGroup = new THREE.Group();

      var moteGeo = new THREE.BoxGeometry(1, 1, 1);
      var boneMoteMat = PA.glowMaterial(P.bone, { additive: true, depthWrite: false, opacity: 0.22 });
      var moonMoteMat = PA.glowMaterial(P.moonlight, { additive: true, depthWrite: false, opacity: 0.18 });

      var motes = [];
      var numMotes = 42;
      for (var i = 0; i < numMotes; i++) {
        var baseMat = (i % 2 === 0) ? boneMoteMat : moonMoteMat;
        var mat = baseMat.clone();
        var mesh = new THREE.Mesh(moteGeo, mat);
        var size = PA.rand(0.02, 0.045);
        mesh.scale.setScalar(size);
        var bx = PA.rand(-4.5, 4.5);
        var by = PA.rand(0.5, 5.0);
        var bz = PA.rand(-3.5, 3.0);
        mesh.position.set(bx, by, bz);
        dustGroup.add(mesh);
        motes.push({
          mesh: mesh,
          mat: mat,
          baseX: bx,
          baseY: by,
          baseZ: bz,
          seed: PA.rand(0, 200),
          riseSpeed: PA.rand(0.015, 0.05),
          baseOpacity: PA.rand(0.12, 0.28)
        });
      }

      function update(dt, elapsed) {
        for (var i = 0; i < motes.length; i++) {
          var m = motes[i];
          m.baseY += dt * m.riseSpeed;
          if (m.baseY > 5.0) {
            m.baseY = 0.5;
            m.baseX = PA.rand(-4.5, 4.5);
            m.baseZ = PA.rand(-3.5, 3.0);
          }
          var wanderX = PA.flicker(elapsed, m.seed, 0.15, -0.25, 0.25);
          var wanderZ = PA.flicker(elapsed, m.seed + 50, 0.12, -0.25, 0.25);
          var wanderY = PA.flicker(elapsed, m.seed + 90, 0.2, -0.08, 0.08);
          m.mesh.position.set(m.baseX + wanderX, m.baseY + wanderY, m.baseZ + wanderZ);
          var twinkle = PA.flicker(elapsed, m.seed + 150, 0.5, 0.6, 1.0);
          m.mat.opacity = m.baseOpacity * twinkle;
        }
      }

      return {
        group: dustGroup,
        lights: [],
        update: update,
        interactables: []
      };
    }

    // =======================================================================
    // Assemble
    // =======================================================================
    var cauldron = buildCauldron();
    var book = buildSpellbook();
    var clock = buildClock();
    var dust = buildDustMotes();

    group.add(cauldron.group);
    group.add(book.group);
    group.add(clock.group);
    group.add(dust.group);

    var lights = []
      .concat(cauldron.lights || [])
      .concat(book.lights || [])
      .concat(clock.lights || [])
      .concat(dust.lights || []);

    var interactables = []
      .concat(cauldron.interactables || [])
      .concat(book.interactables || [])
      .concat(clock.interactables || [])
      .concat(dust.interactables || []);

    return {
      group: group,
      lights: lights,
      update: function (dt, elapsed) {
        cauldron.update(dt, elapsed);
        book.update(dt, elapsed);
        clock.update(dt, elapsed);
        dust.update(dt, elapsed);
      },
      interactables: interactables
    };
  };
})();
