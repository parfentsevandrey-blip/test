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
            bmp = steady(ua, bmp, name);
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

    /**
     * Снимок, который больше не меняется. Программная графика эмулятора рисует кадр страницы до семи секунд и показывает кадр, в
     * котором часть плиток ещё не нарисована (так на одном снимке целой карточки не было, а на её месте было небо): снимаем ещё раз,
     * пока два снимка подряд не совпадут почти во всём, но не больше пяти раз. «Почти» — потому что часы в строке состояния и
     * обратный отсчёт приглашения меняют сотни точек, а недорисованная карточка — сотни тысяч.
     */
    private static Bitmap steady(UiAutomation ua, Bitmap first, String name) throws InterruptedException {
        Bitmap last = first;
        for (int i = 0; i < 5; i++) {
            Thread.sleep(800);
            Bitmap next = ua.takeScreenshot();
            if (next == null) {
                return last;
            }
            if (alike(last, next)) {
                return next;
            }
            Log.i(TAG, name + ": страница ещё дорисовывается, снимаем снова");
            last = next;
        }
        Log.w(TAG, name + ": снимки так и не перестали меняться, берём последний");
        return last;
    }

    /** Совпадают ли два снимка, кроме мелочей: сравнивается каждая четвёртая точка каждой четвёртой строки, различий не больше 0,5 %. */
    static boolean alike(Bitmap a, Bitmap b) {
        int w = a.getWidth(), h = a.getHeight();
        if (w != b.getWidth() || h != b.getHeight()) {
            return false;
        }
        // (снимок UiAutomation — «аппаратный» Bitmap, из которого точки не читаются: читаем из копий)
        Bitmap ca = a.copy(Bitmap.Config.ARGB_8888, false), cb = b.copy(Bitmap.Config.ARGB_8888, false);
        int[] ra = new int[w], rb = new int[w];
        long all = 0, differ = 0;
        for (int y = 0; y < h; y += 4) {
            ca.getPixels(ra, 0, w, 0, y, w, 1);
            cb.getPixels(rb, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x += 4) {
                all++;
                if (ra[x] != rb[x]) {
                    differ++;
                }
            }
        }
        ca.recycle();
        cb.recycle();
        return differ * 200 <= all;
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
