package app.rosa.weather.core.data.network

import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.WeatherCondition
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class OpenMeteoClientTest {
    private val forecastJson = javaClass.classLoader!!.getResource("forecast.json")!!.readText()
    private val requested = mutableListOf<String>()

    private val client = OpenMeteoClient(
        HttpClient(MockEngine { request ->
            requested += request.url.toString()
            val body = when (request.url.host) {
                "api.open-meteo.com" -> forecastJson
                "air-quality-api.open-meteo.com" -> """{"current":{"time":1758628800,"european_aqi":31.0,"pm2_5":6.2}}"""
                else -> """{"results":[{"id":524901,"name":"Москва","latitude":55.75,"longitude":37.62,"country":"Россия","country_code":"RU","admin1":"Москва","timezone":"Europe/Moscow"}]}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
    )

    @Test
    fun `maps forecast response into the domain model`() = runTest {
        val place = Place("geo:1", "Москва", 55.75, 37.62)
        val forecast = client.forecast(place, fetchedAt = 1_758_629_000)

        assertThat(forecast.placeId).isEqualTo("geo:1")
        assertThat(forecast.utcOffsetSeconds).isEqualTo(10_800)
        assertThat(forecast.current.temperature).isEqualTo(12.4)
        assertThat(WeatherCondition.fromWmo(forecast.current.weatherCode)).isEqualTo(WeatherCondition.LightRain)
        assertThat(forecast.hourly).hasSize(4)
        assertThat(forecast.hourly.last().visibility).isNull()
        assertThat(forecast.daily.first().sunrise).isEqualTo(1_758_597_240)
        assertThat(forecast.nowcast.last().precipitation).isEqualTo(0.0)
        assertThat(forecast.air?.europeanAqi).isEqualTo(31)

        val forecastUrl = requested.first { "//api.open-meteo.com" in it }
        assertThat(forecastUrl).contains("timeformat=unixtime")
        assertThat(forecastUrl).contains("wind_speed_unit=ms")
        assertThat(forecastUrl).contains("minutely_15=")
    }

    @Test
    fun `maps geocoding results to places`() = runTest {
        val places = client.search("Моск", "ru")
        assertThat(places.single().id).isEqualTo("geo:524901")
        assertThat(places.single().countryCode).isEqualTo("RU")
    }
}
