package com.cozyhome.weather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.cozyhome.weather.MainActivity
import com.cozyhome.weather.data.WeatherKind
import com.cozyhome.weather.data.WeatherRepository
import com.cozyhome.weather.data.WeatherSnapshot
import com.cozyhome.weather.data.emoji
import com.cozyhome.weather.data.weatherDescription
import com.cozyhome.weather.util.formatHour
import com.cozyhome.weather.util.formatTemp
import com.cozyhome.weather.util.formatUpdatedAt

/**
 * Home-screen widget. GlanceTheme maps to the device's dynamic (Material You)
 * palette, so widget colors follow wallpaper changes automatically.
 */
class WeatherWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(140.dp, 60.dp)
        private val MEDIUM = DpSize(220.dp, 110.dp)
        private val TALL = DpSize(250.dp, 170.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WeatherRepository(context).cachedSnapshot()
        provideContent {
            GlanceTheme {
                WidgetContent(snapshot)
            }
        }
    }
}

@Composable
private fun WidgetContent(snapshot: WeatherSnapshot?) {
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (snapshot == null) {
            Text(
                text = "Открой приложение 🌤️",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
            return@Column
        }

        val current = snapshot.forecast.current
        val kind = WeatherKind.fromCode(current.weatherCode)
        val isDay = current.isDay == 1
        val daily = snapshot.forecast.daily

        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = formatTemp(current.temperature),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = if (size.height >= 100.dp) 40.sp else 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = snapshot.place.name,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = kind.emoji(isDay),
                    style = TextStyle(fontSize = if (size.height >= 100.dp) 32.sp else 24.sp),
                )
                if (size.width >= 200.dp) {
                    val max = daily.temperatureMax.firstOrNull()
                    val min = daily.temperatureMin.firstOrNull()
                    if (max != null && min != null) {
                        Text(
                            text = formatTemp(max) + " / " + formatTemp(min),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        )
                    }
                }
            }
        }

        if (size.height >= 100.dp) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = weatherDescription(current.weatherCode),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        }

        if (size.height >= 150.dp) {
            Spacer(GlanceModifier.height(10.dp))
            HourlyStrip(snapshot)
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "обновлено " + formatUpdatedAt(snapshot.fetchedAtEpochMs),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun HourlyStrip(snapshot: WeatherSnapshot) {
    val hourly = snapshot.forecast.hourly
    val startIdx = hourly.time
        .indexOfFirst { it >= snapshot.forecast.current.time }
        .coerceAtLeast(0)
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        for (j in 0 until 4) {
            val i = startIdx + 2 + j * 3
            if (i >= hourly.time.size) break
            val hr = hourly.time[i].substringAfter('T').take(2).toIntOrNull() ?: 12
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatHour(hourly.time[i]),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
                Text(
                    text = WeatherKind.fromCode(hourly.weatherCode.getOrElse(i) { 0 }).emoji(hr in 6..20),
                    style = TextStyle(fontSize = 16.sp),
                )
                Text(
                    text = formatTemp(hourly.temperature.getOrElse(i) { 0.0 }),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            if (j < 3) Spacer(GlanceModifier.defaultWeight())
        }
    }
}
