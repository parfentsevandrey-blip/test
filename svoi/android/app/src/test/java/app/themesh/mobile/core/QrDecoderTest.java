package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

/**
 * Распознавание QR-кода на кадре: настоящие QR-коды (ZXing рисует их так же, как ядро — go-qrcode, уровень исправления M, поля
 * 4 модуля) в яркостных буферах «как с камеры» ({@link QrImages}). Длины приглашений — как у настоящих: 250, 320 и 450 знаков,
 * 461 — самое длинное из возможных (8 адресов IPv6 и название в 40 байт). Границы того, что ещё читается, — в
 * {@link QrDecoderLimitsTest}.
 */
public class QrDecoderTest {
    private static final int[] LENGTHS = {250, 320, 450, 461};

    /** Приглашение нужной длины, всегда одно и то же (длина и зерно определяют текст, а от текста зависит и картинка). */
    private static String invite(int length) {
        return QrImages.invite(length, 1000 + length);
    }

    private static void assertRead(String message, String expected, QrImages.Frame frame, boolean deep) {
        assertEquals(message, expected, frame.decode(deep));
    }

    @Test
    public void cleanCodesAreReadAtEveryModuleSize() {
        for (int length : LENGTHS) {
            for (String text : new String[] {invite(length), QrImages.withDashes(invite(length))}) {
                boolean[][] modules = QrImages.modules(text);
                for (int size : new int[] {2, 3, 4, 6}) {
                    QrImages.Frame frame = QrImages.scene(modules).moduleSize(size).pad(17).render();
                    String what = text.length() + " знаков, " + size + " пикселей на модуль";
                    assertRead(what, text, frame, false);
                    assertRead(what + ", углублённо", text, frame, true);
                }
            }
        }
    }

    @Test
    public void garbageInTheTailOfEveryRowIsIgnored() {
        String text = invite(320);
        boolean[][] modules = QrImages.modules(text);
        for (int pad : new int[] {0, 1, 7, 64, 513}) {
            QrImages.Frame frame = QrImages.scene(modules).moduleSize(4).pad(pad).seed(pad + 1).render();
            assertEquals(frame.width + pad, frame.stride);
            assertRead("в хвосте строки " + pad + " байт мусора", text, frame, false);
        }
        // мусор не должен быть пустым: проверяем, что он действительно разный
        QrImages.Frame frame = QrImages.scene(modules).moduleSize(4).pad(64).render();
        assertTrue(frame.luma[frame.width] != frame.luma[frame.width + 1] || frame.luma[frame.width + 2] != frame.luma[frame.width + 3]);
    }

    @Test
    public void aFrameCutShortAfterTheLastPixelIsStillRead() {
        // у части камер буфер плоскости кончается на последнем пикселе последней строки, без выравнивания
        String text = invite(450);
        QrImages.Frame frame = QrImages.scene(QrImages.modules(text)).moduleSize(4).pad(40).render();
        int needed = (frame.height - 1) * frame.stride + frame.width;
        byte[] cut = Arrays.copyOf(frame.luma, needed);
        assertEquals(text, QrDecoder.decode(cut, frame.stride, frame.width, frame.height, false));
        assertNull("а на байт короче уже нельзя", QrDecoder.decode(Arrays.copyOf(frame.luma, needed - 1), frame.stride, frame.width, frame.height, true));
    }

    @Test
    public void turnedCodesAreRead() {
        for (int length : new int[] {250, 450}) {
            String text = invite(length);
            boolean[][] modules = QrImages.modules(text);
            for (int angle : new int[] {90, 180, 270}) {
                for (int size : new int[] {3, 4}) {
                    QrImages.Frame frame = QrImages.scene(modules).moduleSize(size).angle(angle).pad(5).render();
                    assertRead(length + " знаков, поворот " + angle + "°, " + size + " пикселей", text, frame, false);
                }
            }
        }
    }

    @Test
    public void codesAtAnAngleAndTiltedFromTheScreenAreRead() {
        String text = invite(450);
        boolean[][] modules = QrImages.modules(text);
        for (double angle : new double[] {-30, -12, 7, 15, 25, 45}) {
            QrImages.Frame frame = QrImages.scene(modules).moduleSize(5).angle(angle).levels(30, 220).noise(4).pad(9).seed(3).render();
            assertRead("поворот " + angle + "°", text, frame, true);
        }
        for (double keystone : new double[] {0.05, 0.1}) {
            QrImages.Frame frame = QrImages.scene(modules).moduleSize(6).keystone(keystone).angle(4).levels(30, 220).noise(4).pad(9).seed(4).render();
            assertRead("наклон " + keystone, text, frame, true);
        }
    }

