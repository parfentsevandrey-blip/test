package app.quire.calendar.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.quire.calendar.R
import app.quire.calendar.core.Palette

/** The one bar every secondary screen wears: a chevron, a word, nothing else. */
object Chrome {

    fun topBar(
        context: Context,
        palette: Palette,
        title: String,
        onBack: () -> Unit,
    ): View {
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val back = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_left)
            setColorFilter(palette.inkMuted)
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f))
            setPadding(dp(9f), dp(9f), dp(9f), dp(9f))
            contentDescription = title
            val out = TypedValue()
            context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, out, true,
            )
            setBackgroundResource(out.resourceId)
            setOnClickListener { onBack() }
        }
        val label = TextView(context).apply {
            text = title
            setTextColor(palette.ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            letterSpacing = -0.015f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(6f) }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(10f), dp(16f), dp(10f))
            addView(back)
            addView(label)
        }
    }
}
