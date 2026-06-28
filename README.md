# organism

A living surface, hand-written in **raw WebGL2** — no library, no framework,
no build step.

It's a **Gray–Scott reaction–diffusion** system: two chemicals iterated on the
GPU across floating-point ping-pong buffers, then shaded as a wet, lit,
iridescent material (height-field normals + moving lights + specular + fresnel +
a curated teal→aqua→gold→magenta palette). It grows, heals, and drifts through
pattern regimes on its own — and you **sculpt it by moving or holding the
pointer** (you grow life where you touch).

Deliberately **none of the usual things**: no particles, no typography, no
scroll, no keyframed animation. The motion *is* the chemistry.

**Open `index.html` directly** — it runs from disk (no server, no dependencies).
~11 KB total.

Needs a WebGL2 GPU with `EXT_color_buffer_float` (every current browser); falls
back to a one-line notice otherwise.

> The previous concept (a particle / scroll site) is preserved under
> `archive/promptfield/`.
