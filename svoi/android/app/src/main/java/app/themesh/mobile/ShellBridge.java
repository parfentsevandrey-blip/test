package app.themesh.mobile;

import android.webkit.JavascriptInterface;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import app.themesh.mobile.core.OriginPolicy;
import app.themesh.mobile.core.PageLook;

/**
 * Объект {@code window.themeshShell} в странице интерфейса: то, что страница просит у окна приложения, пока оно
 * показывает её на весь экран и без своей верхней полосы. {@link #menu()} — открыть меню приложения («⋮»: запуск
 * вместе с телефоном, батарея, журнал, «О программе»), {@link #look(String, String)} — «страница сейчас светлая или
 * тёмная, стеклянная или обычная»: окно красит под это системные панели и фон за прозрачной страницей.
 *
 * <p>Методы вызываются из служебного потока WebView: всё, что трогает окно, уходит в главный поток. Просьбы
 * выполняются, только если сейчас в окне интерфейс узла (адрес {@code http://127.0.0.1:<порт>/…}): чужая страница,
 * если бы она сюда попала, ничего не откроет и ничего не перекрасит. Всё окружение — через интерфейсы, поэтому
 * класс проверяется на компьютере.
 */
final class ShellBridge {
    /** Имя объекта в странице: {@code window.themeshShell}. */
    static final String NAME = "themeshShell";

    /** Что окно делает по просьбам страницы; вызывается в главном потоке. */
    interface Host {
        void openMenu();

        void look(PageLook look);
    }

    private final Executor mainThread;
    private final Supplier<String> pageUrl;
    private final Supplier<String> nodeOrigin;
    private final Host host;

    /**
     * @param mainThread выполняет задачу в главном потоке
     * @param pageUrl    адрес страницы, которая сейчас в окне ({@code WebView.getUrl()}); вызывается только в главном потоке
     * @param nodeOrigin адрес узла ({@code http://127.0.0.1:<порт>}), с которого окно загрузило интерфейс; тоже в главном потоке
     * @param host       что делать с просьбами
     */
    ShellBridge(Executor mainThread, Supplier<String> pageUrl, Supplier<String> nodeOrigin, Host host) {
        this.mainThread = mainThread;
        this.pageUrl = pageUrl;
        this.nodeOrigin = nodeOrigin;
        this.host = host;
    }

    private boolean fromTheNodesPage() {
        return OriginPolicy.isInternal(nodeOrigin.get(), pageUrl.get());
    }

    /** Открывает меню приложения. */
    @JavascriptInterface
    public void menu() {
        mainThread.execute(() -> {
            if (fromTheNodesPage()) {
                host.openMenu();
            }
        });
    }

    /**
     * Страница сообщает свой вид: тема «light» или «dark», вид «glass» или «classic». Всё остальное игнорируется.
     */
    @JavascriptInterface
    public void look(String theme, String skin) {
        PageLook look = PageLook.of(skin, theme);
        if (look == null) {
            return;
        }
        mainThread.execute(() -> {
            if (fromTheNodesPage()) {
                host.look(look);
            }
        });
    }
}
