package app.themesh.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import app.themesh.mobile.core.Handshake;

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
        }
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
