package app.quire.weather

import android.content.Context
import androidx.core.content.edit
import kotlin.math.roundToInt

/** How the temperature is written. Stored as Celsius always; this is a reading, not a storage. */
enum class Degrees(val key: String) {
    CELSIUS("c"),
    FAHRENHEIT("f"),
    ;

    fun from(celsius: Double): Double =
        if (this == CELSIUS) celsius else celsius * 9.0 / 5.0 + 32.0

    companion object {
        fun of(key: String?): Degrees = entries.firstOrNull { it.key == key } ?: CELSIUS
    }
}

/** How the wind is written, from the km/h the forecast is fetched in. */
enum class WindUnit(val key: String, val factor: Double) {
    KMH("kmh", 1.0),
    MS("ms", 1.0 / 3.6),
    MPH("mph", 0.621371),
    ;

    fun from(kmh: Double): Double = kmh * factor

    companion object {
        fun of(key: String?): WindUnit = entries.firstOrNull { it.key == key } ?: KMH
    }
}

/**
 * How the pressure is written, from the hectopascals the forecast is fetched in.
 *
 * Both are offered rather than one picked from the locale, because the reading is a preference
 * and not a fact about where somebody is: plenty of people read millimetres in a country that
 * publishes hectopascals, and the other way round.
 */
enum class Pressure(val key: String, val factor: Double) {
    HPA("hpa", 1.0),
    MMHG("mmhg", 0.750062),
    ;

    fun from(hpa: Double): Double = hpa * factor

    companion object {
        fun of(key: String?): Pressure = entries.firstOrNull { it.key == key } ?: HPA
    }
}

/**
 * Everything the weather app can be told.
 *
 * Units are a display decision and nothing else: the forecast is always fetched and stored in
 * Celsius and km/h, and converted where it is written out. That is what lets somebody switch to
 * Fahrenheit and see the change immediately, on a card that has not been refetched, instead of
 * waiting an hour for numbers in the new unit to arrive.
 */
class WeatherSettings private constructor(private val context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Minutes between refreshes.
     *
     * The default is sixty because that is roughly how often the forecast is recomputed upstream:
     * asking twice as often gets the same answer twice and charges the battery for it. The
     * shorter intervals are offered anyway — a person watching a storm come in has a reason — and
     * are driven by an alarm rather than a job, since jobs will not go below fifteen minutes.
     */
    var periodMinutes: Int
        get() = prefs.getInt(KEY_PERIOD, DEFAULT_PERIOD)
        set(value) = prefs.edit { putInt(KEY_PERIOD, PERIODS.minByOrNull { kotlin.math.abs(it - value) } ?: DEFAULT_PERIOD) }

    /**
     * Whether the weather moves behind the top of the screen.
     *
     * On, and quiet: it is the app's one piece of atmosphere and it is drawn faintly enough to
     * read past. Somebody who would rather have a still page can say so here, and somebody who
     * has turned animation off system-wide gets the still page without asking.
     */
    var liveSky: Boolean
        get() = prefs.getBoolean(KEY_LIVE_SKY, true)
        set(value) = prefs.edit { putBoolean(KEY_LIVE_SKY, value) }

    /**
     * Whether the cards carry a rim of moving light.
     *
     * On, because it is the one place on the page where an ornament costs nothing: it lives on the
     * edge a card already has, so there is no text it can sit on top of and nothing it can push out
     * of the way. It is still a switch, because "quiet" and "still" are not the same preference and
     * somebody who wants the second should not have to want the first as well.
     */
    var glassEdges: Boolean
        get() = prefs.getBoolean(KEY_GLASS, true)
        set(value) = prefs.edit { putBoolean(KEY_GLASS, value) }

    var alerts: Boolean
        get() = prefs.getBoolean(KEY_ALERTS, false)
        set(value) = prefs.edit { putBoolean(KEY_ALERTS, value) }

    /** The chance of rain, in per cent, at which a day is worth a notification. */
    var threshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = prefs.edit { putInt(KEY_THRESHOLD, value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)) }

    var degrees: Degrees
        get() = Degrees.of(prefs.getString(KEY_DEGREES, null))
        set(value) = prefs.edit { putString(KEY_DEGREES, value.key) }

    var wind: WindUnit
        get() = WindUnit.of(prefs.getString(KEY_WIND, null))
        set(value) = prefs.edit { putString(KEY_WIND, value.key) }

    var pressure: Pressure
        get() = Pressure.of(prefs.getString(KEY_PRESSURE, null))
        set(value) = prefs.edit { putString(KEY_PRESSURE, value.key) }

    /** The day an alert was last posted for, so the same rain is not announced twice. */
    var alertedOn: String
        get() = prefs.getString(KEY_ALERTED, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_ALERTED, value) }

    /** A temperature as it is written, in whole degrees of the chosen unit. */
    fun write(celsius: Double): String = "${degrees.from(celsius).roundToInt()}°"

    /** A wind speed in whole units of the chosen one. */
    fun writeWind(kmh: Double): Int = wind.from(kmh).roundToInt()

    companion object {
        private const val FILE = "quire-weather-settings"
        private const val KEY_PERIOD = "period"
        private const val KEY_ALERTS = "alerts"
        private const val KEY_LIVE_SKY = "livesky"
        private const val KEY_GLASS = "glassedges"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_DEGREES = "degrees"
        private const val KEY_WIND = "wind"
        private const val KEY_PRESSURE = "pressure"
        private const val KEY_ALERTED = "alerted"

        /**
         * The offered intervals, in minutes. Anything else is snapped to the nearest.
         *
         * The two short ones are below JobScheduler's floor and are driven by [WeatherTick]
         * instead; see the note there for what "five minutes" honestly means on a sleeping phone.
         */
        val PERIODS = listOf(5, 10, 30, 60, 180, 360)

        const val DEFAULT_PERIOD = 60
        const val DEFAULT_THRESHOLD = 50
        const val MIN_THRESHOLD = 20
        const val MAX_THRESHOLD = 90

        @Volatile
        private var instance: WeatherSettings? = null

        fun get(context: Context): WeatherSettings = instance ?: synchronized(this) {
            instance ?: WeatherSettings(context.applicationContext).also { instance = it }
        }
    }
}
