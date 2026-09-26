package app.opal.core.tunnel.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.opal.core.model.tunnel.TrafficSample
import app.opal.core.model.tunnel.TunnelSnapshot
import app.opal.core.model.tunnel.TunnelState
import app.opal.core.tunnel.Labels
import app.opal.core.tunnel.R
import app.opal.core.tunnel.TunnelService

/**
 * The foreground notification. While connecting/reconnecting it is an Android 16 Live Update
 * (ProgressStyle, promoted ongoing); once connected it becomes an ordinary, silent ongoing
 * notification with speed and actions. NotificationCompat falls back to a progress bar before 16.
 * Every state with a live VPN offers "Reconnect" (except without a network) and "Disconnect".
 */
internal class TunnelNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel =
            NotificationChannel(
                    CHANNEL_STATUS,
                    context.getString(R.string.notif_channel_status),
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply {
                    description = context.getString(R.string.notif_channel_status_desc)
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(snapshot: TunnelSnapshot, traffic: TrafficSample?): Notification {
        val b =
            NotificationCompat.Builder(context, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_stat_opal)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openApp())
                // Android 14+ lets the user swipe a foreground notification away: put it back.
                .setDeleteIntent(action(TunnelService.ACTION_NOTIFICATION_DISMISSED))
        when (val state = snapshot.state) {
            is TunnelState.Connecting,
            is TunnelState.Reconnecting -> {
                val progress = snapshot.bootstrap?.progress ?: 0
                val phase = snapshot.bootstrap?.phase?.let { context.getString(Labels.phase(it)) }
                val racing =
                    snapshot.racing
                        .takeIf { it.size > 1 }
                        ?.joinToString { context.getString(Labels.transport(it)) }
                b.setContentTitle(
                        context.getString(
                            if (state is TunnelState.Reconnecting) R.string.notif_reconnecting
                            else R.string.notif_connecting
                        )
                    )
                    .setContentText(
                        racing?.let { context.getString(R.string.notif_racing, it) } ?: phase
                    )
                    .setRequestPromotedOngoing(true)
                    .setShortCriticalText("$progress%")
                    .setStyle(
                        NotificationCompat.ProgressStyle()
                            .setProgress(progress)
                            .setProgressIndeterminate(progress == 0)
                            .setStyledByProgress(true)
                    )
                    .addReconnectAndDisconnect()
            }
            TunnelState.Connected -> {
                val transport = snapshot.transport?.let { context.getString(Labels.transport(it)) }
                val speed = traffic?.let {
                    context.getString(
                        R.string.notif_speed,
                        Labels.speed(context, it.read),
                        Labels.speed(context, it.written),
                    )
                }
                b.setContentTitle(context.getString(R.string.notif_connected))
                    .setContentText(listOfNotNull(speed, transport).joinToString(" · "))
                    .addReconnectAndDisconnect()
            }
            TunnelState.WaitingForNetwork ->
                b.setContentTitle(context.getString(R.string.notif_no_network))
                    .setContentText(context.getString(R.string.notif_no_network_text))
                    .addAction(
                        0,
                        context.getString(R.string.notif_action_disconnect),
                        action(TunnelService.ACTION_DISCONNECT),
                    )
            TunnelState.Blocked ->
                b.setContentTitle(context.getString(R.string.notif_blocked))
                    .setContentText(context.getString(R.string.notif_blocked_text))
                    .addReconnectAndDisconnect()
            TunnelState.Standby ->
                b.setContentTitle(context.getString(R.string.notif_standby))
                    .setContentText(context.getString(R.string.notif_standby_text))
                    .addAction(
                        0,
                        context.getString(R.string.notif_action_connect),
                        action(TunnelService.ACTION_CONNECT),
                    )
                    .addAction(
                        0,
                        context.getString(R.string.notif_action_turn_off),
                        action(TunnelService.ACTION_STOP_STANDBY),
                    )
            TunnelState.Stopping -> b.setContentTitle(context.getString(R.string.notif_stopping))
            TunnelState.Off,
            is TunnelState.Error -> b.setContentTitle(context.getString(R.string.notif_error))
        }
        return b.build()
    }

    fun notify(notification: Notification) {
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS not granted: the foreground service still runs, just unseen.
            }
        }
    }

    private fun NotificationCompat.Builder.addReconnectAndDisconnect(): NotificationCompat.Builder =
        addAction(
                0,
                context.getString(R.string.notif_action_reconnect),
                action(TunnelService.ACTION_RECONNECT),
            )
            .addAction(
                0,
                context.getString(R.string.notif_action_disconnect),
                action(TunnelService.ACTION_DISCONNECT),
            )

    private fun openApp(): PendingIntent? {
        val launch =
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun action(action: String): PendingIntent =
        PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, TunnelService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val CHANNEL_STATUS = "tunnel_status"
        const val NOTIFICATION_ID = 1
    }
}
