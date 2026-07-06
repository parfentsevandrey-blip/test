// fireplace.js - the heart of the room: a stone hearth with a constantly living fire.
// Module contract: window.DF.modules.fireplace = function(THREE, PA) { ... }
(function () {
  window.DF = window.DF || {};
  window.DF.modules = window.DF.modules || {};

  window.DF.modules.fireplace = function (THREE, PA) {
    var P = PA.PALETTE;

    function hexCss(n) {
      return '#' + ('000000' + n.toString(16)).slice(-6);
    }

    var group = new THREE.Group();

    // ---------------------------------------------------------------------
    // Textures
    // ---------------------------------------------------------------------
    var stoneTex = PA.createPixelTexture(16, 16, function (ctx, w, h) {
      ctx.fillStyle = hexCss(P.stoneMid);
      ctx.fillRect(0, 0, w, h);
      ctx.fillStyle = hexCss(P.mortar);
      ctx.fillRect(0, 0, w, 1);
      ctx.fillRect(0, 8, w, 1);
      ctx.fillRect(4, 0, 1, 8);
      ctx.fillRect(12, 0, 1, 8);
      ctx.fillRect(0, 8, 1, 8);
      ctx.fillRect(8, 8, 1, 8);
      ctx.fillStyle = hexCss(P.stoneDark);
      ctx.fillRect(2, 2, 2, 2);
      ctx.fillRect(9, 3, 2, 2);
      ctx.fillRect(10, 11, 2, 2);
      ctx.fillRect(3, 10, 2, 2);
      ctx.fillStyle = hexCss(P.stoneLight);
      ctx.fillRect(6, 1, 1, 1);
      ctx.fillRect(1, 9, 1, 1);
    }, { repeat: true, repeatX: 3, repeatY: 2 });

    var woodTex = PA.createPixelTexture(16, 8, function (ctx, w, h) {
      ctx.fillStyle = hexCss(P.wood);
      ctx.fillRect(0, 0, w, h);
      ctx.fillStyle = hexCss(P.woodDark);
      ctx.fillRect(0, 1, w, 1);
      ctx.fillRect(0, 5, w, 1);
      ctx.fillRect(3, 0, 1, 8);
      ctx.fillRect(10, 0, 1, 8);
      ctx.fillRect(13, 0, 1, 8);
    }, { repeat: true, repeatX: 2, repeatY: 1 });

    var rugTex = PA.createPixelTexture(16, 16, function (ctx, w, h) {
      ctx.fillStyle = hexCss(P.rug);
      ctx.fillRect(0, 0, w, h);
      ctx.fillStyle = hexCss(P.rugDark);
      ctx.fillRect(0, 0, w, 2);
      ctx.fillRect(0, h - 2, w, 2);
      ctx.fillRect(0, 0, 2, h);
      ctx.fillRect(w - 2, 0, 2, h);
      ctx.fillRect(6, 6, 4, 4);
      ctx.fillStyle = hexCss(P.gold);
      ctx.fillRect(7, 7, 2, 2);
      ctx.fillStyle = hexCss(P.rugDark);
      ctx.fillRect(3, 3, 1, 1);
      ctx.fillRect(12, 3, 1, 1);
      ctx.fillRect(3, 12, 1, 1);
      ctx.fillRect(12, 12, 1, 1);
    }, { repeat: false });

    // ---------------------------------------------------------------------
    // Materials
    // ---------------------------------------------------------------------
    var stoneMat = PA.toonMaterial(P.stoneMid, { map: stoneTex });
    var sootMat = PA.toonMaterial(P.stoneDark);
    var woodMat = PA.toonMaterial(P.wood, { map: woodTex });
    var rugMat = PA.toonMaterial(P.rug, { map: rugTex });

    var fireCoreMat = PA.glowMaterial(P.fire1, { additive: true, depthWrite: false, side: THREE.DoubleSide });
    var fireMidMat = PA.glowMaterial(P.fire2, { additive: true, depthWrite: false, side: THREE.DoubleSide });
    var fireOuterMat = PA.glowMaterial(P.fire3, { additive: true, depthWrite: false, side: THREE.DoubleSide });

    var sootGlowMat = PA.glowMaterial(P.fire3, { additive: true, depthWrite: false, side: THREE.DoubleSide, opacity: 0.15 });
    var rugGlowMat = PA.glowMaterial(P.fire2, { additive: true, depthWrite: false, side: THREE.DoubleSide, opacity: 0.1 });
    var fireHitMat = PA.glowMaterial(P.fire1, { additive: true, depthWrite: false, side: THREE.DoubleSide, opacity: 0.07 });

    // ---------------------------------------------------------------------
    // Stone hearth structure (against back wall, z=-4)
    // ---------------------------------------------------------------------
    var plinth = PA.box(3.0, 0.2, 1.4, stoneMat);
    plinth.position.set(2.7, 0.1, -3.3);
    group.add(plinth);

    var leftPillar = PA.box(0.6, 1.6, 0.9, stoneMat);
    leftPillar.position.set(1.5, 1.0, -3.45);
    group.add(leftPillar);

    var rightPillar = PA.box(0.6, 1.6, 0.9, stoneMat);
    rightPillar.position.set(3.9, 1.0, -3.45);
    group.add(rightPillar);

    var lintel = PA.box(3.0, 0.4, 0.9, stoneMat);
    lintel.position.set(2.7, 2.0, -3.45);
    group.add(lintel);

    var backPanel = PA.box(1.8, 1.6, 0.35, sootMat);
    backPanel.position.set(2.7, 1.0, -3.775);
    group.add(backPanel);

    var mantel = PA.box(3.0, 0.25, 1.5, woodMat);
    mantel.position.set(2.7, 2.325, -3.25);
    group.add(mantel);

    var chimneyBreast = PA.box(3.0, 1.75, 1.2, stoneMat);
    chimneyBreast.position.set(2.7, 3.325, -3.4);
    group.add(chimneyBreast);

    // Subtle glow catcher against the sooty inner back wall, lit by the fire.
    var sootGlowGeo = new THREE.PlaneGeometry(1.7, 1.5);
    var sootGlow = new THREE.Mesh(sootGlowGeo, sootGlowMat);
    sootGlow.position.set(2.7, 1.0, -3.58);
    group.add(sootGlow);

    // ---------------------------------------------------------------------
    // Rug in front of the hearth
    // ---------------------------------------------------------------------
    var rug = PA.box(2.4, 0.05, 1.6, rugMat);
    rug.position.set(2.7, 0.025, -1.8);
    group.add(rug);

    var rugGlowGeo = new THREE.PlaneGeometry(2.2, 1.4);
    var rugGlow = new THREE.Mesh(rugGlowGeo, rugGlowMat);
    rugGlow.rotation.x = -Math.PI / 2;
    rugGlow.position.set(2.7, 0.06, -1.8);
    group.add(rugGlow);

    // ---------------------------------------------------------------------
    // Fire: layered cross-billboard flames
    // ---------------------------------------------------------------------
    var fireGroup = new THREE.Group();
    group.add(fireGroup);

    var flamePlaneGeo = new THREE.PlaneGeometry(1, 1);
    flamePlaneGeo.translate(0, 0.5, 0); // pivot at base so scale.y grows upward

    var flames = [];

    function makeFlame(mat, x, z, baseW, baseH, seed, speed) {
      var g = new THREE.Group();
      var p1 = new THREE.Mesh(flamePlaneGeo, mat);
      var p2 = new THREE.Mesh(flamePlaneGeo, mat);
      p2.rotation.y = Math.PI / 2;
      g.add(p1);
      g.add(p2);
      g.position.set(x, 0.22, z);
      g.scale.set(baseW, baseH, 1);
      fireGroup.add(g);
      flames.push({ group: g, baseW: baseW, baseH: baseH, seed: seed, speed: speed });
    }

    makeFlame(fireOuterMat, 2.05, -3.4, 0.55, 1.0, 1.1, 0.9);
    makeFlame(fireOuterMat, 3.35, -3.35, 0.5, 0.95, 2.3, 1.05);
    makeFlame(fireMidMat, 2.3, -3.3, 0.45, 1.3, 3.7, 1.2);
    makeFlame(fireMidMat, 2.9, -3.35, 0.4, 1.4, 4.9, 1.35);
    makeFlame(fireMidMat, 3.15, -3.25, 0.42, 1.2, 6.1, 1.1);
    makeFlame(fireCoreMat, 2.55, -3.3, 0.3, 1.6, 7.4, 1.5);
    makeFlame(fireCoreMat, 2.75, -3.3, 0.28, 1.75, 8.6, 1.6);

    // Heat-haze veil covering the firebox opening; also doubles as the click target.
    var fireHitGeo = new THREE.PlaneGeometry(1.8, 1.6);
    var fireHitPlane = new THREE.Mesh(fireHitGeo, fireHitMat);
    fireHitPlane.position.set(2.7, 1.0, -3.05);
    fireGroup.add(fireHitPlane);

    // ---------------------------------------------------------------------
    // Embers: small pool of rising, recycled glowing particles
    // ---------------------------------------------------------------------
    var embers = [];
    var emberGeo = new THREE.BoxGeometry(0.06, 0.06, 0.06);
    var numEmbers = 16;
    for (var i = 0; i < numEmbers; i++) {
      var mat = PA.glowMaterial(P.ember, { additive: true, depthWrite: false, opacity: 0.9 });
      var mesh = new THREE.Mesh(emberGeo, mat);
      var x0 = PA.rand(1.95, 3.45);
      var z0 = PA.rand(-3.55, -3.05);
      var y0 = PA.rand(0.25, 1.7);
      mesh.position.set(x0, y0, z0);
      fireGroup.add(mesh);
      embers.push({
        mesh: mesh,
        mat: mat,
        x0: x0,
        z0: z0,
        speed: PA.rand(0.35, 0.75),
        driftSeed: PA.rand(0, 100)
      });
    }

    // ---------------------------------------------------------------------
    // Light
    // ---------------------------------------------------------------------
    var fireLight = new THREE.PointLight(P.fire2, 1.8, 6.5, 2);
    fireLight.position.set(2.7, 1.2, -3.2);
    var lightColorA = new THREE.Color(P.fire2);
    var lightColorB = new THREE.Color(P.fire1);

    // ---------------------------------------------------------------------
    // Interaction / poke state
    // ---------------------------------------------------------------------
    var currentElapsed = 0;
    var lastPokedAt = -999;
    var hovering = false;

    function poke() {
      lastPokedAt = currentElapsed;
    }

    return {
      group: group,
      lights: [fireLight],
      update: function (dt, elapsed) {
        currentElapsed = elapsed;

        var t = elapsed - lastPokedAt;
        var pokeAmt = (t >= 0 && t < 0.6) ? (1 - t / 0.6) : 0;
        var boost = 1 + pokeAmt * 1.35;

        // Flames: layered organic flicker per-piece.
        for (var i = 0; i < flames.length; i++) {
          var f = flames[i];
          var sway = PA.flicker(elapsed, f.seed, f.speed * 0.6, -0.22, 0.22);
          var sy = PA.flicker(elapsed, f.seed + 1, f.speed, 0.82, 1.22) * boost;
          var sx = PA.flicker(elapsed, f.seed + 2, f.speed * 1.4, 0.85, 1.12);
          f.group.rotation.y = sway;
          f.group.scale.set(f.baseW * sx, f.baseH * sy, 1);
        }

        // Embers: rise, drift, fade, and recycle.
        for (var j = 0; j < embers.length; j++) {
          var e = embers[j];
          e.mesh.position.y += e.speed * dt * (0.6 + pokeAmt * 0.9);

          var driftX = PA.flicker(elapsed, e.driftSeed, 0.7, -0.08, 0.08);
          var driftZ = PA.flicker(elapsed, e.driftSeed + 50, 0.55, -0.06, 0.06);
          e.mesh.position.x = e.x0 + driftX;
          e.mesh.position.z = e.z0 + driftZ;

          if (e.mesh.position.y > 1.9) {
            e.mesh.position.y = PA.rand(0.2, 0.4);
            e.x0 = PA.rand(1.95, 3.45);
            e.z0 = PA.rand(-3.55, -3.05);
          }

          var fadeStart = 1.3;
          var topY = 1.9;
          var fade = 1;
          if (e.mesh.position.y > fadeStart) {
            fade = PA.clamp(1 - (e.mesh.position.y - fadeStart) / (topY - fadeStart), 0, 1);
          }
          e.mat.opacity = 0.9 * fade;

          var sparkle = PA.flicker(elapsed, e.driftSeed + 20, 3.0, 0.7, 1.15);
          e.mesh.scale.setScalar(sparkle);
        }

        // Point light flicker, synced loosely to the fire's own pulse.
        var li = PA.flicker(elapsed, 999, 6.0, 0.85, 1.25) * boost;
        fireLight.intensity = 1.6 * li;
        var colorMix = PA.flicker(elapsed, 998, 4.0, 0, 1);
        fireLight.color.copy(lightColorA).lerp(lightColorB, colorMix);

        // Ambient glow surfaces that dance with the fire.
        var glowPulse = PA.flicker(elapsed, 500, 5.0, 0.7, 1.0) * boost;
        sootGlowMat.opacity = 0.15 * glowPulse;
        rugGlowMat.opacity = 0.1 * glowPulse;

        var hazePulse = PA.flicker(elapsed, 501, 2.0, 0.04, 0.11);
        fireHitMat.opacity = hazePulse + (hovering ? 0.05 : 0);
      },
      interactables: [
        {
          object: fireHitPlane,
          onClick: function () {
            poke();
          },
          onHoverStart: function () {
            hovering = true;
          },
          onHoverEnd: function () {
            hovering = false;
          },
          cursor: 'pointer',
          hint: 'Пошевелить огонь'
        }
      ]
    };
  };
})();
