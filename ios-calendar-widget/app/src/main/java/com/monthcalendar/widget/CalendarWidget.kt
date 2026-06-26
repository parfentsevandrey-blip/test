package com.monthcalendar.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.LocalDate

/**
 * An iOS-style month-view calendar widget.
 *
 * Resizing is handled by [SizeMode.Responsive]: the launcher renders the
 * variant that best fits the user's chosen cell span, and Glance swaps between
 * three [Layout] presets (compact / medium / large), scaling typography and the
 * "today" circle. The look mirrors the iOS Calendar widget: a white (dark in
 * dark mode) rounded card, a red month title, gray Monday-first weekday
 * initials, faded spillover days and a red disc on today.
 */
class CalendarWidget : GlanceAppWidget() {

    private val small = DpSize(150.dp, 150.dp)
    private val medium = DpSize(250.dp, 190.dp)
    private val large = DpSize(300.dp, 300.dp)

    override val sizeMode = SizeMode.Responsive(setOf(small, medium, large))

    private data class Layout(
        val showTitle: Boolean,
        val titleSize: Int,
        val headerSize: Int,
        val daySize: Int,
        val circle: Int,
        val pad: Int,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read "today" inside provideGlance so a re-render always reflects the
        // current date (the receiver re-renders on DATE_CHANGED + daily worker).
        val month = CalendarModel.monthFor(LocalDate.now())
        provideContent {
            Content(month)
        }
    }

    @Composable
    private fun Content(month: MonthData) {
        val size = LocalSize.current
        val layout = when {
            size.height < 175.dp -> Layout(showTitle = size.width >= 200.dp, titleSize = 13, headerSize = 9, daySize = 11, circle = 18, pad = 12)
            size.height < 250.dp -> Layout(showTitle = true, titleSize = 15, headerSize = 10, daySize = 13, circle = 22, pad = 14)
            else -> Layout(showTitle = true, titleSize = 17, headerSize = 11, daySize = 15, circle = 28, pad = 16)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(R.color.cal_bg))
                .cornerRadius(24.dp)
                .padding(layout.pad.dp)
                .clickable(actionStartActivity(calendarIntent())),
        ) {
            if (layout.showTitle) {
                Text(
                    text = month.title,
                    style = TextStyle(
                        color = ColorProvider(R.color.ios_red),
                        fontSize = layout.titleSize.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(6.dp))
            }

            // Weekday header row
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                month.weekdayHeaders.forEachIndexed { i, label ->
                    Text(
                        text = label,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(
                            color = ColorProvider(R.color.cal_text_secondary),
                            fontSize = layout.headerSize.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
            Spacer(GlanceModifier.height(4.dp))

            // Six week rows, each filling the remaining height equally.
            month.weeks.forEach { week ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    week.forEach { cell ->
                        DayCellView(cell, layout)
                    }
                }
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.DayCellView(cell: DayCell, layout: Layout) {
        val color = when {
            cell.isToday -> ColorProvider(Color.White)
            !cell.inCurrentMonth -> ColorProvider(R.color.cal_text_faded)
            cell.isWeekend -> ColorProvider(R.color.cal_text_secondary)
            else -> ColorProvider(R.color.cal_text)
        }
        Box(
            modifier = GlanceModifier.defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            val circleMod = if (cell.isToday) {
                GlanceModifier
                    .size(layout.circle.dp)
                    .background(ImageProvider(R.drawable.today_circle))
            } else {
                GlanceModifier.size(layout.circle.dp)
            }
            Box(modifier = circleMod, contentAlignment = Alignment.Center) {
                Text(
                    text = cell.day.toString(),
                    style = TextStyle(
                        color = color,
                        fontSize = layout.daySize.sp,
                        fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }

    /** Open the device's default Calendar app when the widget is tapped. */
    private fun calendarIntent(): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_CALENDAR)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
}
