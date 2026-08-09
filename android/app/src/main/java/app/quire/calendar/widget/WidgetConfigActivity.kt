package app.quire.calendar.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.quire.calendar.R
import app.quire.calendar.core.Accent
import app.quire.calendar.core.Skin
import app.quire.calendar.core.WidgetPrefs
import app.quire.calendar.ui.BaseActivity
import app.quire.calendar.ui.Chrome
import app.quire.calendar.ui.Panel

/**
 * Configuration with the thing itself on screen. Every control redraws the real
 * RemoteViews tree above it, so opacity, accent and density are judged against
 * the widget rather than against a description of it.
 */
class WidgetConfigActivity : BaseActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var widgetPrefs: WidgetPrefs
    private lateinit var previewHost: PreviewHost
    private val renderer = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "quire-preview").apply { isDaemon = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(Activity.RESULT_CANCELED, resultIntent())
        widgetPrefs = prefs.widget(widgetId)

        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.canvas)
        }
        root.addView(
            Chrome.topBar(this, palette, getString(R.string.widget_config_title)) { finish() },
        )

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // The wallpaper stand-in: a flat mid-tone so card opacity is readable.
        val stage = FrameLayout(this).apply {
            setBackgroundColor(if (palette.dark) 0xFF1E1E1C.toInt() else 0xFFC9C4B8.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(268f),
            )
        }
        previewHost = PreviewHost(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { setMargins(dp(22f), dp(20f), dp(22f), dp(20f)) }
        }
        stage.addView(previewHost)
        content.addView(stage)

        content.addView(
            TextView(this).apply {
                text = getString(R.string.widget_preview_hint)
                setTextColor(palette.inkFaint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setPadding(dp(20f), dp(12f), dp(20f), 0)
            },
        )

        content.addView(buildPanel().view)

        root.addView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(content)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            },
        )

        root.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    maxOf(1, Math.round(density * 0.5f)),
                )
                setBackgroundColor(palette.hairline)
            },
        )
        root.addView(
            TextView(this).apply {
                text = getString(R.string.done)
                setTextColor(palette.accent)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                letterSpacing = 0.13f
                isAllCaps = true
                gravity = Gravity.CENTER
                setPadding(0, dp(18f), 0, dp(18f))
                val out = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
                setOnClickListener { commit() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            },
        )

        setContentView(root)
        padForSystemBars(root)
        previewHost.post { refreshPreview() }
    }

    private fun buildPanel(): Panel {
        val panel = Panel(this, palette)

        panel.section(R.string.section_appearance)
        val skins = listOf(Skin.AUTO, Skin.PAPER, Skin.INK, Skin.COLOUR)
        panel.segmented(
            titleRes = R.string.skin,
            options = listOf(
                getString(R.string.skin_auto),
                getString(R.string.skin_paper),
                getString(R.string.skin_ink),
                getString(R.string.skin_colour),
            ),
            selectedIndex = skins.indexOf(widgetPrefs.skin).coerceAtLeast(0),
        ) { index ->
            widgetPrefs.skin = skins[index]
            refreshPreview()
        }
        panel.accents(widgetPrefs.accent) { accent: Accent ->
            widgetPrefs.accent = accent
            refreshPreview()
        }
        val opacities = listOf(70, 80, 90, 100)
        panel.segmented(
            titleRes = R.string.opacity,
            options = opacities.map { "$it" },
            selectedIndex = opacities.indexOf(widgetPrefs.opacity).takeIf { it >= 0 } ?: 3,
        ) { index ->
            widgetPrefs.opacity = opacities[index]
            refreshPreview()
        }

        panel.section(R.string.section_grid)
        panel.toggle(R.string.show_events, R.string.show_events_hint, widgetPrefs.showEvents) {
            widgetPrefs.showEvents = it
            refreshPreview()
        }
        panel.rule()
        panel.toggle(R.string.coloured_dots, R.string.coloured_dots_hint, widgetPrefs.colouredDots) {
            widgetPrefs.colouredDots = it
            refreshPreview()
        }
        panel.rule()
        panel.toggle(R.string.show_adjacent, R.string.show_adjacent_hint, widgetPrefs.showAdjacent) {
            widgetPrefs.showAdjacent = it
            refreshPreview()
        }
        panel.rule()
        panel.toggle(R.string.dim_weekends, R.string.dim_weekends_hint, widgetPrefs.dimWeekends) {
            widgetPrefs.dimWeekends = it
            refreshPreview()
        }
        panel.rule()
        panel.toggle(R.string.week_numbers, R.string.week_numbers_hint, widgetPrefs.weekNumbers) {
            widgetPrefs.weekNumbers = it
            refreshPreview()
        }
        return panel
    }

    /**
     * Building the tree reads the calendar provider, so it happens off the main
     * thread; only `apply` — which inflates views — comes back to it. The
     * executor is serial, so rapid toggling still lands in order.
     */
    private fun refreshPreview() {
        val id = widgetId
        val app = applicationContext
        renderer.execute {
            val views = WidgetRenderer.build(app, AppWidgetManager.getInstance(app), id)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val rendered = views.apply(this, previewHost)
                previewHost.removeAllViews()
                previewHost.addView(
                    rendered,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        }
    }

    private fun commit() {
        // Writing the skin marks the widget configured for later reconfiguration.
        widgetPrefs.skin = widgetPrefs.skin
        val app = applicationContext
        val id = widgetId
        renderer.execute { MonthWidgetProvider.render(app, AppWidgetManager.getInstance(app), id) }
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    override fun onDestroy() {
        renderer.shutdown()
        super.onDestroy()
    }

    private fun resultIntent() =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    /** Swallows touches so the live preview's own tap targets stay inert. */
    private class PreviewHost(context: Context) : FrameLayout(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent?) = true
        override fun onTouchEvent(event: MotionEvent?) = true
        override fun performClick(): Boolean = super.performClick()
    }
}
