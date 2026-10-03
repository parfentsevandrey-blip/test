package app.themesh.mobile;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.themesh.mobile.core.FileNames;

/**
 * «Скачать» в интерфейсе: файл забирается тем же HTTP-запросом с cookie окна (сессия в WebView) и
 * сохраняется в общую папку «Загрузки» — через MediaStore (Android 10+) или напрямую (Android 8–9),
 * под именем, которого там ещё нет. В конце — уведомление «Файл сохранён», открывающее файл. Тот же сохранятель
 * ({@link #saveInto}) использует {@link ReceivedStore} для копий полученных от других устройств файлов (в подпапку
 * «The Mesh»).
 */
final class Downloads {
    private static final String TAG = "themesh";
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> new Thread(r, "themesh-download"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Downloads() {
    }

    /** Нужно ли разрешение на запись во внешнее хранилище (только Android 8–9). */
    static boolean needsLegacyPermission() {
        return Build.VERSION.SDK_INT < 29;
    }

    /**
     * Скачивает в фоне. {@code url} уже проверен: это адрес интерфейса узла (cookie туда и пойдут, больше никуда).
     */
    static void start(Context context, String url, String contentDisposition, String mimeType) {
        Context app = context.getApplicationContext();
        POOL.execute(() -> {
            try {
                save(app, url, contentDisposition, mimeType);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "download failed: " + e);
                toast(app, app.getString(R.string.toast_download_failed, String.valueOf(e.getMessage())));
            }
        });
    }

    private static void save(Context app, String url, String listenerDisposition, String listenerMime) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
        try {
            c.setInstanceFollowRedirects(false); // cookie уходят только по этому адресу
            c.setConnectTimeout(5_000);
            c.setReadTimeout(30_000);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) {
                c.setRequestProperty("Cookie", cookie);
            }
            int code = c.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            String disposition = c.getHeaderField("Content-Disposition");
            String name = FileNames.suggest(url, disposition != null ? disposition : listenerDisposition);
            String type = mimeOf(c.getContentType(), listenerMime, name);
            toast(app, app.getString(R.string.toast_download_started, name));
            try (InputStream in = c.getInputStream()) {
                saveInto(app, in, name, type, "", (uri, shown) -> Notifier.downloadSaved(app, uri, type, shown));
            }
        } finally {
            c.disconnect();
        }
    }

    /** Вызывается, когда файл лёг и известен его адрес (на Android 8–9 — после того, как его просканирует система). */
    interface Saved {
        void onSaved(Uri uri, String name);
    }

    /**
     * Кладёт поток в «Загрузки» (или в подпапку «Загрузок», если {@code subfolder} не пуст) под именем, которого
     * там ещё нет: через MediaStore (Android 10+) или напрямую (Android 8–9, нужно разрешение на запись).
     *
     * @param saved может быть {@code null}
     * @return имя, под которым файл лёг
     */
    static String saveInto(Context app, InputStream in, String name, String type, String subfolder, Saved saved) throws IOException {
        return Build.VERSION.SDK_INT >= 29
                ? saveWithMediaStore(app, in, name, type, subfolder, saved)
                : saveLegacy(app, in, name, type, subfolder, saved);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static String saveWithMediaStore(Context app, InputStream in, String name, String type, String subfolder, Saved saved)
            throws IOException {
        ContentResolver cr = app.getContentResolver();
        String relative = subfolder.isEmpty() ? Environment.DIRECTORY_DOWNLOADS : Environment.DIRECTORY_DOWNLOADS + "/" + subfolder;
        String unique = FileNames.uniqueName(name, candidate -> existsIn(cr, relative, candidate));
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, unique);
        v.put(MediaStore.Downloads.MIME_TYPE, type);
        v.put(MediaStore.Downloads.RELATIVE_PATH, relative);
        v.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) {
            throw new IOException("MediaStore refused the file");
        }
        try (OutputStream out = cr.openOutputStream(uri)) {
            if (out == null) {
                throw new IOException("cannot open the file for writing");
            }
            copy(in, out);
        } catch (IOException | RuntimeException e) {
            cr.delete(uri, null, null); // не оставляем половину файла
            throw e;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING, 0);
        cr.update(uri, done, null, null);
        String shown = displayName(cr, uri, unique);
        if (saved != null) {
            saved.onSaved(uri, shown);
        }
        return shown;
    }

    /** Есть ли в этой папке «Загрузок» (MediaStore, только файлы приложения) файл с таким именем. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private static boolean existsIn(ContentResolver cr, String relativePath, String name) {
        try (Cursor cur = cr.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Downloads._ID},
                MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?",
                new String[] {name, relativePath + "/"}, null)) {
            return cur != null && cur.moveToFirst();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static String displayName(ContentResolver cr, Uri uri, String fallback) {
        try (Cursor cur = cr.query(uri, new String[] {MediaStore.Downloads.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst() && cur.getString(0) != null) {
                return cur.getString(0);
            }
        } catch (RuntimeException ignored) {
            // имя — для уведомления
        }
        return fallback;
    }

    @SuppressWarnings("deprecation")
    private static String saveLegacy(Context app, InputStream in, String name, String type, String subfolder, Saved saved)
            throws IOException {
        File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = subfolder.isEmpty() ? root : new File(root, subfolder);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("no Downloads folder");
        }
        String unique = FileNames.uniqueName(name, candidate -> new File(dir, candidate).exists());
        File file = new File(dir, unique);
        try (OutputStream out = new FileOutputStream(file)) {
            copy(in, out);
        } catch (IOException | RuntimeException e) {
            file.delete();
            throw e;
        }
        MediaScannerConnection.scanFile(app, new String[] {file.getPath()}, new String[] {type},
                (path, uri) -> {
                    if (saved != null) {
                        saved.onSaved(uri, unique);
                    }
                });
        return unique;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
    }

    /** Тип файла: из ответа узла или подсказки WebView, а если там «что угодно» — по расширению имени. */
    static String mimeOf(String contentType, String listenerMime, String name) {
        String type = clean(contentType);
        if (type.isEmpty()) {
            type = clean(listenerMime);
        }
        if (type.isEmpty() || type.equals("application/octet-stream")) {
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot < name.length() - 1) {
                String guess = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
                if (guess != null) {
                    return guess;
                }
            }
            return type.isEmpty() ? "application/octet-stream" : type;
        }
        return type;
    }

    private static String clean(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semi = contentType.indexOf(';');
        return (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase(Locale.ROOT);
    }

    private static void toast(Context app, String text) {
        MAIN.post(() -> Toast.makeText(app, text, Toast.LENGTH_SHORT).show());
    }
}
