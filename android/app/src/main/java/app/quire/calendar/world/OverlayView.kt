package app.quire.calendar.world

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.CalendarSource
import app.quire.engine.anim.Clock
import app.quire.engine.anim.MotionProfile
import app.quire.engine.anim.Spring
import app.quire.engine.design.Metrics
import app.quire.engine.design.Theme
import app.quire.engine.input.GestureEngine

/**
 * The two sheets that come over the world — settings from the bottom, search from the top — and
 * nothing else. They live on their own surface above [WorldView] so that neither one has to be
 * threaded through the world's own draw order, and so that a touch that lands on neither of them
 * falls straight through to the calendar underneath.
 *
 * This is still one screen: nothing here is an Activity, a Dialog or a Fragment, and no window
 * is created to show it.
 */
class OverlayView(context: Context) : View(context), GestureEngine.Listener {

    /** Which sheet is up; the two are mutually exclusive by design. */
    enum class Sheet { NONE, SETTINGS, SEARCH }

    private val settings = SettingsPanel(context)
    private val search = SearchPanel(context)
    private val notice = Notice()

    private val gestures = GestureEngine(resources.displayMetrics.density)

    // One spring per sheet rather than one shared one: closing settings and opening search in the
    // same frame has to read as two movements, not as a single value passing through zero.
    private val settingsOpen = Spring(0f)
    private val searchOpen = Spring(0f)

    // The card is not a sheet: it can be up while the world is being used, so it has its own
    // travel and does not take part in the one-sheet-at-a-time rule.
    private val noticeOpen = Spring(0f)

    private val bounds = RectF()

    private var safeTop = 0f

    private var sheet = Sheet.NONE
    private var running = false
    private var draggingSheet = Sheet.NONE

    // A drag that started on a slider belongs to that slider until the finger lifts; without
    // this, sliding one left would also scroll the sheet it lives on.
    private var draggingSlider = false

    private val frame: (Float) -> Boolean = { dt -> advance(dt) }

    /** Called whenever the settings sheet emits a new state, so the host can store it. */
    var onSettingsChanged: ((SettingsPanel.State) -> Unit)? = null

    /** Called when the search field's text changes, so the host can run the query off-thread. */
    var onQueryChanged: ((String) -> Unit)? = null

    /** Called when a search result is chosen, with the entry that was picked. */
    var onResultChosen: ((AgendaEntry) -> Unit)? = null

    /** Called once a sheet has been asked to close, so the host can unrecede the world. */
    var onSheetClosed: (() -> Unit)? = null

    /** Called when the notice card's one action is taken. */
    var onNoticeAction: (() -> Unit)? = null

    /** Called when the notice card is waved away rather than acted on. */
    var onNoticeDismissed: (() -> Unit)? = null

    init {
        isFocusableInTouchMode = true
        gestures.listener = this
        gestures.attach(this)
        settings.onChange = { state -> onSettingsChanged?.invoke(state) }
        settings.onDismiss = { dismiss() }
        search.onQueryChanged = { text -> onQueryChanged?.invoke(text) }
        search.onResultChosen = { entry -> onResultChosen?.invoke(entry) }
        search.onDismiss = { dismiss() }
        notice.onAction = {
            hideNotice()
            onNoticeAction?.invoke()
        }
        notice.onDismiss = {
            hideNotice()
            onNoticeDismissed?.invoke()
        }
    }

    /** Which sheet is currently up, for the host's back handling. */
    val showing: Sheet get() = sheet

    /** Repaints both sheets in a new palette; safe to call while one of them is open. */
    fun configure(theme: Theme, metrics: Metrics, motion: MotionProfile) {
        settings.configure(theme, metrics, motion)
        search.configure(theme, metrics, motion)
        notice.configure(theme, metrics, motion)
        settingsOpen.profile(motion)
        searchOpen.profile(motion)
        noticeOpen.profile(motion)
        invalidate()
    }

