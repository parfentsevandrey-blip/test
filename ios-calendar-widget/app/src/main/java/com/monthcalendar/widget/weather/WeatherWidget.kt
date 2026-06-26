package com.monthcalendar.widget.weather

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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
import com.monthcalendar.widget.R
import com.monthcalendar.widget.WidgetGradient
import com.monthcalendar.widget.design.D
import com.monthcalendar.widget.design.Eyebrow
import com.monthcalendar.widget.design.MetricTile
import com.monthcalendar.widget.design.WPalette
import com.monthcalendar.widget.design.WPalettes
import com.monthcalendar.widget.design.glass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Weather widget on the shared design system. A weather-and-time-of-day mood
 * gradient hero, hand-drawn vector icons, an hourly strip, a multi-day forecast
 * and an icon-led stat strip. Robust states: configured-but-loading, stale, and
 * the not-configured prompt. Data: Open-Meteo (free, no key).
 */
class WeatherWidget : GlanceAppWidget() {

    private val small = DpSize(150.dp, 110.dp)
    private val medium = DpSize(220.dp, 200.dp)
    private val large = DpSize(260.dp, 300.dp)

    override val sizeMode = SizeMode.Responsive(setOf(small, medium, large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = WeatherStore(context).config()
        val now = System.currentTimeMillis()
        // Render-only: never fetch on the Glance update path (that could add tens
        // of seconds of latency on a cold/slow network). Read the cache; if it's
        // empty or stale, let the background worker fetch and re-render.
        val data = if (config.isConfigured) WeatherRepository.cached(context) else null
        val ageMs = if (data != null) now - data.updatedAt else 0L
        val isStale = data != null && ageMs > STALE_AFTER_MS
        if (config.isConfigured && (data == null || isStale)) {
            WeatherWorker.enqueueExpedited(context) // self-heal on the home screen
        }

        val g = WeatherCodes.gradient(data?.code ?: 0, data?.isDay ?: true)
        val gradient = WidgetGradient.vertical(g[0], g[1], g[2])
        val is24h = DateFormat.is24HourFormat(context)

        provideContent { Content(config, data, isStale, is24h, gradient) }
    }

    @Composable
    private fun Content(
        config: WeatherConfig,
        data: WeatherData?,
        isStale: Boolean,
        is24h: Boolean,
        gradient: ImageProvider,
    ) {
        val p = WPalettes.onGradient()
        val tier = when {
            LocalSize.current.height < 150.dp -> 0
            LocalSize.current.height < 250.dp -> 1
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
                else -> Body(data, tier, isStale, is24h, p)
            }
        }
    }

