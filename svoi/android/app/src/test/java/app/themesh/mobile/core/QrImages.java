package app.themesh.mobile.core;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Кадры «как с камеры» для проверки {@link QrDecoder}: настоящий QR-код (его рисует ZXing, как ядро — go-qrcode, с исправлением
 * ошибок уровня M и полями в 4 модуля) в яркостном буфере, где размер модуля, поворот, наклон, шум, размытие и «мусор» в хвосте
 * строк задаются как нужно. Модуль может занимать и дробное число пикселей: камера всегда усредняет свет по площади пикселя,
 * поэтому границы модулей на таких кадрах размыты так, как у настоящих.
 */
public final class QrImages {
    private QrImages() {
    }

    /** Кадр: яркость построчно, в строке {@code stride} байт, из них пикселей {@code width}. */
    public static final class Frame {
        public final byte[] luma;
        public final int stride;
        public final int width;
        public final int height;

        Frame(byte[] luma, int stride, int width, int height) {
            this.luma = luma;
            this.stride = stride;
            this.width = width;
            this.height = height;
        }

        public String decode(boolean deep) {
            return QrDecoder.decode(luma, stride, width, height, deep);
        }
    }

    /** Модули QR-кода ({@code true} — тёмный), вместе с полями в 4 модуля: так же, как у картинки, которую рисует интерфейс. */
    public static boolean[][] modules(String text) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 4);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints); // 0×0: ровно один пиксель на модуль
        } catch (WriterException e) {
            throw new IllegalArgumentException(e);
        }
        boolean[][] out = new boolean[matrix.getHeight()][matrix.getWidth()];
        for (int y = 0; y < out.length; y++) {
            for (int x = 0; x < out[y].length; x++) {
                out[y][x] = matrix.get(x, y);
            }
        }
        return out;
    }

    /** Приглашение случайного вида: «MESH1-» и знаки base32 заглавными, всего {@code length} знаков. */
    public static String invite(int length, long seed) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder(InviteCode.PREFIX);
        while (sb.length() < length) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** То же приглашение в виде, который читают глазами: дефис после каждых 8 знаков. */
    public static String withDashes(String invite) {
        String body = invite.substring(InviteCode.PREFIX.length());
        StringBuilder sb = new StringBuilder(InviteCode.PREFIX);
        for (int i = 0; i < body.length(); i += 8) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(body, i, Math.min(body.length(), i + 8));
        }
        return sb.toString();
    }

    public static Scene scene(boolean[][] modules) {
        return new Scene(modules);
    }

    /** Что снимает «камера». Настройки меняются цепочкой вызовов; {@link #render()} рисует кадр. */
    public static final class Scene {
        private static final int SAMPLES = 3; // подвыборок на пиксель по каждой оси: границы модулей усредняются

        private final boolean[][] modules;
        private double moduleSize = 4;
        private double angle;
        private double keystone;
        private int canvasWidth;
        private int canvasHeight;
        private double centerX = 0.5;
        private double centerY = 0.5;
        private double shiftX;
        private double shiftY;
        private int dark = 0;
        private int light = 255;
        private boolean inverted;
        private double noise;
        private int blur;
        private int pad;
        private boolean background;
        private long seed = 1;

        Scene(boolean[][] modules) {
            this.modules = modules;
        }

        /** Сколько пикселей занимает сторона модуля (можно дробное). */
        public Scene moduleSize(double pixels) {
            this.moduleSize = pixels;
            return this;
        }

        /** Поворот кода вокруг его центра, в градусах. */
        public Scene angle(double degrees) {
            this.angle = degrees;
            return this;
        }

        /** Наклон телефона относительно экрана: чем больше, тем сильнее одна сторона кода меньше противоположной (0,15 — заметно). */
        public Scene keystone(double k) {
            this.keystone = k;
            return this;
        }

        /** Размер кадра; по умолчанию — по коду с полями (при повороте — по диагонали, чтобы не обрезать углы). */
        public Scene canvas(int width, int height) {
            this.canvasWidth = width;
            this.canvasHeight = height;
            return this;
        }

        /** Где центр кода в кадре, долями ширины и высоты (по умолчанию посередине). */
        public Scene center(double fx, double fy) {
            this.centerX = fx;
            this.centerY = fy;
            return this;
        }

        /** Сдвиг кода на доли пикселя: от того, куда границы модулей попадают относительно пикселей, чтение зависит заметно. */
        public Scene shift(double dx, double dy) {
            this.shiftX = dx;
            this.shiftY = dy;
            return this;
        }

        /** Яркость тёмных и светлых модулей: с экрана камера видит не чёрное и белое, а, например, 30 и 220. */
        public Scene levels(int dark, int light) {
            this.dark = dark;
            this.light = light;
            return this;
        }

        /** Белые модули на тёмном (тёмная тема, негатив). */
        public Scene inverted() {
            this.inverted = true;
            return this;
        }

        /** Шум: стандартное отклонение яркости пикселя (из 255). */
        public Scene noise(double sigma) {
            this.noise = sigma;
            return this;
        }

        /** Размытие: сколько раз кадр сглаживается ядром 1-2-1 по обеим осям (1 — слегка, 2 — заметно, 3 — сильно). */
        public Scene blur(int passes) {
            this.blur = passes;
            return this;
        }

        /** Лишние байты в хвосте каждой строки (у камеры строка выровнена и длиннее ширины); в них — мусор. */
        public Scene pad(int bytes) {
            this.pad = bytes;
            return this;
        }

        /** Вокруг кода не пустой лист, а неровный тёмно-серый фон; сами поля кода (4 модуля) остаются светлыми. */
        public Scene background() {
            this.background = true;
            return this;
        }

        public Scene seed(long seed) {
            this.seed = seed;
            return this;
        }

        public Frame render() {
            int n = modules.length;
            double side = n * moduleSize; // сторона кода с полями в пикселях
            boolean straight = angle % 90 == 0 && keystone == 0;
            int w = canvasWidth > 0 ? canvasWidth : (int) Math.ceil(straight ? side : side * 1.5);
            int h = canvasHeight > 0 ? canvasHeight : w;
            int stride = w + pad;
            double rad = Math.toRadians(angle);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double cx = w * centerX + shiftX;
            double cy = h * centerY + shiftY;
            double[][] plane = new double[h][w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double sum = 0;
                    for (int sy = 0; sy < SAMPLES; sy++) {
                        for (int sx = 0; sx < SAMPLES; sx++) {
                            double px = x + (sx + 0.5) / SAMPLES - cx;
                            double py = y + (sy + 0.5) / SAMPLES - cy;
                            double d = 1 + keystone * py / side; // наклон: чем ниже, тем крупнее
                            px /= d;
                            py /= d;
                            // обратный поворот: где эта точка кадра на неповёрнутом коде (от его левого верхнего угла)
                            double ux = px * cos + py * sin + side / 2;
                            double uy = -px * sin + py * cos + side / 2;
                            sum += level(ux, uy, side, x, y, w);
                        }
                    }
                    plane[y][x] = sum / (SAMPLES * SAMPLES);
                }
            }
            double[] line = new double[Math.max(w, h)];
            for (int pass = 0; pass < blur; pass++) {
                blur(plane, w, h, line);
            }
            Random random = new Random(seed);
            byte[] data = new byte[stride * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double v = plane[y][x] + (noise > 0 ? random.nextGaussian() * noise : 0);
                    data[y * stride + x] = (byte) Math.max(0, Math.min(255, (int) Math.round(v)));
                }
                for (int x = w; x < stride; x++) {
                    data[y * stride + x] = (byte) random.nextInt(256); // мусор в выравнивании строки
                }
            }
            return new Frame(data, stride, w, h);
        }

        /** Яркость в точке кода (координаты — в пикселях от левого верхнего угла кода); за его краем — фон. */
        private double level(double ux, double uy, double side, int x, int y, int width) {
            if (ux >= 0 && uy >= 0 && ux < side && uy < side) {
                int mx = Math.min(modules.length - 1, (int) (ux / moduleSize));
                int my = Math.min(modules.length - 1, (int) (uy / moduleSize));
                return modules[my][mx] != inverted ? dark : light;
            }
            if (background) {
                return 70 + 40 * Math.sin(x / 37.0) * Math.cos(y / 53.0) + x * 20.0 / width; // неровный тёмный фон
            }
            return inverted ? dark : light; // пустое поле — того же цвета, что поля кода
        }

        private static void blur(double[][] plane, int w, int h, double[] line) {
            for (int y = 0; y < h; y++) { // по горизонтали
                for (int x = 0; x < w; x++) {
                    line[x] = (plane[y][Math.max(0, x - 1)] + 2 * plane[y][x] + plane[y][Math.min(w - 1, x + 1)]) / 4;
                }
                System.arraycopy(line, 0, plane[y], 0, w);
            }
            for (int x = 0; x < w; x++) { // по вертикали
                for (int y = 0; y < h; y++) {
                    line[y] = (plane[Math.max(0, y - 1)][x] + 2 * plane[y][x] + plane[Math.min(h - 1, y + 1)][x]) / 4;
                }
                for (int y = 0; y < h; y++) {
                    plane[y][x] = line[y];
                }
            }
        }
    }
}
