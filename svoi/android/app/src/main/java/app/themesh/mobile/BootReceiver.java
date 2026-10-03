package app.themesh.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * После включения телефона (и после обновления приложения) запускает узел, если человек включил в меню
 * «Запускать при включении телефона». По умолчанию выключено.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        if (new Prefs(context).autostart()) {
            NodeService.start(context);
        }
    }
}
