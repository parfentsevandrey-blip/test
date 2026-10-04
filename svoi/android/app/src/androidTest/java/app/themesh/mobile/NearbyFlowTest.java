package app.themesh.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.themesh.mobile.core.AppLog;

/**
 * «Рядом» на настоящем окне настоящего телефона (эмулятор CI): устройство с приложением само находит другое, которое может его
 * добавить, показывает его в списке, подключается одним нажатием и подтверждает шесть цифр. Вторым устройством («Mac», как в
 * жизни) служит вторая копия той же программы {@code libthemesh.so} со своим каталогом данных — настоящий второй процесс, который
 * находит телефон и отвечает ему по сети, а не подставка. Оба направления:
 *
 * <ul>
 *   <li>у телефона нет сети, «Mac» её хозяин: телефон видит «Mac» в списке, подключается, сверяет цифры и входит;
 *   <li>у телефона своя сеть, «Mac» новичок: «Mac» находит телефон, просит добавить, телефон показывает окно с теми же цифрами, и
 *       хозяин телефона разрешает.
 * </ul>
 *
 * <p>Оба узла живут на одном телефоне и делят адрес в сети, поэтому проверяется путь «по адресу» (Android 11+ не даёт программе
 * перечислить интерфейсы), а не по списку интерфейсов; а вот ответы на вопрос «кто рядом?» по одноадресной отправке между двумя
 * сокетами одного адреса и порта могут попасть не к тому — их заменяют объявления, которые идут по группе и по широковещанию и
 * доходят до обоих. Снимки экрана на каждом шаге кладёт {@link Shots} (смотрит человек: артефакт «themesh-android-emulator-screens»).
 * Что осталось на телефоне после теста, убирается: сеть покидается, второй узел останавливается.
 */
@RunWith(AndroidJUnit4.class)
public class NearbyFlowTest {
    private static final String TAG = "NearbyFlowTest";
    private static final String PACKAGE = "app.themesh.mobile";
    private static final String MAC_MESH = "Дом Мака";
    /** Объявления идут раз в 5 с, эмулятор CI небыстрый: на то, чтобы найти друг друга, дано много времени. */
    private static final long DISCOVERY_MS = 150_000;
    private static final long STEP_MS = 90_000;
    private static final String POST = "method:'POST',headers:{'Content-Type':'application/json','X-Themesh':'1'}";
    /** Строка запроса в `themesh nearby` у того, кого просят добавить: цифры, «подтвердили ли там», номер запроса. */
    private static final Pattern ASK = Pattern.compile("(\\d{6})\\s+(true|false)\\s+([0-9a-f]{8,})\\s*$", Pattern.MULTILINE);
    private static final Pattern DIGITS = Pattern.compile("The six digits: (\\d{6})");

    private final long waitMs = Long.parseLong(InstrumentationRegistry.getArguments().getString("waitSeconds", "90")) * 1000;
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private NodeProcess mac;

