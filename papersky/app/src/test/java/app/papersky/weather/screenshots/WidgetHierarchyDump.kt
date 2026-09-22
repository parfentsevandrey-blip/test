package app.papersky.weather.screenshots

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.papersky.weather.Fixtures
import app.papersky.weather.TestApp
import app.papersky.weather.container
import app.papersky.weather.core.data.ForecastStore
import app.papersky.weather.widget.PaperskyWidget
import app.papersky.weather.widget.WidgetConfig
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApp::class, qualifiers = "w411dp-h914dp-xxhdpi")
class WidgetHierarchyDump {
    @Test
    fun dump() {
        Shots.assumeEnabled()
        val app = ApplicationProvider.getApplicationContext<TestApp>()
        runBlocking {
            app.container.places.updateDevice(Fixtures.moscow)
            val store = app.container.weather.javaClass.getDeclaredField("store").apply { isAccessible = true }.get(app.container.weather) as ForecastStore
            store.put(Fixtures.moscow(System.currentTimeMillis()))
        }
        for (size in listOf(DpSize(292.dp, 88.dp), DpSize(140.dp, 192.dp))) {
            val rv = runBlocking { PaperskyWidget().compose(app, size = size, state = WidgetConfig().toPreferences()) }
            val d = app.resources.displayMetrics.density
            val host = FrameLayout(app)
            val v = rv.apply(app, host)
            val w = (size.width.value * d).toInt(); val h = (size.height.value * d).toInt()
            host.addView(v, FrameLayout.LayoutParams(w, h))
            host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, w, h)
            println("==== $size")
            fun walk(view: View, depth: Int) {
                val lp = view.layoutParams
                val extra = if (view is TextView) " '${view.text}'" else ""
                println("  ".repeat(depth) + "${view.javaClass.simpleName} [${view.left},${view.top} ${view.width}x${view.height}] lp=${lp?.width}x${lp?.height} vis=${view.visibility}$extra")
                if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), depth + 1)
            }
            walk(host, 0)
        }
    }
}
