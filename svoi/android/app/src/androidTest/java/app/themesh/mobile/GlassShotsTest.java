package app.themesh.mobile;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Снимки экрана настоящего окна на настоящем WebView (в эмуляторе CI): стеклянный вид, строка состояния, меню «⋮», светлая и
 * тёмная темы, первый экран и «Главная». Ничего, кроме того, что снимки получились, тест не проверяет — смотрит на них человек
 * (CI кладёт их в артефакт «themesh-android-emulator-screens»), поэтому шаг, который не удался (меню не открылось, страница не
 * успела), записывается в журнал и не ломает остальные снимки.
 *
 * <p>Снимки пишутся в /data/local/tmp/themesh-shots от имени оболочки (UiAutomation.executeShellCommandRw): эту папку не удаляет
 * удаление приложения после прогона, и из неё их забирает {@code adb pull}.
 */
@RunWith(AndroidJUnit4.class)
public class GlassShotsTest {
    private static final String TAG = "GlassShotsTest";
    private static final String PACKAGE = "app.themesh.mobile";
    private static final String DIR = "/data/local/tmp/themesh-shots";
    private final long waitMs = Long.parseLong(InstrumentationRegistry.getArguments().getString("waitSeconds", "90")) * 1000;

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final List<String> written = new ArrayList<>();

    @Before
    public void allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.getUiAutomation().grantRuntimePermission(PACKAGE, Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @After
    public void stopTheNode() throws Exception {
        NodeService.quit(context);
        Thread.sleep(1500);
    }

    @Test
    public void shotsOfTheWindow() throws Exception {
        assumeTrue("снимки пишутся через executeShellCommandRw (Android 11+)", Build.VERSION.SDK_INT >= 30);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // чистое начало: если сеть осталась от другого теста, выйти из неё
            if (waitFor(scenario, "document.querySelector('[data-testid^=\"page-\"]') != null", waitMs)
                    && !has(scenario, "document.querySelector('[data-testid=\"page-onboarding\"]') != null")) {
                run(scenario, "fetch('api/mesh/leave',{method:'POST',headers:{'Content-Type':'application/json','X-Themesh':'1'},body:'{}'}).then(function(){location.reload();}); true");
            }
            assertTrue("первый экран не появился за " + waitMs / 1000 + " с",
                    waitFor(scenario, "document.querySelector('[data-testid=\"page-onboarding\"]') != null", waitMs));

            for (String theme : new String[] {"light", "dark"}) {
                setTheme(scenario, theme);
                waitFor(scenario, "document.querySelector('[data-testid=\"page-onboarding\"]') != null"
                        + " && document.documentElement.getAttribute('data-theme') === '" + theme + "'", 30_000);
                shot("01-start-" + theme);
                // меню приложения «⋮»: его просит страница
                run(scenario, "window.themeshShell && window.themeshShell.menu(); true");
                shot("02-menu-" + theme);
                drain(instrumentation.getUiAutomation().executeShellCommand("input keyevent KEYCODE_BACK"));
                Thread.sleep(800);
                // форма «Создать свою сеть»
                run(scenario, "var b = document.querySelector('[data-testid=\"onb-create\"]'); if (b) b.click(); true");
                shot("03-create-" + theme);
                run(scenario, "var k = document.querySelector('.onb-back'); if (k) k.click(); true");
            }

            // сеть создана: «Главная», окно «Добавить устройство»
            run(scenario, "window.__created = 0; fetch('api/mesh/create',{method:'POST',headers:{'Content-Type':'application/json','X-Themesh':'1'},"
                    + "body:JSON.stringify({meshName:'Дом',deviceName:'pixel',owner:'Андрей'})}).then(function(r){window.__created=r.status;}); true");
            waitFor(scenario, "window.__created === 200", 30_000);
            for (String theme : new String[] {"light", "dark"}) {
                setTheme(scenario, theme);
                waitFor(scenario, "document.querySelector('[data-testid=\"page-home\"]') != null"
                        + " && document.documentElement.getAttribute('data-theme') === '" + theme + "'", 30_000);
                shot("04-home-" + theme);
                run(scenario, "var a = document.querySelector('[data-testid=\"home-action-add\"]'); if (a) a.click(); true");
                Thread.sleep(600);
                shot("05-add-device-" + theme);
                run(scenario, "var c = document.querySelector('[data-testid=\"invite-create\"]'); if (c) c.click(); true");
                waitFor(scenario, "document.querySelector('[data-testid=\"invite-qr\"] img') != null", 20_000);
                shot("06-invite-" + theme);
                drain(instrumentation.getUiAutomation().executeShellCommand("input keyevent KEYCODE_BACK"));
                run(scenario, "var x = document.querySelector('.modal__close'); if (x) x.click(); true");
                Thread.sleep(500);
                run(scenario, "location.hash = '#/settings'; true");
                Thread.sleep(800);
                shot("07-settings-" + theme);
                run(scenario, "location.hash = '#/home'; true");
            }

            // вернуть узел в прежнее состояние: другие тесты ждут первый экран
            run(scenario, "fetch('api/mesh/leave',{method:'POST',headers:{'Content-Type':'application/json','X-Themesh':'1'},body:'{}'}); true");
            Thread.sleep(1500);
        }
        assertTrue("ни одного снимка не получилось: " + written, !written.isEmpty());
    }

