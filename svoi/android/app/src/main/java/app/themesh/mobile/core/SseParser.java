package app.themesh.mobile.core;

/**
 * Разбор потока {@code text/event-stream} строка за строкой (https://html.spec.whatwg.org/multipage/server-sent-events.html).
 * Пустая строка завершает сообщение; строки, начинающиеся с «:», — комментарии (так узел шлёт
 * keepalive); несколько строк {@code data:} склеиваются через «\n»; тип по умолчанию — «message».
 * Недописанное сообщение (поток оборвался до пустой строки) не выдаётся.
 */
public final class SseParser {
    private String event = "message";
    private StringBuilder data;

    /**
     * Принимает очередную строку (без перевода строки) и возвращает готовое сообщение, если эта
     * строка его завершила, иначе {@code null}.
     */
    public SseEvent accept(String line) {
        if (line.isEmpty()) {
            SseEvent out = null;
            if (data != null) {
                out = new SseEvent(event, data.toString());
            }
            event = "message";
            data = null;
            return out;
        }
        if (line.charAt(0) == ':') {
            return null;
        }
        String field = line;
        String value = "";
        int colon = line.indexOf(':');
        if (colon >= 0) {
            field = line.substring(0, colon);
            value = line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
        }
        switch (field) {
            case "event":
                event = value.trim();
                if (event.isEmpty()) {
                    event = "message";
                }
                break;
            case "data":
                if (data == null) {
                    data = new StringBuilder(value);
                } else {
                    data.append('\n').append(value);
                }
                break;
            default:
                // id:, retry: и всё незнакомое нам не нужно
                break;
        }
        return null;
    }
}
