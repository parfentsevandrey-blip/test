package app.themesh.mobile;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Второй узел на том же телефоне (эмуляторе): та же программа {@code libthemesh.so}, что внутри приложения, со своим каталогом
 * данных. Он играет роль «Mac» из жизни — устройства, которое стоит рядом в той же сети Wi‑Fi: объявляет себя, спрашивает, кто
 * может его добавить, и отвечает на просьбы. Сети он узнаёт так же, как узел приложения (из файла с адресами телефона): настоящий
 * Android не даёт программам перечислять сетевые интерфейсы, и то, что проверяется здесь, — настоящий путь «по адресу», а не
 * по списку интерфейсов.
 */
final class NodeProcess {
    private static final String TAG = "NodeProcess";

    /** Результат команды: код выхода и весь вывод. */
    static final class Result {
        final int exit;
        final String out;

        Result(int exit, String out) {
            this.exit = exit;
            this.out = out;
        }

        @Override
        public String toString() {
            return "exit " + exit + ": " + out;
        }
    }

    /** Команда, которая ещё работает: строки её вывода и ввод. */
    static final class Running {
        /** Сколько последних строк вывода помнить: узел с {@code --debug} пишет много, а нужен только хвост. */
        private static final int KEEP = 3000;

        final Process process;
        final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final ArrayDeque<String> all = new ArrayDeque<>();
        private final OutputStream stdin;

