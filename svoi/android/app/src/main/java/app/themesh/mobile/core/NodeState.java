package app.themesh.mobile.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * То, что приложению нужно знать о сети: создана ли она, кто в ней и кто на связи. Обновляется из
 * снимка {@code GET /api/state} и событий {@code self} и {@code peers}.
 */
public final class NodeState {
    /** Другое устройство сети. */
    public static final class Peer {
        public final String id;
        public final String name;
        public final boolean online;

        public Peer(String id, String name, boolean online) {
            this.id = id;
            this.name = name;
            this.online = online;
        }
    }

    private boolean known;
    private boolean configured;
    private String version = "";
    private List<Peer> peers = Collections.emptyList();

    /** Пришёл ли уже хоть один снимок или событие. */
    public synchronized boolean known() {
        return known;
    }

    public synchronized boolean configured() {
        return configured;
    }

    /** Версия ядра (self.version) или пустая строка. */
    public synchronized String version() {
        return version;
    }

    public synchronized List<Peer> peers() {
        return peers;
    }

    /** Имя устройства по id или {@code null}, если такого не знаем. */
    public synchronized String peerName(String id) {
        for (Peer p : peers) {
            if (p.id.equals(id)) {
                return p.name;
            }
        }
        return null;
    }

    /** Ответ {@code GET /api/state}. */
    public synchronized void applySnapshot(JSONObject state) {
        known = true;
        JSONObject self = Json.obj(state, "self");
        Boolean cfg = Json.bool(state, "configured");
        if (cfg == null) {
            cfg = Json.bool(self, "configured");
        }
        configured = cfg != null && cfg;
        applySelfFields(self);
        JSONArray list = Json.arr(state, "peers");
        peers = parsePeers(list);
    }

    /** Событие {@code self}. */
    public synchronized void applySelf(JSONObject self) {
        known = true;
        Boolean cfg = Json.bool(self, "configured");
        if (cfg != null) {
            configured = cfg;
        }
        applySelfFields(self);
    }

    /** Событие {@code peers}: весь список целиком. */
    public synchronized void applyPeers(JSONArray list) {
        known = true;
        peers = parsePeers(list);
    }

    private void applySelfFields(JSONObject self) {
        String v = Json.str(self, "version");
        if (!v.isEmpty()) {
            version = v;
        }
    }

    private static List<Peer> parsePeers(JSONArray list) {
        if (list == null) {
            return Collections.emptyList();
        }
        List<Peer> out = new ArrayList<>(list.length());
        for (int i = 0; i < list.length(); i++) {
            JSONObject p = list.optJSONObject(i);
            if (p == null) {
                continue;
            }
            String id = Json.str(p, "id");
            if (id.isEmpty()) {
                continue;
            }
            Boolean online = Json.bool(p, "online");
            out.add(new Peer(id, Json.str(p, "name"), online != null && online));
        }
        return Collections.unmodifiableList(out);
    }
}
