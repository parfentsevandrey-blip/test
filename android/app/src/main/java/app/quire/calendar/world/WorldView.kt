package app.quire.calendar.world

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.MonthModel
import app.quire.engine.anim.Clock
import app.quire.engine.anim.Decay
import app.quire.engine.anim.MotionProfile
import app.quire.engine.anim.Spring
import app.quire.engine.anim.clamp
import app.quire.engine.anim.lerp
import app.quire.engine.anim.smoothstep
import app.quire.engine.design.Metrics
import app.quire.engine.design.Theme
import app.quire.engine.fx.Particles
import app.quire.engine.fx.Shaders
import app.quire.engine.input.GestureEngine
import app.quire.engine.scene.Camera3D
import app.quire.engine.scene.Quad3D
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// ---- the world, in world units -----------------------------------------------------------

// A plate is one unit wide, and as tall as the picture MonthPlate bakes (512 x 640). Every
// other length here is a multiple of that, so the whole scene rescales from one number and the
// quad matrix — never the geometry — is what answers the size of the screen.
private const val PLATE_WIDTH = 1f
private const val PLATE_HEIGHT = 1.25f

// The window of months held in slots, and the quads that draw them.
//
// The wall is a year page laid out by each month's own absolute index, so the window has to be
// hung on the page rather than on the focus: at any scroll position exactly two year pages can
// be on screen — the twenty-four months from `12*floor(page)` — and the corridor, when it is
// showing instead, needs a month behind the focus and five in front of it. Four months of lead
// covers the corridor's reach backwards out of January and the float-error case where `floor`
// lands a year early; thirty-four slots cover both pages and the corridor's reach forwards out
// of December, with a slot of slack at each end.
private const val WINDOW = 34
private const val SLOTS = 34
private const val PAGE_LEAD = 4

// The most plates one frame may draw, which must not exceed MonthPlate's own cache: a frame that
// drew a thirteenth distinct month would evict — and recycle — a bitmap it had already handed to
// the display list. MonthPlate keeps that number private, so this is the copy of it, and the two
// have to move together.
private const val MAX_DRAWN = 12

// ---- the year wall --------------------------------------------------------------------------

// Twelve months, three across and four down. The two gaps are deliberately unequal: they are
// chosen so the wall's aspect matches the clear band's, which is what lets the wall fill the band
// on both axes at once with neither one binding. One square gap would leave the height short by
// most of an inch and every date on the page smaller for it.
private const val GRID_COLUMNS = 3
private const val GRID_ROWS = 4
private const val GRID_GAP_X = 0.055f
private const val GRID_GAP_Y = 0.142f
private const val COLUMN_PITCH = PLATE_WIDTH + GRID_GAP_X
private const val ROW_PITCH = PLATE_HEIGHT + GRID_GAP_Y
private const val WALL_WIDTH = 3f * PLATE_WIDTH + 2f * GRID_GAP_X
private const val WALL_HEIGHT = 4f * PLATE_HEIGHT + 3f * GRID_GAP_Y

// The margin the wall is framed inside, and deliberately narrower than the gutter. A printed year
// page gets away with a wide margin because its months carry no padding of their own; ours are
// cards and carry five dp a side already, so a gutter here would be paid twice and taken off
// every date on the page.
private const val GRID_MARGIN_DP = 10f

// The gap between one year page and the next, and so the stride a drag moves. Wide enough that no
// more than three columns — twelve plates, exactly what [MAX_DRAWN] allows — can overlap the
// viewport at once, however far through a page turn the wall is stopped.
private const val GRID_PAGE_GAP = 1.6f
private const val PAGE_STRIDE = WALL_WIDTH + GRID_PAGE_GAP

// The wall is not flat. Columns and rows bow gently away from the eye over a radius this long,
// and the whole thing leans back at the top; together they spread the plates over about half a
// world unit of depth, which is an eight per cent size gradient from the top row to the bottom.
// The lean is the one knob that trades legibility for depth and it is monotone: at 0.20 rad the
// spread is twelve per cent and January's numerals start to go.
private const val GRID_DOME = 7.2f
private const val GRID_LEAN = 0.10f
private val GRID_LEAN_SIN = sin(GRID_LEAN)

// The month holding today stands this far proud of the wall, falling off over this many cells.
// Keyed on today rather than on the focus: the focus moves on a tap at the year level, and a lift
// that followed it would pop, having no spring of its own — where today is fixed for the day,
// costs nothing and gives the wall an anchor.
private const val GRID_PROUD = 0.18f
private const val GRID_PROUD_FALLOFF = 1.6f

// How far each plate turns towards the eye. Not the whole way: the residual keeps the wall
// reading as a wall rather than as twelve billboards.
private const val GRID_FACE = 0.85f

// Where a plate waits when the corridor has no use for it: its own cell on the wall, this far
// behind it. That is what stops the months already passed — which sit behind the eye — from
// sweeping through the camera to reach their cells.
private const val GRID_ENTRY_DEPTH = 2.5f

// How far either side of the stretch where a plate can be seen the corridor keeps hold of it.
// The hand-over between the corridor and the staging position happens entirely inside this band,
// where the plate's corridor alpha is exactly zero, so nothing is ever seen moving through it.
private const val CORRIDOR_HOLD = 0.9f

// How much of the pinch's range the wall's arrival is spread over, radiating from the focused
// cell. Any more and the far corners are still travelling after the fingers have stopped, which
// reads as lag rather than as choreography.
private const val GRID_STAGGER = 0.35f

// Half the diagonal of the wall in cells, which is what turns a cell distance into 0..1.
private val CELL_REACH = sqrt(4f + 9f)

// Below this the wall counts as unformed and the page follows the focus. `gridness` is exactly
// zero for any zoom at or under GRID_FORM_START, so this is only a hair of tolerance.
private const val GRID_LATCH = 0.002f

// And past this the wall is what the world is: which unit a drag scrolls in, whether a jump
// across the year is worth springing through, and whether the title reads a month or a year.
private const val GRID_FORMED = 0.5f

// How far the wall turns about its own centre at full tilt. On a line, parallax is a depth
// ordering the eye can walk; on a wall there is no ordering, so it has to become rotation — the
// two sides trade depth as the phone moves, which is the only cue that reads across a grid.
private const val GRID_SWING = 0.030f
private const val GRID_SWING_V = 0.022f

// A page more than this far from where the wall is scrolled to has gone; the fade is clear of
// half a page either way, so a wall stopped between two years shows both of them whole.
private const val PAGE_FADE_START = 0.55f
private const val PAGE_FADE_END = 0.85f

// How far into the morph a month of another year, seen down the corridor, has finished leaving.
private const val PAGE_LEAVE_END = 0.20f

// The corridor. Spacing is a fraction of the distance the focused plate is viewed from, so the
// month behind is always the same size on screen whatever the display.
private const val CORRIDOR_SPACING = 0.62f
private const val CORRIDOR_FAN = 0.78f
private const val CORRIDOR_FAN_RATE = 0.85f
private const val CORRIDOR_FACE = 0.55f

// A month the camera has travelled past slides aside as it goes, so the two never overlap for
// long. The ramp starts flat, so nothing kinks on the frame a plate crosses the focus.
private const val CORRIDOR_PASS_SIDE = 0.62f
private const val CORRIDOR_PASS_RAMP = 0.35f

// A plate the camera is passing fades out before it can swallow the screen, and a plate at the
// far end of the corridor fades in rather than appearing.
private const val NEAR_FADE_START = -0.55f
private const val NEAR_FADE_END = -0.05f
private const val FAR_FADE_START = 3.4f
private const val FAR_FADE_END = 5.0f

// Never divide the facing angle by a depth at or behind the eye.
private const val MIN_FACE_DEPTH = 0.25f

// ---- camera -------------------------------------------------------------------------------

private const val DEGREES = 0.017453292f

// A long lens keeps the perspective gentle, which is what lets a date stay a date rather than a
// trapezoid. With depth switched off it grows longer still, so the picture reads nearly flat.
//
// The wall widens it again, and this is the biggest single lever it has. At forty-two degrees the
// eye would stand three times as far back from a wall as from a month, and half a unit of wall
// depth would be worth almost nothing on screen; at fifty-six it stands twice as far back and the
// same half unit is worth forty per cent more size difference. Depth off must not widen anything,
// so both ends collapse to the flat lens there.
private const val FOV_DEEP = 42f
private const val FOV_GRID = 56f
private const val FOV_FLAT = 30f
private const val CAM_NEAR = 0.05f
private const val CAM_FAR_MARGIN = 6f

// Travel is continuous months; distance is 1 (one plate fills the view) to 12 (the year wall).
private const val MIN_DISTANCE = 1f
private const val MAX_DISTANCE = 12f

// Where the pinch stops being "a month" and starts being "the year", for the level the Hud
// lights and the back gesture steps out of.
private const val LEVEL_SPLIT = 6.5f

// The wall forms a little after the camera starts moving back, so a small pinch is travel along
// the corridor rather than an immediate change of shape.
private const val GRID_FORM_START = 0.06f
private const val GRID_FORM_END = 0.88f

// How far the world is pushed back and dimmed under an open day.
//
// The dim is shared with [Ambience]: this half fades the plates towards the ground behind them,
// and the shade drawn over the whole world takes the ground down with them. Fading the plates
// alone left an open day standing on a background that had not moved at all, which reads as a
// card on a photograph rather than as a world that has stepped back.
private const val OPEN_PUSH = 0.07f
private const val WORLD_DIM = 0.22f

