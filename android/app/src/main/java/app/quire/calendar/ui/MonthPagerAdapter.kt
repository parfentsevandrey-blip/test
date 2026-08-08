package app.quire.calendar.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.quire.calendar.R
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Palette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Everything the grid needs that is not the month itself. */
class GridConfig(
    val palette: Palette,
    val firstDay: DayOfWeek,
    val showAdjacent: Boolean,
    val dimWeekends: Boolean,
    val weekNumbers: Boolean,
    val colouredDots: Boolean,
    val today: LocalDate,
    val selected: LocalDate?,
    val hidden: Set<Long>,
)

class MonthPagerAdapter(
    private val loader: MonthLoader,
    private val config: () -> GridConfig,
    private val onDayClick: (LocalDate) -> Unit,
) : RecyclerView.Adapter<MonthPagerAdapter.Holder>() {

    class Holder(val grid: MonthGridView) : RecyclerView.ViewHolder(grid)

    override fun getItemCount(): Int = LAST_POSITION + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val grid = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_month, parent, false) as MonthGridView
        return Holder(grid)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val c = config()
        val month = MonthModel.monthAt(position)
        holder.grid.apply {
            palette = c.palette
            firstDayOfWeek = c.firstDay
            showAdjacent = c.showAdjacent
            dimWeekends = c.dimWeekends
            weekNumbers = c.weekNumbers
            colouredDots = c.colouredDots
            today = c.today
            this.month = month
            selected = c.selected
            loads = loader.cached(month, c.firstDay) ?: emptyMap()
            onDayClick = this@MonthPagerAdapter.onDayClick
        }
        loader.request(month, c.firstDay, c.hidden) { loaded, marks ->
            if (holder.grid.month == loaded) holder.grid.loads = marks
        }
    }

    companion object {
        /** 1970-01 through 2100-12; the position is the epoch month index. */
        val LAST_POSITION = MonthModel.indexOf(YearMonth.of(2100, 12))

        fun positionOf(month: YearMonth): Int =
            MonthModel.indexOf(month).coerceIn(0, LAST_POSITION)
    }
}
