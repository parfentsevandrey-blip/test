import { useEffect, useRef } from "react";
import { AbsoluteFill, useCurrentFrame, useVideoConfig } from "remotion";
import { colorFor, glyphForLum, sampleField } from "../glyph/field";

type Props = {
  /** 0 → primordial noise, 1 → resolved building. */
  form: number;
  /** 0 → intact, 1 → central swath parted for the title. */
  part: number;
  /** Overall opacity of the whole field. */
  opacity?: number;
};

// «Кутузовский 12» drawn as a live grid of characters on a <canvas>.
export const GlyphField: React.FC<Props> = ({ form, part, opacity = 1 }) => {
  const ref = useRef<HTMLCanvasElement>(null);
  const frame = useCurrentFrame();
  const { width, height, fps } = useVideoConfig();

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const t = frame / fps;
    const panX = t * 0.014; // slow lateral camera drift across the facade

    const cols = Math.round(width / 15);
    const rows = Math.round(height / 22);
    const cellW = width / cols;
    const cellH = height / rows;
    const fontPx = Math.round(cellH * 0.96);

    ctx.clearRect(0, 0, width, height);
    ctx.font = `${fontPx}px "JetBrains Mono", "SFMono-Regular", ui-monospace, monospace`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";

    for (let row = 0; row < rows; row++) {
      const v = (row + 0.5) / rows;
      const cy = row * cellH + cellH / 2;
      for (let col = 0; col < cols; col++) {
        const u = (col + 0.5) / cols;
        const { lum, warm } = sampleField(u, v, { form, part, panX, t });
        if (lum < 0.06) continue;
        const ch = glyphForLum(lum);
        if (ch === " ") continue;

        const [r, g, b] = colorFor(lum, warm);
        // Brighter, warmer cells read as more solid; dark cells stay faint.
        const alpha = Math.min(1, 0.14 + lum * 1.05) * opacity;

        // A soft brass bloom on the brightest cells (columns, lobby, windows).
        if (lum > 0.72 && warm > 0.5) {
          ctx.shadowColor = `rgba(231, 201, 138, ${0.5 * opacity})`;
          ctx.shadowBlur = 6 + lum * 10;
        } else {
          ctx.shadowBlur = 0;
        }

        ctx.fillStyle = `rgba(${r | 0}, ${g | 0}, ${b | 0}, ${alpha})`;
        ctx.fillText(ch, col * cellW + cellW / 2, cy);
      }
    }
    ctx.shadowBlur = 0;
  }, [frame, fps, width, height, form, part, opacity]);

  return (
    <AbsoluteFill>
      <canvas ref={ref} width={width} height={height} style={{ width, height }} />
    </AbsoluteFill>
  );
};
