package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Управление процессом узла. Логика (запуск, ожидание, перезапуск с паузами, остановка с повышением строгости)
 * проверяется на сценариях-заглушках, а настоящая программа themesh — если её сборка для этого процессора лежит в
 * src/main/jniLibs (tools/build-core.sh): тогда проверяется и вся командная строка.
 */
public class NodeSupervisorTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    private volatile NodeApi lastApi;
    private NodeSupervisor supervisor;
    private File files;

    @Before
    public void setUp() throws Exception {
        files = tmp.newFolder("files");
    }

    @After
    public void tearDown() throws Exception {
        if (supervisor != null) {
            supervisor.requestStop();
            assertTrue("наблюдатель завершился", supervisor.awaitTermination(20_000));
        }
    }

    private NodeSupervisor.Spec spec(File binary) throws Exception {
        NodeSupervisor.Spec s = new NodeSupervisor.Spec();
        s.binary = binary;
        s.filesDir = files;
        s.cacheDir = tmp.newFolder();
        s.dataDir = new File(files, "themesh");
        s.logFile = new File(files, "themesh.log");
        s.addrsFile = new File(files, "local-addrs.txt");
        s.preferredPort = Ports.anyFree();
        return s;
    }

    private void start(NodeSupervisor.Spec spec) {
        supervisor = new NodeSupervisor(spec, new AppLog(spec.logFile, null), new NodeSupervisor.Listener() {
            @Override
            public void onStarting(int attempt) {
                events.add("starting:" + attempt);
            }

            @Override
            public void onReady(NodeApi api, int port) {
                lastApi = api;
                events.add("ready:" + port);
            }

            @Override
            public void onFailed(NodeSupervisor.Failure failure, long retryInMs) {
                events.add("failed:" + failure.kind + ":" + failure.exitCode + ":" + retryInMs);
            }

            @Override
            public void onStopped() {
                events.add("stopped");
            }
        });
        supervisor.start();
    }

    private String next(long seconds) throws InterruptedException {
        String s = events.poll(seconds, TimeUnit.SECONDS);
        assertNotNull("событие не пришло за " + seconds + " с", s);
        return s;
    }

    private File script(String name, String body) throws Exception {
        File f = new File(tmp.getRoot(), name);
        Files.write(f.toPath(), ("#!/bin/sh\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(f.setExecutable(true));
        return f;
    }

    private static boolean unixLike() {
        return new File("/bin/sh").canExecute();
    }

    // ---- заглушки ---------------------------------------------------------------------------

    @Test
    public void aMissingProgramIsReportedAndRetried() throws Exception {
        start(spec(new File(tmp.getRoot(), "no-such-libthemesh.so")));
        assertEquals("starting:1", next(5));
        assertEquals("failed:BINARY_MISSING:0:1000", next(5));
        assertEquals("starting:2", next(5));
        assertEquals("failed:BINARY_MISSING:0:2000", next(5));
    }

    @Test
    public void aProgramThatExitsAtOnceGetsGrowingPauses() throws Exception {
        assumeTrue(unixLike());
        start(spec(script("exit3.sh", "exit 3")));
        assertEquals("starting:1", next(5));
        assertEquals("failed:EXITED_AT_START:3:1000", next(5));
        assertEquals("starting:2", next(5));
        assertEquals("failed:EXITED_AT_START:3:2000", next(5));
        assertEquals("starting:3", next(5));
        assertEquals("failed:EXITED_AT_START:3:4000", next(8));
    }

    /** Скрипт-заглушка: записывает свои аргументы по одному на строку и выходит. */
    private File argsRecorder(File out) throws Exception {
        return script("args.sh", "for a in \"$@\"; do printf '%s\\n' \"$a\"; done > '" + out.getPath() + "'\nexit 3");
    }

    @Test
    public void theDeviceNameIsPassedAsTheNameFlag() throws Exception {
        assumeTrue(unixLike());
        File out = new File(tmp.getRoot(), "args.txt");
        NodeSupervisor.Spec s = spec(argsRecorder(out));
        s.deviceName = "Pixel 7 Pro";
        start(s);
        assertEquals("starting:1", next(5));
        assertEquals("failed:EXITED_AT_START:3:1000", next(5));
        java.util.List<String> args = Files.readAllLines(out.toPath());
        int i = args.indexOf("--name");
        assertTrue("есть --name: " + args, i >= 0 && i + 1 < args.size());
        assertEquals("имя одним аргументом, вместе с пробелами", "Pixel 7 Pro", args.get(i + 1));
        assertEquals("up", args.get(0));
        assertTrue(args.contains("--exit-when-stdin-closes"));
        assertTrue(args.contains("--no-browser"));
    }

    @Test
    public void withoutADeviceNameNoNameFlagIsPassed() throws Exception {
        assumeTrue(unixLike());
        for (String none : new String[] {null, ""}) {
            File out = new File(tmp.getRoot(), "args-" + (none == null ? "null" : "empty") + ".txt");
            NodeSupervisor.Spec s = spec(argsRecorder(out));
            s.deviceName = none;
            NodeSupervisor one = new NodeSupervisor(s, new AppLog(s.logFile, null), new NodeSupervisor.Listener() {
                @Override
                public void onStarting(int attempt) {
                    events.add("starting:" + attempt);
                }

                @Override
                public void onReady(NodeApi api, int port) {
                }

                @Override
                public void onFailed(NodeSupervisor.Failure failure, long retryInMs) {
                    events.add("failed");
                }

                @Override
                public void onStopped() {
                }
            });
            one.start();
            assertEquals("starting:1", next(5));
            assertEquals("failed", next(5));
            one.requestStop();
            assertTrue(one.awaitTermination(10_000));
            assertFalse(Files.readAllLines(out.toPath()).contains("--name"));
            events.clear();
        }
    }

    @Test
    public void restartNowSkipsTheWaitAndResetsThePauses() throws Exception {
        assumeTrue(unixLike());
        start(spec(script("exit3.sh", "exit 3")));
        next(5); // starting:1
        assertEquals("failed:EXITED_AT_START:3:1000", next(5));
        assertEquals("starting:2", next(5));
        assertEquals("failed:EXITED_AT_START:3:2000", next(5));
        supervisor.restartNow(); // человек нажал «Запустить ещё раз»: не ждать 2 секунды
        long t0 = System.nanoTime();
        assertEquals("starting:3", next(5));
        assertTrue("без паузы", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 1500);
        assertEquals("после «ещё раз» паузы снова с секунды", "failed:EXITED_AT_START:3:1000", next(5));
    }

    @Test
    public void aProgramThatNeverAnswersIsStoppedAndReported() throws Exception {
        assumeTrue(unixLike());
        NodeSupervisor.Spec s = spec(script("hang.sh", "trap '' TERM\nwhile :; do sleep 1; done"));
        s.startTimeoutMs = 400;
        s.stopGraceMs = 300;
        s.killGraceMs = 300;
        start(s);
        assertEquals("starting:1", next(5));
        assertEquals("failed:NO_ANSWER:137:1000", next(10)); // SIGKILL: 128 + 9
    }

    @Test
    public void stopWaitsForTheProgramToExitByItselfThenInsists() throws Exception {
        assumeTrue(unixLike());
        // «узел», который не слушает stdin и не отвечает на SIGTERM: остановка доходит до SIGKILL
        NodeSupervisor.Spec s = spec(script("deaf.sh", "trap '' TERM\nwhile :; do sleep 1; done"));
        s.stopGraceMs = 300;
        s.killGraceMs = 300;
        start(s);
        assertEquals("starting:1", next(5));
        Thread.sleep(300);
        long t0 = System.nanoTime();
        supervisor.requestStop();
        assertTrue(supervisor.awaitTermination(10_000));
        assertEquals("stopped", lastOf());
        assertTrue("SIGKILL подействовал", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 5000);
    }

    private String lastOf() {
        String last = null;
        for (String e : events) {
            last = e;
        }
        return last;
    }

    // ---- настоящая программа ----------------------------------------------------------------

    private static File realCore() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "");
        File f = new File("src/main/jniLibs/x86_64/libthemesh.so");
        assumeTrue("нужен Linux x86_64 и собранное ядро (tools/build-core.sh x86_64)",
                os.contains("linux") && (arch.equals("amd64") || arch.equals("x86_64")) && f.isFile());
        return f.getAbsoluteFile();
    }

    private NodeSupervisor.Spec realSpec() throws Exception {
        NodeSupervisor.Spec s = spec(realCore());
        s.extraArgs = Arrays.asList("--no-stun", "--no-portmap"); // тесты не ходят в интернет и не просят роутер
        return s;
    }

    @Test
    public void theRealNodeStartsServesTheApiAndStopsCleanly() throws Exception {
        NodeSupervisor.Spec s = realSpec();
        Files.write(s.addrsFile.toPath(), "192.168.77.5\n".getBytes(StandardCharsets.UTF_8));
        start(s);
        assertEquals("starting:1", next(5));
        String ready = next(30);
        assertTrue(ready, ready.startsWith("ready:"));
        assertEquals("порт выбран приложением", "ready:" + s.preferredPort, ready);

        NodeApi api = lastApi;
        assertEquals("http://127.0.0.1:" + s.preferredPort, api.origin());
        JSONObject state = api.getObject("/api/state");
        assertFalse(state.getBoolean("configured"));
        assertFalse(state.getJSONObject("self").getString("version").isEmpty());
        assertTrue(api.loginUrl().matches("http://127\\.0\\.0\\.1:\\d+/\\?t=[0-9a-f]{48}"));

        // HOME = filesDir: каталог для полученных файлов — внутри него
        String downloadDir = api.getObject("/api/settings").getString("downloadDir");
        assertEquals(new File(files, "Downloads/The Mesh").getPath(), downloadDir);
        assertTrue("ключ устройства в каталоге данных", new File(s.dataDir, "device.key").isFile());

        // THEMESH_LOCAL_ADDRS_FILE: адреса из файла видны в «наших» адресах, когда сеть создана
        api.postObject("/api/mesh/create", new JSONObject().put("meshName", "Test").put("deviceName", "phone-test").put("owner", "Tester"));
        boolean listed = false;
        for (int i = 0; i < 100 && !listed; i++) {
            JSONObject self = api.getObject("/api/state").getJSONObject("self");
            if (self.has("endpoints")) {
                for (int k = 0; k < self.getJSONArray("endpoints").length(); k++) {
                    listed |= self.getJSONArray("endpoints").getJSONObject(k).getString("addr").startsWith("192.168.77.5:");
                }
            }
            if (!listed) {
                Thread.sleep(100);
            }
        }
        assertTrue("адрес из local-addrs.txt попал в адреса узла", listed);

        supervisor.requestStop();
        assertTrue(supervisor.awaitTermination(15_000));
        assertEquals("stopped", lastOf());
        assertFalse("чистый выход убирает ui.addr", new File(s.dataDir, "ui.addr").exists());
        assertTrue(new File(s.dataDir, "device.key").isFile());
    }

    @Test
    public void theRealNodeOffersTheDeviceNameItWasGiven() throws Exception {
        NodeSupervisor.Spec s = realSpec();
        s.deviceName = DeviceName.choose("Pixel 7 Pro", "ignored");
        start(s);
        assertEquals("starting:1", next(5));
        assertTrue(next(30).startsWith("ready:"));
        // пустое имя в запросе значит «возьми предложенное»; узел делает из него метку DNS
        lastApi.postObject("/api/mesh/create", new JSONObject().put("meshName", "T").put("deviceName", "").put("owner", ""));
        assertEquals("pixel-7-pro", lastApi.getObject("/api/state").getJSONObject("self").getString("name"));
    }

    @Test
    public void theRealNodeTakesARussianNameWithAnEmojiToo() throws Exception {
        // аргументы командной строки JVM на компьютере кодирует в системной кодировке: проверяем, только если это UTF-8
        // (на телефоне ART всегда передаёт UTF-8)
        assumeTrue("нужна системная кодировка UTF-8", "UTF-8".equalsIgnoreCase(System.getProperty("sun.jnu.encoding", "")));
        NodeSupervisor.Spec s = realSpec();
        s.deviceName = DeviceName.choose("  Телефон   Андрея 📱 ", "ignored");
        start(s);
        assertEquals("starting:1", next(5));
        assertTrue(next(30).startsWith("ready:"));
        lastApi.postObject("/api/mesh/create", new JSONObject().put("meshName", "T").put("deviceName", "").put("owner", ""));
        assertEquals("telefon-andreya", lastApi.getObject("/api/state").getJSONObject("self").getString("name"));
    }

    @Test
    public void theRealNodeIsRestartedAfterACrashAndStaleFilesDoNotFoolTheSupervisor() throws Exception {
        NodeSupervisor.Spec s = realSpec();
        start(s);
        assertEquals("starting:1", next(5));
        assertTrue(next(30).startsWith("ready:"));

        java.util.List<Integer> nodes = ProcTree.children(ProcTree.myPid(), "libthemesh.so");
        assertEquals("процесс узла — единственный дочерний: " + nodes, 1, nodes.size());
        ProcTree.kill9(nodes.get(0)); // как будто убила система
        assertEquals("failed:CRASHED:137:1000", next(10));
        assertEquals("starting:2", next(10));
        assertTrue("после ui.addr, оставшегося от убитого узла, готовность всё равно настоящая", next(30).startsWith("ready:"));
        assertEquals("api снова отвечает", false, lastApi.getObject("/api/state").getBoolean("configured"));
    }

    @Test
    public void aSecondNodeOnTheSameDirectoryDoesNotRunTwiceAndTheSupervisorRecovers() throws Exception {
        NodeSupervisor.Spec s = realSpec();
        start(s);
        assertEquals("starting:1", next(5));
        assertTrue(next(30).startsWith("ready:"));
        // второй наблюдатель на тот же каталог: узел откажется («already running»), наблюдатель будет повторять попытки
        NodeSupervisor.Spec s2 = spec(s.binary);
        s2.dataDir = s.dataDir;
        s2.extraArgs = s.extraArgs;
        BlockingQueue<String> second = new LinkedBlockingQueue<>();
        NodeSupervisor other = new NodeSupervisor(s2, new AppLog(new File(files, "second.log"), null), new NodeSupervisor.Listener() {
            @Override
            public void onStarting(int attempt) {
                second.add("starting");
            }

            @Override
            public void onReady(NodeApi api, int port) {
                second.add("ready");
            }

            @Override
            public void onFailed(NodeSupervisor.Failure failure, long retryInMs) {
                second.add("failed:" + failure.kind);
            }

            @Override
            public void onStopped() {
                second.add("stopped");
            }
        });
        other.start();
        try {
            assertEquals("starting", second.poll(5, TimeUnit.SECONDS));
            String outcome = second.poll(15, TimeUnit.SECONDS);
            // либо узел отказался сразу (EXITED_AT_START), либо второй наблюдатель присоединился к уже работающему — только не два узла
            assertNotNull(outcome);
            assertTrue(outcome, outcome.equals("failed:EXITED_AT_START") || outcome.equals("ready"));
        } finally {
            other.requestStop();
            assertTrue(other.awaitTermination(20_000));
        }
    }
}
