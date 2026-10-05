package app.themesh.mobile;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Снимки экрана настоящего окна на настоящем WebView (в эмуляторе CI): вид «Роса» (небо и стекло), строка состояния, меню «⋮», светлая и
 * тёмная темы, первый экран и «Главная». Ничего, кроме того, что снимки получились, тест не проверяет — смотрит на них человек
 * (CI кладёт их в артефакт «themesh-android-emulator-screens»), поэтому шаг, который не удался (меню не открылось, страница не
 * успела), записывается в журнал и не ломает остальные снимки. Как снимки попадают на диск, см. {@link Shots}; что происходит в
 * странице — {@link WebProbe}. «Рядом» с настоящим вторым узлом снимает {@link NearbyFlowTest}.
 */
@RunWith(AndroidJUnit4.class)
public class GlassShotsTest {
    private static final String PACKAGE = "app.themesh.mobile";
    /** После того как страница договорила анимации (WebProbe.settle) шрифты и размытие ещё устанавливаются. */
    private static final long SETTLE_MS = 1500;
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
            WebProbe web = new WebProbe(scenario);
            // чистое начало: если сеть осталась от другого теста, выйти из неё
            if (web.waitFor("document.querySelector('[data-testid^=\"page-\"]') != null", waitMs)
                    && !web.has("document.querySelector('[data-testid=\"page-onboarding\"]') != null")) {
                web.run("fetch('api/mesh/leave',{method:'POST',headers:{'Content-Type':'application/json','X-Themesh':'1'},body:'{}'}).then(function () { location.reload(); });");
            }
            assertTrue("первый экран не появился за " + waitMs / 1000 + " с",
                    web.waitFor("document.querySelector('[data-testid=\"page-onboarding\"]') != null", waitMs));

            for (String theme : new String[] {"light", "dark"}) {
                setTheme(web, theme, "page-onboarding");
                shot(web, "01-start-" + theme);
                // меню приложения «⋮»: его просит страница. Закрывается оно самим окном, а не клавишей «Назад»: если бы меню не
                // открылось, «Назад» закрыла бы приложение, и следующие снимки были бы снимками рабочего стола
                web.run("window.themeshShell && window.themeshShell.menu();");
                shot(web, "02-menu-" + theme);
                scenario.onActivity(MainActivity::dismissMenu);
                Thread.sleep(800);
                // форма «Создать свою сеть»
                web.click("[data-testid=\"onb-create\"]");
                shot(web, "03-create-" + theme);
                web.click(".onb-back");
            }

            // сеть создана: «Главная», окно «Добавить устройство»
            web.run("window.__created = 0; fetch('api/mesh/create',{method:'POST',headers:{'Content-Type':'application/json','X-Themesh':'1'},"
                    + "body:JSON.stringify({meshName:'Дом',deviceName:'pixel',owner:'Андрей'})}).then(function (r) { window.__created = r.status; });");
            web.waitFor("window.__created === 200", 30_000);
            for (String theme : new String[] {"light", "dark"}) {
                setTheme(web, theme, "page-home");
                shot(web, "04-home-" + theme);
                web.click("[data-testid=\"home-action-add\"]");
                shot(web, "05-add-device-" + theme);
                web.click("[data-testid=\"invite-create\"]");
                web.waitFor("document.querySelector('[data-testid=\"invite-qr\"] img') != null", 20_000);
                shot(web, "06-invite-" + theme);
                web.click(".modal__close");
                Thread.sleep(500);
                web.run("location.hash = '#/settings';");
                shot(web, "07-settings-" + theme);
                web.run("location.hash = '#/home';");
                Thread.sleep(500);
            }

            // «Роса»: картинки для выбора настроения в «Настройки → Внешний вид» и небо днём, ночью и в вечернем настроении (страница
            // показывает небо на заданной высоте солнца, ?sky=: так снимки не зависят от того, когда их снимают)
            String[][] moods = {{"auto", "25", "auto", "10-sky-day"}, {"auto", "-16", "auto", "11-sky-night"}, {"evening", "", "evening", "12-sky-evening"}};
            for (String[] mood : moods) {
                Log.i("Shots", "step: " + mood[3]);
                web.run("try { localStorage.setItem('themesh.theme', '" + mood[0] + "'); } catch (e) {}"
                        + " location.href = location.pathname + '" + (mood[1].isEmpty() ? "" : "?sky=" + mood[1]) + "#/home';");
                Thread.sleep(1500);
                web.waitFor("document.querySelector('[data-testid=\"page-home\"]') != null"
                        + " && document.documentElement.getAttribute('data-appearance') === '" + mood[2] + "'", 30_000);
                shot(web, mood[3]);
            }

            pickerShot(web);

            // вернуть узел в прежнее состояние: другие тесты ждут первый экран
            web.run("fetch('api/mesh/leave',{method:'POST',headers:{'Content-Type':'application/json','X-Themesh':'1'},body:'{}'});");
            Thread.sleep(1500);
        }
        assertTrue("ни одного снимка не получилось", !written.isEmpty());
    }

    /**
     * «Настройки → Внешний вид»: картинки выбора неба. Страницу открывают заново, сразу на этом экране, и прокручивают к выбору. (Эта страница
     * когда-то убивала сам эмулятор CI — см. комментарий к emulator-options в themesh-android.yml; журнал шагов оставлен: если эмулятор
     * пропадёт снова, по нему видно, на каком шаге.)
     */
    private void pickerShot(WebProbe web) throws Exception {
        Log.i("Shots", "step: the sky picker");
        web.run("try { localStorage.setItem('themesh.theme', 'auto'); } catch (e) {}"
                + " location.href = location.pathname + '?sky=25#/settings/interface';");
        Thread.sleep(1500);
        web.waitFor("document.querySelector('[data-testid=\"theme-auto\"]') != null || document.querySelector('[data-testid=\"lang-auto\"]') != null", 30_000);
        web.run("var e = document.querySelector('[data-testid=\"theme-auto\"]') || document.querySelector('[data-testid=\"lang-auto\"]'); if (e) e.scrollIntoView({ block: 'center' });");
        Thread.sleep(900);
        shot(web, "13-sky-picker");
    }

    /** Включает тему страницы (она запоминает её в localStorage), перезагружает страницу и ждёт, пока на ней нужный экран в этой теме. */
    private void setTheme(WebProbe web, String theme, String page) throws Exception {
        web.run("try { localStorage.setItem('themesh.theme', '" + theme + "'); } catch (e) {} location.reload();");
        Thread.sleep(1500);
        web.waitFor("document.querySelector('[data-testid=\"" + page + "\"]') != null"
                + " && document.documentElement.getAttribute('data-theme') === '" + theme + "'", 30_000);
    }

    private void shot(WebProbe web, String name) throws InterruptedException {
        web.settle(15_000);
        // (what the page runs with: the level of motion it settled on and what the window said in its user agent)
        Log.i("Shots", name + ": " + web.evaluate("document.documentElement.getAttribute('data-fx') + ' default=' + document.documentElement.getAttribute('data-fx-default') + ' ' + (navigator.userAgent.match(/TheMeshAndroid[^)]*\\)/) || [''])[0]"));
        if (Shots.take(instrumentation, name, SETTLE_MS)) {
            written.add(name);
        }
    }
}
