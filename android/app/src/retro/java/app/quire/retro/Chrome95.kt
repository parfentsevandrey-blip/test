package app.quire.retro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The chrome of 1995, rebuilt from its rules rather than from screenshots.
 *
 * Everything Windows 95 drew came out of one idea: every rectangle is a physical thing lit from
 * the top left. A button is a slab standing proud — white along its top and left, black along
 * its bottom and right, with a grey step between — and pressing it does not tint it, it turns
 * the light around so the slab becomes a hole. A text field is a hole to begin with. A window is
 * a slab with a title bar. That single rule, applied without exception, is the whole look: no
 * rounded corners anywhere, no shadow that is not a bevel, and no colour outside the sixteen the
 * VGA palette guaranteed.
 *
 * None of it is a texture or a nine-patch — it is four filled rectangles per edge, crisp at any
 * density and weighing nothing, which is also how the original did it.
 *
 * The type is drawn with `BasicText` rather than Material's `Text`, and that is deliberate: this
 * interface must not be able to reach the design system the other two apps share. The joke only
 * stays honest if the 1995 build cannot accidentally inherit a 2026 component.
 */
object Win95 {
    /** The face of everything: the grey that ran the decade. */
    val Face = Color(0xFFC0C0C0)

    /** The light and the dark of the bevel, and the mid-tone step between them. */
    val Light = Color(0xFFFFFFFF)
    val Shadow = Color(0xFF808080)
    val DarkEdge = Color(0xFF000000)

    /** The desktop, and the two blues of an active title bar. */
    val Desktop = Color(0xFF008080)
    val TitleLeft = Color(0xFF000080)
    val TitleRight = Color(0xFF1084D0)
    val TitleInk = Color(0xFFFFFFFF)

    /** The inside of a field, and the ink on it. */
    val Field = Color(0xFFFFFFFF)
    val Ink = Color(0xFF000000)
    val InkDim = Color(0xFF808080)

    /** The one accent the era allowed itself, for a highlighted row. */
    val Selection = Color(0xFF000080)

    /** Type: the system font at the sizes the dialogs used, and never a light weight. */
    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = Ink,
    )
    val Bold = Body.copy(fontWeight = FontWeight.Bold)
    val Dim = Body.copy(color = InkDim)
    val Title = Body.copy(fontWeight = FontWeight.Bold, color = TitleInk)
    val Big = Body.copy(fontWeight = FontWeight.Bold, fontSize = 36.sp)
}

/**
 * The bevel, in one place.
 *
 * [out] is a thing standing proud of the surface — a button, a window, a panel. Inverted, it is
 * a hole: a pressed button, a text field, a progress well. The two-step form (white then face;
 * grey then black) is what Windows drew round buttons and windows; the one-step form is the
 * thinner frame it drew round the small boxes on a status bar.
 */
internal fun Modifier.bevel(out: Boolean = true, thick: Boolean = true): Modifier = drawBehind {
    val one = 1.dp.toPx()

    fun edges(offset: Float, topLeft: Color, bottomRight: Color) {
        val w = size.width
        val h = size.height
        drawRect(topLeft, Offset(offset, offset), Size(w - offset * 2f, one))
        drawRect(topLeft, Offset(offset, offset), Size(one, h - offset * 2f))
        drawRect(bottomRight, Offset(offset, h - offset - one), Size(w - offset * 2f, one))
        drawRect(bottomRight, Offset(w - offset - one, offset), Size(one, h - offset * 2f))
    }

    if (out) {
        edges(0f, Win95.Light, Win95.DarkEdge)
        if (thick) edges(one, Win95.Face, Win95.Shadow)
    } else {
        edges(0f, Win95.Shadow, Win95.Light)
        if (thick) edges(one, Win95.DarkEdge, Win95.Face)
    }
}

/** A panel: the grey face with a bevel round it — the container everything else sits in. */
@Composable
internal fun Panel95(
    modifier: Modifier = Modifier,
    out: Boolean = true,
    thick: Boolean = true,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .background(Win95.Face)
            .bevel(out = out, thick = thick)
            .padding(padding),
    ) { content() }
}

/**
 * A window: title bar, the three little buttons, and a client area.
 *
 * The title bar is the one gradient the era allowed — navy to a lighter blue, left to right —
 * and it is why an active window read as active from across the room. The minimise and maximise
 * squares do nothing, and they are drawn anyway: a window without them is not a window.
 */
@Composable
internal fun Window95(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Panel95(modifier, padding = PaddingValues(3.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(Brush.horizontalGradient(listOf(Win95.TitleLeft, Win95.TitleRight)))
                    .padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = title,
                    style = Win95.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TitleButton("_")
                TitleButton("□")
                TitleButton("✕", onClose)
            }
            content()
        }
    }
}

/** One of the three squares at the right of a title bar. */
@Composable
private fun TitleButton(glyph: String, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .padding(start = 2.dp)
            .size(16.dp, 14.dp)
            .background(Win95.Face)
            .bevel(out = true)
            .let { if (onClick == null) it else it.clickable { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = Win95.Body.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold))
    }
}

/**
 * A push button: proud until it is pressed, at which point the light turns around and the label
 * shifts a pixel down and right — on a real button, the label is on the face that moved.
 */
@Composable
internal fun Button95(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .background(Win95.Face)
            .bevel(out = !pressed)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = Win95.Body,
            modifier = Modifier.padding(
                start = if (pressed) 1.dp else 0.dp,
                top = if (pressed) 1.dp else 0.dp,
            ),
        )
    }
}

/** A sunken white box — a field, a list, anything the era wanted to look like a hole. */
@Composable
internal fun Well95(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(4.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .background(Win95.Field)
            .bevel(out = false)
            .padding(padding),
    ) { content() }
}
