package app.themesh.mobile.core;

import java.util.Objects;

/** Одно сообщение потока Server-Sent Events: {@code event: <type>} и {@code data: <json>}. */
public final class SseEvent {
    public final String event;
    public final String data;

    public SseEvent(String event, String data) {
        this.event = event;
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SseEvent)) {
            return false;
        }
        SseEvent e = (SseEvent) o;
        return event.equals(e.event) && data.equals(e.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event, data);
    }

    @Override
    public String toString() {
        return "SseEvent{" + event + ": " + data + "}";
    }
}
