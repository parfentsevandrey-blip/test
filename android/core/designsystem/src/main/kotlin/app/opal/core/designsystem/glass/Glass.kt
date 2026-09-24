package app.opal.core.designsystem.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.shadow.Shadow as ComposeShadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.theme.LocalReducedMotion
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.random.Random

/** Rendering tier for glass. Effects degrade on weaker devices; the network core never does. */
enum class GlassQuality {
    /** API 33+: vibrancy + blur + lens refraction at the edges + specular highlight. */
    Full,
    /** API 31–32: vibrancy + blur + highlight, no refraction (no AGSL). */
    Blur,
    /**
     * Below API 31, "simplified graphics" or battery saver: tint + grain + rim, no live effects.
     */
    Fallback;

    companion object {
        fun forDevice(sdk: Int, simplified: Boolean): GlassQuality =
            when {
                simplified || sdk < 31 -> Fallback
                sdk < 33 -> Blur
                else -> Full
            }
    }
}

/** Glass rendering tier of the current subtree (set by the theme). */
val LocalGlassQuality = staticCompositionLocalOf { GlassQuality.Fallback }

/**
 * What glass refracts. Controls placed inside content sample only the aurora; floating navigation
 * (tab bar, sheets, toasts) samples the aurora and then the content above it. The two layers are
 * recorded by siblings (aurora, content), so nothing is recorded twice and floating glass is never
 * part of what it samples.
 */
@Stable class GlassBackdrops(val aurora: Backdrop, val floating: Backdrop)

@Composable
fun rememberGlassBackdrops(aurora: LayerBackdrop, content: LayerBackdrop): GlassBackdrops {
    val floating = rememberCombinedBackdrop(aurora, content)
    return remember(aurora, floating) { GlassBackdrops(aurora, floating) }
}

val LocalGlassBackdrops = staticCompositionLocalOf<GlassBackdrops?> { null }

enum class GlassLayer {
    /** Glass placed inside screen content (sphere, chips, buttons). */
    InContent,
    /** Glass floating above content (tab bar, sheets, toasts). */
    Floating,
}

@Immutable
data class GlassStyle(
    val blur: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val depthEffect: Boolean = false,
    val chromaticAberration: Boolean = false,
    /** Stronger tint for surfaces carrying a lot of text (sheets). */
    val strongTint: Boolean = false,
    val shadowRadius: Dp = 18.dp,
) {
    companion object {
        val Control = GlassStyle(blur = 2.dp, refractionHeight = 12.dp, refractionAmount = 20.dp)
        val Bar =
            GlassStyle(
                blur = 6.dp,
                refractionHeight = 16.dp,
                refractionAmount = 28.dp,
                depthEffect = true,
            )
        val Sheet =
            GlassStyle(
                blur = 14.dp,
                refractionHeight = 20.dp,
                refractionAmount = 32.dp,
                strongTint = true,
                shadowRadius = 32.dp,
            )
        val Sphere =
            GlassStyle(
                blur = 3.dp,
                refractionHeight = 44.dp,
                refractionAmount = 70.dp,
                depthEffect = true,
                chromaticAberration = true,
                shadowRadius = 36.dp,
            )
    }
}

/**
 * The single place where glass is rendered. [interactive] adds the "jelly" press response and a
 * highlight that follows the finger.
 */
@Composable
fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Control,
    layer: GlassLayer = GlassLayer.InContent,
    tint: Color = Color.Unspecified,
    interactive: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val quality = LocalGlassQuality.current
    val colors = OpalTheme.colors
    val backdrops = LocalGlassBackdrops.current
    val backdrop: Backdrop? = backdrops?.let {
        if (layer == GlassLayer.Floating) it.floating else it.aurora
    }
    val baseTint = if (style.strongTint) colors.glassTintStrong else colors.glassTint
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val press =
        if (interactive)
            remember(scope, reducedMotion) { InteractiveHighlight(scope, jelly = !reducedMotion) }
        else null

    val surface =
        if (quality == GlassQuality.Fallback || backdrop == null) {
            modifier.fallbackGlass(
                shape,
                style,
                colors.glassTintStrong,
                tint,
                colors.glassRim,
                colors.isDark,
            )
        } else {
            modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(style.blur.toPx())
                    if (quality == GlassQuality.Full) {
                        lens(
                            refractionHeight = style.refractionHeight.toPx(),
                            refractionAmount = style.refractionAmount.toPx(),
                            depthEffect = style.depthEffect,
                            chromaticAberration = style.chromaticAberration,
                        )
                    }
                },
                highlight = { Highlight.Default.copy(alpha = if (colors.isDark) 0.55f else 0.9f) },
                shadow = {
                    Shadow(
                        radius = style.shadowRadius,
                        color = Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.12f),
                    )
                },
                layerBlock = press?.layerBlock,
                onDrawSurface = {
                    drawRect(baseTint)
                    if (tint != Color.Unspecified) drawRect(tint)
                },
            )
        }
    Box(
        surface.then(press?.modifier ?: Modifier).then(press?.gestureModifier ?: Modifier),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onGlass) { content() }
    }
}

/** Cheap, static look-alike: translucent tint, fine grain, rim light and a soft drop shadow. */
private fun Modifier.fallbackGlass(
    shape: Shape,
    style: GlassStyle,
    baseTint: Color,
    tint: Color,
    rim: Color,
    dark: Boolean,
): Modifier =
    this.dropShadow(
            shape,
            ComposeShadow(
                radius = style.shadowRadius,
                color = Color.Black.copy(alpha = if (dark) 0.3f else 0.12f),
                offset = DpOffset(0.dp, style.shadowRadius / 6f),
            ),
        )
        .clip(shape)
        .drawWithCache {
            val grain = ShaderBrush(ImageShader(Grain.bitmap, TileMode.Repeated, TileMode.Repeated))
            val sheen =
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.10f else 0.35f),
                    0.45f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width * 0.6f, size.height),
                )
            onDrawBehind {
                drawRect(baseTint)
                if (tint != Color.Unspecified) drawRect(tint)
                drawRect(sheen)
                drawRect(grain, alpha = if (dark) 0.05f else 0.035f)
            }
        }
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(rim, rim.copy(alpha = rim.alpha * 0.15f))),
            shape = shape,
        )

/** A small tileable monochrome noise texture, generated once. */
private object Grain {
    val bitmap: ImageBitmap by lazy {
        val size = 64
        val random = Random(7)
        val pixels =
            IntArray(size * size) {
                val v = random.nextInt(256)
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        android.graphics.Bitmap.createBitmap(
                pixels,
                size,
                size,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            .asImageBitmap()
    }
}
