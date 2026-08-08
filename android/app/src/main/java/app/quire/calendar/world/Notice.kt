package app.quire.calendar.world

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.quire.engine.anim.MotionProfile
import app.quire.engine.anim.Spring
import app.quire.engine.anim.lerp
import app.quire.engine.design.Metrics
import app.quire.engine.design.Theme

/**
 * One card that explains something the world cannot show by itself and offers the single action
 * that fixes it — today that is the calendar permission, which is the only thing this app asks
 * for. It is drawn, like everything else here, and it hangs from the top edge so it never covers
 * the row of controls at the bottom.
 */
class Notice {

    private val backing = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val actionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val cross = Path()

    private val card = RectF()
    private val actionRect = RectF()
    private val closeRect = RectF()
    private val bounds = RectF()

    private val press = Spring(0f)
    private val closePress = Spring(0f)

    private var theme: Theme? = null
    private var metrics: Metrics? = null
    private var motion: MotionProfile = MotionProfile.STANDARD

    private var headline: String = ""
    private var action: String = ""
    private var body: CharSequence = ""
    private var bodyLayout: StaticLayout? = null
    private var laidOutFor = -1f

    private var safeTop = 0f

    // Where the last frame actually drew the card. The entrance slides it down from under the
    // top edge, so hit testing against the settled rectangle would aim at a card that is not
    // there yet. Seeded to 1 by show(), because the first tap can land before the first frame.
    private var shownOpenness = 0f

    /** Whether the card is asking for anything at the moment. */
    var visible: Boolean = false
        private set

    /** Run when the one action is tapped. */
    var onAction: (() -> Unit)? = null

    /** Run when the card is dismissed without acting on it. */
    var onDismiss: (() -> Unit)? = null

    /** Adopts a palette; safe to call while the card is on screen. */
    fun configure(theme: Theme, metrics: Metrics, motion: MotionProfile) {
        this.theme = theme
        this.metrics = metrics
        this.motion = motion
        press.profile(motion)
        closePress.profile(motion)
        headlinePaint.textSize = metrics.sp(17f)
        headlinePaint.letterSpacing = -0.015f
        headlinePaint.isFakeBoldText = true
        bodyPaint.textSize = metrics.sp(14f)
        actionPaint.textSize = metrics.sp(14f)
        actionPaint.letterSpacing = 0.04f
        actionPaint.isFakeBoldText = true
        rule.strokeWidth = metrics.hairline
        // The wrap depends on the type size, so a size change has to rebuild the paragraph.
        laidOutFor = -1f
    }

    /** Where the card may draw, and how far down the system's own furniture reaches. */
    fun setBounds(full: RectF, safeTop: Float) {
        if (bounds != full || this.safeTop != safeTop) laidOutFor = -1f
        bounds.set(full)
        this.safeTop = safeTop
    }

    /** Puts the card up with its one action. */
    fun show(headline: String, body: CharSequence, action: String) {
        this.headline = headline
        this.body = body
        this.action = action
        laidOutFor = -1f
        shownOpenness = 1f
        visible = true
    }

    /** Takes the card away; the host springs [draw]'s openness down to zero behind it. */
    fun hide() {
        visible = false
        press.snapTo(0f)
        closePress.snapTo(0f)
    }

    /** Advances the two press springs; false once nothing is moving. */
    fun advance(dt: Float): Boolean {
        var alive = press.advance(dt)
        alive = closePress.advance(dt) || alive
        return alive
    }

