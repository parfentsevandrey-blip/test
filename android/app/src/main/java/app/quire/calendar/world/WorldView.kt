package app.quire.calendar.world

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

// ---- the world, in world units -----------------------------------------------------------

// A plate is one unit wide, and as tall as the picture MonthPlate bakes (512 x 640). Every
// other length here is a multiple of that, so the whole scene rescales from one number and the
// quad matrix — never the geometry — is what answers the size of the screen.
private const val PLATE_WIDTH = 1f
private const val PLATE_HEIGHT = 1.25f

// Twelve months are drawn at most, which is exactly what the year ring holds and exactly what
// MonthPlate's cache can hold: drawing a thirteenth in one frame would evict a plate this frame
// has already drawn. Five of them sit behind the focus, so a flick backwards has somewhere to
// come from.
private const val WINDOW = 12
private const val BEHIND = 5

// Preallocated quads. The window never uses more than [WINDOW]; the rest are headroom so the
// window can grow without the per-frame path ever allocating a quad.
private const val SLOTS = 16

private const val TAU = 6.2831855f

// The ring: twelve plates round a circle, spaced so a plate's width leaves a gap of about half
// its own width between neighbours.
private const val RING_STEP = TAU / 12f
private const val RING_RADIUS = 2.85f
private const val RING_MARGIN = 0.18f

// Plates on the ring turn most, but not all, of the way to tangent. Fully tangent hides
// everything past a quarter turn behind its own edge; holding a little back keeps seven or eight
// months readable at once, which is what makes the ring worth standing back for.
private const val RING_FACE = 0.78f

// How far above the plates the eye rises as the ring forms, as a fraction of its radius.
private const val RING_ELEVATION = 0.30f

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
private const val FOV_DEEP = 42f
private const val FOV_FLAT = 30f
private const val CAM_NEAR = 0.05f

// Travel is continuous months; distance is 1 (one plate fills the view) to 12 (the year ring).
private const val MIN_DISTANCE = 1f
private const val MAX_DISTANCE = 12f

// Where the pinch stops being "a month" and starts being "the year", for the level the Hud
// lights and the back gesture steps out of.
private const val LEVEL_SPLIT = 6.5f

// The ring forms a little after the camera starts moving back, so a small pinch is travel along
// the corridor rather than an immediate change of shape.
private const val RING_FORM_START = 0.06f
private const val RING_FORM_END = 0.88f

// How far the world is pushed back and dimmed under an open day.
private const val OPEN_PUSH = 0.07f
private const val WORLD_DIM = 0.30f

// How far the eye slides with the hand, in world units at full tilt.
private const val TILT_REACH = 0.024f

// ---- gestures -----------------------------------------------------------------------------

// One screen of drag is one month at the corridor, and rather more once the ring is turning
// under the finger — a ring twelve plates round would otherwise take twelve screens to walk.
private const val MONTHS_PER_SCREEN_NEAR = 1.05f
private const val MONTHS_PER_SCREEN_RING = 3.6f

private const val FLING_FRICTION = 5.2f
private const val FLING_LIMIT = 30f

// Below this the fling has stopped being a throw and is only creeping, so the spring takes it
// the rest of the way and lands it on a whole month.
private const val HAND_OVER = 0.7f
private const val PROJECT_SECONDS = 0.11f

// How far a drag from the top of an open day has to travel downwards before it closes it.
private const val CLOSE_TRAVEL_DP = 96f

private const val SPARKS = 14
private const val SPARK_SPEED_DP = 150f

// ---- background ---------------------------------------------------------------------------

private const val DRIFT_PER_SECOND = 0.012f
private const val POOL_STOPS = 3
private const val POOL_RADIUS = 0.92f
private const val POOL_A_ALPHA = 0.55f
private const val POOL_B_ALPHA = 0.45f
private const val POOL_MID_ALPHA = 0.24f
private const val BACKGROUND_GRAIN = 0.55f

