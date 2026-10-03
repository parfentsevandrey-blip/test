package app.themesh.mobile.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Живое соединение с потоком событий узла ({@code GET /api/events}) — тем же, которым пользуется веб-интерфейс.
 * Сначала открывается поток, затем берётся снимок {@code GET /api/state} (так не теряется ничего
 * из случившегося между ними), дальше события применяются к {@link NodeState}, а переданные и
 * полученные файлы, сообщения и письма отдаются слушателю. Если соединение рвётся, оно
 * восстанавливается (паузы 0,5 с, 1 с … 15 с).
 */
public final class EventWatcher {
    public interface Listener {
        /** Снимок, {@code self} или {@code peers}: {@link NodeState} изменился. */
        void onState(NodeState state);

        /** Событие {@code transfer}, {@code chat} или {@code mail}. */
        void onEvent(String kind, JSONObject data);

        /** Соединение с потоком установлено или потеряно. */
        void onConnection(boolean connected);
    }

    /** Узел шлёт keepalive каждые 15 секунд; молчание дольше этого срока значит обрыв. */
    static final int READ_TIMEOUT_MS = 45_000;

    private final NodeApi api;
    private final NodeState state;
    private final AppLog log;
    private final Listener listener;
    private final int readTimeoutMs;

    private Thread thread;
    private boolean stopped;
    private HttpURLConnection conn;

    public EventWatcher(NodeApi api, NodeState state, AppLog log, Listener listener) {
        this(api, state, log, listener, READ_TIMEOUT_MS);
    }

    EventWatcher(NodeApi api, NodeState state, AppLog log, Listener listener, int readTimeoutMs) {
        this.api = api;
        this.state = state;
        this.log = log;
        this.listener = listener;
        this.readTimeoutMs = readTimeoutMs;
    }

    public synchronized void start() {
        if (thread != null) {
            throw new IllegalStateException("already started");
        }
        thread = new Thread(this::run, "themesh-events");
        thread.setDaemon(true);
        thread.start();
    }

    /** Останавливает чтение (не ждёт): обрывает соединение, поток завершится сам. */
    public void stop() {
        HttpURLConnection c;
        synchronized (this) {
            stopped = true;
            c = conn;
            notifyAll();
        }
        if (c != null) {
            c.disconnect();
        }
    }

    public boolean awaitTermination(long timeoutMs) throws InterruptedException {
        Thread t;
        synchronized (this) {
            t = thread;
        }
        if (t == null) {
            return true;
        }
        t.join(timeoutMs);
        return !t.isAlive();
    }

    /** Перечитывает {@code GET /api/state} (например, когда пишет устройство, которого мы ещё не знаем). */
    public void refresh() throws IOException {
        state.applySnapshot(api.getObject("/api/state"));
        listener.onState(state);
    }

    private synchronized boolean isStopped() {
        return stopped;
    }

    private void run() {
        long delay = 500;
        int failures = 0;
        while (!isStopped()) {
            HttpURLConnection c = null;
            long connectedAt = 0;
            try {
                c = api.openEvents(readTimeoutMs);
                synchronized (this) {
                    if (stopped) {
                        c.disconnect();
                        return;
                    }
                    conn = c;
                }
                try {
                    refresh();
                } catch (IOException e) {
                    log.w("events: cannot read the state: " + e.getMessage());
                }
                connectedAt = System.nanoTime();
                failures = 0;
                fireConnection(true);
                SseStream.read(c.getInputStream(), this::dispatch);
            } catch (IOException | RuntimeException e) {
                if (isStopped()) {
                    return;
                }
                failures++;
                if (failures <= 3 || failures % 20 == 0) {
                    log.w("events: connection lost: " + e);
                }
            } finally {
                synchronized (this) {
                    conn = null;
                }
                if (c != null) {
                    c.disconnect();
                }
            }
            fireConnection(false);
            if (connectedAt != 0 && (System.nanoTime() - connectedAt) > 10_000_000_000L) {
                delay = 500; // жило долго: это обычный обрыв, а не цикл отказов
            } else {
                delay = Math.min(delay * 2, 15_000);
            }
            if (!pause(delay)) {
                return;
            }
        }
    }

    private synchronized boolean pause(long ms) {
        long end = System.nanoTime() + ms * 1_000_000;
        while (!stopped) {
            long left = (end - System.nanoTime()) / 1_000_000;
            if (left <= 0) {
                break;
            }
            try {
                wait(left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopped = true;
            }
        }
        return !stopped;
    }

    private void dispatch(SseEvent ev) {
        try {
            switch (ev.event) {
                case "self":
                    state.applySelf(new JSONObject(ev.data));
                    listener.onState(state);
                    break;
                case "peers":
                    state.applyPeers(new JSONArray(ev.data));
                    listener.onState(state);
                    break;
                case "transfer":
                case "chat":
                case "mail":
                    listener.onEvent(ev.event, new JSONObject(ev.data));
                    break;
                default:
                    break; // hello, counters, notify, invites… приложению не нужны
            }
        } catch (JSONException e) {
            log.w("events: cannot parse '" + ev.event + "'");
        } catch (RuntimeException e) {
            log.e("events: handler for '" + ev.event + "' failed", e);
        }
    }

    private void fireConnection(boolean connected) {
        try {
            listener.onConnection(connected);
        } catch (RuntimeException e) {
            log.e("events: listener failed", e);
        }
    }
}
