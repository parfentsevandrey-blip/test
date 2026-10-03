package app.themesh.mobile.core;

import java.util.List;

/**
 * Одна короткая фраза о сети для уведомления службы: «На связи 2 из 3», «Сеть ещё не создана».
 * Та же логика, что в desktop/src/status.js; в отличие от неё, пока состояние ещё не получено,
 * говорит «Запускаем…», а не «Сеть ещё не создана» (иначе у уже созданной сети на секунду мелькала бы ложь).
 */
public final class StatusLine {
    private StatusLine() {
    }

    public static String text(Texts t, NodeStatus status, NodeState state) {
        if (status == NodeStatus.IDLE || status == NodeStatus.STARTING) {
            return t.statusStarting();
        }
        if (status != NodeStatus.RUNNING) {
            return t.statusStopped();
        }
        if (state == null || !state.known()) {
            return t.statusStarting();
        }
        if (!state.configured()) {
            return t.statusNoNetwork();
        }
        List<NodeState.Peer> peers = state.peers();
        if (peers.isEmpty()) {
            return t.statusAlone();
        }
        int online = 1; // это устройство
        for (NodeState.Peer p : peers) {
            if (p.online) {
                online++;
            }
        }
        return t.statusOnline(online, peers.size() + 1);
    }
}
