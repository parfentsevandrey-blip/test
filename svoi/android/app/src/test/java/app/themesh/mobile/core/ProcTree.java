package app.themesh.mobile.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Дочерние процессы по /proc (Linux): в модульных тестах Android нет ProcessHandle. */
final class ProcTree {
    private ProcTree() {
    }

    static int myPid() throws IOException {
        return Integer.parseInt(new File("/proc/self").getCanonicalFile().getName());
    }

    /** Дети процесса {@code parent}, у которых командная строка (argv[0]) заканчивается на {@code suffix}. */
    static List<Integer> children(int parent, String suffix) throws IOException {
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
                String stat = new String(Files.readAllBytes(new File(p, "stat").toPath()), StandardCharsets.UTF_8);
                // «pid (comm) S ppid ...»: comm может содержать пробелы и скобки, поэтому ищем последнюю «)»
                String[] rest = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
                int ppid = Integer.parseInt(rest[1]);
                if (ppid != parent) {
                    continue;
                }
                String cmd = new String(Files.readAllBytes(new File(p, "cmdline").toPath()), StandardCharsets.UTF_8);
                String argv0 = cmd.split("\0", -1)[0];
                if (argv0.endsWith(suffix)) {
                    out.add(Integer.parseInt(p.getName()));
                }
            } catch (IOException | RuntimeException e) {
                // процесс успел завершиться
            }
        }
        return out;
    }

    static void kill9(int pid) throws IOException, InterruptedException {
        new ProcessBuilder("kill", "-9", String.valueOf(pid)).start().waitFor();
    }
}