    @Composable
    private fun Body(data: WeatherData, tier: Int, isStale: Boolean, is24h: Boolean, p: WPalette) {
        val ctx = LocalContext.current
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = data.locationName.ifBlank { ctx.getString(R.string.w_title) },
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = p.textPrimary, fontSize = D.titleSm, fontWeight = FontWeight.Bold),
                )
                if (tier >= 1) {
                    Text(
                        text = if (isStale) ctx.getString(R.string.w_stale) else updatedLabel(ctx, data.updatedAt),
                        style = TextStyle(color = p.textFaint, fontSize = D.eyebrow),
                    )
                }
            }
            Spacer(GlanceModifier.height(if (tier == 0) D.s1 else D.s4))

            // Hero
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(WeatherCodes.iconRes(data.code, data.isDay)),
                    contentDescription = ctx.getString(WeatherCodes.labelRes(data.code)),
                    modifier = GlanceModifier.size(if (tier == 0) 46.dp else 60.dp),
                )
                Spacer(GlanceModifier.width(D.s4))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${data.temp.roundToInt()}${data.tempUnit}",
                        style = TextStyle(color = p.textPrimary, fontSize = if (tier == 0) D.display2 else D.display, fontWeight = FontWeight.Bold),
                    )
                    if (tier >= 1) {
                        Text(ctx.getString(WeatherCodes.labelRes(data.code)), maxLines = 1, style = TextStyle(color = p.textSecondary, fontSize = D.label))
                    }
                }
                data.today?.let { t -> HiLo(t.max, t.min, p) }
            }

            if (tier >= 1) {
                Spacer(GlanceModifier.height(D.s2))
                Text(
                    text = "${ctx.getString(R.string.w_feels)} ${data.apparentTemp.roundToInt()}°  ·  ${ctx.getString(R.string.w_humidity)} ${data.humidity}%",
                    style = TextStyle(color = p.textFaint, fontSize = D.caption),
                )
            }

            if (tier >= 1 && data.hourly.isNotEmpty()) {
                Spacer(GlanceModifier.height(D.s5))
                HourlyCard(data, is24h, p, ctx)
            }

            if (tier >= 2) {
                if (data.daily.size > 1) {
                    Spacer(GlanceModifier.height(D.s5))
                    DailyCard(data, p, ctx)
                }
                Spacer(GlanceModifier.height(D.s5))
                StatStrip(data, p, ctx)
                Spacer(GlanceModifier.height(D.s3))
                Text(ctx.getString(R.string.w_attribution), style = TextStyle(color = p.textFaint, fontSize = D.eyebrow))
            }
        }
    }

    @Composable
    private fun HiLo(max: Double, min: Double, p: WPalette) {
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(provider = ImageProvider(R.drawable.ic_caret_up), contentDescription = null, colorFilter = ColorFilter.tint(p.textPrimary), modifier = GlanceModifier.size(11.dp))
                Spacer(GlanceModifier.width(D.s1))
                Text("${max.roundToInt()}°", style = TextStyle(color = p.textPrimary, fontSize = D.body, fontWeight = FontWeight.Medium))
            }
            Spacer(GlanceModifier.height(D.s1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(provider = ImageProvider(R.drawable.ic_caret_down), contentDescription = null, colorFilter = ColorFilter.tint(p.textFaint), modifier = GlanceModifier.size(11.dp))
                Spacer(GlanceModifier.width(D.s1))
                Text("${min.roundToInt()}°", style = TextStyle(color = p.textFaint, fontSize = D.body))
            }
        }
    }

    @Composable
    private fun HourlyCard(data: WeatherData, is24h: Boolean, p: WPalette, ctx: Context) {
        Column(modifier = glass(p).fillMaxWidth().padding(D.s4)) {
            Eyebrow(ctx.getString(R.string.w_hourly), p)
            Spacer(GlanceModifier.height(D.s3))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                data.hourly.take(6).forEachIndexed { i, h ->
                    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) ctx.getString(R.string.w_now) else hourLabel(h.time, is24h), style = TextStyle(color = p.textFaint, fontSize = D.eyebrow))
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
    private fun DailyCard(data: WeatherData, p: WPalette, ctx: Context) {
        Column(modifier = glass(p).fillMaxWidth().padding(horizontal = D.s6, vertical = D.s4)) {
            Eyebrow(ctx.getString(R.string.w_this_week), p)
            Spacer(GlanceModifier.height(D.s3))
            data.daily.drop(1).take(4).forEachIndexed { i, d ->
                if (i > 0) Spacer(GlanceModifier.height(D.s4))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dowShort(d.date), style = TextStyle(color = p.textPrimary, fontSize = D.label, fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.width(D.s4))
                    Image(provider = ImageProvider(WeatherCodes.iconRes(d.code, true)), contentDescription = null, modifier = GlanceModifier.size(20.dp))
                    if (d.precipProb >= 20) {
                        Spacer(GlanceModifier.width(D.s3))
                        Text("${d.precipProb}%", style = TextStyle(color = p.info, fontSize = D.label))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Text("${d.min.roundToInt()}°", style = TextStyle(color = p.textFaint, fontSize = D.label))
                    Spacer(GlanceModifier.width(D.s4))
                    Text("${d.max.roundToInt()}°", style = TextStyle(color = p.textPrimary, fontSize = D.label, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    @Composable
    private fun StatStrip(data: WeatherData, p: WPalette, ctx: Context) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            MetricTile(glass(p, strong = true).defaultWeight(), data.today?.sunrise?.let { fmtTime(it.hour, it.minute) } ?: "—", ctx.getString(R.string.w_sunrise), p, R.drawable.ic_sunrise)
            Spacer(GlanceModifier.width(D.s3))
            MetricTile(glass(p, strong = true).defaultWeight(), data.today?.sunset?.let { fmtTime(it.hour, it.minute) } ?: "—", ctx.getString(R.string.w_sunset), p, R.drawable.ic_sunset)
            Spacer(GlanceModifier.width(D.s3))
            MetricTile(glass(p, strong = true).defaultWeight(), "${data.windMax.roundToInt()}", ctx.getString(if (data.metric) R.string.w_unit_kmh else R.string.w_unit_mph), p, R.drawable.ic_wind)
            Spacer(GlanceModifier.width(D.s3))
            MetricTile(glass(p, strong = true).defaultWeight(), "${data.uvMax.roundToInt()}", ctx.getString(R.string.w_uv), p, R.drawable.ic_uv)
        }
    }

    @Composable
    private fun Loading(location: String, p: WPalette) {
        val ctx = LocalContext.current
        Column(
            modifier = GlanceModifier.fillMaxSize().clickable(actionRunCallback<RefreshWeatherAction>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(location.ifBlank { ctx.getString(R.string.w_title) }, maxLines = 1, style = TextStyle(color = p.textPrimary, fontSize = D.title, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(D.s3))
            Text(ctx.getString(R.string.w_updating), style = TextStyle(color = p.textSecondary, fontSize = D.label))
        }
    }

    @Composable
    private fun NotConfigured(p: WPalette) {
        val ctx = LocalContext.current
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(provider = ImageProvider(WeatherCodes.iconRes(2, true)), contentDescription = null, modifier = GlanceModifier.size(46.dp))
            Spacer(GlanceModifier.height(D.s4))
            Text(ctx.getString(R.string.w_title), style = TextStyle(color = p.textPrimary, fontSize = D.title, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(D.s2))
            Text(ctx.getString(R.string.w_pick_city), style = TextStyle(color = p.textSecondary, fontSize = D.label, textAlign = TextAlign.Center))
        }
    }

    private fun weatherActivityIntent(): Intent =
        Intent().setClassName(
            "com.monthcalendar.widget", "com.monthcalendar.widget.WeatherActivity",
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

    companion object {
        private const val STALE_AFTER_MS = 90L * 60L * 1000L
    }
}

private fun fmtTime(h: Int, m: Int): String = "%02d:%02d".format(h, m)

private fun hourLabel(time: java.time.LocalDateTime, is24h: Boolean): String {
    val fmt = if (is24h) DateTimeFormatter.ofPattern("HH") else DateTimeFormatter.ofPattern("h a", Locale.getDefault())
    return time.format(fmt)
}

private fun dowShort(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

private fun updatedLabel(ctx: Context, epoch: Long): String {
    if (epoch <= 0) return ""
    val mins = ((System.currentTimeMillis() - epoch) / 60000L).toInt()
    return when {
        mins <= 1 -> ctx.getString(R.string.w_updated_now)
        mins < 60 -> ctx.getString(R.string.w_updated_min, mins)
        else -> ctx.getString(R.string.w_updated_hour, mins / 60)
    }
}
