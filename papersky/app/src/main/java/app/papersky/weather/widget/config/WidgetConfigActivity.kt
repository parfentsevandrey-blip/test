package app.papersky.weather.widget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.papersky.weather.R
import app.papersky.weather.container
import app.papersky.weather.core.model.UserSettings
import app.papersky.weather.core.model.momentAt
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.PaperskyChrome
import app.papersky.weather.ui.WidgetEditScreen

/**
 * Opened by the launcher when a widget is added (optional on Android 12+) or reconfigured from its
 * long-press menu. Shows the same workshop as the app, bound to this widget.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle(UserSettings())
            val places by c.places.data.collectAsStateWithLifecycle(null)
            val scene = remember(places) {
                val place = places?.device ?: places?.saved?.firstOrNull()
                place?.let { c.weather.peek(it.id) }?.let { f ->
                    f.momentAt(System.currentTimeMillis() / 1000)?.let { SceneState.from(it, SceneState.seedFor(f.placeId), f) }
                } ?: SceneState(seed = 3)
            }
            PaperskyChrome(settings, scene) {
                WidgetEditScreen(c, appWidgetId, stringResource(R.string.editor_done)) { saved ->
                    if (saved) setResult(RESULT_OK, result)
                    finish()
                }
            }
        }
    }
}