        /** @param queue складывать ли строки в очередь для {@link #waitForLine} (узлу, который только пишет журнал, она не нужна) */
        Running(Process process, boolean queue) {
            this.process = process;
            this.stdin = process.getOutputStream();
            Thread t = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = r.readLine()) != null) {
                        synchronized (all) {
                            all.addLast(l);
                            if (all.size() > KEEP) {
                                all.removeFirst();
                            }
                        }
                        if (queue) {
                            lines.add(l);
                        }
                    }
                } catch (IOException ignored) {
                    // процесс закончился
                }
            }, "node-process-output");
            t.setDaemon(true);
            t.start();
        }

        /** Ждёт строку, подходящую под регулярное выражение, и возвращает её (или {@code null}, если за время её не было). */
        String waitForLine(String regex, long timeoutMs) throws InterruptedException {
            long end = System.currentTimeMillis() + timeoutMs;
            while (true) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    return null;
                }
                String l = lines.poll(left, TimeUnit.MILLISECONDS);
                if (l == null) {
                    return null;
                }
                if (l.matches("(?s).*" + regex + ".*")) {
                    return l;
                }
            }
        }

        void type(String text) throws IOException {
            stdin.write(text.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }

        String output() {
            synchronized (all) {
                return String.join("\n", all);
            }
        }

        /** Последние {@code n} строк вывода. */
        String tail(int n) {
            synchronized (all) {
                List<String> l = new ArrayList<>(all);
                return String.join("\n", l.subList(Math.max(0, l.size() - n), l.size()));
            }
        }

        /** Код выхода или {@code null}, если команда не закончила за время. */
        Integer waitForExit(long timeoutMs) throws InterruptedException {
            return process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) ? process.exitValue() : null;
        }

        void kill() {
            process.destroyForcibly();
        }
    }

    final File core;
    final File dir;
    final File home;
    final File addrsFile;
    final int uiPort;
    private final String platform;
    private Running node;

    /**
     * @param label    имя папки в кэше приложения
     * @param udpPort  UDP-порт этого узла: не тот, что у узла приложения (41710), чтобы они не мешали друг другу
     * @param platform как узел называет свою систему («darwin/arm64»)
     */
    NodeProcess(Context context, String label, int udpPort, String platform) throws IOException {
        this.platform = platform;
        File base = new File(context.getCacheDir(), label);
        deleteRecursively(base);
        dir = new File(base, "data");
        home = new File(base, "home");
        if (!dir.mkdirs() || !home.mkdirs()) {
            throw new IOException("не создать " + base);
        }
        core = new File(context.getApplicationInfo().nativeLibraryDir, "libthemesh.so");
        addrsFile = new File(base, "local-addrs.txt");
        try (ServerSocket s = new ServerSocket(0)) {
            uiPort = s.getLocalPort();
        }
        Files.write(new File(dir, "config.json").toPath(),
                ("{\"udpPort\":" + udpPort + ",\"stunEnabled\":false,\"portMap\":false}").getBytes(StandardCharsets.UTF_8));

        // Те же сети, что знает узел приложения: он получает их от приложения через этот файл.
        File theirs = new File(context.getFilesDir(), "local-addrs.txt");
        long end = System.currentTimeMillis() + 30_000;
        while ((!theirs.isFile() || theirs.length() == 0) && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Files.copy(theirs.toPath(), addrsFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Log.i(TAG, label + ": сети " + new String(Files.readAllBytes(addrsFile.toPath()), StandardCharsets.UTF_8).trim().replace('\n', ' ')
                + ", UDP " + udpPort + ", интерфейс " + uiPort);
    }

    private ProcessBuilder builder(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(core.getPath());
        for (String a : args) {
            cmd.add(a);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(home);
        pb.redirectErrorStream(true);
        pb.environment().put("HOME", home.getPath());
        pb.environment().put("TMPDIR", home.getPath());
        pb.environment().put("THEMESH_DIR", dir.getPath());
        pb.environment().put("THEMESH_LOCAL_ADDRS_FILE", addrsFile.getPath());
        pb.environment().put("THEMESH_PLATFORM", platform);
        pb.environment().put("QUIC_GO_DISABLE_RECEIVE_BUFFER_WARNING", "true");
        return pb;
    }

    /** Команда `themesh …` для этого узла (каталог данных — из окружения): ждёт конца и возвращает вывод. */
    Result run(long timeoutMs, String... args) throws Exception {
        Running r = new Running(builder(args).start(), false);
        Integer exit = r.waitForExit(timeoutMs);
        if (exit == null) {
            r.kill();
            return new Result(-1, r.output() + "\n(команда не закончила за " + timeoutMs / 1000 + " с)");
        }
        Thread.sleep(100); // читатель вывода дочитывает последние строки
        return new Result(exit, r.output());
    }

    /** Команда, которая работает долго и спрашивает ввод (`themesh nearby join …`). */
    Running start(String... args) throws IOException {
        return new Running(builder(args).start(), true);
    }

    /** Создаёт сеть на этом устройстве (оно становится её администратором). */
    void init(String mesh, String name, String owner) throws Exception {
        Result r = run(60_000, "init", "--mesh", mesh, "--name", name, "--owner", owner);
        if (r.exit != 0) {
            throw new IllegalStateException("themesh init: " + r);
        }
    }

    /** Запускает узел и ждёт, пока он ответит командам (рукопожатие с интерфейсом пройдено). */
    void up() throws Exception {
        node = new Running(builder("up", "--no-browser", "--no-stun", "--no-portmap", "--debug", "--exit-when-stdin-closes",
                "--ui", "127.0.0.1:" + uiPort).start(), false);
        long end = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < end) {
            if (new File(dir, "ui.addr").isFile() && run(15_000, "status").exit == 0) {
                return;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("узел не стал отвечать за 60 с:\n" + node.tail(60));
    }

    /** Журнал узла, чтобы при неудаче было видно, что он делал. */
    String log() {
        return node == null ? "" : node.tail(150);
    }

    void stop() {
        if (node != null) {
            try {
                node.process.getOutputStream().close(); // --exit-when-stdin-closes
            } catch (IOException ignored) {
                // уже закончил
            }
            try {
                if (node.waitForExit(8000) == null) {
                    node.kill();
                }
            } catch (InterruptedException e) {
                node.kill();
                Thread.currentThread().interrupt();
            }
            node = null;
        }
    }

    private static void deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursively(k);
            }
        }
        f.delete();
    }
}
