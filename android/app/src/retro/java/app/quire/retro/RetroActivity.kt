package app.quire.retro

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quire.weather.DayForecast
import app.quire.weather.Forecast
import app.quire.weather.Sky
import app.quire.weather.WeatherSettings
import app.quire.weather.ui.WeatherModel
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/**
 * Quire 95: the same forecast, drawn the way a computer would have drawn it in 1995.
 *
 * It is a separate application on purpose. Everything below the interface — the Open-Meteo
 * client, the store, the settings, the wake-up jobs — is the shared `wxcore` the modern weather
 * app uses, so this is not a fork of the forecast, only of the paint. What it cannot reach is
 * the design system: not a Material component in the tree, no dynamic colour, no motion scheme.
 * The palette is the sixteen colours VGA guaranteed and the type is the system sans at twelve
 * points, because that is what the era had.
 *
 * There is no dark mode. Windows 95 did not have one.
 */
class RetroActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Desktop95() }
    }
}

@Composable
internal fun Desktop95(model: WeatherModel = viewModel()) {
    val context = LocalContext.current
    // The units are the one preference this build honours: it has no settings window of its
    // own, so it wears whatever the modern app was told — the store is shared.
    val settings = remember(context) { WeatherSettings.get(context) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) model.permissionGranted() }

    LaunchedEffect(Unit) { model.refresh() }

    // The teal desktop, and the window sitting on it with a margin — which is the picture
    // anybody who used the thing has in their head.
    Box(
        Modifier
            .fillMaxSize()
            .background(Win95.Desktop)
            .padding(6.dp),
    ) {
        Window95(
            title = "Weather - " + (model.forecast?.place?.takeIf { it.isNotBlank() } ?: "Untitled"),
            modifier = Modifier.fillMaxSize(),
            onClose = { (context as? ComponentActivity)?.finish() },
        ) {
            Column(Modifier.fillMaxSize()) {
                MenuBar95()
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                ) {
                    val forecast = model.forecast
                    if (forecast == null) {
                        Waiting95(
                            located = model.located,
                            onGrant = { ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                        )
                    } else {
                        Now95(forecast, settings)
                        Spacer(Modifier.height(8.dp))
                        Readings95(forecast, settings)
                        Spacer(Modifier.height(8.dp))
                        Days95(forecast, settings)
                    }
                }
                StatusBar95(model)
            }
        }
    }
}

/** The menu bar. Nothing drops down; every one of these was a word you could not click twice. */
@Composable
private fun MenuBar95() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Win95.Face)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf("File", "Edit", "View", "Help").forEach { BasicText(it, style = Win95.Body) }
    }
}

/** The big number, in a sunken well, with the sky spelled out beside it in bold. */
@Composable
private fun Now95(forecast: Forecast, settings: WeatherSettings) {
    Well95(Modifier.fillMaxWidth().testTag("retro-now"), PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkyGlyph95(forecast.now.sky, forecast.now.day, Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(settings.write(forecast.now.temperature), style = Win95.Big)
                BasicText(skyWord(forecast.now.sky), style = Win95.Bold)
                BasicText(
                    "Feels like " + settings.write(forecast.now.feelsLike),
                    style = Win95.Dim,
                )
            }
        }
    }
}

/** The readings, as a two-column table with a rule between the rows, like a dialog's list. */
@Composable
private fun Readings95(forecast: Forecast, settings: WeatherSettings) {
    Panel95(Modifier.fillMaxWidth(), out = false, thick = false) {
        Column(Modifier.padding(1.dp)) {
            val rows = buildList {
                add("Humidity" to forecast.now.humidity.toString() + "%")
                add("Wind" to settings.writeWind(forecast.now.wind).toString() + " " +
                    settings.wind.key.uppercase(Locale.ROOT))
                if (forecast.now.gust >= 0) {
                    add("Gust" to settings.writeWind(forecast.now.gust).toString())
                }
                if (forecast.now.pressure >= 0) {
                    add("Pressure" to settings.pressure.from(forecast.now.pressure)
                        .toInt().toString())
                }
                if (forecast.now.uv >= 0) add("UV index" to forecast.now.uv.toInt().toString())
            }
            rows.forEachIndexed { index, (name, value) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (index % 2 == 0) Win95.Field else Win95.Face)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    BasicText(name, style = Win95.Body, modifier = Modifier.weight(1f))
                    BasicText(value, style = Win95.Bold)
                }
            }
        }
    }
}

/**
 * The five days as a list box, the selected row inverted to navy and white.
 *
 * A list box in 1995 was a white hole with a highlighted row, and clicking a row moved the
 * highlight — which is the entire interaction this screen has, and exactly as much as it needs.
 */
@Composable
private fun Days95(forecast: Forecast, settings: WeatherSettings) {
    var chosen by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        BasicText("Extended forecast:", style = Win95.Body)
        Spacer(Modifier.height(3.dp))
        Well95(Modifier.fillMaxWidth().testTag("retro-days"), PaddingValues(2.dp)) {
            Column(Modifier.fillMaxWidth()) {
                forecast.ahead(5).forEachIndexed { index, day ->
                    DayRow95(day, index == chosen, settings) { chosen = index }
                }
            }
        }
    }
}

