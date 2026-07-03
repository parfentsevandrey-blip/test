/**
 * Tiny WebAudio synth for the Win95 theme's nostalgic sound cues.
 * Everything is generated — no audio assets, so the strict CSP stays intact.
 * If the AudioContext can't start (no user gesture yet), cues are silently
 * skipped: sounds are garnish, never load-bearing.
 */

let ctx: AudioContext | null = null

function withContext(fn: (audio: AudioContext, t0: number) => void): void {
  try {
    if (!ctx) ctx = new AudioContext()
    const audio = ctx
    const run = (): void => fn(audio, audio.currentTime + 0.02)
    if (audio.state === 'suspended') {
      void audio.resume().then(run).catch(() => {})
    } else {
      run()
    }
  } catch {
    // Audio unavailable — the 90s were sometimes silent too.
  }
}

interface ToneOptions {
  type?: OscillatorType
  gain?: number
  attack?: number
  release?: number
}

function tone(
  audio: AudioContext,
  start: number,
  frequency: number,
  duration: number,
  { type = 'sine', gain = 0.06, attack = 0.01, release = 0.12 }: ToneOptions = {}
): void {
  const osc = audio.createOscillator()
  const amp = audio.createGain()
  osc.type = type
  osc.frequency.value = frequency
  amp.gain.setValueAtTime(0, start)
  amp.gain.linearRampToValueAtTime(gain, start + attack)
  amp.gain.setValueAtTime(gain, start + Math.max(attack, duration - release))
  amp.gain.exponentialRampToValueAtTime(0.0004, start + duration)
  osc.connect(amp)
  amp.connect(audio.destination)
  osc.start(start)
  osc.stop(start + duration + 0.05)
}

/** Dreamy boot-up arpeggio in the spirit of a mid-90s OS startup sound. */
export function playStartupChime(): void {
  withContext((audio, t0) => {
    const notes = [311.13, 415.3, 466.16, 622.25, 830.61] // Eb4 Ab4 Bb4 Eb5 Ab5
    notes.forEach((freq, i) => {
      tone(audio, t0 + i * 0.09, freq, 1.6 - i * 0.12, { gain: 0.05, release: 1.0 })
    })
    tone(audio, t0, 103.83, 1.8, { type: 'triangle', gain: 0.045, release: 1.2 }) // Ab2 pad
  })
}

/** The stern little "chord" that used to accompany every forbidden click. */
export function playErrorChord(): void {
  withContext((audio, t0) => {
    for (const freq of [392, 493.88, 587.33]) {
      tone(audio, t0, freq, 0.26, { type: 'square', gain: 0.022, release: 0.16 })
    }
  })
}

/** Dry button tap. */
export function playClickTap(): void {
  withContext((audio, t0) => {
    tone(audio, t0, 1560, 0.04, { type: 'square', gain: 0.028, release: 0.03 })
  })
}

/** Friendly two-note pop for the office-assistant paperclip. */
export function playAssistantPop(): void {
  withContext((audio, t0) => {
    tone(audio, t0, 659.26, 0.09, { gain: 0.05, release: 0.06 })
    tone(audio, t0 + 0.09, 880, 0.14, { gain: 0.05, release: 0.1 })
  })
}
