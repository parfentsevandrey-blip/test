package app.themesh.mobile;

import android.content.Context;
import android.content.SharedPreferences;

/** Немногие настройки самого приложения (настройки сети живут в узле, а не здесь). */
final class Prefs {
    private static final String FILE = "themesh";
    private static final String AUTOSTART = "autostart";
    private static final String LAST_PORT = "lastPort";
    private static final String NOTIF_ASKED = "notifAsked";
    private static final String WEBVIEW_WARNED = "webViewWarned";
    private static final String COPY_RECEIVED = "copyReceived";
    private static final String SKY_TOP = "skyTop";
    private static final String SKY_BOTTOM = "skyBottom";

    private final SharedPreferences prefs;

    Prefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** «Запускать при включении телефона»; по умолчанию выключено. */
    boolean autostart() {
        return prefs.getBoolean(AUTOSTART, false);
    }

    void setAutostart(boolean on) {
        prefs.edit().putBoolean(AUTOSTART, on).apply();
    }

    /** Порт веб-интерфейса прошлого запуска (0 — не помним). */
    int lastPort() {
        return prefs.getInt(LAST_PORT, 0);
    }

    void setLastPort(int port) {
        prefs.edit().putInt(LAST_PORT, port).apply();
    }

    /** Спрашивали ли уже разрешение на уведомления (спрашиваем один раз). */
    boolean notifAsked() {
        return prefs.getBoolean(NOTIF_ASKED, false);
    }

    void setNotifAsked() {
        prefs.edit().putBoolean(NOTIF_ASKED, true).apply();
    }

    /** «Сохранять полученные файлы в «Загрузки»»: копия каждого принятого файла в общей папке; по умолчанию включено. */
    boolean copyReceived() {
        return prefs.getBoolean(COPY_RECEIVED, true);
    }

    void setCopyReceived(boolean on) {
        prefs.edit().putBoolean(COPY_RECEIVED, on).apply();
    }

    /** Версия WebView, о которой уже предупредили (пусто — ни о какой). */
    String webViewWarned() {
        return prefs.getString(WEBVIEW_WARNED, "");
    }

    void setWebViewWarned(String version) {
        prefs.edit().putString(WEBVIEW_WARNED, version).apply();
    }

    /** Цвет неба «Росы» у верхнего края страницы, каким его видели в последний раз (0 — не помним). */
    int skyTop() {
        return prefs.getInt(SKY_TOP, 0);
    }

    /** То же у нижнего края. */
    int skyBottom() {
        return prefs.getInt(SKY_BOTTOM, 0);
    }

    /** Запоминает небо: заставка при следующем запуске будет того же цвета, что страница, а не чужого. */
    void setSky(int top, int bottom) {
        prefs.edit().putInt(SKY_TOP, top).putInt(SKY_BOTTOM, bottom).apply();
    }
}
