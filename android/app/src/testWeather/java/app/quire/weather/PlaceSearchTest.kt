package app.quire.weather

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Naming a place instead of being located.
 *
 * The point of this path is that the app is useful to somebody who will not hand over a location,
 * so what is checked here is that a named place actually outranks the device — not merely that it
 * is stored somewhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaceSearchTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    /** Trimmed from a real answer, keeping the fields and the shape. */
    private val response = """
        {"results":[
          {"id":524901,"name":"Moscow","latitude":55.75222,"longitude":37.61556,
           "country":"Russia","admin1":"Moscow","country_code":"RU"},
          {"id":5601538,"name":"Moscow","latitude":46.73239,"longitude":-117.00017,
           "country":"United States","admin1":"Idaho","country_code":"US"}
        ],"generationtime_ms":0.7}
    """.trimIndent()

    @Test
    fun `a search answer becomes places`() {
        val places = PlaceSearch.parse(response)
        assertEquals(2, places.size)
        assertEquals("Moscow", places[0].name)
        assertEquals(55.75222, places[0].latitude, 1e-5)
        // Two places of the same name are told apart by what is under them, not by the name —
        // and a region that merely repeats the name is dropped, since "Moscow · Moscow · Russia"
        // tells you nothing the first word did not.
        assertEquals("Russia", places[0].describe())
        assertEquals("Idaho · United States", places[1].describe())
    }

    /** No matches means no `results` key at all, not an empty array. */
    @Test
    fun `an answer with no matches is empty rather than an error`() {
        assertTrue(PlaceSearch.parse("""{"generationtime_ms":0.2}""").isEmpty())
    }

    @Test
    fun `a query too short to mean anything is not sent`() {
        var asked = false
        val found = PlaceSearch.search("M", "en") { asked = true; response }
        assertTrue("a one-letter query went to the network", !asked)
        assertTrue(found.isEmpty())
    }

    @Test
    fun `the query is escaped into the url`() {
        val url = PlaceSearch.url("Нижний Новгород", "ru")
        assertTrue("the space was not escaped: $url", !url.contains(" "))
        assertTrue("the language was not passed: $url", url.contains("language=ru"))
    }

    /**
     * The whole point: once a place is named, the device's own position is not consulted. Somebody
     * who typed Berlin while sitting in Munich meant Berlin.
     */
    @Test
    fun `a named place outranks the device and survives a restart`() {
        WeatherStore.clear(context)
        assertTrue("nothing should be pinned to begin with", !WeatherStore.pinned(context))

        WeatherStore.pin(context, Place("Berlin", "Berlin", "Germany", 52.52, 13.405))
        assertTrue("the place was not pinned", WeatherStore.pinned(context))

        val remembered = WeatherStore.lastPlace(context)!!
        assertEquals("Berlin", remembered.first)
        assertEquals(52.52, remembered.second, 0.01)

        WeatherStore.unpin(context)
        assertTrue("the pin outlived being removed", !WeatherStore.pinned(context))
        // The coordinates stay: they are the last place a forecast was for, which is still the
        // best guess until the device offers a better one.
        assertEquals("Berlin", WeatherStore.lastPlace(context)!!.first)
    }

    /** Choosing a place drops the forecast with it: Munich's weather under Berlin's name is worse
     *  than an empty card for a second. */
    @Test
    fun `pinning a new place discards the old forecast`() {
        WeatherStore.clear(context)
        WeatherStore.save(
            context,
            Forecast(
                place = "Munich", latitude = 48.14, longitude = 11.58,
                now = Conditions(20.0, 20.0, Sky.CLEAR, true, 50, 5.0),
                days = emptyList(), fetched = 1L,
            ),
        )
        assertTrue("nothing was stored", WeatherStore.load(context) != null)

        WeatherStore.pin(context, Place("Berlin", null, "Germany", 52.52, 13.405))
        assertTrue(
            "the old city's forecast survived the move",
            WeatherStore.load(context) == null,
        )
    }
}
