/* =========================================================================
   Screen-space ambient occlusion.

   The room has exactly one shadow-casting light, so every surface that is not
   in the fire's cone is lit by flat ambient and reads as pasted-on: a table
   leg meets the rug with no darkening, a cushion meets the sofa with no seam,
   a corner is as bright as the middle of a wall. Contact darkening is what
   makes a rendered room feel like a photographed one.

   Contract
   --------
     const ao = new AmbientOcclusion(deps);
     ao.resize(w, h);
     ao.render(depthTexture, camera);   // once per frame, after the room pass
     ao.texture                          // r = 1 unoccluded … 0 fully occluded

   `deps` = { THREE, renderer, blit, rt, VERT_QUAD } — everything the pass
   needs, injected so this file imports nothing but stays testable.

   Only the depth buffer is available; view-space normals must be reconstructed
   from it. The scene is small (a 10 × 8 × 3.3 m room), so the sample radius
   wants to be in the 0.15–0.5 m range, not the usual outdoor scale.
   ========================================================================= */

export class AmbientOcclusion {
  constructor({ THREE, renderer, blit, rt, VERT_QUAD }) {
    this.THREE = THREE;
    this.renderer = renderer;
    this.blit = blit;
    this.scale = 0.5;                 // AO is low-frequency; half res is plenty
    this.strength = 0.0;              // 0 until a real implementation lands
    this.radius = 0.35;               // metres
    this.target = rt(2, 2, { depth: false });
    this.blurTarget = rt(2, 2, { depth: false });

    const uniforms = {
      tDepth: { value: null },
      uProjInv: { value: new THREE.Matrix4() },
      uProj: { value: new THREE.Matrix4() },
      uTexel: { value: new THREE.Vector2() },
      uRadius: { value: this.radius },
      uNear: { value: 0.05 },
      uFar: { value: 6000 },
      uTime: { value: 0 },
    };
    this.uniforms = uniforms;

    // placeholder: fully unoccluded, so the scene renders unchanged until the
    // real pass is written
    this.material = new THREE.ShaderMaterial({
      uniforms,
      vertexShader: VERT_QUAD,
      fragmentShader: /* glsl */`
        varying vec2 vUv;
        void main(){ gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0); }
      `,
      depthTest: false, depthWrite: false,
    });
  }

  resize(w, h) {
    const tw = Math.max(2, (w * this.scale) | 0), th = Math.max(2, (h * this.scale) | 0);
    if (this.target.width !== tw || this.target.height !== th) {
      this.target.setSize(tw, th);
      this.blurTarget.setSize(tw, th);
    }
    this.uniforms.uTexel.value.set(1 / tw, 1 / th);
  }

  render(depthTexture, camera) {
    const u = this.uniforms;
    u.tDepth.value = depthTexture;
    u.uProj.value.copy(camera.projectionMatrix);
    u.uProjInv.value.copy(camera.projectionMatrixInverse);
    u.uNear.value = camera.near;
    u.uFar.value = camera.far;
    u.uRadius.value = this.radius;
    this.blit(this.material, this.target);
  }

  get texture() { return this.target.texture; }
}
