/* =========================================================================
   Aerial perspective, shared by everything outside the glass.

   A rainy night eats contrast with distance faster than a clear one, and it
   eats it unevenly: down at street level the air is full of sodium light
   bouncing off the underside of the weather, so the haze there is warm and
   much denser than it is up at our floor. Both terms live here so the city,
   the ground and the rain can never disagree about where the horizon is.
   ========================================================================= */
import * as THREE from 'three';

export const FOG = /* glsl */`
uniform vec3  uFogColor;
uniform vec3  uFogGround;
uniform float uFogDens;
vec3 applyFog(vec3 col, vec3 worldPos, float dist){
  float f = 1.0 - exp(-uFogDens * uFogDens * dist * dist);
  // fog warms and thickens toward the street far below
  float low = smoothstep(40.0, -140.0, worldPos.y);
  vec3 fc = mix(uFogColor, uFogGround, low * 0.85);
  fc += uFogGround * 0.30 * low;
  return mix(col, fc, clamp(f, 0.0, 1.0));
}
`;

/* one shared set of fog uniforms so every outdoor material stays in sync */
export const FOG_U = {
  uFogColor:  { value: new THREE.Color(0x0b1018) },
  /* Matched to the sodium band the sky shader draws at the horizon. If the
     haze over the far city is darker than the glow above it, the horizon
     turns into a hard line with a bright sky sitting on a dark plain. */
  uFogGround: { value: new THREE.Color(0x2a1d13) },
  /* Tuned against the city's actual size. At 0.0027 anything past 800 m was
     100% haze, which is fine when the skyline is 300 m away and fatal once
     downtown is a kilometre out. */
  uFogDens:   { value: 0.00074 },
};

export const fogUniforms = () => FOG_U;

/** What applyFog() resolves to at street level and full distance — i.e. the
 *  colour the far city fades into. The sky has to blend to the same value at
 *  the horizon, or the edge of the ground plane draws a line across it. */
export function groundHaze() {
  const g = FOG_U.uFogGround.value;
  return new THREE.Color()
    .copy(FOG_U.uFogColor.value).lerp(g, 0.85)
    .add(new THREE.Color().copy(g).multiplyScalar(0.30));
}
