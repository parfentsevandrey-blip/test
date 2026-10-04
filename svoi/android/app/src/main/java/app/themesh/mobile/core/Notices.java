package app.themesh.mobile.core;

import org.json.JSONObject;

import java.io.IOException;

/**
 * Какие события узла заслуживают уведомления и как они выглядят. Логика та же, что в
 * desktop/src/notify.js (notificationFor): предложенный и полученный файл, сообщение чата от
 * другого устройства, новое непрочитанное письмо во «Входящих», просьба устройства рядом добавить его в сеть.
 */
public final class Notices {
    /** Откуда берутся имена устройств и тексты писем. */
    public interface Lookup {
        /** Имя устройства по id; пустая строка, если неизвестно. */
        String peerName(String peerId);

        /** Письмо целиком ({@code GET /api/mail/<id>}). */
        JSONObject mail(String id) throws IOException;
    }

    /** Длина тела уведомления, как на компьютере. */
    static final int CLIP = 140;

    private Notices() {
    }

    /**
     * @param kind событие: «transfer», «chat», «mail» или «nearby» (одна просьба из списка просьб; остальные
     *             уведомлений не порождают)
     * @param d    данные события (docs/UI-API.md)
     * @return уведомление или {@code null}
     */
    public static Notice forEvent(String kind, JSONObject d, Texts t, Lookup lookup) {
        if (d == null) {
            return null;
        }
        switch (kind) {
            case "transfer":
                return transfer(d, t, lookup);
            case "chat":
                return chat(d, t, lookup);
            case "mail":
                return mail(d, t, lookup);
            case "nearby":
                return nearby(d, t);
            default:
                return null;
        }
    }

    private static Notice transfer(JSONObject d, Texts t, Lookup lookup) {
        if (!"in".equals(Json.str(d, "dir"))) {
            return null;
        }
        String id = Json.str(d, "id");
        String state = Json.str(d, "state");
        String name = Json.str(d, "name");
        String peer = Json.str(d, "peerName");
        if (peer.isEmpty()) {
            peer = peerName(lookup, Json.str(d, "peer"));
        }
        if ("offered".equals(state)) {
            String key = "offer:" + id;
            return new Notice(Notice.Kind.OFFER, key, key, peer.isEmpty() ? t.appName() : peer, t.offerBody(name), Route.HOME);
        }
        if ("done".equals(state)) {
            String key = "done:" + id;
            return new Notice(Notice.Kind.RECEIVED, key, key, t.receivedTitle(), t.receivedBody(name, peer), Route.FILES_SEND);
        }
        return null;
    }

    private static Notice chat(JSONObject d, Texts t, Lookup lookup) {
        Boolean mine = Json.bool(d, "mine");
        String id = Json.str(d, "id");
        if (mine == null || mine || id.isEmpty()) {
            return null; // только сообщения от других устройств
        }
        String peerId = Json.str(d, "peer");
        String peer = peerName(lookup, peerId);
        String body = clip(Json.str(d, "text"), CLIP);
        if (body.isEmpty()) {
            body = t.chatAttachment();
        }
        return new Notice(Notice.Kind.CHAT, "chat:" + id, "chat:" + peerId, peer.isEmpty() ? t.appName() : peer, body, Route.chat(peerId));
    }

    private static Notice mail(JSONObject d, Texts t, Lookup lookup) {
        Boolean unread = Json.bool(d, "unread");
        String id = Json.str(d, "id");
        if (!"inbox".equals(Json.str(d, "folder")) || unread == null || !unread || id.isEmpty()) {
            return null;
        }
        JSONObject m;
        try {
            m = lookup.mail(id);
        } catch (IOException | RuntimeException e) {
            return null; // письма уже может не быть
        }
        if (m == null) {
            return null;
        }
        String from = Json.str(Json.obj(m, "from"), "name");
        String subject = clip(Json.str(m, "subject"), CLIP);
        String key = "mail:" + id;
        return new Notice(Notice.Kind.MAIL, key, key, from.isEmpty() ? t.mailTitleUnknown() : t.mailTitle(from),
                subject.isEmpty() ? t.noSubject() : subject, Route.MAIL_INBOX);
    }

    /** Устройство рядом просит добавить его: имя и шесть цифр, которые нужно сверить с его экраном. */
    private static Notice nearby(JSONObject d, Texts t) {
        String id = Json.str(d, "id");
        String code = Json.str(d, "code");
        if (id.isEmpty() || code.isEmpty()) {
            return null;
        }
        if (code.matches("\\d{6}")) {
            code = code.substring(0, 3) + " " + code.substring(3);
        }
        String name = clip(Json.str(d, "name"), 40);
        String key = "nearby:" + id;
        return new Notice(Notice.Kind.NEARBY, key, key, t.nearbyTitle(name.isEmpty() ? "?" : name), t.nearbyBody(code), Route.HOME);
    }

    private static String peerName(Lookup lookup, String id) {
        String name = lookup.peerName(id);
        return name == null ? "" : name;
    }

    /** Пробелы и переводы строк сводятся к одному пробелу; длинное обрезается с «…». */
    public static String clip(String s, int max) {
        String x = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        if (x.length() <= max) {
            return x;
        }
        int cut = max - 1;
        if (cut > 0 && Character.isHighSurrogate(x.charAt(cut - 1))) {
            cut--; // не разрезать суррогатную пару (эмодзи)
        }
        return x.substring(0, cut) + "…";
    }
}
