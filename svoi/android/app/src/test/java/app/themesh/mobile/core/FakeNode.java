package app.themesh.mobile.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Подделка узла для тестов: тот же HTTP-контракт (handshake, вход по токену, состояние, поток событий), но
 * настраиваемый и записывающий, что у него просили. Слушает 127.0.0.1 на свободном порту. Написана на голых
 * сокетах: в модульных тестах Android доступен только API, который есть в android.jar (нет com.sun.net.httpserver).
 */
final class FakeNode implements AutoCloseable {
    static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef";

    /** Один запрос, как его увидел сервер. */
    static final class Seen {
        final String method;
        final String path;
        final String authorization;
        final String themesh;
        final String contentType;
        final String body;

        Seen(String method, String path, String authorization, String themesh, String contentType, String body) {
            this.method = method;
            this.path = path;
            this.authorization = authorization;
            this.themesh = themesh;
            this.contentType = contentType;
            this.body = body;
        }
    }

    /** Одно открытое соединение с потоком событий. */
    static final class Stream {
        static final String CLOSE = "\u0000close";
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        void event(String name, String data) {
            queue.add("event: " + name + "\ndata: " + data + "\n\n");
        }

        void close() {
            queue.add(CLOSE);
        }
    }

    final String origin;
    final List<Seen> seen = new CopyOnWriteArrayList<>();
    final List<Stream> streams = new CopyOnWriteArrayList<>();
    volatile String proofToken = TOKEN;
    volatile int handshakeStatus = 200;
    volatile String handshakeBody;
    volatile String state = "{\"configured\":false,\"self\":{\"configured\":false,\"version\":\"9.9.9\"},\"peers\":[]}";
    volatile String eventsContentType = "text/event-stream";
    volatile String loginBody;

    private final ServerSocket listener;
    private final List<Socket> open = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    FakeNode() throws IOException {
        listener = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        origin = "http://127.0.0.1:" + listener.getLocalPort();
        Thread acceptor = new Thread(this::acceptLoop, "fake-node-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    NodeApi api() {
        return new NodeApi(origin, TOKEN);
    }

    /** Подождать, пока откроется {@code n}-е соединение с потоком событий. */
    Stream awaitStream(int n, long timeoutMs) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (streams.size() < n) {
            if (System.nanoTime() > end) {
                throw new AssertionError("соединение №" + n + " с потоком событий не открылось за " + timeoutMs + " мс");
            }
            Thread.sleep(10);
        }
        return streams.get(n - 1);
    }

    List<Seen> requestsTo(String path) {
        List<Seen> out = new ArrayList<>();
        for (Seen s : seen) {
            if (s.path.equals(path)) {
                out.add(s);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = listener.accept();
                open.add(s);
                Thread t = new Thread(() -> serve(s), "fake-node-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (Socket s = socket) {
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String target = parts[1];
            String authorization = null;
            String themesh = null;
            String contentType = null;
            int length = 0;
            for (String line = readLine(in); line != null && !line.isEmpty(); line = readLine(in)) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                switch (name) {
                    case "authorization":
                        authorization = value;
                        break;
                    case "x-themesh":
                        themesh = value;
                        break;
                    case "content-type":
                        contentType = value;
                        break;
                    case "content-length":
                        length = Integer.parseInt(value);
                        break;
                    default:
                        break;
                }
            }
            byte[] body = new byte[length];
            for (int got = 0; got < length; ) {
                int n = in.read(body, got, length - got);
                if (n < 0) {
                    break;
                }
                got += n;
            }
            int q = target.indexOf('?');
            String path = q >= 0 ? target.substring(0, q) : target;
            String query = q >= 0 ? target.substring(q + 1) : "";
            seen.add(new Seen(method, path, authorization, themesh, contentType, new String(body, StandardCharsets.UTF_8)));
            route(out, path, query, authorization);
        } catch (IOException e) {
            // клиент ушёл
        } finally {
            open.remove(socket);
        }
    }

    private void route(OutputStream out, String path, String query, String authorization) throws IOException {
        boolean authed = ("Bearer " + TOKEN).equals(authorization);
        switch (path) {
            case "/api/handshake":
                handshake(out, query);
                return;
            case "/api/login/code":
                if (authed) {
                    respond(out, 200, loginBody != null ? loginBody
                            : "{\"code\":\"" + "ab12".repeat(12) + "\",\"expiresIn\":600,\"singleUse\":true,\"sessionTtl\":1209600,\"bound\":false}");
                } else {
                    unauthorized(out);
                }
                return;
            case "/api/state":
                if (authed) {
                    respond(out, 200, state);
                } else {
                    unauthorized(out);
                }
                return;
            case "/api/mail/m1":
                if (authed) {
                    respond(out, 200, "{\"id\":\"m1\",\"from\":{\"name\":\"nas\"},\"subject\":\"Hello\"}");
                } else {
                    unauthorized(out);
                }
                return;
            case "/api/events":
                if (authed) {
                    events(out);
                } else {
                    unauthorized(out);
                }
                return;
            case "/redir":
                write(out, "HTTP/1.1 302 Found\r\nLocation: " + origin + "/api/state\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
                return;
            case "/api/boom":
                respond(out, 500, "{\"error\":{\"code\":\"internal\",\"message\":\"boom\"}}");
                return;
            default:
                respond(out, 404, "{\"error\":{\"code\":\"notfound\"}}");
        }
    }

    private void handshake(OutputStream out, String query) throws IOException {
        if (handshakeStatus == 302) {
            write(out, "HTTP/1.1 302 Found\r\nLocation: " + origin + "/redir-target\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
            return;
        }
        if (handshakeStatus != 200) {
            respond(out, handshakeStatus, "{}");
            return;
        }
        if (handshakeBody != null) {
            respond(out, 200, handshakeBody);
            return;
        }
        String nonce = query.startsWith("n=") ? query.substring(2) : "";
        respond(out, 200, "{\"proof\":\"" + Handshake.proof(proofToken, nonce) + "\"}");
    }

    private void unauthorized(OutputStream out) throws IOException {
        respond(out, 401, "{\"error\":{\"code\":\"unauthorized\",\"message\":\"sign in\"}}");
    }

    private void events(OutputStream out) throws IOException {
        Stream stream = new Stream();
        streams.add(stream);
        // без Content-Length и без chunked: тело идёт до закрытия соединения
        write(out, "HTTP/1.1 200 OK\r\nContent-Type: " + eventsContentType + "\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n");
        write(out, "retry: 2000\n\nevent: hello\ndata: {\"serverTime\":1}\n\n");
        try {
            for (;;) {
                String raw = stream.queue.poll(30, TimeUnit.SECONDS);
                if (raw == null || raw.equals(Stream.CLOSE)) {
                    return;
                }
                write(out, raw);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(OutputStream out, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : status == 401 ? "Unauthorized" : status == 404 ? "Not Found" : status == 500 ? "Internal Server Error" : "Status";
        write(out, "HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: application/json\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n");
        out.write(b);
        out.flush();
    }

    private static void write(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (;;) {
            int c = in.read();
            if (c < 0) {
                return buf.size() == 0 ? null : buf.toString("UTF-8");
            }
            if (c == '\n') {
                String s = buf.toString("UTF-8");
                return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
            }
            buf.write(c);
        }
    }

    @Override
    public void close() {
        closed = true;
        for (Stream s : streams) {
            s.close();
        }
        try {
            listener.close();
        } catch (IOException ignored) {
            // закрываем
        }
        for (Socket s : open) {
            try {
                s.close();
            } catch (IOException ignored) {
                // закрываем
            }
        }
    }
}
