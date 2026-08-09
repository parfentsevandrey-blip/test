package app.quire.calendar.world

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.quire.calendar.R
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.EventRepository
import app.quire.calendar.core.MonthLoader
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import app.quire.calendar.widget.MonthWidgetProvider
import app.quire.engine.anim.MotionProfile
import app.quire.engine.design.Metrics
import app.quire.engine.design.Theme
import app.quire.engine.input.Tilt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * The whole app: one Activity holding one world and the two sheets that come over it. There is no
 * second Activity and therefore no window transition anywhere, and no setting here recreates it —
 * a palette change is a new [Theme] handed to a view that is already on screen.
 */
class WorldActivity : AppCompatActivity(), WorldView.Data {

    private lateinit var prefs: Prefs
    private lateinit var world: WorldView
    private lateinit var overlay: OverlayView
    private lateinit var loader: MonthLoader
    private lateinit var tilt: Tilt

    private val agendaCache = HashMap<LocalDate, List<AgendaEntry>>()
    private val agendaPending = HashSet<LocalDate>()

    private var theme: Theme = Theme(Prefs.DEFAULT_SEED, dark = false)

    /** Set once the card has been dismissed or acted on, so it is not re-raised this session. */
    private var noticeWaved = false

    /** Set once the system dialog has come back refused; the card then points at system settings. */
    private var permissionRefused = false
    private var searchJob: Runnable? = null
    private var pendingQuery = ""

