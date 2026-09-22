package app.papersky.weather

import android.content.Context
import app.papersky.weather.core.data.AppJson
import app.papersky.weather.core.data.DeviceLocator
import app.papersky.weather.core.data.ForecastStore
import app.papersky.weather.core.data.OpenMeteoApi
import app.papersky.weather.core.data.PlacesData
import app.papersky.weather.core.data.PlacesRepository
import app.papersky.weather.core.data.SettingsRepository
import app.papersky.weather.core.data.WeatherRepository
import app.papersky.weather.core.data.jsonDataStore
import app.papersky.weather.core.model.UserSettings
import app.papersky.weather.core.sync.SyncStatus
import app.papersky.weather.core.sync.WeatherSync
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Hand-rolled dependency graph. The app is small enough that a DI framework would add more
 * build complexity than it removes; widgets, workers and screens all reach it via [container].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http: HttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(ContentNegotiation) { json(AppJson) }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 2)
                retryOnException(maxRetries = 2, retryOnTimeout = true)
                exponentialDelay()
            }
            install(UserAgent) { agent = "Papersky/${BuildConfig.VERSION_NAME} (Android)" }
        }
    }

    val api: OpenMeteoApi by lazy { OpenMeteoApi(http) }

    val weather: WeatherRepository by lazy {
        WeatherRepository(api, ForecastStore(File(appContext.filesDir, "forecasts")))
    }

    val places = PlacesRepository(
        jsonDataStore(appContext, "places.json", PlacesData(), PlacesData.serializer(), ioScope),
    )

    val settings = SettingsRepository(
        jsonDataStore(appContext, "settings.json", UserSettings(), UserSettings.serializer(), ioScope),
    )

    val locator = DeviceLocator(appContext)

    val syncStatus = SyncStatus()

    val sync: WeatherSync by lazy { WeatherSync(appContext, this) }
}

/** Implemented by the Application (and by test applications) to expose the graph. */
interface ContainerHost {
    val container: AppContainer
}

val Context.container: AppContainer
    get() = (applicationContext as ContainerHost).container
