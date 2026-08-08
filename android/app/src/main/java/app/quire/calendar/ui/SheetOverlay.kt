package app.quire.calendar.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Tokens

/**
 * Panels do not take over the screen; they float above it.
 *
 * Each section is its own slab with its own shadow, and they arrive one after
 * another from below rather than as a single block, so the stack reads as
 * objects settling rather than a page loading. The world stays visible and
 * slightly pushed back behind them.
 */
class SheetOverlay(context: Context) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private val entering = PathInterpolator(0.16f, 0.9f, 0.2f, 1f)
    private val leaving = PathInterpolator(0.4f, 0f, 0.9f, 0.2f)

    private val scrim = View(context).apply {
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        alpha = 0f
    }
    private val scroller = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        clipToPadding = false
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
    }
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12f), dp(24f), dp(12f), dp(12f))
    }

    var palette: Palette = Tokens.palette(false, app.quire.calendar.core.Accent.CINNABAR)
    var motion: MotionProfile = MotionProfile.STANDARD
    var onDismissed: (() -> Unit)? = null

    private val slabs = ArrayList<View>()
    var isShowing = false
        private set

    var safeTop = 0
    var safeBottom = 0

    init {
        visibility = GONE
        scroller.addView(column)
        addView(scrim)
        addView(scroller)
        scrim.setOnClickListener { dismiss() }
        isClickable = true
    }

    fun applyInsets(top: Int, bottom: Int) {
        safeTop = top
        safeBottom = bottom
        column.setPadding(dp(12f), dp(24f) + top, dp(12f), dp(12f) + bottom)
    }

    // ---- building ------------------------------------------------------

    fun begin(): Builder {
        column.removeAllViews()
        slabs.clear()
        scrim.setBackgroundColor(
            Tokens.withAlpha(if (palette.dark) 0xFF000000.toInt() else palette.ink, 0.52f),
        )
        return Builder()
    }

    inner class Builder {
        fun title(text: String) {
            column.addView(
                TextView(context).apply {
                    this.text = text
                    // The title floats on the dimmed world, not on a slab, so it
                    // takes the light ink in both skins and carries its own
                    // shadow rather than asking for a darker scrim.
                    setTextColor(if (palette.dark) palette.ink else palette.canvas)
                    setShadowLayer(dp(10f).toFloat(), 0f, dp(2f).toFloat(), 0x99000000.toInt())
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                    letterSpacing = -0.025f
                    setPadding(dp(14f), dp(4f), dp(14f), dp(14f))
                },
            )
        }

        /** One floating card holding a group of rows. */
        fun slab(build: (Panel) -> Unit) {
            val panel = Panel(context, palette)
            build(panel)
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(24f).toFloat()
                    setColor(palette.surface)
                    setStroke(maxOf(1, Math.round(density * 0.5f)), palette.hairlineStrong)
                }
                elevation = dp(10f).toFloat()
                clipToOutline = true
                addView(panel.view)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(12f) }
            }
            slabs += card
            column.addView(card)
        }

        fun searchField(hint: String, onQuery: (String) -> Unit): EditText {
            val field = EditText(context).apply {
                this.hint = hint
                setHintTextColor(palette.inkGhost)
                setTextColor(palette.ink)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                background = null
                maxLines = 1
                setSingleLine()
                setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun afterTextChanged(s: Editable?) = onQuery(s?.toString().orEmpty())
                })
            }
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(24f).toFloat()
                    setColor(palette.surface)
                    setStroke(maxOf(1, Math.round(density * 0.5f)), palette.hairlineStrong)
                }
                elevation = dp(10f).toFloat()
                addView(field)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(12f) }
            }
            slabs += card
            column.addView(card)
            return field
        }

        /** A slab whose contents are rebuilt in place, for live results. */
        fun liveSlab(): Panel {
            val panel = Panel(context, palette)
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(24f).toFloat()
                    setColor(palette.surface)
                    setStroke(maxOf(1, Math.round(density * 0.5f)), palette.hairlineStrong)
                }
                elevation = dp(10f).toFloat()
                addView(panel.view)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(12f) }
            }
            slabs += card
            column.addView(card)
            return panel
        }
    }

    // ---- presentation --------------------------------------------------

    fun present() {
        isShowing = true
        visibility = VISIBLE
        scrim.animate().cancel()
        if (motion.instant) {
            scrim.alpha = 1f
        } else {
            scrim.alpha = 0f
            scrim.animate().alpha(1f).setDuration(190).start()
        }

        val rise = dp(44f).toFloat()
        slabs.forEachIndexed { index, slab ->
            slab.animate().cancel()
            if (motion.instant) {
                slab.alpha = 1f
                slab.translationY = 0f
                slab.scaleX = 1f
                slab.scaleY = 1f
                return@forEachIndexed
            }
            slab.alpha = 0f
            slab.translationY = rise
            slab.scaleX = 0.97f
            slab.scaleY = 0.97f
            slab.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * motion.staggerMillis)
                .setDuration(340)
                .setInterpolator(entering)
                .start()
        }
    }

    fun dismiss() {
        if (!isShowing) return
        isShowing = false
        scrim.animate().alpha(0f).setDuration(if (motion.instant) 0 else 150).start()
        val fall = dp(26f).toFloat()
        slabs.reversed().forEachIndexed { index, slab ->
            slab.animate()
                .alpha(0f)
                .translationY(fall)
                .setStartDelay(if (motion.instant) 0 else index * 14L)
                .setDuration(if (motion.instant) 0 else 160)
                .setInterpolator(leaving)
                .start()
        }
        postDelayed({
            if (!isShowing) {
                visibility = GONE
                column.removeAllViews()
                slabs.clear()
            }
        }, if (motion.instant) 0 else 260)
        onDismissed?.invoke()
    }
}
