package app.opal.core.tunnel.notify

import android.app.Notification
import android.app.PendingIntent
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.tunnel.BootstrapInfo
import app.opal.core.model.tunnel.BootstrapPhase
import app.opal.core.model.tunnel.ReconnectReason
import app.opal.core.model.tunnel.TrafficSample
import app.opal.core.model.tunnel.TunnelSnapshot
import app.opal.core.model.tunnel.TunnelState
import app.opal.core.tunnel.TunnelService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The status notification: its buttons in every state, the live speed and its persistence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru")
class TunnelNotificationsTest {

    private val notifications = TunnelNotifications(RuntimeEnvironment.getApplication())

    private fun Notification.buttons(): List<String> = actions.orEmpty().map { it.title.toString() }

    private fun Notification.buttonIntents(): List<String?> =
        actions.orEmpty().map { it.actionIntent.serviceAction() }

    private fun PendingIntent.serviceAction(): String? = shadowOf(this).savedIntent.action

    @Test
    fun `connected shows the speed with Reconnect and Disconnect`() {
        val n =
            notifications.build(
                TunnelSnapshot(state = TunnelState.Connected, transport = TransportKind.Snowflake),
                TrafficSample(1_250_000, 48_000, 90_000_000, 4_000_000, 7),
            )
        assertEquals(listOf("Переподключить", "Отключить"), n.buttons())
        assertEquals(
            listOf(TunnelService.ACTION_RECONNECT, TunnelService.ACTION_DISCONNECT),
            n.buttonIntents(),
        )
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.startsWith("↓ ") && " · ↑ " in text && text.endsWith("Snowflake"))
    }

    @Test
    fun `connecting and reconnecting offer both buttons`() {
        for (state in
            listOf(
                TunnelState.Connecting(),
                TunnelState.Reconnecting(ReconnectReason.User),
                TunnelState.Blocked,
            )) {
            val n =
                notifications.build(
                    TunnelSnapshot(
                        state = state,
                        bootstrap = BootstrapInfo(40, BootstrapPhase.LoadingRelays),
                    ),
                    null,
                )
            assertEquals(state.toString(), listOf("Переподключить", "Отключить"), n.buttons())
        }
    }

    @Test
    fun `without a network only Disconnect makes sense`() {
        val n = notifications.build(TunnelSnapshot(state = TunnelState.WaitingForNetwork), null)
        assertEquals(listOf("Отключить"), n.buttons())
    }

    @Test
    fun `the notification stays ongoing and comes back if swiped away`() {
        val n = notifications.build(TunnelSnapshot(state = TunnelState.Connected), null)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(TunnelService.ACTION_NOTIFICATION_DISMISSED, n.deleteIntent.serviceAction())
    }
}
