package app.quire.calendar.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import app.quire.calendar.R
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.EventRepository
import app.quire.calendar.core.MonthModel
import app.quire.calendar.databinding.ActivityMainBinding
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : BaseActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var loader: MonthLoader
    private lateinit var pagerAdapter: MonthPagerAdapter
    private lateinit var agendaAdapter: AgendaAdapter

    private var today: LocalDate = LocalDate.now()
    private var selected: LocalDate = LocalDate.now()
    private var visibleMonth: YearMonth = YearMonth.now()

    private val requestCalendar =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loader.invalidate()
                refreshEverything()
            } else {
                b.permissionBody.text = getString(R.string.permission_denied_body)
                b.permissionAction.text = getString(R.string.open_settings)
            }
        }

    private val pickYear =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val epochDay = result.data?.getLongExtra(YearActivity.EXTRA_EPOCH_DAY, -1L) ?: -1L
            if (epochDay >= 0) goTo(LocalDate.ofEpochDay(epochDay), smooth = false)
        }

    override fun settingsSignature(): String = listOf(
        prefs.accent.key,
        prefs.firstDay,
        prefs.showAdjacent,
        prefs.dimWeekends,
        prefs.weekNumbers,
        prefs.colouredDots,
        prefs.hiddenCalendars.sorted().joinToString(","),
    ).joinToString("|")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        padForSystemBars(b.root)

        loader = MonthLoader(this)
        savedInstanceState?.getLong(STATE_SELECTED, -1L)?.takeIf { it >= 0 }?.let {
            selected = LocalDate.ofEpochDay(it)
        }
        visibleMonth = YearMonth.from(selected)

        paint()
        buildPager()
        buildAgenda()
        wireHeader()

        handleIntent(intent)
        updateTitle()
        updateDayLabel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_SELECTED, selected.toEpochDay())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val now = LocalDate.now()
        if (now != today) {
            today = now
            forEachGrid { it.today = now }
        }
        loader.invalidate()
        refreshEverything()
    }

    // ---- construction --------------------------------------------------

    private fun paint() {
        b.root.setBackgroundColor(palette.canvas)
        b.monthTitle.setTextColor(palette.ink)
        b.yearTitle.setTextColor(palette.inkGhost)
        b.todayButton.setTextColor(palette.accent)
        b.settingsButton.setColorFilter(palette.inkMuted)
        b.addEventButton.setColorFilter(palette.inkMuted)
        b.selectedDayLabel.setTextColor(palette.inkFaint)
        b.emptyState.setTextColor(palette.inkGhost)
        b.gridRule.setBackgroundColor(palette.hairline)
        b.weekdays.palette = palette
        b.weekdays.dimWeekends = prefs.dimWeekends
        b.weekdays.weekNumbers = prefs.weekNumbers
        b.weekdays.firstDayOfWeek = MonthModel.firstDayOfWeek(prefs.firstDay, Locale.getDefault())
        b.permissionHeadline.setTextColor(palette.ink)
        b.permissionBody.setTextColor(palette.inkMuted)
        b.permissionAction.setTextColor(palette.accent)
    }

    private fun buildPager() {
        val metrics = resources.displayMetrics
        val rowHeight = (metrics.widthPixels / MonthModel.COLUMNS * 0.84f)
            .coerceIn(42f * metrics.density, 64f * metrics.density)
        b.pager.updateLayoutParams { height = (rowHeight * MonthModel.ROWS).toInt() }

        pagerAdapter = MonthPagerAdapter(
            loader = loader,
            config = ::gridConfig,
            onDayClick = { date -> select(date) },
        )
        b.pager.adapter = pagerAdapter
        b.pager.offscreenPageLimit = 1
        b.pager.setCurrentItem(MonthPagerAdapter.positionOf(visibleMonth), false)
        b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                visibleMonth = MonthModel.monthAt(position)
                updateTitle()
                if (YearMonth.from(selected) != visibleMonth) {
                    val landing = if (visibleMonth == YearMonth.from(today)) {
                        today
                    } else {
                        visibleMonth.atDay(1)
                    }
                    select(landing)
                }
            }
        })
    }

    private fun buildAgenda() {
        agendaAdapter = AgendaAdapter(this, palette) { entry -> openEvent(entry) }
        b.agenda.layoutManager = LinearLayoutManager(this)
        b.agenda.adapter = agendaAdapter
    }

    private fun wireHeader() {
        b.todayButton.setOnClickListener { goTo(LocalDate.now(), smooth = true) }
        b.titleBlock.setOnClickListener {
            pickYear.launch(YearActivity.intent(this, visibleMonth))
        }
        b.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.addEventButton.setOnClickListener { composeEvent() }
        b.permissionAction.setOnClickListener {
            if (b.permissionAction.text == getString(R.string.open_settings)) {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    ),
                )
            } else {
                requestCalendar.launch(Manifest.permission.READ_CALENDAR)
            }
        }
    }

    // ---- state ---------------------------------------------------------

    private fun gridConfig() = GridConfig(
        palette = palette,
        firstDay = MonthModel.firstDayOfWeek(prefs.firstDay, Locale.getDefault()),
        showAdjacent = prefs.showAdjacent,
        dimWeekends = prefs.dimWeekends,
        weekNumbers = prefs.weekNumbers,
        colouredDots = prefs.colouredDots,
        today = today,
        selected = selected,
        hidden = prefs.hiddenCalendars,
    )

    private fun forEachGrid(action: (MonthGridView) -> Unit) {
        val recycler = b.pager.getChildAt(0) as? RecyclerView ?: return
        for (i in 0 until recycler.childCount) {
            (recycler.getChildAt(i) as? MonthGridView)?.let(action)
        }
    }

    private fun select(date: LocalDate) {
        selected = date
        forEachGrid { it.selected = date }
        updateDayLabel()
        loadAgenda()
    }

    private fun goTo(date: LocalDate, smooth: Boolean) {
        val target = YearMonth.from(date)
        selected = date
        if (target != visibleMonth) {
            visibleMonth = target
            b.pager.setCurrentItem(MonthPagerAdapter.positionOf(target), smooth)
        }
        forEachGrid { it.selected = date }
        updateTitle()
        updateDayLabel()
        loadAgenda()
    }

    private fun refreshEverything() {
        val granted = EventRepository.hasPermission(this)
        b.permissionBar.visibility = if (granted) View.GONE else View.VISIBLE
        b.agendaHeader.visibility = if (granted) View.VISIBLE else View.GONE
        if (granted) {
            forEachGrid { grid ->
                loader.request(grid.month, grid.firstDayOfWeek, prefs.hiddenCalendars) { m, marks ->
                    if (grid.month == m) grid.loads = marks
                }
            }
            loadAgenda()
        } else {
            forEachGrid { it.loads = emptyMap() }
            agendaAdapter.submit(emptyList())
            b.emptyState.visibility = View.GONE
        }
    }

    private fun loadAgenda() {
        if (!EventRepository.hasPermission(this)) return
        val requested = selected
        loader.agenda(requested, prefs.hiddenCalendars) { date, entries ->
            if (date != selected) return@agenda
            agendaAdapter.submit(entries)
            b.emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateTitle() {
        val locale = Locale.getDefault()
        b.monthTitle.text = MonthModel.monthName(visibleMonth, locale)
        b.yearTitle.text = visibleMonth.year.toString()
        b.todayButton.visibility =
            if (visibleMonth == YearMonth.from(LocalDate.now())) View.INVISIBLE else View.VISIBLE
    }

    private fun updateDayLabel() {
        val locale = Locale.getDefault()
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMM")
        b.selectedDayLabel.text = DateTimeFormatter.ofPattern(pattern, locale)
            .format(selected)
            .uppercase(locale)
    }

    // ---- intents -------------------------------------------------------

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "quire") return
        val date = runCatching { LocalDate.parse(data.lastPathSegment) }.getOrNull() ?: return
        goTo(date, smooth = false)
    }

    private fun composeEvent() {
        val start = if (selected == LocalDate.now()) {
            System.currentTimeMillis()
        } else {
            selected.atTime(LocalTime.of(9, 0))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 3_600_000L)
        runCatching { startActivity(intent) }
            .onFailure { if (it !is ActivityNotFoundException) throw it }
    }

    private fun openEvent(entry: AgendaEntry) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, entry.eventId)
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(uri)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, entry.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, entry.end)
        runCatching { startActivity(intent) }
            .onFailure { if (it !is ActivityNotFoundException) throw it }
    }

    private companion object {
        const val STATE_SELECTED = "selected"
    }
}
