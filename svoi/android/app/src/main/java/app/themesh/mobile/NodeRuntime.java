package app.themesh.mobile;

import android.os.Handler;
import android.os.Looper;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import app.themesh.mobile.core.NodeApi;
import app.themesh.mobile.core.NodeState;
import app.themesh.mobile.core.NodeStatus;
import app.themesh.mobile.core.NodeSupervisor;

/**
 * Что сейчас с узлом — одно на весь процесс (служба и окно живут в одном процессе). Служба пишет сюда,
 * окно читает и подписывается на изменения. Мастер-токен здесь не хранится вообще: доступ к узлу —
 * объект {@link NodeApi}, который сам его прячет.
 */
final class NodeRuntime {
    interface Listener {
        /** Состояние узла изменилось (вызывается в главном потоке). */
        void onNodeChanged();
    }

    private static final NodeRuntime INSTANCE = new NodeRuntime();

    static NodeRuntime get() {
        return INSTANCE;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Runnable fire = this::fireNow;
    private volatile NodeStatus status = NodeStatus.IDLE;
    private volatile NodeApi api;
    private volatile NodeSupervisor.Failure failure;
    private volatile int failures;
    private volatile NodeState state = new NodeState();
    private volatile boolean quitRequested;
    private volatile boolean uiForeground;

    private NodeRuntime() {
    }

    NodeStatus status() {
        return status;
    }

    /** Доступ к узлу, пока он {@link NodeStatus#RUNNING}, иначе {@code null}. */
    NodeApi api() {
        return status == NodeStatus.RUNNING ? api : null;
    }

    NodeSupervisor.Failure failure() {
        return failure;
    }

    /** Сколько попыток подряд не удалось с последнего успешного запуска. */
    int failures() {
        return failures;
    }

    NodeState state() {
        return state;
    }

    /** Человек нажал «Выйти»: окно должно закрыться. */
    boolean quitRequested() {
        return quitRequested;
    }

    /** Окно сейчас на экране и в фокусе: уведомлять о событиях не нужно, интерфейс показывает их сам. */
    boolean uiForeground() {
        return uiForeground;
    }

    void setUiForeground(boolean value) {
        uiForeground = value;
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ---- пишет служба -----------------------------------------------------------------------

    /** Узел просят запустить (окно открыто, загрузка системы): забываем «Выйти» и показываем «Запускаем». */
    void requestStart() {
        quitRequested = false;
        if (status == NodeStatus.STOPPED || status == NodeStatus.IDLE) {
            status = NodeStatus.STARTING;
            changed();
        }
    }

    void starting() {
        status = NodeStatus.STARTING;
        quitRequested = false;
        changed();
    }

    void ready(NodeApi newApi, NodeState newState) {
        api = newApi;
        state = newState;
        failure = null;
        failures = 0;
        status = NodeStatus.RUNNING;
        changed();
    }

    void failed(NodeSupervisor.Failure f) {
        failure = f;
        failures++;
        api = null;
        status = NodeStatus.FAILED;
        changed();
    }

    void stopped(boolean byUser) {
        api = null;
        quitRequested = byUser;
        status = NodeStatus.STOPPED;
        changed();
    }

    /** Изменились данные сети (кто на связи), статус прежний. */
    void changed() {
        main.removeCallbacks(fire);
        main.post(fire);
    }

    private void fireNow() {
        for (Listener l : listeners) {
            l.onNodeChanged();
        }
    }
}
