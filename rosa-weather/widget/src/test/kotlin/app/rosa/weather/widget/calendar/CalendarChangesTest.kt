package app.rosa.weather.widget.calendar

import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The job that redraws calendar widgets when the phone's calendars change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarChangesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        val background = Executors.newSingleThreadExecutor()
        runCatching { WorkManager.initialize(context, Configuration.Builder().setExecutor(background).setTaskExecutor(background).build()) }
    }

    @Test
    fun `one watch waits for the calendars to change, however often it is armed`() = runBlocking {
        withTimeout(20_000) {
            CalendarChanges.watch(context)
            CalendarChanges.watch(context)
            CalendarChanges.watch(context)
        }
        val waiting = withTimeout(20_000) {
            WorkManager.getInstance(context).getWorkInfosByTagFlow("calendar_changes").first()
        }.filter { it.state == WorkInfo.State.ENQUEUED }
        assertThat(waiting).hasSize(1)
        val triggers = waiting.single().constraints.contentUriTriggers
        assertThat(triggers.map { it.uri }).containsExactly(CalendarContract.CONTENT_URI)
        assertThat(triggers.single().isTriggeredForDescendants).isTrue()
    }
}
