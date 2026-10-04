package app.themesh.mobile;

import android.util.Log;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Окно приложения глазами теста: выполнить скрипт в странице интерфейса, подождать, пока условие станет истинным, прочитать
 * значение. Ошибка страницы теста не ломает: условие просто остаётся ложным.
 */
final class WebProbe {
    private static final String TAG = "WebProbe";
    private final ActivityScenario<MainActivity> scenario;

    WebProbe(ActivityScenario<MainActivity> scenario) {
        this.scenario = scenario;
    }

    /** Сырой результат скрипта (JSON-значение) или {@code null}, если страница не ответила за 10 с. */
    String evaluate(String script) throws InterruptedException {
        AtomicReference<String> value = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        scenario.onActivity(activity -> {
            WebView web = activity.findViewById(R.id.web);
            web.evaluateJavascript(script, v -> {
                value.set(v);
                done.countDown();
            });
        });
        return done.await(10, TimeUnit.SECONDS) ? value.get() : null;
    }

    /** Выполняет операторы в странице (в своей функции, чтобы не засорять window). */
    void run(String statements) throws InterruptedException {
        String v = evaluate("(function () { try { " + statements + " return true; } catch (e) { return 'error: ' + e; } })()");
        if (!"true".equals(v)) {
            Log.w(TAG, "скрипт ответил " + v + ": " + statements);
        }
    }

    /** Истинно ли выражение сейчас. */
    boolean has(String expression) throws InterruptedException {
        return "true".equals(evaluate("(function () { try { return !!(" + expression + "); } catch (e) { return false; } })()"));
    }

    boolean waitFor(String expression, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (has(expression)) {
                return true;
            }
            Thread.sleep(500);
        }
        Log.w(TAG, "не дождались: " + expression);
        return false;
    }

    /** Значение выражения как строка (строка JSON без кавычек); пусто, если страница не ответила или выражение не вычислилось. */
    String value(String expression) throws InterruptedException {
        String v = evaluate("(function () { try { var x = (" + expression + "); return x == null ? '' : String(x); } catch (e) { return ''; } })()");
        if (v == null || v.equals("null")) {
            return "";
        }
        return v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"") ? v.substring(1, v.length() - 1) : v;
    }

    /** Значение выражения — объект (его JSON разбирается); {@code null}, если страница не ответила или выражение не вычислилось. */
    JSONObject object(String expression) throws Exception {
        String v = evaluate("(function () { try { var x = (" + expression + "); return x === undefined ? null : x; } catch (e) { return null; } })()");
        if (v == null || v.equals("null")) {
            return null;
        }
        return new JSONObject(v);
    }

    /** Нажимает на элемент по селектору (с условием на ещё один селектор внутри него, если задан). */
    void click(String selector) throws InterruptedException {
        run("var e = document.querySelector('" + selector.replace("'", "\\'") + "'); if (e) e.click();");
    }
}
