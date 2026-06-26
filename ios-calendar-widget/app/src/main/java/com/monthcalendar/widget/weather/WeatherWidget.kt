package com.monthcalendar.widget.weather

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.monthcalendar.widget.WidgetGradient
import com.monthcalendar.widget.design.D
import com.monthcalendar.widget.design.Eyebrow
import com.monthcalendar.widget.design.MetricTile
import com.monthcalendar.widget.design.WPalette
import com.monthcalendar.widget.design.WPalettes
import com.monthcalendar.widget.design.glass
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Weather widget, built on the shared design system ([D]/[WPalette]). A
 * weather-and-time-of-day mood gradient hero, vector icons, an hourly strip,
 * a multi-day forecast and a stat strip. Data: Open-Meteo (free, key-less).
 */
class WeatherWidget : GlanceAppWidget() {

    private val small = DpSize(150.dp, 110.dp)
    private val medium = DpSize(220.dp, 200.dp)
    private val large = DpSize(260.dp, 300.dp)

    override val sizeMode = SizeMode.Responsive(setOf(small, medium, large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read the chosen location separately from the cached forecast so that a
        // configured-but-not-yet-fetched widget shows the city + a loading state
        // instead of falling back to the "pick a city" prompt.
        val config = WeatherStore(context).config()
        val data = if (config.isConfigured) {
            WeatherRepository.cached(context) ?: WeatherRepository.refresh(context, System.currentTimeMillis())
        } else {
            null
        }
        val g = WeatherCodes.gradient(data?.code ?: 0, data?.isDay ?: true)
        val gradient = WidgetGradient.vertical(g[0], g[1], g[2])

        provideContent { Content(config, data, gradient) }
    }

    @Composable
    private fun Content(config: WeatherConfig, data: WeatherData?, gradient: ImageProvider) {
        val p = WPalettes.onGradient()
        val size = LocalSize.current
        val tier = when {
            size.height < 150.dp -> 0
            size.height < 250.dp -> 1
            else -> 2
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(gradient)
                .cornerRadius(D.rXl)
                .padding(if (tier == 0) D.s7 else D.s8)
                .clickable(actionStartActivity(weatherActivityIntent())),
        ) {
            when {
                !config.isConfigured -> NotConfigured(p)
                data == null -> Loading(config.locationName, p)
                else -> Body(data, tier, p)
            }
        }
    }

    @Composable
    private fun Body(data: WeatherData, tier: Int, p: WPalette) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = data.locationName.ifBlank { "Погода" },
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = p.textPrimary, fontSize = D.titleSm, fontWeight = FontWeight.Bold),
                )
                if (tier >= 1) {
                    Text(updatedLabel(data.updatedAt), style = TextStyle(color = p.textFaint, fontSize = D.eyebrow))
                }
            }
            Spacer(GlanceModifier.height(if (tier == 0) D.s1 else D.s4))

            // Hero: icon + temperature + condition + hi/lo
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(WeatherCodes.iconRes(data.code, data.isDay)),
                    contentDescription = WeatherCodes.label(data.code),
                    modifier = GlanceModifier.size(if (tier == 0) 46.dp else 60.dp),
                )
                Spacer(GlanceModifier.width(D.s4))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${data.temp.roundToInt()}${data.tempUnit}",
                        style = TextStyle(color = p.textPrimary, fontSize = if (tier == 0) D.display2 else D.display, fontWeight = FontWeight.Bold),
                    )
                    if (tier >= 1) {
                        Text(WeatherCodes.label(data.code), maxLines = 1, style = TextStyle(color = p.textSecondary, fontSize = D.label))
                    }
                }
                data.today?.let { t ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text("↑ ${t.max.roundToInt()}°", style = TextStyle(color = p.textPrimary, fontSize = D.body, fontWeight = FontWeight.Medium))
                        Spacer(GlanceModifier.height(D.s1))
                        Text("↓ ${t.min.roundToInt()}°", style = TextStyle(color = p.textFaint, fontSize = D.body))
                    }
                }
            }

            if (tier >= 1 && data.hourly.isNotEmpty()) {
                Spacer(GlanceModifier.height(D.s5))
                HourlyStrip(data, p)
            }

            if (tier >= 2) {
                if (data.daily.size > 1) {
                    Spacer(GlanceModifier.height(D.s5))
                    DailyList(data, p)
                }
                Spacer(GlanceModifier.height(D.s5))
                StatStrip(data, p)
            }
        }
    }

    @Composable
    private fun HourlyStrip(data: WeatherData, p: WPalette) {
        Column(modifier = glass(p).fillMaxWidth().padding(D.s4)) {
            Eyebrow("почасово", p)
            Spacer(GlanceModifier.height(D.s3))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                data.hourly.take(6).forEachIndexed { i, h ->
                    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) "сейчас" else "%02d".format(h.time.hour), style = TextStyle(color = p.textFaint, fontSize = D.eyebrow))
                        Spacer(GlanceModifier.height(D.s2))
                        Image(provider = ImageProvider(WeatherCodes.iconRes(h.code, h.isDay)), contentDescription = null, modifier = GlanceModifier.size(22.dp))
                        Spacer(GlanceModifier.height(D.s2))
                        Text("${h.temp.roundToInt()}°", style = TextStyle(color = p.textPrimary, fontSize = D.label, fontWeight = FontWeight.Medium))
                        if (h.precipProb >= 20) {
                            Text("${h.precipProb}%", style = TextStyle(color = p.info, fontSize = D.eyebrow))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DailyList(data: WeatherData, p: WPalette) {
        Column(modifier = glass(p).fillMaxWidth().padding(horizontal = D.s5, vertical = D.s4)) {
            Eyebrow("на неделю", p)
            Spacer(GlanceModifier.height(D.s3))
            data.daily.drop(1).take(4).forEachIndexed { i, d ->
                if (i > 0) Spacer(GlanceModifier.height(D.s4))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dowShort(d.date), style = TextStyle(color = p.textPrimary, fontSize = D.body, fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.width(D.s4))
                    Image(provider = ImageProvider(WeatherCodes.iconRes(d.code, true)), contentDescription = null, modifier = GlanceModifier.size(20.dp))
                    if (d.precipProb >= 20) {
                        Spacer(GlanceModifier.width(D.s3))
                        Text("${d.precipProb}%", style = TextStyle(color = p.info, fontSize = D.label))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Text("${d.min.roundToInt()}°", style = TextStyle(color = p.textFaint, fontSize = D.body))
                    Spacer(GlanceModifier.width(D.s4))
                    Text("${d.max.roundToInt()}°", style = TextStyle(color = p.textPrimary, fontSize = D.body, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    @Composable
    private fun StatStrip(data: WeatherData, p: WPalette) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            MetricTile(glass(p, strong = true).defaultWeight(), data.today?.sunrise?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "—", "восход", p)
            Spacer(GlanceModifier.width(D.s3))
            MetricTile(glass(p, strong = true).defaultWeight(), data.today?.sunset?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "—", "закат", p)
            Spacer(GlanceModifier.width(D.s3))
            MetricTile(glass(p, strong = true).defaultWeight(), "${data.windMax.roundToInt()}", "ветер", p)
            Spacer(GlanceModifier.width(D.s3))
            MetricTile(glass(p, strong = true).defaultWeight(), "${data.uvMax.roundToInt()}", "уф", p)
        }
    }

    @Composable
    private fun Loading(location: String, p: WPalette) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(location.ifBlank { "Погода" }, maxLines = 1, style = TextStyle(color = p.textPrimary, fontSize = D.title, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(D.s3))
            Text("Обновление…", style = TextStyle(color = p.textSecondary, fontSize = D.label))
        }
    }

    @Composable
    private fun NotConfigured(p: WPalette) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(provider = ImageProvider(WeatherCodes.iconRes(2, true)), contentDescription = null, modifier = GlanceModifier.size(46.dp))
            Spacer(GlanceModifier.height(D.s4))
            Text("Погода", style = TextStyle(color = p.textPrimary, fontSize = D.title, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(D.s2))
            Text("Нажмите, чтобы выбрать город", style = TextStyle(color = p.textSecondary, fontSize = D.label, textAlign = TextAlign.Center))
        }
    }

    private fun weatherActivityIntent(): Intent =
        Intent().setClassName(
            "com.monthcalendar.widget", "com.monthcalendar.widget.WeatherActivity",
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
}

private val DOW_RU = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private fun dowShort(date: LocalDate): String =
    if (date == LocalDate.now()) "Сегодня" else DOW_RU[date.dayOfWeek.value - 1]

private fun updatedLabel(epoch: Long): String {
    if (epoch <= 0) return ""
    val mins = ((System.currentTimeMillis() - epoch) / 60000L).toInt()
    return when {
        mins <= 1 -> "только что"
        mins < 60 -> "$mins мин"
        else -> "${mins / 60} ч"
    }
}
