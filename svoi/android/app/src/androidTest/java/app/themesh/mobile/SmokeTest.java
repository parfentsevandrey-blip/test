package app.themesh.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import app.themesh.mobile.core.Handshake;
import app.themesh.mobile.core.LocalAddrs;

/**
 * Дымовой тест на устройстве или в эмуляторе (./gradlew connectedDebugAndroidTest): окно запускает узел,
 * узел отвечает на handshake, окно показывает веб-интерфейс, а процесс узла — дочерний процесс приложения.
 * Тест работает в процессе самого приложения (той же учётной записи), поэтому видит его файлы и /proc.
 * Самодостаточен: ActivityScenario и обычный AndroidJUnit4. Время ожидания по умолчанию 90 с, меняется аргументом
 * инструментации {@code waitSeconds}.
 */
@RunWith(AndroidJUnit4.class)
public class SmokeTest {
    private static final String PACKAGE = "app.themesh.mobile";
    private static final int DEFAULT_PORT = 8777;
    /** Сколько ждать узел и окно; на медленном эмуляторе (без KVM) можно увеличить: -e waitSeconds 400. */
    private final long waitMs = Long.parseLong(InstrumentationRegistry.getArguments().getString("waitSeconds", "90")) * 1000;

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();

    @Before
    public void allowNotifications() {
        // Без разрешения окно сначала покажет свой вежливый вопрос (Android 13+) и запустит узел только после ответа.
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.getUiAutomation().grantRuntimePermission(PACKAGE, Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @After
    public void stopTheNode() throws Exception {
        NodeService.quit(context); // как кнопка «Выйти»: узел и служба останавливаются
        long end = System.currentTimeMillis() + 20_000;
        while (!nodeProcesses().isEmpty() && System.currentTimeMillis() < end) {
            Thread.sleep(200);
        }
    }

    @Test
    public void theNodeStartsAndTheInterfaceIsShown() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // 1. Узел отвечает на handshake (по умолчанию на 127.0.0.1:8777) и знает токен приложения.
            long deadline = System.currentTimeMillis() + waitMs;
            int port = 0;
            String lastProblem = "узел не отвечает";
            while (System.currentTimeMillis() < deadline) {
                port = portOfTheNode();
                lastProblem = checkHandshake(port);
                if (lastProblem == null) {
                    break;
                }
                Thread.sleep(500);
            }
            assertEquals("узел не прошёл handshake за " + waitMs / 1000 + " с: " + lastProblem, null, lastProblem);
            if (port == DEFAULT_PORT) {
                assertEquals("первым выбирается порт 8777", DEFAULT_PORT, port);
            }

            // 2. Окно показывает интерфейс: страница приветствия (сеть ещё не создана) или другая страница интерфейса.
            boolean configured = nodeIsConfigured(port);
            String script = configured
                    ? "document.querySelector('[data-testid^=\"page-\"]') != null"
                    : "document.querySelector('[data-testid=\"page-onboarding\"]') != null";
            assertTrue("интерфейс не появился в окне за " + waitMs / 1000 + " с (проверка: " + script + ")",
                    waitForScript(scenario, script, deadline));

            // 2б. Имя устройства: приложение передало узлу имя телефона (а не имя хоста: на Android это «localhost»),
            // узел предлагает его в состоянии, и форма создания сети подставляет в поле именно его.
            if (!configured) {
                String offered = offeredName(port);
                assertFalse("узел предлагает имя телефона, а не имя хоста: «" + offered + "»",
                        offered.isEmpty() || offered.startsWith("localhost"));
                String formShowsIt = "(function () {"
                        + " var b = document.querySelector('[data-testid=\"onb-create\"]'); if (b) b.click();"
                        + " var i = document.querySelector('[data-testid=\"onb-device-name\"]');"
                        + " return i != null && i.getAttribute('placeholder') === " + JSONObject.quote(offered) + "; })()";
                assertTrue("форма создания сети не предложила имя «" + offered + "»", waitForScript(scenario, formShowsIt, deadline));

                // 2в. Мост окна: на телефоне с камерой страница видит window.themeshApp (canScan() истинно, scanInvite — функция),
                // без камеры — не видит (тогда форма не покажет кнопку «Сканировать QR-код»). Без камеры тест не пропускается целиком:
                // остальное в нём (процесс узла, служба, уведомление) к камере отношения не имеет.
                String bridge = evaluate(scenario, "(function () { var a = window.themeshApp;"
                        + " return a ? [typeof a.canScan, typeof a.scanInvite, String(a.canScan())].join() : 'none'; })()");
                if (hasCamera()) {
                    assertEquals("window.themeshApp: canScan и scanInvite — функции, canScan() истинно", "\"function,function,true\"", bridge);
                } else {
                    assertEquals("у устройства нет камеры, и моста window.themeshApp быть не должно", "\"none\"", bridge);
                }
            }

            // 3. Процесс узла — дочерний процесс приложения, с нужной командной строкой.
            List<Integer> nodes = nodeProcesses();
            assertEquals("ровно один процесс узла среди детей приложения: " + nodes, 1, nodes.size());
            String cmdline = commandLine(nodes.get(0));
            assertTrue(cmdline, cmdline.contains("--exit-when-stdin-closes"));
            assertTrue(cmdline, cmdline.contains("--no-browser"));
            assertTrue(cmdline, cmdline.contains("127.0.0.1:" + port));
            assertEquals("родитель — процесс приложения", Process.myPid(), parentOf(nodes.get(0)));

            // 4. Служба переднего плана работает и показывает своё постоянное уведомление.
            assertTrue("служба NodeService запущена", serviceIsRunning());
            if (Build.VERSION.SDK_INT >= 33) {
                assertTrue("постоянное уведомление «The Mesh работает»", waitForNodeNotification());
            }

            // 5. Форма «Подключиться по приглашению» показывает кнопку «Сканировать QR-код» (data-testid="onb-scan"), когда окно умеет
            // сканировать. Проверка нарочно последняя и отдельная: если интерфейс узла собран без этой кнопки, всё остальное уже
            // проверено, а причина понятна из сообщения. Скрипт нажимает «Подключиться по приглашению» (а если открыта другая форма,
            // возвращается назад) и смотрит, появилась ли кнопка.
            if (!configured && hasCamera()) {
                String reachTheScanButton = "(function () {"
                        + " if (document.querySelector('[data-testid=\"onb-scan\"]')) return true;"
                        + " var join = document.querySelector('[data-testid=\"onb-join\"]');"
                        + " if (join) { join.click(); return false; }"
                        + " var back = document.querySelector('.onb-back'); if (back) back.click();"
                        + " return false; })()";
                assertTrue("в форме «Подключиться по приглашению» нет кнопки «Сканировать QR-код» (data-testid=\"onb-scan\"), хотя "
                        + "window.themeshApp.canScan() истинно: интерфейс узла собран без сканера или не показывает кнопку",
                        waitForScript(scenario, reachTheScanButton, System.currentTimeMillis() + 30_000));
            }
        }
    }