// The open day draws its own header — the number, the weekday, the month — into the corner the
// Hud's title stands in, so the title fades out as the panel grows and is gone before the panel's
// contents arrive (DayPanel starts its own fade at 0.34 of openness, which is file-private there,
// hence a separate number here rather than a shared one). The strip is not touched: Today, Year,
// Add, Search and Settings are how the day is left.
//
// Driven by the openness value rather than by the level, which is an int read off a target and
// would pop the title on the frame a gesture decided. The value is the same spring the panel
// morph rides, so the title dissolves with the panel and comes back on a drag that closes it.
private const val TITLE_FADE_OUT = 0.30f

// How far the eye slides with the hand, in world units at full tilt — at the month, where the eye
// stands at nearZ. It is redefined as a screen-space quantity: the slide grows with the distance
// the eye stands back, so the sway is the same number of pixels at every level.
private const val TILT_REACH = 0.024f

// The Hud draws the title into the top corner and reports only how tall its bottom strip is, so
// the world keeps the one measurement it needs to clear it: the gutter above the text, and one
// line of it. Both numbers are Hud's own — if the title's type moves, these follow it.
private const val HUD_TITLE_DP = 28f
private const val HUD_TITLE_LINE = 1.18f

// How far either side of the swap the title fades through nothing, so the month name becomes the
// year without either of them being seen to be replaced.
private const val TITLE_SWAP_BAND = 0.10f

// ---- gestures -----------------------------------------------------------------------------

// One screen of drag is one month at the corridor. At the wall the unit is a year and the rate is
// not a constant at all: it is derived from the page stride on screen, so the wall tracks the
// finger exactly, which is what a scrolling page has to do.
private const val MONTHS_PER_SCREEN_NEAR = 1.05f

private const val FLING_FRICTION = 5.2f
private const val FLING_LIMIT = 30f
private const val PAGE_FLING_LIMIT = 12f

// Below this the fling has stopped being a throw and is only creeping, so the spring takes it
// the rest of the way and lands it on a whole month — or, at the wall, on a whole year.
private const val HAND_OVER = 0.7f
private const val PAGE_HAND_OVER = 0.06f
private const val PROJECT_SECONDS = 0.11f

// How far a drag from the top of an open day has to travel downwards before it closes it.
private const val CLOSE_TRAVEL_DP = 96f

private const val SPARKS = 14
private const val SPARK_SPEED_DP = 150f

// ---- sparks and trails ------------------------------------------------------------------

// A landing on a new level throws a little light off the corners of the month it landed on. Four
// small bursts rather than one large one: a burst is emitted from a point in every direction, so
// the only way to say "this rectangle" is to say it at its corners.
//
// Deliberately quieter than the burst that confirms a chosen day. Sparks in this app mean "that
// happened"; firing them at every month boundary a fling crosses, or at both ends of every
// change, would spend the meaning until they were only clutter.
private const val LEVEL_SPARK_POINTS = 4
private const val LEVEL_SPARKS = 3
private const val LEVEL_SPARK_SPEED_DP = 130f
private const val LEVEL_SPARK_ALPHA = 0.55f

// A drag only leaves a trail once it is genuinely being thrown, measured per touch event — which
// is at most one a frame, so the emitter is self-limiting without a rate of its own.
//
// Fainter than the sparks, and for a reason a render made obvious: a trail particle is about the
// size of an event mark, and at full strength one passing over a row of days reads as a mark
// rather than as light. At this alpha the halo carries it and the core stops competing.
private const val TRAIL_SPEED_DP = 13f
private const val TRAIL_ALPHA = 0.28f

// ---- the press ----------------------------------------------------------------------------

// A press is a rebound, not a held state: GestureEngine reports nothing at all when a stroke
// lifts after a long press or a suppressed tap, so a dip that waited for a lift would stick on
// the screen. It is armed at full and springs back to nothing, which needs no lift to end it.
private const val PRESS_WASH = 1.6f

// The corners and the dip, in the plate's own normalised space. Every vertical measure is the
// horizontal one over the plate's aspect, which is what makes a corner drawn on a 4:5 card come
// out round rather than as an ellipse, and a dip come in by the same number of pixels on both
// axes rather than by the same fraction.
//
// The card dips by far less than a day does, in proportion. A tile is a small thing being pushed
// in; a card is a whole plate being pressed, and insetting that by a day's fraction leaves the
// card's own edge showing all round the wash, which reads as a grey rectangle laid on the month
// rather than as the month going down.
private const val PLATE_ASPECT = PLATE_HEIGHT / PLATE_WIDTH
private const val PRESS_PLATE_RADIUS = 0.055f
private const val PRESS_PLATE_INSET = 0.012f
private const val PRESS_CELL_RADIUS = 0.24f
private const val PRESS_CELL_INSET = 0.06f

private const val MIN_ALPHA = 1f / 255f
private const val RGB_MASK = 0x00FFFFFF

/**
 * The calendar as a place rather than a page: months are plates standing in a corridor that
 * recedes from the viewer, the camera travels along it, and pulling back with two fingers stands
 * the year up as a wall — twelve months of one calendar year, three across and four down, filling
 * the screen. Choosing a day lifts its tile out of the plate and opens it into the [DayPanel].
 *
 * Two continuous coordinates carry the whole thing. `travel` is a fractional month index, the
 * camera's station along the corridor; `distance` is how far back the eye stands, 1 for a month
 * filling the view and 12 for the year; and `page` is which year the wall is scrolled to, in whole
 * years since 1970. Nothing here switches between screens: every level is the same quads placed by
 * the same function blending between the corridor and the wall, so a pinch interrupted halfway is
 * a real position rather than an animation caught between two states, and reversing it retraces
 * the same path — the stagger the plates arrive on is a function of the pinch, never of time.
 *
 * Which twelve are on the wall is not a question the window answers. A month's cell is its own
 * month of its own year — `floorMod(index, 12)` on a page of `floorDiv(index, 12)` — so a plate
 * lands in the same cell of the same page whatever the window is doing and whichever slot happens
 * to hold it. `page` follows the focus while the wall is not formed and the focus follows `page`
 * once it is, and since a pinch writes only `distance`, neither can move while fingers are down:
 * plate identity through a gesture is a property of the arithmetic rather than of care.
 *
 * Depth is in service of legibility, not the other way round: the focused plate is square to the
 * camera at level 1, the lens is long, and the tilt parallax translates the eye rather than
 * rotating it, so a plate parallel to the screen stays a rectangle. On the wall, where plates lie
 * across the view axis instead of along it and translation has nothing to show, tilt turns the
 * wall about its own centre and every plate re-aims at where the eye actually is.
 *
 * The effects over the scene obey the same rule as its geometry: they are functions of where the
 * world is standing rather than of how long it has been open. [Ambience] draws a ground whose
 * phase is read off `travel` and `distance` and which slides under the plates with the hand at a
 * slower rate than they do; the wall arrives in a wave radiating from the focused cell, lagged by
 * distance in cells and keyed on the pinch, not on a clock. Nothing here breathes. The three
 * things that are genuinely events — a chosen day, a landed level, a thrown drag — are the only
 * ones that claim frames, they are edge-triggered from the gesture rather than tested per frame,
 * and every one of them is skipped outright under [MotionProfile.OFF], which is a contract about
 * arriving now rather than an instruction to hurry.
 *
 * Everything drawn per frame is a preallocated field — quads, paints, matrices, rectangles, the
 * month and load windows — and the frame loop is [Clock], subscribed while the view is attached
 * and asked for frames only while something is actually moving.
 */
class WorldView(context: Context) : View(context) {

    /**
     * Where the world gets what it draws. Both calls are answered from the host's own cache and
     * are asked only when the visible window of months moves, never once per frame.
     */
    interface Data {

        /** Event load per day for [month], as the plates bake into their pictures. */
        fun loads(month: YearMonth): Map<LocalDate, DayLoad>

        /** Everything happening on [date], for the panel the day opens into. */
        fun agenda(date: LocalDate): List<AgendaEntry>
    }

    // ---- scene ---------------------------------------------------------

    private val displayDensity: Float = resources.displayMetrics.density

    private val camera = Camera3D()

    private val quads: Array<Quad3D> = Array(SLOTS) {
        Quad3D().apply {
            width = PLATE_WIDTH
            height = PLATE_HEIGHT
        }
    }

    private val months: Array<YearMonth> = Array(WINDOW) { MonthModel.monthAt(it) }
    private val loads: Array<Map<LocalDate, DayLoad>> =
        Array<Map<LocalDate, DayLoad>>(WINDOW) { emptyMap() }
    private val alphas = FloatArray(WINDOW)
    private val order = IntArray(WINDOW)
    private var drawnCount = 0
    private var windowBase = Int.MIN_VALUE