    @Test
    public void whiteOnDarkCodesNeedTheDeepPass() {
        for (int length : new int[] {250, 450}) {
            String text = invite(length);
            QrImages.Frame frame = QrImages.scene(QrImages.modules(text)).moduleSize(4).inverted().pad(3).render();
            assertRead(length + " знаков", text, frame, true);
            // ZXing сам инвертированный код не читает (если когда-нибудь начнёт, это условие можно убрать): поэтому и нужен deep
            assertNull(length + " знаков, обычный проход", frame.decode(false));
        }
    }

    @Test
    public void noiseAndBlurOfARealCameraAreTolerated() {
        // не чёрное на белом, а 30 и 220 с шумом и лёгким размытием границ: чем дальше код, тем мельче модуль
        for (int length : new int[] {250, 320, 450}) {
            String text = invite(length);
            boolean[][] modules = QrImages.modules(text);
            for (int size : new int[] {5, 6}) {
                for (int blur = 0; blur <= 1; blur++) {
                    QrImages.Frame frame = QrImages.scene(modules).moduleSize(size).levels(30, 220).noise(6).blur(blur).pad(11).seed(size * 10 + blur).render();
                    assertRead(length + " знаков, " + size + " px, размытие " + blur, text, frame, true);
                }
            }
        }
    }

    @Test
    public void theDeepPassReadsWhatTheQuickOneMisses() {
        // шум и размытие на модулях в 4 пикселя: обычный проход читает не все кадры, углублённый (общий порог, кадр в 2/3 раза
        // меньше, негатив) — заметно больше
        int quick = 0;
        int deep = 0;
        int frames = 24;
        for (int i = 0; i < frames; i++) {
            String text = QrImages.invite(461, 500 + i);
            QrImages.Frame frame = QrImages.scene(QrImages.modules(text)).moduleSize(4).levels(30, 220).noise(7).blur(1).pad(i).seed(i).shift(i / 24.0, (i * 7 % 24) / 24.0).render();
            quick += text.equals(frame.decode(false)) ? 1 : 0;
            deep += text.equals(frame.decode(true)) ? 1 : 0;
        }
        assertTrue("глубокий проход не хуже быстрого: " + deep + " и " + quick + " из " + frames, deep >= quick);
        assertTrue("глубокий проход читает заметно больше: " + deep + " против " + quick + " из " + frames, deep >= quick + 4);
        assertTrue("и читает почти всё: " + deep + " из " + frames, deep >= frames * 3 / 4);
    }

    @Test
    public void aCodeSomewhereInABigFrameIsFound() {
        // кадр камеры 1920×1080 (в строке 1984 байта), код где-то в нём, вокруг неровный тёмный фон
        String text = invite(450);
        boolean[][] modules = QrImages.modules(text);
        double[][] centers = {{0.5, 0.5}, {0.25, 0.35}, {0.78, 0.7}, {0.4, 0.82}};
        for (double[] c : centers) {
            QrImages.Frame frame = QrImages.scene(modules).moduleSize(5).canvas(1920, 1080).center(c[0], c[1]).background()
                    .levels(30, 220).noise(5).pad(64).seed(8).render();
            assertEquals(1984, frame.stride);
            assertRead("центр кода в " + Arrays.toString(c), text, frame, false);
        }
    }

