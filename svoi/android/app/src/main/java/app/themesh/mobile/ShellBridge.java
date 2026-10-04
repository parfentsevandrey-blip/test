package app.themesh.mobile;

import android.webkit.JavascriptInterface;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import app.themesh.mobile.core.OriginPolicy;
import app.themesh.mobile.core.PageLook;
import app.themesh.mobile.core.ThemeColor;

/**
 * Объект {@code window.themeshShell} в странице интерфейса: то, что страница просит у окна приложения, пока оно
 * показывает её на весь экран и без своей верхней полосы. {@link #menu()} — открыть меню приложения («⋮»: запуск
 * вместе с телефоном, батарея, журнал, «О программе»), {@link #look(String, String)} — «страница сейчас светлая или
 * тёмная, «Роса», стеклянная или обычная»: окно красит под это системные панели и фон за прозрачной страницей,
 * {@link #sky(String, String, boolean)} — цвета неба «Росы» у верхнего и нижнего края страницы (окно красит ими полосы под
 * строкой состояния и навигационной панелью), {@link #haptic(String)} — лёгкий тактильный отклик (вкладки, кнопки).
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

        /** Цвета неба «Росы» у верхнего и нижнего края страницы (непрозрачный ARGB). */
        void sky(int top, int bottom);

        /** Тактильный отклик: {@link #HAPTIC_KINDS}. */
        void haptic(String kind);
    }

    /** Какие отклики страница может просить: «tick» (линза проходит вкладку), «press» (нажатие), «select» (выбор). */
    static final java.util.List<String> HAPTIC_KINDS = java.util.Arrays.asList("tick", "press", "select");

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
     * Страница сообщает свой вид: тема «light» или «dark», вид «rosa», «glass» или «classic». Всё остальное игнорируется.
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

    /**
     * Страница «Росы» сообщает цвета неба у верхнего и нижнего края ({@code #rrggbb}); {@code light} — тёмный ли на нём текст
     * (значки системных панелей окно выбирает по {@link #look}). Всё, что не цвет, игнорируется.
     */
    @JavascriptInterface
    public void sky(String top, String bottom, boolean light) {
        int t = ThemeColor.parse(top);
        int b = ThemeColor.parse(bottom);
        if (t == ThemeColor.NONE || b == ThemeColor.NONE) {
            return;
        }
        mainThread.execute(() -> {
            if (fromTheNodesPage()) {
                host.sky(t, b);
            }
        });
    }

    /** Лёгкий тактильный отклик: «tick», «press» или «select»; другое название игнорируется. */
    @JavascriptInterface
    public void haptic(String kind) {
        if (kind == null || !HAPTIC_KINDS.contains(kind)) {
            return;
        }
        mainThread.execute(() -> {
            if (fromTheNodesPage()) {
                host.haptic(kind);
            }
        });
    }
}
