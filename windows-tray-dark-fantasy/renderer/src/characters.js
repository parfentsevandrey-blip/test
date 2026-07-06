// characters.js - the room's three inhabitants: a hooded figure brooding by the
// fire, a raven perched behind it, and a black cat curled up on the floor.
// Module contract: window.DF.modules.characters = function(THREE, PA) { ... }
(function () {
  window.DF = window.DF || {};
  window.DF.modules = window.DF.modules || {};

  window.DF.modules.characters = function (THREE, PA) {
    var P = PA.PALETTE;

    function hexCss(n) {
      return '#' + ('000000' + n.toString(16)).slice(-6);
    }

    var group = new THREE.Group();

    // Tracks the shared clock so onClick handlers (which get no elapsed arg)
    // can stamp "this happened now" and update() can decay it afterwards.
    var currentElapsed = 0;

    // -----------------------------------------------------------------
    // Shared textures
    // -----------------------------------------------------------------
    var woodTex = PA.createPixelTexture(16, 8, function (ctx, w, h) {
      ctx.fillStyle = hexCss(P.wood);
      ctx.fillRect(0, 0, w, h);
      ctx.fillStyle = hexCss(P.woodDark);
      ctx.fillRect(0, 1, w, 1);
      ctx.fillRect(0, 5, w, 1);
      ctx.fillRect(3, 0, 1, 8);
      ctx.fillRect(10, 0, 1, 8);
    }, { repeat: true, repeatX: 2, repeatY: 1 });

    var robeTex = PA.createPixelTexture(8, 16, function (ctx, w, h) {
      ctx.fillStyle = hexCss(P.cloth);
      ctx.fillRect(0, 0, w, h);
      ctx.fillStyle = hexCss(P.clothDark);
      ctx.fillRect(1, 0, 1, h);
      ctx.fillRect(5, 0, 1, h);
      ctx.fillRect(3, 3, 1, 10);
    }, { repeat: true, repeatX: 2, repeatY: 2 });

    // -----------------------------------------------------------------
    // Shared materials
    // -----------------------------------------------------------------
    var stoneMat = PA.toonMaterial(P.stoneMid);
    var woodMat = PA.toonMaterial(P.wood, { map: woodTex });
    var cushionMat = PA.toonMaterial(P.rug);
    var robeMat = PA.toonMaterial(P.cloth, { map: robeTex });
    var hoodMat = PA.toonMaterial(P.clothDark);
    var shadowFaceMat = PA.toonMaterial(P.mortar);
    var skinMat = PA.toonMaterial(P.skin);

    var ravenMat = PA.toonMaterial(P.catBlack);
    var ravenWingMat = PA.toonMaterial(P.stoneDark);
    var beakMat = PA.toonMaterial(P.gold);

    var catMat = PA.toonMaterial(P.catBlack);
    var eyeGlowMat = PA.glowMaterial(P.poison, { additive: true, depthWrite: false, opacity: 0.95 });

    // ===================================================================
    // 1. ARMCHAIR + HOODED FIGURE
    //    footprint x:[-3.2,-1.4] z:[-2.6,-0.6] y:[0,2.4]
    // ===================================================================
    var furnitureGroup = new THREE.Group();
    group.add(furnitureGroup);

    var legGeo = new THREE.BoxGeometry(0.15, 0.5, 0.15);
    var legSpots = [[-3.0, -1.05], [-1.6, -1.05], [-3.0, -2.15], [-1.6, -2.15]];
    for (var li = 0; li < legSpots.length; li++) {
      var leg = new THREE.Mesh(legGeo, stoneMat);
      leg.position.set(legSpots[li][0], 0.25, legSpots[li][1]);
      furnitureGroup.add(leg);
    }

    var seat = PA.box(1.5, 0.16, 1.3, stoneMat);
    seat.position.set(-2.3, 0.5, -1.6);
    furnitureGroup.add(seat);

    var cushion = PA.box(1.4, 0.08, 1.2, cushionMat);
    cushion.position.set(-2.3, 0.62, -1.6);
    furnitureGroup.add(cushion);

    var backrest = PA.box(1.5, 1.7, 0.2, stoneMat);
    backrest.position.set(-2.3, 1.45, -2.4);
    furnitureGroup.add(backrest);

    var backTrim = PA.box(1.5, 0.1, 0.24, woodMat);
    backTrim.position.set(-2.3, 2.3, -2.4);
    furnitureGroup.add(backTrim);

    var armGeo = new THREE.BoxGeometry(0.18, 0.45, 1.0);
    var armXs = [-3.0, -1.6];
    for (var ai = 0; ai < armXs.length; ai++) {
      var arm = new THREE.Mesh(armGeo, woodMat);
      arm.position.set(armXs[ai], 0.82, -1.55);
      furnitureGroup.add(arm);
    }

    // Small wall/chair-mounted perch plank the raven stands on.
    var perch = PA.box(0.4, 0.05, 0.3, woodMat);
    perch.position.set(-2.95, 2.02, -2.2);
    furnitureGroup.add(perch);

    // Resting hands (idle-wobbled independently below, not tied to breathing).
    var handGeo = new THREE.BoxGeometry(0.16, 0.12, 0.26);
    var handL = new THREE.Mesh(handGeo, skinMat);
    handL.position.set(-3.0, 1.08, -1.35);
    group.add(handL);
    var handR = new THREE.Mesh(handGeo, skinMat);
    handR.position.set(-1.6, 1.08, -1.35);
    group.add(handR);

    // ---- seated hooded figure ----
    var figureGroup = new THREE.Group();
    figureGroup.position.set(-2.3, 0, -2.0);
    group.add(figureGroup);

    var torso = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.5, 1.15, 8), robeMat);
    torso.position.set(0, 1.175, 0);
    figureGroup.add(torso);

    var headGroup = new THREE.Group();
    headGroup.position.set(0, 1.83, 0.07);
    figureGroup.add(headGroup);

    var headSphere = new THREE.Mesh(new THREE.SphereGeometry(0.22, 8, 6), shadowFaceMat);
    headGroup.add(headSphere);

    var hood = new THREE.Mesh(new THREE.ConeGeometry(0.34, 0.5, 8), hoodMat);
    hood.position.set(0, 0.1, -0.06);
    headGroup.add(hood);

    // Head-tilt idle state: eases toward a fresh random target every ~6-10s.
    var headTilt = { next: 4, target: 0, current: 0 };
    var lastNoticedAt = -999;

    // ===================================================================
    // 2. RAVEN - perched on the plank behind the chair
    // ===================================================================
    var ravenBaseY = 2.2;
    var ravenGroup = new THREE.Group();
    ravenGroup.position.set(-2.95, ravenBaseY, -2.2);
    group.add(ravenGroup);

    var ravenBody = new THREE.Mesh(new THREE.SphereGeometry(0.12, 8, 6), ravenMat);
    ravenBody.scale.set(1.0, 1.05, 1.5);
    ravenGroup.add(ravenBody);

    var ravenHeadPivot = new THREE.Group();
    ravenHeadPivot.position.set(0, 0.11, 0.13);
    ravenGroup.add(ravenHeadPivot);

    var ravenHead = new THREE.Mesh(new THREE.SphereGeometry(0.08, 8, 6), ravenMat);
    ravenHeadPivot.add(ravenHead);

    var ravenBeak = new THREE.Mesh(new THREE.ConeGeometry(0.03, 0.11, 6), beakMat);
    ravenBeak.rotation.x = Math.PI / 2;
    ravenBeak.position.set(0, -0.01, 0.1);
    ravenHeadPivot.add(ravenBeak);

    var wingGeo = new THREE.BoxGeometry(0.2, 0.035, 0.15);
    var wingFold = 0.2;

    var wingPivotL = new THREE.Group();
    wingPivotL.position.set(-0.11, 0.01, -0.02);
    wingPivotL.rotation.z = wingFold;
    ravenGroup.add(wingPivotL);
    var wingL = new THREE.Mesh(wingGeo, ravenWingMat);
    wingL.position.set(-0.09, 0, 0);
    wingPivotL.add(wingL);

    var wingPivotR = new THREE.Group();
    wingPivotR.position.set(0.11, 0.01, -0.02);
    wingPivotR.rotation.z = -wingFold;
    ravenGroup.add(wingPivotR);
    var wingR = new THREE.Mesh(wingGeo, ravenWingMat);
    wingR.position.set(0.09, 0, 0);
    wingPivotR.add(wingR);

    var ravenTail = new THREE.Mesh(new THREE.BoxGeometry(0.06, 0.03, 0.2), ravenMat);
    ravenTail.position.set(0, -0.02, -0.22);
    ravenGroup.add(ravenTail);

    var lastFlapAt = -999;

    // ===================================================================
    // 3. BLACK CAT - curled up on the floor
    //    footprint roughly x:[0.2,1.0] z:[-1.5,-0.9] y:[0,0.4]
    // ===================================================================
    var catGroup = new THREE.Group();
    catGroup.position.set(0.6, 0, -1.2);
    group.add(catGroup);

    var catBody = new THREE.Mesh(new THREE.SphereGeometry(0.22, 8, 6), catMat);
    catBody.scale.set(1.25, 0.62, 1.05);
    catBody.position.set(0, 0.15, 0);
    catGroup.add(catBody);

    var earGeo = new THREE.ConeGeometry(0.045, 0.09, 6);
    var earL = new THREE.Mesh(earGeo, catMat);
    earL.position.set(-0.08, 0.29, 0.13);
    earL.rotation.z = 0.15;
    catGroup.add(earL);
    var earR = new THREE.Mesh(earGeo, catMat);
    earR.position.set(0.08, 0.29, 0.13);
    earR.rotation.z = -0.15;
    catGroup.add(earR);

    var eyeGeo = new THREE.SphereGeometry(0.02, 6, 4);
    var eyeL = new THREE.Mesh(eyeGeo, eyeGlowMat);
    eyeL.position.set(-0.045, 0.18, 0.19);
    catGroup.add(eyeL);
    var eyeR = new THREE.Mesh(eyeGeo, eyeGlowMat);
    eyeR.position.set(0.045, 0.18, 0.19);
    catGroup.add(eyeR);

    // Tail: a short chain of shrinking boxes curling around the body's side,
    // all sharing one unit-cube geometry (each instance scaled individually).
    var tailUnitGeo = new THREE.BoxGeometry(1, 1, 1);
    var tailGroup = new THREE.Group();
    tailGroup.position.set(0, 0.13, -0.15);
    catGroup.add(tailGroup);

    var tailSegs = [
      { x: 0.00, y: 0.00, z: -0.03, ry: 0.0, s: 0.05 },
      { x: 0.03, y: 0.00, z: -0.06, ry: 0.5, s: 0.045 },
      { x: 0.06, y: 0.01, z: -0.07, ry: 1.0, s: 0.04 },
      { x: 0.09, y: 0.015, z: -0.05, ry: 1.6, s: 0.035 },
      { x: 0.10, y: 0.02, z: -0.02, ry: 2.1, s: 0.03 }
    ];
    for (var ti = 0; ti < tailSegs.length; ti++) {
      var seg = tailSegs[ti];
      var segMesh = new THREE.Mesh(tailUnitGeo, catMat);
      segMesh.scale.set(seg.s, seg.s, seg.s * 1.6);
      segMesh.position.set(seg.x, seg.y, seg.z);
      segMesh.rotation.y = seg.ry;
      tailGroup.add(segMesh);
    }

    var lastPettedAt = -999;

    // -----------------------------------------------------------------
    // Interaction state (set in onClick, consumed continuously in update)
    // -----------------------------------------------------------------
    var figureHover = false;
    var ravenHover = false;
    var catHover = false;

    return {
      group: group,
      update: function (dt, elapsed) {
        currentElapsed = elapsed;

        // ---------------- Hooded figure ----------------
        torso.scale.y = 1 + Math.sin(elapsed * 0.5 + 12.3) * 0.015;

        if (elapsed > headTilt.next) {
          headTilt.target = PA.rand(-0.15, 0.15);
          headTilt.next = elapsed + PA.rand(6, 10);
        }
        headTilt.current += (headTilt.target - headTilt.current) * Math.min(1, dt * 0.8);
        headGroup.rotation.z = headTilt.current;

        var noticeT = elapsed - lastNoticedAt;
        var noticeAmt = (noticeT >= 0 && noticeT < 1.5) ? Math.sin(Math.min(noticeT / 1.5, 1) * Math.PI) : 0;
        headGroup.rotation.x = -noticeAmt * 0.35 + (figureHover ? -0.03 : 0);

        handL.rotation.z = PA.flicker(elapsed, 411, 0.4, -0.05, 0.05);
        handR.rotation.z = PA.flicker(elapsed, 433, 0.4, -0.05, 0.05);

        furnitureGroup.rotation.z = PA.flicker(elapsed, 111, 0.12, -0.004, 0.004);

        // ---------------- Raven ----------------
        ravenHeadPivot.rotation.y = Math.sin(elapsed * 0.45 + 7.0) * 0.5;

        var flapT = elapsed - lastFlapAt;
        var flapAmt = (flapT >= 0 && flapT < 1.0) ? Math.sin(Math.min(flapT / 1.0, 1) * Math.PI) : 0;

        var wingIdleL = PA.flicker(elapsed, 611, 1.6, -0.05, 0.05);
        var wingIdleR = PA.flicker(elapsed, 622, 1.6, -0.05, 0.05);
        wingPivotL.rotation.z = wingFold + wingIdleL - flapAmt * 0.95;
        wingPivotR.rotation.z = -wingFold - wingIdleR + flapAmt * 0.95;

        ravenGroup.position.y = ravenBaseY + flapAmt * 0.3 + (ravenHover ? 0.01 : 0);
        ravenTail.rotation.x = Math.sin(elapsed * 0.9 + 3.0) * 0.06 - flapAmt * 0.2;

        // ---------------- Cat ----------------
        var petT = elapsed - lastPettedAt;
        var petAmt = (petT >= 0 && petT < 2.0) ? Math.sin(Math.min(petT / 2.0, 1) * Math.PI) : 0;

        var breathe = 1 + Math.sin(elapsed * 0.7 + 5.5) * 0.02;
        catBody.scale.set(
          1.25 * (1 - petAmt * 0.12) * breathe,
          0.62 * (1 + petAmt * 0.55) * breathe,
          1.05 * (1 + petAmt * 0.2) * breathe
        );
        catGroup.position.y = petAmt * 0.1;

        var tailSwish = Math.sin(elapsed * 0.8 + 9.1) * 0.25;
        tailGroup.rotation.y = tailSwish + petAmt * 0.15;
        tailGroup.rotation.x = -petAmt * 0.5;

        var earPerk = petAmt * 0.1;
        earL.rotation.x = -earPerk;
        earR.rotation.x = -earPerk;

        // Blink: eyes dip briefly every few seconds, otherwise stay lit;
        // gets a touch brighter/prouder right after a pet.
        var blinkPhase = elapsed % 4.5;
        var blinkClose = blinkPhase < 0.12 ? Math.sin((blinkPhase / 0.12) * Math.PI) : 0;
        eyeGlowMat.opacity = (0.95 - blinkClose * 0.85) * (1 + petAmt * 0.2);
      },
      interactables: [
        {
          object: figureGroup,
          onClick: function () {
            lastNoticedAt = currentElapsed;
          },
          onHoverStart: function () {
            figureHover = true;
          },
          onHoverEnd: function () {
            figureHover = false;
          },
          cursor: 'pointer',
          hint: 'Странная фигура у камина'
        },
        {
          object: ravenGroup,
          onClick: function () {
            lastFlapAt = currentElapsed;
          },
          onHoverStart: function () {
            ravenHover = true;
          },
          onHoverEnd: function () {
            ravenHover = false;
          },
          cursor: 'pointer',
          hint: 'Ворон присматривает за тобой'
        },
        {
          object: catGroup,
          onClick: function () {
            lastPettedAt = currentElapsed;
          },
          onHoverStart: function () {
            catHover = true;
          },
          onHoverEnd: function () {
            catHover = false;
          },
          cursor: 'pointer',
          hint: 'Погладить кота'
        }
      ]
    };
  };
})();
