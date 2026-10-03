package app.themesh.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;

import java.util.Arrays;

import app.themesh.mobile.core.Notice;

/** Каналы и уведомления: постоянное уведомление службы, уведомления о событиях и о сохранённых файлах. */
final class Notifier {
    /** Постоянное уведомление службы (тихое). */
    static final String CHANNEL_NODE = "node";
    /** Файлы, сообщения, письма. */
    static final String CHANNEL_EVENTS = "events";
    /** «Файл сохранён». */
    static final String CHANNEL_DOWNLOADS = "downloads";

    static final int ID_NODE = 1;
    private static final int ID_EVENT = 2;
    private static final int ID_DOWNLOAD = 3;

    private Notifier() {
    }

    /** Создаёт каналы (повторный вызов безопасен: меняются только названия). */
    static void ensureChannels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        NotificationChannel node = new NotificationChannel(CHANNEL_NODE,
                context.getString(R.string.channel_node_name), NotificationManager.IMPORTANCE_LOW);
        node.setDescription(context.getString(R.string.channel_node_desc));
        node.setShowBadge(false);
        NotificationChannel events = new NotificationChannel(CHANNEL_EVENTS,
                context.getString(R.string.channel_events_name), NotificationManager.IMPORTANCE_DEFAULT);
        events.setDescription(context.getString(R.string.channel_events_desc));
        NotificationChannel downloads = new NotificationChannel(CHANNEL_DOWNLOADS,
                context.getString(R.string.channel_downloads_name), NotificationManager.IMPORTANCE_LOW);
        downloads.setDescription(context.getString(R.string.channel_downloads_desc));
        nm.createNotificationChannels(Arrays.asList(node, events, downloads));
    }

    /** Постоянное уведомление «The Mesh работает» со строкой о сети и действиями «Открыть» и «Выйти». */
    static Notification node(Context context, String statusLine) {
        PendingIntent open = openApp(context, 0, null);
        PendingIntent quit = PendingIntent.getService(context, 1,
                new Intent(context, NodeService.class).setAction(NodeService.ACTION_QUIT),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Icon icon = Icon.createWithResource(context, R.drawable.ic_stat_themesh);
        Notification.Builder b = new Notification.Builder(context, CHANNEL_NODE)
                .setSmallIcon(R.drawable.ic_stat_themesh)
                .setContentTitle(context.getString(R.string.fgs_title))
                .setContentText(statusLine)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(icon, context.getString(R.string.action_open), open).build())
                .addAction(new Notification.Action.Builder(icon, context.getString(R.string.action_quit), quit).build());
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    /** Уведомление о событии; нажатие открывает окно и переводит интерфейс на {@code notice.route}. */
    static void post(Context context, Notice notice) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        Notification.Builder b = new Notification.Builder(context, CHANNEL_EVENTS)
                .setSmallIcon(R.drawable.ic_stat_themesh)
                .setContentTitle(notice.title)
                .setContentText(notice.body)
                .setStyle(new Notification.BigTextStyle().bigText(notice.body))
                .setContentIntent(openApp(context, notice.tag.hashCode(), notice.route))
                .setAutoCancel(true)
                .setCategory(category(notice.kind));
        nm.notify(notice.tag, ID_EVENT, b.build());
    }

    /** «Файл сохранён»: нажатие открывает файл. */
    static void downloadSaved(Context context, Uri uri, String mime, String name) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        Notification.Builder b = new Notification.Builder(context, CHANNEL_DOWNLOADS)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.notif_saved_title))
                .setContentText(name)
                .setAutoCancel(true);
        if (uri != null) {
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime == null || mime.isEmpty() ? "*/*" : mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            b.setContentIntent(PendingIntent.getActivity(context, uri.hashCode(), view,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        nm.notify("download:" + name, ID_DOWNLOAD, b.build());
    }

    /** Intent окна приложения; {@code route} (если есть) перед выполнением проверит MainActivity. */
    private static PendingIntent openApp(Context context, int requestCode, String route) {
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (route != null) {
            open.putExtra(MainActivity.EXTRA_ROUTE, route);
        }
        return PendingIntent.getActivity(context, requestCode, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static String category(Notice.Kind kind) {
        switch (kind) {
            case CHAT:
                return Notification.CATEGORY_MESSAGE;
            case MAIL:
                return Notification.CATEGORY_EMAIL;
            default:
                return Notification.CATEGORY_STATUS;
        }
    }
}
