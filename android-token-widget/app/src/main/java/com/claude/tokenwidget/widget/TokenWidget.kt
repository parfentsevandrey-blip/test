package com.claude.tokenwidget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.components.CircleIconButton
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
import androidx.glance.text.TextStyle
import com.claude.tokenwidget.MainActivity
import com.claude.tokenwidget.data.UsageData
import com.claude.tokenwidget.data.UsageRepository

/**
 * The Claude token-usage home-screen widget.
 *
 * Resizing is handled by [SizeMode.Responsive]: the launcher renders the
 * largest layout that fits the user's chosen cell span, and Glance swaps
 * between [Compact], [Medium] and [Large] automatically — no per-size code in
 * the manifest. The visual language is Material 3 Expressive: a high-radius
 * surface, the Claude clay accent, and chunky rounded progress tracks.
 */
class TokenWidget : GlanceAppWidget() {

    // Breakpoints map to the targetCell sizes in token_widget_info.xml.
    private val compact = DpSize(120.dp, 48.dp)
    private val medium = DpSize(180.dp, 110.dp)
    private val large = DpSize(250.dp, 180.dp)

    override val sizeMode = SizeMode.Responsive(setOf(compact, medium, large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val usage = UsageRepository(context).current()
        provideContent {
            GlanceTheme {
                WidgetContent(usage)
            }
        }
    }

    @Composable
    private fun WidgetContent(usage: UsageData) {
        val size = LocalSize.current
        val context = LocalContext.current
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(if (size.height < 80.dp) 12.dp else 16.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            when {
                size.height < 80.dp -> Compact(usage)
                size.height < 150.dp -> Medium(usage)
                else -> Large(usage)
            }
        }
    }

    // --- Compact: two slim bars stacked, percentages only -------------------
    @Composable
    private fun Compact(usage: UsageData) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            MeterRow(label = "S", fraction = usage.sessionFraction, percent = usage.sessionPercent, accent = true, dense = true)
            Spacer(GlanceModifier.height(8.dp))
            MeterRow(label = "W", fraction = usage.weeklyFraction, percent = usage.weeklyPercent, accent = false, dense = true)
        }
    }

    // --- Medium: header + two labelled meters --------------------------------
    @Composable
    private fun Medium(usage: UsageData) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(showRefresh = false)
            Spacer(GlanceModifier.height(10.dp))
            Meter(title = "Сессия", fraction = usage.sessionFraction, percent = usage.sessionPercent, accent = true)
            Spacer(GlanceModifier.height(8.dp))
            Meter(title = "Неделя", fraction = usage.weeklyFraction, percent = usage.weeklyPercent, accent = false)
        }
    }

    // --- Large: header + meters with token counts + reset hints --------------
    @Composable
    private fun Large(usage: UsageData) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(showRefresh = true)
            Spacer(GlanceModifier.height(12.dp))
            Meter(
                title = "Сессия",
                fraction = usage.sessionFraction,
                percent = usage.sessionPercent,
                accent = true,
                detail = "${formatTokens(usage.sessionTokensUsed)} / ${formatTokens(usage.sessionTokenLimit)}",
                reset = relativeReset(usage.sessionResetAt, usage.updatedAt),
            )
            Spacer(GlanceModifier.height(12.dp))
            Meter(
                title = "Неделя",
                fraction = usage.weeklyFraction,
                percent = usage.weeklyPercent,
                accent = false,
                detail = "${formatTokens(usage.weeklyTokensUsed)} / ${formatTokens(usage.weeklyTokenLimit)}",
                reset = relativeReset(usage.weeklyResetAt, usage.updatedAt),
            )
        }
    }

    // --- Building blocks -----------------------------------------------------
    @Composable
    private fun Header(showRefresh: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Claude · tokens",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (showRefresh) {
                Spacer(GlanceModifier.defaultWeight())
                CircleIconButton(
                    imageProvider = ImageProvider(android.R.drawable.ic_popup_sync),
                    contentDescription = "Обновить",
                    backgroundColor = null,
                    onClick = actionRunCallback<RefreshAction>(),
                )
            }
        }
    }

    @Composable
    private fun Meter(
        title: String,
        fraction: Float,
        percent: Int,
        accent: Boolean,
        detail: String? = null,
        reset: String? = null,
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = "$percent%",
                    style = TextStyle(
                        color = if (accent) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(GlanceModifier.height(5.dp))
            ProgressTrack(fraction = fraction, accent = accent)
            if (detail != null) {
                Spacer(GlanceModifier.height(4.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = detail,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                    if (reset != null) {
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            text = reset,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MeterRow(label: String, fraction: Float, percent: Int, accent: Boolean, dense: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = label,
                style = TextStyle(
                    color = if (accent) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
            Box(modifier = GlanceModifier.defaultWeight()) {
                ProgressTrack(fraction = fraction, accent = accent)
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = "$percent%",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    /**
     * A thick, rounded, Material 3 Expressive progress track. Uses Glance's
     * native [LinearProgressIndicator] (which honours a fractional progress)
     * and rounds the corners into a pill.
     */
    @Composable
    private fun ProgressTrack(fraction: Float, accent: Boolean) {
        val fillColor = if (accent) GlanceTheme.colors.primary else GlanceTheme.colors.secondary
        LinearProgressIndicator(
            progress = fraction.coerceIn(0f, 1f),
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(10.dp)
                .cornerRadius(8.dp),
            color = fillColor,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
    }
}

private fun formatTokens(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.0fK", value / 1_000.0)
    else -> value.toString()
}

private fun relativeReset(resetAt: Long, now: Long): String {
    if (resetAt <= 0 || now <= 0) return ""
    val ms = resetAt - now
    if (ms <= 0) return "сброс скоро"
    val hours = ms / (60 * 60 * 1000)
    val days = hours / 24
    return when {
        days >= 1 -> "↻ ${days}д"
        hours >= 1 -> "↻ ${hours}ч"
        else -> "↻ <1ч"
    }
}
