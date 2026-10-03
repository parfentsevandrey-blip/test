package app.themesh.mobile.core;

/**
 * Версия системного WebView. Интерфейс использует {@code color-mix()}, {@code dvh} и {@code inert}
 * (docs/UI-NOTES.md): в Chromium 111 есть всё это. На более старом WebView (телефон без обновлений WebView, например
 * без Google Play) страницы выглядят сломанными, поэтому об этом стоит сказать человеку, а не молчать.
 */
public final class WebViewVersion {
    /** Первая версия Chromium, в которой есть всё, что нужно интерфейсу (поздней всех появилась {@code color-mix()}). */
    public static final int MIN_MAJOR = 111;

    /** Настоящий Chromium в WebView Android 8+ не старше 60-х; меньшие числа — чужая нумерация. */
    private static final int FIRST_KNOWN_MAJOR = 50;

    private WebViewVersion() {
    }

    /**
     * Главная версия Chromium из имени версии пакета WebView («113.0.5672.136» → 113) или -1, если имя
     * непривычное (сборка со своей нумерацией): тогда человека зря не пугаем.
     */
    public static int major(String versionName) {
        if (versionName == null) {
            return -1;
        }
        String s = versionName.trim();
        int digits = 0;
        while (digits < s.length() && s.charAt(digits) >= '0' && s.charAt(digits) <= '9') {
            digits++;
        }
        if (digits == 0 || digits > 3 || digits >= s.length() || s.charAt(digits) != '.') {
            return -1;
        }
        int major = Integer.parseInt(s.substring(0, digits));
        return major >= FIRST_KNOWN_MAJOR ? major : -1;
    }

    /** Версия известна и меньше {@link #MIN_MAJOR}. */
    public static boolean isTooOld(String versionName) {
        int major = major(versionName);
        return major >= 0 && major < MIN_MAJOR;
    }
}
