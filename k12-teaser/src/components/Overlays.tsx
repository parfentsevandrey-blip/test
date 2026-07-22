import { AbsoluteFill, Easing, interpolate, useCurrentFrame, useVideoConfig } from "remotion";

// ── Vignette: darken the frame edges for a cinematic falloff ───────────────
export const Vignette: React.FC = () => (
  <AbsoluteFill
    style={{
      background:
        "radial-gradient(120% 100% at 50% 46%, transparent 46%, rgba(3,4,7,0.55) 82%, rgba(2,3,5,0.9) 100%)",
      pointerEvents: "none",
    }}
  />
);

// ── Film grain: animated fractal noise, one fresh seed per frame ───────────
export const Grain: React.FC<{ opacity?: number }> = ({ opacity = 0.06 }) => {
  const frame = useCurrentFrame();
  const { width, height } = useVideoConfig();
  return (
    <AbsoluteFill style={{ opacity, mixBlendMode: "overlay", pointerEvents: "none" }}>
      <svg width={width} height={height}>
        <filter id="grain">
          <feTurbulence
            type="fractalNoise"
            baseFrequency="0.9"
            numOctaves={2}
            seed={frame}
            stitchTiles="stitch"
          />
          <feColorMatrix type="saturate" values="0" />
        </filter>
        <rect width="100%" height="100%" filter="url(#grain)" />
      </svg>
    </AbsoluteFill>
  );
};

// ── Light leak: a single warm sweep that crosses during the reveal ─────────
export const LightLeak: React.FC<{ from: number; to: number }> = ({ from, to }) => {
  const frame = useCurrentFrame();
  const p = interpolate(frame, [from, to], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.4, 0, 0.2, 1),
  });
  const x = interpolate(p, [0, 1], [-40, 140]);
  const fade = interpolate(p, [0, 0.15, 0.85, 1], [0, 1, 1, 0]);
  return (
    <AbsoluteFill style={{ mixBlendMode: "screen", pointerEvents: "none", opacity: fade * 0.5 }}>
      <AbsoluteFill
        style={{
          translate: `${x}% 0`,
          rotate: "12deg",
          scale: "1.6",
          background:
            "linear-gradient(90deg, transparent 0%, rgba(231,201,138,0.0) 34%, rgba(244,226,184,0.55) 50%, rgba(201,163,94,0.0) 66%, transparent 100%)",
        }}
      />
    </AbsoluteFill>
  );
};

// ── Fade from / to black at the head and tail ──────────────────────────────
export const EdgeFades: React.FC<{ inTo: number; outFrom: number; outTo: number }> = ({
  inTo,
  outFrom,
  outTo,
}) => {
  const frame = useCurrentFrame();
  const o = interpolate(
    frame,
    [0, inTo, outFrom, outTo],
    [1, 0, 0, 1],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.4, 0, 0.2, 1) },
  );
  return <AbsoluteFill style={{ backgroundColor: "#020305", opacity: o, pointerEvents: "none" }} />;
};
