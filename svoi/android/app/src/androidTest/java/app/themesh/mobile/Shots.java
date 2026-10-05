package app.themesh.mobile;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Снимки экрана для людей: пишутся в /data/local/tmp/themesh-shots от имени оболочки (эту папку не удаляет удаление приложения
 * после прогона, и из неё CI забирает снимки командой {@code adb pull}, см. tools/emulator-run.sh).
 *
 * <p>Команды UiAutomation делят строку по пробелам и не понимают ни кавычек, ни «&amp;&amp;», ни «&gt;»: папку делает одна команда, а файл
 * пишет {@code dd}, которой на стандартный ввод отдают снимок.
 */
final class Shots {
    static final String DIR = "/data/local/tmp/themesh-shots";
    private static final String TAG = "Shots";

    private Shots() {
    }

    /** Снимает экран через {@code settleMs} (анимации страницы на медленном эмуляторе заканчиваются не сразу); {@code true}, если файл записан. */
    static boolean take(Instrumentation instrumentation, String name, long settleMs) {
        try {
            Thread.sleep(settleMs);
            UiAutomation ua = instrumentation.getUiAutomation();
            // окно «Pixel Launcher isn't responding» поверх приложения (медленный эмулятор) закрыло бы снимок: нажать в нём «Подождать»
            for (int i = 0; i < 3 && PermissionDialog.waitOut(ua); i++) {
                Log.i(TAG, name + ": нажато «Подождать» в окне «не отвечает»");
                Thread.sleep(900);
            }
            Bitmap bmp = ua.takeScreenshot();
            // (на перегруженном эмуляторе система иногда отвечает «нет снимка»: два раза пробуем ещё)
            for (int i = 0; i < 2 && bmp == null; i++) {
                Log.w(TAG, name + ": система не дала снимок, пробуем ещё");
                Thread.sleep(1500);
                bmp = ua.takeScreenshot();
            }
            if (bmp == null) {
                Log.w(TAG, name + ": система не дала снимок");
                return false;
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, png);
            drain(ua.executeShellCommand("mkdir -p " + DIR));
            ParcelFileDescriptor[] fds = ua.executeShellCommandRw("dd bs=65536 of=" + DIR + "/" + name + ".png");
            try (OutputStream stdin = new ParcelFileDescriptor.AutoCloseOutputStream(fds[1])) {
                stdin.write(png.toByteArray());
            }
            drain(fds[0]);
            Log.i(TAG, name + ": " + bmp.getWidth() + "x" + bmp.getHeight() + ", " + png.size() + " байт");
            return true;
        } catch (Exception e) {
            Log.w(TAG, name + ": не получилось", e);
            return false;
        }
    }

    /** Читает вывод команды до конца: когда она закончила, чтение возвращает -1. */
    static void drain(ParcelFileDescriptor out) throws Exception {
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(out)) {
            while (in.read() >= 0) {
                // вывод не нужен
            }
        }
    }
}
