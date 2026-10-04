package app.themesh.mobile.core;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Распознаёт QR-код на кадре камеры. Чистая Java (ZXing core, без Android и без сети): кадр — это яркость (плоскость Y
 * формата YUV_420_888, байт на пиксель), поэтому всё проверяется на компьютере без телефона.
 *
 * <p>Порядок попыток: {@link HybridBinarizer} (локальный порог — хорош при неровном освещении и бликах на экране); при
 * {@code deep} ещё {@link GlobalHistogramBinarizer} (общий порог — иногда читает то, что локальный «съел»), кадр,
 * уменьшенный в 2/3 раза усреднением (шум и размытие усредняются, а сетка пикселей ложится на модули иначе: на кадрах с
 * шумом и небольшим размытием это заметно прибавляет прочитанных), и инвертированный кадр (белые модули на тёмном —
 * тёмная тема). Анализатор просит {@code deep} на каждом третьем кадре, чтобы нагрузка на кадр оставалась небольшой, а
 * редкие случаи всё же находились.
 */
public final class QrDecoder {
    /** Кадр меньше этого по короткой стороне уменьшать незачем: модули и так мелкие. */
    private static final int MIN_SHRINK_SIDE = 150;

    private static final Map<DecodeHintType, Object> HINTS;

    static {
        EnumMap<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        HINTS = Collections.unmodifiableMap(hints);
    }

    private QrDecoder() {
    }

    /**
     * Текст QR-кода на кадре или {@code null}, если кода нет (или он не читается).
     *
     * @param luma      яркость построчно; в строке {@code rowStride} байт, из них первые {@code width} — пиксели, остальное
     *                  (если {@code rowStride > width}) — выравнивание, там может быть что угодно
     * @param rowStride длина строки в байтах (у камеры она бывает больше ширины кадра: выравнивание буфера)
     * @param deep      пробовать ещё и общий порог, и инвертированный кадр (дороже: до трёх попыток вместо одной)
     */
    public static String decode(byte[] luma, int rowStride, int width, int height, boolean deep) {
        if (luma == null || width <= 0 || height <= 0 || rowStride < width
                || (long) (height - 1) * rowStride + width > luma.length) {
            return null; // кадр не бывает таким: не падаем, просто ничего не находим
        }
        try {
            LuminanceSource source = new PlanarYUVLuminanceSource(luma, rowStride, height, 0, 0, width, height, false);
            String text = read(new HybridBinarizer(source));
            if (text == null && deep) {
                text = read(new GlobalHistogramBinarizer(source));
                if (text == null) {
                    text = readShrunk(luma, rowStride, width, height);
                }
                if (text == null) {
                    text = read(new HybridBinarizer(source.invert()));
                }
            }
            return text;
        } catch (RuntimeException e) {
            return null; // ZXing на странных кадрах может споткнуться о границы массива: это «не нашли», а не авария
        }
    }

    /** Кадр, уменьшенный в 2/3 раза: каждые три пикселя по каждой оси — два, яркость усредняется по площади. */
    private static String readShrunk(byte[] luma, int rowStride, int width, int height) {
        if (Math.min(width, height) < MIN_SHRINK_SIDE) {
            return null;
        }
        int w = width * 2 / 3;
        int h = height * 2 / 3;
        byte[] small = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int y0 = y * 3 / 2; // у чётных строк веса 2 и 1, у нечётных 1 и 2 (в третях площади)
            int wy0 = (y & 1) == 0 ? 2 : 1;
            int wy1 = 3 - wy0;
            int row0 = y0 * rowStride;
            int row1 = row0 + rowStride;
            for (int x = 0; x < w; x++) {
                int x0 = x * 3 / 2;
                int wx0 = (x & 1) == 0 ? 2 : 1;
                int wx1 = 3 - wx0;
                int sum = wy0 * (wx0 * (luma[row0 + x0] & 0xFF) + wx1 * (luma[row0 + x0 + 1] & 0xFF))
                        + wy1 * (wx0 * (luma[row1 + x0] & 0xFF) + wx1 * (luma[row1 + x0 + 1] & 0xFF));
                small[y * w + x] = (byte) ((sum + 4) / 9);
            }
        }
        return read(new HybridBinarizer(new PlanarYUVLuminanceSource(small, w, h, 0, 0, w, h, false)));
    }

    private static String read(Binarizer binarizer) {
        try {
            return new MultiFormatReader().decode(new BinaryBitmap(binarizer), HINTS).getText();
        } catch (ReaderException e) {
            return null; // на кадре нет QR-кода, или он не прошёл исправление ошибок
        }
    }
}
