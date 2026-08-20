package app.quire.weather

import android.content.Context
import android.widget.RemoteViews

/**
 * Which card this application's widget draws — the Material 3 one.
 *
 * The provider, the fetch and the wake-ups are shared with the retro build in `src/wxcore`, and
 * this is the one seam between them: each flavour compiles its own `WeatherCard`, so a widget
 * repaint asked for by shared code lands on the interface that flavour actually has. No flag, no
 * branch, no reflection — the linker picks.
 */
internal object WeatherCard {

    fun build(context: Context, widgetId: Int, widthDp: Int, heightDp: Int): RemoteViews =
        WeatherWidgetRenderer.build(context, widgetId, widthDp, heightDp)
}