    private void setTheme(ActivityScenario<MainActivity> scenario, String theme) throws Exception {
        run(scenario, "try { localStorage.setItem('themesh.theme', '" + theme + "'); } catch (e) {} location.reload(); true");
        Thread.sleep(1500);
    }

    // ---- снимки -----------------------------------------------------------------------------

    private void shot(String name) {
        try {
            Thread.sleep(1200); // анимации выключены, но шрифты и размытие устанавливаются не мгновенно
            Bitmap bmp = instrumentation.getUiAutomation().takeScreenshot();
            if (bmp == null) {
                Log.w(TAG, name + ": система не дала снимок");
                return;
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, png);
            writeToShell(name + ".png", png.toByteArray());
            written.add(name);
            Log.i(TAG, name + ": " + bmp.getWidth() + "x" + bmp.getHeight() + ", " + png.size() + " байт");
        } catch (Exception e) {
            Log.w(TAG, name + ": не получилось", e);
        }
    }

    /**
     * Записывает файл в {@link #DIR} от имени оболочки. Команда запускается без {@code sh -c}: UiAutomation делит строку по пробелам и
     * не понимает ни кавычек, ни «&&», ни «&gt;», — поэтому папка делается отдельной командой, а запись — программой {@code dd}, которая
     * читает стандартный ввод и пишет в файл, названный в её аргументе.
     */
    private void writeToShell(String file, byte[] bytes) throws Exception {
        UiAutomation ua = instrumentation.getUiAutomation();
        drain(ua.executeShellCommand("mkdir -p " + DIR));
        ParcelFileDescriptor[] fds = ua.executeShellCommandRw("dd bs=65536 of=" + DIR + "/" + file);
        try (OutputStream stdin = new ParcelFileDescriptor.AutoCloseOutputStream(fds[1])) {
            stdin.write(bytes);
        }
        drain(fds[0]);
    }

    /** Читает вывод команды до конца: когда она закончила, чтение возвращает -1. */
    private static void drain(ParcelFileDescriptor out) throws Exception {
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(out)) {
            while (in.read() >= 0) {
                // вывод не нужен
            }
        }
    }

    // ---- страница ---------------------------------------------------------------------------

    /** Выполняет операторы в странице (в своей функции, чтобы не засорять window); ошибка страницы не ломает тест. */
    private void run(ActivityScenario<MainActivity> scenario, String statements) throws InterruptedException {
        String v = evaluate(scenario, "(function () { try { " + statements.replaceAll("; true$", ";") + " return true; } catch (e) { return 'error: ' + e; } })()");
        if (v == null || !v.equals("true")) {
            Log.w(TAG, "скрипт ответил " + v + ": " + statements);
        }
    }

    private boolean has(ActivityScenario<MainActivity> scenario, String expression) throws InterruptedException {
        return "true".equals(evaluate(scenario, "(function () { try { return !!(" + expression + "); } catch (e) { return false; } })()"));
    }

    private boolean waitFor(ActivityScenario<MainActivity> scenario, String expression, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (has(scenario, expression)) {
                return true;
            }
            Thread.sleep(500);
        }
        Log.w(TAG, "не дождались: " + expression);
        return false;
    }

    private String evaluate(ActivityScenario<MainActivity> scenario, String script) throws InterruptedException {
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
}
