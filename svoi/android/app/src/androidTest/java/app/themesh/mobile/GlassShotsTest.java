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
    /**
     * Как снимается страница «Настройки → Внешний вид»: в эмуляторе CI на ней один раз пропало само устройство (процесс эмулятора
     * завершился). Чтобы найти, что именно её вызывает, CI запускает несколько вариантов рядом (см. pickerShot); обычный запуск — «full».
     */
    private final String pickerVariant = InstrumentationRegistry.getArguments().getString("pickerVariant", "full");

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
     * Что убирается из страницы перед снимком (стиль, добавленный в страницу): картинки выбора неба обошлись эмулятору CI дорого, и
     * каждый вариант убирает из них что-то одно. {@code null}: страница как есть.
     */
    private static String pickerCss(String variant) {
        switch (variant) {
            case "nopicker":
                return ".skypick{display:none!important}";
            case "noeffects":
                return "*,*::before,*::after{box-shadow:none!important;filter:none!important;backdrop-filter:none!important;"
                        + "-webkit-backdrop-filter:none!important;text-shadow:none!important;animation:none!important;transition:none!important;will-change:auto!important;}";
            case "invisible": // место занято, но ничего не рисуется
                return ".skypick{visibility:hidden!important}";
            case "transparent": // рисуется, но невидимо
                return ".skypick{opacity:0!important}";
            case "nothumb": // только подписи
                return ".skypick__thumb{display:none!important}";
            case "nolabel": // только картинки
                return ".skypick__opt>span:not(.skypick__thumb){display:none!important}";
            case "onefirst": // только первая из четырёх
                return ".skypick__opt:not(:first-child){display:none!important}";
            case "onelast": // только последняя из четырёх
                return ".skypick__opt:not(:last-child){display:none!important}";
            case "emptybox": // картинки — пустые прозрачные коробки
                return ".skypick__thumb{background:transparent!important;box-shadow:none!important;border-radius:0!important;overflow:visible!important;aspect-ratio:auto!important;height:40px!important}"
                        + ".skypick__check{display:none!important}";
            case "block": // без сетки и без гибкой раскладки
                return ".skypick,.skypick__opt{display:block!important}.skypick__thumb{display:inline-block!important;width:60px!important}";
            default:
                return null;
        }
    }

    private void pickerShot(WebProbe web) throws Exception {
        Log.i("Shots", "step: the sky picker, variant " + pickerVariant);
        if (pickerVariant.equals("skip")) {
            return;
        }
        web.run("try { localStorage.setItem('themesh.theme', 'auto'); } catch (e) {}");
        String css = pickerCss(pickerVariant);
        // «full» и «noscroll» открывают страницу заново (как раньше); остальные — сначала «Главная», потом смена адреса внутри страницы
        boolean byHash = css != null || pickerVariant.equals("hash");
        if (byHash) {
            web.run("location.href = location.pathname + '?sky=25#/home';");
            Thread.sleep(1500);
            web.waitFor("document.querySelector('[data-testid=\"page-home\"]') != null", 30_000);
            Log.i("Shots", "step: the home page is up");
            if (css != null) {
                web.run("var s = document.createElement('style'); s.textContent = '" + css.replace("\\", "\\\\").replace("'", "\\'") + "'; document.head.appendChild(s);");
            }
            web.run("location.hash = '#/settings/interface';");
        } else {
            web.run("location.href = location.pathname + '?sky=25#/settings/interface';");
        }
        Thread.sleep(1500);
        Log.i("Shots", "step: waiting for the page of the settings");
        web.waitFor("document.querySelector('[data-testid=\"theme-auto\"]') != null || document.querySelector('[data-testid=\"lang-auto\"]') != null", 30_000);
        if (!pickerVariant.equals("noscroll")) {
            Log.i("Shots", "step: scrolling to the picker");
            web.run("var e = document.querySelector('[data-testid=\"theme-auto\"]') || document.querySelector('[data-testid=\"lang-auto\"]'); if (e) e.scrollIntoView({ block: 'center' });");
        }
        Thread.sleep(900);
        Log.i("Shots", "step: the picture of the picker");
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
