package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Настоящий узел (программа themesh, {@code src/main/jniLibs/x86_64/libthemesh.so}) во временном каталоге — так, как его запускает
 * приложение ({@link NodeSupervisor}), только на компьютере. Тесты, которым он нужен, пропускаются, если ядро не собрано
 * (tools/build-core.sh) или компьютер не Linux x86_64: так же, как {@code NodeSupervisorTest}.
 */
final class RealNode implements AutoCloseable {
    private final NodeSupervisor supervisor;
    private final NodeApi api;
    final File files;
    final File dataDir;

    private RealNode(NodeSupervisor supervisor, NodeApi api, File files, File dataDir) {
        this.supervisor = supervisor;
        this.api = api;
        this.files = files;
        this.dataDir = dataDir;
    }

    /** Ядро для этого компьютера или пропуск теста. */
    static File core() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "");
        File f = new File("src/main/jniLibs/x86_64/libthemesh.so");
        assumeTrue("нужен Linux x86_64 и собранное ядро (tools/build-core.sh x86_64)",
                os.contains("linux") && (arch.equals("amd64") || arch.equals("x86_64")) && f.isFile());
        return f.getAbsoluteFile();
    }

    /**
     * Запускает узел в {@code root} и ждёт, пока он ответит. {@code localAddrs} — адреса «телефона» (то, что приложение пишет в
     * local-addrs.txt): они попадают в приглашения, которые создаёт узел.
     */
    static RealNode start(File root, List<String> localAddrs) throws Exception {
        File binary = core();
        File files = new File(root, "files");
        NodeSupervisor.Spec spec = new NodeSupervisor.Spec();
        spec.binary = binary;
        spec.filesDir = files;
        spec.cacheDir = new File(root, "cache");
        spec.dataDir = new File(files, "themesh");
        spec.logFile = new File(files, "themesh.log");
        spec.addrsFile = new File(files, "local-addrs.txt");
        spec.preferredPort = Ports.anyFree();
        spec.extraArgs = Arrays.asList("--no-stun", "--no-portmap"); // тесты не ходят в интернет и не просят роутер
        files.mkdirs();
        StringBuilder addrs = new StringBuilder();
        for (String a : localAddrs) {
            addrs.append(a).append('\n');
        }
        Files.write(spec.addrsFile.toPath(), addrs.toString().getBytes(StandardCharsets.UTF_8));

        BlockingQueue<NodeApi> ready = new LinkedBlockingQueue<>();
        BlockingQueue<String> failed = new LinkedBlockingQueue<>();
        NodeSupervisor supervisor = new NodeSupervisor(spec, new AppLog(spec.logFile, null), new NodeSupervisor.Listener() {
            @Override
            public void onStarting(int attempt) {
            }

            @Override
            public void onReady(NodeApi api, int port) {
                ready.add(api);
            }

            @Override
            public void onFailed(NodeSupervisor.Failure failure, long retryInMs) {
                failed.add(failure.toString());
            }

            @Override
            public void onStopped() {
            }
        });
        supervisor.start();
        NodeApi api = ready.poll(40, TimeUnit.SECONDS);
        if (api == null) {
            supervisor.requestStop();
            supervisor.awaitTermination(20_000);
        }
        assertNotNull("узел не ответил за 40 с; сбои: " + failed + ", журнал: " + spec.logFile, api);
        assertEquals("порт выбран приложением", "http://127.0.0.1:" + spec.preferredPort, api.origin());
        return new RealNode(supervisor, api, files, spec.dataDir);
    }

    NodeApi api() {
        return api;
    }

    @Override
    public void close() throws Exception {
        supervisor.requestStop();
        assertTrue("узел не остановился", supervisor.awaitTermination(20_000));
    }
}
