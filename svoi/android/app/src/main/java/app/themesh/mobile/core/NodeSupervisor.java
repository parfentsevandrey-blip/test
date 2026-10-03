package app.themesh.mobile.core;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Владеет процессом узла: запускает {@code libthemesh.so up --no-browser --exit-when-stdin-closes --ui
 * 127.0.0.1:<порт> --dir <каталог данных>}, ждёт, пока интерфейс ответит и пройдёт handshake, следит
 * за процессом и перезапускает его, если он упал (паузы 1, 2, 4 … 30 секунд, после двух минут работы
 * отсчёт начинается заново). Это обычная Java без Android: так её можно проверить на компьютере
 * настоящей программой.
 *
 * <p>Stdin процесса — канал, который остаётся открытым: пока приложение живо, узел работает; как только
 * канал закрывается (приложение вышло или умерло, как бы это ни случилось), узел завершается сам.
 * Остановка: закрыть stdin, подождать до 6 секунд, затем SIGTERM, ещё 3 секунды и SIGKILL.
 */
public final class NodeSupervisor {
    /** Почему узел не работает. Разбираются в Android-слое: тексты для человека — там, в ресурсах. */
    public static final class Failure {
        public enum Kind {
            /** В APK нет программы для этого процессора. */
            BINARY_MISSING,
            /** Файл есть, но запустить его нельзя. */
            NOT_EXECUTABLE,
            /** Не нашёлся свободный порт на 127.0.0.1. */
            NO_PORT,
            /** ProcessBuilder.start() не удался. */
            SPAWN_FAILED,
            /** Процесс завершился, так и не открыв интерфейс. */
            EXITED_AT_START,
            /** Процесс жив, но интерфейс не ответил за отведённое время. */
            NO_ANSWER,
            /** Узел работал и остановился сам. */
            CRASHED
        }

        public final Kind kind;
        public final int exitCode;
        /** Путь или текст исключения, для журнала и сообщения. */
        public final String detail;

        public Failure(Kind kind, int exitCode, String detail) {
            this.kind = kind;
            this.exitCode = exitCode;
            this.detail = detail == null ? "" : detail;
        }

        @Override
        public String toString() {
            return kind + (exitCode != 0 ? " (exit " + exitCode + ")" : "") + (detail.isEmpty() ? "" : ": " + detail);
        }
    }

    /** События вызываются из потока наблюдателя; долго в них не задерживаться. */
    public interface Listener {
        /** Запускаем процесс (попытка по счёту). */
        void onStarting(int attempt);

        /** Интерфейс отвечает и это наш узел. */
        void onReady(NodeApi api, int port);

        /** Не запустился или остановился сам; следующая попытка через {@code retryInMs}. */
        void onFailed(Failure failure, long retryInMs);

        /** Наблюдатель закончил работу (после {@link #requestStop()}). */
        void onStopped();
    }

    /** Что и как запускать. */
    public static final class Spec {
        /** Исполняемый файл ядра (ApplicationInfo.nativeLibraryDir/libthemesh.so). */
        public File binary;
        /** HOME и рабочий каталог (filesDir). */
        public File filesDir;
        /** TMPDIR (cacheDir). */
        public File cacheDir;
        /** THEMESH_DIR и --dir: ключи, почта, настройки; здесь же ui.addr и ui.token. */
        public File dataDir;
        /** Сюда идёт весь вывод узла. */
        public File logFile;
        /** THEMESH_LOCAL_ADDRS_FILE. */
        public File addrsFile;
        public int preferredPort = Ports.DEFAULT;
        /** Порт прошлого запуска (0 — не помним). */
        public IntSupplier lastPort = () -> 0;
        /** Дополнительные флаги командной строки (для тестов). */
        public List<String> extraArgs = Collections.emptyList();
        public long startTimeoutMs = 40_000;
        public long stopGraceMs = 6_000;
        public long killGraceMs = 3_000;
    }

    private static final Pattern ADDR = Pattern.compile("127\\.0\\.0\\.1:([0-9]{1,5})");

    private final Spec spec;
    private final AppLog log;
    private final Listener listener;
    private final Backoff backoff = new Backoff();
    private final Object lock = new Object();

    private Thread thread;
    private boolean stopRequested;
    private boolean restartRequested;
    private Process current;

    public NodeSupervisor(Spec spec, AppLog log, Listener listener) {
        this.spec = spec;
        this.log = log;
        this.listener = listener;
    }

