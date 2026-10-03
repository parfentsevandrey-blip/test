package app.themesh.mobile.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Журнал: файл themesh.log. В него идёт всё, что печатает узел (его вывод перенаправлен сюда при
 * запуске), и короткие записи самого приложения с пометкой «[app]» — когда запускали, почему
 * перезапустили. Секретов (токена, кода входа) здесь не бывает ни в каком виде.
 */
public final class AppLog {
    /** Кому ещё передавать записи (logcat). */
    public interface Sink {
        void log(char level, String message);
    }

    /** Предел размера, после которого журнал уходит в themesh.log.1. */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final File file;
    private final Sink sink;

    public AppLog(File file, Sink sink) {
        this.file = file;
        this.sink = sink;
    }

    public File file() {
        return file;
    }

    public void i(String message) {
        write('I', message);
    }

    public void w(String message) {
        write('W', message);
    }

    public void e(String message, Throwable t) {
        if (t == null) {
            write('E', message);
            return;
        }
        StringWriter sw = new StringWriter();
        sw.append(message).append(": ");
        t.printStackTrace(new PrintWriter(sw));
        write('E', sw.toString().trim());
    }

    private synchronized void write(char level, String message) {
        if (sink != null) {
            sink.log(level, message);
        }
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date());
        String line = stamp + " [app] " + level + " " + message + "\n";
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // журнал — удобство, а не причина падать
        }
    }

    /**
     * Перед запуском узла (он тогда не работает и файл не держит): если журнал вырос больше
     * {@code maxBytes}, старый уходит в «имя.1», прежний «.1» удаляется.
     */
    public static void rotate(File file, long maxBytes) {
        if (file.length() <= maxBytes) {
            return;
        }
        File old = new File(file.getPath() + ".1");
        if (old.exists() && !old.delete()) {
            truncate(file);
            return;
        }
        if (!file.renameTo(old)) {
            truncate(file);
        }
    }

    /**
     * Пока узел работает и держит файл открытым на дописывание, переименовывать его бессмысленно (он
     * продолжит писать в «.1»): копируем хвост в «.1» и обрезаем журнал до нуля.
     */
    public static void rotateRunning(File file, long maxBytes) {
        if (file.length() <= maxBytes) {
            return;
        }
        File old = new File(file.getPath() + ".1");
        try {
            try (FileOutputStream out = new FileOutputStream(old, false)) {
                out.write(readTail(file, 256 * 1024));
            }
            truncate(file);
        } catch (IOException ignored) {
            // в следующий раз
        }
    }

    private static void truncate(File file) {
        try (RandomAccessFile f = new RandomAccessFile(file, "rw")) {
            f.setLength(0);
        } catch (IOException ignored) {
            // ничего не поделаешь
        }
    }

    /** Последние {@code max} байт файла (пусто, если файла нет). */
    public static byte[] readTail(File file, int max) throws IOException {
        if (!file.isFile()) {
            return new byte[0];
        }
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            long len = f.length();
            int n = (int) Math.min(len, max);
            byte[] b = new byte[n];
            f.seek(len - n);
            f.readFully(b);
            return b;
        }
    }
}