    /**
     * What each month's picture was baked from. A plate holds the loads it was painted with, so
     * a month whose events land later has to be dropped and painted again — but only that month.
     * The host reports every arrival separately, and throwing the whole cache away each time
     * would re-bake the visible window a dozen times over during a cold start.
     *
     * Keyed by month rather than by slot, so a month that leaves the window and comes back
     * keeps the picture it already had.
     */
    private val baked =
        object : LinkedHashMap<YearMonth, Map<LocalDate, DayLoad>>(BAKED_BUCKETS, LOAD_FACTOR) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<YearMonth, Map<LocalDate, DayLoad>>,
            ): Boolean = size > BAKED_RECORDS
        }

    private var shownAgenda: List<AgendaEntry>? = null

    private val plate = MonthPlate(context)
    private val dayPanel = DayPanel(context)
    private val hud = Hud(context)
    private val ambience = Ambience()
    private val particles = Particles()
    private val gestures = GestureEngine(displayDensity)

    // ---- motion --------------------------------------------------------

    /** The continuous month index the camera is standing at; whole numbers are months. */
    private val travel = Spring()

    /**
     * The continuous year page the wall is scrolled to, in whole years since 1970.
     *
     * It has to be a coordinate of its own. Which calendar year is centred is
     * `floorDiv(round(travel), 12)` — a step function — and no affine function of `travel` can
     * centre a year across all twelve of its months: place the pages side by side and scroll them
     * by `travel/12` and only January is ever centred. Smoothing the step gives either a
     * staircase that lurches through December or a coordinate with its own dynamics, and this app
     * already has the answer for a coordinate with its own dynamics.
     */
    private val page = Spring()

    /** How far back the eye stands, in the 1..12 the pinch speaks. */
    private val distance = Spring(MIN_DISTANCE)

    /** 0 the day is still a tile in its plate, 1 the panel is open. */
    private val openness = Spring()

    /** The throw a released drag becomes, before travel settles on a whole month. */
    private val fling = Decay()

    /** Where the phone is being held, smoothed so the parallax glides rather than jitters. */
    private val tiltX = Spring()
    private val tiltY = Spring()

    /**
     * The rebound under a finger: armed at 1 by a press and springing back to 0 on its own.
     *
     * One spring for the whole world rather than one per plate. Only one thing can be under a
     * finger at a time, and a second press arriving while the first is still returning is the
     * same press moved, not two of them.
     */
    private val press = Spring()

    /** Which month the press landed on, as an absolute index, and which of its cells. */
    private var pressIndex = Int.MIN_VALUE
    private var pressCell = -1

    /**
     * The level the sparks were last thrown for. The trigger has to be an edge against a stored
     * value: a per-frame test of `level` would fire again on every frame of the landing, and a
     * test taken while fingers are down would fire every time a wobbling pinch crossed the split.
     */
    private var sparkedLevel = 1

    private var flinging = false
    private var flingYears = false
    private var dragging = false
    private var dragYears = false
    private var pinching = false
    private var panelDrag = false
    private var closeTravel = 0f
    private var downInBar = false

    // ---- per-frame scratch, never allocated in draw ---------------------

    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val tileMatrix = Matrix()
    private val tileUv = RectF()
    private val tileScreen = RectF()
    private val pressRect = RectF()
    private val sparkRect = RectF()
    private val panelBounds = RectF()
    private val viewRect = RectF()
    private val cullRect = RectF()
    private val hitUv = FloatArray(2)

    // ---- geometry, worked out when the surface or the metrics move ------

    private var nearZ = 1f
    private var gridZ = 1f
    private var camFar = 100f
    private var spacing = 1f
    private var bandOffset = 0f
    private var gridBandOffset = 0f
    private var pageStridePixels = 1f
    private var safeTop = 0f
    private var safeBottom = 0f

    // Where the eye stood when the camera was last aimed. The wall turns every plate towards it,
    // so place() needs the tilt-slid position rather than the origin.
    private var eyeX = 0f
    private var eyeY = 0f
    private var eyeZ = 1f

    private var titleKey = Int.MIN_VALUE
    private var titleText = ""
    private var subtitleText = ""

    private var selectedMonth: YearMonth = YearMonth.now()
    private var selectedIndex = 0

    // Which cell of which page holds today, so the wall can stand that month proud without any
    // per-frame date arithmetic.
    private var todayCell = 0
    private var todayPage = 0

    // ---- public surface ------------------------------------------------

    /**
     * The only source of colour in the world. Setting it repaints everything, including the
     * baked month pictures, which hold the colours they were rasterised with.
     */
    var theme: Theme = Theme(Theme.seeds[0].second, dark = false)
        set(value) {
            if (value == field) return
            field = value
            Shaders.reset()
            // The plate drops its own pictures when it is told a colour moved; nothing else
            // here is worth keeping either way.
            configureChildren()
            wake()
        }

    /**
     * How lively the world is, and the one setting that has to be obeyed rather than
     * interpreted: `OFF` means every value arrives on the frame it is given its target.
     */
    var motion: MotionProfile = MotionProfile.STANDARD
        set(value) {
            if (value == field) return
            field = value
            travel.profile(value)
            page.profile(value)
            distance.profile(value)
            openness.profile(value)
            press.profile(value)
            // Tilt answers the hand rather than a gesture, so it glides on a calm profile
            // whatever the rest of the world is doing — unless motion is off altogether.
            val steady = if (value.instant) MotionProfile.OFF else MotionProfile.CALM
            tiltX.profile(steady)
            tiltY.profile(steady)
            configureChildren()
            wake()
        }

    /** The only source of size, including type sizes; setting it relays the whole world out. */
    var metrics: Metrics = Metrics(displayDensity)
        set(value) {
            field = value
            configureChildren()
            layoutSurface()
            wake()
        }

    /** Which weekday the grids start on, which moves every date in every plate. */
    var firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
        set(value) {
            if (value == field) return
            field = value
            configureChildren()
            refreshWindow(force = true)
            wake()
        }

    /** The date that carries the accent disc; re-set it at midnight. */
    var today: LocalDate = LocalDate.now()
        set(value) {
            if (value == field) return
            field = value
            refreshToday()
            configureChildren()
            wake()
        }

    /** The chosen day: what the panel opens and what the plates ring. */
    var selected: LocalDate = LocalDate.now()
        private set

    /** Whether a choice or a change of level answers with a tick under the finger. */
    var haptics: Boolean = true

    /**
     * Parallax and the deep lens. Switched off, the eye stops answering the hand and the lens
     * grows longer, so the world reads nearly flat without losing its layout.
     */
    var depth: Boolean = true
        set(value) {
            if (value == field) return
            field = value
            if (!value) {
                tiltX.snapTo(0f)
                tiltY.snapTo(0f)
            }
            layoutSurface()
            wake()
        }

    /** Whether event marks are drawn in their calendars' colours or in one quiet ink. */
    var colouredMarks: Boolean = true
        set(value) {
            if (value == field) return
            field = value
            configureChildren()
            wake()
        }

    /** Whether each day is washed with the accent in proportion to how much it holds. */
    var density: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            configureChildren()
            wake()
        }

    /** Where the world reads its months and agendas; setting it refreshes what is on screen. */
    var data: Data? = null
        set(value) {
            field = value
            refreshWindow(force = true)
            wake()
        }

    /** Told whenever the chosen day changes, however it was chosen. */
    var onSelectionChanged: ((LocalDate) -> Unit)? = null

    /** Told when an entry in the open day is activated. */
    var onEntryActivated: ((AgendaEntry) -> Unit)? = null

    /** Told when a long press asks for something new on the day under the finger. */
    var onComposeRequested: ((LocalDate) -> Unit)? = null

    /** Told which of the Hud's targets was pressed; the ids are the ones `Hud` declares. */
    var onHudAction: ((Int) -> Unit)? = null

    /** Told when the world settles on a different [level], so a host can follow it. */
    var onLevelChanged: (() -> Unit)? = null

    /**
     * Where the world is standing: 0 the year wall, 1 one month, 2 the open day.
     *
     * Read from the targets rather than the values, so it names where the world is going the
     * moment a gesture decides it — a pinch writes its target continuously, so the level flips
     * under the fingers exactly as the wall takes over.
     */
    val level: Int
        get() = when {
            openness.target > 0.5f -> 2
            distance.target > LEVEL_SPLIT -> 0
            else -> 1
        }

    private val frame: (Float) -> Boolean = { dt -> step(dt) }

    init {
        isClickable = true
        isFocusable = true
        setWillNotDraw(false)
        selectedMonth = YearMonth.from(selected)
        selectedIndex = MonthModel.indexOf(selectedMonth)
        travel.snapTo(selectedIndex.toFloat())
        page.snapTo(clamp(Math.floorDiv(selectedIndex, 12).toFloat(), 0f, PAGE_MAX))
        refreshToday()
        fling.friction = FLING_FRICTION
        fling.min = MIN_INDEX.toFloat()
        fling.max = MAX_INDEX.toFloat()
        tiltX.profile(MotionProfile.CALM)
        tiltY.profile(MotionProfile.CALM)
        configureChildren()
        refreshWindow(force = true)
        gestures.attach(this)
        gestures.listener = Touch()
    }

    // ---- placement -----------------------------------------------------

    /**
     * Where the phone is being held, in −1..1 on each axis, as the host's sensor reports it.
     * Ignored while [depth] is off, which is what "no parallax" has to mean.
     */
    fun setTilt(x: Float, y: Float) {
        if (!depth) return
        tiltX.target = clamp(x, -1f, 1f)
        tiltY.target = clamp(y, -1f, 1f)
        if (motion.instant) {
            tiltX.snapTo(tiltX.target)
            tiltY.snapTo(tiltY.target)
        }
        wake()
    }

    /**
     * The window's own insets, so the title clears the status bar and the world keeps its
     * months clear of the strip along the bottom.
     */
    fun setSafeInsets(top: Float, bottom: Float) {
        val safeTopValue = max(0f, top)
        val safeBottomValue = max(0f, bottom)
        if (safeTopValue == safeTop && safeBottomValue == safeBottom) return
        safeTop = safeTopValue
        safeBottom = safeBottomValue
        hud.setSafeInsets(safeTopValue, safeBottomValue)
        layoutSurface()
        wake()
    }

    /**
     * Takes the world to [date] at [level], choosing the day on the way. With [animate] false —
     * or with motion off — it arrives on the next frame instead of travelling.
     */
    fun goTo(date: LocalDate, level: Int = 1, animate: Boolean = true) {
        val was = this.level
        select(date, notify = true, haptic = false)
        val index = MonthModel.indexOf(YearMonth.from(date)).toFloat()
        flinging = false
        travel.target = clamp(index, MIN_INDEX.toFloat(), MAX_INDEX.toFloat())
        if (!animate || motion.instant) travel.snapTo(travel.target)
        applyLevel(level, animate)
        notifyLevel(was)
        wake()
    }

    /** Moves to a level without touching which day is chosen. */
    fun goToLevel(level: Int) {
        val was = this.level
        applyLevel(level, animate = true)
        tick(HapticFeedbackConstants.CLOCK_TICK)
        notifyLevel(was)
        wake()
    }

    /**
     * Steps out one level — the day back into its month, the month back into its year — and
     * reports false when there is nowhere further out to go, so a host can let the back
     * gesture leave the screen.
     */
    fun zoomOut(): Boolean {
        val here = level
        if (here <= 0) return false
        goToLevel(here - 1)
        return true
    }

    /**
     * Marks or agendas arrived. Every month in the window is asked for again, and the ones
     * whose answer actually changed are the only ones repainted — the host reports each arrival
     * separately, so this is called many times over a cold start.
     */
    fun dataChanged() {
        refreshWindow(force = true)
        if (openness.target > 0.5f) refreshAgenda()
        wake()
    }

    // ---- lifecycle -----------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Clock.subscribe(frame)
    }

    override fun onDetachedFromWindow() {
        Clock.unsubscribe(frame)
        gestures.cancel()
        particles.clear()
        // The bitmaps are a cache, not state: the next frame after a reattach paints them
        // again, and holding twelve of them for a view nobody is looking at is a waste.
        plate.release()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Shaders.reset()
        layoutSurface()
        wake()
    }

    private fun layoutSurface() {
        val w = width.toFloat()
        val h = height.toFloat()
        hud.layout(w, h)
        viewRect.set(0f, 0f, w, h)
        if (w <= 1f || h <= 1f) return

        val bar = hud.barHeight
        val gutter = metrics.gutter
        val freeWidth = max(w - 2f * gutter, gutter)
        val freeHeight = max(h - safeTop - bar - 2f * gutter, gutter)

        val nearTanY = tan(nearFov() * DEGREES * 0.5f)
        val nearTanX = nearTanY * (w / h)

        // Far enough back that the plate fits the clear band between the title and the strip,
        // measured on both axes and answering to whichever runs out first — a portrait plate on
        // a portrait screen is usually stopped by the width.
        nearZ = max(
            PLATE_HEIGHT * h / (2f * nearTanY * freeHeight),
            PLATE_WIDTH * w / (2f * nearTanX * freeWidth),
        )

        // And far enough back that the whole wall fits the band that is genuinely clear — under
        // the title rather than under the status bar, and inside a margin narrower than the
        // gutter, because every plate is a card and carries padding of its own. The two gaps
        // between the plates were chosen so this comes out equal on both axes.
        val margin = metrics.dp(GRID_MARGIN_DP)
        val bandTop = safeTop + gutter + metrics.sp(HUD_TITLE_DP) * HUD_TITLE_LINE + gutter
        val bandBottom = h - bar - gutter
        val bandWidth = max(w - 2f * margin, margin)
        val bandHeight = max(bandBottom - bandTop, margin)
        val gridTanY = tan(gridFov() * DEGREES * 0.5f)
        val gridTanX = gridTanY * (w / h)
        gridZ = max(
            max(
                WALL_HEIGHT * h / (2f * gridTanY * bandHeight),
                WALL_WIDTH * w / (2f * gridTanX * bandWidth),
            ),
            nearZ,
        )

        camFar = gridZ + PAGE_STRIDE + GRID_ENTRY_DEPTH + CAM_FAR_MARGIN
        spacing = nearZ * CORRIDOR_SPACING
        // What one year of drag is worth on screen. Derived rather than named, so the wall tracks
        // the finger exactly instead of at some rate that happens to feel right on one display.
        pageStridePixels = max(PAGE_STRIDE * h / (2f * gridTanY * gridZ), 1f)

        // The clear band is not centred on the screen, so the eye is offset until the band's
        // middle is where the plate lands. Both eye and target move together, which shifts the
        // picture without shearing it.
        bandOffset = (safeTop + h - bar) * 0.5f - h * 0.5f
        // The wall leans back, so its ink is not centred on its own geometry: the top row stands
        // further away than the bottom one and covers less of the band. Centring what is drawn
        // rather than where it nominally is buys most of a row's worth of slack back.
        val focal = h / (2f * gridTanY)
        val edge = (GRID_ROWS - 1) * 0.5f * ROW_PITCH
        val reach = edge + PLATE_HEIGHT * 0.5f
        val top = reach * focal / max(gridZ - wallDepth(0f, edge), MIN_FACE_DEPTH)
        val bottom = reach * focal / max(gridZ - wallDepth(0f, -edge), MIN_FACE_DEPTH)
        gridBandOffset = (bandTop + bandBottom) * 0.5f - h * 0.5f + (top - bottom) * 0.5f

        panelBounds.set(gutter, safeTop + gutter, w - gutter, h - bar - gutter)
        dayPanel.setBounds(panelBounds)
    }

    private fun configureChildren() {
        plate.configure(theme, metrics, firstDayOfWeek, today, colouredMarks, density)
        dayPanel.configure(theme, metrics)
        hud.configure(theme, metrics, motion)
        hud.setSafeInsets(safeTop, safeBottom)
        ambience.configure(theme, metrics)
    }

    private fun refreshToday() {
        val index = MonthModel.indexOf(YearMonth.from(today))
        todayCell = Math.floorMod(index, 12)
        todayPage = Math.floorDiv(index, 12)
    }

    private fun nearFov(): Float = if (depth) FOV_DEEP else FOV_FLAT

    private fun gridFov(): Float = if (depth) FOV_GRID else FOV_FLAT

    private fun fovDegrees(): Float = lerp(nearFov(), gridFov(), gridness())

    // ---- frame loop ----------------------------------------------------

    /**
     * Asks for frames again and repaints.
     *
     * The window is brought up to date here rather than in [onDraw] because moving it can drop
     * a baked picture, and a plate recycles the bitmap it drops: doing that in the middle of a
     * draw pass would pull it out from under a display list that may still be holding it.
     */
    private fun wake() {
        if (isAttachedToWindow) Clock.subscribe(frame)
        refreshWindow(force = false)
        invalidate()
    }

    /** One frame: advance everything, then say whether anything still needs another. */
    private fun step(dt: Float): Boolean {
        var moving = dragging || pinching
        if (!dragging && !pinching && stepScroll(dt)) moving = true
        if (!pinching && distance.advance(dt)) moving = true
        if (openness.advance(dt)) moving = true
        if (tiltX.advance(dt)) moving = true
        if (tiltY.advance(dt)) moving = true
        if (dayPanel.advance(dt)) moving = true
        if (hud.advance(dt)) moving = true
        if (particles.advance(dt)) moving = true
        if (press.advance(dt)) {
            moving = true
        } else {
            // The rebound is over, so the world stops looking for something to wash. Cleared
            // here rather than at a lift, which is a callback a suppressed tap never sends.
            pressIndex = Int.MIN_VALUE
            pressCell = -1
        }
        // Travel has just moved, so the window may have to follow it. This runs in the
        // Choreographer's animation callback, ahead of the traversal that draws, which is what
        // keeps a recycled picture out of a display list.
        refreshWindow(force = false)
        invalidate()
        return moving
    }

    /**
     * The scroll is either a throw or a spring, never both, and it runs in whichever unit the
     * drag was started in — months along the corridor, years across the wall. The throw runs
     * until it is only creeping, and then hands its remaining speed to the spring along with a
     * target one whole unit away, so a flick lands on a month rather than stopping between two.
     */
    private fun stepScroll(dt: Float): Boolean {
        if (!flinging) {
            var moving = travel.advance(dt)
            if (page.advance(dt)) moving = true
            return moving
        }
        val scroll = if (flingYears) page else travel
        // The coordinate not being thrown is at rest by construction — one of the two rules is
        // always holding it — but it is advanced rather than assumed to be.
        if (flingYears) travel.advance(dt) else page.advance(dt)
        val running = fling.advance(dt)
        scroll.value = fling.value
        scroll.target = fling.value
        scroll.velocity = 0f
        val handOver = if (flingYears) PAGE_HAND_OVER else HAND_OVER
        if (running && abs(fling.velocity) > handOver) return true
        flinging = false
        scroll.velocity = fling.velocity
        val landing = fling.value + fling.velocity * PROJECT_SECONDS
        if (flingYears) {
            page.target = settlePage(landing)
            adoptPage()
        } else {
            travel.target = settle(landing)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        refreshTitle()
        val grid = gridness()
        // The ground answers where the camera is standing rather than how long the app has been
        // open: travel turns it slowly, the pinch turns it further, and the hand slides it under
        // the plates at a rate of its own. Every one of those is a value the world already holds,
        // so a settled screen paints the same ground for ever and claims nothing for it.
        ambience.drawField(
            canvas = canvas,
            width = w,
            height = h,
            travel = travel.value,
            zoom = zoomFraction(),
            grid = grid,
            tiltX = if (depth) tiltX.value else 0f,
            tiltY = if (depth) tiltY.value else 0f,
        )
        aimCamera(w, h)
        projectPlates()
        refreshOrigin()
        drawPlates(canvas)
        particles.draw(canvas)
        // Over the world and under the day, so opening a day takes the ground down with the
        // plates instead of leaving them fading against a background that never moved.
        ambience.drawShade(canvas, w, h, openness.value)
        dayPanel.draw(canvas, openness.value)
        hud.draw(
            canvas = canvas,
            title = titleText,
            subtitle = subtitleText,
            level = level,
            alpha = 1f,
            // The month name becomes the year on one frame in the middle of the pinch, so the
            // title is taken through nothing either side of it rather than swapped in place.
            titleAlpha = (1f - smoothstep(0f, TITLE_FADE_OUT, openness.value)) *
                (1f - smoothstep(GRID_FORMED - TITLE_SWAP_BAND, GRID_FORMED, grid) +
                    smoothstep(GRID_FORMED, GRID_FORMED + TITLE_SWAP_BAND, grid)),
        )
    }

    // ---- the scene -----------------------------------------------------

    private fun zoomFraction(): Float =
        clamp((distance.value - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE), 0f, 1f)

    /** How far the wall has formed: 0 the corridor, 1 the year page, and every value between. */
    private fun gridness(): Float = smoothstep(GRID_FORM_START, GRID_FORM_END, zoomFraction())

    private fun aimCamera(w: Float, h: Float) {
        val zoom = zoomFraction()
        val grid = gridness()
        val open = clamp(openness.value, 0f, 1f)
        val camZ = lerp(nearZ, gridZ, zoom) * (1f + OPEN_PUSH * open)
        val fov = fovDegrees()
        val tanY = tan(fov * DEGREES * 0.5f)

        // The clear band's offset is in pixels; at the focused plate's depth this is what it is
        // worth in world units. The band itself moves as the wall forms — a month is framed
        // between the title and the strip, a year page against the ink it actually draws.
        val shift = lerp(bandOffset, gridBandOffset, grid) * (2f * camZ * tanY / h)
        // The eye slides further the further back it stands, so the sway is the same number of
        // pixels at every level; at the month, where camZ is nearZ, this is exactly what it was.
        val reach = TILT_REACH * camZ / nearZ
        val slideX = if (depth) tiltX.value * reach else 0f
        val slideY = if (depth) -tiltY.value * reach else 0f

        camera.fovDegrees = fov
        camera.near = CAM_NEAR
        camera.far = camFar
        camera.position.set(slideX, shift + slideY, camZ)
        camera.target.set(slideX, shift + slideY, 0f)
        camera.update(w, h)

        // Kept for place(): every plate on the wall turns towards where the eye actually is, and
        // re-aiming live is what makes tilt read as depth rather than as a page under glass.
        eyeX = slideX
        eyeY = shift + slideY
        eyeZ = camZ
    }

    /**
     * Places and projects every plate in the window, and works out how present each one is.
     *
     * Every slot is projected whether or not it will be drawn, because the same corners answer a
     * tap and locate the tile an opening day grows out of. What is drawn is held under
     * [MAX_DRAWN] three times over — by the corridor and page fades, by a lateral cull against
     * the viewport, and finally by [capDrawn] — because a frame that drew a thirteenth distinct
     * month would recycle a bitmap it had already handed to the display list.
     */
    private fun projectPlates() {
        val grid = gridness()
        val pageValue = page.value
        val focusCell = Math.floorMod(travelIndex(), 12)
        drawnCount = 0
        var slot = 0
        while (slot < WINDOW) {
            val index = windowBase + slot
            val cell = Math.floorMod(index, 12)
            val pageIndex = Math.floorDiv(index, 12)
            val d = index - travel.value
            val quad = quads[slot]

            // The wall assembles staggered, radiating from the focused cell — arriving together
            // would make it one rigid object, which contradicts twelve separate cards. The
            // stagger is a function of the pinch's own progress and never of time, so reversing
            // the gesture retraces every path and stopping halfway is a real half-formed wall.
            val own = smoothstep(
                0f,
                1f,
                (grid - GRID_STAGGER * cellDistance(cell, focusCell) / CELL_REACH) /
                    (1f - GRID_STAGGER),
            )
            // The corridor keeps hold of a plate only over the stretch where it can be seen.
            // Outside that the plate waits behind its own cell instead, and the hand-over
            // happens entirely where its corridor alpha is zero — which is what stops the months
            // already passed, sitting behind the eye, from sweeping through the camera on their
            // way to the wall.
            val hold = smoothstep(NEAR_FADE_START - CORRIDOR_HOLD, NEAR_FADE_START, d) *
                (1f - smoothstep(FAR_FADE_END, FAR_FADE_END + CORRIDOR_HOLD, d))
            place(quad, cell, pageIndex, d, own, hold)
            quad.project(camera)

            // In the corridor the camera travels through the months it has read and towards the
            // ones it has not; on the wall what matters instead is how far the month's own page
            // is from the one the wall is scrolled to.
            val corridor = smoothstep(NEAR_FADE_START, NEAR_FADE_END, d) *
                (1f - smoothstep(FAR_FADE_START, FAR_FADE_END, d))
            val onPage = 1f - smoothstep(
                PAGE_FADE_START,
                PAGE_FADE_END,
                abs(pageIndex - pageValue),
            )
            // A month down the corridor whose page is sliding off has nowhere on the wall to
            // arrive, so it leaves near the start of the morph rather than lingering through all
            // of it. That is the right reading, and it is also what holds the drawn count inside
            // the plate cache at the worst focus there is: from December, five of the corridor's
            // six months belong to next year, and seventeen plates would want drawing at once.
            val here = corridor * (1f - smoothstep(0f, PAGE_LEAVE_END, grid) * (1f - onPage))
            // Blended rather than maxed. A month present in both layouts — the focus, and every
            // other month of its own page — has to stay opaque all the way through, and the max
            // of two complementary terms dips to a half in the middle, which lets the corridor
            // behind show straight through the card in front.
            var alpha = lerp(here, onPage, own)
            if (alpha > MIN_ALPHA && quad.visible) {
                // Quad3D only asks whether a quad is in front of the eye and facing it; a page
                // half a screen off the left edge passes both. Order matters — a quad with a
                // corner behind the eye has garbage corners, so visibility is tested first.
                quad.bounds(cullRect)
                if (RectF.intersects(cullRect, viewRect)) {
                    order[drawnCount] = slot
                    drawnCount++
                } else {
                    alpha = 0f
                }
            }
            alphas[slot] = alpha
            slot++
        }
        sortBackToFront()
        capDrawn()
    }

    /**
     * One position for one plate, blended from three: where the corridor wants it, where its cell
     * on the wall is, and — for a month the corridor has no use for — that same cell pushed back
     * out of the way. Still no states and no branches; a pinch stopped halfway is a real place.
     */
    private fun place(
        quad: Quad3D,
        cell: Int,
        pageIndex: Int,
        d: Float,
        grid: Float,
        hold: Float,
    ) {
        // The corridor: a serpentine line of plates receding from the eye, the focused one dead
        // ahead at the origin. A plate the eye has already travelled past slides aside as it
        // goes, so the two never fight over the middle of the screen. The ramp is eased rather
        // than linear, so nothing kinks on the frame a plate crosses the focus.
        val past = -d
        val aside =
            if (past > 0f) past * CORRIDOR_PASS_SIDE * smoothstep(0f, CORRIDOR_PASS_RAMP, past)
            else 0f
        val cx = CORRIDOR_FAN * sin(d * CORRIDOR_FAN_RATE) + aside
        val cz = -d * spacing
        val cyaw = CORRIDOR_FACE * atan2(-cx, max(eyeZ - cz, MIN_FACE_DEPTH))

        // The wall: the month's own cell of its own year page. A total function of the month's
        // index, so a plate lands in the same cell of the same page forever — there is no year
        // boundary in it because there is no boundary in the arithmetic.
        val column = cell % GRID_COLUMNS
        val row = cell / GRID_COLUMNS
        val local = (column - 1) * COLUMN_PITCH
        val high = ((GRID_ROWS - 1) * 0.5f - row) * ROW_PITCH
        val flat = wallDepth(local, high) + proudOf(cell, pageIndex)

        // Tilt turns the wall about its own centre, on top of the eye's slide. Small angles, so
        // the swing is the first order of the two rotations and costs no trigonometry; the plates
        // are aimed after it, so each one answers the new geometry rather than riding it rigidly.
        val swingY = GRID_SWING * tiltX.value
        val swingX = GRID_SWING_V * tiltY.value
        val paged = local + (pageIndex - page.value) * PAGE_STRIDE
        val turned = flat - paged * swingY
        val gx = paged + flat * swingY
        val gy = high - turned * swingX
        val gz = turned + high * swingX

        val ahead = max(eyeZ - gz, MIN_FACE_DEPTH)
        val gyaw = GRID_FACE * atan2(eyeX - gx, ahead)
        // The signs are not symmetric and that is right: a positive pitch pushes the top corners
        // towards +z, which is towards the camera, which is what a plate above the eye must do.
        val gpitch = GRID_FACE * atan2(gy - eyeY, ahead)

        quad.position.set(
            lerp(lerp(gx, cx, hold), gx, grid),
            lerp(lerp(gy, 0f, hold), gy, grid),
            lerp(lerp(gz - GRID_ENTRY_DEPTH, cz, hold), gz, grid),
        )
        quad.yaw = lerp(lerp(gyaw, cyaw, hold), gyaw, grid)
        quad.pitch = lerp(lerp(gpitch, 0f, hold), gpitch, grid)
        quad.roll = 0f
    }

    /**
     * How far back a cell of the wall stands. Two shallow bows and a lean: the columns and the
     * rows curve away from the eye, and the whole wall tips back at the top.
     */
    private fun wallDepth(local: Float, high: Float): Float =
        -GRID_DOME * (1f - cos(local / GRID_DOME)) -
            GRID_DOME * (1f - cos(high / GRID_DOME)) -
            high * GRID_LEAN_SIN

    /** How far the month holding today stands proud of the wall, and only on today's own page. */
    private fun proudOf(cell: Int, pageIndex: Int): Float {
        if (pageIndex != todayPage) return 0f
        return GRID_PROUD *
            (1f - smoothstep(0f, GRID_PROUD_FALLOFF, cellDistance(cell, todayCell)))
    }

    /** How far apart two cells of the wall are, in cells, measured on the diagonal. */
    private fun cellDistance(a: Int, b: Int): Float {
        val dx = (a % GRID_COLUMNS - b % GRID_COLUMNS).toFloat()
        val dy = (a / GRID_COLUMNS - b / GRID_COLUMNS).toFloat()
        return sqrt(dx * dx + dy * dy)
    }

    // Painter's algorithm: furthest first. An insertion sort over a nearly-sorted handful of
    // entries is a few comparisons and, unlike a comparator, allocates nothing.
    private fun sortBackToFront() {
        var i = 1
        while (i < drawnCount) {
            val slot = order[i]
            val key = quads[slot].depth
            var j = i - 1
            while (j >= 0 && quads[order[j]].depth < key) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = slot
            i++
        }
    }

    /**
     * The last line of defence for the plate cache: drops the faintest entries until no more than
     * [MAX_DRAWN] are left. The fades and the cull hold the count at twelve on their own; this
     * only ever fires on a transient frame in the middle of the morph, and it removes what is
     * least visible. Bounded work, no allocation, and it runs over the sorted order in place.
     */
    private fun capDrawn() {
        while (drawnCount > MAX_DRAWN) {
            var weakest = 0
            var i = 1
            while (i < drawnCount) {
                if (alphas[order[i]] < alphas[order[weakest]]) weakest = i
                i++
            }
            var j = weakest
            while (j < drawnCount - 1) {
                order[j] = order[j + 1]
                j++
            }
            drawnCount--
        }
    }

    private fun drawPlates(canvas: Canvas) {
        val dim = 1f - WORLD_DIM * clamp(openness.value, 0f, 1f)
        var i = 0
        while (i < drawnCount) {
            val slot = order[i]
            val month = months[slot]
            val fade = alphas[slot] * dim
            plate.draw(
                canvas = canvas,
                quad = quads[slot],
                month = month,
                // The chosen day is marked on the month that owns it. A neighbouring plate
                // holds the same date in its trailing cells, and ringing it three times over
                // would say the world had three selections.
                selected = if (month == selectedMonth) selected else null,
                loads = loads[slot],
                alpha = fade,
            )
            // Immediately after its own plate rather than in a pass of its own, so a plate in
            // front of the pressed one still covers the wash the way it covers everything else.
            if (windowBase + slot == pressIndex) drawPress(canvas, slot, fade)
            i++
        }
    }

    /**
     * The wash under a finger: the cell that was pressed, or the whole card when the press landed
     * in a plate's margins — which at the year is most of what there is to hit.
     *
     * Drawn through the plate's own matrix, so it is keystoned with the card and stays welded to
     * the cell it marks. The rectangle shrinks as the press deepens and comes back as it fades,
     * which is what a tile pushed in and released looks like without moving the quad the hit test
     * and the opening day are both reading.
     */
    private fun drawPress(canvas: Canvas, slot: Int, alpha: Float) {
        val dip = press.value
        if (!(dip > 0f) || !(alpha > MIN_ALPHA)) return
        val quad = quads[slot]
        if (!quad.visible) return
        val radiusX: Float
        val insetX: Float
        if (pressCell >= 0) {
            plate.cellBounds(pressCell, pressRect)
            radiusX = pressRect.width() * PRESS_CELL_RADIUS
            insetX = pressRect.width() * PRESS_CELL_INSET * dip
        } else {
            pressRect.set(0f, 0f, 1f, 1f)
            radiusX = PRESS_PLATE_RADIUS
            insetX = PRESS_PLATE_INSET * dip
        }
        pressRect.inset(insetX, insetX / PLATE_ASPECT)
        quad.matrixFor(1f, 1f, tileMatrix)
        canvas.save()
        canvas.concat(tileMatrix)
        // theme.press is the design system's own wash under a touch, and its alpha is the
        // ceiling here — the multiplier only holds the wash near that ceiling through most of
        // the rebound instead of fading it from the first frame, which reads as a flicker.
        pressPaint.color = withAlpha(theme.press, alpha * dip * PRESS_WASH)
        canvas.drawRoundRect(pressRect, radiusX, radiusX / PLATE_ASPECT, pressPaint)
        canvas.restore()
    }

    // ---- the window of months ------------------------------------------

    /**
     * Rule A: while the wall is not formed, the page follows the focus.
     *
     * A snap of a quantity nothing can see — it moves no plate and claims no frame — and it is
     * what makes the year a pinch opens onto the calendar year of the month being read, whichever
     * of its twelve months that is. It reads the target rather than the value so that a jump
     * asked for and a level asked for in the same breath agree about where they are going.
     */
    private fun syncPage() {
        if (gridness() > GRID_LATCH) return
        page.snapTo(pageOf(travel.target.roundToInt()))
    }

    /**
     * Rule B: while the wall is formed, the focus follows the page, keeping its month of the
     * year — pinch out of August, scroll on a year, pinch back in, land on August.
     *
     * A snap rather than a target: at this end of the morph the corridor's whole contribution is
     * multiplied by zero, so travel crossing eleven months is not something the screen can show.
     */
    private fun adoptPage() {
        if (gridness() <= GRID_LATCH) return
        val landed = 12 * page.target.roundToInt() + Math.floorMod(travelIndex(), 12)
        travel.snapTo(clamp(landed.toFloat(), MIN_INDEX.toFloat(), MAX_INDEX.toFloat()))
    }

    /**
     * Which year the window of months is hung on.
     *
     * Below the latch the corridor owns it and it follows the focus; above, the wall owns it and
     * it follows the page. The two agree exactly on the frame the latch is crossed — [syncPage]
     * has just written one from the other — so a pinch, which writes only `distance`, can never
     * move it. That is why no slot can change its month while fingers are down: plate identity
     * through a gesture is a property of the arithmetic, not of care.
     */
    private fun pageAnchor(): Int =
        if (gridness() > GRID_LATCH) floor(page.value).toInt()
        else Math.floorDiv(travelIndex(), 12)

    /**
     * Keeps the window of months, and their loads, over the two year pages that can be on screen
     * and over the stretch of corridor the focus can see. The window moves a year at a time, so
     * the overlap is shifted rather than rebuilt.
     */
    private fun refreshWindow(force: Boolean) {
        syncPage()
        val base = 12 * pageAnchor() - PAGE_LEAD
        if (!force && base == windowBase) return
        // The first fill has no window to shift, and the subtraction that would measure the
        // move has nothing meaningful to measure.
        val fresh = force || windowBase == Int.MIN_VALUE
        val delta = if (fresh) WINDOW else base - windowBase
        windowBase = base
        if (fresh || abs(delta) >= WINDOW) {
            var slot = 0
            while (slot < WINDOW) {
                fillSlot(slot)
                slot++
            }
            return
        }
        if (delta > 0) {
            var slot = 0
            while (slot < WINDOW - delta) {
                months[slot] = months[slot + delta]
                loads[slot] = loads[slot + delta]
                slot++
            }
            while (slot < WINDOW) {
                fillSlot(slot)
                slot++
            }
        } else if (delta < 0) {
            val back = -delta
            var slot = WINDOW - 1
            while (slot >= back) {
                months[slot] = months[slot - back]
                loads[slot] = loads[slot - back]
                slot--
            }
            while (slot >= 0) {
                fillSlot(slot)
                slot--
            }
        }
    }

    private fun fillSlot(slot: Int) {
        val month = MonthModel.monthAt(windowBase + slot)
        val next = data?.loads(month) ?: emptyMap()
        months[slot] = month
        loads[slot] = next
        // Identity rather than equality: the host hands back the same map until the numbers
        // behind it actually change, and walking 42 days of entries every time the window moved
        // would cost more than the picture it is protecting.
        if (baked.put(month, next) !== next) plate.invalidate(month)
    }

    /**
     * The month name, or the year once the wall has taken over.
     *
     * Keyed on a packed int rather than on the strings, so the one allocation here happens when
     * the world lands on something new instead of once a frame: a month is its own index, a year
     * the negative of its page, and travel is clamped to zero and up so the two cannot collide.
     */
    private fun refreshTitle() {
        val wall = gridness() > GRID_FORMED
        val year = page.target.roundToInt()
        val key = if (wall) -(year + 1) else travelIndex()
        if (key == titleKey) return
        titleKey = key
        if (wall) {
            titleText = (EPOCH_YEAR + year).toString()
            subtitleText = ""
            return
        }
        val month = MonthModel.monthAt(travelIndex())
        titleText = MonthModel.monthName(month, locale())
        subtitleText = month.year.toString()
    }

    private fun travelIndex(): Int = travel.value.roundToInt()

    private fun pageOf(index: Int): Float =
        clamp(Math.floorDiv(index, 12).toFloat(), 0f, PAGE_MAX)

    private fun slotOf(index: Int): Int {
        val slot = index - windowBase
        return if (slot in 0 until WINDOW) slot else -1
    }

    // ---- days ----------------------------------------------------------

    /** The first cell of a month's grid: where its 42 consecutive days start. */
    private fun firstCell(month: YearMonth): LocalDate {
        val first = month.atDay(1)
        val lead = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, MonthModel.COLUMNS)
        return first.minusDays(lead.toLong())
    }

    private fun dateAt(month: YearMonth, cell: Int): LocalDate =
        firstCell(month).plusDays(cell.toLong())

    private fun cellOf(month: YearMonth, date: LocalDate): Int {
        val index = (date.toEpochDay() - firstCell(month).toEpochDay()).toInt()
        return if (index in 0 until MonthModel.CELLS) index else -1
    }

    /** The screen rectangle a cell occupies on a projected plate, or false when it is not shown. */
    private fun tileRect(slot: Int, cell: Int, out: RectF): Boolean {
        val quad = quads[slot]
        if (!quad.visible) return false
        plate.cellBounds(cell, tileUv)
        // Mapping a unit square rather than the cache's pixels means the cell's own normalised
        // bounds go straight through the same matrix the picture is drawn with.
        quad.matrixFor(1f, 1f, tileMatrix)
        out.set(tileUv)
        tileMatrix.mapRect(out)
        return true
    }

    /**
     * Keeps the panel welded to the tile it grew out of while it is neither shut nor fully
     * open, so it opens from the day rather than from wherever the day used to be.
     */
    private fun refreshOrigin() {
        if (openness.value <= 0f && openness.target <= 0f) return
        if (openness.value >= 1f) return
        val slot = slotOf(selectedIndex)
        if (slot < 0) return
        val cell = cellOf(selectedMonth, selected)
        if (cell < 0) return
        if (tileRect(slot, cell, tileScreen)) dayPanel.setOrigin(tileScreen)
    }

    private fun openDay() {
        val slot = slotOf(selectedIndex)
        val cell = if (slot >= 0) cellOf(selectedMonth, selected) else -1
        if (cell < 0 || !tileRect(slot, cell, tileScreen)) {
            // Nothing on screen to grow from — a day reached from the year wall, or before the
            // first frame. It opens from the middle of where it will end up instead.
            tileScreen.set(
                panelBounds.centerX(),
                panelBounds.centerY(),
                panelBounds.centerX(),
                panelBounds.centerY(),
            )
        }
        dayPanel.setOrigin(tileScreen)
        shownAgenda = null
        refreshAgenda()
        openness.target = 1f
        if (motion.instant) openness.snapTo(1f)
    }

    /**
     * Hands the panel the day's entries, and only when they are not the ones it is already
     * showing: [show] restarts the stagger, and a list that re-entered from the top every time
     * another month's marks landed would never finish arriving.
     */
    private fun refreshAgenda() {
        val entries = data?.agenda(selected).orEmpty()
        if (entries === shownAgenda) return
        shownAgenda = entries
        dayPanel.show(selected, entries, motion)
    }

    private fun closeDay() {
        if (openness.target <= 0f && openness.value <= 0f) return
        dayPanel.hide()
        openness.target = 0f
        if (motion.instant) openness.snapTo(0f)
    }

    private fun select(date: LocalDate, notify: Boolean, haptic: Boolean) {
        if (date == selected) return
        selected = date
        selectedMonth = YearMonth.from(date)
        selectedIndex = MonthModel.indexOf(selectedMonth)
        if (haptic) tick(HapticFeedbackConstants.CLOCK_TICK)
        if (notify) onSelectionChanged?.invoke(date)
    }

    /**
     * The day under a point, chosen: the plate it belongs to is brought forward, the day is
     * selected, and a few sparks in the accent confirm it at the tile itself.
     *
     * @return the date, or null when the point missed every plate or landed outside a grid.
     */
    private fun chooseDayAt(x: Float, y: Float, open: Boolean): LocalDate? {
        var i = drawnCount - 1
        while (i >= 0) {
            val slot = order[i]
            i--
            if (!quads[slot].hit(x, y, hitUv)) continue
            val month = months[slot]
            // Even a miss inside the plate's margins is worth something: the month that was
            // pointed at comes to the front, which is what makes the year wall navigable.
            face(month)
            val cell = plate.cellAt(hitUv[0], hitUv[1])
            if (cell < 0) {
                wake()
                return null
            }
            val date = dateAt(month, cell)
            select(date, notify = true, haptic = true)
            if (tileRect(slot, cell, tileScreen)) {
                // Reduced motion is a contract about arriving now. Nine tenths of a second of
                // sparks is residual animation, and — because the world claims a frame for as
                // long as one particle is alive — nine tenths of a second of frames as well.
                if (!motion.instant) {
                    particles.burst(
                        x = tileScreen.centerX(),
                        y = tileScreen.centerY(),
                        count = SPARKS,
                        colour = theme.accent,
                        speed = metrics.dp(SPARK_SPEED_DP),
                    )
                }
                dayPanel.setOrigin(tileScreen)
            }
            if (open) {
                distance.target = MIN_DISTANCE
                openDay()
            }
            wake()
            return date
        }
        return null
    }

    /** Brings a month to the front of the corridor, or its cell to the middle of the wall. */
    private fun face(month: YearMonth) {
        val index = MonthModel.indexOf(month)
        flinging = false
        travel.target = clamp(index.toFloat(), MIN_INDEX.toFloat(), MAX_INDEX.toFloat())
        // Springing travel across a year through a corridor nobody can see would claim frames
        // for nothing, so with the wall formed it simply arrives.
        if (motion.instant || gridness() > GRID_FORMED) travel.snapTo(travel.target)
        if (gridness() <= GRID_LATCH) return
        // Tapping a plate on a wall stopped between two pages brings that plate's year home.
        page.target = pageOf(index)
        if (motion.instant) page.snapTo(page.target)
    }

    // ---- sparks, trails and the press ----------------------------------

    /**
     * Throws a little light off the month the world has just landed on, once per landing.
     *
     * Edge-triggered against [sparkedLevel] and called only from the two places a level is
     * actually settled — [applyLevel] and the end of a pinch. A per-frame predicate would fire
     * every frame of the landing, and a test taken mid-pinch would fire again every time a
     * wobbling gesture recrossed the split.
     *
     * The corners come from [Quad3D.bounds] on the plate the focus is on, so the burst rings the
     * month wherever it happens to be standing: the one card at the month level, its own cell of
     * the wall at the year. Nothing is thrown for an opening day — that has its own confirmation
     * at the tile, and the panel would cover these before they were seen.
     */
    private fun sparkLevel() {
        val now = level
        if (now == sparkedLevel) return
        sparkedLevel = now
        if (now > 1 || motion.instant) return
        val slot = slotOf(travel.target.roundToInt())
        if (slot < 0) return
        val quad = quads[slot]
        // False before the first frame has projected anything, which is exactly when a host
        // restoring its level would otherwise throw sparks at a world nobody has seen yet.
        if (!quad.visible) return
        quad.bounds(sparkRect)
        if (!RectF.intersects(sparkRect, viewRect)) return
        val colour = withAlpha(theme.accent, LEVEL_SPARK_ALPHA)
        val speed = metrics.dp(LEVEL_SPARK_SPEED_DP)
        var corner = 0
        while (corner < LEVEL_SPARK_POINTS) {
            particles.burst(
                x = if (corner and 1 == 0) sparkRect.left else sparkRect.right,
                y = if (corner < 2) sparkRect.top else sparkRect.bottom,
                count = LEVEL_SPARKS,
                colour = colour,
                speed = speed,
            )
            corner++
        }
    }

    /**
     * A trail under a drag that is genuinely being thrown, emitted per touch event rather than
     * per frame — which caps it at one particle a frame without a rate limiter of its own, and
     * means it stops the instant the finger does rather than trailing an inertial fling.
     */
    private fun trailAt(x: Float, y: Float, dx: Float) {
        if (motion.instant) return
        if (abs(dx) < metrics.dp(TRAIL_SPEED_DP)) return
        particles.trail(x, y, withAlpha(theme.accent, TRAIL_ALPHA))
    }

    /**
     * Arms the rebound under a finger, on whatever it landed on: a day, or the card itself where
     * there is no day — at the year that is how a month is picked, so it has to answer too.
     *
     * The month is remembered by its own index rather than by its slot, because the window can
     * slide out from under a slot while the rebound is still returning and the wash would follow
     * the slot onto a different month.
     */
    private fun pressAt(x: Float, y: Float) {
        pressIndex = Int.MIN_VALUE
        pressCell = -1
        if (motion.instant) return
        var i = drawnCount - 1
        while (i >= 0) {
            val slot = order[i]
            i--
            if (!quads[slot].hit(x, y, hitUv)) continue
            pressIndex = windowBase + slot
            pressCell = plate.cellAt(hitUv[0], hitUv[1])
            press.snapTo(1f)
            press.target = 0f
            return
        }
    }

    // ---- levels --------------------------------------------------------

    private fun applyLevel(next: Int, animate: Boolean) {
        when (next.coerceIn(0, 2)) {
            // Settled from the target rather than the value, so a level asked for straight
            // after a month was chosen lands on that month instead of on the one the world
            // happens to be passing through on its way there.
            0 -> {
                closeDay()
                distance.target = MAX_DISTANCE
                travel.target = settle(travel.target)
            }
            1 -> {
                closeDay()
                distance.target = MIN_DISTANCE
                travel.target = settle(travel.target)
            }
            else -> {
                distance.target = MIN_DISTANCE
                travel.target = clamp(
                    selectedIndex.toFloat(),
                    MIN_INDEX.toFloat(),
                    MAX_INDEX.toFloat(),
                )
                openDay()
            }
        }
        flinging = false
        page.target = pageOf(travel.target.roundToInt())
        if (!animate || motion.instant) {
            distance.snapTo(distance.target)
            travel.snapTo(travel.target)
            page.snapTo(page.target)
            openness.snapTo(openness.target)
        }
        sparkLevel()
    }

    private fun notifyLevel(previous: Int) {
        if (level != previous) onLevelChanged?.invoke()
    }

    private fun settle(raw: Float): Float =
        clamp(raw.roundToInt().toFloat(), MIN_INDEX.toFloat(), MAX_INDEX.toFloat())

    private fun settlePage(raw: Float): Float = clamp(raw.roundToInt().toFloat(), 0f, PAGE_MAX)

    private fun tick(constant: Int) {
        if (haptics) performHapticFeedback(constant)
    }

    // ---- input ---------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestures.onTouch(event)) return true
        return super.onTouchEvent(event)
    }

    /** Kept so the world answers an accessibility click the same way it answers a tap. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun monthsPerPixel(): Float = MONTHS_PER_SCREEN_NEAR / max(width.toFloat(), 1f)

    /** One over the page stride on screen, so a dragged wall tracks the finger exactly. */
    private fun yearsPerPixel(): Float = 1f / pageStridePixels

    private fun panelOpen(): Boolean = openness.target > 0.5f

    /**
     * Everything the finger can say. Order matters: the strip along the bottom is asked first,
     * then the open day, and only then the world behind them.
     */
    private inner class Touch : GestureEngine.Listener {

        override fun onDown(x: Float, y: Float) {
            flinging = false
            travel.velocity = 0f
            page.velocity = 0f
            downInBar = hud.actionAt(x, y) != 0
            panelDrag = panelOpen() && !downInBar
            closeTravel = 0f
            // The strip answers a press with its own dip, and an open day is the panel's to
            // answer; what is left is the world, which had nothing until now.
            if (!downInBar && !panelDrag) pressAt(x, y)
            wake()
        }

        override fun onTap(x: Float, y: Float) {
            performClick()
            val action = hud.actionAt(x, y)
            if (action != 0) {
                hud.press(action)
                tick(HapticFeedbackConstants.CLOCK_TICK)
                onHudAction?.invoke(action)
                wake()
                return
            }
            if (panelOpen()) {
                val entry = dayPanel.entryAt(x, y)
                if (entry != null) {
                    tick(HapticFeedbackConstants.CLOCK_TICK)
                    onEntryActivated?.invoke(entry)
                    return
                }
                // A tap on the world around an open day puts it back in its month.
                if (!panelBounds.contains(x, y)) goToLevel(1)
                return
            }
            chooseDayAt(x, y, open = false)
        }

        override fun onDoubleTap(x: Float, y: Float) {
            if (hud.actionAt(x, y) != 0) return
            val was = level
            when (was) {
                // From the year, a double tap dives into the month that was pointed at.
                0 -> {
                    chooseDayAt(x, y, open = false)
                    applyLevel(1, animate = true)
                }
                // From a month, it opens the day under the finger — or the day already chosen,
                // when the tap landed somewhere that is not a day at all.
                1 -> {
                    val opened = chooseDayAt(x, y, open = true)
                    if (opened == null) applyLevel(2, animate = true)
                }
                // From an open day, back out to the month it came from.
                else -> applyLevel(1, animate = true)
            }
            tick(HapticFeedbackConstants.CLOCK_TICK)
            notifyLevel(was)
            wake()
        }

        override fun onLongPress(x: Float, y: Float) {
            if (downInBar || panelOpen()) return
            val date = chooseDayAt(x, y, open = false) ?: return
            tick(HapticFeedbackConstants.LONG_PRESS)
            onComposeRequested?.invoke(date)
        }

        override fun onDragStart(x: Float, y: Float) {
            if (downInBar) return
            if (panelDrag) return
            dragging = true
            // Latched once, here. A drag cannot begin in the middle of a pinch — the pinch owns
            // both fingers — so the unit the world is scrolled in cannot change under the finger.
            dragYears = gridness() > GRID_FORMED
            wake()
        }

        override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
            if (downInBar) return
            if (panelDrag) {
                // At the top of the list a downward drag is not a scroll, it is the day being
                // put back; anywhere else the finger owns the list.
                if (dayPanel.scrollAtTop && dy > 0f) {
                    closeTravel += dy
                    if (closeTravel > metrics.dp(CLOSE_TRAVEL_DP)) {
                        panelDrag = false
                        gestures.cancel()
                        goToLevel(1)
                        return
                    }
                }
                dayPanel.scrollBy(dy)
                wake()
                return
            }
            if (!dragging) return
            trailAt(x, y, dx)
            if (dragYears) {
                page.snapTo(clamp(page.value - dx * yearsPerPixel(), 0f, PAGE_MAX))
            } else {
                travel.snapTo(
                    clamp(
                        travel.value - dx * monthsPerPixel(),
                        MIN_INDEX.toFloat(),
                        MAX_INDEX.toFloat(),
                    ),
                )
            }
            wake()
        }

        override fun onDragEnd(vx: Float, vy: Float) {
            if (panelDrag) {
                panelDrag = false
                dayPanel.fling(vy)
                wake()
                return
            }
            if (!dragging) return
            dragging = false
            if (motion.instant) {
                if (dragYears) {
                    page.snapTo(settlePage(page.value))
                    adoptPage()
                } else {
                    travel.snapTo(settle(travel.value))
                }
                wake()
                return
            }
            if (dragYears) {
                fling.min = 0f
                fling.max = PAGE_MAX
                fling.snapTo(page.value)
                fling.velocity = clamp(-vx * yearsPerPixel(), -PAGE_FLING_LIMIT, PAGE_FLING_LIMIT)
            } else {
                fling.min = MIN_INDEX.toFloat()
                fling.max = MAX_INDEX.toFloat()
                fling.snapTo(travel.value)
                fling.velocity = clamp(-vx * monthsPerPixel(), -FLING_LIMIT, FLING_LIMIT)
            }
            flingYears = dragYears
            flinging = true
            wake()
        }

        override fun onPinch(scale: Float, focusX: Float, focusY: Float) {
            if (!(scale > 0f)) return
            val was = level
            // The wall may have been scrolled away from the month the corridor is standing at.
            // The opening of a pinch is the one moment the two can be brought back together
            // unseen, because the world is either fully a wall or fully a corridor here — and
            // from here on the pinch writes only `distance`, so travel is frozen for its whole
            // duration and no slot can change the month it holds.
            if (!pinching) adoptPage()
            pinching = true
            dragging = false
            panelDrag = false
            // Pinching in on an open day is how the day is put back, so the world is reachable
            // again before the fingers have finished asking for it.
            if (panelOpen() && scale < 1f) closeDay()
            // The pinch is about the view's axis rather than the focus point: the world is a
            // corridor with one thing straight ahead, and zooming towards a corner of it would
            // only take the month being read off the screen.
            distance.snapTo(clamp(distance.value / scale, MIN_DISTANCE, MAX_DISTANCE))
            notifyLevel(was)
            wake()
        }

        override fun onPinchEnd() {
            if (!pinching) return
            pinching = false
            val was = level
            // Two places to stand, and a pinch lands at whichever it was nearer when it ended.
            distance.target =
                if (distance.value > LEVEL_SPLIT) MAX_DISTANCE else MIN_DISTANCE
            travel.target = settle(travel.target)
            if (motion.instant) {
                distance.snapTo(distance.target)
                travel.snapTo(travel.target)
            }
            // Here rather than inside onPinch: the level flips under the fingers the moment the
            // pinch crosses the split, and a burst there would fire again on every recrossing of
            // a gesture that is still making up its mind.
            sparkLevel()
            tick(HapticFeedbackConstants.CLOCK_TICK)
            notifyLevel(was)
            wake()
        }
    }

    // ---- helpers -------------------------------------------------------

    private fun locale(): Locale {
        val locales = resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }

    private fun withAlpha(colour: Int, alpha: Float): Int {
        // The colour's own alpha is scaled rather than replaced, so a theme colour that is
        // already a wash fades from what it is instead of to full.
        val source = (colour ushr 24) and 0xFF
        val scaled = (source / 255f * clamp(alpha, 0f, 1f) * 255f + 0.5f).toInt()
        return (scaled shl 24) or (colour and RGB_MASK)
    }

    private companion object {

        // The world runs from the epoch month to the end of 2100, which is the range every
        // other part of the app already agrees to.
        const val EPOCH_YEAR: Int = 1970
        const val MIN_INDEX: Int = 0
        val MAX_INDEX: Int = MonthModel.indexOf(YearMonth.of(2100, 12))

        /** The last year page, which is the year [MAX_INDEX] falls in. */
        val PAGE_MAX: Float = Math.floorDiv(MAX_INDEX, 12).toFloat()

        // A couple of years either side of the window, which is thirty-four months wide: far
        // enough that turning a page and turning back does not re-bake what it just dropped, and
        // the record is only a month and a reference apiece.
        const val BAKED_RECORDS: Int = 96
        const val BAKED_BUCKETS: Int = 128
        const val LOAD_FACTOR: Float = 0.75f
    }
}
