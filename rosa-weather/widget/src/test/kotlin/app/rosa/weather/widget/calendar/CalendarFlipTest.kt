package app.rosa.weather.widget.calendar

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import androidx.datastore.core.DataStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import app.rosa.weather.core.data.network.OpenMeteoClient
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.repository.WeatherRepository
import app.rosa.weather.core.data.repository.WidgetConfigRepository
import app.rosa.weather.core.data.store.ForecastCache
import app.rosa.weather.core.data.sync.SyncScheduler
import app.rosa.weather.core.data.util.WallClock
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.SavedPlaces
import app.rosa.weather.core.model.WidgetConfigs
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.widget.WidgetUpdater
import app.rosa.weather.widget.provider.CalendarWidgetProvider
import app.rosa.weather.widget.provider.WidgetKind
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The arrows end to end: an update draws the months around the one on show, a flip then only
 * hands the launcher a page drawn ahead, and a page drawn for other settings is never shown.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-rXX-w411dp-h891dp-xxhdpi")
class CalendarFlipTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 9, 25)
    private val now = today.atTime(12, 0).atZone(zone).toEpochSecond()
    private val clock = WallClock { now * 1000 }
    private val id = 7
    private val manager = AppWidgetManager.getInstance(context)
    private val configs = Store(WidgetConfigs(mapOf(id to WidgetKind.Calendar.defaultConfig)))
    private lateinit var updater: WidgetUpdater

    /** A DataStore held in memory. */
    private class Store<T>(initial: T) : DataStore<T> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<T> = state
        override suspend fun updateData(transform: suspend (t: T) -> T): T = transform(state.value).also { state.value = it }
    }

    @Before
    fun setUp() {
        val background = Executors.newSingleThreadExecutor()
        runCatching { WorkManager.initialize(context, Configuration.Builder().setExecutor(background).setTaskExecutor(background).build()) }
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, CalendarWidgetProvider::class.java)
            minWidth = 250
            minHeight = 110
        }
        shadowOf(manager).addBoundWidget(id, info)
        manager.updateAppWidgetOptions(
            id,
            Bundle().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(314f, 252f), SizeF(380f, 200f))) },
        )
        val place = Place("sample", "Москва", 55.75, 37.62)
        val forecast = SampleForecast.create(nowEpochSeconds = now, placeId = place.id)
        // The network is never asked: widgets draw from the cache.
        val client = OpenMeteoClient::class.java.getDeclaredConstructor(dagger.Lazy::class.java)
            .newInstance(dagger.Lazy<Any> { error("no network in tests") })
        updater = WidgetUpdater(
            context,
            WeatherRepository(client, Store(ForecastCache(mapOf(place.id to forecast))), clock),
            PlacesRepository(Store(SavedPlaces(places = listOf(place), selectedId = place.id))),
            SettingsRepository(Store(AppSettings())),
            WidgetConfigRepository(configs),
            SyncScheduler(context),
            clock,
        )
    }

    private fun pages(): List<String> =
        File(context.cacheDir, "calendar-pages").list().orEmpty().filter { it.startsWith("w$id-") && it.endsWith(".page") }.sorted()

    private fun shown(): String = shadowOf(manager).getViewFor(id)
        .findViewById<android.view.View>(app.rosa.weather.widget.R.id.widget_image).contentDescription.toString()

    private fun move(delta: Int) = CalendarNavigation(context).move(id, delta, today)

    @Test
    fun `the arrows flip to pages drawn ahead`() = runBlocking {
        withTimeout(120_000) {
            updater.update(intArrayOf(id))
            updater.settle()
        }
        assertThat(shown()).isEqualTo("Сентябрь 2026. Пятница, 25. Неделя 39: Кафе у окна")
        // On show, the months either side, and one more on: two quick taps forward are both ready.
        assertThat(pages()).containsExactly("w7-2026-08.page", "w7-2026-09.page", "w7-2026-10.page", "w7-2026-11.page")

        move(1)
        val started = System.nanoTime()
        assertThat(updater.flip(id)).isTrue()
        println("CalendarFlipTest: flip %.1f ms".format((System.nanoTime() - started) / 1e6))
        assertThat(shown()).isEqualTo("Октябрь 2026. Пятница, 25. Неделя 42: Камин в замке")
        move(1)
        val again = System.nanoTime()
        assertThat(updater.flip(id)).isTrue()
        println("CalendarFlipTest: second flip %.1f ms (Robolectric inflates the launcher's views in-process)".format((System.nanoTime() - again) / 1e6))
        assertThat(shown()).isEqualTo("Ноябрь 2026. Пятница, 25. Неделя 46: Вечер с книгой")

        // After the flips, the months around November; August is too far off to keep.
        withTimeout(120_000) {
            updater.drawAhead(intArrayOf(id))
            updater.settle()
        }
        assertThat(pages()).containsExactly("w7-2026-09.page", "w7-2026-10.page", "w7-2026-11.page", "w7-2026-12.page", "w7-2027-01.page")

        // The title's way home is ready too.
        move(0)
        assertThat(updater.flip(id)).isTrue()
        assertThat(shown()).isEqualTo("Сентябрь 2026. Пятница, 25. Неделя 39: Кафе у окна")
    }

    @Test
    fun `a page drawn for other settings is not shown`() = runBlocking {
        withTimeout(120_000) {
            updater.update(intArrayOf(id))
            updater.settle()
        }
        configs.updateData { it.copy(byId = it.byId + (id to it[id].copy(style = WidgetStyle.Paper))) }
        CalendarPages.clearMemory()
        move(1)
        assertThat(updater.flip(id)).isFalse()
        // The arrow's fallback draws the month then and there, and the pages around it anew.
        withTimeout(120_000) {
            updater.update(intArrayOf(id))
            updater.settle()
        }
        assertThat(shown()).isEqualTo("Октябрь 2026. Пятница, 25. Неделя 42: Камин в замке")
        move(1)
        assertThat(updater.flip(id)).isTrue()
    }
}