    /** How far the system's own furniture reaches, so the card hangs below the status bar. */
    fun setSafeInsets(top: Float, bottom: Float) {
        safeTop = top
        invalidate()
    }

    /** Puts the explaining card up over the world, with the one action that resolves it. */
    fun presentNotice(headline: String, body: CharSequence, action: String) {
        notice.show(headline, body, action)
        noticeOpen.target = 1f
        wake()
    }

    /** Sends the card back off the top edge. */
    fun hideNotice() {
        noticeOpen.target = 0f
        notice.hide()
        wake()
    }

    /** Whether the card is currently asking for something. */
    val noticeShowing: Boolean get() = notice.visible

    /** The calendars the settings sheet offers to hide. */
    fun setCalendars(sources: List<CalendarSource>) = settings.setCalendars(sources)

    /** The version string the about row prints. */
    fun setVersion(name: String) = settings.setVersion(name)

    /** Hands search results back once the provider has answered. */
    fun setResults(forQuery: String, results: List<AgendaEntry>) =
        search.setResults(forQuery, results)

    /** Raises the settings sheet, seeded with the values the host is holding. */
    fun presentSettings(state: SettingsPanel.State) {
        search.hide()
        searchOpen.target = 0f
        settings.show(state)
        sheet = Sheet.SETTINGS
        settingsOpen.target = 1f
        hideIme()
        wake()
    }

    /** Lowers the search sheet and puts the caret in its field. */
    fun presentSearch() {
        settings.hide()
        settingsOpen.target = 0f
        search.show()
        sheet = Sheet.SEARCH
        searchOpen.target = 1f
        requestFocus()
        showIme()
        wake()
    }

    /** Sends whichever sheet is up back off the screen. Returns false when there was none. */
    fun dismiss(): Boolean {
        if (sheet == Sheet.NONE) return false
        settingsOpen.target = 0f
        searchOpen.target = 0f
        settings.hide()
        search.hide()
        sheet = Sheet.NONE
        draggingSheet = Sheet.NONE
        gestures.cancel()
        hideIme()
        onSheetClosed?.invoke()
        wake()
        return true
    }