private const val MIN_ALPHA = 1f / 255f
private const val RGB_MASK = 0x00FFFFFF

/**
 * The calendar as a place rather than a page: months are plates standing in a corridor that
 * recedes from the viewer, the camera travels along it, and pulling back with two fingers stands
 * the year up as a ring of twelve plates to orbit. Choosing a day lifts its tile out of the plate
 * and opens it into the [DayPanel].
 *
 * One continuous coordinate carries the whole thing. `travel` is a fractional month index — the
 * camera's station along the corridor and its angle around the ring are the same number — and
 * `distance` is how far back the eye stands, 1 for a month filling the view and 12 for the year.
 * Nothing here switches between screens; every level is the same twelve quads placed by the same
 * function with one blend factor between two layouts, so a pinch interrupted halfway is a real
 * position rather than an animation caught between two states.
 *
 * Depth is in service of legibility, not the other way round: the focused plate is square to the
 * camera at level 1, the lens is long, and the tilt parallax translates the eye rather than
 * rotating it, so a plate parallel to the screen stays a rectangle.
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
    private val particles = Particles()
    private val gestures = GestureEngine(displayDensity)

    // ---- motion --------------------------------------------------------

    /** The continuous month index the camera is standing at; whole numbers are months. */
    private val travel = Spring()

    /** How far back the eye stands, in the 1..12 the pinch speaks. */
    private val distance = Spring(MIN_DISTANCE)

    /** 0 the day is still a tile in its plate, 1 the panel is open. */
    private val openness = Spring()

    /** The throw a released drag becomes, before travel settles on a whole month. */
    private val fling = Decay()

    /** Where the phone is being held, smoothed so the parallax glides rather than jitters. */
    private val tiltX = Spring()
    private val tiltY = Spring()

    private var flinging = false
    private var dragging = false
    private var pinching = false
    private var panelDrag = false
    private var closeTravel = 0f
    private var downInBar = false

    // ---- per-frame scratch, never allocated in draw ---------------------

    // Filtering is asked for here as well as on the shader: Shaders' stand-in is a few dozen
    // pixels stretched across the whole screen, and BitmapShader.setFilterMode only exists from
    // API 33, so below that the paint is the only thing that can smooth it.
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val poolMatrix = Matrix()
    private val poolStops = FloatArray(POOL_STOPS)
    private val poolAColours = IntArray(POOL_STOPS)
    private val poolBColours = IntArray(POOL_STOPS)
    private var poolA: RadialGradient? = null
    private var poolB: RadialGradient? = null
    private var backgroundA = 0
    private var backgroundB = 0

    private val tileMatrix = Matrix()
    private val tileUv = RectF()
    private val tileScreen = RectF()
    private val panelBounds = RectF()
    private val hitUv = FloatArray(2)

    // ---- geometry, worked out when the surface or the metrics move ------

    private var nearZ = 1f
    private var ringZ = 1f
    private var camFar = 100f
    private var spacing = 1f
    private var bandOffset = 0f
    private var safeTop = 0f
    private var safeBottom = 0f
    private var phase = 0f

    private var titleIndex = Int.MIN_VALUE
    private var titleText = ""
    private var subtitleText = ""

    private var selectedMonth: YearMonth = YearMonth.now()
    private var selectedIndex = 0

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
            buildPools()
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
            distance.profile(value)
            openness.profile(value)
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
     * Where the world is standing: 0 the year ring, 1 one month, 2 the open day.
     *
     * Read from the targets rather than the values, so it names where the world is going the
     * moment a gesture decides it — a pinch writes its target continuously, so the level flips
     * under the fingers exactly as the ring takes over.
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
        fling.friction = FLING_FRICTION
        fling.min = MIN_INDEX.toFloat()
        fling.max = MAX_INDEX.toFloat()
        tiltX.profile(MotionProfile.CALM)
        tiltY.profile(MotionProfile.CALM)
        buildPools()
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
        if (w <= 1f || h <= 1f) return

        val bar = hud.barHeight
        val gutter = metrics.gutter
        val freeWidth = max(w - 2f * gutter, gutter)
        val freeHeight = max(h - safeTop - bar - 2f * gutter, gutter)

        val tanY = tan(fovDegrees() * DEGREES * 0.5f)
        val tanX = tanY * (w / h)

        // Far enough back that the plate fits the clear band between the title and the strip,
        // measured on both axes and answering to whichever runs out first — a portrait plate on
        // a portrait screen is usually stopped by the width.
        nearZ = max(
            PLATE_HEIGHT * h / (2f * tanY * freeHeight),
            PLATE_WIDTH * w / (2f * tanX * freeWidth),
        )
        // And far enough back that the whole ring fits across that band. The ring's widest
        // point is its own centre plane, which sits one radius beyond the nearest plate.
        val reach = RING_RADIUS + PLATE_WIDTH * 0.5f + RING_MARGIN
        ringZ = max(reach * w / (tanX * freeWidth) - RING_RADIUS, nearZ)
        camFar = ringZ + 6f * RING_RADIUS + 8f
        spacing = nearZ * CORRIDOR_SPACING

        // The clear band is not centred on the screen, so the eye is offset until the band's
        // middle is where the plate lands. Both eye and target move together, which shifts the
        // picture without shearing it.
        bandOffset = (safeTop + h - bar) * 0.5f - h * 0.5f

        panelBounds.set(gutter, safeTop + gutter, w - gutter, h - bar - gutter)
        dayPanel.setBounds(panelBounds)
    }

    private fun configureChildren() {
        plate.configure(theme, metrics, firstDayOfWeek, today, colouredMarks, density)
        dayPanel.configure(theme, metrics)
        hud.configure(theme, metrics, motion)
        hud.setSafeInsets(safeTop, safeBottom)
    }

    private fun fovDegrees(): Float = if (depth) FOV_DEEP else FOV_FLAT

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
        if (!dragging && !pinching && stepTravel(dt)) moving = true
        if (!pinching && distance.advance(dt)) moving = true
        if (openness.advance(dt)) moving = true
        if (tiltX.advance(dt)) moving = true
        if (tiltY.advance(dt)) moving = true
        if (dayPanel.advance(dt)) moving = true
        if (hud.advance(dt)) moving = true
        if (particles.advance(dt)) moving = true
        // The background drifts with the frames it is given rather than asking for any: a world
        // standing still should stand still, not breathe at sixty frames a second forever.
        phase += dt * DRIFT_PER_SECOND
        // Travel has just moved, so the window may have to follow it. This runs in the
        // Choreographer's animation callback, ahead of the traversal that draws, which is what
        // keeps a recycled picture out of a display list.
        refreshWindow(force = false)
        invalidate()
        return moving
    }

    /**
     * Travel is either a throw or a spring, never both. The throw runs until it is only
     * creeping, and then hands its remaining speed to the spring along with a target one whole
     * month away — so a flick lands on a month rather than stopping between two.
     */
    private fun stepTravel(dt: Float): Boolean {
        if (!flinging) return travel.advance(dt)
        val running = fling.advance(dt)
        travel.value = fling.value
        travel.target = fling.value
        travel.velocity = 0f
        if (running && abs(fling.velocity) > HAND_OVER) return true
        flinging = false
        travel.velocity = fling.velocity
        travel.target = settle(fling.value + fling.velocity * PROJECT_SECONDS)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        refreshTitle()
        drawBackground(canvas, w, h)
        aimCamera(w, h)
        projectPlates()
        refreshOrigin()
        drawPlates(canvas)
        particles.draw(canvas)
        dayPanel.draw(canvas, openness.value)
        hud.draw(canvas, titleText, subtitleText, level, 1f)
    }

    // ---- background ----------------------------------------------------

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        // A RuntimeShader is a GPU program: handing one to a software canvas throws rather than
        // degrading, and this view is drawn into a plain Bitmap more often than it looks — a
        // screenshot, a print, a render test, or simply a window that lost its hardware layer.
        // The support flag answers "does this Android build have AGSL", which is not the same
        // question as "can this canvas run it", so both have to be asked.
        if (Shaders.supported && canvas.isHardwareAccelerated) {
            val shader = Shaders.background(
                width = w,
                height = h,
                colourA = backgroundA,
                colourB = backgroundB,
                base = theme.canvas,
                t = phase,
                grain = BACKGROUND_GRAIN,
            )
            if (shader != null) {
                backgroundPaint.shader = shader
                canvas.drawRect(0f, 0f, w, h, backgroundPaint)
                backgroundPaint.shader = null
                return
            }
        }
        // No runtime shaders: the same picture by hand, two soft pools of the theme's aurora
        // hues laid over its canvas. Both gradients are built at unit radius once per palette
        // and only placed by a matrix here, so a drifting background allocates nothing.
        canvas.drawColor(theme.canvas)
        val angle = phase * TAU
        val span = max(w, h) * POOL_RADIUS
        drawPool(
            canvas = canvas,
            gradient = poolA,
            cx = w * (0.30f + 0.14f * cos(angle)),
            cy = h * (0.32f + 0.10f * sin(angle * 1.3f + 0.7f)),
            radius = span,
            w = w,
            h = h,
        )
        drawPool(
            canvas = canvas,
            gradient = poolB,
            cx = w * (0.72f + 0.12f * cos(angle * 0.8f + 2.1f)),
            cy = h * (0.70f + 0.13f * sin(angle * 1.1f)),
            radius = span,
            w = w,
            h = h,
        )
    }

    private fun drawPool(
        canvas: Canvas,
        gradient: RadialGradient?,
        cx: Float,
        cy: Float,
        radius: Float,
        w: Float,
        h: Float,
    ) {
        if (gradient == null) return
        poolMatrix.setScale(radius, radius)
        poolMatrix.postTranslate(cx, cy)
        gradient.setLocalMatrix(poolMatrix)
        backgroundPaint.shader = gradient
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)
        backgroundPaint.shader = null
    }

    private fun buildPools() {
        backgroundA = withAlpha(theme.auroraA, POOL_A_ALPHA)
        backgroundB = withAlpha(theme.auroraB, POOL_B_ALPHA)
        poolStops[0] = 0f
        poolStops[1] = 0.55f
        poolStops[2] = 1f
        poolAColours[0] = backgroundA
        poolAColours[1] = withAlpha(theme.auroraA, POOL_MID_ALPHA)
        poolAColours[2] = withAlpha(theme.auroraA, 0f)
        poolBColours[0] = backgroundB
        poolBColours[1] = withAlpha(theme.auroraB, POOL_MID_ALPHA)
        poolBColours[2] = withAlpha(theme.auroraB, 0f)
        poolA = RadialGradient(0f, 0f, 1f, poolAColours, poolStops, Shader.TileMode.CLAMP)
        poolB = RadialGradient(0f, 0f, 1f, poolBColours, poolStops, Shader.TileMode.CLAMP)
    }

    // ---- the scene -----------------------------------------------------

    private fun zoomFraction(): Float =
        clamp((distance.value - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE), 0f, 1f)

    private fun ringness(): Float = smoothstep(RING_FORM_START, RING_FORM_END, zoomFraction())

    private fun aimCamera(w: Float, h: Float) {
        val zoom = zoomFraction()
        val ring = ringness()
        val open = clamp(openness.value, 0f, 1f)
        val camZ = lerp(nearZ, ringZ, zoom) * (1f + OPEN_PUSH * open)
        val tanY = tan(fovDegrees() * DEGREES * 0.5f)

        // The clear band's offset is in pixels; at the focused plate's depth this is what it is
        // worth in world units.
        val shift = bandOffset * (2f * camZ * tanY / h)
        val slideX = if (depth) tiltX.value * TILT_REACH else 0f
        val slideY = if (depth) -tiltY.value * TILT_REACH else 0f

        camera.fovDegrees = fovDegrees()
        camera.near = CAM_NEAR
        camera.far = camFar
        camera.position.set(
            slideX,
            shift + slideY + RING_ELEVATION * RING_RADIUS * ring,
            camZ,
        )
        camera.target.set(slideX, shift + slideY, -RING_RADIUS * ring)
        camera.update(w, h)
    }

    /**
     * Places and projects every plate in the window, and works out how present each one is.
     *
     * All twelve are projected whether or not they will be drawn, because the same corners
     * answer a tap and locate the tile an opening day grows out of.
     */
    private fun projectPlates() {
        val ring = ringness()
        val zoom = zoomFraction()
        val camZ = lerp(nearZ, ringZ, zoom)
        val first = windowBase - BEHIND
        drawnCount = 0
        var slot = 0
        while (slot < WINDOW) {
            val d = (first + slot) - travel.value
            val quad = quads[slot]
            place(quad, d, ring, camZ)
            quad.project(camera)
            // In the corridor the camera travels through the months it has read and towards the
            // ones it has not; on the ring nothing is passed, so neither fade applies there.
            val corridor = smoothstep(NEAR_FADE_START, NEAR_FADE_END, d) *
                (1f - smoothstep(FAR_FADE_START, FAR_FADE_END, d))
            val alpha = lerp(corridor, 1f, ring)
            alphas[slot] = alpha
            if (alpha > MIN_ALPHA && quad.visible) {
                order[drawnCount] = slot
                drawnCount++
            }
            slot++
        }
        sortBackToFront()
    }

    private fun place(quad: Quad3D, d: Float, ring: Float, camZ: Float) {
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
        val cyaw = CORRIDOR_FACE * atan2(-cx, max(camZ - cz, MIN_FACE_DEPTH))

        // The ring: the same months round a circle whose nearest point is where the focused
        // plate already stands, so the two layouts agree at d = 0 and the morph has nothing to
        // travel through.
        val a = d * RING_STEP
        val rx = RING_RADIUS * sin(a)
        val rz = RING_RADIUS * (cos(a) - 1f)

        quad.position.set(lerp(cx, rx, ring), 0f, lerp(cz, rz, ring))
        quad.yaw = lerp(cyaw, a * RING_FACE, ring)
        quad.pitch = 0f
        quad.roll = 0f
    }

    // Painter's algorithm: furthest first. An insertion sort over twelve nearly-sorted entries
    // is a handful of comparisons and, unlike a comparator, allocates nothing.
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

    private fun drawPlates(canvas: Canvas) {
        val dim = 1f - WORLD_DIM * clamp(openness.value, 0f, 1f)
        var i = 0
        while (i < drawnCount) {
            val slot = order[i]
            val month = months[slot]
            plate.draw(
                canvas = canvas,
                quad = quads[slot],
                month = month,
                // The chosen day is marked on the month that owns it. A neighbouring plate
                // holds the same date in its trailing cells, and ringing it three times over
                // would say the world had three selections.
                selected = if (month == selectedMonth) selected else null,
                loads = loads[slot],
                alpha = alphas[slot] * dim,
            )
            i++
        }
    }

    // ---- the window of months ------------------------------------------

    /**
     * Keeps the twelve months around the focus, and their loads, in step with travel. The
     * window usually moves by one, so the overlap is shifted rather than rebuilt: a fling would
     * otherwise ask the host for twelve months on every frame it crossed a month boundary.
     */
    private fun refreshWindow(force: Boolean) {
        val base = travelIndex()
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
        val month = MonthModel.monthAt(windowBase - BEHIND + slot)
        val next = data?.loads(month) ?: emptyMap()
        months[slot] = month
        loads[slot] = next
        // Identity rather than equality: the host hands back the same map until the numbers
        // behind it actually change, and walking 42 days of entries every time the window moved
        // would cost more than the picture it is protecting.
        if (baked.put(month, next) !== next) plate.invalidate(month)
    }

    private fun refreshTitle() {
        val index = travelIndex()
        if (index == titleIndex) return
        titleIndex = index
        val month = MonthModel.monthAt(index)
        titleText = MonthModel.monthName(month, locale())
        subtitleText = month.year.toString()
    }

    private fun travelIndex(): Int = travel.value.roundToInt()

    private fun slotOf(index: Int): Int {
        val slot = index - (windowBase - BEHIND)
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
            // Nothing on screen to grow from — a day reached from the year ring, or before the
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
            // pointed at comes to the front, which is what makes the year ring navigable.
            face(month)
            val cell = plate.cellAt(hitUv[0], hitUv[1])
            if (cell < 0) {
                wake()
                return null
            }
            val date = dateAt(month, cell)
            select(date, notify = true, haptic = true)
            if (tileRect(slot, cell, tileScreen)) {
                particles.burst(
                    x = tileScreen.centerX(),
                    y = tileScreen.centerY(),
                    count = SPARKS,
                    colour = theme.accent,
                    speed = metrics.dp(SPARK_SPEED_DP),
                )
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

    /** Brings a month to the front of the corridor, or round to the front of the ring. */
    private fun face(month: YearMonth) {
        val index = MonthModel.indexOf(month).toFloat()
        flinging = false
        travel.target = clamp(index, MIN_INDEX.toFloat(), MAX_INDEX.toFloat())
        if (motion.instant) travel.snapTo(travel.target)
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
        if (!animate || motion.instant) {
            distance.snapTo(distance.target)
            travel.snapTo(travel.target)
            openness.snapTo(openness.target)
        }
    }

    private fun notifyLevel(previous: Int) {
        if (level != previous) onLevelChanged?.invoke()
    }

    private fun settle(raw: Float): Float =
        clamp(raw.roundToInt().toFloat(), MIN_INDEX.toFloat(), MAX_INDEX.toFloat())

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

    private fun monthsPerPixel(): Float {
        val across = lerp(MONTHS_PER_SCREEN_NEAR, MONTHS_PER_SCREEN_RING, ringness())
        return across / max(width.toFloat(), 1f)
    }

    private fun panelOpen(): Boolean = openness.target > 0.5f

    /**
     * Everything the finger can say. Order matters: the strip along the bottom is asked first,
     * then the open day, and only then the world behind them.
     */
    private inner class Touch : GestureEngine.Listener {

        override fun onDown(x: Float, y: Float) {
            flinging = false
            travel.velocity = 0f
            downInBar = hud.actionAt(x, y) != 0
            panelDrag = panelOpen() && !downInBar
            closeTravel = 0f
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
            travel.snapTo(
                clamp(
                    travel.value - dx * monthsPerPixel(),
                    MIN_INDEX.toFloat(),
                    MAX_INDEX.toFloat(),
                ),
            )
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
                travel.snapTo(settle(travel.value))
                wake()
                return
            }
            fling.snapTo(travel.value)
            fling.velocity = clamp(-vx * monthsPerPixel(), -FLING_LIMIT, FLING_LIMIT)
            flinging = true
            wake()
        }

        override fun onPinch(scale: Float, focusX: Float, focusY: Float) {
            if (!(scale > 0f)) return
            val was = level
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
        const val MIN_INDEX: Int = 0
        val MAX_INDEX: Int = MonthModel.indexOf(YearMonth.of(2100, 12))

        // Two years either side of the window is as far as a fling reaches before the host has
        // reloaded anyway, and the record is only a month and a reference apiece.
        const val BAKED_RECORDS: Int = 48
        const val BAKED_BUCKETS: Int = 64
        const val LOAD_FACTOR: Float = 0.75f
    }
}
