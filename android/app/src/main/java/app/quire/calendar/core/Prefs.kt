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
        const val KEY_HEAT = "heat"
        const val KEY_SWIPE_NAV = "swipe_nav"
        const val KEY_DYNAMIC = "dynamic"
        const val KEY_PAINTED_SCHEME = "painted_scheme"

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

    /** Tint each square by how full the day is. */
    var heat: Boolean
        get() = sp.getBoolean(KEY_HEAT, false)
        set(v) = sp.edit { putBoolean(KEY_HEAT, v) }

    /**
     * Whether a finger laid on the navigation bar can slide along it, changing the screen as it
     * passes each item, instead of having to lift and land four separate times.
     *
     * On by default, and it costs a tap nothing: the drag only takes over once the finger has
     * travelled the platform's own touch slop sideways, which no tap ever does. Off is here
     * because a bar that changes screens under a thumb resting on it is a fair thing to dislike,
     * and because someone whose hands do not hold still should be able to say so.
     */
    var swipeNav: Boolean
        get() = sp.getBoolean(KEY_SWIPE_NAV, true)
        set(v) = sp.edit { putBoolean(KEY_SWIPE_NAV, v) }

    /**
     * Whether the app takes its colours from the device's own Material scheme — the one Android
     * recomputes from the wallpaper — instead of from the built-in cinnabar palette.
     *
     * On by default where the platform publishes a scheme, because a calendar that matches the
     * phone it lives on is the better first impression.
     */
    var dynamic: Boolean
        get() = sp.getBoolean(KEY_DYNAMIC, true)
        set(v) = sp.edit { putBoolean(KEY_DYNAMIC, v) }

    /**
     * A fingerprint of the device's Material colours as the widgets were last painted in them.
     *
     * Widgets bake their colours, so this is what tells a later launch that the palette has moved
     * and the pictures on the home screen are out of date. Zero where the platform publishes no
     * scheme, which is also the default, so a device without one never repaints for this.
     */
    var paintedScheme: Int
        get() = sp.getInt(KEY_PAINTED_SCHEME, 0)
        set(v) = sp.edit { putInt(KEY_PAINTED_SCHEME, v) }

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

    /**
     * A placed widget wears the filled card until it is configured otherwise. The app half is a
     * page of ink and follows the system; a widget sits on somebody's wallpaper, and a card that
     * carries its own colour reads as an object on it rather than a hole in it.
     */
    var skin: Skin
        get() = Skin.from(sp.getString(k("skin"), Skin.COLOUR.key))
        set(v) = sp.edit { putString(k("skin"), v.key) }

    var accent: Accent
        get() = Accent.from(sp.getString(k("accent"), Accent.CINNABAR.key))
        set(v) = sp.edit { putString(k("accent"), v.key) }

    /**
     * Whether a filled card takes its colour from the device's Material scheme rather than from
     * [accent]. Only the filled skin can honour it; Paper and Ink ignore it.
     */
    var dynamic: Boolean
        get() = sp.getBoolean(k("dynamic"), true)
        set(v) = sp.edit { putBoolean(k("dynamic"), v) }

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

    /**
     * Whether a day is tinted by how full it is.
     *
     * The same reading the app gives a busy day, and the reason the card no longer draws a
     * hairline down every column: a lattice says where the columns are, which the numbers already
     * say, and the tint says something the numbers cannot.
     */
    var density: Boolean
        get() = sp.getBoolean(k("density"), true)
        set(v) = sp.edit { putBoolean(k("density"), v) }

    /** Months away from today; reset whenever the calendar day rolls over. */
    var monthOffset: Int
        get() = sp.getInt(k("offset"), 0)
        set(v) = sp.edit { putInt(k("offset"), v) }

    fun copyFrom(other: WidgetPrefs) {
        skin = other.skin
        accent = other.accent
        dynamic = other.dynamic
        opacity = other.opacity
        showEvents = other.showEvents
        colouredDots = other.colouredDots
        showAdjacent = other.showAdjacent
        dimWeekends = other.dimWeekends
        weekNumbers = other.weekNumbers
        density = other.density
    }
}
