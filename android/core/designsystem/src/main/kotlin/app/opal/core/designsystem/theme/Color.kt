package app.opal.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces

/** Semantic colours. Text on glass is chosen for ≥ 4.5:1 against the glass tint (WCAG AA). */
@Immutable
data class OpalColors(
    val isDark: Boolean,
    /** Behind the aurora (visible while the aurora fades in). */
    val background: Color,
    val onBackground: Color,
    val onBackgroundMuted: Color,
    /** Base tint laid over glass so text keeps its contrast whatever is behind it. */
    val glassTint: Color,
    val glassTintStrong: Color,
    val onGlass: Color,
    val onGlassMuted: Color,
    val glassRim: Color,
    /** Non-glass content panels (lists, circuit, graph): readable, never refractive. */
    val panel: Color,
    val panelStroke: Color,
    val accent: Color,
    val connected: Color,
    val connecting: Color,
    val warning: Color,
    val error: Color,
    val download: Color,
    val upload: Color,
)

private fun p3(r: Float, g: Float, b: Float, a: Float = 1f) =
    Color(r, g, b, a, ColorSpaces.DisplayP3)

val LightColors =
    OpalColors(
        isDark = false,
        background = Color(0xFFEFF1F6),
        onBackground = Color(0xFF0B1020),
        onBackgroundMuted = Color(0xFF475069),
        glassTint = Color.White.copy(alpha = 0.42f),
        glassTintStrong = Color.White.copy(alpha = 0.72f),
        onGlass = Color(0xFF0B1020),
        onGlassMuted = Color(0xFF3D4660),
        glassRim = Color.White.copy(alpha = 0.7f),
        panel = Color.White.copy(alpha = 0.62f),
        panelStroke = Color.White.copy(alpha = 0.8f),
        accent = Color(0xFF3F5BFF),
        connected = Color(0xFF00805E),
        connecting = Color(0xFF8A4B00),
        warning = Color(0xFF8A5A00),
        error = Color(0xFFB3261E),
        download = Color(0xFF007A73),
        upload = Color(0xFF6A3FD9),
    )

val DarkColors =
    OpalColors(
        isDark = true,
        background = Color(0xFF070A12),
        onBackground = Color(0xFFF3F5FA),
        onBackgroundMuted = Color(0xFFB4BCD0),
        glassTint = Color(0xFF0B1020).copy(alpha = 0.36f),
        glassTintStrong = Color(0xFF0B1020).copy(alpha = 0.64f),
        onGlass = Color(0xFFF7F8FC),
        onGlassMuted = Color(0xFFC6CDDD),
        glassRim = Color.White.copy(alpha = 0.32f),
        panel = Color(0xFF0B1020).copy(alpha = 0.5f),
        panelStroke = Color.White.copy(alpha = 0.12f),
        accent = Color(0xFF8C9DFF),
        connected = Color(0xFF6BF0C8),
        connecting = Color(0xFFFFC37A),
        warning = Color(0xFFFFD08A),
        error = Color(0xFFFFB4AB),
        download = Color(0xFF6BE3D9),
        upload = Color(0xFFC3A8FF),
    )

/**
 * Four colours blended by the aurora; defined in Display P3 (clipped to sRGB where unsupported).
 */
@Immutable data class AuroraPalette(val c0: Color, val c1: Color, val c2: Color, val c3: Color)

enum class AuroraMood {
    /** VPN off: cold neutral. */
    Idle,
    /** Connecting: amber–violet. */
    Connecting,
    /** Protected: teal–green. */
    Protected,
    /** Blocked / error: muted warm red. */
    Alert,
}

fun auroraPalette(mood: AuroraMood, dark: Boolean): AuroraPalette =
    if (dark) {
        when (mood) {
            AuroraMood.Idle ->
                AuroraPalette(
                    p3(0.11f, 0.14f, 0.22f),
                    p3(0.16f, 0.19f, 0.29f),
                    p3(0.08f, 0.10f, 0.16f),
                    p3(0.19f, 0.22f, 0.33f),
                )
            AuroraMood.Connecting ->
                AuroraPalette(
                    p3(0.52f, 0.30f, 0.07f),
                    p3(0.30f, 0.17f, 0.52f),
                    p3(0.60f, 0.36f, 0.10f),
                    p3(0.22f, 0.15f, 0.46f),
                )
            AuroraMood.Protected ->
                AuroraPalette(
                    p3(0.03f, 0.38f, 0.32f),
                    p3(0.05f, 0.44f, 0.24f),
                    p3(0.03f, 0.31f, 0.42f),
                    p3(0.10f, 0.47f, 0.36f),
                )
            AuroraMood.Alert ->
                AuroraPalette(
                    p3(0.45f, 0.12f, 0.16f),
                    p3(0.30f, 0.11f, 0.36f),
                    p3(0.50f, 0.20f, 0.12f),
                    p3(0.22f, 0.10f, 0.20f),
                )
        }
    } else {
        when (mood) {
            AuroraMood.Idle ->
                AuroraPalette(
                    p3(0.82f, 0.87f, 0.97f),
                    p3(0.87f, 0.83f, 0.96f),
                    p3(0.93f, 0.94f, 0.97f),
                    p3(0.74f, 0.80f, 0.90f),
                )
            AuroraMood.Connecting ->
                AuroraPalette(
                    p3(1.00f, 0.78f, 0.52f),
                    p3(0.77f, 0.64f, 1.00f),
                    p3(1.00f, 0.70f, 0.47f),
                    p3(0.60f, 0.55f, 1.00f),
                )
            AuroraMood.Protected ->
                AuroraPalette(
                    p3(0.55f, 0.89f, 0.82f),
                    p3(0.72f, 0.93f, 0.78f),
                    p3(0.56f, 0.81f, 0.93f),
                    p3(0.84f, 0.95f, 0.91f),
                )
            AuroraMood.Alert ->
                AuroraPalette(
                    p3(1.00f, 0.72f, 0.66f),
                    p3(0.91f, 0.64f, 0.85f),
                    p3(1.00f, 0.80f, 0.62f),
                    p3(0.85f, 0.62f, 0.72f),
                )
        }
    }
