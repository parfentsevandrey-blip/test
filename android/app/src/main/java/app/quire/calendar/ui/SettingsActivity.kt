package app.quire.calendar.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDelegate
import app.quire.calendar.QuireApp
import app.quire.calendar.R
import app.quire.calendar.core.Accent
import app.quire.calendar.core.EventRepository
import app.quire.calendar.core.Skin
import app.quire.calendar.widget.MonthWidgetProvider

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.canvas)
        }
        root.addView(
            Chrome.topBar(this, palette, getString(R.string.settings)) { finish() },
        )

        val panel = Panel(this, palette)
        buildWeek(panel)
        buildAppearance(panel)
        buildGrid(panel)
        buildCalendars(panel)
        buildAbout(panel)

        root.addView(
            ScrollView(this).apply {
                isFillViewport = true
                clipToPadding = false
                addView(panel.view)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            },
        )

        setContentView(root)
        padForSystemBars(root)
    }

    private fun commit() = MonthWidgetProvider.requestUpdate(this)

    private fun buildWeek(panel: Panel) {
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
            commit()
        }
    }

    private fun buildAppearance(panel: Panel) {
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
            commit()
            AppCompatDelegate.setDefaultNightMode(QuireApp.nightMode(skins[index]))
        }
        panel.accents(prefs.accent) { accent: Accent ->
            prefs.accent = accent
            commit()
            recreate()
        }
    }

    private fun buildGrid(panel: Panel) {
        panel.section(R.string.section_grid)
        panel.toggle(R.string.show_adjacent, R.string.show_adjacent_hint, prefs.showAdjacent) {
            prefs.showAdjacent = it
            commit()
        }
        panel.rule()
        panel.toggle(R.string.dim_weekends, R.string.dim_weekends_hint, prefs.dimWeekends) {
            prefs.dimWeekends = it
            commit()
        }
        panel.rule()
        panel.toggle(R.string.week_numbers, R.string.week_numbers_hint, prefs.weekNumbers) {
            prefs.weekNumbers = it
            commit()
        }
        panel.rule()
        panel.toggle(R.string.coloured_dots, R.string.coloured_dots_hint, prefs.colouredDots) {
            prefs.colouredDots = it
            commit()
        }
    }

    private fun buildCalendars(panel: Panel) {
        panel.section(R.string.section_calendars)
        val sources = EventRepository.calendars(this)
        if (sources.isEmpty()) {
            panel.note(getString(R.string.no_calendars))
            return
        }
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
                commit()
            }
        }
    }

    private fun buildAbout(panel: Panel) {
        panel.section(R.string.section_about)
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        panel.note(
            getString(R.string.about_line, version) + "\n" + getString(R.string.about_body),
        )
    }
}
