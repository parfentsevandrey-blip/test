package app.quire.calendar.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.quire.calendar.R
import app.quire.calendar.core.MonthModel
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Twelve months at a glance, one year per screenful. The same grid view runs in
 * compact mode — numbers only — so the year reads as texture and today reads as
 * the single mark in it.
 */
class YearActivity : BaseActivity() {

    private val first = 1970
    private val last = 2100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startYear = intent.getIntExtra(EXTRA_YEAR, YearMonth.now().year)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.canvas)
        }
        root.addView(Chrome.topBar(this, palette, getString(R.string.year)) { finish() })

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@YearActivity)
            adapter = YearAdapter()
            setHasFixedSize(true)
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        (list.layoutManager as LinearLayoutManager)
            .scrollToPositionWithOffset((startYear - first).coerceIn(0, last - first), 0)

        root.addView(list)
        setContentView(root)
        padForSystemBars(root)
    }

    private fun pick(month: YearMonth) {
        val today = LocalDate.now()
        val date = if (YearMonth.from(today) == month) today else month.atDay(1)
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_EPOCH_DAY, date.toEpochDay()),
        )
        finish()
    }

    private inner class YearAdapter : RecyclerView.Adapter<YearHolder>() {
        override fun getItemCount() = last - first + 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            YearHolder(YearBlock(parent.context))

        override fun onBindViewHolder(holder: YearHolder, position: Int) {
            holder.block.bind(first + position)
        }
    }

    private inner class YearHolder(val block: YearBlock) : RecyclerView.ViewHolder(block)

    /** A year label and a three-by-four field of compact months. */
    private inner class YearBlock(context: Context) : LinearLayout(context) {

        private val density = resources.displayMetrics.density
        private fun dp(v: Float) = (v * density).toInt()

        private val label = TextView(context).apply {
            setTextColor(palette.ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            letterSpacing = -0.02f
            setPadding(dp(20f), dp(22f), dp(20f), dp(12f))
        }

        private val cells = ArrayList<MonthCell>(12)

        init {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            addView(label)

            val available = resources.displayMetrics.widthPixels - dp(32f)
            val cellWidth = available / 3
            val gridHeight = (cellWidth / MonthModel.COLUMNS.toFloat() * MonthModel.ROWS * 1.06f)

            for (row in 0 until 4) {
                val line = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    setPadding(dp(16f), 0, dp(16f), dp(12f))
                }
                for (column in 0 until 3) {
                    val cell = MonthCell(context, gridHeight.toInt())
                    cells += cell
                    line.addView(
                        cell,
                        LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
                    )
                }
                addView(line)
            }
        }

        fun bind(year: Int) {
            label.text = year.toString()
            for (index in cells.indices) {
                cells[index].bind(YearMonth.of(year, index + 1))
            }
        }
    }

    private inner class MonthCell(context: Context, gridHeight: Int) : LinearLayout(context) {

        private val density = resources.displayMetrics.density
        private fun dp(v: Float) = (v * density).toInt()

        private val name = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.START
            setPadding(dp(4f), 0, 0, dp(4f))
        }

        private val grid = MonthGridView(context).apply {
            compact = true
            palette = this@YearActivity.palette
            showAdjacent = false
            dimWeekends = prefs.dimWeekends
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, gridHeight)
        }

        private var month: YearMonth = YearMonth.now()

        init {
            orientation = VERTICAL
            setPadding(dp(4f), 0, dp(4f), 0)
            addView(name)
            addView(grid)
            val out = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
            setOnClickListener { pick(month) }
        }

        fun bind(target: YearMonth) {
            month = target
            val locale = Locale.getDefault()
            val current = YearMonth.from(LocalDate.now())
            name.text = MonthModel.monthName(target, locale)
            name.setTextColor(if (target == current) palette.accent else palette.ink)
            grid.firstDayOfWeek = MonthModel.firstDayOfWeek(prefs.firstDay, locale)
            grid.today = LocalDate.now()
            grid.month = target
            grid.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val EXTRA_YEAR = "year"
        const val EXTRA_EPOCH_DAY = "epoch_day"

        fun intent(context: Context, month: YearMonth): Intent =
            Intent(context, YearActivity::class.java).putExtra(EXTRA_YEAR, month.year)
    }
}