    private val requestCalendar =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loader.invalidate()
                agendaCache.clear()
                overlay.setCalendars(EventRepository.calendars(this))
                world.dataChanged()
            } else {
                // Refused: the grid still works, so the app keeps running and says once what it
                // is missing and where to change it, rather than asking again on every resume.
                permissionRefused = true
                presentPermissionNotice(denied = true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs.get(this)
        super.onCreate(savedInstanceState)
        loader = MonthLoader(this)
        tilt = Tilt(this)
        if (!prefs.hasMotionPreference) {
            prefs.motion = MotionProfile.STANDARD.key
        }

        world = WorldView(this)
        overlay = OverlayView(this)

        val root = FrameLayout(this)
        val fill = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        root.addView(world, FrameLayout.LayoutParams(fill))
        root.addView(overlay, FrameLayout.LayoutParams(fill))
        setContentView(root)

        world.data = this
        world.onSelectionChanged = { agendaFor(it) }
        world.onEntryActivated = { openEvent(it) }
        world.onComposeRequested = { composeEvent(it) }
        world.onHudAction = { handleAction(it) }
        world.onLevelChanged = { overlay.dismiss() }

        overlay.onSettingsChanged = { store(it) }
        overlay.onQueryChanged = { runSearch(it) }
        overlay.onResultChosen = { entry ->
            overlay.dismiss()
            world.goTo(EventRepository.dateOf(entry), level = 2, animate = true)
        }
        overlay.setVersion(
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
                .getOrNull()
                .orEmpty(),
        )

        tilt.onChanged = { x, y -> world.setTilt(x, y) }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            world.setSafeInsets(bars.top.toFloat(), bars.bottom.toFloat())
            overlay.setSafeTop(bars.top.toFloat())
            insets
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        overlay.dismiss() -> Unit
                        world.zoomOut() -> Unit
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
            world.goTo(LocalDate.ofEpochDay(it), level = 1, animate = false)
        }
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_SELECTED, world.selected.toEpochDay())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        world.today = LocalDate.now()
        loader.invalidate()
        agendaCache.clear()
        agendaPending.clear()
        world.dataChanged()
        agendaFor(world.selected)
        overlay.setCalendars(EventRepository.calendars(this))
        if (world.depth) tilt.start() else tilt.stop()
        if (EventRepository.hasPermission(this)) {
            overlay.hideNotice()
        } else if (!noticeWaved) {
            // Asked with a card first rather than straight to the system dialog: the one chance
            // an app gets at that dialog is worth spending after the reason has been given.
            //
            // The flag it checks is per-instance, not per-process. The card is the only way to
            // grant access from inside the app, so "the user waved it away once" must not mean
            // "there is now no way back" — reopening the app brings it round again.
            presentPermissionNotice(denied = permissionRefused)
        }
    }

    /** The one thing the app ever asks for, explained, with the button that grants it. */
    private fun presentPermissionNotice(denied: Boolean) {
        overlay.presentNotice(
            getString(R.string.permission_headline),
            getString(if (denied) R.string.permission_denied_body else R.string.permission_body),
            getString(if (denied) R.string.open_settings else R.string.permission_action),
        )
        overlay.onNoticeAction = {
            noticeWaved = true
            if (permissionRefused) {
                openSystemSettings()
            } else {
                requestCalendar.launch(Manifest.permission.READ_CALENDAR)
            }
        }
        overlay.onNoticeDismissed = { noticeWaved = true }
    }

    override fun onPause() {
        // The sensor is only worth its wake-ups while the world is on screen.
        tilt.stop()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySettings()
    }

    // ---- settings ------------------------------------------------------

    /** True when the palette should be the dark one, honouring the user's override first. */
    private fun wantsDark(): Boolean = when (prefs.skin) {
        Skin.PAPER -> false
        // COLOUR belongs to the widget, which is a dark card; the app never sets it.
        Skin.INK, Skin.COLOUR -> true
        Skin.AUTO ->
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Rebuilds the theme and hands it, and every flag, to both surfaces. Nothing is recreated:
     * this runs under the user's finger while the settings sheet is open.
     */
    private fun applySettings() {
        val dark = wantsDark()
        theme = Theme(prefs.seed, dark, prefs.contrast)
        val metrics = Metrics(resources.displayMetrics.density, prefs.scale)
        // The app's own setting is authoritative. Reading the system animator scale every time
        // would leave anyone who turned animations down for speed with an app that cannot move
        // and no way inside it to say otherwise.
        val motion = MotionProfile.from(prefs.motion)

        window.decorView.setBackgroundColor(theme.canvas)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = theme.canvas
            window.navigationBarColor = theme.canvas
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }

        world.theme = theme
        world.metrics = metrics
        world.motion = motion
        world.haptics = prefs.haptics
        world.depth = prefs.depth && !motion.instant
        world.density = prefs.heat
        world.colouredMarks = prefs.colouredDots
        world.firstDayOfWeek = MonthModel.firstDayOfWeek(prefs.firstDay, Locale.getDefault())

        overlay.configure(theme, metrics, motion)

        if (world.depth) tilt.start() else tilt.stop()
        loader.invalidate()
        agendaCache.clear()
        world.dataChanged()
        MonthWidgetProvider.requestUpdate(this)
    }

    /** The settings sheet hands back a whole state; this is the only place it is written down. */
    private fun store(state: SettingsPanel.State) {
        prefs.seed = state.seed
        prefs.skin = when (state.dark) {
            null -> Skin.AUTO
            false -> Skin.PAPER
            true -> Skin.INK
        }
        prefs.contrast = state.contrast
        prefs.scale = state.scale
        prefs.firstDay = when (state.firstDay) {
            DayOfWeek.MONDAY -> "mon"
            DayOfWeek.SATURDAY -> "sat"
            DayOfWeek.SUNDAY -> "sun"
            else -> "auto"
        }
        prefs.motion = state.motion.key
        prefs.haptics = state.haptics
        prefs.depth = state.depth
        prefs.heat = state.density
        prefs.colouredDots = state.colouredMarks
        prefs.showAdjacent = state.adjacent
        prefs.hiddenCalendars = state.hidden
        applySettings()
    }

    /** The values the sheet opens on, read back out of the same store. */
    private fun currentState(): SettingsPanel.State = SettingsPanel.State(
        seed = prefs.seed,
        dark = when (prefs.skin) {
            Skin.AUTO -> null
            Skin.PAPER -> false
            Skin.INK, Skin.COLOUR -> true
        },
        contrast = prefs.contrast,
        scale = prefs.scale,
        firstDay = when (prefs.firstDay) {
            "mon" -> DayOfWeek.MONDAY
            "sat" -> DayOfWeek.SATURDAY
            "sun" -> DayOfWeek.SUNDAY
            else -> null
        },
        motion = MotionProfile.from(prefs.motion),
        haptics = prefs.haptics,
        depth = prefs.depth,
        density = prefs.heat,
        colouredMarks = prefs.colouredDots,
        adjacent = prefs.showAdjacent,
        hidden = prefs.hiddenCalendars,
    )

    // ---- data ----------------------------------------------------------

    override fun loads(month: YearMonth): Map<LocalDate, DayLoad> {
        val cached = loader.cached(month, world.firstDayOfWeek)
        if (cached != null) return cached
        loader.request(month, world.firstDayOfWeek, prefs.hiddenCalendars) { _, _ ->
            world.dataChanged()
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
            world.dataChanged()
        }
    }

    // ---- actions -------------------------------------------------------

    private fun handleAction(id: Int) {
        when (id) {
            ACTION_TODAY -> {
                overlay.dismiss()
                world.goTo(LocalDate.now(), level = 1, animate = true)
            }
            ACTION_YEAR -> {
                overlay.dismiss()
                world.goToLevel(if (world.level == 0) 1 else 0)
            }
            ACTION_ADD -> composeEvent(world.selected)
            ACTION_SEARCH -> overlay.presentSearch()
            ACTION_SETTINGS -> overlay.presentSettings(currentState())
        }
    }

    /** Debounced so that typing does not put one provider query per keystroke on the executor. */
    private fun runSearch(text: String) {
        pendingQuery = text
        searchJob?.let { overlay.removeCallbacks(it) }
        if (text.trim().length < 2) {
            overlay.setResults(text, emptyList())
            return
        }
        val job = Runnable {
            loader.search(text, world.selected, prefs.hiddenCalendars) { entries ->
                if (pendingQuery == text) overlay.setResults(text, entries)
            }
        }
        searchJob = job
        overlay.postDelayed(job, SEARCH_DEBOUNCE_MILLIS)
    }

    // ---- intents -------------------------------------------------------

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "quire") return
        val date = runCatching { LocalDate.parse(data.lastPathSegment) }.getOrNull() ?: return
        world.goTo(date, level = if (data.host == "day") 2 else 1, animate = false)
    }

    private fun composeEvent(date: LocalDate) {
        val start = if (date == LocalDate.now()) {
            System.currentTimeMillis()
        } else {
            date.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + HOUR_MILLIS)
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

    /** Opens this app's own row in system settings, for a permission that was refused for good. */
    private fun openSystemSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                ),
            )
        }
    }

    private companion object {
        const val STATE_SELECTED = "selected"

        const val SEARCH_DEBOUNCE_MILLIS = 220L
        const val HOUR_MILLIS = 3_600_000L

        const val ACTION_TODAY = 1
        const val ACTION_YEAR = 2
        const val ACTION_ADD = 3
        const val ACTION_SEARCH = 4
        const val ACTION_SETTINGS = 5
    }
}
