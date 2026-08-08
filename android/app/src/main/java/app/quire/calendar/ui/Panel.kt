package app.quire.calendar.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.quire.calendar.R
import app.quire.calendar.core.Accent
import app.quire.calendar.core.Palette

/**
 * Settings are built in code, not in a preference XML, because the rows here are
 * not the platform's rows: a segmented strip marked by a rule under the live
 * option, a hand-drawn switch, a line of accent swatches. Six row types cover
 * both the app's settings and the widget's configuration screen.
 */
class Panel(private val context: Context, private val palette: Palette) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun add(child: View) = view.addView(child)

    private fun selectableBackground(): Int {
        val out = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
    }

    // ---- primitives ----------------------------------------------------

    fun section(titleRes: Int) {
        val label = TextView(context).apply {
            text = context.getString(titleRes)
            setTextColor(palette.inkFaint)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.13f
            isAllCaps = true
            setPadding(dp(20f), dp(26f), dp(20f), dp(10f))
        }
        add(label)
    }

    fun rule(insetStart: Float = 20f) {
        val line = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxOf(1, Math.round(density * 0.5f)),
            ).apply { marginStart = dp(insetStart) }
            setBackgroundColor(palette.hairline)
        }
        add(line)
    }

    fun note(text: CharSequence) {
        val body = TextView(context).apply {
            this.text = text
            setTextColor(palette.inkMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setLineSpacing(dp(3f).toFloat(), 1f)
            setPadding(dp(20f), dp(4f), dp(20f), dp(16f))
        }
        add(body)
    }

    private fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setPadding(dp(20f), dp(13f), dp(16f), dp(13f))
    }

    private fun titleColumn(title: String, hint: String?): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(
                TextView(context).apply {
                    text = title
                    setTextColor(palette.ink)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    letterSpacing = -0.005f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
            )
            if (hint != null) {
                addView(
                    TextView(context).apply {
                        text = hint
                        setTextColor(palette.inkFaint)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setPadding(0, dp(3f), 0, 0)
                    },
                )
            }
        }

    // ---- rows ----------------------------------------------------------

    fun toggle(titleRes: Int, hintRes: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
        val toggle = ToggleView(context).apply {
            palette = this@Panel.palette
            setCheckedImmediately(checked)
        }
        val container = row().apply {
            addView(
                titleColumn(
                    context.getString(titleRes),
                    hintRes?.let(context::getString),
                ),
            )
            addView(
                toggle,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(16f) },
            )
            setBackgroundResource(selectableBackground())
            setOnClickListener {
                toggle.checked = !toggle.checked
                onChange(toggle.checked)
            }
        }
        add(container)
    }

    /**
     * A strip of options with a rule under the live one. No dialog, no chevron —
     * the choice and its alternatives stay on screen together.
     */
    fun segmented(
        titleRes: Int,
        options: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
    ) {
        val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = ArrayList<TextView>(options.size)
        val underlines = ArrayList<View>(options.size)

        fun paint(active: Int) {
            for (i in labels.indices) {
                labels[i].setTextColor(if (i == active) palette.ink else palette.inkFaint)
                labels[i].typeface = Typeface.create(
                    if (i == active) "sans-serif-medium" else "sans-serif",
                    Typeface.NORMAL,
                )
                underlines[i].setBackgroundColor(
                    if (i == active) palette.accent else Color.TRANSPARENT,
                )
            }
        }

        options.forEachIndexed { index, option ->
            val label = TextView(context).apply {
                text = option
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                gravity = Gravity.CENTER
                setPadding(dp(9f), dp(4f), dp(9f), dp(5f))
                // Without this the label inherits MATCH_PARENT from its vertical
                // parent, measures at the full row width inside a wrap-content
                // column, and the first option swallows the entire strip.
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val underline = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(1.5f),
                )
            }
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(selectableBackground())
                addView(label)
                addView(underline)
                setOnClickListener {
                    paint(index)
                    onSelect(index)
                }
            }
            labels += label
            underlines += underline
            strip.addView(column)
        }
        paint(selectedIndex)

        add(
            row().apply {
                addView(titleColumn(context.getString(titleRes), null))
                addView(strip)
            },
        )
    }

    fun accents(selected: Accent, onSelect: (Accent) -> Unit) {
        val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val dots = Accent.entries.map { accent ->
            AccentDotView(context, accent).apply {
                palette = this@Panel.palette
                active = accent == selected
                contentDescription = context.getString(accentLabel(accent))
                setOnClickListener {
                    strip.children().forEach { (it as AccentDotView).active = it.accent == accent }
                    onSelect(accent)
                }
            }
        }
        dots.forEach(strip::addView)

        add(
            row().apply {
                addView(titleColumn(context.getString(R.string.accent), null))
                addView(strip)
            },
        )
    }

    /** A row that reads as a value and opens something else when tapped. */
    fun action(
        title: String,
        value: String?,
        accent: Boolean = false,
        onClick: () -> Unit,
    ) {
        add(
            row().apply {
                addView(
                    titleColumn(title, null).apply {
                        if (accent) {
                            (getChildAt(0) as TextView).apply {
                                setTextColor(palette.accent)
                                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                            }
                        }
                    },
                )
                if (value != null) {
                    addView(
                        TextView(context).apply {
                            text = value
                            setTextColor(palette.inkFaint)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                        },
                    )
                }
                setBackgroundResource(selectableBackground())
                setOnClickListener { onClick() }
            },
        )
    }

    /** A calendar source: colour chip, name, account, and a checkmark. */
    fun check(
        title: String,
        subtitle: String?,
        colour: Int,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        val chip = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(9f), dp(9f)).apply {
                marginEnd = dp(13f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (colour == 0) palette.inkFaint else colour)
            }
        }
        val mark = ImageView(context).apply {
            setImageResource(R.drawable.ic_check)
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f))
            setColorFilter(palette.ink)
            alpha = if (checked) 1f else 0f
        }
        add(
            row().apply {
                addView(chip)
                addView(titleColumn(title, subtitle))
                addView(mark)
                setBackgroundResource(selectableBackground())
                setOnClickListener {
                    val next = mark.alpha < 0.5f
                    mark.animate().alpha(if (next) 1f else 0f).setDuration(140L).start()
                    onChange(next)
                }
            },
        )
    }

    fun custom(child: View) = add(child)

    /** Empties the panel so its owner can rebuild it from new data. */
    fun clear() = view.removeAllViews()

    private fun LinearLayout.children(): List<View> = (0 until childCount).map { getChildAt(it) }

    /** The swatches carry no visible label, so the name lives in the description. */
    private fun accentLabel(accent: Accent): Int = when (accent) {
        Accent.CINNABAR -> R.string.accent_cinnabar
        Accent.INDIGO -> R.string.accent_indigo
        Accent.MOSS -> R.string.accent_moss
        Accent.OCHRE -> R.string.accent_ochre
        Accent.PLUM -> R.string.accent_plum
        Accent.GRAPHITE -> R.string.accent_graphite
    }
}