    // ---- frame ---------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!settingsOpen.atRest || !searchOpen.atRest || sheet != Sheet.NONE) wake()
    }

    override fun onDetachedFromWindow() {
        Clock.unsubscribe(frame)
        running = false
        super.onDetachedFromWindow()
    }

    private fun wake() {
        if (running || !isAttachedToWindow) {
            invalidate()
            return
        }
        running = true
        Clock.subscribe(frame)
        invalidate()
    }

    private fun advance(dt: Float): Boolean {
        var alive = false
        if (!settingsOpen.atRest) alive = settingsOpen.advance(dt) || alive
        if (!searchOpen.atRest) alive = searchOpen.advance(dt) || alive
        if (!noticeOpen.atRest) alive = noticeOpen.advance(dt) || alive
        if (settingsOpen.value > 0.001f) alive = settings.advance(dt) || alive
        if (searchOpen.value > 0.001f) alive = search.advance(dt) || alive
        if (noticeOpen.value > 0.001f) alive = notice.advance(dt) || alive
        invalidate()
        // Everything is gone and nothing is moving: stop taking frames rather than idling at 60.
        if (!alive && sheet == Sheet.NONE && !notice.visible) {
            running = false
            return false
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        // The card sits under the sheets: a sheet is a place you went, the card is a note left on
        // the world, and a note does not cover the thing you just opened.
        if (noticeOpen.value > 0.001f) {
            notice.setBounds(bounds, safeTop)
            notice.draw(canvas, noticeOpen.value)
        }
        if (settingsOpen.value > 0.001f) {
            settings.setBounds(bounds)
            settings.draw(canvas, settingsOpen.value)
        }
        if (searchOpen.value > 0.001f) {
            search.setBounds(bounds)
            search.draw(canvas, searchOpen.value)
        }
    }

    // ---- touch ---------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Nothing is up, so this surface is not there at all and the world below gets the touch.
        if (sheet == Sheet.NONE && settingsOpen.value <= 0.001f && searchOpen.value <= 0.001f) {
            // A card on its own does not claim the screen: only the strokes that start on it are
            // ours, and the calendar behind it stays usable while it is being ignored.
            if (!notice.hit(event.x, event.y)) return false
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                notice.onTap(event.x, event.y)
                wake()
            }
            return true
        }
        return gestures.onTouch(event)
    }

    override fun onDown(x: Float, y: Float) {
        draggingSheet = sheet
        draggingSlider = false
    }

    override fun onDragStart(x: Float, y: Float) {
        // Offered to the sheet first: a slider claims the stroke, and anything else lets it fall
        // through to scrolling.
        draggingSlider = draggingSheet == Sheet.SETTINGS && settings.onDragStart(x, y)
        wake()
    }

    override fun onTap(x: Float, y: Float) {
        val consumed = when (sheet) {
            Sheet.SETTINGS -> settings.onTap(x, y)
            Sheet.SEARCH -> search.onTap(x, y)
            Sheet.NONE -> false
        }
        // A tap on the world showing past the sheet means "put this away", the same as back.
        if (!consumed) dismiss()
        wake()
    }

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        if (draggingSlider) {
            settings.onDrag(x, y)
            wake()
            return
        }
        when (draggingSheet) {
            Sheet.SETTINGS -> settings.scrollBy(dy)
            Sheet.SEARCH -> search.scrollBy(dy)
            Sheet.NONE -> Unit
        }
        wake()
    }

    override fun onDragEnd(vx: Float, vy: Float) {
        if (draggingSlider) {
            settings.onDragEnd(vx)
            draggingSlider = false
            draggingSheet = Sheet.NONE
            wake()
            return
        }
        when (draggingSheet) {
            // Settings comes up from the bottom, so throwing it back down is how it is put away;
            // the panel reports whether its own scroll is already at the top, because a list part
            // way down has to finish scrolling before the sheet itself starts moving.
            Sheet.SETTINGS ->
                if (settings.scrollAtTop && vy > FLING_TO_DISMISS) dismiss() else settings.fling(vy)
            // Search hangs from the top edge, so the same downward throw would push it further
            // in rather than away. It leaves on a tap outside it, or on back.
            Sheet.SEARCH -> search.fling(vy)
            Sheet.NONE -> Unit
        }
        draggingSheet = Sheet.NONE
        wake()
    }

    // ---- text ----------------------------------------------------------

    override fun onCheckIsTextEditor(): Boolean = sheet == Sheet.SEARCH

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (sheet != Sheet.SEARCH) return null
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_FULLSCREEN
        // Not a full editor: the IME then reports plain key events, which is all the drawn field
        // needs. A drawn field has no Editable for the IME to commit into.
        return BaseInputConnection(this, false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (sheet != Sheet.SEARCH) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                search.backspace()
                wake()
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                hideIme()
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> return dismiss()
        }
        val typed = event.unicodeChar
        if (typed != 0) {
            search.insert(typed.toChar().toString())
            wake()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // Deprecated with nothing to replace it: a soft keyboard that commits several characters at
    // once — a suggestion, a pasted word, an emoji — still reports them exactly this way to a
    // view that is not a full editor, and a drawn field has no Editable for the IME to fill.
    @Suppress("DEPRECATION")
    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        if (sheet == Sheet.SEARCH) {
            val chars = event.characters
            if (!chars.isNullOrEmpty()) {
                search.insert(chars)
                wake()
                return true
            }
        }
        return super.onKeyMultiple(keyCode, repeatCount, event)
    }

    private fun showIme() {
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return
        post { manager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideIme() {
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return
        manager.hideSoftInputFromWindow(windowToken, 0)
    }

    private companion object {
        /** Pixels per second downward, past which a drag is a dismissal rather than a scroll. */
        const val FLING_TO_DISMISS = 900f
    }
}
