package app.rosa.weather.core.data.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import app.rosa.weather.core.data.store.ForecastCache
import app.rosa.weather.core.data.store.JsonSerializer
import app.rosa.weather.core.data.store.StoreJson
import app.rosa.weather.core.data.sync.WeatherSyncListener
import app.rosa.weather.core.data.util.WallClock
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.SavedPlaces
import app.rosa.weather.core.model.WidgetConfigs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {

    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            retryOnException(maxRetries = 2, retryOnTimeout = true)
            exponentialDelay()
        }
        defaultRequest {
            headers.append(HttpHeaders.UserAgent, "RosaWeather/1.0 (Android)")
        }
    }

    @Provides
    fun clock(): WallClock = WallClock.System

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w("Rosa", "Background work failed", e) },
    )

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<AppSettings> =
        jsonStore(context, "settings.json", AppSettings.serializer(), AppSettings())

    @Provides
    @Singleton
    fun placesStore(@ApplicationContext context: Context): DataStore<SavedPlaces> =
        jsonStore(context, "places.json", SavedPlaces.serializer(), SavedPlaces())

    @Provides
    @Singleton
    fun widgetConfigStore(@ApplicationContext context: Context): DataStore<WidgetConfigs> =
        jsonStore(context, "widgets.json", WidgetConfigs.serializer(), WidgetConfigs())

    @Provides
    @Singleton
    fun forecastStore(@ApplicationContext context: Context): DataStore<ForecastCache> =
        jsonStore(context, "forecasts.json", ForecastCache.serializer(), ForecastCache())

    private fun <T> jsonStore(context: Context, file: String, serializer: KSerializer<T>, default: T): DataStore<T> =
        DataStoreFactory.create(
            serializer = JsonSerializer(serializer, default, StoreJson),
            corruptionHandler = ReplaceFileCorruptionHandler { default },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.dataStoreFile(file) },
        )
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncBindings {
    @Multibinds
    abstract fun syncListeners(): Set<WeatherSyncListener>
}
