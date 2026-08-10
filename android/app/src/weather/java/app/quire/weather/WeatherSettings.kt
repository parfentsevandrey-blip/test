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
     * The floor is fifteen because that is JobScheduler's, and the default is sixty because that
     * is roughly how often the forecast is recomputed upstream — asking twice as often gets the
     * same answer twice and charges the battery for it.
     */
    var periodMinutes: Int
        get() = prefs.getInt(KEY_PERIOD, DEFAULT_PERIOD)
        set(value) = prefs.edit { putInt(KEY_PERIOD, PERIODS.minByOrNull { kotlin.math.abs(it - value) } ?: DEFAULT_PERIOD) }

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
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_DEGREES = "degrees"
        private const val KEY_WIND = "wind"
        private const val KEY_ALERTED = "alerted"

        /** The offered intervals, in minutes. Anything else is snapped to the nearest. */
        val PERIODS = listOf(30, 60, 180, 360)

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
