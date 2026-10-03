package app.themesh.mobile;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import app.themesh.mobile.core.ReceivedFiles;

/**
 * «Загрузки/The Mesh» на телефоне — то место, куда {@link ReceivedFiles} кладёт копии полученных файлов: через
 * MediaStore (Android 10+) или напрямую (Android 8–9, и только если разрешение на запись уже выдано: просить его из
 * службы в фоне нельзя). Имя берётся то же, что у файла в папке приложения, а если в «Загрузках» оно занято — следующее
 * свободное («a (2).txt»), так что чужие и прежние файлы не затираются.
 */
final class ReceivedStore implements ReceivedFiles.Store {
    private final Context app;

    ReceivedStore(Context context) {
        this.app = context.getApplicationContext();
    }

    @Override
    public boolean canWrite() {
        return !Downloads.needsLegacyPermission()
                || app.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public String copy(File source, String name, String mime) throws IOException {
        String type = Downloads.mimeOf(mime, "", name);
        try (InputStream in = new FileInputStream(source)) {
            return Downloads.saveInto(app, in, name, type, ReceivedFiles.FOLDER, null);
        }
    }
}
