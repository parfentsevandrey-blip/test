package app.quire.weather

import android.content.Context
import android.widget.RemoteViews
import app.quire.retro.W95WidgetRenderer

/**
 * Which card this application's widget draws — the 1995 one.
 *
 * Same class, same package, same signature as the modern flavour's; only this source set is
 * compiled into the retro build. The shared provider in `wxcore` calls it without knowing which
 * decade it is in.
 */
internal object WeatherCard {

    fun build(context: Context, widgetId: Int, widthDp: Int, heightDp: Int): RemoteViews =
        W95WidgetRenderer.build(context, widgetId, widthDp, heightDp)
}