    /** Запускает наблюдателя (один раз). */
    public synchronized void start() {
        if (thread != null) {
            throw new IllegalStateException("already started");
        }
        thread = new Thread(this::loop, "themesh-node");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Просит остановиться и сразу закрывает stdin узла (быстро, можно из главного потока). Дальше
     * наблюдатель сам доведёт дело до конца (SIGTERM, SIGKILL) и позовёт {@link Listener#onStopped()}.
     */
    public void requestStop() {
        Process p;
        synchronized (lock) {
            stopRequested = true;
            p = current;
            lock.notifyAll();
        }
        closeStdin(p);
    }

    /** Ждёт, пока наблюдатель закончит; {@code true}, если закончил. */
    public boolean awaitTermination(long timeoutMs) throws InterruptedException {
        Thread t;
        synchronized (this) {
            t = thread;
        }
        if (t == null) {
            return true;
        }
        t.join(timeoutMs);
        return !t.isAlive();
    }

    /**
     * «Запустить ещё раз»: не ждать паузы, а если процесс сейчас жив (завис при запуске) — остановить его
     * и запустить заново. Паузы после этого снова начинаются с секунды.
     */
    public void restartNow() {
        Process p;
        synchronized (lock) {
            if (stopRequested) {
                return;
            }
            restartRequested = true;
            p = current;
            lock.notifyAll();
        }
        closeStdin(p);
    }

    // ---- наблюдатель ------------------------------------------------------------------------

    private void loop() {
        int attempt = 0;
        try {
            for (;;) {
                synchronized (lock) {
                    if (stopRequested) {
                        break;
                    }
                    if (restartRequested) {
                        backoff.reset(); // человек нажал «Запустить ещё раз»: паузы снова с секунды
                    }
                    restartRequested = false;
                }
                attempt++;
                notifyStarting(attempt);
                long t0 = System.nanoTime();
                Failure failure = runOnce();
                long uptimeMs = (System.nanoTime() - t0) / 1_000_000;
                boolean restart;
                synchronized (lock) {
                    if (stopRequested) {
                        break;
                    }
                    restart = restartRequested;
                }
                if (restart || failure == null) {
                    // человек просил «запустить ещё раз»: без паузы и без сообщения об ошибке
                    backoff.reset();
                    continue;
                }
                long wait = backoff.onExit(uptimeMs);
                log.w("node is down (" + failure + "), up for " + uptimeMs / 1000 + " s; next attempt in " + wait + " ms");
                notifyFailed(failure, wait);
                if (!sleep(wait)) {
                    break;
                }
            }
        } finally {
            log.i("node supervisor finished");
            try {
                listener.onStopped();
            } catch (RuntimeException e) {
                log.e("listener.onStopped", e);
            }
        }
    }

    /** Один запуск от процесса до его конца. {@code null} — завершили сами (остановка или перезапуск). */
    private Failure runOnce() {
        File bin = spec.binary;
        if (!bin.isFile()) {
            return new Failure(Failure.Kind.BINARY_MISSING, 0, bin.getPath());
        }
        if (!bin.canExecute() && !bin.setExecutable(true)) {
            return new Failure(Failure.Kind.NOT_EXECUTABLE, 0, bin.getPath());
        }
        spec.dataDir.mkdirs();
        spec.cacheDir.mkdirs();
        File logDir = spec.logFile.getAbsoluteFile().getParentFile();
        if (logDir != null) {
            logDir.mkdirs();
        }
        AppLog.rotate(spec.logFile, AppLog.MAX_BYTES);
        int port = Ports.pick(spec.preferredPort, spec.lastPort.getAsInt());
        if (port == 0) {
            return new Failure(Failure.Kind.NO_PORT, 0, "");
        }
        long spawnedAt = System.currentTimeMillis();
        Process p;
        try {
            p = spawn(port);
        } catch (IOException e) {
            log.e("cannot start " + bin, e);
            return new Failure(Failure.Kind.SPAWN_FAILED, 0, String.valueOf(e.getMessage()));
        }
        log.i("node started: " + bin.getName() + " up --ui 127.0.0.1:" + port);
        synchronized (lock) {
            current = p;
        }
        // Конец процесса будит наблюдателя сразу: пока узел работает, опрашивать его каждые доли секунды незачем (батарея).
        Thread waiter = new Thread(() -> {
            try {
                p.waitFor();
            } catch (InterruptedException ignored) {
                // наблюдатель завершается
            }
            synchronized (lock) {
                lock.notifyAll();
            }
        }, "themesh-node-wait");
        waiter.setDaemon(true);
        waiter.start();
        try {
            return supervise(p, port, spawnedAt);
        } finally {
            synchronized (lock) {
                current = null;
            }
            closeStdin(p);
            if (exitCode(p) == null) {
                p.destroyForcibly();
            }
        }
    }

    private Process spawn(int port) throws IOException {
        List<String> cmd = new ArrayList<>(Arrays.asList(
                spec.binary.getPath(), "up", "--no-browser", "--exit-when-stdin-closes",
                "--ui", "127.0.0.1:" + port, "--dir", spec.dataDir.getPath()));
        cmd.addAll(spec.extraArgs);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(spec.filesDir);
        Map<String, String> env = pb.environment();
        env.put("HOME", spec.filesDir.getPath());
        env.put("TMPDIR", spec.cacheDir.getPath());
        env.put("THEMESH_DIR", spec.dataDir.getPath());
        env.put("THEMESH_LOCAL_ADDRS_FILE", spec.addrsFile.getPath());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(spec.logFile));
        // stdin остаётся каналом: мы держим его открытым
        return pb.start();
    }

    private Failure supervise(Process p, int port, long spawnedAt) {
        long t0 = System.nanoTime();
        boolean ready = false;
        boolean timedOut = false;
        boolean asked = false; // уже попросили выйти (закрыли stdin)
        long killAt = 0;
        long forceAt = 0;
        long lastLogCheck = t0;
        for (;;) {
            Integer code = exitCode(p);
            boolean wanted;
            synchronized (lock) {
                wanted = stopRequested || restartRequested;
            }
            if (code != null) {
                if (wanted) {
                    log.i("node exited (code " + code + ") on request");
                    return null;
                }
                if (timedOut) {
                    return new Failure(Failure.Kind.NO_ANSWER, code, "");
                }
                return new Failure(ready ? Failure.Kind.CRASHED : Failure.Kind.EXITED_AT_START, code, "");
            }
            long now = (System.nanoTime() - t0) / 1_000_000;
            if ((wanted || timedOut) && !asked) {
                asked = true;
                closeStdin(p);
                killAt = now + spec.stopGraceMs;
            }
            if (asked && now >= killAt && forceAt == 0) {
                log.w("node did not exit by itself, sending SIGTERM");
                p.destroy();
                forceAt = now + spec.killGraceMs;
            }
            if (forceAt != 0 && now >= forceAt) {
                log.w("node did not react to SIGTERM, killing");
                p.destroyForcibly();
                forceAt = Long.MAX_VALUE;
            }
            if (!ready && !asked) {
                NodeApi api = tryReady(port, spawnedAt);
                if (api != null) {
                    ready = true;
                    log.i("node is ready at " + api.origin());
                    notifyReady(api, portOf(api));
                } else if (now > spec.startTimeoutMs) {
                    log.w("node did not answer in " + spec.startTimeoutMs / 1000 + " s");
                    timedOut = true;
                    continue;
                }
            }
            if (ready && (System.nanoTime() - lastLogCheck) > TimeUnit.MINUTES.toNanos(5)) {
                lastLogCheck = System.nanoTime();
                AppLog.rotateRunning(spec.logFile, AppLog.MAX_BYTES);
            }
            synchronized (lock) {
                if (!stopRequested && !restartRequested && exitCode(p) == null) {
                    try {
                        // Запуск и остановка требуют частых проверок, работа — нет: конец процесса и просьбы будят сами.
                        lock.wait(ready && !asked ? 5_000 : 100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        stopRequested = true;
                    }
                }
            }
        }
    }

    /** Если интерфейс открыт и отвечает как наш узел — доступ к нему, иначе {@code null}. */
    private NodeApi tryReady(int port, long spawnedAt) {
        File addrFile = new File(spec.dataDir, "ui.addr");
        String addr = readTrim(addrFile);
        String token = readTrim(new File(spec.dataDir, "ui.token"));
        if (addr.isEmpty() || token.length() < 32) {
            return null;
        }
        Matcher m = ADDR.matcher(addr);
        if (!m.matches()) {
            return null;
        }
        int actual = Integer.parseInt(m.group(1));
        // Файл мог остаться от прошлого запуска, который не успел его убрать. Если порт не тот, что мы
        // выбрали, верим только файлу, записанному уже после нашего запуска.
        if (actual != port && addrFile.lastModified() < spawnedAt - 2_000) {
            return null;
        }
        String origin = "http://127.0.0.1:" + actual;
        if (!NodeApi.verify(origin, token)) {
            return null;
        }
        return new NodeApi(origin, token);
    }

    private static int portOf(NodeApi api) {
        String origin = api.origin();
        return Integer.parseInt(origin.substring(origin.lastIndexOf(':') + 1));
    }

    // ---- мелочи -----------------------------------------------------------------------------

    /** Ждёт {@code ms}; {@code false}, если за это время попросили остановиться. Перезапуск прерывает ожидание. */
    private boolean sleep(long ms) {
        long end = System.nanoTime() + ms * 1_000_000;
        synchronized (lock) {
            while (!stopRequested && !restartRequested) {
                long left = (end - System.nanoTime()) / 1_000_000;
                if (left <= 0) {
                    break;
                }
                try {
                    lock.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stopRequested = true;
                }
            }
            return !stopRequested;
        }
    }

    private static Integer exitCode(Process p) {
        try {
            return p.exitValue();
        } catch (IllegalThreadStateException e) {
            return null;
        }
    }

    private static void closeStdin(Process p) {
        if (p == null) {
            return;
        }
        try (OutputStream out = p.getOutputStream()) {
            out.flush();
        } catch (IOException ignored) {
            // уже закрыт
        }
    }

    private static String readTrim(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private void notifyStarting(int attempt) {
        try {
            listener.onStarting(attempt);
        } catch (RuntimeException e) {
            log.e("listener.onStarting", e);
        }
    }

    private void notifyReady(NodeApi api, int port) {
        try {
            listener.onReady(api, port);
        } catch (RuntimeException e) {
            log.e("listener.onReady", e);
        }
    }

    private void notifyFailed(Failure f, long retryInMs) {
        try {
            listener.onFailed(f, retryInMs);
        } catch (RuntimeException e) {
            log.e("listener.onFailed", e);
        }
    }
}