    @Before
    public void allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.getUiAutomation().grantRuntimePermission(PACKAGE, Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @After
    public void tidyUp() throws Exception {
        if (mac != null) {
            mac.stop();
            mac = null;
        }
        NodeService.quit(context);
        Thread.sleep(1500);
    }

    // ---- у телефона нет сети: он входит в сеть «Mac» ---------------------------------------------

    @Test
    public void aPhoneWithoutAMeshFindsAMacNearbyAndIsAddedByIt() throws Exception {
        inWindow("phone-joins", web -> {
            assertTrue("первый экран не показал блок «Рядом»", web.waitFor("document.querySelector('[data-testid=\"nearby\"]') != null", waitMs));
            shot(web, "10-nearby-searching");

            mac = new NodeProcess(context, "mac", 0, "darwin/arm64");
            mac.init(MAC_MESH, "mac", "Андрей");
            mac.up();

            // телефон сам находит «Mac» и показывает его с названием его сети
            String found = "document.querySelector('[data-testid=\"nearby-device\"][data-name=\"mac\"]')";
            assertTrue("телефон не показал «Mac» в списке за " + DISCOVERY_MS / 1000 + " с", web.waitFor(found + " != null", DISCOVERY_MS));
            assertTrue("в строке «Mac» нет названия его сети: " + web.value(found + ".textContent"),
                    web.has(found + ".textContent.indexOf('" + MAC_MESH + "') >= 0"));
            shot(web, "11-nearby-list");

            // одно нажатие — и на экране шесть цифр
            web.click("[data-testid=\"nearby-connect\"]");
            assertTrue("шесть цифр не появились за " + STEP_MS / 1000 + " с",
                    web.waitFor("document.querySelector('[data-testid=\"nearby-join\"][data-state=\"waiting\"]') != null", STEP_MS));
            String code = web.value("document.querySelector('[data-testid=\"nearby-code\"] .nearby-code__digits').getAttribute('data-code')");
            assertTrue("шесть цифр: «" + code + "»", code.matches("\\d{6}"));
            shot(web, "12-nearby-code");

            // «Mac» видит запрос с теми же цифрами, и там ещё никто не подтвердил
            Ask ask = macAsk(false, STEP_MS);
            if (ask == null) {
                fail("«Mac» не увидел запрос телефона. `themesh nearby` ответил: " + macSays("nearby"));
            }
            assertEquals("цифры на телефоне и на «Mac» разные", code, ask.code);
            assertFalse("запрос кажется подтверждённым, а телефон ещё не нажал «Совпадает»", ask.confirmed);

            // человек у телефона говорит «совпадает» и ждёт «Mac»
            web.click("[data-testid=\"nearby-match\"]");
            assertTrue("телефон не перешёл в «ждёт хозяина»",
                    web.waitFor("document.querySelector('[data-testid=\"nearby-join\"][data-state=\"confirmed\"]') != null", STEP_MS));
            shot(web, "13-nearby-waiting");
            ask = macAsk(true, STEP_MS);
            if (ask == null) {
                fail("«Mac» не узнал, что на телефоне цифры подтвердили: " + macSays("nearby"));
            }

            // человек у «Mac» разрешает
            NodeProcess.Result allowed = mac.run(30_000, "nearby", "allow", ask.id, "--owner", "Андрей");
            assertEquals("`themesh nearby allow`: " + allowed, 0, allowed.exit);

            // телефон в сети: «Главная» с «Mac» среди устройств
            assertTrue("телефон не показал «Главную» после добавления",
                    web.waitFor("document.querySelector('[data-testid=\"page-home\"]') != null", STEP_MS));
            shot(web, "14-nearby-joined-home");
            JSONObject state = waitForState(web, s -> s.optBoolean("configured") && peer(s, "mac") != null, STEP_MS);
            if (state == null) {
                fail("телефон не знает «Mac» среди устройств сети");
            }
            JSONObject self = state.getJSONObject("self");
            assertEquals(MAC_MESH, self.getString("meshName"));
            assertFalse("добавленное так устройство должно быть обычным, не администратором", self.getBoolean("admin"));
            assertTrue("«Mac» должен быть администратором в списке телефона", peer(state, "mac").optBoolean("admin"));

            // и устройства соединились: «Mac» знает, что телефон — Android
            state = waitForState(web, s -> peerOnline(s, "mac"), STEP_MS);
            if (state == null) {
                fail("телефон вошёл в сеть, но не соединился с «Mac»");
            }
            assertEquals("darwin", peer(state, "mac").optString("os"));
            JSONObject theirs = macState(s -> s.optJSONArray("peers") != null && s.optJSONArray("peers").length() == 1
                    && s.optJSONArray("peers").optJSONObject(0).optBoolean("online"), STEP_MS);
            if (theirs == null) {
                fail("«Mac» не видит телефон в сети: " + macSays("status"));
            }
            JSONObject phone = theirs.getJSONArray("peers").getJSONObject(0);
            assertEquals("android", phone.optString("os"));
            assertFalse(phone.optBoolean("admin"));
        });
    }

    // ---- у телефона своя сеть: «Mac» входит в неё -------------------------------------------------

    @Test
    public void aNewMacFindsThePhoneNearbyAndThePhoneAddsIt() throws Exception {
        inWindow("mac-joins", web -> {
            createMesh(web, "Дом", "pixel", "Андрей");
            shot(web, "20-nearby-admin-home");

            mac = new NodeProcess(context, "mac", 0, "darwin/arm64");
            mac.up(); // у «Mac» нет сети: он слушает, кто рядом может его добавить

            // «Mac» находит телефон (администратор, которого видно)
            if (!macSees("pixel", DISCOVERY_MS)) {
                fail("«Mac» не нашёл телефон за " + DISCOVERY_MS / 1000 + " с: " + macSays("nearby"));
            }

            NodeProcess.Running join = mac.start("nearby", "join", "pixel", "--name", "mac");
            try {
                String line = join.waitForLine("The six digits: \\d{6}", STEP_MS);
                assertNotNull("`themesh nearby join` не показал шесть цифр за " + STEP_MS / 1000 + " с:\n" + join.output(), line);
                Matcher m = DIGITS.matcher(line);
                assertTrue(line, m.find());
                String code = m.group(1);

                // у телефона открылось окно с просьбой, и цифры в нём те же
                assertTrue("у телефона не открылось окно с просьбой за " + STEP_MS / 1000 + " с",
                        web.waitFor("document.querySelector('[data-testid=\"nearby-ask\"]') != null", STEP_MS));
                assertEquals("цифры на телефоне и на «Mac» разные", code,
                        web.value("document.querySelector('[data-testid=\"nearby-ask-code\"] .nearby-code__digits').getAttribute('data-code')"));
                assertEquals("mac", web.value("document.querySelector('[data-testid=\"nearby-ask-name\"]').textContent"));
                shot(web, "21-nearby-ask");

                // человек у «Mac» говорит «совпадает», телефон это видит
                join.type("y\n");
                assertTrue("окно телефона не узнало, что на «Mac» цифры подтвердили",
                        web.waitFor("document.querySelector('[data-testid=\"nearby-ask-state\"][data-confirmed=\"true\"]') != null", STEP_MS));
                shot(web, "22-nearby-ask-confirmed");

                // хозяин телефона разрешает
                web.click("[data-testid=\"nearby-allow\"]");
                assertNotNull("`themesh nearby join` не сообщил, что «Mac» добавлен:\n" + join.output(), join.waitForLine("Added:", STEP_MS));
                Integer exit = join.waitForExit(30_000);
                assertEquals("`themesh nearby join` закончил с ошибкой:\n" + join.output(), Integer.valueOf(0), exit);
            } finally {
                join.kill();
            }

            // «Mac» среди устройств телефона, обычное, а не администратор; и они соединились
            JSONObject state = waitForState(web, s -> peerOnline(s, "mac"), STEP_MS);
            if (state == null) {
                fail("«Mac» добавлен, но телефон его в сети не видит: " + macSays("status"));
            }
            assertFalse("добавленное так устройство должно быть обычным, не администратором", peer(state, "mac").optBoolean("admin"));
            assertEquals("darwin", peer(state, "mac").optString("os"));
            assertTrue(state.getJSONObject("self").getBoolean("admin"));
            // «Нужно ваше внимание» не спрашивает про устройство, которое уже добавлено (ответ на «Разрешить» приходит позже события
            // о том, что узел забыл просьбу, и не должен его затирать)
            assertTrue("на «Главной» осталась просьба «Mac», хотя его уже добавили",
                    web.waitFor("document.querySelector('[data-testid=\"home-att-nearby\"]') == null", 20_000));
            shot(web, "23-nearby-admin-added");

            JSONObject theirs = macState(s -> s.optBoolean("configured") && s.optJSONArray("peers") != null && s.optJSONArray("peers").length() == 1, STEP_MS);
            if (theirs == null) {
                fail("«Mac» не стал частью сети: " + macSays("status"));
            }
            assertEquals("Дом", theirs.getJSONObject("self").getString("meshName"));
            assertFalse(theirs.getJSONObject("self").getBoolean("admin"));
        });
    }

    // ---- общее ------------------------------------------------------------------------------------

    private interface Flow {
        void run(WebProbe web) throws Exception;
    }

    private interface Check {
        boolean ok(JSONObject state);
    }

    /** Окно приложения на «чистом» телефоне (сети нет); при неудаче — снимок, журналы обоих узлов; в конце телефон покидает сеть. */
    private void inWindow(String name, Flow flow) throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            WebProbe web = new WebProbe(scenario);
            try {
                assertTrue("окно не показало страницу за " + waitMs / 1000 + " с", web.waitFor("document.querySelector('[data-testid^=\"page-\"]') != null", waitMs));
                if (!web.has("document.querySelector('[data-testid=\"page-onboarding\"]') != null")) {
                    leave(web);
                    web.run("location.reload();");
                }
                assertTrue("первый экран не появился за " + waitMs / 1000 + " с",
                        web.waitFor("document.querySelector('[data-testid=\"page-onboarding\"]') != null", waitMs));
                flow.run(web);
            } catch (Throwable t) {
                Shots.take(instrumentation, "99-failed-" + name, 500);
                dump(name);
                throw t;
            } finally {
                leave(web);
            }
        }
    }

    /** Снимок экрана, когда страница договорила анимации (см. {@link WebProbe#settle}) и шрифты с размытием установились. */
    private void shot(WebProbe web, String name) throws InterruptedException {
        web.settle(15_000);
        Shots.take(instrumentation, name, 1000);
    }

    private void createMesh(WebProbe web, String meshName, String deviceName, String owner) throws Exception {
        web.run("window.__created = 0; fetch('api/mesh/create',{" + POST + ",body:JSON.stringify({meshName:'" + meshName + "',deviceName:'" + deviceName
                + "',owner:'" + owner + "'})}).then(function (r) { window.__created = r.status; });");
        assertTrue("сеть не создалась", web.waitFor("window.__created === 200", 30_000));
        web.run("location.reload();");
        assertTrue("после создания сети нет «Главной»", web.waitFor("document.querySelector('[data-testid=\"page-home\"]') != null", STEP_MS));
    }

    private void leave(WebProbe web) throws InterruptedException {
        web.run("fetch('api/mesh/leave',{" + POST + ",body:'{}'});");
        Thread.sleep(1500);
    }

    /** Состояние узла приложения (то, что получает окно), когда оно подходит под условие; {@code null}, если не дождались. */
    private JSONObject waitForState(WebProbe web, Check check, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            web.run("window.__state = null; fetch('api/state').then(function (r) { return r.json(); }).then(function (j) { window.__state = j; });");
            if (web.waitFor("window.__state !== null", 15_000)) {
                JSONObject s = web.object("window.__state");
                if (s != null && check.ok(s)) {
                    return s;
                }
            }
            Thread.sleep(2000);
        }
        return null;
    }

    /** Состояние узла «Mac» (`themesh status --json`), когда оно подходит под условие; {@code null}, если не дождались. */
    private JSONObject macState(Check check, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            NodeProcess.Result r = mac.run(20_000, "status", "--json");
            int from = r.out.indexOf('{');
            int to = r.out.lastIndexOf('}');
            if (r.exit == 0 && from >= 0 && to > from) {
                try {
                    JSONObject s = new JSONObject(r.out.substring(from, to + 1));
                    if (check.ok(s)) {
                        return s;
                    }
                } catch (org.json.JSONException e) {
                    Log.w(TAG, "status --json: " + e);
                }
            }
            Thread.sleep(2000);
        }
        return null;
    }

    private static JSONObject peer(JSONObject state, String name) {
        JSONArray peers = state.optJSONArray("peers");
        for (int i = 0; peers != null && i < peers.length(); i++) {
            JSONObject p = peers.optJSONObject(i);
            if (p != null && name.equals(p.optString("name"))) {
                return p;
            }
        }
        return null;
    }

    private static boolean peerOnline(JSONObject state, String name) {
        JSONObject p = peer(state, name);
        return p != null && p.optBoolean("online");
    }

    /** Запрос на добавление, как его видит «Mac» в `themesh nearby`. */
    private static final class Ask {
        final String code;
        final boolean confirmed;
        final String id;

        Ask(String code, boolean confirmed, String id) {
            this.code = code;
            this.confirmed = confirmed;
            this.id = id;
        }
    }

    /** Ждёт, пока у «Mac» (он хозяин сети) появится запрос (и, если {@code confirmed}, пока на другом устройстве подтвердят цифры). */
    private Ask macAsk(boolean confirmed, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            NodeProcess.Result r = mac.run(20_000, "nearby");
            Matcher m = ASK.matcher(r.out);
            if (r.exit == 0 && m.find()) {
                Ask ask = new Ask(m.group(1), m.group(2).equals("true"), m.group(3));
                if (!confirmed || ask.confirmed) {
                    return ask;
                }
            }
            Thread.sleep(1500);
        }
        return null;
    }

    /** Ждёт, пока `themesh nearby` у «Mac» (у него нет сети) назовёт устройство рядом. */
    private boolean macSees(String name, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            NodeProcess.Result r = mac.run(20_000, "nearby");
            if (r.exit == 0 && r.out.contains(name)) {
                return true;
            }
            Thread.sleep(2000);
        }
        return false;
    }

    /** Ответ команды `themesh …` узла «Mac» (для сообщения о неудаче). */
    private String macSays(String... args) {
        try {
            return mac.run(20_000, args).toString();
        } catch (Exception e) {
            return "(команда не запустилась: " + e + ")";
        }
    }

    /** Что делал каждый из узлов, когда тест не удался. */
    private void dump(String name) {
        try {
            Log.e(TAG, name + ": журнал узла «Mac» (хвост):\n" + (mac == null ? "(его не запускали)" : mac.log()));
            File log = new File(context.getFilesDir(), "themesh.log");
            Log.e(TAG, name + ": журнал узла приложения (хвост):\n" + new String(AppLog.readTail(log, 24_000), StandardCharsets.UTF_8));
            File addrs = new File(context.getFilesDir(), "local-addrs.txt");
            Log.e(TAG, name + ": адреса телефона: "
                    + (addrs.isFile() ? new String(java.nio.file.Files.readAllBytes(addrs.toPath()), StandardCharsets.UTF_8).trim().replace('\n', ' ') : "(файла нет)"));
        } catch (Exception e) {
            Log.e(TAG, name + ": журналы прочитать не вышло", e);
        }
    }
}
