package app.themesh.mobile.core;

/**
 * Что можно загружать внутри окна: только веб-интерфейс узла на своём адресе (http://127.0.0.1:порт) и
 * то, что страница сама делает из своих данных (blob:, data:, about:blank). Всё остальное — не в окно:
 * ссылки на сайты открываются в браузере, прочие запросы блокируются.
 * Сравниваются строки, а не разобранные адреса: «http://127.0.0.1:8777@evil.example/» и «…:87771/» не проходят.
 */
public final class OriginPolicy {
    private OriginPolicy() {
    }

    /** Адрес принадлежит интерфейсу узла (сам адрес, его путь, запрос или фрагмент). */
    public static boolean isInternal(String origin, String url) {
        if (origin == null || origin.isEmpty() || url == null || !url.startsWith(origin)) {
            return false;
        }
        if (url.length() == origin.length()) {
            return true;
        }
        char next = url.charAt(origin.length());
        return next == '/' || next == '?' || next == '#';
    }

    /** Можно ли выполнить такой запрос страницы (подресурс или переход). */
    public static boolean allowsRequest(String origin, String url) {
        if (isInternal(origin, url)) {
            return true;
        }
        if (url == null) {
            return false;
        }
        return url.equals("about:blank") || url.startsWith("data:") || isInternal(origin, stripBlob(url));
    }

    private static String stripBlob(String url) {
        return url.startsWith("blob:") ? url.substring("blob:".length()) : "";
    }

    /** Ссылка, которую стоит отдать системному браузеру (http или https). */
    public static boolean isWebLink(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase(java.util.Locale.ROOT);
        return u.startsWith("http://") || u.startsWith("https://");
    }
}
