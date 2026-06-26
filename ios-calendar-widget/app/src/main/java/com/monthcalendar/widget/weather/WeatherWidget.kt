package com.monthcalendar.widget.weather

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.unit.ColorProvider
import com.monthcalendar.widget.WidgetGradient
import java.time.LocalDate
import kotlin.math.roundToInt

private val White = ColorProvider(Color.White)
private val White80 = ColorProvider(Color(0xCCFFFFFF))
private val White65 = ColorProvider(Color(0xA6FFFFFF))
private val Chip = ColorProvider(Color(0x2BFFFFFF))
private val ChipStrong = ColorProvider(Color(0x3DFFFFFF))

/**
 * Premium Material-Expressive weather widget. A weather-and-time-of-day mood
 * gradient hero, hand-drawn vector icons, an hourly strip, a multi-day forecast
 * with precipitation, plus sunrise/sunset and key stats. Data: Open-Meteo.
 */
class WeatherWidget : GlanceAppWidget() {

    private val small = DpSize(150.dp, 110.dp)
    private val medium = DpSize(220.dp, 200.dp)
    private val large = DpSize(260.dp, 300.dp)

    override val sizeMode = SizeMode.Responsive(setOf(small, medium, large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WeatherRepository.cached(context)
            ?: WeatherRepository.refresh(context, System.currentTimeMillis())
        val g = if (data != null) WeatherCodes.gradient(data.code, data.isDay)
        else WeatherCodes.gradient(0, true)
        val gradient = WidgetGradient.vertical(g[0], g[1], g[2])

        provideContent { Content(data, gradient) }
    }

    @Composable
    private fun Content(data: WeatherData?, gradient: ImageProvider) {
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
                .cornerRadius(28.dp)
                .padding(if (tier == 0) 14.dp else 16.dp)
                .clickable(actionStartActivity(weatherActivityIntent())),
        ) {
            if (data == null) NotConfigured() else Body(data, tier)
        }
    }

    @Composable
    private fun Body(data: WeatherData, tier: Int) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header: location + updated
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = data.locationName.ifBlank { "Погода" },
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = White, fontSize = if (tier == 0) 13.sp else 15.sp, fontWeight = FontWeight.Medium),
                )
                if (tier >= 1) {
                    Text(text = updatedLabel(data.updatedAt), style = TextStyle(color = White65, fontSize = 11.sp))
                }
            }
            Spacer(GlanceModifier.height(if (tier == 0) 2.dp else 6.dp))

            // Hero
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(WeatherCodes.iconRes(data.code, data.isDay)),
                    contentDescription = WeatherCodes.label(data.code),
                    modifier = GlanceModifier.size(if (tier == 0) 44.dp else 58.dp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${data.temp.roundToInt()}${data.tempUnit}",
                        style = TextStyle(color = White, fontSize = if (tier == 0) 30.sp else 40.sp, fontWeight = FontWeight.Bold),
                    )
                    if (tier >= 1) {
                        Text(text = WeatherCodes.label(data.code), maxLines = 1, style = TextStyle(color = White80, fontSize = 12.sp))
                    }
                }
                data.today?.let { t ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text("↑ ${t.max.roundToInt()}°", style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        Spacer(GlanceModifier.height(2.dp))
                        Text("↓ ${t.min.roundToInt()}°", style = TextStyle(color = White65, fontSize = 13.sp))
                    }
                }
            }

            if (tier >= 1 && data.hourly.isNotEmpty()) {
                Spacer(GlanceModifier.height(10.dp))
                HourlyStrip(data)
            }

            if (tier >= 2) {
                if (data.daily.size > 1) {
                    Spacer(GlanceModifier.height(10.dp))
                    DailyList(data)
                }
                Spacer(GlanceModifier.height(10.dp))
                StatsFooter(data)
            }
        }
    }

    @Composable
    private fun HourlyStrip(data: WeatherData) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(18.dp)
                .background(Chip)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        ) {
            data.hourly.take(6).forEachIndexed { i, h ->
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (i == 0) "сейчас" else "%02d".format(h.time.hour),
                        style = TextStyle(color = White65, fontSize = 10.sp),
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Image(
                        provider = ImageProvider(WeatherCodes.iconRes(h.code, h.isDay)),
                        contentDescription = null,
                        modifier = GlanceModifier.size(22.dp),
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        text = "${h.temp.roundToInt()}°",
                        style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                    if (h.precipProb >= 20) {
                        Text(text = "${h.precipProb}%", style = TextStyle(color = ColorProvider(Color(0xCC8FD3FF)), fontSize = 9.sp))
                    }
                }
            }
        }
    }

    @Composable
    private fun DailyList(data: WeatherData) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            data.daily.drop(1).take(4).forEachIndexed { i, d ->
                if (i > 0) Spacer(GlanceModifier.height(5.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dowShort(d.date),
                        style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Image(
                        provider = ImageProvider(WeatherCodes.iconRes(d.code, true)),
                        contentDescription = null,
                        modifier = GlanceModifier.size(20.dp),
                    )
                    if (d.precipProb >= 20) {
                        Spacer(GlanceModifier.width(6.dp))
                        Text(text = "${d.precipProb}%", style = TextStyle(color = ColorProvider(Color(0xCC8FD3FF)), fontSize = 11.sp))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Text(text = "${d.min.roundToInt()}°", style = TextStyle(color = White65, fontSize = 13.sp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text(text = "${d.max.roundToInt()}°", style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    @Composable
    private fun StatsFooter(data: WeatherData) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            StatChip("Восход", data.today?.sunrise?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "—")
            Spacer(GlanceModifier.width(6.dp))
            StatChip("Закат", data.today?.sunset?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "—")
            Spacer(GlanceModifier.width(6.dp))
            StatChip("Ветер", "${data.windMax.roundToInt()}")
            Spacer(GlanceModifier.width(6.dp))
            StatChip("УФ", "${data.uvMax.roundToInt()}")
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.StatChip(label: String, value: String) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .cornerRadius(14.dp)
                .background(ChipStrong)
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value, style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Text(text = label, style = TextStyle(color = White65, fontSize = 9.sp))
        }
    }

    @Composable
    private fun NotConfigured() {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(provider = ImageProvider(WeatherCodes.iconRes(2, true)), contentDescription = null, modifier = GlanceModifier.size(44.dp))
            Spacer(GlanceModifier.height(6.dp))
            Text(text = "Погода", style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            Text(text = "Нажмите, чтобы выбрать город", style = TextStyle(color = White80, fontSize = 12.sp, textAlign = TextAlign.Center))
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
        mins < 60 -> "$mins мин назад"
        else -> "${mins / 60} ч назад"
    }
}
