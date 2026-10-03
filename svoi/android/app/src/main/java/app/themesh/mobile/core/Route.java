package app.themesh.mobile.core;

import java.util.regex.Pattern;

/**
 * Маршрут внутри веб-интерфейса («#/chat/<id>»). Он приходит в Intent (его может прислать любое
 * приложение: MainActivity открыта для запуска) и выполняется как JavaScript в странице, поэтому
 * перед выполнением проверяется строгим регулярным выражением. То же, что в desktop/src/window.js,
 * плюс предел длины.
 */
public final class Route {
    public static final String HOME = "#/home";
    public static final String FILES_SEND = "#/files/send";
    public static final String MAIL_INBOX = "#/mail/inbox";

    private static final Pattern VALID = Pattern.compile("#/[A-Za-z0-9_\\-./?=&%]{0,200}");
    private static final Pattern DEVICE_ID = Pattern.compile("[a-z0-9]{1,64}");

    private Route() {
    }

    /** Можно ли выполнять этот маршрут. */
    public static boolean isValid(String route) {
        return route != null && VALID.matcher(route).matches();
    }

    /** Маршрут чата с устройством; если «id» выглядит подозрительно — общий список чатов. */
    public static String chat(String deviceId) {
        if (deviceId != null && DEVICE_ID.matcher(deviceId).matches()) {
            return "#/chat/" + deviceId;
        }
        return "#/chat";
    }

    /**
     * Скрипт, который переводит страницу на маршрут, или {@code null}, если маршрут не прошёл проверку.
     * Алфавит маршрута не содержит кавычек и обратной косой черты, поэтому подстановка безопасна.
     */
    public static String script(String route) {
        if (!isValid(route)) {
            return null;
        }
        return "location.hash=\"" + route + "\"";
    }
}
