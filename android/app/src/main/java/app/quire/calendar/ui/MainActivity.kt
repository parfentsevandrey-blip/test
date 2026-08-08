package app.quire.calendar.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.quire.calendar.QuireApp
import app.quire.calendar.R
import app.quire.calendar.core.Accent
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.EventRepository
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Skin
import app.quire.calendar.core.Tokens
import app.quire.calendar.widget.MonthWidgetProvider
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One activity, one world. Year, month, day, menus and settings all live on the
 * same screen — there is no second Activity to navigate to and therefore no
 * window transition anywhere in the app.
 */
class MainActivity : BaseActivity(), StageView.Data {

    private lateinit var stage: StageView
    private lateinit var menu: WheelMenu
    private lateinit var sheet: SheetOverlay
    private lateinit var loader: MonthLoader

    private val agendaCache = HashMap<LocalDate, List<AgendaEntry>>()
    private val agendaPending = HashSet<LocalDate>()

    private var searchJob: Runnable? = null

    /** Settings are applied live, so nothing here should recreate the activity. */
    override fun settingsSignature(): String = "live"

    private val requestCalendar =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loader.invalidate()
                agendaCache.clear()
                stage.dataChanged()
            } else {
                presentPermission(denied = true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loader = MonthLoader(this)

        val root = FrameLayout(this)
        stage = StageView(this)
        menu = WheelMenu(this)
        sheet = SheetOverlay(this)

        root.addView(
            stage,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            menu,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            sheet,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(root)

        stage.data = this
        stage.onSelectionChanged = { agendaFor(it) }
        stage.onEntryActivated = { openEvent(it) }
        stage.onMenuRequested = { x, y -> openMenu(x, y) }
        stage.onMenuDrag = { x, y -> menu.trackDrag(x, y) }
        stage.onMenuRelease = { x, y -> menu.trackRelease(x, y) }
        stage.onLevelChanged = { if (sheet.isShowing) sheet.dismiss() }

        menu.onPick = { handleMenu(it) }
        menu.onClosed = { stage.setReceded(sheet.isShowing) }
        sheet.onDismissed = { stage.setReceded(menu.isOpen) }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            stage.setSafeInsets(bars.top.toFloat(), bars.bottom.toFloat())
            menu.safeTop = bars.top.toFloat()
            menu.safeBottom = bars.bottom.toFloat()
            sheet.applyInsets(bars.top, bars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        sheet.isShowing -> sheet.dismiss()
                        menu.isOpen -> menu.close()
                        stage.zoomOut() -> Unit
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            },
        )

        applySettings()
        savedInstanceState?.getLong(STATE_SELECTED, -1L)?.takeIf { it >= 0 }?.let {
            stage.goTo(LocalDate.ofEpochDay(it), level = 1, animate = false)
        }
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_SELECTED, stage.selected.toEpochDay())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        stage.today = LocalDate.now()
        loader.invalidate()
        agendaCache.clear()
        agendaPending.clear()
        stage.dataChanged()
        agendaFor(stage.selected)
        if (!EventRepository.hasPermission(this) && !askedForPermission) {
            askedForPermission = true
            presentPermission(denied = false)
        }
    }

    // ---- settings ------------------------------------------------------

    private fun applySettings() {
        palette = Tokens.palette(Tokens.isSystemDark(this), prefs.accent)
        val motion = if (MotionProfile.systemHoldsStill(contentResolver)) {
            MotionProfile.OFF
        } else {
            MotionProfile.from(prefs.motion)
        }
        window.decorView.setBackgroundColor(palette.canvas)

        stage.palette = palette
        stage.motion = motion
        stage.haptics = prefs.haptics
        stage.firstDayOfWeek = MonthModel.firstDayOfWeek(prefs.firstDay, Locale.getDefault())
        stage.style = GridStyle(
            showAdjacent = prefs.showAdjacent,
            dimWeekends = prefs.dimWeekends,
            colouredDots = prefs.colouredDots,
            weekNumbers = prefs.weekNumbers,
            heat = prefs.heat,
        )
        menu.palette = palette
        menu.motion = motion
        menu.haptics = prefs.haptics
        sheet.palette = palette
        sheet.motion = motion
        loader.invalidate()
        agendaCache.clear()
        stage.dataChanged()
        MonthWidgetProvider.requestUpdate(this)
    }

    // ---- data ----------------------------------------------------------

    override fun loads(month: YearMonth): Map<LocalDate, DayLoad> {
        val cached = loader.cached(month, stage.firstDayOfWeek)
        if (cached != null) return cached
        loader.request(month, stage.firstDayOfWeek, prefs.hiddenCalendars) { _, _ ->
            stage.dataChanged()
        }
        return emptyMap()
    }

    override fun agenda(date: LocalDate): List<AgendaEntry> {
        agendaCache[date]?.let { return it }
        agendaFor(date)
        return emptyList()
    }

    private fun agendaFor(date: LocalDate) {
        if (agendaCache.containsKey(date) || !agendaPending.add(date)) return
        loader.agenda(date, prefs.hiddenCalendars) { day, entries ->
            agendaPending.remove(day)
            agendaCache[day] = entries
            stage.invalidate()
        }
    }

    // ---- menu ----------------------------------------------------------

    private fun openMenu(x: Float, y: Float) {
        stage.setReceded(true)
        menu.open(
            x,
            y,
            listOf(
                WheelMenu.Item(MENU_TODAY, getString(R.string.today), R.drawable.ic_ring),
                WheelMenu.Item(MENU_YEAR, getString(R.string.year), R.drawable.ic_grid),
                WheelMenu.Item(MENU_SEARCH, getString(R.string.search), R.drawable.ic_search),
                WheelMenu.Item(MENU_ADD, getString(R.string.add), R.drawable.ic_plus),
                WheelMenu.Item(MENU_SETTINGS, getString(R.string.settings), R.drawable.ic_settings),
            ),
            centreLabel = stage.selected.dayOfMonth.toString(),
        )
    }

    private fun handleMenu(id: Int) {
        when (id) {
            MENU_TODAY -> stage.goTo(LocalDate.now(), level = 1)
            MENU_YEAR -> stage.goToLevel(0)
            MENU_SEARCH -> presentSearch()
            MENU_ADD -> composeEvent()
            MENU_SETTINGS -> presentSettings()
        }
    }

    // ---- sheets --------------------------------------------------------

    private fun presentSettings() {
        val builder = sheet.begin()
        builder.title(getString(R.string.settings))

        builder.slab { panel ->
            panel.section(R.string.section_motion)
            val profiles = listOf(
                MotionProfile.OFF,
                MotionProfile.CALM,
                MotionProfile.STANDARD,
                MotionProfile.PLAYFUL,
            )
            panel.segmented(
                titleRes = R.string.motion,
                options = listOf(
                    getString(R.string.motion_off),
                    getString(R.string.motion_calm),
                    getString(R.string.motion_standard),
                    getString(R.string.motion_playful),
                ),
                selectedIndex = profiles.indexOf(MotionProfile.from(prefs.motion))
                    .coerceAtLeast(0),
            ) { index ->
                prefs.motion = profiles[index].key
                applySettings()
            }
            panel.rule()
            panel.toggle(R.string.haptics, R.string.haptics_hint, prefs.haptics) {
                prefs.haptics = it
                applySettings()
            }
        }

        builder.slab { panel ->
            panel.section(R.string.section_appearance)
            val skins = listOf(Skin.AUTO, Skin.PAPER, Skin.INK)
            panel.segmented(
                titleRes = R.string.skin,
                options = listOf(
                    getString(R.string.skin_auto),
                    getString(R.string.skin_paper),
                    getString(R.string.skin_ink),
                ),
                selectedIndex = skins.indexOf(prefs.skin).coerceAtLeast(0),
            ) { index ->
                prefs.skin = skins[index]
                AppCompatDelegate.setDefaultNightMode(QuireApp.nightMode(skins[index]))
                applySettings()
            }
            panel.accents(prefs.accent) { accent: Accent ->
                prefs.accent = accent
                applySettings()
            }
        }

        builder.slab { panel ->
            panel.section(R.string.section_week)
            val keys = listOf("auto", "mon", "sat", "sun")
            panel.segmented(
                titleRes = R.string.first_day,
                options = listOf(
                    getString(R.string.first_day_auto),
                    getString(R.string.first_day_mon),
                    getString(R.string.first_day_sat),
                    getString(R.string.first_day_sun),
                ),
                selectedIndex = keys.indexOf(prefs.firstDay).coerceAtLeast(0),
            ) { index ->
                prefs.firstDay = keys[index]
                applySettings()
            }
        }

        builder.slab { panel ->
            panel.section(R.string.section_grid)
            panel.toggle(R.string.heat, R.string.heat_hint, prefs.heat) {
                prefs.heat = it
                applySettings()
            }
            panel.rule()
            panel.toggle(R.string.show_adjacent, R.string.show_adjacent_hint, prefs.showAdjacent) {
                prefs.showAdjacent = it
                applySettings()
            }
            panel.rule()
            panel.toggle(R.string.dim_weekends, R.string.dim_weekends_hint, prefs.dimWeekends) {
                prefs.dimWeekends = it
                applySettings()
            }
            panel.rule()
            panel.toggle(R.string.week_numbers, R.string.week_numbers_hint, prefs.weekNumbers) {
                prefs.weekNumbers = it
                applySettings()
            }
            panel.rule()
            panel.toggle(R.string.coloured_dots, R.string.coloured_dots_hint, prefs.colouredDots) {
                prefs.colouredDots = it
                applySettings()
            }
        }

        val sources = EventRepository.calendars(this)
        if (sources.isNotEmpty()) {
            builder.slab { panel ->
                panel.section(R.string.section_calendars)
                panel.note(getString(R.string.calendars_hint))
                val hidden = prefs.hiddenCalendars.toMutableSet()
                sources.forEachIndexed { index, source ->
                    if (index > 0) panel.rule()
                    panel.check(
                        title = source.displayName,
                        subtitle = source.accountName.takeIf { it != source.displayName },
                        colour = source.colour,
                        checked = source.id !in hidden,
                    ) { checked ->
                        if (checked) hidden.remove(source.id) else hidden.add(source.id)
                        prefs.hiddenCalendars = hidden
                        applySettings()
                    }
                }
            }
        }

        builder.slab { panel ->
            panel.section(R.string.section_about)
            val version = runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull().orEmpty()
            panel.note(
                getString(R.string.about_line, version) + "\n" + getString(R.string.about_body),
            )
        }

        sheet.present()
        stage.setReceded(true)
    }

    private fun presentSearch() {
        val builder = sheet.begin()
        builder.title(getString(R.string.search))
        val results = builder.liveSlab()
        val field = builder.searchField(getString(R.string.search_hint)) { text ->
            searchJob?.let { sheet.removeCallbacks(it) }
            val job = Runnable { runSearch(text, results) }
            searchJob = job
            sheet.postDelayed(job, 220L)
        }
        results.note(getString(R.string.search_empty))
        sheet.present()
        stage.setReceded(true)
        field.post {
            field.requestFocus()
            (getSystemService(android.view.inputmethod.InputMethodManager::class.java))
                ?.showSoftInput(field, 0)
        }
    }

    private fun runSearch(text: String, results: Panel) {
        if (text.trim().length < 2) {
            results.clear()
            results.note(getString(R.string.search_empty))
            return
        }
        loader.search(text, stage.selected, prefs.hiddenCalendars) { entries ->
            results.clear()
            if (entries.isEmpty()) {
                results.note(getString(R.string.search_none))
                return@search
            }
            val locale = Locale.getDefault()
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
            val formatter = DateTimeFormatter.ofPattern(pattern, locale)
            entries.take(20).forEachIndexed { index, entry ->
                if (index > 0) results.rule()
                val date = EventRepository.dateOf(entry)
                results.action(entry.title.ifBlank { "—" }, formatter.format(date)) {
                    sheet.dismiss()
                    stage.goTo(date, level = 2)
                }
            }
        }
    }

    private fun presentPermission(denied: Boolean) {
        val builder = sheet.begin()
        builder.title(getString(R.string.permission_headline))
        builder.slab { panel ->
            panel.note(
                getString(
                    if (denied) R.string.permission_denied_body else R.string.permission_body,
                ),
            )
            panel.action(
                getString(if (denied) R.string.open_settings else R.string.permission_action),
                null,
                accent = true,
            ) {
                sheet.dismiss()
                if (denied) {
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
        sheet.present()
        stage.setReceded(true)
    }

    // ---- intents -------------------------------------------------------

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "quire") return
        val date = runCatching { LocalDate.parse(data.lastPathSegment) }.getOrNull() ?: return
        stage.goTo(date, level = if (data.host == "day") 2 else 1, animate = false)
    }

    private fun composeEvent() {
        val date = stage.selected
        val start = if (date == LocalDate.now()) {
            System.currentTimeMillis()
        } else {
            date.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
        var askedForPermission = false

        const val MENU_TODAY = 1
        const val MENU_YEAR = 2
        const val MENU_SEARCH = 3
        const val MENU_ADD = 4
        const val MENU_SETTINGS = 5
    }
}