    // ---- сканер QR-кода: окно и страница -----------------------------------------------------

    private static final String INVITE = TestInvite.CODE;

    /** Слушатель в странице: складывает всё, что приходит событием themesh-scan, в window.__scans. */
    private static final String LISTEN = "(function () { window.__scans = [];"
            + " window.addEventListener('themesh-scan', function (e) { window.__scans.push(e.detail); }); return true; })()";

    @Test
    public void thePageGetsWhatTheScannerReturned() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            long deadline = System.currentTimeMillis() + waitMs;
            assertTrue("интерфейс не появился в окне за " + waitMs / 1000 + " с",
                    waitForScript(scenario, "document.querySelector('[data-testid^=\"page-\"]') != null", deadline));
            assertEquals("true", evaluate(scenario, LISTEN));

            // Что вернул сканер (код результата и Intent) → что получает страница. Каждый ответ ждём, прежде чем слать следующий.
            Object[][] cases = {
                    {Activity.RESULT_OK, new Intent().putExtra(ScanActivity.EXTRA_INVITE, INVITE), new JSONObject().put("text", INVITE)},
                    {Activity.RESULT_CANCELED, new Intent(), new JSONObject().put("error", "cancelled")},
                    {Activity.RESULT_CANCELED, null, new JSONObject().put("error", "cancelled")},
                    {Activity.RESULT_CANCELED, new Intent().putExtra(ScanActivity.EXTRA_ERROR, "denied"), new JSONObject().put("error", "denied")},
                    {Activity.RESULT_CANCELED, new Intent().putExtra(ScanActivity.EXTRA_ERROR, "unavailable"), new JSONObject().put("error", "unavailable")},
                    // чужое в ответе страница не получает: только приглашение или один из трёх кодов
                    {Activity.RESULT_OK, new Intent().putExtra(ScanActivity.EXTRA_INVITE, "https://evil.example/"), new JSONObject().put("error", "unavailable")},
                    {Activity.RESULT_CANCELED, new Intent().putExtra(ScanActivity.EXTRA_ERROR, "\"});alert(1)//"), new JSONObject().put("error", "cancelled")},
            };
            JSONArray expected = new JSONArray();
            for (int i = 0; i < cases.length; i++) {
                int code = (Integer) cases[i][0];
                Intent data = (Intent) cases[i][1];
                scenario.onActivity(activity -> activity.onScanResult(code, data));
                expected.put(cases[i][2]);
                assertTrue("страница не получила событие themesh-scan № " + (i + 1) + " (" + cases[i][2] + ")",
                        waitForScript(scenario, "window.__scans.length === " + (i + 1), deadline));
            }
            assertEquals(expected.toString(), scans(scenario).toString());
        }
    }

    @Test
    public void thePageOpensTheScannerAndGetsItsResult() throws Exception {
        assumeTrue("у устройства нет камеры: сканера в окне нет", hasCamera());
        // с разрешением на камеру: иначе система спросит о нём поверх экрана, а здесь важно другое — путь страница → сканер → страница
        instrumentation.getUiAutomation().grantRuntimePermission(PACKAGE, Manifest.permission.CAMERA);
        // камера «есть», но в эмуляторе может не открываться (нет веб-камеры): тогда экран сканера закроется сам, и проверять нечего
        assumeTrue("камера не открывается и без нашего экрана (в эмуляторе нет камеры?)", CameraProbe.opens(context));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            long deadline = System.currentTimeMillis() + waitMs;
            assertTrue("интерфейс не появился в окне за " + waitMs / 1000 + " с",
                    waitForScript(scenario, "document.querySelector('[data-testid^=\"page-\"]') != null", deadline));
            assertEquals("true", evaluate(scenario, LISTEN));

            // страница просит сканер (дважды подряд: второй раз его открывать не нужно), сканер «прочитал» приглашение
            assertEquals("true", evaluate(scenario, "window.themeshApp.scanInvite(); window.themeshApp.scanInvite(); true"));
            ScanActivity scanner = waitForScanner(60_000);
            Thread.sleep(2000);
            assertEquals("двойное нажатие открывает один сканер", 1, scanners().size());
            scanner.runOnUiThread(() -> scanner.onDecoded(INVITE));
            assertTrue("страница не получила приглашение", waitForScript(scenario, "window.__scans.length === 1", deadline));
            assertEquals("[{\"text\":\"" + INVITE + "\"}]", scans(scenario).toString());
            waitForNoScanner(30_000);

            // и ещё раз: человек нажал «Отмена» — страница получает «cancelled» (и ничего не делает: так принято в форме)
            assertEquals("true", evaluate(scenario, "window.__scans = []; window.themeshApp.scanInvite(); true"));
            ScanActivity second = waitForScanner(60_000);
            second.runOnUiThread(() -> second.findViewById(R.id.scan_cancel).performClick());
            assertTrue("страница не получила отмену", waitForScript(scenario, "window.__scans.length === 1", deadline));
            assertEquals("[{\"error\":\"cancelled\"}]", scans(scenario).toString());
            waitForNoScanner(30_000);
        }
    }

    // ---- адреса телефона для узла -----------------------------------------------------------

    @Test
    public void theAddressesOfThePhoneAreWrittenForTheNode() throws Exception {
        // Go-часть с Android 11 не может перечислить сетевые интерфейсы сама: приглашение, которое создаёт телефон, содержит адрес в
        // домашней сети, только если приложение записало его в files/local-addrs.txt (на эмуляторе это 10.0.2.x)
        List<String> usable = LocalAddrs.lines(LocalAddrs.scan());
        assumeFalse("у устройства нет ни одного сетевого адреса, кроме петлевых: записывать в файл нечего", usable.isEmpty());
        File file = new File(context.getFilesDir(), "local-addrs.txt");
        file.delete(); // чтобы не принять за результат этого запуска файл прошлого
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            long deadline = System.currentTimeMillis() + waitMs;
            String text = "";
            while (System.currentTimeMillis() < deadline) {
                if (file.isFile()) {
                    text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    if (!text.trim().isEmpty()) {
                        break;
                    }
                }
                Thread.sleep(300);
            }
            assertTrue("служба не записала " + file + " за " + waitMs / 1000 + " с", file.isFile());
            assertFalse("файл адресов пуст, хотя у устройства есть адреса " + usable, text.trim().isEmpty());
            boolean sharedNetwork = false;
            boolean expectShared = false; // есть интерфейс с групповой рассылкой и известной длиной префикса (Wi-Fi, Ethernet)
            for (LocalAddrs.Addr a : LocalAddrs.scan()) {
                expectShared |= a.shared && a.prefix >= 1 && LocalAddrs.usable(a.address) && a.address instanceof java.net.Inet4Address;
            }
            for (String line : text.trim().split("\n")) {
                // «192.168.1.50/24» — адрес в общей сети (по ней ядро ищет устройства рядом), «10.20.30.40» — просто адрес
                String[] parts = line.trim().split("/");
                String addr = parts[0];
                assertTrue("в файле адресов не адрес: «" + addr + "»", addr.matches("\\d{1,3}(\\.\\d{1,3}){3}") || addr.contains(":"));
                assertTrue("в файле адресов лишний адрес (петлевой, link-local, групповой): " + addr,
                        LocalAddrs.usable(java.net.InetAddress.getByName(addr)));
                if (parts.length > 1) {
                    int prefix = Integer.parseInt(parts[1]);
                    assertTrue("в файле адресов невозможная длина префикса: " + line, prefix >= 1 && prefix <= (addr.contains(":") ? 128 : 32));
                    sharedNetwork |= !addr.contains(":");
                }
            }
            // у телефона есть Wi-Fi или Ethernet с групповой рассылкой: хотя бы один адрес записан с префиксом (по ним ядро ищет устройства рядом)
            assertTrue("ни у одного адреса нет длины префикса сети, хотя есть общая сеть: " + text.trim(), sharedNetwork || !expectShared);
            System.out.println("local-addrs.txt: " + text.trim().replace('\n', ' '));
        }
    }

    // ---- окно сканера ----------------------------------------------------------------------

    private boolean hasCamera() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    /** Результат скрипта в окне (JSON-значение: строка в кавычках, true, число…) или {@code null}, если страница не ответила за 10 с. */
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

    /** Всё, что страница получила событием themesh-scan (слушатель {@link #LISTEN}). */
    private JSONArray scans(ActivityScenario<MainActivity> scenario) throws Exception {
        String json = evaluate(scenario, "JSON.stringify(window.__scans)"); // результат — строка JSON внутри строки JSON
        return new JSONArray((String) new JSONTokener(json).nextValue());
    }

    /** Экраны сканера, которые сейчас есть (на экране или под другим). */
    private List<ScanActivity> scanners() {
        List<ScanActivity> found = new ArrayList<>();
        instrumentation.runOnMainSync(() -> {
            for (Stage stage : new Stage[] {Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED}) {
                for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)) {
                    if (a instanceof ScanActivity && !found.contains(a)) {
                        found.add((ScanActivity) a);
                    }
                }
            }
        });
        return found;
    }

    private ScanActivity waitForScanner(long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            List<ScanActivity> now = scanners();
            if (!now.isEmpty()) {
                return now.get(0);
            }
            Thread.sleep(250);
        }
        fail("экран сканера не открылся за " + timeoutMs / 1000 + " с");
        return null;
    }

    private void waitForNoScanner(long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end && !scanners().isEmpty()) {
            Thread.sleep(250);
        }
        assertTrue("экран сканера не закрылся", scanners().isEmpty());
    }

    // ---- узел -------------------------------------------------------------------------------

    private int portOfTheNode() {
        File addr = new File(context.getFilesDir(), "themesh/ui.addr");
        try {
            String s = new String(Files.readAllBytes(addr.toPath()), StandardCharsets.UTF_8).trim();
            return Integer.parseInt(s.substring(s.lastIndexOf(':') + 1));
        } catch (IOException | RuntimeException e) {
            return DEFAULT_PORT;
        }
    }

    /** {@code null}, если узел ответил правильным доказательством; иначе что не так. */
    private String checkHandshake(int port) {
        try {
            String token = new String(Files.readAllBytes(new File(context.getFilesDir(), "themesh/ui.token").toPath()), StandardCharsets.UTF_8).trim();
            byte[] raw = new byte[16];
            new SecureRandom().nextBytes(raw);
            StringBuilder nonce = new StringBuilder();
            for (byte b : raw) {
                nonce.append(String.format("%02x", b));
            }
            JSONObject answer = new JSONObject(get(port, "/api/handshake?n=" + nonce));
            String proof = answer.optString("proof");
            return Handshake.matches(proof, Handshake.proof(token, nonce.toString())) ? null : "неверное доказательство: " + proof;
        } catch (Exception e) {
            return e.toString();
        }
    }

    private boolean nodeIsConfigured(int port) throws Exception {
        return nodeState(port).optBoolean("configured");
    }

    /** Имя, которое узел без сети предлагает себе ({@code self.defaultName} в {@code GET /api/state}). */
    private String offeredName(int port) throws Exception {
        JSONObject self = nodeState(port).optJSONObject("self");
        return self == null ? "" : self.optString("defaultName");
    }

    private JSONObject nodeState(int port) throws Exception {
        String token = new String(Files.readAllBytes(new File(context.getFilesDir(), "themesh/ui.token").toPath()), StandardCharsets.UTF_8).trim();
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/api/state").openConnection(Proxy.NO_PROXY);
        try {
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setConnectTimeout(5000);
            c.setReadTimeout(10_000);
            return new JSONObject(read(c.getInputStream()));
        } finally {
            c.disconnect();
        }
    }

    private static String get(int port, String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection(Proxy.NO_PROXY);
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(2000);
            c.setReadTimeout(3000);
            if (c.getResponseCode() != 200) {
                throw new IOException("HTTP " + c.getResponseCode());
            }
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ---- окно -------------------------------------------------------------------------------

    private boolean waitForScript(ActivityScenario<MainActivity> scenario, String script, long deadline) throws InterruptedException {
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<String> value = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            scenario.onActivity(activity -> {
                WebView web = activity.findViewById(R.id.web);
                web.evaluateJavascript(script, v -> {
                    value.set(v);
                    done.countDown();
                });
            });
            if (done.await(5, TimeUnit.SECONDS) && "true".equals(value.get())) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    // ---- процессы и служба ------------------------------------------------------------------

    /** Дочерние процессы приложения, запущенные из libthemesh.so. */
    private static List<Integer> nodeProcesses() {
        List<Integer> out = new ArrayList<>();
        File[] procs = new File("/proc").listFiles();
        if (procs == null) {
            return out;
        }
        for (File p : procs) {
            if (!p.getName().matches("\\d+")) {
                continue;
            }
            try {
                int pid = Integer.parseInt(p.getName());
                if (parentOf(pid) != Process.myPid()) {
                    continue;
                }
                if (rawCommandLine(pid).split("\0", -1)[0].endsWith("libthemesh.so")) {
                    out.add(pid);
                }
            } catch (IOException | RuntimeException e) {
                // процесс успел завершиться или недоступен
            }
        }
        return out;
    }

    private static int parentOf(int pid) throws IOException {
        String stat = new String(Files.readAllBytes(new File("/proc/" + pid + "/stat").toPath()), StandardCharsets.UTF_8);
        // «pid (comm) S ppid …»: comm может содержать пробелы и скобки
        return Integer.parseInt(stat.substring(stat.lastIndexOf(')') + 2).split(" ")[1]);
    }

    /** Командная строка как есть: аргументы разделены нулевыми байтами, первый — путь к исполняемому файлу. */
    private static String rawCommandLine(int pid) throws IOException {
        return new String(Files.readAllBytes(new File("/proc/" + pid + "/cmdline").toPath()), StandardCharsets.UTF_8);
    }

    private static String commandLine(int pid) throws IOException {
        return rawCommandLine(pid).replace('\0', ' ').trim();
    }

    @SuppressWarnings("deprecation")
    private boolean serviceIsRunning() {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(Integer.MAX_VALUE)) {
            if (NodeService.class.getName().equals(s.service.getClassName())) {
                return s.foreground;
            }
        }
        return false;
    }

    private boolean waitForNodeNotification() throws InterruptedException {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        long end = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < end) {
            for (StatusBarNotification n : nm.getActiveNotifications()) {
                if (n.getId() == Notifier.ID_NODE) {
                    return true;
                }
            }
            Thread.sleep(300);
        }
        return false;
    }

    @Test
    public void quitStopsTheNode() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            long deadline = System.currentTimeMillis() + waitMs;
            while (nodeProcesses().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(300);
            }
            assertFalse("узел запустился", nodeProcesses().isEmpty());
            NodeService.quit(context);
            long end = System.currentTimeMillis() + 20_000;
            while (!nodeProcesses().isEmpty() && System.currentTimeMillis() < end) {
                Thread.sleep(200);
            }
            if (!nodeProcesses().isEmpty()) {
                fail("после «Выйти» узел всё ещё работает: " + nodeProcesses());
            }
        }
    }
}
