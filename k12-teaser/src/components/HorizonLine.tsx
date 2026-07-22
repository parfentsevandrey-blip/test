import { AbsoluteFill, Easing, interpolate, useCurrentFrame } from "remotion";

// The cold open: a single brass line drawing itself across the twilight
// horizon before the building precipitates out of it.
export const HorizonLine: React.FC = () => {
  const frame = useCurrentFrame();

  const w = interpolate(frame, [6, 46], [0, 64], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });
  const glow = interpolate(frame, [8, 40], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  // Hand off to the glyph field: the line fades as the building forms.
  const out = interpolate(frame, [46, 82], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  return (
    <AbsoluteFill
      style={{
        alignItems: "center",
        justifyContent: "center",
        opacity: out,
        pointerEvents: "none",
      }}
    >
      {/* Soft brass bloom pooling on the horizon. */}
      <div
        style={{
          position: "absolute",
          top: "58%",
          width: "70%",
          height: 220,
          opacity: glow * 0.6,
          background:
            "radial-gradient(60% 100% at 50% 50%, rgba(201,163,94,0.35), transparent 70%)",
          filter: "blur(8px)",
        }}
      />
      <div
        style={{
          position: "absolute",
          top: "calc(58% + 108px)",
          width: `${w}%`,
          height: 2,
          background:
            "linear-gradient(90deg, transparent, rgba(231,201,138,0.9) 50%, transparent)",
          boxShadow: "0 0 18px rgba(201,163,94,0.6)",
        }}
      />
    </AbsoluteFill>
  );
};
