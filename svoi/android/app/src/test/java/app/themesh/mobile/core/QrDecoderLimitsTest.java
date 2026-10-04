package app.themesh.mobile.core;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;
import java.util.Random;

/**
 * До какого размера модуля (стороны чёрного или белого квадратика QR-кода в пикселях кадра) декодер ещё надёжен. Каждая клетка
 * таблицы — {@value #FRAMES} разных кадров (разные приглашения длиной от 250 до 461 знака, разный сдвиг кода на доли
 * пикселя, поворот, шум): доля прочитанных обычным проходом и углублённым, в процентах. Таблица печатается в журнал теста
 * (build/test-results), а утверждения ниже закрепляют только то, что надёжно прочиталось, — с запасом.
 *
 * <p>Это измерение на искусственных кадрах: код нарисован, а не снят. Настоящий экран ноутбука, настоящая камера, муар
 * между сеткой пикселей экрана и датчика, блики и автофокус здесь не участвуют.
 */
public class QrDecoderLimitsTest {
    static final int FRAMES = 24;
    /** Размеры модуля, пикселей. 1,5–2,5 — на пределе, 3 и больше — рабочие. */
    private static final double[] SIZES = {1.5, 2, 2.5, 3, 3.5, 4, 5, 6, 8};

    /** Условия съёмки. */
    private enum Shot {
        IDEAL("чёткий, чёрное на белом, ровно"),
        TYPICAL("шум 6, размытие, 30/220, поворот до 10°"),
        HARD("шум 10, размытие, 40/200, поворот до 20°, наклон");

        final String what;

        Shot(String what) {
            this.what = what;
        }

        QrImages.Scene apply(QrImages.Scene scene, Random random) {
            switch (this) {
                case TYPICAL:
                    return scene.levels(30, 220).noise(6).blur(1).angle(random.nextDouble() * 20 - 10);
                case HARD:
                    return scene.levels(40, 200).noise(10).blur(1).angle(random.nextDouble() * 40 - 20).keystone(0.08);
                default:
                    return scene;
            }
        }
    }

    /** Сколько кадров из {@link #FRAMES} прочитал обычный проход ([0]) и углублённый ([1]). */
    static int[] read(double moduleSize, Shot shot, long seed) {
        Random random = new Random(seed);
        int quick = 0;
        int deep = 0;
        for (int i = 0; i < FRAMES; i++) {
            String text = QrImages.invite(250 + random.nextInt(212), random.nextLong());
            QrImages.Scene scene = QrImages.scene(QrImages.modules(text)).moduleSize(moduleSize)
                    .shift(random.nextDouble(), random.nextDouble()).pad(random.nextInt(70)).seed(random.nextLong());
            QrImages.Frame frame = shot.apply(scene, random).render();
            quick += text.equals(frame.decode(false)) ? 1 : 0;
            deep += text.equals(frame.decode(true)) ? 1 : 0;
        }
        return new int[] {quick, deep};
    }

    @Test
    public void theTableOfWhatIsStillRead() {
        StringBuilder table = new StringBuilder("\nКакая доля кадров прочитана (%), обычный проход / углублённый, " + FRAMES + " кадров в клетке\n");
        table.append(String.format(Locale.ROOT, "%-52s", "пикселей на модуль →"));
        for (double size : SIZES) {
            table.append(String.format(Locale.ROOT, "%10.1f", size));
        }
        table.append('\n');
        int[][] deepByShot = new int[Shot.values().length][SIZES.length];
        for (Shot shot : Shot.values()) {
            table.append(String.format(Locale.ROOT, "%-52s", shot.what));
            for (int i = 0; i < SIZES.length; i++) {
                int[] n = read(SIZES[i], shot, 77 + i);
                deepByShot[shot.ordinal()][i] = n[1];
                table.append(String.format(Locale.ROOT, "%6d/%-3d", n[0] * 100 / FRAMES, n[1] * 100 / FRAMES));
            }
            table.append('\n');
        }
        System.out.println(table);

        // Закрепляем только устойчивое, с запасом (в клетках ниже порога результат зависит от удачи и от версии ZXing).
        for (int i = 0; i < SIZES.length; i++) {
            double size = SIZES[i];
            if (size >= 3) {
                assertTrue("чёткие кадры, " + size + " px: " + deepByShot[Shot.IDEAL.ordinal()][i] + " из " + FRAMES,
                        deepByShot[Shot.IDEAL.ordinal()][i] >= FRAMES * 85 / 100);
            }
            if (size >= 5) {
                assertTrue("обычные кадры, " + size + " px: " + deepByShot[Shot.TYPICAL.ordinal()][i] + " из " + FRAMES,
                        deepByShot[Shot.TYPICAL.ordinal()][i] >= FRAMES * 85 / 100);
            }
            if (size >= 6) {
                assertTrue("тяжёлые кадры, " + size + " px: " + deepByShot[Shot.HARD.ordinal()][i] + " из " + FRAMES,
                        deepByShot[Shot.HARD.ordinal()][i] >= FRAMES * 60 / 100);
            }
        }
    }

    @Test
    public void belowTheLimitNothingIsInventedEither() {
        // модуль в полтора пикселя и мельче читается плохо; главное — что не прочитано неверно: либо верный текст, либо ничего
        Random random = new Random(5);
        for (int i = 0; i < 12; i++) {
            String text = QrImages.invite(320, random.nextLong());
            QrImages.Frame frame = QrImages.scene(QrImages.modules(text)).moduleSize(1.0 + random.nextDouble() * 0.9)
                    .shift(random.nextDouble(), random.nextDouble()).levels(30, 220).noise(8).blur(1).seed(i).render();
            for (boolean deep : new boolean[] {false, true}) {
                String got = frame.decode(deep);
                assertTrue("либо верно, либо никак: " + got, got == null || got.equals(text));
            }
        }
    }
}
