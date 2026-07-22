import { AbsoluteFill, Easing, interpolate, useCurrentFrame } from "remotion";
import { palette, serif } from "../theme";

// The closing breath: a quiet line of limestone, glass and brass.
export const SignOff: React.FC = () => {
  const frame = useCurrentFrame();

  const o = interpolate(frame, [0, 24], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });
  const dot = interpolate(frame, [10, 40], [0, 120], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });

  return (
    <AbsoluteFill
      style={{ alignItems: "center", justifyContent: "center", pointerEvents: "none" }}
    >
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 26 }}>
        <div
          style={{
            width: dot,
            height: 1,
            background: `linear-gradient(90deg, transparent, ${palette.gold}, transparent)`,
            opacity: o,
          }}
        />
        <div
          style={{
            fontFamily: serif,
            fontStyle: "italic",
            fontWeight: 400,
            fontSize: 46,
            letterSpacing: "0.02em",
            color: palette.gold2,
            opacity: o,
            translate: `0 ${interpolate(o, [0, 1], [12, 0])}px`,
          }}
        >
          Известняк · стекло · латунь
        </div>
        <div
          style={{
            fontFamily: serif,
            fontWeight: 300,
            fontSize: 26,
            letterSpacing: "0.34em",
            textIndent: "0.34em",
            color: palette.inkFaint,
            opacity: o * 0.9,
          }}
        >
          MMXXVI
        </div>
      </div>
    </AbsoluteFill>
  );
};