    /**
     * Draws the card at [openness]: 0 is off the top edge and fully transparent, 1 is settled in
     * place. The host owns that number so the card enters on the same spring as everything else.
     */
    fun draw(canvas: Canvas, openness: Float) {
        val theme = this.theme ?: return
        val metrics = this.metrics ?: return
        if (openness <= 0.001f) return
        layout(metrics)
        val layout = bodyLayout ?: return

        shownOpenness = openness.coerceIn(0f, 1f)
        val alpha = (shownOpenness * 255f).toInt()
        // The card arrives by sliding out from under the top edge rather than fading in place:
        // a thing that explains itself should look like it came from somewhere.
        val slide = slideAt(shownOpenness)
        val saved = canvas.save()
        canvas.translate(0f, slide)

        backing.color = theme.surfaceLifted
        backing.alpha = alpha
        canvas.drawRoundRect(card, metrics.radiusLarge, metrics.radiusLarge, backing)
        rule.color = theme.hairline
        rule.alpha = alpha
        canvas.drawRoundRect(card, metrics.radiusLarge, metrics.radiusLarge, rule)

        headlinePaint.color = theme.ink
        headlinePaint.alpha = alpha
        canvas.drawText(
            headline,
            card.left + metrics.gutter,
            card.top + metrics.gutter + headlinePaint.textSize,
            headlinePaint,
        )

        bodyPaint.color = theme.inkMuted
        bodyPaint.alpha = alpha
        val bodySaved = canvas.save()
        canvas.translate(
            card.left + metrics.gutter,
            card.top + metrics.gutter + headlinePaint.textSize * 1.7f,
        )
        layout.draw(canvas)
        canvas.restoreToCount(bodySaved)

        // The action dips under the finger and springs back, so a tap is felt before it is obeyed.
        val dip = 1f - PRESS_DIP * press.value
        val actionSaved = canvas.save()
        canvas.scale(dip, dip, actionRect.centerX(), actionRect.centerY())
        backing.color = theme.accent
        backing.alpha = alpha
        canvas.drawRoundRect(actionRect, actionRect.height() * 0.5f, actionRect.height() * 0.5f, backing)
        actionPaint.color = theme.onAccent
        actionPaint.alpha = alpha
        canvas.drawText(
            action,
            actionRect.centerX() - actionPaint.measureText(action) * 0.5f,
            actionRect.centerY() - (actionPaint.descent() + actionPaint.ascent()) * 0.5f,
            actionPaint,
        )
        canvas.restoreToCount(actionSaved)

        rule.color = theme.inkFaint
        rule.alpha = alpha
        val closeDip = 1f - PRESS_DIP * closePress.value
        val closeSaved = canvas.save()
        canvas.scale(closeDip, closeDip, closeRect.centerX(), closeRect.centerY())
        canvas.drawPath(cross, rule)
        canvas.restoreToCount(closeSaved)

        canvas.restoreToCount(saved)
    }

    /**
     * Whether a point lands on the card. The host asks before it swallows a stroke: with only a
     * card up, everything outside it still belongs to the world underneath.
     */
    fun hit(x: Float, y: Float): Boolean = visible && card.contains(x, y - slideAt(shownOpenness))

    /** How far the card is still short of its resting place, in pixels. */
    private fun slideAt(openness: Float): Float = lerp(-card.height(), 0f, openness)

    /** Returns true when the point was the card's, so the host stops looking behind it. */
    fun onTap(screenX: Float, screenY: Float): Boolean {
        if (!visible) return false
        // Taken back into the card's own space, so a tap during the entrance hits what the eye
        // sees rather than where the card will eventually settle.
        val x = screenX
        val y = screenY - slideAt(shownOpenness)
        if (closeRect.contains(x, y)) {
            closePress.snapTo(1f)
            closePress.target = 0f
            hide()
            onDismiss?.invoke()
            return true
        }
        if (actionRect.contains(x, y)) {
            press.snapTo(1f)
            press.target = 0f
            onAction?.invoke()
            return true
        }
        return card.contains(x, y)
    }

    /**
     * Measures the paragraph and places everything. Only runs when a size actually changed, so
     * the per-frame path never builds a layout.
     */
    private fun layout(metrics: Metrics) {
        val width = bounds.width()
        if (laidOutFor == width && bodyLayout != null) return
        laidOutFor = width

        val side = metrics.gutter
        val inner = (width - side * 4f).coerceAtLeast(1f).toInt()
        bodyLayout = StaticLayout.Builder
            .obtain(body, 0, body.length, bodyPaint, inner)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(metrics.dp(3f), 1f)
            .setIncludePad(false)
            .build()

        val actionHeight = metrics.rowHeight * 0.72f
        val height = metrics.gutter * 2f +
            headlinePaint.textSize * 1.7f +
            (bodyLayout?.height?.toFloat() ?: 0f) +
            metrics.gutter +
            actionHeight
        card.set(
            bounds.left + side,
            bounds.top + safeTop + side,
            bounds.right - side,
            bounds.top + safeTop + side + height,
        )

        val actionWidth = actionPaint.measureText(action) + metrics.gutter * 2.4f
        actionRect.set(
            card.left + metrics.gutter,
            card.bottom - metrics.gutter - actionHeight,
            card.left + metrics.gutter + actionWidth,
            card.bottom - metrics.gutter,
        )

        val closeSize = metrics.dp(20f)
        closeRect.set(
            card.right - metrics.gutter - closeSize,
            card.top + metrics.gutter,
            card.right - metrics.gutter,
            card.top + metrics.gutter + closeSize,
        )
        val arm = closeSize * 0.28f
        cross.reset()
        cross.moveTo(closeRect.centerX() - arm, closeRect.centerY() - arm)
        cross.lineTo(closeRect.centerX() + arm, closeRect.centerY() + arm)
        cross.moveTo(closeRect.centerX() + arm, closeRect.centerY() - arm)
        cross.lineTo(closeRect.centerX() - arm, closeRect.centerY() + arm)
    }

    private companion object {
        /** How far a pressed target shrinks, as a fraction of its own size. */
        const val PRESS_DIP = 0.06f
    }
}
