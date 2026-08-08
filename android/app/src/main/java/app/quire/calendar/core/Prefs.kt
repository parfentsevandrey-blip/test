package app.quire.calendar.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Two scopes share one file: the app's own preferences, and a small record per
 * placed widget (`w<id>_*`). Widgets are configured independently on purpose —
 * one can sit on a dark wallpaper in cinnabar while the app runs in graphite.
 */
class Prefs private constructor(private val sp: SharedPreferences) {

    companion object {
        private const val FILE = "quire"

        // App scope
        const val KEY_FIRST_DAY = "first_day"
        const val KEY_SKIN = "skin"
        const val KEY_ACCENT = "accent"
        const val KEY_SHOW_ADJACENT = "show_adjacent"
        const val KEY_DIM_WEEKENDS = "dim_weekends"
        const val KEY_WEEK_NUMBERS = "week_numbers"
        const val KEY_COLOURED_DOTS = "coloured_dots"
        const val KEY_HIDDEN_CALENDARS = "hidden_calendars"
        const val KEY_MOTION = "motion"
        const val KEY_HAPTICS = "haptics"
        const val KEY_HEAT = "heat"
        const val KEY_DEPTH = "depth"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(
                context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    // ---- App -----------------------------------------------------------

    var firstDay: String
        get() = sp.getString(KEY_FIRST_DAY, "auto") ?: "auto"
        set(v) = sp.edit { putString(KEY_FIRST_DAY, v) }

    var skin: Skin
        get() = Skin.from(sp.getString(KEY_SKIN, Skin.AUTO.key))
        set(v) = sp.edit { putString(KEY_SKIN, v.key) }

    var accent: Accent
        get() = Accent.from(sp.getString(KEY_ACCENT, Accent.CINNABAR.key))
        set(v) = sp.edit { putString(KEY_ACCENT, v.key) }

    var showAdjacent: Boolean
        get() = sp.getBoolean(KEY_SHOW_ADJACENT, true)
        set(v) = sp.edit { putBoolean(KEY_SHOW_ADJACENT, v) }

    var dimWeekends: Boolean
        get() = sp.getBoolean(KEY_DIM_WEEKENDS, true)
        set(v) = sp.edit { putBoolean(KEY_DIM_WEEKENDS, v) }

    var weekNumbers: Boolean
        get() = sp.getBoolean(KEY_WEEK_NUMBERS, false)
        set(v) = sp.edit { putBoolean(KEY_WEEK_NUMBERS, v) }

    var colouredDots: Boolean
        get() = sp.getBoolean(KEY_COLOURED_DOTS, true)
        set(v) = sp.edit { putBoolean(KEY_COLOURED_DOTS, v) }

    /** False until the user picks a profile; the first value comes from the OS. */
    val hasMotionPreference: Boolean get() = sp.contains(KEY_MOTION)

    /** Liveliness of the whole interface; see ui/Motion.kt. */
    var motion: String
        get() = sp.getString(KEY_MOTION, "standard") ?: "standard"
        set(v) = sp.edit { putString(KEY_MOTION, v) }

    var haptics: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(v) = sp.edit { putBoolean(KEY_HAPTICS, v) }

    /** Parallax from the device's tilt, and perspective in the transitions. */
    var depth: Boolean
        get() = sp.getBoolean(KEY_DEPTH, true)
        set(v) = sp.edit { putBoolean(KEY_DEPTH, v) }

    /** Tint each square by how full the day is. */
    var heat: Boolean
        get() = sp.getBoolean(KEY_HEAT, false)
        set(v) = sp.edit { putBoolean(KEY_HEAT, v) }

    var hiddenCalendars: Set<Long>
        get() = sp.getStringSet(KEY_HIDDEN_CALENDARS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
        set(v) = sp.edit { putStringSet(KEY_HIDDEN_CALENDARS, v.map(Long::toString).toSet()) }

    // ---- Widget --------------------------------------------------------

    fun widget(id: Int): WidgetPrefs = WidgetPrefs(sp, id)

    fun forgetWidget(id: Int) = sp.edit {
        val prefix = "w${id}_"
        sp.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
    }
}

class WidgetPrefs(private val sp: SharedPreferences, private val id: Int) {

    private fun k(name: String) = "w${id}_$name"

    val configured: Boolean get() = sp.contains(k("skin"))

    var skin: Skin
        get() = Skin.from(sp.getString(k("skin"), Skin.AUTO.key))
        set(v) = sp.edit { putString(k("skin"), v.key) }

    var accent: Accent
        get() = Accent.from(sp.getString(k("accent"), Accent.CINNABAR.key))
        set(v) = sp.edit { putString(k("accent"), v.key) }

    /** Card opacity, 0..100. Below 100 the wallpaper reads through. */
    var opacity: Int
        get() = sp.getInt(k("opacity"), 96)
        set(v) = sp.edit { putInt(k("opacity"), v.coerceIn(0, 100)) }

    var showEvents: Boolean
        get() = sp.getBoolean(k("events"), true)
        set(v) = sp.edit { putBoolean(k("events"), v) }

    var colouredDots: Boolean
        get() = sp.getBoolean(k("coloured"), true)
        set(v) = sp.edit { putBoolean(k("coloured"), v) }

    var showAdjacent: Boolean
        get() = sp.getBoolean(k("adjacent"), true)
        set(v) = sp.edit { putBoolean(k("adjacent"), v) }

    var dimWeekends: Boolean
        get() = sp.getBoolean(k("weekends"), true)
        set(v) = sp.edit { putBoolean(k("weekends"), v) }

    var weekNumbers: Boolean
        get() = sp.getBoolean(k("weeknum"), false)
        set(v) = sp.edit { putBoolean(k("weeknum"), v) }

    /** Months away from today; reset whenever the calendar day rolls over. */
    var monthOffset: Int
        get() = sp.getInt(k("offset"), 0)
        set(v) = sp.edit { putInt(k("offset"), v) }

    fun copyFrom(other: WidgetPrefs) {
        skin = other.skin
        accent = other.accent
        opacity = other.opacity
        showEvents = other.showEvents
        colouredDots = other.colouredDots
        showAdjacent = other.showAdjacent
        dimWeekends = other.dimWeekends
        weekNumbers = other.weekNumbers
    }
}
