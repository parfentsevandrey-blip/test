package app.quire.calendar.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.quire.calendar.R
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.Palette
import app.quire.calendar.databinding.ItemAgendaBinding
import java.util.Date

/**
 * The day's entries. Time on the left in tabular figures, a coloured rule, then
 * the title — the same three-column rhythm the grid uses, one scale up.
 */
class AgendaAdapter(
    private val context: Context,
    private var palette: Palette,
    private val onClick: (AgendaEntry) -> Unit,
) : RecyclerView.Adapter<AgendaAdapter.Holder>() {

    private var entries: List<AgendaEntry> = emptyList()
    private val timeFormat = android.text.format.DateFormat.getTimeFormat(context)

    class Holder(val binding: ItemAgendaBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(list: List<AgendaEntry>) {
        entries = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemAgendaBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        val b = holder.binding

        if (entry.allDay) {
            b.timeStart.text = context.getString(R.string.all_day)
            b.timeStart.setTextColor(palette.inkMuted)
            b.timeEnd.visibility = View.GONE
        } else {
            b.timeStart.text = timeFormat.format(Date(entry.begin))
            b.timeStart.setTextColor(palette.ink)
            b.timeEnd.visibility = View.VISIBLE
            b.timeEnd.text = timeFormat.format(Date(entry.end))
            b.timeEnd.setTextColor(palette.inkFaint)
        }

        b.rule.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (entry.colour == 0) palette.inkGhost else entry.colour,
        )

        b.title.text = entry.title.ifBlank { "—" }
        b.title.setTextColor(palette.ink)

        val subtitle = listOfNotNull(entry.location, entry.calendarName)
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }
        if (subtitle == null) {
            b.subtitle.visibility = View.GONE
        } else {
            b.subtitle.visibility = View.VISIBLE
            b.subtitle.text = subtitle
            b.subtitle.setTextColor(palette.inkFaint)
        }

        b.root.setOnClickListener { onClick(entry) }
    }
}
