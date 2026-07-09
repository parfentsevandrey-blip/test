const SYNODIC_MONTH_MS = 29.530588853 * 86_400_000
/** A known new moon (Jan 6, 2000, 18:14 UTC) — everything else is derived from the fixed synodic period. */
const KNOWN_NEW_MOON_MS = Date.UTC(2000, 0, 6, 18, 14)

/**
 * Pure client-side moon phase — no API call. 0 = new moon, 0.5 = full moon,
 * wrapping back to (approaches) 1 = new moon again. Accurate to within a
 * couple of hours over centuries, which is more than enough for a decorative
 * night-time marker.
 */
export function getMoonPhase(nowMs: number): number {
  const elapsedMs = nowMs - KNOWN_NEW_MOON_MS
  const phase = (elapsedMs % SYNODIC_MONTH_MS) / SYNODIC_MONTH_MS
  return phase < 0 ? phase + 1 : phase
}
