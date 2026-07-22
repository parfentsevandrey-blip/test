import { loadFont as loadSerif } from "@remotion/google-fonts/CormorantGaramond";
import { loadFont as loadSans } from "@remotion/google-fonts/Manrope";

// Cormorant Garamond — the editorial serif of the «Кутузовский 12» brand.
export const serif = loadSerif("normal", {
  weights: ["300", "400", "500", "600"],
  subsets: ["latin", "cyrillic"],
}).fontFamily;

// Manrope — the supporting sans.
export const sans = loadSans("normal", {
  weights: ["200", "300", "400", "500", "600"],
  subsets: ["latin", "cyrillic"],
}).fontFamily;

// Brand palette, lifted verbatim from css/styles.css of the landing page.
export const palette = {
  bg: "#06080d",
  bg2: "#0a0d14",
  panel: "#0d111a",
  ink: "#eef0f4",
  inkSoft: "#9aa1b1",
  inkFaint: "#5c6271",
  gold: "#c9a35e",
  gold2: "#e7c98a",
  goldDeep: "#9c7a3e",
  // Twilight + river tones from the site's hero gradients.
  skyTop: "#101a2e",
  skyMid: "#0c1626",
  skyLow: "#1b2740",
  emberLow: "#3a2a1e",
  riverTop: "#2a1c2a",
  riverLow: "#0a0710",
  warmLobby: "#f4e2b8",
} as const;

// Parse "#rrggbb" once into an [r,g,b] tuple for canvas colour math.
export const rgb = (hex: string): [number, number, number] => [
  parseInt(hex.slice(1, 3), 16),
  parseInt(hex.slice(3, 5), 16),
  parseInt(hex.slice(5, 7), 16),
];
