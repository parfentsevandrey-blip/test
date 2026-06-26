package com.monthcalendar.widget.weather

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.monthcalendar.widget.AccentSchemes
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Material 3 Expressive weather widget, a sibling of the calendar widget:
 * dynamic colour, large rounded surface, adaptive size. Data comes from
 * Open-Meteo (free, no key). Compact shows the current conditions; larger
 * sizes add feels-like / humidity / wind and a multi-day forecast.
 */
class WeatherWidget : GlanceAppWidget() {

    private val small = DpSize(150.dp, 110.dp)
    private val medium = DpSize(220.dp, 180.dp)
    private val large = DpSize(260.dp, 260.dp)

    override val sizeMode = SizeMode.Responsive(setOf(small, medium, large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WeatherRepository.cached(context)
            ?: WeatherRepository.refresh(context, System.currentTimeMillis())
        val colors = AccentSchemes.dynamic(context)

        provideContent {
            val content: @Composable () -> Unit = { Content(data) }
            if (colors != null) GlanceTheme(colors = colors, content = content)
            else GlanceTheme(content = content)
        }
    }

    @Composable
    private fun Content(data: WeatherData?) {
        val size = LocalSize.current
        val tier = when {
            size.height < 150.dp -> 0
            size.height < 230.dp -> 1
            else -> 2
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(if (tier == 0) 14.dp else 16.dp)
                .clickable(actionStartActivity(weatherActivityIntent())),
        ) {
            if (data == null) NotConfigured() else WeatherBody(data, tier)
        }
    }

    @Composable
    private fun WeatherBody(data: WeatherData, tier: Int) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Location
            Text(
                text = data.locationName.ifBlank { "Погода" },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (tier == 0) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.height(if (tier == 0) 4.dp else 8.dp))

            // Current: emoji + temperature + condition
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = WeatherCodes.emoji(data.code),
                    style = TextStyle(fontSize = if (tier == 0) 26.sp else 34.sp),
                )
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(
                        text = "${data.temp.roundToInt()}${data.tempUnit}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = if (tier == 0) 28.sp else 36.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    if (tier >= 1) {
                        Text(
                            text = WeatherCodes.label(data.code),
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        )
                    }
                }
            }

            if (tier >= 1) {
                Spacer(GlanceModifier.height(10.dp))
                StatsRow(data)
            }

            if (tier >= 2 && data.daily.isNotEmpty()) {
                Spacer(GlanceModifier.height(12.dp))
                Forecast(data)
            }
        }
    }

    @Composable
    private fun StatsRow(data: WeatherData) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat("Ощущается", "${data.apparentTemp.roundToInt()}${data.tempUnit}")
            Spacer(GlanceModifier.defaultWeight())
            Stat("Влажность", "${data.humidity}%")
            Spacer(GlanceModifier.defaultWeight())
            Stat("Ветер", "${data.windSpeed.roundToInt()} ${data.windUnit}")
        }
    }

    @Composable
    private fun Stat(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = label,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
        }
    }

    @Composable
    private fun Forecast(data: WeatherData) {
        val today = LocalDate.now()
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            data.daily.take(5).forEachIndexed { i, d ->
                if (i > 0) Spacer(GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (d.date == today) "Сегодня" else dowShort(d.date),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    Text(text = WeatherCodes.emoji(d.code), style = TextStyle(fontSize = 15.sp))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = "${d.max.roundToInt()}°",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "${d.min.roundToInt()}°",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    )
                }
            }
        }
    }

    @Composable
    private fun NotConfigured() {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🌤️ Погода",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "Нажмите, чтобы выбрать город",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center),
            )
        }
    }

    private fun weatherActivityIntent(): Intent =
        Intent().setClassName(
            "com.monthcalendar.widget", "com.monthcalendar.widget.WeatherActivity",
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
}

private val DOW_RU = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private fun dowShort(date: LocalDate): String = DOW_RU[date.dayOfWeek.value - 1]
