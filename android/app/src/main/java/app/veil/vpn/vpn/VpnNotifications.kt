package app.veil.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.veil.vpn.R
import app.veil.vpn.model.TunnelState
import app.veil.vpn.ui.MainActivity

/**
 * The persistent notification. Android requires one for a foreground service,
 * and for a VPN it doubles as the honest answer to "is my traffic protected
 * right now" — so it states the transport and the bootstrap progress rather
 * than a generic "running".
 */
object VpnNotifications {

    const val ID = 0x5645
    private const val CHANNEL = "tunnel"

    fun build(context: Context, state: TunnelState): Notification {
        ensureChannel(context)

        val title = context.getString(
            when (state) {
                is TunnelState.Idle -> R.string.state_idle
                is TunnelState.Probing -> R.string.state_probing
                is TunnelState.Starting -> R.string.state_starting
                is TunnelState.Bootstrapping -> R.string.state_bootstrapping
                is TunnelState.Connected -> R.string.state_connected
                is TunnelState.VpnGateStarting -> R.string.state_starting
                is TunnelState.VpnGateConnected -> R.string.state_connected
                is TunnelState.Escalating -> R.string.state_reconfiguring
                is TunnelState.Stopping -> R.string.state_stopping
                is TunnelState.Failed -> R.string.state_failed
            },
        )

        val detail = when (state) {
            is TunnelState.Probing -> context.getString(state.noteRes)
            is TunnelState.Starting ->
                "${context.getString(state.transport.labelRes)} " +
                    "(${state.attempt}/${state.ladderSize})"
            is TunnelState.Bootstrapping ->
                "${context.getString(state.transport.labelRes)} — " +
                    "${state.percent}% ${state.summary}"
            is TunnelState.Connected -> context.getString(state.transport.labelRes)
            is TunnelState.VpnGateStarting ->
                context.getString(R.string.vpngate_step, state.server, state.attempt, state.total)
            is TunnelState.VpnGateConnected ->
                context.getString(R.string.vpngate_via, state.server, state.country)
            is TunnelState.Escalating ->
                "${context.getString(state.from.labelRes)} -> " +
                    context.getString(state.to.labelRes)
            is TunnelState.Failed -> state.reason
            else -> null
        }

        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setOngoing(state.isBusy || state.isLive)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(contentIntent(context))

        detail?.let { builder.setContentText(it) }

        if (state is TunnelState.Bootstrapping) {
            builder.setProgress(100, state.percent, false)
        } else if (state is TunnelState.Probing && state.total > 0) {
            builder.setProgress(state.total, state.done, false)
        }

        if (state.isBusy || state.isLive) {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.notif_action_stop),
                    servicePendingIntent(context, VeilVpnService.ACTION_DISCONNECT),
                ).build(),
            )
        }
        return builder.build()
    }

    fun update(context: Context, state: TunnelState) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(ID, build(context, state)) }
    }

    fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun servicePendingIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, VeilVpnService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.notif_channel_tunnel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_tunnel_desc)
            setShowBadge(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        manager.createNotificationChannel(channel)
    }
}
