package app.quire.calendar.widget

import android.content.Context
import android.content.res.Configuration
import app.quire.calendar.core.Prefs
import app.quire.engine.design.SystemScheme

/**
 * Notices that the device's Material colours have moved under a placed widget, and repaints it.
 *
 * A widget is a picture the launcher holds on to. Quire computes its palette when it builds that
 * picture and bakes the colours into it, so the picture keeps the colours it was painted with
 * until something asks for a new one — and changing the phone's theme asks nobody. That is why a
 * widget used to keep the old colour until it was taken off the home screen and put back.
 *
 * There is no broadcast for this. `ACTION_CONFIGURATION_CHANGED` cannot be delivered to a receiver
 * declared in a manifest, and the wallpaper-changed broadcast has not been sent since API 26. So
 * the change is caught two ways, and either alone is enough:
 *
 *  - the app process, whenever it starts or is reconfigured, compares and repaints — which covers
 *    the theme being changed while Quire is running, and being opened after;
 *  - [CalendarWatchService]'s job wakes on the setting the theme picker writes, which covers the
 *    far more common case of the app not running at all.
 *
 * The comparison is what keeps this cheap: a rotation, a font-scale change and an app launch all
 * arrive here, and a repaint is a cross-process calendar query, so it only happens when the
 * colours really did change.
 */
object SchemeWatch {

    /** Repaints every placed widget if the device's colours are not the ones they were painted in. */
    fun repaintIfChanged(context: Context) {
        val prefs = Prefs.get(context)
        val now = fingerprint(context)
        if (now == prefs.paintedScheme) return
        prefs.paintedScheme = now
        MonthWidgetProvider.requestUpdate(context)
    }

    /** Records the colours as painted, for a repaint that happened for some other reason. */
    fun markPainted(context: Context) {
        Prefs.get(context).paintedScheme = fingerprint(context)
    }

    /**
     * Everything a widget's colours are drawn from, in one number.
     *
     * Both halves of the scheme, because a widget can be showing either — the filled card takes
     * the dark roles, a Paper card the light ones — and a user who changes their colours has
     * changed both. And the night mode on top, because a widget set to follow the system paints a
     * different one of those two halves when the phone goes dark, and that is the same staleness
     * wearing different clothes.
     */
    private fun fingerprint(context: Context): Int {
        var hash = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (SystemScheme.supported) {
            hash = 31 * hash + (SystemScheme.read(context, dark = false)?.hashCode() ?: 0)
            hash = 31 * hash + (SystemScheme.read(context, dark = true)?.hashCode() ?: 0)
        }
        return hash
    }
}
