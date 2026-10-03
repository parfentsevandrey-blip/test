package app.themesh.mobile.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Читает тело ответа {@code text/event-stream} и отдаёт сообщения по мере их прихода. Строки могут
 * кончаться на «\n», «\r» или «\r\n»; границы сетевых порций и многобайтных символов UTF-8 не важны.
 */
public final class SseStream {
    /** Получатель сообщений. */
    public interface Listener {
        void onEvent(SseEvent event);
    }

    /** Предел длины одной строки: узел присылает списки устройств, но не мегабайты. */
    static final int MAX_LINE = 4 << 20;

    private SseStream() {
    }

    /**
     * Читает поток до конца (возвращается, когда сервер закрыл соединение) или до ошибки ввода-вывода
     * (например, SocketTimeoutException, если сервер замолчал и не шлёт даже keepalive).
     */
    public static void read(InputStream in, Listener listener) throws IOException {
        Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 8192);
        SseParser parser = new SseParser();
        boolean skipLf = false;
        StringBuilder line = new StringBuilder();
        for (;;) {
            int c = reader.read();
            if (c < 0) {
                return; // конец потока; недописанная строка и сообщение отбрасываются
            }
            if (skipLf) {
                skipLf = false;
                if (c == '\n') {
                    continue;
                }
            }
            if (c == '\n' || c == '\r') {
                skipLf = c == '\r';
                SseEvent ev = parser.accept(line.toString());
                line.setLength(0);
                if (ev != null) {
                    listener.onEvent(ev);
                }
                continue;
            }
            if (line.length() >= MAX_LINE) {
                throw new IOException("строка потока событий длиннее " + MAX_LINE + " символов");
            }
            line.append((char) c);
        }
    }
}