    @Test
    public void framesWithoutACodeGiveNothing() {
        int w = 640;
        int h = 480;
        Random random = new Random(42);
        byte[] black = new byte[w * h];
        byte[] white = new byte[w * h];
        byte[] gray = new byte[w * h];
        byte[] noise = new byte[w * h];
        byte[] gradient = new byte[w * h];
        byte[] checker = new byte[w * h];
        byte[] stripes = new byte[w * h];
        Arrays.fill(white, (byte) 255);
        Arrays.fill(gray, (byte) 128);
        random.nextBytes(noise);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                gradient[y * w + x] = (byte) (x * 255 / (w - 1));
                checker[y * w + x] = (byte) (((x / 8 + y / 8) & 1) == 0 ? 0 : 255);
                stripes[y * w + x] = (byte) ((x / 5) % 2 == 0 ? 0 : 255);
            }
        }
        byte[][] frames = {black, white, gray, noise, gradient, checker, stripes};
        String[] names = {"чёрный", "белый", "серый", "шум", "градиент", "шахматы", "полосы"};
        for (int i = 0; i < frames.length; i++) {
            assertNull(names[i], QrDecoder.decode(frames[i], w, w, h, false));
            assertNull(names[i] + ", углублённо", QrDecoder.decode(frames[i], w, w, h, true));
        }
        for (int seed = 0; seed < 5; seed++) { // шум разных зёрен и с выровненными строками
            byte[] padded = new byte[(w + 32) * h];
            new Random(seed).nextBytes(padded);
            assertNull("шум " + seed, QrDecoder.decode(padded, w + 32, w, h, true));
        }
    }

    @Test
    public void aCodeThatIsNotAnInviteIsReadAsTextAndTheFilterRejectsIt() {
        for (String other : new String[] {"https://example.com/path?x=1&y=2", "WIFI:T:WPA;S:home;P:secret;;", "hello, world", "Привет, мир"}) {
            QrImages.Frame frame = QrImages.scene(QrImages.modules(other)).moduleSize(5).pad(4).render();
            assertEquals(other, frame.decode(false));
            assertNull("не приглашение: " + other, InviteCode.extract(frame.decode(false)));
        }
    }

    @Test
    public void whatTheCameraReadsIsTheInviteTheInterfaceShows() {
        // QR, который рисует интерфейс, — приглашение без дефисов; с дефисами (прежний вид) разбор приводит к тому же
        String compact = invite(450);
        for (String shown : new String[] {compact, QrImages.withDashes(compact)}) {
            QrImages.Frame frame = QrImages.scene(QrImages.modules(shown)).moduleSize(5).pad(10).render();
            assertEquals(compact, InviteCode.extract(frame.decode(false)));
        }
    }

    @Test
    public void impossibleFramesGiveNothingInsteadOfFailing() {
        byte[] luma = new byte[100 * 100];
        assertNull(QrDecoder.decode(null, 100, 100, 100, true));
        assertNull(QrDecoder.decode(new byte[0], 0, 0, 0, true));
        assertNull("ширина 0", QrDecoder.decode(luma, 100, 0, 100, false));
        assertNull("высота 0", QrDecoder.decode(luma, 100, 100, 0, false));
        assertNull("отрицательная высота", QrDecoder.decode(luma, 100, 100, -5, false));
        assertNull("строка короче ширины", QrDecoder.decode(luma, 99, 100, 100, false));
        assertNull("буфер короче кадра", QrDecoder.decode(luma, 100, 100, 101, false));
        assertNull("буфер короче кадра (строка длиннее ширины)", QrDecoder.decode(luma, 120, 100, 100, true));
        assertNull("огромный кадр на малом буфере", QrDecoder.decode(luma, Integer.MAX_VALUE, 100, Integer.MAX_VALUE, true));
        assertNull("крошечный кадр", QrDecoder.decode(new byte[4], 2, 2, 2, true));
        assertNull("кадр в один пиксель", QrDecoder.decode(new byte[1], 1, 1, 1, true));
    }

    @Test
    public void severalCodesAreNotAProblemAndTheDecoderIsThreadSafe() throws Exception {
        // анализатор работает в одном потоке, но класс без состояния: проверяем, что параллельные вызовы не мешают друг другу
        String a = invite(320);
        String b = invite(450);
        QrImages.Frame fa = QrImages.scene(QrImages.modules(a)).moduleSize(4).pad(3).render();
        QrImages.Frame fb = QrImages.scene(QrImages.modules(b)).moduleSize(4).pad(5).render();
        Thread[] threads = new Thread[6];
        String[] results = new String[threads.length];
        for (int i = 0; i < threads.length; i++) {
            int k = i;
            threads[i] = new Thread(() -> {
                String last = null;
                for (int n = 0; n < 5; n++) {
                    last = (k % 2 == 0 ? fa : fb).decode(n % 2 == 0);
                    if (last == null) {
                        break;
                    }
                }
                results[k] = last;
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        for (int i = 0; i < threads.length; i++) {
            assertNotNull("поток " + i, results[i]);
            assertEquals(i % 2 == 0 ? a : b, results[i]);
        }
    }
}
