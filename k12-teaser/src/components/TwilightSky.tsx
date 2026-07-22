import { AbsoluteFill, Easing, interpolate, useCurrentFrame } from "remotion";
import { palette } from "../theme";

// The twilight backdrop that shows through the gaps between glyphs — deep
// indigo above, a warm dusk bleed rising from the centre, river-dark below.
export const TwilightSky: React.FC = () => {
  const frame = useCurrentFrame();

  // The dusk slowly warms as the building resolves.
  const warmth = interpolate(frame, [40, 180], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });

  return (
    <AbsoluteFill style={{ backgroundColor: palette.bg }}>
      {/* Vertical twilight gradient: indigo sky → river dark. */}
      <AbsoluteFill
        style={{
          background: `linear-gradient(180deg, ${palette.skyTop} 0%, ${palette.skyMid} 42%, ${palette.bg2} 68%, ${palette.riverLow} 100%)`,
        }}
      />
      {/* Warm dusk / lobby bleed rising from lower-centre. */}
      <AbsoluteFill
        style={{
          opacity: 0.32 + warmth * 0.35,
          background: `radial-gradient(58% 46% at 50% 82%, rgba(201,163,94,0.5), rgba(201,163,94,0.12) 45%, transparent 72%)`,
        }}
      />
      {/* A cool counter-glow at the horizon for depth. */}
      <AbsoluteFill
        style={{
          opacity: 0.25,
          background: `radial-gradient(70% 40% at 50% 30%, rgba(27,39,64,0.6), transparent 70%)`,
        }}
      />
    </AbsoluteFill>
  );
};
