package app.quire.calendar.core

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import app.quire.engine.design.SystemScheme

/**
 * Puts a colour on a widget in whichever of three ways will keep it honest longest.
 *
 * A widget is a picture the launcher keeps, and there are three ways to colour one:
 *
 *  - **By resource id** (Android 12+). The launcher resolves the id again at every apply, so the
 *    colour follows dark mode *and* the wallpaper palette with this app's process asleep — the
 *    same mechanism the system's own widgets use, and the only one that survives a palette
 *    change without a repaint. Used when the widget is wearing the system's look, which is the
 *    default placement.
 *  - **As a day/night pair** (Android 12+). Both faces baked, the launcher picks at apply time.
 *    Follows dark mode instantly but not the palette; used when the placement was configured
 *    with its own skin or accent, which by definition does not follow the wallpaper.
 *  - **As one int** (before 12). The face worn at paint time, refreshed by the watchers.
 *
 * The [day]/[night] palettes are read once through configuration-forced contexts, so a "pair"
 * paint is correct whatever face the process happens to be wearing when it renders.
 */
class WidgetPaint private constructor(
    private val system: Boolean,
    private val current: Palette,
    private val day: Palette,
    private val night: Palette,
) {

    /** A colour applied through a method taking one int, e.g. setTextColor or setColorFilter. */
    fun tint(views: RemoteViews, id: Int, method: String, res: Int, pick: (Palette) -> Int) {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ->
                views.setInt(id, method, pick(current))
            system && CSL_METHODS.containsKey(method) ->
                views.setColorStateList(id, CSL_METHODS.getValue(method), res)
            else ->
                views.setColorInt(id, method, pick(day), pick(night))
        }
    }

    /** A colour that is data rather than palette — an event's own colour — worn by both faces. */
    fun fixed(views: RemoteViews, id: Int, method: String, colour: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorInt(id, method, colour, colour)
        } else {
            views.setInt(id, method, colour)
        }
    }

    companion object {
        /**
         * The ColorStateList twin of each int-taking method, because the resource path can only
         * go through methods that accept one — a tint list is what the framework re-resolves.
         */
        private val CSL_METHODS = mapOf(
            "setTextColor" to "setTextColor",
            "setColorFilter" to "setImageTintList",
            "setBackgroundColor" to "setBackgroundTintList",
        )

        /**
         * Reads the palette in both faces and decides whether this placement follows the system.
         *
         * A placement follows the system when it is the filled card taking the wallpaper's
         * colours — the default. A Paper or Ink card, or a filled one pinned to a chosen accent,
         * was configured to look one particular way, and resources would overwrite that choice.
         */
        fun of(context: Context, skin: Skin, accent: Accent, dynamic: Boolean): WidgetPaint =
            WidgetPaint(
                system = skin == Skin.COLOUR && dynamic && SystemScheme.supported,
                current = Tokens.widgetPalette(context, skin, accent, dynamic),
                day = Tokens.widgetPalette(face(context, night = false), skin, accent, dynamic),
                night = Tokens.widgetPalette(face(context, night = true), skin, accent, dynamic),
            )

        private fun face(context: Context, night: Boolean): Context {
            val config = Configuration(context.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            return context.createConfigurationContext(config)
        }
    }
}
