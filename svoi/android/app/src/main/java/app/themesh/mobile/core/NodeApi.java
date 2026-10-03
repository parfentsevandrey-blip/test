package app.themesh.mobile.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Разговор приложения с узлом по HTTP на 127.0.0.1. Мастер-токен (ui.token) известен только этому
 * объекту: он уходит в заголовок Authorization и больше никуда — ни в адрес, ни в Intent, ни в журнал,
 * ни в страницу. Объект создаётся только для узла, прошедшего {@link #verify}. Перенаправления
 * не выполняются никогда: токен нельзя отправить туда, куда «отправил» чужой ответ.
 */
public final class NodeApi {
    /** Ответ узла с кодом не 2xx (или неожиданный ответ). */
    public static final class ApiException extends IOException {
        public final int status;

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final Pattern ORIGIN = Pattern.compile("http://127\\.0\\.0\\.1:[0-9]{1,5}");
    private static final Pattern CODE = Pattern.compile("[0-9a-f]{32,128}");
    private static final int MAX_BODY = 8 << 20;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final String origin;
    private final String token;

    /** @param origin «http://127.0.0.1:8777» */
    public NodeApi(String origin, String token) {
        if (origin == null || !ORIGIN.matcher(origin).matches()) {
            throw new IllegalArgumentException("узел ищут только на 127.0.0.1: " + origin);
        }
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("пустой токен");
        }
        this.origin = origin;
        this.token = token;
    }

    public String origin() {
        return origin;
    }

    /** Узел с таким адресом знает токен? Токен при этом никуда не отправляется. */
    public static boolean verify(String origin, String token) {
        if (origin == null || !ORIGIN.matcher(origin).matches() || token == null || token.isEmpty()) {
            return false;
        }
        HttpURLConnection c = null;
        try {
            String nonce = Handshake.newNonce();
            c = open(origin + "/api/handshake?n=" + nonce, "GET", 3_000, 3_000);
            if (c.getResponseCode() != 200) {
                return false;
            }
            JSONObject o = new JSONObject(readAll(c.getInputStream(), 4096));
            return Handshake.matches(Json.str(o, "proof"), Handshake.proof(token, nonce));
        } catch (IOException | JSONException | RuntimeException e) {
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    /** {@code GET path} с токеном; тело ответа — JSON-объект. */
    public JSONObject getObject(String path) throws IOException {
        return parse(request("GET", path, null));
    }

    /** {@code POST path} с JSON-телом и токеном. */
    public JSONObject postObject(String path, JSONObject body) throws IOException {
        return parse(request("POST", path, body.toString().getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Разовый код входа (10 минут, один раз). Без {"local":true}: такой код привязывался бы к пользователю
     * по /proc/net/tcp, а приложению Android читать его нельзя.
     */
    public String loginCode() throws IOException {
        String code = Json.str(postObject("/api/login/code", new JSONObject()), "code");
        if (!CODE.matcher(code).matches()) {
            throw new ApiException(200, "узел дал странный код входа");
        }
        return code;
    }

    /** Адрес, который открывает веб-интерфейс и входит в него: {@code origin/?t=<код>}. */
    public String loginUrl() throws IOException {
        return origin + "/?t=" + loginCode();
    }

    /**
     * Открывает поток событий. Читать его (и закрывать) должен вызывающий; {@code readTimeoutMs} должен
     * быть больше паузы между keepalive (узел шлёт их каждые 15 секунд).
     */
    public HttpURLConnection openEvents(int readTimeoutMs) throws IOException {
        HttpURLConnection c = open(origin + "/api/events", "GET", CONNECT_TIMEOUT_MS, readTimeoutMs);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "text/event-stream");
        try {
            int code = c.getResponseCode();
            if (code != 200) {
                throw new ApiException(code, "события: HTTP " + code);
            }
            String type = c.getContentType();
            if (type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("text/event-stream")) {
                throw new ApiException(code, "события: неожиданный тип ответа");
            }
            return c;
        } catch (IOException | RuntimeException e) {
            c.disconnect();
            throw e;
        }
    }

    private String request(String method, String path, byte[] body) throws IOException {
        HttpURLConnection c = open(origin + path, method, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        try {
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("X-Themesh", "1");
            c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body);
                }
            }
            int code = c.getResponseCode();
            if (code >= 200 && code < 300) {
                return readAll(c.getInputStream(), MAX_BODY);
            }
            if (code >= 300 && code < 400) {
                throw new ApiException(code, method + " " + path + ": перенаправление не выполняется");
            }
            String text = "";
            InputStream err = c.getErrorStream();
            if (err != null) {
                text = readAll(err, 2048);
            }
            throw new ApiException(code, method + " " + path + ": HTTP " + code + (text.isEmpty() ? "" : " " + text));
        } finally {
            c.disconnect();
        }
    }

    /** Соединение без заголовков авторизации: адрес проверен, перенаправления выключены, прокси не нужен. */
    private static HttpURLConnection open(String url, String method, int connectMs, int readMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
        c.setInstanceFollowRedirects(false);
        c.setUseCaches(false);
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setRequestMethod(method);
        return c;
    }

    private static JSONObject parse(String text) throws IOException {
        try {
            return new JSONObject(text);
        } catch (JSONException e) {
            throw new IOException("узел ответил не JSON: " + e.getMessage());
        }
    }

    static String readAll(InputStream in, int max) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                if (out.size() + n > max) {
                    throw new IOException("ответ длиннее " + max + " байт");
                }
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
