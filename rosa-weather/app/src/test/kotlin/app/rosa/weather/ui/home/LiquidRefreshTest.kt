package app.rosa.weather.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** The pull-to-refresh drop must never take scrolling or flings that belong to the list. */
class LiquidRefreshTest {
    private val refresh = LiquidRefresh(onRefresh = {}, thresholdPx = 250f, haptics = null, isRefreshing = { false })
        .apply { scope = CoroutineScope(Dispatchers.Unconfined) }
    private val connection = refresh.connection

    @Test
    fun `scrolling the list leaves the drop alone`() {
        assertThat(connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)).isEqualTo(Offset.Zero)
        assertThat(connection.onPostScroll(Offset(0f, -40f), Offset.Zero, NestedScrollSource.UserInput)).isEqualTo(Offset.Zero)
        assertThat(refresh.pull).isEqualTo(0f)
    }

    @Test
    fun `pushing back up takes only what the drop was pulled, then gives the list the rest`() {
        connection.onPostScroll(Offset.Zero, Offset(0f, 40f), NestedScrollSource.UserInput)
        assertThat(refresh.pull).isEqualTo(18f)
        // Every event sees the previous one's result: the drop can't eat more than it had.
        assertThat(connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)).isEqualTo(Offset(0f, -10f))
        assertThat(connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)).isEqualTo(Offset(0f, -8f))
        assertThat(connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)).isEqualTo(Offset.Zero)
        assertThat(refresh.pull).isEqualTo(0f)
    }

    @Test
    fun `a fling goes to the list unless the drop is really out`() = runBlocking {
        assertThat(connection.onPreFling(Velocity(0f, -3000f))).isEqualTo(Velocity.Zero)
        connection.onPostScroll(Offset.Zero, Offset(0f, 1f), NestedScrollSource.UserInput)
        assertThat(connection.onPreFling(Velocity(0f, -3000f))).isEqualTo(Velocity.Zero)
        assertThat(refresh.pull).isEqualTo(0f)
    }
}
