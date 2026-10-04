package app.themesh.mobile.core;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Что приложение сообщает странице, когда экран сканера закрылся: событие DOM {@code themesh-scan} на {@code window}, у
 * которого {@code detail} — {@code {"text": "MESH1-…"}} (приглашение заглавными, без дефисов и пробелов) или
 * {@code {"error": "cancelled" | "denied" | "unavailable"}}. Контракт с интерфейсом описан в docs/UI-API.md
 * («Scanning an invitation»). {@code detail} собирается {@link JSONObject}, а не склейкой строк; в скрипт попадает только
 * готовый JSON.
 */
public final class ScanEvent {
    /** Человек вышел из сканера (кнопка «Отмена», «Назад»): странице сказать нечего. */
    public static final String CANCELLED = "cancelled";
    /** Нет разрешения на камеру. */
    public static final String DENIED = "denied";
    /** Камеры нет или она не открылась. */
    public static final String UNAVAILABLE = "unavailable";

    private static final String BEFORE = "window.dispatchEvent(new CustomEvent('themesh-scan', {detail: ";
    private static final String AFTER = "}))";

    private ScanEvent() {
    }

    /** Один из трёх кодов ошибки; всё незнакомое (или отсутствующее) — «cancelled». */
    public static String errorCode(String code) {
        if (DENIED.equals(code) || UNAVAILABLE.equals(code)) {
            return code;
        }
        return CANCELLED;
    }

    /**
     * Скрипт для {@code WebView.evaluateJavascript}: страница получает прочитанное приглашение. Если это не приглашение в
     * обычном виде ({@link InviteCode#extract}), страница получит «unavailable»: ничего, кроме приглашения, она не получает.
     */
    public static String found(String invite) {
        if (invite == null || !invite.equals(InviteCode.extract(invite))) {
            return failed(UNAVAILABLE);
        }
        return script(detail("text", invite));
    }

    /** Скрипт для {@code WebView.evaluateJavascript}: страница получает код ошибки (см. {@link #errorCode}). */
    public static String failed(String code) {
        return script(detail("error", errorCode(code)));
    }

    private static JSONObject detail(String key, String value) {
        try {
            return new JSONObject().put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException(e); // ключ не пустой, значение — строка: так не бывает
        }
    }

    private static String script(JSONObject detail) {
        return BEFORE + detail + AFTER;
    }
}