@Composable
private fun DayRow95(
    day: DayForecast,
    selected: Boolean,
    settings: WeatherSettings,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Win95.Selection else Win95.Field)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (selected) Win95.Field else Win95.Ink
        BasicText(
            text = day.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.ENGLISH),
            style = Win95.Body.copy(color = ink),
            modifier = Modifier.width(44.dp),
        )
        SkyGlyph95(day.sky, true, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        BasicText(
            text = skyWord(day.sky),
            style = Win95.Body.copy(color = ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = settings.write(day.high) + " / " + settings.write(day.low),
            style = Win95.Bold.copy(color = ink),
        )
    }
}

/** The status bar: two sunken boxes, the way every window of the era ended. */
@Composable
private fun StatusBar95(model: WeatherModel) {
    val stamp = remember(model.forecast?.fetched) {
        model.forecast?.fetched?.let {
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).format(
                java.time.Instant.ofEpochMilli(it)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime(),
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().background(Win95.Face).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Panel95(Modifier.weight(1f), out = false, thick = false, padding = PaddingValues(3.dp)) {
            BasicText(
                text = if (model.loading) "Connecting..." else "Ready",
                style = Win95.Body,
                maxLines = 1,
            )
        }
        Panel95(out = false, thick = false, padding = PaddingValues(3.dp)) {
            BasicText(stamp?.let { "Updated $it" } ?: "--:--", style = Win95.Body, maxLines = 1)
        }
    }
}

/** Before the first fetch: the dialog the era would have put up, buttons and all. */
@Composable
private fun Waiting95(located: Boolean, onGrant: () -> Unit) {
    Panel95(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = if (located) {
                    "Contacting weather service..."
                } else {
                    "Quire 95 needs to know where you are\nbefore it can report the weather."
                },
                style = Win95.Body,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            if (!located) Button95("OK", onClick = onGrant)
        }
    }
}

/**
 * The sky as a glyph, drawn rather than fetched.
 *
 * The modern app's vector icons live in the shared resources and would work here — and would be
 * wrong here. A 2026 icon set on a 1995 window is the one thing that would give the joke away,
 * so these are blocks: a disc for the sun, a stack for cloud, strokes for rain, and that is the
 * whole vocabulary a sixteen-colour icon had.
 */
@Composable
private fun SkyGlyph95(sky: Sky, day: Boolean, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val unit = size.minDimension / 16f
        fun box(x: Float, y: Float, w: Float, h: Float, colour: androidx.compose.ui.graphics.Color) {
            drawRect(colour, androidx.compose.ui.geometry.Offset(x * unit, y * unit),
                androidx.compose.ui.geometry.Size(w * unit, h * unit))
        }
        val sun = androidx.compose.ui.graphics.Color(0xFFFFFF00)
        val moon = androidx.compose.ui.graphics.Color(0xFFC0C0C0)
        // Silver, not white: the well behind this glyph is white, and a white cloud on a
        // white field is a cloud nobody can see — which is exactly how the first render came out.
        val cloud = androidx.compose.ui.graphics.Color(0xFFC0C0C0)
        val cloudEdge = Win95.Shadow
        val water = androidx.compose.ui.graphics.Color(0xFF0000FF)
        val bolt = androidx.compose.ui.graphics.Color(0xFFFFFF00)

        val clear = sky == Sky.CLEAR || sky == Sky.MOSTLY_CLEAR
        val cloudy = sky != Sky.CLEAR
        val wet = sky in setOf(Sky.DRIZZLE, Sky.RAIN, Sky.SHOWERS, Sky.THUNDER, Sky.SLEET)
        val snowy = sky == Sky.SNOW || sky == Sky.SLEET

        if (clear || sky == Sky.PARTLY_CLOUDY || sky == Sky.MOSTLY_CLEAR) {
            val disc = if (day) sun else moon
            box(9f, 1f, 5f, 5f, disc)
            box(8f, 2f, 7f, 3f, disc)
        }
        if (cloudy) {
            box(2f, 6f, 11f, 4f, cloud)
            box(4f, 4f, 7f, 3f, cloud)
            box(2f, 9f, 11f, 1f, cloudEdge)
        }
        if (wet) {
            val ink = if (snowy) androidx.compose.ui.graphics.Color(0xFF00FFFF) else water
            listOf(3f, 6f, 9f).forEachIndexed { index, x ->
                box(x, 11f + (index % 2), 1f, 3f, ink)
            }
        }
        if (sky == Sky.THUNDER) box(7f, 11f, 2f, 4f, bolt)
        if (sky == Sky.FOG) {
            listOf(6f, 9f, 12f).forEach { y -> box(2f, y, 12f, 1f, cloudEdge) }
        }
    }
}

/** The sky in the era's own vocabulary: short, capitalised, and in English, like the OS. */
internal fun skyWord(sky: Sky): String = when (sky) {
    Sky.CLEAR -> "Clear"
    Sky.MOSTLY_CLEAR -> "Mostly clear"
    Sky.PARTLY_CLOUDY -> "Partly cloudy"
    Sky.OVERCAST -> "Overcast"
    Sky.FOG -> "Fog"
    Sky.DRIZZLE -> "Drizzle"
    Sky.RAIN -> "Rain"
    Sky.SHOWERS -> "Showers"
    Sky.SLEET -> "Sleet"
    Sky.SNOW -> "Snow"
    Sky.THUNDER -> "Thunderstorm"
}
