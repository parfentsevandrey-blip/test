package app.themesh.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import app.themesh.mobile.core.ReceivedFiles;

/**
 * Настоящая запись в общую папку «Загрузки/The Mesh» (MediaStore на Android 10+, обычные файлы на Android 8–9):
 * то, что на компьютере можно проверить только подделкой. Окно и узел не нужны: проверяется {@link ReceivedStore}.
 * Запуск на эмуляторе или телефоне: {@code ./gradlew connectedDebugAndroidTest} или
 * {@code adb shell am instrument -w -e class app.themesh.mobile.ReceivedStoreTest app.themesh.mobile.test/androidx.test.runner.AndroidJUnitRunner}.
 */
@RunWith(AndroidJUnit4.class)
public class ReceivedStoreTest {
    private static final String RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/" + ReceivedFiles.FOLDER;

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<Uri> rows = new ArrayList<>(); // что создали в MediaStore (убираем в конце)
    private final List<File> files = new ArrayList<>(); // что создали как обычные файлы

    @Before
    public void allowWritingOnOldAndroid() {
        if (Build.VERSION.SDK_INT < 29) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    @After
    public void cleanUp() {
        ContentResolver cr = context.getContentResolver();
        for (Uri uri : rows) {
            try {
                cr.delete(uri, null, null);
            } catch (RuntimeException ignored) {
                // уже удалено
            }
        }
        for (File f : files) {
            f.delete();
        }
        // на случай, если тест упал раньше, чем нашёл свой файл: убираем всё тестовое по имени
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                cr.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.Downloads.DISPLAY_NAME + " LIKE ? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?",
                        new String[] {"themesh-test-%", RELATIVE_PATH + "/"});
            } catch (RuntimeException ignored) {
                // нечего убирать
            }
        } else {
            @SuppressWarnings("deprecation")
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ReceivedFiles.FOLDER);
            File[] left = dir.listFiles((d, n) -> n.startsWith("themesh-test-"));
            if (left != null) {
                for (File f : left) {
                    f.delete();
                }
            }
        }
    }

    /** Файл в кэше приложения (как «своя копия» полученного файла: внутри filesDir/cacheDir), с заметным содержимым. */
    private File source(String name) throws IOException {
        File f = new File(context.getCacheDir(), UUID.randomUUID() + "-" + name);
        files.add(f);
        try (OutputStream out = new FileOutputStream(f)) {
            byte[] line = "привет, The Mesh 📱 — строка для копирования\n".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 6000; i++) { // около 400 КБ: несколько буферов копирования
                out.write(line);
            }
        }
        return f;
    }

    private static String uniqueName(String suffix) {
        return "themesh-test-" + UUID.randomUUID().toString().substring(0, 8) + suffix;
    }

    private static byte[] read(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    /** Файл «Загрузки/The Mesh/<name>»: его содержимое и тип, или {@code null}, если такого нет. */
    private Found find(String name) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = context.getContentResolver();
            try (Cursor c = cr.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[] {MediaStore.Downloads._ID, MediaStore.Downloads.MIME_TYPE, MediaStore.Downloads.IS_PENDING},
                    MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?",
                    new String[] {name, RELATIVE_PATH + "/"}, null)) {
                if (c == null || !c.moveToFirst()) {
                    return null;
                }
                Uri uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0));
                rows.add(uri);
                Found found = new Found();
                found.mime = c.getString(1);
                found.pending = c.getInt(2) != 0;
                found.content = read(cr.openInputStream(uri));
                return found;
            }
        }
        @SuppressWarnings("deprecation")
        File f = new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ReceivedFiles.FOLDER), name);
        if (!f.isFile()) {
            return null;
        }
        files.add(f);
        Found found = new Found();
        found.content = Files.readAllBytes(f.toPath());
        return found;
    }

    private static final class Found {
        String mime;
        boolean pending;
        byte[] content;
    }

    @Test
    public void aReceivedFileLandsInDownloadsTheMeshAndTheSourceStays() throws Exception {
        String name = uniqueName(".txt");
        File source = source(name);
        byte[] original = Files.readAllBytes(source.toPath());

        ReceivedStore store = new ReceivedStore(context);
        assertTrue("писать в «Загрузки» можно", store.canWrite());
        String saved = store.copy(source, name, "text/plain");

        assertEquals("имя свободно, значит, остаётся своим", name, saved);
        assertTrue("исходный файл на месте", source.isFile());
        Found found = find(saved);
        assertNotNull("файл есть в «Загрузки/The Mesh»", found);
        assertFalse("запись завершена, а не «ожидает»", found.pending);
        assertTrue("содержимое то же, байт в байт", Arrays.equals(original, found.content));
        if (Build.VERSION.SDK_INT >= 29) {
            assertEquals("text/plain", found.mime);
        }
    }

    @Test
    public void theSameNameTwiceGivesTwoFilesAndTheFirstIsNotTouched() throws Exception {
        String name = uniqueName(".txt");
        File first = source(name);
        File second = source(name);
        // второй файл — с другим содержимым, чтобы увидеть, что первый не затёрт
        Files.write(second.toPath(), "другое содержимое\n".getBytes(StandardCharsets.UTF_8));
        byte[] firstBytes = Files.readAllBytes(first.toPath());

        ReceivedStore store = new ReceivedStore(context);
        String one = store.copy(first, name, "text/plain");
        String two = store.copy(second, name, "text/plain");

        assertEquals(name, one);
        assertNotEquals("занятое имя не используется повторно", one, two);
        assertTrue("второе имя похоже на первое: " + two, two.startsWith(name.substring(0, name.length() - 4)) && two.endsWith(".txt"));
        assertTrue(Arrays.equals(firstBytes, find(one).content));
        assertEquals("другое содержимое\n", new String(find(two).content, StandardCharsets.UTF_8));
    }

    @Test
    public void aTypeThatIsMissingIsGuessedFromTheName() throws Exception {
        String name = uniqueName(".jpg");
        File source = source(name);
        String saved = new ReceivedStore(context).copy(source, name, ""); // узел не прислал тип
        Found found = find(saved);
        assertNotNull(found);
        if (Build.VERSION.SDK_INT >= 29) {
            assertEquals("image/jpeg", found.mime);
        }
    }

    @Test
    public void russianNamesAndEmojiAreKept() throws Exception {
        String name = uniqueName(" отчёт 📱.txt");
        File source = source("plain.txt");
        String saved = new ReceivedStore(context).copy(source, name, "text/plain");
        assertEquals(name, saved);
        assertNotNull(find(saved));
    }
}
