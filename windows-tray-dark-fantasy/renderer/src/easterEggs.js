// easterEggs.js - hidden secrets scattered around the room: a cursed skull,
// a dusty tome hiding a dancing skeleton, a rare scurrying mouse, and a
// Konami-code ghost. Owns no shared props from other modules - everything
// here is self-contained.
// Module contract: window.DF.modules.easterEggs = function(THREE, PA) { ... }
(function () {
  window.DF = window.DF || {};
  window.DF.modules = window.DF.modules || {};

  window.DF.modules.easterEggs = function (THREE, PA) {
    var P = PA.PALETTE;
    var MOUSE_GRAY = 0x4a4550; // extra muted-gray, stays in the dark/desaturated family

    function hexCss(n) {
      return '#' + ('000000' + n.toString(16)).slice(-6);
    }

    var group = new THREE.Group();

    // Tracks the shared clock so onClick handlers (which get no elapsed arg)
    // can stamp "this happened now"; update() reads currentElapsed afterwards.
    var currentElapsed = 0;

    // ===================================================================
    // 1. HIDDEN SKULL - back-right corner ledge
    //    footprint x:[4.4,4.8] y:[2.8,3.3] z:[-3.9,-3.6]
    // ===================================================================
    var skullGroup = new THREE.Group();
    skullGroup.position.set(4.6, 0, -3.75);
    group.add(skullGroup);

    var ledgeMat = PA.toonMaterial(P.stoneMid);
    var ledge = PA.box(0.36, 0.14, 0.28, ledgeMat);
    ledge.position.set(0, 2.87, 0);
    skullGroup.add(ledge);

    var skullMeshGroup = new THREE.Group();
    skullMeshGroup.position.set(0, 2.96, 0);
    skullGroup.add(skullMeshGroup);

    var boneMat = PA.toonMaterial(P.bone);
    var boneShadowMat = PA.toonMaterial(P.boneShadow);
    var skullEyeGlowMat = PA.glowMaterial(P.purple, { additive: true, depthWrite: false, opacity: 0.0 });

    var cranium = PA.box(0.22, 0.18, 0.2, boneMat);
    cranium.position.set(0, 0.1, 0);
    skullMeshGroup.add(cranium);

    var jaw = PA.box(0.15, 0.06, 0.15, boneShadowMat);
    jaw.position.set(0, -0.01, 0.02);
    skullMeshGroup.add(jaw);

    var socketGeo = new THREE.BoxGeometry(0.05, 0.05, 0.02);
    var eyeSocketL = new THREE.Mesh(socketGeo, boneShadowMat);
    eyeSocketL.position.set(-0.06, 0.1, 0.095);
    skullMeshGroup.add(eyeSocketL);
    var eyeSocketR = new THREE.Mesh(socketGeo, boneShadowMat);
    eyeSocketR.position.set(0.06, 0.1, 0.095);
    skullMeshGroup.add(eyeSocketR);

    var glowGeo = new THREE.BoxGeometry(0.045, 0.045, 0.01);
    var eyeGlowL = new THREE.Mesh(glowGeo, skullEyeGlowMat);
    eyeGlowL.position.set(-0.06, 0.1, 0.108);
    skullMeshGroup.add(eyeGlowL);
    var eyeGlowR = new THREE.Mesh(glowGeo, skullEyeGlowMat);
    eyeGlowR.position.set(0.06, 0.1, 0.108);
    skullMeshGroup.add(eyeGlowR);

    var skullClicks = 0;
    var skullFlareStartAt = -999;
    var SKULL_FLARE_DURATION = 3.0;

    // ===================================================================
    // 2. DUSTY TOME - front-left floor, hides a dancing skeleton
    //    footprint x:[-4.6,-3.6] z:[1.6,3.2] y:[0,0.5]
    // ===================================================================
    var tomeGroup = new THREE.Group();
    tomeGroup.position.set(-4.1, 0, 2.4);
    group.add(tomeGroup);

    var pagesMat = PA.toonMaterial(P.bone);
    var coverMat = PA.toonMaterial(P.wood);
    var claspMat = PA.toonMaterial(P.gold);

    var tomeCoverBottom = PA.box(0.46, 0.03, 0.34, coverMat);
    tomeCoverBottom.position.set(0, 0.015, 0);
    tomeGroup.add(tomeCoverBottom);

    var tomePages = PA.box(0.42, 0.08, 0.3, pagesMat);
    tomePages.position.set(0, 0.07, 0);
    tomeGroup.add(tomePages);

    var tomeCoverTop = PA.box(0.46, 0.03, 0.34, coverMat);
    tomeCoverTop.position.set(0, 0.125, 0);
    tomeGroup.add(tomeCoverTop);

    var tomeClasp = PA.box(0.06, 0.05, 0.35, claspMat);
    tomeClasp.position.set(0, 0.09, 0);
    tomeGroup.add(tomeClasp);

    // Faint dust motes drifting above the tome - the tome's idle "tell".
    var moteGeo = new THREE.BoxGeometry(0.02, 0.02, 0.02);
    var motes = [];
    for (var mi = 0; mi < 3; mi++) {
      var moteMat = PA.glowMaterial(P.bone, { additive: true, depthWrite: false, opacity: 0.25 });
      var moteMesh = new THREE.Mesh(moteGeo, moteMat);
      var mBaseX = PA.rand(-0.14, 0.14);
      var mBaseZ = PA.rand(-0.08, 0.08);
      moteMesh.position.set(mBaseX, 0.2 + mi * 0.05, mBaseZ);
      tomeGroup.add(moteMesh);
      motes.push({ mesh: moteMesh, mat: moteMat, seed: 501 + mi * 31, baseX: mBaseX, baseZ: mBaseZ, baseY: 0.2 + mi * 0.05 });
    }

    // Tiny procedural dancing skeleton, hidden until the tome's 3rd click.
    var skeletonGroup = new THREE.Group();
    skeletonGroup.position.set(0, 0.5, 0);
    skeletonGroup.scale.set(0.001, 0.001, 0.001);
    tomeGroup.add(skeletonGroup);

    var skelBoneMat = PA.toonMaterial(P.bone);
    var skelShadowMat = PA.toonMaterial(P.boneShadow);

    var skelHead = PA.box(0.09, 0.09, 0.09, skelBoneMat);
    skelHead.position.set(0, 0.34, 0);
    skeletonGroup.add(skelHead);

    var skelRib = PA.box(0.11, 0.14, 0.07, skelBoneMat);
    skelRib.position.set(0, 0.22, 0);
    skeletonGroup.add(skelRib);

    var skelPelvis = PA.box(0.1, 0.06, 0.07, skelShadowMat);
    skelPelvis.position.set(0, 0.13, 0);
    skeletonGroup.add(skelPelvis);

    var armGeo = new THREE.BoxGeometry(0.035, 0.14, 0.035);
    var armPivotL = new THREE.Group();
    armPivotL.position.set(-0.07, 0.27, 0);
    skeletonGroup.add(armPivotL);
    var skelArmL = new THREE.Mesh(armGeo, skelBoneMat);
    skelArmL.position.set(0, -0.07, 0);
    armPivotL.add(skelArmL);

    var armPivotR = new THREE.Group();
    armPivotR.position.set(0.07, 0.27, 0);
    skeletonGroup.add(armPivotR);
    var skelArmR = new THREE.Mesh(armGeo, skelBoneMat);
    skelArmR.position.set(0, -0.07, 0);
    armPivotR.add(skelArmR);

    var legGeo = new THREE.BoxGeometry(0.04, 0.16, 0.04);
    var legPivotL = new THREE.Group();
    legPivotL.position.set(-0.035, 0.1, 0);
    skeletonGroup.add(legPivotL);
    var skelLegL = new THREE.Mesh(legGeo, skelShadowMat);
    skelLegL.position.set(0, -0.08, 0);
    legPivotL.add(skelLegL);

    var legPivotR = new THREE.Group();
    legPivotR.position.set(0.035, 0.1, 0);
    skeletonGroup.add(legPivotR);
    var skelLegR = new THREE.Mesh(legGeo, skelShadowMat);
    skelLegR.position.set(0, -0.08, 0);
    legPivotR.add(skelLegR);

    var tomeClicks = 0;
    var jigStartAt = -999;
    var JIG_DURATION = 2.6;

    // ===================================================================
    // 3. SCURRYING MOUSE - rare, front floor strip
    //    z:[2.5,3.6] x:[-4,4] y~0.06
    // ===================================================================
    var mouseGeo = new THREE.BoxGeometry(0.12, 0.07, 0.16);
    var mouseMat = PA.toonMaterial(MOUSE_GRAY);
    var mouseMesh = new THREE.Mesh(mouseGeo, mouseMat);
    mouseMesh.position.set(0, 0.06, 3.0);
    mouseMesh.scale.set(0.001, 0.001, 0.001);
    group.add(mouseMesh);

    var mouseState = {
      active: false,
      fleeing: false,
      startAt: 0,
      duration: 2.4,
      startX: 0,
      startZ: 3.0,
      endX: 0,
      endZ: 3.0,
      curveSeed: 0,
    };
    var mouseNextAppearanceAt = PA.rand(20, 50);

    // ===================================================================
    // 4. KONAMI CODE GHOST
    // ===================================================================
    var ghostTex = PA.createPixelTexture(16, 24, function (ctx, w, h) {
      ctx.fillStyle = hexCss(P.ghost);
      // head
      ctx.fillRect(5, 2, 6, 5);
      // body, tapering into a blocky wisp-tail hem
      ctx.fillRect(4, 7, 8, 10);
      ctx.fillRect(3, 17, 2, 3);
      ctx.fillRect(6, 17, 2, 4);
      ctx.fillRect(9, 17, 2, 3);
      ctx.fillRect(11, 17, 2, 4);
      // hollow eyes
      ctx.clearRect(6, 4, 1, 2);
      ctx.clearRect(9, 4, 1, 2);
    });

    var ghostMat = PA.glowMaterial(P.ghost, {
      map: ghostTex,
      transparent: true,
      opacity: 0,
      depthWrite: false,
      side: THREE.DoubleSide,
    });
    var ghostMesh = new THREE.Mesh(new THREE.PlaneGeometry(1.0, 1.5), ghostMat);
    ghostMesh.position.set(-5.5, 3.0, -1.0);
    group.add(ghostMesh);

    var ghostFlightStartedAt = -999;
    var GHOST_FLIGHT_DURATION = 4.6;

    try {
      window.DF.onKonami(function () {
        try {
          ghostFlightStartedAt = currentElapsed;
          window.DF.flashTint('rgba(140,170,255,0.3)', 1200);
          window.DF.showBanner('<b>Древний дух пробуждён...</b><br>Ты произнёс верные слова.', 4000);
        } catch (e) {
          /* defensive: never let the payoff crash the app */
        }
      });
    } catch (e) {
      /* onKonami not available for some reason - fail silently, rest of module still works */
    }

    // ===================================================================
    // UPDATE - each sub-feature wrapped so one bug can't sink the others
    // ===================================================================
    return {
      group: group,
      update: function (dt, elapsed) {
        currentElapsed = elapsed;

        // ---------------- 1. Skull ----------------
        try {
          skullMeshGroup.position.y = 2.96 + Math.sin(elapsed * 0.6 + 71.0) * 0.01;
          skullGroup.rotation.y = Math.sin(elapsed * 0.08 + 71.0) * 0.03;

          var skFlareT = elapsed - skullFlareStartAt;
          if (skFlareT >= 0 && skFlareT < SKULL_FLARE_DURATION) {
            var skFade = 1 - skFlareT / SKULL_FLARE_DURATION;
            skullEyeGlowMat.opacity = PA.clamp(0.25 + skFade * 0.75 + Math.sin(elapsed * 24) * 0.05 * skFade, 0, 1);
          } else {
            var idleGlow = PA.flicker(elapsed, 701, 0.2, -0.06, 0.09);
            skullEyeGlowMat.opacity = PA.clamp(idleGlow, 0, 0.12);
          }
        } catch (e) {
          /* skull sub-feature failed this frame; skip and keep going */
        }

        // ---------------- 2. Tome + dancing skeleton ----------------
        try {
          tomeCoverTop.rotation.z = PA.flicker(elapsed, 511, 0.3, -0.012, 0.012);

          for (var mi2 = 0; mi2 < motes.length; mi2++) {
            var m = motes[mi2];
            m.mesh.position.y = m.baseY + Math.sin(elapsed * 0.5 + m.seed) * 0.05;
            m.mesh.position.x = m.baseX + Math.sin(elapsed * 0.3 + m.seed * 1.3) * 0.02;
            m.mat.opacity = PA.clamp(PA.flicker(elapsed, m.seed, 0.35, 0.05, 0.35), 0, 1);
          }

          var jigT = elapsed - jigStartAt;
          if (jigT >= 0 && jigT < JIG_DURATION) {
            var swing = Math.sin(jigT * 14.0);
            armPivotL.rotation.x = swing * 0.9;
            armPivotR.rotation.x = -swing * 0.9;
            legPivotL.rotation.x = -swing * 0.7;
            legPivotR.rotation.x = swing * 0.7;
            var hop = Math.abs(Math.sin(jigT * 7.0)) * 0.07;
            skeletonGroup.position.y = 0.5 + hop;
            skeletonGroup.rotation.y = Math.sin(jigT * 3.0) * 0.3;

            var jFade = 1;
            if (jigT < 0.15) jFade = jigT / 0.15;
            else if (jigT > JIG_DURATION - 0.3) jFade = Math.max(0, (JIG_DURATION - jigT) / 0.3);
            skeletonGroup.scale.set(jFade, jFade, jFade);
          } else {
            skeletonGroup.scale.set(0.001, 0.001, 0.001);
          }
        } catch (e) {
          /* tome sub-feature failed this frame; skip and keep going */
        }

        // ---------------- 3. Mouse ----------------
        try {
          if (!mouseState.active && elapsed >= mouseNextAppearanceAt) {
            mouseState.active = true;
            mouseState.fleeing = false;
            mouseState.startAt = elapsed;
            mouseState.duration = PA.rand(2.0, 3.0);
            mouseState.startX = PA.rand(-4, 4);
            mouseState.startZ = PA.rand(2.5, 3.0);
            var travel = PA.rand(1.5, 3.0) * (Math.random() < 0.5 ? -1 : 1);
            mouseState.endX = PA.clamp(mouseState.startX + travel, -4, 4);
            mouseState.endZ = PA.clamp(mouseState.startZ + PA.rand(-0.4, 0.6), 2.5, 3.6);
            mouseState.curveSeed = PA.rand(0, 100);
          }

          if (mouseState.active) {
            var mt = elapsed - mouseState.startAt;
            var t = mt / Math.max(0.05, mouseState.duration);
            if (t >= 1) {
              mouseState.active = false;
              mouseMesh.scale.set(0.001, 0.001, 0.001);
              mouseNextAppearanceAt = elapsed + PA.rand(45, 90);
            } else {
              var mx = mouseState.startX + (mouseState.endX - mouseState.startX) * t;
              var mz = mouseState.startZ + (mouseState.endZ - mouseState.startZ) * t + Math.sin(t * Math.PI * 2 + mouseState.curveSeed) * 0.15;
              mz = PA.clamp(mz, 2.5, 3.6);
              var scurryBob = Math.abs(Math.sin(elapsed * 40)) * 0.01;
              mouseMesh.position.set(mx, 0.06 + scurryBob, mz);
              mouseMesh.rotation.y = Math.atan2(mouseState.endX - mouseState.startX, mouseState.endZ - mouseState.startZ);
              mouseMesh.scale.set(1, 1, 1);
            }
          }
        } catch (e) {
          /* mouse sub-feature failed this frame; skip and keep going */
        }

        // ---------------- 4. Konami ghost ----------------
        try {
          var gT = elapsed - ghostFlightStartedAt;
          if (gT >= 0 && gT < GHOST_FLIGHT_DURATION) {
            var gp = gT / GHOST_FLIGHT_DURATION;
            var gx = -5.5 + 11.0 * gp;
            var gy = 3.0 + Math.sin(elapsed * 1.1 + 3.0) * 0.4;
            ghostMesh.position.set(gx, gy, -1.0);
            ghostMesh.rotation.z = Math.sin(elapsed * 1.7) * 0.06;

            var gFadeIn = gp < 0.15 ? gp / 0.15 : 1;
            var gFadeOut = gp > 0.85 ? (1 - gp) / 0.15 : 1;
            ghostMat.opacity = PA.clamp(Math.min(gFadeIn, gFadeOut), 0, 1) * 0.85;
          } else {
            ghostMat.opacity = 0;
            ghostMesh.position.set(-5.5, 3.0 + Math.sin(elapsed * 0.3 + 50.0) * 0.05, -1.0);
          }
        } catch (e) {
          /* ghost sub-feature failed this frame; skip and keep going */
        }
      },

      interactables: [
        {
          object: skullGroup,
          onClick: function () {
            skullClicks++;
            if (skullClicks >= 3) {
              skullClicks = 0;
              skullFlareStartAt = currentElapsed;
              try {
                window.DF.flashTint('rgba(106,44,145,0.45)', 900);
                window.DF.showBanner('<b>Ты нашёл проклятый череп</b><br>Что-то очень древнее заметило тебя...', 3500);
              } catch (e) {
                /* payoff failed - the flare animation above still runs */
              }
            }
          },
          cursor: 'pointer',
          hint: 'Что это там, в тени...',
        },
        {
          object: tomeGroup,
          onClick: function () {
            tomeClicks++;
            if (tomeClicks >= 3) {
              tomeClicks = 0;
              jigStartAt = currentElapsed;
              try {
                window.DF.showBanner('<b>Секрет: пляшущий скелет!</b><br>Похоже, ему всё ещё нравится веселиться.', 3200);
              } catch (e) {
                /* payoff failed - the jig animation above still runs */
              }
            }
          },
          cursor: 'pointer',
          hint: 'Пыльный фолиант на полу',
        },
        {
          object: mouseMesh,
          onClick: function () {
            if (mouseState.active) {
              if (!mouseState.fleeing) {
                mouseState.fleeing = true;
                var remaining = 0.3;
                mouseState.duration = Math.max(0.05, currentElapsed - mouseState.startAt + remaining);
              }
              try {
                window.DF.showBanner('Мышь юркнула прочь!', 1500);
              } catch (e) {
                /* ignore */
              }
            }
          },
          cursor: 'pointer',
          hint: 'Мышь!',
        },
      ],
    };
  };
})();
