package app.themesh.mobile;

import android.webkit.JavascriptInterface;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import app.themesh.mobile.core.OriginPolicy;

/**
 * Объект {@code window.themeshApp} в странице интерфейса: единственное, что приложение даёт странице, — сканер QR-кода
 * приглашения. {@link #canScan()} — есть ли у телефона камера (по нему форма «Подключиться по приглашению» решает,
 * показывать ли кнопку «Сканировать QR-код»), {@link #scanInvite()} — открыть экран сканера. Результат приходит странице
 * событием {@code themesh-scan} (см. {@link app.themesh.mobile.core.ScanEvent}).
 *
 * <p>Методы вызываются из служебного потока WebView, а не из главного: всё, что трогает окно, уходит в главный поток.
 * {@code scanInvite} выполняется, только если сейчас в окне интерфейс узла (адрес {@code http://127.0.0.1:<порт>/…}): чужая
 * страница, если бы она сюда попала, камеру открыть не может. Всё окружение — через интерфейсы, поэтому класс проверяется
 * на компьютере.
 */
final class ScanBridge {
    /** Имя объекта в странице: {@code window.themeshApp}. */
    static final String NAME = "themeshApp";

    private final boolean camera;
    private final Executor mainThread;
    private final Supplier<String> pageUrl;
    private final Supplier<String> nodeOrigin;
    private final Runnable openScanner;

    /**
     * @param camera      есть ли у телефона камера ({@code FEATURE_CAMERA_ANY})
     * @param mainThread  выполняет задачу в главном потоке
     * @param pageUrl     адрес страницы, которая сейчас в окне ({@code WebView.getUrl()}); вызывается только в главном потоке
     * @param nodeOrigin  адрес узла ({@code http://127.0.0.1:<порт>}), с которого окно загрузило интерфейс; тоже в главном потоке
     * @param openScanner открывает экран сканера; вызывается в главном потоке
     */
    ScanBridge(boolean camera, Executor mainThread, Supplier<String> pageUrl, Supplier<String> nodeOrigin, Runnable openScanner) {
        this.camera = camera;
        this.mainThread = mainThread;
        this.pageUrl = pageUrl;
        this.nodeOrigin = nodeOrigin;
        this.openScanner = openScanner;
    }

    /** Есть ли у телефона камера. */
    @JavascriptInterface
    public boolean canScan() {
        return camera;
    }

    /** Открывает экран сканера; ответ придёт событием {@code themesh-scan}. Просьбы не от интерфейса узла игнорируются. */
    @JavascriptInterface
    public void scanInvite() {
        if (!camera) {
            return;
        }
        mainThread.execute(() -> {
            if (OriginPolicy.isInternal(nodeOrigin.get(), pageUrl.get())) {
                openScanner.run();
            }
        });
    }
}
