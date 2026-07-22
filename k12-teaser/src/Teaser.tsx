import { AbsoluteFill, Easing, interpolate, Sequence, useCurrentFrame } from "remotion";
import { GlyphField } from "./components/GlyphField";
import { HorizonLine } from "./components/HorizonLine";
import { EdgeFades, Grain, LightLeak, Vignette } from "./components/Overlays";
import { SignOff } from "./components/SignOff";
import { TitleCard } from "./components/TitleCard";
import { TwilightSky } from "./components/TwilightSky";

// ── Timeline (30 fps · 420 frames · 14 s) ─────────────────────────────────
//   0.0–1.5 s  cold open: brass horizon draws on the twilight
//   1.3–5.2 s  the building precipitates out of noise as glyphs
//   5.0–7.0 s  the glyph field parts through the centre
//   6.8–12.6 s КУТУЗОВСКИЙ 12 resolves, letter by letter
//  12.4–14.0 s sign-off, then a slow fade to black
export const Teaser: React.FC = () => {
  const frame = useCurrentFrame();

  const form = interpolate(frame, [40, 155], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.33, 0, 0.2, 1),
  });
  const part = interpolate(frame, [205, 258], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });
  const fieldO = interpolate(frame, [40, 82], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  return (
    <AbsoluteFill style={{ backgroundColor: "#06080d" }}>
      <TwilightSky />
      <GlyphField form={form} part={part} opacity={fieldO} />
      <HorizonLine />
      <LightLeak from={150} to={218} />
      <Vignette />

      <Sequence from={205} durationInFrames={180} name="Title" layout="none">
        <TitleCard />
      </Sequence>
      <Sequence from={372} durationInFrames={48} name="SignOff" layout="none">
        <SignOff />
      </Sequence>

      <Grain opacity={0.05} />
      <EdgeFades inTo={12} outFrom={404} outTo={420} />
    </AbsoluteFill>
  );
};
