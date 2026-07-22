import { AbsoluteFill, Easing, interpolate, useCurrentFrame } from "remotion";
import { palette, sans, serif } from "../theme";

const TITLE = "КУТУЗОВСКИЙ 12";

// The name resolves out of the parted glyph field — letter by letter in
// Cormorant brass, framed by a kicker and a subtitle in Manrope.
export const TitleCard: React.FC = () => {
  const frame = useCurrentFrame();

  const kickerO = interpolate(frame, [8, 34], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const ruleW = interpolate(frame, [40, 90], [0, 300], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });
  const subO = interpolate(frame, [66, 92], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  // The whole card eases away before the sign-off.
  const cardOut = interpolate(frame, [148, 168], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.4, 0, 0.2, 1),
  });

  return (
    <AbsoluteFill
      style={{
        alignItems: "center",
        justifyContent: "center",
        opacity: cardOut,
        pointerEvents: "none",
      }}
    >
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 30 }}>
        {/* Kicker */}
        <div
          style={{
            fontFamily: sans,
            fontWeight: 500,
            fontSize: 26,
            letterSpacing: "0.52em",
            textIndent: "0.52em",
            color: palette.gold,
            opacity: kickerO,
            translate: `0 ${interpolate(kickerO, [0, 1], [10, 0])}px`,
          }}
        >
          МОСКВА · КУТУЗОВСКИЙ ПРОСПЕКТ
        </div>

        {/* Title — letter by letter. The brass gradient is applied per-span so
            it survives each letter's own blur/stacking context. */}
        <div
          style={{
            fontFamily: serif,
            fontWeight: 500,
            fontSize: 168,
            lineHeight: 1,
            letterSpacing: "0.04em",
            display: "flex",
            whiteSpace: "pre",
            // Cap-height (lining) figures so the address number reads monumental.
            fontVariantNumeric: "lining-nums",
            fontFeatureSettings: '"lnum" 1',
          }}
        >
          {TITLE.split("").map((ch, i) => {
            const local = frame - 20 - i * 3.4;
            const o = interpolate(local, [0, 20], [0, 1], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(0.16, 1, 0.3, 1),
            });
            const dy = interpolate(o, [0, 1], [26, 0]);
            const blur = interpolate(o, [0, 1], [9, 0]);
            return (
              <span
                key={i}
                style={{
                  opacity: o,
                  translate: `0 ${dy}px`,
                  filter: `blur(${blur}px) drop-shadow(0 12px 34px rgba(0,0,0,0.5))`,
                  display: "inline-block",
                  backgroundImage: `linear-gradient(180deg, ${palette.gold2} 0%, ${palette.gold} 52%, ${palette.goldDeep} 100%)`,
                  WebkitBackgroundClip: "text",
                  backgroundClip: "text",
                  WebkitTextFillColor: "transparent",
                  color: "transparent",
                }}
              >
                {ch}
              </span>
            );
          })}
        </div>

        {/* Brass rule */}
        <div
          style={{
            width: ruleW,
            height: 1,
            background: `linear-gradient(90deg, transparent, ${palette.gold}, transparent)`,
          }}
        />

        {/* Subtitle */}
        <div
          style={{
            fontFamily: sans,
            fontWeight: 300,
            fontSize: 30,
            letterSpacing: "0.16em",
            color: palette.inkSoft,
            opacity: subO,
            translate: `0 ${interpolate(subO, [0, 1], [10, 0])}px`,
          }}
        >
          Клубный дом&nbsp;&nbsp;·&nbsp;&nbsp;Deluxe&nbsp;club&nbsp;house
        </div>
      </div>
    </AbsoluteFill>
  );
};
